package com.courseqa.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lightweight offline query understanding shared by retrieval and scope checks. */
public final class QuestionIntentAnalyzer {
    private static final Map<String, String> DOMAIN_TOKEN_CORRECTIONS = Map.ofEntries(
            Map.entry("nhia", "nghia"),
            Map.entry("nghiaa", "nghia"),
            Map.entry("vung", "vung"),
            Map.entry("vunng", "vung"),
            Map.entry("nguphap", "ngu phap"),
            Map.entry("tuvung", "tu vung")
    );
    private static final Set<String> FUZZY_DOMAIN_TOKENS = Set.of(
            "vung", "nghia", "ngu", "phap", "tom", "tat", "tong", "hop", "liet", "ke"
    );

    private QuestionIntentAnalyzer() { }

    public static QueryIntent analyze(String question) {
        String normalized = normalizeAndCorrect(question);
        Section section = detectSection(normalized, question == null ? "" : question);
        String original = question == null ? "" : question;
        boolean summary = containsAny(normalized, "tong hop", "tom tat", "summary", "summarize", "noi dung chinh")
                || containsAny(original, "まとめ", "要約", "概要");
        boolean exhaustive = containsAny(normalized,
                "tat ca", "toan bo", "liet ke", "danh sach", "tong hop", "day du", "trong file", "trong tai lieu")
                || containsAny(original, "すべて", "全部", "一覧");
        boolean asksMeaning = containsAny(normalized,
                "nghia", "giai nghia", "dich", "translate", "meaning")
                || containsAny(original, "意味", "とは", "どういう");
        QuestionForm form = detectQuestionForm(normalized, original, summary, exhaustive, asksMeaning);
        return new QueryIntent(normalized, section, summary, exhaustive, asksMeaning, form);
    }

    private static QuestionForm detectQuestionForm(
            String normalized,
            String original,
            boolean summary,
            boolean exhaustive,
            boolean asksMeaning
    ) {
        if (summary) return QuestionForm.SUMMARY;
        if (containsAny(normalized, "so sanh", "phan biet", "khac nhau", "compare", "versus", " vs ")) {
            return QuestionForm.COMPARISON;
        }
        if (containsAny(normalized, "tai sao", "vi sao", "nguyen nhan", "why", "vai tro", "y nghia")) {
            return QuestionForm.REASONING;
        }
        if (containsAny(normalized, "quy trinh", "cac buoc", "trinh tu", "thuc hien nhu the nao", "how to")) {
            return QuestionForm.PROCEDURE;
        }
        if (exhaustive || containsAny(normalized,
                "gom nhung", "bao gom", "nhung gi", "cac loai", "ke ten", "which", "what are")) {
            return QuestionForm.LIST;
        }
        if (asksMeaning || containsAny(normalized, "la gi", "dinh nghia", "khai niem", "duoc hieu", "what is")
                || containsAny(original, "何ですか")) {
            return QuestionForm.DEFINITION;
        }
        return QuestionForm.FACT;
    }

    public static String normalizeAndCorrect(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        List<String> corrected = new ArrayList<>();
        for (String token : withoutMarks.split("\\s+")) {
            if (token.isBlank()) continue;
            String replacement = DOMAIN_TOKEN_CORRECTIONS.get(token);
            if (replacement == null && token.length() >= 4) {
                replacement = FUZZY_DOMAIN_TOKENS.stream()
                        .filter(candidate -> Math.abs(candidate.length() - token.length()) <= 1)
                        .filter(candidate -> levenshtein(candidate, token) <= 1)
                        .findFirst()
                        .orElse(null);
            }
            corrected.add(replacement == null ? token : replacement);
        }
        return String.join(" ", corrected).replaceAll("\\s+", " ").trim();
    }

    private static Section detectSection(String normalized, String original) {
        if (containsAny(normalized, "tu vung", "vocabulary", "vocab")
                || original.contains("ことば") || original.contains("語彙")) {
            return Section.VOCABULARY;
        }
        if (containsAny(normalized, "ngu phap", "grammar")
                || original.contains("ぶんぽう") || original.contains("文法")) {
            return Section.GRAMMAR;
        }
        if (containsAny(normalized, "bai tap", "exercise", "challenge")
                || original.contains("チャレンジ") || original.contains("練習")) {
            return Section.EXERCISE;
        }
        if (containsAny(normalized, "mau cau", "vi du", "example") || original.contains("例文")) {
            return Section.EXAMPLE;
        }
        return Section.NONE;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            int[] current = new int[right.length() + 1];
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            previous = current;
        }
        return previous[right.length()];
    }

    public enum Section {
        NONE,
        VOCABULARY,
        GRAMMAR,
        EXAMPLE,
        EXERCISE
    }

    public enum QuestionForm {
        FACT,
        DEFINITION,
        LIST,
        COMPARISON,
        REASONING,
        PROCEDURE,
        SUMMARY
    }

    public record QueryIntent(
            String normalized,
            Section section,
            boolean summary,
            boolean exhaustive,
            boolean asksMeaning,
            QuestionForm form
    ) {
        public boolean hasSection() {
            return section != Section.NONE;
        }

        public boolean requiresExpandedContext() {
            return hasSection() || summary || exhaustive
                    || form == QuestionForm.LIST
                    || form == QuestionForm.COMPARISON
                    || form == QuestionForm.REASONING
                    || form == QuestionForm.PROCEDURE;
        }
    }
}
