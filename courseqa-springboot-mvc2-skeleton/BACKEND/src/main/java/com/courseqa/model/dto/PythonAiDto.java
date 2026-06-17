package com.courseqa.model.dto;

import java.util.List;
import java.util.Map;

public class PythonAiDto {

    // ── What we send to POST /api/chat ──
    public static class ChatRequest {
        public String question;
        public String session_id;  // optional — Python creates one if null
        public String subject;     // optional — filters by subject
    }

    // ── What Python sends back ──
    public static class ChatResponse {
        public String session_id;
        public String answer;
        public List<Map<String, Object>> sources;    // filename, page, score, preview, etc.
        public List<Map<String, Object>> retrieved;  // raw chunks with scores
    }
}