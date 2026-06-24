package com.czkuo.rdf88701.presentation.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public class GripperListItemDto {
    @JsonProperty("id")   public int id;
    @JsonProperty("name") public String name;

    public GripperListItemDto() {}
    public GripperListItemDto(int id, String name) { this.id = id; this.name = name; }
}
