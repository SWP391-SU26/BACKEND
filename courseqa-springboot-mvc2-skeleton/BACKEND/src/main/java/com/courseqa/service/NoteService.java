package com.courseqa.service;

import com.courseqa.model.entity.SavedNote;
import com.courseqa.repository.SavedNoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {
    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final SavedNoteRepository savedNoteRepository;

    public NoteService(SavedNoteRepository savedNoteRepository) {
        this.savedNoteRepository = savedNoteRepository;
    }

    public SavedNote saveNote(UUID userId, UUID workspaceId, String noteTitle, String noteContent) {
        log.info("Saving note for userId: {}, workspaceId: {}", userId, workspaceId);

        SavedNote note = new SavedNote();
        note.setUserId(userId);
        note.setWorkspaceId(workspaceId);
        note.setNoteTitle(noteTitle);
        note.setNoteContent(noteContent);
        note.setNoteType("MANUAL");
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        SavedNote savedNote = savedNoteRepository.save(note);
        log.info("Saved note with id: {}", savedNote.getNoteId());
        return savedNote;
    }

    public List<SavedNote> getNotes(UUID workspaceId, UUID userId) {
        log.info("Fetching notes for workspaceId: {}", workspaceId);

        List<SavedNote> notes = savedNoteRepository.findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(workspaceId, userId);
        log.debug("Found {} notes for workspaceId: {}", notes.size(), workspaceId);
        return notes;
    }
}
