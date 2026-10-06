package com.smarthome.vision.vision;

/**
 * Un punto de la mano (MediaPipe Hands) en pixeles de la imagen de la camara.
 * El navegador convierte las coordenadas normalizadas (0..1) a pixeles para
 * que las distancias no se deformen por la relacion de aspecto 4:3 / 16:9.
 */
public record Landmark(double x, double y, double z) {
}
