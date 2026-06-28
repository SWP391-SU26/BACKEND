package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.EvaluationQuestionRepository;
import com.courseqa.repository.ExperimentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FineTuningService {

    private static final Logger log = LoggerFactory.getLogger(FineTuningService.class);
    private static final String SYSTEM_PROMPT =
            "Bạn là trợ lý học tập. Hãy trả lời câu hỏi bằng tiếng Việt rõ ràng, chính xác và không bịa thông tin.";

    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationQuestionRepository evaluationQuestionRepository;
    private final ExperimentRepository experimentRepository;
    private final ObjectMapper objectMapper;

    public FineTuningService(
            EvaluationDatasetRepository evaluationDatasetRepository,
            EvaluationQuestionRepository evaluationQuestionRepository,
            ExperimentRepository experimentRepository,
            ObjectMapper objectMapper) {
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationQuestionRepository = evaluationQuestionRepository;
        this.experimentRepository = experimentRepository;
        this.objectMapper = objectMapper;
    }

    public Experiment createExperimentRecord(String name, UUID datasetId, UUID researcherId, String llmModel, String configJson) {
        log.info("Creating fine-tuning experiment record: name={}, researcherId={}", name, researcherId);

        EvaluationDataset dataset = evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        Experiment experiment = new Experiment();
        experiment.setDatasetId(datasetId);
        experiment.setCourseId(dataset.getCourseId());
        experiment.setWorkspaceId(dataset.getWorkspaceId());
        experiment.setExperimentName(name);
        experiment.setExperimentType("FINE_TUNING");
        experiment.setLlmModel(llmModel);
        experiment.setTopK(5);
        experiment.setTemperature(0.2);
        experiment.setConfigJson(configJson);
        experiment.setCreatedBy(researcherId);
        experiment.setStatus("PENDING");
        experiment.setCreatedAt(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());

        Experiment savedExperiment = experimentRepository.save(experiment);
        log.info("Created fine-tuning experiment with id: {}, status: PENDING", savedExperiment.getExperimentId());
        return savedExperiment;
    }

    public ResponseEntity<Resource> exportJsonl(UUID datasetId) {
        log.info("Exporting JSONL for datasetId: {}", datasetId);

        evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        List<EvaluationQuestion> questions = evaluationQuestionRepository.findByDatasetId(datasetId);
        log.debug("Found {} questions in dataset {}", questions.size(), datasetId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            for (EvaluationQuestion question : questions) {
                Map<String, Object> jsonLine = new HashMap<>();
                jsonLine.put("messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", question.getQuestionText()),
                        Map.of("role", "assistant", "content", question.getGroundTruthAnswer())
                ));
                jsonLine.put("metadata", Map.of(
                        "evaluation_question_id", question.getEvaluationQuestionId().toString(),
                        "dataset_id", datasetId.toString(),
                        "question_type", question.getQuestionType() == null ? "" : question.getQuestionType(),
                        "difficulty", question.getDifficulty() == null ? "" : question.getDifficulty()
                ));

                String jsonString = objectMapper.writeValueAsString(jsonLine);
                baos.write(jsonString.getBytes(StandardCharsets.UTF_8));
                baos.write("\n".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            log.error("Error while converting questions to JSONL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export JSONL: " + e.getMessage(), e);
        }

        Resource resource = new ByteArrayResource(baos.toByteArray());
        String filename = String.format("dataset_%s_train.jsonl", datasetId);

        log.info("Exported JSONL file: {} ({} bytes)", filename, baos.toByteArray().length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(resource);
    }

    public List<String> listExperimentFiles() {
        log.info("Listing all experiment files");

        List<Experiment> experiments = experimentRepository.findAll();
        log.debug("Found {} experiments", experiments.size());

        List<String> files = experiments.stream()
                .map(exp -> String.format("experiment_%s_%s.jsonl", exp.getExperimentId(), exp.getExperimentName()))
                .collect(Collectors.toList());

        log.debug("Generated {} file names", files.size());
        return files;
    }
}
