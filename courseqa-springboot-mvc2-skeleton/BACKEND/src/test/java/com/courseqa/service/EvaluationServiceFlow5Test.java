package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.EvaluationDataset;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceFlow5Test {
    @Mock EvaluationDatasetRepository datasets;
    @Mock EvaluationDatasetDocumentRepository datasetDocuments;
    @Mock EvaluationQuestionRepository questions;
    @Mock ExperimentRepository experiments;
    @Mock ExperimentResultRepository results;
    @Mock CourseRepository courses;
    @Mock CourseDocumentRepository documents;
    @Mock CourseWorkspaceRepository workspaces;
    @Mock LearningScopeService scopes;
    @Mock BenchmarkInferenceService benchmarkInferenceService;
    @Mock AIClientService aiClientService;
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(datasets, datasetDocuments, questions, experiments, results, courses,
                documents, workspaces, scopes, benchmarkInferenceService, aiClientService,
                Runnable::run, new ObjectMapper());
    }

    @Test
    void datasetResolvesSemesterWorkspaceAndPersistsOnlyProcessedDocuments() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        List<UUID> documentIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        Course course = new Course();
        course.setCourseId(courseId);
        course.setSemesterWorkspaceId(semesterId);
        CourseWorkspace workspace = new CourseWorkspace();
        workspace.setWorkspaceId(workspaceId);
        List<CourseDocument> selected = documentIds.stream().map(id -> {
            CourseDocument document = new CourseDocument();
            document.setDocumentId(id);
            document.setCourseId(courseId);
            document.setProcessingStatus("PROCESSED");
            return document;
        }).toList();
        when(scopes.requireAccessibleCourse(courseId, userId, true)).thenReturn(course);
        when(scopes.requireActiveWorkspace(courseId)).thenReturn(workspace);
        when(documents.findAllById(documentIds)).thenReturn(selected);
        when(datasets.save(any())).thenAnswer(invocation -> {
            EvaluationDataset dataset = invocation.getArgument(0);
            dataset.setDatasetId(UUID.randomUUID());
            return dataset;
        });

        EvaluationDataset created = service.createDataset("Benchmark", courseId, documentIds, userId);

        assertEquals(semesterId, created.getSemesterWorkspaceId());
        assertEquals(workspaceId, created.getWorkspaceId());
        assertEquals(documentIds, created.getDocumentIds());
        assertEquals("DRAFT", created.getStatus());
        verify(datasetDocuments, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void frozenDatasetRejectsQuestionMutation() {
        UUID datasetId = UUID.randomUUID();
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setDatasetId(datasetId);
        dataset.setStatus("FROZEN");
        when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));

        assertThrows(ResponseStatusException.class,
                () -> service.addQuestion(datasetId, "Question", "Ground truth"));
    }

    @Test
    void runningExperimentCanBeCancelledWithoutLosingProgress() {
        UUID experimentId = UUID.randomUUID();
        Experiment running = new Experiment();
        running.setExperimentId(experimentId);
        running.setStatus("RUNNING");
        running.setProgress(40);
        running.setSuccessCount(2);
        running.setFailureCount(0);
        when(experiments.findById(experimentId)).thenReturn(Optional.of(running));
        when(experiments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Experiment cancelled = service.cancelBenchmark(experimentId);

        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals(40, cancelled.getProgress());
        assertEquals(2, cancelled.getSuccessCount());
        verify(experiments).save(running);

        ReflectionTestUtils.invokeMethod(service, "executeBenchmark", experimentId);
        verifyNoInteractions(benchmarkInferenceService);
    }

    @Test
    void pendingExperimentCannotBeCancelled() {
        UUID experimentId = UUID.randomUUID();
        Experiment pending = new Experiment();
        pending.setExperimentId(experimentId);
        pending.setStatus("PENDING");
        when(experiments.findById(experimentId)).thenReturn(Optional.of(pending));

        assertThrows(ResponseStatusException.class, () -> service.cancelBenchmark(experimentId));
    }

    @Test
    void queuedExperimentCanBeCancelledBeforeItUsesGpu() {
        UUID experimentId = UUID.randomUUID();
        Experiment queued = new Experiment();
        queued.setExperimentId(experimentId);
        queued.setStatus("QUEUED");
        queued.setProgress(0);
        when(experiments.findById(experimentId)).thenReturn(Optional.of(queued));
        when(experiments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Experiment cancelled = service.cancelBenchmark(experimentId);

        assertEquals("CANCELLED", cancelled.getStatus());
        ReflectionTestUtils.invokeMethod(service, "executeBenchmark", experimentId);
        verifyNoInteractions(benchmarkInferenceService);
    }

    @Test
    void queuedExperimentTransitionsToRunningOnlyWhenWorkerStarts() {
        UUID experimentId = UUID.randomUUID();
        Experiment queued = new Experiment();
        queued.setExperimentId(experimentId);
        queued.setStatus("QUEUED");
        when(experiments.findById(experimentId)).thenReturn(Optional.of(queued));
        when(experiments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Boolean activated = ReflectionTestUtils.invokeMethod(service, "markRunningIfQueued", experimentId);

        assertTrue(Boolean.TRUE.equals(activated));
        assertEquals("RUNNING", queued.getStatus());
        assertTrue(queued.getStartedAt() != null);
    }

    @Test
    void secondExperimentOfSameTypeCannotStartWhileSiblingIsRunning() {
        UUID datasetId = UUID.randomUUID();
        Experiment pending = experiment(UUID.randomUUID(), datasetId, "RAG");
        pending.setStatus("PENDING");
        Experiment running = experiment(UUID.randomUUID(), datasetId, "RAG");
        running.setStatus("RUNNING");
        when(experiments.findById(pending.getExperimentId())).thenReturn(Optional.of(pending));
        when(experiments.findByDatasetIdOrderByCreatedAtDesc(datasetId))
                .thenReturn(List.of(running, pending));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.startBenchmark(pending.getExperimentId(), false));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("already queued or running"));
    }

    @Test
    void completedExperimentCannotRerunWhileRagasIsStillRunning() {
        UUID datasetId = UUID.randomUUID();
        Experiment experiment = experiment(UUID.randomUUID(), datasetId, "FINE_TUNED");
        experiment.setStatus("COMPLETED");
        experiment.setRagasStatus("RUNNING");
        when(experiments.findById(experiment.getExperimentId())).thenReturn(Optional.of(experiment));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.startBenchmark(experiment.getExperimentId(), true));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("RAGAS is still running"));
    }

    @Test
    void staleRunningExperimentIsMarkedFailedInsteadOfBlockingForever() {
        Experiment stale = experiment(UUID.randomUUID(), UUID.randomUUID(), "RAG");
        stale.setStatus("RUNNING");
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(31));
        when(experiments.findAll()).thenReturn(List.of(stale));
        when(experiments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Experiment> listed = service.listExperiments();

        assertEquals("FAILED", listed.get(0).getStatus());
        assertTrue(listed.get(0).getErrorMessage().contains("interrupted"));
        verify(experiments).save(stale);
    }

    @Test
    void benchmarkProfileLocksFullBatchConfiguration() throws Exception {
        String config = ReflectionTestUtils.invokeMethod(service, "withBenchmarkProfile", "{}", 50);
        var parsed = new ObjectMapper().readTree(config).path("benchmarkProfile");

        assertEquals("qwen1.5b-interleaved-v4", parsed.path("version").asText());
        assertEquals(1, parsed.path("batchSize").asInt());
        assertEquals(50, parsed.path("questionCount").asInt());
        assertEquals(1, parsed.path("repetitions").asInt());
        assertEquals(1024, parsed.path("tokenBudgets").path("SHORT").path("maxInputTokens").asInt());
        assertEquals(160, parsed.path("tokenBudgets").path("STANDARD").path("maxNewTokens").asInt());
        assertEquals(192, parsed.path("tokenBudgets").path("DEEP").path("maxNewTokens").asInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelReadinessReadsSharedRuntimeMetadataFromGenerationBlock() {
        when(aiClientService.getModelStatus()).thenReturn(Map.of(
                "base_rag_status", "BASE_RAG_READY",
                "fine_tuned_status", "QUALITY_GATE_FAILED",
                "benchmark_eligible", true,
                "generation", Map.of(
                        "base_model", "Qwen/Qwen2.5-1.5B-Instruct",
                        "adapter_version", "qwen2.5-1.5b-triethoc-lora-v1",
                        "model_verification_status", "UNVERIFIED",
                        "adapter_verified", false)));

        Map<String, Object> readiness = service.modelReadiness();

        assertEquals("Qwen/Qwen2.5-1.5B-Instruct", readiness.get("baseModel"));
        assertEquals("qwen2.5-1.5b-triethoc-lora-v1", readiness.get("adapterVersion"));
        assertEquals("UNVERIFIED", readiness.get("modelVerificationStatus"));
        assertEquals(false, readiness.get("qualityGatePassed"));
        assertEquals("BASE_RAG_READY", readiness.get("baseRagStatus"));
        assertEquals("QUALITY_GATE_FAILED", readiness.get("fineTunedStatus"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void comparisonExposesReportMetadataAndKeepsFineTunedContextMetricsNull() {
        UUID datasetId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID ragId = UUID.randomUUID();
        UUID fineId = UUID.randomUUID();
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setDatasetId(datasetId);
        dataset.setDatasetName("Research snapshot");
        dataset.setDatasetVersion("v1");
        dataset.setStatus("FROZEN");
        dataset.setChecksum("checksum");
        EvaluationQuestion question = new EvaluationQuestion();
        question.setEvaluationQuestionId(questionId);
        question.setDatasetId(datasetId);

        Experiment rag = experiment(ragId, datasetId, "RAG");
        Experiment fine = experiment(fineId, datasetId, "FINE_TUNED");
        ExperimentResult ragResult = result(ragId, questionId, 0.2);
        ragResult.setFaithfulness(0.6);
        ragResult.setContextPrecision(0.9);
        ragResult.setContextRecall(0.4);
        ragResult.setCitationsJson("[{\"title\":\"Page 12\"}]");
        ExperimentResult fineResult = result(fineId, questionId, 0.5);

        when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(experiments.findById(ragId)).thenReturn(Optional.of(rag));
        when(experiments.findById(fineId)).thenReturn(Optional.of(fine));
        when(results.findByExperimentId(ragId)).thenReturn(List.of(ragResult));
        when(results.findByExperimentId(fineId)).thenReturn(List.of(fineResult));
        when(questions.findByDatasetId(datasetId)).thenReturn(List.of(question));
        when(datasetDocuments.findByIdDatasetId(datasetId)).thenReturn(List.of());

        Map<String, Object> report = service.comparison(datasetId, ragId, fineId);
        Map<String, Object> metadata = (Map<String, Object>) report.get("dataset");
        Map<String, Object> fineSummary = (Map<String, Object>) report.get("fineTunedExperiment");
        Map<String, Object> row = ((List<Map<String, Object>>) report.get("perQuestion")).get(0);

        assertEquals("RAGAS_OFFICIAL", report.get("metricStandard"));
        assertEquals("ragas-0.4", report.get("formulaVersion"));
        assertEquals("Research snapshot", metadata.get("name"));
        assertEquals(1, metadata.get("questionCount"));
        assertNull(fineSummary.get("faithfulness"));
        assertNull(fineSummary.get("contextPrecision"));
        assertNull(row.get("fineTunedContextRecall"));
        assertEquals(0.3, row.get("tokenOverlapProxyDelta"));
    }

    private Experiment experiment(UUID id, UUID datasetId, String type) {
        Experiment value = new Experiment();
        value.setExperimentId(id);
        value.setDatasetId(datasetId);
        value.setExperimentName(type + " run");
        value.setExperimentType(type);
        value.setDatasetChecksum("checksum");
        value.setStatus("COMPLETED");
        value.setRagasStatus("COMPLETED");
        value.setRagasProgress(100);
        value.setSuccessCount(1);
        value.setFailureCount(0);
        value.setConfigJson("{\"benchmarkProfile\":{\"version\":\"full-batch-v1\"}}");
        return value;
    }

    private ExperimentResult result(UUID experimentId, UUID questionId, double correctness) {
        ExperimentResult value = new ExperimentResult();
        value.setExperimentId(experimentId);
        value.setEvaluationQuestionId(questionId);
        value.setQuestionText("Question");
        value.setGroundTruthAnswer("Ground truth");
        value.setGeneratedAnswer("Answer");
        value.setAnswerCorrectness(correctness);
        value.setAnswerRelevance(correctness);
        value.setSemanticSimilarity(correctness);
        value.setMetricStandard("RAGAS_OFFICIAL");
        value.setRagasStatus("COMPLETED");
        value.setLatencyMs(1000);
        return value;
    }
}
