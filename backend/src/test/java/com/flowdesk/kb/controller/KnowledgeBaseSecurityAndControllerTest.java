package com.flowdesk.kb.controller;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.dto.KbArticleDeflectionResponse;
import com.flowdesk.kb.dto.KbArticleResponse;
import com.flowdesk.kb.dto.KbArticleSummaryResponse;
import com.flowdesk.kb.dto.KbCategoryResponse;
import com.flowdesk.kb.service.KnowledgeBaseService;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBaseSecurityAndControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeBaseService kbService;

    @MockitoBean
    private UserRepository userRepository;

    private UUID employeeId;
    private UUID supportId;
    private UUID teamLeadId;
    private UUID adminId;

    private User employeeUser;
    private User supportUser;
    private User teamLeadUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        employeeId = UUID.randomUUID();
        supportId = UUID.randomUUID();
        teamLeadId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        employeeUser = new User("emp@flowdesk.test", "hash", "Alice", "Employee");
        employeeUser.getRoles().add(new Role(RoleCode.EMPLOYEE, "Employee"));
        ReflectionTestUtils.setField(employeeUser, "id", employeeId);

        supportUser = new User("sup@flowdesk.test", "hash", "Bob", "Support");
        supportUser.getRoles().add(new Role(RoleCode.SUPPORT_ENGINEER, "Support"));
        ReflectionTestUtils.setField(supportUser, "id", supportId);

        teamLeadUser = new User("lead@flowdesk.test", "hash", "Charlie", "Lead");
        teamLeadUser.getRoles().add(new Role(RoleCode.TEAM_LEAD, "Team Lead"));
        ReflectionTestUtils.setField(teamLeadUser, "id", teamLeadId);

        adminUser = new User("admin@flowdesk.test", "hash", "David", "Admin");
        adminUser.getRoles().add(new Role(RoleCode.ADMIN, "Admin"));
        ReflectionTestUtils.setField(adminUser, "id", adminId);

        when(userRepository.findById(employeeId)).thenReturn(Optional.of(employeeUser));
        when(userRepository.findById(supportId)).thenReturn(Optional.of(supportUser));
        when(userRepository.findById(teamLeadId)).thenReturn(Optional.of(teamLeadUser));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
    }

    @Test
    @DisplayName("Unauthenticated guest cannot access KB endpoints")
    void unauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/kb/categories"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/kb/articles"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/kb/articles/how-to-vpn"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated employee can list categories and search articles")
    void employeeCanListAndSearch() throws Exception {
        when(kbService.getActiveCategories()).thenReturn(List.of(
                new KbCategoryResponse(UUID.randomUUID(), "Network", "network", "Desc", "wifi", 1, true, 5)
        ));

        mockMvc.perform(get("/api/kb/categories")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Network"));

        when(kbService.searchPublishedArticles(any(), any(), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(
                        new KbArticleSummaryResponse(UUID.randomUUID(), "VPN Guide", "vpn-guide", "Summary",
                                ArticleStatus.PUBLISHED, UUID.randomUUID(), "Network", "network", supportId, "Bob Support", 10, Instant.now(), Instant.now())
                ), 0, 10, 1, 1, true, true)
        );

        mockMvc.perform(get("/api/kb/articles?search=vpn")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("VPN Guide"));
    }

    @Test
    @DisplayName("Employee calling deflection receives suggestion list")
    void employeeDeflectionSuccess() throws Exception {
        when(kbService.getDeflectionSuggestions("Cannot connect to VPN")).thenReturn(List.of(
                new KbArticleDeflectionResponse(UUID.randomUUID(), "VPN Troubleshooting", "vpn-troubleshooting", "Fix common VPN errors", "Network", "network")
        ));

        mockMvc.perform(get("/api/kb/articles/deflect?query=Cannot connect to VPN")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("VPN Troubleshooting"));
    }

    @Test
    @DisplayName("Employee calling support article management receives 403 Forbidden")
    void employeeForbiddenFromArticleManagement() throws Exception {
        String jsonPayload = """
                {
                    "categoryId": "%s",
                    "title": "Unauthorized Article",
                    "summary": "Summary content here",
                    "contentMarkdown": "# Body"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/support/kb/articles")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Support Engineer can create draft article")
    void supportEngineerCanCreateDraft() throws Exception {
        UUID categoryId = UUID.randomUUID();
        String jsonPayload = """
                {
                    "categoryId": "%s",
                    "title": "New Support SOP",
                    "summary": "Summary of standard operating procedure",
                    "contentMarkdown": "# Step 1\\nExecute check."
                }
                """.formatted(categoryId);

        when(kbService.createDraftArticle(any(), any())).thenReturn(
                new KbArticleResponse(UUID.randomUUID(), "New Support SOP", "new-support-sop", "Summary of standard operating procedure",
                        "# Step 1\nExecute check.", ArticleStatus.DRAFT, categoryId, "General", "general", supportId, "Bob Support", 0, null, Instant.now(), Instant.now())
        );

        mockMvc.perform(post("/api/support/kb/articles")
                        .with(jwt().jwt(j -> j.subject(supportId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Support SOP"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("Support Engineer can transition article status")
    void supportEngineerCanTransitionStatus() throws Exception {
        UUID articleId = UUID.randomUUID();
        String jsonPayload = """
                {
                    "status": "PUBLISHED"
                }
                """;

        when(kbService.transitionStatus(eq(articleId), eq(ArticleStatus.PUBLISHED), any())).thenReturn(
                new KbArticleResponse(articleId, "New Support SOP", "new-support-sop", "Summary",
                        "# Step 1", ArticleStatus.PUBLISHED, UUID.randomUUID(), "General", "general", supportId, "Bob Support", 0, Instant.now(), Instant.now(), Instant.now())
        );

        mockMvc.perform(patch("/api/support/kb/articles/{id}/status", articleId)
                        .with(jwt().jwt(j -> j.subject(supportId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("Support Engineer calling admin category management receives 403 Forbidden")
    void supportEngineerForbiddenFromCategoryManagement() throws Exception {
        String jsonPayload = """
                {
                    "name": "New Cat",
                    "description": "Desc",
                    "icon": "folder",
                    "displayOrder": 1
                }
                """;

        mockMvc.perform(post("/api/admin/kb/categories")
                        .with(jwt().jwt(j -> j.subject(supportId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Team Lead can manage categories")
    void teamLeadCanCreateCategory() throws Exception {
        String jsonPayload = """
                {
                    "name": "New Cat",
                    "description": "Desc",
                    "icon": "folder",
                    "displayOrder": 1
                }
                """;

        when(kbService.createCategory(any(), any())).thenReturn(
                new KbCategoryResponse(UUID.randomUUID(), "New Cat", "new-cat", "Desc", "folder", 1, true, 0)
        );

        mockMvc.perform(post("/api/admin/kb/categories")
                        .with(jwt().jwt(j -> j.subject(teamLeadId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Cat"));
    }

    @Test
    @DisplayName("Admin can manage categories")
    void adminCanCreateCategory() throws Exception {
        String jsonPayload = """
                {
                    "name": "Hardware",
                    "description": "Desc",
                    "icon": "laptop",
                    "displayOrder": 2
                }
                """;

        when(kbService.createCategory(any(), any())).thenReturn(
                new KbCategoryResponse(UUID.randomUUID(), "Hardware", "hardware", "Desc", "laptop", 2, true, 0)
        );

        mockMvc.perform(post("/api/admin/kb/categories")
                        .with(jwt().jwt(j -> j.subject(adminId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hardware"));
    }
}
