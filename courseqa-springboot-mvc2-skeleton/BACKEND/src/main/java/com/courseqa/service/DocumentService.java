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
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private static final String CHUNK_STRATEGY_PREFIX = "structured_v4";
    private static final Locale SENTENCE_LOCALE = Locale.forLanguageTag("vi");
    private static final int BOILERPLATE_MAX_CHARS = 80;
    private static final int HEADING_MAX_CHARS = 120;
    /** Weak signals need a tighter limit: citations and questions are long. */
    private static final int WEAK_HEADING_MAX_CHARS = 70;
    /** Smallest side, in pixels, an embedded image must have to be worth OCR-ing. */
    private static final int MIN_OCR_IMAGE_SIDE = 200;
    /** Beyond this width:height ratio an image is a banner or divider, not content. */
    private static final double MAX_OCR_ASPECT_RATIO = 8.0;
    /** OCR output shorter than this is noise rather than recovered content. */
    private static final int MIN_OCR_TEXT_CHARS = 12;
    /** Marks where an embedded image sat, so its OCR text lands in the right place. */
    private static final String IMAGE_PLACEHOLDER_PREFIX = String.valueOf((char) 1) + "IMG:";
    private static final String IMAGE_PLACEHOLDER_SUFFIX = String.valueOf((char) 1);
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
    private final SubscriptionService subscriptionService;
    private final String ocrTessdataPath;
    private final String ocrLanguage;
    private final double ocrMinConfidence;
    private final ChunkTokenCounter tokenCounter;
    private final SemanticBoundaryDetector semanticBoundaries;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int slideChunkSize;
    private final int slideChunkOverlap;

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
            SubscriptionService subscriptionService,
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${cloudinary.cloud-name:}") String cloudinaryCloudName,
            @Value("${cloudinary.api-key:}") String cloudinaryApiKey,
            @Value("${cloudinary.api-secret:}") String cloudinaryApiSecret,
            @Value("${app.ocr.tessdata-path:}") String ocrTessdataPath,
            @Value("${app.ocr.language:vie+eng}") String ocrLanguage,
            @Value("${app.ocr.min-confidence:60}") double ocrMinConfidence,
            ChunkTokenCounter tokenCounter,
            SemanticBoundaryDetector semanticBoundaries,
            @Value("${app.chunking.size:450}") int chunkSize,
            @Value("${app.chunking.overlap:55}") int chunkOverlap,
            @Value("${app.chunking.slide-size:250}") int slideChunkSize,
            @Value("${app.chunking.slide-overlap:40}") int slideChunkOverlap
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
        this.subscriptionService = subscriptionService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.previewRoot = this.uploadRoot.resolve("previews").normalize();
        this.cloudinary = createCloudinary(cloudinaryCloudName, cloudinaryApiKey, cloudinaryApiSecret);
        this.ocrTessdataPath = ocrTessdataPath;
        this.ocrLanguage = ocrLanguage;
        this.ocrMinConfidence = ocrMinConfidence;
        this.tokenCounter = tokenCounter;
        this.semanticBoundaries = semanticBoundaries;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.slideChunkSize = slideChunkSize;
        this.slideChunkOverlap = slideChunkOverlap;
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

    /**
     * Fast, synchronous part of an upload: validate, save the file locally, check
     * for a duplicate (same owner + content hash), and create the document row.
     * Cloudinary upload, preview, extraction, chunking and embedding all happen
     * afterwards in {@link #runUploadPipeline(UUID)}, run on a background executor
     * so the request returns immediately and processing survives navigation/logout.
     */
    @Transactional
    public DocumentDto.DocumentResponse uploadDocument(MultipartFile file, DocumentDto.UploadDocumentRequest request) {
        validateUpload(file, request);

        try {
            Files.createDirectories(uploadRoot);
            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            String fileType = resolveFileType(originalFilename);
            Path targetPath = uploadRoot.resolve(UUID.randomUUID() + "-" + originalFilename).normalize();
            file.transferTo(targetPath);
            verifyFileSignature(targetPath, fileType, uploadRoot);

            String contentHash = computeContentHash(targetPath);
            Optional<CourseDocument> duplicate =
                    courseDocumentRepository.findFirstByUploadedByAndContentHash(request.uploadedBy, contentHash);
            if (duplicate.isPresent()) {
                deleteStoredFile(targetPath, uploadRoot);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You already uploaded this file as \"" + duplicate.get().getOriginalFilename() + "\".");
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
            document.setFilePath(targetPath.toString());
            document.setStorageProvider("LOCAL");
            document.setFileSizeBytes(file.getSize());
            document.setProcessingStatus("PROCESSING");
            document.setIndexingStatus("PENDING");
            document.setDocumentScope(request.courseId == null ? "PERSONAL" : "COURSE");
            document.setReviewStatus(request.courseId == null ? "NOT_SUBMITTED" : "APPROVED");
            document.setLanguage("und");
            document.setContentHash(contentHash);
            document.setUploadedAt(now);
            document.setUpdatedAt(now);

            CourseDocument savedDocument = courseDocumentRepository.save(document);
            return toResponse(savedDocument, request.uploadedBy);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store file.");
        }
    }

    /**
     * Turns a fully staged resumable upload into a document row, applying the same
     * validation, duplicate check and status handling as a direct upload so both
     * paths converge on one pipeline.
     */
    @Transactional
    public DocumentDto.DocumentResponse registerStagedUpload(
            Path stagedFile,
            String rawFilename,
            String mimeType,
            UUID workspaceId,
            UUID courseId,
            UUID chapterId,
            UUID uploadedBy) {
        DocumentDto.UploadDocumentRequest request = new DocumentDto.UploadDocumentRequest();
        request.workspaceId = workspaceId != null
                ? workspaceId
                : personalWorkspaceService.getOrCreate(uploadedBy).getWorkspaceId();
        request.courseId = courseId;
        request.chapterId = chapterId;
        request.uploadedBy = uploadedBy;
        validateUploadTarget(request);
        if (courseId == null) {
            try {
                validatePersonalQuota(rawFilename, Files.size(stagedFile), uploadedBy);
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not read the staged file.");
            }
        }

        try {
            Files.createDirectories(uploadRoot);
            String originalFilename = sanitizeFilename(rawFilename);
            String fileType = resolveFileType(originalFilename);
            Path targetPath = uploadRoot.resolve(UUID.randomUUID() + "-" + originalFilename).normalize();
            Files.move(stagedFile, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            verifyFileSignature(targetPath, fileType, uploadRoot);

            String contentHash = computeContentHash(targetPath);
            Optional<CourseDocument> duplicate =
                    courseDocumentRepository.findFirstByUploadedByAndContentHash(uploadedBy, contentHash);
            if (duplicate.isPresent()) {
                deleteStoredFile(targetPath, uploadRoot);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You already uploaded this file as \"" + duplicate.get().getOriginalFilename() + "\".");
            }

            LocalDateTime now = LocalDateTime.now();
            CourseDocument document = new CourseDocument();
            document.setWorkspaceId(request.workspaceId);
            document.setCourseId(courseId);
            document.setChapterId(chapterId);
            document.setUploadedBy(uploadedBy);
            document.setDocumentTitle(stripExtension(originalFilename));
            document.setOriginalFilename(originalFilename);
            document.setFileType(fileType);
            document.setMimeType(mimeType);
            document.setFilePath(targetPath.toString());
            document.setStorageProvider("LOCAL");
            document.setFileSizeBytes(Files.size(targetPath));
            document.setProcessingStatus("PROCESSING");
            document.setIndexingStatus("PENDING");
            document.setDocumentScope(courseId == null ? "PERSONAL" : "COURSE");
            document.setReviewStatus(courseId == null ? "NOT_SUBMITTED" : "APPROVED");
            document.setLanguage("und");
            document.setContentHash(contentHash);
            document.setUploadedAt(now);
            document.setUpdatedAt(now);

            return toResponse(courseDocumentRepository.save(document), uploadedBy);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the staged file.");
        }
    }

    /**
     * Hashes the upload in fixed-size blocks rather than loading it whole. At the
     * 20 MB per-file ceiling, reading everything into a byte[] meant every concurrent
     * upload pinned its entire file in heap just to compute a digest.
     */
    private String computeContentHash(Path filePath) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (InputStream input = new java.io.BufferedInputStream(Files.newInputStream(filePath), 8192)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder value = new StringBuilder();
            for (byte item : digest.digest()) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    /**
     * Background continuation of {@link #uploadDocument}: Cloudinary upload, preview,
     * text extraction, OCR fallback, chunking and embedding. Runs on
     * {@code documentIndexingTaskExecutor}, submitted by
     * {@link DocumentProcessingService}, so it keeps running after the HTTP request
     * that triggered the upload has ended.
     */
    /*
     * Deliberately not @Transactional. This method uploads to Cloudinary, may shell
     * out to LibreOffice and can run OCR for minutes; holding a database
     * transaction open across all of that would pin a pooled connection for the
     * whole time. It also used to swallow its own failure state: the FAILED status
     * was written inside the transaction and then rolled back by the rethrow, so
     * the document stayed PROCESSING while the job reported FAILED. The individual
     * repository writes below are each transactional on their own.
     */
    public void runUploadPipeline(UUID documentId) {
        runUploadPipeline(documentId, step -> { });
    }

    /** {@code phaseListener} receives each phase name as the pipeline reaches it. */
    public void runUploadPipeline(UUID documentId, java.util.function.Consumer<String> phaseListener) {
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        Path targetPath = Path.of(document.getFilePath());
        String originalFilename = document.getOriginalFilename();
        String fileType = document.getFileType();

        String uploadedPublicId = null;
        String uploadedPreviewPublicId = null;
        try {
            phaseListener.accept("UPLOADING");
            ensureCloudinaryConfigured();
            CloudinaryUpload originalUpload = uploadToCloudinary(
                    targetPath, "courseqa/documents/" + UUID.randomUUID() + "-" + originalFilename);
            uploadedPublicId = originalUpload.publicId();

            CloudinaryUpload previewUpload = createAndUploadPreview(targetPath, originalFilename, fileType);
            if (previewUpload != null) {
                uploadedPreviewPublicId = previewUpload.publicId();
            }

            document.setFilePath(originalUpload.secureUrl());
            document.setStorageProvider("CLOUDINARY");
            document.setCloudinaryPublicId(originalUpload.publicId());
            document.setCloudinarySecureUrl(originalUpload.secureUrl());
            if (previewUpload != null) {
                document.setCloudinaryPreviewPublicId(previewUpload.publicId());
                document.setCloudinaryPreviewUrl(previewUpload.secureUrl());
            }
            document.setUpdatedAt(LocalDateTime.now());
            courseDocumentRepository.save(document);

            processDocument(document, targetPath, fileType, phaseListener);
        } catch (IOException exception) {
            destroyCloudinaryAsset(uploadedPublicId);
            destroyCloudinaryAsset(uploadedPreviewPublicId);
            markDocumentFailed(document, "Could not store or process file: " + exception.getMessage());
            throw new IllegalStateException(exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            destroyCloudinaryAsset(uploadedPublicId);
            destroyCloudinaryAsset(uploadedPreviewPublicId);
            markDocumentFailed(document, exception.getMessage());
            throw exception;
        } finally {
            deleteStoredFile(targetPath, uploadRoot);
        }
    }

    /**
     * Records the failure so it survives the exception the caller is about to
     * rethrow. This works because {@link #runUploadPipeline} is not transactional:
     * the repository save commits on its own. An explicit {@code REQUIRES_NEW}
     * here would be misleading — it is a self-invocation, so the proxy is bypassed
     * and the annotation would never take effect.
     */
    private void markDocumentFailed(CourseDocument document, String message) {
        CourseDocument fresh = courseDocumentRepository.findById(document.getDocumentId()).orElse(document);
        fresh.setProcessingStatus("FAILED");
        fresh.setIndexingStatus("FAILED");
        fresh.setIndexError(message);
        fresh.setErrorMessage(message);
        fresh.setUpdatedAt(LocalDateTime.now());
        courseDocumentRepository.save(fresh);
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
        jdbcTemplate.update("UPDATE evaluation_questions SET expected_document_id = NULL WHERE expected_document_id = ?",
                documentId);
        jdbcTemplate.update("DELETE FROM evaluation_dataset_documents WHERE document_id = ?", documentId);

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

        if (courseId != null && !courseDocumentRepository
                .existsByCourseIdAndProcessingStatusAndIndexingStatus(courseId, "PROCESSED", "INDEXED")) {
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

    private void processDocument(CourseDocument document, Path filePath, String fileType,
            java.util.function.Consumer<String> phaseListener) {
        try {
            phaseListener.accept("EXTRACTING");
            List<ExtractedPage> extractedPages = extractPages(filePath, fileType);
            phaseListener.accept("OCR");
            extractedPages = applyOcrFallback(filePath, fileType, extractedPages);
            phaseListener.accept("CHUNKING");
            List<DocumentPage> pages = savePages(document.getDocumentId(), extractedPages);
            List<DocumentChunk> chunks = saveChunks(document, pages, 1, true);

            document.setTotalPages(pages.size());
            document.setLanguage(detectDocumentLanguage(extractedPages));
            document.setProcessingStatus(chunks.isEmpty() ? "NO_TEXT" : "PROCESSED");
            document.setIndexingStatus(chunks.isEmpty() ? "FAILED" : "PENDING");
            document.setIndexError(chunks.isEmpty() ? "No text chunks were extracted." : null);
            document.setErrorMessage(null);
            document.setUpdatedAt(LocalDateTime.now());
            courseDocumentRepository.save(document);
        } catch (Exception exception) {
            document.setProcessingStatus("FAILED");
            document.setIndexingStatus("FAILED");
            document.setIndexError(exception.getMessage());
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
            page.setHeadingPath(extractedPage.headingPath());
            page.setOcrApplied(extractedPage.ocrApplied());
            page.setOcrConfidence(extractedPage.ocrConfidence());
            pages.add(page);
        }

        return documentPageRepository.saveAll(pages);
    }

    /**
     * Builds chunks over the whole document rather than page-by-page, so a chunk
     * can span a page break (and record a real {@code pageStart..pageEnd} range)
     * instead of being cut short at every page boundary.
     *
     * <p>Size invariants: each chunk carries at most {@code plan.overlap()} tokens
     * of overlap from its predecessor plus at least one new segment, so a chunk
     * never exceeds {@code plan.size() + plan.overlap()} tokens and is never a
     * strict superset of the previous chunk.
     */
    private List<DocumentChunk> saveChunks(
            CourseDocument document, List<DocumentPage> pages, int chunkVersion, boolean active) {
        ChunkPlan plan = chunkPlanFor(document.getFileType());
        List<TextSegment> segments = buildSegments(pages);
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 1;
        LocalDateTime now = LocalDateTime.now();

        // A topic change is an extra reason to end a chunk; the size budget still
        // applies on top, so semantic mode can never produce an oversized chunk.
        boolean[] topicShift = semanticBoundaries.detect(
                segments.stream().map(TextSegment::text).toList());

        List<TextSegment> current = new ArrayList<>();
        boolean bufferHasNewContent = false;

        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            TextSegment segment = segments.get(segmentIndex);
            boolean startsNewTopic = segmentIndex < topicShift.length && topicShift[segmentIndex];
            boolean firstPartOfSegment = true;
            for (TextSegment part : splitOversizedSegment(segment, plan)) {
                // Measure the content that would actually be stored, heading prefix and
                // separators included. Adding up per-segment token counts cannot work:
                // BPE tokenisation is not additive across concatenation, so incremental
                // arithmetic silently drifts over the budget.
                boolean topicBreak = startsNewTopic && firstPartOfSegment;
                firstPartOfSegment = false;
                if (bufferHasNewContent && (topicBreak || tokensIfAdded(current, part) > plan.size())) {
                    chunks.add(newChunk(document, current, chunkIndex++, now, chunkVersion, active, plan));
                    current = overlapSegments(current, plan);
                    // Carried overlap must never be what pushes the next chunk over the
                    // budget; give up the oldest overlap until the incoming part fits.
                    while (!current.isEmpty() && tokensIfAdded(current, part) > plan.size()) {
                        current.remove(0);
                    }
                    bufferHasNewContent = false;
                }
                current.add(part);
                bufferHasNewContent = true;
            }
        }

        if (bufferHasNewContent) {
            chunks.add(newChunk(document, current, chunkIndex++, now, chunkVersion, active, plan));
        }

        return documentChunkRepository.saveAll(chunks);
    }

    private int tokensIfAdded(List<TextSegment> buffer, TextSegment candidate) {
        List<TextSegment> probe = new ArrayList<>(buffer.size() + 1);
        probe.addAll(buffer);
        probe.add(candidate);
        return countTokens(assembleContent(probe));
    }

    /** The exact text stored on a chunk: heading path, blank line, then the body. */
    private String assembleContent(List<TextSegment> segments) {
        String body = segments.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n\n"))
                .trim();
        String heading = headingOf(segments);
        return heading == null ? body : heading + "\n\n" + body;
    }

    private String headingOf(List<TextSegment> segments) {
        return segments.stream()
                .map(TextSegment::headingPath)
                .filter(heading -> heading != null && !heading.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Flattens pages into a document-wide stream of blocks, dropping repeated
     * running headers/footers and keeping table blocks intact.
     */
    private List<TextSegment> buildSegments(List<DocumentPage> pages) {
        List<TextSegment> segments = new ArrayList<>();
        BoilerplateSignature boilerplate = detectBoilerplate(pages);
        for (DocumentPage page : pages) {
            String text = removeBoilerplate(page.getRawText(), boilerplate);
            for (String block : splitBlocks(text)) {
                segments.add(new TextSegment(
                        block, page.getPageNumber(), page.getHeadingPath(), isTableBlock(block)));
            }
        }
        return segments;
    }

    /**
     * Running headers/footers (course name, page numbers) repeat on nearly every
     * page and otherwise leak into every chunk as retrieval noise. Digits are
     * masked so "Trang 1"/"Trang 2" collapse to one signature.
     *
     * <p>Top and bottom lines are tracked separately, and only genuinely short
     * lines at the very edge of a page qualify — otherwise real body text that
     * happens to follow a numbered pattern ("Nội dung của trang 3.") would be
     * mistaken for a footer and deleted.
     */
    private BoilerplateSignature detectBoilerplate(List<DocumentPage> pages) {
        if (pages.size() < 4) {
            return BoilerplateSignature.empty();
        }
        Map<String, Integer> topCounts = new HashMap<>();
        Map<String, Integer> bottomCounts = new HashMap<>();
        for (DocumentPage page : pages) {
            List<String> lines = contentLines(page.getRawText());
            int window = edgeWindow(lines.size());
            Set<String> top = new HashSet<>();
            Set<String> bottom = new HashSet<>();
            for (int i = 0; i < window && i < lines.size(); i++) {
                addBoilerplateCandidate(top, lines.get(i));
            }
            for (int i = Math.max(0, lines.size() - window); i < lines.size(); i++) {
                addBoilerplateCandidate(bottom, lines.get(i));
            }
            top.forEach(key -> topCounts.merge(key, 1, Integer::sum));
            bottom.forEach(key -> bottomCounts.merge(key, 1, Integer::sum));
        }
        int threshold = (int) Math.ceil(pages.size() * 0.6);
        return new BoilerplateSignature(
                overThreshold(topCounts, threshold), overThreshold(bottomCounts, threshold));
    }

    private Set<String> overThreshold(Map<String, Integer> counts, int threshold) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void addBoilerplateCandidate(Set<String> target, String line) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && trimmed.length() <= BOILERPLATE_MAX_CHARS) {
            target.add(boilerplateKey(trimmed));
        }
    }

    private List<String> contentLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\n")).filter(line -> !line.isBlank()).toList();
    }

    /** How many lines at each edge of a page may count as header/footer. */
    private int edgeWindow(int contentLineCount) {
        return Math.min(2, Math.max(1, contentLineCount / 3));
    }

    private String boilerplateKey(String line) {
        return line.trim().replaceAll("\\d+", "#").toLowerCase(Locale.ROOT);
    }

    private String removeBoilerplate(String text, BoilerplateSignature boilerplate) {
        if (text == null) {
            return "";
        }
        if (boilerplate.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n");
        List<Integer> contentIndexes = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                contentIndexes.add(i);
            }
        }
        int window = edgeWindow(contentIndexes.size());
        Set<Integer> topIndexes = new HashSet<>();
        Set<Integer> bottomIndexes = new HashSet<>();
        for (int i = 0; i < window && i < contentIndexes.size(); i++) {
            topIndexes.add(contentIndexes.get(i));
        }
        for (int i = Math.max(0, contentIndexes.size() - window); i < contentIndexes.size(); i++) {
            bottomIndexes.add(contentIndexes.get(i));
        }

        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String key = boilerplateKey(lines[i]);
            boolean drop = (topIndexes.contains(i) && boilerplate.top().contains(key))
                    || (bottomIndexes.contains(i) && boilerplate.bottom().contains(key));
            if (!drop) {
                kept.add(lines[i]);
            }
        }
        return String.join("\n", kept);
    }

    private record BoilerplateSignature(Set<String> top, Set<String> bottom) {
        static BoilerplateSignature empty() {
            return new BoilerplateSignature(Set.of(), Set.of());
        }

        boolean isEmpty() {
            return top.isEmpty() && bottom.isEmpty();
        }
    }

    /**
     * Splits raw page text into blocks. Prose blocks are collapsed onto one line,
     * but markdown table blocks keep their newlines so the row/column structure
     * produced during extraction survives into the chunk.
     */
    private List<String> splitBlocks(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String normalized = rawText
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
        List<String> blocks = new ArrayList<>();
        for (String raw : normalized.split("\\n\\s*\\n")) {
            if (!raw.isBlank()) {
                blocks.addAll(separateTableRuns(raw));
            }
        }
        if (blocks.isEmpty()) {
            String fallback = cleanText(rawText);
            if (!fallback.isBlank()) {
                blocks.add(fallback);
            }
        }
        return blocks;
    }

    /** Splits a block into alternating runs of table rows and prose. */
    private List<String> separateTableRuns(String block) {
        List<String> result = new ArrayList<>();
        List<String> tableLines = new ArrayList<>();
        List<String> textLines = new ArrayList<>();
        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("|")) {
                flushProse(textLines, result);
                tableLines.add(trimmed);
            } else {
                flushTable(tableLines, result);
                textLines.add(trimmed);
            }
        }
        flushTable(tableLines, result);
        flushProse(textLines, result);
        return result;
    }

    private void flushTable(List<String> tableLines, List<String> result) {
        if (!tableLines.isEmpty()) {
            result.add(String.join("\n", tableLines));
            tableLines.clear();
        }
    }

    private void flushProse(List<String> textLines, List<String> result) {
        if (textLines.isEmpty()) {
            return;
        }
        String paragraph = String.join(" ", textLines).replaceAll("[ \\t\\x0B\\f]+", " ").trim();
        if (!paragraph.isBlank()) {
            result.add(paragraph);
        }
        textLines.clear();
    }

    private boolean isTableBlock(String block) {
        return block != null && block.startsWith("|");
    }

    /**
     * Splits a segment that alone exceeds the chunk budget. Prose is cut on
     * sentence boundaries; tables are cut on row boundaries with the header row
     * repeated so every part stays readable on its own.
     */
    private List<TextSegment> splitOversizedSegment(TextSegment segment, ChunkPlan plan) {
        // The heading path is stored in front of the body, so it eats into the budget
        // a single part may occupy.
        int budget = segmentBudget(segment, plan);
        if (countTokens(segment.text()) <= budget) {
            return List.of(segment);
        }
        if (segment.table()) {
            return splitTableSegment(segment, budget);
        }

        return splitTextToBudget(segment.text(), budget).stream()
                .map(segment::withText)
                .toList();
    }

    private int segmentBudget(TextSegment segment, ChunkPlan plan) {
        String heading = segment.headingPath();
        int headingTokens = heading == null || heading.isBlank() ? 0 : countTokens(heading);
        return Math.max(plan.size() / 4, plan.size() - headingTokens);
    }

    /**
     * Splits free text into pieces of at most {@code maxTokens}, cutting on
     * sentence boundaries and only falling back to word windows for a single
     * sentence that is itself over budget (typical of OCR output with no
     * punctuation).
     */
    private List<String> splitTextToBudget(String text, int maxTokens) {
        List<String> parts = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;
        for (String sentence : splitSentences(text)) {
            int sentenceTokens = countTokens(sentence);
            if (sentenceTokens > maxTokens) {
                if (!current.isEmpty()) {
                    parts.add(String.join(" ", current));
                    current = new ArrayList<>();
                    currentTokens = 0;
                }
                parts.addAll(splitByWords(sentence, maxTokens));
                continue;
            }
            if (!current.isEmpty() && currentTokens + sentenceTokens > maxTokens) {
                parts.add(String.join(" ", current));
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(sentence);
            currentTokens += sentenceTokens;
        }
        if (!current.isEmpty()) {
            parts.add(String.join(" ", current));
        }
        return parts.isEmpty() ? List.of(text) : parts;
    }

    private List<TextSegment> splitTableSegment(TextSegment segment, int budget) {
        String[] lines = segment.text().split("\n");
        String header = lines.length > 0 ? lines[0] : "";
        boolean hasSeparator = lines.length > 1 && lines[1].replace("|", "").trim().matches("[-\\s]+");
        String headerBlock = hasSeparator ? header + "\n" + lines[1] : header;
        int bodyStart = hasSeparator ? 2 : 1;
        int headerTokens = countTokens(headerBlock);
        // An unusually large header would leave no room for rows; stop repeating it.
        boolean repeatHeader = headerTokens <= budget / 2;
        String prefix = repeatHeader ? headerBlock + "\n" : "";
        int rowBudget = budget - (repeatHeader ? headerTokens : 0);

        List<TextSegment> parts = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;
        for (int i = bodyStart; i < lines.length; i++) {
            String row = lines[i];
            int rowTokens = countTokens(row);
            if (rowTokens > rowBudget) {
                // A single row bigger than the budget cannot be kept intact; emit it
                // in sentence-sized pieces, each still carrying the header so the
                // columns stay identifiable.
                if (!current.isEmpty()) {
                    parts.add(segment.withText(prefix + String.join("\n", current)));
                    current = new ArrayList<>();
                    currentTokens = 0;
                }
                for (String piece : splitTextToBudget(row, rowBudget)) {
                    parts.add(segment.withText(prefix + piece));
                }
                continue;
            }
            if (!current.isEmpty() && currentTokens + rowTokens > rowBudget) {
                parts.add(segment.withText(prefix + String.join("\n", current)));
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(row);
            currentTokens += rowTokens;
        }
        if (!current.isEmpty()) {
            parts.add(segment.withText(prefix + String.join("\n", current)));
        }
        return parts.isEmpty() ? List.of(segment) : parts;
    }

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(SENTENCE_LOCALE);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences.isEmpty() ? List.of(text.trim()) : sentences;
    }

    private List<String> splitByWords(String text, int maxTokens) {
        String[] words = text.trim().split("\\s+");
        List<String> parts = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String word : words) {
            current.add(word);
            if (current.size() % 8 == 0 && countTokens(String.join(" ", current)) >= maxTokens) {
                parts.add(String.join(" ", current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            parts.add(String.join(" ", current));
        }
        return parts.isEmpty() ? List.of(text) : parts;
    }

    /**
     * Returns the trailing segments of the finished chunk that fit inside the
     * overlap budget. When the last segment alone is larger than the budget, only
     * its trailing sentences are carried over — previously the whole segment was
     * always carried, which made each chunk a superset of the one before it.
     */
    private List<TextSegment> overlapSegments(List<TextSegment> segments, ChunkPlan plan) {
        List<TextSegment> overlap = new ArrayList<>();
        int tokens = 0;
        for (int i = segments.size() - 1; i >= 0; i--) {
            TextSegment segment = segments.get(i);
            int segmentTokens = countTokens(segment.text());
            if (tokens + segmentTokens <= plan.overlap()) {
                overlap.add(0, segment);
                tokens += segmentTokens;
                continue;
            }
            int remaining = plan.overlap() - tokens;
            if (remaining > 0 && !segment.table()) {
                String tail = trailingText(segment.text(), remaining);
                if (!tail.isBlank()) {
                    overlap.add(0, segment.withText(tail));
                }
            }
            break;
        }
        return overlap;
    }

    /** Trailing sentences of {@code text} that fit within {@code maxTokens}. */
    private String trailingText(String text, int maxTokens) {
        List<String> sentences = splitSentences(text);
        List<String> tail = new ArrayList<>();
        int tokens = 0;
        for (int i = sentences.size() - 1; i >= 0; i--) {
            String sentence = sentences.get(i);
            int sentenceTokens = countTokens(sentence);
            if (tail.isEmpty() && sentenceTokens > maxTokens) {
                return trailingWords(sentence, maxTokens);
            }
            if (tokens + sentenceTokens > maxTokens) {
                break;
            }
            tail.add(0, sentence);
            tokens += sentenceTokens;
        }
        return String.join(" ", tail).trim();
    }

    private String trailingWords(String text, int maxTokens) {
        String[] words = text.trim().split("\\s+");
        List<String> picked = new ArrayList<>();
        String best = "";
        for (int i = words.length - 1; i >= 0; i--) {
            picked.add(0, words[i]);
            if (picked.size() % 8 == 0) {
                String candidate = String.join(" ", picked);
                if (countTokens(candidate) > maxTokens) {
                    break;
                }
                best = candidate;
            }
        }
        if (best.isEmpty()) {
            String candidate = String.join(" ", picked);
            best = countTokens(candidate) <= maxTokens ? candidate : "";
        }
        return best;
    }

    /**
     * Guarantees the document has chunks produced by the current strategy. A
     * document chunked by an older strategy is re-chunked into a new version and
     * that version is activated in the same transaction, so the older chunks are
     * deactivated rather than left active alongside the new ones (DOC-FR-12:
     * exactly one active chunk version per document).
     */
    @Transactional
    public List<DocumentChunk> ensureCanonicalChunks(UUID documentId) {
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        String currentStrategy = chunkPlanFor(document.getFileType()).strategy();
        List<DocumentChunk> existing = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<DocumentChunk> canonical = existing.stream()
                .filter(chunk -> currentStrategy.equalsIgnoreCase(chunk.getChunkStrategy()))
                .toList();
        if (!canonical.isEmpty()) {
            return canonical;
        }
        int version = reindexChunks(documentId);
        activateChunkVersion(documentId, version);
        return documentChunkRepository.findByDocumentIdAndChunkVersionOrderByChunkIndexAsc(documentId, version);
    }

    /**
     * Re-chunks a document into a brand-new, inactive chunk version from its
     * already-extracted pages, without touching the currently active version.
     * Chat keeps answering from the old (still active) version until
     * {@link #activateChunkVersion} flips over — a failed re-index simply leaves
     * the inactive draft version behind.
     */
    @Transactional
    public int reindexChunks(UUID documentId) {
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        List<DocumentPage> pages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(documentId);
        if (pages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document has no extracted pages to reindex.");
        }
        int newVersion = documentChunkRepository.findMaxChunkVersion(documentId) + 1;
        saveChunks(document, pages, newVersion, false);
        return newVersion;
    }

    /**
     * Atomically makes {@code version} the only active chunk version for a
     * document. Called only after the new version's embeddings finished
     * successfully, so the swap is all-or-nothing from a chat perspective.
     */
    @Transactional
    public void activateChunkVersion(UUID documentId, int version) {
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        for (DocumentChunk chunk : chunks) {
            boolean shouldBeActive = chunk.getChunkVersion() != null && chunk.getChunkVersion() == version;
            if (!java.util.Objects.equals(chunk.getIsActive(), shouldBeActive)) {
                chunk.setIsActive(shouldBeActive);
            }
        }
        documentChunkRepository.saveAll(chunks);
    }

    public DocumentDto.DocumentResponse requireReindexAccess(UUID documentId, UUID requesterId) {
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        if (!isAdmin(requesterId) && !requesterId.equals(document.getUploadedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot reindex this document.");
        }
        return toResponse(document, requesterId);
    }

    private DocumentChunk newChunk(
            CourseDocument document,
            List<TextSegment> segments,
            int chunkIndex,
            LocalDateTime createdAt,
            int chunkVersion,
            boolean active,
            ChunkPlan plan) {
        // Prefixing the heading path makes each chunk self-describing, so a chunk
        // taken out of context still says which section it came from. Built by the
        // same helper the budget check uses, so the two can never disagree.
        String content = assembleContent(segments);
        String headingPath = headingOf(segments);
        int pageStart = segments.stream().mapToInt(TextSegment::pageNumber).min().orElse(1);
        int pageEnd = segments.stream().mapToInt(TextSegment::pageNumber).max().orElse(pageStart);

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(document.getDocumentId());
        chunk.setWorkspaceId(document.getWorkspaceId());
        chunk.setCourseId(document.getCourseId());
        chunk.setChapterId(document.getChapterId());
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkStrategy(plan.strategy());
        chunk.setChunkSize(plan.size());
        chunk.setChunkOverlap(plan.overlap());
        chunk.setContent(content);
        chunk.setContentCompressed(EmbeddingService.compressUnicodeText(content));
        chunk.setPageStart(pageStart);
        chunk.setPageEnd(pageEnd);
        chunk.setTokenCount(countTokens(content));
        chunk.setWordCount(countWords(content));
        chunk.setCharCount(content.length());
        chunk.setCreatedAt(createdAt);
        chunk.setHeadingPath(headingPath);
        chunk.setChunkVersion(chunkVersion);
        chunk.setIsActive(active);
        return chunk;
    }

    /** One block of document text, tagged with where it came from. */
    private record TextSegment(String text, int pageNumber, String headingPath, boolean table) {
        TextSegment withText(String replacement) {
            return new TextSegment(replacement, pageNumber, headingPath, table);
        }
    }

    /**
     * Budget for one document. Slide decks carry short bullet text, so packing them
     * to the same size as running prose would blur several slides into one chunk.
     */
    private record ChunkPlan(int size, int overlap, String strategy) { }

    private ChunkPlan chunkPlanFor(String fileType) {
        boolean slides = "PPTX".equalsIgnoreCase(fileType);
        int size = slides ? slideChunkSize : chunkSize;
        int overlap = slides ? slideChunkOverlap : chunkOverlap;
        String unit = tokenCounter.isExact() ? "bge" : "est";
        // The semantic setting is part of the identity: turning it on produces
        // different chunks, and ensureCanonicalChunks compares strategy names to
        // decide whether existing chunks are still current.
        String mode = semanticBoundaries.isEnabled() ? "_sem" + semanticBoundaries.thresholdLabel() : "";
        return new ChunkPlan(size, overlap,
                CHUNK_STRATEGY_PREFIX + "_" + size + "_" + overlap + "_" + unit + mode);
    }

    private int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return tokenCounter.count(text);
    }

    private List<ExtractedPage> extractPages(Path filePath, String fileType) throws IOException {
        return switch (fileType) {
            case "PDF" -> extractPdfPages(filePath);
            case "DOCX" -> extractDocxPages(filePath);
            case "PPTX" -> extractPptxPages(filePath);
            case "TXT", "MD", "CSV" ->
                    List.of(new ExtractedPage(1, Files.readString(filePath, StandardCharsets.UTF_8), null, false, null));
            default -> throw new IOException("Unsupported file type: " + fileType);
        };
    }

    /**
     * Runs Tesseract OCR on PDF pages whose text-layer extraction came back empty
     * (typical of scanned documents). Non-PDF files and pages that already have
     * text are returned unchanged. OCR failures are isolated per page: a page that
     * cannot be OCR'd stays empty rather than failing the whole document.
     */
    private List<ExtractedPage> applyOcrFallback(Path filePath, String fileType, List<ExtractedPage> extractedPages) {
        if (!"PDF".equals(fileType)) {
            return extractedPages;
        }
        boolean needsOcr = extractedPages.stream().anyMatch(page -> page.text() == null || page.text().isBlank());
        if (!needsOcr) {
            return extractedPages;
        }

        List<ExtractedPage> result = new ArrayList<>(extractedPages);
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            ITesseract tesseract = createTesseract();
            if (tesseract == null) {
                return result;
            }
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < result.size(); i++) {
                ExtractedPage page = result.get(i);
                if (page.text() != null && !page.text().isBlank()) {
                    continue;
                }
                try {
                    BufferedImage rendered = renderer.renderImageWithDPI(page.pageNumber() - 1, 300);
                    OcrResult ocr = runOcr(tesseract, rendered);
                    result.set(i, new ExtractedPage(
                            page.pageNumber(), ocr.text(), page.headingPath(), true, ocr.confidence()));
                } catch (TesseractException | IOException ocrException) {
                    // Leave this page empty; the rest of the document still processes normally.
                }
            }
        } catch (IOException loadException) {
            return result;
        }
        return result;
    }

    /**
     * Collects embedded images while a DOCX/PPTX is being read, then decides which
     * of them are worth OCR-ing. Decorative assets — logos, icons, dividers — are
     * dropped, so only images that actually carry content contribute text.
     */
    private static final class EmbeddedImages {
        private final Map<String, byte[]> byKey = new LinkedHashMap<>();
        private final Map<String, Integer> occurrences = new HashMap<>();

        /** Records an occurrence and returns the placeholder to inline in the text. */
        String register(byte[] data) {
            if (data == null || data.length == 0) {
                return "";
            }
            String key = digest(data);
            byKey.putIfAbsent(key, data);
            occurrences.merge(key, 1, Integer::sum);
            return IMAGE_PLACEHOLDER_PREFIX + key + IMAGE_PLACEHOLDER_SUFFIX;
        }

        boolean isEmpty() {
            return byKey.isEmpty();
        }

        private static String digest(byte[] data) {
            try {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(data);
                StringBuilder value = new StringBuilder();
                for (int i = 0; i < 8; i++) {
                    value.append(String.format("%02x", hash[i]));
                }
                return value.toString();
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable.", exception);
            }
        }
    }

    /**
     * Replaces the image placeholders left during extraction with whatever text OCR
     * could recover. An image repeated across much of the document is treated as a
     * logo/watermark and skipped, and each distinct image is only OCR'd once no
     * matter how often it appears.
     */
    private List<ExtractedPage> resolveEmbeddedImages(List<ExtractedPage> pages, EmbeddedImages images) {
        if (images.isEmpty()) {
            return pages;
        }
        ITesseract tesseract = createTesseract();
        int repeatLimit = Math.max(3, (int) Math.ceil(pages.size() * 0.4));

        Map<String, String> textByKey = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : images.byKey.entrySet()) {
            boolean looksLikeLogo = images.occurrences.getOrDefault(entry.getKey(), 0) >= repeatLimit;
            textByKey.put(entry.getKey(),
                    looksLikeLogo || tesseract == null ? "" : ocrEmbeddedImage(tesseract, entry.getValue()));
        }

        List<ExtractedPage> resolved = new ArrayList<>(pages.size());
        for (ExtractedPage page : pages) {
            String text = page.text();
            boolean recovered = false;
            for (Map.Entry<String, String> entry : textByKey.entrySet()) {
                String placeholder = IMAGE_PLACEHOLDER_PREFIX + entry.getKey() + IMAGE_PLACEHOLDER_SUFFIX;
                if (!text.contains(placeholder)) {
                    continue;
                }
                recovered |= !entry.getValue().isEmpty();
                text = text.replace(placeholder, entry.getValue());
            }
            resolved.add(new ExtractedPage(
                    page.pageNumber(), text, page.headingPath(),
                    page.ocrApplied() || recovered, page.ocrConfidence()));
        }
        return resolved;
    }

    /** OCR of one embedded image, or empty when it is decorative or unreadable. */
    private String ocrEmbeddedImage(ITesseract tesseract, byte[] data) {
        try {
            BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (image == null) {
                return "";
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width < MIN_OCR_IMAGE_SIDE || height < MIN_OCR_IMAGE_SIDE) {
                return "";
            }
            double ratio = (double) Math.max(width, height) / Math.min(width, height);
            if (ratio > MAX_OCR_ASPECT_RATIO) {
                return "";
            }
            OcrResult result = runOcr(tesseract, image);
            String text = cleanText(result.text());
            if (text.length() < MIN_OCR_TEXT_CHARS || result.confidence() < ocrMinConfidence) {
                return "";
            }
            return text;
        } catch (IOException | TesseractException | RuntimeException exception) {
            return "";
        }
    }

    private ITesseract createTesseract() {
        if (ocrTessdataPath == null || ocrTessdataPath.isBlank()) {
            return null;
        }
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(ocrTessdataPath);
        tesseract.setLanguage(ocrLanguage);
        return tesseract;
    }

    /**
     * Runs Tesseract once and derives both the page text and its mean word
     * confidence from the same word list, instead of paying for a second full OCR
     * pass just to measure confidence. Words are regrouped into lines by their
     * bounding boxes, and a blank line is inserted on a large vertical gap so the
     * paragraph structure survives into chunking.
     */
    private OcrResult runOcr(ITesseract tesseract, BufferedImage image) throws TesseractException {
        List<Word> words;
        try {
            words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        } catch (RuntimeException exception) {
            words = List.of();
        }
        if (words == null || words.isEmpty()) {
            return new OcrResult(tesseract.doOCR(image), 0.0);
        }

        List<List<Word>> lines = groupWordsIntoLines(words);
        StringBuilder text = new StringBuilder();
        Rectangle previousLine = null;
        for (List<Word> line : lines) {
            Rectangle bounds = lineBounds(line);
            if (previousLine != null) {
                int gap = bounds.y - (previousLine.y + previousLine.height);
                if (gap > Math.max(previousLine.height, bounds.height) * 0.6) {
                    text.append('\n');
                }
            }
            text.append(line.stream().map(Word::getText).map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.joining(" ")));
            text.append('\n');
            previousLine = bounds;
        }

        double confidence = words.stream().mapToDouble(Word::getConfidence).average().orElse(0.0);
        return new OcrResult(text.toString(), confidence);
    }

    private List<List<Word>> groupWordsIntoLines(List<Word> words) {
        List<Word> sorted = new ArrayList<>(words);
        sorted.sort(Comparator
                .comparingInt((Word word) -> word.getBoundingBox().y)
                .thenComparingInt(word -> word.getBoundingBox().x));

        List<List<Word>> lines = new ArrayList<>();
        for (Word word : sorted) {
            Rectangle box = word.getBoundingBox();
            List<Word> target = null;
            if (!lines.isEmpty()) {
                Rectangle reference = lineBounds(lines.get(lines.size() - 1));
                int overlap = Math.min(reference.y + reference.height, box.y + box.height)
                        - Math.max(reference.y, box.y);
                if (overlap > Math.min(reference.height, box.height) * 0.5) {
                    target = lines.get(lines.size() - 1);
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                lines.add(target);
            }
            target.add(word);
        }
        for (List<Word> line : lines) {
            line.sort(Comparator.comparingInt(word -> word.getBoundingBox().x));
        }
        return lines;
    }

    private Rectangle lineBounds(List<Word> line) {
        Rectangle bounds = null;
        for (Word word : line) {
            bounds = bounds == null ? new Rectangle(word.getBoundingBox()) : bounds.union(word.getBoundingBox());
        }
        return bounds == null ? new Rectangle() : bounds;
    }

    private record OcrResult(String text, double confidence) { }

    private List<ExtractedPage> extractPdfPages(Path filePath) throws IOException {
        List<PdfLine> lines = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PdfLineCollector collector = new PdfLineCollector(lines);
            collector.setSortByPosition(true);
            collector.writeText(document, java.io.Writer.nullWriter());
        }
        return assemblePdfPages(reorderColumns(lines));
    }

    /**
     * A two-column page read top-to-bottom interleaves the columns into nonsense.
     * When the line-start positions on a page are clearly bimodal (a left cluster
     * and a right cluster, with a gutter between them), the lines are regrouped so
     * the left column is read fully before the right one. Layouts that are not
     * clearly two-column are left untouched.
     */
    private List<PdfLine> reorderColumns(List<PdfLine> lines) {
        Map<Integer, List<PdfLine>> byPage = lines.stream()
                .collect(Collectors.groupingBy(PdfLine::page, LinkedHashMap::new, Collectors.toList()));
        List<PdfLine> reordered = new ArrayList<>(lines.size());
        for (List<PdfLine> pageLines : byPage.values()) {
            reordered.addAll(reorderPageColumns(pageLines));
        }
        return reordered;
    }

    private List<PdfLine> reorderPageColumns(List<PdfLine> pageLines) {
        if (pageLines.size() < 6) {
            return pageLines;
        }
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        for (PdfLine line : pageLines) {
            minX = Math.min(minX, line.startX());
            maxX = Math.max(maxX, line.endX());
        }
        float width = maxX - minX;
        if (width <= 0) {
            return pageLines;
        }

        float split = minX + width / 2f;
        List<PdfLine> left = new ArrayList<>();
        List<PdfLine> right = new ArrayList<>();
        for (PdfLine line : pageLines) {
            if (line.startX() >= split) {
                right.add(line);
            } else {
                left.add(line);
            }
        }
        // Require both sides to be substantial, and no line to straddle the gutter,
        // otherwise this is a single-column page with an indent or a wide figure.
        if (left.size() < pageLines.size() * 0.25 || right.size() < pageLines.size() * 0.25) {
            return pageLines;
        }
        boolean straddles = left.stream().anyMatch(line -> line.endX() > split + width * 0.05f);
        if (straddles) {
            return pageLines;
        }

        List<PdfLine> ordered = new ArrayList<>(pageLines.size());
        ordered.addAll(left);
        ordered.addAll(right);
        return ordered;
    }

    /**
     * Turns the collected lines into pages, inferring the body text size so that
     * noticeably larger short lines can be treated as headings. Heading sizes are
     * ranked into levels, giving PDF chunks the same hierarchical heading path that
     * DOCX gets from Word styles.
     */
    private List<ExtractedPage> assemblePdfPages(List<PdfLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        float bodySize = bodyFontSize(lines);
        List<Float> headingSizes = lines.stream()
                .filter(line -> isLargerThanBody(line, bodySize))
                .map(line -> roundSize(line.fontSize()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(4)
                .toList();
        // Bold text and numbering are weak signals: in a textbook they also match
        // list items, exercise questions and bibliography entries. Only trust them
        // when the document has no typographic hierarchy to read instead.
        boolean allowWeakSignals = headingSizes.isEmpty();

        Map<Integer, List<PdfLine>> byPage = lines.stream()
                .collect(Collectors.groupingBy(PdfLine::page, LinkedHashMap::new, Collectors.toList()));

        List<String> headingStack = new ArrayList<>();
        List<ExtractedPage> pages = new ArrayList<>();
        for (Map.Entry<Integer, List<PdfLine>> entry : byPage.entrySet()) {
            List<PdfLine> pageLines = entry.getValue();
            String pageHeading = headingStack.isEmpty() ? null : String.join(" › ", headingStack);
            boolean headingAssigned = false;
            StringBuilder text = new StringBuilder();

            for (int index = 0; index < pageLines.size(); index++) {
                PdfLine line = pageLines.get(index);
                int level = pdfHeadingLevel(line, headingSizes, bodySize, allowWeakSignals);
                if (level > 0) {
                    while (headingStack.size() >= level) {
                        headingStack.remove(headingStack.size() - 1);
                    }
                    headingStack.add(line.text().trim());
                    // A heading near the top of the page describes that page; one that
                    // appears further down belongs mostly to the following page.
                    if (!headingAssigned && index <= Math.max(1, pageLines.size() / 4)) {
                        pageHeading = String.join(" › ", headingStack);
                        headingAssigned = true;
                    }
                }
                if (line.paragraphBreak() && text.length() > 0) {
                    text.append('\n');
                }
                text.append(line.text()).append('\n');
            }
            pages.add(new ExtractedPage(entry.getKey(), text.toString(), pageHeading, false, null));
        }
        return pages;
    }

    /** Most common line size weighted by how much text is set at that size. */
    private float bodyFontSize(List<PdfLine> lines) {
        Map<Float, Integer> weights = new HashMap<>();
        for (PdfLine line : lines) {
            String trimmed = line.text().trim();
            if (!trimmed.isEmpty()) {
                weights.merge(roundSize(line.fontSize()), trimmed.length(), Integer::sum);
            }
        }
        return weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0f);
    }

    /**
     * A heading is short, and stands out in at least one of three ways: a larger
     * font, bold weight, or an explicit numbering prefix. Relying on font size
     * alone missed headings in PDFs typeset at a single size, which is common in
     * exported course material.
     */
    private boolean isLargerThanBody(PdfLine line, float bodySize) {
        String trimmed = line.text().trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= HEADING_MAX_CHARS
                && bodySize > 0
                && roundSize(line.fontSize()) > bodySize * 1.15f;
    }

    /**
     * A heading is short and stands out. A larger font is the reliable signal; bold
     * weight and numbering are only consulted for documents typeset at a single
     * size, where nothing else distinguishes a heading.
     */
    private boolean isHeadingCandidate(PdfLine line, float bodySize, boolean allowWeakSignals) {
        if (isLargerThanBody(line, bodySize)) {
            return true;
        }
        if (!allowWeakSignals) {
            return false;
        }
        String trimmed = line.text().trim();
        if (trimmed.isEmpty() || trimmed.length() > WEAK_HEADING_MAX_CHARS) {
            return false;
        }
        boolean sameSize = bodySize <= 0 || roundSize(line.fontSize()) >= bodySize;
        return sameSize && (line.bold() || looksNumberedHeading(trimmed));
    }

    /** Matches "Chương 1", "PHẦN II", "1.2", "1.2.3", "I." and similar prefixes. */
    private boolean looksNumberedHeading(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        return normalized.matches("^(chuong|chương|phan|phần|bai|bài|muc|mục|tiet|tiết)\\s+[0-9ivxl]+\\b.*")
                || normalized.matches("^[0-9]+(\\.[0-9]+){0,3}\\.?\\s+\\p{L}.*")
                || normalized.matches("^[ivxl]+\\.\\s+\\p{L}.*");
    }

    /**
     * Levels come from font size when the document varies it; otherwise from the
     * depth of a numbering prefix ("1.2.3" is level 3), so a single-size PDF still
     * produces a hierarchy instead of a flat list.
     */
    private int pdfHeadingLevel(
            PdfLine line, List<Float> headingSizes, float bodySize, boolean allowWeakSignals) {
        if (!isHeadingCandidate(line, bodySize, allowWeakSignals)) {
            return 0;
        }
        int index = headingSizes.indexOf(roundSize(line.fontSize()));
        if (index >= 0) {
            return index + 1;
        }
        return numberingDepth(line.text().trim());
    }

    private int numberingDepth(String text) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^([0-9]+(?:\\.[0-9]+){0,3})\\.?\\s").matcher(text);
        if (matcher.find()) {
            return matcher.group(1).split("\\.").length;
        }
        return 1;
    }

    private float roundSize(float size) {
        return Math.round(size * 2f) / 2f;
    }

    private record PdfLine(
            int page, String text, float fontSize, float startX, float endX,
            boolean paragraphBreak, boolean bold) { }

    /**
     * Walks the PDF once and records every line with its font size and horizontal
     * extent, instead of calling {@code PDFTextStripper.getText(document)} once per
     * page (which re-walks the whole document on every call and is O(n^2) for large
     * PDFs). The captured geometry then drives paragraph breaks, heading detection
     * and column reordering.
     */
    private static final class PdfLineCollector extends PDFTextStripper {
        private static final float PARAGRAPH_GAP_RATIO = 1.6f;

        private final List<PdfLine> lines;
        private int pageNumber = 0;
        private float previousLineY = Float.NaN;
        private float previousLineHeight = 0f;

        PdfLineCollector(List<PdfLine> lines) throws IOException {
            this.lines = lines;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            previousLineY = Float.NaN;
            previousLineHeight = 0f;
            pageNumber++;
            super.startPage(page);
        }

        /** PDFs express weight through the font name; there is no bold flag. */
        private static boolean isBoldFont(TextPosition position) {
            if (position.getFont() == null || position.getFont().getName() == null) {
                return false;
            }
            String name = position.getFont().getName().toLowerCase(java.util.Locale.ROOT);
            return name.contains("bold") || name.contains("black") || name.contains("heavy");
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            float lineY = Float.NaN;
            float lineHeight = 0f;
            float fontSize = 0f;
            float startX = 0f;
            float endX = 0f;
            int boldGlyphs = 0;
            int totalGlyphs = 0;

            if (textPositions != null && !textPositions.isEmpty()) {
                TextPosition first = textPositions.get(0);
                lineY = first.getYDirAdj();
                lineHeight = first.getHeightDir();
                startX = first.getXDirAdj();
                for (TextPosition position : textPositions) {
                    lineHeight = Math.max(lineHeight, position.getHeightDir());
                    fontSize = Math.max(fontSize, position.getFontSizeInPt());
                    startX = Math.min(startX, position.getXDirAdj());
                    endX = Math.max(endX, position.getXDirAdj() + position.getWidthDirAdj());
                    totalGlyphs++;
                    if (isBoldFont(position)) {
                        boldGlyphs++;
                    }
                }
            }
            boolean bold = totalGlyphs > 0 && boldGlyphs * 2 > totalGlyphs;

            boolean paragraphBreak = false;
            if (!Float.isNaN(lineY) && !Float.isNaN(previousLineY)) {
                float reference = Math.max(previousLineHeight, lineHeight);
                if (reference > 0 && lineY - previousLineY > reference * PARAGRAPH_GAP_RATIO) {
                    paragraphBreak = true;
                }
            }

            lines.add(new PdfLine(
                    pageNumber, text, fontSize > 0 ? fontSize : lineHeight, startX, endX, paragraphBreak, bold));

            if (!Float.isNaN(lineY)) {
                previousLineY = lineY;
                if (lineHeight > 0) {
                    previousLineHeight = lineHeight;
                }
            }
            super.writeString(text, textPositions);
        }
    }

    private List<ExtractedPage> extractDocxPages(Path filePath) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder buffer = new StringBuilder();
            List<String> headingStack = new ArrayList<>();
            String currentHeading = null;
            int pageNumber = 1;
            EmbeddedImages images = new EmbeddedImages();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    for (XWPFRun run : paragraph.getRuns()) {
                        for (XWPFPicture picture : run.getEmbeddedPictures()) {
                            XWPFPictureData data = picture.getPictureData();
                            if (data != null) {
                                buffer.append(images.register(data.getData())).append("\n\n");
                            }
                        }
                    }
                    String text = paragraph.getText();
                    int level = headingLevel(paragraph);
                    if (level > 0 && !text.isBlank()) {
                        if (buffer.length() > 0) {
                            pages.add(new ExtractedPage(pageNumber++, buffer.toString(), currentHeading, false, null));
                            buffer.setLength(0);
                        }
                        // Drop any deeper/sibling headings, then push this one, so the
                        // stack always reads as the path from the document root.
                        while (headingStack.size() >= level) {
                            headingStack.remove(headingStack.size() - 1);
                        }
                        headingStack.add(text.trim());
                        currentHeading = String.join(" › ", headingStack);
                        buffer.append(text).append("\n\n");
                    } else if (!text.isBlank()) {
                        buffer.append(text).append("\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    buffer.append(tableToMarkdown(table)).append("\n\n");
                }
            }
            if (buffer.length() > 0) {
                pages.add(new ExtractedPage(pageNumber, buffer.toString(), currentHeading, false, null));
            }
            return resolveEmbeddedImages(pages, images);
        }
    }

    /** Word heading level (1-6), or 0 when the paragraph is not a heading. */
    private int headingLevel(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null) {
            return 0;
        }
        String normalized = styleId.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.startsWith("title")) {
            return 1;
        }
        if (!normalized.startsWith("heading")) {
            return 0;
        }
        String digits = normalized.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            int level = Integer.parseInt(digits);
            return level >= 1 && level <= 6 ? level : 0;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String tableToMarkdown(XWPFTable table) {
        StringBuilder markdown = new StringBuilder();
        List<XWPFTableRow> rows = table.getRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
            markdown.append("| ");
            for (XWPFTableCell cell : cells) {
                markdown.append(cell.getText().replace("\n", " ").trim()).append(" | ");
            }
            markdown.append('\n');
            if (rowIndex == 0) {
                markdown.append("|");
                for (int i = 0; i < cells.size(); i++) {
                    markdown.append(" --- |");
                }
                markdown.append('\n');
            }
        }
        return markdown.toString();
    }

    private String tableToMarkdown(XSLFTable table) {
        StringBuilder markdown = new StringBuilder();
        List<XSLFTableRow> rows = table.getRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<XSLFTableCell> cells = rows.get(rowIndex).getCells();
            markdown.append("| ");
            for (XSLFTableCell cell : cells) {
                markdown.append(cell.getText().replace("\n", " ").trim()).append(" | ");
            }
            markdown.append('\n');
            if (rowIndex == 0) {
                markdown.append("|");
                for (int i = 0; i < cells.size(); i++) {
                    markdown.append(" --- |");
                }
                markdown.append('\n');
            }
        }
        return markdown.toString();
    }

    private List<ExtractedPage> extractPptxPages(Path filePath) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(filePath);
             XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            int pageNumber = 1;
            EmbeddedImages images = new EmbeddedImages();
            for (XSLFSlide slide : slideShow.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTable table) {
                        text.append(tableToMarkdown(table)).append('\n');
                    } else if (shape instanceof XSLFTextShape textShape) {
                        text.append(textShape.getText()).append("\n\n");
                    } else if (shape instanceof XSLFPictureShape picture
                            && picture.getPictureData() != null) {
                        text.append(images.register(picture.getPictureData().getData())).append("\n\n");
                    }
                }
                pages.add(new ExtractedPage(
                        pageNumber, text.toString(), slideHeading(slide, pageNumber), false, null));
                pageNumber++;
            }
            return resolveEmbeddedImages(pages, images);
        }
    }

    /** Slide title if the deck defines one, otherwise a stable "Slide N" label. */
    private String slideHeading(XSLFSlide slide, int pageNumber) {
        String title = slide.getTitle();
        if (title != null && !title.isBlank()) {
            return "Slide " + pageNumber + " › " + title.trim();
        }
        return "Slide " + pageNumber;
    }

    private void validateUpload(MultipartFile file, DocumentDto.UploadDocumentRequest request) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required.");
        }
        validateUploadTarget(request);
    }

    /** Permission and destination checks that do not depend on the file itself. */
    private void validateUploadTarget(DocumentDto.UploadDocumentRequest request) {
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
            // Publishing straight into a course is an admin action. The URL-based rule
            // in SecurityConfig only guards /api/documents/**, so enforcing it here too
            // keeps any other entry point (such as resumable uploads) from bypassing it.
            if (!isAdmin(request.uploadedBy)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only an administrator can upload directly into a course.");
            }
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
                .filter(course -> courseDocumentRepository
                        .existsByCourseIdAndProcessingStatusAndIndexingStatus(courseId, "PROCESSED", "INDEXED"))
                .isPresent();
    }

    private void validatePersonalQuota(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required.");
        }
        validatePersonalQuota(sanitizeFilename(file.getOriginalFilename()), file.getSize(), userId);
    }

    /**
     * The quota rules themselves, independent of how the bytes arrived. Both the
     * direct multipart upload and the resumable upload must pass through here;
     * checking only in the multipart controller left the resumable endpoint able to
     * exceed the document count, the storage total and the accepted file types.
     */
    public void validatePersonalQuota(String filename, long sizeBytes, UUID userId) {
        com.courseqa.model.entity.SubscriptionPlan plan = subscriptionService.effectivePlanForQuota(userId);
        String fileType = resolveFileType(sanitizeFilename(filename));
        if (!List.of("PDF", "DOCX", "PPTX").contains(fileType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Personal uploads support PDF, DOCX, and PPTX files only.");
        }
        if (sizeBytes > plan.getMaxFileBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Each file is limited to " + toMegabytes(plan.getMaxFileBytes()) + " MB on your plan.");
        }
        if (courseDocumentRepository.countByUploadedByAndDocumentScope(userId, "PERSONAL") >= plan.getMaxDocuments()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your plan supports at most " + plan.getMaxDocuments() + " personal documents.");
        }
        long usedBytes = java.util.Optional.ofNullable(
                courseDocumentRepository.sumFileSizeByUploadedByAndDocumentScope(userId, "PERSONAL")).orElse(0L);
        if (usedBytes + sizeBytes > plan.getMaxStorageBytes()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your plan includes " + toMegabytes(plan.getMaxStorageBytes()) + " MB of personal storage.");
        }
    }

    private static long toMegabytes(long bytes) {
        return bytes / 1024 / 1024;
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

    /**
     * Confirms the bytes match the extension before anything downstream trusts it.
     * The extension is chosen by whoever uploads, so on its own it lets a renamed
     * executable or a truncated download reach Cloudinary and the parsers; the
     * first few bytes of these formats are fixed and cheap to check.
     */
    private void verifyFileSignature(Path filePath, String fileType, Path storageRoot) throws IOException {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = Files.newInputStream(filePath)) {
            read = in.readNBytes(head, 0, head.length);
        }
        boolean matches = switch (fileType) {
            // "%PDF"
            case "PDF" -> read >= 4 && head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46;
            // OOXML is a ZIP container: "PK\3\4" (or the empty/spanned variants).
            case "DOCX", "PPTX", "XLSX" -> read >= 4 && head[0] == 0x50 && head[1] == 0x4B
                    && (head[2] == 0x03 || head[2] == 0x05 || head[2] == 0x07);
            // Legacy Office is an OLE2 compound file: D0 CF 11 E0 A1 B1 1A E1.
            case "DOC", "PPT", "XLS" -> read >= 8 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF
                    && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0;
            // Plain text has no signature to check.
            default -> true;
        };
        if (!matches) {
            deleteStoredFile(filePath, storageRoot);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This file is not a valid " + fileType + " file.");
        }
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

    private record ExtractedPage(
            int pageNumber, String text, String headingPath, boolean ocrApplied, Double ocrConfidence) {
    }

    private record CloudinaryUpload(String publicId, String secureUrl) {
    }

    public record StoredDocumentFile(Path path, String url, String filename, String mimeType) {
        public boolean isRemote() {
            return url != null && !url.isBlank();
        }
    }
}
