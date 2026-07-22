package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.LearningScopeDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.EvaluationDatasetDocument;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.entity.ExperimentResult;
import com.courseqa.model.entity.ExperimentMetricAggregate;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.EvaluationDatasetDocumentRepository;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.EvaluationQuestionRepository;
import com.courseqa.repository.ExperimentRepository;
import com.courseqa.repository.ExperimentResultRepository;
import com.courseqa.repository.ExperimentMetricAggregateRepository;
import com.courseqa.repository.EmbeddingModelRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvaluationService {
    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "FAILED");
    private static final int BENCHMARK_BATCH_SIZE = 4;
    private static final int BENCHMARK_MAX_INPUT_TOKENS = 448;
    private static final int BENCHMARK_MAX_NEW_TOKENS = 64;
    private static final String BENCHMARK_PROFILE_VERSION = "full-batch-v1";

    private final EvaluationDatasetRepository datasets;
    private final EvaluationDatasetDocumentRepository datasetDocuments;
    private final EvaluationQuestionRepository questions;
    private final ExperimentRepository experiments;
    private final ExperimentResultRepository results;
    private final ExperimentMetricAggregateRepository aggregates;
    private final EmbeddingModelRepository embeddingModels;
    private final CourseRepository courses;
    private final CourseDocumentRepository documents;
    private final CourseWorkspaceRepository workspaces;
    private final LearningScopeService learningScopeService;
    private final ChatService chatService;
    private final AIClientService aiClientService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;
    private final Set<UUID> cancellationRequests = ConcurrentHashMap.newKeySet();

    public EvaluationService(
            EvaluationDatasetRepository datasets,
            EvaluationDatasetDocumentRepository datasetDocuments,
            EvaluationQuestionRepository questions,
            ExperimentRepository experiments,
            ExperimentResultRepository results,
            ExperimentMetricAggregateRepository aggregates,
            EmbeddingModelRepository embeddingModels,
            CourseRepository courses,
            CourseDocumentRepository documents,
            CourseWorkspaceRepository workspaces,
            LearningScopeService learningScopeService,
            ChatService chatService,
            AIClientService aiClientService,
            @Qualifier("evaluationTaskExecutor") TaskExecutor taskExecutor,
            ObjectMapper objectMapper) {
        this.datasets = datasets;
        this.datasetDocuments = datasetDocuments;
        this.questions = questions;
        this.experiments = experiments;
        this.results = results;
        this.aggregates = aggregates;
        this.embeddingModels = embeddingModels;
        this.courses = courses;
        this.documents = documents;
        this.workspaces = workspaces;
        this.learningScopeService = learningScopeService;
        this.chatService = chatService;
        this.aiClientService = aiClientService;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    public List<LearningScopeDto.SemesterScope> getScopes(UUID userId) {
        return learningScopeService.scope(userId, true);
    }

    public EvaluationDataset createDataset(String datasetName, UUID courseId, List<UUID> requestedDocumentIds,
            UUID createdBy) {
        Course course = learningScopeService.requireAccessibleCourse(courseId, createdBy, true);
        CourseWorkspace workspace = learningScopeService.requireActiveWorkspace(courseId);
        List<UUID> documentIds = requestedDocumentIds == null
                ? List.of()
                : requestedDocumentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (documentIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one processed document.");
        }
        List<CourseDocument> selected = documents.findAllById(documentIds);
        if (selected.size() != documentIds.size()
                || selected.stream().anyMatch(document -> !courseId.equals(document.getCourseId())
                        || !"PROCESSED".equals(document.getProcessingStatus())
                        || !"INDEXED".equals(document.getIndexingStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "All selected documents must be processed and indexed documents from the selected course.");
        }

        LocalDateTime now = LocalDateTime.now();
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setDatasetName(datasetName.trim());
        dataset.setDatasetVersion("v1");
        dataset.setCourseId(courseId);
        dataset.setWorkspaceId(workspace.getWorkspaceId());
        dataset.setSemesterWorkspaceId(course.getSemesterWorkspaceId());
        dataset.setStatus("DRAFT");
        dataset.setCreatedBy(createdBy);
        dataset.setCreatedAt(now);
        dataset.setUpdatedAt(now);
        dataset = datasets.save(dataset);
        for (UUID documentId : documentIds) {
            datasetDocuments.save(new EvaluationDatasetDocument(dataset.getDatasetId(), documentId));
        }
        dataset.setDocumentIds(documentIds);
        return dataset;
    }

    public List<EvaluationDataset> listDatasets() {
        return datasets.findAll().stream()
                .sorted(Comparator.comparing(EvaluationDataset::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .peek(this::attachDocumentIds)
                .toList();
    }

    public List<CourseDocument> getDatasetDocuments(UUID datasetId) {
        requireDataset(datasetId);
        List<UUID> ids = snapshotDocumentIds(datasetId);
        Map<UUID, CourseDocument> byId = new HashMap<>();
        documents.findAllById(ids).forEach(document -> byId.put(document.getDocumentId(), document));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public EvaluationQuestion addQuestion(UUID datasetId, String question, String groundTruth) {
        EvaluationDataset dataset = requireEditableDataset(datasetId);
        EvaluationQuestion item = new EvaluationQuestion();
        item.setDatasetId(datasetId);
        item.setCourseId(dataset.getCourseId());
        item.setQuestionNo(questions.findByDatasetId(datasetId).size() + 1);
        item.setQuestionText(question.trim());
        item.setGroundTruthAnswer(groundTruth.trim());
        item.setQuestionType("FACTUAL");
        item.setDifficulty("MEDIUM");
        item.setCreatedAt(LocalDateTime.now());
        dataset.setUpdatedAt(LocalDateTime.now());
        datasets.save(dataset);
        return questions.save(item);
    }

    public List<EvaluationQuestion> getQuestions(UUID datasetId) {
        requireDataset(datasetId);
        return questions.findByDatasetId(datasetId).stream()
                .sorted(Comparator.comparing(EvaluationQuestion::getQuestionNo))
                .toList();
    }

    public Map<String, Object> importQuestions(UUID datasetId, MultipartFile file) {
        EvaluationDataset dataset = requireEditableDataset(datasetId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required.");
        }
        try {
            byte[] bytes = file.getBytes();
            List<List<String>> rows = parseCsv(stripUtf8Bom(new String(bytes, StandardCharsets.UTF_8)));
            if (rows.isEmpty()) throw new IllegalArgumentException("CSV file has no rows.");
            Map<String, Integer> headers = headerIndex(rows.get(0));
            Integer questionIndex = firstHeader(headers, "question", "question_text");
            Integer answerIndex = firstHeader(headers, "expected_answer", "ground_truth_answer", "answer");
            if (questionIndex == null || answerIndex == null) {
                throw new IllegalArgumentException("CSV must include question and expected_answer columns.");
            }
            Integer pageIndex = firstHeader(headers, "expected_page", "page");
            Integer categoryIndex = firstHeader(headers, "category", "question_type", "type");
            Integer difficultyIndex = firstHeader(headers, "difficulty");
            int next = questions.findByDatasetId(datasetId).size() + 1;
            int skipped = 0;
            List<EvaluationQuestion> imported = new ArrayList<>();
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                String questionText = cell(row, questionIndex).trim();
                String answer = cell(row, answerIndex).trim();
                if (questionText.isEmpty() || answer.isEmpty()) { skipped++; continue; }
                EvaluationQuestion item = new EvaluationQuestion();
                item.setDatasetId(datasetId);
                item.setCourseId(dataset.getCourseId());
                item.setQuestionNo(next++);
                item.setQuestionText(questionText);
                item.setGroundTruthAnswer(answer);
                item.setExpectedPage(parseOptionalInt(cell(row, pageIndex)));
                item.setQuestionType(defaultIfBlank(cell(row, categoryIndex), "FACTUAL"));
                item.setDifficulty(defaultIfBlank(cell(row, difficultyIndex), "MEDIUM"));
                item.setCreatedAt(LocalDateTime.now());
                imported.add(item);
            }
            questions.saveAll(imported);
            dataset.setUpdatedAt(LocalDateTime.now());
            datasets.save(dataset);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("datasetId", datasetId);
            response.put("importedCount", imported.size());
            response.put("skippedCount", skipped);
            response.put("sourceChecksum", sha256(bytes));
            response.put("filename", file.getOriginalFilename());
            return response;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read CSV file: " + exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    public Experiment createExperiment(UUID datasetId, String name, String type, String llmModel,
            UUID embeddingModelId, String chunkingStrategy, Integer topK, Double similarityThreshold,
            Integer randomSeed, String configJson, UUID createdBy) {
        EvaluationDataset dataset = requireDataset(datasetId);
        if ("INVALID".equals(dataset.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, defaultIfBlank(dataset.getValidationError(),
                    "This legacy dataset is invalid."));
        }
        String normalizedType = normalizeExperimentType(type);
        LocalDateTime now = LocalDateTime.now();
        Experiment experiment = new Experiment();
        experiment.setDatasetId(datasetId);
        experiment.setCourseId(dataset.getCourseId());
        experiment.setWorkspaceId(dataset.getWorkspaceId());
        experiment.setExperimentName(name.trim());
        experiment.setExperimentType(normalizedType);
        experiment.setLlmModel(llmModel.trim());
        if (embeddingModelId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embeddingModelId is required.");
        }
        EmbeddingModel embeddingModel = embeddingModels.findById(embeddingModelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Embedding model not found."));
        if (!EmbeddingService.PRODUCTION_MODEL.equalsIgnoreCase(embeddingModel.getModelName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Current benchmark only supports " + EmbeddingService.PRODUCTION_MODEL + ".");
        }
        String strategy = chunkingStrategy == null ? "" : chunkingStrategy.trim().toUpperCase(Locale.ROOT);
        if (!"PARAGRAPH_700_120".equals(strategy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Current benchmark only supports PARAGRAPH_700_120 chunking.");
        }
        experiment.setEmbeddingModelId(embeddingModelId);
        experiment.setChunkingStrategy(strategy);
        experiment.setTopK(topK == null ? 5 : topK);
        experiment.setSimilarityThreshold(similarityThreshold == null ? 0.25 : similarityThreshold);
        experiment.setRandomSeed(randomSeed == null ? 42 : randomSeed);
        experiment.setTemperature(0.2);
        experiment.setMetricStandard("OFFICIAL_RAGAS");
        experiment.setEvaluatorConfigJson("{\"ragasVersion\":\"0.4.3\",\"judge\":\"gpt-4o-mini\","
                + "\"embedding\":\"text-embedding-3-small\",\"concurrency\":2,\"maxRetries\":3}");
        experiment.setConfigJson(configJson == null || configJson.isBlank() ? "{}" : configJson);
        experiment.setCreatedBy(createdBy);
        experiment.setStatus("PENDING");
        experiment.setProgress(0);
        experiment.setSuccessCount(0);
        experiment.setFailureCount(0);
        experiment.setCreatedAt(now);
        experiment.setUpdatedAt(now);
        return experiments.save(experiment);
    }

    public List<Experiment> listExperiments() {
        return experiments.findAll().stream()
                .sorted(Comparator.comparing(Experiment::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public Experiment getExperiment(UUID experimentId) {
        return experiments.findById(experimentId)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + experimentId));
    }

    public List<ExperimentResult> getResults(UUID experimentId) {
        getExperiment(experimentId);
        List<ExperimentResult> items = results.findByExperimentId(experimentId);
        Map<UUID, EvaluationQuestion> byId = new HashMap<>();
        questions.findAllById(items.stream().map(ExperimentResult::getEvaluationQuestionId).toList())
                .forEach(question -> byId.put(question.getEvaluationQuestionId(), question));
        items.forEach(result -> {
            EvaluationQuestion question = byId.get(result.getEvaluationQuestionId());
            if (question != null) {
                result.setQuestionText(question.getQuestionText());
                result.setGroundTruthAnswer(question.getGroundTruthAnswer());
            }
        });
        return items;
    }

    public List<ExperimentMetricAggregate> getAggregates(UUID experimentId) {
        getExperiment(experimentId);
        return aggregates.findByExperimentIdOrderByMetricNameAsc(experimentId);
    }

    public synchronized Experiment startBenchmark(UUID experimentId) {
        Experiment experiment = getExperiment(experimentId);
        if (!"PENDING".equals(experiment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An experiment is immutable after it has been queued. Create a new experiment to rerun it.");
        }
        Map<String, Object> readiness = readiness(experiment.getDatasetId(), experiment.getExperimentType());
        if (!Boolean.TRUE.equals(readiness.get("ready"))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) readiness.get("blockers");
            String message = blockers.stream().map(item -> String.valueOf(item.get("message")))
                    .reduce((left, right) -> left + " " + right).orElse("Experiment is not ready.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
        EvaluationDataset dataset = freezeDataset(experiment.getDatasetId());
        cancellationRequests.remove(experimentId);
        results.deleteByExperimentId(experimentId);
        experiment.setDatasetChecksum(dataset.getChecksum());
        experiment.setConfigJson(withBenchmarkProfile(experiment.getConfigJson(), getQuestions(dataset.getDatasetId()).size()));
        freezeExperimentConfig(experiment, dataset);
        experiment.setStatus("QUEUED");
        experiment.setProgress(0);
        experiment.setSuccessCount(0);
        experiment.setFailureCount(0);
        experiment.setErrorMessage(null);
        experiment.setStartedAt(null);
        experiment.setCompletedAt(null);
        experiment.setUpdatedAt(LocalDateTime.now());
        experiment = experiments.save(experiment);
        UUID queuedId = experimentId;
        taskExecutor.execute(() -> executeBenchmark(queuedId));
        return experiment;
    }

    public synchronized Experiment cancelBenchmark(UUID experimentId) {
        Experiment experiment = getExperiment(experimentId);
        if ("CANCELLED".equals(experiment.getStatus())) return experiment;
        if (!Set.of("QUEUED", "RUNNING").contains(experiment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a queued or running experiment can be cancelled.");
        }
        cancellationRequests.add(experimentId);
        experiment.setStatus("CANCELLED");
        experiment.setErrorMessage(null);
        experiment.setCompletedAt(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());
        return experiments.save(experiment);
    }

    public Map<String, Object> readiness(UUID datasetId, String requestedType) {
        EvaluationDataset dataset = requireDataset(datasetId);
        String type = normalizeExperimentType(requestedType);
        List<Map<String, Object>> checks = new ArrayList<>();
        List<Map<String, Object>> blockers = new ArrayList<>();
        addCheck(checks, blockers, "dataset", !"INVALID".equals(dataset.getStatus()),
                "Dataset is valid.", defaultIfBlank(dataset.getValidationError(), "Dataset is invalid."));
        try {
            learningScopeService.requireAccessibleCourse(dataset.getCourseId(), dataset.getCreatedBy(), true);
            addCheck(checks, blockers, "course", true, "Semester and course are active.", "");
        } catch (RuntimeException exception) {
            addCheck(checks, blockers, "course", false, "", "Semester or course is archived/inactive.");
        }
        List<CourseDocument> snapshot = getDatasetDocuments(datasetId);
        boolean validDocuments = !snapshot.isEmpty() && snapshot.stream().allMatch(document ->
                dataset.getCourseId().equals(document.getCourseId())
                        && "PROCESSED".equals(document.getProcessingStatus())
                        && "INDEXED".equals(document.getIndexingStatus()));
        addCheck(checks, blockers, "documents", validDocuments,
                snapshot.size() + " processed document(s) are frozen in the snapshot.",
                "Dataset needs at least one processed document snapshot.");
        int questionCount = questions.findByDatasetId(datasetId).size();
        addCheck(checks, blockers, "questions", questionCount == 50,
                "Exactly 50 benchmark questions are ready.",
                "Official benchmark requires exactly 50 questions; current count is " + questionCount + ".");
        int leakageCount = trainingLeakageCount(getQuestions(datasetId));
        addCheck(checks, blockers, "trainingLeakage", leakageCount == 0,
                "No benchmark question overlaps LoRA train/validation data.",
                leakageCount + " benchmark question(s) overlap LoRA train/validation data.");

        Map<String, Object> model = modelReadiness();
        boolean modelReady = "FINE_TUNED".equals(type)
                ? Boolean.TRUE.equals(model.get("inferenceReady"))
                : Boolean.TRUE.equals(model.get("generationReady"));
        addCheck(checks, blockers, "model", modelReady,
                "Strict " + type + " generation is ready.",
                "Strict " + type + " model is not ready. Check the Python model status.");
        boolean evaluatorReady = booleanValue(model, "openai_configured", "openaiConfigured");
        addCheck(checks, blockers, "officialRagas", evaluatorReady,
                "OpenAI evaluator is configured for Official RAGAS.",
                "OPENAI_API_KEY is required for Official RAGAS evaluation.");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasetId", datasetId);
        response.put("experimentType", type);
        response.put("ready", blockers.isEmpty());
        response.put("checks", checks);
        response.put("blockers", blockers);
        response.put("model", model);
        return response;
    }

    public Map<String, Object> modelReadiness() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Map<String, Object> raw = aiClientService.getModelStatus();
            boolean inferenceReady = booleanValue(raw, "inference_ready", "inferenceReady", "local_model_loaded");
            boolean trainingReady = booleanValue(raw, "training_ready", "trainingReady");
            boolean generationReady = booleanValue(raw, "generation_ready", "generationReady") || inferenceReady;
            response.put("reachable", true);
            response.put("inferenceReady", inferenceReady);
            response.put("trainingReady", trainingReady);
            response.put("generationReady", generationReady);
            response.put("adapterDir", firstValue(raw, "adapter_dir", "adapter_path"));
            response.put("details", raw);
        } catch (RuntimeException exception) {
            response.put("reachable", false);
            response.put("inferenceReady", false);
            response.put("trainingReady", false);
            response.put("generationReady", false);
            response.put("error", exception.getMessage());
        }
        return response;
    }

    public Map<String, Object> comparison(UUID datasetId, UUID ragExperimentId, UUID fineExperimentId) {
        EvaluationDataset dataset = requireDataset(datasetId);
        Experiment rag = getExperiment(ragExperimentId);
        Experiment fine = getExperiment(fineExperimentId);
        if (!datasetId.equals(rag.getDatasetId()) || !datasetId.equals(fine.getDatasetId())
                || !"RAG".equals(normalizeExperimentType(rag.getExperimentType()))
                || !"FINE_TUNED".equals(normalizeExperimentType(fine.getExperimentType()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select one RAG run and one Fine-tuned run from the selected dataset.");
        }
        if (rag.getDatasetChecksum() == null || !rag.getDatasetChecksum().equals(fine.getDatasetChecksum())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Experiments cannot be compared because their dataset checksums differ.");
        }
        Object ragProfile = benchmarkProfile(rag);
        Object fineProfile = benchmarkProfile(fine);
        if (ragProfile == null || !Objects.equals(ragProfile, fineProfile)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Experiments cannot be compared because their benchmark profiles differ.");
        }
        List<ExperimentResult> ragResults = getResults(ragExperimentId);
        List<ExperimentResult> fineResults = getResults(fineExperimentId);
        if (!"OFFICIAL_RAGAS".equals(rag.getMetricStandard())
                || !"OFFICIAL_RAGAS".equals(fine.getMetricStandard())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "LOCAL_PROXY experiments cannot be compared with Official RAGAS experiments.");
        }
        boolean officialRagas = !ragResults.isEmpty() && !fineResults.isEmpty()
                && java.util.stream.Stream.concat(ragResults.stream(), fineResults.stream())
                        .allMatch(result -> "SUCCESS".equals(result.getRagasStatus()));
        Map<UUID, ExperimentResult> fineByQuestion = fineResults.stream().collect(
                java.util.stream.Collectors.toMap(ExperimentResult::getEvaluationQuestionId, Function.identity(),
                        (left, right) -> left));
        List<Map<String, Object>> perQuestion = new ArrayList<>();
        for (ExperimentResult ragResult : ragResults) {
            ExperimentResult fineResult = fineByQuestion.get(ragResult.getEvaluationQuestionId());
            if (fineResult == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", ragResult.getEvaluationQuestionId());
            row.put("question", ragResult.getQuestionText());
            row.put("groundTruth", ragResult.getGroundTruthAnswer());
            row.put("ragAnswer", ragResult.getGeneratedAnswer());
            row.put("fineTunedAnswer", fineResult.getGeneratedAnswer());
            row.put("ragAnswerCorrectness", ragResult.getAnswerCorrectness());
            row.put("fineTunedAnswerCorrectness", fineResult.getAnswerCorrectness());
            row.put("answerCorrectnessDelta", nullableDelta(fineResult.getAnswerCorrectness(), ragResult.getAnswerCorrectness()));
            row.put("ragAnswerRelevance", ragResult.getAnswerRelevance());
            row.put("fineTunedAnswerRelevance", fineResult.getAnswerRelevance());
            row.put("ragSemanticSimilarity", ragResult.getSemanticSimilarity());
            row.put("fineTunedSemanticSimilarity", fineResult.getSemanticSimilarity());
            row.put("ragFaithfulness", ragResult.getFaithfulness());
            row.put("fineTunedFaithfulness", null);
            row.put("ragContextPrecision", ragResult.getContextPrecision());
            row.put("fineTunedContextPrecision", null);
            row.put("ragContextRecall", ragResult.getContextRecall());
            row.put("fineTunedContextRecall", null);
            row.put("ragLatencyMs", ragResult.getLatencyMs());
            row.put("fineTunedLatencyMs", fineResult.getLatencyMs());
            row.put("ragBatchLatencyMs", ragResult.getBatchLatencyMs());
            row.put("fineTunedBatchLatencyMs", fineResult.getBatchLatencyMs());
            row.put("ragEffectiveLatencyMs", ragResult.getEffectiveLatencyMs());
            row.put("fineTunedEffectiveLatencyMs", fineResult.getEffectiveLatencyMs());
            row.put("ragBatchSize", ragResult.getBatchSize());
            row.put("fineTunedBatchSize", fineResult.getBatchSize());
            row.put("ragInputTokens", ragResult.getInputTokens());
            row.put("ragOutputTokens", ragResult.getOutputTokens());
            row.put("fineTunedInputTokens", fineResult.getInputTokens());
            row.put("fineTunedOutputTokens", fineResult.getOutputTokens());
            row.put("ragContexts", parseJsonCollection(ragResult.getRetrievedContextJson()));
            row.put("ragCitations", parseJsonCollection(ragResult.getCitationsJson()));
            row.put("fineTunedContexts", List.of());
            row.put("fineTunedCitations", List.of());
            row.put("ragError", ragResult.getErrorMessage());
            row.put("fineTunedError", fineResult.getErrorMessage());
            perQuestion.add(row);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasetId", datasetId);
        response.put("datasetChecksum", rag.getDatasetChecksum());
        Map<String, Object> datasetMetadata = new LinkedHashMap<>();
        datasetMetadata.put("datasetId", dataset.getDatasetId());
        datasetMetadata.put("name", dataset.getDatasetName());
        datasetMetadata.put("version", dataset.getDatasetVersion());
        datasetMetadata.put("status", dataset.getStatus());
        datasetMetadata.put("courseId", dataset.getCourseId());
        datasetMetadata.put("semesterWorkspaceId", dataset.getSemesterWorkspaceId());
        datasetMetadata.put("questionCount", getQuestions(datasetId).size());
        datasetMetadata.put("documentCount", snapshotDocumentIds(datasetId).size());
        datasetMetadata.put("checksum", rag.getDatasetChecksum());
        response.put("dataset", datasetMetadata);
        response.put("metricStandard", "OFFICIAL_RAGAS");
        response.put("formulaVersion", "ragas-0.4.3");
        response.put("benchmarkProfile", ragProfile);
        response.put("methodology", Map.of(
                "officialRagas", officialRagas,
                "label", officialRagas ? "Official RAGAS" : "Official RAGAS incomplete",
                "evaluatorModel", "gpt-4o-mini",
                "evaluatorEmbeddingModel", "text-embedding-3-small"));
        response.put("ragExperiment", experimentSummary(rag, ragResults, true));
        response.put("fineTunedExperiment", experimentSummary(fine, fineResults, false));
        response.put("perQuestion", perQuestion);
        return response;
    }

    private void executeBenchmark(UUID experimentId) {
        try {
            if (!markRunningIfQueued(experimentId)) return;
            executeBenchmarkUntilCancelled(experimentId);
        } finally {
            cancellationRequests.remove(experimentId);
        }
    }

    private void executeBenchmarkUntilCancelled(UUID experimentId) {
        if (isCancellationRequested(experimentId)) return;
        Experiment experiment = getExperiment(experimentId);
        EvaluationDataset dataset = requireDataset(experiment.getDatasetId());
        List<EvaluationQuestion> benchmarkQuestions = getQuestions(dataset.getDatasetId());
        List<UUID> documentIds = snapshotDocumentIds(dataset.getDatasetId());
        int success = 0;
        int failure = 0;
        List<String> errorMessages = new ArrayList<>();
        try {
            UUID sessionId = chatService.createEvaluationSession(experiment.getCreatedBy(), dataset.getCourseId(),
                    dataset.getSemesterWorkspaceId(), documentIds).getChatSessionId();
            for (int index = 0; index < benchmarkQuestions.size(); index += BENCHMARK_BATCH_SIZE) {
                if (isCancellationRequested(experimentId)) return;
                int end = Math.min(index + BENCHMARK_BATCH_SIZE, benchmarkQuestions.size());
                List<EvaluationQuestion> batch = benchmarkQuestions.subList(index, end);
                long startedAt = System.nanoTime();
                try {
                    String mode = "FINE_TUNED".equals(experiment.getExperimentType()) ? "FINE_TUNED" : "RAG";
                    List<ChatDto.AskResponse> answers = chatService.askEvaluationBatch(sessionId,
                            batch.stream().map(EvaluationQuestion::getQuestionText).toList(), mode);
                    if (isCancellationRequested(experimentId)) return;
                    if (answers.size() != batch.size()) {
                        throw new IllegalStateException("AI batch returned " + answers.size()
                                + " answers for " + batch.size() + " questions.");
                    }
                    int batchLatency = elapsedMs(startedAt);
                    int effectiveLatency = Math.max(1, batchLatency / batch.size());
                    for (int offset = 0; offset < batch.size(); offset++) {
                        persistSuccess(experiment, batch.get(offset), answers.get(offset),
                                batchLatency, effectiveLatency, batch.size());
                        success++;
                    }
                } catch (RuntimeException exception) {
                    if (isCancellationRequested(experimentId)) return;
                    int batchLatency = elapsedMs(startedAt);
                    int effectiveLatency = Math.max(1, batchLatency / batch.size());
                    for (EvaluationQuestion question : batch) {
                        persistFailure(experiment, question, exception,
                                batchLatency, effectiveLatency, batch.size());
                        failure++;
                        errorMessages.add("Q" + question.getQuestionNo() + ": " + exception.getMessage());
                    }
                }
                int progress = (int) Math.round((end * 100.0) / benchmarkQuestions.size());
                if (!saveProgressIfRunning(experimentId, success, failure, progress)) return;
            }
        } catch (RuntimeException exception) {
            if (isCancellationRequested(experimentId)) return;
            failure = Math.max(1, failure);
            errorMessages.add(exception.getMessage());
        }
        if (!isCancellationRequested(experimentId) && success > 0) {
            List<String> ragasErrors = evaluateOfficialRagas(experiment);
            failure += ragasErrors.size();
            errorMessages.addAll(ragasErrors);
        }
        if (!finishBenchmarkIfRunning(experimentId, success, failure, errorMessages)) return;
        log.info("Flow 5 benchmark {} ended with status {}, success={}, failure={}", experimentId,
                failure == 0 ? "COMPLETED" : "FAILED", success, failure);
    }

    private boolean isCancellationRequested(UUID experimentId) {
        return cancellationRequests.contains(experimentId);
    }

    private List<String> evaluateOfficialRagas(Experiment experiment) {
        List<ExperimentResult> storedResults = results.findByExperimentId(experiment.getExperimentId()).stream()
                .filter(result -> result.getErrorMessage() == null || result.getErrorMessage().isBlank())
                .toList();
        if (storedResults.isEmpty()) return List.of("RAGAS: no successful generated answers to evaluate.");

        Map<UUID, EvaluationQuestion> questionById = new HashMap<>();
        questions.findAllById(storedResults.stream().map(ExperimentResult::getEvaluationQuestionId).toList())
                .forEach(question -> questionById.put(question.getEvaluationQuestionId(), question));
        PythonAiDto.RagasBatchRequest request = new PythonAiDto.RagasBatchRequest();
        request.evaluator_model = "gpt-4o-mini";
        request.evaluator_embedding_model = "text-embedding-3-small";
        request.items = storedResults.stream().map(result -> {
            EvaluationQuestion question = questionById.get(result.getEvaluationQuestionId());
            PythonAiDto.RagasEvaluationItem item = new PythonAiDto.RagasEvaluationItem();
            item.request_id = result.getExperimentResultId().toString();
            item.question = question.getQuestionText();
            item.response = result.getGeneratedAnswer();
            item.reference = question.getGroundTruthAnswer();
            item.retrieved_contexts = contextStrings(result.getRetrievedContextJson());
            item.experiment_type = experiment.getExperimentType();
            return item;
        }).toList();

        List<String> errors = new ArrayList<>();
        try {
            PythonAiDto.RagasBatchResponse response = aiClientService.evaluateRagas(request);
            Map<UUID, PythonAiDto.RagasEvaluationResult> byId = response.items.stream().collect(
                    java.util.stream.Collectors.toMap(item -> UUID.fromString(item.request_id), Function.identity()));
            for (ExperimentResult result : storedResults) {
                PythonAiDto.RagasEvaluationResult metric = byId.get(result.getExperimentResultId());
                if (metric == null || !"SUCCESS".equals(metric.status)) {
                    String error = metric == null ? "Missing RAGAS result." : defaultIfBlank(metric.error, metric.status);
                    result.setRagasStatus(metric == null ? "FAILED" : metric.status);
                    result.setRagasError(error);
                    errors.add("RAGAS " + result.getExperimentResultId() + ": " + error);
                } else {
                    result.setFaithfulness(metric.faithfulness);
                    result.setAnswerRelevance(metric.answer_relevancy);
                    result.setAnswerCorrectness(metric.answer_correctness);
                    result.setContextPrecision(metric.context_precision);
                    result.setContextRecall(metric.context_recall);
                    result.setRagasStatus("SUCCESS");
                    result.setRagasError(null);
                }
                result.setMetricStandard("OFFICIAL_RAGAS");
                result.setEvaluatorModel(response.evaluator_model);
                result.setEvaluatorEmbeddingModel(response.evaluator_embedding_model);
                result.setRagasVersion(response.ragas_version);
                results.save(result);
            }
        } catch (RuntimeException exception) {
            for (ExperimentResult result : storedResults) {
                result.setRagasStatus("FAILED");
                result.setRagasError(exception.getMessage());
                results.save(result);
            }
            errors.add("RAGAS batch: " + exception.getMessage());
        }
        rebuildAggregates(experiment.getExperimentId());
        return errors;
    }

    private List<String> contextStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private void rebuildAggregates(UUID experimentId) {
        aggregates.deleteByExperimentId(experimentId);
        List<ExperimentResult> values = results.findByExperimentId(experimentId);
        saveAggregate(experimentId, "faithfulness", values.stream().map(ExperimentResult::getFaithfulness).toList());
        saveAggregate(experimentId, "answer_relevancy", values.stream().map(ExperimentResult::getAnswerRelevance).toList());
        saveAggregate(experimentId, "answer_correctness", values.stream().map(ExperimentResult::getAnswerCorrectness).toList());
        saveAggregate(experimentId, "context_precision", values.stream().map(ExperimentResult::getContextPrecision).toList());
        saveAggregate(experimentId, "context_recall", values.stream().map(ExperimentResult::getContextRecall).toList());
        saveAggregate(experimentId, "latency_ms", values.stream()
                .map(value -> value.getLatencyMs() == null ? null : value.getLatencyMs().doubleValue()).toList());
    }

    private void saveAggregate(UUID experimentId, String metricName, List<Double> rawValues) {
        List<Double> values = rawValues.stream().filter(Objects::nonNull).toList();
        ExperimentMetricAggregate aggregate = new ExperimentMetricAggregate();
        aggregate.setExperimentId(experimentId);
        aggregate.setMetricName(metricName);
        aggregate.setSampleCount(values.size());
        aggregate.setFailureCount(rawValues.size() - values.size());
        aggregate.setCreatedAt(LocalDateTime.now());
        if (!values.isEmpty()) {
            double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            aggregate.setAverageValue(round4(average));
            aggregate.setMinValue(round4(values.stream().mapToDouble(Double::doubleValue).min().orElse(0)));
            aggregate.setMaxValue(round4(values.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
            double variance = values.stream().mapToDouble(value -> Math.pow(value - average, 2)).average().orElse(0);
            aggregate.setStandardDeviation(round4(Math.sqrt(variance)));
        }
        aggregates.save(aggregate);
    }

    private synchronized boolean markRunningIfQueued(UUID experimentId) {
        if (isCancellationRequested(experimentId)) return false;
        Experiment current = getExperiment(experimentId);
        if (!"QUEUED".equals(current.getStatus())) return false;
        current.setStatus("RUNNING");
        current.setStartedAt(LocalDateTime.now());
        current.setUpdatedAt(LocalDateTime.now());
        experiments.save(current);
        return true;
    }

    private int elapsedMs(long startedAt) {
        return (int) Math.round((System.nanoTime() - startedAt) / 1_000_000.0);
    }

    private synchronized boolean saveProgressIfRunning(UUID experimentId, int success, int failure, int progress) {
        if (isCancellationRequested(experimentId)) return false;
        Experiment current = getExperiment(experimentId);
        if (!"RUNNING".equals(current.getStatus())) return false;
        current.setSuccessCount(success);
        current.setFailureCount(failure);
        current.setProgress(progress);
        current.setUpdatedAt(LocalDateTime.now());
        experiments.save(current);
        return true;
    }

    private synchronized boolean finishBenchmarkIfRunning(UUID experimentId, int success, int failure,
            List<String> errorMessages) {
        if (isCancellationRequested(experimentId)) return false;
        Experiment current = getExperiment(experimentId);
        if (!"RUNNING".equals(current.getStatus())) return false;
        current.setSuccessCount(success);
        current.setFailureCount(failure);
        current.setProgress(100);
        current.setStatus(failure == 0 ? "COMPLETED" : "FAILED");
        current.setErrorMessage(errorMessages.isEmpty() ? null : String.join(" | ", errorMessages));
        current.setCompletedAt(LocalDateTime.now());
        current.setUpdatedAt(LocalDateTime.now());
        experiments.save(current);
        return true;
    }

    private void persistSuccess(Experiment experiment, EvaluationQuestion question, ChatDto.AskResponse answer,
            int batchLatencyMs, int effectiveLatencyMs, int batchSize) {
        boolean rag = "RAG".equals(experiment.getExperimentType());
        String generated = answer == null ? "" : defaultIfBlank(answer.answer, "");
        List<ChatDto.CitationItem> citations = answer == null || answer.citations == null
                ? List.of() : answer.citations;
        List<String> contexts = answer == null || answer.retrievedContexts == null
                ? List.of()
                : answer.retrievedContexts.stream().filter(value -> value != null && !value.isBlank()).toList();
        ExperimentResult result = baseResult(experiment, question, batchLatencyMs, effectiveLatencyMs, batchSize);
        result.setGeneratedAnswer(generated);
        result.setRetrievedContextJson(rag ? toJson(contexts) : null);
        result.setCitationsJson(rag ? toJson(citations) : null);
        result.setMetricStandard("OFFICIAL_RAGAS");
        result.setRagasStatus("PENDING");
        results.save(result);
    }

    private void persistFailure(Experiment experiment, EvaluationQuestion question, RuntimeException exception,
            int batchLatencyMs, int effectiveLatencyMs, int batchSize) {
        ExperimentResult result = baseResult(experiment, question, batchLatencyMs, effectiveLatencyMs, batchSize);
        result.setErrorMessage(exception.getMessage());
        results.save(result);
    }

    private ExperimentResult baseResult(Experiment experiment, EvaluationQuestion question,
            int batchLatencyMs, int effectiveLatencyMs, int batchSize) {
        ExperimentResult result = new ExperimentResult();
        result.setExperimentId(experiment.getExperimentId());
        result.setEvaluationQuestionId(question.getEvaluationQuestionId());
        result.setLatencyMs(effectiveLatencyMs);
        result.setBatchLatencyMs(batchLatencyMs);
        result.setEffectiveLatencyMs(effectiveLatencyMs);
        result.setBatchSize(batchSize);
        result.setCost(BigDecimal.ZERO);
        result.setMetricStandard(experiment.getMetricStandard());
        result.setRagasStatus("NOT_EVALUATED");
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private String withBenchmarkProfile(String configJson, int questionCount) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (configJson != null && !configJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(configJson, Map.class);
                root.putAll(parsed);
            } catch (JsonProcessingException ignored) {
                root.put("originalConfig", configJson);
            }
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("version", BENCHMARK_PROFILE_VERSION);
        profile.put("questionCount", questionCount);
        profile.put("batchSize", BENCHMARK_BATCH_SIZE);
        profile.put("maxInputTokens", BENCHMARK_MAX_INPUT_TOKENS);
        profile.put("maxNewTokens", BENCHMARK_MAX_NEW_TOKENS);
        root.put("benchmarkProfile", profile);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize benchmark profile.", exception);
        }
    }

    private Object benchmarkProfile(Experiment experiment) {
        if (experiment.getConfigJson() == null || experiment.getConfigJson().isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(experiment.getConfigJson(), Map.class);
            return config.get("benchmarkProfile");
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private void freezeExperimentConfig(Experiment experiment, EvaluationDataset dataset) {
        List<CourseDocument> snapshot = getDatasetDocuments(dataset.getDatasetId());
        String embeddingRevision = snapshot.stream()
                .map(CourseDocument::getIndexedModelVersion)
                .filter(value -> value != null && !value.isBlank())
                .distinct().sorted().reduce((left, right) -> left + "," + right)
                .orElse("configured");
        experiment.setEmbeddingModelVersion(embeddingRevision);
        String adapterChecksum = fileChecksum(resolvePythonRoot()
                .resolve("models/qwen-rag-lora/adapter_model.safetensors"));
        experiment.setGenerationModelVersion(experiment.getLlmModel() + "@" + adapterChecksum);
        String canonical = String.join("|",
                dataset.getChecksum(),
                String.valueOf(experiment.getEmbeddingModelId()),
                experiment.getEmbeddingModelVersion(),
                experiment.getChunkingStrategy(),
                String.valueOf(experiment.getTopK()),
                String.valueOf(experiment.getSimilarityThreshold()),
                experiment.getGenerationModelVersion(),
                String.valueOf(experiment.getRandomSeed()),
                experiment.getMetricStandard(),
                experiment.getEvaluatorConfigJson(),
                experiment.getConfigJson());
        experiment.setFrozenConfigHash(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private int trainingLeakageCount(List<EvaluationQuestion> benchmarkQuestions) {
        Set<String> trainingQuestions = new HashSet<>();
        Path dataDir = resolvePythonRoot().resolve("data/finetuning");
        for (String filename : List.of("train.jsonl", "validation.jsonl")) {
            Path path = dataDir.resolve(filename);
            if (!Files.isRegularFile(path)) continue;
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(line);
                    for (com.fasterxml.jackson.databind.JsonNode message : root.path("messages")) {
                        if ("user".equals(message.path("role").asText())) {
                            trainingQuestions.add(normalizeQuestion(message.path("content").asText()));
                        }
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot validate LoRA data leakage: " + exception.getMessage(), exception);
            }
        }
        return (int) benchmarkQuestions.stream()
                .map(EvaluationQuestion::getQuestionText)
                .map(this::normalizeQuestion)
                .filter(trainingQuestions::contains)
                .count();
    }

    private String normalizeQuestion(String value) {
        return Normalizer.normalize(defaultIfBlank(value, ""), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private Path resolvePythonRoot() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path candidate = current.resolve("../..").normalize();
        return Files.isDirectory(candidate.resolve("data/finetuning")) ? candidate : current;
    }

    private String fileChecksum(Path path) {
        if (!Files.isRegularFile(path)) return "missing";
        try {
            return sha256(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot checksum model artifact: " + path, exception);
        }
    }

    private EvaluationDataset freezeDataset(UUID datasetId) {
        EvaluationDataset dataset = requireDataset(datasetId);
        if ("FROZEN".equals(dataset.getStatus()) && dataset.getChecksum() != null) return dataset;
        StringBuilder canonical = new StringBuilder("course:").append(dataset.getCourseId()).append('\n');
        getDatasetDocuments(datasetId).stream().sorted(Comparator.comparing(CourseDocument::getDocumentId))
                .forEach(document -> canonical.append("document:").append(document.getDocumentId()).append('|')
                        .append(document.getFileSizeBytes()).append('|').append(document.getUpdatedAt()).append('\n'));
        getQuestions(datasetId).forEach(question -> canonical.append("question:")
                .append(question.getQuestionNo()).append('|').append(question.getQuestionText()).append('|')
                .append(question.getGroundTruthAnswer()).append('\n'));
        dataset.setChecksum(sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        dataset.setStatus("FROZEN");
        dataset.setValidationError(null);
        dataset.setUpdatedAt(LocalDateTime.now());
        return datasets.save(dataset);
    }

    private Map<String, Object> experimentSummary(Experiment experiment, List<ExperimentResult> values,
            boolean includeContextMetrics) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("experimentId", experiment.getExperimentId());
        summary.put("name", experiment.getExperimentName());
        summary.put("status", experiment.getStatus());
        summary.put("llmModel", experiment.getLlmModel());
        summary.put("embeddingModelId", experiment.getEmbeddingModelId());
        summary.put("embeddingModelVersion", experiment.getEmbeddingModelVersion());
        summary.put("chunkingStrategy", experiment.getChunkingStrategy());
        summary.put("topK", experiment.getTopK());
        summary.put("similarityThreshold", experiment.getSimilarityThreshold());
        summary.put("randomSeed", experiment.getRandomSeed());
        summary.put("generationModelVersion", experiment.getGenerationModelVersion());
        summary.put("metricStandard", experiment.getMetricStandard());
        summary.put("evaluatorConfig", parseJsonCollection(experiment.getEvaluatorConfigJson()));
        summary.put("frozenConfigHash", experiment.getFrozenConfigHash());
        summary.put("startedAt", experiment.getStartedAt());
        summary.put("completedAt", experiment.getCompletedAt());
        summary.put("benchmarkProfile", benchmarkProfile(experiment));
        summary.put("successCount", experiment.getSuccessCount());
        summary.put("failureCount", experiment.getFailureCount());
        int totalCount = values.size();
        long successfulResults = values.stream()
                .filter(value -> value.getErrorMessage() == null || value.getErrorMessage().isBlank())
                .count();
        summary.put("totalCount", totalCount);
        summary.put("successRate", totalCount == 0 ? null : round4(successfulResults / (double) totalCount));
        summary.put("answerCorrectness", average(values, ExperimentResult::getAnswerCorrectness));
        summary.put("answerRelevance", average(values, ExperimentResult::getAnswerRelevance));
        summary.put("semanticSimilarity", average(values, ExperimentResult::getSemanticSimilarity));
        summary.put("latencyMs", average(values, value -> value.getLatencyMs() == null ? null : value.getLatencyMs().doubleValue()));
        if (includeContextMetrics) {
            summary.put("faithfulness", average(values, ExperimentResult::getFaithfulness));
            summary.put("contextPrecision", average(values, ExperimentResult::getContextPrecision));
            summary.put("contextRecall", average(values, ExperimentResult::getContextRecall));
        } else {
            summary.put("faithfulness", null);
            summary.put("contextPrecision", null);
            summary.put("contextRecall", null);
        }
        return summary;
    }

    private Object parseJsonCollection(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            return parsed instanceof List<?> || parsed instanceof Map<?, ?> ? parsed : List.of();
        } catch (JsonProcessingException exception) {
            log.warn("Could not parse stored evaluation evidence JSON", exception);
            return List.of();
        }
    }

    private Double average(List<ExperimentResult> values, Function<ExperimentResult, Double> field) {
        return values.stream().map(field).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average()
                .stream().map(value -> round4(value)).boxed().findFirst().orElse(null);
    }

    private Double nullableDelta(Double left, Double right) {
        return left == null || right == null ? null : round4(left - right);
    }

    private void addCheck(List<Map<String, Object>> checks, List<Map<String, Object>> blockers, String code,
            boolean passed, String successMessage, String failureMessage) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("passed", passed);
        item.put("message", passed ? successMessage : failureMessage);
        checks.add(item);
        if (!passed) blockers.add(item);
    }

    private boolean booleanValue(Map<String, Object> values, String... keys) {
        for (String key : keys) if (Boolean.TRUE.equals(values.get(key))) return true;
        Object runtime = values.get("runtime");
        if (runtime instanceof Map<?, ?> nested) {
            for (String key : keys) if (Boolean.TRUE.equals(nested.get(key))) return true;
        }
        Object details = values.get("details");
        if (details instanceof Map<?, ?> nested) {
            for (String key : keys) if (Boolean.TRUE.equals(nested.get(key))) return true;
        }
        return false;
    }

    private Object firstValue(Map<String, Object> values, String... keys) {
        for (String key : keys) if (values.get(key) != null) return values.get(key);
        Object runtime = values.get("runtime");
        if (runtime instanceof Map<?, ?> nested) for (String key : keys) if (nested.get(key) != null) return nested.get(key);
        return null;
    }

    private EvaluationDataset requireDataset(UUID datasetId) {
        return datasets.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));
    }

    private EvaluationDataset requireEditableDataset(UUID datasetId) {
        EvaluationDataset dataset = requireDataset(datasetId);
        if ("FROZEN".equals(dataset.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dataset is frozen because a benchmark has already started.");
        }
        if ("INVALID".equals(dataset.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    defaultIfBlank(dataset.getValidationError(), "Dataset is invalid."));
        }
        return dataset;
    }

    private void attachDocumentIds(EvaluationDataset dataset) {
        dataset.setDocumentIds(snapshotDocumentIds(dataset.getDatasetId()));
    }

    private List<UUID> snapshotDocumentIds(UUID datasetId) {
        return datasetDocuments.findByIdDatasetId(datasetId).stream()
                .map(EvaluationDatasetDocument::getDocumentId).filter(Objects::nonNull).sorted().toList();
    }

    private String normalizeExperimentType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.contains("FINE")) return "FINE_TUNED";
        if ("RAG".equals(normalized)) return "RAG";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experimentType must be RAG or FINE_TUNED.");
    }

    private String stripUtf8Bom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    cell.append('"'); index++;
                } else quoted = !quoted;
            } else if (current == ',' && !quoted) {
                row.add(cell.toString()); cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') index++;
                row.add(cell.toString()); cell.setLength(0);
                if (row.stream().anyMatch(value -> value != null && !value.trim().isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else cell.append(current);
        }
        row.add(cell.toString());
        if (row.stream().anyMatch(value -> value != null && !value.trim().isEmpty())) rows.add(row);
        return rows;
    }

    private Map<String, Integer> headerIndex(List<String> headerRow) {
        Map<String, Integer> headers = new HashMap<>();
        for (int index = 0; index < headerRow.size(); index++) headers.put(normalizeHeader(headerRow.get(index)), index);
        return headers;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private Integer firstHeader(Map<String, Integer> headers, String... names) {
        for (String name : names) if (headers.get(normalizeHeader(name)) != null) return headers.get(normalizeHeader(name));
        return null;
    }

    private String cell(List<String> row, Integer index) {
        return index == null || index < 0 || index >= row.size() || row.get(index) == null ? "" : row.get(index);
    }

    private Integer parseOptionalInt(String value) {
        try { return value == null || value.isBlank() ? null : Integer.valueOf(value.trim()); }
        catch (NumberFormatException exception) { return null; }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { return "[]"; }
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte item : hash) value.append(String.format("%02x", item));
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private double tokenF1(String actual, String expected) {
        List<String> actualTokens = tokenize(actual);
        List<String> expectedTokens = tokenize(expected);
        if (expectedTokens.isEmpty()) return actualTokens.isEmpty() ? 1.0 : 0.0;
        if (actualTokens.isEmpty()) return 0.0;
        Map<String, Integer> actualCounts = tokenCounts(actualTokens);
        Map<String, Integer> expectedCounts = tokenCounts(expectedTokens);
        int common = 0;
        for (Map.Entry<String, Integer> entry : actualCounts.entrySet())
            common += Math.min(entry.getValue(), expectedCounts.getOrDefault(entry.getKey(), 0));
        if (common == 0) return 0.0;
        double precision = (double) common / actualTokens.size();
        double recall = (double) common / expectedTokens.size();
        return round4(2 * precision * recall / (precision + recall));
    }

    private Map<String, Integer> tokenCounts(List<String> tokens) {
        Map<String, Integer> counts = new HashMap<>();
        tokens.forEach(token -> counts.put(token, counts.getOrDefault(token, 0) + 1));
        return counts;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("[^a-z0-9\\p{IsAlphabetic}]+")) if (!token.isBlank()) tokens.add(token);
        return tokens;
    }

    private double faithfulnessProxy(String answer, List<String> contexts) {
        Set<String> answerTokens = meaningfulTokenSet(answer);
        if (answerTokens.isEmpty()) return 0.0;
        int total = answerTokens.size();
        answerTokens.retainAll(meaningfulTokenSet(String.join(" ", contexts)));
        return round4((double) answerTokens.size() / total);
    }

    private double contextRecallProxy(String expected, List<String> contexts) {
        Set<String> expectedTokens = meaningfulTokenSet(expected);
        if (expectedTokens.isEmpty()) return 0.0;
        int total = expectedTokens.size();
        expectedTokens.retainAll(meaningfulTokenSet(String.join(" ", contexts)));
        return round4((double) expectedTokens.size() / total);
    }

    private double contextPrecisionProxy(String expected, List<String> contexts) {
        Set<String> expectedTokens = meaningfulTokenSet(expected);
        if (expectedTokens.isEmpty() || contexts.isEmpty()) return 0.0;
        long relevant = contexts.stream().filter(context -> {
            Set<String> tokens = meaningfulTokenSet(context); tokens.retainAll(expectedTokens); return !tokens.isEmpty();
        }).count();
        return round4((double) relevant / contexts.size());
    }

    private Set<String> meaningfulTokenSet(String text) {
        Set<String> stopWords = Set.of("la", "gi", "va", "co", "duoc", "nhu", "the", "nao", "trong",
                "theo", "nhung", "cac", "cua", "ve", "tai", "de", "mot", "cho", "khi", "tu");
        Set<String> values = new HashSet<>();
        for (String token : tokenize(text)) if (token.length() > 1 && !stopWords.contains(token)) values.add(token);
        return values;
    }

    private double round4(double value) { return Math.round(value * 10_000.0) / 10_000.0; }
}
