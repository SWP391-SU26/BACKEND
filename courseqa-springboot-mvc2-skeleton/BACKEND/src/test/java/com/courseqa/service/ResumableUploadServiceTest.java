package com.courseqa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.DocumentDto;
import com.courseqa.model.entity.UploadSession;
import com.courseqa.repository.UploadSessionRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class ResumableUploadServiceTest {
    private final UploadSessionRepository sessions = mock(UploadSessionRepository.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final Map<UUID, UploadSession> store = new HashMap<>();

    @TempDir
    Path tempDir;

    private ResumableUploadService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(sessions.save(any(UploadSession.class))).thenAnswer(call -> {
            UploadSession session = call.getArgument(0);
            if (session.getUploadId() == null) {
                session.setUploadId(UUID.randomUUID());
            }
            store.put(session.getUploadId(), session);
            return session;
        });
        when(sessions.findById(any())).thenAnswer(call -> Optional.ofNullable(store.get(call.getArgument(0))));
        service = new ResumableUploadService(sessions, documents, tempDir.toString(), 20L * 1024 * 1024, 24);
    }

    @Test
    void anInterruptedUploadResumesFromTheLastConfirmedByte() throws Exception {
        UploadSession session = begin("giaotrinh.pdf", 12);

        service.append(session.getUploadId(), userId, 0, stream("Triet hoc "));
        UploadSession afterFirst = service.status(session.getUploadId(), userId);
        assertThat(afterFirst.getReceivedBytes()).isEqualTo(10);

        // The client reconnects, asks where to continue, and sends only the rest.
        service.append(session.getUploadId(), userId, afterFirst.getReceivedBytes(), stream("vn"));

        UploadSession done = service.status(session.getUploadId(), userId);
        assertThat(done.getReceivedBytes()).isEqualTo(12);
        assertThat(Files.readString(Path.of(done.getTempPath()))).isEqualTo("Triet hoc vn");
    }

    @Test
    void bytesLeftBehindByAConnectionThatDiedMidRangeAreNotCountedTwice() throws Exception {
        UploadSession session = begin("giaotrinh.pdf", 12);
        service.append(session.getUploadId(), userId, 0, stream("Triet hoc "));

        // Simulate a range that reached the disk but whose transaction rolled
        // back: the file is longer than the offset the client will resume from.
        Path temp = Path.of(service.status(session.getUploadId(), userId).getTempPath());
        Files.writeString(temp, "Triet hoc RAC");

        service.append(session.getUploadId(), userId, 10, stream("vn"));

        assertThat(Files.readString(temp))
                .as("the replayed range must overwrite the orphaned bytes, not follow them")
                .isEqualTo("Triet hoc vn");
        assertThat(service.status(session.getUploadId(), userId).getReceivedBytes()).isEqualTo(12);
    }

    @Test
    void aChunkSentAtTheWrongOffsetIsRejected() {
        UploadSession session = begin("giaotrinh.pdf", 20);
        service.append(session.getUploadId(), userId, 0, stream("abcde"));

        assertThatThrownBy(() -> service.append(session.getUploadId(), userId, 0, stream("abcde")))
                .as("a duplicated chunk must not corrupt the file")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Expected the next chunk at byte 5");
    }

    @Test
    void completingBeforeAllBytesArriveIsRefused() {
        UploadSession session = begin("giaotrinh.pdf", 100);
        service.append(session.getUploadId(), userId, 0, stream("mot phan"));

        assertThatThrownBy(() -> service.complete(session.getUploadId(), userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Upload is incomplete");
    }

    @Test
    void personalUploadsMustPassTheSameQuotaAsDirectUploads() {
        DocumentDto.ResumableUploadRequest request = new DocumentDto.ResumableUploadRequest();
        request.filename = "giaotrinh.pdf";
        request.totalBytes = 1024L;
        // courseId stays null: this is a personal upload, so the quota applies.
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "You can store at most 20 documents."))
                .when(documents).validatePersonalQuota(eq("giaotrinh.pdf"), eq(1024L), eq(userId));

        assertThatThrownBy(() -> service.begin(request, userId))
                .as("the resumable path must not be a way around the personal quota")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at most 20 documents");
    }

    @Test
    void anotherUserCannotTouchSomeoneElsesUpload() {
        UploadSession session = begin("giaotrinh.pdf", 10);

        assertThatThrownBy(() -> service.status(session.getUploadId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("another user");
    }

    @Test
    void sendingMoreThanDeclaredIsRejected() {
        UploadSession session = begin("giaotrinh.pdf", 5);

        assertThatThrownBy(() -> service.append(session.getUploadId(), userId, 0, stream("qua dai that")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceed the declared size");
    }

    @Test
    void completedUploadHandsTheFileToTheNormalPipeline() throws Exception {
        UploadSession session = begin("giaotrinh.pdf", 5);
        service.append(session.getUploadId(), userId, 0, stream("12345"));

        DocumentDto.DocumentResponse response = new DocumentDto.DocumentResponse();
        response.documentId = UUID.randomUUID();
        when(documents.registerStagedUpload(any(), eq("giaotrinh.pdf"), any(), any(), any(), any(), eq(userId)))
                .thenReturn(response);

        DocumentDto.DocumentResponse result = service.complete(session.getUploadId(), userId);

        assertThat(result.documentId).isEqualTo(response.documentId);
        assertThat(service.status(session.getUploadId(), userId).getStatus()).isEqualTo("COMPLETED");
    }

    private UploadSession begin(String filename, long totalBytes) {
        DocumentDto.ResumableUploadRequest request = new DocumentDto.ResumableUploadRequest();
        request.filename = filename;
        request.totalBytes = totalBytes;
        request.workspaceId = UUID.randomUUID();
        return service.begin(request, userId);
    }

    private ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
