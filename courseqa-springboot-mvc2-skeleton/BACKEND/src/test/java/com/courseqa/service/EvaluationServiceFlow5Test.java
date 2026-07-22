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
import com.courseqa.repository.ExperimentMetricAggregateRepository;
import com.courseqa.repository.EmbeddingModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Mock ExperimentMetricAggregateRepository aggregates;
    @Mock EmbeddingModelRepository embeddingModels;
    @Mock CourseRepository courses;
    @Mock CourseDocumentRepository documents;
    @Mock CourseWorkspaceRepository workspaces;
    @Mock LearningScopeService scopes;
    @Mock ChatService chatService;
    @Mock AIClientService aiClientService;
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(datasets, datasetDocuments, questions, experiments, results,
                aggregates, embeddingModels, courses,
                documents, workspaces, scopes, chatService, aiClientService, Runnable::run, new ObjectMapper());
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
            document.setIndexingStatus("INDEXED");
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
        verifyNoInteractions(chatService);
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
        verifyNoInteractions(chatService);
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
    void benchmarkProfileLocksFullBatchConfiguration() throws Exception {
        String config = ReflectionTestUtils.invokeMethod(service, "withBenchmarkProfile", "{}", 50);
        var parsed = new ObjectMapper().readTree(config).path("benchmarkProfile");

        assertEquals("full-batch-v1", parsed.path("version").asText());
        assertEquals(50, parsed.path("questionCount").asInt());
        assertEquals(4, parsed.path("batchSize").asInt());
        assertEquals(448, parsed.path("maxInputTokens").asInt());
        assertEquals(64, parsed.path("maxNewTokens").asInt());
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

        assertEquals("OFFICIAL_RAGAS", report.get("metricStandard"));
        assertEquals("ragas-0.4.3", report.get("formulaVersion"));
        assertEquals("Research snapshot", metadata.get("name"));
        assertEquals(1, metadata.get("questionCount"));
        assertNull(fineSummary.get("faithfulness"));
        assertNull(fineSummary.get("contextPrecision"));
        assertNull(row.get("fineTunedContextRecall"));
        assertEquals(0.3, row.get("answerCorrectnessDelta"));
    }

    private Experiment experiment(UUID id, UUID datasetId, String type) {
        Experiment value = new Experiment();
        value.setExperimentId(id);
        value.setDatasetId(datasetId);
        value.setExperimentName(type + " run");
        value.setExperimentType(type);
        value.setDatasetChecksum("checksum");
        value.setMetricStandard("OFFICIAL_RAGAS");
        value.setStatus("COMPLETED");
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
        value.setLatencyMs(1000);
        value.setMetricStandard("OFFICIAL_RAGAS");
        value.setRagasStatus("SUCCESS");
        return value;
    }
}
