package com.flowdesk.attachment.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local filesystem implementation of {@link AttachmentStorageService}.
 * Enforces strict path traversal protection, server-generated opaque UUID keys,
 * bounded-memory streaming, and atomic file publication.
 */
@Service
public class LocalDiskAttachmentStorageService implements AttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskAttachmentStorageService.class);

    private static final Pattern OPAQUE_KEY_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.dat$");

    private static final int BUFFER_SIZE = 8192;
    private static final String TMP_SUBDIR = ".tmp";

    private final Path baseDirectory;
    private final Path tempDirectory;
    private final long maxFileSizeBytes;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalDiskAttachmentStorageService(AttachmentStorageProperties properties) {
        Objects.requireNonNull(properties, "AttachmentStorageProperties is required");
        this.baseDirectory = properties.getLocal().getBasePath();
        this.tempDirectory = this.baseDirectory.resolve(TMP_SUBDIR);
        this.maxFileSizeBytes = properties.getMaxFileSizeBytes();
    }

    /**
     * Direct constructor for unit testing with custom directories and limits.
     */
    public LocalDiskAttachmentStorageService(Path baseDirectory, long maxFileSizeBytes) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory is required").normalize().toAbsolutePath();
        this.tempDirectory = this.baseDirectory.resolve(TMP_SUBDIR);
        this.maxFileSizeBytes = maxFileSizeBytes;
        initDirectories();
    }

    @PostConstruct
    public void initDirectories() {
        try {
            Files.createDirectories(baseDirectory);
            Files.createDirectories(tempDirectory);
            log.info("Initialized local attachment storage directory at: {}", baseDirectory);
        } catch (IOException e) {
            throw new StorageException("Failed to initialize attachment storage directories at: " + baseDirectory, e);
        }
    }

    @Override
    public StoredFile store(InputStream contentStream, long expectedSize) {
        Objects.requireNonNull(contentStream, "contentStream is required");

        if (expectedSize > maxFileSizeBytes) {
            throw new StorageException("File size exceeds maximum allowed limit of " + maxFileSizeBytes + " bytes");
        }

        String storageKey = UUID.randomUUID() + ".dat";
        Path targetPath = resolveAndValidate(storageKey);

        if (Files.exists(targetPath)) {
            throw new StorageException("Storage key collision detected for key: " + storageKey);
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(tempDirectory, "upload-", ".tmp");
            MessageDigest digest = createSha256Digest();
            long totalBytesWritten = 0;

            try (InputStream in = new BufferedInputStream(contentStream);
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile, StandardOpenOption.WRITE))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    totalBytesWritten += bytesRead;
                    if (totalBytesWritten > maxFileSizeBytes) {
                        throw new StorageException("Attachment stream exceeded maximum size of "
                                + maxFileSizeBytes + " bytes");
                    }
                    digest.update(buffer, 0, bytesRead);
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            if (totalBytesWritten == 0) {
                throw new StorageException("Attachment content cannot be empty (0 bytes)");
            }

            // Atomic publication to final storage path
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                log.warn("Atomic move not supported by filesystem, falling back to replace move");
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String sha256Hex = HexFormat.of().formatHex(digest.digest());
            return new StoredFile(storageKey, totalBytesWritten, sha256Hex);

        } catch (IOException e) {
            throw new StorageException("Failed to write attachment to storage", e);
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Suppress cleanup exception
                }
            }
        }
    }

    @Override
    public InputStream load(String storageKey) {
        Path targetPath = resolveAndValidate(storageKey);

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new StorageException("Attachment file not found: " + storageKey);
        }

        try {
            return Files.newInputStream(targetPath, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new StorageException("Failed to read attachment file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path targetPath = resolveAndValidate(storageKey);
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            throw new StorageException("Failed to delete attachment: " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            Path targetPath = resolveAndValidate(storageKey);
            return Files.isRegularFile(targetPath);
        } catch (StoragePathTraversalException e) {
            return false;
        }
    }

    @Override
    public long size(String storageKey) {
        Path targetPath = resolveAndValidate(storageKey);
        if (!Files.isRegularFile(targetPath)) {
            throw new StorageException("Attachment file not found: " + storageKey);
        }
        try {
            return Files.size(targetPath);
        } catch (IOException e) {
            throw new StorageException("Failed to check size for attachment: " + storageKey, e);
        }
    }

    /**
     * Resolves a storage key against the base directory while strictly enforcing
     * opaque format, directory confinement, and symbolic link rejection.
     */
    public Path resolveAndValidate(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StoragePathTraversalException("Storage key cannot be null or blank");
        }

        if (!OPAQUE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            throw new StoragePathTraversalException("Storage key contains invalid characters or does not match expected opaque format: " + storageKey);
        }

        Path resolved = baseDirectory.resolve(storageKey.trim()).normalize();

        if (!resolved.startsWith(baseDirectory)) {
            throw new StoragePathTraversalException("Storage path escapes configured base directory: " + storageKey);
        }

        if (Files.exists(resolved) && Files.isSymbolicLink(resolved)) {
            throw new StoragePathTraversalException("Symbolic links are not permitted in storage paths: " + storageKey);
        }

        return resolved;
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest algorithm not available", e);
        }
    }
}
