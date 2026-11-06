package com.bibliometria.api.service;

public class ArticleNotFoundException extends RuntimeException {

    public ArticleNotFoundException(String articleId) {
        super("No existe el artículo " + articleId + ".");
    }
}
