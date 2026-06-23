package com.czkuo.rdf88701.infra.mqtt;

import com.czkuo.rdf88701.config.mqtt.MqttConnectionProperties;
import com.czkuo.rdf88701.config.mqtt.MqttConfigProperties;
import com.czkuo.rdf88701.domain.event.MqttMessageReceivedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;


/**
 * MqttClientManager
 * - 僅負責 MQTT client 初始化與實際發送（底層通訊）
 * - 使用 Eclipse Paho v5.0 client，支援多個連線（多 target system）
 * - 所有收到的訊息皆透過 Spring 事件方式傳出（與業務解耦）
 */
@Slf4j
@Component
public class MqttClientManager {

    private final MqttConfigProperties mqttConfigProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor mqttInboxExecutor;

    // 儲存各個系統的連線設定（從設定檔讀入）
    private Map<String, MqttConnectionProperties> connectionConfigs;

    // 各系統對應的 MQTT 客戶端實體（ConcurrentHashMap，避免回呼執行緒併發問題）
    @Getter
    private final Map<String, MqttClient> clientMap = new ConcurrentHashMap<>();

    // 每個 system 的 backoff 設定（毫秒）
    private final Map<String, Long> connectBackoffMs = new ConcurrentHashMap<>();

    // 每個 system 一把「publish 併發閥門」：避免把 Paho 的 in-flight 撐爆
    private final Map<String, Semaphore> publishGuards = new ConcurrentHashMap<>();

