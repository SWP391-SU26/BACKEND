package com.courseqa.model.entity;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="course_memberships") public class CourseMembership {
 @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="course_membership_id") private UUID courseMembershipId;
 @Column(name="course_id") private UUID courseId; @Column(name="user_id") private UUID userId; @Column(name="membership_role") private String membershipRole; @Column(name="status") private String status; @Column(name="assigned_by") private UUID assignedBy; @Column(name="assigned_at") private LocalDateTime assignedAt;
 public UUID getCourseMembershipId(){return courseMembershipId;} public UUID getCourseId(){return courseId;} public void setCourseId(UUID v){courseId=v;} public UUID getUserId(){return userId;} public void setUserId(UUID v){userId=v;} public String getMembershipRole(){return membershipRole;} public void setMembershipRole(String v){membershipRole=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public UUID getAssignedBy(){return assignedBy;} public void setAssignedBy(UUID v){assignedBy=v;} public LocalDateTime getAssignedAt(){return assignedAt;} public void setAssignedAt(LocalDateTime v){assignedAt=v;}
}
