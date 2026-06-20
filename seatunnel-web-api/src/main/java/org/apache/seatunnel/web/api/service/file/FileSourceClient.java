package org.apache.seatunnel.web.api.service.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.FileDataSourceConfig;
import org.apache.seatunnel.web.api.service.model.FileSourceScanRequest;
import org.apache.seatunnel.web.api.service.model.FileSourceTestResult;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class FileSourceClient {

    private static final int DEFAULT_TEST_FILE_LIMIT = 5;
    private static final int DEFAULT_MAX_FILES = 1000;
    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int DATA_TIMEOUT_MILLIS = 30000;

    public boolean supports(DbType dbType) {
        return dbType == DbType.LOCAL_FILE
                || dbType == DbType.NAS
                || dbType == DbType.FTP
                || dbType == DbType.SFTP;
    }

    public FileDataSourceConfig parseConfig(DbType dbType, String json) {
        if (isBlank(json)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "connectionParams");
        }
        try {
            JsonNode node = JSONUtils.parseObject(json);
            FileDataSourceConfig config = new FileDataSourceConfig();
            config.setType(text(node, "type", "dbType"));
            config.setDbType(text(node, "dbType", "type"));
            config.setHost(text(node, "host"));
            config.setPort(integer(node, "port"));
            config.setUsername(text(node, "username", "user"));
            config.setPassword(text(node, "password"));
            config.setRootPath(text(node, "rootPath", "root_path", "path"));
            config.setPassiveMode(bool(node, "passiveMode", "passive_mode"));
            config.setDescription(text(node, "description", "remark"));
            config.setEnabled(bool(node, "enabled"));
            validateConfig(dbType, config);
            return config;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "connectionParams");
        }
    }

    public void validateConfig(DbType dbType, FileDataSourceConfig config) {
        if (!supports(dbType)) {
            throw new ServiceException("Unsupported file datasource type: " + dbType);
        }
        if (config == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "connectionParams");
        }
        if (isBlank(config.getRootPath())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "rootPath");
        }
        if (dbType == DbType.FTP || dbType == DbType.SFTP) {
            if (isBlank(config.getHost())) {
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "host");
            }
            if (isBlank(config.getUsername())) {
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "username");
            }
        }
    }

    public FileSourceTestResult test(DbType dbType, FileDataSourceConfig config) {
        validateConfig(dbType, config);
        String rootPath = normalizeRootPath(config.getRootPath());
        if (dbType == DbType.LOCAL_FILE || dbType == DbType.NAS) {
            return testLocal(rootPath);
        }
        if (dbType == DbType.FTP) {
            return testFtp(config, rootPath);
        }
        if (dbType == DbType.SFTP) {
            return testSftp(config, rootPath);
        }
        throw new ServiceException("Unsupported file datasource type: " + dbType);
    }

    public List<DiscoveredFile> scan(FileSourceScanRequest request) {
        validateScanRequest(request);
        DbType sourceType = request.getSourceType();
        String rootPath = normalizeRootPath(request.getRootPath());
        if (sourceType == DbType.LOCAL_FILE || sourceType == DbType.NAS) {
            return scanLocal(request, rootPath);
        }
        if (sourceType == DbType.FTP) {
            return scanFtp(request, rootPath);
        }
        if (sourceType == DbType.SFTP) {
            return scanSftp(request, rootPath);
        }
        throw new ServiceException("Unsupported file datasource type: " + sourceType);
    }

    public String sha256(DbType sourceType, String fullPath) {
        if (sourceType != DbType.LOCAL_FILE && sourceType != DbType.NAS) {
            throw new ServiceException("Checksum is only supported for LOCAL_FILE/NAS in this release");
        }
        try (InputStream inputStream = Files.newInputStream(Path.of(fullPath))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new ServiceException("Calculate file checksum failed: " + e.getMessage(), e);
        }
    }

    public <T> T withInputStream(
            DbType sourceType,
            FileDataSourceConfig config,
            String fullPath,
            InputStreamHandler<T> handler
    ) {
        if (sourceType == null || isBlank(fullPath) || handler == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "fileInputStream");
        }
        validateConfig(sourceType, config);
        if (sourceType == DbType.LOCAL_FILE || sourceType == DbType.NAS) {
            try (InputStream inputStream = Files.newInputStream(Path.of(fullPath))) {
                return handler.handle(inputStream);
            } catch (Exception e) {
                throw new ServiceException("Read local file failed: " + e.getMessage(), e);
            }
        }
        if (sourceType == DbType.FTP) {
            return withFtpInputStream(config, fullPath, handler);
        }
        if (sourceType == DbType.SFTP) {
            return withSftpInputStream(config, fullPath, handler);
        }
        throw new ServiceException("Unsupported file datasource type: " + sourceType);
    }

    private FileSourceTestResult testLocal(String rootPath) {
        Path path = Path.of(rootPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ServiceException("Local rootPath does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new ServiceException("Local rootPath is not a directory: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new ServiceException("Local rootPath is not readable: " + path);
        }
        List<String> samples;
        try (Stream<Path> paths = Files.list(path)) {
            samples = paths
                    .filter(Files::isRegularFile)
                    .sorted()
                    .limit(DEFAULT_TEST_FILE_LIMIT)
                    .map(item -> item.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new ServiceException("List local rootPath failed: " + e.getMessage(), e);
        }
        return FileSourceTestResult.builder()
                .success(true)
                .message("Local rootPath is readable")
                .rootPath(path.toString())
                .sampleFiles(samples)
                .build();
    }

    private <T> T withFtpInputStream(
            FileDataSourceConfig config,
            String fullPath,
            InputStreamHandler<T> handler
    ) {
        FTPClient client = null;
        InputStream inputStream = null;
        try {
            client = connectFtp(config);
            inputStream = client.retrieveFileStream(fullPath);
            if (inputStream == null) {
                throw new ServiceException("FTP retrieve file failed: " + client.getReplyString());
            }
            T result = handler.handle(inputStream);
            inputStream.close();
            inputStream = null;
            if (!client.completePendingCommand()) {
                throw new ServiceException("FTP complete file transfer failed: " + client.getReplyString());
            }
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Read FTP file failed: " + e.getMessage(), e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
            disconnectFtp(client);
        }
    }

    private <T> T withSftpInputStream(
            FileDataSourceConfig config,
            String fullPath,
            InputStreamHandler<T> handler
    ) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = connectSftpSession(config);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MILLIS);
            try (InputStream inputStream = channel.get(fullPath)) {
                return handler.handle(inputStream);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Read SFTP file failed: " + e.getMessage(), e);
        } finally {
            disconnectSftp(channel, session);
        }
    }

    private FileSourceTestResult testFtp(FileDataSourceConfig config, String rootPath) {
        FTPClient client = null;
        try {
            client = connectFtp(config);
            if (!client.changeWorkingDirectory(rootPath)) {
                throw new ServiceException("FTP rootPath is not accessible: " + rootPath);
            }
            List<String> samples = new ArrayList<>();
            for (FTPFile file : client.listFiles(rootPath)) {
                if (file != null && file.isFile()) {
                    samples.add(file.getName());
                }
                if (samples.size() >= DEFAULT_TEST_FILE_LIMIT) {
                    break;
                }
            }
            return FileSourceTestResult.builder()
                    .success(true)
                    .message("FTP rootPath is accessible")
                    .rootPath(rootPath)
                    .sampleFiles(samples)
                    .build();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("FTP connection test failed: " + e.getMessage(), e);
        } finally {
            disconnectFtp(client);
        }
    }

    private FileSourceTestResult testSftp(FileDataSourceConfig config, String rootPath) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = connectSftpSession(config);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MILLIS);
            channel.cd(rootPath);
            List<String> samples = new ArrayList<>();
            Vector<ChannelSftp.LsEntry> entries = channel.ls(rootPath);
            for (ChannelSftp.LsEntry entry : entries) {
                if (isCurrentOrParent(entry.getFilename())) {
                    continue;
                }
                if (!entry.getAttrs().isDir()) {
                    samples.add(entry.getFilename());
                }
                if (samples.size() >= DEFAULT_TEST_FILE_LIMIT) {
                    break;
                }
            }
            return FileSourceTestResult.builder()
                    .success(true)
                    .message("SFTP rootPath is accessible")
                    .rootPath(rootPath)
                    .sampleFiles(samples)
                    .build();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("SFTP connection test failed: " + e.getMessage(), e);
        } finally {
            disconnectSftp(channel, session);
        }
    }

    private List<DiscoveredFile> scanLocal(FileSourceScanRequest request, String rootPath) {
        Path basePath = Path.of(rootPath).toAbsolutePath().normalize();
        if (!Files.exists(basePath)) {
            throw new ServiceException("Local rootPath does not exist: " + basePath);
        }
        if (!Files.isDirectory(basePath)) {
            throw new ServiceException("Local rootPath is not a directory: " + basePath);
        }
        if (!Files.isReadable(basePath)) {
            throw new ServiceException("Local rootPath is not readable: " + basePath);
        }

        int maxDepth = resolveLocalWalkDepth(request);
        int maxFiles = resolveMaxFiles(request.getMaxFiles());
        PatternSet patterns = PatternSet.of(request.getIncludePatterns(), request.getExcludePatterns());
        try (Stream<Path> paths = Files.walk(basePath, maxDepth)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> toLocalDiscoveredFile(basePath, path))
                    .filter(file -> patterns.matches(file.getRelativePath(), file.getFileName()))
                    .sorted(Comparator.comparing(DiscoveredFile::getRelativePath))
                    .limit(maxFiles)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new ServiceException("Scan local files failed: " + e.getMessage(), e);
        }
    }

    private List<DiscoveredFile> scanFtp(FileSourceScanRequest request, String rootPath) {
        FTPClient client = null;
        try {
            client = connectFtp(request.getConfig());
            if (!client.changeWorkingDirectory(rootPath)) {
                throw new ServiceException("FTP rootPath is not accessible: " + rootPath);
            }
            List<DiscoveredFile> result = new ArrayList<>();
            scanFtpDirectory(
                    client,
                    rootPath,
                    "",
                    0,
                    resolveRemoteDepth(request),
                    resolveMaxFiles(request.getMaxFiles()),
                    PatternSet.of(request.getIncludePatterns(), request.getExcludePatterns()),
                    result
            );
            result.sort(Comparator.comparing(DiscoveredFile::getRelativePath));
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Scan FTP files failed: " + e.getMessage(), e);
        } finally {
            disconnectFtp(client);
        }
    }

    private List<DiscoveredFile> scanSftp(FileSourceScanRequest request, String rootPath) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = connectSftpSession(request.getConfig());
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MILLIS);
            channel.cd(rootPath);
            List<DiscoveredFile> result = new ArrayList<>();
            scanSftpDirectory(
                    channel,
                    rootPath,
                    "",
                    0,
                    resolveRemoteDepth(request),
                    resolveMaxFiles(request.getMaxFiles()),
                    PatternSet.of(request.getIncludePatterns(), request.getExcludePatterns()),
                    result
            );
            result.sort(Comparator.comparing(DiscoveredFile::getRelativePath));
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Scan SFTP files failed: " + e.getMessage(), e);
        } finally {
            disconnectSftp(channel, session);
        }
    }

    private void scanFtpDirectory(
            FTPClient client,
            String directory,
            String relativePrefix,
            int depth,
            int maxDepth,
            int maxFiles,
            PatternSet patterns,
            List<DiscoveredFile> result
    ) throws Exception {
        if (result.size() >= maxFiles) {
            return;
        }
        FTPFile[] files = client.listFiles(directory);
        for (FTPFile file : files) {
            if (file == null || isCurrentOrParent(file.getName())) {
                continue;
            }
            String relativePath = joinRelative(relativePrefix, file.getName());
            String fullPath = joinRemotePath(directory, file.getName());
            if (file.isDirectory()) {
                if (depth < maxDepth) {
                    scanFtpDirectory(client, fullPath, relativePath, depth + 1, maxDepth, maxFiles, patterns, result);
                }
                continue;
            }
            if (!file.isFile() || !patterns.matches(relativePath, file.getName())) {
                continue;
            }
            result.add(DiscoveredFile.builder()
                    .absolutePath(fullPath)
                    .relativePath(relativePath)
                    .fileName(file.getName())
                    .size(file.getSize())
                    .lastModifiedTime(toLocalDateTime(file.getTimestamp() == null
                            ? Instant.EPOCH
                            : file.getTimestamp().toInstant()))
                    .build());
            if (result.size() >= maxFiles) {
                return;
            }
        }
    }

    private void scanSftpDirectory(
            ChannelSftp channel,
            String directory,
            String relativePrefix,
            int depth,
            int maxDepth,
            int maxFiles,
            PatternSet patterns,
            List<DiscoveredFile> result
    ) throws Exception {
        if (result.size() >= maxFiles) {
            return;
        }
        Vector<ChannelSftp.LsEntry> entries = channel.ls(directory);
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (isCurrentOrParent(name)) {
                continue;
            }
            String relativePath = joinRelative(relativePrefix, name);
            String fullPath = joinRemotePath(directory, name);
            if (entry.getAttrs().isDir()) {
                if (depth < maxDepth) {
                    scanSftpDirectory(channel, fullPath, relativePath, depth + 1, maxDepth, maxFiles, patterns, result);
                }
                continue;
            }
            if (!patterns.matches(relativePath, name)) {
                continue;
            }
            result.add(DiscoveredFile.builder()
                    .absolutePath(fullPath)
                    .relativePath(relativePath)
                    .fileName(name)
                    .size((long) entry.getAttrs().getSize())
                    .lastModifiedTime(toLocalDateTime(Instant.ofEpochSecond(entry.getAttrs().getMTime())))
                    .build());
            if (result.size() >= maxFiles) {
                return;
            }
        }
    }

    private DiscoveredFile toLocalDiscoveredFile(Path basePath, Path path) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            String relativePath = basePath.relativize(normalized).toString()
                    .replace(path.getFileSystem().getSeparator(), "/");
            return DiscoveredFile.builder()
                    .absolutePath(normalized.toString())
                    .relativePath(relativePath)
                    .fileName(path.getFileName().toString())
                    .size(Files.size(path))
                    .lastModifiedTime(toLocalDateTime(Files.getLastModifiedTime(path).toInstant()))
                    .build();
        } catch (Exception e) {
            throw new ServiceException("Read local file attributes failed: " + e.getMessage(), e);
        }
    }

    private FTPClient connectFtp(FileDataSourceConfig config) throws Exception {
        FTPClient client = new FTPClient();
        client.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        client.setDataTimeout(DATA_TIMEOUT_MILLIS);
        client.connect(config.getHost(), config.getPort() == null ? 21 : config.getPort());
        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            throw new ServiceException("FTP server rejected connection: " + client.getReplyString());
        }
        if (!client.login(config.getUsername(), config.getPassword() == null ? "" : config.getPassword())) {
            throw new ServiceException("FTP login failed: " + client.getReplyString());
        }
        if (Boolean.TRUE.equals(config.getPassiveMode())) {
            client.enterLocalPassiveMode();
        }
        client.setFileType(FTPClient.BINARY_FILE_TYPE);
        return client;
    }

    private Session connectSftpSession(FileDataSourceConfig config) throws Exception {
        JSch jSch = new JSch();
        Session session = jSch.getSession(
                config.getUsername(),
                config.getHost(),
                config.getPort() == null ? 22 : config.getPort()
        );
        session.setPassword(config.getPassword() == null ? "" : config.getPassword());
        Properties properties = new Properties();
        properties.put("StrictHostKeyChecking", "no");
        session.setConfig(properties);
        session.connect(CONNECT_TIMEOUT_MILLIS);
        return session;
    }

    private void disconnectFtp(FTPClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.logout();
                client.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    private void disconnectSftp(ChannelSftp channel, Session session) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    private void validateScanRequest(FileSourceScanRequest request) {
        if (request == null || request.getSourceType() == null || request.getConfig() == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "scanRequest");
        }
        validateConfig(request.getSourceType(), request.getConfig());
        if (isBlank(request.getRootPath())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "rootPath");
        }
    }

    private int resolveLocalWalkDepth(FileSourceScanRequest request) {
        if (!Boolean.TRUE.equals(request.getRecursive())) {
            return 1;
        }
        if (request.getMaxDepth() == null || request.getMaxDepth() <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, request.getMaxDepth() + 1);
    }

    private int resolveRemoteDepth(FileSourceScanRequest request) {
        if (!Boolean.TRUE.equals(request.getRecursive())) {
            return 0;
        }
        if (request.getMaxDepth() == null || request.getMaxDepth() <= 0) {
            return Integer.MAX_VALUE;
        }
        return request.getMaxDepth();
    }

    private int resolveMaxFiles(Integer maxFiles) {
        return maxFiles == null || maxFiles <= 0 ? DEFAULT_MAX_FILES : maxFiles;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).withNano(0);
    }

    private String normalizeRootPath(String rootPath) {
        return isBlank(rootPath) ? rootPath : rootPath.trim();
    }

    private String joinRemotePath(String parent, String child) {
        if ("/".equals(parent)) {
            return "/" + child;
        }
        if (parent.endsWith("/")) {
            return parent + child;
        }
        return parent + "/" + child;
    }

    private String joinRelative(String parent, String child) {
        if (isBlank(parent)) {
            return child;
        }
        return parent + "/" + child;
    }

    private boolean isCurrentOrParent(String name) {
        return ".".equals(name) || "..".equals(name);
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (!isBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asInt();
    }

    private Boolean bool(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asBoolean();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class PatternSet {

        private final List<Pattern> includes;

        private final List<Pattern> excludes;

        private PatternSet(List<Pattern> includes, List<Pattern> excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }

        static PatternSet of(String includePatterns, String excludePatterns) {
            return new PatternSet(compile(includePatterns), compile(excludePatterns));
        }

        boolean matches(String relativePath, String fileName) {
            boolean included = includes.isEmpty()
                    || matchesAny(includes, relativePath, fileName);
            return included && !matchesAny(excludes, relativePath, fileName);
        }

        private static boolean matchesAny(List<Pattern> patterns, String relativePath, String fileName) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(relativePath).matches() || pattern.matcher(fileName).matches()) {
                    return true;
                }
            }
            return false;
        }

        private static List<Pattern> compile(String patternText) {
            if (patternText == null || patternText.trim().isEmpty()) {
                return List.of();
            }
            String[] parts = patternText.split("[,\\n]");
            List<Pattern> patterns = new ArrayList<>();
            for (String part : parts) {
                String pattern = part.trim();
                if (!pattern.isEmpty()) {
                    patterns.add(Pattern.compile(globToRegex(pattern)));
                }
            }
            return patterns;
        }

        private static String globToRegex(String glob) {
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    boolean doubleStar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                    regex.append(doubleStar ? ".*" : "[^/]*");
                    if (doubleStar) {
                        i++;
                    }
                } else if (c == '?') {
                    regex.append("[^/]");
                } else if (".()[]{}+$^|\\\\".indexOf(c) >= 0) {
                    regex.append('\\').append(c);
                } else {
                    regex.append(c);
                }
            }
            regex.append('$');
            return regex.toString();
        }
    }

    @FunctionalInterface
    public interface InputStreamHandler<T> {
        T handle(InputStream inputStream) throws Exception;
    }
}
