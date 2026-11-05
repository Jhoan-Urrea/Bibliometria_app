package com.bibliometria.api.controller;

import com.bibliometria.api.dto.ArticleDto;
import com.bibliometria.api.service.BibliometricAnalysisService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
public class ArticlesController {

    private final BibliometricAnalysisService analysisService;

    public ArticlesController(BibliometricAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping
    public List<ArticleDto> listArticles() {
        return analysisService.listArticles();
    }
}
