package com.bibliometria.core.text;

/** Metadatos de artículo usados como entrada del preprocesamiento textual. */
public record Article(
    String id, String title, String authors, String abstractText, boolean verified) {

  public Article {
    if (id == null || title == null || authors == null || abstractText == null) {
      throw new IllegalArgumentException("Los campos de Article no pueden ser null");
    }
  }
}
