SET NOCOUNT ON;

IF NOT EXISTS (
    SELECT 1
    FROM embedding_models
    WHERE model_name = N'paraphrase-multilingual-MiniLM-L12-v2-onnx'
)
BEGIN
    INSERT INTO embedding_models(
        model_name,
        provider,
        dimension,
        is_local,
        description,
        config_json,
        is_active,
        created_at
    )
    VALUES (
        N'paraphrase-multilingual-MiniLM-L12-v2-onnx',
        N'FastEmbed ONNX',
        384,
        1,
        N'Offline multilingual semantic embeddings for Vietnamese, Japanese and English document retrieval.',
        N'{"runtime":"onnxruntime","pooling":"mean","normalized":true}',
        1,
        GETDATE()
    );
END;
