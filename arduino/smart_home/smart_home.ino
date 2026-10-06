/*
  =====================================================================
   SMART HOME VISION - Arduino Uno
  =====================================================================
   Recibe comandos de texto por USB (Serial, 9600 baudios), uno por linea:

     MAIN_ON  / MAIN_OFF   -> foco 220V (modulo RELE en el pin 7)
     RED_ON   / RED_OFF    -> foco rojo  (LED pin 4)
     GREEN_ON / GREEN_OFF  -> foco verde (LED pin 5)
     BLUE_ON  / BLUE_OFF   -> foco azul  (LED pin 6)
     LIGHTS_ON             -> los 4 focos encendidos
     ALL_OFF               -> los 4 focos apagados
     HOME_MODE             -> foco 220V + azul encendidos, rojo y verde apagados
     PING                  -> responde PONG (para probar la conexion)

   Responde:  OK:<COMANDO>  o  ERR:UNKNOWN:<texto>

   Conexiones:
     Pin 4  -> resistencia 220Ω -> LED rojo  (pata larga) -> GND
     Pin 5  -> resistencia 220Ω -> LED verde (pata larga) -> GND
     Pin 6  -> resistencia 220Ω -> LED azul  (pata larga) -> GND
     Pin 7  -> IN del modulo rele (VCC -> 5V, GND -> GND). El rele conmuta el foco de 220V.
              ⚠️ 220V es PELIGROSO: solo un adulto con experiencia debe cablear esa parte.
              Para probar sin el foco, pon un LED con resistencia en el pin 7
              y cambia RELAY_ACTIVE_LOW a false.

   Prueba sin la aplicacion: Monitor Serie (9600 baudios, "Nueva linea"),
   escribe RED_ON y Enter. ¡Cierra el Monitor Serie antes de arrancar
   Spring Boot! (el puerto solo puede abrirlo un programa a la vez)

   No necesita librerias adicionales.
  =====================================================================
*/

// ------------------------------------------------------------ Pines
const int RED_PIN = 4;
const int GREEN_PIN = 5;
const int BLUE_PIN = 6;
const int RELAY_PIN = 7;   // foco 220V

// La mayoria de modulos rele se activan con LOW ("active low").
// Si el foco funciona al reves, o usas un LED en lugar del rele, pon false.
const bool RELAY_ACTIVE_LOW = true;

String command = "";

void setup() {
  Serial.begin(9600);

  pinMode(RED_PIN, OUTPUT);
  pinMode(GREEN_PIN, OUTPUT);
  pinMode(BLUE_PIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  setAll(false, false, false, false);

  // Animacion de arranque: rojo, verde, azul (sirve para comprobar el cableado)
  int leds[] = {RED_PIN, GREEN_PIN, BLUE_PIN};
  for (int i = 0; i < 3; i++) {
    digitalWrite(leds[i], HIGH);
    delay(200);
    digitalWrite(leds[i], LOW);
  }

  Serial.println("READY");
}

void loop() {
  // Leer caracteres hasta encontrar el fin de linea '\n'
  while (Serial.available() > 0) {
    char c = Serial.read();
    if (c == '\n') {
      command.trim();
      if (command.length() > 0) {
        executeCommand(command);
      }
      command = "";
    } else if (c != '\r') {
      command += c;
    }
  }
}

void executeCommand(String cmd) {
  // --- Focos individuales
  if (cmd == "MAIN_ON") {
    setMain(true);
  } else if (cmd == "MAIN_OFF") {
    setMain(false);
  } else if (cmd == "RED_ON") {
    digitalWrite(RED_PIN, HIGH);
  } else if (cmd == "RED_OFF") {
    digitalWrite(RED_PIN, LOW);
  } else if (cmd == "GREEN_ON") {
    digitalWrite(GREEN_PIN, HIGH);
  } else if (cmd == "GREEN_OFF") {
    digitalWrite(GREEN_PIN, LOW);
  } else if (cmd == "BLUE_ON") {
    digitalWrite(BLUE_PIN, HIGH);
  } else if (cmd == "BLUE_OFF") {
    digitalWrite(BLUE_PIN, LOW);

  // --- Escenas
  } else if (cmd == "LIGHTS_ON") {
    setAll(true, true, true, true);
  } else if (cmd == "ALL_OFF") {
    setAll(false, false, false, false);
  } else if (cmd == "HOME_MODE") {
    setAll(true, false, false, true);

  } else if (cmd == "PING") {
    Serial.println("PONG");
    return;
  } else {
    Serial.print("ERR:UNKNOWN:");
    Serial.println(cmd);
    return;
  }
  Serial.print("OK:");
  Serial.println(cmd);
}

void setMain(bool on) {
  bool level = RELAY_ACTIVE_LOW ? !on : on;
  digitalWrite(RELAY_PIN, level ? HIGH : LOW);
}

void setAll(bool main, bool red, bool green, bool blue) {
  setMain(main);
  digitalWrite(RED_PIN, red ? HIGH : LOW);
  digitalWrite(GREEN_PIN, green ? HIGH : LOW);
  digitalWrite(BLUE_PIN, blue ? HIGH : LOW);
}
