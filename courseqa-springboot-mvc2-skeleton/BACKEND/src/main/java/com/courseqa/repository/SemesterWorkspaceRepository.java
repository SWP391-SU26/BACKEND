package com.courseqa.repository;
import com.courseqa.model.entity.SemesterWorkspace; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface SemesterWorkspaceRepository extends JpaRepository<SemesterWorkspace, UUID> { List<SemesterWorkspace> findAllByOrderByCreatedAtDesc(); List<SemesterWorkspace> findByDeletedAtIsNullOrderByCreatedAtDesc(); List<SemesterWorkspace> findByDeletedAtIsNotNullOrderByDeletedAtDesc(); boolean existsBySemesterCode(String semesterCode); }
