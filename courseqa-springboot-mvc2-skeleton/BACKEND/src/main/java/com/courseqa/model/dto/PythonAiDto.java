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

    // What we send to POST /api/generate
    public static class GenerateRequest {
        public String question;
        public List<GenerateContext> contexts;
    }

    public static class GenerateContext {
        public String chunk_id;
        public String document_id;
        public String filename;
        public Integer page;
        public String content;
    }

    // What Python sends back from POST /api/generate
    public static class GenerateResponse {
        public String answer;
        public Boolean is_out_of_scope;
        public List<Map<String, Object>> sources;
    }

    // What we send to POST /api/benchmarks/run
    public static class BenchmarkRequest {
        public String test_set_path;
        public String mode;
        public String generation_provider;
    }

    // What Python sends back from POST /api/benchmarks/run
    public static class BenchmarkResponse {
        public String run_id;
        public Map<String, Object> metrics;
        public List<Map<String, Object>> results;
        public String csv_path;
    }
}
