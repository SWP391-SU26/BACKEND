package com.courseqa.service;

import com.courseqa.model.dto.ChapterSuggestionDto;
import com.courseqa.model.entity.*;
import com.courseqa.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChapterSuggestionService {
    private static final Pattern HEADING = Pattern.compile("(?i)^(ch(?:ương|uong)|chapter|phần|part|unit)\\s*[0-9ivx]+[.: -].{1,180}$");
    private final CourseDocumentRepository documents; private final DocumentPageRepository pages;
    private final DocumentChunkRepository chunks; private final ChapterRepository chapters;
    private final DocumentChapterSuggestionRepository suggestions; private final DocumentChapterRangeRepository ranges;
    public ChapterSuggestionService(CourseDocumentRepository documents, DocumentPageRepository pages,
            DocumentChunkRepository chunks, ChapterRepository chapters,
            DocumentChapterSuggestionRepository suggestions, DocumentChapterRangeRepository ranges) {
        this.documents=documents;this.pages=pages;this.chunks=chunks;this.chapters=chapters;this.suggestions=suggestions;this.ranges=ranges;
    }

    @Transactional
    public List<ChapterSuggestionDto.ReviewItem> getOrGenerate(UUID documentId) {
        CourseDocument document = getProcessed(documentId);
        List<DocumentChapterSuggestion> existing = suggestions.findByDocumentIdOrderByPageStartAsc(documentId);
        if (!existing.isEmpty()) return existing.stream().map(ChapterSuggestionDto.ReviewItem::from).toList();
        List<DocumentPage> documentPages = pages.findByDocumentIdOrderByPageNumberAsc(documentId);
        if (documentPages.isEmpty()) return List.of();
        List<Heading> headings = new ArrayList<>();
        for (DocumentPage page : documentPages) {
            String text = page.getCleanedText() == null ? "" : page.getCleanedText();
            Arrays.stream(text.split("\\R")).map(String::trim).filter(line -> HEADING.matcher(line).matches())
                    .findFirst().ifPresent(title -> headings.add(new Heading(title, page.getPageNumber())));
        }
        if (headings.isEmpty()) headings.add(new Heading(document.getDocumentTitle(), 1));
        int totalPages = Optional.ofNullable(document.getTotalPages()).orElse(documentPages.size());
        for (int i=0;i<headings.size();i++) {
            Heading heading=headings.get(i); int end=i+1<headings.size()?headings.get(i+1).page()-1:totalPages;
            if (end < heading.page()) continue;
            DocumentChapterSuggestion suggestion=new DocumentChapterSuggestion();suggestion.setDocumentId(documentId);
            suggestion.setSuggestedTitle(heading.title());suggestion.setPageStart(heading.page());suggestion.setPageEnd(end);
            suggestion.setConfidence(headings.size()==1?0.55:0.85);suggestion.setStatus("PENDING");suggestions.save(suggestion);
        }
        return suggestions.findByDocumentIdOrderByPageStartAsc(documentId).stream().map(ChapterSuggestionDto.ReviewItem::from).toList();
    }

    @Transactional
    public List<ChapterSuggestionDto.ReviewItem> confirm(UUID documentId, ChapterSuggestionDto.ConfirmRequest request) {
        CourseDocument document=getProcessed(documentId);
        if(request==null||request.chapters==null||request.chapters.isEmpty()) throw bad("At least one chapter is required.");
        List<ChapterSuggestionDto.ReviewItem> items=request.chapters.stream().sorted(Comparator.comparingInt(x->x.pageStart==null?0:x.pageStart)).toList();
        int total=Optional.ofNullable(document.getTotalPages()).orElse(Integer.MAX_VALUE);int previousEnd=0;
        for(ChapterSuggestionDto.ReviewItem item:items){if(item.title==null||item.title.isBlank()||item.pageStart==null||item.pageEnd==null||item.pageStart<1||item.pageEnd<item.pageStart||item.pageEnd>total)throw bad("Invalid chapter title or page range.");if(item.pageStart<=previousEnd)throw bad("Chapter page ranges cannot overlap.");previousEnd=item.pageEnd;}
        List<DocumentChapterRange> oldRanges = ranges.findByDocumentIdOrderByPageStartAsc(documentId);
        oldRanges.stream().map(DocumentChapterRange::getChapterId).distinct().forEach(chapterId -> chapters.findById(chapterId).ifPresent(chapter -> { chapter.setIsActive(false); chapter.setUpdatedAt(LocalDateTime.now()); chapters.save(chapter); }));
        ranges.deleteByDocumentId(documentId);suggestions.deleteByDocumentId(documentId);
        List<DocumentChunk> documentChunks=chunks.findByDocumentIdOrderByChunkIndexAsc(documentId);documentChunks.forEach(chunk -> chunk.setChapterId(null));int order=chapters.findByCourseIdOrderByOrderIndexAsc(document.getCourseId()).stream().map(Chapter::getOrderIndex).filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
        for(ChapterSuggestionDto.ReviewItem item:items){Chapter chapter=new Chapter();chapter.setCourseId(document.getCourseId());chapter.setChapterTitle(item.title.trim());chapter.setDescription("Derived from "+document.getOriginalFilename()+", pages "+item.pageStart+"-"+item.pageEnd);chapter.setOrderIndex(++order);chapter.setIsActive(true);chapter.setCreatedAt(LocalDateTime.now());chapter.setUpdatedAt(LocalDateTime.now());chapter=chapters.save(chapter);
            DocumentChapterRange range=new DocumentChapterRange();range.setDocumentId(documentId);range.setChapterId(chapter.getChapterId());range.setPageStart(item.pageStart);range.setPageEnd(item.pageEnd);ranges.save(range);
            DocumentChapterSuggestion accepted=new DocumentChapterSuggestion();accepted.setDocumentId(documentId);accepted.setSuggestedTitle(item.title.trim());accepted.setPageStart(item.pageStart);accepted.setPageEnd(item.pageEnd);accepted.setConfidence(item.confidence);accepted.setStatus("CONFIRMED");suggestions.save(accepted);
            UUID chapterId=chapter.getChapterId();documentChunks.stream().filter(chunk->overlaps(chunk,item.pageStart,item.pageEnd)).forEach(chunk->chunk.setChapterId(chapterId));}
        chunks.saveAll(documentChunks);
        return suggestions.findByDocumentIdOrderByPageStartAsc(documentId).stream().map(ChapterSuggestionDto.ReviewItem::from).toList();
    }
    private boolean overlaps(DocumentChunk chunk,int start,int end){int s=Optional.ofNullable(chunk.getPageStart()).orElse(1),e=Optional.ofNullable(chunk.getPageEnd()).orElse(s);return s<=end&&e>=start;}
    private CourseDocument getProcessed(UUID id){CourseDocument d=documents.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found."));if(!"PROCESSED".equals(d.getProcessingStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Document must be processed before reviewing chapters.");return d;}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);} private record Heading(String title,int page){}
}
