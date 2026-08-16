package com.aedev.myserver.infrastructure.audio;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class AudioFileStorageService {

    private final AudioProperties audioProperties;

    public AudioFileStorageService(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    /**
     * Saves audio bytes to disk under a sanitized filename and returns the
     * absolute file path. Filenames are built from mediaId+contentHash by
     * the caller, but this method still defensively strips anything that
     * isn't alphanumeric/dash/underscore/dot to block path traversal
     * (e.g., a mediaId containing "../../etc/passwd").
     */
    public StoredFile store(String fileName, byte[] audioBytes) {
        String sanitized = sanitize(fileName);

        try {
            Path storageDir = Path.of(audioProperties.storagePath());
            Files.createDirectories(storageDir);

            Path targetPath = storageDir.resolve(sanitized).normalize();

            // Defense in depth: even after sanitizing the filename, confirm
            // the resolved path is still inside the storage directory.
            if (!targetPath.startsWith(storageDir.normalize())) {
                throw new IllegalArgumentException("Resolved path escapes storage directory: " + fileName);
            }

            Files.write(targetPath, audioBytes);

            String publicUrl = audioProperties.publicBaseUrl() + "/" + sanitized;

            return new StoredFile(sanitized, targetPath.toString(), publicUrl, audioBytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store audio file: " + fileName, e);
        }
    }

    public boolean exists(String filePath) {
        return Files.exists(Path.of(filePath));
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record StoredFile(String fileName, String filePath, String url, long fileSize) {
    }
}