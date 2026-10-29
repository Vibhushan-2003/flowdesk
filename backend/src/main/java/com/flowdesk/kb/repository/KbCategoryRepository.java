package com.flowdesk.kb.repository;

import com.flowdesk.kb.domain.KbCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbCategoryRepository extends JpaRepository<KbCategory, UUID> {

    List<KbCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<KbCategory> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<KbCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
