package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.AnswerCitation;
import com.courseqa.model.entity.ChunkEmbedding;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RetrievalService {
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

    public RetrievalService(
            DocumentChunkRepository documentChunkRepository,
            ChunkEmbeddingRepository chunkEmbeddingRepository,
            RetrievalQueryRepository retrievalQueryRepository,
            RetrievalResultRepository retrievalResultRepository,
            AnswerCitationRepository answerCitationRepository,
            EmbeddingService embeddingService,
            CourseDocumentRepository courseDocumentRepository
    ) {
        this.documentChunkRepository = documentChunkRepository;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
        this.retrievalQueryRepository = retrievalQueryRepository;
        this.retrievalResultRepository = retrievalResultRepository;
        this.answerCitationRepository = answerCitationRepository;
        this.embeddingService = embeddingService;
        this.courseDocumentRepository = courseDocumentRepository;
    }

    @Transactional
    public RagDto.RetrievalResponse retrieve(RagDto.RetrievalRequest request) {
        validateRetrievalRequest(request);

        Instant startedAt = Instant.now();
        EmbeddingModel model = embeddingService.resolveModel(request.embeddingModelId);
        int topK = request.topK == null || request.topK <= 0 ? 5 : Math.min(request.topK, 40);
        double threshold = request.similarityThreshold == null ? DEFAULT_SIMILARITY_THRESHOLD : request.similarityThreshold;

        List<DocumentChunk> workspaceChunks = resolveCandidateChunks(request);
        if (workspaceChunks.isEmpty()) {
            return emptyRetrievalResponse(model);
        }

        Map<UUID, ChunkEmbedding> embeddingsByChunkId = loadEmbeddingsByChunkId(model, workspaceChunks);

        if (embeddingsByChunkId.isEmpty()) {
            workspaceChunks.stream()
                    .map(DocumentChunk::getWorkspaceId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .forEach(workspaceId -> prepareMissingWorkspaceEmbeddings(workspaceId, model));
            embeddingsByChunkId = loadEmbeddingsByChunkId(model, workspaceChunks);
        }

        if (embeddingsByChunkId.isEmpty()) {
            return noPreparedEmbeddingsResponse(model);
        }

        double[] queryVector = embeddingService.embedText(request.queryText, model.getDimension());
        Map<UUID, CourseDocument> documentsById = loadDocumentsById(workspaceChunks);
        Map<UUID, ChunkEmbedding> preparedEmbeddingsByChunkId = embeddingsByChunkId;
        List<ScoredChunk> scoredCandidates = workspaceChunks.stream()
                .map(chunk -> scoreChunk(
                        chunk,
                        preparedEmbeddingsByChunkId.get(chunk.getChunkId()),
                        queryVector,
                        request.queryText,
                        documentsById.get(chunk.getDocumentId()))
                )
                .filter(scoredChunk -> scoredChunk.score() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing((ScoredChunk scoredChunk) -> nullToMax(scoredChunk.chunk().getPageStart()))
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                .toList();

        boolean hasDocumentReferenceMatch = scoredCandidates.stream()
                .anyMatch(scoredChunk -> scoredChunk.documentReferenceScore() >= DOCUMENT_REFERENCE_THRESHOLD);
        boolean hasStrongExactMatch = scoredCandidates.stream()
                .anyMatch(scoredChunk -> scoredChunk.score() >= STRONG_MATCH_THRESHOLD);
        boolean hasExplicitDocumentReference = hasExplicitDocumentReference(request.queryText);
        String noAnswerReason = null;
        List<ScoredChunk> filteredCandidates;
        if (hasExplicitDocumentReference && !hasDocumentReferenceMatch) {
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
            filteredCandidates = scoredCandidates.stream()
                    .filter(scoredChunk -> !hasStrongExactMatch || scoredChunk.score() >= STRONG_MATCH_THRESHOLD)
                    .toList();
        }
        List<ScoredChunk> scoredChunks = (hasDocumentReferenceMatch && isSummaryQuestion(request.queryText))
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

    private Map<UUID, ChunkEmbedding> loadEmbeddingsByChunkId(EmbeddingModel model, List<DocumentChunk> workspaceChunks) {
        return chunkEmbeddingRepository
                .findByEmbeddingModelIdAndChunkIdIn(
                        model.getEmbeddingModelId(),
                        workspaceChunks.stream().map(DocumentChunk::getChunkId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(ChunkEmbedding::getChunkId, Function.identity()));
    }

    private List<DocumentChunk> resolveCandidateChunks(RagDto.RetrievalRequest request) {
        List<DocumentChunk> chunks;
        if (request.documentIds != null && !request.documentIds.isEmpty()) {
            chunks = documentChunkRepository.findByDocumentIdInOrderByCreatedAtAsc(request.documentIds.stream().distinct().toList());
        } else if (request.workspaceIds != null && !request.workspaceIds.isEmpty()) {
            chunks = documentChunkRepository.findByWorkspaceIdInOrderByCreatedAtAsc(request.workspaceIds.stream().distinct().toList());
        } else {
            chunks = documentChunkRepository.findByWorkspaceIdOrderByCreatedAtAsc(request.workspaceId);
        }

        Map<UUID, CourseDocument> documents = loadDocumentsById(chunks);
        return chunks.stream()
                .filter(chunk -> {
                    CourseDocument document = documents.get(chunk.getDocumentId());
                    return document != null && "PROCESSED".equals(document.getProcessingStatus());
                })
                .toList();
    }

    private void prepareMissingWorkspaceEmbeddings(UUID workspaceId, EmbeddingModel model) {
        RagDto.PrepareEmbeddingsRequest prepareRequest = new RagDto.PrepareEmbeddingsRequest();
        prepareRequest.workspaceId = workspaceId;
        prepareRequest.embeddingModelId = model.getEmbeddingModelId();
        embeddingService.prepareEmbeddings(prepareRequest);
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
            ChunkEmbedding embedding,
            double[] queryVector,
            String queryText,
            CourseDocument document
    ) {
        double exactTokenScore = embeddingService.exactTokenOverlapScore(queryText, chunk.getContent());
        double documentReferenceScore = documentReferenceScore(queryText, document);
        if (embedding == null) {
            return new ScoredChunk(
                    chunk,
                    Math.max(exactTokenScore, documentReferenceScore),
                    documentReferenceScore,
                    exactTokenScore
            );
        }
        double[] chunkVector = embeddingService.parseJsonVector(embedding.getEmbeddingJson());
        double vectorScore = embeddingService.cosineVectorScore(queryVector, chunkVector);
        double contentScore = Math.max(vectorScore, exactTokenScore);
        return new ScoredChunk(
                chunk,
                Math.max(contentScore, documentReferenceScore),
                documentReferenceScore,
                contentScore
        );
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
        String query = normalizeLoose(queryText);
        return query.contains("tong hop")
                || query.contains("tom tat")
                || query.contains("summary")
                || query.contains("summarize")
                || query.contains("noi dung chinh");
    }

    private boolean isSectionQuestion(String queryText) {
        String query = normalizeLoose(queryText);
        return query.contains("tat ca")
                || query.contains("toan bo")
                || query.contains("liet ke")
                || query.contains("danh sach")
                || query.contains("tu vung")
                || query.contains("ngu phap")
                || query.contains("mau cau")
                || query.contains("vi du")
                || query.contains("bai tap");
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
        String query = normalizeLoose(queryText);
        if (query.contains("tu vung")) {
            return SectionKind.VOCABULARY;
        }
        if (query.contains("ngu phap") || query.contains("mau cau")) {
            return SectionKind.GRAMMAR;
        }
        if (query.contains("bai tap") || query.contains("vi du")) {
            return SectionKind.EXERCISE;
        }
        return SectionKind.NONE;
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
        if (candidates.size() <= topK) {
            return candidates.stream()
                    .sorted(Comparator.comparing((ScoredChunk scoredChunk) -> nullToMax(scoredChunk.chunk().getPageStart()))
                            .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                    .toList();
        }

        List<ScoredChunk> byPage = candidates.stream()
                .sorted(Comparator.comparing((ScoredChunk scoredChunk) -> nullToMax(scoredChunk.chunk().getPageStart()))
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getChunkIndex())))
                .toList();
        List<ScoredChunk> selected = new ArrayList<>();

        addIfMissing(selected, byPage.get(0));

        candidates.stream()
                .filter(scoredChunk -> summaryPriority(scoredChunk.chunk().getContent()) > 0)
                .sorted(Comparator.comparingInt((ScoredChunk scoredChunk) ->
                                summaryPriority(scoredChunk.chunk().getContent())).reversed()
                        .thenComparing(scoredChunk -> nullToMax(scoredChunk.chunk().getPageStart())))
                .forEach(scoredChunk -> {
                    if (selected.size() < topK) {
                        addIfMissing(selected, scoredChunk);
                    }
                });

        int step = Math.max(1, byPage.size() / topK);
        for (int index = 0; index < byPage.size() && selected.size() < topK; index += step) {
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

    private int summaryPriority(String content) {
        String normalized = normalizeLoose(content);
        int priority = 0;
        if (normalized.contains("mon hoc") || normalized.contains("bai")) {
            priority += 12;
        }
        if (normalized.contains("tu vung") || normalized.contains("ことば")) {
            priority += 20;
        }
        if (normalized.contains("ngu phap") || normalized.contains("ぶんぽう")) {
            priority += 20;
        }
        if (normalized.contains("mau cau") || normalized.contains("phuong tien") || normalized.contains("cach thuc")) {
            priority += 12;
        }
        if (normalized.contains("チャレンジ") || normalized.contains("challenge")) {
            priority += 10;
        }
        if (normalized.contains("ください") || normalized.contains("わかります") || normalized.contains("どの") || normalized.contains("どれ")) {
            priority += 10;
        }
        if (content != null && content.length() > 120) {
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
        query.setQueryText(request.queryText.trim());
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
