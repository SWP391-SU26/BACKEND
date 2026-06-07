package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.DocumentDto;
import com.courseqa.service.DocumentService;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ApiResponse<DocumentDto.DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam UUID workspaceId,
            @RequestParam UUID courseId,
            @RequestParam(required = false) UUID chapterId,
            @RequestParam(required = false) UUID uploadedBy
    ) {
        DocumentDto.UploadDocumentRequest request = new DocumentDto.UploadDocumentRequest();
        request.workspaceId = workspaceId;
        request.courseId = courseId;
        request.chapterId = chapterId;
        request.uploadedBy = uploadedBy;
        return ApiResponse.ok(documentService.uploadDocument(file, request));
    }

    @GetMapping("/workspace/{workspaceId}")
    public ApiResponse<List<DocumentDto.DocumentResponse>> getDocumentsByWorkspace(@PathVariable UUID workspaceId) {
        return ApiResponse.ok(documentService.getDocumentsByWorkspace(workspaceId));
    }

    @GetMapping("/{documentId}/pages")
    public ApiResponse<List<DocumentDto.PageResponse>> getPages(@PathVariable UUID documentId) {
        return ApiResponse.ok(documentService.getPages(documentId));
    }

    @GetMapping("/{documentId}/chunks")
    public ApiResponse<List<DocumentDto.ChunkResponse>> getChunks(@PathVariable UUID documentId) {
        return ApiResponse.ok(documentService.getChunks(documentId));
    }

    @GetMapping("/{documentId}/file")
    public ResponseEntity<Resource> getFile(@PathVariable UUID documentId) {
        DocumentService.StoredDocumentFile file = documentService.getStoredFile(documentId);
        Resource resource = new FileSystemResource(file.path());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.filename())
                        .build()
                        .toString())
                .body(resource);
    }
}
