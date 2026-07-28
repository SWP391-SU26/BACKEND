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
import com.courseqa.repository.DocumentChapterRangeRepository;
import com.courseqa.repository.DocumentChapterSuggestionRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.DocumentPageRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int CHUNK_SIZE = 260;
    private static final int CHUNK_OVERLAP = 50;
    private static final String CHUNK_STRATEGY = "paragraph_260_50";
    private static final long PERSONAL_FILE_LIMIT = 20L * 1024 * 1024;
    private static final long PERSONAL_STORAGE_LIMIT = 200L * 1024 * 1024;
    private static final long PERSONAL_DOCUMENT_LIMIT = 20;

    private final CourseDocumentRepository courseDocumentRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SemesterWorkspaceRepository semesterWorkspaceRepository;
    private final DocumentChapterRangeRepository documentChapterRangeRepository;
    private final DocumentChapterSuggestionRepository documentChapterSuggestionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;
    private final Path previewRoot;
    private final Cloudinary cloudinary;
    private final PersonalWorkspaceService personalWorkspaceService;

    public DocumentService(
            CourseDocumentRepository courseDocumentRepository,
            CourseRepository courseRepository,
            ChapterRepository chapterRepository,
            CourseWorkspaceRepository courseWorkspaceRepository,
            DocumentPageRepository documentPageRepository,
            DocumentChunkRepository documentChunkRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SemesterWorkspaceRepository semesterWorkspaceRepository,
            DocumentChapterRangeRepository documentChapterRangeRepository,
            DocumentChapterSuggestionRepository documentChapterSuggestionRepository,
            JdbcTemplate jdbcTemplate,
            PersonalWorkspaceService personalWorkspaceService,
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
        this.semesterWorkspaceRepository = semesterWorkspaceRepository;
        this.documentChapterRangeRepository = documentChapterRangeRepository;
        this.documentChapterSuggestionRepository = documentChapterSuggestionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.personalWorkspaceService = personalWorkspaceService;
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
                    targetPath, "courseqa/documents/" + UUID.randomUUID() + "-" + originalFilename);
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
            document.setDocumentScope(request.courseId == null ? "PERSONAL" : "COURSE");
            document.setReviewStatus(request.courseId == null ? "NOT_SUBMITTED" : "APPROVED");
            document.setLanguage("und");
            document.setUploadedAt(now);
            document.setUpdatedAt(now);

            CourseDocument savedDocument = courseDocumentRepository.save(document);
            processDocument(savedDocument, targetPath, fileType);
            return toResponse(savedDocument, request.uploadedBy);
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

    @Transactional
    public DocumentDto.DocumentResponse uploadPersonalDocument(MultipartFile file, UUID userId) {
        requireRequester(userId);
        validatePersonalQuota(file, userId);
        CourseWorkspace workspace = personalWorkspaceService.getOrCreate(userId);
        DocumentDto.UploadDocumentRequest request = new DocumentDto.UploadDocumentRequest();
        request.workspaceId = workspace.getWorkspaceId();
        request.uploadedBy = userId;
        return uploadDocument(file, request);
    }

    public List<DocumentDto.DocumentResponse> getMyDocuments(UUID userId) {
        requireRequester(userId);
        return toResponses(courseDocumentRepository.findByUploadedByOrderByUploadedAtDesc(userId), userId);
    }

    public List<DocumentDto.DocumentResponse> getReviewQueue(UUID adminId) {
        requireAdmin(adminId);
        return toResponses(courseDocumentRepository.findByReviewStatusOrderBySubmittedAtAsc("PENDING"), adminId);
    }

    @Transactional
    public DocumentDto.DocumentResponse submitForReview(UUID documentId, UUID courseId, UUID userId) {
        CourseDocument document = requireOwnedDocument(documentId, userId);
        if (!"PROCESSED".equals(document.getProcessingStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only processed documents can be submitted.");
        }
        if (!"PERSONAL".equals(document.getDocumentScope())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document is already shared with a course.");
        }
        if ("PENDING".equals(document.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document is already waiting for review.");
        }
        requireShareableCourse(courseId);
        LocalDateTime now = LocalDateTime.now();
        document.setTargetCourseId(courseId);
        document.setReviewStatus("PENDING");
        document.setSubmittedAt(now);
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        document.setRejectionReason(null);
        document.setUpdatedAt(now);
        return toResponse(courseDocumentRepository.save(document), userId);
    }

    @Transactional
    public DocumentDto.DocumentResponse cancelSubmission(UUID documentId, UUID userId) {
        CourseDocument document = requireOwnedDocument(documentId, userId);
        if (!"PERSONAL".equals(document.getDocumentScope()) || !"PENDING".equals(document.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending personal submissions can be cancelled.");
        }
        document.setReviewStatus("NOT_SUBMITTED");
        document.setTargetCourseId(null);
        document.setSubmittedAt(null);
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        document.setRejectionReason(null);
        document.setUpdatedAt(LocalDateTime.now());
        return toResponse(courseDocumentRepository.save(document), userId);
    }

    @Transactional
    public DocumentDto.DocumentResponse reviewDocument(UUID documentId, DocumentDto.ReviewRequest request, UUID adminId) {
        requireAdmin(adminId);
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        if (!"PERSONAL".equals(document.getDocumentScope()) || !"PENDING".equals(document.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document is not waiting for review.");
        }
        String status = request == null || request.status == null
                ? "" : request.status.trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        if ("REJECTED".equals(status)) {
            if (request.rejectionReason == null || request.rejectionReason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason is required.");
            }
            document.setReviewStatus("REJECTED");
            document.setReviewedBy(adminId);
            document.setReviewedAt(now);
            document.setRejectionReason(request.rejectionReason.trim());
            document.setUpdatedAt(now);
            return toResponse(courseDocumentRepository.save(document), adminId);
        }
        if (!"APPROVED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review status must be APPROVED or REJECTED.");
        }

        UUID courseId = request.courseId != null ? request.courseId : document.getTargetCourseId();
        requireShareableCourse(courseId);
        CourseWorkspace courseWorkspace = courseWorkspaceRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .filter(workspace -> Boolean.TRUE.equals(workspace.getIsActive()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The selected course has no active workspace."));

        document.setWorkspaceId(courseWorkspace.getWorkspaceId());
        document.setCourseId(courseId);
        document.setChapterId(null);
        document.setDocumentScope("COURSE");
        document.setReviewStatus("APPROVED");
        document.setTargetCourseId(courseId);
        document.setReviewedBy(adminId);
        document.setReviewedAt(now);
        document.setRejectionReason(null);
        document.setUpdatedAt(now);
        courseDocumentRepository.save(document);

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        chunks.forEach(chunk -> {
            chunk.setWorkspaceId(courseWorkspace.getWorkspaceId());
            chunk.setCourseId(courseId);
            chunk.setChapterId(null);
        });
        documentChunkRepository.saveAll(chunks);
        return toResponse(document, adminId);
    }

    public List<DocumentDto.DocumentResponse> getDocuments(UUID requesterId) {
        requireRequester(requesterId);
        List<CourseDocument> documents = isAdmin(requesterId)
                ? courseDocumentRepository.findAllByOrderByUploadedAtDesc()
                : courseDocumentRepository.findAllByOrderByUploadedAtDesc().stream()
                    .filter(this::isApprovedCourseDocument)
                    .filter(document -> isCourseAvailable(document.getCourseId()))
                    .toList();

        return toResponses(documents, requesterId);
    }

    public List<DocumentDto.DocumentResponse> getDocumentsByWorkspace(UUID workspaceId, UUID requesterId) {
        requireRequester(requesterId);
        CourseWorkspace workspace = courseWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        if (!isAdmin(requesterId)) {
            if (workspace.getCourseId() == null && !requesterId.equals(workspace.getOwnerUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This personal workspace belongs to another user.");
            }
            if (workspace.getCourseId() != null && !isCourseAvailable(workspace.getCourseId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not currently available.");
            }
        }
       /*  List<CourseDocument> documents = isAdmin(requesterId)
                ? courseDocumentRepository.findByWorkspaceIdOrderByUploadedAtDesc(workspaceId)
                : courseDocumentRepository.findByWorkspaceIdAndUploadedByOrderByUploadedAtDesc(workspaceId, requesterId);
       */
        List<CourseDocument> documents =
            courseDocumentRepository.findByWorkspaceIdOrderByUploadedAtDesc(workspaceId);

        List<CourseDocument> accessibleDocuments = documents.stream()
                .filter(document -> isAdmin(requesterId) || requesterId.equals(document.getUploadedBy()) || isApprovedCourseDocument(document))
                .toList();
        return toResponses(accessibleDocuments, requesterId);
    }

    public DocumentDto.DocumentResponse getDocument(UUID documentId, UUID requesterId) {
        CourseDocument document = getAccessibleDocument(documentId, requesterId);
        return toResponse(document, requesterId);
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
        boolean admin = isAdmin(requesterId);
        boolean deletablePersonal = requesterId.equals(document.getUploadedBy())
                && "PERSONAL".equals(document.getDocumentScope())
                && List.of("NOT_SUBMITTED", "REJECTED").contains(document.getReviewStatus());
        if (!admin && !deletablePersonal) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only unsubmitted or rejected personal documents can be deleted by their owner.");
        }
        UUID courseId = document.getCourseId();
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
        jdbcTemplate.update("DELETE FROM chat_session_documents WHERE document_id = ?", documentId);
        jdbcTemplate.update("UPDATE saved_notes SET document_id = NULL WHERE document_id = ?", documentId);

        documentChapterRangeRepository.findByDocumentIdOrderByPageStartAsc(documentId).stream()
                .map(com.courseqa.model.entity.DocumentChapterRange::getChapterId).distinct()
                .forEach(chapterId -> chapterRepository.findById(chapterId).ifPresent(chapter -> {
                    chapter.setIsActive(false); chapter.setUpdatedAt(LocalDateTime.now()); chapterRepository.save(chapter);
                }));
        documentChapterRangeRepository.deleteByDocumentId(documentId);
        documentChapterSuggestionRepository.deleteByDocumentId(documentId);
        documentPageRepository.deleteByDocumentId(documentId);
        documentChunkRepository.deleteByDocumentId(documentId);
        courseDocumentRepository.delete(document);
        courseDocumentRepository.flush();

        if (courseId != null && !courseDocumentRepository.existsByCourseIdAndProcessingStatus(courseId, "PROCESSED")) {
            courseRepository.findById(courseId).ifPresent(course -> {
                course.setIsActive(false);
                course.setUpdatedAt(LocalDateTime.now());
                courseRepository.save(course);
            });
        }

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
            document.setLanguage(detectDocumentLanguage(extractedPages));
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

    private String detectDocumentLanguage(List<ExtractedPage> pages) {
        String sample = pages.stream()
                .map(ExtractedPage::text)
                .filter(text -> text != null && !text.isBlank())
                .limit(20)
                .reduce("", (left, right) -> left + " " + right);
        if (sample.isBlank()) {
            return "und";
        }

        long japanese = sample.codePoints().filter(codePoint ->
                (codePoint >= 0x3040 && codePoint <= 0x30FF)
                        || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)).count();
        long vietnamese = sample.codePoints().filter(codePoint ->
                "ăâđêôơưĂÂĐÊÔƠƯáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ"
                        .indexOf(codePoint) >= 0).count();

        if (japanese >= 12 && japanese >= vietnamese * 2) {
            return vietnamese >= 6 ? "ja-vi" : "ja";
        }
        if (vietnamese >= 4) {
            return japanese >= 12 ? "vi-ja" : "vi";
        }
        return "und";
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
            List<String> paragraphs = splitParagraphs(page.getRawText());
            if (paragraphs.isEmpty()) {
                continue;
            }

            List<String> currentParagraphs = new ArrayList<>();
            int currentWordCount = 0;
            for (String paragraph : paragraphs) {
                List<String> paragraphParts = splitLongParagraph(paragraph);
                for (String part : paragraphParts) {
                    int partWordCount = countWords(part);
                    if (!currentParagraphs.isEmpty() && currentWordCount + partWordCount > CHUNK_SIZE) {
                        chunks.add(newChunk(document, page, chunkIndex++, currentParagraphs, now));
                        currentParagraphs = overlapParagraphs(currentParagraphs);
                        currentWordCount = currentParagraphs.stream().mapToInt(this::countWords).sum();
                    }
                    currentParagraphs.add(part);
                    currentWordCount += partWordCount;
                }
            }

            if (!currentParagraphs.isEmpty()) {
                chunks.add(newChunk(document, page, chunkIndex++, currentParagraphs, now));
            }
        }

        return documentChunkRepository.saveAll(chunks);
    }

    private DocumentChunk newChunk(
            CourseDocument document,
            DocumentPage page,
            int chunkIndex,
            List<String> paragraphs,
            LocalDateTime createdAt) {
        String content = String.join("\n\n", paragraphs).trim();
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(document.getDocumentId());
        chunk.setWorkspaceId(document.getWorkspaceId());
        chunk.setCourseId(document.getCourseId());
        chunk.setChapterId(document.getChapterId());
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkStrategy(CHUNK_STRATEGY);
        chunk.setChunkSize(CHUNK_SIZE);
        chunk.setChunkOverlap(CHUNK_OVERLAP);
        chunk.setContent(content);
        chunk.setContentCompressed(EmbeddingService.compressUnicodeText(content));
        chunk.setPageStart(page.getPageNumber());
        chunk.setPageEnd(page.getPageNumber());
        chunk.setTokenCount(countWords(content));
        chunk.setWordCount(countWords(content));
        chunk.setCharCount(content.length());
        chunk.setCreatedAt(createdAt);
        return chunk;
    }

    private List<String> splitParagraphs(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String normalized = rawText
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
        String[] blocks = normalized.split("\\n\\s*\\n|(?m)^\\s*$");
        List<String> paragraphs = new ArrayList<>();
        for (String block : blocks) {
            String paragraph = block.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                    .replaceAll("\\n+", " ")
                    .trim();
            if (!paragraph.isBlank()) {
                paragraphs.add(paragraph);
            }
        }
        if (paragraphs.isEmpty()) {
            String fallback = cleanText(rawText);
            if (!fallback.isBlank()) {
                paragraphs.add(fallback);
            }
        }
        return paragraphs;
    }

    private List<String> splitLongParagraph(String paragraph) {
        if (countWords(paragraph) <= CHUNK_SIZE) {
            return List.of(paragraph);
        }
        String[] words = paragraph.split("\\s+");
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + CHUNK_SIZE, words.length);
            parts.add(String.join(" ", java.util.Arrays.copyOfRange(words, start, end)).trim());
            if (end == words.length) {
                break;
            }
            start = end;
        }
        return parts;
    }

    private List<String> overlapParagraphs(List<String> paragraphs) {
        List<String> overlap = new ArrayList<>();
        int words = 0;
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            String paragraph = paragraphs.get(i);
            int paragraphWords = countWords(paragraph);
            if (!overlap.isEmpty() && words + paragraphWords > CHUNK_OVERLAP) {
                break;
            }
            overlap.add(0, paragraph);
            words += paragraphWords;
        }
        return overlap;
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
        if (request == null || request.workspaceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceId is required.");
        }
        if (request.uploadedBy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uploadedBy is required.");
        }
        if (!userRepository.existsById(request.uploadedBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uploadedBy user not found.");
        }
        CourseWorkspace workspace = courseWorkspaceRepository.findById(request.workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace not found."));
        if (request.courseId == null) {
            if (workspace.getCourseId() != null || !request.uploadedBy.equals(workspace.getOwnerUserId())
                    || !"PRIVATE".equals(workspace.getVisibility())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid personal workspace.");
            }
        } else {
            com.courseqa.model.entity.Course uploadCourse = courseRepository.findById(request.courseId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course not found."));
            if ("ARCHIVED".equals(uploadCourse.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived courses are read-only.");
            }
            if (!request.courseId.equals(workspace.getCourseId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace does not belong to the selected course.");
            }
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

        if (isApprovedCourseDocument(document) && isCourseAvailable(document.getCourseId())) return document;

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this document.");
    }

    private boolean isCourseAvailable(UUID courseId) {
        if (courseId == null) return false;
        return courseRepository.findById(courseId)
                .filter(course -> Boolean.TRUE.equals(course.getIsActive()))
                .filter(course -> !"ARCHIVED".equals(course.getStatus()))
                .filter(course -> semesterWorkspaceRepository.findById(course.getSemesterWorkspaceId())
                        .map(semester -> "ACTIVE".equals(semester.getStatus()))
                        .orElse(false))
                .filter(course -> courseDocumentRepository.existsByCourseIdAndProcessingStatus(courseId, "PROCESSED"))
                .isPresent();
    }

    private void validatePersonalQuota(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required.");
        }
        String fileType = resolveFileType(sanitizeFilename(file.getOriginalFilename()));
        if (!List.of("PDF", "DOCX", "PPTX").contains(fileType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Personal uploads support PDF, DOCX, and PPTX files only.");
        }
        if (file.getSize() > PERSONAL_FILE_LIMIT) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each file is limited to 20 MB.");
        }
        if (courseDocumentRepository.countByUploadedBy(userId) >= PERSONAL_DOCUMENT_LIMIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can store at most 20 documents.");
        }
        long usedBytes = java.util.Optional.ofNullable(courseDocumentRepository.sumFileSizeByUploadedBy(userId)).orElse(0L);
        if (usedBytes + file.getSize() > PERSONAL_STORAGE_LIMIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your personal document storage is limited to 200 MB.");
        }
    }

    private CourseDocument requireOwnedDocument(UUID documentId, UUID userId) {
        requireRequester(userId);
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        if (!userId.equals(document.getUploadedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This document belongs to another user.");
        }
        return document;
    }

    private void requireShareableCourse(UUID courseId) {
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId is required.");
        }
        com.courseqa.model.entity.Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        boolean available = Boolean.TRUE.equals(course.getIsActive())
                && !"ARCHIVED".equals(course.getStatus())
                && semesterWorkspaceRepository.findById(course.getSemesterWorkspaceId())
                    .map(semester -> "ACTIVE".equals(semester.getStatus()))
                    .orElse(false);
        if (!available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Documents can only be submitted to an active course in an active semester.");
        }
    }

    private boolean isApprovedCourseDocument(CourseDocument document) {
        return document.getCourseId() != null
                && (document.getDocumentScope() == null || "COURSE".equals(document.getDocumentScope()))
                && (document.getReviewStatus() == null || "APPROVED".equals(document.getReviewStatus()));
    }

    private DocumentDto.DocumentResponse toResponse(CourseDocument document, UUID requesterId) {
        String uploaderName = document.getUploadedBy() == null
                ? null
                : userRepository.findById(document.getUploadedBy())
                    .map(user -> user.getFullName())
                    .orElse(null);
        return toResponse(document, requesterId, uploaderName);
    }

    private List<DocumentDto.DocumentResponse> toResponses(
            List<CourseDocument> documents, UUID requesterId) {
        Map<UUID, String> uploaderNames = new HashMap<>();
        List<UUID> uploaderIds = documents.stream()
                .map(CourseDocument::getUploadedBy)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        userRepository.findAllById(uploaderIds).forEach(user ->
                uploaderNames.put(user.getUserId(), user.getFullName()));
        return documents.stream()
                .map(document -> toResponse(document, requesterId, uploaderNames.get(document.getUploadedBy())))
                .toList();
    }

    private DocumentDto.DocumentResponse toResponse(
            CourseDocument document, UUID requesterId, String uploaderName) {
        DocumentDto.DocumentResponse response = DocumentDto.DocumentResponse.fromEntity(document);
        response.uploaderName = uploaderName;
        response.canDelete = isAdmin(requesterId) || (requesterId != null && requesterId.equals(document.getUploadedBy())
                && "PERSONAL".equals(document.getDocumentScope())
                && List.of("NOT_SUBMITTED", "REJECTED").contains(document.getReviewStatus()));
        return response;
    }

    private void requireAdmin(UUID userId) {
        requireRequester(userId);
        if (!isAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator permission is required.");
        }
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
