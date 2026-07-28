package com.courseqa.service;

import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.repository.CourseDocumentRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Pure research inference. It deliberately does not create chat sessions,
 * messages, retrieval audit rows, or answer-citation entities.
 */
@Service
public class BenchmarkInferenceService {
    private static final String OUT_OF_SCOPE_MESSAGE =
            "Xin lỗi, mình không tìm thấy nội dung liên quan trong tài liệu của workspace này.";

    private final RetrievalService retrievalService;
    private final AIClientService aiClientService;
    private final CourseDocumentRepository documents;
    private final QuestionScopeGuard scopeGuard;

    public BenchmarkInferenceService(
            RetrievalService retrievalService,
            AIClientService aiClientService,
            CourseDocumentRepository documents,
            QuestionScopeGuard scopeGuard) {
        this.retrievalService = retrievalService;
        this.aiClientService = aiClientService;
        this.documents = documents;
        this.scopeGuard = scopeGuard;
    }

    public List<ChatDto.AskResponse> answerBatch(
            BenchmarkScope scope,
            List<BenchmarkQuestion> questions,
            String requestedMode,
            boolean allowUnverifiedModel) {
        if (questions == null || questions.isEmpty()) return List.of();
        return "FINE_TUNED".equalsIgnoreCase(requestedMode)
                ? answerFineTuned(scope, questions, allowUnverifiedModel)
                : answerRag(scope, questions);
    }

    private List<ChatDto.AskResponse> answerRag(
            BenchmarkScope scope,
            List<BenchmarkQuestion> questions) {
        List<RagPrepared> prepared = questions.stream().map(item -> prepareRag(scope, item)).toList();
        PythonAiDto.GenerateBatchRequest request = new PythonAiDto.GenerateBatchRequest();
        request.strict = true;
        request.items = prepared.stream()
                .filter(item -> item.guard().allowed()
                        && item.retrieval() != null
                        && Boolean.TRUE.equals(item.retrieval().answerable)
                        && item.retrieval().results != null
                        && !item.retrieval().results.isEmpty())
                .map(item -> {
                    PythonAiDto.GenerateBatchItem batch = new PythonAiDto.GenerateBatchItem();
                    batch.request_id = item.question().evaluationQuestionId().toString();
                    batch.question = item.question().question();
                    batch.contexts = item.retrieval().results.stream().map(this::toContext).toList();
                    batch.standalone_query = item.standaloneQuery();
                    batch.history = List.of();
                    batch.answer_profile = item.profile().answerProfile();
                    return batch;
                })
                .toList();

        Map<String, PythonAiDto.GenerateBatchResult> generatedById = new HashMap<>();
        if (!request.items.isEmpty()) {
            PythonAiDto.GenerateBatchResponse generated = aiClientService.callGenerateBatch(request);
            if (generated != null && generated.items != null) {
                generated.items.forEach(item -> generatedById.put(item.request_id, item));
            }
        }

        List<ChatDto.AskResponse> responses = new ArrayList<>();
        for (RagPrepared item : prepared) {
            if (!item.guard().allowed() || item.retrieval() == null
                    || !Boolean.TRUE.equals(item.retrieval().answerable)
                    || item.retrieval().results == null || item.retrieval().results.isEmpty()) {
                responses.add(noEvidenceResponse(item));
                continue;
            }
            String requestId = item.question().evaluationQuestionId().toString();
            PythonAiDto.GenerateBatchResult generated = generatedById.get(requestId);
            if (generated == null || generated.error != null) {
                throw new IllegalStateException("RAG batch did not return a valid answer for " + requestId);
            }
            String answer = Boolean.TRUE.equals(generated.is_out_of_scope)
                    ? OUT_OF_SCOPE_MESSAGE : firstNonBlank(generated.answer, OUT_OF_SCOPE_MESSAGE);
            ChatDto.AskResponse response = new ChatDto.AskResponse(
                    null, null, null, answer, "RAG",
                    firstNonBlank(generated.base_model, item.retrieval().embeddingModelName),
                    firstNonBlank(generated.generation_mode, "BASE_RAG"),
                    null,
                    OUT_OF_SCOPE_MESSAGE.equals(answer)
                            ? List.of()
                            : directCitations(item.retrieval().results, generated.used_chunk_ids));
            applyMetadata(response, generated);
            responses.add(response);
        }
        return responses;
    }

