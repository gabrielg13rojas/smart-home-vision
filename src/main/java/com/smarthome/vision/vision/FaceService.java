package com.smarthome.vision.vision;

import com.smarthome.vision.user.RegisteredUser;
import com.smarthome.vision.user.UserRepository;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Deteccion y reconocimiento facial con OpenCV.
 *
 *  1. YuNet encuentra los rostros de la imagen.
 *  2. SFace alinea cada rostro y lo convierte en un embedding de 128 numeros.
 *  3. Comparamos ese embedding con los de los usuarios registrados
 *     usando similitud coseno. Si supera el umbral -> usuario reconocido.
 */
@Service
public class FaceService {

    private static final Logger log = LoggerFactory.getLogger(FaceService.class);
    public static final String UNKNOWN = "Desconocido";

    private final FaceDetectorYN detector;
    private final FaceRecognizerSF recognizer;
    private final UserRepository users;
    private final double matchThreshold;

    public FaceService(ModelDownloader models, UserRepository users,
                       @Value("${app.face.match-threshold}") double matchThreshold,
                       @Value("${app.face.detection-score}") float detectionScore) {
        this.users = users;
        this.matchThreshold = matchThreshold;
        this.detector = FaceDetectorYN.create(models.yunetModel().toString(), "",
                new Size(320, 320), detectionScore, 0.3f, 5000);
        this.recognizer = FaceRecognizerSF.create(models.sfaceModel().toString(), "");
        log.info("OpenCV listo: YuNet (deteccion) + SFace (reconocimiento), umbral={}", matchThreshold);
    }

    /** Detecta y reconoce todos los rostros de una imagen JPEG/PNG en base64. */
    public List<FaceResult> recognize(String base64Image) {
        Mat image = decode(base64Image);
        List<FaceResult> results = new ArrayList<>();
        List<RegisteredUser> registered = users.findAll();
        synchronized (this) { // los objetos de OpenCV no son thread-safe
            Mat faces = detect(image);
            for (int i = 0; i < faces.rows(); i++) {
                float[] box = row(faces, i);
                float[] embedding = embedding(image, faces.row(i));
                results.add(match(box, embedding, registered));
            }
            faces.release();
        }
        image.release();
        return results;
    }

    /** Calcula embeddings del rostro mas grande de cada imagen (para registrar un usuario). */
    public List<float[]> embeddingsForRegistration(List<String> base64Images) {
        List<float[]> embeddings = new ArrayList<>();
        for (String base64 : base64Images) {
            Mat image = decode(base64);
            synchronized (this) {
                Mat faces = detect(image);
                int largest = -1;
                float largestArea = 0;
                for (int i = 0; i < faces.rows(); i++) {
                    float[] r = row(faces, i);
                    if (r[2] * r[3] > largestArea) {
                        largestArea = r[2] * r[3];
                        largest = i;
                    }
                }
                if (largest >= 0) {
                    embeddings.add(embedding(image, faces.row(largest)));
                }
                faces.release();
            }
            image.release();
        }
        return embeddings;
    }

    // ---------------------------------------------------------------- helpers

    private Mat detect(Mat image) {
        detector.setInputSize(new Size(image.cols(), image.rows()));
        Mat faces = new Mat();
        detector.detect(image, faces); // cada fila: x, y, w, h, 5 puntos (10 valores), score
        return faces;
    }

    private float[] embedding(Mat image, Mat faceRow) {
        Mat aligned = new Mat();
        Mat feature = new Mat();
        recognizer.alignCrop(image, faceRow, aligned);
        recognizer.feature(aligned, feature);
        float[] vector = new float[(int) feature.total()];
        feature.get(0, 0, vector);
        aligned.release();
        feature.release();
        return normalize(vector);
    }

    private FaceResult match(float[] box, float[] embedding, List<RegisteredUser> registered) {
        String bestName = UNKNOWN;
        double bestSimilarity = -1;
        for (RegisteredUser user : registered) {
            for (float[] sample : user.embeddings()) {
                double similarity = cosine(embedding, sample);
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestName = user.name();
                }
            }
        }
        boolean known = bestSimilarity >= matchThreshold;
        return new FaceResult(Math.round(box[0]), Math.round(box[1]), Math.round(box[2]), Math.round(box[3]),
                known ? bestName : UNKNOWN, known, bestSimilarity, confidence(bestSimilarity));
    }

    /**
     * Indicador de confianza para la pantalla (no es una metrica cientifica):
     * el umbral equivale a 60 % y una similitud de 0.7 o mas equivale a 100 %.
     */
    private int confidence(double similarity) {
        double pct = 60 + (similarity - matchThreshold) / (0.7 - matchThreshold) * 40;
        return (int) Math.round(Math.max(0, Math.min(100, pct)));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
        }
        return dot; // ambos vectores ya estan normalizados
    }

    private static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    private static float[] row(Mat faces, int i) {
        float[] r = new float[faces.cols()];
        faces.get(i, 0, r);
        return r;
    }

    private static Mat decode(String base64Image) {
        String data = base64Image.contains(",") ? base64Image.substring(base64Image.indexOf(',') + 1) : base64Image;
        byte[] bytes = Base64.getDecoder().decode(data);
        Mat image = Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_COLOR);
        if (image.empty()) {
            throw new IllegalArgumentException("La imagen recibida no es valida");
        }
        return image;
    }
}
