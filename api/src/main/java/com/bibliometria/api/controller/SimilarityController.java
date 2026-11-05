package com.bibliometria.api.controller;

import com.bibliometria.api.dto.SimilarityRequest;
import com.bibliometria.api.dto.SimilarityResponse;
import com.bibliometria.api.service.BibliometricAnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/similarity")
public class SimilarityController {

    private final BibliometricAnalysisService analysisService;

    public SimilarityController(BibliometricAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public SimilarityResponse calculate(@Valid @RequestBody SimilarityRequest request) {
        return analysisService.calculateSimilarity(request);
    }
}
