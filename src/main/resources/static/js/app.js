// =====================================================================
//  SMART HOME VISION - lógica del dashboard
//
//  Navegador                                   Spring Boot
//  ─────────                                   ───────────
//  webcam ──► foto JPEG cada 250 ms ──────────► POST /api/recognition  (OpenCV: rostro + nombre)
//  MediaPipe ► 21 puntos por mano cada 120 ms ► POST /api/gesture      (gesto + autorización + Arduino)
//  dashboard ◄──────────── cada 700 ms ──────── GET  /api/status
// =====================================================================
import { createHandTracker, HAND_CONNECTIONS } from "./hands.js";

const RECOGNITION_INTERVAL_MS = 250;
const GESTURE_INTERVAL_MS = 120;
const STATUS_INTERVAL_MS = 700;
const FRAME_WIDTH = 640; // ancho de las imágenes enviadas a Spring Boot
const BANNER_MS = 2500;

/** Los 4 focos de la maqueta (los ids coinciden con el enum Light de Java). */
const LIGHTS = [
    { id: "MAIN", name: "Foco 220V", color: "#ffd166", tag: "220V" },
    { id: "RED", name: "Foco rojo", color: "#ff4d4f" },
    { id: "GREEN", name: "Foco verde", color: "#2ee66b" },
    { id: "BLUE", name: "Foco azul", color: "#3d8bfd" },
];

const ACTION_TEXT = {
    MAIN_ON: "→ ENCENDIENDO FOCO 220V", MAIN_OFF: "→ APAGANDO FOCO 220V",
    RED_ON: "→ ENCENDIENDO FOCO ROJO", RED_OFF: "→ APAGANDO FOCO ROJO",
    GREEN_ON: "→ ENCENDIENDO FOCO VERDE", GREEN_OFF: "→ APAGANDO FOCO VERDE",
    BLUE_ON: "→ ENCENDIENDO FOCO AZUL", BLUE_OFF: "→ APAGANDO FOCO AZUL",
    LIGHTS_ON: "→ ENCENDIENDO TODOS LOS FOCOS", ALL_OFF: "→ APAGANDO TODO",
    HOME_MODE: "→ MODO CASA ACTIVADO",
};

const FINGER_NAMES = ["Pulgar", "Índice", "Medio", "Anular", "Meñique"];
const FINGER_COLORS = ["#f472b6", "#facc15", "#34d399", "#60a5fa", "#a78bfa"];
const FINGER_TIPS = [4, 8, 12, 16, 20];
// Colores de Bootstrap: success, danger, orange, secondary
const COLORS = { ok: "#198754", bad: "#dc3545", warn: "#fd7e14", idle: "#6c757d", palm: "rgba(255,255,255,.9)" };

const $ = (id) => document.getElementById(id);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// Identificador de esta pestaña: Spring lleva un conteo de gestos por pestaña
const CLIENT_ID = Math.random().toString(36).slice(2) + Date.now().toString(36);
const video = $("video");
const canvas = $("canvas");
const ctx = canvas.getContext("2d");

const state = {
    running: false,
    faces: [],          // último resultado de /api/recognition (en píxeles del video)
    facesAt: 0,
    hands: [],          // landmarks de MediaPipe (normalizados 0..1)
    handGestures: [],   // forma de cada mano según Spring Boot: {gesture, emoji, label, fingers[5], extended}
    progress: 0,        // cuánto se ha mantenido el gesto principal (0..1)
    outcome: "NONE",
    authorized: false,
    currentUser: null,
    handTracker: null,
    lights: {},         // {MAIN: true, RED: false, ...}
    gestures: [],       // guía de gestos (GET /api/gestures)
};

// ------------------------------------------------------------------ API
async function api(path, options = {}) {
    const res = await fetch(path, { headers: { "Content-Type": "application/json" }, ...options });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
}
const post = (path, body) => api(path, { method: "POST", body: JSON.stringify(body ?? {}) });

/** Crea un elemento HTML: el(tag, {class, text, ...atributos}, ...hijos). textContent evita inyectar HTML. */
function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
        if (k === "class") node.className = v;
        else if (k === "text") node.textContent = v;
        else if (k === "style") node.style.cssText = v;
        else node.setAttribute(k, v);
    }
    node.append(...children.filter((c) => c != null));
    return node;
}

// ------------------------------------------------------------------ Cámara
const CAMERA_KEY = "smartHomeVision.camera"; // cámara elegida, recordada en este navegador
let currentStream = null;

