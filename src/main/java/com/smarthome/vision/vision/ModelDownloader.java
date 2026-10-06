package com.smarthome.vision.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Descarga (una sola vez) los modelos ONNX de OpenCV a la carpeta data/models.
 *
 *  - YuNet: detecta rostros (recuadro + 5 puntos: ojos, nariz, boca).
 *  - SFace: convierte un rostro en un vector de 128 numeros (embedding).
 */
@Component
public class ModelDownloader {

    private static final Logger log = LoggerFactory.getLogger(ModelDownloader.class);

    private final Path modelsDir;
    private final String yunetUrl;
    private final String sfaceUrl;

    public ModelDownloader(@Value("${app.data-dir}") String dataDir,
                           @Value("${app.models.yunet-url}") String yunetUrl,
                           @Value("${app.models.sface-url}") String sfaceUrl) {
        this.modelsDir = Path.of(dataDir, "models");
        this.yunetUrl = yunetUrl;
        this.sfaceUrl = sfaceUrl;
    }

    public Path yunetModel() {
        return ensure("face_detection_yunet_2023mar.onnx", yunetUrl);
    }

    public Path sfaceModel() {
        return ensure("face_recognition_sface_2021dec.onnx", sfaceUrl);
    }

    private Path ensure(String fileName, String url) {
        Path target = modelsDir.resolve(fileName);
        if (Files.exists(target)) {
            return target;
        }
        log.info("Descargando modelo {} desde {} ...", fileName, url);
        try {
            Files.createDirectories(modelsDir);
            Path tmp = modelsDir.resolve(fileName + ".part");
            try (InputStream in = URI.create(url).toURL().openStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Modelo guardado en {}", target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo descargar " + fileName
                    + ". Descargalo manualmente desde " + url + " y copialo en " + modelsDir.toAbsolutePath(), e);
        }
    }
}
