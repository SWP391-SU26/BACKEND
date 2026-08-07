package com.courseqa.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courseqa.model.dto.DocumentDto;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.security.JwtService;
import com.courseqa.security.SecurityConfig;
import com.courseqa.service.DocumentProcessingService;
import com.courseqa.service.DocumentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards the SecurityConfig rule ordering for DocumentController, not the business
 * logic (that's DocumentServiceMoveWorkspaceTest's job). The bug this catches: a
 * broad "PATCH /api/documents/** -> ADMIN" rule silently swallowed the more specific
 * move-to-workspace endpoint, so mvn test stayed green while every student request
 * to it 403'd - none of the other 171 tests run through the real filter chain.
 */
@WebMvcTest(controllers = DocumentController.class)
@Import({SecurityConfig.class, JwtService.class})
class DocumentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private DocumentProcessingService documentProcessingService;

    @MockBean
    private CourseWorkspaceRepository courseWorkspaceRepository;

    private String tokenFor(String... roles) {
        return jwtService.issue(UUID.randomUUID(), "user@example.com", List.of(roles));
    }

    @Test
    void studentCanMoveTheirOwnDocumentBetweenWorkspaces() throws Exception {
        when(documentService.moveToWorkspace(any(), any(), any()))
                .thenReturn(new DocumentDto.DocumentResponse());

        mockMvc.perform(patch("/api/documents/{documentId}/workspace", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void moveWorkspaceStillRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/documents/{documentId}/workspace", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotCallTheAdminOnlyReviewEndpoint() throws Exception {
        mockMvc.perform(patch("/api/documents/{documentId}/review", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCallTheAdminOnlyReviewEndpoint() throws Exception {
        when(documentService.reviewDocument(any(), any(), any()))
                .thenReturn(new DocumentDto.DocumentResponse());

        mockMvc.perform(patch("/api/documents/{documentId}/review", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reviewQueueStaysAdminOnlyEvenForAnAuthenticatedStudent() throws Exception {
        mockMvc.perform(get("/api/documents/review-queue")
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isForbidden());
    }
}
