package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.AnswerCitation;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.model.entity.RetrievalQuery;
import com.courseqa.model.entity.RetrievalResult;
import com.courseqa.repository.AnswerCitationRepository;
import com.courseqa.repository.ChunkEmbeddingRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.RetrievalQueryRepository;
import com.courseqa.repository.RetrievalResultRepository;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RetrievalService {
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.25;
    private static final double STRONG_MATCH_THRESHOLD = 0.75;
    private static final double DOCUMENT_REFERENCE_THRESHOLD = 0.85;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern DOTTED_NUMBER_REFERENCE_PATTERN = Pattern.compile("\\d+\\s*[._-]\\s*\\d+");

    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;
    private final RetrievalQueryRepository retrievalQueryRepository;
    private final RetrievalResultRepository retrievalResultRepository;
    private final AnswerCitationRepository answerCitationRepository;
    private final EmbeddingService embeddingService;
    private final CourseDocumentRepository courseDocumentRepository;
    private final EmbeddingVectorCache vectorCache;

    @Value("${rag.similarity-threshold:0.25}")
    private double configuredSimilarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

    public RetrievalService(
            DocumentChunkRepository documentChunkRepository,
            ChunkEmbeddingRepository chunkEmbeddingRepository,
            RetrievalQueryRepository retrievalQueryRepository,
            RetrievalResultRepository retrievalResultRepository,
            AnswerCitationRepository answerCitationRepository,
            EmbeddingService embeddingService,
            CourseDocumentRepository courseDocumentRepository,
            EmbeddingVectorCache vectorCache
    ) {
        this.documentChunkRepository = documentChunkRepository;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
        this.retrievalQueryRepository = retrievalQueryRepository;
        this.retrievalResultRepository = retrievalResultRepository;
        this.answerCitationRepository = answerCitationRepository;
        this.embeddingService = embeddingService;
        this.courseDocumentRepository = courseDocumentRepository;
        this.vectorCache = vectorCache;
    }

    @Transactional
    public RagDto.RetrievalResponse retrieve(RagDto.RetrievalRequest request) {
        validateRetrievalRequest(request);
        repairRetrievalText(request);
        EmbeddingModel model = embeddingService.resolveModel(request.embeddingModelId);
        double[] queryVector = embeddingService.embedText(request.queryText, model);
        return retrievePrepared(request, model, queryVector);
    }

    @Transactional
    public List<RagDto.RetrievalResponse> retrieveBatch(List<RagDto.RetrievalRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        requests.forEach(this::validateRetrievalRequest);
        requests.forEach(this::repairRetrievalText);
        EmbeddingModel model = embeddingService.resolveModel(requests.get(0).embeddingModelId);
        boolean sameModel = requests.stream().allMatch(request ->
                request.embeddingModelId == null
                        || model.getEmbeddingModelId().equals(request.embeddingModelId));
        if (!sameModel) {
            return requests.stream().map(this::retrieve).toList();
        }
        List<double[]> queryVectors = embeddingService.embedTexts(
                requests.stream().map(request -> request.queryText).toList(),
                model);
        List<RagDto.RetrievalResponse> responses = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            responses.add(retrievePrepared(requests.get(index), model, queryVectors.get(index)));
        }
        return responses;
    }

    private void repairRetrievalText(RagDto.RetrievalRequest request) {
        request.queryText = QuestionIntentAnalyzer.repairUtf8Mojibake(request.queryText);
        request.originalQueryText =
                QuestionIntentAnalyzer.repairUtf8Mojibake(request.originalQueryText);
    }

    private RagDto.RetrievalResponse retrievePrepared(
            RagDto.RetrievalRequest request,
            EmbeddingModel model,
            double[] queryVector) {
        Instant startedAt = Instant.now();
        int topK = request.topK == null || request.topK <= 0 ? 5 : Math.min(request.topK, 40);
        double threshold = request.similarityThreshold == null
                ? configuredSimilarityThreshold : request.similarityThreshold;

        List<DocumentChunk> workspaceChunks = resolveCandidateChunks(request);
        if (workspaceChunks.isEmpty()) {
            return emptyRetrievalResponse(model);
        }
        QuestionIntentAnalyzer.QueryIntent rewrittenIntent =
                QuestionIntentAnalyzer.analyze(request.queryText);
        QuestionIntentAnalyzer.QueryIntent originalIntent =
                QuestionIntentAnalyzer.analyze(request.originalQueryText);
        QuestionIntentAnalyzer.QueryIntent intent =
                request.originalQueryText != null
                        && originalIntent.form() != QuestionIntentAnalyzer.QuestionForm.FACT
                        ? originalIntent
                        : rewrittenIntent;
        String evidenceQuery = request.originalQueryText != null
                && !request.originalQueryText.isBlank()
                && originalIntent.form() != QuestionIntentAnalyzer.QuestionForm.FACT
                ? request.originalQueryText
                : request.queryText;

        Map<UUID, double[]> vectorsByChunkId = loadVectorsByChunkId(model, workspaceChunks);

        if (vectorsByChunkId.isEmpty()) {
            return noPreparedEmbeddingsResponse(model);
        }

        Map<UUID, CourseDocument> documentsById = loadDocumentsById(workspaceChunks);
        Map<UUID, double[]> preparedVectorsByChunkId = vectorsByChunkId;
        List<ScoredChunk> allScoredCandidates = workspaceChunks.stream()
                .map(chunk -> scoreChunk(
                        chunk,
                        preparedVectorsByChunkId.get(chunk.getChunkId()),
                        queryVector,
                        evidenceQuery,
                        documentsById.get(chunk.getDocumentId()),
                        intent)
                )
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing((ScoredChunk scoredChunk) -> nullToMax(scoredChunk.chunk().getPageStart()))
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                .toList();
        List<ScoredChunk> scoredCandidates = allScoredCandidates.stream()
                .filter(scoredChunk -> scoredChunk.score() >= threshold)
                .toList();

        // A generic summary request such as "tóm tắt nội dung" has no lexical
        // overlap with a Japanese document. When the user explicitly selected
        // the document scope, the selection itself is the retrieval constraint,
        // so summarize representative chunks from those documents instead of
        // falling through to unrelated course material or returning zero chunks.
        boolean selectedDocumentSection = isSelectedDocumentScope(request) && intent.hasSection();
        boolean selectedDocumentSummary = isSelectedDocumentScope(request) && intent.summary() && !intent.hasSection();

        boolean hasDocumentReferenceMatch = scoredCandidates.stream()
                .anyMatch(scoredChunk -> scoredChunk.documentReferenceScore() >= DOCUMENT_REFERENCE_THRESHOLD);
        boolean hasExplicitDocumentReference = hasExplicitDocumentReference(request.queryText);
        String noAnswerReason = null;
        List<ScoredChunk> filteredCandidates;
        if (selectedDocumentSection) {
            filteredCandidates = selectSectionChunks(allScoredCandidates, request.queryText, topK);
            if (filteredCandidates.isEmpty()) {
                noAnswerReason = "Không tìm thấy phần nội dung được yêu cầu trong tài liệu đã chọn.";
            }
        } else if (selectedDocumentSummary) {
            filteredCandidates = allScoredCandidates;
        } else if (hasExplicitDocumentReference && !hasDocumentReferenceMatch) {
            noAnswerReason = "Không tìm thấy tài liệu phù hợp với mã hoặc tên bạn nhập trong workspace.";
            filteredCandidates = List.of();
        } else if (hasDocumentReferenceMatch) {
            List<ScoredChunk> documentCandidates = scoredCandidates.stream()
                    .filter(scoredChunk -> scoredChunk.documentReferenceScore() >= DOCUMENT_REFERENCE_THRESHOLD)
                    .toList();
            if (isSummaryQuestion(request.queryText)) {
                filteredCandidates = documentCandidates;
            } else if (isSectionQuestion(request.queryText)) {
                filteredCandidates = selectSectionChunks(documentCandidates, request.queryText, topK);
                if (filteredCandidates.isEmpty()) {
                    noAnswerReason = "Tìm thấy tài liệu, nhưng không tìm thấy phần nội dung bạn yêu cầu trong tài liệu đó.";
                }
            } else {
                filteredCandidates = documentCandidates.stream()
                        .filter(scoredChunk -> scoredChunk.contentScore() >= STRONG_MATCH_THRESHOLD)
                        .sorted(Comparator.comparingDouble(ScoredChunk::contentScore).reversed()
                                .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getPageStart()))
                                .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                        .toList();
                if (filteredCandidates.isEmpty()) {
                    noAnswerReason = "Tìm thấy tài liệu, nhưng không tìm thấy nội dung phù hợp với câu hỏi trong tài liệu đó.";
                }
            }
        } else {
            filteredCandidates = scoredCandidates;
        }
        if (shouldApplyRelativeRelevanceFloor(intent)
                && !selectedDocumentSection
                && !selectedDocumentSummary
                && !(hasDocumentReferenceMatch && isSummaryQuestion(request.queryText))) {
            filteredCandidates = pruneWeakFocusedCandidates(filteredCandidates, threshold, intent);
        }
        if (isBroadIntent(intent)) {
            filteredCandidates = expandBroadCandidates(
                    filteredCandidates, allScoredCandidates, threshold);
        }
        List<ScoredChunk> scoredChunks = selectedDocumentSection
                ? filteredCandidates.stream().limit(topK).toList()
                : selectedDocumentSummary
                ? selectRepresentativeSummaryChunks(filteredCandidates, topK)
                : (hasDocumentReferenceMatch && isSummaryQuestion(request.queryText))
                ? selectRepresentativeSummaryChunks(filteredCandidates, topK)
                : intent.form() == QuestionIntentAnalyzer.QuestionForm.DEFINITION
                ? selectDefinitionEvidence(
                        filteredCandidates, allScoredCandidates, topK, evidenceQuery, intent)
                : shouldUseSectionNeighborhood(intent)
                ? selectBroadSectionChunks(
                        filteredCandidates, allScoredCandidates, topK, request.queryText, intent)
                : selectDiverseChunks(filteredCandidates, topK, intent);
        if (noAnswerReason == null && scoredChunks.isEmpty()) {
            noAnswerReason = "Không tìm thấy nội dung phù hợp trong tài liệu của workspace.";
        }

        boolean shouldPersist = request.chatSessionId != null && request.userMessageId != null;
        RetrievalQuery query = shouldPersist
                ? saveRetrievalQuery(request, model, topK, threshold, scoredChunks, noAnswerReason, startedAt)
                : null;

        List<RagDto.RetrievedChunk> results = toRetrievedChunks(scoredChunks, query);

        RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
        response.retrievalQueryId = query == null ? null : query.getRetrievalQueryId();
        response.embeddingModelId = model.getEmbeddingModelId();
        response.embeddingModelName = model.getModelName();
        response.answerable = !results.isEmpty();
        response.noAnswerReason = results.isEmpty() ? noAnswerReason : null;
        response.results = results;
        return response;
    }

    public double getConfiguredSimilarityThreshold() {
        return configuredSimilarityThreshold;
    }

    private RagDto.RetrievalResponse emptyRetrievalResponse(EmbeddingModel model) {
        RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
        response.embeddingModelId = model.getEmbeddingModelId();
        response.embeddingModelName = model.getModelName();
        response.answerable = false;
        response.noAnswerReason = "No chunks exist in this workspace.";
        response.results = List.of();
        return response;
    }

    private RagDto.RetrievalResponse noPreparedEmbeddingsResponse(EmbeddingModel model) {
        RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
        response.embeddingModelId = model.getEmbeddingModelId();
        response.embeddingModelName = model.getModelName();
        response.answerable = false;
        response.noAnswerReason = "No prepared embeddings could be created for this workspace.";
        response.results = List.of();
        return response;
    }

    private Map<UUID, double[]> loadVectorsByChunkId(EmbeddingModel model, List<DocumentChunk> workspaceChunks) {
        UUID modelId = model.getEmbeddingModelId();
        Map<UUID, double[]> vectors = new java.util.HashMap<>();
        Set<UUID> missingChunkIds = new java.util.HashSet<>();
        for (DocumentChunk chunk : workspaceChunks) {
            double[] cached = vectorCache.get(modelId, chunk.getChunkId());
            if (cached == null) {
                missingChunkIds.add(chunk.getChunkId());
            } else {
                vectors.put(chunk.getChunkId(), cached);
            }
        }
        log.info("Embedding cache model={} hits={} misses={} totalEntries={}",
                modelId, vectors.size(), missingChunkIds.size(), vectorCache.size());
        if (!missingChunkIds.isEmpty()) {
            long loadStartedAt = System.nanoTime();
            List<ChunkEmbeddingRepository.CompressedEmbeddingView> compressed =
                    chunkEmbeddingRepository.findCompressedByModelAndChunkIds(modelId, missingChunkIds);
            if (compressed != null && !compressed.isEmpty()) {
                compressed.forEach(embedding -> {
                    double[] vector = embeddingService.parseCompressedVector(
                            embedding.getEmbeddingCompressed());
                    if (vector.length > 0) {
                        vectors.put(embedding.getChunkId(), vector);
                        vectorCache.put(modelId, embedding.getChunkId(), vector);
                    }
                });
            } else {
                chunkEmbeddingRepository.findByEmbeddingModelIdAndChunkIdIn(modelId, missingChunkIds)
                        .forEach(embedding -> {
                            double[] vector = embeddingService.parseJsonVector(embedding.getEmbeddingJson());
                            if (vector.length > 0) {
                                vectors.put(embedding.getChunkId(), vector);
                                vectorCache.put(modelId, embedding.getChunkId(), vector);
                            }
                        });
            }
            log.info("Loaded and parsed {} embedding vectors in {} ms; cacheEntries={}",
                    vectors.size(), (System.nanoTime() - loadStartedAt) / 1_000_000L, vectorCache.size());
        }
        return vectors;
    }

    private List<DocumentChunk> resolveCandidateChunks(RagDto.RetrievalRequest request) {
        List<DocumentChunk> chunks;
        if (request.documentIds != null && !request.documentIds.isEmpty()) {
            List<UUID> documentIds = request.documentIds.stream().distinct().toList();
            chunks = compressedDocumentChunks(documentIds);
        } else if (request.workspaceIds != null && !request.workspaceIds.isEmpty()) {
            List<UUID> workspaceIds = request.workspaceIds.stream().distinct().toList();
            chunks = compressedWorkspaceChunks(workspaceIds);
        } else {
            chunks = compressedWorkspaceChunks(List.of(request.workspaceId));
        }

        Map<UUID, CourseDocument> documents = loadDocumentsById(chunks);
        return chunks.stream()
                .filter(chunk -> {
                    CourseDocument document = documents.get(chunk.getDocumentId());
                    return document != null
                            && "PROCESSED".equals(document.getProcessingStatus())
                            && "INDEXED".equals(document.getIndexingStatus());
                })
                .toList();
    }

    private List<DocumentChunk> compressedDocumentChunks(List<UUID> documentIds) {
        List<DocumentChunkRepository.CompressedChunkView> compressed =
                documentChunkRepository.findCompressedByDocumentIds(documentIds);
        if (compressed != null && !compressed.isEmpty()) {
            return compressed.stream().map(this::toDocumentChunk).toList();
        }
        return documentChunkRepository.findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(documentIds);
    }

    private List<DocumentChunk> compressedWorkspaceChunks(List<UUID> workspaceIds) {
        List<DocumentChunkRepository.CompressedChunkView> compressed =
                documentChunkRepository.findCompressedByWorkspaceIds(workspaceIds);
        if (compressed != null && !compressed.isEmpty()) {
            return compressed.stream().map(this::toDocumentChunk).toList();
        }
        return workspaceIds.size() == 1
                ? documentChunkRepository.findByWorkspaceIdAndIsActiveTrueOrderByCreatedAtAsc(workspaceIds.get(0))
                : documentChunkRepository.findByWorkspaceIdInAndIsActiveTrueOrderByCreatedAtAsc(workspaceIds);
    }

    private DocumentChunk toDocumentChunk(DocumentChunkRepository.CompressedChunkView source) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(source.getChunkId());
        chunk.setDocumentId(source.getDocumentId());
        chunk.setChunkIndex(source.getChunkIndex());
        chunk.setChunkStrategy(source.getChunkStrategy());
        chunk.setPageStart(source.getPageStart());
        chunk.setPageEnd(source.getPageEnd());
        chunk.setContent(EmbeddingService.decompressUnicodeText(source.getContentCompressed()));
        return chunk;
    }

    private boolean isBroadIntent(QuestionIntentAnalyzer.QueryIntent intent) {
        return intent != null && (
                intent.answerDepth() == QuestionIntentAnalyzer.AnswerDepth.DEEP
                        || intent.form() == QuestionIntentAnalyzer.QuestionForm.LIST
                        || intent.form() == QuestionIntentAnalyzer.QuestionForm.COMPARISON
                        || intent.summary());
    }

    private boolean shouldUseSectionNeighborhood(QuestionIntentAnalyzer.QueryIntent intent) {
        return intent != null
                && intent.answerDepth() == QuestionIntentAnalyzer.AnswerDepth.DEEP
                && (
                    intent.form() == QuestionIntentAnalyzer.QuestionForm.LIST
                            || intent.form() == QuestionIntentAnalyzer.QuestionForm.REASONING
                );
    }

    private boolean shouldApplyRelativeRelevanceFloor(QuestionIntentAnalyzer.QueryIntent intent) {
        if (intent == null || intent.summary() || intent.hasSection()) {
            return false;
        }
        return switch (intent.form()) {
            case DEFINITION, LIST, PROCEDURE, COMPARISON -> false;
            default -> intent.answerDepth() != QuestionIntentAnalyzer.AnswerDepth.DEEP;
        };
    }

    private List<ScoredChunk> pruneWeakFocusedCandidates(
            List<ScoredChunk> candidates,
            double threshold,
            QuestionIntentAnalyzer.QueryIntent intent
    ) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        double topScore = candidates.get(0).score();
        double allowedDrop = intent.form() == QuestionIntentAnalyzer.QuestionForm.REASONING
                ? 0.18
                : 0.16;
        double relativeFloor = Math.max(threshold, topScore - allowedDrop);
        return candidates.stream()
                .filter(candidate -> candidate.score() >= relativeFloor)
                .toList();
    }

    private List<ScoredChunk> selectBroadSectionChunks(
            List<ScoredChunk> candidates,
            List<ScoredChunk> allCandidates,
            int topK,
            String queryText,
            QuestionIntentAnalyzer.QueryIntent intent
    ) {
        if (candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        Set<String> queryTerms = retrievalTerms(queryText);
        String corePhrase = normalizeLoose(
                (queryText == null ? "" : queryText.split("[?!.]", 2)[0])
                        .replaceFirst("(?i)^(tại sao|tai sao|vì sao|vi sao|why)\\s+", ""));
        ScoredChunk anchor = candidates.stream()
                .filter(candidate -> corePhrase.length() >= 10
                        && normalizeLoose(candidate.chunk().getContent()).contains(corePhrase))
                .max(Comparator.comparingDouble(ScoredChunk::score))
                .orElseGet(() -> candidates.stream()
                .max(Comparator
                        .comparingDouble((ScoredChunk candidate) ->
                                lexicalCoverage(queryTerms, candidate.chunk().getContent())
                                        + orderedPhraseCoverage(
                                                queryText, candidate.chunk().getContent()) * 1.5)
                        .thenComparingDouble(ScoredChunk::score))
                .orElse(candidates.get(0)));
        Integer anchorPage = anchor.chunk().getPageStart();
        if (anchorPage == null) {
            return selectDiverseChunks(candidates, topK, null);
        }

        boolean reasoning = intent.form() == QuestionIntentAnalyzer.QuestionForm.REASONING;
        int firstPage = reasoning ? anchorPage - 2 : anchorPage;
        int lastPage = reasoning ? anchorPage + 3 : anchorPage + 5;
        int neighborhoodLimit = Math.min(6,
                Math.max(3, Math.min(topK - 2, (int) Math.ceil(topK * 0.7))));
        List<ScoredChunk> selected = new ArrayList<>();
        Set<String> selectedPages = new HashSet<>();
        allCandidates.stream()
                .filter(candidate -> Objects.equals(
                        candidate.chunk().getDocumentId(),
                        anchor.chunk().getDocumentId()))
                .filter(candidate -> candidate.chunk().getPageStart() != null)
                .filter(candidate -> candidate.chunk().getPageStart() >= firstPage)
                .filter(candidate -> candidate.chunk().getPageStart() <= lastPage)
                .filter(candidate -> candidate.score() >= 0.16)
                .sorted(Comparator
                        .comparingInt((ScoredChunk candidate) ->
                                Math.max(0, candidate.chunk().getPageStart() - anchorPage))
                        .thenComparingInt(candidate ->
                                Math.abs(candidate.chunk().getPageStart() - anchorPage))
                        .thenComparing(Comparator.comparingDouble(ScoredChunk::score).reversed()))
                .forEach(candidate -> {
                    String pageKey = candidate.chunk().getDocumentId() + ":"
                            + candidate.chunk().getPageStart();
                    if (selected.size() < neighborhoodLimit && selectedPages.add(pageKey)) {
                        addIfMissing(selected, candidate);
                    }
                });

        if (selected.size() < Math.min(3, topK)) {
            List<ScoredChunk> remaining = candidates.stream()
                    .filter(candidate -> selected.stream().noneMatch(existing ->
                            existing.chunk().getChunkId().equals(candidate.chunk().getChunkId())))
                    .toList();
            selectDiverseChunks(remaining, topK - selected.size(), null)
                    .forEach(candidate -> addIfMissing(selected, candidate));
        }
        return selected.stream().limit(topK).toList();
    }

    private List<ScoredChunk> selectDefinitionEvidence(
            List<ScoredChunk> candidates,
            List<ScoredChunk> allCandidates,
            int topK,
            String queryText,
            QuestionIntentAnalyzer.QueryIntent intent
    ) {
        if (candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        int definitionLimit = Math.min(topK, intent.exhaustive() ? 2 : 3);
        ScoredChunk anchor = candidates.stream()
                .max(Comparator
                        .comparingDouble((ScoredChunk candidate) ->
                                definitionEvidencePriority(candidate, queryText, intent))
                        .thenComparingDouble(ScoredChunk::score))
                .orElse(candidates.get(0));

        List<ScoredChunk> selected = new ArrayList<>();
        addIfMissing(selected, anchor);
        double anchorPriority = definitionEvidencePriority(anchor, queryText, intent);
        Integer anchorPage = anchor.chunk().getPageStart();
        if (anchorPage != null) {
            if (definitionComponentBoost(queryText, anchor.chunk().getContent()) > 0.0) {
                allCandidates.stream()
                        .filter(candidate -> Objects.equals(
                                candidate.chunk().getDocumentId(),
                                anchor.chunk().getDocumentId()))
                        .filter(candidate -> Objects.equals(
                                candidate.chunk().getPageStart(),
                                anchorPage - 1))
                        .max(Comparator.comparingDouble(candidate ->
                                definitionEvidencePriority(candidate, queryText, intent)))
                        .ifPresent(candidate -> {
                            if (selected.size() < definitionLimit) {
                                addIfMissing(selected, candidate);
                            }
                        });
            }
            allCandidates.stream()
                    .filter(candidate -> Objects.equals(
                            candidate.chunk().getDocumentId(),
                            anchor.chunk().getDocumentId()))
                    .filter(candidate -> candidate.chunk().getPageStart() != null)
                    .filter(candidate -> Math.abs(
                            candidate.chunk().getPageStart() - anchorPage) <= 2)
                    .filter(candidate ->
                            candidate.chunk().getChunkId().equals(anchor.chunk().getChunkId())
                                    || orderedPhraseCoverage(
                                            definitionSubject(queryText),
                                            candidate.chunk().getContent()) >= 0.55
                                    || (candidate.chunk().getPageStart() == anchorPage + 1
                                            && hasUnclosedQuotation(
                                                    anchor.chunk().getContent())))
                    .sorted(Comparator
                            .comparingDouble((ScoredChunk candidate) ->
                                    definitionEvidencePriority(candidate, queryText, intent))
                            .reversed()
                            .thenComparingInt(candidate -> Math.abs(
                                    candidate.chunk().getPageStart() - anchorPage))
                            .thenComparingInt(candidate ->
                                    candidate.chunk().getPageStart())
                            .thenComparing(candidate ->
                                    nullToMax(candidate.chunk().getChunkIndex())))
                    .forEach(candidate -> {
                        if (selected.size() < definitionLimit) {
                            addIfMissing(selected, candidate);
                        }
                    });
        }
        candidates.stream()
                .filter(candidate -> candidate.chunk().getChunkId().equals(anchor.chunk().getChunkId())
                        || (definitionEvidencePriority(candidate, queryText, intent)
                                >= anchorPriority - 0.35
                                && orderedPhraseCoverage(
                                        definitionSubject(queryText),
                                        candidate.chunk().getContent()) >= 0.55))
                .sorted(Comparator
                        .comparingDouble((ScoredChunk candidate) ->
                                definitionEvidencePriority(candidate, queryText, intent))
                        .reversed())
                .forEach(candidate -> {
                    if (selected.size() < definitionLimit) {
                        addIfMissing(selected, candidate);
                    }
                });
        return selected.stream()
                .sorted(Comparator
                        .comparing((ScoredChunk candidate) ->
                                candidate.chunk().getDocumentId().toString())
                        .thenComparing(candidate -> nullToMax(candidate.chunk().getPageStart()))
                        .thenComparing(candidate -> nullToMax(candidate.chunk().getChunkIndex())))
                .limit(definitionLimit)
                .toList();
    }

    private double definitionEvidencePriority(
            ScoredChunk candidate,
            String queryText,
            QuestionIntentAnalyzer.QueryIntent intent
    ) {
        String content = candidate.chunk().getContent();
        String subject = definitionSubject(queryText);
        double subjectCoverage = lexicalCoverage(retrievalTerms(subject), content);
        double subjectPhraseCoverage = orderedPhraseCoverage(subject, content);
        double exactSubjectPriority = normalizeLoose(content).contains(subject) ? 0.65 : 0.0;
        boolean containsQuotation = content != null
                && (content.contains("\"") || content.contains("“") || content.contains("”"));
        double quotationPriority = containsQuotation && subjectCoverage >= 0.30 ? 0.40 : 0.0;
        return candidate.score()
                + exactSubjectPriority
                + (subjectCoverage * 0.45)
                + (subjectPhraseCoverage * 0.35)
                + quotationPriority
                + attributionEvidenceBoost(queryText, content)
                + definitionComponentBoost(queryText, content)
                + definitionCueBoost(intent, queryText, content);
    }

    private double attributionEvidenceBoost(String queryText, String content) {
        if (queryText == null || content == null) {
            return 0.0;
        }
        int comma = queryText.indexOf(',');
        if (comma <= 0) {
            return 0.0;
        }
        String prefix = normalizeLoose(queryText.substring(0, comma));
        if (!prefix.startsWith("theo ")) {
            return 0.0;
        }
        String attribution = prefix.substring("theo ".length()).trim();
        Set<String> attributionTerms = retrievalTerms(attribution);
        if (attributionTerms.isEmpty()) {
            return 0.0;
        }
        double coverage = lexicalCoverage(attributionTerms, content);
        String normalizedContent = normalizeLoose(content);
        boolean directAttributedDefinition = normalizedContent.contains("da dinh nghia")
                || normalizedContent.contains("defines as")
                || normalizedContent.contains("defined as");
        if (coverage >= 0.99 && directAttributedDefinition) {
            return 2.2;
        }
        if (coverage >= 0.99) {
            return 1.0;
        }
        return coverage >= 0.50 ? 0.35 : 0.0;
    }

    private double definitionComponentBoost(String queryText, String content) {
        String query = normalizeLoose(queryText);
        boolean asksForComponents = query.contains("gom nhung")
                || query.contains("bao gom")
                || query.contains("gom may")
                || query.contains("may mat")
                || query.contains("nhung mat nao")
                || query.contains("thanh phan")
                || query.contains("cac phan");
        if (!asksForComponents) {
            return 0.0;
        }
        String normalizedContent = normalizeLoose(content);
        boolean hasPairedStructure =
                (normalizedContent.contains("mat thu nhat")
                        && normalizedContent.contains("mat thu hai"))
                        || (normalizedContent.contains("phan thu nhat")
                        && normalizedContent.contains("phan thu hai"))
                        || (normalizedContent.contains("buoc thu nhat")
                        && normalizedContent.contains("buoc thu hai"))
                        || (normalizedContent.contains("mot la")
                        && normalizedContent.contains("hai la"));
        if (hasPairedStructure) {
            return 0.70;
        }
        if (normalizedContent.contains("co hai mat")
                || normalizedContent.contains("bao gom")
                || normalizedContent.contains("gom hai")) {
            return 0.35;
        }
        return 0.0;
    }

    private boolean hasUnclosedQuotation(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        long straightQuotes = content.chars().filter(character -> character == '"').count();
        long openingQuotes = content.chars().filter(character -> character == '“').count();
        long closingQuotes = content.chars().filter(character -> character == '”').count();
        return straightQuotes % 2 == 1 || openingQuotes > closingQuotes;
    }

    private double lexicalCoverage(Set<String> queryTerms, String content) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> contentTerms = retrievalTerms(content);
        return (double) queryTerms.stream().filter(contentTerms::contains).count()
                / queryTerms.size();
    }

    private double orderedPhraseCoverage(String queryText, String content) {
        Set<String> ignored = Set.of(
                "tai", "sao", "vi", "giai", "thich", "day", "du", "khia", "canh",
                "trinh", "bay", "neu", "hay", "mot", "so", "cac", "ve");
        List<String> queryTokens = java.util.Arrays.stream(normalizeLoose(queryText).split("\\s+"))
                .filter(token -> (token.length() >= 2 || "y".equals(token))
                        && !ignored.contains(token))
                .toList();
        if (queryTokens.size() < 2) {
            return 0.0;
        }
        String normalizedContent = normalizeLoose(content);
        int pairs = 0;
        int matched = 0;
        for (int index = 0; index < queryTokens.size() - 1; index++) {
            String left = queryTokens.get(index);
            String right = queryTokens.get(index + 1);
            if (left.equals(right)) {
                continue;
            }
            pairs++;
            if (normalizedContent.contains(left + " " + right)) {
                matched++;
            }
        }
        return pairs == 0 ? 0.0 : (double) matched / pairs;
    }

    private List<ScoredChunk> expandBroadCandidates(
            List<ScoredChunk> filtered,
            List<ScoredChunk> all,
            double threshold
    ) {
        if (filtered.isEmpty()) {
            return filtered;
        }
        double relaxedThreshold = Math.max(0.16, threshold - 0.10);
        List<ScoredChunk> anchors = filtered.stream().limit(4).toList();
        LinkedHashSet<ScoredChunk> expanded = new LinkedHashSet<>(filtered);
        for (ScoredChunk candidate : all) {
            if (candidate.score() >= relaxedThreshold || nearAnyAnchor(candidate, anchors, 4)) {
                expanded.add(candidate);
            }
        }
        return expanded.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();
    }

    private boolean nearAnyAnchor(ScoredChunk candidate, List<ScoredChunk> anchors, int pageDistance) {
        Integer page = candidate.chunk().getPageStart();
        if (page == null) {
            return false;
        }
        return anchors.stream().anyMatch(anchor ->
                Objects.equals(anchor.chunk().getDocumentId(), candidate.chunk().getDocumentId())
                        && anchor.chunk().getPageStart() != null
                        && Math.abs(anchor.chunk().getPageStart() - page) <= pageDistance);
    }

    private List<ScoredChunk> selectDiverseChunks(
            List<ScoredChunk> candidates,
            int topK,
            QuestionIntentAnalyzer.QueryIntent intent
    ) {
        if (candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        List<ScoredChunk> remaining = new ArrayList<>(candidates);
        List<ScoredChunk> selected = new ArrayList<>();
        List<Set<String>> selectedTerms = new ArrayList<>();
        Map<String, Integer> perPage = new java.util.HashMap<>();
        int pageLimit = isBroadIntent(intent) ? 2 : 3;

        while (!remaining.isEmpty() && selected.size() < topK) {
            ScoredChunk best = null;
            double bestAdjusted = Double.NEGATIVE_INFINITY;
            Set<String> bestTerms = Set.of();
            for (ScoredChunk candidate : remaining) {
                String pageKey = candidate.chunk().getDocumentId() + ":"
                        + candidate.chunk().getPageStart();
                if (perPage.getOrDefault(pageKey, 0) >= pageLimit) {
                    continue;
                }
                Set<String> terms = retrievalTerms(candidate.chunk().getContent());
                double duplicate = selectedTerms.stream()
                        .mapToDouble(existing -> jaccard(existing, terms))
                        .max().orElse(0.0);
                if (duplicate >= 0.88) {
                    continue;
                }
                boolean newPage = !perPage.containsKey(pageKey);
                double adjusted = candidate.score()
                        - (duplicate * (isBroadIntent(intent) ? 0.24 : 0.15))
                        + (newPage && isBroadIntent(intent) ? 0.06 : 0.0);
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    best = candidate;
                    bestTerms = terms;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
            selectedTerms.add(bestTerms);
            String pageKey = best.chunk().getDocumentId() + ":" + best.chunk().getPageStart();
            perPage.merge(pageKey, 1, Integer::sum);
            remaining.remove(best);
        }
        return selected;
    }

    private Set<String> retrievalTerms(String value) {
        String normalized = normalizeLoose(value);
        Set<String> stop = Set.of(
                "la", "va", "cua", "cho", "trong", "mot", "nhung", "cac", "duoc",
                "voi", "tu", "the", "nay", "do", "khi", "co", "ve",
                "giai", "thich", "day", "khia", "canh", "trinh", "bay", "neu", "hay",
                "tai", "sao");
        Set<String> terms = new HashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 3 && !stop.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private boolean isSelectedDocumentScope(RagDto.RetrievalRequest request) {
        if (request.scopeType == null || request.documentIds == null || request.documentIds.isEmpty()) {
            return false;
        }
        String scope = request.scopeType.trim().toUpperCase(java.util.Locale.ROOT);
        return "DOCUMENTS".equals(scope) || "PERSONAL".equals(scope);
    }

    private Map<UUID, CourseDocument> loadDocumentsById(List<DocumentChunk> workspaceChunks) {
        return courseDocumentRepository.findAllById(
                        workspaceChunks.stream()
                                .map(DocumentChunk::getDocumentId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(CourseDocument::getDocumentId, Function.identity()));
    }

    private ScoredChunk scoreChunk(
            DocumentChunk chunk,
            double[] chunkVector,
            double[] queryVector,
            String queryText,
            CourseDocument document,
            QuestionIntentAnalyzer.QueryIntent intent
    ) {
        double exactTokenScore = embeddingService.exactTokenOverlapScore(queryText, chunk.getContent());
        double lexicalScore = Math.max(
                exactTokenScore,
                lexicalCoverage(retrievalTerms(queryText), chunk.getContent())
        );
        double phraseScore = orderedPhraseCoverage(queryText, chunk.getContent());
        double documentReferenceScore = documentReferenceScore(queryText, document);
        if (chunkVector == null || chunkVector.length == 0) {
            double lexicalContentScore = Math.min(
                    1.0,
                    (lexicalScore * 0.70)
                            + (phraseScore * 0.30)
                            + definitionCueBoost(intent, queryText, chunk.getContent())
                            + historicalOriginCueBoost(queryText, chunk.getContent())
            );
            return new ScoredChunk(
                    chunk,
                    Math.max(lexicalContentScore, documentReferenceScore),
                    documentReferenceScore,
                    lexicalContentScore
            );
        }
        double vectorScore = embeddingService.cosineVectorScore(queryVector, chunkVector);
        double semanticScore = Math.max(0.0, vectorScore);
        double contentScore = Math.min(
                1.0,
                (semanticScore * 0.68)
                        + (lexicalScore * 0.20)
                        + (phraseScore * 0.12)
                        + definitionCueBoost(intent, queryText, chunk.getContent())
                        + historicalOriginCueBoost(queryText, chunk.getContent())
        );
        return new ScoredChunk(
                chunk,
                Math.max(contentScore, documentReferenceScore),
                documentReferenceScore,
                contentScore
        );
    }

    private double definitionCueBoost(
            QuestionIntentAnalyzer.QueryIntent intent,
            String queryText,
            String content
    ) {
        if (intent == null || intent.form() != QuestionIntentAnalyzer.QuestionForm.DEFINITION) {
            return 0.0;
        }

        String subject = definitionSubject(queryText);
        if (subject.length() < 2) {
            return 0.0;
        }

        String normalizedContent = normalizeLoose(content);
        double subjectCoverage = lexicalCoverage(retrievalTerms(subject), content);
        boolean hasDefinitionCue = normalizedContent.contains("dinh nghia")
                || normalizedContent.contains("duoc hieu la")
                || normalizedContent.contains("co nghia la")
                || normalizedContent.contains("means ")
                || normalizedContent.contains("is defined as");
        boolean explicitDefinition = normalizedContent.contains("dinh nghia " + subject)
                || normalizedContent.contains("khai niem " + subject)
                || normalizedContent.contains(subject + " la ")
                || normalizedContent.contains(subject + " duoc dinh nghia");
        if (explicitDefinition) {
            return 0.20;
        }
        return hasDefinitionCue && subjectCoverage >= 0.60 ? 0.08 : 0.0;
    }

    private double historicalOriginCueBoost(String queryText, String content) {
        String query = normalizeLoose(queryText);
        if (!query.contains("ra doi")
                || !(query.contains("o dau") || query.contains("khi nao")
                        || query.contains("thoi gian") || query.contains("som nhat"))) {
            return 0.0;
        }
        String normalizedContent = normalizeLoose(content);
        boolean directOriginStatement = normalizedContent.contains("ra doi o ca")
                || (normalizedContent.contains("ra doi o ")
                        && normalizedContent.contains("trung tam"))
                || (normalizedContent.contains("ra doi")
                        && normalizedContent.contains("gan nhu cung mot thoi gian"));
        return directOriginStatement ? 0.35 : 0.0;
    }

    private String definitionSubject(String queryText) {
        String definitionQuery = queryText == null ? "" : queryText.trim();
        int attributionComma = definitionQuery.indexOf(',');
        if (attributionComma > 0
                && normalizeLoose(definitionQuery.substring(0, attributionComma)).startsWith("theo ")) {
            definitionQuery = definitionQuery.substring(attributionComma + 1);
        }
        String subject = normalizeLoose(definitionQuery)
                .replaceFirst("^(dinh nghia|khai niem)\\s+", "")
                .replaceFirst(
                        "\\s+(la gi|duoc dinh nghia nhu the nao|duoc hieu nhu the nao|what is)"
                                + "(?:\\s+va\\s+.*)?$",
                        "")
                .replaceFirst("\\s+theo\\s+(?:quan diem cua\\s+)?[^,?]+$", "")
                .trim();
        return subject;
    }

    private double documentReferenceScore(String queryText, CourseDocument document) {
        if (document == null || !hasDocumentReferenceIntent(queryText)) {
            return 0.0;
        }

        String query = normalizeLoose(queryText);
        String documentTitle = normalizeLoose(document.getDocumentTitle());
        String filename = normalizeLoose(document.getOriginalFilename());
        String documentText = (documentTitle + " " + filename).trim();

        if (!documentTitle.isBlank() && query.contains(documentTitle)) {
            return 0.95;
        }
        if (!filename.isBlank() && query.contains(filename)) {
            return 0.95;
        }

        List<String> queryNumbers = extractNumbers(queryText);
        List<String> documentNumbers = extractNumbers(documentText);
        if (queryNumbers.size() >= 2 && startsWithNumberSequence(documentNumbers, queryNumbers)) {
            return 0.92;
        }

        return 0.0;
    }

    private boolean hasDocumentReferenceIntent(String queryText) {
        String query = normalizeLoose(queryText);
        return query.contains("tai lieu")
                || query.contains("document")
                || query.contains("file")
                || query.contains("bai")
                || query.contains("lesson")
                || query.contains("chapter")
                || query.contains("chuong")
                || query.contains(" st ")
                || DOTTED_NUMBER_REFERENCE_PATTERN.matcher(queryText == null ? "" : queryText).find();
    }

    private boolean hasExplicitDocumentReference(String queryText) {
        String query = normalizeLoose(queryText);
        if (DOTTED_NUMBER_REFERENCE_PATTERN.matcher(queryText == null ? "" : queryText).find()) {
            return true;
        }
        return (query.contains("tai lieu") || query.contains("document") || query.contains("file"))
                && !extractNumbers(queryText).isEmpty();
    }

    private boolean isSummaryQuestion(String queryText) {
        return QuestionIntentAnalyzer.analyze(queryText).summary();
    }

    private boolean isSectionQuestion(String queryText) {
        return QuestionIntentAnalyzer.analyze(queryText).hasSection();
    }

    private List<ScoredChunk> selectSectionChunks(List<ScoredChunk> candidates, String queryText, int topK) {
        List<ScoredChunk> byPage = candidates.stream()
                .sorted(Comparator.comparing((ScoredChunk scoredChunk) -> nullToMax(scoredChunk.chunk().getPageStart()))
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                .toList();
        if (byPage.isEmpty()) {
            return List.of();
        }

        SectionKind sectionKind = sectionKind(queryText);
        if (sectionKind == SectionKind.NONE) {
            return byPage.stream().limit(topK).toList();
        }

        int startIndex = -1;
        for (int index = 0; index < byPage.size(); index++) {
            if (matchesSectionStart(byPage.get(index).chunk().getContent(), sectionKind)) {
                startIndex = index;
                break;
            }
        }
        if (startIndex < 0) {
            return byPage.stream()
                    .filter(scoredChunk -> scoredChunk.contentScore() >= STRONG_MATCH_THRESHOLD)
                    .limit(topK)
                    .toList();
        }

        List<ScoredChunk> selected = new ArrayList<>();
        for (int index = startIndex; index < byPage.size() && selected.size() < topK; index++) {
            if (index > startIndex && isAnyOtherMajorSection(byPage.get(index).chunk().getContent(), sectionKind)) {
                break;
            }
            selected.add(byPage.get(index));
        }
        return selected;
    }

    private SectionKind sectionKind(String queryText) {
        return switch (QuestionIntentAnalyzer.analyze(queryText).section()) {
            case VOCABULARY -> SectionKind.VOCABULARY;
            case GRAMMAR -> SectionKind.GRAMMAR;
            case EXAMPLE, EXERCISE -> SectionKind.EXERCISE;
            case NONE -> SectionKind.NONE;
        };
    }

    private boolean matchesSectionStart(String content, SectionKind sectionKind) {
        String normalized = normalizeLoose(content);
        return switch (sectionKind) {
            case VOCABULARY -> normalized.contains("tu vung") || contentContains(content, "ことば");
            case GRAMMAR -> normalized.contains("ngu phap") || contentContains(content, "ぶんぽう");
            case EXERCISE -> normalized.contains("bai tap")
                    || normalized.contains("vi du")
                    || contentContains(content, "チャレンジ");
            case NONE -> false;
        };
    }

    private boolean isAnyOtherMajorSection(String content, SectionKind currentSection) {
        if (currentSection != SectionKind.VOCABULARY
                && (matchesSectionStart(content, SectionKind.VOCABULARY))) {
            return true;
        }
        if (currentSection != SectionKind.GRAMMAR
                && (matchesSectionStart(content, SectionKind.GRAMMAR))) {
            return true;
        }
        return false;
    }

    private boolean contentContains(String content, String needle) {
        return content != null && content.contains(needle);
    }

    private List<ScoredChunk> selectRepresentativeSummaryChunks(List<ScoredChunk> candidates, int topK) {
        List<ScoredChunk> byPage = candidates.stream()
                .filter(scoredChunk -> !isSummaryNoise(scoredChunk.chunk().getContent()))
                .sorted(Comparator.comparing((ScoredChunk scoredChunk) -> nullToMax(scoredChunk.chunk().getPageStart()))
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                .toList();
        if (byPage.size() <= topK) {
            return byPage;
        }
        List<ScoredChunk> selected = new ArrayList<>();
        byPage.stream()
                .filter(scoredChunk -> summaryPriority(scoredChunk.chunk().getContent()) >= 30)
                .sorted(Comparator.comparingInt(
                                (ScoredChunk scoredChunk) -> summaryPriority(scoredChunk.chunk().getContent()))
                        .reversed()
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getPageStart())))
                .limit(Math.max(1, topK * 2L / 3L))
                .forEach(scoredChunk -> addIfMissing(selected, scoredChunk));

        double step = (byPage.size() - 1) / (double) Math.max(1, topK - 1);
        for (int slot = 0; slot < topK && selected.size() < topK; slot++) {
            int index = (int) Math.round(slot * step);
            addIfMissing(selected, byPage.get(index));
        }
        for (ScoredChunk scoredChunk : byPage) {
            if (selected.size() >= topK) {
                break;
            }
            addIfMissing(selected, scoredChunk);
        }

        return selected.stream()
                .limit(topK)
                .sorted(Comparator.comparing((ScoredChunk scoredChunk) -> nullToMax(scoredChunk.chunk().getPageStart()))
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                .toList();
    }

    private void addIfMissing(List<ScoredChunk> selected, ScoredChunk candidate) {
        boolean exists = selected.stream()
                .anyMatch(scoredChunk -> scoredChunk.chunk().getChunkId().equals(candidate.chunk().getChunkId()));
        if (!exists) {
            selected.add(candidate);
        }
    }

    private boolean isSummaryNoise(String content) {
        String normalized = normalizeLoose(content);
        if (normalized.isBlank()) return true;
        long questionMarks = content == null ? 0 : content.chars().filter(value -> value == '?').count();
        return questionMarks >= 2
                || normalized.contains("muc luc")
                || normalized.contains("cau hoi on tap")
                || normalized.contains("bai tap on tap")
                || normalized.contains("tai lieu tham khao")
                || normalized.matches(".*\\.{4,}\\s*\\d+\\s*$");
    }

    private int summaryPriority(String content) {
        String normalized = normalizeLoose(content);
        int priority = 0;
        String opening = normalized.substring(0, Math.min(normalized.length(), 500));
        if (opening.matches(".*\\bchuong\\s+(?:[ivxlcdm]+|\\d+)\\b.*")) {
            priority += 60;
        }
        if (opening.matches(".*\\bphan\\s+(?:[ivxlcdm]+|\\d+)\\b.*")) {
            priority += 45;
        }
        if (opening.contains("noi dung chuong")
                || opening.contains("nhung nguyen ly")
                || opening.contains("khai luoc")
                || opening.contains("van de co ban")) {
            priority += 25;
        }
        if (content != null && content.length() > 300) {
            priority += 1;
        }
        return priority;
    }

    private String normalizeLoose(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutMarks.toLowerCase(java.util.Locale.ROOT)
                .replace('\u0111', 'd')
                .replaceAll("\\.(pdf|docx|doc|pptx|ppt|txt)$", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private List<String> extractNumbers(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(value == null ? "" : value);
        List<String> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private boolean startsWithNumberSequence(List<String> documentNumbers, List<String> queryNumbers) {
        if (documentNumbers.size() < queryNumbers.size()) {
            return false;
        }
        for (int index = 0; index < queryNumbers.size(); index++) {
            if (!documentNumbers.get(index).equals(queryNumbers.get(index))) {
                return false;
            }
        }
        return true;
    }

    private int nullToMax(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    public List<RagDto.RetrievalQueryResponse> getRetrievalQueries(UUID workspaceId) {
        if (workspaceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId is required.");
        }

        List<RetrievalQuery> queries = retrievalQueryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        return queries.stream()
                .map(RagDto.RetrievalQueryResponse::fromEntity)
                .toList();
    }

    public List<RagDto.RetrievalResultResponse> getRetrievalResults(UUID retrievalQueryId) {
        if (retrievalQueryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "retrievalQueryId is required.");
        }

        List<RetrievalResult> results = retrievalResultRepository.findByRetrievalQueryIdOrderByResultRankAsc(retrievalQueryId);
        return results.stream()
                .map(RagDto.RetrievalResultResponse::fromEntity)
                .toList();
    }

    public List<RagDto.CitationResponse> getCitations(UUID assistantMessageId) {
        if (assistantMessageId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assistantMessageId is required.");
        }

        List<AnswerCitation> citations = answerCitationRepository.findByAssistantMessageIdOrderByCitationOrderAsc(assistantMessageId);
        return citations.stream()
                .map(RagDto.CitationResponse::fromEntity)
                .toList();
    }

    private RetrievalQuery saveRetrievalQuery(
            RagDto.RetrievalRequest request,
            EmbeddingModel model,
            int topK,
            double threshold,
            List<ScoredChunk> scoredChunks,
            String noAnswerReason,
            Instant startedAt
    ) {
        RetrievalQuery query = new RetrievalQuery();
        query.setChatSessionId(request.chatSessionId);
        query.setUserMessageId(request.userMessageId);
        query.setWorkspaceId(request.workspaceId);
        query.setSemesterWorkspaceId(request.semesterId);
        query.setScopeType(request.scopeType == null || request.scopeType.isBlank() ? "COURSE" : request.scopeType);
        query.setQueryText(request.originalQueryText == null || request.originalQueryText.isBlank()
                ? request.queryText.trim()
                : request.originalQueryText.trim());
        query.setRewrittenQuery(request.queryText.trim());
        query.setEmbeddingModelId(model.getEmbeddingModelId());
        query.setTopK(topK);
        query.setSimilarityMetric("embedding_cosine");
        query.setSimilarityThreshold(threshold);
        query.setIsAnswerable(!scoredChunks.isEmpty());
        query.setNoAnswerReason(scoredChunks.isEmpty() ? noAnswerReason : null);
        query.setLatencyMs((int) Duration.between(startedAt, Instant.now()).toMillis());
        query.setCreatedAt(LocalDateTime.now());
        return retrievalQueryRepository.save(query);
    }

    private List<RagDto.RetrievedChunk> toRetrievedChunks(List<ScoredChunk> scoredChunks, RetrievalQuery query) {
        UUID retrievalQueryId = query == null ? null : query.getRetrievalQueryId();
        LocalDateTime now = LocalDateTime.now();
        Map<UUID, CourseDocument> documentsById = courseDocumentRepository.findAllById(
                        scoredChunks.stream()
                                .map(scoredChunk -> scoredChunk.chunk().getDocumentId())
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(CourseDocument::getDocumentId, Function.identity()));

        return java.util.stream.IntStream.range(0, scoredChunks.size())
                .mapToObj(index -> {
                    ScoredChunk scoredChunk = scoredChunks.get(index);
                    DocumentChunk chunk = scoredChunk.chunk();
                    CourseDocument document = documentsById.get(chunk.getDocumentId());
                    int rank = index + 1;
                    UUID retrievalResultId = null;

                    if (retrievalQueryId != null) {
                        RetrievalResult result = new RetrievalResult();
                        result.setRetrievalQueryId(retrievalQueryId);
                        result.setChunkId(chunk.getChunkId());
                        result.setDocumentId(chunk.getDocumentId());
                        result.setResultRank(rank);
                        result.setSimilarityScore(scoredChunk.score());
                        result.setRerankScore(scoredChunk.score());
                        result.setCreatedAt(now);
                        retrievalResultId = retrievalResultRepository.save(result).getRetrievalResultId();
                    }

                    RagDto.RetrievedChunk response = new RagDto.RetrievedChunk();
                    response.retrievalResultId = retrievalResultId;
                    response.chunkId = chunk.getChunkId();
                    response.documentId = chunk.getDocumentId();
                    response.documentTitle = document == null ? null : document.getDocumentTitle();
                    response.filename = document == null ? null : document.getOriginalFilename();
                    response.pageStart = chunk.getPageStart();
                    response.pageEnd = chunk.getPageEnd();
                    response.rank = rank;
                    response.similarityScore = scoredChunk.score();
                    response.content = chunk.getContent();
                    return response;
                })
                .toList();
    }

    private void validateRetrievalRequest(RagDto.RetrievalRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Retrieval request is required.");
        }
        boolean hasWorkspace = request.workspaceId != null;
        boolean hasWorkspaces = request.workspaceIds != null && !request.workspaceIds.isEmpty();
        boolean hasDocuments = request.documentIds != null && !request.documentIds.isEmpty();
        if (!hasWorkspace && !hasWorkspaces && !hasDocuments) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId, workspaceIds, or documentIds is required.");
        }
        if (request.queryText == null || request.queryText.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "queryText is required.");
        }
    }

    private record ScoredChunk(DocumentChunk chunk, double score, double documentReferenceScore, double contentScore) {
    }

    private enum SectionKind {
        NONE,
        VOCABULARY,
        GRAMMAR,
        EXERCISE
    }
}
