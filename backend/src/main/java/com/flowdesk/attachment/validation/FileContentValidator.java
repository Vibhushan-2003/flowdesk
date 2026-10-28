package com.flowdesk.attachment.validation;

import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates file content, format compliance, structural signatures, and filename sanitization.
 *
 * <p><strong>Important Security Notice:</strong> Structural signature and format inspection
 * verifies file conformity to permitted format specifications. It is NOT malware scanning
 * and does not guarantee that a file is malware-free.
 */
@Component
public class FileContentValidator {

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB
    public static final int MAX_FILENAME_LENGTH = 150;
    private static final int HEADER_INSPECTION_BYTES = 8192;

    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "bash", "ps1", "vbs", "js", "mjs", "ts",
            "html", "htm", "xhtml", "svg", "php", "phtml", "jsp", "asp", "aspx",
            "jar", "war", "ear", "zip", "tar", "gz", "7z", "rar", "dll", "so", "dylib",
            "com", "scr", "msi", "bin", "py", "rb", "pl"
    );

    // Active web/executable script indicators rejected in text files
    private static final Pattern SCRIPTABLE_TAG_PATTERN = Pattern.compile(
            "(?i)<\\s*(script|html|!doctype|svg|iframe|embed|object|applet)|<\\?php|javascript:"
    );

    // Magic byte definitions
    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_MAGIC = new byte[]{0x52, 0x49, 0x46, 0x46}; // RIFF
    private static final byte[] WEBP_MAGIC = new byte[]{0x57, 0x45, 0x42, 0x50}; // WEBP

    // Dangerous binary signatures checked in text files
    private static final byte[] DOS_PE_MAGIC = new byte[]{0x4D, 0x5A}; // MZ
    private static final byte[] ELF_MAGIC = new byte[]{0x7F, 0x45, 0x4C, 0x46}; // \x7fELF
    private static final byte[] JAVA_CLASS_MAGIC = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04}; // PK..
    private static final byte[] SHEBANG_MAGIC = new byte[]{0x23, 0x21}; // #!

    /**
     * Validates an in-memory byte array attachment.
     */
    public FileValidationResult validate(String originalFilename, String declaredContentType, byte[] contentBytes) {
        if (contentBytes == null || contentBytes.length == 0) {
            throw new FileValidationException("Attachment file cannot be empty (0 bytes)");
        }
        return validate(originalFilename, declaredContentType, new ByteArrayInputStream(contentBytes), contentBytes.length);
    }

    /**
     * Validates a stored local file.
     */
    public FileValidationResult validateFile(Path filePath, String originalFilename, String declaredContentType) {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileValidationException("Attachment file does not exist on disk");
        }
        try {
            long size = Files.size(filePath);
            try (InputStream in = Files.newInputStream(filePath)) {
                return validate(originalFilename, declaredContentType, in, size);
            }
        } catch (IOException e) {
            throw new FileValidationException("Failed to read file for validation", e);
        }
    }

    /**
     * Validates an attachment stream, computing SHA-256 while inspecting file signatures.
     */
    public FileValidationResult validate(String originalFilename, String declaredContentType, InputStream inputStream, long declaredSize) {
        if (declaredSize <= 0) {
            throw new FileValidationException("Attachment file cannot be empty (0 bytes)");
        }

        if (declaredSize > MAX_FILE_SIZE_BYTES) {
            throw new FileValidationException("File size (" + declaredSize + " bytes) exceeds maximum permitted limit of 10 MB");
        }

        String sanitizedFilename = sanitizeFilename(originalFilename);
        String extension = extractExtension(sanitizedFilename);

        if (FORBIDDEN_EXTENSIONS.contains(extension)) {
            throw new FileValidationException("Files with extension '." + extension + "' are strictly prohibited");
        }

        AllowedAttachmentType allowedType = AllowedAttachmentType.fromExtension(extension)
                .orElseThrow(() -> new FileValidationException("Unsupported file extension: '." + extension + "'"));

        validateClaimedMimeType(declaredContentType, allowedType);

        try {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            bis.mark(HEADER_INSPECTION_BYTES);

            byte[] headerBytes = new byte[HEADER_INSPECTION_BYTES];
            int headerRead = bis.read(headerBytes);

            if (headerRead <= 0) {
                throw new FileValidationException("Attachment file cannot be empty (0 bytes)");
            }

            byte[] actualHeader = new byte[headerRead];
            System.arraycopy(headerBytes, 0, actualHeader, 0, headerRead);

            // Inspect signatures and content structure
            if (allowedType.isBinary()) {
                inspectBinarySignature(actualHeader, allowedType);
            } else {
                inspectTextContent(actualHeader, allowedType);
            }

            // Reset stream and calculate SHA-256 across entire content
            bis.reset();
            MessageDigest digest = createSha256Digest();
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;

            while ((bytesRead = bis.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > MAX_FILE_SIZE_BYTES) {
                    throw new FileValidationException("File size exceeds maximum permitted limit of 10 MB");
                }
                digest.update(buffer, 0, bytesRead);
            }

            if (totalBytesRead == 0) {
                throw new FileValidationException("Attachment file cannot be empty (0 bytes)");
            }

            String sha256Hex = HexFormat.of().formatHex(digest.digest());

            return new FileValidationResult(
                    sanitizedFilename,
                    allowedType,
                    allowedType.getCanonicalMimeType(),
                    extension,
                    totalBytesRead,
                    sha256Hex
            );

        } catch (IOException e) {
            throw new FileValidationException("Failed to read attachment stream for validation", e);
        }
    }

    /**
     * Sanitizes original filenames for safe display, logging and download headers.
     * Prevents path traversal, directory components, control characters and excessive length.
     */
    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new FileValidationException("Filename is required");
        }

        // Strip path traversal and directory separators
        String name = originalFilename.trim();
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Replace null bytes and non-printable control characters with underscores
        name = name.replace("\0", "_").replaceAll("[\\p{Cntrl}]", "_");

        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == name.length() - 1) {
            throw new FileValidationException("Filename must contain a valid name and extension");
        }

        String baseName = name.substring(0, lastDot);
        String extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);

        // Sanitize base name: allow alphanumeric, underscore, dash, dot
        String cleanBase = baseName.replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_")
                .trim();

        if (cleanBase.isEmpty() || cleanBase.equals("_")) {
            cleanBase = "attachment";
        }

        // Limit base length to 100 chars
        if (cleanBase.length() > 100) {
            cleanBase = cleanBase.substring(0, 100);
        }

        String sanitized = cleanBase + "." + extension;
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
        }

        return sanitized;
    }

    private void inspectBinarySignature(byte[] header, AllowedAttachmentType type) {
        switch (type) {
            case PDF -> {
                if (!startsWith(header, PDF_MAGIC)) {
                    throw new FileValidationException("Invalid PDF format: file does not begin with '%PDF-' header");
                }
            }
            case PNG -> {
                if (!startsWith(header, PNG_MAGIC)) {
                    throw new FileValidationException("Invalid PNG format: file does not match PNG signature");
                }
            }
            case JPEG -> {
                if (!startsWith(header, JPEG_MAGIC)) {
                    throw new FileValidationException("Invalid JPEG format: file does not begin with SOI marker");
                }
            }
            case WEBP -> {
                if (header.length < 12 || !startsWith(header, RIFF_MAGIC) || !matchesAt(header, WEBP_MAGIC, 8)) {
                    throw new FileValidationException("Invalid WebP format: file missing RIFF/WEBP signature");
                }
            }
            default -> throw new FileValidationException("Unsupported binary format: " + type);
        }
    }

    private void inspectTextContent(byte[] header, AllowedAttachmentType type) {
        // Reject dangerous executable and archive headers disguised as text
        if (startsWith(header, DOS_PE_MAGIC)) {
            throw new FileValidationException("Executable Windows PE binary disguised as text file");
        }
        if (startsWith(header, ELF_MAGIC)) {
            throw new FileValidationException("Executable Linux ELF binary disguised as text file");
        }
        if (startsWith(header, JAVA_CLASS_MAGIC)) {
            throw new FileValidationException("Compiled Java class disguised as text file");
        }
        if (startsWith(header, ZIP_MAGIC)) {
            throw new FileValidationException("Archive file disguised as text file");
        }
        if (startsWith(header, SHEBANG_MAGIC)) {
            throw new FileValidationException("Executable script disguised as text file");
        }

        // Check for binary null bytes
        for (byte b : header) {
            if (b == 0x00) {
                throw new FileValidationException("Binary null bytes detected in text attachment");
            }
        }

        // Validate UTF-8 decoding
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(header))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new FileValidationException("Text attachment is not valid UTF-8 encoded text", e);
        }

        // Reject active HTML, SVG or script tags in plain text / CSV files
        if (SCRIPTABLE_TAG_PATTERN.matcher(text).find()) {
            throw new FileValidationException("Active HTML/script content is not permitted in text attachments");
        }
    }

    private void validateClaimedMimeType(String declaredContentType, AllowedAttachmentType allowedType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return; // Accept empty/missing header and rely on content inspection
        }

        String declared = declaredContentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = declared.indexOf(';');
        if (semicolon >= 0) {
            declared = declared.substring(0, semicolon).trim();
        }

        // Generic stream type is acceptable from browsers
        if (declared.equals("application/octet-stream")) {
            return;
        }

        // Allowed content type compatibility checks
        if (allowedType == AllowedAttachmentType.PDF && !declared.equals("application/pdf") && !declared.equals("application/x-pdf")) {
            throw new FileValidationException("Declared Content-Type '" + declared + "' does not match PDF format");
        }
        if (allowedType == AllowedAttachmentType.PNG && !declared.equals("image/png")) {
            throw new FileValidationException("Declared Content-Type '" + declared + "' does not match PNG format");
        }
        if (allowedType == AllowedAttachmentType.JPEG && !declared.equals("image/jpeg") && !declared.equals("image/jpg") && !declared.equals("image/pjpeg")) {
            throw new FileValidationException("Declared Content-Type '" + declared + "' does not match JPEG format");
        }
        if (allowedType == AllowedAttachmentType.WEBP && !declared.equals("image/webp")) {
            throw new FileValidationException("Declared Content-Type '" + declared + "' does not match WebP format");
        }
        if ((allowedType == AllowedAttachmentType.TXT || allowedType == AllowedAttachmentType.LOG)
                && !declared.startsWith("text/plain") && !declared.equals("text/x-log")) {
            throw new FileValidationException("Declared Content-Type '" + declared + "' does not match text format");
        }
        if (allowedType == AllowedAttachmentType.CSV
                && !declared.equals("text/csv") && !declared.equals("text/plain") && !declared.equals("application/csv") && !declared.equals("application/vnd.ms-excel")) {
            throw new FileValidationException("Declared Content-Type '" + declared + "' does not match CSV format");
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAt(byte[] data, byte[] expected, int offset) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
