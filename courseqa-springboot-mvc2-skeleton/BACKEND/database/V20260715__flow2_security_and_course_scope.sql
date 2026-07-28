-- Allow the same course code in different semesters, while keeping it unique inside one semester.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @constraintName SYSNAME;
SELECT TOP 1 @constraintName = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID('courses')
  AND kc.type = 'UQ'
  AND c.name = 'course_code';

IF @constraintName IS NOT NULL
    EXEC('ALTER TABLE courses DROP CONSTRAINT [' + @constraintName + ']');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('courses') AND name = 'uq_courses_semester_code')
    CREATE UNIQUE INDEX uq_courses_semester_code
        ON courses(semester_workspace_id, course_code)
        WHERE semester_workspace_id IS NOT NULL;
