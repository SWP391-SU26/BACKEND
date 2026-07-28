package com.courseqa.model.dto;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

public class LearningScopeDto {
    public static class SemesterScope {
        public UUID semesterId;
        public String semesterCode;
        public String semesterName;
        public String status;
        public UUID createdBy;
        public String creatorName;
        public LocalDateTime createdAt;
        public List<CourseScope> courses = new ArrayList<>();
    }

    public static class CourseScope {
        public UUID courseId;
        public String courseCode;
        public String courseName;
        public String status;
        public UUID createdBy;
        public String creatorName;
        public LocalDateTime createdAt;
        public UUID workspaceId;
        public int documentCount;
        public int processedDocumentCount;
    }

    public static class CourseMaterials {
        public UUID courseId;
        public List<ChapterMaterials> chapters = new ArrayList<>();
        public List<Material> unclassifiedMaterials = new ArrayList<>();
    }

    public static class ChapterMaterials {
        public UUID chapterId;
        public String chapterTitle;
        public Integer orderIndex;
        public List<Material> materials = new ArrayList<>();
    }

    public static class Material {
        public UUID documentId;
        public String documentTitle;
        public String originalFilename;
        public String processingStatus;
        public Integer pageStart;
        public Integer pageEnd;
        public Integer totalPages;
    }
}
