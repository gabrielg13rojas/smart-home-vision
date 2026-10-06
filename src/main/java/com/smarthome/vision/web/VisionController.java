package com.smarthome.vision.web;

import com.smarthome.vision.home.CommandService;
import com.smarthome.vision.home.HomeState;
import com.smarthome.vision.vision.FaceResult;
import com.smarthome.vision.vision.FaceService;
import com.smarthome.vision.vision.Gesture;
import com.smarthome.vision.vision.GestureClassifier;
import com.smarthome.vision.vision.HandShape;
import com.smarthome.vision.vision.Landmark;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints que el navegador llama continuamente mientras la camara esta activa:
 *   POST /api/recognition  (cada ~300 ms) -> rostros reconocidos
 *   POST /api/gesture      (cada ~120 ms) -> gesto de la mano + comando
 */
@RestController
@RequestMapping("/api")
public class VisionController {

    public record RecognitionRequest(String image) { }

    public record RecognitionResponse(List<FaceResult> faces, boolean authorized, String currentUser,
                                      HomeState.FaceState faceState) { }

    /**
     * clientId = identificador de la pestana del navegador (cada pestana lleva su propio conteo);
     * hands = lista de manos; cada mano = 21 puntos (MediaPipe) en pixeles.
     */
    public record GestureRequest(String clientId, List<List<Landmark>> hands) { }

    /** fingers = [pulgar, indice, medio, anular, menique] (true = extendido) */
    public record HandGesture(String gesture, String emoji, String label, List<Boolean> fingers, int extended) { }

    public record GestureResponse(List<HandGesture> hands, boolean authorized, CommandService.GestureEvent event) { }

    private final FaceService faceService;
    private final GestureClassifier classifier;
    private final CommandService commandService;
    private final HomeState state;

    public VisionController(FaceService faceService, GestureClassifier classifier,
                            CommandService commandService, HomeState state) {
        this.faceService = faceService;
        this.classifier = classifier;
        this.commandService = commandService;
        this.state = state;
    }

    @PostMapping("/recognition")
    public RecognitionResponse recognition(@RequestBody RecognitionRequest request) {
        if (request.image() == null || request.image().isBlank()) {
            throw new IllegalArgumentException("Falta la imagen");
        }
        List<FaceResult> faces = faceService.recognize(request.image());
        state.updateFaces(faces);
        return new RecognitionResponse(faces, state.isAuthorized(), state.currentUser(), state.faceState());
    }

    @PostMapping("/gesture")
    public GestureResponse gesture(@RequestBody GestureRequest request) {
        List<List<Landmark>> hands = request.hands() == null ? List.of() : request.hands();
        List<HandShape> shapes = hands.stream().map(classifier::classify).toList();

        // Usamos el primer gesto valido de las manos visibles
        Gesture main = shapes.stream().map(HandShape::gesture)
                .filter(g -> g != Gesture.NONE).findFirst().orElse(Gesture.NONE);
        CommandService.GestureEvent event = commandService.onGesture(request.clientId(), main);

        List<HandGesture> perHand = shapes.stream()
                .map(h -> new HandGesture(h.gesture().name(), h.gesture().emoji(), h.gesture().label(),
                        h.fingers(), h.extendedCount()))
                .toList();
        return new GestureResponse(perHand, state.isAuthorized(), event);
    }
}
