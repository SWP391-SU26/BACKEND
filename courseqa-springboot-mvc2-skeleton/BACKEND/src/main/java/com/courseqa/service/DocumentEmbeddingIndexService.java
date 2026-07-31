package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.repository.CourseDocumentRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentEmbeddingIndexService {
    private final EmbeddingService embeddingService;
    private final DocumentService documentService;
    private final CourseDocumentRepository documentRepository;

    public DocumentEmbeddingIndexService(
            EmbeddingService embeddingService,
            DocumentService documentService,
            CourseDocumentRepository documentRepository
    ) {
        this.embeddingService = embeddingService;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
    }

    @Async("documentIndexingTaskExecutor")
    public void prepareDocument(UUID documentId) {
        if (documentId == null) {
            return;
        }

        RagDto.PrepareEmbeddingsRequest request = new RagDto.PrepareEmbeddingsRequest();
        request.documentId = documentId;
        try {
            CourseDocument document = documentRepository.findById(documentId).orElseThrow();
            document.setIndexingStatus("EMBEDDING");
            document.setIndexError(null);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);

            documentService.ensureCanonicalChunks(documentId);
            EmbeddingModel model = embeddingService.resolveModel(null);
            RagDto.PrepareEmbeddingsResponse response = embeddingService.prepareEmbeddings(request);
            int prepared = response.createdEmbeddings + response.skippedExisting;
            if (response.totalChunks <= 0 || prepared != response.totalChunks) {
                throw new IllegalStateException(
                        "Indexed %d of %d chunks.".formatted(prepared, response.totalChunks));
            }
            document.setIndexingStatus("INDEXED");
            document.setIndexedEmbeddingModelId(response.embeddingModelId);
            document.setIndexedModelVersion(model.getModelName());
            document.setIndexedAt(LocalDateTime.now());
            document.setIndexError(null);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
            log.info(
                    "Semantic indexing completed for document {}: created={}, skipped={}, total={}",
                    documentId,
                    response.createdEmbeddings,
                    response.skippedExisting,
                    response.totalChunks
            );
        } catch (RuntimeException exception) {
            documentRepository.findById(documentId).ifPresent(document -> {
                document.setIndexingStatus("FAILED");
                document.setIndexError(exception.getMessage());
                document.setUpdatedAt(LocalDateTime.now());
                documentRepository.save(document);
            });
            log.error("Semantic indexing failed for document {}.", documentId, exception);
        }
    }
}
