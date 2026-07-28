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
import java.util.List;
import java.util.Map;
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

        Instant startedAt = Instant.now();
        EmbeddingModel model = embeddingService.resolveModel(request.embeddingModelId);
        int topK = request.topK == null || request.topK <= 0 ? 5 : Math.min(request.topK, 40);
        double threshold = request.similarityThreshold == null
                ? configuredSimilarityThreshold : request.similarityThreshold;

        List<DocumentChunk> workspaceChunks = resolveCandidateChunks(request);
        if (workspaceChunks.isEmpty()) {
            return emptyRetrievalResponse(model);
        }
        QuestionIntentAnalyzer.QueryIntent intent = QuestionIntentAnalyzer.analyze(request.queryText);

        Map<UUID, double[]> vectorsByChunkId = loadVectorsByChunkId(model, workspaceChunks);

        if (vectorsByChunkId.isEmpty()) {
            return noPreparedEmbeddingsResponse(model);
        }

        double[] queryVector = embeddingService.embedText(request.queryText, model);
        Map<UUID, CourseDocument> documentsById = loadDocumentsById(workspaceChunks);
        Map<UUID, double[]> preparedVectorsByChunkId = vectorsByChunkId;
        List<ScoredChunk> allScoredCandidates = workspaceChunks.stream()
                .map(chunk -> scoreChunk(
                        chunk,
                        preparedVectorsByChunkId.get(chunk.getChunkId()),
                        queryVector,
                        request.queryText,
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
        List<ScoredChunk> scoredChunks = selectedDocumentSection
                ? filteredCandidates.stream().limit(topK).toList()
                : selectedDocumentSummary
                ? selectRepresentativeSummaryChunks(filteredCandidates, topK)
                : (hasDocumentReferenceMatch && isSummaryQuestion(request.queryText))
                ? selectRepresentativeSummaryChunks(filteredCandidates, topK)
                : filteredCandidates.stream()
                .limit(topK)
                .toList();
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
                    return document != null && "PROCESSED".equals(document.getProcessingStatus());
                })
                .toList();
    }

    private List<DocumentChunk> compressedDocumentChunks(List<UUID> documentIds) {
        List<DocumentChunkRepository.CompressedChunkView> compressed =
                documentChunkRepository.findCompressedByDocumentIds(documentIds);
        if (compressed != null && !compressed.isEmpty()) {
            return compressed.stream().map(this::toDocumentChunk).toList();
        }
        return documentChunkRepository.findByDocumentIdInOrderByCreatedAtAsc(documentIds);
    }

    private List<DocumentChunk> compressedWorkspaceChunks(List<UUID> workspaceIds) {
        List<DocumentChunkRepository.CompressedChunkView> compressed =
                documentChunkRepository.findCompressedByWorkspaceIds(workspaceIds);
        if (compressed != null && !compressed.isEmpty()) {
            return compressed.stream().map(this::toDocumentChunk).toList();
        }
        return workspaceIds.size() == 1
                ? documentChunkRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceIds.get(0))
                : documentChunkRepository.findByWorkspaceIdInOrderByCreatedAtAsc(workspaceIds);
    }

    private DocumentChunk toDocumentChunk(DocumentChunkRepository.CompressedChunkView source) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(source.getChunkId());
        chunk.setDocumentId(source.getDocumentId());
        chunk.setPageStart(source.getPageStart());
        chunk.setPageEnd(source.getPageEnd());
        chunk.setContent(EmbeddingService.decompressUnicodeText(source.getContentCompressed()));
        return chunk;
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
        double documentReferenceScore = documentReferenceScore(queryText, document);
        if (chunkVector == null || chunkVector.length == 0) {
            return new ScoredChunk(
                    chunk,
                    Math.max(exactTokenScore, documentReferenceScore),
                    documentReferenceScore,
                    exactTokenScore
            );
        }
        double vectorScore = embeddingService.cosineVectorScore(queryVector, chunkVector);
        double semanticScore = Math.max(0.0, vectorScore);
        double contentScore = Math.min(
                1.0,
                (semanticScore * 0.80)
                        + (exactTokenScore * 0.20)
                        + definitionCueBoost(intent, queryText, chunk.getContent())
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

        String subject = normalizeLoose(queryText)
                .replaceFirst("^(dinh nghia|khai niem)\\s+", "")
                .replaceFirst("\\s+(la gi|duoc hieu nhu the nao|what is)$", "")
                .trim();
        if (subject.length() < 2) {
            return 0.0;
        }

        String normalizedContent = normalizeLoose(content);
        boolean explicitDefinition = normalizedContent.contains("dinh nghia " + subject)
                || normalizedContent.contains("khai niem " + subject)
                || normalizedContent.contains(subject + " la ");
        return explicitDefinition ? 0.12 : 0.0;
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
