package com.smarthome.vision.user;

import java.util.List;

/**
 * Usuario registrado. NO se guardan fotos: solo los embeddings
 * (vectores de 128 numeros) calculados por el modelo SFace.
 */
public record RegisteredUser(String name, String createdAt, List<float[]> embeddings) {
}
