// =====================================================================
//  Detección de manos con MediaPipe Hands (Tasks Vision) en el navegador.
//  Devuelve 21 puntos (landmarks) por mano, con x,y normalizados (0..1).
//  La clasificación del gesto la hace Spring Boot (GestureClassifier.java).
// =====================================================================

const MEDIAPIPE_VERSION = "0.10.14";
const MEDIAPIPE_URL = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_VERSION}`;
const HAND_MODEL_URL =
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";

/** Pares de puntos que se unen con líneas para dibujar el "esqueleto" de la mano. */
export const HAND_CONNECTIONS = [
    [0, 1], [1, 2], [2, 3], [3, 4],          // pulgar
    [0, 5], [5, 6], [6, 7], [7, 8],          // índice
    [5, 9], [9, 10], [10, 11], [11, 12],     // medio
    [9, 13], [13, 14], [14, 15], [15, 16],   // anular
    [13, 17], [17, 18], [18, 19], [19, 20],  // meñique
    [0, 17],                                 // base de la palma
];

let visionModule = null;
let fileset = null;

/**
 * Crea el detector de manos.
 * @param delegate "CPU" (más compatible) o "GPU" (más rápido, pero en algunos
 *                 equipos con Windows no detecta nada sin dar error).
 */
export async function createHandTracker(delegate = "CPU") {
    visionModule ??= await import(`${MEDIAPIPE_URL}/vision_bundle.mjs`);
    fileset ??= await visionModule.FilesetResolver.forVisionTasks(`${MEDIAPIPE_URL}/wasm`);

    const create = (d) => visionModule.HandLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: HAND_MODEL_URL, delegate: d },
        runningMode: "VIDEO",
        numHands: 2,
        minHandDetectionConfidence: 0.5,
        minHandPresenceConfidence: 0.5,
        minTrackingConfidence: 0.5,
    });

    let landmarker;
    try {
        landmarker = await create(delegate);
    } catch (e) {
        if (delegate === "CPU") throw e;
        console.warn("[SHV] MediaPipe GPU no disponible, usando CPU", e);
        delegate = "CPU";
        landmarker = await create("CPU");
    }
    console.info(`[SHV] MediaPipe Hands listo (${delegate})`);

    let lastVideoTime = -1;
    let lastTimestamp = 0;
    let lastHands = [];
    return {
        delegate,
        /** Devuelve las manos del frame actual: [[{x,y,z} x21], ...] */
        detect(video) {
            if (video.readyState < 2 || !video.videoWidth) return [];
            if (video.currentTime !== lastVideoTime) { // solo procesar frames nuevos
                lastVideoTime = video.currentTime;
                // MediaPipe exige marcas de tiempo estrictamente crecientes
                lastTimestamp = Math.max(lastTimestamp + 1, Math.round(performance.now()));
                const result = landmarker.detectForVideo(video, lastTimestamp);
                lastHands = result.landmarks || [];
            }
            return lastHands;
        },
        close() {
            try { landmarker.close(); } catch { /* ya cerrado */ }
        },
    };
}
