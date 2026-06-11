package org.apache.seatunnel.web.api.service.file;

import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.LocalFileScanRequest;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LocalSyncFileScanner {

    public List<DiscoveredFile> scan(LocalFileScanRequest request) {
        if (request == null || isBlank(request.getBasePath())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "filePath");
        }

        Path basePath = Path.of(request.getBasePath()).toAbsolutePath().normalize();
        if (!Files.exists(basePath)) {
            throw new ServiceException("Local file basePath does not exist: " + basePath);
        }
        if (!Files.isDirectory(basePath)) {
            throw new ServiceException("Local file basePath is not a directory: " + basePath);
        }

        Pattern pattern = isBlank(request.getPattern()) ? null : Pattern.compile(request.getPattern());
        ZoneId zoneId = resolveZoneId(request.getTimezone());
        int maxDepth = Boolean.TRUE.equals(request.getRecursive()) ? Integer.MAX_VALUE : 1;

        try (Stream<Path> paths = Files.walk(basePath, maxDepth)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> toDiscoveredFile(basePath, path, zoneId))
                    .filter(Objects::nonNull)
                    .filter(file -> matches(pattern, file))
                    .sorted(Comparator.comparing(DiscoveredFile::getRelativePath))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ServiceException("Scan local files failed: " + e.getMessage(), e);
        }
    }

    private DiscoveredFile toDiscoveredFile(Path basePath, Path path, ZoneId zoneId) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            String relativePath = basePath.relativize(path.toAbsolutePath().normalize()).toString()
                    .replace(path.getFileSystem().getSeparator(), "/");
            return DiscoveredFile.builder()
                    .absolutePath(path.toAbsolutePath().normalize().toString())
                    .fileName(path.getFileName().toString())
                    .relativePath(relativePath)
                    .size(attributes.size())
                    .lastModifiedTime(toLocalDateTime(attributes.lastModifiedTime().toInstant(), zoneId))
                    .build();
        } catch (IOException e) {
            throw new ServiceException("Read local file attributes failed, path=" + path + ": " + e.getMessage(), e);
        }
    }

    private boolean matches(Pattern pattern, DiscoveredFile file) {
        if (pattern == null) {
            return true;
        }
        return pattern.matcher(file.getRelativePath()).matches()
                || pattern.matcher(file.getFileName()).matches();
    }

    private LocalDateTime toLocalDateTime(Instant instant, ZoneId zoneId) {
        return LocalDateTime.ofInstant(instant, zoneId).withNano(0);
    }

    private ZoneId resolveZoneId(String timezone) {
        if (isBlank(timezone)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new ServiceException("Unsupported file timezone: " + timezone);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
