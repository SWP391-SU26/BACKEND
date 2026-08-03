package com.courseqa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.repository.CourseDocumentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentEmbeddingIndexServiceTest {
    @Test
    void preparesOnlyTheUploadedDocument() {
        EmbeddingService embeddings = mock(EmbeddingService.class);
        DocumentService documents = mock(DocumentService.class);
        CourseDocumentRepository repository = mock(CourseDocumentRepository.class);
        RagDto.PrepareEmbeddingsResponse response = new RagDto.PrepareEmbeddingsResponse();
        response.totalChunks = 1;
        response.createdEmbeddings = 1;
        response.skippedExisting = 0;
        response.embeddingModelId = UUID.randomUUID();
        when(embeddings.prepareEmbeddings(argThat(request -> request != null), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);
        EmbeddingModel model = new EmbeddingModel();
        model.setModelName("BAAI/bge-m3");
        when(embeddings.resolveModel(null)).thenReturn(model);

        UUID documentId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        when(repository.findById(documentId)).thenReturn(Optional.of(document));
        new DocumentEmbeddingIndexService(embeddings, documents, repository)
                .prepareDocument(documentId);

        verify(documents).ensureCanonicalChunks(documentId);
        verify(embeddings).prepareEmbeddings(
                argThat(request -> documentId.equals(request.documentId) && request.workspaceId == null),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void partialEmbeddingIsReportedToTheCallerInsteadOfLookingLikeSuccess() {
        EmbeddingService embeddings = mock(EmbeddingService.class);
        DocumentService documents = mock(DocumentService.class);
        CourseDocumentRepository repository = mock(CourseDocumentRepository.class);
        RagDto.PrepareEmbeddingsResponse response = new RagDto.PrepareEmbeddingsResponse();
        response.totalChunks = 10;
        response.createdEmbeddings = 4;
        response.skippedExisting = 0;
        when(embeddings.prepareEmbeddings(argThat(request -> request != null), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);
        EmbeddingModel model = new EmbeddingModel();
        model.setModelName("BAAI/bge-m3");
        when(embeddings.resolveModel(null)).thenReturn(model);

        UUID documentId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        when(repository.findById(documentId)).thenReturn(Optional.of(document));
        DocumentEmbeddingIndexService service =
                new DocumentEmbeddingIndexService(embeddings, documents, repository);

        // Swallowing this let the processing job report COMPLETED, and let a
        // reindex activate a chunk version that was never fully embedded.
        assertThatThrownBy(() -> service.prepareDocument(documentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Indexed 4 of 10 chunks");
        assertThat(document.getIndexingStatus()).isEqualTo("FAILED");
    }
}
