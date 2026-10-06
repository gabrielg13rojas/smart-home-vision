package com.smarthome.vision.home;

import com.smarthome.vision.arduino.ArduinoService;
import com.smarthome.vision.vision.FaceService;
import com.smarthome.vision.vision.Gesture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * El "cerebro" de la demo: decide si un gesto se convierte en comando.
 *
 * <pre>
 *   gesto estable (mantenido holdMs) ──► ¿usuario reconocido?
 *                                          │ NO  → ACCESO DENEGADO (no se envia nada)
 *                                          │ SI  → comando → Arduino
 * </pre>
 *
 * Reglas para que los focos se comporten como interruptores:
 *  - Cada gesto se ejecuta UNA vez: ☝️ enciende el foco y, aunque la mano
 *    salga del video, el foco sigue encendido.
 *  - Para repetir el MISMO gesto (y apagar el foco) la mano debe desaparecer
 *    al menos releaseMs. Cortes de deteccion mas cortos se ignoran.
 *  - Cada pestana del navegador (clientId) lleva su propio conteo, asi dos
 *    pestanas abiertas no se interfieren.
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    public enum Outcome { NONE, WAITING, EXECUTED, DENIED }

    /**
     * Resultado de procesar un gesto: se devuelve al navegador para mostrar mensajes.
     *
     * @param progress cuanto se ha mantenido el gesto (0 a 1); al llegar a 1 se ejecuta
     */
    public record GestureEvent(Outcome outcome, Gesture gesture, Command command, String user,
                               boolean sentToArduino, double progress) { }

    /** Seguimiento del gesto de una pestana del navegador. */
    private static final class Tracker {
        Gesture candidate = Gesture.NONE; // gesto que se esta manteniendo
        long candidateSince;              // desde cuando
        long candidateLastSeen;           // ultima vez que se vio
        Gesture fired;                    // ultimo gesto ejecutado (bloqueado hasta que desaparezca)
        long firedLastSeen;               // ultima vez que se vio el gesto ejecutado
        long lastUpdate;
    }

    private final HomeState state;
    private final ArduinoService arduino;
    private final long holdMs;
    private final long releaseMs;
    private final long graceMs;
    private final Map<String, Tracker> trackers = new HashMap<>();

    public CommandService(HomeState state, ArduinoService arduino,
                          @Value("${app.gesture.hold-ms}") long holdMs,
                          @Value("${app.gesture.release-ms}") long releaseMs,
                          @Value("${app.gesture.grace-ms}") long graceMs) {
        this.state = state;
        this.arduino = arduino;
        this.holdMs = holdMs;
        this.releaseMs = releaseMs;
        this.graceMs = graceMs;
        // Si el Arduino se reinicia (envia "READY"), le reenviamos el estado de los focos
        arduino.onReady(this::resyncArduino);
    }

    /** Version para una sola pestana (la usan los tests). */
    public GestureEvent onGesture(Gesture gesture) {
        return onGesture("default", gesture);
    }

    public synchronized GestureEvent onGesture(String clientId, Gesture gesture) {
        state.setCurrentGesture(gesture);
        long now = System.currentTimeMillis();
        Tracker t = tracker(clientId, now);

        // 1) El gesto ya ejecutado sigue en pantalla: no se repite
        if (t.fired != null && gesture == t.fired) {
            t.firedLastSeen = now;
            return new GestureEvent(Outcome.WAITING, gesture, null, state.currentUser(), false, 1);
        }
        // 2) Rearmar: el gesto ejecutado desaparecio el tiempo suficiente
        if (t.fired != null && now - t.firedLastSeen >= releaseMs) {
            t.fired = null;
        }
        // 3) Sin mano: toleramos cortes breves de deteccion sin perder el progreso
        if (gesture == Gesture.NONE) {
            if (now - t.candidateLastSeen > graceMs) {
                t.candidate = Gesture.NONE;
            }
            return new GestureEvent(Outcome.NONE, gesture, null, null, false, 0);
        }
        // 4) Gesto nuevo: empezamos a contar
        if (gesture != t.candidate) {
            t.candidate = gesture;
            t.candidateSince = now;
        }
        t.candidateLastSeen = now;

        Command command = resolve(gesture);
        double progress = holdMs == 0 ? 1 : Math.min(1.0, (now - t.candidateSince) / (double) holdMs);
        if (progress < 1) {
            return new GestureEvent(Outcome.WAITING, gesture, command, state.currentUser(), false, progress);
        }

        // 5) Gesto mantenido el tiempo suficiente: se ejecuta una sola vez
        t.fired = gesture;
        t.firedLastSeen = now;
        t.candidate = Gesture.NONE;

        // ----- REGLA DE AUTORIZACION -----
        if (!state.isAuthorized()) {
            log.warn("ACCESO DENEGADO: gesto {} de un usuario no reconocido", gesture);
            state.addHistory("🔒 " + FaceService.UNKNOWN, gesture, command, "DENEGADO");
            return new GestureEvent(Outcome.DENIED, gesture, command, null, false, 1);
        }
        String user = state.currentUser();
        boolean sent = execute(command, user, gesture);
        return new GestureEvent(Outcome.EXECUTED, gesture, command, user, sent, 1);
    }

    /** Toggle: si el foco esta encendido lo apaga, si esta apagado lo enciende. */
    private Command resolve(Gesture gesture) {
        if (gesture.toggle() != null) {
            Light light = gesture.toggle();
            return state.isOn(light) ? light.offCommand() : light.onCommand();
        }
        return gesture.command();
    }

    /** Ejecuta un comando: lo envia al Arduino, actualiza el estado y el historial. */
    public boolean execute(Command command, String user, Gesture gesture) {
        boolean sent = arduino.send(command.name());
        state.apply(command);
        state.addHistory(user, gesture, command, sent ? "OK" : "SIMULADO");
        log.info("{} ejecuto {} ({})", user, command, sent ? "Arduino" : "simulado");
        return sent;
    }

    /** Reenvia al Arduino el estado actual de cada foco (tras un reinicio de la placa). */
    private void resyncArduino() {
        state.lights().forEach((light, on) -> arduino.send((on ? light.onCommand() : light.offCommand()).name()));
        log.info("Estado de los focos reenviado al Arduino: {}", state.lights());
    }

    private Tracker tracker(String clientId, long now) {
        // Olvidamos pestanas cerradas hace mas de un minuto
        trackers.values().removeIf(t -> now - t.lastUpdate > 60_000);
        Tracker t = trackers.computeIfAbsent(clientId == null ? "default" : clientId, k -> new Tracker());
        t.lastUpdate = now;
        return t;
    }
}