function savedCamera() {
    try { return localStorage.getItem(CAMERA_KEY) || ""; } catch { return ""; }
}

function saveCamera(deviceId) {
    try { localStorage.setItem(CAMERA_KEY, deviceId); } catch { /* sin almacenamiento: no pasa nada */ }
}

const activeCameraId = () => currentStream?.getVideoTracks()[0]?.getSettings().deviceId || "";

/** Llena el desplegable con las cámaras del equipo (los nombres aparecen después de dar permiso). */
async function listCameras() {
    const select = $("cameraSelect");
    let cameras = [];
    try {
        cameras = (await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === "videoinput");
    } catch (e) {
        console.warn("enumerateDevices", e);
    }
    if (cameras.length === 0) {
        select.replaceChildren(new Option("No se encontraron cámaras", ""));
        return;
    }
    select.replaceChildren(...cameras.map((c, i) => new Option(c.label || `Cámara ${i + 1}`, c.deviceId)));
    const wanted = activeCameraId() || savedCamera();
    if (cameras.some((c) => c.deviceId === wanted)) select.value = wanted;
}

/** Abre una cámara concreta (deviceId) o la predeterminada (""). */
async function openCamera(deviceId) {
    const constraints = { width: { ideal: 640 }, height: { ideal: 480 } };
    if (deviceId) constraints.deviceId = { exact: deviceId };
    let stream;
    try {
        stream = await navigator.mediaDevices.getUserMedia({ video: constraints, audio: false });
    } catch (e) {
        // La cámara recordada ya no está conectada: usamos la predeterminada
        if (deviceId && (e.name === "OverconstrainedError" || e.name === "NotFoundError")) return openCamera("");
        throw e;
    }
    currentStream = stream;
    video.srcObject = stream;
    await video.play();
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.parentElement.style.aspectRatio = `${video.videoWidth} / ${video.videoHeight}`;
    saveCamera(activeCameraId());
}

async function startCamera() {
    try {
        await openCamera($("cameraSelect").value || savedCamera());
    } catch (e) {
        alert("No se pudo abrir la cámara: " + e.message +
            "\n\n¿Diste permiso al navegador? ¿Otra aplicación (Zoom, Teams...) la está usando?");
        return;
    }
    $("placeholder").classList.add("d-none");
    await listCameras(); // ahora sí con los nombres reales
    if (state.running) return;
    state.running = true;

    requestAnimationFrame(renderLoop);
    recognitionLoop();
    gestureLoop();
    loadHandTracker();
}

/** Cambia de cámara sin detener la detección. */
async function switchCamera(deviceId) {
    saveCamera(deviceId);
    if (!state.running) return; // se usará al pulsar "Encender cámara"
    const previous = activeCameraId();
    currentStream?.getTracks().forEach((t) => t.stop());
    try {
        await openCamera(deviceId);
    } catch (e) {
        alert("No se pudo abrir esa cámara: " + e.message + "\n\nVolviendo a la anterior.");
        await openCamera(previous).catch(() => {});
    }
    listCameras();
}

const ENGINE_KEY = "smartHomeVision.handEngine"; // "CPU" o "GPU"
let handError = null;

function savedEngine() {
    try { return localStorage.getItem(ENGINE_KEY) || "CPU"; } catch { return "CPU"; }
}

