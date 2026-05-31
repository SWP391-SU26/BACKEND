package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.entity.ExperimentResult;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.EvaluationQuestionRepository;
import com.courseqa.repository.ExperimentRepository;
import com.courseqa.repository.ExperimentResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EvaluationService - Quản lý evaluation datasets, questions, experiments, results
 * Phần SQL implementation, TODO: gọi Python /ai/benchmark sau khi có API contract từ TV6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationQuestionRepository evaluationQuestionRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentResultRepository experimentResultRepository;

    /**
     * Tạo evaluation dataset mới
     *
     * @param name Tên dataset
     * @param subjectId ID của subject/course
     * @param createdBy ID của user tạo dataset
     * @return EvaluationDataset
     */
    public EvaluationDataset createDataset(String name, Long subjectId, Long createdBy) {
        logger.info("Creating evaluation dataset: name={}, subjectId={}, createdBy={}", name, subjectId, createdBy);

        EvaluationDataset dataset = EvaluationDataset.builder()
                .name(name)
                .subjectId(subjectId)
                .questionCount(0)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();

        EvaluationDataset savedDataset = evaluationDatasetRepository.save(dataset);
        logger.info("Created evaluation dataset with id: {}", savedDataset.getId());
        return savedDataset;
    }

    /**
     * Lấy danh sách tất cả datasets
     *
     * @return List<EvaluationDataset>
     */
    public List<EvaluationDataset> listDatasets() {
        logger.info("Fetching all evaluation datasets");

        List<EvaluationDataset> datasets = evaluationDatasetRepository.findAll();
        logger.debug("Found {} datasets", datasets.size());
        return datasets;
    }

    /**
     * Thêm câu hỏi vào dataset
     * - Lưu EvaluationQuestion
     * - Update question_count trong EvaluationDataset
     *
     * @param datasetId ID của dataset
     * @param question Nội dung câu hỏi
     * @param groundTruth Câu trả lời đúng
     * @return EvaluationQuestion
     */
    public EvaluationQuestion addQuestion(Long datasetId, String question, String groundTruth) {
        logger.info("Adding question to dataset: datasetId={}, question={}", datasetId, question);

        // Kiểm tra dataset tồn tại
        EvaluationDataset dataset = evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset", "id", datasetId));

        // Tạo question mới
        EvaluationQuestion evaluationQuestion = EvaluationQuestion.builder()
                .datasetId(datasetId)
                .questionText(question)
                .groundTruthAnswer(groundTruth)
                .build();

        EvaluationQuestion savedQuestion = evaluationQuestionRepository.save(evaluationQuestion);
        logger.info("Added question to dataset - questionId: {}", savedQuestion.getId());

        // Update question_count trong dataset
        dataset.setQuestionCount(dataset.getQuestionCount() + 1);
        evaluationDatasetRepository.save(dataset);
        logger.debug("Updated dataset question_count to: {}", dataset.getQuestionCount());

        return savedQuestion;
    }

    /**
     * Lấy danh sách các câu hỏi trong dataset
     *
     * @param datasetId ID của dataset
     * @return List<EvaluationQuestion>
     */
    public List<EvaluationQuestion> getQuestions(Long datasetId) {
        logger.info("Fetching questions for dataset: {}", datasetId);

        // Kiểm tra dataset tồn tại
        evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset", "id", datasetId));

        List<EvaluationQuestion> questions = evaluationQuestionRepository.findByDatasetId(datasetId);
        logger.debug("Found {} questions in dataset {}", questions.size(), datasetId);
        return questions;
    }

    /**
     * Tạo experiment record mới
     * - status mặc định = "PENDING"
     *
     * @param name Tên experiment
     * @param researcherId ID của researcher
     * @param configJson JSON config string
     * @return Experiment
     */
    public Experiment createExperiment(String name, Long researcherId, String configJson) {
        logger.info("Creating experiment: name={}, researcherId={}", name, researcherId);

        Experiment experiment = Experiment.builder()
                .name(name)
                .researcherId(researcherId)
                .configJson(configJson)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        Experiment savedExperiment = experimentRepository.save(experiment);
        logger.info("Created experiment with id: {}, status: PENDING", savedExperiment.getId());
        return savedExperiment;
    }

    /**
     * Lấy danh sách tất cả experiments
     *
     * @return List<Experiment>
     */
    public List<Experiment> listExperiments() {
        logger.info("Fetching all experiments");

        List<Experiment> experiments = experimentRepository.findAll();
        logger.debug("Found {} experiments", experiments.size());
        return experiments;
    }

    /**
     * Lấy kết quả của experiment
     *
     * @param experimentId ID của experiment
     * @return List<ExperimentResult>
     */
    public List<ExperimentResult> getResults(Long experimentId) {
        logger.info("Fetching results for experiment: {}", experimentId);

        // Kiểm tra experiment tồn tại
        experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment", "id", experimentId));

        List<ExperimentResult> results = experimentResultRepository.findByExperimentId(experimentId);
        logger.debug("Found {} results for experiment {}", results.size(), experimentId);
        return results;
    }

    /**
     * TODO: Chạy benchmark cho experiment (gọi Python AI Engine)
     * Cần implement khi có API contract từ TV6
     *
     * Logic:
     * 1. Load Experiment từ SQL
     * 2. Load EvaluationQuestion từ associated dataset
     * 3. Gọi AIClientService.callBenchmark()
     * 4. Lưu từng ExperimentResult vào SQL
     * 5. Update Experiment.status = "COMPLETED"
     */
    public void runBenchmark(Long experimentId) {
        logger.info("TODO: Implement runBenchmark for experimentId: {}", experimentId);
        throw new UnsupportedOperationException("runBenchmark not implemented yet - waiting for Python API contract from TV6");
    }
}
