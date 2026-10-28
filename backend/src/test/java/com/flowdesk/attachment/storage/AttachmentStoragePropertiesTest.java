package com.flowdesk.attachment.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AttachmentStorageProperties Unit Tests")
class AttachmentStoragePropertiesTest {

    @Test
    @DisplayName("Should have secure default configuration values")
    void shouldHaveSecureDefaults() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();

        assertEquals(10 * 1024 * 1024L, properties.getMaxFileSizeBytes());
        assertEquals(10, properties.getMaxAttachmentsPerTicket());
        assertEquals("data/attachments", properties.getLocal().getBaseDirectory());
        assertNotNull(properties.getLocal().getBasePath());
    }

    @Test
    @DisplayName("Should configure custom values successfully")
    void shouldConfigureCustomValues() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();

        properties.setMaxFileSizeBytes(5 * 1024 * 1024L);
        properties.setMaxAttachmentsPerTicket(5);
        properties.getLocal().setBaseDirectory("custom/storage/path");

        assertEquals(5 * 1024 * 1024L, properties.getMaxFileSizeBytes());
        assertEquals(5, properties.getMaxAttachmentsPerTicket());
        assertEquals("custom/storage/path", properties.getLocal().getBaseDirectory());
        assertTrue(properties.getLocal().getBasePath().toString().endsWith("custom\\storage\\path")
                || properties.getLocal().getBasePath().toString().endsWith("custom/storage/path"));
    }

    @Test
    @DisplayName("Should reject invalid non-positive limits and blank directory")
    void shouldRejectInvalidLimits() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxFileSizeBytes(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxFileSizeBytes(-100));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttachmentsPerTicket(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttachmentsPerTicket(-5));
        assertThrows(IllegalArgumentException.class, () -> properties.getLocal().setBaseDirectory(""));
        assertThrows(IllegalArgumentException.class, () -> properties.getLocal().setBaseDirectory("   "));
        assertThrows(IllegalArgumentException.class, () -> properties.getLocal().setBaseDirectory(null));
    }
}
