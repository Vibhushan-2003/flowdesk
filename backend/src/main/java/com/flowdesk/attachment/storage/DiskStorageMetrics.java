package com.flowdesk.attachment.storage;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Micrometer metrics binder monitoring the persistent attachment storage filesystem.
 * Exposes:
 *   - flowdesk.disk.free.bytes: Usable free space on the persistent storage volume.
 *   - flowdesk.disk.total.bytes: Total capacity of the persistent storage volume.
 *   - flowdesk.disk.free.percent: Percentage of free space remaining (0.0 to 100.0).
 */
@Component
public class DiskStorageMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(DiskStorageMetrics.class);

    private final File storageDir;

    public DiskStorageMetrics(
            @Value("${app.storage.attachment.local.base-directory:data/attachments}")
            String storagePath
    ) {
        this.storageDir = new File(storagePath);
        if (!this.storageDir.exists()) {
            boolean created = this.storageDir.mkdirs();
            if (created) {
                log.info("Created attachment storage directory at: {}", this.storageDir.getAbsolutePath());
            }
        }
    }

    public long getFreeBytes() {
        return storageDir.getUsableSpace();
    }

    public long getTotalBytes() {
        return storageDir.getTotalSpace();
    }

    public double getFreePercent() {
        long total = getTotalBytes();
        if (total <= 0) {
            return 100.0;
        }
        return ((double) getFreeBytes() / (double) total) * 100.0;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("flowdesk.disk.free.bytes", this, DiskStorageMetrics::getFreeBytes)
                .description("Usable free disk space in bytes on persistent attachment storage")
                .baseUnit("bytes")
                .register(registry);

        Gauge.builder("flowdesk.disk.total.bytes", this, DiskStorageMetrics::getTotalBytes)
                .description("Total disk space capacity in bytes on persistent attachment storage")
                .baseUnit("bytes")
                .register(registry);

        Gauge.builder("flowdesk.disk.free.percent", this, DiskStorageMetrics::getFreePercent)
                .description("Percentage of free disk space remaining on persistent attachment storage")
                .baseUnit("percent")
                .register(registry);
    }
}

