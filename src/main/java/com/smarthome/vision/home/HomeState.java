package com.smarthome.vision.home;

import com.smarthome.vision.vision.FaceResult;
import com.smarthome.vision.vision.Gesture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Estado de la aplicacion (en memoria):
 *   currentUser, isAuthorized, currentGesture y los focos (220V, rojo, verde, azul).
 */
@Component
public class HomeState {

    public enum FaceState { AUTHORIZED, UNKNOWN, NO_FACE }

    public record HistoryEntry(String time, String user, String gestureEmoji, String command, String result) { }

    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_HISTORY = 15;

    private final long authTimeoutMs;

    // --- Quien esta frente a la camara
    private String currentUser;
    private long lastKnownSeen;
    private long lastUnknownSeen;

    // --- Gesto y comando
    private Gesture currentGesture = Gesture.NONE;
    private Command lastCommand;

    // --- Dispositivos de la maqueta
    private final Map<Light, Boolean> lights = new EnumMap<>(Light.class);

    private final LinkedList<HistoryEntry> history = new LinkedList<>();

    public HomeState(@Value("${app.face.auth-timeout-ms}") long authTimeoutMs) {
        this.authTimeoutMs = authTimeoutMs;
        for (Light light : Light.values()) {
            lights.put(light, false);
        }
    }

    /** Actualiza quien esta frente a la camara con el resultado del reconocimiento facial. */
    public synchronized void updateFaces(List<FaceResult> faces) {
        long now = System.currentTimeMillis();
        FaceResult best = faces.stream().filter(FaceResult::known)
                .max((a, b) -> Double.compare(a.similarity(), b.similarity())).orElse(null);
        if (best != null) {
            currentUser = best.name();
            lastKnownSeen = now;
        } else if (!faces.isEmpty()) {
            // Solo hay rostros desconocidos: se bloquean los gestos inmediatamente
            currentUser = null;
            lastUnknownSeen = now;
        }
        // Si no hay rostros no cambiamos nada: la autorizacion caduca sola tras authTimeoutMs
    }

    public synchronized boolean isAuthorized() {
        return currentUser != null && System.currentTimeMillis() - lastKnownSeen < authTimeoutMs;
    }

    public synchronized String currentUser() {
        return isAuthorized() ? currentUser : null;
    }

    public synchronized FaceState faceState() {
        if (isAuthorized()) {
            return FaceState.AUTHORIZED;
        }
        return System.currentTimeMillis() - lastUnknownSeen < authTimeoutMs ? FaceState.UNKNOWN : FaceState.NO_FACE;
    }

    public synchronized void setCurrentGesture(Gesture gesture) {
        this.currentGesture = gesture;
    }

    public synchronized Gesture currentGesture() {
        return currentGesture;
    }

    /** Refleja en el estado el efecto de un comando (lo mismo que hace el Arduino). */
    public synchronized void apply(Command command) {
        lastCommand = command;
        switch (command) {
            case MAIN_ON -> lights.put(Light.MAIN, true);
            case MAIN_OFF -> lights.put(Light.MAIN, false);
            case RED_ON -> lights.put(Light.RED, true);
            case RED_OFF -> lights.put(Light.RED, false);
            case GREEN_ON -> lights.put(Light.GREEN, true);
            case GREEN_OFF -> lights.put(Light.GREEN, false);
            case BLUE_ON -> lights.put(Light.BLUE, true);
            case BLUE_OFF -> lights.put(Light.BLUE, false);
            case LIGHTS_ON -> lights.replaceAll((light, on) -> true);
            case ALL_OFF -> lights.replaceAll((light, on) -> false);
            case HOME_MODE -> { // foco principal + luz azul de ambiente
                lights.put(Light.MAIN, true);
                lights.put(Light.RED, false);
                lights.put(Light.GREEN, false);
                lights.put(Light.BLUE, true);
            }
        }
    }

    public synchronized void addHistory(String user, Gesture gesture, Command command, String result) {
        history.addFirst(new HistoryEntry(LocalTime.now().format(HH_MM_SS), user,
                gesture != null ? gesture.emoji() : "🖱️", command.name(), result));
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    public synchronized Command lastCommand() {
        return lastCommand;
    }

    public synchronized boolean isOn(Light light) {
        return lights.get(light);
    }

    /** Estado de los focos: {"MAIN": true, "RED": false, ...} */
    public synchronized Map<Light, Boolean> lights() {
        return new EnumMap<>(lights);
    }

    public synchronized List<HistoryEntry> history() {
        return new ArrayList<>(history);
    }
}
