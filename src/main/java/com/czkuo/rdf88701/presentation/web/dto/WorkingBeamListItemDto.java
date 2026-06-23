package com.czkuo.rdf88701.presentation.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkingBeamListItemDto {
    @JsonProperty("id")   public int id;
    @JsonProperty("name") public String name;

    public WorkingBeamListItemDto() {}
    public WorkingBeamListItemDto(int id, String name) { this.id = id; this.name = name; }
}
