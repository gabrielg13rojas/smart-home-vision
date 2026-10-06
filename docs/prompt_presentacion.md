Actúa como diseñador de presentaciones técnicas y docente de ingeniería. Crea una presentación de **PowerPoint (.pptx, 16:9, en español)** para un **taller práctico de 3 horas** sobre el proyecto **"Smart Home Vision"**. El público son estudiantes y desarrolladores con conocimientos básicos de programación. La usaré **hoy** para dar el taller, así que debe estar lista para proyectar.

## Requisitos de formato

- 28 a 34 diapositivas, 16:9, en español.
- Diseño limpio y claro, **fondo blanco o gris muy claro** (nada de tema oscuro). Paleta: azul `#0d6efd` como color principal, con verde `#198754` (autorizado/OK), rojo `#dc3545` (denegado) y amarillo `#ffc107` (foco 220V). Tipografía sans-serif legible (Segoe UI, Inter o similar). Títulos cortos, máximo 5 o 6 viñetas por diapositiva.
- **Notas del orador** en cada diapositiva: 3 a 6 frases con lo que debo decir y, cuando aplique, una pregunta para el público.
- Diagramas hechos con formas de PowerPoint (cajas, flechas, iconos), no imágenes pixeladas. Si no puedes dibujar algo, deja un marcador claro: `[DIAGRAMA: descripción]`.
- El código va en una caja de texto monoespaciada (Consolas o similar) con fondo gris claro, **máximo 15 líneas por diapositiva**. Usa los fragmentos que te doy más abajo; no inventes código.
- No inventes cifras de mercado ni estadísticas. Si incluyes un dato numérico externo, debe ser verificable y con la fuente en la diapositiva; si no estás seguro, plantéalo como pregunta para el debate.
- Indica en cada diapositiva con una etiqueta pequeña a qué bloque del taller pertenece (Introducción, Visión, Gestos, Arduino, Ética, Negocio, Cierre).

## Descripción del proyecto (datos reales, úsalos tal cual)

**Smart Home Vision** es una demo educativa de domótica. Una webcam reconoce **quién** eres (reconocimiento facial) y **qué quieres hacer** (gesto de la mano). Solo si el rostro está registrado, el gesto se convierte en un comando que viaja por USB a un **Arduino Uno**, que enciende o apaga **4 focos**: uno de **220V** mediante un **módulo relé** y tres **LEDs** (rojo, verde y azul).

Lema del flujo: **ROSTRO → AUTORIZACIÓN → MANO → GESTO → SPRING BOOT → ARDUINO → CASA INTELIGENTE**

Es un **prototipo educativo**, no un sistema biométrico de seguridad: se puede engañar con una foto y está pensado para 2 a 5 usuarios.

### Tecnologías y versiones

| Componente | Tecnología | Versión | Licencia |
|---|---|---|---|
| Lenguaje backend | Java | 17+ (probado con 21) | GPL v2 + Classpath Exception (OpenJDK / Temurin) |
| Framework | Spring Boot (spring-boot-starter-web) | 3.3.5 | Apache 2.0 |
| Visión por computador | OpenCV (paquete Maven `org.openpnp:opencv`, con librerías nativas incluidas) | 4.9.0 | Apache 2.0 (OpenCV 4.5+) |
| Detector de rostros | Modelo **YuNet** (`face_detection_yunet_2023mar.onnx`, ~230 KB) del OpenCV Model Zoo | 2023mar | MIT |
| Reconocimiento facial | Modelo **SFace** (`face_recognition_sface_2021dec.onnx`, ~37 MB) del OpenCV Model Zoo | 2021dec | Apache 2.0 |
| Detección de manos | **MediaPipe Hands** (`@mediapipe/tasks-vision`, modelo `hand_landmarker.task`), corre en el navegador | 0.10.14 | Apache 2.0 |
| Comunicación serial | jSerialComm | 2.11.0 | Doble licencia: Apache 2.0 o LGPL v3 |
| Frontend | HTML + CSS + JavaScript vanilla + **Bootstrap** + Bootstrap Icons | 5.3.3 / 1.11.3 | MIT |
| Build | Maven (con Maven Wrapper, no hay que instalarlo) | 3.9.9 | Apache 2.0 |
| Microcontrolador | Arduino Uno, sketch en C++ (Arduino core) | — | Arduino core: LGPL; Arduino IDE 2: AGPL v3 |

