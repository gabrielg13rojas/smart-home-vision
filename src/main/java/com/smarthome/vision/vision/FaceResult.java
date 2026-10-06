package com.smarthome.vision.vision;

/**
 * Un rostro encontrado en una imagen.
 *
 * @param x,y,width,height recuadro en pixeles de la imagen recibida
 * @param name             nombre del usuario reconocido o "Desconocido"
 * @param known            true si coincide con un usuario registrado
 * @param similarity       similitud coseno con el mejor usuario (-1..1)
 * @param confidence       indicador 0-100 % para mostrar en pantalla (NO es una metrica biometrica)
 */
public record FaceResult(int x, int y, int width, int height,
                         String name, boolean known, double similarity, int confidence) {
}
