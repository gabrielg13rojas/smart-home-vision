package com.smarthome.vision.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * "Base de datos" del taller: un archivo JSON local (data/users.json).
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final List<RegisteredUser> users = new ArrayList<>();

    public UserRepository(@Value("${app.data-dir}") String dataDir) {
        this.file = Path.of(dataDir, "users.json");
        load();
    }

    public synchronized List<RegisteredUser> findAll() {
        return List.copyOf(users);
    }

    /** Agrega muestras al usuario (si ya existe, se suman a las anteriores). */
    public synchronized RegisteredUser addEmbeddings(String name, List<float[]> newEmbeddings) {
        RegisteredUser existing = users.stream()
                .filter(u -> u.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        List<float[]> all = new ArrayList<>();
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (existing != null) {
            all.addAll(existing.embeddings());
            createdAt = existing.createdAt();
            users.remove(existing);
        }
        all.addAll(newEmbeddings);
        RegisteredUser user = new RegisteredUser(name, createdAt, all);
        users.add(user);
        save();
        return user;
    }

    public synchronized boolean delete(String name) {
        boolean removed = users.removeIf(u -> u.name().equalsIgnoreCase(name));
        if (removed) {
            save();
        }
        return removed;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            users.addAll(mapper.readValue(file.toFile(), new TypeReference<List<RegisteredUser>>() { }));
            log.info("Usuarios cargados: {}", users.stream().map(RegisteredUser::name).toList());
        } catch (IOException e) {
            log.error("No se pudo leer {}: {}", file, e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), users);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
