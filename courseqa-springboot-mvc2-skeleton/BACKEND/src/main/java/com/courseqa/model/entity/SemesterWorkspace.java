package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "semester_workspaces")
public class SemesterWorkspace {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "semester_workspace_id")
    private UUID semesterWorkspaceId;
    @Column(name = "semester_code") private String semesterCode;
    @Column(name = "semester_name") private String semesterName;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "status") private String status;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    public UUID getSemesterWorkspaceId(){return semesterWorkspaceId;} public String getSemesterCode(){return semesterCode;} public void setSemesterCode(String v){semesterCode=v;} public String getSemesterName(){return semesterName;} public void setSemesterName(String v){semesterName=v;} public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate v){startDate=v;} public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate v){endDate=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public UUID getCreatedBy(){return createdBy;} public void setCreatedBy(UUID v){createdBy=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
