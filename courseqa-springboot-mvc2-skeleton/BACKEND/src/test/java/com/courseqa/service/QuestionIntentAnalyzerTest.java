package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }
}
