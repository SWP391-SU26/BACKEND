-- Additive migration for Flow 2. Run after VietnameseCourseQA20DB.sql.
CREATE TABLE semester_workspaces (
  semester_workspace_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
  semester_code NVARCHAR(50) NOT NULL UNIQUE,
  semester_name NVARCHAR(255) NOT NULL,
  start_date DATE NULL, end_date DATE NULL,
  status NVARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_by UNIQUEIDENTIFIER NULL,
  created_at DATETIME2 NOT NULL DEFAULT GETDATE(), updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
  CONSTRAINT chk_semester_status CHECK(status IN ('DRAFT','ACTIVE','ARCHIVED')),
  CONSTRAINT fk_semester_creator FOREIGN KEY(created_by) REFERENCES users(user_id) ON DELETE SET NULL
);
ALTER TABLE courses ADD semester_workspace_id UNIQUEIDENTIFIER NULL, status NVARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE courses ADD CONSTRAINT fk_courses_semester FOREIGN KEY(semester_workspace_id) REFERENCES semester_workspaces(semester_workspace_id);
CREATE TABLE course_memberships (
  course_membership_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(), course_id UNIQUEIDENTIFIER NOT NULL,
  user_id UNIQUEIDENTIFIER NOT NULL, membership_role NVARCHAR(20) NOT NULL DEFAULT 'STUDENT',
  status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE', assigned_by UNIQUEIDENTIFIER NULL,
  assigned_at DATETIME2 NOT NULL DEFAULT GETDATE(),
  CONSTRAINT uq_course_member UNIQUE(course_id,user_id),
  CONSTRAINT fk_membership_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
  CONSTRAINT fk_membership_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
CREATE TABLE document_chapter_suggestions (
  suggestion_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(), document_id UNIQUEIDENTIFIER NOT NULL,
  suggested_title NVARCHAR(255) NOT NULL, page_start INT NOT NULL, page_end INT NOT NULL,
  confidence FLOAT NULL, status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
  CONSTRAINT fk_suggestion_document FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE CASCADE
);
CREATE TABLE document_chapter_ranges (
  document_chapter_range_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(), document_id UNIQUEIDENTIFIER NOT NULL,
  chapter_id UNIQUEIDENTIFIER NOT NULL, page_start INT NOT NULL, page_end INT NOT NULL,
  -- SQL Server rejects two cascading paths from course_documents/chapters. The service removes ranges explicitly.
  CONSTRAINT fk_range_document FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE NO ACTION,
  CONSTRAINT fk_range_chapter FOREIGN KEY(chapter_id) REFERENCES chapters(chapter_id) ON DELETE NO ACTION
);
-- Backfill existing records before making semester_workspace_id mandatory in a later release.
