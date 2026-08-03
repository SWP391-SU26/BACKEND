package com.courseqa.service;

import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.ProcessingJob;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.ProcessingJobRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the {@link ProcessingJob} lifecycle for document upload/reindex/retry and
 * submits the actual work to {@code documentIndexingTaskExecutor}, so processing
 * keeps running after the HTTP request/session that triggered it has ended
 * (Student navigating away, reloading, or logging out).
 */
@Slf4j
@Service
public class DocumentProcessingService {
    private static final Set<String> RUNNING_STATUSES = Set.of(
            "QUEUED", "RUNNING", "EXTRACTING", "OCR", "CHUNKING", "EMBEDDING");
    private static final int STALE_AFTER_MINUTES = 30;

    private final ProcessingJobRepository processingJobRepository;
    private final CourseDocumentRepository courseDocumentRepository;
    private final DocumentService documentService;
    private final DocumentEmbeddingIndexService documentEmbeddingIndexService;
    private final Executor documentIndexingTaskExecutor;

    public DocumentProcessingService(
            ProcessingJobRepository processingJobRepository,
            CourseDocumentRepository courseDocumentRepository,
            DocumentService documentService,
            DocumentEmbeddingIndexService documentEmbeddingIndexService,
            @Qualifier("documentIndexingTaskExecutor") Executor documentIndexingTaskExecutor
    ) {
        this.processingJobRepository = processingJobRepository;
        this.courseDocumentRepository = courseDocumentRepository;
        this.documentService = documentService;
        this.documentEmbeddingIndexService = documentEmbeddingIndexService;
        this.documentIndexingTaskExecutor = documentIndexingTaskExecutor;
    }

    @Transactional
    public ProcessingJob enqueueUpload(UUID documentId, UUID createdBy) {
        ProcessingJob job = createJob(documentId, "UPLOAD", createdBy);
        submitAfterCommit(() -> runUploadJob(job.getJobId(), documentId));
        return job;
    }

    @Transactional
    public ProcessingJob enqueueReindex(UUID documentId, UUID createdBy) {
        ProcessingJob job = createJob(documentId, "REINDEX", createdBy);
        submitAfterCommit(() -> runReindexJob(job.getJobId(), documentId));
        return job;
    }

