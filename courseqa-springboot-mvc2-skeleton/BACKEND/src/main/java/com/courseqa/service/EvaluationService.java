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
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.EvaluationDatasetDocumentRepository;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.EvaluationQuestionRepository;
import com.courseqa.repository.ExperimentRepository;
import com.courseqa.repository.ExperimentResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvaluationService {
    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "FAILED");
    private static final int BENCHMARK_BATCH_SIZE = 1;
    private static final int BENCHMARK_MAX_INPUT_TOKENS = 1536;
    private static final int BENCHMARK_MAX_NEW_TOKENS = 192;
    private static final int BENCHMARK_REPETITIONS = 3;
    private static final String BENCHMARK_PROFILE_VERSION = "qwen1.5b-sequential-v2";

    private final EvaluationDatasetRepository datasets;
    private final EvaluationDatasetDocumentRepository datasetDocuments;
    private final EvaluationQuestionRepository questions;
    private final ExperimentRepository experiments;
    private final ExperimentResultRepository results;
    private final CourseRepository courses;
    private final CourseDocumentRepository documents;
    private final CourseWorkspaceRepository workspaces;
    private final LearningScopeService learningScopeService;
    private final BenchmarkInferenceService benchmarkInferenceService;
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
            CourseRepository courses,
            CourseDocumentRepository documents,
            CourseWorkspaceRepository workspaces,
            LearningScopeService learningScopeService,
            BenchmarkInferenceService benchmarkInferenceService,
            AIClientService aiClientService,
            @Qualifier("evaluationTaskExecutor") TaskExecutor taskExecutor,
            ObjectMapper objectMapper) {
        this.datasets = datasets;
        this.datasetDocuments = datasetDocuments;
        this.questions = questions;
        this.experiments = experiments;
        this.results = results;
        this.courses = courses;
        this.documents = documents;
        this.workspaces = workspaces;
        this.learningScopeService = learningScopeService;
        this.benchmarkInferenceService = benchmarkInferenceService;
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
                        || !"PROCESSED".equals(document.getProcessingStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "All selected documents must be processed documents from the selected course.");
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

    @Transactional
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
            Integer sourceIndex = firstHeader(headers, "expected_source", "source", "filename");
            Integer evidenceIndex = firstHeader(headers, "evidence_quote", "evidence");
            Integer chapterIndex = firstHeader(headers, "chapter", "chapter_label");
            Integer outOfScopeIndex = firstHeader(headers, "is_out_of_scope", "out_of_scope");
            Integer categoryIndex = firstHeader(headers, "category", "question_type", "type");
            Integer difficultyIndex = firstHeader(headers, "difficulty");
            Set<String> knownQuestions = questions.findByDatasetId(datasetId).stream()
                    .map(EvaluationQuestion::getQuestionText)
                    .filter(Objects::nonNull)
                    .map(this::normalizeForMatching)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            Map<String, CourseDocument> documentsByFilename = getDatasetDocuments(datasetId).stream()
                    .filter(document -> document.getOriginalFilename() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            document -> document.getOriginalFilename().trim().toLowerCase(Locale.ROOT),
                            Function.identity(), (left, right) -> left));
            int next = questions.findByDatasetId(datasetId).size() + 1;
            int skipped = 0;
            List<EvaluationQuestion> imported = new ArrayList<>();
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                String questionText = cell(row, questionIndex).trim();
                String answer = cell(row, answerIndex).trim();
                if (questionText.isEmpty() || answer.isEmpty()) { skipped++; continue; }
                if (!knownQuestions.add(normalizeForMatching(questionText))) {
                    throw new IllegalArgumentException("Duplicate question at CSV row " + (rowIndex + 1) + ".");
                }
                String expectedPageCell = cell(row, pageIndex);
                Integer expectedPage = parseOptionalInt(expectedPageCell);
                if (!expectedPageCell.isBlank() && expectedPage == null) {
                    throw new IllegalArgumentException("expected_page must be an integer at CSV row "
                            + (rowIndex + 1) + ".");
                }
                if (expectedPage != null && expectedPage <= 0) {
                    throw new IllegalArgumentException("expected_page must be positive at CSV row "
                            + (rowIndex + 1) + ".");
                }
                String expectedSource = cell(row, sourceIndex).trim();
                CourseDocument expectedDocument = expectedSource.isBlank() ? null
                        : documentsByFilename.get(expectedSource.toLowerCase(Locale.ROOT));
                if (!expectedSource.isBlank() && expectedDocument == null) {
                    throw new IllegalArgumentException("expected_source is not in the dataset corpus at CSV row "
                            + (rowIndex + 1) + ".");
                }
                EvaluationQuestion item = new EvaluationQuestion();
                item.setDatasetId(datasetId);
                item.setCourseId(dataset.getCourseId());
                item.setQuestionNo(next++);
                item.setQuestionText(questionText);
                item.setGroundTruthAnswer(answer);
                item.setExpectedPage(expectedPage);
                item.setExpectedSource(expectedSource.isBlank() ? null : expectedSource);
                item.setExpectedDocumentId(expectedDocument == null ? null : expectedDocument.getDocumentId());
                item.setEvidenceQuote(defaultIfBlank(cell(row, evidenceIndex), null));
                item.setChapterLabel(defaultIfBlank(cell(row, chapterIndex), null));
                item.setIsOutOfScope(parseBoolean(cell(row, outOfScopeIndex)));
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
            String configJson, UUID createdBy) {
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


      //  experiment.setLlmModel("Qwen/Qwen2.5-1.5B-Instruct");
        experiment.setLlmModel(resolveBaseModel());



        experiment.setChunkingStrategy("FIXED_500_OVERLAP_50");
        experiment.setTopK(5);
        experiment.setTemperature(0.0);


       // experiment.setFineTunedModelName("FINE_TUNED".equals(normalizedType)
        //        ? "qwen2.5-1.5b-triethoc-lora-v1" : null);
        experiment.setFineTunedModelName("FINE_TUNED".equals(normalizedType)
                ? resolveAdapterVersion() : null);


                
        experiment.setConfigJson(withResearchConfig(configJson, normalizedType));
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

    public synchronized Experiment startBenchmark(UUID experimentId) {
        return startBenchmark(experimentId, false);
    }

    public synchronized Experiment startBenchmark(UUID experimentId, boolean allowUnverifiedModel) {
        Experiment experiment = getExperiment(experimentId);
        if (Set.of("QUEUED", "RUNNING").contains(experiment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This experiment is already queued or running.");
        }
        Map<String, Object> readiness = readiness(experiment.getDatasetId(), experiment.getExperimentType());
        boolean acknowledgedUnverified = "FINE_TUNED".equals(experiment.getExperimentType())
                && allowUnverifiedModel
                && Boolean.TRUE.equals(readiness.get("benchmarkReady"));
        if (!Boolean.TRUE.equals(readiness.get("ready")) && !acknowledgedUnverified) {
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
        experiment.setConfigJson(withBenchmarkProfile(
                experiment.getConfigJson(),
                getQuestions(dataset.getDatasetId()).size(),
                acknowledgedUnverified));
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
                dataset.getCourseId().equals(document.getCourseId()) && "PROCESSED".equals(document.getProcessingStatus()));
        addCheck(checks, blockers, "documents", validDocuments,
                snapshot.size() + " processed document(s) are frozen in the snapshot.",
                "Dataset needs at least one processed document snapshot.");
        int questionCount = questions.findByDatasetId(datasetId).size();
        addCheck(checks, blockers, "questions", questionCount > 0,
                questionCount + " benchmark question(s) are ready.", "Dataset has no benchmark questions.");

        Map<String, Object> model = modelReadiness();
        boolean modelReady = "FINE_TUNED".equals(type)
                ? Boolean.TRUE.equals(model.get("fineTunedReady"))
                : Boolean.TRUE.equals(model.get("baseRagReady"));
        String modelStatus = String.valueOf("FINE_TUNED".equals(type)
                ? model.getOrDefault("fineTunedStatus", "MODEL_RUNTIME_NOT_READY")
                : model.getOrDefault("baseRagStatus", "MODEL_RUNTIME_NOT_READY"));
        addCheck(checks, blockers, "model", modelReady,
                "Strict " + type + " generation is ready.",
                "Strict " + type + " model is not ready: " + modelStatus + ".");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasetId", datasetId);
        response.put("experimentType", type);
        response.put("ready", blockers.isEmpty());
        boolean nonModelReady = checks.stream()
                .filter(item -> !"model".equals(item.get("code")))
                .allMatch(item -> Boolean.TRUE.equals(item.get("passed")));
        boolean benchmarkEligible = Boolean.TRUE.equals(model.get("benchmarkEligible"));
        boolean requiresAcknowledgement = "FINE_TUNED".equals(type)
                && !modelReady && benchmarkEligible;
        response.put("benchmarkReady", nonModelReady && (modelReady || requiresAcknowledgement));
        response.put("requiresUnverifiedAcknowledgement", requiresAcknowledgement);
        response.put("modelVerificationStatus",
                modelReady ? "VERIFIED" : (requiresAcknowledgement ? "UNVERIFIED" : "NOT_READY"));
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
            String baseRagStatus = String.valueOf(firstValue(raw, "base_rag_status"));
            String fineTunedStatus = String.valueOf(firstValue(raw, "fine_tuned_status"));
            boolean baseRagReady = "BASE_RAG_READY".equals(baseRagStatus);
            boolean fineTunedReady = "FINE_TUNED_READY".equals(fineTunedStatus);
            boolean benchmarkEligible = booleanValue(raw, "benchmark_eligible", "benchmarkEligible");
            String verificationStatus = String.valueOf(firstValue(
                    raw, "model_verification_status", "modelVerificationStatus"));
            response.put("reachable", true);
            response.put("inferenceReady", inferenceReady);
            response.put("trainingReady", trainingReady);
            response.put("generationReady", generationReady);
            response.put("baseRagReady", baseRagReady);
            response.put("fineTunedReady", fineTunedReady);
            response.put("baseRagStatus", baseRagStatus);
            response.put("fineTunedStatus", fineTunedStatus);
            response.put("benchmarkEligible", benchmarkEligible);
            response.put("modelVerificationStatus", verificationStatus);
            response.put("qualityGatePassed", booleanValue(raw, "adapter_verified", "quality_gate_passed"));
            response.put("baseModel", firstValue(raw, "base_model"));
            response.put("adapterVersion", firstValue(raw, "adapter_version"));
            response.put("quantization", firstValue(raw, "quantization"));
            response.put("generationDevice", firstValue(raw, "generation_device"));
            response.put("embeddingDevice", firstValue(raw, "embedding_device"));
            response.put("adapterDir", firstValue(raw, "adapter_dir", "adapter_path"));
            response.put("details", raw);
        } catch (RuntimeException exception) {
            response.put("reachable", false);
            response.put("inferenceReady", false);
            response.put("trainingReady", false);
            response.put("generationReady", false);
            response.put("baseRagReady", false);
            response.put("fineTunedReady", false);
            response.put("benchmarkEligible", false);
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
            row.put("ragProviderUsed", ragResult.getProviderUsed());
            row.put("fineTunedProviderUsed", fineResult.getProviderUsed());
            row.put("ragBaseModel", ragResult.getBaseModel());
            row.put("fineTunedBaseModel", fineResult.getBaseModel());
            row.put("ragAdapterVersion", ragResult.getAdapterVersion());
            row.put("fineTunedAdapterVersion", fineResult.getAdapterVersion());
            row.put("ragEmbeddingModel", ragResult.getEmbeddingModel());
            row.put("ragGenerationMode", ragResult.getGenerationMode());
            row.put("fineTunedGenerationMode", fineResult.getGenerationMode());
            row.put("ragModelVerificationStatus", ragResult.getModelVerificationStatus());
            row.put("fineTunedModelVerificationStatus", fineResult.getModelVerificationStatus());
            row.put("ragQualityGatePassed", ragResult.getQualityGatePassed());
            row.put("fineTunedQualityGatePassed", fineResult.getQualityGatePassed());
            row.put("ragMetricStandard", ragResult.getMetricStandard());
            row.put("fineTunedMetricStandard", fineResult.getMetricStandard());
            row.put("ragJudgeModel", ragResult.getJudgeModel());
            row.put("fineTunedJudgeModel", fineResult.getJudgeModel());
            row.put("ragTokenOverlapProxy", ragResult.getAnswerCorrectness());
            row.put("fineTunedTokenOverlapProxy", fineResult.getAnswerCorrectness());
            row.put("tokenOverlapProxyDelta",
                    nullableDelta(fineResult.getAnswerCorrectness(), ragResult.getAnswerCorrectness()));
            row.put("ragAnswerRelevance", ragResult.getAnswerRelevance());
            row.put("fineTunedAnswerRelevance", fineResult.getAnswerRelevance());
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
        response.put("metricStandard", "RAGAS_OFFICIAL");
        response.put("formulaVersion", "ragas-0.4");
        response.put("benchmarkProfile", ragProfile);
        response.put("methodology", Map.of(
                "officialRagas", true,
                "judge", "OpenAI",
                "evaluatorEmbedding", "BAAI/bge-m3",
                "proxyNotice", "answerCorrectness and semanticSimilarity are token_overlap_proxy metrics, not RAGAS."));
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
            String mode = "FINE_TUNED".equals(experiment.getExperimentType()) ? "FINE_TUNED" : "RAG";
            boolean allowUnverifiedModel = allowsUnverifiedModel(experiment);
            BenchmarkInferenceService.BenchmarkScope benchmarkScope =
                    new BenchmarkInferenceService.BenchmarkScope(
                            dataset.getWorkspaceId(),
                            dataset.getSemesterWorkspaceId(),
                            documentIds,
                            experiment.getEmbeddingModelId());
            if (!benchmarkQuestions.isEmpty()) {
                EvaluationQuestion warmup = benchmarkQuestions.get(0);
                benchmarkInferenceService.answerBatch(
                        benchmarkScope,
                        List.of(new BenchmarkInferenceService.BenchmarkQuestion(
                                warmup.getEvaluationQuestionId(), warmup.getQuestionText())),
                        mode,
                        allowUnverifiedModel);
            }
            for (int index = 0; index < benchmarkQuestions.size(); index += BENCHMARK_BATCH_SIZE) {
                if (isCancellationRequested(experimentId)) return;
                int end = Math.min(index + BENCHMARK_BATCH_SIZE, benchmarkQuestions.size());
                List<EvaluationQuestion> batch = benchmarkQuestions.subList(index, end);
                try {
                    List<ChatDto.AskResponse> answers = List.of();
                    long repeatedLatency = 0;
                    for (int repetition = 0; repetition < BENCHMARK_REPETITIONS; repetition++) {
                        long repetitionStartedAt = System.nanoTime();
                        answers = benchmarkInferenceService.answerBatch(
                                benchmarkScope,
                                batch.stream().map(question ->
                                        new BenchmarkInferenceService.BenchmarkQuestion(
                                                question.getEvaluationQuestionId(),
                                                question.getQuestionText())).toList(),
                                mode,
                                allowUnverifiedModel);
                        repeatedLatency += elapsedMs(repetitionStartedAt);
                    }
                    if (isCancellationRequested(experimentId)) return;
                    if (answers.size() != batch.size()) {
                        throw new IllegalStateException("AI batch returned " + answers.size()
                                + " answers for " + batch.size() + " questions.");
                    }
                    Map<String, PythonAiDto.OfficialRagasResult> ragasResults =
                            evaluateWithOfficialRagas(batch, answers);
                    int batchLatency = Math.max(1, (int) Math.round(
                            repeatedLatency / (double) BENCHMARK_REPETITIONS));
                    int effectiveLatency = Math.max(1, (int) Math.round(
                            repeatedLatency / (double) (BENCHMARK_REPETITIONS * batch.size())));
                    for (int offset = 0; offset < batch.size(); offset++) {
                        persistSuccess(experiment, batch.get(offset), answers.get(offset),
                                ragasResults.get(String.valueOf(batch.get(offset).getEvaluationQuestionId())),
                                batchLatency, effectiveLatency, batch.size());
                        success++;
                    }
                } catch (RuntimeException exception) {
                    if (isCancellationRequested(experimentId)) return;
                    int batchLatency = 0;
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
        if (!finishBenchmarkIfRunning(experimentId, success, failure, errorMessages)) return;
        log.info("Flow 5 benchmark {} ended with status {}, success={}, failure={}", experimentId,
                failure == 0 ? "COMPLETED" : "FAILED", success, failure);
    }

    private boolean isCancellationRequested(UUID experimentId) {
        return cancellationRequests.contains(experimentId);
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

    private Map<String, PythonAiDto.OfficialRagasResult> evaluateWithOfficialRagas(
            List<EvaluationQuestion> batch, List<ChatDto.AskResponse> answers) {
        PythonAiDto.OfficialRagasBatchRequest request = new PythonAiDto.OfficialRagasBatchRequest();
        request.items = new ArrayList<>();
        for (int index = 0; index < batch.size(); index++) {
            EvaluationQuestion question = batch.get(index);
            ChatDto.AskResponse answer = answers.get(index);
            PythonAiDto.OfficialRagasItem item = new PythonAiDto.OfficialRagasItem();
            item.request_id = String.valueOf(question.getEvaluationQuestionId());
            item.question = question.getQuestionText();
            item.response = answer == null ? "" : defaultIfBlank(answer.answer, "");
            item.reference = question.getGroundTruthAnswer();
            item.contexts = answer == null || answer.citations == null
                    ? List.of()
                    : answer.citations.stream().map(citation -> defaultIfBlank(citation.quoteText, ""))
                            .filter(value -> !value.isBlank()).toList();
            request.items.add(item);
        }
        PythonAiDto.OfficialRagasBatchResponse response = aiClientService.callOfficialRagasBatch(request);
        if (response == null || !"RAGAS_OFFICIAL".equals(response.metric_standard) || response.items == null) {
            throw new IllegalStateException("Official RAGAS evaluator did not return a valid result.");
        }
        Map<String, PythonAiDto.OfficialRagasResult> byId = new HashMap<>();
        for (PythonAiDto.OfficialRagasResult result : response.items) {
            if (result.error != null && !result.error.isBlank()) {
                throw new IllegalStateException("Official RAGAS failed for " + result.request_id + ": "
                        + result.error);
            }
            result.metric_standard = response.metric_standard;
            result.judge_model = response.judge_model;
            result.embedding_model = response.evaluator_embedding;
            result.prompt_version = response.prompt_version;
            byId.put(result.request_id, result);
        }
        if (byId.size() != batch.size()) {
            throw new IllegalStateException("Official RAGAS returned incomplete batch results.");
        }
        return byId;
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return false;
        return Set.of("true", "1", "yes", "y").contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private void persistSuccess(Experiment experiment, EvaluationQuestion question, ChatDto.AskResponse answer,
            PythonAiDto.OfficialRagasResult ragas,
            int batchLatencyMs, int effectiveLatencyMs, int batchSize) {
        boolean rag = "RAG".equals(experiment.getExperimentType());
        String generated = answer == null ? "" : defaultIfBlank(answer.answer, "");
        List<ChatDto.CitationItem> citations = answer == null || answer.citations == null
                ? List.of() : answer.citations;
        List<String> contexts = citations.stream().map(item -> defaultIfBlank(item.quoteText, ""))
                .filter(value -> !value.isBlank()).toList();
        double answerF1 = tokenF1(generated, question.getGroundTruthAnswer());
        ExperimentResult result = baseResult(experiment, question, batchLatencyMs, effectiveLatencyMs, batchSize);
        result.setGeneratedAnswer(generated);
        result.setRetrievedContextJson(rag ? toJson(contexts) : null);
        result.setCitationsJson(rag ? toJson(citations) : null);
        result.setFaithfulness(ragas == null ? null : ragas.faithfulness);
        result.setAnswerRelevance(ragas == null ? null : ragas.answer_relevancy);
        result.setContextPrecision(ragas == null ? null : ragas.context_precision);
        result.setContextRecall(ragas == null ? null : ragas.context_recall);
        result.setAnswerCorrectness(answerF1);
        result.setSemanticSimilarity(answerF1);
        result.setProviderUsed(answer == null ? null : answer.providerUsed);
        result.setBaseModel(answer == null ? null : answer.baseModel);
        result.setAdapterVersion(answer == null ? null : answer.adapterVersion);
        result.setEmbeddingModel(answer == null ? null : answer.embeddingModel);
        result.setGenerationMode(answer == null ? null : answer.generationMode);
        result.setDatasetVersion(answer == null ? null : answer.datasetVersion);
        result.setPromptVersion(answer != null && answer.promptVersion != null
                ? answer.promptVersion : (ragas == null ? null : ragas.prompt_version));
        result.setMetricStandard(ragas == null ? null : ragas.metric_standard);
        result.setJudgeModel(ragas == null ? null : ragas.judge_model);
        result.setEvaluatorEmbedding(ragas == null ? null : ragas.embedding_model);
        result.setSourceHit(question.getExpectedDocumentId() == null ? null : citations.stream()
                .anyMatch(citation -> question.getExpectedDocumentId().equals(citation.documentId)));
        result.setPageHit(question.getExpectedPage() == null ? null : citations.stream().anyMatch(citation ->
                citation.pageStart != null
                        && citation.pageStart <= question.getExpectedPage()
                        && (citation.pageEnd == null || citation.pageEnd >= question.getExpectedPage())));
        result.setRefusalCorrect(Boolean.TRUE.equals(question.getIsOutOfScope())
                ? isRefusalAnswer(generated) : null);
        result.setThroughputQps(effectiveLatencyMs <= 0 ? null : round4(1000.0 / effectiveLatencyMs));
        result.setPeakVramBytes(answer == null ? null : answer.peakVramBytes);
        result.setModelVerificationStatus(answer == null
                ? null : defaultIfBlank(answer.modelVerificationStatus,
                        rag ? "VERIFIED" : "UNVERIFIED"));
        result.setQualityGatePassed(answer == null
                ? null : (rag ? Boolean.TRUE : answer.qualityGatePassed));
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
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private String withBenchmarkProfile(
            String configJson,
            int questionCount,
            boolean allowUnverifiedModel
    ) {
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
        profile.put("warmupRuns", 1);
        profile.put("repetitions", BENCHMARK_REPETITIONS);
        profile.put("temperature", 0.0);
        profile.put("seed", 42);
        root.put("benchmarkProfile", profile);
        root.put("allowUnverifiedModel", allowUnverifiedModel);
        root.put("modelVerificationStatus", allowUnverifiedModel ? "UNVERIFIED" : "VERIFIED");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize benchmark profile.", exception);
        }
    }

    private String withResearchConfig(String configJson, String experimentType) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (configJson != null && !configJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(configJson, Map.class);
                root.putAll(parsed);
            } catch (JsonProcessingException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson must be valid JSON.");
            }
        }
        root.put("generationMode", "FINE_TUNED".equals(experimentType)
                ? "FINE_TUNED_ONLY" : "BASE_RAG");
        //root.put("baseModel", "Qwen/Qwen2.5-1.5B-Instruct");
       // root.put("adapterVersion", "FINE_TUNED".equals(experimentType)
       //         ? "qwen2.5-1.5b-triethoc-lora-v1" : null);

        root.put("baseModel", resolveBaseModel());
        root.put("adapterVersion", "FINE_TUNED".equals(experimentType)
                ? resolveAdapterVersion() : null);



        root.put("embeddingModel", "BAAI/bge-m3");
        root.put("chunkingStrategy", "FIXED_500_OVERLAP_50");
        root.put("topK", 5);
        root.put("temperature", 0.0);
        root.put("seed", 42);
        root.put("datasetVersion", "triethoc-v1");
        root.put("promptVersion", "triethoc-grounded-v1");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize research configuration.", exception);
        }
    }
//-----------------Helpers
private String resolveBaseModel() {
        try {
            Map<String, Object> raw = aiClientService.getModelStatus();
            Object baseModel = firstValue(raw, "base_model");
            if (baseModel != null && !String.valueOf(baseModel).isBlank()) {
                return String.valueOf(baseModel);
            }
        } catch (Exception exception) {
            log.warn("Could not read base model from AI engine: {}", exception.getMessage());
        }
        return "UNKNOWN";
    }

    private String resolveAdapterVersion() {
        try {
            Map<String, Object> raw = aiClientService.getModelStatus();
            Object adapterVersion = firstValue(raw, "adapter_version");
            if (adapterVersion != null && !String.valueOf(adapterVersion).isBlank()) {
                return String.valueOf(adapterVersion);
            }
        } catch (Exception exception) {
            log.warn("Could not read adapter version from AI engine: {}", exception.getMessage());
        }
        return "UNKNOWN";
    }
//--------------------------------------

    private String withBenchmarkProfile(String configJson, int questionCount) {
        return withBenchmarkProfile(configJson, questionCount, false);
    }

    private boolean allowsUnverifiedModel(Experiment experiment) {
        if (experiment.getConfigJson() == null || experiment.getConfigJson().isBlank()) return false;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(experiment.getConfigJson(), Map.class);
            return Boolean.TRUE.equals(config.get("allowUnverifiedModel"));
        } catch (JsonProcessingException exception) {
            return false;
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
        summary.put("startedAt", experiment.getStartedAt());
        summary.put("completedAt", experiment.getCompletedAt());
        summary.put("benchmarkProfile", benchmarkProfile(experiment));
        summary.put("successCount", experiment.getSuccessCount());
        summary.put("failureCount", experiment.getFailureCount());
        ExperimentResult firstCompleted = values.stream()
                .filter(value -> value.getErrorMessage() == null || value.getErrorMessage().isBlank())
                .findFirst().orElse(null);
        summary.put("providerUsed", firstCompleted == null ? null : firstCompleted.getProviderUsed());
        summary.put("baseModel", firstCompleted == null ? null : firstCompleted.getBaseModel());
        summary.put("adapterVersion", firstCompleted == null ? null : firstCompleted.getAdapterVersion());
        summary.put("embeddingModel", firstCompleted == null ? null : firstCompleted.getEmbeddingModel());
        summary.put("generationMode", firstCompleted == null ? null : firstCompleted.getGenerationMode());
        summary.put("datasetVersion", firstCompleted == null ? null : firstCompleted.getDatasetVersion());
        summary.put("promptVersion", firstCompleted == null ? null : firstCompleted.getPromptVersion());
        summary.put("modelVerificationStatus",
                firstCompleted == null ? null : firstCompleted.getModelVerificationStatus());
        summary.put("qualityGatePassed",
                firstCompleted == null ? null : firstCompleted.getQualityGatePassed());
        summary.put("metricStandard", firstCompleted == null ? null : firstCompleted.getMetricStandard());
        summary.put("judgeModel", firstCompleted == null ? null : firstCompleted.getJudgeModel());
        int totalCount = values.size();
        long successfulResults = values.stream()
                .filter(value -> value.getErrorMessage() == null || value.getErrorMessage().isBlank())
                .count();
        summary.put("totalCount", totalCount);
        summary.put("successRate", totalCount == 0 ? null : round4(successfulResults / (double) totalCount));
        summary.put("tokenOverlapProxy", average(values, ExperimentResult::getAnswerCorrectness));
        summary.put("answerRelevance", average(values, ExperimentResult::getAnswerRelevance));
        summary.put("sourceHitRate", booleanRate(values, ExperimentResult::getSourceHit));
        summary.put("pageHitRate", booleanRate(values, ExperimentResult::getPageHit));
        summary.put("refusalAccuracy", booleanRate(values, ExperimentResult::getRefusalCorrect));
        summary.put("throughputQps", average(values, ExperimentResult::getThroughputQps));
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

    private boolean isRefusalAnswer(String answer) {
        String normalized = normalizeForMatching(answer);
        return normalized.contains("khong tim thay")
                || normalized.contains("chua tim thay")
                || normalized.contains("ngoai pham vi")
                || normalized.contains("khong co thong tin");
    }

    private Double booleanRate(List<ExperimentResult> values,
            Function<ExperimentResult, Boolean> extractor) {
        List<Boolean> applicable = values.stream().map(extractor)
                .filter(Objects::nonNull).toList();
        if (applicable.isEmpty()) return null;
        return round4(applicable.stream().filter(Boolean.TRUE::equals).count()
                / (double) applicable.size());
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
        for (String container : List.of("runtime", "generation")) {
            Object nestedValue = values.get(container);
            if (nestedValue instanceof Map<?, ?> nested) {
                for (String key : keys) if (Boolean.TRUE.equals(nested.get(key))) return true;
            }
        }
        return false;
    }

    private Object firstValue(Map<String, Object> values, String... keys) {
        for (String key : keys) if (values.get(key) != null) return values.get(key);
        for (String container : List.of("runtime", "generation")) {
            Object nestedValue = values.get(container);
            if (nestedValue instanceof Map<?, ?> nested) {
                for (String key : keys) if (nested.get(key) != null) return nested.get(key);
            }
        }
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

    private String normalizeForMatching(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\p{IsAlphabetic}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
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
