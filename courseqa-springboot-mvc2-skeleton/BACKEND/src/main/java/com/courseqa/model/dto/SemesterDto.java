package com.courseqa.model.dto;
import com.courseqa.model.entity.*; import java.time.*; import java.util.*;
public class SemesterDto {
 public static class CreateRequest { public String semesterCode; public String semesterName; public LocalDate startDate; public LocalDate endDate; public UUID createdBy; }
 public static class UpdateRequest { public String semesterName; public LocalDate startDate; public LocalDate endDate; }
 public static class StatusRequest { public String status; }
 public static class Response { public UUID semesterWorkspaceId; public String semesterCode; public String semesterName; public LocalDate startDate; public LocalDate endDate; public String status; public LocalDateTime createdAt; public static Response from(SemesterWorkspace e){ Response r=new Response(); r.semesterWorkspaceId=e.getSemesterWorkspaceId();r.semesterCode=e.getSemesterCode();r.semesterName=e.getSemesterName();r.startDate=e.getStartDate();r.endDate=e.getEndDate();r.status=e.getStatus();r.createdAt=e.getCreatedAt();return r;} }
 public static class MemberRequest { public UUID userId; public String membershipRole; }
 public static class MemberResponse { public UUID courseMembershipId; public UUID userId; public String membershipRole; public String status; public LocalDateTime assignedAt; public static MemberResponse from(CourseMembership e){MemberResponse r=new MemberResponse();r.courseMembershipId=e.getCourseMembershipId();r.userId=e.getUserId();r.membershipRole=e.getMembershipRole();r.status=e.getStatus();r.assignedAt=e.getAssignedAt();return r;} }
}
