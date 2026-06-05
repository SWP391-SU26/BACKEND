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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * EvaluationService - Quản lý evaluation datasets, questions, experiments, results
 * Phần SQL implementation, TODO: gọi Python /ai/benchmark sau khi có API contract từ TV6
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationQuestionRepository evaluationQuestionRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentResultRepository experimentResultRepository;

    public EvaluationService(
            EvaluationDatasetRepository evaluationDatasetRepository,
            EvaluationQuestionRepository evaluationQuestionRepository,
            ExperimentRepository experimentRepository,
            ExperimentResultRepository experimentResultRepository) {
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationQuestionRepository = evaluationQuestionRepository;
        this.experimentRepository = experimentRepository;
        this.experimentResultRepository = experimentResultRepository;
    }

    /**
     * Tạo evaluation dataset mới
     *
     * @param datasetName Tên dataset
     * @param courseId ID của course
     * @param workspaceId ID của workspace
     * @param createdBy ID của user tạo dataset
     * @return EvaluationDataset
     */
    public EvaluationDataset createDataset(String datasetName, UUID courseId, UUID workspaceId, UUID createdBy) {
        log.info("Creating evaluation dataset: name={}, courseId={}, createdBy={}", datasetName, courseId, createdBy);

        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setDatasetName(datasetName);
        dataset.setCourseId(courseId);
        dataset.setWorkspaceId(workspaceId);
        dataset.setCreatedBy(createdBy);
        dataset.setCreatedAt(LocalDateTime.now());

        EvaluationDataset savedDataset = evaluationDatasetRepository.save(dataset);
        log.info("Created evaluation dataset with id: {}", savedDataset.getDatasetId());
        return savedDataset;
    }

    /**
     * Lấy danh sách tất cả datasets
     *
     * @return List<EvaluationDataset>
     */
    public List<EvaluationDataset> listDatasets() {
        log.info("Fetching all evaluation datasets");

        List<EvaluationDataset> datasets = evaluationDatasetRepository.findAll();
        log.debug("Found {} datasets", datasets.size());
        return datasets;
    }

    /**
     * Thêm câu hỏi vào dataset
     * - Lưu EvaluationQuestion
     *
     * @param datasetId ID của dataset
     * @param question Nội dung câu hỏi
     * @param groundTruth Câu trả lời đúng
     * @return EvaluationQuestion
     */
    public EvaluationQuestion addQuestion(UUID datasetId, String question, String groundTruth) {
        log.info("Adding question to dataset: datasetId={}, question={}", datasetId, question);

        // Kiểm tra dataset tồn tại
        evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        // Tạo question mới
        EvaluationQuestion evaluationQuestion = new EvaluationQuestion();
        evaluationQuestion.setDatasetId(datasetId);
        evaluationQuestion.setQuestionText(question);
        evaluationQuestion.setGroundTruthAnswer(groundTruth);

        EvaluationQuestion savedQuestion = evaluationQuestionRepository.save(evaluationQuestion);
        log.info("Added question to dataset - questionId: {}", savedQuestion.getEvaluationQuestionId());

        return savedQuestion;
    }

    /**
     * Lấy danh sách các câu hỏi trong dataset
     *
     * @param datasetId ID của dataset
     * @return List<EvaluationQuestion>
     */
    public List<EvaluationQuestion> getQuestions(UUID datasetId) {
        log.info("Fetching questions for dataset: {}", datasetId);

        // Kiểm tra dataset tồn tại
        evaluationDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationDataset not found with id: " + datasetId));

        List<EvaluationQuestion> questions = evaluationQuestionRepository.findByDatasetId(datasetId);
        log.debug("Found {} questions in dataset {}", questions.size(), datasetId);
        return questions;
    }

    /**
     * Tạo experiment record mới
     * - status mặc định = "PENDING"
     *
     * @param experimentName Tên experiment
     * @param experimentType Loại experiment
     * @param configJson JSON config string
     * @param createdBy ID của researcher/creator
     * @return Experiment
     */
    public Experiment createExperiment(String experimentName, String experimentType, String configJson, UUID createdBy) {
        log.info("Creating experiment: name={}, createdBy={}", experimentName, createdBy);

        Experiment experiment = new Experiment();
        experiment.setExperimentName(experimentName);
        experiment.setExperimentType(experimentType);
        experiment.setConfigJson(configJson);
        experiment.setCreatedBy(createdBy);
        experiment.setStatus("PENDING");
        experiment.setCreatedAt(LocalDateTime.now());

        Experiment savedExperiment = experimentRepository.save(experiment);
        log.info("Created experiment with id: {}, status: PENDING", savedExperiment.getExperimentId());
        return savedExperiment;
    }

    /**
     * Lấy danh sách tất cả experiments
     *
     * @return List<Experiment>
     */
    public List<Experiment> listExperiments() {
        log.info("Fetching all experiments");

        List<Experiment> experiments = experimentRepository.findAll();
        log.debug("Found {} experiments", experiments.size());
        return experiments;
    }

    /**
     * Lấy kết quả của experiment
     *
     * @param experimentId ID của experiment
     * @return List<ExperimentResult>
     */
    public List<ExperimentResult> getResults(UUID experimentId) {
        log.info("Fetching results for experiment: {}", experimentId);

        // Kiểm tra experiment tồn tại
        experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + experimentId));

        List<ExperimentResult> results = experimentResultRepository.findByExperimentId(experimentId);
        log.debug("Found {} results for experiment {}", results.size(), experimentId);
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
    public void runBenchmark(UUID experimentId) {
        log.info("TODO: Implement runBenchmark for experimentId: {}", experimentId);
        throw new UnsupportedOperationException("runBenchmark not implemented yet - waiting for Python API contract from TV6");
    }
}
