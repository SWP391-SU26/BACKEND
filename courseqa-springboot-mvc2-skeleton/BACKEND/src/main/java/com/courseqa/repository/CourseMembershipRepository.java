package com.courseqa.repository;
import com.courseqa.model.entity.CourseMembership; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface CourseMembershipRepository extends JpaRepository<CourseMembership, UUID> {
 List<CourseMembership> findByCourseIdAndStatus(UUID courseId,String status);
 List<CourseMembership> findByUserIdAndStatus(UUID userId,String status);
 Optional<CourseMembership> findByCourseIdAndUserId(UUID courseId,UUID userId);
 boolean existsByCourseIdAndUserIdAndStatus(UUID courseId,UUID userId,String status);
}