Incluye una diapositiva de **licencias**: qué significa cada una (permisivas como MIT y Apache 2.0 frente a copyleft como LGPL, GPL y AGPL), qué obligaciones tendría usar esto en un producto comercial (conservar avisos de copyright y el texto de la licencia, el aviso NOTICE de Apache, y que en LGPL el usuario pueda reemplazar la librería) y una nota: *"verificar las licencias vigentes antes de un uso comercial"*. Advierte también que el `.task` de MediaPipe y los modelos ONNX son **modelos de terceros** con sus propias tarjetas de modelo (model cards).

### ¿Hubo que entrenar algo? NO

Diapositiva clave. **No se entrenó ningún modelo**:

- **YuNet** y **SFace** vienen **preentrenados** por sus autores sobre grandes conjuntos de datos públicos de rostros. Se descargan solos la primera vez en `data/models/`.
- **MediaPipe Hands** viene preentrenado por Google.
- El **"registro" de un usuario no es entrenamiento**: solo calcula y guarda un vector numérico (*embedding*) de su cara. Es *one-shot / few-shot*: se toman 5 fotos, se convierten en 5 vectores de 128 números y se guardan en `data/users.json`. **Las fotos se descartan.**
- Los **gestos no usan machine learning propio**: son **reglas geométricas** escritas a mano sobre los 21 puntos de la mano.

Explica la diferencia entre **entrenar** un modelo, hacer **fine-tuning** y **usar un modelo preentrenado para extraer características** (lo que hace este proyecto).

### Arquitectura (para un diagrama de 3 bloques)

```
NAVEGADOR (http://localhost:8080)
  Webcam → <video> → (a) MediaPipe Hands (JS): 21 puntos por mano, ~30-60 fps
                   → (b) foto JPEG 640x480 cada 250 ms
  Canvas: dibuja video en espejo + recuadros + esqueleto de la mano + etiquetas
  Dashboard Bootstrap: acceso, manos detectadas, 4 focos, historial
        │ POST /api/recognition (foto)     │ POST /api/gesture (21 puntos, cada 120 ms)     ▲ GET /api/status (cada 700 ms)
        ▼                                  ▼                                               │
SPRING BOOT (Java)
  FaceService (OpenCV: YuNet + SFace) → HomeState (usuario actual, autorizado sí/no)
  GestureClassifier (reglas) → CommandService (¿gesto estable 0,7 s? ¿usuario autorizado?)
  ArduinoService (jSerialComm)
        │ USB serial 9600 baudios, texto "RED_ON\n"
        ▼
ARDUINO UNO → digitalWrite → LEDs D4/D5/D6 y relé D7 (foco 220V) → responde "OK:RED_ON"
```

Explica **por qué MediaPipe corre en el navegador**: MediaPipe Hands no tiene una librería estable para Java de escritorio; su versión oficial JavaScript corre en el navegador con GPU (WebGL/WASM). El navegador solo calcula los puntos; **las decisiones (gesto, autorización, comando) las toma Spring Boot**. Es una decisión de arquitectura pragmática que vale la pena discutir.

API REST del proyecto (una diapositiva en forma de tabla):

| Método | Ruta | Para qué |
|---|---|---|
| POST | `/api/users/register` | Registrar un rostro (nombre + 5 fotos en base64) |
| GET / DELETE | `/api/users`, `/api/users/{name}` | Listar o borrar usuarios |
| POST | `/api/recognition` | Detectar y reconocer rostros en una foto |
| POST | `/api/gesture` | Clasificar el gesto, aplicar la autorización y enviar el comando |
| GET | `/api/gestures` | Guía de gestos para la web |
| GET | `/api/status` | Estado completo del dashboard |
| POST | `/api/commands/{cmd}` | Prueba manual de un comando (sin cámara) |
| GET / POST | `/api/serial/ports`, `/api/serial/connect` | Elegir el puerto del Arduino |

### Cómo procesa OpenCV los datos (paso a paso, ideal como diagrama de tubería)

