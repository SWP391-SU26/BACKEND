package com.courseqa.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.courseqa.model.dto.DocumentDto;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.Chapter;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.DocumentPage;
import com.courseqa.model.entity.UserRole;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.DocumentPageRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;
    private final Path previewRoot;
    private final Cloudinary cloudinary;

    public DocumentService(
            CourseDocumentRepository courseDocumentRepository,
            CourseRepository courseRepository,
            ChapterRepository chapterRepository,
            CourseWorkspaceRepository courseWorkspaceRepository,
            DocumentPageRepository documentPageRepository,
            DocumentChunkRepository documentChunkRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${cloudinary.cloud-name:}") String cloudinaryCloudName,
            @Value("${cloudinary.api-key:}") String cloudinaryApiKey,
            @Value("${cloudinary.api-secret:}") String cloudinaryApiSecret
    ) {
        this.courseDocumentRepository = courseDocumentRepository;
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.courseWorkspaceRepository = courseWorkspaceRepository;
        this.documentPageRepository = documentPageRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.previewRoot = this.uploadRoot.resolve("previews").normalize();
        this.cloudinary = createCloudinary(cloudinaryCloudName, cloudinaryApiKey, cloudinaryApiSecret);
    }

    private Cloudinary createCloudinary(String cloudName, String apiKey, String apiSecret) {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret)) {
            return null;
        }

        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    private void ensureCloudinaryConfigured() {
        if (cloudinary == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET."
            );
        }
    }

    private CloudinaryUpload uploadToCloudinary(Path filePath, String publicId) throws IOException {
        ensureCloudinaryConfigured();
        Map<?, ?> uploadResult = cloudinary.uploader().upload(filePath.toFile(), ObjectUtils.asMap(
                "resource_type", "raw",
                "public_id", publicId
        ));
        return new CloudinaryUpload(
                String.valueOf(uploadResult.get("public_id")),
                String.valueOf(uploadResult.get("secure_url"))
        );
    }

    private CloudinaryUpload createAndUploadPreview(Path originalPath, String originalFilename, String fileType) throws IOException {
        if (!List.of("DOCX", "PPTX").contains(fileType)) {
            return null;
        }

        if (findLibreOffice() == null) {
            return null;
        }

        Files.createDirectories(previewRoot);
        Path previewPath = previewRoot.resolve(UUID.randomUUID() + "-" + stripExtension(originalFilename) + ".pdf").normalize();
        try {
            convertOfficeDocumentToPdf(originalPath, previewRoot, previewPath);

            if (!Files.exists(previewPath)) {
                return null;
            }

            return uploadToCloudinary(previewPath, "courseqa/previews/" + previewPath.getFileName());
        } finally {
            deleteStoredFile(previewPath, previewRoot);
        }
    }

    private boolean isCloudStored(CourseDocument document) {
        return "CLOUDINARY".equalsIgnoreCase(document.getStorageProvider())
                && document.getCloudinarySecureUrl() != null
                && !document.getCloudinarySecureUrl().isBlank();
    }

    private void destroyCloudinaryAsset(String publicId) {
        if (cloudinary == null || publicId == null || publicId.isBlank()) {
            return;
        }

        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
        } catch (IOException ignored) {
            // Keep document deletion usable even if Cloudinary cleanup is temporarily unavailable.
        }
    }

    @Transactional
    public DocumentDto.DocumentResponse uploadDocument(MultipartFile file, DocumentDto.UploadDocumentRequest request) {
        validateUpload(file, request);

        String uploadedPublicId = null;
        String uploadedPreviewPublicId = null;
        Path targetPath = null;

        try {
            ensureCloudinaryConfigured();
            Files.createDirectories(uploadRoot);
            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            String fileType = resolveFileType(originalFilename);
            targetPath = uploadRoot.resolve(UUID.randomUUID() + "-" + originalFilename).normalize();
            file.transferTo(targetPath);
            CloudinaryUpload originalUpload = uploadToCloudinary(
                    targetPath,
                    "courseqa/documents/" + UUID.randomUUID() + "-" + originalFilename
            );
            uploadedPublicId = originalUpload.publicId();

            CloudinaryUpload previewUpload = createAndUploadPreview(targetPath, originalFilename, fileType);
            if (previewUpload != null) {
                uploadedPreviewPublicId = previewUpload.publicId();
            }

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
            document.setFilePath(originalUpload.secureUrl());
            document.setStorageProvider("CLOUDINARY");
            document.setCloudinaryPublicId(originalUpload.publicId());
            document.setCloudinarySecureUrl(originalUpload.secureUrl());
            if (previewUpload != null) {
                document.setCloudinaryPreviewPublicId(previewUpload.publicId());
                document.setCloudinaryPreviewUrl(previewUpload.secureUrl());
            }
            document.setFileSizeBytes(file.getSize());
            document.setProcessingStatus("PROCESSING");
            document.setLanguage("vi");
            document.setUploadedAt(now);
            document.setUpdatedAt(now);

            CourseDocument savedDocument = courseDocumentRepository.save(document);
            processDocument(savedDocument, targetPath, fileType);
            return DocumentDto.DocumentResponse.fromEntity(savedDocument);
        } catch (IOException exception) {
            destroyCloudinaryAsset(uploadedPublicId);
            destroyCloudinaryAsset(uploadedPreviewPublicId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store or process file.");
        } catch (RuntimeException exception) {
            destroyCloudinaryAsset(uploadedPublicId);
            destroyCloudinaryAsset(uploadedPreviewPublicId);
            throw exception;
        } finally {
            if (targetPath != null) {
                deleteStoredFile(targetPath, uploadRoot);
            }
        }
    }

    public List<DocumentDto.DocumentResponse> getDocuments(UUID requesterId) {
        requireRequester(requesterId);
        List<CourseDocument> documents = isAdmin(requesterId)
                ? courseDocumentRepository.findAllByOrderByUploadedAtDesc()
                : courseDocumentRepository.findByUploadedByOrderByUploadedAtDesc(requesterId);

        return documents.stream()
                .map(DocumentDto.DocumentResponse::fromEntity)
                .toList();
    }

    public List<DocumentDto.DocumentResponse> getDocumentsByWorkspace(UUID workspaceId, UUID requesterId) {
        requireRequester(requesterId);
        List<CourseDocument> documents = isAdmin(requesterId)
                ? courseDocumentRepository.findByWorkspaceIdOrderByUploadedAtDesc(workspaceId)
                : courseDocumentRepository.findByWorkspaceIdAndUploadedByOrderByUploadedAtDesc(workspaceId, requesterId);

        return documents.stream()
                .map(DocumentDto.DocumentResponse::fromEntity)
                .toList();
    }

    public DocumentDto.DocumentResponse getDocument(UUID documentId, UUID requesterId) {
        CourseDocument document = getAccessibleDocument(documentId, requesterId);
        return DocumentDto.DocumentResponse.fromEntity(document);
    }

    public List<DocumentDto.PageResponse> getPages(UUID documentId, UUID requesterId) {
        getAccessibleDocument(documentId, requesterId);
        return documentPageRepository.findByDocumentIdOrderByPageNumberAsc(documentId).stream()
                .map(DocumentDto.PageResponse::fromEntity)
                .toList();
    }

    public List<DocumentDto.ChunkResponse> getChunks(UUID documentId, UUID requesterId) {
        getAccessibleDocument(documentId, requesterId);
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(DocumentDto.ChunkResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID requesterId) {
        CourseDocument document = getAccessibleDocument(documentId, requesterId);
        Path previewPath = previewRoot.resolve(documentId + ".pdf").normalize();
        Path originalPath = null;
        if (!isCloudStored(document)) {
            originalPath = Path.of(document.getFilePath()).toAbsolutePath().normalize();
        }

        jdbcTemplate.update("""
                DELETE FROM answer_citations
                WHERE document_id = ?
                   OR chunk_id IN (SELECT chunk_id FROM document_chunks WHERE document_id = ?)
                   OR retrieval_result_id IN (SELECT retrieval_result_id FROM retrieval_results WHERE document_id = ?)
                """, documentId, documentId, documentId);
        jdbcTemplate.update("DELETE FROM retrieval_results WHERE document_id = ?", documentId);
        jdbcTemplate.update("UPDATE saved_notes SET document_id = NULL WHERE document_id = ?", documentId);

        documentPageRepository.deleteByDocumentId(documentId);
        documentChunkRepository.deleteByDocumentId(documentId);
        courseDocumentRepository.delete(document);

        if (isCloudStored(document)) {
            destroyCloudinaryAsset(document.getCloudinaryPublicId());
            destroyCloudinaryAsset(document.getCloudinaryPreviewPublicId());
        } else if (originalPath != null) {
            deleteStoredFile(originalPath, uploadRoot);
        }
        deleteStoredFile(previewPath, previewRoot);
    }

    public StoredDocumentFile getStoredFile(UUID documentId, UUID requesterId) {
        CourseDocument document = getAccessibleDocument(documentId, requesterId);
        if (isCloudStored(document)) {
            return new StoredDocumentFile(
                    null,
                    document.getCloudinarySecureUrl(),
                    document.getOriginalFilename(),
                    document.getMimeType() == null || document.getMimeType().isBlank()
                            ? "application/octet-stream"
                            : document.getMimeType()
            );
        }

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
                null,
                document.getOriginalFilename(),
                mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType
        );
    }

    public StoredDocumentFile getPreviewFile(UUID documentId, UUID requesterId) {
        CourseDocument document = getAccessibleDocument(documentId, requesterId);
        String fileType = document.getFileType() == null ? "" : document.getFileType().toUpperCase(Locale.ROOT);

        if (isCloudStored(document)) {
            String previewUrl = "PDF".equals(fileType)
                    ? document.getCloudinarySecureUrl()
                    : document.getCloudinaryPreviewUrl();
            if (previewUrl == null || previewUrl.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_IMPLEMENTED,
                        "Document preview was not generated. Install LibreOffice before uploading DOCX/PPTX files."
                );
            }
            return new StoredDocumentFile(null, previewUrl, stripExtension(document.getOriginalFilename()) + ".pdf", "application/pdf");
        }

        Path originalPath = resolveStoredPath(document);
        if ("PDF".equals(fileType)) {
            return new StoredDocumentFile(originalPath, null, document.getOriginalFilename(), "application/pdf");
        }

        if (!List.of("DOCX", "PPTX").contains(fileType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Inline preview is available for PDF, DOCX, and PPTX.");
        }

        try {
            Files.createDirectories(previewRoot);
            Path previewPath = previewRoot.resolve(documentId + ".pdf").normalize();
            if (!previewPath.startsWith(previewRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preview path.");
            }

            if (!Files.exists(previewPath)) {
                convertOfficeDocumentToPdf(originalPath, previewRoot, previewPath);
            }

            if (!Files.exists(previewPath)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Preview PDF was not created.");
            }

            return new StoredDocumentFile(previewPath, null, stripExtension(document.getOriginalFilename()) + ".pdf", "application/pdf");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create preview file.");
        }
    }

    private Path resolveStoredPath(CourseDocument document) {
        Path filePath = Path.of(document.getFilePath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(uploadRoot) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored file not found.");
        }
        return filePath;
    }

    private void deleteStoredFile(Path filePath, Path allowedRoot) {
        try {
            Path normalizedPath = filePath.toAbsolutePath().normalize();
            if (normalizedPath.startsWith(allowedRoot) && Files.isRegularFile(normalizedPath)) {
                Files.deleteIfExists(normalizedPath);
            }
        } catch (IOException ignored) {
            // Database deletion should not be rolled back because a local preview/original file is locked.
        }
    }

    private void convertOfficeDocumentToPdf(Path originalPath, Path outputDir, Path targetPdf) throws IOException {
        Path soffice = findLibreOffice();
        if (soffice == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "DOCX/PPTX inline preview needs LibreOffice installed on the backend machine."
            );
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                soffice.toString(),
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                outputDir.toString(),
                originalPath.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Preview conversion timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Preview conversion interrupted.");
        }

        if (process.exitValue() != 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Preview conversion failed.");
        }

        Path generatedPdf = outputDir.resolve(stripExtension(originalPath.getFileName().toString()) + ".pdf").normalize();
        if (Files.exists(generatedPdf) && !generatedPdf.equals(targetPdf)) {
            Files.move(generatedPdf, targetPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path findLibreOffice() {
        List<Path> absoluteCandidates = List.of(
                Path.of("C:/Program Files/LibreOffice/program/soffice.exe"),
                Path.of("C:/Program Files (x86)/LibreOffice/program/soffice.exe")
        );

        for (Path candidate : absoluteCandidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        for (String command : List.of("soffice", "libreoffice")) {
            if (isCommandAvailable(command)) {
                return Path.of(command);
            }
        }

        return null;
    }

    private boolean isCommandAvailable(String command) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> lookupCommand = osName.contains("win")
                ? List.of("where", command)
                : List.of("which", command);

        try {
            Process process = new ProcessBuilder(lookupCommand).redirectErrorStream(true).start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
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
        if (request.uploadedBy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uploadedBy is required.");
        }
        if (!userRepository.existsById(request.uploadedBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uploadedBy user not found.");
        }
        if (!courseRepository.existsById(request.courseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course not found.");
        }

        CourseWorkspace workspace = courseWorkspaceRepository.findById(request.workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace not found."));
        if (!request.courseId.equals(workspace.getCourseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace does not belong to the selected course.");
        }

        if (request.chapterId != null) {
            Chapter chapter = chapterRepository.findById(request.chapterId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chapter not found."));
            if (!request.courseId.equals(chapter.getCourseId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chapter does not belong to the selected course.");
            }
        }
    }

    private CourseDocument getAccessibleDocument(UUID documentId, UUID requesterId) {
        requireRequester(requesterId);
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));

        if (isAdmin(requesterId) || requesterId.equals(document.getUploadedBy())) {
            return document;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access documents uploaded by your account.");
    }

    private void requireRequester(UUID requesterId) {
        if (requesterId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "requesterId is required.");
        }
        if (!userRepository.existsById(requesterId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester user not found.");
        }
    }

    private boolean isAdmin(UUID userId) {
        return userRoleRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(UserRole::getRoleName)
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role));
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ExtractedPage(int pageNumber, String text) {
    }

    private record CloudinaryUpload(String publicId, String secureUrl) {
    }

    public record StoredDocumentFile(Path path, String url, String filename, String mimeType) {
        public boolean isRemote() {
            return url != null && !url.isBlank();
        }
    }
}
