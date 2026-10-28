package com.flowdesk.attachment.storage;

import java.io.InputStream;

/**
 * Storage provider interface separating attachment storage abstractions
 * from the underlying filesystem or future object storage providers.
 */
public interface AttachmentStorageService {

    /**
     * Stores the content stream into private storage under a server-generated opaque key.
     * Streams data with bounded memory usage, computes SHA-256 on the fly, and guarantees
     * atomic publication where supported.
     *
     * @param contentStream input stream containing attachment data
     * @param expectedSize expected size in bytes, or -1 if unknown
     * @return StoredFile metadata containing the opaque storage key, final byte count, and SHA-256
     */
    StoredFile store(InputStream contentStream, long expectedSize);

    /**
     * Loads the stored attachment as an InputStream.
     *
     * @param storageKey server-generated opaque key
     * @return readable stream
     */
    InputStream load(String storageKey);

    /**
     * Deletes the stored attachment identified by the opaque storage key.
     * Silently succeeds if the file does not exist.
     *
     * @param storageKey server-generated opaque key
     */
    void delete(String storageKey);

    /**
     * Checks if the attachment exists in storage.
     *
     * @param storageKey server-generated opaque key
     * @return true if the attachment file exists
     */
    boolean exists(String storageKey);

    /**
     * Retrieves the stored file size in bytes.
     *
     * @param storageKey server-generated opaque key
     * @return size in bytes
     */
    long size(String storageKey);
}
