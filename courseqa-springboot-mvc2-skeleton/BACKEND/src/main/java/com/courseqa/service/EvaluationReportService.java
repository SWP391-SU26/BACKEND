package com.courseqa.service;

import com.courseqa.model.entity.EvaluationReport;
import com.courseqa.repository.EvaluationReportRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvaluationReportService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final EvaluationReportRepository reports;
    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;
    private final Path reportDir;

    public EvaluationReportService(
            EvaluationReportRepository reports,
            EvaluationService evaluationService,
            ObjectMapper objectMapper,
            @Qualifier("evaluationTaskExecutor") TaskExecutor executor,
            @Value("${app.report-dir:reports/evaluation}") String reportDir) {
        this.reports = reports;
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.reportDir = Path.of(reportDir).toAbsolutePath().normalize();
    }

    public EvaluationReport createReport(UUID datasetId, UUID ragExperimentId, UUID fineTunedExperimentId,
            String requestedLanguage, String requestedTitle, UUID adminId) {
        String language = normalizeLanguage(requestedLanguage);
        Map<String, Object> comparison = evaluationService.comparison(datasetId, ragExperimentId, fineTunedExperimentId);
        EvaluationReport report = new EvaluationReport();
        report.setDatasetId(datasetId);
        report.setRagExperimentId(ragExperimentId);
        report.setFineTunedExperimentId(fineTunedExperimentId);
        report.setLanguage(language);
        report.setTitle(defaultIfBlank(requestedTitle, language.equals("en")
                ? "RBL Experimental Report: RAG vs Fine-tuned"
                : "Báo cáo thực nghiệm RBL: RAG vs Fine-tuned"));
        report.setStatus("QUEUED");
        report.setProgress(0);
        report.setCreatedBy(adminId);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        EvaluationReport saved = reports.save(report);
        executor.execute(() -> generate(saved.getReportId(), comparison));
        return saved;
    }

    public List<EvaluationReport> listReports(UUID adminId) {
        return reports.findByCreatedByOrderByCreatedAtDesc(adminId);
    }

    public EvaluationReport getReport(UUID reportId, UUID adminId) {
        EvaluationReport report = requireReport(reportId);
        requireOwner(report, adminId);
        return report;
    }

    public EvaluationReport retryReport(UUID reportId, UUID adminId) {
        EvaluationReport report = getReport(reportId, adminId);
        if (!"FAILED".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only FAILED reports can be retried.");
        }
        Map<String, Object> comparison = evaluationService.comparison(report.getDatasetId(),
                report.getRagExperimentId(), report.getFineTunedExperimentId());
        report.setStatus("QUEUED");
        report.setProgress(0);
        report.setErrorMessage(null);
        report.setStartedAt(null);
        report.setCompletedAt(null);
        report.setUpdatedAt(LocalDateTime.now());
        EvaluationReport saved = reports.save(report);
        executor.execute(() -> generate(saved.getReportId(), comparison));
        return saved;
    }

    public void deleteReport(UUID reportId, UUID adminId) {
        EvaluationReport report = getReport(reportId, adminId);
        deleteIfPresent(report.getPdfPath());
        deleteIfPresent(report.getDocxPath());
        deleteIfPresent(report.getCsvPath());
        reports.delete(report);
    }

    public DownloadedReport download(UUID reportId, String requestedFormat, UUID adminId) {
        EvaluationReport report = getReport(reportId, adminId);
        if (!"COMPLETED".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is not completed yet.");
        }
        String format = normalizeFormat(requestedFormat);
        String path = switch (format) {
            case "PDF" -> report.getPdfPath();
            case "DOCX" -> report.getDocxPath();
            case "CSV" -> report.getCsvPath();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported format.");
        };
        Path file = safeReportPath(path);
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report artifact is missing.");
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            String filename = file.getFileName().toString();
            String contentType = switch (format) {
                case "PDF" -> "application/pdf";
                case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                default -> "text/csv; charset=UTF-8";
            };
            return new DownloadedReport(filename, contentType, new ByteArrayResource(bytes));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot read report artifact.");
        }
    }

    private void generate(UUID reportId, Map<String, Object> comparison) {
        EvaluationReport report = requireReport(reportId);
        try {
            report.setStatus("GENERATING");
            report.setProgress(10);
            report.setStartedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            reports.save(report);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("generatedAt", LocalDateTime.now().toString());
            snapshot.put("reportId", reportId);
            snapshot.put("language", report.getLanguage());
            snapshot.put("title", report.getTitle());
            snapshot.put("comparison", comparison);
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            report.setSnapshotJson(snapshotJson);
            report.setSnapshotChecksum(sha256(snapshotJson));
            updateProgress(report, 35);

            Path folder = reportDir.resolve(reportId.toString()).normalize();
            if (!folder.startsWith(reportDir)) {
                throw new IOException("Invalid report folder.");
            }
            Files.createDirectories(folder);
            ReportContent content = buildContent(report, comparison);
            String baseName = safeFilename(report.getTitle()) + "-" + STAMP.format(LocalDateTime.now());

            Path csv = folder.resolve(baseName + ".csv").normalize();
            Files.writeString(csv, buildCsv(comparison), StandardCharsets.UTF_8);
            updateProgress(report, 55);

            Path docx = folder.resolve(baseName + ".docx").normalize();
            writeDocx(content, docx);
            updateProgress(report, 75);

            Path pdf = folder.resolve(baseName + ".pdf").normalize();
            writePdf(content, pdf);

            report.setCsvPath(csv.toString());
            report.setDocxPath(docx.toString());
            report.setPdfPath(pdf.toString());
            report.setStatus("COMPLETED");
            report.setProgress(100);
            report.setCompletedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            reports.save(report);
        } catch (Exception exception) {
            report.setStatus("FAILED");
            report.setErrorMessage(exception.getMessage());
            report.setCompletedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            reports.save(report);
        }
    }

    private ReportContent buildContent(EvaluationReport report, Map<String, Object> comparison) {
        boolean en = "en".equals(report.getLanguage());
        Map<String, Object> dataset = map(comparison.get("dataset"));
        Map<String, Object> rag = map(comparison.get("ragExperiment"));
        Map<String, Object> fine = map(comparison.get("fineTunedExperiment"));
        Map<String, Object> profile = map(comparison.get("benchmarkProfile"));
        List<Map<String, Object>> rows = rows(comparison);

        List<ReportSection> sections = new ArrayList<>();
        sections.add(new ReportSection(en ? "Table of contents" : "Mục lục", List.of(
                "1. " + (en ? "Executive summary" : "Tóm tắt kết quả"),
                "2. Dataset, snapshot and checksum",
                "3. " + (en ? "Model and experiment configuration" : "Cấu hình model và thí nghiệm"),
                "4. " + (en ? "Methodology and metrics" : "Phương pháp và metric"),
                "5. " + (en ? "Result dashboard" : "Dashboard kết quả"),
                "6. " + (en ? "Error analysis, limitations and appendix" : "Phân tích lỗi, giới hạn và phụ lục"))));

        String result = conclusion(rag, fine, en);
        sections.add(new ReportSection(en ? "1. Executive summary" : "1. Tóm tắt kết quả", List.of(
                result,
                en ? "The report does not claim a universally best model. It compares two runs on one frozen test set."
                        : "Báo cáo không tuyên bố một model tốt nhất toàn diện. Kết luận chỉ dựa trên hai run cùng một test set đã đóng băng.",
                en ? "Student-facing chat remains RAG; Fine-tuned is treated as a research benchmark branch."
                        : "Chatbot cho student hiện là RAG; Fine-tuned được trình bày là nhánh research benchmark.")));

        sections.add(new ReportSection(en ? "2. Dataset, snapshot and checksum" : "2. Dataset, snapshot và checksum", List.of(
                line(en ? "Dataset" : "Dataset", dataset.get("name")),
                line("Dataset ID", report.getDatasetId()),
                line("Dataset checksum", comparison.get("datasetChecksum")),
                line(en ? "Question count" : "Số câu hỏi", dataset.get("questionCount")),
                line(en ? "Document count" : "Số tài liệu", dataset.get("documentCount")),
                en ? "The snapshot is stored in the report row so later experiment changes do not silently alter submitted numbers."
                        : "Snapshot số liệu được lưu trong bản ghi report để thay đổi experiment về sau không làm lệch số liệu đã nộp.")));

        sections.add(new ReportSection(en ? "3. Model and experiment configuration" : "3. Cấu hình model và thí nghiệm", List.of(
                line("RAG experiment", rag.get("name")),
                line("RAG model", first(rag.get("baseModel"), rag.get("llmModel"), rag.get("model"))),
                line("RAG generation mode", rag.get("generationMode")),
                line("RAG embedding", first(rag.get("embeddingModel"), profile.get("embeddingModel"), "N/A")),
                line("Fine-tuned experiment", fine.get("name")),
                line("Fine-tuned base model", first(fine.get("baseModel"), fine.get("llmModel"), fine.get("model"))),
                line("Fine-tuned adapter", first(fine.get("adapterVersion"), fine.get("fineTunedModelName"), "N/A")),
                line("Fine-tuned verification", first(fine.get("modelVerificationStatus"), "UNVERIFIED/UNKNOWN")),
                line("Benchmark profile", first(profile.get("version"), profile.get("profileVersion"), "N/A")),
                line("Batch size", first(profile.get("batchSize"), rag.get("batchSize"), "N/A")),
                line("Token limit", tokenBudget(profile)),
                line("Prompt version", first(rag.get("promptVersion"), fine.get("promptVersion"), "N/A")))));

        sections.add(new ReportSection(en ? "4. Methodology and metrics" : "4. Phương pháp và metric", List.of(
                en ? "Both experiments must share dataset checksum and benchmark profile before comparison is allowed."
                        : "Hai experiment chỉ được so sánh nếu cùng dataset checksum và cùng benchmark profile.",
                en ? "Local proxy metrics are labelled as LOCAL_PROXY and must not be called Official RAGAS."
                        : "Metric local proxy được ghi nhãn LOCAL_PROXY, không gọi là Official RAGAS.",
                en ? "If Official RAGAS is available, it is presented separately from local proxy values."
                        : "Nếu có Official RAGAS, report trình bày tách khỏi bảng local proxy.",
                en ? "Fine-tuned has no retrieval context, so citation/context metrics are marked Not applicable instead of zero."
                        : "Fine-tuned không có retrieval context, nên citation/context metric ghi Không áp dụng thay vì ghi 0.",
                en ? "Local token F1 compares generated answer with ground truth: precision = overlap/generated tokens, recall = overlap/ground-truth tokens, F1 = 2PR/(P+R)."
                        : "Local token F1 so sánh câu trả lời AI với ground truth: precision = token trùng/token câu trả lời, recall = token trùng/token ground truth, F1 = 2PR/(P+R).")));

        sections.add(new ReportSection(en ? "5. Result dashboard" : "5. Dashboard kết quả", List.of(
                metricLine("RAG answer correctness", rag.get("tokenOverlapProxy")),
                metricLine("Fine-tuned answer correctness", fine.get("tokenOverlapProxy")),
                metricLine("RAG answer relevance", rag.get("answerRelevance")),
                metricLine("Fine-tuned answer relevance", fine.get("answerRelevance")),
                metricLine("RAG latency", first(rag.get("latencyMs"), rag.get("effectiveLatencyMs"))),
                metricLine("Fine-tuned latency", first(fine.get("latencyMs"), fine.get("effectiveLatencyMs"))),
                metricLine("RAG faithfulness", rag.get("faithfulness")),
                metricLine("RAG context precision", rag.get("contextPrecision")),
                metricLine("RAG context recall", rag.get("contextRecall")),
                en ? "Fine-tuned citation/context metrics: Not applicable." : "Citation/context metric của Fine-tuned: Không áp dụng.",
                line(en ? "Valid paired samples" : "Số mẫu ghép hợp lệ", rows.size()),
                line(en ? "Rows with any error" : "Số dòng có lỗi", rows.stream().filter(EvaluationReportService::hasError).count()))));

        sections.add(new ReportSection(en ? "6. Error analysis, limitations and appendix" : "6. Phân tích lỗi, giới hạn và phụ lục", List.of(
                en ? "Partial results are preserved. Failed questions remain visible in CSV and appendix."
                        : "Partial result được giữ lại. Câu lỗi vẫn xuất hiện trong CSV và phụ lục.",
                en ? "Limitations: dataset size, ground-truth quality, retrieval quality and evaluator availability can affect conclusions."
                        : "Giới hạn: kích thước dataset, chất lượng ground truth, chất lượng retrieval và khả dụng của evaluator đều ảnh hưởng kết luận.",
                en ? "Appendix preview:" : "Xem trước phụ lục:",
                appendixPreview(rows, en))));

        return new ReportContent(report.getTitle(), en ? "FStu CourseQA Research Report" : "FStu CourseQA - Báo cáo nghiên cứu",
                LocalDateTime.now().toString(), sections);
    }

    private String buildCsv(Map<String, Object> comparison) {
        List<String> headers = List.of("questionId", "question", "groundTruth", "ragAnswer", "fineTunedAnswer",
                "ragTokenOverlapProxy", "fineTunedTokenOverlapProxy", "tokenOverlapProxyDelta",
                "ragAnswerRelevance", "fineTunedAnswerRelevance", "ragFaithfulness",
                "ragContextPrecision", "ragContextRecall", "fineTunedContextMetrics",
                "ragLatencyMs", "fineTunedLatencyMs", "ragCitations", "ragError", "fineTunedError");
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(headers.stream().map(EvaluationReportService::csv).collect(Collectors.joining(","))).append("\n");
        for (Map<String, Object> row : rows(comparison)) {
            List<Object> values = new ArrayList<>();
            values.add(value(row, "questionId"));
            values.add(value(row, "question"));
            values.add(value(row, "groundTruth"));
            values.add(value(row, "ragAnswer"));
            values.add(value(row, "fineTunedAnswer"));
            values.add(value(row, "ragTokenOverlapProxy"));
            values.add(value(row, "fineTunedTokenOverlapProxy"));
            values.add(value(row, "tokenOverlapProxyDelta"));
            values.add(value(row, "ragAnswerRelevance"));
            values.add(value(row, "fineTunedAnswerRelevance"));
            values.add(value(row, "ragFaithfulness"));
            values.add(value(row, "ragContextPrecision"));
            values.add(value(row, "ragContextRecall"));
            values.add("N/A - Fine-tuned has no retrieval context");
            values.add(first(value(row, "ragEffectiveLatencyMs"), value(row, "ragLatencyMs")));
            values.add(first(value(row, "fineTunedEffectiveLatencyMs"), value(row, "fineTunedLatencyMs")));
            values.add(value(row, "ragCitations"));
            values.add(value(row, "ragError"));
            values.add(value(row, "fineTunedError"));
            csv.append(values.stream().map(EvaluationReportService::csv).collect(Collectors.joining(","))).append("\n");
        }
        return csv.toString();
    }

    private void writeDocx(ReportContent content, Path output) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); OutputStream stream = Files.newOutputStream(output)) {
            paragraph(doc, content.subtitle(), 14, true, ParagraphAlignment.CENTER);
            paragraph(doc, content.title(), 20, true, ParagraphAlignment.CENTER);
            paragraph(doc, "Generated at: " + content.generatedAt(), 10, false, ParagraphAlignment.CENTER);
            for (ReportSection section : content.sections()) {
                paragraph(doc, section.title(), 15, true, ParagraphAlignment.LEFT);
                for (String item : section.lines()) {
                    paragraph(doc, item, 10, false, ParagraphAlignment.LEFT);
                }
            }
            doc.write(stream);
        }
    }

    private void paragraph(XWPFDocument doc, String text, int size, boolean bold, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(alignment);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Arial");
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(defaultIfBlank(text, "—"));
    }

    @SuppressWarnings("unused")
    private void table(XWPFDocument doc, List<List<String>> rows) {
        XWPFTable table = doc.createTable(rows.size(), rows.isEmpty() ? 1 : rows.get(0).size());
        CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW("5000");
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < rows.get(r).size(); c++) {
                XWPFTableCell cell = row.getCell(c);
                cell.setText(rows.get(r).get(c));
            }
        }
    }

    private void writePdf(ReportContent content, Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font regular = loadFont(document);
            List<String> lines = new ArrayList<>();
            lines.add(content.subtitle());
            lines.add(content.title());
            lines.add("Generated at: " + content.generatedAt());
            lines.add("");
            for (ReportSection section : content.sections()) {
                lines.add(section.title());
                lines.addAll(section.lines());
                lines.add("");
            }
            writePdfLines(document, regular, lines);
            document.save(output.toFile());
        }
    }

    private PDType0Font loadFont(PDDocument document) throws IOException {
        List<Path> candidates = List.of(
                Path.of("C:/Windows/Fonts/arial.ttf"),
                Path.of("C:/Windows/Fonts/calibri.ttf"),
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return PDType0Font.load(document, candidate.toFile());
            }
        }
        byte[] emptyTtf = new byte[0];
        try {
            return PDType0Font.load(document, new ByteArrayInputStream(emptyTtf));
        } catch (IOException ignored) {
            throw new IOException("No Unicode TrueType font found for PDF generation.");
        }
    }

    private void writePdfLines(PDDocument document, PDType0Font font, List<String> sourceLines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDPageContentStream content = new PDPageContentStream(document, page);
        float margin = 50;
        float y = page.getMediaBox().getHeight() - margin;
        float width = page.getMediaBox().getWidth() - margin * 2;
        float fontSize = 10;
        float leading = 15;
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(margin, y);
        for (String raw : sourceLines) {
            for (String line : wrap(raw, font, fontSize, width)) {
                if (y < margin) {
                    content.endText();
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - margin;
                    content.beginText();
                    content.setFont(font, fontSize);
                    content.newLineAtOffset(margin, y);
                }
                content.showText(sanitizePdfText(line));
                content.newLineAtOffset(0, -leading);
                y -= leading;
            }
        }
        content.endText();
        content.close();
    }

    private List<String> wrap(String text, PDType0Font font, float fontSize, float maxWidth) throws IOException {
        if (text == null || text.isBlank()) return List.of("");
        List<String> wrapped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(sanitizePdfText(candidate)) / 1000 * fontSize;
            if (width > maxWidth && !current.isEmpty()) {
                wrapped.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        wrapped.add(current.toString());
        return wrapped;
    }

    private void updateProgress(EvaluationReport report, int progress) {
        report.setProgress(progress);
        report.setUpdatedAt(LocalDateTime.now());
        reports.save(report);
    }

    private EvaluationReport requireReport(UUID reportId) {
        return reports.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
    }

    private void requireOwner(EvaluationReport report, UUID adminId) {
        if (adminId == null || !Objects.equals(report.getCreatedBy(), adminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this report.");
        }
    }

    private Path safeReportPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report artifact is missing.");
        }
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!path.startsWith(reportDir)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid report path.");
        }
        return path;
    }

    private void deleteIfPresent(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return;
        try {
            Path path = safeReportPath(rawPath);
            Files.deleteIfExists(path);
        } catch (RuntimeException | IOException ignored) {
            // Metadata deletion should not fail only because an old artifact is already gone.
        }
    }

    private static String normalizeLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "vi";
    }

    private static String normalizeFormat(String format) {
        String normalized = format == null ? "PDF" : format.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PDF", "DOCX", "CSV").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be PDF, DOCX or CSV.");
        }
        return normalized;
    }

    private static String safeFilename(String value) {
        String cleaned = defaultIfBlank(value, "flow5-report")
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("-{2,}", "-");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private static String csv(Object value) {
        String text = stringify(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hashed) builder.append(String.format("%02x", b));
        return builder.toString();
    }

    private static String sanitizePdfText(String value) {
        return defaultIfBlank(value, "").replace("\t", "    ").replace("\r", "");
    }

    private static String line(String label, Object value) {
        return label + ": " + stringify(value);
    }

    private static String metricLine(String label, Object value) {
        if (value == null) return label + ": N/A";
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (numeric >= 0 && numeric <= 1) return label + ": " + String.format(Locale.US, "%.2f%%", numeric * 100);
        }
        return label + ": " + stringify(value);
    }

    private static String conclusion(Map<String, Object> rag, Map<String, Object> fine, boolean en) {
        Double ragScore = number(rag.get("tokenOverlapProxy"));
        Double fineScore = number(fine.get("tokenOverlapProxy"));
        if (ragScore == null || fineScore == null) {
            return en ? "The available data is insufficient for a quality conclusion."
                    : "Dữ liệu hiện tại chưa đủ để kết luận chất lượng.";
        }
        double diff = fineScore - ragScore;
        if (Math.abs(diff) < 0.02) {
            return en ? "The two runs are near equivalent on local answer-correctness proxy."
                    : "Hai run gần tương đương theo local answer-correctness proxy.";
        }
        if (diff > 0) {
            return en ? "Fine-tuned scores higher on local answer-correctness proxy, but RAG remains stronger for citation and traceability."
                    : "Fine-tuned cao hơn ở local answer-correctness proxy, nhưng RAG vẫn mạnh hơn về citation và truy vết nguồn.";
        }
        return en ? "RAG scores higher on local answer-correctness proxy and provides citation/context traceability."
                : "RAG cao hơn ở local answer-correctness proxy và có citation/context để truy vết.";
    }

    private static String appendixPreview(List<Map<String, Object>> rows, boolean en) {
        if (rows.isEmpty()) return en ? "No paired question rows." : "Không có dòng câu hỏi ghép được.";
        return rows.stream().limit(5)
                .map(row -> "- " + stringify(row.get("question")) + " | Δ=" + stringify(row.get("tokenOverlapProxyDelta"))
                        + (hasError(row) ? " | ERROR" : ""))
                .collect(Collectors.joining("\n"));
    }

    private static String tokenBudget(Map<String, Object> profile) {
        Object direct = first(profile.get("maxInputTokens"), profile.get("maxNewTokens"), profile.get("tokenLimit"));
        if (direct != null) return stringify(direct);
        Object budgets = profile.get("tokenBudgets");
        return budgets == null ? "N/A" : stringify(budgets);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> comparison) {
        Object value = comparison.get("perQuestion");
        return value instanceof List<?> list ? list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList() : List.of();
    }

    private static Object value(Map<String, Object> row, String key) {
        return row.get(key);
    }

    private static boolean hasError(Map<String, Object> row) {
        return row.get("ragError") != null || row.get("fineTunedError") != null;
    }

    private static Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Object first(Object... values) {
        for (Object value : values) {
            if (value != null && !stringify(value).isBlank()) return value;
        }
        return null;
    }

    private static String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof String text) return text;
        return String.valueOf(value);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record DownloadedReport(String filename, String contentType, Resource resource) { }

    private record ReportContent(String title, String subtitle, String generatedAt, List<ReportSection> sections) { }

    private record ReportSection(String title, List<String> lines) { }
}
