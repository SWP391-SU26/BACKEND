package com.courseqa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises extraction against real PDF/DOCX/PPTX files rather than synthetic
 * page rows, so the PDF paragraph-gap heuristic, PDF font-size heading detection,
 * DOCX heading stack and PPTX table extraction are actually verified end to end.
 */
class DocumentExtractionTest {
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final DocumentPageRepository pageRepository = mock(DocumentPageRepository.class);
    private final CourseDocumentRepository documentRepository = mock(CourseDocumentRepository.class);

    @TempDir
    Path tempDir;

    @Test
    void pdfParagraphGapsBecomeSeparateBlocks() throws Exception {
        Path pdf = tempDir.resolve("prose.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.beginText();
                content.newLineAtOffset(60, 750);
                content.setLeading(15);
                content.showText("First paragraph line one.");
                content.newLine();
                content.showText("First paragraph line two.");
                // Large vertical jump: this is a new paragraph, not a new line.
                content.newLineAtOffset(0, -60);
                content.showText("Second paragraph starts here.");
                content.endText();
            }
            document.save(pdf.toFile());
        }

        List<ExtractedPageView> pages = extract(pdf, "PDF");
        assertThat(pages).hasSize(1);
        String text = pages.get(0).text();
        assertThat(text).contains("First paragraph line one.");
        assertThat(text).contains("Second paragraph starts here.");
        assertThat(text)
                .as("a wide vertical gap must produce a blank line so paragraphs stay separate")
                .containsPattern("(?s)First paragraph line two\\.\\s*\\n\\s*\\n\\s*Second paragraph");
    }

    @Test
    void pdfHeadingsAreDetectedFromFontSizeAndBecomeAHeadingPath() throws Exception {
        Path pdf = tempDir.resolve("headings.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.newLineAtOffset(60, 760);
                content.setLeading(22);
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                content.showText("Chuong 1 Tong quan");
                content.newLine();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                for (int i = 0; i < 12; i++) {
                    content.showText("Noi dung than bai viet o co chu binh thuong dong " + i + ".");
                    content.newLine();
                }
                content.endText();
            }
            document.save(pdf.toFile());
        }

        List<ExtractedPageView> pages = extract(pdf, "PDF");
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).headingPath())
                .as("the larger line must be recognised as a heading")
                .isEqualTo("Chuong 1 Tong quan");
    }

    @Test
    void docxHeadingsBuildAHierarchicalPathAndTablesKeepRows() throws Exception {
        Path docx = tempDir.resolve("course.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            addStyledParagraph(document, "Chuong 1", "Heading1");
            addStyledParagraph(document, "1.2 Khai niem", "Heading2");
            addStyledParagraph(document, "Noi dung cua muc con.", null);

            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Mon");
            table.getRow(0).getCell(1).setText("Diem");
            table.getRow(1).getCell(0).setText("Toan");
            table.getRow(1).getCell(1).setText("9");

            try (OutputStream out = Files.newOutputStream(docx)) {
                document.write(out);
            }
        }

        List<ExtractedPageView> pages = extract(docx, "DOCX");
        String headings = pages.stream().map(ExtractedPageView::headingPath).filter(h -> h != null).toList().toString();
        assertThat(headings)
                .as("nested Word headings must produce a hierarchical path")
                .contains("Chuong 1 › 1.2 Khai niem");

        String allText = pages.stream().map(ExtractedPageView::text).reduce("", (a, b) -> a + "\n" + b);
        assertThat(allText).contains("| Mon | Diem |");
        assertThat(allText).contains("| Toan | 9 |");
    }

    @Test
    void pptxTablesAreExtractedAndSlideTitleBecomesTheHeading() throws Exception {
        Path pptx = tempDir.resolve("deck.pptx");
        try (XMLSlideShow deck = new XMLSlideShow()) {
            XSLFSlide slide = deck.createSlide();
            XSLFTextBox title = slide.createTextBox();
            title.setText("Bai giang so 1");

            XSLFTable table = slide.createTable();
            XSLFTableRow header = table.addRow();
            header.addCell().setText("Tuan");
            header.addCell().setText("Chu de");
            XSLFTableRow row = table.addRow();
            row.addCell().setText("1");
            row.addCell().setText("Nhap mon");

            try (OutputStream out = Files.newOutputStream(pptx)) {
                deck.write(out);
            }
        }

        List<ExtractedPageView> pages = extract(pptx, "PPTX");
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).text())
                .as("PPTX table content used to be dropped entirely")
                .contains("| Tuan | Chu de |");
        assertThat(pages.get(0).text()).contains("| 1 | Nhap mon |");
        assertThat(pages.get(0).headingPath()).startsWith("Slide 1");
    }

    @Test
    void twoColumnPdfIsReadColumnByColumnNotAcross() throws Exception {
        Path pdf = tempDir.resolve("columns.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                writeColumn(content, 55, 760, "LEFT");
                writeColumn(content, 330, 760, "RIGHT");
            }
            document.save(pdf.toFile());
        }

        List<ExtractedPageView> pages = extract(pdf, "PDF");
        String text = pages.get(0).text();
        int lastLeft = text.lastIndexOf("LEFT line 7");
        int firstRight = text.indexOf("RIGHT line 0");
        assertThat(lastLeft).isGreaterThan(-1);
        assertThat(firstRight).isGreaterThan(-1);
        assertThat(lastLeft)
                .as("the whole left column must be read before the right column starts")
                .isLessThan(firstRight);
    }

    @Test
    void headingsAreFoundInAUniformFontPdfViaNumbering() throws Exception {
        // Everything is set at one size, so font-size detection alone finds nothing.
        Path pdf = tempDir.resolve("uniform.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.beginText();
                content.newLineAtOffset(60, 770);
                content.setLeading(16);
                content.showText("1. Tong quan ve triet hoc");
                content.newLine();
                for (int i = 0; i < 6; i++) {
                    content.showText("Doan van thuong o cung mot co chu dong so " + i + ".");
                    content.newLine();
                }
                content.showText("1.2 Doi tuong nghien cuu");
                content.newLine();
                for (int i = 0; i < 6; i++) {
                    content.showText("Noi dung tiep theo cua muc con dong " + i + ".");
                    content.newLine();
                }
                content.endText();
            }
            document.save(pdf.toFile());
        }

        List<ExtractedPageView> pages = extract(pdf, "PDF");
        assertThat(pages.get(0).headingPath())
                .as("numbering must be enough to recognise a heading without a size change")
                .isNotNull()
                .contains("1. Tong quan ve triet hoc");
    }

    @Test
    void repeatedLogoIsNotTurnedIntoText() throws Exception {
        // The same small image on every slide is branding, not content.
        byte[] logo = pngBytes(64, 64);
        Path pptx = tempDir.resolve("branded.pptx");
        try (XMLSlideShow deck = new XMLSlideShow()) {
            XSLFPictureData logoData = deck.addPicture(logo, PictureData.PictureType.PNG);
            for (int i = 1; i <= 5; i++) {
                XSLFSlide slide = deck.createSlide();
                XSLFTextBox text = slide.createTextBox();
                text.setText("Noi dung slide " + i);
                slide.createPicture(logoData);
            }
            try (OutputStream out = Files.newOutputStream(pptx)) {
                deck.write(out);
            }
        }

        List<ExtractedPageView> pages = extract(pptx, "PPTX");
        assertThat(pages).hasSize(5);
        String allText = pages.stream().map(ExtractedPageView::text).reduce("", (a, b) -> a + "\n" + b);
        assertThat(allText).contains("Noi dung slide 3");
        assertThat(allText)
                .as("image placeholders must never leak into the extracted text")
                .doesNotContain("IMG:");
        assertThat(allText).doesNotContain(String.valueOf((char) 1));
    }

    @Test
    void embeddedImagesLeaveNoPlaceholderWhenOcrIsNotConfigured() throws Exception {
        Path docx = tempDir.resolve("with-image.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Truoc anh minh hoa.");
            byte[] picture = pngBytes(400, 300);
            run.addPicture(new ByteArrayInputStream(picture), Document.PICTURE_TYPE_PNG,
                    "figure.png", Units.toEMU(400), Units.toEMU(300));
            addStyledParagraph(document, "Sau anh minh hoa.", null);
            try (OutputStream out = Files.newOutputStream(docx)) {
                document.write(out);
            }
        }

        List<ExtractedPageView> pages = extract(docx, "DOCX");
        String allText = pages.stream().map(ExtractedPageView::text).reduce("", (a, b) -> a + "\n" + b);
        assertThat(allText).contains("Truoc anh minh hoa.");
        assertThat(allText).contains("Sau anh minh hoa.");
        assertThat(allText)
                .as("without Tesseract the image simply contributes nothing, no marker left behind")
                .doesNotContain("IMG:");
    }

    /** Solid-colour PNG of the requested size, used as a stand-in figure/logo. */
    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // --- helpers -------------------------------------------------------------

    private void writeColumn(PDPageContentStream content, float x, float y, String label) throws IOException {
        content.beginText();
        content.newLineAtOffset(x, y);
        content.setLeading(16);
        for (int i = 0; i < 8; i++) {
            content.showText(label + " line " + i);
            content.newLine();
        }
        content.endText();
    }

    private void addStyledParagraph(XWPFDocument document, String text, String style) {
        XWPFParagraph paragraph = document.createParagraph();
        if (style != null) {
            paragraph.setStyle(style);
        }
        paragraph.createRun().setText(text);
    }

    /** Calls the private extractPages(Path, String) and mirrors the record fields. */
    @SuppressWarnings("unchecked")
    private List<ExtractedPageView> extract(Path file, String fileType) throws Exception {
        DocumentService service = newService();
        Method method = DocumentService.class.getDeclaredMethod("extractPages", Path.class, String.class);
        method.setAccessible(true);
        List<Object> raw = (List<Object>) method.invoke(service, file, fileType);
        return raw.stream().map(ExtractedPageView::from).toList();
    }

    private record ExtractedPageView(int pageNumber, String text, String headingPath) {
        static ExtractedPageView from(Object extractedPage) {
            try {
                Class<?> type = extractedPage.getClass();
                return new ExtractedPageView(
                        (int) type.getMethod("pageNumber").invoke(extractedPage),
                        (String) type.getMethod("text").invoke(extractedPage),
                        (String) type.getMethod("headingPath").invoke(extractedPage));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private DocumentService newService() {
        return new DocumentService(
                documentRepository, mock(CourseRepository.class), mock(ChapterRepository.class),
                mock(CourseWorkspaceRepository.class), pageRepository, chunkRepository,
                mock(UserRepository.class), mock(UserRoleRepository.class),
                mock(SemesterWorkspaceRepository.class), mock(DocumentChapterRangeRepository.class),
                mock(DocumentChapterSuggestionRepository.class), mock(JdbcTemplate.class),
                mock(PersonalWorkspaceService.class), mock(SubscriptionService.class), "uploads", "", "", "", "", "vie+eng", 60,
                new ChunkTokenCounter("", false), disabledSemantics(), 450, 55, 250, 40);
    }

    /** Sanity check that extraction output really flows through into chunks. */
    @Test
    void extractedDocxFlowsThroughIntoChunksWithHeadingPath() throws Exception {
        Path docx = tempDir.resolve("flow.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            addStyledParagraph(document, "Chuong 2", "Heading1");
            addStyledParagraph(document, "Noi dung chi tiet cua chuong hai.", null);
            try (OutputStream out = Files.newOutputStream(docx)) {
                document.write(out);
            }
        }
        List<ExtractedPageView> extracted = extract(docx, "DOCX");

        UUID documentId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setWorkspaceId(UUID.randomUUID());
        List<DocumentPage> pages = extracted.stream().map(view -> {
            DocumentPage page = new DocumentPage();
            page.setPageId(UUID.randomUUID());
            page.setPageNumber(view.pageNumber());
            page.setRawText(view.text());
            page.setCleanedText(view.text());
            page.setHeadingPath(view.headingPath());
            return page;
        }).toList();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(pageRepository.findByDocumentIdOrderByPageNumberAsc(documentId)).thenReturn(pages);
        when(chunkRepository.findMaxChunkVersion(documentId)).thenReturn(0);
        when(chunkRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        newService().reindexChunks(documentId);

        org.mockito.ArgumentCaptor<List<DocumentChunk>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(chunkRepository).saveAll(captor.capture());
        List<DocumentChunk> chunks = captor.getValue();

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getHeadingPath()).isEqualTo("Chuong 2");
        assertThat(chunks.get(0).getContent()).contains("Noi dung chi tiet cua chuong hai.");
    }

    /** Structural chunking only: semantic mode is opt-in and needs a live model. */
    private static SemanticBoundaryDetector disabledSemantics() {
        return new SemanticBoundaryDetector(mock(EmbeddingService.class), false, 0.62, 4000);
    }
}
