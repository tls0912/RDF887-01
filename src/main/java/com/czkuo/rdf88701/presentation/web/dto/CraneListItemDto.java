package com.czkuo.rdf88701.presentation.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CraneListItemDto {
    @JsonProperty("id")   public int id;
    @JsonProperty("name") public String name;

    public CraneListItemDto() {}
    public CraneListItemDto(int id, String name) { this.id = id; this.name = name; }
}
