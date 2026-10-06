package com.smarthome.vision;

import com.smarthome.vision.arduino.ArduinoService;
import com.smarthome.vision.home.Command;
import com.smarthome.vision.home.CommandService;
import com.smarthome.vision.home.CommandService.Outcome;
import com.smarthome.vision.home.HomeState;
import com.smarthome.vision.home.Light;
import com.smarthome.vision.vision.FaceResult;
import com.smarthome.vision.vision.Gesture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica la regla central: solo un usuario reconocido puede controlar la casa. */
class AuthorizationRuleTest {

    private final HomeState state = new HomeState(2000);
    private final ArduinoService arduino = new ArduinoService(false, "auto", 9600); // modo simulado
    private final CommandService commands = new CommandService(state, arduino, 0, 0, 0);

    @Test
    void unknownPersonCannotTurnOnLights() {
        state.updateFaces(List.of(face("Desconocido", false)));

        var event = commands.onGesture(Gesture.OPEN_PALM);

        assertEquals(Outcome.DENIED, event.outcome());
        assertFalse(state.lights().containsValue(true));
    }

    @Test
    void recognizedUserTurnsOnAllLightsAndActivatesHomeMode() {
        state.updateFaces(List.of(face("Gabriel", true)));

        var event = commands.onGesture(Gesture.OPEN_PALM);
        assertEquals(Outcome.EXECUTED, event.outcome());
        assertEquals(Command.LIGHTS_ON, event.command());
        assertEquals("Gabriel", event.user());
        assertFalse(state.lights().containsValue(false));

        commands.onGesture(Gesture.THUMBS_UP); // modo casa: 220V + azul
        assertTrue(state.isOn(Light.MAIN));
        assertFalse(state.isOn(Light.RED));
        assertFalse(state.isOn(Light.GREEN));
        assertTrue(state.isOn(Light.BLUE));
    }

    @Test
    void eachFingerCountTogglesItsOwnLight() {
        state.updateFaces(List.of(face("Gabriel", true)));

        assertEquals(Command.MAIN_ON, commands.onGesture(Gesture.ONE_FINGER).command());
        assertEquals(Command.RED_ON, commands.onGesture(Gesture.TWO_FINGERS).command());
        assertEquals(Command.GREEN_ON, commands.onGesture(Gesture.THREE_FINGERS).command());
        assertEquals(Command.BLUE_ON, commands.onGesture(Gesture.FOUR_FINGERS).command());
        assertFalse(state.lights().containsValue(false));

        // El mismo gesto otra vez (despues de bajar la mano) apaga el foco
        commands.onGesture(Gesture.NONE);
        assertEquals(Command.RED_OFF, commands.onGesture(Gesture.TWO_FINGERS).command());
        assertFalse(state.isOn(Light.RED));
        assertTrue(state.isOn(Light.MAIN));
    }

    @Test
    void sameGestureRunsOnlyOnceUntilItChanges() {
        state.updateFaces(List.of(face("Gabriel", true)));

        assertEquals(Outcome.EXECUTED, commands.onGesture(Gesture.ONE_FINGER).outcome());
        assertEquals(Outcome.WAITING, commands.onGesture(Gesture.ONE_FINGER).outcome());
        assertTrue(state.isOn(Light.MAIN));
        commands.onGesture(Gesture.NONE);
        assertEquals(Outcome.EXECUTED, commands.onGesture(Gesture.ONE_FINGER).outcome());
        assertFalse(state.isOn(Light.MAIN));
    }

    @Test
    void lightStaysOnWhenHandLeavesAndBriefDetectionDropoutsAreIgnored() throws InterruptedException {
        CommandService realistic = new CommandService(state, arduino, 0, 150, 0);
        state.updateFaces(List.of(face("Gabriel", true)));

        assertEquals(Outcome.EXECUTED, realistic.onGesture(Gesture.ONE_FINGER).outcome());
        assertTrue(state.isOn(Light.MAIN));

        // MediaPipe pierde la mano un instante y la recupera: NO debe apagarse
        realistic.onGesture(Gesture.NONE);
        assertEquals(Outcome.WAITING, realistic.onGesture(Gesture.ONE_FINGER).outcome());
        assertTrue(state.isOn(Light.MAIN));

        // La mano sale del video: el foco sigue encendido
        realistic.onGesture(Gesture.NONE);
        Thread.sleep(200);
        realistic.onGesture(Gesture.NONE);
        assertTrue(state.isOn(Light.MAIN));

        // Vuelve el MISMO gesto: ahora si se apaga
        assertEquals(Command.MAIN_OFF, realistic.onGesture(Gesture.ONE_FINGER).command());
        assertFalse(state.isOn(Light.MAIN));
    }

    @Test
    void anotherBrowserTabWithoutHandsDoesNotInterfere() throws InterruptedException {
        CommandService withHold = new CommandService(state, arduino, 100, 0, 0);
        state.updateFaces(List.of(face("Gabriel", true)));

        withHold.onGesture("pestana-1", Gesture.TWO_FINGERS);
        withHold.onGesture("pestana-2", Gesture.NONE); // otra pestana abierta, sin mano
        Thread.sleep(130);
        withHold.onGesture("pestana-2", Gesture.NONE);

        assertEquals(Outcome.EXECUTED, withHold.onGesture("pestana-1", Gesture.TWO_FINGERS).outcome());
        assertTrue(state.isOn(Light.RED));
    }

    @Test
    void unknownFaceRevokesAuthorizationImmediately() {
        state.updateFaces(List.of(face("Gabriel", true)));
        assertTrue(state.isAuthorized());

        state.updateFaces(List.of(face("Desconocido", false)));
        assertFalse(state.isAuthorized());
        assertEquals(HomeState.FaceState.UNKNOWN, state.faceState());
    }

    private static FaceResult face(String name, boolean known) {
        return new FaceResult(0, 0, 100, 100, name, known, known ? 0.8 : 0.1, known ? 100 : 20);
    }
}