async function loadHandTracker() {
    const msg = $("handsMsg");
    const engine = $("handEngine").value;
    try { localStorage.setItem(ENGINE_KEY, engine); } catch { /* sin almacenamiento */ }
    state.handTracker?.close();
    state.handTracker = null;
    handError = null;
    msg.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span>Cargando MediaPipe Hands (${engine}). La primera vez descarga el modelo...`;
    try {
        state.handTracker = await createHandTracker(engine);
    } catch (e) {
        console.error("[SHV] MediaPipe", e);
        msg.innerHTML = '<i class="bi bi-exclamation-triangle-fill text-danger me-1"></i>No se pudo cargar MediaPipe Hands: ' +
            `<code>${String(e.message || e).slice(0, 120)}</code>. Revisa la conexión a internet. El reconocimiento facial sigue funcionando.`;
    }
}

/** Línea de diagnóstico bajo el video: motor, manos detectadas y errores. */
function updateHandsDiagnostics() {
    if (!state.handTracker) return;
    const msg = $("handsMsg");
    const n = state.hands.length;
    if (handError) {
        msg.innerHTML = `<i class="bi bi-exclamation-triangle-fill text-danger me-1"></i>Error en MediaPipe (${state.handTracker.delegate}): ` +
            `<code>${handError}</code>. Prueba cambiar el motor de manos a ${state.handTracker.delegate === "GPU" ? "CPU" : "GPU"}.`;
    } else if (!$("toggleHands").checked) {
        msg.innerHTML = '<i class="bi bi-pause-circle me-1"></i>Detección de manos desactivada.';
    } else {
        msg.innerHTML = `<i class="bi bi-check-circle-fill text-success me-1"></i>MediaPipe Hands listo (${state.handTracker.delegate}) · ` +
            (n > 0 ? `<b>${n} mano${n > 1 ? "s" : ""} detectada${n > 1 ? "s" : ""}</b>`
                : "0 manos: muestra la palma completa a 50-80 cm, con buena luz");
    }
}

// ------------------------------------------------------------------ Dibujo sobre el video (~60 fps)
let frames = 0, fpsTime = performance.now();

function renderLoop() {
    const w = canvas.width, h = canvas.height;

    // 1) Video en modo espejo (más natural para quien está frente a la cámara)
    ctx.save();
    ctx.translate(w, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, 0, 0, w, h);
    ctx.restore();

    // 2) Manos: MediaPipe corre en cada frame
    try {
        state.hands = $("toggleHands").checked && state.handTracker ? state.handTracker.detect(video) : [];
        handError = null;
    } catch (e) {
        if (!handError) console.error("[SHV] Error en MediaPipe", e);
        handError = String(e.message || e).slice(0, 120);
        state.hands = [];
    }

    // 3) Rostros y manos encima del video
    if ($("toggleFaces").checked && Date.now() - state.facesAt < 1000) drawFaces(w);
    drawHands(w, h);

    frames++;
    if (performance.now() - fpsTime > 1000) {
        $("fps").textContent = `${frames} fps`;
        updateHandsDiagnostics();
        frames = 0;
        fpsTime = performance.now();
    }
    requestAnimationFrame(renderLoop);
}

const fontPx = () => Math.max(13, Math.round(canvas.width / 38));

/** Etiqueta con fondo redondeado. anchor "bottom" = el texto queda encima de y. */
function drawTag(text, x, y, bg, anchor = "bottom") {
    const size = fontPx(), padX = 8, padY = 5;
    ctx.font = `600 ${size}px "Segoe UI", system-ui, sans-serif`;
    const tw = ctx.measureText(text).width;
    const bw = tw + padX * 2, bh = size + padY * 2;
    const bx = Math.min(Math.max(0, x), canvas.width - bw);
    const by = Math.max(0, Math.min(anchor === "bottom" ? y - bh : y, canvas.height - bh));
    ctx.fillStyle = bg;
    ctx.beginPath();
    ctx.roundRect(bx, by, bw, bh, 6);
    ctx.fill();
    ctx.fillStyle = "#fff";
    ctx.textBaseline = "middle";
    ctx.fillText(text, bx + padX, by + bh / 2 + 1);
}

/** Recuadro con esquinas marcadas (estilo "visor") y relleno translúcido. */
function drawBox(x, y, w, h, color) {
    const len = Math.min(w, h) * 0.22;
    ctx.fillStyle = color + "1f";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = color + "88";
    ctx.lineWidth = 1.5;
    ctx.strokeRect(x, y, w, h);
    ctx.strokeStyle = color;
    ctx.lineWidth = 4;
    ctx.lineCap = "round";
    ctx.beginPath();
    for (const [cx, cy, dx, dy] of [[x, y, 1, 1], [x + w, y, -1, 1], [x, y + h, 1, -1], [x + w, y + h, -1, -1]]) {
        ctx.moveTo(cx, cy + dy * len);
        ctx.lineTo(cx, cy);
        ctx.lineTo(cx + dx * len, cy);
    }
    ctx.stroke();
}

function drawFaces(w) {
    for (const f of state.faces) {
        const x = w - f.x - f.width; // espejo horizontal
        const color = f.known ? COLORS.ok : COLORS.bad;
        drawBox(x, f.y, f.width, f.height, color);
        drawTag(f.known ? `✓ ${f.name} · ${f.confidence}%` : "🔒 Desconocido", x, f.y - 6, color);
    }
}

const fingerOf = (idx) => (idx === 0 ? -1 : Math.floor((idx - 1) / 4));

function drawHands(w, h) {
    const mainIdx = state.handGestures.findIndex((g) => g && g.gesture !== "NONE");

    state.hands.forEach((landmarks, i) => {
        const pts = landmarks.map((p) => ({ x: (1 - p.x) * w, y: p.y * h })); // espejo
        const shape = state.handGestures[i];
        const hasGesture = shape && shape.gesture !== "NONE";
        const color = !hasGesture ? COLORS.idle : state.authorized ? COLORS.ok : COLORS.warn;

        // Recuadro de la mano
        const xs = pts.map((p) => p.x), ys = pts.map((p) => p.y);
        const pad = 22;
        const bx = Math.min(...xs) - pad, by = Math.min(...ys) - pad;
        const bw = Math.max(...xs) - Math.min(...xs) + pad * 2, bh = Math.max(...ys) - Math.min(...ys) + pad * 2;
        drawBox(bx, by, bw, bh, color);

        // Esqueleto: cada dedo de un color, la palma en blanco
        ctx.lineWidth = 3;
        ctx.lineCap = "round";
        for (const [a, b] of HAND_CONNECTIONS) {
            const sameFinger = fingerOf(a) === fingerOf(b) || (a === 0 && b === 1);
            ctx.strokeStyle = sameFinger ? FINGER_COLORS[fingerOf(b)] : COLORS.palm;
            ctx.beginPath();
            ctx.moveTo(pts[a].x, pts[a].y);
            ctx.lineTo(pts[b].x, pts[b].y);
            ctx.stroke();
        }
        // Articulaciones
        pts.forEach((p, idx) => {
            if (FINGER_TIPS.includes(idx)) return;
            ctx.beginPath();
            ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
            ctx.fillStyle = idx === 0 ? "#fff" : FINGER_COLORS[fingerOf(idx)];
            ctx.fill();
        });
        // Puntas: rellenas = dedo extendido, huecas = dedo doblado (la "forma" de la mano)
        FINGER_TIPS.forEach((tip, f) => {
            const extended = shape ? shape.fingers[f] : true;
            ctx.beginPath();
            ctx.arc(pts[tip].x, pts[tip].y, 8, 0, Math.PI * 2);
            ctx.lineWidth = 3;
            ctx.strokeStyle = FINGER_COLORS[f];
            if (extended) {
                ctx.fillStyle = FINGER_COLORS[f];
                ctx.fill();
                ctx.strokeStyle = "#fff";
            }
            ctx.stroke();
        });

        // Etiquetas: gesto arriba, número de dedos abajo
        let title = hasGesture ? `${shape.emoji} ${shape.label}` : "Mano detectada";
        if (hasGesture && !state.authorized) title += "  🔒";
        drawTag(title, bx, by - 6, color);
        if (shape) drawTag(`${shape.extended} dedo${shape.extended === 1 ? "" : "s"} extendido${shape.extended === 1 ? "" : "s"}`, bx, by + bh + 6, "rgba(15,23,42,.85)", "top");

        // Barra de progreso del gesto (mantener ~1 s para ejecutar)
        if (i === mainIdx && state.progress > 0) {
            ctx.fillStyle = "rgba(0,0,0,.5)";
            ctx.fillRect(bx + 4, by + bh - 12, bw - 8, 8);
            ctx.fillStyle = color;
            ctx.fillRect(bx + 4, by + bh - 12, (bw - 8) * state.progress, 8);
        }
    });
}

// ------------------------------------------------------------------ Reconocimiento facial
const frameCanvas = document.createElement("canvas");

/** Captura el frame actual (sin espejo) como JPEG base64. */
function captureFrame() {
    const scale = Math.min(1, FRAME_WIDTH / video.videoWidth);
    frameCanvas.width = Math.round(video.videoWidth * scale);
    frameCanvas.height = Math.round(video.videoHeight * scale);
    frameCanvas.getContext("2d").drawImage(video, 0, 0, frameCanvas.width, frameCanvas.height);
    return { image: frameCanvas.toDataURL("image/jpeg", 0.85), scale };
}

async function recognitionLoop() {
    while (state.running) {
        if ($("toggleFaces").checked && video.videoWidth > 0) {
            try {
                const { image, scale } = captureFrame();
                const r = await post("/api/recognition", { image });
                state.faces = r.faces.map((f) => ({
                    ...f, x: f.x / scale, y: f.y / scale, width: f.width / scale, height: f.height / scale,
                }));
                state.facesAt = Date.now();
                onAuthorization(r.authorized, r.currentUser);
            } catch (e) {
                console.warn("recognition", e);
            }
        } else {
            state.faces = [];
        }
        await sleep(RECOGNITION_INTERVAL_MS);
    }
}

function onAuthorization(authorized, user) {
    if (authorized && user !== state.currentUser) {
        showBanner(`✓ ${user.toUpperCase()}`, "USUARIO AUTORIZADO", true);
    }
    state.authorized = authorized;
    state.currentUser = authorized ? user : null;
}

// ------------------------------------------------------------------ Gestos
async function gestureLoop() {
    while (state.running) {
        if ($("toggleHands").checked && state.handTracker) {
            // Convertimos a píxeles para que las distancias no se deformen
            const w = video.videoWidth, h = video.videoHeight;
            const hands = state.hands.map((lm) => lm.map((p) => ({ x: p.x * w, y: p.y * h, z: p.z })));
            try {
                const r = await post("/api/gesture", { clientId: CLIENT_ID, hands });
                state.handGestures = r.hands;
                state.authorized = r.authorized;
                state.progress = r.event ? r.event.progress : 0;
                state.outcome = r.event ? r.event.outcome : "NONE";
                handleGestureEvent(r.event);
            } catch (e) {
                console.warn("gesture", e);
            }
        } else {
            state.handGestures = [];
            state.progress = 0;
        }
        renderHandsPanel();
        await sleep(GESTURE_INTERVAL_MS);
    }
}

function handleGestureEvent(event) {
    if (!event) return;
    const g = state.gestures.find((x) => x.name === event.gesture);
    const title = g ? `${g.emoji} ${g.label}` : event.gesture;
    if (event.outcome === "EXECUTED") {
        showBanner(title, ACTION_TEXT[event.command] || event.command, true);
        flashStep("stepSpring", "ok");
        flashStep("stepArduino", "ok");
        refreshStatus();
    } else if (event.outcome === "DENIED") {
        showBanner("🔒 ACCESO DENEGADO", "Usuario desconocido · gestos deshabilitados", false);
        flashStep("stepSpring", "bad");
        refreshStatus();
    }
}

/** Panel "Manos detectadas": gesto, dedos extendidos y progreso. */
function renderHandsPanel() {
    const list = $("handsList");
    const shapes = state.handGestures;
    $("handCount").textContent = shapes.length;
    setStep("stepHand", shapes.some((s) => s.gesture !== "NONE") ? "on" : "");

    if (shapes.length === 0) {
        list.replaceChildren(el("div", { class: "text-center text-secondary py-3" },
            el("i", { class: "bi bi-hand-index display-6 d-block mb-2" }),
            el("span", { text: state.running ? "Levanta la mano frente a la cámara" : "Enciende la cámara para empezar" })));
        return;
    }
    const mainIdx = shapes.findIndex((s) => s.gesture !== "NONE");
    list.replaceChildren(...shapes.map((s, i) => {
        const has = s.gesture !== "NONE";
        const chips = el("div", { class: "finger-chips d-flex flex-wrap gap-1 mt-1" },
            ...FINGER_NAMES.map((name, f) => el("span", {
                class: "badge fw-normal " + (s.fingers[f] ? "" : "bg-light border text-secondary"),
                style: s.fingers[f] ? `background:${FINGER_COLORS[f]};color:#111` : "",
                text: name,
            })));
        let status = null;
        if (i === mainIdx) {
            const locked = !state.authorized;
            const done = state.progress >= 1;
            const barClass = locked ? "bg-warning" : done ? "bg-success" : "bg-info progress-bar-striped progress-bar-animated";
            status = el("div", { class: "mt-2" },
                el("div", { class: "progress", role: "progressbar", style: "height: 6px" },
                    el("div", { class: `progress-bar ${barClass}`, style: `width:${Math.round(state.progress * 100)}%` })),
                el("div", {
                    class: "small mt-1 " + (locked ? "text-warning-emphasis" : done ? "text-success" : "text-secondary"),
                    text: locked ? "🔒 Gestos bloqueados: rostro no autorizado"
                        : done ? "✓ Ejecutado — baja la mano para repetir" : "Mantén el gesto…",
                }));
        }
        return el("div", { class: i > 0 ? "border-top pt-3 mt-3" : "" },
            el("div", { class: "d-flex align-items-center gap-3" },
                el("div", { class: "display-6 text-center" + (has ? "" : " opacity-25"), style: "width: 52px", text: has ? s.emoji : "✋" }),
                el("div", { class: "flex-grow-1" },
                    el("div", { class: "fw-semibold", text: has ? s.label : "Gesto no reconocido" }),
                    el("div", { class: "small text-secondary", text: `Mano ${i + 1} · ${s.extended} ${s.extended === 1 ? "dedo extendido" : "dedos extendidos"}` }),
                    chips)),
            status);
    }));
}