    private List<ChatDto.AskResponse> answerFineTuned(
            BenchmarkScope scope,
            List<BenchmarkQuestion> questions,
            boolean allowUnverifiedModel) {
        List<String> filenames = documents.findAllById(scope.documentIds()).stream()
                .map(CourseDocument::getOriginalFilename)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        PythonAiDto.ChatFinetunedBatchRequest request = new PythonAiDto.ChatFinetunedBatchRequest();
        request.strict = true;
        request.allow_unverified = allowUnverifiedModel;
        request.items = questions.stream()
                .filter(item -> scopeGuard.preCheck(item.question()).allowed())
                .map(item -> {
                    PythonAiDto.ChatFinetunedBatchItem batch = new PythonAiDto.ChatFinetunedBatchItem();
                    batch.request_id = item.evaluationQuestionId().toString();
                    batch.question = item.question();
                    batch.document_filenames = filenames;
                    return batch;
                })
                .toList();

        Map<String, PythonAiDto.ChatFinetunedBatchResult> generatedById = new HashMap<>();
        if (!request.items.isEmpty()) {
            PythonAiDto.ChatFinetunedBatchResponse generated =
                    aiClientService.callChatFinetunedBatch(request);
            if (generated != null && generated.items != null) {
                generated.items.forEach(item -> generatedById.put(item.request_id, item));
            }
        }

        List<ChatDto.AskResponse> responses = new ArrayList<>();
        for (BenchmarkQuestion question : questions) {
            QuestionScopeGuard.GuardDecision guard = scopeGuard.preCheck(question.question());
            if (!guard.allowed()) {
                responses.add(new ChatDto.AskResponse(
                        null, null, null, guard.message(), "FINE_TUNED", "scope-guard",
                        "SCOPE_GUARD", null, List.of()));
                continue;
            }
            String requestId = question.evaluationQuestionId().toString();
            PythonAiDto.ChatFinetunedBatchResult generated = generatedById.get(requestId);
            if (generated == null || generated.error != null
                    || generated.answer == null || generated.answer.isBlank()) {
                throw new IllegalStateException(
                        "Fine-tuned batch did not return a valid answer for " + requestId);
            }
            ChatDto.AskResponse response = new ChatDto.AskResponse(
                    null, null, null, generated.answer, "FINE_TUNED",
                    firstNonBlank(generated.base_model, "unknown"),
                    firstNonBlank(generated.generation_mode, "FINE_TUNED_ONLY"),
                    null, List.of());
            response.providerUsed = generated.provider_used;
            response.baseModel = generated.base_model;
            response.adapterVersion = generated.adapter_version;
            response.datasetVersion = generated.dataset_version;
            response.promptVersion = generated.prompt_version;
            response.peakVramBytes = generated.peak_vram_bytes;
            response.modelVerificationStatus = generated.verification_status;
            response.qualityGatePassed = generated.quality_gate_passed;
            responses.add(response);
        }
        return responses;
    }

    private RagPrepared prepareRag(BenchmarkScope scope, BenchmarkQuestion question) {
        QuestionScopeGuard.GuardDecision guard = scopeGuard.preCheck(question.question());
        RetrievalProfile profile = profile(QuestionIntentAnalyzer.analyze(question.question()));
        if (!guard.allowed()) {
            return new RagPrepared(question, question.question(), profile, null, guard);
        }
        RagDto.RetrievalResponse first = retrieve(scope, question.question(), profile.initialTopK());
        RagDto.RetrievalResponse retrieval = limit(first, profile.finalTopK());
        String standalone = question.question();
        if (weak(first)) {
            String corrected = rewrite(question.question(), profile.answerProfile(), first);
            RagDto.RetrievalResponse second = retrieve(scope, corrected, profile.initialTopK());
            retrieval = merge(first, second, profile.finalTopK());
            standalone = corrected;
        }
        guard = scopeGuard.postRetrievalCheck(question.question(), retrieval);
        return new RagPrepared(question, standalone, profile, retrieval, guard);
    }

