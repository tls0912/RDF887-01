package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 影像外部儲存索引表（S072/S073 等影像引用）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-28
 */
@Getter
@Setter
@ToString
@TableName("image_asset")
public class ImageAsset {

    /**
     * 主鍵 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 來源場景（如 S072/S073）
     */
    private String scene;

    /**
     * 關聯型別：MESSAGE=對應 mqtt_message_log，EVENT=對應 mqtt_event_log，SESSION=其他用途
     */
    private String refType;

    /**
     * 關聯主鍵（mqtt_message_log.id 或 mqtt_event_log.id 等）
     */
    private Long refId;

    /**
     * 角色：raw1/raw2/crop1/roi/thumb 等
     */
    private String role;

    /**
     * 實際儲存路徑（如 file:///data/ocr/... 或 minio://bucket/...）
     */
    private String storageUrl;

    /**
     * 檔案 MIME 類型（預設 image/jpeg）
     */
    private String mime;

    /**
     * 檔案大小（Byte）
     */
    private Integer bytes;

    /**
     * 影像寬度（像素）
     */
    private Integer width;

    /**
     * 影像高度（像素）
     */
    private Integer height;

    /**
     * 影像 SHA-256 校驗碼（判重/驗證用）
     */
    private String sha256;

    /**
     * 影像拍攝時間（若有）
     */
    private LocalDateTime capturedAt;

    /**
     * 保留天數，排程用於自動清理
     */
    private Integer retentionDays;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 更新時間
     */
    private LocalDateTime updatedTime;
}
