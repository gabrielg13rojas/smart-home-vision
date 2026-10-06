package com.smarthome.vision.vision;

import com.smarthome.vision.home.Command;
import com.smarthome.vision.home.Light;

/**
 * Los gestos de la demo. Cada gesto hace UNA de estas dos cosas:
 *  - toggle: enciende el foco si esta apagado, o lo apaga si esta encendido;
 *  - command: ejecuta siempre el mismo comando (escenas).
 */
public enum Gesture {
    NONE("", "SIN GESTO", "", null, null),
    ONE_FINGER("☝️", "UN DEDO", "Solo el índice", Light.MAIN, null),
    TWO_FINGERS("✌️", "DOS DEDOS", "Índice y medio", Light.RED, null),
    THREE_FINGERS("🤟", "TRES DEDOS", "Índice, medio y anular", Light.GREEN, null),
    FOUR_FINGERS("🖖", "CUATRO DEDOS", "Cuatro dedos, pulgar doblado", Light.BLUE, null),
    OPEN_PALM("✋", "PALMA ABIERTA", "Los cinco dedos", null, Command.LIGHTS_ON),
    FIST("✊", "PUÑO", "Mano cerrada", null, Command.ALL_OFF),
    THUMBS_UP("👍", "PULGAR ARRIBA", "Puño con el pulgar hacia arriba", null, Command.HOME_MODE);

    private final String emoji;
    private final String label;
    private final String howTo;
    private final Light toggle;
    private final Command command;

    Gesture(String emoji, String label, String howTo, Light toggle, Command command) {
        this.emoji = emoji;
        this.label = label;
        this.howTo = howTo;
        this.toggle = toggle;
        this.command = command;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }

    public String howTo() {
        return howTo;
    }

    /** Foco que enciende/apaga este gesto (o null si es una escena). */
    public Light toggle() {
        return toggle;
    }

    /** Comando fijo de este gesto (o null si es un toggle). */
    public Command command() {
        return command;
    }

    /** Texto para la guia de gestos: "Enciende/apaga Foco rojo" o "MODO CASA". */
    public String actionLabel() {
        if (toggle != null) {
            return "Enciende / apaga " + toggle.label();
        }
        return command == null ? "" : command.label();
    }
}
