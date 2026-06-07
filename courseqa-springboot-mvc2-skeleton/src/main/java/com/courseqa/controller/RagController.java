package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.RagDto;
import com.courseqa.service.EmbeddingService;
import com.courseqa.service.RetrievalService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin
public class RagController {
    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;

    public RagController(EmbeddingService embeddingService, RetrievalService retrievalService) {
        this.embeddingService = embeddingService;
        this.retrievalService = retrievalService;
    }

    @GetMapping("/embedding-models")
    public ApiResponse<List<RagDto.EmbeddingModelResponse>> getEmbeddingModels() {
        return ApiResponse.ok(embeddingService.getEmbeddingModels());
    }

    @PostMapping("/embedding-models")
    public ApiResponse<RagDto.EmbeddingModelResponse> createEmbeddingModel(
            @RequestBody RagDto.CreateEmbeddingModelRequest request
    ) {
        return ApiResponse.ok(embeddingService.createEmbeddingModel(request));
    }

    @PostMapping("/embeddings/prepare")
    public ApiResponse<RagDto.PrepareEmbeddingsResponse> prepareEmbeddings(
            @RequestBody RagDto.PrepareEmbeddingsRequest request
    ) {
        return ApiResponse.ok(embeddingService.prepareEmbeddings(request));
    }

    @PostMapping("/retrieve")
    public ApiResponse<RagDto.RetrievalResponse> retrieve(@RequestBody RagDto.RetrievalRequest request) {
        return ApiResponse.ok(retrievalService.retrieve(request));
    }

    @PostMapping("/retrieval-queries")
    public ApiResponse<RagDto.RetrievalResponse> createRetrievalQuery(@RequestBody RagDto.RetrievalRequest request) {
        return ApiResponse.ok(retrievalService.retrieve(request));
    }

    @GetMapping("/retrieval-queries")
    public ApiResponse<List<RagDto.RetrievalQueryResponse>> getRetrievalQueries(
            @RequestParam(required = false) UUID workspaceId
    ) {
        return ApiResponse.ok(retrievalService.getRetrievalQueries(workspaceId));
    }

    @GetMapping("/retrieval-results")
    public ApiResponse<List<RagDto.RetrievalResultResponse>> getRetrievalResults(
            @RequestParam(required = false) UUID retrievalQueryId
    ) {
        return ApiResponse.ok(retrievalService.getRetrievalResults(retrievalQueryId));
    }

    @GetMapping("/citations")
    public ApiResponse<List<RagDto.CitationResponse>> getCitations(
            @RequestParam(required = false) UUID assistantMessageId
    ) {
        return ApiResponse.ok(retrievalService.getCitations(assistantMessageId));
    }
}