1. El navegador captura un frame, lo reduce a 640 px de ancho y lo envía como **JPEG en base64**.
2. Java lo decodifica: `Imgcodecs.imdecode(...)` → una matriz **`Mat`** de píxeles en formato **BGR** (alto × ancho × 3 canales de 8 bits).
3. **YuNet** (`FaceDetectorYN`, red neuronal convolucional ligera) devuelve una fila por rostro con **15 números**: `x, y, ancho, alto`, **5 puntos faciales** (2 ojos, nariz, 2 comisuras de la boca) y una **puntuación de confianza** (mínimo 0,8).
4. **Alineación**: `alignCrop` usa los 5 puntos para rotar y recortar la cara a **112×112 px**, siempre en la misma posición.
5. **SFace** (`FaceRecognizerSF`) convierte esa cara en un **embedding de 128 números** (un punto en un espacio de 128 dimensiones). Se normaliza a longitud 1 (norma L2).
6. Se compara con los embeddings guardados mediante **similitud coseno** (producto punto de vectores normalizados: 1 = idénticos, 0 = sin relación).
7. Si la mejor similitud es **≥ 0,363** (umbral recomendado por OpenCV para SFace), la persona queda **reconocida**. El porcentaje en pantalla es un indicador visual del prototipo (umbral = 60 %, similitud 0,7 = 100 %), **no** una métrica biométrica.
8. Regla de autorización: si en la imagen hay solo rostros desconocidos, se bloquea al instante; si la cara deja de verse, la autorización dura 2 s más.

Código real para una diapositiva (similitud coseno):

```java
private static double cosine(float[] a, float[] b) {
    double dot = 0;
    for (int i = 0; i < a.length; i++) {
        dot += a[i] * b[i];
    }
    return dot; // ambos vectores ya estan normalizados
}
// ...
boolean known = bestSimilarity >= matchThreshold; // 0.363
```

Incluye una analogía para el público: *"el embedding es como la huella numérica de tu cara: dos fotos tuyas caen cerca en ese espacio; las de otra persona, lejos"*. Propón un dibujo 2D simplificado con puntos agrupados por persona.

### Cómo se reconocen los gestos

MediaPipe entrega **21 puntos (landmarks)** por mano: 0 = muñeca; 1-4 = pulgar; 5-8 = índice; 9-12 = medio; 13-16 = anular; 17-20 = meñique. Las puntas son 4, 8, 12, 16 y 20. Dibuja una mano esquemática con los 21 puntos numerados:

```
            8   12  16  20        ← puntas
            |   |   |   |
            6   10  14  18        ← articulación media
        4   |   |   |   |
         \  5---9---13--17        ← nudillos
          3  \          /
           2  \        /
            1--\------/
                 0                ← muñeca
```

**Reglas** (archivo `GestureClassifier.java`):

- Un dedo (índice, medio, anular o meñique) está **extendido** si su punta está más lejos de la muñeca que su articulación media (×1,1).
- El **pulgar** está extendido si su punta está lejos del centro de la palma (punto 9): más de 0,6 veces el tamaño de la palma. Medido con fotos reales: ~1,0 palmas con el pulgar extendido frente a ~0,2 doblado.
- Todo se mide **en relación con el tamaño de la palma** (distancia del punto 0 al 9), así funciona cerca o lejos de la cámara.

| Índice | Medio | Anular | Meñique | Gesto | Acción |
|:-:|:-:|:-:|:-:|---|---|
| ✓ | ✗ | ✗ | ✗ | ☝️ Un dedo | Enciende/apaga el **foco 220V** |
| ✓ | ✓ | ✗ | ✗ | ✌️ Dos dedos | Enciende/apaga el **foco rojo** |
| ✓ | ✓ | ✓ | ✗ | 🤟 Tres dedos | Enciende/apaga el **foco verde** |
| ✓ | ✓ | ✓ | ✓ (pulgar doblado) | 🖖 Cuatro dedos | Enciende/apaga el **foco azul** |
| ✓ | ✓ | ✓ | ✓ (con pulgar) | ✋ Palma | **Todos encendidos** (`LIGHTS_ON`) |
| ✗ | ✗ | ✗ | ✗ | ✊ Puño | **Todo apagado** (`ALL_OFF`) |
| ✗ | ✗ | ✗ | ✗ (pulgar arriba) | 👍 Pulgar arriba | **Modo casa**: 220V + azul (`HOME_MODE`) |

