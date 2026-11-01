package com.flowdesk.kb;

import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.domain.KbArticle;
import com.flowdesk.kb.domain.KbCategory;
import com.flowdesk.kb.repository.KbArticleRepository;
import com.flowdesk.kb.repository.KbCategoryRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KbArticleRepository articleRepository;

    @Autowired
    private KbCategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User testAuthor;
    private KbCategory testCategory;

    private final List<UUID> trackedArticleIds = new ArrayList<>();
    private final List<UUID> trackedUserIds = new ArrayList<>();
    private final List<UUID> trackedCategoryIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.SUPPORT_ENGINEER, "Support")));

        testAuthor = new User(
                "kb-author-" + UUID.randomUUID() + "@flowdesk-test.invalid",
                "hashedpassword",
                "KB",
                "Author"
        );
        testAuthor.getRoles().add(supportRole);
        testAuthor = saveUser(testAuthor);

        testCategory = categoryRepository.findBySlug("test-network-cat")
                .orElseGet(() -> {
                    KbCategory created = categoryRepository.save(new KbCategory(
                            "Test Network Cat",
                            "test-network-cat",
                            "Category for network integration tests",
                            "wifi",
                            100
                    ));
                    trackedCategoryIds.add(created.getId());
                    return created;
                });
    }

    @AfterEach
    void tearDown() {
        // Targeted entity cleanup: only delete test-created entities by tracked IDs.
        // Never wipe persistent development data or seeded categories.
        for (UUID id : trackedArticleIds) {
            try {
                articleRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        trackedArticleIds.clear();

        for (UUID id : trackedUserIds) {
            try {
                userRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        trackedUserIds.clear();

        for (UUID id : trackedCategoryIds) {
            try {
                categoryRepository.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        trackedCategoryIds.clear();
    }

    private KbArticle saveArticle(KbArticle article) {
        KbArticle saved = articleRepository.saveAndFlush(article);
        trackedArticleIds.add(saved.getId());
        return saved;
    }

    private User saveUser(User user) {
        User saved = userRepository.saveAndFlush(user);
        trackedUserIds.add(saved.getId());
        return saved;
    }

    @Test
    @DisplayName("searchPublished only returns PUBLISHED articles matching full-text query")
    void searchPublishedExcludesDraftsAndMatchesKeywords() {
        KbArticle publishedVpn = new KbArticle(
                testCategory,
                "Office VPN Setup Guide",
                "office-vpn-setup-" + UUID.randomUUID().toString().substring(0, 6),
                "Connect to the corporate WireGuard VPN easily",
                "Download the profile and configure DNS.",
                testAuthor
        );
        publishedVpn.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(publishedVpn);

        KbArticle draftVpn = new KbArticle(
                testCategory,
                "Draft VPN Deprecation Notice",
                "draft-vpn-deprecation-" + UUID.randomUUID().toString().substring(0, 6),
                "Upcoming changes to VPN infrastructure",
                "Do not publish until Q3.",
                testAuthor
        );
        saveArticle(draftVpn);

        // Search for "VPN"
        Page<KbArticle> results = articleRepository.searchPublished(
                testCategory.getId(),
                "VPN",
                PageRequest.of(0, 10)
        );

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getTitle()).isEqualTo("Office VPN Setup Guide");
        assertThat(results.getContent().get(0).getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
    }

    @Test
    @DisplayName("findDeflectionSuggestions returns matching published articles and ignores drafts")
    void deflectionSuggestionsIntegration() {
        KbArticle publishedWifi = new KbArticle(
                testCategory,
                "Cannot connect to office Wi-Fi",
                "cannot-connect-wifi-" + UUID.randomUUID().toString().substring(0, 6),
                "Troubleshoot wireless network authentication failures",
                "Verify your certificate and ensure 802.1x is enabled.",
                testAuthor
        );
        publishedWifi.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(publishedWifi);

        KbArticle draftWifi = new KbArticle(
                testCategory,
                "Draft Wi-Fi Maintenance",
                "draft-wifi-maint-" + UUID.randomUUID().toString().substring(0, 6),
                "Scheduled downtime for office Wi-Fi",
                "Draft notes.",
                testAuthor
        );
        saveArticle(draftWifi);

        List<KbArticle> suggestions = articleRepository.findDeflectionSuggestions("Cannot connect to office Wi-Fi", 3);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getTitle()).isEqualTo("Cannot connect to office Wi-Fi");
    }

    @Test
    @DisplayName("findDeflectionSuggestions matches natural-language VPN query and excludes drafts and archived")
    void deflectionNaturalLanguageVpnQueryMatchesPublishedAndExcludesDraftsAndArchived() {
        KbArticle publishedVpn = new KbArticle(
                testCategory,
                "How to Fix VPN Connection Issues",
                "how-to-fix-vpn-" + UUID.randomUUID().toString().substring(0, 6),
                "Learn how to troubleshoot common VPN connection problems, including internet connectivity, login failures, and connection timeouts.",
                "# Troubleshooting Steps\nVerify credentials and gateway.",
                testAuthor
        );
        publishedVpn.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(publishedVpn);

        KbArticle draftVpn = new KbArticle(
                testCategory,
                "Draft VPN Deprecation Notice",
                "draft-vpn-notes-" + UUID.randomUUID().toString().substring(0, 6),
                "Upcoming changes to VPN infrastructure and connections",
                "Do not publish until Q3.",
                testAuthor
        );
        saveArticle(draftVpn);

        KbArticle archivedVpn = new KbArticle(
                testCategory,
                "Archived Legacy VPN Documentation",
                "archived-vpn-notes-" + UUID.randomUUID().toString().substring(0, 6),
                "Old VPN connection setup guide from last year",
                "Decommissioned infrastructure.",
                testAuthor
        );
        archivedVpn.transitionStatus(ArticleStatus.PUBLISHED);
        archivedVpn.transitionStatus(ArticleStatus.ARCHIVED);
        saveArticle(archivedVpn);

        // Natural-language query entered by employee on /tickets/new
        List<KbArticle> suggestions = articleRepository.findDeflectionSuggestions("Unable to connect to office VPN", 3);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).extracting(KbArticle::getTitle).contains("How to Fix VPN Connection Issues");
        assertThat(suggestions).extracting(KbArticle::getStatus).containsOnly(ArticleStatus.PUBLISHED);
        assertThat(suggestions).extracting(KbArticle::getTitle).doesNotContain("Draft VPN Deprecation Notice", "Archived Legacy VPN Documentation");
    }

    @Test
    @DisplayName("findDeflectionSuggestions matches short keywords like 'VPN connection'")
    void deflectionShortKeywordsMatching() {
        KbArticle publishedVpn = new KbArticle(
                testCategory,
                "How to Fix VPN Connection Issues",
                "how-to-fix-vpn-short-" + UUID.randomUUID().toString().substring(0, 6),
                "Learn how to troubleshoot common VPN connection problems.",
                "# Troubleshooting Steps",
                testAuthor
        );
        publishedVpn.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(publishedVpn);

        List<KbArticle> suggestions = articleRepository.findDeflectionSuggestions("VPN connection", 3);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.get(0).getTitle()).isEqualTo("How to Fix VPN Connection Issues");
    }

    @Test
    @DisplayName("findDeflectionSuggestions returns empty for completely irrelevant queries")
    void deflectionIrrelevantQueryReturnsEmpty() {
        KbArticle publishedVpn = new KbArticle(
                testCategory,
                "How to Fix VPN Connection Issues",
                "how-to-fix-vpn-irrel-" + UUID.randomUUID().toString().substring(0, 6),
                "Learn how to troubleshoot common VPN connection problems.",
                "# Troubleshooting Steps",
                testAuthor
        );
        publishedVpn.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(publishedVpn);

        List<KbArticle> suggestions = articleRepository.findDeflectionSuggestions("Printer paper jam in copy room", 3);

        assertThat(suggestions).isEmpty();
    }

    @Test
    @DisplayName("findDeflectionSuggestions respects limit and orders by relevance")
    void deflectionResultOrderingAndLimits() {
        KbArticle vpnTroubleshooting = new KbArticle(
                testCategory,
                "How to Fix VPN Connection Issues",
                "vpn-troubleshoot-" + UUID.randomUUID().toString().substring(0, 6),
                "Learn how to troubleshoot common VPN connection problems.",
                "# VPN Guide",
                testAuthor
        );
        vpnTroubleshooting.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(vpnTroubleshooting);

        KbArticle officeErgonomics = new KbArticle(
                testCategory,
                "Office Ergonomics and Desk Setup",
                "office-ergo-" + UUID.randomUUID().toString().substring(0, 6),
                "Guidelines for office chair and desk ergonomics.",
                "# Office Guide",
                testAuthor
        );
        officeErgonomics.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(officeErgonomics);

        List<KbArticle> suggestions = articleRepository.findDeflectionSuggestions("Unable to connect to office VPN", 1);

        assertThat(suggestions).hasSize(1);
        // The article matching multiple relevant terms (VPN + connection) must outrank the one matching only 'office'
        assertThat(suggestions.get(0).getTitle()).isEqualTo("How to Fix VPN Connection Issues");
    }

    @Test
    @DisplayName("Employee GET /api/kb/articles/deflect returns mapped deflection suggestions and ignores short queries")
    void employeeDeflectionEndpointIntegration() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.EMPLOYEE, "Employee")));

        User employee = new User(
                "kb-deflect-emp-" + UUID.randomUUID() + "@flowdesk-test.invalid",
                "hashedpassword",
                "Dan",
                "Employee"
        );
        employee.getRoles().add(employeeRole);
        User savedEmployee = saveUser(employee);

        KbArticle publishedVpn = new KbArticle(
                testCategory,
                "How to Fix VPN Connection Issues",
                "how-to-fix-vpn-api-" + UUID.randomUUID().toString().substring(0, 6),
                "Troubleshoot VPN connection timeouts and login failures",
                "# Troubleshooting Steps",
                testAuthor
        );
        publishedVpn.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(publishedVpn);

        // Query with natural language
        mockMvc.perform(get("/api/kb/articles/deflect")
                        .param("query", "Unable to connect to office VPN")
                        .with(jwt().jwt(j -> j.subject(savedEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("How to Fix VPN Connection Issues"))
                .andExpect(jsonPath("$[0].slug").value(publishedVpn.getSlug()))
                .andExpect(jsonPath("$[0].categoryName").value(testCategory.getName()))
                .andExpect(jsonPath("$[0].categorySlug").value(testCategory.getSlug()));

        // Query with < 3 characters returns empty array
        mockMvc.perform(get("/api/kb/articles/deflect")
                        .param("query", "ab")
                        .with(jwt().jwt(j -> j.subject(savedEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("searchManagement returns articles of any status")
    void searchManagementReturnsAllStatuses() {
        KbArticle pub = new KbArticle(
                testCategory,
                "Hardware Return Policy",
                "hardware-return-" + UUID.randomUUID().toString().substring(0, 6),
                "Summary of hardware return process",
                "Detailed instructions.",
                testAuthor
        );
        pub.transitionStatus(ArticleStatus.PUBLISHED);
        saveArticle(pub);

        KbArticle dft = new KbArticle(
                testCategory,
                "Hardware Lease Policy",
                "hardware-lease-" + UUID.randomUUID().toString().substring(0, 6),
                "Summary of hardware lease process",
                "Detailed instructions.",
                testAuthor
        );
        saveArticle(dft);

        Page<KbArticle> allResults = articleRepository.searchManagement(
                null,
                testCategory.getId(),
                "Hardware",
                PageRequest.of(0, 10)
        );

        assertThat(allResults.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Employee GET draft article returns 404 Not Found, not 500")
    void employeeGetDraftArticleReturns404() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.EMPLOYEE, "Employee")));

        User employee = new User(
                "kb-emp-" + UUID.randomUUID() + "@flowdesk-test.invalid",
                "hashedpassword",
                "Jane",
                "Employee"
        );
        employee.getRoles().add(employeeRole);
        User savedEmployee = saveUser(employee);

        KbArticle draft = new KbArticle(
                testCategory,
                "How to Fix VPN Connection Issues",
                "how-to-fix-vpn-connection-issues-" + UUID.randomUUID().toString().substring(0, 6),
                "Fix VPN connection issues",
                "VPN draft content",
                testAuthor
        );
        draft = saveArticle(draft);

        mockMvc.perform(get("/api/kb/articles/" + draft.getSlug())
                        .with(jwt().jwt(j -> j.subject(savedEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Employee GET published article returns 200 and increments view count")
    void employeeGetPublishedArticleReturns200AndIncrementsViewCount() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.EMPLOYEE, "Employee")));

        User employee = new User(
                "kb-emp-" + UUID.randomUUID() + "@flowdesk-test.invalid",
                "hashedpassword",
                "Jane",
                "Employee"
        );
        employee.getRoles().add(employeeRole);
        User savedEmployee = saveUser(employee);

        KbArticle published = new KbArticle(
                testCategory,
                "Published Knowledge Guide",
                "published-knowledge-guide-" + UUID.randomUUID().toString().substring(0, 6),
                "Public summary of guide",
                "# Full guide body",
                testAuthor
        );
        published.transitionStatus(ArticleStatus.PUBLISHED);
        published = saveArticle(published);

        mockMvc.perform(get("/api/kb/articles/" + published.getSlug())
                        .with(jwt().jwt(j -> j.subject(savedEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());

        KbArticle reloaded = articleRepository.findById(published.getId()).orElseThrow();
        assertThat(reloaded.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Employee GET archived article returns 404 security cloaking")
    void employeeGetArchivedArticleReturns404() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.EMPLOYEE, "Employee")));

        User employee = new User(
                "kb-emp-" + UUID.randomUUID() + "@flowdesk-test.invalid",
                "hashedpassword",
                "Jane",
                "Employee"
        );
        employee.getRoles().add(employeeRole);
        User savedEmployee = saveUser(employee);

        KbArticle archived = new KbArticle(
                testCategory,
                "Archived Legacy System Guide",
                "archived-legacy-guide-" + UUID.randomUUID().toString().substring(0, 6),
                "Legacy instructions",
                "Old system body",
                testAuthor
        );
        archived.transitionStatus(ArticleStatus.PUBLISHED);
        archived.transitionStatus(ArticleStatus.ARCHIVED);
        archived = saveArticle(archived);

        mockMvc.perform(get("/api/kb/articles/" + archived.getSlug())
                        .with(jwt().jwt(j -> j.subject(savedEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Employee GET nonexistent slug returns 404 Not Found")
    void employeeGetNonexistentSlugReturns404() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.EMPLOYEE, "Employee")));

        User employee = new User(
                "kb-emp-" + UUID.randomUUID() + "@flowdesk-test.invalid",
                "hashedpassword",
                "Jane",
                "Employee"
        );
        employee.getRoles().add(employeeRole);
        User savedEmployee = saveUser(employee);

        mockMvc.perform(get("/api/kb/articles/definitely-nonexistent-slug-" + UUID.randomUUID())
                        .with(jwt().jwt(j -> j.subject(savedEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Support Engineer can view draft article via direct slug")
    void supportEngineerCanViewDraftArticle() throws Exception {
        KbArticle draft = new KbArticle(
                testCategory,
                "Support Work in Progress Guide",
                "support-wip-guide-" + UUID.randomUUID().toString().substring(0, 6),
                "WIP summary",
                "WIP content",
                testAuthor
        );
        draft = saveArticle(draft);

        mockMvc.perform(get("/api/kb/articles/" + draft.getSlug())
                        .with(jwt().jwt(j -> j.subject(testAuthor.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Employee attempting to access support management endpoint receives 403 Forbidden")
    void employeeAccessingManagementEndpointReturns403() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.EMPLOYEE, "Employee")));

        User employee = new User(
                "kb-emp-" + UUID.randomUUID() + "@flowdesk-test.invalid",
                "hashedpassword",
                "Jane",
                "Employee"
        );
        employee.getRoles().add(employeeRole);
        User savedEmployee = saveUser(employee);

        mockMvc.perform(get("/api/support/kb/articles")
                        .with(jwt().jwt(j -> j.subject(savedEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Regression: Unrelated existing articles remain unchanged during test execution and cleanup")
    void unrelatedExistingArticlesRemainUnchanged() {
        // 1. Create an "unrelated" pre-existing article (representing development/browser data)
        // Intentionally NOT added to trackedArticleIds
        KbArticle unrelatedArticle = new KbArticle(
                testCategory,
                "Pre-existing Critical Knowledge Base Guide",
                "pre-existing-critical-guide-" + UUID.randomUUID().toString().substring(0, 8),
                "Crucial operations guide that must not be deleted by tests",
                "# Important instructions",
                testAuthor
        );
        unrelatedArticle.transitionStatus(ArticleStatus.PUBLISHED);
        unrelatedArticle = articleRepository.saveAndFlush(unrelatedArticle);
        UUID unrelatedId = unrelatedArticle.getId();

        try {
            // 2. Perform test operations that track and clean up separate test articles
            KbArticle ephemeralArticle = new KbArticle(
                    testCategory,
                    "Ephemeral Test Article",
                    "ephemeral-test-" + UUID.randomUUID().toString().substring(0, 8),
                    "Summary",
                    "Body",
                    testAuthor
            );
            ephemeralArticle = saveArticle(ephemeralArticle);

            // Verify test article exists
            assertThat(articleRepository.findById(ephemeralArticle.getId())).isPresent();

            // Simulate targeted cleanup of tracked test entities as tearDown() does
            for (UUID id : List.copyOf(trackedArticleIds)) {
                articleRepository.deleteById(id);
                trackedArticleIds.remove(id);
            }

            // 3. Verify that the unrelated pre-existing article remains completely intact and unmodified
            Optional<KbArticle> preserved = articleRepository.findById(unrelatedId);
            assertThat(preserved).isPresent();
            assertThat(preserved.get().getTitle()).isEqualTo("Pre-existing Critical Knowledge Base Guide");
            assertThat(preserved.get().getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
            assertThat(preserved.get().getContentMarkdown()).isEqualTo("# Important instructions");
        } finally {
            // Hygienic cleanup of the test fixture
            try {
                articleRepository.deleteById(unrelatedId);
            } catch (Exception ignored) {
            }
        }
    }
}
