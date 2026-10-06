package com.smarthome.vision.arduino;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Comunicacion serial USB con el Arduino (libreria jSerialComm).
 *
 *  Java  -> Arduino : "RED_ON\n", "MAIN_OFF\n", "LIGHTS_ON\n", ...
 *  Arduino -> Java  : "READY", "OK:RED_ON", "ERR:UNKNOWN:..."
 *
 * Si no hay Arduino conectado, la aplicacion sigue funcionando en
 * MODO SIMULADO: los comandos se muestran en el log y en la web.
 */
@Service
public class ArduinoService {

    private static final Logger log = LoggerFactory.getLogger(ArduinoService.class);
    private static final List<String> ARDUINO_HINTS = List.of("arduino", "ch340", "ch910", "usb-serial", "usb serial", "cp210", "ftdi");

    private final boolean enabled;
    private final String configuredPort;
    private final int baudRate;

    private SerialPort port;
    private volatile String lastMessage = "";
    private volatile Runnable readyListener = () -> { };

    public ArduinoService(@Value("${arduino.enabled}") boolean enabled,
                          @Value("${arduino.port}") String configuredPort,
                          @Value("${arduino.baud-rate}") int baudRate) {
        this.enabled = enabled;
        this.configuredPort = configuredPort;
        this.baudRate = baudRate;
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.info("Arduino deshabilitado (arduino.enabled=false): MODO SIMULADO");
            return;
        }
        String name = "auto".equalsIgnoreCase(configuredPort) ? autoDetect() : configuredPort;
        if (name == null) {
            log.warn("No se encontro ningun Arduino. MODO SIMULADO. Puertos disponibles: {}", listPorts());
            return;
        }
        connect(name);
    }

    /** Abre el puerto indicado (por ejemplo "COM3" o "/dev/ttyUSB0"). */
    public synchronized boolean connect(String portName) {
        disconnect();
        SerialPort candidate = SerialPort.getCommPort(portName);
        candidate.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        if (!candidate.openPort()) {
            log.error("No se pudo abrir {}. ¿Esta abierto el Monitor Serie del IDE de Arduino?", portName);
            return false;
        }
        candidate.addDataListener(new LineListener());
        port = candidate;
        log.info("Conectado a Arduino en {} ({} baudios). Esperando reinicio de la placa...", portName, baudRate);
        sleep(2000); // al abrir el puerto, el Arduino Uno/Nano se reinicia
        return true;
    }

    public synchronized void disconnect() {
        if (port != null) {
            port.removeDataListener();
            port.closePort();
            port = null;
        }
    }

    /** Envia un comando. Devuelve true si llego al Arduino, false si estamos en modo simulado. */
    public synchronized boolean send(String command) {
        if (!isConnected()) {
            log.info("[SIMULADO] -> {}", command);
            return false;
        }
        byte[] bytes = (command + "\n").getBytes(StandardCharsets.US_ASCII);
        int written = port.writeBytes(bytes, bytes.length);
        if (written < 0) {
            log.error("Error escribiendo en {}. ¿Se desconecto el cable?", port.getSystemPortName());
            disconnect();
            return false;
        }
        log.info("[SERIAL {}] -> {}", port.getSystemPortName(), command);
        return true;
    }

    public List<String> listPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(p -> p.getSystemPortName() + " - " + p.getPortDescription())
                .toList();
    }

    public synchronized boolean isConnected() {
        return port != null && port.isOpen();
    }

    public synchronized String portName() {
        return port != null ? port.getSystemPortName() : null;
    }

    public String lastMessage() {
        return lastMessage;
    }

    @PreDestroy
    void shutdown() {
        disconnect();
    }

    // ---------------------------------------------------------------- helpers

    private String autoDetect() {
        for (SerialPort p : SerialPort.getCommPorts()) {
            String description = (p.getDescriptivePortName() + " " + p.getPortDescription()).toLowerCase(Locale.ROOT);
            if (ARDUINO_HINTS.stream().anyMatch(description::contains)) {
                log.info("Arduino detectado automaticamente: {} ({})", p.getSystemPortName(), p.getDescriptivePortName());
                return p.getSystemPortName();
            }
        }
        return null;
    }

    /** Procesa cada linea recibida desde el Arduino. */
    private void handleLine(String line) {
        if (line.isEmpty()) {
            return;
        }
        lastMessage = line;
        log.info("[SERIAL] <- {}", line);
        if (line.equals("READY")) {
            // La placa acaba de arrancar (o se reinicio): sus focos estan apagados.
            // En otro hilo para no bloquear la lectura del puerto.
            new Thread(readyListener, "arduino-resync").start();
        }
    }

    /** Accion a ejecutar cada vez que el Arduino envia "READY" (arranque o reinicio). */
    public void onReady(Runnable listener) {
        this.readyListener = listener;
    }

    private class LineListener implements SerialPortMessageListener {
        @Override
        public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_RECEIVED | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
        }

        @Override
        public byte[] getMessageDelimiter() {
            return new byte[]{'\n'};
        }

        @Override
        public boolean delimiterIndicatesEndOfMessage() {
            return true;
        }

        @Override
        public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
                log.warn("Arduino desconectado. Pasando a MODO SIMULADO.");
                new Thread(ArduinoService.this::disconnect).start();
                return;
            }
            handleLine(new String(event.getReceivedData(), StandardCharsets.US_ASCII).trim());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
