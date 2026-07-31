package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QuestionIntentAnalyzerTest {
    @Test
    void sectionIntentTakesPriorityInsideSummaryRequest() {
        QuestionIntentAnalyzer.QueryIntent intent = QuestionIntentAnalyzer.analyze("tổng hợp từ vựng");

        assertEquals(QuestionIntentAnalyzer.Section.VOCABULARY, intent.section());
        assertTrue(intent.summary());
        assertTrue(intent.exhaustive());
    }

    @Test
    void normalizesCommonVietnameseTypos() {
        QuestionIntentAnalyzer.QueryIntent intent = QuestionIntentAnalyzer.analyze("từ vựng Nhật có nhĩa");

        assertEquals(QuestionIntentAnalyzer.Section.VOCABULARY, intent.section());
        assertTrue(intent.asksMeaning());
    }

    @Test
    void repairsUtf8TextThatWasDecodedAsLatin1() {
        String original = "Vật chất được định nghĩa như thế nào?";
        String mojibake = new String(
                original.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1
        );

        assertEquals(original, QuestionIntentAnalyzer.repairUtf8Mojibake(mojibake));
        assertEquals(
                QuestionIntentAnalyzer.QuestionForm.DEFINITION,
                QuestionIntentAnalyzer.analyze(mojibake).form()
        );
    }

    @Test
    void recognizesJapaneseSectionLabels() {
        assertEquals(
                QuestionIntentAnalyzer.Section.GRAMMAR,
                QuestionIntentAnalyzer.analyze("文法をまとめて").section()
        );
    }

    @Test
    void recognizesJapaneseSummaryAndMeaningIntents() {
        assertTrue(QuestionIntentAnalyzer.analyze("この資料をまとめてください").summary());
        assertTrue(QuestionIntentAnalyzer.analyze("かいさつの意味は何ですか").asksMeaning());
    }

    @Test
    void recognizesGeneralQuestionFormsAcrossSubjects() {
        assertEquals(QuestionIntentAnalyzer.QuestionForm.COMPARISON,
                QuestionIntentAnalyzer.analyze("So sánh kiểm thử hộp đen và hộp trắng").form());
        assertEquals(QuestionIntentAnalyzer.QuestionForm.REASONING,
                QuestionIntentAnalyzer.analyze("Vì sao thế giới quan có vai trò quan trọng?").form());
        assertEquals(QuestionIntentAnalyzer.QuestionForm.PROCEDURE,
                QuestionIntentAnalyzer.analyze("Quy trình kiểm thử gồm các bước nào?").form());
        assertEquals(QuestionIntentAnalyzer.QuestionForm.DEFINITION,
                QuestionIntentAnalyzer.analyze("Phép biện chứng được hiểu là gì?").form());
        assertEquals(QuestionIntentAnalyzer.QuestionForm.DEFINITION,
                QuestionIntentAnalyzer.analyze(
                        "Theo V.I. Lênin, vật chất được định nghĩa như thế nào?").form());
    }

    @Test
    void selectsAdaptiveAnswerDepth() {
        assertEquals(QuestionIntentAnalyzer.AnswerDepth.SHORT,
                QuestionIntentAnalyzer.analyze("Triet hoc la gi?").answerDepth());
        assertEquals(QuestionIntentAnalyzer.AnswerDepth.STANDARD,
                QuestionIntentAnalyzer.analyze("Tai sao vat chat quyet dinh y thuc?").answerDepth());
        assertEquals(QuestionIntentAnalyzer.AnswerDepth.DEEP,
                QuestionIntentAnalyzer.analyze(
                        "Mot so hoc thuyet tieu bieu cua triet hoc Trung Hoa co trung dai").answerDepth());
        QuestionIntentAnalyzer.QueryIntent compoundDefinition =
                QuestionIntentAnalyzer.analyze(
                        "Vấn đề cơ bản của hệ thống là gì và gồm những phần nào?");
        assertEquals(QuestionIntentAnalyzer.QuestionForm.DEFINITION, compoundDefinition.form());
        assertEquals(QuestionIntentAnalyzer.AnswerDepth.DEEP, compoundDefinition.answerDepth());
    }

    @Test
    void explicitDepthInstructionOverridesAutomaticChoice() {
        assertEquals(QuestionIntentAnalyzer.AnswerDepth.SHORT,
                QuestionIntentAnalyzer.analyze(
                        "Tra loi ngan gon: tai sao vat chat quyet dinh y thuc?").answerDepth());
        assertEquals(QuestionIntentAnalyzer.AnswerDepth.DEEP,
                QuestionIntentAnalyzer.analyze("Giai thich chi tiet: triet hoc la gi?").answerDepth());
    }
}
