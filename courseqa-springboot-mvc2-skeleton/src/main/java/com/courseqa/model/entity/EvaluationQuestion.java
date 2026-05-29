package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluation_questions")
public class EvaluationQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "evaluation_question_id")
    private UUID evaluationQuestionId;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "question_no")
    private Integer questionNo;

    @Column(name = "question_text", columnDefinition = "NVARCHAR(MAX)")
    private String questionText;

    @Column(name = "ground_truth_answer", columnDefinition = "NVARCHAR(MAX)")
    private String groundTruthAnswer;

    @Column(name = "expected_document_id")
    private UUID expectedDocumentId;

    @Column(name = "expected_page")
    private Integer expectedPage;

    @Column(name = "question_type")
    private String questionType;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public EvaluationQuestion() { }

    public UUID getEvaluationQuestionId() { return evaluationQuestionId; }
    public void setEvaluationQuestionId(UUID evaluationQuestionId) { this.evaluationQuestionId = evaluationQuestionId; }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public Integer getQuestionNo() { return questionNo; }
    public void setQuestionNo(Integer questionNo) { this.questionNo = questionNo; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getGroundTruthAnswer() { return groundTruthAnswer; }
    public void setGroundTruthAnswer(String groundTruthAnswer) { this.groundTruthAnswer = groundTruthAnswer; }

    public UUID getExpectedDocumentId() { return expectedDocumentId; }
    public void setExpectedDocumentId(UUID expectedDocumentId) { this.expectedDocumentId = expectedDocumentId; }

    public Integer getExpectedPage() { return expectedPage; }
    public void setExpectedPage(Integer expectedPage) { this.expectedPage = expectedPage; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