    @Transactional
    public ProcessingJob enqueueRetry(UUID documentId, UUID createdBy) {
        CourseDocument document = courseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));
        ProcessingJob job = createJob(documentId, "RETRY", createdBy);
        boolean hasLocalFile = "LOCAL".equals(document.getStorageProvider());
        if (hasLocalFile) {
            submitAfterCommit(() -> runUploadJob(job.getJobId(), documentId));
        } else {
            submitAfterCommit(() -> runReindexJob(job.getJobId(), documentId));
        }
        return job;
    }

    /**
     * Hands the job to the executor only once the surrounding transaction has
     * committed. Submitting inside the transaction let the worker start before the
     * job row was visible, so its first status update silently found nothing and
     * the job stayed reported as QUEUED for its whole run.
     */
    private void submitAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            documentIndexingTaskExecutor.execute(task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentIndexingTaskExecutor.execute(task);
            }
        });
    }

    private ProcessingJob createJob(UUID documentId, String jobType, UUID createdBy) {
        LocalDateTime now = LocalDateTime.now();
        ProcessingJob job = new ProcessingJob();
        job.setDocumentId(documentId);
        job.setJobType(jobType);
        job.setStatus("QUEUED");
        job.setCreatedBy(createdBy);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return processingJobRepository.save(job);
    }

    private void runUploadJob(UUID jobId, UUID documentId) {
        markRunning(jobId, "EXTRACTING");
        try {
            documentService.runUploadPipeline(documentId, phase -> markStep(jobId, phase));
            markStep(jobId, "EMBEDDING");
            documentEmbeddingIndexService.prepareDocument(documentId, embeddingHeartbeat(jobId));
            markCompleted(jobId);
        } catch (RuntimeException exception) {
            markFailed(jobId, exception);
        }
    }

    private void runReindexJob(UUID jobId, UUID documentId) {
        markRunning(jobId, "CHUNKING");
        try {
            int newVersion = documentService.reindexChunks(documentId);
            markStep(jobId, "EMBEDDING");
            documentEmbeddingIndexService.prepareDocument(documentId, embeddingHeartbeat(jobId));
            // Only flip chat over to the new version once its embeddings are confirmed
            // complete (prepareDocument throws on partial/failed embedding).
            documentService.activateChunkVersion(documentId, newVersion);
            markCompleted(jobId);
        } catch (RuntimeException exception) {
            markFailed(jobId, exception);
        }
    }

    /**
     * Publishes embedding progress onto the job row. Besides driving a real
     * percentage in the UI, this refreshes {@code updated_at} so a long but healthy
     * embedding run is never mistaken for a stalled one by
     * {@link #reconcileIfStale}.
     */
    private EmbeddingService.ProgressListener embeddingHeartbeat(UUID jobId) {
        return (embedded, total) -> markStep(jobId, "EMBEDDING " + embedded + "/" + total);
    }

    @Transactional
    protected void markRunning(UUID jobId, String progressStep) {
        processingJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("RUNNING");
            job.setProgressStep(progressStep);
            job.setStartedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            processingJobRepository.save(job);
        });
    }

    @Transactional
    protected void markStep(UUID jobId, String progressStep) {
        processingJobRepository.findById(jobId).ifPresent(job -> {
            job.setProgressStep(progressStep);
            job.setUpdatedAt(LocalDateTime.now());
            processingJobRepository.save(job);
        });
    }

    @Transactional
    protected void markCompleted(UUID jobId) {
        processingJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("COMPLETED");
            job.setProgressStep("READY");
            job.setCompletedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            processingJobRepository.save(job);
        });
    }

    @Transactional
    protected void markFailed(UUID jobId, Exception exception) {
        log.error("Processing job {} failed.", jobId, exception);
        processingJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(failureStatus(job));
            job.setErrorMessage(exception.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            processingJobRepository.save(job);
        });
    }

    /**
     * {@code FAILED_<STEP>}, dropping any progress counter the heartbeat appended
     * so the status stays a stable, matchable value (not "FAILED_EMBEDDING 480/737").
     */
    private String failureStatus(ProcessingJob job) {
        String step = Optional.ofNullable(job.getProgressStep()).orElse("PROCESSING");
        int space = step.indexOf(' ');
        return "FAILED_" + (space > 0 ? step.substring(0, space) : step);
    }

    /**
     * Fails documents that have been mid-processing far too long. The job-based
     * reconciler only sees documents that have a job row, so anything predating the
     * job table - or whose job was lost - would otherwise show "Processing" in the
     * UI forever.
     */
    @Transactional
    public int reconcileStuckDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES);
        List<CourseDocument> stalled =
                courseDocumentRepository.findByIndexingStatusInAndUpdatedAtBefore(
                        List.copyOf(RUNNING_STATUSES), cutoff).stream()
                        .filter(document -> !hasLiveJob(document.getDocumentId(), cutoff))
                        .toList();
        for (CourseDocument document : stalled) {
            document.setProcessingStatus("FAILED");
            document.setIndexingStatus("FAILED");
            String reason = "Processing stopped reporting progress for over "
                    + STALE_AFTER_MINUTES + " minutes.";
            document.setIndexError(reason);
            document.setErrorMessage(reason);
            document.setUpdatedAt(LocalDateTime.now());
        }
        courseDocumentRepository.saveAll(stalled);
        return stalled.size();
    }

    /**
     * True while a worker is still reporting progress for this document. The
     * embedding heartbeat refreshes the job row, not the document row, so a long
     * but perfectly healthy indexing run looks stale from the document's side and
     * would otherwise be failed out from under itself.
     */
    private boolean hasLiveJob(UUID documentId, LocalDateTime cutoff) {
        return processingJobRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
                .filter(job -> RUNNING_STATUSES.contains(job.getStatus()))
                .map(ProcessingJob::getUpdatedAt)
                .filter(updatedAt -> updatedAt.isAfter(cutoff))
                .isPresent();
    }

    public ProcessingJob getLatestJob(UUID documentId) {
        return processingJobRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
                .map(this::reconcileIfStale)
                .orElse(null);
    }

    public List<ProcessingJob> listJobs() {
        return processingJobRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::reconcileIfStale)
                .toList();
    }

    public ProcessingJob getJob(UUID jobId) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processing job not found."));
        return reconcileIfStale(job);
    }

    /**
     * A job stuck in a running-ish status with no progress update for over
     * {@link #STALE_AFTER_MINUTES} minutes means the backend restarted or crashed
     * mid-job. Mark it failed so the document/UI don't stay "processing" forever,
     * mirroring {@code EvaluationService.reconcileStaleExperiment}.
     */
    @Transactional
    protected ProcessingJob reconcileIfStale(ProcessingJob job) {
        LocalDateTime updatedAt = job.getUpdatedAt();
        if (updatedAt == null || updatedAt.isAfter(LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES))) {
            return job;
        }
        if (!RUNNING_STATUSES.contains(job.getStatus())) {
            return job;
        }
        job.setStatus("FAILED_" + Optional.ofNullable(job.getProgressStep()).orElse("PROCESSING"));
        job.setErrorMessage("Processing was interrupted because the backend stopped reporting progress for over "
                + STALE_AFTER_MINUTES + " minutes.");
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        ProcessingJob saved = processingJobRepository.save(job);

        courseDocumentRepository.findById(job.getDocumentId()).ifPresent(document -> {
            if (Set.of("PROCESSING").contains(document.getProcessingStatus())
                    || RUNNING_STATUSES.contains(document.getIndexingStatus())) {
                document.setProcessingStatus("FAILED");
                document.setIndexingStatus("FAILED");
                document.setIndexError(saved.getErrorMessage());
                document.setErrorMessage(saved.getErrorMessage());
                document.setUpdatedAt(LocalDateTime.now());
                courseDocumentRepository.save(document);
            }
        });
        return saved;
    }
}
