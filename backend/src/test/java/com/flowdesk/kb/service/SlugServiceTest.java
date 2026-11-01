package com.flowdesk.kb.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugServiceTest {

    private final SlugService slugService = new SlugService();

    @Test
    @DisplayName("Converts title with accents, spaces, and punctuation to clean slug")
    void toSlugConvertsCleanly() {
        assertThat(slugService.toSlug("How to Connect to FlowDesk VPN?"))
                .isEqualTo("how-to-connect-to-flowdesk-vpn");

        assertThat(slugService.toSlug("Café & Résumé Upload: Step-by-Step!"))
                .isEqualTo("cafe-resume-upload-step-by-step");

        assertThat(slugService.toSlug("   multiple    spaces   and --- hyphens   "))
                .isEqualTo("multiple-spaces-and-hyphens");
    }

    @Test
    @DisplayName("Handles null and blank inputs safely")
    void handlesNullAndBlank() {
        assertThat(slugService.toSlug(null)).startsWith("untitled-");
        assertThat(slugService.toSlug("   ")).startsWith("untitled-");
        assertThat(slugService.toSlug("!!!@@@###$$$")).startsWith("untitled-");
    }

    @Test
    @DisplayName("Resolves slug collisions by appending counter")
    void resolvesSlugCollisions() {
        Set<String> existingSlugs = new SetBuilder()
                .add("vpn-guide")
                .add("vpn-guide-2")
                .add("vpn-guide-3")
                .build();

        String uniqueSlug = slugService.generateUniqueSlug("VPN Guide", existingSlugs::contains);

        assertThat(uniqueSlug).isEqualTo("vpn-guide-4");
    }

    @Test
    @DisplayName("Returns base slug if no collision")
    void returnsBaseSlugIfNoCollision() {
        Set<String> existingSlugs = Set.of("other-guide");

        String uniqueSlug = slugService.generateUniqueSlug("VPN Guide", existingSlugs::contains);

        assertThat(uniqueSlug).isEqualTo("vpn-guide");
    }

    private static class SetBuilder {
        private final Set<String> set = new HashSet<>();

        SetBuilder add(String s) {
            set.add(s);
            return this;
        }

        Set<String> build() {
            return set;
        }
    }
}
