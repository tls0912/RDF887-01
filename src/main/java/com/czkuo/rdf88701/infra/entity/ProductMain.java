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
 * @since 2025-05-06
 */
@Getter
@Setter
@ToString
@TableName("product_main")
public class ProductMain {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 虛擬產品代號（系統唯一流水編號）
     */
    private String aliasCode;

    /**
     * 所屬容器主鍵（container_main.id）
     */
    private Long containerMainId;

    /**
     * 條碼
     */
    private String productCode;

    /**
     * 批號
     */
    private String lotNo;

    /**
     * 料號
     */
    private String partNo;

    private LocalDateTime createdTime;
}
