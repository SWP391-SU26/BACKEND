package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.EvaluationReport;
import com.courseqa.repository.EvaluationReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationReportServiceTest {
    @TempDir
    Path reportDir;

    @Test
    void createsDownloadableCsvDocxAndPdfFromOneFrozenSnapshot() throws Exception {
        EvaluationReportRepository reports = mock(EvaluationReportRepository.class);
        EvaluationService evaluations = mock(EvaluationService.class);
        AtomicReference<EvaluationReport> stored = new AtomicReference<>();
        UUID datasetId = UUID.randomUUID();
        UUID ragId = UUID.randomUUID();
        UUID fineTunedId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(reports.save(any(EvaluationReport.class))).thenAnswer(invocation -> {
            EvaluationReport report = invocation.getArgument(0);
            if (report.getReportId() == null) report.setReportId(UUID.randomUUID());
            stored.set(report);
            return report;
        });
        when(reports.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(stored.get()));
        when(evaluations.comparison(datasetId, ragId, fineTunedId)).thenReturn(Map.of(
                "dataset", Map.of("name", "Triet hoc", "questionCount", 2, "documentCount", 1),
                "ragExperiment", Map.of("name", "RAG", "faithfulness", 0.91, "answerRelevancy", 0.88),
                "fineTunedExperiment", Map.of("name", "Fine-tuned", "faithfulness", 0.72,
                        "answerRelevancy", 0.81),
                "benchmarkProfile", Map.of("version", "test-v1"),
                "datasetChecksum", "sha256:test",
                "perQuestion", List.of(Map.of(
                        "question", "Triet hoc la gi?",
                        "ragAnswer", "RAG answer",
                        "fineTunedAnswer", "Fine-tuned answer"))));

        EvaluationReportService service = new EvaluationReportService(
                reports, evaluations, new ObjectMapper(), Runnable::run, reportDir.toString());

        EvaluationReport created = service.createReport(
                datasetId, ragId, fineTunedId, "en", "RAG vs Fine-tuned", adminId);

        assertEquals("COMPLETED", created.getStatus());
        assertEquals(100, created.getProgress());
        assertNotNull(created.getSnapshotChecksum());
        assertTrue(created.getSnapshotJson().contains("Triet hoc"));

        byte[] pdf = Files.readAllBytes(Path.of(created.getPdfPath()));
        byte[] docx = Files.readAllBytes(Path.of(created.getDocxPath()));
        String csv = Files.readString(Path.of(created.getCsvPath()), StandardCharsets.UTF_8);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertEquals('P', docx[0]);
        assertEquals('K', docx[1]);
        assertTrue(csv.contains("Triet hoc la gi?"));
        assertTrue(service.download(created.getReportId(), "PDF", adminId)
                .resource().contentLength() > 0);
    }
}
