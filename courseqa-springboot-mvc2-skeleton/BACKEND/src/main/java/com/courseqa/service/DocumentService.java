package com.courseqa.service;

import com.courseqa.model.dto.DocumentDto;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.DocumentPage;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.DocumentPageRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 150;
    private static final String CHUNK_STRATEGY = "fixed_1200_150";

    private final CourseDocumentRepository courseDocumentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final Path uploadRoot;

    public DocumentService(
            CourseDocumentRepository courseDocumentRepository,
            DocumentPageRepository documentPageRepository,
            DocumentChunkRepository documentChunkRepository,
            @Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        this.courseDocumentRepository = courseDocumentRepository;
        this.documentPageRepository = documentPageRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public DocumentDto.DocumentResponse uploadDocument(MultipartFile file, DocumentDto.UploadDocumentRequest request) {
        validateUpload(file, request);

        try {
            Files.createDirectories(uploadRoot);
            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            String fileType = resolveFileType(originalFilename);
            Path targetPath = uploadRoot.resolve(UUID.randomUUID() + "-" + originalFilename).normalize();
            file.transferTo(targetPath);

            LocalDateTime now = LocalDateTime.now();
            CourseDocument document = new CourseDocument();
            document.setWorkspaceId(request.workspaceId);
            document.setCourseId(request.courseId);
            document.setChapterId(request.chapterId);
            document.setUploadedBy(request.uploadedBy);
            document.setDocumentTitle(stripExtension(originalFilename));
            document.setOriginalFilename(originalFilename);
            document.setFileType(fileType);
            document.setMimeType(file.getContentType());
            document.setFilePath(targetPath.toString());
            document.setFileSizeBytes(file.getSize());
            document.setProcessingStatus("PROCESSING");
            document.setLanguage("vi");
            document.setUploadedAt(now);
            document.setUpdatedAt(now);

            CourseDocument savedDocument = courseDocumentRepository.save(document);
            processDocument(savedDocument, targetPath, fileType);
            return DocumentDto.DocumentResponse.fromEntity(savedDocument);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store or process file.");
        }
    }

    public List<DocumentDto.DocumentResponse> getDocumentsByWorkspace(UUID workspaceId) {
        return courseDocumentRepository.findByWorkspaceIdOrderByUploadedAtDesc(workspaceId).stream()
                .map(DocumentDto.DocumentResponse::fromEntity)
                .toList();
    }

    public List<DocumentDto.PageResponse> getPages(UUID documentId) {
        ensureDocumentExists(documentId);
        return documentPageRepository.findByDocumentIdOrderByPageNumberAsc(documentId).stream()
                .map(DocumentDto.PageResponse::fromEntity)
                .toList();
    }

    public List<DocumentDto.ChunkResponse> getChunks(UUID documentId) {
        ensureDocumentExists(documentId);
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(DocumentDto.ChunkResponse::fromEntity)
                .toList();
    }

    public StoredDocumentFile getStoredFile(UUID documentId) {
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        Path filePath = Path.of(document.getFilePath()).toAbsolutePath().normalize();

        if (!filePath.startsWith(uploadRoot) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored file not found.");
        }

        String mimeType = document.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            try {
                mimeType = Files.probeContentType(filePath);
            } catch (IOException ignored) {
                mimeType = null;
            }
        }

        return new StoredDocumentFile(
                filePath,
                document.getOriginalFilename(),
                mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType
        );
    }

    private void processDocument(CourseDocument document, Path filePath, String fileType) {
        try {
            List<ExtractedPage> extractedPages = extractPages(filePath, fileType);
            List<DocumentPage> pages = savePages(document.getDocumentId(), extractedPages);
            List<DocumentChunk> chunks = saveChunks(document, pages);

            document.setTotalPages(pages.size());
            document.setProcessingStatus(chunks.isEmpty() ? "NO_TEXT" : "PROCESSED");
            document.setErrorMessage(null);
            document.setUpdatedAt(LocalDateTime.now());
            courseDocumentRepository.save(document);
        } catch (Exception exception) {
            document.setProcessingStatus("FAILED");
            document.setErrorMessage(exception.getMessage());
            document.setUpdatedAt(LocalDateTime.now());
            courseDocumentRepository.save(document);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document processing failed: " + exception.getMessage());
        }
    }

    private List<DocumentPage> savePages(UUID documentId, List<ExtractedPage> extractedPages) {
        List<DocumentPage> pages = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (ExtractedPage extractedPage : extractedPages) {
            String cleanedText = cleanText(extractedPage.text());
            DocumentPage page = new DocumentPage();
            page.setDocumentId(documentId);
            page.setPageNumber(extractedPage.pageNumber());
            page.setRawText(extractedPage.text());
            page.setCleanedText(cleanedText);
            page.setWordCount(countWords(cleanedText));
            page.setCharCount(cleanedText.length());
            page.setExtractionStatus(cleanedText.isBlank() ? "NO_TEXT" : "TEXT_EXTRACTED");
            page.setExtractedAt(now);
            pages.add(page);
        }

        return documentPageRepository.saveAll(pages);
    }

    private List<DocumentChunk> saveChunks(CourseDocument document, List<DocumentPage> pages) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 1;
        LocalDateTime now = LocalDateTime.now();

        for (DocumentPage page : pages) {
            String text = page.getCleanedText();
            if (text == null || text.isBlank()) {
                continue;
            }

            String[] words = text.split("\\s+");
            int start = 0;
            while (start < words.length) {
                int end = Math.min(start + CHUNK_SIZE, words.length);
                String content = String.join(" ", java.util.Arrays.copyOfRange(words, start, end)).trim();
                if (!content.isBlank()) {
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocumentId(document.getDocumentId());
                    chunk.setWorkspaceId(document.getWorkspaceId());
                    chunk.setCourseId(document.getCourseId());
                    chunk.setChapterId(document.getChapterId());
                    chunk.setChunkIndex(chunkIndex++);
                    chunk.setChunkStrategy(CHUNK_STRATEGY);
                    chunk.setChunkSize(CHUNK_SIZE);
                    chunk.setChunkOverlap(CHUNK_OVERLAP);
                    chunk.setContent(content);
                    chunk.setPageStart(page.getPageNumber());
                    chunk.setPageEnd(page.getPageNumber());
                    chunk.setTokenCount(countWords(content));
                    chunk.setWordCount(countWords(content));
                    chunk.setCharCount(content.length());
                    chunk.setCreatedAt(now);
                    chunks.add(chunk);
                }

                if (end == words.length) {
                    break;
                }
                start = Math.max(end - CHUNK_OVERLAP, start + 1);
            }
        }

        return documentChunkRepository.saveAll(chunks);
    }

    private List<ExtractedPage> extractPages(Path filePath, String fileType) throws IOException {
        return switch (fileType) {
            case "PDF" -> extractPdfPages(filePath);
            case "DOCX" -> List.of(new ExtractedPage(1, extractDocxText(filePath)));
            case "PPTX" -> extractPptxPages(filePath);
            case "TXT", "MD", "CSV" -> List.of(new ExtractedPage(1, Files.readString(filePath, StandardCharsets.UTF_8)));
            default -> throw new IOException("Unsupported file type: " + fileType);
        };
    }

    private List<ExtractedPage> extractPdfPages(Path filePath) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                pages.add(new ExtractedPage(pageNumber, stripper.getText(document)));
            }
        }
        return pages;
    }

    private String extractDocxText(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private List<ExtractedPage> extractPptxPages(Path filePath) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(filePath);
             XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            int pageNumber = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                StringBuilder text = new StringBuilder();
                slide.getShapes().forEach(shape -> {
                    if (shape instanceof XSLFTextShape textShape) {
                        text.append(textShape.getText()).append('\n');
                    }
                });
                pages.add(new ExtractedPage(pageNumber++, text.toString()));
            }
        }
        return pages;
    }

    private void validateUpload(MultipartFile file, DocumentDto.UploadDocumentRequest request) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required.");
        }
        if (request == null || request.workspaceId == null || request.courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId and courseId are required.");
        }
    }

    private void ensureDocumentExists(UUID documentId) {
        if (!courseDocumentRepository.existsById(documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found.");
        }
    }

    private String sanitizeFilename(String filename) {
        String safeFilename = filename == null || filename.isBlank() ? "document" : filename;
        return safeFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String stripExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String resolveFileType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File extension is required.");
        }
        return filename.substring(dotIndex + 1).toUpperCase(Locale.ROOT);
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private record ExtractedPage(int pageNumber, String text) {
    }

    public record StoredDocumentFile(Path path, String filename, String mimeType) {
    }
}