    // 用於訂閱重試等小型延遲任務（daemon 單執行緒）
    private final ScheduledExecutorService mqttScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mqtt-mini-scheduler");
                t.setDaemon(true);
                return t;
            });

    public MqttClientManager(
            MqttConfigProperties mqttConfigProperties,
            ApplicationEventPublisher eventPublisher,
            Executor mqttInboxExecutor
    ) {
        this.mqttConfigProperties = mqttConfigProperties;
        this.eventPublisher = eventPublisher;
        this.mqttInboxExecutor = mqttInboxExecutor;
    }

    /**
     * 取得（或建立）指定 system 的 publish 併發閥門。預設同時放行 8 筆。
     */
    private Semaphore guardFor(String system) {
        return publishGuards.computeIfAbsent(system, s -> new Semaphore(8));
    }

    /**
     * 初始化方法，在 Spring 啟動後建立所有連線與訂閱
     */
    @PostConstruct
    public void init() {
        this.connectionConfigs = mqttConfigProperties.getConnections();

        connectionConfigs.forEach((system, config) -> {
            try {
                log.info("[MQTT][{}] 準備連線到 broker：{}", system, config.getBroker());

                // 使用 Paho 內建的執行緒/排程
                MqttClient client = new MqttClient(config.getBroker(), config.getClientId(), null);

                // 建議：讓 token 等待時間 > connectionTimeout，避免外層先超時
                client.setTimeToWait(15_000); // 可視需要調整或移除

                client.setCallback(new MqttCallback() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        log.info("[MQTT][{}] {}連線至 broker：{}", system, reconnect ? "重" : "已", serverURI);
                        try {
                            client.subscribe(config.getRecvTopic(), 0);
                            log.info("[MQTT][{}] 已訂閱 topic={}", system, config.getRecvTopic());
                        } catch (MqttException e) {
                            // 重連臨界點偶爾拿不到 SUBACK → 延遲 400ms 重試一次（一次即可）
                            log.warn("[MQTT][{}] 訂閱失敗（稍後重試一次）：{}", system, e.getMessage());
                            mqttScheduler.schedule(() -> {
                                try {
                                    if (client.isConnected()) {
                                        client.subscribe(config.getRecvTopic(), 0);
                                        log.info("[MQTT][{}] 重試訂閱成功 → {}", system, config.getRecvTopic());
                                    }
                                } catch (Exception ex) {
                                    log.error("[MQTT][{}] 重試訂閱仍失敗：{}", system, ex.getMessage(), ex);
                                }
                            }, 400, TimeUnit.MILLISECONDS);
                        }
                    }

                    @Override
                    public void disconnected(MqttDisconnectResponse resp) {
                        Throwable cause = resp.getException();
                        if (cause != null) {
                            log.warn("[MQTT][{}] 連線中斷：{}，cause={}", system, resp.getReasonString(), cause.getMessage(), cause);
                        } else {
                            log.warn("[MQTT][{}] 連線中斷：{}", system, resp.getReasonString());
                        }
                    }

                    @Override
                    public void mqttErrorOccurred(MqttException exception) {
                        log.error("[MQTT][{}] 發生例外：{}", system, exception.getMessage(), exception);
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                        log.info("[MQTT][{}] 接收到訊息 → topic={} qos={} retained={} payload={}",
                                system, topic, message.getQos(), message.isRetained(), payload);

                        try {
                            mqttInboxExecutor.execute(() -> {
                                try {
                                    eventPublisher.publishEvent(new MqttMessageReceivedEvent(this, system, topic, payload));
                                } catch (Exception e) {
                                    log.error("[MQTT][{}] 處理消息失敗，topic={}，原因={}", system, topic, e.getMessage(), e);
                                }
                            });
                        } catch (RuntimeException rex) {
                            // 例如佇列滿被丟棄（DiscardOldestPolicy 不會丟異常；CallerRuns/Abort 才會）
                            log.warn("[MQTT][{}] inbox 忙碌，丟棄 QoS0 訊息 topic={}", system, topic);
                        }
                    }

                    @Override
                    public void deliveryComplete(IMqttToken token) {
                        //log.debug("[MQTT][{}] deliveryComplete (callback triggered)", system);
                    }

                    @Override
                    public void authPacketArrived(int reasonCode, MqttProperties properties) {
                        //log.debug("[MQTT][{}] 收到 Auth Packet，reasonCode={}", system, reasonCode);
                    }
                });

                // ===== 連線選項（MQTT 5）=====
                // MqttConnectionOptions options = new MqttConnectionOptions();
                // options.setCleanStart(true);
                // options.setAutomaticReconnect(true);
                // options.setKeepAliveInterval(20);
                // options.setConnectionTimeout(10);
                // options.setReceiveMaximum(64);

                // if (config.getUsername() != null) options.setUserName(config.getUsername());
                // if (config.getPassword() != null) options.setPassword(config.getPassword().getBytes(StandardCharsets.UTF_8));

                // 連線；成功後會觸發 connectComplete(...)，並在那裡完成（重）訂閱
                //client.connect(options);

                clientMap.put(system, client);

                // 啟動「首次連線」：成功則回呼 connectComplete，失敗則我們自己指數回退重試
                scheduleConnect(system, client, config, 0);

                log.info("[MQTT][{}] 已啟動並等待訊息（KeepAlive=20s, AutoReconnect=ON, CleanStart=true, ReceiveMaximum=64）", system);
            } catch (MqttException e) {
                log.error("[MQTT][{}] 初始化連線失敗：{}", system, e.getMessage(), e);
            }
        });
    }

    /**
     * 實際發送 MQTT 訊息（不包含業務層記錄邏輯）
     * - QoS 0：對你目前場景足夠；斷線期間不排隊
     * - 加入「本地併發閥門」避免把 Paho in-flight 打爆（v5 沒有 setMaxInflight）
     */
    public void publish(String system, String payload) throws MqttException {
        MqttClient client = clientMap.get(system);
        if (client == null) {
            throw new MqttException(new Throwable("無效的系統代號（未設定連線資訊）：[" + system + "]"));
        }
        if (!client.isConnected()) {
            throw new MqttException(new Throwable("MQTT 尚未連線至目標系統：[" + system + "]"));
        }

        String topic = getSendTopic(system);
        if (topic == null || topic.isBlank()) {
            throw new MqttException(new Throwable("未設定發送 topic（sendTopic）給系統：[" + system + "]"));
        }

        Semaphore sem = guardFor(system);
        boolean acquired = false;
        try {
            acquired = sem.tryAcquire(100, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new MqttException(new Throwable("本地節流：publish 併發過高"));
            }

            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(0);
            message.setRetained(false);

            client.publish(topic, message);  // 同步：QoS0 已寫出；QoS1/2 等到 ACK
            log.info("[MQTT][{}] 已發送訊息 → topic={} qos={} retained={} payload={}",
                    system, topic, message.getQos(), message.isRetained(), payload);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MqttException(ie);
        } finally {
            if (acquired) sem.release();
        }
    }

    /**
     * 單次連線嘗試（成功 true / 失敗 false）
     */
    private boolean connectOnce(String system, MqttClient client, MqttConnectionProperties config) {
        try {
            // ===== 連線選項（MQTT 5）=====
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setCleanStart(true);
            options.setAutomaticReconnect(true);  // 成功後的斷線交給它
            options.setKeepAliveInterval(20);
            options.setConnectionTimeout(10);
            options.setReceiveMaximum(64);

            if (config.getUsername() != null) options.setUserName(config.getUsername());
            if (config.getPassword() != null) options.setPassword(config.getPassword().getBytes(StandardCharsets.UTF_8));

            client.setTimeToWait(15_000);
            client.connect(options); // 可能丟出例外
            log.info("[MQTT][{}] connectOnce 成功（AutoReconnect=ON）", system);
            // 連線成功 → 重置 backoff
            connectBackoffMs.put(system, 1_000L);
            return true;
        } catch (MqttException e) {
            log.warn("[MQTT][{}] connectOnce 失敗：{}", system, e.getMessage());
            return false;
        }
    }

    /**
     * 排程（或立即）重試連線，採指數回退
     */
    private void scheduleConnect(String system, MqttClient client, MqttConnectionProperties config, long initialDelayMs) {
        long delay = (initialDelayMs > 0)
                ? initialDelayMs
                : connectBackoffMs.computeIfAbsent(system, s -> 1_000L); // 初始 1 秒

        mqttScheduler.schedule(() -> {
            // 已連上就不用再連
            if (client.isConnected()) {
                return;
            }
            boolean ok = connectOnce(system, client, config);
            if (!ok) {
                long next = Math.min(delay * 2, 60_000L); // 封頂 60s
                connectBackoffMs.put(system, next);
                log.info("[MQTT][{}] 將於 {} ms 後再嘗試連線", system, next);
                scheduleConnect(system, client, config, next);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 取得指定系統的發送 topic 名稱
     */
    public String getSendTopic(String system) {
        MqttConnectionProperties config = connectionConfigs.get(system);
        return (config != null) ? config.getSendTopic() : null;
    }

    /**
     * 取得指定系統的連線狀態
     */
    public boolean isConnected(String system) {
        MqttClient c = clientMap.get(system);
        return c != null && c.isConnected();
    }

    /**
     * 關閉所有 MQTT client 連線（Spring 關閉前自動執行）
     */
    @PreDestroy
    public void shutdown() {
        clientMap.forEach((system, client) -> {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                    log.info("[MQTT][{}] 已正常中斷連線", system);
                }
                client.close();
            } catch (MqttException e) {
                log.warn("[MQTT][{}] 關閉連線失敗：{}", system, e.getMessage());
            }
        });
        mqttScheduler.shutdownNow();
    }
}