Anti-errores (diapositiva "¿por qué no se dispara solo?"):

- El gesto debe mantenerse **0,7 s** (en pantalla se llena una barra de progreso).
- Cada gesto se ejecuta **una sola vez**; para repetirlo hay que bajar la mano. Así ☝️ enciende y el siguiente ☝️ apaga (*toggle*).
- **Spring decide si enviar `_ON` u `_OFF`** según el estado actual del foco, para que Java y Arduino no se desincronicen.

Código real (núcleo del clasificador):

```java
if (index && middle && ring && pinky) {
    return thumb ? Gesture.OPEN_PALM : Gesture.FOUR_FINGERS;
}
if (index && middle && ring)          return Gesture.THREE_FINGERS;
if (index && middle && !ring && !pinky) return Gesture.TWO_FINGERS;
if (index && !middle && !ring && !pinky) return Gesture.ONE_FINGER;
if (!index && !middle && !ring && !pinky) {
    double thumbHeight = p.get(INDEX_MCP).y() - p.get(THUMB_TIP).y();
    if (thumb && thumbHeight > 0.3 * palm) return Gesture.THUMBS_UP;
    return Gesture.FIST;
}
```

### Cómo crear un gesto nuevo (diapositiva paso a paso, como reto del taller)

Ejemplo: 🤘 "rock" (índice + meñique) para encender solo los LEDs de colores.

1. `Gesture.java`: añadir `ROCK("🤘", "ROCK", "Índice y meñique", null, Command.COLORS_ON)`.
2. `GestureClassifier.java`: añadir la regla `if (index && !middle && !ring && pinky) return Gesture.ROCK;`
3. `Command.java`: añadir `COLORS_ON`, y en `HomeState.apply()` qué focos cambia.
4. `smart_home.ino`: añadir `else if (cmd == "COLORS_ON") { ... }`.
5. Escribir un test en `GestureClassifierTest.java` con una mano sintética.
6. La guía de gestos de la web se actualiza **sola** (la lee de `GET /api/gestures`).

Menciona la alternativa con machine learning: grabar ejemplos de landmarks y entrenar un clasificador pequeño (k-NN, árbol de decisión o una red simple), o usar **MediaPipe Gesture Recognizer** con *Model Maker*. Explica cuándo conviene cada opción (reglas = transparentes y sin datos; ML = gestos complejos o dinámicos).

### Comunicación con Arduino (protocolo serial)

- USB serial a **9600 baudios**, texto plano, un comando por línea terminado en `\n`.
- Java → Arduino: `MAIN_ON` `MAIN_OFF` `RED_ON` `RED_OFF` `GREEN_ON` `GREEN_OFF` `BLUE_ON` `BLUE_OFF` `LIGHTS_ON` `ALL_OFF` `HOME_MODE` `PING`.
- Arduino → Java: `READY` al arrancar, `OK:<comando>`, `ERR:UNKNOWN:<texto>`, `PONG`.
- Spring detecta solo el puerto (busca "Arduino", "CH340", etc. e ignora el Bluetooth). Si no hay placa, funciona en **modo simulado**.
- Al abrir el puerto, el Uno se reinicia, por eso Java espera 2 s.

Código real del Arduino (fragmento):

```cpp
void loop() {
  while (Serial.available() > 0) {
    char c = Serial.read();
    if (c == '\n') { command.trim(); executeCommand(command); command = ""; }
    else if (c != '\r') { command += c; }
  }
}
void executeCommand(String cmd) {
  if (cmd == "RED_ON")        digitalWrite(RED_PIN, HIGH);
  else if (cmd == "MAIN_ON")  setMain(true);   // rele del foco 220V
  else if (cmd == "ALL_OFF")  setAll(false, false, false, false);
  // ...
  Serial.print("OK:"); Serial.println(cmd);
}
```

Código real de Java (envío):

