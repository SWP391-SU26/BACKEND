package com.courseqa.service;

import com.courseqa.model.dto.RagDto;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QuestionScopeGuard {
    private static final double STRONG_SEMANTIC_EVIDENCE = 0.68;
    public static final String REFUSE_MESSAGE =
            "Mình chỉ trả lời dựa trên tài liệu môn học đã chọn. "
                    + "Câu hỏi này chưa thấy liên quan đến tài liệu, bạn hỏi lại về nội dung trong tài liệu nhé.";
    public static final String CLARIFY_MESSAGE =
            "Bạn nói rõ khái niệm hoặc nội dung nào trong tài liệu muốn hỏi nhé, "
                    + "mình sẽ tra trong tài liệu đã chọn để trả lời.";

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "how", "i", "in",
            "is", "it", "of", "on", "or", "that", "the", "this", "to", "was", "what", "when",
            "where", "which", "who", "why", "you",
            "anh", "ban", "bang", "bao", "bi", "cai", "cac", "cach", "cho", "co", "con",
            "cua", "da", "dang", "day", "de", "den", "di", "do", "duoc", "gi", "giup",
            "hay", "hoi", "khong", "la", "lai", "lam", "minh", "mot", "nay", "neu",
            "nhu", "nhung", "noi", "o", "phai", "ra", "rang", "sao", "thi",
            "theo", "toi", "trong", "tu", "ve", "va", "voi"
    );
    private static final Set<String> QUESTION_WORDS = Set.of(
            "gi", "la", "sao", "nao", "hay", "neu", "trinh", "bay", "giai", "thich",
            "dinh", "nghia", "liet", "ke", "tom", "tat", "so", "sanh", "phan", "tich",
            "vi", "du", "nguyen", "nhan", "y", "vai", "tro", "dac", "diem"
    );
    private static final Set<String> LEARNING_HINTS = Set.of(
            "bai", "chuong", "giao", "trinh", "hoc", "khai", "niem", "dinh", "nghia",
            "noi", "dung", "tai", "lieu", "mon", "phan", "tich", "tom", "tat", "liet",
            "ke", "vi", "du", "tri", "thuc", "ly", "luan"
    );
    private static final List<Pattern> AMBIGUOUS_PATTERNS = List.of(
            Pattern.compile("^(cai|do|no|nay|day)\\s*(la\\s*)?gi\\??$"),
            Pattern.compile("^(dung|sai)\\s*(khong|ko|k)\\??$"),
            Pattern.compile("^giai\\s*thich\\s*(di|them)?\\??$"),
            Pattern.compile("^noi\\s*(ro|ngan)?\\s*(hon|thoi)?\\??$"),
            Pattern.compile("^la\\s*gi\\??$")
    );
    private static final List<Pattern> HARD_OUT_OF_SCOPE_INTENT_PATTERNS = List.of(
            Pattern.compile("\\b(ti\\s*so|ty\\s*so|score|tran\\s*dau|da\\s*bong|bong\\s*da|world\\s*cup|euro|fc)\\b"),
            Pattern.compile("\\b(thoi\\s*tiet|du\\s*bao|nhiet\\s*do|troi\\s*(mua|nang)|mua\\s*(khong|ko|k))\\b"),
            Pattern.compile("\\b(gia\\s*(bitcoin|btc|vang|usd|do\\s*la|co\\s*phieu)|btc|bitcoin|coin|crypto|vang\\s*sjc|chung\\s*khoan|ty\\s*gia)\\b"),
            Pattern.compile("\\b(lich\\s*chieu\\s*phim|cgv|phim\\s*(toi\\s*nay|hom\\s*nay))\\b"),
            Pattern.compile("\\b(xo\\s*so|ket\\s*qua\\s*xo\\s*so|vietlott)\\b"),
            Pattern.compile("\\b(tong\\s*thong|thu\\s*tuong|chu\\s*tich\\s*nuoc|bo\\s*truong).*(hien\\s*nay|bay\\s*gio|hom\\s*nay|moi\\s*nhat)\\b"),
            Pattern.compile("\\b(ma\\s*giam\\s*gia|shopee|lazada|tiki).*(moi\\s*nhat|hom\\s*nay|bay\\s*gio)?\\b"),
            Pattern.compile("\\b(mat\\s*khau|password|tai\\s*khoan\\s*admin|admin\\s*password|secret|api\\s*key)\\b"),
            Pattern.compile("\\b(dat\\s*lich|nhac\\s*toi|remind|reminder|hen\\s*gio|bao\\s*thuc)\\b")
    );
    private static final List<Pattern> OUT_OF_SCOPE_INTENT_PATTERNS = List.of(
            Pattern.compile("\\b(ti\\s*so|ty\\s*so|score|tran\\s*dau|da\\s*bong|bong\\s*da|world\\s*cup|euro)\\b"),
            Pattern.compile("\\b(thoi\\s*tiet|mua|nang|nhiet\\s*do|du\\s*bao)\\b"),
            Pattern.compile("\\b(gia|btc|bitcoin|coin|crypto|vang|co\\s*phieu|chung\\s*khoan|usd|vnd)\\b"),
            Pattern.compile("\\b(hom\\s*nay|hien\\s*tai|bay\\s*gio|toi\\s*qua|hom\\s*qua|moi\\s*nhat|latest|current)\\b"),
            Pattern.compile("\\b(dich\\s*sang|translate|translation)\\b"),
            Pattern.compile("\\b(lich\\s*cua\\s*toi|deadline\\s*cua\\s*toi|tai\\s*khoan\\s*cua\\s*toi)\\b")
    );

    public GuardDecision preCheck(String question) {
        List<String> tokens = tokens(question);
        if (tokens.isEmpty()) {
            return GuardDecision.clarify(CLARIFY_MESSAGE);
        }
        String normalized = String.join(" ", tokens);
        if (isAmbiguous(normalized, tokens)) {
            return GuardDecision.clarify(CLARIFY_MESSAGE);
        }
        if (hasHardOutOfScopeIntent(normalized)) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }
        return GuardDecision.allow();
    }

    public GuardDecision postRetrievalCheck(String question, RagDto.RetrievalResponse retrieval) {
        if (retrieval == null || !Boolean.TRUE.equals(retrieval.answerable)
                || retrieval.results == null || retrieval.results.isEmpty()) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }

        // For an explicit document scope, retrieval has already constrained the
        // context to the selected document IDs. Generic commands do not contain
        // subject keywords that can overlap Japanese or other foreign-language
        // content, so the selected document itself is sufficient grounding.
        QuestionIntentAnalyzer.QueryIntent intent = QuestionIntentAnalyzer.analyze(question);
        if ((!intent.asksMeaning() && containsCjk(question))
                || isGenericDocumentSummary(question)
                || (intent.hasSection() && !intent.asksMeaning())) {
            return GuardDecision.allow();
        }

        List<String> keyTerms = keyTerms(question);
        if (keyTerms.isEmpty()) {
            return GuardDecision.clarify(CLARIFY_MESSAGE);
        }
        if (intent.asksMeaning() && !hasMeaningEvidence(keyTerms, retrieval.results)) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }
        if (intent.asksMeaning() && containsCjk(question)) {
            return GuardDecision.allow();
        }
        if (!intent.asksMeaning() && hasStrongSemanticEvidence(retrieval.results)) {
            return GuardDecision.allow();
        }
        if (keyTerms.size() >= 2 && !hasDistinctivePhraseEvidence(question, retrieval.results)) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }

        List<String> contextTokens = retrieval.results.stream()
                .limit(3)
                .map(chunk -> chunk.content == null ? "" : chunk.content)
                .flatMap(content -> tokens(content).stream())
                .toList();
        Set<String> contextSet = new HashSet<>(contextTokens);
        Set<String> matched = new LinkedHashSet<>();
        for (String term : keyTerms) {
            if (contextSet.contains(term)) {
                matched.add(term);
            }
        }

        if (requiresEntityPair(question, keyTerms) && matched.size() < 2) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }
        if (hasRiskyShortTerm(keyTerms) && matched.size() < Math.min(2, keyTerms.size())) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }
        if (keyTerms.size() >= 3 && matched.size() < Math.ceil(keyTerms.size() * 0.6)) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }
        if (keyTerms.size() == 2 && matched.size() < 2 && hasOutOfScopeIntent(String.join(" ", tokens(question)))) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }
        if (keyTerms.size() == 1 && matched.isEmpty()) {
            return GuardDecision.refuse(REFUSE_MESSAGE);
        }
        return GuardDecision.allow();
    }

    private boolean hasMeaningEvidence(List<String> queryTerms, List<RagDto.RetrievedChunk> results) {
        Set<String> querySet = new HashSet<>(queryTerms);
        return results.stream().limit(3).anyMatch(chunk -> tokens(chunk.content).stream()
                .filter(token -> !STOP_WORDS.contains(token) && !QUESTION_WORDS.contains(token))
                .anyMatch(token -> !querySet.contains(token)));
    }

    private boolean hasStrongSemanticEvidence(List<RagDto.RetrievedChunk> results) {
        return results.stream().limit(3)
                .map(chunk -> chunk.similarityScore)
                .filter(java.util.Objects::nonNull)
                .anyMatch(score -> score >= STRONG_SEMANTIC_EVIDENCE);
    }

    private boolean hasDistinctivePhraseEvidence(String question, List<RagDto.RetrievedChunk> results) {
        List<String> questionTokens = tokens(question);
        if (questionTokens.size() < 2) {
            return false;
        }
        Set<String> contextBigrams = new HashSet<>();
        Set<String> contextTrigrams = new HashSet<>();
        results.stream().limit(3).forEach(chunk -> {
            List<String> contentTokens = tokens(chunk.content);
            for (int index = 0; index + 2 <= contentTokens.size(); index++) {
                contextBigrams.add(String.join(" ", contentTokens.subList(index, index + 2)));
            }
            for (int index = 0; index + 3 <= contentTokens.size(); index++) {
                contextTrigrams.add(String.join(" ", contentTokens.subList(index, index + 3)));
            }
        });

        for (int index = 0; index + 3 <= questionTokens.size(); index++) {
            List<String> phraseTokens = questionTokens.subList(index, index + 3);
            boolean allGeneric = phraseTokens.stream()
                    .allMatch(token -> STOP_WORDS.contains(token) || QUESTION_WORDS.contains(token));
            if (!allGeneric && contextTrigrams.contains(String.join(" ", phraseTokens))) {
                return true;
            }
        }

        int matchedBigrams = 0;
        Set<String> counted = new HashSet<>();
        for (int index = 0; index + 2 <= questionTokens.size(); index++) {
            List<String> phraseTokens = questionTokens.subList(index, index + 2);
            boolean allGeneric = phraseTokens.stream()
                    .allMatch(token -> STOP_WORDS.contains(token) || QUESTION_WORDS.contains(token));
            String phrase = String.join(" ", phraseTokens);
            if (!allGeneric && contextBigrams.contains(phrase) && counted.add(phrase)) {
                matchedBigrams++;
            }
        }
        int requiredBigrams = questionTokens.size() >= 5 ? 2 : 1;
        return matchedBigrams >= requiredBigrams;
    }

  /*   List<String> keyTerms(String question) {


        List<String> values = new ArrayList<>();
        for (String token : tokens(question)) {
            if (STOP_WORDS.contains(token) || QUESTION_WORDS.contains(token)) {
                continue;
            }
            values.add(token);
        }
        return values.stream().distinct().toList();
    }
*/

