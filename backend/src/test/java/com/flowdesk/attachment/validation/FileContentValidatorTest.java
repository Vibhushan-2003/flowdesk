package com.flowdesk.attachment.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileContentValidator Unit Tests")
class FileContentValidatorTest {

    private FileContentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileContentValidator();
    }

    @Nested
    @DisplayName("Filename Sanitization Tests")
    class FilenameSanitizationTests {

        @Test
        @DisplayName("Should sanitize simple safe filename")
        void shouldSanitizeSafeFilename() {
            String sanitized = validator.sanitizeFilename("document.pdf");
            assertEquals("document.pdf", sanitized);
        }

        @Test
        @DisplayName("Should strip path traversal sequences and directory separators")
        void shouldStripPathTraversal() {
            assertEquals("passwd.txt", validator.sanitizeFilename("../../etc/passwd.txt"));
            assertEquals("secret.pdf", validator.sanitizeFilename("..\\..\\windows\\secret.pdf"));
            assertEquals("report.csv", validator.sanitizeFilename("/var/log/report.csv"));
            assertEquals("archive.log", validator.sanitizeFilename("C:\\Users\\admin\\archive.log"));
        }

        @Test
        @DisplayName("Should replace special and control characters with underscores")
        void shouldReplaceSpecialCharacters() {
            assertEquals("my_report_final_1.pdf", validator.sanitizeFilename("my report (final) #1.pdf"));
            assertEquals("test_file.png", validator.sanitizeFilename("test$@!file.png"));
            assertEquals("test_file.txt", validator.sanitizeFilename("test\0file.txt"));
        }

        @Test
        @DisplayName("Should reject filename missing extension or missing name")
        void shouldRejectInvalidFilenames() {
            assertThrows(FileValidationException.class, () -> validator.sanitizeFilename(""));
            assertThrows(FileValidationException.class, () -> validator.sanitizeFilename("   "));
            assertThrows(FileValidationException.class, () -> validator.sanitizeFilename(null));
            assertThrows(FileValidationException.class, () -> validator.sanitizeFilename("no_extension"));
            assertThrows(FileValidationException.class, () -> validator.sanitizeFilename(".gitignore"));
            assertThrows(FileValidationException.class, () -> validator.sanitizeFilename("trailing_dot."));
        }

        @Test
        @DisplayName("Should truncate excessively long filenames while preserving extension")
        void shouldTruncateExcessivelyLongFilename() {
            String longName = "a".repeat(200) + ".pdf";
            String sanitized = validator.sanitizeFilename(longName);
            assertTrue(sanitized.length() <= FileContentValidator.MAX_FILENAME_LENGTH);
            assertTrue(sanitized.endsWith(".pdf"));
        }
    }

    @Nested
    @DisplayName("Binary Signature Validation Tests")
    class BinarySignatureTests {

        @Test
        @DisplayName("Should validate valid PDF file")
        void shouldValidatePdf() {
            byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
            FileValidationResult result = validator.validate("contract.pdf", "application/pdf", pdfBytes);

            assertEquals("contract.pdf", result.sanitizedFilename());
            assertEquals(AllowedAttachmentType.PDF, result.allowedType());
            assertEquals("application/pdf", result.canonicalContentType());
            assertEquals("pdf", result.extension());
            assertEquals(pdfBytes.length, result.sizeBytes());
            assertEquals(computeExpectedSha256(pdfBytes), result.checksumSha256());
        }

        @Test
        @DisplayName("Should validate valid PNG file")
        void shouldValidatePng() {
            byte[] pngBytes = new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
            };
            FileValidationResult result = validator.validate("screenshot.png", "image/png", pngBytes);

            assertEquals(AllowedAttachmentType.PNG, result.allowedType());
            assertEquals("image/png", result.canonicalContentType());
            assertEquals("png", result.extension());
        }

        @Test
        @DisplayName("Should validate valid JPEG file")
        void shouldValidateJpeg() {
            byte[] jpegBytes = new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
            };
            FileValidationResult result = validator.validate("photo.jpg", "image/jpeg", jpegBytes);

            assertEquals(AllowedAttachmentType.JPEG, result.allowedType());
            assertEquals("image/jpeg", result.canonicalContentType());
            assertEquals("jpg", result.extension());
        }

        @Test
        @DisplayName("Should validate valid WebP file")
        void shouldValidateWebp() {
            byte[] webpBytes = new byte[]{
                    0x52, 0x49, 0x46, 0x46, // RIFF
                    0x1A, 0x00, 0x00, 0x00, // length
                    0x57, 0x45, 0x42, 0x50, // WEBP
                    0x56, 0x50, 0x38, 0x20  // VP8
            };
            FileValidationResult result = validator.validate("banner.webp", "image/webp", webpBytes);

            assertEquals(AllowedAttachmentType.WEBP, result.allowedType());
            assertEquals("image/webp", result.canonicalContentType());
            assertEquals("webp", result.extension());
        }

        @Test
        @DisplayName("Should reject Windows executable masquerading as a PDF")
        void shouldRejectExecutableMasqueradingAsPdf() {
            // Starts with 'MZ' (DOS executable header) but named .pdf
            byte[] fakePdf = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

            FileValidationException exception = assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("invoice.pdf", "application/pdf", fakePdf)
            );
            assertTrue(exception.getMessage().contains("Invalid PDF format"));
        }

        @Test
        @DisplayName("Should reject random binary data masquerading as PNG or JPEG")
        void shouldRejectCorruptBinarySignatures() {
            byte[] randomBytes = "This is definitely not an image".getBytes(StandardCharsets.UTF_8);

            assertThrows(FileValidationException.class, () -> validator.validate("photo.png", "image/png", randomBytes));
            assertThrows(FileValidationException.class, () -> validator.validate("photo.jpg", "image/jpeg", randomBytes));
            assertThrows(FileValidationException.class, () -> validator.validate("photo.webp", "image/webp", randomBytes));
        }
    }

    @Nested
    @DisplayName("Text and CSV Content Validation Tests")
    class TextContentValidationTests {

        @Test
        @DisplayName("Should validate clean UTF-8 plain text file")
        void shouldValidateCleanTextFile() {
            byte[] textBytes = "Line 1: Server started.\nLine 2: Ready for connections.\n".getBytes(StandardCharsets.UTF_8);
            FileValidationResult result = validator.validate("server.log", "text/plain", textBytes);

            assertEquals(AllowedAttachmentType.LOG, result.allowedType());
            assertEquals("text/plain", result.canonicalContentType());
            assertEquals(textBytes.length, result.sizeBytes());
        }

        @Test
        @DisplayName("Should validate clean CSV file")
        void shouldValidateCleanCsv() {
            byte[] csvBytes = "id,name,role\n1,Alice,Engineer\n2,Bob,Support\n".getBytes(StandardCharsets.UTF_8);
            FileValidationResult result = validator.validate("users.csv", "text/csv", csvBytes);

            assertEquals(AllowedAttachmentType.CSV, result.allowedType());
            assertEquals("text/csv", result.canonicalContentType());
        }

        @Test
        @DisplayName("Should reject executable Windows PE binary disguised as .txt")
        void shouldRejectPeDisguisedAsText() {
            byte[] peBytes = new byte[]{0x4D, 0x5A, 0x00, 0x01, 0x02}; // MZ header
            FileValidationException ex = assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("notes.txt", "text/plain", peBytes)
            );
            assertTrue(ex.getMessage().contains("Executable Windows PE binary"));
        }

        @Test
        @DisplayName("Should reject Linux ELF binary disguised as .log")
        void shouldRejectElfDisguisedAsLog() {
            byte[] elfBytes = new byte[]{0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01}; // \x7fELF
            FileValidationException ex = assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("app.log", "text/plain", elfBytes)
            );
            assertTrue(ex.getMessage().contains("Executable Linux ELF binary"));
        }

        @Test
        @DisplayName("Should reject compiled Java class disguised as text")
        void shouldRejectClassDisguisedAsText() {
            byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00};
            FileValidationException ex = assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("exploit.txt", "text/plain", classBytes)
            );
            assertTrue(ex.getMessage().contains("Compiled Java class"));
        }

        @Test
        @DisplayName("Should reject shell script disguised as text")
        void shouldRejectShellScriptDisguisedAsText() {
            byte[] scriptBytes = "#!/bin/bash\nrm -rf /\n".getBytes(StandardCharsets.UTF_8);
            FileValidationException ex = assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("script.txt", "text/plain", scriptBytes)
            );
            assertTrue(ex.getMessage().contains("Executable script"));
        }

        @Test
        @DisplayName("Should reject text file containing binary null bytes")
        void shouldRejectNullBytesInText() {
            byte[] nullBytes = "Hello\0World".getBytes(StandardCharsets.ISO_8859_1);
            FileValidationException ex = assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("nulls.txt", "text/plain", nullBytes)
            );
            assertTrue(ex.getMessage().contains("null bytes"));
        }

        @Test
        @DisplayName("Should reject text file containing HTML script tags (XSS prevention)")
        void shouldRejectHtmlScriptTagsInText() {
            byte[] htmlBytes = "<script>alert('xss')</script>".getBytes(StandardCharsets.UTF_8);
            FileValidationException ex = assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("payload.txt", "text/plain", htmlBytes)
            );
            assertTrue(ex.getMessage().contains("Active HTML/script content"));
        }

        @Test
        @DisplayName("Should reject text file containing SVG or iframe elements")
        void shouldRejectSvgOrIframeInText() {
            byte[] svgBytes = "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8);
            assertThrows(FileValidationException.class, () -> validator.validate("drawing.txt", "text/plain", svgBytes));

            byte[] iframeBytes = "<iframe src='http://evil.com'></iframe>".getBytes(StandardCharsets.UTF_8);
            assertThrows(FileValidationException.class, () -> validator.validate("data.csv", "text/csv", iframeBytes));
        }
    }

    @Nested
    @DisplayName("Extension and Content-Type Constraint Tests")
    class ExtensionAndTypeTests {

        @Test
        @DisplayName("Should reject strictly prohibited file extensions")
        void shouldRejectProhibitedExtensions() {
            byte[] data = "dummy content".getBytes(StandardCharsets.UTF_8);

            assertThrows(FileValidationException.class, () -> validator.validate("script.sh", "application/x-sh", data));
            assertThrows(FileValidationException.class, () -> validator.validate("app.exe", "application/x-msdownload", data));
            assertThrows(FileValidationException.class, () -> validator.validate("vector.svg", "image/svg+xml", data));
            assertThrows(FileValidationException.class, () -> validator.validate("page.html", "text/html", data));
            assertThrows(FileValidationException.class, () -> validator.validate("bundle.zip", "application/zip", data));
            assertThrows(FileValidationException.class, () -> validator.validate("app.js", "application/javascript", data));
        }

        @Test
        @DisplayName("Should reject conflicting declared Content-Type")
        void shouldRejectConflictingDeclaredContentType() {
            byte[] pdfBytes = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);
            // PDF file claiming to be an image
            assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("doc.pdf", "image/png", pdfBytes)
            );
        }

        @Test
        @DisplayName("Should accept generic application/octet-stream Content-Type from browsers")
        void shouldAcceptGenericOctetStream() {
            byte[] pdfBytes = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);
            FileValidationResult result = validator.validate("doc.pdf", "application/octet-stream", pdfBytes);
            assertEquals("application/pdf", result.canonicalContentType());
        }

        @Test
        @DisplayName("Should reject 0-byte empty file")
        void shouldRejectEmptyFile() {
            assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("empty.pdf", "application/pdf", new ByteArrayInputStream(new byte[0]), 0)
            );
        }

        @Test
        @DisplayName("Should reject file exceeding 10 MB limit")
        void shouldRejectOversizedFile() {
            long oversized = FileContentValidator.MAX_FILE_SIZE_BYTES + 1;
            assertThrows(
                    FileValidationException.class,
                    () -> validator.validate("large.pdf", "application/pdf", new ByteArrayInputStream(new byte[10]), oversized)
            );
        }
    }

    private static String computeExpectedSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
