package com.bibliometria.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank @Size(min = 1, max = 200) String query,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) {

    public SearchRequest {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }
}
