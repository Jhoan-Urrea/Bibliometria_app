package com.bibliometria.api.controller;

import com.bibliometria.api.dto.SearchRequest;
import com.bibliometria.api.dto.SearchResponse;
import com.bibliometria.api.dto.SearchTraceResponse;
import com.bibliometria.api.service.BibliometricAnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@Validated
public class SearchController {

    private final BibliometricAnalysisService analysisService;

    public SearchController(BibliometricAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return analysisService.search(request);
    }

    @GetMapping("/trace")
    public SearchTraceResponse trace(
            @RequestParam("pattern") @NotBlank @Size(max = 200) String pattern,
            @RequestParam(value = "articleId", required = false) @Size(min = 1, max = 32)
                    String articleId) {
        return analysisService.searchTrace(pattern, articleId);
    }
}
