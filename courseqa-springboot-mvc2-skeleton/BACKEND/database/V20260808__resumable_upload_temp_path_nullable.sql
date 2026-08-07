-- ResumableUploadService.begin() must insert the row before it knows the
-- generated upload_id, so temp_path (which is derived from that id) cannot be
-- set until a second save() right after. The NOT NULL constraint added in
-- V20260804__resumable_uploads.sql blocked that first insert outright, so every
-- resumable upload (any personal file over 2 MB) failed with a 500 before a
-- single byte was received. App code fills the column in immediately after,
-- within the same request, so relaxing this to NULL is safe.
IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID('upload_sessions')
             AND name = 'temp_path'
             AND is_nullable = 0)
    ALTER TABLE upload_sessions ALTER COLUMN temp_path NVARCHAR(500) NULL;
