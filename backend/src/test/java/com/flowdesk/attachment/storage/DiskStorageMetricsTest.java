package com.flowdesk.attachment.storage;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiskStorageMetricsTest {

    @Test
    @DisplayName("DiskStorageMetrics binds free, total, and percent gauges to registry")
    void bindsDiskStorageGauges(@TempDir Path tempDir) {
        String testStoragePath = tempDir.resolve("attachments").toString();

        DiskStorageMetrics metrics = new DiskStorageMetrics(testStoragePath);
        MeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);

        Gauge freeGauge = registry.find("flowdesk.disk.free.bytes").gauge();
        Gauge totalGauge = registry.find("flowdesk.disk.total.bytes").gauge();
        Gauge percentGauge = registry.find("flowdesk.disk.free.percent").gauge();

        assertThat(freeGauge).isNotNull();
        assertThat(totalGauge).isNotNull();
        assertThat(percentGauge).isNotNull();

        assertThat(freeGauge.value()).isGreaterThan(0.0);
        assertThat(totalGauge.value()).isGreaterThan(0.0);
        assertThat(percentGauge.value()).isGreaterThan(0.0).isLessThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("Directory is created if it does not already exist")
    void createsDirectoryIfNotExists(@TempDir Path tempDir) {
        Path newDirPath = tempDir.resolve("non_existent_subdir");
        assertThat(newDirPath.toFile().exists()).isFalse();

        DiskStorageMetrics metrics = new DiskStorageMetrics(newDirPath.toString());
        assertThat(newDirPath.toFile().exists()).isTrue();
    }
}