// ------------------------------------------------------------------ Banner y pasos del flujo
let bannerTimer;

function showBanner(title, subtitle, ok) {
    $("bannerTitle").textContent = title;
    $("bannerSubtitle").textContent = subtitle;
    $("banner").className = `overlay-banner alert ${ok ? "alert-success" : "alert-danger"} shadow text-center`;
    clearTimeout(bannerTimer);
    bannerTimer = setTimeout(() => $("banner").classList.add("d-none"), BANNER_MS);
}

const flashing = {};
const STEP_CLASS = { "": "text-bg-light border", on: "text-bg-primary", ok: "text-bg-success", bad: "text-bg-danger" };
const stepClass = (cls) => "badge rounded-pill fw-normal " + STEP_CLASS[cls];

function setStep(id, cls) {
    if (flashing[id]) return;
    $(id).className = stepClass(cls);
}

function flashStep(id, cls) {
    clearTimeout(flashing[id]);
    $(id).className = stepClass(cls);
    flashing[id] = setTimeout(() => {
        flashing[id] = null;
        $(id).className = stepClass("");
    }, 1500);
}

// ------------------------------------------------------------------ Focos
const RAYS = Array.from({ length: 7 }, (_, k) => {
    const a = (-160 + k * 23.3) * Math.PI / 180;
    const [r1, r2] = [38, 47];
    return `<line x1="${36 + r1 * Math.cos(a)}" y1="${32 + r1 * Math.sin(a)}" x2="${36 + r2 * Math.cos(a)}" y2="${32 + r2 * Math.sin(a)}"/>`;
}).join("");

