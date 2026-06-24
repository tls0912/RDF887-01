package com.czkuo.rdf88701.infra.ocr;

import com.czkuo.rdf88701.domain.dto.ocr.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * OCR 廠商 HTTP client。
 *
 * <p>封裝 MCS 呼叫 OCR 廠商 API 的同步 HTTP 操作，負責 URL 正規化、逾時設定、
 * Authorization header、成功/錯誤 log 與 404 fallback，不負責本地任務狀態流轉。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class OcrVendorHttpClient {

    /** 統一 log 前綴，避免字面值重複產生告警 */
    private static final String TAG = "OCR HTTP";

    /** 同步 HTTP 客戶端；由 RestTemplateBuilder 建立並設定超時 */
    private final RestTemplate restTemplate;

    @Value("${ocr.vendor.base-url}")
    private String baseUrl; // 例：http://192.168.1.199:50 或 https://vendor.example.com

    @Value("${ocr.vendor.api-key:}") // 若需要驗證就設；留空則不帶
    private String apiKey;            // 送出時帶 Authorization: Bearer {apiKey}

    private static final String V1 = "/api/v1";

    /**
     * 以 RestTemplateBuilder 建立 RestTemplate（設定合理超時）。
     * 使用 connectTimeout()/readTimeout()（3.4+），避免使用已棄用的 setXxxTimeout。
     */
    public OcrVendorHttpClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(8))
                .build();
    }

    // ---------------- 公開 API ----------------

    /** POST /ocr-tasks：建立任務（非 2xx 會拋例外） */
    public OcrCreateTaskResponse createTask(OcrCreateTaskRequest req) {
        final String url = url("/ocr-tasks");
        long t0 = logReq("POST", url);
        try {
            ResponseEntity<OcrCreateTaskResponse> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(req, jsonHeaders()), OcrCreateTaskResponse.class);
            logResp("POST", url, resp.getStatusCode(), t0);
            return require2xxBody(resp, "createTask");
        } catch (HttpStatusCodeException e) {
            logHttpError("POST", url, e, t0);
            throw e; // 交由上層判斷是否重試
        }
    }

    /** GET /ocr-tasks/{taskId}：查任務狀態（404 → Optional.empty()） */
    public Optional<OcrTaskStatusResponse> getTaskStatus(long taskId) {
        final String url = url("/ocr-tasks/" + taskId);
        long t0 = logReq("GET", url);
        try {
            ResponseEntity<OcrTaskStatusResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), OcrTaskStatusResponse.class);
            logResp("GET", url, resp.getStatusCode(), t0);
            return Optional.of(require2xxBody(resp, "getTaskStatus"));
        } catch (HttpClientErrorException.NotFound e) {
            logWarn404("GET", url, "taskId=" + taskId, t0);
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            logHttpError("GET", url, e, t0);
            throw e;
        }
    }

    /** GET /ocr-tasks/{taskId}/image：取單張圖片（404 → Optional.empty()） */
    public Optional<OcrTaskImageResponse> getTaskImage(long taskId) {
        final String url = url("/ocr-tasks/" + taskId + "/image");
        long t0 = logReq("GET", url);
        try {
            ResponseEntity<OcrTaskImageResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), OcrTaskImageResponse.class);
            logResp("GET", url, resp.getStatusCode(), t0);
            return Optional.of(require2xxBody(resp, "getTaskImage"));
        } catch (HttpClientErrorException.NotFound e) {
            logWarn404("GET", url, "taskId=" + taskId, t0);
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            logHttpError("GET", url, e, t0);
            throw e;
        }
    }

    /** GET /ocr-tasks/{taskId}/images：取多張圖片（404 → Optional.empty()） */
    public Optional<OcrTaskImagesResponse> getTaskImages(long taskId) {
        final String url = url("/ocr-tasks/" + taskId + "/images");
        long t0 = logReq("GET", url);
        try {
            ResponseEntity<OcrTaskImagesResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), OcrTaskImagesResponse.class);
            logResp("GET", url, resp.getStatusCode(), t0);
            return Optional.of(require2xxBody(resp, "getTaskImages"));
        } catch (HttpClientErrorException.NotFound e) {
            logWarn404("GET", url, "taskId=" + taskId, t0);
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            logHttpError("GET", url, e, t0);
            throw e;
        }
    }

    /** GET /ocr-devices/{id}/status：查設備狀態（404 → Optional.empty()） */
    public Optional<OcrDeviceStatusResponse> getDeviceStatus(int ocrDeviceId) {
        final String url = url("/ocr-devices/" + ocrDeviceId + "/status");
        long t0 = logReq("GET", url);
        try {
            ResponseEntity<OcrDeviceStatusResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), OcrDeviceStatusResponse.class);
            logResp("GET", url, resp.getStatusCode(), t0);
            return Optional.of(require2xxBody(resp, "getDeviceStatus"));
        } catch (HttpClientErrorException.NotFound e) {
            logWarn404("GET", url, "id=" + ocrDeviceId, t0);
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            logHttpError("GET", url, e, t0);
            throw e;
        }
    }

    /** GET /ocr-devices/{id}/alarms：查警報清單（2xx 但 body=null → 回空清單） */
    public List<OcrAlarmItem> getDeviceAlarms(int ocrDeviceId) {
        final String url = url("/ocr-devices/" + ocrDeviceId + "/alarms");
        long t0 = logReq("GET", url);
        try {
            ResponseEntity<List<OcrAlarmItem>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()),
                    new ParameterizedTypeReference<List<OcrAlarmItem>>() {});
            logResp("GET", url, resp.getStatusCode(), t0);
            List<OcrAlarmItem> body = resp.getStatusCode().is2xxSuccessful() ? resp.getBody() : null;
            return body != null ? body : List.of();
        } catch (HttpClientErrorException.NotFound e) {
            logWarn404("GET", url, "id=" + ocrDeviceId, t0);
            return List.of();
        } catch (HttpStatusCodeException e) {
            logHttpError("GET", url, e, t0);
            throw e;
        }
    }

    // ---------------- 共用工具 ----------------

    /** 規範化 URL：去掉 baseUrl 結尾的 "/"，確保用單一 "/" 串接路徑。 */
    private String url(String path) {
        String root = StringUtils.removeEnd(StringUtils.trimToEmpty(baseUrl), "/");
        String p = path.startsWith("/") ? path : ("/" + path);
        return root + V1 + p;
    }

    /** 驗證 2xx 且 body 不為 null，否則拋 IllegalStateException。 */
    private static <T> T require2xxBody(ResponseEntity<T> resp, String action) {
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(action + " failed: status=" + resp.getStatusCode());
        }
        T body = resp.getBody();
        if (body == null) {
            throw new IllegalStateException(action + " failed: empty body, status=" + resp.getStatusCode());
        }
        return body;
    }

    // ---- 統一的請求/回應/錯誤 log（解決 Similar log messages，並加入耗時統計） ----

    /** 回傳起始時間（ns），之後用來算總耗時（ms）。 */
    private long logReq(String method, String url) {
        long t0 = System.nanoTime();
        log.info("[{}] {} {}", TAG, method, url);
        return t0;
    }

    /** 輸出成功回應的狀態碼與總耗時（ms）。 */
    private void logResp(String method, String url, HttpStatusCode sc, long t0) {
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        log.info("[{}] {} {} -> {} {} ({} ms)", TAG, method, url, sc.value(), sc, ms);
    }

    /** 針對 404 的警告 log（含提示字串與耗時）。 */
    private void logWarn404(String method, String url, String hint, long t0) {
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        log.warn("[{}] {} {} -> 404 Not Found [{}] ({} ms)", TAG, method, url, hint, ms);
    }

    /** 輸出錯誤回應（含狀態碼、截斷後 response body 與耗時）。 */
    private void logHttpError(String method, String url, HttpStatusCodeException e, long t0) {
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        String body = StringUtils.abbreviate(e.getResponseBodyAsString(), 2000); // 截斷避免爆 log
        log.error("[{}] {} {} -> {} {} ({} ms)\n{}",
                TAG, method, url, e.getStatusCode().value(), e.getStatusCode(), ms, body);
    }

    // ---------------- Headers ----------------

    /** GET 用：Accept: application/json；若有 apiKey 則帶 Authorization。 */
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.isNotBlank(apiKey)) {
            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return h;
    }

    /** POST/PUT 用：在 headers() 基礎上加 Content-Type: application/json。 */
    private HttpHeaders jsonHeaders() {
        HttpHeaders h = headers();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
