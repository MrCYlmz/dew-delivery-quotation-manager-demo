package de.dew.backend.storage;

import de.dew.backend.config.DewProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "xlsx", "xls");

    private static final String FALLBACK_EXTENSION = "bin";

    private final Path root;

    public FileStorageService(DewProperties properties) {
        this.root = Paths.get(properties.storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
        log.info("File storage directory: {}", root);
    }

    public String store(InputStream in, String originalFilename) throws IOException {
        String name = UUID.randomUUID() + "." + extensionOf(originalFilename);
        Path target = root.resolve(name);
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public Path resolve(String storedPath) {
        Path resolved = root.resolve(storedPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Stored path escapes the storage directory: " + storedPath);
        }
        return resolved;
    }

    public boolean exists(String storedPath) {
        return Files.isRegularFile(resolve(storedPath));
    }

    public long sizeOf(String storedPath) throws IOException {
        return Files.size(resolve(storedPath));
    }

    public void delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Could not delete stored file {}: {}", storedPath, e.getMessage());
        }
    }

    public Path root() {
        return root;
    }

    static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return FALLBACK_EXTENSION;
        }
        String bare = originalFilename;
        int lastSlash = Math.max(bare.lastIndexOf('/'), bare.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            bare = bare.substring(lastSlash + 1);
        }
        int dot = bare.lastIndexOf('.');
        if (dot < 0 || dot == bare.length() - 1) {
            return FALLBACK_EXTENSION;
        }
        String ext = bare.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
        return ALLOWED_EXTENSIONS.contains(ext) ? ext : FALLBACK_EXTENSION;
    }
}
