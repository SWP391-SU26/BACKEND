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
        public String standalone_query;
        public List<ChatHistoryItem> history;
        public String answer_profile;
        public String answer_depth;
    }

    public static class ChatHistoryItem {
        public String role;
        public String content;
    }

    public static class RewriteQueryRequest {
        public String question;
        public List<ChatHistoryItem> history;
        public String intent;
        public Integer attempt;
        public List<String> evidence_hints;
    }

    public static class RewriteQueryResponse {
        public String standalone_query;
        public String language;
        public String intent;
        public Integer attempt;
        public String base_model;
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
        public String provider_used;
        public String base_model;
        public String adapter_version;
        public String embedding_model;
        public String generation_mode;
        public String dataset_version;
        public String prompt_version;
        public List<String> used_chunk_ids;
        public Long peak_vram_bytes;
        public String grounding_status;
        public String fallback_reason;
        public Double grounding_score;
        public Boolean repair_attempted;
        public Integer unsupported_sentence_count;
    }

    public static class GenerateBatchItem {
        public String request_id;
        public String question;
        public List<GenerateContext> contexts;
        public String standalone_query;
        public List<ChatHistoryItem> history;
        public String answer_profile;
        public String answer_depth;
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
        public String provider_used;
        public String base_model;
        public String adapter_version;
        public String embedding_model;
        public String generation_mode;
        public String dataset_version;
        public String prompt_version;
        public List<String> used_chunk_ids;
        public Long peak_vram_bytes;
        public String grounding_status;
        public String fallback_reason;
        public Double grounding_score;
        public Boolean repair_attempted;
        public Integer unsupported_sentence_count;
    }

    public static class GenerateBatchResponse {
        public List<GenerateBatchResult> items;
        public Integer batch_size;
        public Integer max_input_tokens;
        public Integer max_new_tokens;
        public Integer effective_batch_size;
        public Integer oom_fallback_count;
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
        public String provider_used;
        public String base_model;
        public String adapter_version;
        public String generation_mode;
        public String dataset_version;
        public String prompt_version;
        public Long peak_vram_bytes;
        public String verification_status;
        public Boolean quality_gate_passed;
    }

    public static class ChatFinetunedBatchItem {
        public String request_id;
        public String question;
        public List<String> document_filenames;
        public String answer_depth;
    }

    public static class ChatFinetunedBatchRequest {
        public List<ChatFinetunedBatchItem> items;
        public Boolean strict;
        public Boolean allow_unverified;
        public Boolean benchmark_mode;
    }

    public static class ChatFinetunedBatchResult {
        public String request_id;
        public String answer;
        public String error;
        public Boolean is_out_of_scope;
        public Boolean model_inference_executed;
        public Double scope_confidence;
        public String provider_used;
        public String base_model;
        public String adapter_version;
        public String generation_mode;
        public String dataset_version;
        public String prompt_version;
        public Long peak_vram_bytes;
        public String verification_status;
        public Boolean quality_gate_passed;
    }

    public static class ChatFinetunedBatchResponse {
        public List<ChatFinetunedBatchResult> items;
        public Integer batch_size;
        public Integer max_input_tokens;
        public Integer max_new_tokens;
        public Integer effective_batch_size;
        public Integer oom_fallback_count;
    }

    public static class OfficialRagasItem {
        public String request_id;
        public String question;
        public String response;
        public String reference;
        public List<String> contexts;
    }

    public static class OfficialRagasBatchRequest {
        public List<OfficialRagasItem> items;
    }

    public static class OfficialRagasResult {
        public String request_id;
        public Double faithfulness;
        public Double answer_relevancy;
        public Double context_precision;
        public Double context_recall;
        public String error;
        public String metric_standard;
        public String judge_model;
        public String embedding_model;
        public String prompt_version;
    }

    public static class OfficialRagasBatchResponse {
        public List<OfficialRagasResult> items;
        public String metric_standard;
        public String judge_model;
        public String evaluator_embedding;
        public String prompt_version;
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
