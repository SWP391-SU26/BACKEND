package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.EvaluationQuestionRepository;
import com.courseqa.repository.ExperimentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * FineTuningService - Export training data, manage fine-tuning experiments
 * 100% SQL implementation - không cần Python
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FineTuningService {

    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationQuestionRepository evaluationQuestionRepository;
    private final ExperimentRepository experimentRepository;
    private final ObjectMapper objectMapper;

    /**
     * Tạo experiment record mới
     * - status mặc định = "PENDING"
     *
     * @param name Tên experiment
     * @param researcherId ID của researcher
     * @param configJson JSON config string
     * @return Experiment
     */
    public Experiment createExperimentRecord(String name, Long researcherId, String configJson) {
        log.info("Creating fine-tuning experiment record: name={}, researcherId={}", name, researcherId);

        Experiment experiment = new Experiment();
        experiment.setExperimentName(name);
        experiment.setConfigJson(configJson);
        experiment.setStatus("PENDING");
        experiment.setCreatedAt(LocalDateTime.now());

        Experiment savedExperiment = experimentRepository.save(experiment);
        log.info("Created fine-tuning experiment with id: {}, status: PENDING", savedExperiment.getExperimentId());
        return savedExperiment;
    }

    /**
     * Export EvaluationQuestion thành JSONL file download
     * Format: {"prompt": "question_text", "completion": "ground_truth_answer"}
     *
     * @param datasetId ID của dataset
     * @return ResponseEntity<Resource> - file download
     */
    public ResponseEntity<Resource> exportJsonl(UUID datasetId) {
        log.info("Exporting JSONL for datasetId: {}", datasetId);

        // Kiểm tra dataset tồn tại
        evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        // Load tất cả questions
        List<EvaluationQuestion> questions = evaluationQuestionRepository.findByDatasetId(datasetId);
        log.debug("Found {} questions in dataset {}", questions.size(), datasetId);

        // Format thành JSONL
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            for (EvaluationQuestion question : questions) {
                // Tạo JSON object: {"prompt": "...", "completion": "..."}
                Map<String, String> jsonLine = new HashMap<>();
                jsonLine.put("prompt", question.getQuestionText());
                jsonLine.put("completion", question.getGroundTruthAnswer());

                // Write JSON line + newline
                String jsonString = objectMapper.writeValueAsString(jsonLine);
                baos.write(jsonString.getBytes(StandardCharsets.UTF_8));
                baos.write("\n".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            log.error("Error while converting questions to JSONL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export JSONL: " + e.getMessage(), e);
        }

        // Tạo Resource từ ByteArray
        Resource resource = new ByteArrayResource(baos.toByteArray());
        String filename = String.format("dataset_%s_train.jsonl", datasetId);

        log.info("Exported JSONL file: {} ({} bytes)", filename, baos.toByteArray().length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(resource);
    }

    /**
     * Lấy danh sách tên file của tất cả experiments
     * Format: experiment_{id}_{name}.jsonl
     *
     * @return List<String> - danh sách tên file
     */
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
