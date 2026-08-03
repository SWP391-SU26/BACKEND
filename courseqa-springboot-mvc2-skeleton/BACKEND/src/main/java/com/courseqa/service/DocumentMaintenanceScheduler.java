package com.courseqa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic housekeeping for the document pipeline. Without this, the cleanup
 * routines existed but nothing ever called them: abandoned upload parts stayed
 * on disk forever, and a document whose worker died could sit on "Processing"
 * indefinitely because reconciliation only ran when someone happened to read the
 * job.
 */
@Slf4j
@Component
public class DocumentMaintenanceScheduler {
    private final ResumableUploadService resumableUploadService;
    private final DocumentProcessingService documentProcessingService;

    public DocumentMaintenanceScheduler(
            ResumableUploadService resumableUploadService,
            DocumentProcessingService documentProcessingService) {
        this.resumableUploadService = resumableUploadService;
        this.documentProcessingService = documentProcessingService;
    }

    /** Deletes staged bytes for uploads the user never returned to finish. */
    @Scheduled(fixedDelayString = "${app.upload.purge-interval-ms:3600000}",
            initialDelayString = "${app.upload.purge-initial-delay-ms:120000}")
    public void purgeAbandonedUploads() {
        try {
            int purged = resumableUploadService.purgeAbandoned();
            if (purged > 0) {
                log.info("Cleared {} abandoned upload session(s).", purged);
            }
        } catch (RuntimeException exception) {
            log.warn("Abandoned upload cleanup failed; will retry on the next run.", exception);
        }
    }

    /** Fails documents left mid-processing by a crashed or restarted worker. */
    @Scheduled(fixedDelayString = "${app.processing.reconcile-interval-ms:600000}",
            initialDelayString = "${app.processing.reconcile-initial-delay-ms:60000}")
    public void reconcileStuckDocuments() {
        try {
            int reconciled = documentProcessingService.reconcileStuckDocuments();
            if (reconciled > 0) {
                log.info("Marked {} stalled document(s) as failed.", reconciled);
            }
        } catch (RuntimeException exception) {
            log.warn("Document reconciliation failed; will retry on the next run.", exception);
        }
    }
}
