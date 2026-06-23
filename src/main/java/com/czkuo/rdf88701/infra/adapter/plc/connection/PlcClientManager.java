package com.czkuo.rdf88701.infra.adapter.plc.connection;

import com.czkuo.rdf88701.common.enums.ConnectionMode;
import com.czkuo.rdf88701.common.exception.plc.PlcConnectionException;
import com.czkuo.rdf88701.config.plc.PlcCraneRegistry;
import com.czkuo.rdf88701.config.plc.PlcDeviceRegistry;
import com.czkuo.rdf88701.config.plc.PlcProperties;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.PlcProtocolAdapterFactory;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.PlcProtocolAdapter;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.support.ConnectablePlcProtocolAdapter;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcConnectedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcDisconnectedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcModeChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * PLC 客戶端管理器：
 * 負責所有裝置的初始化、狀態管理、連線控制與封裝操作。
 * 整合 PlcConnectionStrategyManager 提供重試、熔斷、自動切換等功能。
 */
@Slf4j
@Component
public class PlcClientManager {

    private final PlcProtocolAdapterFactory adapterFactory;
    private final PlcDeviceRegistry deviceRegistry;
    private final Executor plcInitExecutor;
    private final PlcEventPublisher eventPublisher;
    private final PlcConnectionStrategyManager strategyManager;

    /** 所有裝置的協議轉接器快取 */
    private final Map<String, PlcProtocolAdapter> adapterMap = new ConcurrentHashMap<>();

    /** 所有裝置的狀態快取 */
    private final Map<String, PlcDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /** 預設值：單一 port 最大重試次數 */
    private static final int DEFAULT_MAX_RETRY_PER_PORT = 3;

    /** 預設值：base backoff 毫秒（每次失敗後會乘 2 遞增） */
    private static final long DEFAULT_BASE_BACKOFF_MS = 500L;

    /** 預設值：每次 connect 預估耗時（用於估算 timeout） */
    private static final long DEFAULT_ESTIMATED_ATTEMPT_MS = 8500L;

    /** 預設值：整體重連 buffer 時間 */
    private static final long DEFAULT_OVERALL_TIMEOUT_BUFFER_MS = 5000L;

    public PlcClientManager(PlcProtocolAdapterFactory adapterFactory,
                            PlcDeviceRegistry deviceRegistry,
                            PlcConnectionStrategyManager strategyManager,
                            PlcEventPublisher eventPublisher,
                            Executor plcInitExecutor,
                            PlcCraneRegistry plcCraneRegistry) {
        this.adapterFactory = adapterFactory;
        this.deviceRegistry = deviceRegistry;
        this.strategyManager = strategyManager;
        this.eventPublisher = eventPublisher;
        this.plcInitExecutor = plcInitExecutor;
    }

    /**
     * 判斷指定裝置是否已完成初始化（已建立 Adapter 與 Status）
     */
    public boolean isInitialized(String deviceName) {
        return adapterMap.containsKey(deviceName) && statusMap.containsKey(deviceName);
    }

