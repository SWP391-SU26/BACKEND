package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.dto.RagDto;
import com.courseqa.repository.CourseDocumentRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BenchmarkInferenceServiceTest {

    @Test
    void ragBenchmarkUsesEvaluationQuestionIdAndDoesNotCreateChatAuditIds() {
        RetrievalService retrieval = mock(RetrievalService.class);
        AIClientService ai = mock(AIClientService.class);
        CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
        QuestionScopeGuard guard = mock(QuestionScopeGuard.class);
        BenchmarkInferenceService service =
                new BenchmarkInferenceService(retrieval, ai, documents, guard);
        UUID questionId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(guard.preCheck(any())).thenReturn(QuestionScopeGuard.GuardDecision.allow());
        when(guard.postRetrievalCheck(any(), any()))
                .thenReturn(QuestionScopeGuard.GuardDecision.allow());
        when(retrieval.getConfiguredSimilarityThreshold()).thenReturn(0.25);
        AtomicReference<RagDto.RetrievalRequest> captured = new AtomicReference<>();
        when(retrieval.retrieve(any())).thenAnswer(invocation -> {
            RagDto.RetrievalRequest request = invocation.getArgument(0);
            captured.set(request);
            RagDto.RetrievedChunk chunk = new RagDto.RetrievedChunk();
            chunk.chunkId = chunkId;
            chunk.documentId = documentId;
            chunk.documentTitle = "Triết học";
            chunk.filename = "triethoc.pdf";
            chunk.pageStart = 81;
            chunk.pageEnd = 81;
            chunk.similarityScore = 0.9;
            chunk.content = "Vật chất có trước và quyết định ý thức.";
            RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
            response.answerable = true;
            response.embeddingModelName = "BAAI/bge-m3";
            response.results = List.of(chunk, chunk);
            return response;
        });
        when(ai.callGenerateBatch(any())).thenAnswer(invocation -> {
            PythonAiDto.GenerateBatchRequest request = invocation.getArgument(0);
            assertEquals(questionId.toString(), request.items.get(0).request_id);
            PythonAiDto.GenerateBatchResult result = new PythonAiDto.GenerateBatchResult();
            result.request_id = questionId.toString();
            result.answer = "Vật chất có trước nên giữ vai trò quyết định đối với ý thức.";
            result.provider_used = "local-base";
            result.base_model = "Qwen/Qwen2.5-1.5B-Instruct";
            result.generation_mode = "BASE_RAG";
            result.used_chunk_ids = List.of(chunkId.toString());
            PythonAiDto.GenerateBatchResponse response = new PythonAiDto.GenerateBatchResponse();
            response.items = List.of(result);
            return response;
        });

        List<ChatDto.AskResponse> answers = service.answerBatch(
                new BenchmarkInferenceService.BenchmarkScope(
                        UUID.randomUUID(), UUID.randomUUID(), List.of(documentId), null),
                List.of(new BenchmarkInferenceService.BenchmarkQuestion(
                        questionId, "Tại sao vật chất quyết định ý thức?")),
                "RAG",
                false);

        assertEquals(1, answers.size());
        assertNull(answers.get(0).chatSessionId);
        assertNull(answers.get(0).userMessageId);
        assertNull(answers.get(0).assistantMessageId);
        assertEquals(1, answers.get(0).citations.size());
        assertNull(captured.get().chatSessionId);
        assertNull(captured.get().userMessageId);
        verify(ai).callGenerateBatch(any());
    }
}
