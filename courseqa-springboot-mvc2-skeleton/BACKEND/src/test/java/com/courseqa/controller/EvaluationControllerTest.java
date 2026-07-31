package com.courseqa.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.dto.EvaluationDto.RunExperimentRequest;
import com.courseqa.model.dto.EvaluationDto.RunPairRequest;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.EvaluationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EvaluationControllerTest {
    @Mock EvaluationService evaluationService;

    @Test
    void createDatasetUsesJwtOwnerAndDocumentSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        List<UUID> documentIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        JwtPrincipal principal = new JwtPrincipal(userId, "researcher@example.com", List.of("RESEARCHER"));
        EvaluationController.CreateDatasetRequest request = new EvaluationController.CreateDatasetRequest();
        request.datasetName = "Fall benchmark";
        request.courseId = courseId;
        request.documentIds = documentIds;
        request.createdBy = UUID.randomUUID();
        request.workspaceId = UUID.randomUUID();
        when(evaluationService.createDataset(eq("Fall benchmark"), eq(courseId), eq(documentIds), eq(userId)))
                .thenReturn(new EvaluationDataset());

        var response = new EvaluationController(evaluationService).createDataset(principal, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(evaluationService).createDataset("Fall benchmark", courseId, documentIds, userId);
    }

    @Test
    void createExperimentIgnoresClientCreatedBy() {
        UUID userId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        JwtPrincipal principal = new JwtPrincipal(userId, "admin@example.com", List.of("ADMIN"));
        EvaluationController.CreateExperimentRequest request = new EvaluationController.CreateExperimentRequest();
        request.datasetId = datasetId;
        request.experimentName = "Strict RAG";
        request.experimentType = "RAG";
        request.llmModel = "qwen-rag-lora";
        request.createdBy = UUID.randomUUID();
        when(evaluationService.createExperiment(eq(datasetId), eq("Strict RAG"), eq("RAG"),
                eq("qwen-rag-lora"), eq("{}"), eq(userId))).thenReturn(new Experiment());

        new EvaluationController(evaluationService).createExperiment(principal, request);

        verify(evaluationService).createExperiment(datasetId, "Strict RAG", "RAG", "qwen-rag-lora", "{}", userId);
    }

    @Test
    void runEndpointReturnsAcceptedForBackgroundJob() {
        UUID experimentId = UUID.randomUUID();
        when(evaluationService.startBenchmark(any(), eq(false))).thenReturn(new Experiment());

        var response = new EvaluationController(evaluationService).runBenchmark(experimentId);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void runEndpointForwardsExplicitUnverifiedAcknowledgement() {
        UUID experimentId = UUID.randomUUID();
        RunExperimentRequest request = new RunExperimentRequest();
        request.allowUnverifiedModel = true;
        when(evaluationService.startBenchmark(experimentId, true)).thenReturn(new Experiment());

        var response = new EvaluationController(evaluationService).runBenchmark(experimentId, request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(evaluationService).startBenchmark(experimentId, true);
    }

    @Test
    void pairEndpointQueuesBothExperimentsWithOneRequest() {
        UUID ragId = UUID.randomUUID();
        UUID fineId = UUID.randomUUID();
        RunPairRequest request = new RunPairRequest();
        request.ragExperimentId = ragId;
        request.fineTunedExperimentId = fineId;
        request.allowUnverifiedModel = true;
        when(evaluationService.startBenchmarkPair(ragId, fineId, true))
                .thenReturn(Map.of("rag", new Experiment(), "fineTuned", new Experiment()));

        var response = new EvaluationController(evaluationService).runBenchmarkPair(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(evaluationService).startBenchmarkPair(ragId, fineId, true);
    }

    @Test
    void cancelEndpointStopsRunningBackgroundJob() {
        UUID experimentId = UUID.randomUUID();
        when(evaluationService.cancelBenchmark(experimentId)).thenReturn(new Experiment());

        var response = new EvaluationController(evaluationService).cancelBenchmark(experimentId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(evaluationService).cancelBenchmark(experimentId);
    }
}