    private RagDto.RetrievalResponse retrieve(
            BenchmarkScope scope, String query, int topK) {
        RagDto.RetrievalRequest request = new RagDto.RetrievalRequest();
        request.chatSessionId = null;
        request.userMessageId = null;
        request.workspaceId = scope.workspaceId();
        request.documentIds = scope.documentIds();
        request.semesterId = scope.semesterId();
        request.scopeType = "DOCUMENTS";
        request.originalQueryText = query;
        request.queryText = query;
        request.embeddingModelId = scope.embeddingModelId();
        request.topK = topK;
        request.similarityThreshold = retrievalService.getConfiguredSimilarityThreshold();
        return retrievalService.retrieve(request);
    }

    private String rewrite(String question, String intent, RagDto.RetrievalResponse retrieval) {
        PythonAiDto.RewriteQueryRequest request = new PythonAiDto.RewriteQueryRequest();
        request.question = question;
        request.history = List.of();
        request.intent = intent;
        request.attempt = 2;
        request.evidence_hints = retrieval == null || retrieval.results == null
                ? List.of()
                : retrieval.results.stream().limit(2)
                        .map(item -> firstNonBlank(item.content, ""))
                        .toList();
        try {
            PythonAiDto.RewriteQueryResponse response = aiClientService.callRewriteQuery(request);
            return response == null ? question : firstNonBlank(response.standalone_query, question);
        } catch (RuntimeException exception) {
            return question;
        }
    }

    private ChatDto.AskResponse noEvidenceResponse(RagPrepared item) {
        String answer = item.guard().allowed()
                ? (item.retrieval() == null
                        ? OUT_OF_SCOPE_MESSAGE
                        : firstNonBlank(item.retrieval().noAnswerReason, OUT_OF_SCOPE_MESSAGE))
                : item.guard().message();
        return new ChatDto.AskResponse(
                null, null, null, answer, "RAG",
                item.retrieval() == null ? "scope-guard" : item.retrieval().embeddingModelName,
                item.guard().allowed() ? "OUT_OF_SCOPE" : "SCOPE_GUARD",
                null, List.of());
    }

    private List<ChatDto.CitationItem> directCitations(
            List<RagDto.RetrievedChunk> chunks,
            List<String> usedChunkIds) {
        Set<String> used = new LinkedHashSet<>(usedChunkIds == null ? List.of() : usedChunkIds);
        Map<UUID, RagDto.RetrievedChunk> unique = new LinkedHashMap<>();
        chunks.stream()
                .filter(chunk -> chunk.chunkId != null && used.contains(chunk.chunkId.toString()))
                .forEach(chunk -> unique.putIfAbsent(chunk.chunkId, chunk));
        return unique.values().stream().map(chunk -> {
            ChatDto.CitationItem citation = new ChatDto.CitationItem(
                    null, null, null, chunk.chunkId, chunk.documentId,
                    chunk.documentTitle, chunk.pageStart, chunk.pageEnd, chunk.content);
            citation.retrievalScore = chunk.similarityScore;
            return citation;
        }).toList();
    }

    private void applyMetadata(
            ChatDto.AskResponse response,
            PythonAiDto.GenerateBatchResult generated) {
        response.providerUsed = generated.provider_used;
        response.baseModel = generated.base_model;
        response.adapterVersion = generated.adapter_version;
        response.embeddingModel = generated.embedding_model;
        response.datasetVersion = generated.dataset_version;
        response.promptVersion = generated.prompt_version;
        response.usedChunkIds = generated.used_chunk_ids;
        response.peakVramBytes = generated.peak_vram_bytes;
        response.groundingStatus = generated.grounding_status;
        response.fallbackReason = generated.fallback_reason;
        response.groundingScore = generated.grounding_score;
        response.repairAttempted = generated.repair_attempted;
        response.unsupportedSentenceCount = generated.unsupported_sentence_count;
    }

