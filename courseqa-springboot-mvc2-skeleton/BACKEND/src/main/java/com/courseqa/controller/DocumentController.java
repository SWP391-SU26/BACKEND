package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.DocumentDto;
import com.courseqa.service.DocumentEmbeddingIndexService;
import com.courseqa.service.DocumentService;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.security.JwtPrincipal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin
public class DocumentController {
    private final DocumentService documentService;
    private final DocumentEmbeddingIndexService documentEmbeddingIndexService;
    private final CourseWorkspaceRepository courseWorkspaceRepository;

    public DocumentController(
            DocumentService documentService,
            DocumentEmbeddingIndexService documentEmbeddingIndexService,
            CourseWorkspaceRepository courseWorkspaceRepository
    ) {
        this.documentService = documentService;
        this.documentEmbeddingIndexService = documentEmbeddingIndexService;
        this.courseWorkspaceRepository = courseWorkspaceRepository;
    }

    @PostMapping("/upload")
    public ApiResponse<DocumentDto.DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam UUID courseId,
            @RequestParam(required = false) UUID chapterId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        DocumentDto.UploadDocumentRequest request = new DocumentDto.UploadDocumentRequest();
        request.workspaceId = workspaceId != null ? workspaceId : courseWorkspaceRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream().findFirst().map(w -> w.getWorkspaceId()).orElse(null);
        request.courseId = courseId;
        request.chapterId = chapterId;
        request.uploadedBy = principal.userId();
        DocumentDto.DocumentResponse response = documentService.uploadDocument(file, request);
        documentEmbeddingIndexService.prepareDocument(response.documentId);
        return ApiResponse.ok(response);
    }

    @PostMapping("/personal")
    public ApiResponse<DocumentDto.DocumentResponse> uploadPersonalDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        DocumentDto.DocumentResponse response = documentService.uploadPersonalDocument(file, principal.userId());
        documentEmbeddingIndexService.prepareDocument(response.documentId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/mine")
    public ApiResponse<List<DocumentDto.DocumentResponse>> getMyDocuments(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.getMyDocuments(principal.userId()));
    }

    @PostMapping("/{documentId}/submission")
    public ApiResponse<DocumentDto.DocumentResponse> submitForReview(
            @PathVariable UUID documentId,
            @RequestBody DocumentDto.SubmissionRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.submitForReview(documentId, request.courseId, principal.userId()));
    }

    @DeleteMapping("/{documentId}/submission")
    public ApiResponse<DocumentDto.DocumentResponse> cancelSubmission(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.cancelSubmission(documentId, principal.userId()));
    }

    @GetMapping("/review-queue")
    public ApiResponse<List<DocumentDto.DocumentResponse>> getReviewQueue(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.getReviewQueue(principal.userId()));
    }

    @PatchMapping("/{documentId}/review")
    public ApiResponse<DocumentDto.DocumentResponse> reviewDocument(
            @PathVariable UUID documentId,
            @RequestBody DocumentDto.ReviewRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.reviewDocument(documentId, request, principal.userId()));
    }

    @GetMapping
    public ApiResponse<List<DocumentDto.DocumentResponse>> getDocuments(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(documentService.getDocuments(principal.userId()));
    }

    @GetMapping("/workspace/{workspaceId}")
    public ApiResponse<List<DocumentDto.DocumentResponse>> getDocumentsByWorkspace(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.getDocumentsByWorkspace(workspaceId, principal.userId()));
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentDto.DocumentResponse> getDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.getDocument(documentId, principal.userId()));
    }

    @GetMapping("/{documentId}/pages")
    public ApiResponse<List<DocumentDto.PageResponse>> getPages(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.getPages(documentId, principal.userId()));
    }

    @GetMapping("/{documentId}/chunks")
    public ApiResponse<List<DocumentDto.ChunkResponse>> getChunks(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(documentService.getChunks(documentId, principal.userId()));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        documentService.deleteDocument(documentId, principal.userId());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{documentId}/file")
    public ResponseEntity<?> getFile(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        DocumentService.StoredDocumentFile file = documentService.getStoredFile(documentId, principal.userId());
        if (file.isRemote()) {
            return ResponseEntity.status(302)
                    .location(URI.create(file.url()))
                    .build();
        }

        Resource resource = new FileSystemResource(file.path());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.filename())
                .build()
                .toString())
                .body(resource);
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<?> getPreview(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        DocumentService.StoredDocumentFile file = documentService.getPreviewFile(documentId, principal.userId());
        if (file.isRemote()) {
            return ResponseEntity.status(302)
                    .location(URI.create(file.url()))
                    .build();
        }

        Resource resource = new FileSystemResource(file.path());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.filename())
                        .build()
                        .toString())
                .body(resource);
    }
}
