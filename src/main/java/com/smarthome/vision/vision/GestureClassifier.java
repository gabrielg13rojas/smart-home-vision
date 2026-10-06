package com.smarthome.vision.vision;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Clasifica un gesto a partir de los 21 puntos de la mano que entrega MediaPipe.
 *
 * <pre>
 *            8   12  16  20        <- puntas (TIP)
 *            |   |   |   |
 *            6   10  14  18        <- articulacion media (PIP)
 *        4   |   |   |   |
 *         \  5---9---13--17        <- nudillos (MCP)
 *          3  \          /
 *           2  \        /
 *            1--\------/
 *                 0                <- muneca (WRIST)
 * </pre>
 *
 * Paso 1: decidir que dedos estan extendidos.
 *   - Indice, medio, anular, menique: la punta esta mas lejos de la muneca que la articulacion media.
 *   - Pulgar: la punta esta lejos del centro de la palma (punto 9) y apunta hacia afuera.
 * Paso 2: el patron de dedos extendidos da el gesto (tabla en classify()).
 */
@Component
public class GestureClassifier {

    private static final int WRIST = 0;
    private static final int THUMB_IP = 3, THUMB_TIP = 4;
    private static final int INDEX_MCP = 5, INDEX_PIP = 6, INDEX_TIP = 8;
    private static final int MIDDLE_MCP = 9, MIDDLE_PIP = 10, MIDDLE_TIP = 12;
    private static final int RING_PIP = 14, RING_TIP = 16;
    private static final int PINKY_MCP = 17, PINKY_PIP = 18, PINKY_TIP = 20;

    public HandShape classify(List<Landmark> p) {
        if (p == null || p.size() < 21) {
            return HandShape.NONE;
        }
        double palm = dist(p.get(WRIST), p.get(MIDDLE_MCP)); // tamano de referencia de la mano

        boolean thumb = isThumbExtended(p, palm);
        boolean index = isExtended(p, INDEX_TIP, INDEX_PIP);
        boolean middle = isExtended(p, MIDDLE_TIP, MIDDLE_PIP);
        boolean ring = isExtended(p, RING_TIP, RING_PIP);
        boolean pinky = isExtended(p, PINKY_TIP, PINKY_PIP);

        return new HandShape(gesture(p, palm, thumb, index, middle, ring, pinky),
                List.of(thumb, index, middle, ring, pinky));
    }

    private static Gesture gesture(List<Landmark> p, double palm,
                                   boolean thumb, boolean index, boolean middle, boolean ring, boolean pinky) {
        //  indice medio anular menique   -> gesto
        //    ✓      ✓     ✓      ✓       -> ✋ (con pulgar) / 🖖 4 dedos (sin pulgar)
        //    ✓      ✓     ✓      ✗       -> 🤟 3 dedos
        //    ✓      ✓     ✗      ✗       -> ✌️ 2 dedos
        //    ✓      ✗     ✗      ✗       -> ☝️ 1 dedo
        //    ✗      ✗     ✗      ✗       -> 👍 (pulgar arriba) o ✊ puño
        if (index && middle && ring && pinky) {
            return thumb ? Gesture.OPEN_PALM : Gesture.FOUR_FINGERS;
        }
        if (index && middle && ring) {
            return Gesture.THREE_FINGERS;
        }
        if (index && middle && !ring && !pinky) {
            return Gesture.TWO_FINGERS;
        }
        if (index && !middle && !ring && !pinky) {
            return Gesture.ONE_FINGER;
        }
        if (!index && !middle && !ring && !pinky) {
            // En la imagen, "y" crece hacia abajo
            double thumbHeight = p.get(INDEX_MCP).y() - p.get(THUMB_TIP).y();
            if (thumb && thumbHeight > 0.3 * palm) {
                return Gesture.THUMBS_UP;
            }
            if (thumb && thumbHeight < -0.3 * palm) {
                return Gesture.NONE; // pulgar hacia abajo: no es un gesto de la demo
            }
            return Gesture.FIST;
        }
        return Gesture.NONE;
    }

    private static boolean isExtended(List<Landmark> p, int tip, int pip) {
        return dist(p.get(tip), p.get(WRIST)) > dist(p.get(pip), p.get(WRIST)) * 1.1;
    }

    /**
     * Medido con fotos reales: pulgar extendido -> punta a ~1.0 palmas del punto 9;
     * pulgar doblado -> ~0.2 palmas. Ademas, si esta extendido se aleja del menique.
     */
    private static boolean isThumbExtended(List<Landmark> p, double palm) {
        return dist(p.get(THUMB_TIP), p.get(MIDDLE_MCP)) > 0.6 * palm
                && dist(p.get(THUMB_TIP), p.get(PINKY_MCP)) > dist(p.get(THUMB_IP), p.get(PINKY_MCP));
    }

    private static double dist(Landmark a, Landmark b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