```java
byte[] bytes = (command + "\n").getBytes(StandardCharsets.US_ASCII);
port.writeBytes(bytes, bytes.length);
```

### Ilustración del circuito (dibújala como esquema de conexiones)

Placa: **Arduino Uno**. Componentes: 3 LEDs (rojo, verde, azul), 3 resistencias de **220 Ω**, 1 **módulo relé de 5 V** de un canal, portalámparas y foco de 220V, protoboard.

| Componente | Conexión |
|---|---|
| LED rojo | **D4** → resistencia 220 Ω → pata larga (ánodo) del LED; pata corta (cátodo) → **GND** |
| LED verde | **D5** → 220 Ω → LED → GND |
| LED azul | **D6** → 220 Ω → LED → GND |
| Módulo relé | **IN → D7**, **VCC → 5V**, **GND → GND** |
| Foco 220V | Fase (L) del enchufe → **COM** del relé; **NO** del relé → borne 1 del portalámparas; Neutro (N) → borne 2 |

```
 ARDUINO UNO
  D4 ──[220Ω]──►|── GND   LED rojo
  D5 ──[220Ω]──►|── GND   LED verde
  D6 ──[220Ω]──►|── GND   LED azul
  D7 ───────────────────── IN  ┐
  5V ───────────────────── VCC ├ MÓDULO RELÉ ── COM ◄── Fase 220V
 GND ───────────────────── GND ┘               NO  ──► Foco ──► Neutro
```

- El relé suele ser **activo en LOW** (`RELAY_ACTIVE_LOW = true` en el sketch). Para practicar sin 220V se pone un LED amarillo en D7 con `RELAY_ACTIVE_LOW = false`.
- Incluye una diapositiva destacada de **SEGURIDAD ELÉCTRICA**: 220V puede ser mortal; solo una persona con experiencia cablea el lado de alta tensión, siempre desenchufado, con bornes aislados y sobre superficie no conductora. El relé **aísla** el Arduino (5 V) de la red (220V). Se usa el contacto NO (normalmente abierto).

### Interfaz web (describe con un boceto o marcador `[CAPTURA: ...]`)

- Barra superior con los pasos del flujo, que se colorean en vivo.
- Video en espejo con recuadro **verde** en la cara (`✓ Gabriel · 92 %`) o **rojo** (`🔒 Desconocido`).
- Recuadro en cada mano con su gesto (`✌️ DOS DEDOS`), esqueleto con un color por dedo, puntas rellenas (dedo extendido) o huecas (doblado) y una barra de progreso.
- Panel "Manos detectadas" con chips: Pulgar · Índice · Medio · Anular · Meñique.
- 4 focos ilustrados que se iluminan (también se encienden con un clic para pruebas).
- Selector de cámara, historial (las filas DENEGADO en rojo), registro de usuarios, panel de Arduino.
- Mensaje grande sobre el video: `✌️ DOS DEDOS → ENCENDIENDO FOCO ROJO` o `🔒 ACCESO DENEGADO`.

## Debate ético sobre biometría (3 a 4 diapositivas, con preguntas abiertas)

Plantéalo como debate, no como sermón. Temas:

- **La cara como dato sensible**: un embedding **sigue siendo un dato biométrico**, aunque no sea una foto. ¿Se puede reconstruir algo a partir de él? ¿Quién debe custodiarlo?
- **Consentimiento informado**: en el taller cada persona elige registrarse y puede borrarse. ¿Qué pasa en una casa con visitas, empleados domésticos o niños?
- **Privacidad por diseño**: aquí todo es **local** (sin nube) y no se guardan fotos. Compara con productos comerciales que suben video a la nube.
- **Seguridad y suplantación**: el prototipo se engaña con una foto (no tiene detección de vida / *liveness*). ¿Abrirías la puerta de tu casa con esto?
- **Sesgos**: los modelos de reconocimiento facial pueden funcionar peor con ciertos tonos de piel, edades o condiciones de luz, según cómo se entrenaron. ¿Cómo lo mediríamos?
- **Vigilancia y *function creep***: una cámara para encender luces podría usarse para registrar quién entra y sale. ¿Dónde está el límite?
- **Marco legal**: el RGPD europeo (art. 9) trata la biometría como categoría especial; la LGPD de Brasil también la considera sensible. Invita al público a investigar la ley de protección de datos personales de su propio país. No afirmes artículos ni números de ley que no estén verificados.
- **El lado positivo**: accesibilidad para personas con movilidad reducida, control sin contacto, personas mayores.
- Cierra con 3 preguntas para votar a mano alzada.

