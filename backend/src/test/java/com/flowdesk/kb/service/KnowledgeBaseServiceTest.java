package com.flowdesk.kb.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.domain.KbArticle;
import com.flowdesk.kb.domain.KbCategory;
import com.flowdesk.kb.dto.CreateKbArticleRequest;
import com.flowdesk.kb.dto.CreateKbCategoryRequest;
import com.flowdesk.kb.dto.KbArticleDeflectionResponse;
import com.flowdesk.kb.dto.KbArticleResponse;
import com.flowdesk.kb.dto.KbArticleSummaryResponse;
import com.flowdesk.kb.dto.KbCategoryResponse;
import com.flowdesk.kb.repository.KbArticleRepository;
import com.flowdesk.kb.repository.KbCategoryRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KbArticleRepository articleRepository;

    @Mock
    private KbCategoryRepository categoryRepository;

    @Mock
    private SlugService slugService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private KnowledgeBaseService kbService;

    private User employeeUser;
    private User supportUser;
    private KbCategory category;
    private KbArticle publishedArticle;
    private KbArticle draftArticle;

    @BeforeEach
    void setUp() {
        Role employeeRole = new Role(RoleCode.EMPLOYEE, "Employee");
        Role supportRole = new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer");

        employeeUser = new User("emp@flowdesk.test", "hash", "Alice", "Employee");
        employeeUser.getRoles().add(employeeRole);
        ReflectionTestUtils.setField(employeeUser, "id", UUID.randomUUID());

        supportUser = new User("sup@flowdesk.test", "hash", "Bob", "Support");
        supportUser.getRoles().add(supportRole);
        ReflectionTestUtils.setField(supportUser, "id", UUID.randomUUID());

        category = new KbCategory("VPN & Network", "vpn-network", "Network guides", "wifi", 1);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());

        publishedArticle = new KbArticle(
                category,
                "How to Connect to VPN",
                "how-to-connect-to-vpn",
                "Instructions for VPN setup",
                "# VPN Setup\n\nFollow these steps...",
                supportUser
        );
        ReflectionTestUtils.setField(publishedArticle, "id", UUID.randomUUID());
        publishedArticle.transitionStatus(ArticleStatus.PUBLISHED);

        draftArticle = new KbArticle(
                category,
                "Internal Draft SOP",
                "internal-draft-sop",
                "Secret upcoming procedures",
                "Internal only details",
                supportUser
        );
        ReflectionTestUtils.setField(draftArticle, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("Employee can read published article and view count increments")
    void employeeCanReadPublishedArticle() {
        when(articleRepository.findBySlug("how-to-connect-to-vpn")).thenReturn(Optional.of(publishedArticle));
        when(articleRepository.save(any(KbArticle.class))).thenAnswer(inv -> inv.getArgument(0));

        KbArticleResponse response = kbService.getArticleBySlug("how-to-connect-to-vpn", employeeUser);

        assertThat(response.title()).isEqualTo("How to Connect to VPN");
        assertThat(response.viewCount()).isEqualTo(1);
        verify(articleRepository).save(publishedArticle);
    }

    @Test
    @DisplayName("Employee attempting to read draft article receives 404 security cloaking")
    void employeeReadingDraftArticleGets404() {
        when(articleRepository.findBySlug("internal-draft-sop")).thenReturn(Optional.of(draftArticle));

        assertThatThrownBy(() -> kbService.getArticleBySlug("internal-draft-sop", employeeUser))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Support Engineer can read draft article")
    void supportEngineerCanReadDraftArticle() {
        when(articleRepository.findBySlug("internal-draft-sop")).thenReturn(Optional.of(draftArticle));

        KbArticleResponse response = kbService.getArticleBySlug("internal-draft-sop", supportUser);

        assertThat(response.title()).isEqualTo("Internal Draft SOP");
        assertThat(response.status()).isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    @DisplayName("Non-existent slug throws 404 Not Found")
    void nonExistentSlugThrows404() {
        when(articleRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kbService.getArticleBySlug("missing", employeeUser))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Create draft article generates unique slug and records audit log")
    void createDraftArticleSuccess() {
        CreateKbArticleRequest request = new CreateKbArticleRequest(
                category.getId(),
                "New WiFi Guide",
                "How to connect to office WiFi",
                "Step 1: Select FlowDesk-Secure SSID."
        );

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(slugService.generateUniqueSlug(eq("New WiFi Guide"), any())).thenReturn("new-wifi-guide");
        when(articleRepository.saveAndFlush(any(KbArticle.class))).thenAnswer(inv -> {
            KbArticle art = inv.getArgument(0);
            ReflectionTestUtils.setField(art, "id", UUID.randomUUID());
            return art;
        });

        KbArticleResponse response = kbService.createDraftArticle(request, supportUser);

        assertThat(response.title()).isEqualTo("New WiFi Guide");
        assertThat(response.slug()).isEqualTo("new-wifi-guide");
        assertThat(response.status()).isEqualTo(ArticleStatus.DRAFT);

        verify(auditService).recordUserAction(
                eq(supportUser),
                eq(AuditAction.KB_ARTICLE_CREATED),
                any(),
                any(),
                eq("new-wifi-guide"),
                any()
        );
    }

    @Test
    @DisplayName("Valid status transitions: DRAFT -> PUBLISHED -> ARCHIVED -> PUBLISHED")
    void validStatusTransitions() {
        UUID articleId = draftArticle.getId();
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(draftArticle));
        when(articleRepository.save(any(KbArticle.class))).thenAnswer(inv -> inv.getArgument(0));

        // DRAFT -> PUBLISHED
        KbArticleResponse published = kbService.transitionStatus(articleId, ArticleStatus.PUBLISHED, supportUser);
        assertThat(published.status()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(draftArticle.getPublishedAt()).isNotNull();

        // PUBLISHED -> ARCHIVED
        KbArticleResponse archived = kbService.transitionStatus(articleId, ArticleStatus.ARCHIVED, supportUser);
        assertThat(archived.status()).isEqualTo(ArticleStatus.ARCHIVED);

        // ARCHIVED -> PUBLISHED (republish)
        KbArticleResponse republished = kbService.transitionStatus(articleId, ArticleStatus.PUBLISHED, supportUser);
        assertThat(republished.status()).isEqualTo(ArticleStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Invalid status transition throws 400 Bad Request")
    void invalidStatusTransitionThrowsBadRequest() {
        UUID articleId = publishedArticle.getId();
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(publishedArticle));

        // PUBLISHED -> PUBLISHED is invalid
        assertThatThrownBy(() -> kbService.transitionStatus(articleId, ArticleStatus.PUBLISHED, supportUser))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Deflection returns empty list when query is shorter than 3 characters")
    void deflectionReturnsEmptyForShortQuery() {
        List<KbArticleDeflectionResponse> empty1 = kbService.getDeflectionSuggestions(null);
        List<KbArticleDeflectionResponse> empty2 = kbService.getDeflectionSuggestions("  ");
        List<KbArticleDeflectionResponse> empty3 = kbService.getDeflectionSuggestions("hi");

        assertThat(empty1).isEmpty();
        assertThat(empty2).isEmpty();
        assertThat(empty3).isEmpty();
    }

    @Test
    @DisplayName("Deflection returns top 3 matching published articles")
    void deflectionReturnsSuggestions() {
        when(articleRepository.findDeflectionSuggestions("VPN", 3)).thenReturn(List.of(publishedArticle));

        List<KbArticleDeflectionResponse> results = kbService.getDeflectionSuggestions("VPN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("How to Connect to VPN");
        assertThat(results.get(0).categoryName()).isEqualTo("VPN & Network");
    }

    @Test
    @DisplayName("Search published articles returns paginated response")
    void searchPublishedArticlesReturnsPage() {
        when(articleRepository.searchPublished(eq(category.getId()), eq("connect"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(publishedArticle)));

        PageResponse<KbArticleSummaryResponse> page = kbService.searchPublishedArticles(category.getId(), "connect", 0, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).title()).isEqualTo("How to Connect to VPN");
    }

    @Test
    @DisplayName("Create category generates unique slug and records audit log")
    void createCategorySuccess() {
        CreateKbCategoryRequest request = new CreateKbCategoryRequest("Software Tools", "Software tools and apps", "app", 3);
        when(slugService.generateUniqueSlug(eq("Software Tools"), any())).thenReturn("software-tools");
        when(categoryRepository.save(any(KbCategory.class))).thenAnswer(inv -> {
            KbCategory c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });

        KbCategoryResponse response = kbService.createCategory(request, supportUser);

        assertThat(response.name()).isEqualTo("Software Tools");
        assertThat(response.slug()).isEqualTo("software-tools");
        verify(auditService).recordUserAction(
                eq(supportUser),
                eq(AuditAction.KB_CATEGORY_CREATED),
                any(),
                any(),
                eq("software-tools"),
                any()
        );
    }
}
