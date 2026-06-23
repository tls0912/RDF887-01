package com.czkuo.rdf88701.presentation.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GripperListItemDto {
    @JsonProperty("id")   public int id;
    @JsonProperty("name") public String name;

    public GripperListItemDto() {}
    public GripperListItemDto(int id, String name) { this.id = id; this.name = name; }
}
