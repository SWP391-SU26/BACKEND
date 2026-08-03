package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.DocumentDto;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.DocumentProcessingService;
import com.courseqa.service.ResumableUploadService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resumable uploads: open a session, PUT byte ranges, and finish. If the network
 * drops the client asks for the session status and continues from
 * {@code nextOffset} instead of resending the whole file.
 */
@RestController
@RequestMapping("/api/uploads")
@CrossOrigin
public class ResumableUploadController {
    private final ResumableUploadService resumableUploadService;
    private final DocumentProcessingService documentProcessingService;

    public ResumableUploadController(
            ResumableUploadService resumableUploadService,
            DocumentProcessingService documentProcessingService) {
        this.resumableUploadService = resumableUploadService;
        this.documentProcessingService = documentProcessingService;
    }

    @PostMapping
    public ApiResponse<DocumentDto.ResumableUploadStatus> begin(
            @RequestBody DocumentDto.ResumableUploadRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(DocumentDto.ResumableUploadStatus.fromEntity(
                resumableUploadService.begin(request, principal.userId())));
    }

    @GetMapping("/{uploadId}")
    public ApiResponse<DocumentDto.ResumableUploadStatus> status(
            @PathVariable UUID uploadId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(DocumentDto.ResumableUploadStatus.fromEntity(
                resumableUploadService.status(uploadId, principal.userId())));
    }

    /** Appends the next range; {@code X-Upload-Offset} must equal {@code nextOffset}. */
    @PutMapping("/{uploadId}")
    public ApiResponse<DocumentDto.ResumableUploadStatus> append(
            @PathVariable UUID uploadId,
            @RequestHeader("X-Upload-Offset") long offset,
            HttpServletRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        try {
            return ApiResponse.ok(DocumentDto.ResumableUploadStatus.fromEntity(
                    resumableUploadService.append(uploadId, principal.userId(), offset, request.getInputStream())));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded range.");
        }
    }

    @PostMapping("/{uploadId}/complete")
    public ApiResponse<DocumentDto.DocumentResponse> complete(
            @PathVariable UUID uploadId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        DocumentDto.DocumentResponse response =
                resumableUploadService.complete(uploadId, principal.userId());
        documentProcessingService.enqueueUpload(response.documentId, principal.userId());
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{uploadId}")
    public ApiResponse<Void> abort(
            @PathVariable UUID uploadId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        resumableUploadService.abort(uploadId, principal.userId());
        return ApiResponse.ok(null);
    }
}