const BULB_SVG = `
<svg viewBox="0 0 72 100" aria-hidden="true">
  <g class="rays">${RAYS}</g>
  <path class="glass" d="M36 4C19 4 7 16 7 32c0 11 6 18 11 24 3 4 5 8 5 12v4h26v-4c0-4 2-8 5-12 5-6 11-13 11-24C65 16 53 4 36 4z"/>
  <path class="filament" d="M29 72V58l-5-12 7 5 5-9 5 9 7-5-5 12v14"/>
  <rect class="base" x="23" y="75" width="26" height="6" rx="2"/>
  <rect class="base" x="24" y="83" width="24" height="6" rx="2"/>
  <path class="base" d="M28 91h16l-4 7h-8z"/>
</svg>`;

function renderBulbs() {
    $("bulbs").replaceChildren(...LIGHTS.map((light) => {
        const g = state.gestures.find((x) => x.light === light.id);
        const tile = el("button", { type: "button", class: "bulb-tile btn btn-light border w-100 h-100 d-flex flex-column align-items-center gap-1 py-3",
            id: `bulb-${light.id}`, style: `--bulb:${light.color}`,
            title: `Clic para encender/apagar ${light.name}` });
        tile.innerHTML = BULB_SVG;
        tile.append(
            el("div", { class: "fw-semibold mt-1" }, light.name + " ",
                light.tag ? el("span", { class: "badge text-bg-warning ms-1" }, el("i", { class: "bi bi-lightning-charge-fill" }), " " + light.tag) : null),
            el("div", { class: "small text-secondary", text: g ? `${g.emoji} ${g.howTo}` : "" }),
            el("span", { class: "badge rounded-pill text-bg-secondary", id: `bulbState-${light.id}`, text: "OFF" }));
        tile.onclick = () => manualCommand(`${light.id}_${state.lights[light.id] ? "OFF" : "ON"}`);
        return el("div", { class: "col-6 col-md-3" }, tile);
    }));
}

