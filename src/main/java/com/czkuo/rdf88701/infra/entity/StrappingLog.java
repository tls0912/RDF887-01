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
 * 
 * </p>
 *
 * @author czkuo
 * @since 2025-10-02
 */
@Getter
@Setter
@ToString
@TableName("strapping_log")
public class StrappingLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 打帶機位置：1/2/3
     */
    private Byte machinePos;

    private String machineId;

    /**
     * PLC 流水號 index（每次+1）
     */
    private Integer seqIndex;

    /**
     * seq_index 重滾輪次（軟體偵測）
     */
    private Integer seqEpoch;

    /**
     * 由 25 words 解碼出的字串（≤50字元）
     */
    private String productId;

    /**
     * 1=OK, 2=NG
     */
    private Byte result;

    /**
     * 原始 Strapping Position（=機台號 1/2/3）
     */
    private Byte strappingPos;

    /**
     * PLC 時間戳 (YYMM/DDhh/mmss)
     */
    private LocalDateTime eventTime;

    private LocalDateTime createdTime;
}