    private PythonAiDto.GenerateContext toContext(RagDto.RetrievedChunk chunk) {
        PythonAiDto.GenerateContext context = new PythonAiDto.GenerateContext();
        context.chunk_id = chunk.chunkId == null ? null : chunk.chunkId.toString();
        context.document_id = chunk.documentId == null ? null : chunk.documentId.toString();
        context.filename = firstNonBlank(chunk.filename, chunk.documentTitle);
        context.page = chunk.pageStart;
        context.content = chunk.content;
        context.score = chunk.similarityScore;
        return context;
    }

    private boolean weak(RagDto.RetrievalResponse response) {
        if (response == null || response.results == null || response.results.size() < 2) return true;
        Double top = response.results.get(0).similarityScore;
        return top == null || top < Math.max(
                retrievalService.getConfiguredSimilarityThreshold() + 0.05, 0.30);
    }

    private RagDto.RetrievalResponse limit(RagDto.RetrievalResponse source, int limit) {
        if (source == null || source.results == null || source.results.size() <= limit) return source;
        RagDto.RetrievalResponse result = copy(source);
        result.results = source.results.stream().limit(limit).toList();
        result.answerable = !result.results.isEmpty();
        return result;
    }

    private RagDto.RetrievalResponse merge(
            RagDto.RetrievalResponse first,
            RagDto.RetrievalResponse second,
            int limit) {
        List<RagDto.RetrievedChunk> candidates = new ArrayList<>();
        if (first != null && first.results != null) candidates.addAll(first.results);
        if (second != null && second.results != null) candidates.addAll(second.results);
        candidates.sort(Comparator.comparingDouble(
                item -> item.similarityScore == null ? 0.0 : -item.similarityScore));
        Map<UUID, RagDto.RetrievedChunk> byId = new LinkedHashMap<>();
        Set<String> content = new LinkedHashSet<>();
        for (RagDto.RetrievedChunk candidate : candidates) {
            String signature = normalize(firstNonBlank(candidate.content, ""));
            signature = signature.substring(0, Math.min(350, signature.length()));
            if (candidate.chunkId != null && (signature.isBlank() || content.add(signature))) {
                byId.putIfAbsent(candidate.chunkId, candidate);
            }
        }
        RagDto.RetrievalResponse result = copy(
                second != null && second.results != null && !second.results.isEmpty() ? second : first);
        result.results = byId.values().stream().limit(limit).toList();
        result.answerable = !result.results.isEmpty();
        result.noAnswerReason = result.answerable ? null : OUT_OF_SCOPE_MESSAGE;
        return result;
    }

    private RagDto.RetrievalResponse copy(RagDto.RetrievalResponse source) {
        RagDto.RetrievalResponse result = new RagDto.RetrievalResponse();
        if (source == null) {
            result.results = List.of();
            result.answerable = false;
            return result;
        }
        result.embeddingModelId = source.embeddingModelId;
        result.embeddingModelName = source.embeddingModelName;
        result.answerable = source.answerable;
        result.noAnswerReason = source.noAnswerReason;
        result.results = source.results == null ? List.of() : source.results;
        return result;
    }

    private RetrievalProfile profile(QuestionIntentAnalyzer.QueryIntent intent) {
        if (intent.summary() || intent.hasSection()) return new RetrievalProfile(20, 12, "summary");
        return switch (intent.form()) {
            case COMPARISON -> new RetrievalProfile(12, 8, "comparison");
            case LIST -> new RetrievalProfile(12, 8, "list");
            case REASONING -> new RetrievalProfile(12, 8, "reasoning");
            case PROCEDURE -> new RetrievalProfile(12, 8, "procedure");
            case DEFINITION -> new RetrievalProfile(8, 5, "definition");
            default -> new RetrievalProfile(8, 5, "factual");
        };
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record BenchmarkScope(
            UUID workspaceId,
            UUID semesterId,
            List<UUID> documentIds,
            UUID embeddingModelId) { }

    public record BenchmarkQuestion(UUID evaluationQuestionId, String question) { }

    private record RetrievalProfile(int initialTopK, int finalTopK, String answerProfile) { }

    private record RagPrepared(
            BenchmarkQuestion question,
            String standaloneQuery,
            RetrievalProfile profile,
            RagDto.RetrievalResponse retrieval,
            QuestionScopeGuard.GuardDecision guard) { }
}