## Oportunidades de negocio en domótica (2 a 3 diapositivas)

Sin cifras inventadas. Ideas para discutir:

- **Accesibilidad y adultos mayores**: control por gestos o voz para personas con movilidad reducida; alertas.
- **Modernizar casas existentes (retrofit)**: módulos relé o Wi-Fi (ESP32) para casas sin domótica, con instalación como servicio.
- **Hoteles, oficinas y salas de reuniones**: escenas de luz por gesto, control sin contacto (higiene).
- **Eficiencia energética**: apagar lo que nadie usa; medición de consumo.
- **Educación y kits STEM**: vender este mismo taller o kit (Arduino + cámara + software) a colegios y universidades.
- **Integración con estándares**: Matter, Zigbee, Home Assistant y MQTT, como próximo paso técnico.
- **Modelos de negocio**: hardware + instalación, suscripción de software, mantenimiento, consultoría de privacidad.
- **Riesgos**: seguridad (IoT hackeado), regulación de datos biométricos, confianza del usuario y soporte.
- Diapositiva final de **lienzo rápido** (problema, cliente, solución, cómo se cobra) para que los asistentes lo completen en grupos.

## Estructura sugerida de diapositivas

1. Portada: "Smart Home Vision — Visión por computador + Spring Boot + Arduino", subtítulo "Taller práctico", espacio para mi nombre y la fecha.
2. Agenda del taller (3 h) con bloques y tiempos.
3. ¿Qué vamos a construir? Demo en una imagen + lema del flujo.
4. Arquitectura general (diagrama de 3 bloques).
5. Flujo de información paso a paso (diagrama de secuencia: navegador → Spring → Arduino → respuesta).
6. Tecnologías y versiones (tabla).
7. Licencias (tabla + qué implican).
8. ¿Entrenamos algo? NO: preentrenado frente a entrenar frente a fine-tuning.
9. Visión por computador en 1 minuto: imagen = matriz de números (`Mat`, BGR).
10. Detección facial con YuNet (recuadro + 5 puntos + confianza).
11. Alineación y embedding con SFace (112×112 → 128 números).
12. Similitud coseno y umbral (dibujo 2D + código).
13. Registro de usuarios: qué se guarda y qué NO (sin fotos, `users.json`).
14. Regla de autorización (diagrama de decisión: ¿hay rostro? ¿registrado? → bloquear o permitir).
15. MediaPipe Hands: 21 landmarks (mano numerada).
16. ¿Por qué MediaPipe en el navegador? (decisión de arquitectura).
17. De puntos a dedos: reglas geométricas (extendido frente a doblado).
18. Tabla de gestos → focos.
19. Anti-errores: mantener 0,7 s, una vez por gesto, toggle.
20. Código del clasificador.
21. Cómo crear un gesto nuevo (6 pasos) y la alternativa con ML.
22. Spring Boot: API REST (tabla) y responsabilidades de cada clase.
23. Protocolo serial Java ↔ Arduino (comandos y respuestas).
24. Código Arduino.
25. Circuito: esquema de conexiones del Uno.
26. Seguridad eléctrica con 220V y el relé.
27. Interfaz web (boceto o captura).
28. Demo en vivo: guion de 6 pasos (registrar → reconocer → ✌️ rojo → ☝️ 220V → ✊ apagar → persona desconocida = denegado).
29. Problemas comunes y soluciones (cámara, puerto COM ocupado por el Monitor Serie, luz, umbral).
30-32. Debate ético sobre biometría.
33-34. Oportunidades de negocio + lienzo rápido.
35. Retos para llevar a casa + cierre y preguntas.

Al terminar, entrega el archivo `.pptx` y una lista de los marcadores `[DIAGRAMA]` o `[CAPTURA]` que yo deba completar a mano.
