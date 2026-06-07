package com.courseqa.model.dto;

import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseWorkspace;
import java.time.LocalDateTime;
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

    public static class CourseResponse {
        public UUID courseId;
        public String courseCode;
        public String courseName;
        public String description;
        public Boolean isActive;
        public LocalDateTime createdAt;

        public static CourseResponse fromEntity(Course course) {
            CourseResponse response = new CourseResponse();
            response.courseId = course.getCourseId();
            response.courseCode = course.getCourseCode();
            response.courseName = course.getCourseName();
            response.description = course.getDescription();
            response.isActive = course.getIsActive();
            response.createdAt = course.getCreatedAt();
            return response;
        }
    }

    public static class WorkspaceResponse {
        public UUID workspaceId;
        public UUID courseId;
        public String workspaceTitle;
        public String description;
        public String visibility;
        public Boolean isActive;
        public LocalDateTime createdAt;

        public static WorkspaceResponse fromEntity(CourseWorkspace workspace) {
            WorkspaceResponse response = new WorkspaceResponse();
            response.workspaceId = workspace.getWorkspaceId();
            response.courseId = workspace.getCourseId();
            response.workspaceTitle = workspace.getWorkspaceTitle();
            response.description = workspace.getDescription();
            response.visibility = workspace.getVisibility();
            response.isActive = workspace.getIsActive();
            response.createdAt = workspace.getCreatedAt();
            return response;
        }
    }
}
