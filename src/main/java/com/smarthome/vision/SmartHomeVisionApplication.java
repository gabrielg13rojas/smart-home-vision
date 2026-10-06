package com.smarthome.vision;

import nu.pattern.OpenCV;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Smart Home Vision - demo educativa.
 *
 * Flujo: WEBCAM -> ROSTRO -> AUTORIZACION -> MANO -> GESTO -> SPRING BOOT -> ARDUINO
 */
@SpringBootApplication
public class SmartHomeVisionApplication {

    public static void main(String[] args) {
        // Carga la libreria nativa de OpenCV incluida en el jar org.openpnp:opencv.
        OpenCV.loadLocally();
        SpringApplication.run(SmartHomeVisionApplication.class, args);
    }
}
