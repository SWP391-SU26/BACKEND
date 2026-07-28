package com.courseqa.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.RagDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentEmbeddingIndexServiceTest {
    @Test
    void preparesOnlyTheUploadedDocument() {
        EmbeddingService embeddings = mock(EmbeddingService.class);
        RagDto.PrepareEmbeddingsResponse response = new RagDto.PrepareEmbeddingsResponse();
        when(embeddings.prepareEmbeddings(argThat(request -> request != null)))
                .thenReturn(response);

        UUID documentId = UUID.randomUUID();
        new DocumentEmbeddingIndexService(embeddings).prepareDocument(documentId);

        verify(embeddings).prepareEmbeddings(argThat(request ->
                documentId.equals(request.documentId) && request.workspaceId == null));
    }
}
