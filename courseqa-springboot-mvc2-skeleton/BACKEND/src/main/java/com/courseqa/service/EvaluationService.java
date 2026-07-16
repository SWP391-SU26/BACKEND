package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.entity.ExperimentResult;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.EvaluationQuestionRepository;
import com.courseqa.repository.ExperimentRepository;
import com.courseqa.repository.ExperimentResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationQuestionRepository evaluationQuestionRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentResultRepository experimentResultRepository;
    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvaluationService(
            EvaluationDatasetRepository evaluationDatasetRepository,
            EvaluationQuestionRepository evaluationQuestionRepository,
            ExperimentRepository experimentRepository,
            ExperimentResultRepository experimentResultRepository,
            ChatService chatService) {
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationQuestionRepository = evaluationQuestionRepository;
        this.experimentRepository = experimentRepository;
        this.experimentResultRepository = experimentResultRepository;
        this.chatService = chatService;
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

    public Map<String, Object> importQuestions(UUID datasetId, MultipartFile file) {
        log.info("Importing evaluation questions from CSV: datasetId={}, filename={}", datasetId, file.getOriginalFilename());

        EvaluationDataset dataset = evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required.");
        }

        try {
            byte[] bytes = file.getBytes();
            String content = stripUtf8Bom(new String(bytes, StandardCharsets.UTF_8));
            List<List<String>> csvRows = parseCsv(content);
            if (csvRows.isEmpty()) {
                throw new IllegalArgumentException("CSV file has no rows.");
            }

            Map<String, Integer> headers = headerIndex(csvRows.get(0));
            Integer questionIndex = firstHeader(headers, "question", "question_text");
            Integer answerIndex = firstHeader(headers, "expected_answer", "ground_truth_answer", "answer");
            if (questionIndex == null || answerIndex == null) {
                throw new IllegalArgumentException("CSV must include question and expected_answer columns.");
            }

            Integer pageIndex = firstHeader(headers, "expected_page", "page");
            Integer categoryIndex = firstHeader(headers, "category", "question_type", "type");
            Integer difficultyIndex = firstHeader(headers, "difficulty");

            int nextQuestionNo = evaluationQuestionRepository.findByDatasetId(datasetId).size() + 1;
            int skippedCount = 0;
            List<EvaluationQuestion> importedQuestions = new ArrayList<>();

            for (int rowIndex = 1; rowIndex < csvRows.size(); rowIndex++) {
                List<String> row = csvRows.get(rowIndex);
                String questionText = cell(row, questionIndex).trim();
                String groundTruthAnswer = cell(row, answerIndex).trim();
                if (questionText.isEmpty() || groundTruthAnswer.isEmpty()) {
                    skippedCount++;
                    continue;
                }

                EvaluationQuestion question = new EvaluationQuestion();
                question.setDatasetId(datasetId);
                question.setCourseId(dataset.getCourseId());
                question.setQuestionNo(nextQuestionNo++);
                question.setQuestionText(questionText);
                question.setGroundTruthAnswer(groundTruthAnswer);
                question.setExpectedPage(parseOptionalInt(cell(row, pageIndex)));
                question.setQuestionType(defaultIfBlank(cell(row, categoryIndex), "FACTUAL"));
                question.setDifficulty(defaultIfBlank(cell(row, difficultyIndex), "MEDIUM"));
                question.setCreatedAt(LocalDateTime.now());
                importedQuestions.add(question);
            }

            evaluationQuestionRepository.saveAll(importedQuestions);
            dataset.setUpdatedAt(LocalDateTime.now());
            evaluationDatasetRepository.save(dataset);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasetId", datasetId);
            result.put("importedCount", importedQuestions.size());
            result.put("skippedCount", skippedCount);
            result.put("checksum", sha256(bytes));
            result.put("filename", file.getOriginalFilename());
            return result;
        } catch (IOException exception) {
            throw new RuntimeException("Could not read CSV file: " + exception.getMessage(), exception);
        }
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

        List<ExperimentResult> results = experimentResultRepository.findByExperimentId(experimentId);
        Map<UUID, EvaluationQuestion> questionsById = new HashMap<>();
        evaluationQuestionRepository.findAllById(
                results.stream()
                        .map(ExperimentResult::getEvaluationQuestionId)
                        .filter(java.util.Objects::nonNull)
                        .toList()
        ).forEach(question -> questionsById.put(question.getEvaluationQuestionId(), question));

        for (ExperimentResult result : results) {
            EvaluationQuestion question = questionsById.get(result.getEvaluationQuestionId());
            if (question != null) {
                result.setQuestionText(question.getQuestionText());
                result.setGroundTruthAnswer(question.getGroundTruthAnswer());
            }
        }
        return results;
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
            experimentResultRepository.deleteByExperimentId(experimentId);
            int resultCount = runBenchmarkWithJavaRag(experiment, questions);

            experiment.setStatus("COMPLETED");
            experiment.setCompletedAt(LocalDateTime.now());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentRepository.save(experiment);
            log.info("Benchmark completed for experimentId: {}, resultCount: {}", experimentId, resultCount);
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

    private String stripUtf8Bom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '"') {
                if (inQuotes && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (current == ',' && !inQuotes) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !inQuotes) {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                if (!isBlankRow(row)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }

        row.add(cell.toString());
        if (!isBlankRow(row)) {
            rows.add(row);
        }
        return rows;
    }

    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(value -> value == null || value.trim().isEmpty());
    }

    private Map<String, Integer> headerIndex(List<String> headerRow) {
        Map<String, Integer> headers = new HashMap<>();
        for (int index = 0; index < headerRow.size(); index++) {
            headers.put(normalizeHeader(headerRow.get(index)), index);
        }
        return headers;
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase().replace("-", "_");
    }

    private Integer firstHeader(Map<String, Integer> headers, String... names) {
        for (String name : names) {
            Integer index = headers.get(normalizeHeader(name));
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private String cell(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private Integer parseOptionalInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String benchmarkModeFor(Experiment experiment) {
        String type = experiment.getExperimentType() == null ? "" : experiment.getExperimentType().toUpperCase();
        return type.contains("FINE") ? "finetuned_only" : "rag";
    }

    private int runBenchmarkWithJavaRag(Experiment experiment, List<EvaluationQuestion> questions) {
        if (experiment.getWorkspaceId() == null) {
            throw new IllegalStateException("Cannot run RAG benchmark because experiment has no workspaceId.");
        }
        if (experiment.getCreatedBy() == null) {
            throw new IllegalStateException("Cannot run RAG benchmark because experiment has no createdBy user.");
        }

        String answerMode = benchmarkModeFor(experiment).equals("finetuned_only") ? "FINE_TUNED" : "RAG";
        UUID sessionId = chatService
                .createOrGetSession(experiment.getCreatedBy(), experiment.getWorkspaceId())
                .getChatSessionId();

        int resultCount = 0;
        for (EvaluationQuestion question : questions) {
            long startedAt = System.nanoTime();
            ChatDto.AskResponse answer = chatService.askQuestion(sessionId, question.getQuestionText(), answerMode);
            int latencyMs = (int) Math.round((System.nanoTime() - startedAt) / 1_000_000.0);
            persistJavaBenchmarkResult(experiment, question, answer, latencyMs);
            resultCount++;
        }
        return resultCount;
    }

    private void persistJavaBenchmarkResult(
            Experiment experiment,
            EvaluationQuestion question,
            ChatDto.AskResponse answer,
            int latencyMs) {
        String generatedAnswer = answer == null ? "" : defaultIfBlank(answer.answer, "");
        List<ChatDto.CitationItem> citations = answer == null || answer.citations == null
                ? List.of()
                : answer.citations;
        List<String> contexts = citations.stream()
                .map(citation -> defaultIfBlank(citation.quoteText, ""))
                .filter(value -> !value.isBlank())
                .toList();

        double answerF1 = tokenF1(generatedAnswer, question.getGroundTruthAnswer());

        ExperimentResult result = new ExperimentResult();
        result.setExperimentId(experiment.getExperimentId());
        result.setEvaluationQuestionId(question.getEvaluationQuestionId());
        result.setGeneratedAnswer(generatedAnswer);
        result.setRetrievedContextJson(toJson(contexts));
        result.setCitationsJson(toJson(citations));
        result.setFaithfulness(faithfulnessProxy(generatedAnswer, contexts));
        result.setAnswerRelevance(answerF1);
        result.setContextPrecision(contextPrecisionProxy(question.getGroundTruthAnswer(), contexts));
        result.setContextRecall(contextRecallProxy(question.getGroundTruthAnswer(), contexts));
        result.setAnswerCorrectness(answerF1);
        result.setSemanticSimilarity(answerF1);
        result.setLatencyMs(latencyMs);
        result.setCost(BigDecimal.ZERO);
        result.setCreatedAt(LocalDateTime.now());
        experimentResultRepository.save(result);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private double tokenF1(String actual, String expected) {
        List<String> actualTokens = tokenize(actual);
        List<String> expectedTokens = tokenize(expected);
        if (expectedTokens.isEmpty()) {
            return actualTokens.isEmpty() ? 1.0 : 0.0;
        }
        if (actualTokens.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> actualCounts = tokenCounts(actualTokens);
        Map<String, Integer> expectedCounts = tokenCounts(expectedTokens);
        int common = 0;
        for (Map.Entry<String, Integer> entry : actualCounts.entrySet()) {
            common += Math.min(entry.getValue(), expectedCounts.getOrDefault(entry.getKey(), 0));
        }
        if (common == 0) {
            return 0.0;
        }
        double precision = (double) common / actualTokens.size();
        double recall = (double) common / expectedTokens.size();
        return round4(2 * precision * recall / (precision + recall));
    }

    private Map<String, Integer> tokenCounts(List<String> tokens) {
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.put(token, counts.getOrDefault(token, 0) + 1);
        }
        return counts;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("[^a-z0-9\\p{IsAlphabetic}]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private double faithfulnessProxy(String answer, List<String> contexts) {
        Set<String> answerTokens = meaningfulTokenSet(answer);
        Set<String> contextTokens = meaningfulTokenSet(String.join(" ", contexts));
        if (answerTokens.isEmpty()) {
            return 0.0;
        }
        answerTokens.retainAll(contextTokens);
        return round4((double) answerTokens.size() / meaningfulTokenSet(answer).size());
    }

    private double contextRecallProxy(String expectedAnswer, List<String> contexts) {
        Set<String> expectedTokens = meaningfulTokenSet(expectedAnswer);
        Set<String> contextTokens = meaningfulTokenSet(String.join(" ", contexts));
        if (expectedTokens.isEmpty()) {
            return 0.0;
        }
        expectedTokens.retainAll(contextTokens);
        return round4((double) expectedTokens.size() / meaningfulTokenSet(expectedAnswer).size());
    }

    private double contextPrecisionProxy(String expectedAnswer, List<String> contexts) {
        Set<String> expectedTokens = meaningfulTokenSet(expectedAnswer);
        if (expectedTokens.isEmpty() || contexts.isEmpty()) {
            return 0.0;
        }
        long relevant = contexts.stream()
                .filter(context -> {
                    Set<String> contextTokens = meaningfulTokenSet(context);
                    contextTokens.retainAll(expectedTokens);
                    return !contextTokens.isEmpty();
                })
                .count();
        return round4((double) relevant / contexts.size());
    }

    private Set<String> meaningfulTokenSet(String text) {
        Set<String> stopWords = Set.of(
                "la", "gi", "va", "co", "duoc", "nhu", "the", "nao", "trong", "theo",
                "nhung", "cac", "cua", "ve", "tai", "de", "mot", "cho", "khi", "tu"
        );
        Set<String> tokens = new HashSet<>();
        for (String token : tokenize(text)) {
            if (token.length() > 1 && !stopWords.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

}
