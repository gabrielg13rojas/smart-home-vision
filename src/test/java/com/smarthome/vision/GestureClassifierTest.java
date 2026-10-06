package com.smarthome.vision;

import com.smarthome.vision.vision.Gesture;
import com.smarthome.vision.vision.GestureClassifier;
import com.smarthome.vision.vision.Landmark;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueba el clasificador con manos "dibujadas a mano" (sin camara).
 * Mano vertical, muneca abajo en (0,0); en imagen la y crece hacia abajo.
 */
class GestureClassifierTest {

    private final GestureClassifier classifier = new GestureClassifier();

    @Test
    void openPalm() {
        assertEquals(Gesture.OPEN_PALM, classifier.classify(handOf(true, true, true, true, true, false)).gesture());
    }

    @Test
    void fist() {
        assertEquals(Gesture.FIST, classifier.classify(handOf(false, false, false, false, false, false)).gesture());
    }

    @Test
    void oneFinger() {
        assertEquals(Gesture.ONE_FINGER, classifier.classify(handOf(false, true, false, false, false, false)).gesture());
    }

    @Test
    void twoFingers() {
        assertEquals(Gesture.TWO_FINGERS, classifier.classify(handOf(false, true, true, false, false, false)).gesture());
    }

    @Test
    void thumbsUp() {
        assertEquals(Gesture.THUMBS_UP, classifier.classify(handOf(true, false, false, false, false, true)).gesture());
    }

    @Test
    void threeFingers() {
        assertEquals(Gesture.THREE_FINGERS, classifier.classify(handOf(false, true, true, true, false, false)).gesture());
    }

    @Test
    void fourFingersWithThumbFolded() {
        assertEquals(Gesture.FOUR_FINGERS, classifier.classify(handOf(false, true, true, true, true, false)).gesture());
    }

    @Test
    void rockIsNotAGesture() {
        assertEquals(Gesture.NONE, classifier.classify(handOf(false, true, false, false, true, false)).gesture());
    }

    @Test
    void thumbsDownIsNotAGesture() {
        List<Landmark> p = handOf(true, false, false, false, false, false);
        p.set(3, pt(-60, -40));
        p.set(4, pt(-60, 10));
        assertEquals(Gesture.NONE, classifier.classify(p).gesture());
    }

    @Test
    void onlyMiddleFingerIsNotAGesture() {
        assertEquals(Gesture.NONE, classifier.classify(handOf(false, false, true, false, false, false)).gesture());
    }

    @Test
    void reportsWhichFingersAreExtended() {
        var shape = classifier.classify(handOf(false, true, true, false, false, false));
        assertEquals(List.of(false, true, true, false, false), shape.fingers());
        assertEquals(2, shape.extendedCount());
    }

    /** Construye los 21 puntos de una mano con los dedos indicados extendidos. */
    private static List<Landmark> handOf(boolean thumb, boolean index, boolean middle, boolean ring, boolean pinky,
                                       boolean thumbUp) {
        Landmark[] p = new Landmark[21];
        p[0] = pt(0, 0);
        // Pulgar: 1..4
        p[1] = pt(-30, -20);
        p[2] = pt(-50, -40);
        if (thumbUp) {
            p[3] = pt(-45, -120);
            p[4] = pt(-45, -170);
        } else if (thumb) {
            p[3] = pt(-70, -60);
            p[4] = pt(-95, -75);
        } else {
            p[3] = pt(-40, -60);
            p[4] = pt(-15, -75);
        }
        finger(p, 5, -30, -90, index);
        finger(p, 9, -10, -100, middle);
        finger(p, 13, 10, -95, ring);
        finger(p, 17, 28, -85, pinky);
        return new ArrayList<>(Arrays.asList(p));
    }

    private static void finger(Landmark[] p, int mcp, double x, double y, boolean extended) {
        p[mcp] = pt(x, y);
        if (extended) {
            p[mcp + 1] = pt(x, y - 35);
            p[mcp + 2] = pt(x, y - 60);
            p[mcp + 3] = pt(x, y - 80);
        } else { // doblado: la punta vuelve hacia la palma
            p[mcp + 1] = pt(x, y - 30);
            p[mcp + 2] = pt(x, y - 10);
            p[mcp + 3] = pt(x, y + 5);
        }
    }

    private static Landmark pt(double x, double y) {
        return new Landmark(x, y, 0);
    }
}
