package com.bibliometria.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum LinkageDto {
    @JsonProperty("single")
    SINGLE,
    @JsonProperty("complete")
    COMPLETE,
    @JsonProperty("average")
    AVERAGE
}
