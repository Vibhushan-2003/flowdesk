package com.flowdesk.kb.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class SlugService {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-+");

    public String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "untitled-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String noWhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = MULTI_HYPHEN.matcher(slug).replaceAll("-");

        // Trim leading and trailing hyphens
        slug = slug.replaceAll("^-+|-+$", "").toLowerCase(Locale.ENGLISH);

        if (slug.isBlank()) {
            return "untitled-" + UUID.randomUUID().toString().substring(0, 8);
        }

        // Limit length to avoid exceeding column limits
        if (slug.length() > 200) {
            slug = slug.substring(0, 200).replaceAll("-+$", "");
        }

        return slug;
    }

    public String generateUniqueSlug(String title, Predicate<String> existsPredicate) {
        String baseSlug = toSlug(title);
        if (!existsPredicate.test(baseSlug)) {
            return baseSlug;
        }

        for (int i = 2; i <= 100; i++) {
            String candidate = baseSlug + "-" + i;
            if (!existsPredicate.test(candidate)) {
                return candidate;
            }
        }

        return baseSlug + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
