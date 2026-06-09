package com.courseqa.repository;

import com.courseqa.model.entity.Chapter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    List<Chapter> findByCourseIdOrderByOrderIndexAsc(UUID courseId);

    List<Chapter> findByCourseIdAndIsActiveTrueOrderByOrderIndexAsc(UUID courseId);
}
