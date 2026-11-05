package com.bibliometria.api.controller;

import com.bibliometria.api.dto.ClusteringRequest;
import com.bibliometria.api.dto.ClusteringResponse;
import com.bibliometria.api.service.BibliometricAnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clustering")
public class ClusteringController {

    private final BibliometricAnalysisService analysisService;

    public ClusteringController(BibliometricAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ClusteringResponse cluster(@Valid @RequestBody ClusteringRequest request) {
        return analysisService.cluster(request);
    }
}
