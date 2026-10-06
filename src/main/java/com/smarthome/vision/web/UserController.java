package com.smarthome.vision.web;

import com.smarthome.vision.user.RegisteredUser;
import com.smarthome.vision.user.UserRepository;
import com.smarthome.vision.vision.FaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Registro y listado de usuarios (reconocimiento facial). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /** name = nombre del usuario, images = fotos de la webcam en base64 (solo se usan para calcular embeddings). */
    public record RegisterRequest(String name, List<String> images) { }

    public record UserSummary(String name, String createdAt, int samples) { }

    private final FaceService faceService;
    private final UserRepository users;

    public UserController(FaceService faceService, UserRepository users) {
        this.faceService = faceService;
        this.users = users;
    }

    @PostMapping("/register")
    public UserSummary register(@RequestBody RegisterRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Escribe un nombre");
        }
        if (request.images() == null || request.images().isEmpty()) {
            throw new IllegalArgumentException("No se recibieron imagenes");
        }
        List<float[]> embeddings = faceService.embeddingsForRegistration(request.images());
        if (embeddings.isEmpty()) {
            throw new IllegalArgumentException("No se detecto ningun rostro. Mira a la camara con buena luz.");
        }
        return summary(users.addEmbeddings(name, embeddings));
    }

    @GetMapping
    public List<UserSummary> list() {
        return users.findAll().stream().map(UserController::summary).toList();
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String name) {
        return users.delete(name)
                ? ResponseEntity.ok(Map.of("deleted", name))
                : ResponseEntity.notFound().build();
    }

    private static UserSummary summary(RegisteredUser u) {
        return new UserSummary(u.name(), u.createdAt(), u.embeddings().size());
    }
}
