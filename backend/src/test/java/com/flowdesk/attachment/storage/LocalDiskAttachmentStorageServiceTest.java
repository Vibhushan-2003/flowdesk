package com.flowdesk.attachment.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalDiskAttachmentStorageService Unit Tests")
class LocalDiskAttachmentStorageServiceTest {

    private static final Pattern OPAQUE_KEY_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.dat$");

    @TempDir
    Path tempStorageDir;

    private LocalDiskAttachmentStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalDiskAttachmentStorageService(tempStorageDir, 1024 * 1024L); // 1 MB limit for tests
    }

    @Nested
    @DisplayName("Storage and Retrieval Tests")
    class StorageAndRetrievalTests {

        @Test
        @DisplayName("Should store file atomically under server-generated opaque key and compute SHA-256")
        void shouldStoreFileAtomically() throws IOException {
            byte[] content = "Hello FlowDesk Attachment Storage!".getBytes(StandardCharsets.UTF_8);
            StoredFile storedFile = storageService.store(new ByteArrayInputStream(content), content.length);

            assertNotNull(storedFile);
            assertNotNull(storedFile.storageKey());
            assertTrue(OPAQUE_KEY_PATTERN.matcher(storedFile.storageKey()).matches(),
                    "Storage key must be an opaque UUID ending in .dat");
            assertEquals(content.length, storedFile.sizeBytes());
            assertEquals(computeSha256(content), storedFile.checksumSha256());

            // Verify file exists on disk in private storage
            assertTrue(storageService.exists(storedFile.storageKey()));
            assertEquals(content.length, storageService.size(storedFile.storageKey()));

            // Verify content read matches original
            try (InputStream in = storageService.load(storedFile.storageKey())) {
                byte[] readBytes = in.readAllBytes();
                assertArrayEquals(content, readBytes);
            }
        }

        @Test
        @DisplayName("Should reject storing empty content (0 bytes)")
        void shouldRejectEmptyContent() {
            StorageException ex = assertThrows(
                    StorageException.class,
                    () -> storageService.store(new ByteArrayInputStream(new byte[0]), 0)
            );
            assertTrue(ex.getMessage().contains("cannot be empty"));
        }

        @Test
        @DisplayName("Should reject stream exceeding maximum configured file size")
        void shouldRejectOversizedStream() {
            byte[] oversizedContent = new byte[1024 * 1024 + 10]; // Exceeds 1 MB test limit
            StorageException ex = assertThrows(
                    StorageException.class,
                    () -> storageService.store(new ByteArrayInputStream(oversizedContent), oversizedContent.length)
            );
            assertTrue(ex.getMessage().contains("exceeds maximum"));

            // Verify no partial files were left in the base storage dir
            try (var stream = Files.list(tempStorageDir)) {
                long storedFiles = stream.filter(p -> !p.getFileName().toString().equals(".tmp")).count();
                assertEquals(0, storedFiles, "No orphaned or partial files should remain after failure");
            } catch (IOException e) {
                fail(e);
            }
        }
    }

    @Nested
    @DisplayName("Path Traversal and Security Defense Tests")
    class PathTraversalDefenseTests {

        @Test
        @DisplayName("Should reject path traversal sequences in storage key")
        void shouldRejectPathTraversalSequences() {
            assertThrows(StoragePathTraversalException.class, () -> storageService.load("../../etc/passwd"));
            assertThrows(StoragePathTraversalException.class, () -> storageService.load("..\\..\\windows\\system32"));
            assertThrows(StoragePathTraversalException.class, () -> storageService.delete("../secret.dat"));
            assertThrows(StoragePathTraversalException.class, () -> storageService.size("../secret.dat"));
            assertFalse(storageService.exists("../../etc/passwd"));
        }

        @Test
        @DisplayName("Should reject user-supplied arbitrary filenames as storage keys")
        void shouldRejectNonOpaqueStorageKeys() {
            assertThrows(StoragePathTraversalException.class, () -> storageService.load("invoice.pdf"));
            assertThrows(StoragePathTraversalException.class, () -> storageService.load("test.txt"));
            assertThrows(StoragePathTraversalException.class, () -> storageService.delete("script.sh"));
            assertFalse(storageService.exists("any_user_file.png"));
        }

        @Test
        @DisplayName("Should reject null or blank storage keys")
        void shouldRejectNullOrBlankKeys() {
            assertThrows(StoragePathTraversalException.class, () -> storageService.load(null));
            assertThrows(StoragePathTraversalException.class, () -> storageService.load("   "));
            assertThrows(StoragePathTraversalException.class, () -> storageService.delete(""));
            assertFalse(storageService.exists(null));
        }

        @Test
        @DisplayName("Should reject symbolic link storage paths")
        void shouldRejectSymbolicLinkStoragePaths() throws IOException {
            // Create target file outside base dir
            Path outsideFile = Files.createTempFile("outside-", ".dat");
            try {
                String opaqueKey = "11111111-2222-3333-4444-555555555555.dat";
                Path symlinkPath = tempStorageDir.resolve(opaqueKey);

                try {
                    Files.createSymbolicLink(symlinkPath, outsideFile);
                    assertThrows(StoragePathTraversalException.class, () -> storageService.load(opaqueKey));
                    assertThrows(StoragePathTraversalException.class, () -> storageService.delete(opaqueKey));
                } catch (UnsupportedOperationException | SecurityException | IOException e) {
                    // Some Windows environments without SeCreateSymbolicLinkPrivilege may not support symlink creation;
                    // the code path is still exercised if supported.
                }
            } finally {
                Files.deleteIfExists(outsideFile);
            }
        }
    }

    @Nested
    @DisplayName("Deletion and Non-Existent File Tests")
    class DeletionTests {

        @Test
        @DisplayName("Should delete existing file safely")
        void shouldDeleteExistingFile() {
            byte[] content = "Data to delete".getBytes(StandardCharsets.UTF_8);
            StoredFile stored = storageService.store(new ByteArrayInputStream(content), content.length);
            assertTrue(storageService.exists(stored.storageKey()));

            storageService.delete(stored.storageKey());
            assertFalse(storageService.exists(stored.storageKey()));
        }

        @Test
        @DisplayName("Should succeed silently when deleting non-existent opaque key")
        void shouldSucceedSilentlyWhenDeletingNonExistentKey() {
            String nonExistentKey = "00000000-0000-0000-0000-000000000000.dat";
            assertDoesNotThrow(() -> storageService.delete(nonExistentKey));
        }

        @Test
        @DisplayName("Should throw StorageException when loading non-existent file")
        void shouldThrowWhenLoadingNonExistentFile() {
            String nonExistentKey = "00000000-0000-0000-0000-000000000000.dat";
            StorageException ex = assertThrows(StorageException.class, () -> storageService.load(nonExistentKey));
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    private static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
