package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.ChunkEmbedding;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.repository.AnswerCitationRepository;
import com.courseqa.repository.ChunkEmbeddingRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.RetrievalQueryRepository;
import com.courseqa.repository.RetrievalResultRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrievalServiceDocumentScopeTest {
    private final DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    private final ChunkEmbeddingRepository embeddings = mock(ChunkEmbeddingRepository.class);
    private final CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private RetrievalService service;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        service = new RetrievalService(
                chunks,
                embeddings,
                mock(RetrievalQueryRepository.class),
                mock(RetrievalResultRepository.class),
                mock(AnswerCitationRepository.class),
                embeddingService,
                documents,
                new EmbeddingVectorCache()
        );

        documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();

        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setDocumentTitle("7. ST 1");
        document.setOriginalFilename("7. ST 1.pdf");
        document.setProcessingStatus("PROCESSED");

        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(chunkId);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(1);
        chunk.setPageStart(1);
        chunk.setContent("ことば Từ vựng");

        EmbeddingModel model = new EmbeddingModel();
        model.setEmbeddingModelId(modelId);
        model.setModelName("offline-test");
        model.setDimension(2);

        ChunkEmbedding embedding = new ChunkEmbedding();
        embedding.setChunkId(chunkId);
        embedding.setEmbeddingModelId(modelId);
        embedding.setEmbeddingJson("[0,1]");

        when(chunks.findByDocumentIdInOrderByCreatedAtAsc(any())).thenReturn(List.of(chunk));
        when(documents.findAllById(any())).thenReturn(List.of(document));
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any())).thenReturn(List.of(embedding));
        when(embeddingService.resolveModel(any())).thenReturn(model);
        when(embeddingService.embedText(anyString(), any(EmbeddingModel.class))).thenReturn(new double[] {1.0, 0.0});
        when(embeddingService.parseJsonVector(anyString())).thenReturn(new double[] {0.0, 1.0});
        when(embeddingService.cosineVectorScore(any(), any())).thenReturn(0.0);
        when(embeddingService.exactTokenOverlapScore(anyString(), anyString())).thenReturn(0.0);
    }

    @Test
    void selectedDocumentSummaryUsesOnlyTheSelectedDocumentEvenWithoutKeywordOverlap() {
        RagDto.RetrievalResponse response = service.retrieve(request("DOCUMENTS", "tóm tắt nội dung"));

        assertTrue(response.answerable);
        assertEquals(1, response.results.size());
        assertEquals(documentId, response.results.get(0).documentId);
    }

    @Test
    void selectedDocumentQuestionWithoutEvidenceIsRefused() {
        RagDto.RetrievalResponse response = service.retrieve(request("DOCUMENTS", "khái niệm không tồn tại"));

        assertFalse(response.answerable);
        assertTrue(response.results.isEmpty());
    }

    @Test
    void vocabularySectionRequestExpandsFromHeaderUntilNextSection() {
        // The focused unit fixture contains one Japanese chunk. This assertion
        // protects intent precedence: section selection must win over summary.
        RagDto.RetrievalResponse response = service.retrieve(request("DOCUMENTS", "tổng hợp từ vựng"));

        assertTrue(response.answerable);
        assertEquals(documentId, response.results.get(0).documentId);
    }

    @Test
    void definitionQuestionPrioritizesAnExplicitDefinitionOverGenericSemanticContent() {
        UUID genericChunkId = UUID.randomUUID();
        UUID definitionChunkId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();

        DocumentChunk generic = new DocumentChunk();
        generic.setChunkId(genericChunkId);
        generic.setDocumentId(documentId);
        generic.setChunkIndex(1);
        generic.setPageStart(78);
        generic.setContent("Tinh vat chat cua the gioi da duoc khoa hoc kiem nghiem.");

        DocumentChunk definition = new DocumentChunk();
        definition.setChunkId(definitionChunkId);
        definition.setDocumentId(documentId);
        definition.setChunkIndex(2);
        definition.setPageStart(81);
        definition.setContent("Dinh nghia vat chat: Vat chat la cai ton tai khach quan ben ngoai y thuc.");

        ChunkEmbedding genericEmbedding = new ChunkEmbedding();
        genericEmbedding.setChunkId(genericChunkId);
        genericEmbedding.setEmbeddingModelId(modelId);
        genericEmbedding.setEmbeddingJson("[0.70,0]");

        ChunkEmbedding definitionEmbedding = new ChunkEmbedding();
        definitionEmbedding.setChunkId(definitionChunkId);
        definitionEmbedding.setEmbeddingModelId(modelId);
        definitionEmbedding.setEmbeddingJson("[0.64,0]");

        when(chunks.findByDocumentIdInOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(generic, definition));
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any()))
                .thenReturn(List.of(genericEmbedding, definitionEmbedding));
        when(embeddingService.parseJsonVector(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value.startsWith("[0.70") ? new double[] {0.70, 0.0} : new double[] {0.64, 0.0};
        });
        when(embeddingService.cosineVectorScore(any(), any())).thenAnswer(invocation ->
                ((double[]) invocation.getArgument(1))[0]);

        RagDto.RetrievalRequest request = request("DOCUMENTS", "Vat chat la gi?");
        request.topK = 1;
        RagDto.RetrievalResponse response = service.retrieve(request);

        assertTrue(response.answerable);
        assertEquals(definitionChunkId, response.results.get(0).chunkId);
    }

    private RagDto.RetrievalRequest request(String scopeType, String question) {
        RagDto.RetrievalRequest request = new RagDto.RetrievalRequest();
        request.scopeType = scopeType;
        request.documentIds = List.of(documentId);
        request.queryText = question;
        request.topK = 5;
        request.similarityThreshold = 0.25;
        return request;
    }
}
