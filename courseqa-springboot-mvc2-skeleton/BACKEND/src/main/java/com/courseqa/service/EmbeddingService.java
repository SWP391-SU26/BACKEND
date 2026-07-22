package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.entity.ChunkEmbedding;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.repository.ChunkEmbeddingRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.EmbeddingModelRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmbeddingService {
    public static final String PRODUCTION_MODEL = "BAAI/bge-m3";
    private static final int DEFAULT_DIMENSION = 1024;
    private static final int EMBEDDING_BATCH_SIZE = 32;
    private static final Set<String> SEARCH_STOPWORDS = Set.of(
            "trong", "tai", "lieu", "document", "file", "co", "khong", "cua", "cho",
            "voi", "hay", "la", "tu", "mot", "cac", "nhung", "nay", "do", "duoc",
            "the", "and", "or", "not", "with", "from", "this", "that"
    );

    private final EmbeddingModelRepository embeddingModelRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;
    private final AIClientService aiClientService;
    private final ObjectMapper objectMapper;

    public EmbeddingService(
            EmbeddingModelRepository embeddingModelRepository,
            DocumentChunkRepository documentChunkRepository,
            ChunkEmbeddingRepository chunkEmbeddingRepository,
            AIClientService aiClientService,
            ObjectMapper objectMapper
    ) {
        this.embeddingModelRepository = embeddingModelRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
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
        String modelVersion = "configured";
        List<DocumentChunk> missing = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            if (chunkEmbeddingRepository.findByChunkIdAndEmbeddingModelId(
                    chunk.getChunkId(), model.getEmbeddingModelId()).isPresent()) {
                skipped++;
            } else {
                missing.add(chunk);
            }
        }

        for (int offset = 0; offset < missing.size(); offset += EMBEDDING_BATCH_SIZE) {
            List<DocumentChunk> batch = missing.subList(offset, Math.min(offset + EMBEDDING_BATCH_SIZE, missing.size()));
            PythonAiDto.EmbeddingBatchRequest embeddingRequest = new PythonAiDto.EmbeddingBatchRequest();
            embeddingRequest.texts = batch.stream().map(DocumentChunk::getContent).toList();
            PythonAiDto.EmbeddingBatchResponse response = aiClientService.embedBatch(embeddingRequest);
            validateEmbeddingResponse(response, batch.size(), model);
            modelVersion = defaultString(response.revision, "configured");
            for (int index = 0; index < batch.size(); index++) {
                ChunkEmbedding embedding = new ChunkEmbedding();
                embedding.setChunkId(batch.get(index).getChunkId());
                embedding.setEmbeddingModelId(model.getEmbeddingModelId());
                embedding.setEmbeddingJson(toJsonVector(response.vectors.get(index)));
                embedding.setDimension(response.dimension);
                embedding.setCreatedAt(LocalDateTime.now());
                chunkEmbeddingRepository.save(embedding);
                created++;
            }
        }

        RagDto.PrepareEmbeddingsResponse response = new RagDto.PrepareEmbeddingsResponse();
        response.embeddingModelId = model.getEmbeddingModelId();
        response.modelName = model.getModelName();
        response.modelVersion = modelVersion;
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

        return embeddingModelRepository.findByModelNameIgnoreCase(PRODUCTION_MODEL)
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

    public double[] embedText(String text, int dimension) {
        PythonAiDto.EmbeddingQueryRequest request = new PythonAiDto.EmbeddingQueryRequest();
        request.text = text;
        PythonAiDto.EmbeddingQueryResponse response = aiClientService.embedQuery(request);
        if (response == null || response.vector == null || response.vector.isEmpty()) {
            throw new IllegalStateException("Python embedding service returned an empty query vector.");
        }
        if (!PRODUCTION_MODEL.equalsIgnoreCase(response.model)) {
            throw new IllegalStateException("Expected " + PRODUCTION_MODEL + " but Python returned " + response.model + ".");
        }
        return response.vector.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public double cosineVectorScore(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0.0;
        }

        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public double exactTokenOverlapScore(String query, String content) {
        List<String> queryTokens = tokenize(query).stream()
                .filter(this::isMeaningfulSearchToken)
                .toList();
        if (queryTokens.isEmpty() || content == null || content.isBlank()) {
            return 0.0;
        }

        String normalizedContent = content.toLowerCase(Locale.ROOT);
        for (String token : queryTokens) {
            if (normalizedContent.contains(token)) {
                return containsJapanese(token) ? 0.95 : 0.75;
            }
        }
        return 0.0;
    }

    public double[] parseJsonVector(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return new double[0];
        }

        try {
            List<Double> values = objectMapper.readValue(embeddingJson, new TypeReference<List<Double>>() { });
            return values.stream().mapToDouble(Double::doubleValue).toArray();
        } catch (JsonProcessingException exception) {
            return new double[0];
        }
    }

    private EmbeddingModel createDefaultModel() {
        EmbeddingModel model = new EmbeddingModel();
        model.setModelName(PRODUCTION_MODEL);
        model.setProvider("sentence-transformers");
        model.setDimension(DEFAULT_DIMENSION);
        model.setIsLocal(true);
        model.setDescription("Production multilingual embedding model served by the Python AI service.");
        model.setConfigJson("{\"normalized\":true}");
        model.setIsActive(true);
        model.setCreatedAt(LocalDateTime.now());
        return embeddingModelRepository.save(model);
    }

    private String toJsonVector(List<Double> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize embedding vector.", exception);
        }
    }

    private void validateEmbeddingResponse(PythonAiDto.EmbeddingBatchResponse response, int expected,
            EmbeddingModel model) {
        if (response == null || response.vectors == null || response.vectors.size() != expected) {
            throw new IllegalStateException("Python embedding service returned an incomplete batch.");
        }
        if (!PRODUCTION_MODEL.equalsIgnoreCase(response.model)) {
            throw new IllegalStateException("Expected " + PRODUCTION_MODEL + " but Python returned " + response.model + ".");
        }
        int dimension = response.dimension == null ? 0 : response.dimension;
        if (dimension <= 0 || response.vectors.stream().anyMatch(vector -> vector == null || vector.size() != dimension)) {
            throw new IllegalStateException("Python embedding vectors have inconsistent dimensions.");
        }
        if (!Integer.valueOf(dimension).equals(model.getDimension())) {
            model.setDimension(dimension);
            embeddingModelRepository.save(model);
        }
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

    private boolean isMeaningfulSearchToken(String token) {
        if (containsJapanese(token)) {
            return true;
        }
        String normalized = stripMarks(token);
        if (SEARCH_STOPWORDS.contains(normalized)) {
            return false;
        }
        return normalized.length() >= 4 || normalized.matches(".*[a-z].*\\d.*|.*\\d.*[a-z].*");
    }

    private String stripMarks(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        return Normalizer.normalize(token, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsJapanese(String token) {
        return token.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x3040 && codePoint <= 0x30FF) ||
                (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
        );
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
