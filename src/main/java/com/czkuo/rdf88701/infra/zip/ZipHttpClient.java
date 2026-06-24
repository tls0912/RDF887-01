package com.czkuo.rdf88701.infra.zip;

import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;

/**
 * ZIP Stocker HTTP client。
 *
 * <p>封裝 MCS 主動呼叫 ZIP Stocker API 的同步 POST 操作，負責 target 路由、
 * URL 正規化、逾時設定、JSON header、泛型 Root 回應反序列化與錯誤 log。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class ZipHttpClient {

    /** 統一 log 前綴，避免重複字面值與格式散落各處 */
    private static final String TAG = "ZIP HTTP";

    /** 同步 HTTP 客戶端；由 RestTemplateBuilder 建立並設定逾時 */
    private final RestTemplate restTemplate;

    /** 路由器：將 target 轉 baseUrl */
    private final ZipRouter router;

    public ZipHttpClient(RestTemplateBuilder builder, ZipRouter router) {
        this.router = router;
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(3))  // 連線逾時（建議 2~5s）
                .readTimeout(Duration.ofSeconds(8))     // 讀取逾時（建議 5~15s）
                .build();
    }

    /**
     * 發送 POST 請求給 ZIP
     *
     * @param target        發送目標（例：ZIPA）
     * @param path          API 路徑（例：/StockerInput）
     * @param req           請求 Root<PrimaryBody>
     * @param respBodyClass 回應 Secondary Body 類別
     */
    public <TReq, TRespBody> Root<TRespBody> post(
            ZipTarget target, String path, Root<TReq> req, Class<TRespBody> respBodyClass) {

        String baseUrl = router.baseUrlOf(target);
        String url = join(baseUrl, path);

        long t0 = logReq("POST", url, "target=" + target);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Root<TReq>> entity = new HttpEntity<>(req, headers);

        try {
            ResponseEntity<Root<TRespBody>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ParameterizedTypeReference.forType(ParameterizedTypeUtils.root(respBodyClass))
            );

            logResp("POST", url, resp.getStatusCode(), t0);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("ZIP POST failed: status=" + resp.getStatusCode());
            }
            Root<TRespBody> body = resp.getBody();
            if (body == null) {
                throw new IllegalStateException("ZIP POST failed: empty body, status=" + resp.getStatusCode());
            }
            log.info("[{}] Secondary Header: {}", TAG, body.getHeader());
            return body;

        } catch (HttpStatusCodeException e) {
            // 記下狀態碼與截斷的 response body，便於除錯
            logHttpError("POST", url, e, t0);
            throw e;
        }
    }

    /** URL 正規化：移除 baseUrl 尾端 '/'，確保 path 以單一 '/' 串接 */
    private static String join(String baseUrl, String path) {
        String root = StringUtils.removeEnd(StringUtils.trimToEmpty(baseUrl), "/");
        String p = (path == null ? "" : path.trim());
        p = p.startsWith("/") ? p : ("/" + p);
        return root + p;
    }

    // ---------- 統一請求/回應/錯誤 logging（含耗時，避免 Similar log messages） ----------

    /** 回傳起始時間（ns），後續用來計算總耗時（ms） */
    private long logReq(String method, String url, String extra) {
        long t0 = System.nanoTime();
        if (StringUtils.isNotBlank(extra)) {
            log.info("[{}] {} {} ({})", TAG, method, url, extra);
        } else {
            log.info("[{}] {} {}", TAG, method, url);
        }
        return t0;
    }

    /** 統一成功回應 log：狀態碼 + 總耗時（ms） */
    private void logResp(String method, String url, HttpStatusCode sc, long t0) {
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        log.info("[{}] {} {} -> {} {} ({} ms)", TAG, method, url, sc.value(), sc, ms);
    }

    /** 統一錯誤回應 log：狀態碼 + 截斷的 body + 總耗時（ms） */
    private void logHttpError(String method, String url, HttpStatusCodeException e, long t0) {
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        String brief = StringUtils.abbreviate(e.getResponseBodyAsString(), 2000);
        log.error("[{}] {} {} -> {} {} ({} ms)\n{}",
                TAG, method, url, e.getStatusCode().value(), e.getStatusCode(), ms, brief);
    }

    /**
     * 泛型工具類
     * - 告訴 RestTemplate 如何將 JSON 反序列化成 Root<T>
     * - 因 Java 泛型在 runtime 會被擦除，所以自訂 ParameterizedType
     * <p>
     * 加上 nullness 註解以符合 IDE 的覆寫規約：
     * - getActualTypeArguments()/getRawType() 永不為 null → @NonNull
     * - getOwnerType() 可能為 null（頂層型別） → @Nullable
     */
    static final class ParameterizedTypeUtils {
        static <T> @NonNull ParameterizedType root(@NonNull Class<T> bodyClass) {
            return new ParameterizedType() {
                @Override public @NonNull Type[] getActualTypeArguments() {
                    return new Type[]{ bodyClass };
                }
                @Override public @NonNull Type getRawType() { return Root.class; }
                @Override public @Nullable Type getOwnerType() { return null; }
            };
        }
    }
}
