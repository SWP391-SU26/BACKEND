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
        document.setIndexingStatus("INDEXED");

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

        when(chunks.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(any())).thenReturn(List.of(chunk));
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

        when(chunks.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(generic, definition));
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any()))
                .thenReturn(List.of(genericEmbedding, definitionEmbedding));
        when(embeddingService.parseJsonVector(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value.startsWith("[0.70") ? new double[] {0.70, 0.0} : new double[] {0.64, 0.0};
        });
        when(embeddingService.cosineVectorScore(any(), any())).thenAnswer(invocation ->
                ((double[]) invocation.getArgument(1))[0]);

        RagDto.RetrievalRequest request = request(
                "DOCUMENTS", "Vat chat theo quan diem cua Lenin la gi?");
        request.topK = 1;
        RagDto.RetrievalResponse response = service.retrieve(request);

        assertTrue(response.answerable);
        assertEquals(definitionChunkId, response.results.get(0).chunkId);
    }

    @Test
    void historicalOriginQuestionPrioritizesTheDirectOriginStatement() {
        UUID modelId = UUID.randomUUID();
        DocumentChunk distractor = chunk(
                5,
                "Triet hoc co dien Duc dat dinh cao voi Heghen vao the ky XIX.");
        DocumentChunk origin = chunk(
                2,
                "Triet hoc ra doi o ca phuong Dong va phuong Tay gan nhu cung mot thoi gian, "
                        + "khoang the ky VIII den VI truoc Cong nguyen, tai Trung Quoc, An Do va Hy Lap.");

        when(chunks.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(distractor, origin));
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any()))
                .thenReturn(List.of(
                        embedding(distractor, modelId, "[0.75,0]"),
                        embedding(origin, modelId, "[0.35,0]")
                ));
        when(embeddingService.parseJsonVector(anyString())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            double score = Double.parseDouble(json.substring(1, json.indexOf(',')));
            return new double[] {score, 0.0};
        });
        when(embeddingService.cosineVectorScore(any(), any())).thenAnswer(invocation ->
                ((double[]) invocation.getArgument(1))[0]);
        when(embeddingService.exactTokenOverlapScore(anyString(), anyString())).thenReturn(0.0);

        RagDto.RetrievalRequest request = request(
                "DOCUMENTS",
                "Triet hoc ra doi som nhat o dau?");
        request.topK = 1;
        RagDto.RetrievalResponse response = service.retrieve(request);

        assertTrue(response.answerable);
        assertEquals(origin.getChunkId(), response.results.get(0).chunkId);
    }

    @Test
    void focusedQuestionUsesHybridEvidenceAndDropsWeakSemanticMatches() {
        UUID modelId = UUID.randomUUID();
        DocumentChunk primary = chunk(
                10,
                "Co che cache invalidation hoat dong bang cach xoa ban ghi cu khi du lieu nguon thay doi.");
        DocumentChunk supporting = chunk(
                11,
                "Khi cache invalidation hoan tat, lan doc tiep theo nap lai du lieu moi tu nguon.");
        DocumentChunk unrelated = chunk(
                90,
                "Lich su nhom trien khai mo ta cac thanh vien va nhung cot moc cua du an.");
        List<DocumentChunk> candidates = List.of(primary, supporting, unrelated);
        when(chunks.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(any())).thenReturn(candidates);

        List<ChunkEmbedding> prepared = List.of(
                embedding(primary, modelId, "[0.70,0]"),
                embedding(supporting, modelId, "[0.75,0]"),
                embedding(unrelated, modelId, "[0.72,0]")
        );
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any())).thenReturn(prepared);
        when(embeddingService.parseJsonVector(anyString())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            double score = Double.parseDouble(json.substring(1, json.indexOf(',')));
            return new double[] {score, 0.0};
        });
        when(embeddingService.cosineVectorScore(any(), any())).thenAnswer(invocation ->
                ((double[]) invocation.getArgument(1))[0]);
        when(embeddingService.exactTokenOverlapScore(anyString(), anyString())).thenReturn(0.0);

        RagDto.RetrievalRequest request = request(
                "DOCUMENTS",
                "Co che cache invalidation hoat dong nhu the nao?");
        request.topK = 3;
        RagDto.RetrievalResponse response = service.retrieve(request);

        assertTrue(response.answerable);
        assertTrue(response.results.stream().anyMatch(item -> item.chunkId.equals(primary.getChunkId())));
        assertTrue(response.results.stream().anyMatch(item -> item.chunkId.equals(supporting.getChunkId())));
        assertFalse(response.results.stream().anyMatch(item -> item.chunkId.equals(unrelated.getChunkId())));
    }

    @Test
    void compoundDefinitionPrefersExactSubjectAndAdjacentComponents() {
        UUID modelId = UUID.randomUUID();
        DocumentChunk distractor = chunk(
                30,
                "Triet hoc An Do gom nhieu truong phai va cac van de ton giao khac nhau.");
        DocumentChunk definition = chunk(
                5,
                "Van de co ban cua triet hoc la van de quan he giua tu duy voi ton tai.");
        DocumentChunk components = chunk(
                6,
                "Van de co ban cua triet hoc co hai mat: cai nao co truoc va con nguoi co "
                        + "kha nang nhan thuc the gioi hay khong.");
        when(chunks.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(distractor, definition, components));
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any()))
                .thenReturn(List.of(
                        embedding(distractor, modelId, "[0.90,0]"),
                        embedding(definition, modelId, "[0.55,0]"),
                        embedding(components, modelId, "[0.52,0]")
                ));
        when(embeddingService.parseJsonVector(anyString())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            double score = Double.parseDouble(json.substring(1, json.indexOf(',')));
            return new double[] {score, 0.0};
        });
        when(embeddingService.cosineVectorScore(any(), any())).thenAnswer(invocation ->
                ((double[]) invocation.getArgument(1))[0]);
        when(embeddingService.exactTokenOverlapScore(anyString(), anyString())).thenReturn(0.0);

        RagDto.RetrievalRequest request = request(
                "DOCUMENTS",
                "Van de co ban cua triet hoc la gi va gom nhung mat nao?");
        request.originalQueryText = request.queryText;
        request.topK = 3;
        RagDto.RetrievalResponse response = service.retrieve(request);

        assertTrue(response.answerable);
        assertEquals(definition.getChunkId(), response.results.get(0).chunkId);
        assertTrue(response.results.stream()
                .anyMatch(item -> item.chunkId.equals(components.getChunkId())));
        assertFalse(response.results.stream()
                .anyMatch(item -> item.chunkId.equals(distractor.getChunkId())));
    }

    @Test
    void deepListQuestionSelectsEvidenceAcrossDifferentPages() {
        UUID modelId = UUID.randomUUID();
        DocumentChunk page19a = chunk(19, "Thuyet Am Duong va Ngu hanh giai thich su bien doi cua vu tru.");
        DocumentChunk page19b = chunk(19, "Thuyet Am Duong va Ngu hanh giai thich su bien doi cua vu tru.");
        DocumentChunk page21 = chunk(21, "Nho gia lay Nhan Nghia va cac van de chinh tri dao duc lam cot loi.");
        DocumentChunk page22 = chunk(22, "Dao gia do Lao Tu khoi xuong, de cao Dao va tu tuong Vo vi.");
        List<DocumentChunk> candidates = List.of(page19a, page19b, page21, page22);
        when(chunks.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(any())).thenReturn(candidates);

        List<ChunkEmbedding> prepared = candidates.stream().map(chunk -> {
            ChunkEmbedding embedding = new ChunkEmbedding();
            embedding.setChunkId(chunk.getChunkId());
            embedding.setEmbeddingModelId(modelId);
            embedding.setEmbeddingJson("[0.8,0]");
            return embedding;
        }).toList();
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any())).thenReturn(prepared);
        when(embeddingService.parseJsonVector(anyString())).thenReturn(new double[] {0.8, 0.0});
        when(embeddingService.cosineVectorScore(any(), any())).thenReturn(0.8);
        when(embeddingService.exactTokenOverlapScore(anyString(), anyString())).thenReturn(0.2);

        RagDto.RetrievalRequest request = request(
                "DOCUMENTS",
                "Trình bày đầy đủ một số học thuyết tiêu biểu của triết học Trung Hoa cổ, trung đại");
        request.topK = 3;
        RagDto.RetrievalResponse response = service.retrieve(request);

        assertTrue(response.answerable);
        assertEquals(List.of(19, 21, 22), response.results.stream()
                .map(item -> item.pageStart)
                .sorted()
                .toList());
    }

    @Test
    void deepReasoningQuestionUsesTheSectionContainingTheFullConcept() {
        UUID modelId = UUID.randomUUID();
        List<DocumentChunk> candidates = List.of(
                chunk(90, "Nguon goc cua y thuc gan voi bo oc nguoi va hoat dong thuc tien."),
                chunk(91, "Noi dung cua y thuc la su phan anh hien thuc khach quan."),
                chunk(92, "Vat chat co truoc, y thuc co sau; vat chat quyet dinh y thuc."),
                chunk(93, "Y thuc tac dong tro lai vat chat thong qua hoat dong thuc tien."),
                chunk(94, "Dieu kien vat chat thay doi thi y thuc som muon thay doi theo."),
                chunk(109, "Ket cau vat chat quyet dinh ket qua cua mot qua trinh.")
        );
        when(chunks.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(any())).thenReturn(candidates);

        List<ChunkEmbedding> prepared = candidates.stream().map(candidate -> {
            ChunkEmbedding embedding = new ChunkEmbedding();
            embedding.setChunkId(candidate.getChunkId());
            embedding.setEmbeddingModelId(modelId);
            embedding.setEmbeddingJson("[0.8,0]");
            return embedding;
        }).toList();
        when(embeddings.findByEmbeddingModelIdAndChunkIdIn(any(), any())).thenReturn(prepared);
        when(embeddingService.parseJsonVector(anyString())).thenReturn(new double[] {0.8, 0.0});
        when(embeddingService.cosineVectorScore(any(), any())).thenReturn(0.8);
        when(embeddingService.exactTokenOverlapScore(anyString(), anyString())).thenReturn(0.2);

        RagDto.RetrievalRequest request = request(
                "DOCUMENTS",
                "Tai sao vat chat quyet dinh y thuc? Giai thich day du cac khia canh.");
        request.topK = 12;
        RagDto.RetrievalResponse response = service.retrieve(request);

        assertTrue(response.answerable);
        assertEquals(List.of(90, 91, 92, 93, 94), response.results.stream()
                .map(item -> item.pageStart)
                .sorted()
                .toList());
    }

    private DocumentChunk chunk(int page, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(page);
        chunk.setPageStart(page);
        chunk.setContent(content);
        return chunk;
    }

    private ChunkEmbedding embedding(DocumentChunk chunk, UUID modelId, String vector) {
        ChunkEmbedding embedding = new ChunkEmbedding();
        embedding.setChunkId(chunk.getChunkId());
        embedding.setEmbeddingModelId(modelId);
        embedding.setEmbeddingJson(vector);
        return embedding;
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
