package org.apache.seatunnel.web.api.service.file;

import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.LocalFileScanRequest;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

class LocalSyncFileScannerTest {

    private final LocalSyncFileScanner scanner = new LocalSyncFileScanner();

    @TempDir
    private Path tempDir;

    @Test
    void scanShouldReturnOnlyTopLevelFilesWhenRecursiveDisabled() throws Exception {
        Path topLevel = Files.writeString(tempDir.resolve("a.json"), "{}");
        Files.createDirectories(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested/b.json"), "{}");

        List<DiscoveredFile> files = scanner.scan(LocalFileScanRequest.builder()
                .basePath(tempDir.toString())
                .recursive(false)
                .build());

        Assertions.assertEquals(1, files.size());
        Assertions.assertEquals(topLevel.toAbsolutePath().normalize().toString(), files.get(0).getAbsolutePath());
        Assertions.assertEquals("a.json", files.get(0).getRelativePath());
    }

    @Test
    void scanShouldReturnNestedFilesWhenRecursiveEnabled() throws Exception {
        Files.writeString(tempDir.resolve("a.json"), "{}");
        Files.createDirectories(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested/b.json"), "{}");

        List<DiscoveredFile> files = scanner.scan(LocalFileScanRequest.builder()
                .basePath(tempDir.toString())
                .recursive(true)
                .build());

        Assertions.assertEquals(2, files.size());
        Assertions.assertTrue(files.stream().anyMatch(item -> "nested/b.json".equals(item.getRelativePath())));
    }

    @Test
    void scanShouldMatchPatternByFileName() throws Exception {
        Files.writeString(tempDir.resolve("keep.csv"), "1");
        Files.writeString(tempDir.resolve("drop.json"), "{}");

        List<DiscoveredFile> files = scanner.scan(LocalFileScanRequest.builder()
                .basePath(tempDir.toString())
                .pattern("keep\\.csv")
                .recursive(false)
                .build());

        Assertions.assertEquals(1, files.size());
        Assertions.assertEquals("keep.csv", files.get(0).getFileName());
    }

    @Test
    void scanShouldMatchPatternByRelativePath() throws Exception {
        Files.createDirectories(tempDir.resolve("daily"));
        Files.writeString(tempDir.resolve("daily/keep.json"), "{}");
        Files.writeString(tempDir.resolve("drop.json"), "{}");

        List<DiscoveredFile> files = scanner.scan(LocalFileScanRequest.builder()
                .basePath(tempDir.toString())
                .pattern("daily/.*\\.json")
                .recursive(true)
                .build());

        Assertions.assertEquals(1, files.size());
        Assertions.assertEquals("daily/keep.json", files.get(0).getRelativePath());
    }

    @Test
    void scanShouldThrowWhenBasePathDoesNotExist() {
        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> scanner.scan(LocalFileScanRequest.builder()
                        .basePath(tempDir.resolve("missing").toString())
                        .build())
        );

        Assertions.assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void scanShouldConvertLastModifiedTimeWithConfiguredTimezone() throws Exception {
        Path file = Files.writeString(tempDir.resolve("a.json"), "{}");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-06-01T00:00:00Z")));

        List<DiscoveredFile> files = scanner.scan(LocalFileScanRequest.builder()
                .basePath(tempDir.toString())
                .timezone("Asia/Shanghai")
                .build());

        Assertions.assertEquals(8, files.get(0).getLastModifiedTime().getHour());
    }
}
