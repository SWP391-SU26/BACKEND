package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.ChunkEmbedding;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.repository.ChunkEmbeddingRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.EmbeddingModelRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmbeddingService {
    private static final int DEFAULT_DIMENSION = 128;

    private final EmbeddingModelRepository embeddingModelRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;

    public EmbeddingService(
            EmbeddingModelRepository embeddingModelRepository,
            DocumentChunkRepository documentChunkRepository,
            ChunkEmbeddingRepository chunkEmbeddingRepository
    ) {
        this.embeddingModelRepository = embeddingModelRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
    }

    public List<RagDto.EmbeddingModelResponse> getEmbeddingModels() {
        return embeddingModelRepository.findAll().stream()
                .map(RagDto.EmbeddingModelResponse::fromEntity)
                .toList();
    }

    @Transactional
    public RagDto.EmbeddingModelResponse createEmbeddingModel(RagDto.CreateEmbeddingModelRequest request) {
        if (request == null || request.modelName == null || request.modelName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelName is required.");
        }

        String modelName = request.modelName.trim();
        if (embeddingModelRepository.existsByModelName(modelName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Embedding model already exists.");
        }

        EmbeddingModel model = new EmbeddingModel();
        model.setModelName(modelName);
        model.setProvider(defaultString(request.provider, "Local Demo"));
        model.setDimension(request.dimension == null || request.dimension <= 0 ? DEFAULT_DIMENSION : request.dimension);
        model.setIsLocal(request.isLocal == null ? true : request.isLocal);
        model.setDescription(request.description);
        model.setConfigJson(defaultString(request.configJson, "{}"));
        model.setIsActive(request.isActive == null ? true : request.isActive);
        model.setCreatedAt(LocalDateTime.now());
        return RagDto.EmbeddingModelResponse.fromEntity(embeddingModelRepository.save(model));
    }

    @Transactional
    public RagDto.PrepareEmbeddingsResponse prepareEmbeddings(RagDto.PrepareEmbeddingsRequest request) {
        if (request == null || (request.workspaceId == null && request.documentId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId or documentId is required.");
        }

        EmbeddingModel model = resolveModel(request.embeddingModelId);
        List<DocumentChunk> chunks = request.documentId != null
                ? documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(request.documentId)
                : documentChunkRepository.findByWorkspaceIdOrderByCreatedAtAsc(request.workspaceId);

        int created = 0;
        int skipped = 0;
        for (DocumentChunk chunk : chunks) {
            boolean exists = chunkEmbeddingRepository
                    .findByChunkIdAndEmbeddingModelId(chunk.getChunkId(), model.getEmbeddingModelId())
                    .isPresent();
            if (exists) {
                skipped++;
                continue;
            }

            ChunkEmbedding embedding = new ChunkEmbedding();
            embedding.setChunkId(chunk.getChunkId());
            embedding.setEmbeddingModelId(model.getEmbeddingModelId());
            embedding.setEmbeddingJson(toJsonVector(createHashedVector(chunk.getContent(), model.getDimension())));
            embedding.setDimension(model.getDimension());
            embedding.setCreatedAt(LocalDateTime.now());
            chunkEmbeddingRepository.save(embedding);
            created++;
        }

        RagDto.PrepareEmbeddingsResponse response = new RagDto.PrepareEmbeddingsResponse();
        response.embeddingModelId = model.getEmbeddingModelId();
        response.totalChunks = chunks.size();
        response.createdEmbeddings = created;
        response.skippedExisting = skipped;
        return response;
    }

    public EmbeddingModel resolveModel(UUID embeddingModelId) {
        if (embeddingModelId != null) {
            return embeddingModelRepository.findById(embeddingModelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Embedding model not found."));
        }

        return embeddingModelRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .findFirst()
                .orElseGet(this::createDefaultModel);
    }

    public double cosineKeywordScore(String query, String content) {
        Map<String, Long> queryVector = termCounts(query);
        Map<String, Long> contentVector = termCounts(content);
        if (queryVector.isEmpty() || contentVector.isEmpty()) {
            return 0.0;
        }

        double dot = 0.0;
        for (Map.Entry<String, Long> entry : queryVector.entrySet()) {
            dot += entry.getValue() * contentVector.getOrDefault(entry.getKey(), 0L);
        }

        double queryNorm = Math.sqrt(queryVector.values().stream().mapToDouble(value -> value * value).sum());
        double contentNorm = Math.sqrt(contentVector.values().stream().mapToDouble(value -> value * value).sum());
        return queryNorm == 0.0 || contentNorm == 0.0 ? 0.0 : dot / (queryNorm * contentNorm);
    }

    private EmbeddingModel createDefaultModel() {
        EmbeddingModel model = new EmbeddingModel();
        model.setModelName("keyword-hash-128");
        model.setProvider("Local Demo");
        model.setDimension(DEFAULT_DIMENSION);
        model.setIsLocal(true);
        model.setDescription("Deterministic keyword hashing model for skeleton RAG preparation.");
        model.setConfigJson("{}");
        model.setIsActive(true);
        model.setCreatedAt(LocalDateTime.now());
        return embeddingModelRepository.save(model);
    }

    private double[] createHashedVector(String text, int dimension) {
        int safeDimension = Math.max(1, dimension);
        double[] vector = new double[safeDimension];
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), safeDimension);
            vector[index] += 1.0;
        }

        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0) {
            return vector;
        }

        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
        return vector;
    }

    private String toJsonVector(double[] vector) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(String.format(Locale.US, "%.6f", vector[index]));
        }
        json.append(']');
        return json.toString();
    }

    private Map<String, Long> termCounts(String text) {
        return tokenize(text).stream()
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()));
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).stream()
                .filter(token -> !token.isBlank())
                .toList();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
