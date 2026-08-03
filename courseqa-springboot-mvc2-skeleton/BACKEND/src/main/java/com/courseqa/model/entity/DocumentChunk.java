package com.courseqa.model.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "chunk_strategy")
    private String chunkStrategy;

    @Column(name = "chunk_size")
    private Integer chunkSize;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @Column(name = "content", columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "content_compressed")
    private byte[] contentCompressed;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "char_count")
    private Integer charCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "heading_path")
    private String headingPath;

    @Column(name = "chunk_version")
    private Integer chunkVersion;

    @Column(name = "is_active")
    private Boolean isActive;

    public DocumentChunk() { }

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(String chunkStrategy) { this.chunkStrategy = chunkStrategy; }

    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }

    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public byte[] getContentCompressed() { return contentCompressed; }
    public void setContentCompressed(byte[] contentCompressed) { this.contentCompressed = contentCompressed; }

    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }

    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getHeadingPath() { return headingPath; }
    public void setHeadingPath(String headingPath) { this.headingPath = headingPath; }

    public Integer getChunkVersion() { return chunkVersion; }
    public void setChunkVersion(Integer chunkVersion) { this.chunkVersion = chunkVersion; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

}
