package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.repository.ChunkEmbeddingRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.EmbeddingModelRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

    @Test
    void sentenceTransformersUsesPythonSemanticEmbedding() {
        AIClientService aiClient = mock(AIClientService.class);
        PythonAiDto.EmbedResponse response = new PythonAiDto.EmbedResponse();
        response.dimension = 3;
        response.vectors = List.of(List.of(0.1, 0.2, 0.3));
        when(aiClient.callEmbed(any(PythonAiDto.EmbedRequest.class))).thenReturn(response);

        EmbeddingService service = new EmbeddingService(
                mock(EmbeddingModelRepository.class),
                mock(DocumentChunkRepository.class),
                mock(ChunkEmbeddingRepository.class),
                aiClient,
                new EmbeddingVectorCache());
        EmbeddingModel model = new EmbeddingModel();
        model.setProvider("sentence-transformers");
        model.setDimension(3);

        assertArrayEquals(new double[] {0.1, 0.2, 0.3}, service.embedText("triết học", model));
        verify(aiClient).callEmbed(any(PythonAiDto.EmbedRequest.class));
    }

    @Test
    void sentenceTransformersBatchesQueryEmbeddings() {
        AIClientService aiClient = mock(AIClientService.class);
        PythonAiDto.EmbedResponse response = new PythonAiDto.EmbedResponse();
        response.dimension = 2;
        response.vectors = List.of(
                List.of(0.1, 0.2),
                List.of(0.3, 0.4));
        when(aiClient.callEmbed(any(PythonAiDto.EmbedRequest.class))).thenReturn(response);
        EmbeddingService service = new EmbeddingService(
                mock(EmbeddingModelRepository.class),
                mock(DocumentChunkRepository.class),
                mock(ChunkEmbeddingRepository.class),
                aiClient,
                new EmbeddingVectorCache());
        EmbeddingModel model = new EmbeddingModel();
        model.setProvider("sentence-transformers");
        model.setDimension(2);

        List<double[]> vectors = service.embedTexts(List.of("q1", "q2"), model);

        assertEquals(2, vectors.size());
        assertArrayEquals(new double[] {0.1, 0.2}, vectors.get(0));
        assertArrayEquals(new double[] {0.3, 0.4}, vectors.get(1));
        verify(aiClient).callEmbed(any(PythonAiDto.EmbedRequest.class));
    }
}