    /**
     * 初始化所有啟用中的裝置（支援併發）
     */
    public void initAllDevices(List<PlcProperties.Device> devices) {
        List<CompletableFuture<Void>> futures = devices.stream()
                .filter(PlcProperties.Device::isEnabled)
                .map(device -> CompletableFuture.runAsync(() -> {
                    try {
                        initDevice(device);
                    } catch (Exception e) {
                        log.error("[PLC] 初始化裝置 '{}' 失敗", device.getName(), e);
                    }
                }, plcInitExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("[PLC] 所有啟用裝置初始化完成");
    }

    /**
     * 初始化單一裝置（建立協議轉接器與初始狀態）
     */
    public void initDevice(PlcProperties.Device device) {
        String name = device.getName();
        if (adapterMap.containsKey(name)) {
            log.warn("[PLC] 裝置 '{}' 已初始化，略過", name);
            return;
        }

        // 保證狀態存在但不覆蓋
        ensureStatusInitialized(name, device);

        // 進行連線與通訊狀態設定
        PlcProtocolAdapter adapter = tryConnectWithFailover(device);
        adapterMap.put(name, adapter);

        log.info("[PLC] 裝置 '{}' 初始化完成", name);
    }

    /**
     * 嘗試使用多個 port 自動連線，優先成功者即保留。
     * 整合策略管理器與 IP 可達性檢查機制，並更新裝置狀態。
     */
    private PlcProtocolAdapter tryConnectWithFailover(PlcProperties.Device device) {
        String name = device.getName();
        String ip = device.getIp();
        List<Integer> ports = resolvePorts(device);
        List<Integer> availablePorts = strategyManager.getAvailablePorts(name, ports);

        // 統一取得狀態（不重複 new）
        PlcDeviceStatus status = ensureStatusInitialized(name, device);

        boolean reachable = true;
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            reachable = inetAddress.isReachable(1000);
            if (!reachable) {
                log.warn("[PLC] 裝置 '{}' IP 不可達（{}），略過所有 port 嘗試", name, ip);
            }
        } catch (Exception e) {
            reachable = false;
            log.warn("[PLC] 裝置 '{}' IP 無法解析或 ping 失敗：{}，略過所有 port 嘗試", name, e.getMessage());
        }

        if (!reachable) {
            availablePorts.forEach(port -> strategyManager.markPortFailure(name, port));
        } else {
            for (Integer port : availablePorts) {
                try {
                    device.setPort(port);
                    PlcProtocolAdapter adapter = adapterFactory.getOrCreateAdapter(device);
                    if (adapter instanceof ConnectablePlcProtocolAdapter connectable && connectable.connect()) {
                        strategyManager.markPortSuccess(name, port);
                        log.info("[PLC] 裝置 '{}' 成功連線至 port {}", name, port);

                        if (!status.isConnected()) {
                            boolean firstTime = (status.getLastConnectedTime() == null); // 先取狀態
                            status.markConnected(); // 再設定狀態

                            if (firstTime) {
                                log.info("[PLC] 裝置 '{}' 初次成功連線", name);
                            } else {
                                log.info("[PLC] 裝置 '{}' 通訊成功恢復", name);
                            }
                        }

                        return adapter;
                    } else {
                        strategyManager.markPortFailure(name, port);
                        log.warn("[PLC] 裝置 '{}' port {} 連線失敗（connect() 返回 false）", name, port);
                    }
                } catch (Exception e) {
                    strategyManager.markPortFailure(name, port);
                    log.warn("[PLC] 裝置 '{}' port {} 連線過程發生例外：{}", name, port, e.getMessage());
                }
            }
        }

        // 所有 port 嘗試皆失敗，標記為斷線
        if (status.isConnected()) {
            status.markDisconnected("All ports failed");
            log.warn("[PLC] 裝置 '{}' 通訊中斷（所有 port 嘗試失敗）", name);
        }

        int fallbackPort = ports.isEmpty() ? 0 : ports.get(0);
        device.setPort(fallbackPort);
        PlcProtocolAdapter fallbackAdapter = adapterFactory.getOrCreateAdapter(device);
        log.warn("[PLC] 裝置 '{}' 所有 port 嘗試皆失敗，已建立 fallback adapter（port {}）", name, fallbackPort);
        return fallbackAdapter;
    }

    /**
     * 封裝執行某裝置動作的方法（禁止未連線情況下執行，且僅允許有效連線）
     */
    public <T> T executeIfAllowed(String deviceName, Function<PlcProtocolAdapter, T> action) {
        PlcDeviceStatus status = statusMap.get(deviceName);
        if (status == null) throw new PlcConnectionException("Device status missing: " + deviceName);

        ConnectionMode mode = status.getConnectionMode();
        if (mode == ConnectionMode.OFF || (mode == ConnectionMode.MANUAL && !status.isConnected())) {
            throw new PlcConnectionException("Device not allowed to communicate: " + deviceName + " [mode=" + mode + "]");
        }

        PlcProtocolAdapter adapter = adapterMap.get(deviceName);
        if (adapter == null) throw new PlcConnectionException("Adapter not found: " + deviceName);

        // 嚴格驗證實際是否已建立連線（不依賴內部狀態）
        if (adapter instanceof ConnectablePlcProtocolAdapter connectable && !connectable.isConnected()) {
            throw new PlcConnectionException("PLC not physically connected: " + deviceName);
        }

        PlcProperties.Device device = deviceRegistry.getDevice(deviceName);
        boolean wasConnected = status.isConnected();
        Instant now = Instant.now();

        try {
            T result = action.apply(adapter);
            status.markConnected();
            if (!wasConnected) {
                eventPublisher.publish(new PlcConnectedEvent(
                        deviceName, now, device.getIp(), device.getProtocol(),
                        "Communication recovered successfully", mode, null, status.getReconnectAttempts()
                ));
                log.info("[PLC] 裝置 '{}' 通訊成功恢復", deviceName);
            }
            return result;
        } catch (Exception ex) {
            if (wasConnected) {
                status.markDisconnected(ex.getMessage());
                eventPublisher.publish(new PlcDisconnectedEvent(
                        deviceName, now, device.getIp(), device.getProtocol(),
                        ex.getMessage(), mode, PlcDisconnectedEvent.Reason.COMMAND_FAILED
                ));
                log.error("[PLC] 裝置 '{}' 因通訊命令失敗而中斷連線：{}", deviceName, ex.getMessage(), ex);
            }
            throw ex;
        }
    }

    /**
     * 註冊 Adapter（初始化時呼叫）
     */
    public void registerAdapter(String deviceName, PlcProtocolAdapter adapter) {
        adapterMap.put(deviceName, adapter);
        log.info("[PLC] Adapter registered for device: {}", deviceName);
    }

    /**
     * 設定狀態物件（初始化時呼叫）
     */
    public void registerDeviceStatus(String deviceName, PlcDeviceStatus status) {
        statusMap.put(deviceName, status);
        log.info("[PLC] Status registered for device: {}", deviceName);
    }

    /**
     * 查詢裝置是否已註冊
     */
    public boolean isDeviceRegistered(String deviceName) {
        return adapterMap.containsKey(deviceName) && statusMap.containsKey(deviceName);
    }

    /**
     * 外部請求建立裝置連線（例如透過 UI 或 API）
     * 僅在 MANUAL 模式下允許操作，並整合熔斷與策略控制
     */
    public boolean connectDevice(String name) {
        PlcDeviceStatus status = statusMap.get(name);
        if (status == null) {
            log.warn("[PLC] 裝置 '{}' 無對應狀態，拒絕外部連線請求", name);
            return false;
        }

        if (status.getConnectionMode() != ConnectionMode.MANUAL) {
            log.warn("[PLC] 裝置 '{}' 非 MANUAL 模式，拒絕外部連線請求", name);
            return false;
        }

        PlcProperties.Device device = deviceRegistry.getDevice(name);
        List<Integer> ports = resolvePorts(device);
        List<Integer> availablePorts = strategyManager.getAvailablePorts(name, ports);

        for (Integer port : availablePorts) {
            try {
                device.setPort(port);
                PlcProtocolAdapter adapter = adapterFactory.getOrCreateAdapter(device);
                if (adapter instanceof ConnectablePlcProtocolAdapter connectable && connectable.connect()) {
                    adapterMap.put(name, adapter);
                    status.markConnected();
                    strategyManager.markPortSuccess(name, port);
                    eventPublisher.publish(new PlcConnectedEvent(name, Instant.now(), device.getIp(), device.getProtocol(), "External connect success", ConnectionMode.MANUAL, null, null));
                    log.info("[PLC] 裝置 '{}' 外部啟動連線成功（port {}）", name, port);
                    return true;
                } else {
                    strategyManager.markPortFailure(name, port);
                }
            } catch (Exception e) {
                strategyManager.markPortFailure(name, port);
                log.warn("[PLC] 裝置 '{}' 外部連線至 port {} 失敗：{}", name, port, e.getMessage());
            }
        }

        log.warn("[PLC] 裝置 '{}' 外部連線所有 port 嘗試皆失敗", name);
        return false;
    }

    /**
     * 外部請求中斷裝置連線（例如透過 UI 或 API）
     */
    public void disconnectDevice(String name) {
        PlcProtocolAdapter adapter = adapterMap.get(name);
        PlcDeviceStatus status = statusMap.get(name);
        if (adapter instanceof ConnectablePlcProtocolAdapter connectable) {
            connectable.disconnect();
            status.markDisconnected("External disconnect");
            PlcProperties.Device device = deviceRegistry.getDevice(name);
            eventPublisher.publish(new PlcDisconnectedEvent(name, Instant.now(), device.getIp(), device.getProtocol(), "External disconnect", ConnectionMode.MANUAL, PlcDisconnectedEvent.Reason.EXTERNAL_DISCONNECT));
            log.info("[PLC] 裝置 '{}' 外部中斷連線", name);
        }
    }

    /**
     * 執行自動重連時，使用所有 port 順序嘗試重建連線（含熔斷與記錄）
     * 每個 port 最多嘗試指定次數才會切換到下一個 port。
     * 每次重試前會先檢查 IP 是否可連線，再視情況嘗試連線。
     * 每次重試之間會引入遞增 backoff 延遲以減少壓力。
     * 總 timeout 為根據 port 數與 retry 次數自適應推算（以 connect-timeout 為基礎）。
     */
    public boolean reconnectInternal(String name, PlcDisconnectedEvent.Reason reason, String message) {
        log.info("[PLC] 裝置 '{}' 觸發自動重連流程（原因：{}，訊息：{}）", name, reason, message);

        PlcProperties.Device device = deviceRegistry.getDevice(name);
        PlcDeviceStatus status = statusMap.get(name);

        List<Integer> ports = resolvePorts(device);
        List<Integer> availablePorts = strategyManager.getAvailablePorts(name, ports);
        String ip = device.getIp();

        int maxRetryPerPort = deviceRegistry.getOptionInt(name, "max-retry-per-port", DEFAULT_MAX_RETRY_PER_PORT);
        long baseBackoffMs = deviceRegistry.getOptionLong(name, "base-backoff-ms", DEFAULT_BASE_BACKOFF_MS);
        long estimatedAttemptMs = deviceRegistry.getOptionLong(name, "estimated-attempt-ms", DEFAULT_ESTIMATED_ATTEMPT_MS);
        long bufferMs = deviceRegistry.getOptionLong(name, "overall-timeout-buffer-ms", DEFAULT_OVERALL_TIMEOUT_BUFFER_MS);

        long overallTimeoutMs = availablePorts.size() * maxRetryPerPort * estimatedAttemptMs + bufferMs;
        Instant overallDeadline = Instant.now().plusMillis(overallTimeoutMs);

        for (Integer port : availablePorts) {
            // 檢查 PLC 是否可連線
            try {
                InetAddress address = InetAddress.getByName(ip);
                if (!address.isReachable(1000)) {
                    log.warn("[PLC] 裝置 '{}' 的 PLC 無法連線（IP: {}），跳過 port {} 嘗試", name, ip, port);
                    strategyManager.markPortFailure(name, port);
                    continue;
                }
            } catch (Exception e) {
                log.warn("[PLC] 裝置 '{}' 嘗試連線 PLC（IP: {}）時發生錯誤：{}，跳過 port {}", name, ip, e.getMessage(), port);
                strategyManager.markPortFailure(name, port);
                continue;
            }

            for (int i = 0; i < maxRetryPerPort; i++) {
                if (Instant.now().isAfter(overallDeadline)) {
                    log.warn("[PLC] 裝置 '{}' 自動重連已達總超時限制（{} ms），停止重連流程", name, overallTimeoutMs);
                    status.markDisconnected("重連超時（整體）");
                    log.warn("[PLC] 裝置 '{}' 所有 port 自動重連失敗", name);
                    eventPublisher.publish(new PlcDisconnectedEvent(
                            name, Instant.now(), ip, device.getProtocol(), "重連超時（整體）",
                            ConnectionMode.AUTO, reason
                    ));
                    return false;
                }

                try {
                    device.setPort(port);
                    PlcProtocolAdapter adapter = adapterFactory.getOrCreateAdapter(device);
                    Instant start = Instant.now();
                    if (adapter instanceof ConnectablePlcProtocolAdapter connectable && connectable.connect()) {
                        long cost = Duration.between(start, Instant.now()).toMillis();
                        adapterMap.put(name, adapter);
                        status.markConnected();
                        strategyManager.markPortSuccess(name, port);
                        log.info("[PLC] 裝置 '{}' 自動重連成功於 port {}（第 {} 次嘗試，用時 {}ms）", name, port, i + 1, cost);
                        eventPublisher.publish(new PlcConnectedEvent(
                                name, Instant.now(), ip, device.getProtocol(), message,
                                ConnectionMode.AUTO, null, status.getReconnectAttempts()
                        ));
                        return true;
                    } else {
                        strategyManager.markPortFailure(name, port);
                        log.warn("[PLC] 裝置 '{}' 第 {} 次嘗試連線 port {} 失敗（connect() = false）", name, i + 1, port);
                    }
                } catch (Exception e) {
                    strategyManager.markPortFailure(name, port);
                    log.warn("[PLC] 裝置 '{}' 第 {} 次嘗試連線 port {} 發生例外：{}", name, i + 1, port, e.getMessage());
                }

                try {
                    long backoff = baseBackoffMs * (1L << i);
                    log.info("[PLC] 裝置 '{}' port {} 第 {} 次重試，等待 backoff: {}ms", name, port, i + 1, backoff);
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[PLC] 裝置 '{}' 重連中斷於 port {}，停止 backoff 重試", name, port);
                    break;
                }
            }

            log.warn("[PLC] 裝置 '{}' port {} 達最大重試次數（{} 次）→ 切換下一個 port", name, port, maxRetryPerPort);
        }

        status.markDisconnected(message);
        log.warn("[PLC] 裝置 '{}' 所有 port 自動重連失敗", name);
        eventPublisher.publish(new PlcDisconnectedEvent(
                name, Instant.now(), ip, device.getProtocol(), message,
                ConnectionMode.AUTO, reason
        ));
        return false;
    }

    /**
     * 取得有效的 port 列表，
     * 若 ports 有值則直接使用；
     * 若只有 port 單一值則轉為 List；
     * 若皆無則回傳空清單（避免 NullPointerException）
     */
    private List<Integer> resolvePorts(PlcProperties.Device device) {
        if (device.getPorts() != null && !device.getPorts().isEmpty()) {
            return device.getPorts();
        }
        if (device.getPort() != null) {
            return List.of(device.getPort());
        }
        return Collections.emptyList();
    }

    /**
     * 切換裝置的連線模式（OFF / MANUAL / AUTO）
     * 切換至 AUTO 模式時，若未連線則嘗試重連。
     * 切換至 OFF 或 MANUAL 模式時，若已連線則中斷連線。
     */
    public void switchConnectionMode(String name, ConnectionMode newMode, String reason) {
        PlcDeviceStatus status = statusMap.get(name);
        if (status == null) throw new IllegalArgumentException("Device status not found: " + name);

        ConnectionMode oldMode = status.getConnectionMode();
        if (oldMode == newMode) {
            log.info("[PLC] 裝置 '{}' 模式未變更（仍為 {}）", name, newMode);
            return;
        }

        status.setConnectionMode(newMode);
        log.info("[PLC] 裝置 '{}' 模式由 {} → {}，理由: {}", name, oldMode, newMode, reason);

        PlcProperties.Device device = deviceRegistry.getDevice(name);
        PlcProtocolAdapter adapter = adapterMap.get(name);

        // 發送模式變更事件
        eventPublisher.publish(new PlcModeChangedEvent(name, Instant.now(), oldMode, newMode, reason));

        // 切換為 OFF 模式 → 強制中斷連線
        if (newMode == ConnectionMode.OFF && adapter instanceof ConnectablePlcProtocolAdapter connectable) {
            connectable.disconnect();
            status.markDisconnected("Switched to OFF mode");
            eventPublisher.publish(new PlcDisconnectedEvent(
                    name, Instant.now(), device.getIp(), device.getProtocol(),
                    "Switched to OFF mode", newMode, PlcDisconnectedEvent.Reason.INTERNAL_DISCONNECT
            ));
            log.info("[PLC] 裝置 '{}' 模式為 OFF，已中斷連線", name);
            return;
        }

        // 切換為 AUTO 模式且尚未連線 → 嘗試自動連線
        if (newMode == ConnectionMode.AUTO && !status.isConnected() && adapter instanceof ConnectablePlcProtocolAdapter) {
            reconnectInternal(name, PlcDisconnectedEvent.Reason.RECONNECT_FAILED, "Mode switched to AUTO");
        }
    }

    /** 查詢裝置是否連線中 */
    public boolean isConnected(String name) {
        PlcDeviceStatus status = statusMap.get(name);
        return status != null && status.isConnected();
    }

    /**
     * 查詢裝置是否真實連線中（透過底層 adapter 查詢）
     */
    public boolean isActuallyConnected(String name) {
        PlcProtocolAdapter adapter = adapterMap.get(name);
        if (adapter instanceof ConnectablePlcProtocolAdapter connectable) {
            return connectable.isConnected(); // 查詢底層 TCP/Socket 連線狀態
        }
        return false; // 不支援查詢的 adapter 一律視為未連線
    }

    /** 查詢裝置的狀態物件 */
    public PlcDeviceStatus getStatus(String name) {
        return statusMap.get(name);
    }

    /** 查詢所有裝置名稱 */
    public List<String> getAllDeviceNames() {
        return adapterMap.keySet().stream().toList();
    }

    /** 清除所有快取資料（測試或重新載入時使用） */
    public void clearAll() {
        adapterMap.clear();
        statusMap.clear();
        log.info("[PLC] 所有 PLC adapter 與狀態快取已清除");
    }

    /**
     * 確保裝置狀態已存在於狀態快取中，若無則初始化。
     * <p>
     * 此方法將使用 {@link PlcDeviceStatus#from(PlcProperties.Device)} 建立狀態物件，
     * 僅在尚未初始化時才會建立，避免覆蓋先前透過通訊連線流程所更新的狀態資料。
     *
     * @param name 裝置名稱（唯一識別）
     * @param device 裝置設定，用於初始化狀態（僅在缺少時使用）
     * @return 已存在或新建的狀態物件
     */
    private PlcDeviceStatus ensureStatusInitialized(String name, PlcProperties.Device device) {
        return statusMap.computeIfAbsent(name, k -> PlcDeviceStatus.from(device));
    }
}