function updateBulbs(lights) {
    state.lights = lights;
    for (const light of LIGHTS) {
        const on = !!lights[light.id];
        $(`bulb-${light.id}`)?.classList.toggle("on", on);
        const badge = $(`bulbState-${light.id}`);
        if (badge) {
            badge.textContent = on ? "ON" : "OFF";
            badge.className = "badge rounded-pill " + (on ? "text-bg-success" : "text-bg-secondary");
        }
    }
}

// ------------------------------------------------------------------ Dashboard
async function refreshStatus() {
    try {
        renderStatus(await api("/api/status"));
    } catch (e) {
        const badge = $("arduinoBadge");
        badge.className = "badge rounded-pill text-bg-danger";
        badge.innerHTML = '<i class="bi bi-wifi-off"></i> Sin conexión con Spring Boot';
    }
}

const ACCESS = {
    AUTHORIZED: { alert: "alert-success", icon: "bi-shield-check", title: "Usuario autorizado", sub: "Gestos habilitados",
        avatar: "bg-success-subtle text-success border-success" },
    UNKNOWN: { alert: "alert-danger", icon: "bi-shield-x", title: "Usuario no autorizado", sub: "Gestos deshabilitados",
        avatar: "bg-danger-subtle text-danger border-danger" },
    NO_FACE: { alert: "alert-secondary", icon: "bi-hourglass-split", title: "Esperando rostro", sub: "Gestos deshabilitados",
        avatar: "bg-light text-secondary" },
};

