package com.courseqa.model.dto;

import java.util.UUID;

// DTOs for course, chapter, workspace requests/responses.
// TODO: Add request/response DTO classes here.

public class CourseDto {
 public static class CreateCourseRequest {
        public String courseCode;
        public String courseName;
        public String description;
        public UUID createdBy;
    }

    public static class UpdateCourseRequest {
        public String courseName;
        public String description;
        public Boolean isActive;
    }

    public static class CreateChapterRequest {
        public String chapterTitle;
        public String description;
        public Integer orderIndex;
    }

    public static class UpdateChapterRequest {
        public String chapterTitle;
        public String description;
        public Integer orderIndex;
        public Boolean isActive;
    }

    public static class CreateWorkspaceRequest {
        public UUID ownerUserId;
        public String workspaceTitle;
        public String description;
        public String visibility;
    }

    public static class UpdateWorkspaceRequest {
        public String workspaceTitle;
        public String description;
        public String visibility;
        public Boolean isActive;
}
}
