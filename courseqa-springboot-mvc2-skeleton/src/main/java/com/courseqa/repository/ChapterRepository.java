package com.courseqa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.courseqa.model.entity.Chapter;

// TODO: Extend JpaRepository after completing entity class.
// Example:
// public interface ChapterRepository extends JpaRepository<Chapter, UUID> {}

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
List<Chapter> findByCourseIdOrderByOrderIndexAsc(UUID courseId);
boolean existsByCourseIdAndChapterTitle(UUID courseId, String chapterTitle);
}