List<String> keyTerms(String question) {
        List<String> values = new ArrayList<>();
        for (String token : tokens(question)) {
            if (STOP_WORDS.contains(token) || QUESTION_WORDS.contains(token)) {
                continue;
            }
            if (token.matches("\\d+")) {
                continue;
            }
            values.add(token);
        }
        return values.stream().distinct().toList();
    }
    
    private boolean isAmbiguous(String normalized, List<String> tokens) {
        if (AMBIGUOUS_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find())) {
            return true;
        }
        List<String> terms = tokens.stream()
                .filter(token -> !STOP_WORDS.contains(token) && !QUESTION_WORDS.contains(token))
                .toList();
        return tokens.size() <= 4 && terms.isEmpty();
    }

    private boolean hasOutOfScopeIntent(String normalized) {
        return OUT_OF_SCOPE_INTENT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    private boolean hasHardOutOfScopeIntent(String normalized) {
        return HARD_OUT_OF_SCOPE_INTENT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    private boolean hasLearningHint(List<String> tokens) {
        return tokens.stream().anyMatch(LEARNING_HINTS::contains);
    }

    private boolean requiresEntityPair(String question, List<String> keyTerms) {
        String normalized = String.join(" ", tokens(question));
        return keyTerms.size() >= 2 && (normalized.contains(" va ") || normalized.contains(" vs ")
                || normalized.contains(" voi ") || normalized.contains("so sanh"));
    }

    private boolean hasRiskyShortTerm(List<String> keyTerms) {
        return keyTerms.stream().anyMatch(term -> term.length() <= 3);
    }

    private boolean isGenericDocumentSummary(String question) {
        String normalized = String.join(" ", tokens(question));
        return normalized.matches("^(tom tat|tong hop)( noi dung| tai lieu)?$")
                || normalized.matches("^noi dung( chinh)?( cua tai lieu)?$")
                || normalized.matches("^(summary|summarize)( document| file)?$")
                || isGenericJapaneseSummary(question);
    }

    private boolean isGenericJapaneseSummary(String question) {
        String value = question == null ? "" : question;
        boolean summaryCommand = value.contains("まとめ") || value.contains("要約") || value.contains("概要");
        boolean documentReference = value.contains("資料") || value.contains("文書")
                || value.contains("ファイル") || value.contains("内容") || value.contains("これ")
                || value.contains("この");
        return summaryCommand && documentReference;
    }

    private boolean containsCjk(String value) {
        if (value == null) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HIRAGANA
                        || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.KATAKANA
                        || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
                        || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL);
    }

    private List<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                values.add(token);
            }
        }
        return values;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutMarks.toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public enum GuardAction {
        ALLOW,
        REFUSE,
        CLARIFY
    }

    public record GuardDecision(GuardAction action, String message) {
        public static GuardDecision allow() {
            return new GuardDecision(GuardAction.ALLOW, null);
        }

        public static GuardDecision refuse(String message) {
            return new GuardDecision(GuardAction.REFUSE, message);
        }

        public static GuardDecision clarify(String message) {
            return new GuardDecision(GuardAction.CLARIFY, message);
        }

        public boolean allowed() {
            return action == GuardAction.ALLOW;
        }
    }
}
