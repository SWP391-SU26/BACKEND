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
    void refusesWhenOnlyOneRiskyShortTokenIsGrounded() {
        RagDto.RetrievalResponse retrieval = retrieval("""
                Trong tài liệu có một đoạn nhắc đến arg như average number trong ví dụ thống kê.
                """);

        QuestionScopeGuard.GuardDecision decision = guard.postRetrievalCheck(
                "tỉ số của arg và tbn là gì ?", retrieval);

        assertEquals(QuestionScopeGuard.GuardAction.REFUSE, decision.action());
    }

    @Test
    void refusesBroadOutOfScopeEndUserQuestionsBeforeRetrieval() {
        List<String> questions = List.of(
                "tỉ số của arg và tbn là gì ?",
                "Dự báo thời tiết Hà Nội ngày mai thế nào?",
                "Giá Bitcoin hôm nay tăng hay giảm?",
                "Viết code Java sắp xếp mảng giúp tôi",
                "Dịch câu này sang tiếng Anh: tôi đang học bài",
                "Lịch chiếu phim tối nay ở CGV có gì?",
                "Kết quả xổ số miền Nam hôm nay là gì?",
                "Tổng thống Mỹ hiện nay là ai?",
                "Tư vấn mua laptop dưới 15 triệu nên chọn máy nào?",
                "Lập thực đơn giảm cân 7 ngày cho tôi",
                "Mật khẩu admin của hệ thống là gì?",
                "Kể một câu chuyện cười ngắn đi",
                "Tạo cho tôi một logo quán cà phê",
                "Giải phương trình x^2 - 5x + 6 = 0",
                "Triết học FC đá với Lênin FC tỉ số bao nhiêu?",
                "Chủ nghĩa duy vật biện chứng có phải tên bài hát không?",
                "Cho tôi mã giảm giá Shopee mới nhất",
                "Hôm nay vàng SJC bao nhiêu một lượng?",
                "Tôi bị đau đầu nên uống thuốc gì?",
                "Đặt lịch nhắc tôi 8 giờ tối mai học bài"
        );

        for (String question : questions) {
            QuestionScopeGuard.GuardDecision decision = guard.preCheck(question);

            assertEquals(QuestionScopeGuard.GuardAction.REFUSE, decision.action(), question);
        }
    }

    private RagDto.RetrievalResponse retrieval(String content) {
        RagDto.RetrievedChunk chunk = new RagDto.RetrievedChunk();
        chunk.content = content;
        RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
        response.answerable = true;
        response.results = List.of(chunk);
        return response;
    }
}
