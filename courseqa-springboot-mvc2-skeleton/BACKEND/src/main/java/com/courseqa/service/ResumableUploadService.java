package com.courseqa.service;

import com.courseqa.model.dto.DocumentDto;
import com.courseqa.model.entity.UploadSession;
import com.courseqa.repository.UploadSessionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Chunked, resumable uploads. A client announces the total size, then appends
 * byte ranges; if the connection drops it asks where the server got to and
 * continues from there rather than resending the whole file. Completed sessions
 * hand the assembled file to the normal document pipeline.
 */
@Slf4j
@Service
public class ResumableUploadService {
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";
    private static final String ABORTED = "ABORTED";

    private final UploadSessionRepository uploadSessionRepository;
    private final DocumentService documentService;
    private final Path stagingRoot;
    private final long maxUploadBytes;
    private final int abandonedAfterHours;

    public ResumableUploadService(
            UploadSessionRepository uploadSessionRepository,
            DocumentService documentService,
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.upload.max-bytes:20971520}") long maxUploadBytes,
            @Value("${app.upload.abandoned-after-hours:24}") int abandonedAfterHours) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.documentService = documentService;
        this.stagingRoot = Path.of(uploadDir).toAbsolutePath().normalize().resolve("staging");
        this.maxUploadBytes = maxUploadBytes;
        this.abandonedAfterHours = abandonedAfterHours;
    }

    @Transactional
    public UploadSession begin(DocumentDto.ResumableUploadRequest request, UUID userId) {
        if (request == null || request.filename == null || request.filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename is required.");
        }
        if (request.totalBytes == null || request.totalBytes <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "totalBytes must be positive.");
        }
        if (request.totalBytes > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds the " + (maxUploadBytes / 1024 / 1024) + " MB limit.");
        }
        // Reject over-quota or unsupported files before the client spends time
        // transferring them; the same rules are re-checked at completion.
        if (request.courseId == null) {
            documentService.validatePersonalQuota(request.filename, request.totalBytes, userId);
        }

        try {
            Files.createDirectories(stagingRoot);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare staging area.");
        }

        LocalDateTime now = LocalDateTime.now();
        UploadSession session = new UploadSession();
        session.setUserId(userId);
        session.setWorkspaceId(request.workspaceId);
        session.setCourseId(request.courseId);
        session.setChapterId(request.chapterId);
        session.setOriginalFilename(request.filename);
        session.setMimeType(request.mimeType);
        session.setTotalBytes(request.totalBytes);
        session.setReceivedBytes(0L);
        session.setStatus(IN_PROGRESS);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        UploadSession saved = uploadSessionRepository.save(session);

        Path temp = stagingRoot.resolve(saved.getUploadId() + ".part").normalize();
        saved.setTempPath(temp.toString());
        return uploadSessionRepository.save(saved);
    }

    /** Where the client should resume from. */
    public UploadSession status(UUID uploadId, UUID userId) {
        return requireOwnedSession(uploadId, userId);
    }

    /**
     * Appends one range. The client must send bytes starting exactly at the
     * current offset; anything else is rejected so a retried or reordered request
     * can never corrupt the file.
     *
     * <p>The file write is not locked against a second concurrent request, and it
     * does not need to be: only one offset is ever accepted, so two racing writers
     * are writing the same range, and the loser is rejected by the {@code @Version}
     * check before its byte count is recorded. Because every append first cuts the
     * file back to the confirmed offset, whatever length the race left behind is
     * discarded by the next range rather than shifting the rest of the file.</p>
     */
    @Transactional
    public UploadSession append(UUID uploadId, UUID userId, long offset, InputStream data) {
        UploadSession session = requireOwnedSession(uploadId, userId);
        if (!IN_PROGRESS.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This upload is already finished.");
        }
        long expected = session.getReceivedBytes() == null ? 0 : session.getReceivedBytes();
        if (offset != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expected the next chunk at byte " + expected + ", not " + offset + ".");
        }

        Path temp = Path.of(session.getTempPath());
        long written = 0;
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // A range that died halfway leaves its bytes on disk while the
            // transaction rolls the offset back, so a plain append would write
            // them twice. Cutting the file back to the confirmed offset first
            // makes replaying a range harmless.
            if (channel.size() > expected) {
                channel.truncate(expected);
            }
            channel.position(expected);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = data.read(buffer)) != -1) {
                if (expected + written + read > session.getTotalBytes()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Upload would exceed the declared size.");
                }
                channel.write(java.nio.ByteBuffer.wrap(buffer, 0, read));
                written += read;
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the uploaded range.");
        }

        session.setReceivedBytes(expected + written);
        session.setUpdatedAt(LocalDateTime.now());
        try {
            return uploadSessionRepository.saveAndFlush(session);
        } catch (org.springframework.dao.OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another chunk for this upload was accepted first; ask for the status and resume.");
        }
    }

    /**
     * Finalises a fully received upload by handing the staged file to the regular
     * document pipeline, so resumable uploads end up identical to direct ones.
     */
    @Transactional
    public DocumentDto.DocumentResponse complete(UUID uploadId, UUID userId) {
        UploadSession session = requireOwnedSession(uploadId, userId);
        if (COMPLETED.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This upload was already completed.");
        }
        long received = session.getReceivedBytes() == null ? 0 : session.getReceivedBytes();
        if (received != session.getTotalBytes()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Upload is incomplete: " + received + " of " + session.getTotalBytes() + " bytes.");
        }

        DocumentDto.DocumentResponse response = documentService.registerStagedUpload(
                Path.of(session.getTempPath()),
                session.getOriginalFilename(),
                session.getMimeType(),
                session.getWorkspaceId(),
                session.getCourseId(),
                session.getChapterId(),
                userId);

        session.setStatus(COMPLETED);
        session.setDocumentId(response.documentId);
        session.setUpdatedAt(LocalDateTime.now());
        uploadSessionRepository.save(session);
        return response;
    }

    @Transactional
    public void abort(UUID uploadId, UUID userId) {
        UploadSession session = requireOwnedSession(uploadId, userId);
        deleteStaged(session);
        session.setStatus(ABORTED);
        session.setUpdatedAt(LocalDateTime.now());
        uploadSessionRepository.save(session);
    }

    /** Clears staged bytes for uploads the user never came back to finish. */
    @Transactional
    public int purgeAbandoned() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(abandonedAfterHours);
        List<UploadSession> stale = uploadSessionRepository.findByStatusAndUpdatedAtBefore(IN_PROGRESS, cutoff);
        for (UploadSession session : stale) {
            deleteStaged(session);
            session.setStatus(ABORTED);
            session.setUpdatedAt(LocalDateTime.now());
        }
        uploadSessionRepository.saveAll(stale);
        return stale.size();
    }

    private void deleteStaged(UploadSession session) {
        if (session.getTempPath() == null) {
            return;
        }
        try {
            Path temp = Path.of(session.getTempPath()).normalize();
            if (temp.startsWith(stagingRoot)) {
                Files.deleteIfExists(temp);
            }
        } catch (IOException exception) {
            log.warn("Could not remove staged upload {}.", session.getUploadId(), exception);
        }
    }

    private UploadSession requireOwnedSession(UUID uploadId, UUID userId) {
        UploadSession session = uploadSessionRepository.findById(uploadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found."));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This upload belongs to another user.");
        }
        return session;
    }
}
