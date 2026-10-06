package com.smarthome.vision.web;

import com.smarthome.vision.arduino.ArduinoService;
import com.smarthome.vision.home.Command;
import com.smarthome.vision.home.CommandService;
import com.smarthome.vision.home.HomeState;
import com.smarthome.vision.home.Light;
import com.smarthome.vision.vision.Gesture;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Estado general (dashboard), comandos manuales y configuracion del puerto serial.
 */
@RestController
@RequestMapping("/api")
public class HomeController {

    public record GestureInfo(String name, String emoji, String label) { }

    public record CommandInfo(String name, String emoji, String label) { }

    /** lights = {"MAIN": true, "RED": false, "GREEN": false, "BLUE": true} */
    public record Devices(Map<Light, Boolean> lights) { }

    public record ArduinoInfo(boolean connected, String port, String lastMessage) { }

    public record Status(String currentUser, boolean authorized, HomeState.FaceState faceState,
                         GestureInfo currentGesture, CommandInfo lastCommand, Devices devices,
                         ArduinoInfo arduino, List<HomeState.HistoryEntry> history) { }

    public record ConnectRequest(String port) { }

    /** Entrada de la guia de gestos que muestra la web. */
    public record GestureGuide(String name, String emoji, String label, String howTo, String action, String light) { }

    private final HomeState state;
    private final CommandService commandService;
    private final ArduinoService arduino;

    public HomeController(HomeState state, CommandService commandService, ArduinoService arduino) {
        this.state = state;
        this.commandService = commandService;
        this.arduino = arduino;
    }

    @GetMapping("/status")
    public Status status() {
        Gesture g = state.currentGesture();
        Command c = state.lastCommand();
        return new Status(
                state.currentUser(),
                state.isAuthorized(),
                state.faceState(),
                new GestureInfo(g.name(), g.emoji(), g.label()),
                c == null ? null : new CommandInfo(c.name(), c.emoji(), c.label()),
                new Devices(state.lights()),
                new ArduinoInfo(arduino.isConnected(), arduino.portName(), arduino.lastMessage()),
                state.history());
    }

    /**
     * Prueba MANUAL de un comando (botones del panel "Prueba de Arduino").
     * Sirve para verificar el Arduino sin camara; no pasa por el reconocimiento facial.
     */
    @PostMapping("/commands/{command}")
    public Status manualCommand(@PathVariable String command) {
        Command cmd;
        try {
            cmd = Command.valueOf(command.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Comando desconocido: " + command);
        }
        commandService.execute(cmd, "Manual", null);
        return status();
    }

    /** Lista de gestos disponibles (la web la usa para dibujar la guia). */
    @GetMapping("/gestures")
    public List<GestureGuide> gestures() {
        return Arrays.stream(Gesture.values())
                .filter(g -> g != Gesture.NONE)
                .map(g -> new GestureGuide(g.name(), g.emoji(), g.label(), g.howTo(), g.actionLabel(),
                        g.toggle() == null ? null : g.toggle().name()))
                .toList();
    }

    @GetMapping("/serial/ports")
    public List<String> ports() {
        return arduino.listPorts();
    }

    @PostMapping("/serial/connect")
    public ArduinoInfo connect(@RequestBody ConnectRequest request) {
        if (request.port() == null || request.port().isBlank()) {
            arduino.disconnect();
        } else if (!arduino.connect(request.port().split(" ")[0])) {
            throw new IllegalArgumentException("No se pudo abrir " + request.port()
                    + ". Cierra el Monitor Serie del IDE de Arduino y revisa el cable.");
        }
        return new ArduinoInfo(arduino.isConnected(), arduino.portName(), arduino.lastMessage());
    }
}
