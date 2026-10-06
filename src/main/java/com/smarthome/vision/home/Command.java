package com.smarthome.vision.home;

/**
 * Comandos que Spring Boot envia al Arduino por el puerto serial.
 * El texto enviado es exactamente el nombre del enum seguido de '\n'
 * (por ejemplo "RED_ON\n") y debe coincidir con arduino/smart_home/smart_home.ino.
 */
public enum Command {
    // --- Focos individuales
    MAIN_ON("💡", "ENCENDER FOCO 220V"),
    MAIN_OFF("💡", "APAGAR FOCO 220V"),
    RED_ON("🔴", "ENCENDER FOCO ROJO"),
    RED_OFF("🔴", "APAGAR FOCO ROJO"),
    GREEN_ON("🟢", "ENCENDER FOCO VERDE"),
    GREEN_OFF("🟢", "APAGAR FOCO VERDE"),
    BLUE_ON("🔵", "ENCENDER FOCO AZUL"),
    BLUE_OFF("🔵", "APAGAR FOCO AZUL"),
    // --- Escenas
    LIGHTS_ON("✨", "ENCENDER TODOS LOS FOCOS"),
    ALL_OFF("⏻", "APAGAR TODO"),
    HOME_MODE("🏠", "MODO CASA");

    private final String emoji;
    private final String label;

    Command(String emoji, String label) {
        this.emoji = emoji;
        this.label = label;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }
}
