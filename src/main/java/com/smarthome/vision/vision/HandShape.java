package com.smarthome.vision.vision;

import java.util.List;

/**
 * "Forma" de una mano: que dedos estan extendidos y el gesto resultante.
 *
 * @param fingers 5 valores en orden: pulgar, indice, medio, anular, menique
 */
public record HandShape(Gesture gesture, List<Boolean> fingers) {

    public static final HandShape NONE = new HandShape(Gesture.NONE, List.of(false, false, false, false, false));

    public int extendedCount() {
        return (int) fingers.stream().filter(f -> f).count();
    }
}
