package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.courseqa.model.dto.RagDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionScopeGuardTest {
    private final QuestionScopeGuard guard = new QuestionScopeGuard();

    @Test
    void refusesLiveSportsQuestionEvenWhenItEndsWithDefinitionPhrase() {
        QuestionScopeGuard.GuardDecision decision = guard.preCheck("tỉ số của arg và tbn là gì ?");

        assertEquals(QuestionScopeGuard.GuardAction.REFUSE, decision.action());
    }

    @Test
    void clarifiesAmbiguousQuestionWithoutClearSubject() {
        QuestionScopeGuard.GuardDecision decision = guard.preCheck("cái đó là gì?");

        assertEquals(QuestionScopeGuard.GuardAction.CLARIFY, decision.action());
    }

    @Test
    void allowsGroundedLearningQuestionWhenContextContainsMainTerms() {
        RagDto.RetrievalResponse retrieval = retrieval("""
                Khái quát lại, có thể hiểu: Triết học là hệ thống tri thức lý luận chung nhất
                của con người về thế giới; về vị trí, vai trò của con người trong thế giới ấy.
                """);

        QuestionScopeGuard.GuardDecision decision = guard.postRetrievalCheck(
                "Triết học được giáo trình khái quát là gì?", retrieval);

        assertTrue(decision.allowed());
    }

    @Test
    void allowsCjkQuestionWhenRetrievalAlreadyFoundContext() {
        RagDto.RetrievalResponse retrieval = retrieval("""
                これは日本語の教材です。人工知能は人間の知的活動を計算機で実現する技術分野です。
                """);

        QuestionScopeGuard.GuardDecision decision = guard.postRetrievalCheck(
                "人工知能とは何ですか？", retrieval);

        assertTrue(decision.allowed());
    }

    @Test
    void refusesWhenOnlyOneRiskyShortTokenIsGrounded() {
        RagDto.RetrievalResponse retrieval = retrieval("""
                Trong tài liệu có một đoạn nhắc đến arg như average number trong ví dụ thống kê.
                """);

        QuestionScopeGuard.GuardDecision decision = guard.postRetrievalCheck(
                "tỉ số của arg và tbn là gì ?", retrieval);

        assertEquals(QuestionScopeGuard.GuardAction.REFUSE, decision.action());
    }

    @Test
    void refusesOnlyUniversalOutOfScopeRequestsBeforeRetrieval() {
        List<String> questions = List.of(
                "tỉ số của arg và tbn là gì ?",
                "Dự báo thời tiết Hà Nội ngày mai thế nào?",
                "Giá Bitcoin hôm nay tăng hay giảm?",
                "Lịch chiếu phim tối nay ở CGV có gì?",
                "Kết quả xổ số miền Nam hôm nay là gì?",
                "Tổng thống Mỹ hiện nay là ai?",
                "Mật khẩu admin của hệ thống là gì?",
                "Triết học FC đá với Lênin FC tỉ số bao nhiêu?",
                "Cho tôi mã giảm giá Shopee mới nhất",
                "Hôm nay vàng SJC bao nhiêu một lượng?",
                "Đặt lịch nhắc tôi 8 giờ tối mai học bài"
        );

        for (String question : questions) {
            QuestionScopeGuard.GuardDecision decision = guard.preCheck(question);

            assertEquals(QuestionScopeGuard.GuardAction.REFUSE, decision.action(), question);
        }
    }

    @Test
    void allowsDomainFlexibleStudyQuestionsToBeDecidedByRetrievalOrTrainedData() {
        List<String> questions = List.of(
                "Viết code Java sắp xếp mảng giúp tôi",
                "Dịch câu này sang tiếng Anh: tôi đang học bài",
                "Tư vấn mua laptop dưới 15 triệu nên chọn máy nào?",
                "Lập thực đơn giảm cân 7 ngày cho tôi",
                "Kể một câu chuyện cười ngắn đi",
                "Tạo cho tôi một logo quán cà phê",
                "Giải phương trình x^2 - 5x + 6 = 0",
                "Chủ nghĩa duy vật biện chứng có phải tên bài hát không?",
                "Tôi bị đau đầu nên uống thuốc gì?"
        );

        for (String question : questions) {
            QuestionScopeGuard.GuardDecision decision = guard.preCheck(question);

            assertTrue(decision.allowed(), question);
        }
    }

    @Test
    void refusesDomainFlexibleQuestionWhenRetrievedContextDoesNotGroundIt() {
        RagDto.RetrievalResponse retrieval = retrieval("""
                Triết học trong tiếng Hy Lạp cổ là philosophia, nghĩa là yêu mến sự thông thái.
                Chủ nghĩa duy tâm cho rằng ý thức, tinh thần là cái có trước.
                """);

        QuestionScopeGuard.GuardDecision decision = guard.postRetrievalCheck(
                "Dịch câu này sang tiếng Anh: tôi đang học bài", retrieval);

        assertEquals(QuestionScopeGuard.GuardAction.REFUSE, decision.action());
    }

    private RagDto.RetrievalResponse retrieval(String content) {
        RagDto.RetrievedChunk chunk = new RagDto.RetrievedChunk();
        chunk.content = content;
        RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
        response.answerable = true;
        response.results = List.of(chunk);
        return response;
    }

    @Test
    void allowsGenericSummaryWhenRetrievalAlreadyContainsSelectedDocumentChunks() {
        RagDto.RetrievalResponse retrieval = new RagDto.RetrievalResponse();
        retrieval.answerable = true;
        RagDto.RetrievedChunk chunk = new RagDto.RetrievedChunk();
        chunk.content = "第７課 友達の家で";
        retrieval.results = List.of(chunk);

        assertTrue(guard.postRetrievalCheck("tóm tắt nội dung", retrieval).allowed());
    }

    @Test
    void allowsVocabularySectionCommandAfterGroundedRetrieval() {
        RagDto.RetrievalResponse retrieval = retrieval("ことば Từ vựng かいさつ こうばん バスてい");

        assertTrue(guard.postRetrievalCheck("tổng hợp tất cả từ vựng", retrieval).allowed());
    }

    @Test
    void refusesMeaningQuestionWhenDocumentOnlyContainsTheTerm() {
        RagDto.RetrievalResponse retrieval = retrieval("かいさつ");

        QuestionScopeGuard.GuardDecision decision = guard.postRetrievalCheck(
                "かいさつ có nghĩa là gì?", retrieval);

        assertEquals(QuestionScopeGuard.GuardAction.REFUSE, decision.action());
    }

    @Test
    void allowsMeaningQuestionWhenDocumentContainsAnExplanation() {
        RagDto.RetrievalResponse retrieval = retrieval("かいさつ: cổng soát vé");

        assertTrue(guard.postRetrievalCheck("かいさつ có nghĩa là gì?", retrieval).allowed());
    }

    @Test
    void allowsGenericJapaneseSummaryForAlreadySelectedDocument() {
        RagDto.RetrievalResponse retrieval = retrieval("第７課 友達の家で");

        assertTrue(guard.postRetrievalCheck("この資料をまとめてください", retrieval).allowed());
    }

    @Test
    void allowsAParaphrasedQuestionWithStrongSemanticEvidence() {
        RagDto.RetrievalResponse retrieval = retrieval(
                "Thế giới quan định hướng mục đích sống và cách thức hoạt động của con người.");
        retrieval.results.get(0).similarityScore = 0.72;

        assertTrue(guard.postRetrievalCheck(
                "Điều gì định hướng cách con người lựa chọn hành động?", retrieval).allowed());
    }

    @Test
    void refusesAParaphrasedQuestionWhenSemanticEvidenceIsWeak() {
        RagDto.RetrievalResponse retrieval = retrieval("Nội dung không liên quan.");
        retrieval.results.get(0).similarityScore = 0.31;

        assertEquals(QuestionScopeGuard.GuardAction.REFUSE,
                guard.postRetrievalCheck("Cấu tạo của động cơ phản lực?", retrieval).action());
    }

    @Test
    void refusesFragmentedKeywordMatchesWithoutARealQueryPhrase() {
        RagDto.RetrievalResponse retrieval = retrieval(
                "Quan điểm máy móc giải thích vận động cơ học; lực lượng phản động xuất hiện trong lịch sử.");
        retrieval.results.get(0).similarityScore = 0.596;

        assertEquals(QuestionScopeGuard.GuardAction.REFUSE,
                guard.postRetrievalCheck("Cấu tạo của động cơ phản lực gồm những gì?", retrieval).action());
    }
}