function renderStatus(s) {
    // Control de acceso
    const a = ACCESS[s.faceState];
    $("userName").textContent = s.faceState === "AUTHORIZED" ? s.currentUser : s.faceState === "UNKNOWN" ? "Desconocido" : "Nadie";
    const avatar = $("avatar");
    avatar.className = "avatar rounded-circle d-flex align-items-center justify-content-center fs-3 fw-bold border border-3 " + a.avatar;
    if (s.faceState === "AUTHORIZED") avatar.textContent = s.currentUser.charAt(0).toUpperCase();
    else avatar.innerHTML = s.faceState === "UNKNOWN" ? '<i class="bi bi-lock-fill"></i>' : '<i class="bi bi-person"></i>';
    $("authAlert").className = `alert ${a.alert} d-flex align-items-center gap-2 mt-3 mb-0 py-2`;
    $("authAlert").replaceChildren(el("i", { class: `bi ${a.icon} fs-4` }),
        el("div", {}, el("b", { text: a.title }), el("div", { class: "small", text: a.sub })));
    setStep("stepFace", s.faceState === "NO_FACE" ? "" : s.faceState === "AUTHORIZED" ? "ok" : "bad");
    setStep("stepAuth", s.faceState === "AUTHORIZED" ? "ok" : s.faceState === "UNKNOWN" ? "bad" : "");

    // Último comando
    $("commandValue").textContent = s.lastCommand ? `${s.lastCommand.emoji} ${s.lastCommand.label}` : "—";

    // Focos
    const d = s.devices;
    updateBulbs(d.lights);

    // Arduino
    const badge = $("arduinoBadge");
    if (s.arduino.connected) {
        badge.className = "badge rounded-pill text-bg-success";
        badge.innerHTML = `<i class="bi bi-usb-plug-fill"></i> Arduino conectado · ${s.arduino.port}`;
    } else {
        badge.className = "badge rounded-pill text-bg-warning";
        badge.innerHTML = '<i class="bi bi-usb-plug"></i> Arduino: modo simulado';
    }

    // Historial
    const RESULT = { OK: "text-bg-success", SIMULADO: "text-bg-secondary", DENEGADO: "text-bg-danger" };
    const body = $("historyBody");
    if (s.history.length === 0) {
        body.replaceChildren(el("tr", {}, el("td", { colspan: "5", class: "text-center text-secondary py-3", text: "Sin comandos todavía" })));
        return;
    }
    body.replaceChildren(...s.history.map((h) => el("tr", { class: h.result === "DENEGADO" ? "table-danger" : "" },
        el("td", { class: "ps-3 text-secondary small", text: h.time }),
        el("td", { text: h.user }),
        el("td", { text: h.gestureEmoji }),
        el("td", {}, el("code", { text: h.command })),
        el("td", { class: "pe-3" }, el("span", { class: `badge ${RESULT[h.result] || "text-bg-secondary"}`, text: h.result })))));
}

// ------------------------------------------------------------------ Guía de gestos
async function loadGestures() {
    state.gestures = await api("/api/gestures");
    $("gestureGuide").replaceChildren(...state.gestures.map((g) => el("tr", {},
        el("td", { class: "fs-4 text-center ps-3", style: "width: 52px", text: g.emoji }),
        el("td", {}, el("div", { class: "fw-semibold small", text: g.label }), el("div", { class: "small text-secondary", text: g.howTo })),
        el("td", { class: "small pe-3 text-end", text: g.action }))));
    renderBulbs();
    refreshStatus();
}

