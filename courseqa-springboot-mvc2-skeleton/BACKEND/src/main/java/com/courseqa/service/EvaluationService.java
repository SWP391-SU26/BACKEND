package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.entity.ExperimentResult;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.EvaluationQuestionRepository;
import com.courseqa.repository.ExperimentRepository;
import com.courseqa.repository.ExperimentResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationQuestionRepository evaluationQuestionRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentResultRepository experimentResultRepository;
    private final AIClientService aiClientService;

    public EvaluationService(
            EvaluationDatasetRepository evaluationDatasetRepository,
            EvaluationQuestionRepository evaluationQuestionRepository,
            ExperimentRepository experimentRepository,
            ExperimentResultRepository experimentResultRepository,
            AIClientService aiClientService) {
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationQuestionRepository = evaluationQuestionRepository;
        this.experimentRepository = experimentRepository;
        this.experimentResultRepository = experimentResultRepository;
        this.aiClientService = aiClientService;
    }

    public EvaluationDataset createDataset(String datasetName, UUID courseId, UUID workspaceId, UUID createdBy) {
        log.info("Creating evaluation dataset: name={}, courseId={}, createdBy={}", datasetName, courseId, createdBy);

        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setDatasetName(datasetName);
        dataset.setDatasetVersion("v1");
        dataset.setCourseId(courseId);
        dataset.setWorkspaceId(workspaceId);
        dataset.setCreatedBy(createdBy);
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setUpdatedAt(LocalDateTime.now());

        EvaluationDataset savedDataset = evaluationDatasetRepository.save(dataset);
        log.info("Created evaluation dataset with id: {}", savedDataset.getDatasetId());
        return savedDataset;
    }

    public List<EvaluationDataset> listDatasets() {
        log.info("Fetching all evaluation datasets");
        return evaluationDatasetRepository.findAll();
    }

    public EvaluationQuestion addQuestion(UUID datasetId, String question, String groundTruth) {
        log.info("Adding question to dataset: datasetId={}, question={}", datasetId, question);

        EvaluationDataset dataset = evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        EvaluationQuestion evaluationQuestion = new EvaluationQuestion();
        evaluationQuestion.setDatasetId(datasetId);
        evaluationQuestion.setCourseId(dataset.getCourseId());
        evaluationQuestion.setQuestionNo(evaluationQuestionRepository.findByDatasetId(datasetId).size() + 1);
        evaluationQuestion.setQuestionText(question);
        evaluationQuestion.setGroundTruthAnswer(groundTruth);
        evaluationQuestion.setQuestionType("FACTUAL");
        evaluationQuestion.setDifficulty("MEDIUM");
        evaluationQuestion.setCreatedAt(LocalDateTime.now());

        EvaluationQuestion savedQuestion = evaluationQuestionRepository.save(evaluationQuestion);
        log.info("Added question to dataset - questionId: {}", savedQuestion.getEvaluationQuestionId());
        return savedQuestion;
    }

    public List<EvaluationQuestion> getQuestions(UUID datasetId) {
        log.info("Fetching questions for dataset: {}", datasetId);

        evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        return evaluationQuestionRepository.findByDatasetId(datasetId);
    }

    public Experiment createExperiment(
            UUID datasetId,
            String experimentName,
            String experimentType,
            String llmModel,
            String configJson,
            UUID createdBy) {
        log.info("Creating experiment: name={}, createdBy={}", experimentName, createdBy);

        EvaluationDataset dataset = evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        Experiment experiment = new Experiment();
        experiment.setDatasetId(datasetId);
        experiment.setCourseId(dataset.getCourseId());
        experiment.setWorkspaceId(dataset.getWorkspaceId());
        experiment.setExperimentName(experimentName);
        experiment.setExperimentType(experimentType);
        experiment.setLlmModel(llmModel);
        experiment.setTopK(5);
        experiment.setTemperature(0.2);
        experiment.setConfigJson(configJson);
        experiment.setCreatedBy(createdBy);
        experiment.setStatus("PENDING");
        experiment.setCreatedAt(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());

        Experiment savedExperiment = experimentRepository.save(experiment);
        log.info("Created experiment with id: {}, status: PENDING", savedExperiment.getExperimentId());
        return savedExperiment;
    }

    public List<Experiment> listExperiments() {
        log.info("Fetching all experiments");
        return experimentRepository.findAll();
    }

    public List<ExperimentResult> getResults(UUID experimentId) {
        log.info("Fetching results for experiment: {}", experimentId);

        experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + experimentId));

        return experimentResultRepository.findByExperimentId(experimentId);
    }

    public Experiment runBenchmark(UUID experimentId) {
        log.info("Running benchmark for experimentId: {}", experimentId);

        Experiment experiment = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + experimentId));

        List<EvaluationQuestion> questions = evaluationQuestionRepository.findByDatasetId(experiment.getDatasetId());
        if (questions.isEmpty()) {
            throw new IllegalStateException("Cannot run benchmark because dataset has no questions.");
        }

        experiment.setStatus("RUNNING");
        experiment.setStartedAt(LocalDateTime.now());
        experiment.setCompletedAt(null);
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentRepository.save(experiment);

        try {
            Path testSetPath = exportPythonBenchmarkCsv(experiment, questions);

            PythonAiDto.BenchmarkRequest request = new PythonAiDto.BenchmarkRequest();
            request.test_set_path = testSetPath.toAbsolutePath().toString();
            request.mode = benchmarkModeFor(experiment);
            request.generation_provider = "auto";

            PythonAiDto.BenchmarkResponse response = aiClientService.callBenchmark(
                    request,
                    PythonAiDto.BenchmarkResponse.class
            );

            experimentResultRepository.deleteByExperimentId(experimentId);
            persistBenchmarkResults(experiment, questions, response);

            experiment.setStatus("COMPLETED");
            experiment.setCompletedAt(LocalDateTime.now());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentRepository.save(experiment);
            log.info("Benchmark completed for experimentId: {}, pythonRunId: {}", experimentId, response.run_id);
            return experiment;
        } catch (Exception exception) {
            log.error("Benchmark failed for experimentId {}: {}", experimentId, exception.getMessage(), exception);
            experiment.setStatus("FAILED");
            experiment.setCompletedAt(LocalDateTime.now());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentRepository.save(experiment);
            throw new RuntimeException("Benchmark failed: " + exception.getMessage(), exception);
        }
    }

    private Path exportPythonBenchmarkCsv(Experiment experiment, List<EvaluationQuestion> questions) throws IOException {
        Path outputDir = Path.of("target", "python-benchmark");
        Files.createDirectories(outputDir);
        Path csvPath = outputDir.resolve("experiment_" + experiment.getExperimentId() + ".csv").toAbsolutePath();

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write("question,expected_answer,expected_source,expected_page,subject,is_out_of_scope,category");
            writer.newLine();
            for (EvaluationQuestion question : questions) {
                writer.write(csvCell(question.getQuestionText()));
                writer.write(",");
                writer.write(csvCell(question.getGroundTruthAnswer()));
                writer.write(",");
                writer.write(csvCell(""));
                writer.write(",");
                writer.write(csvCell(question.getExpectedPage() == null ? "" : question.getExpectedPage().toString()));
                writer.write(",");
                writer.write(csvCell(""));
                writer.write(",");
                writer.write("false");
                writer.write(",");
                writer.write(csvCell(question.getQuestionType() == null ? "general" : question.getQuestionType()));
                writer.newLine();
            }
        }

        return csvPath;
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String benchmarkModeFor(Experiment experiment) {
        String type = experiment.getExperimentType() == null ? "" : experiment.getExperimentType().toUpperCase();
        return type.contains("FINE") ? "finetuned_only" : "rag";
    }

    private void persistBenchmarkResults(
            Experiment experiment,
            List<EvaluationQuestion> questions,
            PythonAiDto.BenchmarkResponse response) {
        if (response == null || response.results == null) {
            return;
        }

        int count = Math.min(questions.size(), response.results.size());
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = response.results.get(i);
            EvaluationQuestion question = questions.get(i);

            ExperimentResult result = new ExperimentResult();
            result.setExperimentId(experiment.getExperimentId());
            result.setEvaluationQuestionId(question.getEvaluationQuestionId());
            result.setGeneratedAnswer(asString(row.get("actual_answer")));
            result.setFaithfulness(asDouble(row.get("faithfulness_proxy")));
            result.setAnswerRelevance(asDouble(row.get("answer_relevancy_proxy")));
            result.setContextPrecision(asDouble(row.get("context_precision_proxy")));
            result.setContextRecall(asDouble(row.get("context_recall_proxy")));
            result.setAnswerCorrectness(asDouble(row.get("answer_token_f1")));
            result.setSemanticSimilarity(asDouble(row.get("answer_token_f1")));
            result.setLatencyMs(asInteger(row.get("latency_ms")));
            result.setCost(BigDecimal.ZERO);
            result.setCreatedAt(LocalDateTime.now());
            experimentResultRepository.save(result);
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        Double number = asDouble(value);
        return number == null ? null : (int) Math.round(number);
    }
}
