package com.bibliometria.api.dto;

public record ArticleDto(
        String id, String title, String authors, String abstractText, boolean verified) {}
