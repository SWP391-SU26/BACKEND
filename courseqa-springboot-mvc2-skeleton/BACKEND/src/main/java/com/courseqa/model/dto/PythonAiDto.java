package com.courseqa.model.dto;

import java.util.List;
import java.util.Map;

public class PythonAiDto {

    public static class EmbedRequest {
        public List<String> texts;
    }

    public static class EmbedResponse {
        public String provider;
        public String model;
        public Integer dimension;
        public List<List<Double>> vectors;
    }

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
        public Boolean strict;
    }

    public static class GenerateContext {
        public String chunk_id;
        public String document_id;
        public String filename;
        public Integer page;
        public String content;
        public Double score;
    }

    // What Python sends back from POST /api/generate
    public static class GenerateResponse {
        public String answer;
        public Boolean is_out_of_scope;
        public List<Map<String, Object>> sources;
    }

    public static class GenerateBatchItem {
        public String request_id;
        public String question;
        public List<GenerateContext> contexts;
    }

    public static class GenerateBatchRequest {
        public List<GenerateBatchItem> items;
        public Boolean strict;
    }

    public static class GenerateBatchResult {
        public String request_id;
        public String answer;
        public Boolean is_out_of_scope;
        public List<Map<String, Object>> sources;
        public String error;
    }

    public static class GenerateBatchResponse {
        public List<GenerateBatchResult> items;
        public Integer batch_size;
        public Integer max_input_tokens;
        public Integer max_new_tokens;
    }

    public static class ChatFinetunedRequest {
        public String question;
        public Boolean strict;
        public List<String> document_filenames;
    }

    public static class ChatFinetunedResponse {
        public String answer;
        public Boolean is_out_of_scope;
        public Double scope_confidence;
        public Boolean model_ready;
        public String status_code;
    }

    public static class ChatFinetunedBatchItem {
        public String request_id;
        public String question;
        public List<String> document_filenames;
    }

    public static class ChatFinetunedBatchRequest {
        public List<ChatFinetunedBatchItem> items;
        public Boolean strict;
    }

    public static class ChatFinetunedBatchResult {
        public String request_id;
        public String answer;
        public String error;
        public Boolean is_out_of_scope;
        public Double scope_confidence;
    }

    public static class ChatFinetunedBatchResponse {
        public List<ChatFinetunedBatchResult> items;
        public Integer batch_size;
        public Integer max_input_tokens;
        public Integer max_new_tokens;
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
