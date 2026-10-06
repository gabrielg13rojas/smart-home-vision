package com.smarthome.vision.home;

/** Los 4 focos de la maqueta. Cada uno tiene su comando de encendido y apagado. */
public enum Light {
    MAIN("Foco 220V"),
    RED("Foco rojo"),
    GREEN("Foco verde"),
    BLUE("Foco azul");

    private final String label;

    Light(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** MAIN -> MAIN_ON, RED -> RED_ON, ... */
    public Command onCommand() {
        return Command.valueOf(name() + "_ON");
    }

    public Command offCommand() {
        return Command.valueOf(name() + "_OFF");
    }
}
