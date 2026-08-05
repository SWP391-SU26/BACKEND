package com.courseqa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.DocumentPage;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.DocumentChapterRangeRepository;
import com.courseqa.repository.DocumentChapterSuggestionRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.DocumentPageRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Locks in the size/overlap invariants of the chunker. These directly cover the
 * defects found in review: overlap that carried an entire oversized paragraph
 * (making each chunk a superset of the previous one), markdown tables being
 * flattened into a single line, and chunks being unable to span a page break.
 */
class DocumentChunkingTest {
    private static final int CHUNK_SIZE = 450;
    private static final int CHUNK_OVERLAP = 55;
    /** Same counter the service uses, so assertions are in the service's own unit. */
    private static final ChunkTokenCounter TOKENS = new ChunkTokenCounter("", false);

    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final DocumentPageRepository pageRepository = mock(DocumentPageRepository.class);
    private final CourseDocumentRepository documentRepository = mock(CourseDocumentRepository.class);

    @Test
    void noChunkExceedsTheTokenBudget() {
        List<DocumentChunk> chunks = chunk(page(1, longParagraph(120), null));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(tokens(chunk.getContent()))
                        .as("chunk %d must stay within budget", chunk.getChunkIndex())
                        .isLessThanOrEqualTo(CHUNK_SIZE + CHUNK_OVERLAP));
    }

    @Test
    void aLongHeadingPathDoesNotPushChunksOverTheBudget() {
        String heading = "Triet hoc Mac - Lenin › Phan I Khai luoc ve triet hoc va lich su triet hoc"
                + " › Chuong 1 Triet hoc va vai tro cua no trong doi song xa hoi";
        List<DocumentChunk> chunks = chunk(page(1, longParagraph(120), heading));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getContent()).startsWith(heading);
            assertThat(tokens(chunk.getContent()))
                    .as("chunk %d must include the heading prefix inside its budget", chunk.getChunkIndex())
                    .isLessThanOrEqualTo(CHUNK_SIZE + CHUNK_OVERLAP);
        });
    }

    @Test
    void noChunkFullyContainsThePreviousChunk() {
        List<DocumentChunk> chunks = chunk(page(1, longParagraph(120), null));

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).getContent();
            String current = chunks.get(i).getContent();
            assertThat(current)
                    .as("chunk %d must not be a superset of chunk %d", i + 1, i)
                    .doesNotContain(previous);
        }
    }

    @Test
    void overlapBetweenConsecutiveChunksStaysWithinBudget() {
        List<DocumentChunk> chunks = chunk(page(1, longParagraph(120), null));

        for (int i = 1; i < chunks.size(); i++) {
            int overlapTokens = sharedPrefixTokens(
                    chunks.get(i - 1).getContent(), chunks.get(i).getContent());
            assertThat(overlapTokens)
                    .as("overlap carried into chunk %d", i + 1)
                    .isLessThanOrEqualTo(CHUNK_OVERLAP + 20);
        }
    }

    @Test
    void markdownTableKeepsItsRowStructure() {
        String table = "| Môn | Điểm |\n| --- | --- |\n| Toán | 9 |\n| Lý | 8 |";
        List<DocumentChunk> chunks = chunk(page(1, "Bảng điểm học kỳ.\n\n" + table, null));

        String content = chunks.get(0).getContent();
        assertThat(content).contains("| Môn | Điểm |");
        assertThat(content).contains("| Toán | 9 |");
        assertThat(content)
                .as("table rows must stay on separate lines")
                .contains("| Toán | 9 |\n| Lý | 8 |");
    }

    @Test
    void aTableRowLargerThanTheBudgetStillRespectsTheChunkLimit() {
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            cell.append("nội dung ô bảng rất dài phần ").append(i).append(' ');
        }
        String table = "| Mục | Diễn giải |\n| --- | --- |\n| A | " + cell.toString().trim() + " |";
        List<DocumentChunk> chunks = chunk(page(1, table, null));

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(tokens(chunk.getContent()))
                        .as("chunk %d holding an oversized table row", chunk.getChunkIndex())
                        .isLessThanOrEqualTo(CHUNK_SIZE + CHUNK_OVERLAP));
    }

    @Test
    void chunksSpanPageBoundariesAndRecordARealPageRange() {
        List<DocumentChunk> chunks = chunk(
                page(1, "Đoạn mở đầu rất ngắn trên trang một.", null),
                page(2, "Đoạn tiếp theo nằm trên trang hai.", null),
                page(3, "Đoạn cuối cùng nằm trên trang ba.", null));

        assertThat(chunks).hasSize(1);
        DocumentChunk only = chunks.get(0);
        assertThat(only.getPageStart()).isEqualTo(1);
        assertThat(only.getPageEnd())
                .as("a chunk covering three short pages must report the full range")
                .isEqualTo(3);
    }

    @Test
    void headingPathIsRecordedAndPrependedToContent() {
        List<DocumentChunk> chunks = chunk(
                page(1, "Nội dung của mục con.", "Chương 1 › 1.2 Khái niệm"));

        DocumentChunk chunk = chunks.get(0);
        assertThat(chunk.getHeadingPath()).isEqualTo("Chương 1 › 1.2 Khái niệm");
        assertThat(chunk.getContent()).startsWith("Chương 1 › 1.2 Khái niệm");
        assertThat(chunk.getContent()).contains("Nội dung của mục con.");
    }

    @Test
    void repeatedRunningHeadersAreStripped() {
        List<DocumentPage> pages = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            pages.add(page(i, "Giáo trình Triết học\nNội dung riêng của trang " + i + ".\nTrang " + i, null));
        }
        List<DocumentChunk> chunks = chunk(pages.toArray(new DocumentPage[0]));

        String allContent = String.join("\n", chunks.stream().map(DocumentChunk::getContent).toList());
        assertThat(allContent).contains("Nội dung riêng của trang 3.");
        assertThat(allContent)
                .as("running header repeated on every page must not survive into chunks")
                .doesNotContain("Giáo trình Triết học");
    }

    // --- helpers -------------------------------------------------------------

    private List<DocumentChunk> chunk(DocumentPage... pages) {
        UUID documentId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setWorkspaceId(UUID.randomUUID());

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(pageRepository.findByDocumentIdOrderByPageNumberAsc(documentId)).thenReturn(List.of(pages));
        when(chunkRepository.findMaxChunkVersion(documentId)).thenReturn(0);
        when(chunkRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        newService().reindexChunks(documentId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private DocumentService newService() {
        return new DocumentService(
                documentRepository,
                mock(CourseRepository.class),
                mock(ChapterRepository.class),
                mock(CourseWorkspaceRepository.class),
                pageRepository,
                chunkRepository,
                mock(UserRepository.class),
                mock(UserRoleRepository.class),
                mock(SemesterWorkspaceRepository.class),
                mock(DocumentChapterRangeRepository.class),
                mock(DocumentChapterSuggestionRepository.class),
                mock(JdbcTemplate.class),
                mock(PersonalWorkspaceService.class),
                mock(SubscriptionService.class),
                "uploads",
                "",
                "",
                "",
                "",
                "vie+eng", 60,
                TOKENS, disabledSemantics(), 450, 55, 250, 40);
    }

    private DocumentPage page(int number, String text, String headingPath) {
        DocumentPage page = new DocumentPage();
        page.setPageId(UUID.randomUUID());
        page.setPageNumber(number);
        page.setRawText(text);
        page.setCleanedText(text);
        page.setHeadingPath(headingPath);
        return page;
    }

    private String longParagraph(int sentences) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= sentences; i++) {
            builder.append("Đây là câu số ").append(i)
                    .append(" trong một đoạn văn dài dùng để kiểm tra thuật toán chia đoạn theo token. ");
        }
        return builder.toString().trim();
    }

    private int tokens(String text) {
        return TOKENS.count(text);
    }

    /** Tokens of the tail of {@code previous} that reappear at the head of {@code current}. */
    private int sharedPrefixTokens(String previous, String current) {
        String[] currentWords = current.split("\\s+");
        int longest = 0;
        for (int size = 1; size <= Math.min(currentWords.length, 200); size++) {
            String head = String.join(" ", java.util.Arrays.copyOfRange(currentWords, 0, size));
            if (previous.contains(head)) {
                longest = size;
            } else {
                break;
            }
        }
        if (longest == 0) {
            return 0;
        }
        return tokens(String.join(" ", java.util.Arrays.copyOfRange(currentWords, 0, longest)));
    }

    /** Structural chunking only: semantic mode is opt-in and needs a live model. */
    private static SemanticBoundaryDetector disabledSemantics() {
        return new SemanticBoundaryDetector(mock(EmbeddingService.class), false, 0.62, 4000);
    }
}