// ------------------------------------------------------------------ Registro de usuarios
async function registerUser() {
    const name = $("nameInput").value.trim();
    const msg = (t, cls = "") => {
        $("registerMsg").textContent = t;
        $("registerMsg").className = "form-text " + cls;
    };
    if (!name) return msg("Escribe un nombre primero.", "text-warning");
    if (!state.running) return msg("Primero enciende la cámara.", "text-warning");

    const btn = $("registerBtn");
    btn.disabled = true;
    try {
        for (let i = 3; i > 0; i--) {
            msg(`👀 Mira a la cámara... ${i}`, "text-info");
            await sleep(700);
        }
        const images = [];
        for (let i = 1; i <= 5; i++) {
            images.push(captureFrame().image);
            msg(`📸 Captura ${i}/5 (mueve un poco la cabeza)`, "text-info");
            await sleep(300);
        }
        msg("⏳ Calculando embeddings faciales...", "text-info");
        const u = await post("/api/users/register", { name, images });
        msg(`✅ ${u.name} registrado con ${u.samples} muestras. Sal de la cámara y vuelve a entrar.`, "text-success");
        $("nameInput").value = "";
        loadUsers();
    } catch (e) {
        msg("❌ " + e.message, "text-danger");
    } finally {
        btn.disabled = false;
    }
}

async function loadUsers() {
    const users = await api("/api/users");
    const list = $("userList");
    if (users.length === 0) {
        list.replaceChildren(el("li", { class: "list-group-item text-secondary px-0", text: "Todavía no hay usuarios" }));
        return;
    }
    list.replaceChildren(...users.map((u) => {
        const del = el("button", { class: "btn btn-sm btn-outline-danger", title: `Borrar a ${u.name}` }, el("i", { class: "bi bi-trash" }));
        del.onclick = async () => {
            if (!confirm(`¿Borrar a ${u.name}?`)) return;
            await api(`/api/users/${encodeURIComponent(u.name)}`, { method: "DELETE" });
            loadUsers();
        };
        return el("li", { class: "list-group-item d-flex align-items-center gap-2 px-0" },
            el("span", { class: "rounded-circle bg-primary-subtle text-primary fw-bold d-inline-flex align-items-center justify-content-center", style: "width: 32px; height: 32px", text: u.name.charAt(0).toUpperCase() }),
            el("span", { class: "flex-grow-1", text: u.name }),
            el("span", { class: "badge text-bg-secondary", text: `${u.samples} muestras` }),
            del);
    }));
}

// ------------------------------------------------------------------ Arduino
async function loadPorts() {
    const [ports, status] = await Promise.all([api("/api/serial/ports"), api("/api/status")]);
    const select = $("portSelect");
    select.replaceChildren(new Option("Sin puerto (modo simulado)", ""));
    for (const p of ports) {
        const opt = new Option(p, p);
        opt.selected = !!status.arduino.port && p.startsWith(status.arduino.port + " ");
        select.append(opt);
    }
}

async function connectPort() {
    const msg = $("arduinoMsg");
    msg.textContent = "⏳ Conectando (el Arduino se reinicia ~2 s)...";
    try {
        const r = await post("/api/serial/connect", { port: $("portSelect").value });
        msg.textContent = r.connected ? `✅ Conectado a ${r.port}` : "Modo simulado";
    } catch (e) {
        msg.textContent = "❌ " + e.message;
    }
    refreshStatus();
}

async function manualCommand(cmd) {
    try {
        const s = await post(`/api/commands/${cmd}`);
        renderStatus(s);
        $("arduinoMsg").textContent = s.arduino.connected
            ? `➡️ ${cmd} enviado a ${s.arduino.port}`
            : `➡️ ${cmd} (simulado: no hay Arduino conectado)`;
    } catch (e) {
        $("arduinoMsg").textContent = "❌ " + e.message;
    }
}

// ------------------------------------------------------------------ Inicio
$("startBtn").onclick = startCamera;
$("cameraSelect").onchange = (e) => switchCamera(e.target.value);
$("handEngine").value = savedEngine();
$("handEngine").onchange = () => state.running && loadHandTracker();
navigator.mediaDevices?.addEventListener("devicechange", listCameras);
$("registerBtn").onclick = registerUser;
$("nameInput").addEventListener("keydown", (e) => e.key === "Enter" && registerUser());
$("refreshPortsBtn").onclick = loadPorts;
$("connectBtn").onclick = connectPort;
document.querySelectorAll("[data-cmd]").forEach((b) => (b.onclick = () => manualCommand(b.dataset.cmd)));

renderHandsPanel();
listCameras();
loadGestures();
loadUsers();
loadPorts();
setInterval(refreshStatus, STATUS_INTERVAL_MS);
