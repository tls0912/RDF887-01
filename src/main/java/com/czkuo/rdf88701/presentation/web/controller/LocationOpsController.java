package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.LocationOpsService;
import com.czkuo.rdf88701.presentation.web.dto.BindRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Location 操作 Controller：
 * - 鎖/解
 * - 預約/取消
 * - 建帳/清帳
 * <p>
 * 成功一律回 204，前端再自行刷新列表/明細。
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationOpsController {

    private final LocationOpsService ops;

    // ---------------- Lock / Unlock ----------------

    @PostMapping("/{id}/lock")
    public ResponseEntity<Void> lock(@PathVariable("id") Long id,
                                     @RequestBody(required = false) ReasonBody body) {
        ops.lock(id, body != null ? body.getReason() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable("id") Long id) {
        ops.unlock(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Reserve / Unreserve ----------------

    @PostMapping("/{id}/reserve")
    public ResponseEntity<Void> reserve(@PathVariable("id") Long id,
                                        @RequestBody(required = false) ReasonBody body) {
        ops.reserve(id, body != null ? body.getReason() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unreserve")
    public ResponseEntity<Void> unreserve(@PathVariable("id") Long id) {
        ops.unreserve(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Bind / Clear ----------------

    @PostMapping("/{id}/bind")
    public ResponseEntity<Void> bind(@PathVariable("id") Long id,
                                     @RequestBody BindRequest req) {
        ops.bind(id, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/clear")
    public ResponseEntity<Void> clear(@PathVariable("id") Long id) {
        ops.clear(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{carrierId}/manualStockOut")
    public ResponseEntity<Void> manualStockOut(@PathVariable("carrierId") String carrierId) {
        ops.manualStockOut(carrierId);
        return ResponseEntity.noContent().build();

    }

    /**
     * 通用的「帶原因字串」body。
     * 若你已經有 LockRequest / ReserveRequest DTO，可以改成使用自己的 DTO。
     */
    @Data
    public static class ReasonBody {
        /**
         * 操作原因（可選）
         */
        private String reason;
        /**
         * 操作者（可選；若要打進 service，可自定義）
         */
        private String operator;
    }
}
