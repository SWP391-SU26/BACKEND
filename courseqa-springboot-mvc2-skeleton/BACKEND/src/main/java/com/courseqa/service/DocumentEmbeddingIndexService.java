package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentEmbeddingIndexService {
    private final EmbeddingService embeddingService;

    public DocumentEmbeddingIndexService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Async
    public void prepareDocument(UUID documentId) {
        if (documentId == null) {
            return;
        }

        RagDto.PrepareEmbeddingsRequest request = new RagDto.PrepareEmbeddingsRequest();
        request.documentId = documentId;
        try {
            RagDto.PrepareEmbeddingsResponse response = embeddingService.prepareEmbeddings(request);
            log.info(
                    "Semantic indexing completed for document {}: created={}, skipped={}, total={}",
                    documentId,
                    response.createdEmbeddings,
                    response.skippedExisting,
                    response.totalChunks
            );
        } catch (RuntimeException exception) {
            log.error("Semantic indexing failed for document {}.", documentId, exception);
        }
    }
}
