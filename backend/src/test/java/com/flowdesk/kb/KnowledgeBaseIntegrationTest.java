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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KnowledgeBaseIntegrationTest {

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
        testAuthor = userRepository.saveAndFlush(testAuthor);

        testCategory = categoryRepository.findBySlug("test-network-cat")
                .orElseGet(() -> categoryRepository.save(new KbCategory(
                        "Test Network Cat",
                        "test-network-cat",
                        "Category for network integration tests",
                        "wifi",
                        100
                )));
    }

    @AfterEach
    void tearDown() {
        articleRepository.deleteAll();
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
        articleRepository.save(publishedVpn);

        KbArticle draftVpn = new KbArticle(
                testCategory,
                "Draft VPN Deprecation Notice",
                "draft-vpn-deprecation-" + UUID.randomUUID().toString().substring(0, 6),
                "Upcoming changes to VPN infrastructure",
                "Do not publish until Q3.",
                testAuthor
        );
        articleRepository.save(draftVpn);

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
        articleRepository.save(publishedWifi);

        KbArticle draftWifi = new KbArticle(
                testCategory,
                "Draft Wi-Fi Maintenance",
                "draft-wifi-maint-" + UUID.randomUUID().toString().substring(0, 6),
                "Scheduled downtime for office Wi-Fi",
                "Draft notes.",
                testAuthor
        );
        articleRepository.save(draftWifi);

        List<KbArticle> suggestions = articleRepository.findDeflectionSuggestions("Cannot connect to office Wi-Fi", 3);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getTitle()).isEqualTo("Cannot connect to office Wi-Fi");
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
        articleRepository.save(pub);

        KbArticle dft = new KbArticle(
                testCategory,
                "Hardware Lease Policy",
                "hardware-lease-" + UUID.randomUUID().toString().substring(0, 6),
                "Summary of hardware lease process",
                "Detailed instructions.",
                testAuthor
        );
        articleRepository.save(dft);

        Page<KbArticle> allResults = articleRepository.searchManagement(
                null,
                testCategory.getId(),
                "Hardware",
                PageRequest.of(0, 10)
        );

        assertThat(allResults.getContent()).hasSize(2);
    }
}
