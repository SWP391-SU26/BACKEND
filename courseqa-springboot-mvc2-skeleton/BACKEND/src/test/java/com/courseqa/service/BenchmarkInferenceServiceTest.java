package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.CourseDocument;
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
        when(retrieval.retrieveBatch(any())).thenAnswer(invocation -> {
            List<RagDto.RetrievalRequest> requests = invocation.getArgument(0);
            RagDto.RetrievalRequest request = requests.get(0);
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
            return List.of(response);
        });
        when(ai.callGenerateBatch(any())).thenAnswer(invocation -> {
            PythonAiDto.GenerateBatchRequest request = invocation.getArgument(0);
            assertEquals(questionId.toString(), request.items.get(0).request_id);
            assertEquals("STANDARD", request.items.get(0).answer_depth);
            PythonAiDto.GenerateBatchResult result = new PythonAiDto.GenerateBatchResult();
            result.request_id = questionId.toString();
            result.answer = "Vật chất có trước nên giữ vai trò quyết định đối với ý thức.";
            result.provider_used = "local-base";
            result.base_model = "Qwen/Qwen2.5-1.5B-Instruct";
            result.generation_mode = "BASE_RAG";
            result.used_chunk_ids = List.of(chunkId.toString());
            PythonAiDto.GenerateBatchResponse response = new PythonAiDto.GenerateBatchResponse();
            response.items = List.of(result);
            response.effective_batch_size = 1;
            response.oom_fallback_count = 0;
            return response;
        });

        BenchmarkInferenceService.BenchmarkBatchResult batch = service.answerBatchWithTelemetry(
                new BenchmarkInferenceService.BenchmarkScope(
                        UUID.randomUUID(), UUID.randomUUID(), List.of(documentId), null),
                List.of(new BenchmarkInferenceService.BenchmarkQuestion(
                        questionId, "Tại sao vật chất quyết định ý thức?")),
                "RAG",
                false);
        List<ChatDto.AskResponse> answers = batch.answers();

        assertEquals(1, answers.size());
        assertEquals(1, batch.effectiveBatchSize());
        assertEquals(0, batch.oomFallbackCount());
        assertNull(answers.get(0).chatSessionId);
        assertNull(answers.get(0).userMessageId);
        assertNull(answers.get(0).assistantMessageId);
        assertEquals(1, answers.get(0).citations.size());
        assertNull(captured.get().chatSessionId);
        assertNull(captured.get().userMessageId);
        verify(ai).callGenerateBatch(any());
    }

    @Test
    void fineTunedBenchmarkRunsFrozenQuestionsWithoutTheInteractiveScopeGuard() {
        RetrievalService retrieval = mock(RetrievalService.class);
        AIClientService ai = mock(AIClientService.class);
        CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
        QuestionScopeGuard guard = mock(QuestionScopeGuard.class);
        BenchmarkInferenceService service =
                new BenchmarkInferenceService(retrieval, ai, documents, guard);
        UUID documentId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setOriginalFilename("triethocmaclenin.pdf");
        when(documents.findAllById(List.of(documentId))).thenReturn(List.of(document));
        when(guard.preCheck(any())).thenReturn(
                QuestionScopeGuard.GuardDecision.refuse("Interactive guard would reject this."));
        when(ai.callChatFinetunedBatch(any())).thenAnswer(invocation -> {
            PythonAiDto.ChatFinetunedBatchRequest request = invocation.getArgument(0);
            assertTrue(Boolean.TRUE.equals(request.benchmark_mode));
            assertEquals(1, request.items.size());
            assertEquals(List.of("triethocmaclenin.pdf"), request.items.get(0).document_filenames);
            PythonAiDto.ChatFinetunedBatchResult item =
                    new PythonAiDto.ChatFinetunedBatchResult();
            item.request_id = questionId.toString();
            item.answer = "Vật chất là thực tại khách quan.";
            item.is_out_of_scope = false;
            item.model_inference_executed = true;
            item.base_model = "Qwen/Qwen2.5-1.5B-Instruct";
            item.generation_mode = "FINE_TUNED_ONLY";
            PythonAiDto.ChatFinetunedBatchResponse response =
                    new PythonAiDto.ChatFinetunedBatchResponse();
            response.items = List.of(item);
            response.effective_batch_size = 1;
            response.oom_fallback_count = 0;
            return response;
        });

        BenchmarkInferenceService.BenchmarkBatchResult result = service.answerBatchWithTelemetry(
                new BenchmarkInferenceService.BenchmarkScope(
                        UUID.randomUUID(), UUID.randomUUID(), List.of(documentId), null),
                List.of(new BenchmarkInferenceService.BenchmarkQuestion(
                        questionId, "Định nghĩa vật chất của Lênin?")),
                "FINE_TUNED",
                true);

        assertEquals(1, result.effectiveBatchSize());
        assertEquals("Vật chất là thực tại khách quan.", result.answers().get(0).answer);
        verify(ai).callChatFinetunedBatch(any());
    }

    @Test
    void fineTunedBenchmarkRejectsAResponseThatNeverReachedModelInference() {
        RetrievalService retrieval = mock(RetrievalService.class);
        AIClientService ai = mock(AIClientService.class);
        CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
        QuestionScopeGuard guard = mock(QuestionScopeGuard.class);
        BenchmarkInferenceService service =
                new BenchmarkInferenceService(retrieval, ai, documents, guard);
        UUID documentId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setOriginalFilename("triethocmaclenin.pdf");
        when(documents.findAllById(List.of(documentId))).thenReturn(List.of(document));
        when(ai.callChatFinetunedBatch(any())).thenAnswer(invocation -> {
            PythonAiDto.ChatFinetunedBatchResult item =
                    new PythonAiDto.ChatFinetunedBatchResult();
            item.request_id = questionId.toString();
            item.answer = "Tôi chưa tìm thấy thông tin này trong tài liệu đã được huấn luyện.";
            item.is_out_of_scope = true;
            item.model_inference_executed = false;
            PythonAiDto.ChatFinetunedBatchResponse response =
                    new PythonAiDto.ChatFinetunedBatchResponse();
            response.items = List.of(item);
            response.effective_batch_size = 0;
            return response;
        });

        assertThrows(IllegalStateException.class, () -> service.answerBatchWithTelemetry(
                new BenchmarkInferenceService.BenchmarkScope(
                        UUID.randomUUID(), UUID.randomUUID(), List.of(documentId), null),
                List.of(new BenchmarkInferenceService.BenchmarkQuestion(
                        questionId, "Định nghĩa vật chất của Lênin?")),
                "FINE_TUNED",
                true));
    }

    @Test
    void fineTunedModelRefusalIsAValidBenchmarkOutcomeAfterInference() {
        RetrievalService retrieval = mock(RetrievalService.class);
        AIClientService ai = mock(AIClientService.class);
        CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
        QuestionScopeGuard guard = mock(QuestionScopeGuard.class);
        BenchmarkInferenceService service =
                new BenchmarkInferenceService(retrieval, ai, documents, guard);
        UUID documentId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setOriginalFilename("triethocmaclenin.pdf");
        when(documents.findAllById(List.of(documentId))).thenReturn(List.of(document));
        when(ai.callChatFinetunedBatch(any())).thenAnswer(invocation -> {
            PythonAiDto.ChatFinetunedBatchResult item =
                    new PythonAiDto.ChatFinetunedBatchResult();
            item.request_id = questionId.toString();
            item.answer = "Tôi chưa tìm thấy thông tin này trong tài liệu đã được huấn luyện.";
            item.is_out_of_scope = true;
            item.model_inference_executed = true;
            item.provider_used = "local-lora";
            PythonAiDto.ChatFinetunedBatchResponse response =
                    new PythonAiDto.ChatFinetunedBatchResponse();
            response.items = List.of(item);
            response.effective_batch_size = 1;
            return response;
        });

        BenchmarkInferenceService.BenchmarkBatchResult result = service.answerBatchWithTelemetry(
                new BenchmarkInferenceService.BenchmarkScope(
                        UUID.randomUUID(), UUID.randomUUID(), List.of(documentId), null),
                List.of(new BenchmarkInferenceService.BenchmarkQuestion(
                        questionId, "Câu hỏi mà model quyết định từ chối")),
                "FINE_TUNED",
                true);

        assertEquals(1, result.effectiveBatchSize());
        assertEquals(
                "Tôi chưa tìm thấy thông tin này trong tài liệu đã được huấn luyện.",
                result.answers().get(0).answer);
    }
}
