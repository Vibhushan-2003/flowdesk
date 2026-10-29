package com.flowdesk.kb.repository;

import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.domain.KbArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbArticleRepository extends JpaRepository<KbArticle, UUID> {

    Optional<KbArticle> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    long countByCategoryId(UUID categoryId);

    long countByCategoryIdAndStatus(UUID categoryId, ArticleStatus status);

    @Query(value = """
        SELECT a.* FROM kb_articles a
        WHERE a.status = 'PUBLISHED'
          AND (:categoryId IS NULL OR a.category_id = :categoryId)
          AND (
              :query IS NULL OR btrim(:query) = '' OR
              a.search_vector @@ plainto_tsquery('english', :query) OR
              LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(a.summary) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY
          CASE WHEN :query IS NOT NULL AND btrim(:query) <> '' AND a.search_vector @@ plainto_tsquery('english', :query)
               THEN ts_rank_cd(a.search_vector, plainto_tsquery('english', :query))
               ELSE 0.0 END DESC,
          a.view_count DESC,
          a.published_at DESC
        """,
        countQuery = """
        SELECT count(*) FROM kb_articles a
        WHERE a.status = 'PUBLISHED'
          AND (:categoryId IS NULL OR a.category_id = :categoryId)
          AND (
              :query IS NULL OR btrim(:query) = '' OR
              a.search_vector @@ plainto_tsquery('english', :query) OR
              LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(a.summary) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """,
        nativeQuery = true)
    Page<KbArticle> searchPublished(
        @Param("categoryId") UUID categoryId,
        @Param("query") String query,
        Pageable pageable
    );

    @Query(value = """
        SELECT a.* FROM kb_articles a
        WHERE a.status = 'PUBLISHED'
          AND (
              a.search_vector @@ plainto_tsquery('english', :query) OR
              LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(a.summary) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY
          CASE WHEN a.search_vector @@ plainto_tsquery('english', :query)
               THEN ts_rank_cd(a.search_vector, plainto_tsquery('english', :query))
               ELSE 0.1 END DESC,
          a.view_count DESC
        LIMIT :limit
        """,
        nativeQuery = true)
    List<KbArticle> findDeflectionSuggestions(
        @Param("query") String query,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT a.* FROM kb_articles a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:categoryId IS NULL OR a.category_id = :categoryId)
          AND (
              :query IS NULL OR btrim(:query) = '' OR
              a.search_vector @@ plainto_tsquery('english', :query) OR
              LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(a.summary) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY a.updated_at DESC
        """,
        countQuery = """
        SELECT count(*) FROM kb_articles a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:categoryId IS NULL OR a.category_id = :categoryId)
          AND (
              :query IS NULL OR btrim(:query) = '' OR
              a.search_vector @@ plainto_tsquery('english', :query) OR
              LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(a.summary) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """,
        nativeQuery = true)
    Page<KbArticle> searchManagement(
        @Param("status") String status,
        @Param("categoryId") UUID categoryId,
        @Param("query") String query,
        Pageable pageable
    );
}
