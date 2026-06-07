package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.AnswerCitation;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.model.entity.RetrievalQuery;
import com.courseqa.model.entity.RetrievalResult;
import com.courseqa.repository.AnswerCitationRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.RetrievalQueryRepository;
import com.courseqa.repository.RetrievalResultRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RetrievalService {
    private final DocumentChunkRepository documentChunkRepository;
    private final RetrievalQueryRepository retrievalQueryRepository;
    private final RetrievalResultRepository retrievalResultRepository;
    private final AnswerCitationRepository answerCitationRepository;
    private final EmbeddingService embeddingService;

    public RetrievalService(
            DocumentChunkRepository documentChunkRepository,
            RetrievalQueryRepository retrievalQueryRepository,
            RetrievalResultRepository retrievalResultRepository,
            AnswerCitationRepository answerCitationRepository,
            EmbeddingService embeddingService
    ) {
        this.documentChunkRepository = documentChunkRepository;
        this.retrievalQueryRepository = retrievalQueryRepository;
        this.retrievalResultRepository = retrievalResultRepository;
        this.answerCitationRepository = answerCitationRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public RagDto.RetrievalResponse retrieve(RagDto.RetrievalRequest request) {
        validateRetrievalRequest(request);

        Instant startedAt = Instant.now();
        EmbeddingModel model = embeddingService.resolveModel(request.embeddingModelId);
        int topK = request.topK == null || request.topK <= 0 ? 5 : Math.min(request.topK, 20);
        double threshold = request.similarityThreshold == null ? 0.05 : request.similarityThreshold;

        List<ScoredChunk> scoredChunks = documentChunkRepository.findByWorkspaceIdOrderByCreatedAtAsc(request.workspaceId).stream()
                .map(chunk -> new ScoredChunk(chunk, embeddingService.cosineKeywordScore(request.queryText, chunk.getContent())))
                .filter(scoredChunk -> scoredChunk.score() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();

        boolean shouldPersist = request.chatSessionId != null && request.userMessageId != null;
        RetrievalQuery query = shouldPersist
                ? saveRetrievalQuery(request, model, topK, threshold, scoredChunks, startedAt)
                : null;

        List<RagDto.RetrievedChunk> results = toRetrievedChunks(scoredChunks, query);

        RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
        response.retrievalQueryId = query == null ? null : query.getRetrievalQueryId();
        response.answerable = !results.isEmpty();
        response.noAnswerReason = results.isEmpty() ? "No chunks matched the query above the similarity threshold." : null;
        response.results = results;
        return response;
    }

    public List<RagDto.RetrievalQueryResponse> getRetrievalQueries(UUID workspaceId) {
        List<RetrievalQuery> queries = workspaceId == null
                ? retrievalQueryRepository.findAllByOrderByCreatedAtDesc()
                : retrievalQueryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        return queries.stream()
                .map(RagDto.RetrievalQueryResponse::fromEntity)
                .toList();
    }

    public List<RagDto.RetrievalResultResponse> getRetrievalResults(UUID retrievalQueryId) {
        List<RetrievalResult> results = retrievalQueryId == null
                ? retrievalResultRepository.findAll()
                : retrievalResultRepository.findByRetrievalQueryIdOrderByResultRankAsc(retrievalQueryId);
        return results.stream()
                .map(RagDto.RetrievalResultResponse::fromEntity)
                .toList();
    }

    public List<RagDto.CitationResponse> getCitations(UUID assistantMessageId) {
        List<AnswerCitation> citations = assistantMessageId == null
                ? answerCitationRepository.findAllByOrderByCreatedAtDesc()
                : answerCitationRepository.findByAssistantMessageIdOrderByCitationOrderAsc(assistantMessageId);
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
            Instant startedAt
    ) {
        RetrievalQuery query = new RetrievalQuery();
        query.setChatSessionId(request.chatSessionId);
        query.setUserMessageId(request.userMessageId);
        query.setWorkspaceId(request.workspaceId);
        query.setQueryText(request.queryText.trim());
        query.setRewrittenQuery(request.queryText.trim());
        query.setEmbeddingModelId(model.getEmbeddingModelId());
        query.setTopK(topK);
        query.setSimilarityMetric("keyword_cosine");
        query.setSimilarityThreshold(threshold);
        query.setIsAnswerable(!scoredChunks.isEmpty());
        query.setNoAnswerReason(scoredChunks.isEmpty() ? "No chunks matched the query above the similarity threshold." : null);
        query.setLatencyMs((int) Duration.between(startedAt, Instant.now()).toMillis());
        query.setCreatedAt(LocalDateTime.now());
        return retrievalQueryRepository.save(query);
    }

    private List<RagDto.RetrievedChunk> toRetrievedChunks(List<ScoredChunk> scoredChunks, RetrievalQuery query) {
        UUID retrievalQueryId = query == null ? null : query.getRetrievalQueryId();
        LocalDateTime now = LocalDateTime.now();

        return java.util.stream.IntStream.range(0, scoredChunks.size())
                .mapToObj(index -> {
                    ScoredChunk scoredChunk = scoredChunks.get(index);
                    DocumentChunk chunk = scoredChunk.chunk();
                    int rank = index + 1;

                    if (retrievalQueryId != null) {
                        RetrievalResult result = new RetrievalResult();
                        result.setRetrievalQueryId(retrievalQueryId);
                        result.setChunkId(chunk.getChunkId());
                        result.setDocumentId(chunk.getDocumentId());
                        result.setResultRank(rank);
                        result.setSimilarityScore(scoredChunk.score());
                        result.setRerankScore(scoredChunk.score());
                        result.setCreatedAt(now);
                        retrievalResultRepository.save(result);
                    }

                    RagDto.RetrievedChunk response = new RagDto.RetrievedChunk();
                    response.chunkId = chunk.getChunkId();
                    response.documentId = chunk.getDocumentId();
                    response.rank = rank;
                    response.similarityScore = scoredChunk.score();
                    response.content = chunk.getContent();
                    return response;
                })
                .toList();
    }

    private void validateRetrievalRequest(RagDto.RetrievalRequest request) {
        if (request == null || request.workspaceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId is required.");
        }
        if (request.queryText == null || request.queryText.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "queryText is required.");
        }
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {
    }
}
