package com.kadaibook.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path root;

    public LocalStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new UncheckedIOException("could not create local storage root", exception);
        }
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("could not write page image", exception);
        }
    }

    @Override
    public byte[] get(String key) {
        Path target = resolve(key);
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read page image", exception);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) {
        Path target = resolve(key);
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                log.info("deleted stored page image {}", key);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("could not delete page image", exception);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank() || key.contains("\0")) {
            throw new IllegalArgumentException("invalid storage key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("storage key escapes the root");
        }
        return resolved;
    }
}
