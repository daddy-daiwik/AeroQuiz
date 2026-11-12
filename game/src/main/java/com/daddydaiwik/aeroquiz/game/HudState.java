package com.daddydaiwik.aeroquiz.game;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * AppState that owns all 2-D HUD rendering and input routing.
 *
 * <p>The individual screens ({@link MenuScreen}, {@link EditorScreen}, {@link FlightHudScreen},
 * {@link GameOverScreen}) are built on top of {@link UiKit}. This class decides which one is visible for the
 * current game phase, scales the whole HUD to the window, forwards mouse/touch/keyboard input, and runs the
 * full-screen damage / success flashes.
 */
public class HudState extends AbstractAppState {

    /** HUD layouts are authored for a 1280x720 canvas and scaled uniformly to fit the window. */
    private static final float DESIGN_W = 1280f;
    private static final float DESIGN_H = 720f;

    private static final float RED_FLASH_DURATION   = 0.45f;
    private static final float GREEN_FLASH_DURATION = 0.35f;

    private static final String[] CALLSIGN_PRESETS = {
        "Maheep", "Ace", "Maverick", "Viper", "Phoenix", "Ghost", "Hawk", "Falcon", "SkyLord", "Eagle"
    };
    private static final QuizSettings.AircraftType[] AIRCRAFT = QuizSettings.AircraftType.values();
    private static final int MAX_CALLSIGN_LENGTH = 16;

    private AeroQuiz app;
    private GameState gameState;
    private UiKit kit;

    // Layout
    private Node hudNode;
    private float screenW, screenH;
    private float uiScale = 1f, uiW = DESIGN_W, uiH = DESIGN_H;

    // Screens
    private MenuScreen menuScreen;
    private EditorScreen editorScreen;
    private FlightHudScreen flightScreen;
    private GameOverScreen gameOverScreen;
    private EditorModel editorModel;
    private boolean editorOpen = false;

    // Pilot setup
    private final StringBuilder nameBuffer = new StringBuilder("Maheep");
    private int callsignIndex = 0;
    private int aircraftIndex = 0;

    // Input
    private float inputCooldown = 0.5f;
    private float pointerX = -1f, pointerY = -1f;
    private UiKit.Button hovered;
    private float caretTimer = 0f;
    private boolean caretOn = true;

    // Flashes
    private Geometry flashQuad;
    private float flashTimer = 0f;
    private float flashDuration = 0f;
    private float flashPeak = 0f;
    private int flashHex = 0;

    public HudState() {}

    public HudState(AeroQuiz app) {
        this.app = app;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app = (AeroQuiz) application;
        this.gameState = stateManager.getState(GameState.class);

        boolean gamma = app.getContext().getSettings().isGammaCorrection();
        kit = new UiKit(app.getAssetManager(), gamma);
        editorModel = new EditorModel(this, app);

        hudNode = new Node("HUD");
        app.getGuiNode().attachChild(hudNode);

        QuizSettings s = QuizLoader.getSettings();
        if (s != null && s.getAircraft() != null) {
            for (int i = 0; i < AIRCRAFT.length; i++) {
                if (AIRCRAFT[i] == s.getAircraft()) aircraftIndex = i;
            }
        }

        rebuild();
        app.getInputManager().addRawInputListener(rawInputListener);
    }

    @Override
    public void cleanup() {
        if (app != null && app.getInputManager() != null) {
            app.getInputManager().removeRawInputListener(rawInputListener);
        }
        if (hudNode != null) hudNode.removeFromParent();
        super.cleanup();
    }

    /** (Re)creates every screen for the current window size. */
    private void rebuild() {
        screenW = app.getCamera().getWidth();
        screenH = app.getCamera().getHeight();
        uiScale = Math.max(0.35f, Math.min(screenW / DESIGN_W, screenH / DESIGN_H));
        uiW = screenW / uiScale;
        uiH = screenH / uiScale;

        hudNode.detachAllChildren();
        hudNode.setLocalScale(uiScale);

        menuScreen     = new MenuScreen(this, kit, uiW, uiH);
        editorScreen   = new EditorScreen(this, kit, uiW, uiH, editorModel);
        flightScreen   = new FlightHudScreen(this, kit, uiW, uiH);
        gameOverScreen = new GameOverScreen(this, kit, uiW, uiH);
        hudNode.attachChild(menuScreen.root);
        hudNode.attachChild(editorScreen.root);
        hudNode.attachChild(flightScreen.root);
        hudNode.attachChild(gameOverScreen.root);

        ColorRGBA white = ColorRGBA.White;
        flashQuad = kit.rect("Flash", uiW, uiH, white, white);
        flashQuad.setLocalTranslation(0f, 0f, UiKit.Z_POPUP + 4f);
        flashQuad.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(flashQuad);
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled() || gameState == null) return;

        if (app.getCamera().getWidth() != (int) screenW || app.getCamera().getHeight() != (int) screenH) {
            rebuild();
        }
        if (inputCooldown > 0f) inputCooldown -= tpf;

        caretTimer += tpf;
        if (caretTimer >= 0.5f) {
            caretTimer = 0f;
            caretOn = !caretOn;
        }

        GameState.Phase phase = gameState.getPhase();
        boolean inMenu = phase == GameState.Phase.NAME_INPUT;
        boolean over = gameState.isGameOver();
        boolean crashing = gameState.isCrashing();

        menuScreen.setVisible(inMenu && !editorOpen);
        editorScreen.setVisible(inMenu && editorOpen);
        flightScreen.setVisible(!inMenu && !over && !crashing);
        gameOverScreen.setVisible(over);

        UiScreen active = activeScreen();
        if (active != null) {
            active.update(tpf);
            hovered = pointerX < 0f ? null : active.buttonAt(pointerX / uiScale, pointerY / uiScale);
            active.tickButtons(tpf, hovered);
        }
        updateFlash(tpf);
    }

    private UiScreen activeScreen() {
        if (menuScreen.isVisible()) return menuScreen;
        if (editorScreen.isVisible()) return editorScreen;
        if (flightScreen.isVisible()) return flightScreen;
        if (gameOverScreen.isVisible()) return gameOverScreen;
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Flashes
    // ═════════════════════════════════════════════════════════════════════════

    /** Red full-screen flash used when the aircraft takes damage. Called by {@link PlaneController}. */
    public void triggerRedFlash() {
        startFlash(0xFF2A1F, 0.40f, RED_FLASH_DURATION);
    }

    /** Subtle green pulse on a correct answer. */
    void flashGreen() {
        startFlash(UiKit.GREEN, 0.16f, GREEN_FLASH_DURATION);
    }

    private void startFlash(int hex, float peak, float duration) {
        flashHex = hex;
        flashPeak = peak;
        flashDuration = duration;
        flashTimer = 0f;
        if (flashQuad != null) flashQuad.setCullHint(Spatial.CullHint.Inherit);
    }

    private void updateFlash(float tpf) {
        if (flashQuad == null || flashQuad.getCullHint() == Spatial.CullHint.Always) return;
        flashTimer += tpf;
        float t = flashTimer / flashDuration;
        if (t >= 1f) {
            flashQuad.setCullHint(Spatial.CullHint.Always);
            return;
        }
        float alpha = flashPeak * (1f - t) * (1f - t);
        flashQuad.getMaterial().setColor("Color", kit.c(flashHex, alpha));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Model access for the screens
    // ═════════════════════════════════════════════════════════════════════════

    AeroQuiz app() { return app; }
    GameState gameState() { return gameState; }
    boolean caretVisible() { return caretOn; }
    String callsign() { return nameBuffer.toString(); }
    QuizSettings.AircraftType aircraft() { return AIRCRAFT[aircraftIndex]; }

    void cycleCallsign(int dir) {
        callsignIndex = (callsignIndex + dir + CALLSIGN_PRESETS.length) % CALLSIGN_PRESETS.length;
        nameBuffer.setLength(0);
        nameBuffer.append(CALLSIGN_PRESETS[callsignIndex]);
    }

    void clearCallsign() {
        nameBuffer.setLength(0);
    }

    void cycleAircraft(int dir) {
        selectAircraft(AIRCRAFT[(aircraftIndex + dir + AIRCRAFT.length) % AIRCRAFT.length]);
    }

    void selectAircraft(QuizSettings.AircraftType type) {
        for (int i = 0; i < AIRCRAFT.length; i++) {
            if (AIRCRAFT[i] == type) aircraftIndex = i;
        }
        QuizSettings settings = QuizLoader.getSettings();
        if (settings != null) {
            settings.setAircraft(type);
            QuizLoader.saveQuestionsAndSettings(null, settings);
        }
        PlaneController pc = app.getStateManager().getState(PlaneController.class);
        if (pc != null) pc.switchAircraft(type);
    }

    void launchFlight() {
        String name = nameBuffer.toString().trim();
        if (name.isEmpty()) name = "Pilot";
        inputCooldown = 0.5f;
        gameState.startQuizWithPlayerName(name);
    }

    void openEditor() {
        inputCooldown = 0.3f;
        editorModel.open();
        editorOpen = true;
    }

    void closeEditor() {
        inputCooldown = 0.3f;
        editorModel.commit();
        editorOpen = false;
    }

    void retryFlight() {
        inputCooldown = 0.5f;
        gameState.restartCurrentGame();
    }

    void backToTerminal() {
        inputCooldown = 0.5f;
        editorOpen = false;
        gameState.resetToRegistration();
    }

    /** Answers the current question by option index (answer buttons along the bottom of the flight HUD). */
    void answerOption(int index) {
        inputCooldown = 0.4f;
        gameState.handleAnswer(index);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Input
    // ═════════════════════════════════════════════════════════════════════════

    private void handleClick(float x, float y) {
        if (gameState == null || inputCooldown > 0f) return;
        pointerX = x;
        pointerY = y;

        UiScreen active = activeScreen();
        if (active != null) {
            UiKit.Button b = active.buttonAt(x / uiScale, y / uiScale);
            if (b != null) {
                b.click();
                return;
            }
        }

        // In flight: clicking a gate in the sky answers with that gate's colour.
        if (flightScreen.isVisible() && gameState.getPhase() == GameState.Phase.PLAYING) {
            ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
            if (spawner != null) {
                String colorKey = spawner.pickObstacleColorAt(x, y);
                if (colorKey != null) {
                    inputCooldown = 0.4f;
                    gameState.handleAnswerColor(colorKey);
                }
            }
        }
    }

    private void handleKey(KeyInputEvent evt) {
        if (!evt.isPressed() || gameState == null || inputCooldown > 0f) return;
        int code = evt.getKeyCode();
        char c = evt.getKeyChar();

        if (gameState.getPhase() == GameState.Phase.NAME_INPUT) {
            if (editorOpen) handleEditorKey(code, c);
            else handleMenuKey(code, c);
        } else if (gameState.isGameOver() || gameState.isCrashing()) {
            if (code == KeyInput.KEY_RETURN || code == KeyInput.KEY_NUMPADENTER || code == KeyInput.KEY_SPACE) {
                backToTerminal();
            } else if (code == KeyInput.KEY_R) {
                retryFlight();
            }
        }
    }

    private void handleMenuKey(int code, char c) {
        if (code == KeyInput.KEY_RETURN || code == KeyInput.KEY_NUMPADENTER) {
            launchFlight();
        } else if (code == KeyInput.KEY_TAB || code == KeyInput.KEY_RIGHT) {
            cycleAircraft(1);
        } else if (code == KeyInput.KEY_LEFT) {
            cycleAircraft(-1);
        } else if (code == KeyInput.KEY_DOWN) {
            cycleCallsign(1);
        } else if (code == KeyInput.KEY_UP) {
            cycleCallsign(-1);
        } else if (code == KeyInput.KEY_BACK) {
            if (nameBuffer.length() > 0) nameBuffer.deleteCharAt(nameBuffer.length() - 1);
        } else if (nameBuffer.length() < MAX_CALLSIGN_LENGTH
                && (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ')) {
            nameBuffer.append(c);
        }
    }

    private void handleEditorKey(int code, char c) {
        if (editorModel.isEditing()) {
            if (code == KeyInput.KEY_RETURN || code == KeyInput.KEY_NUMPADENTER) {
                editorModel.commit();
            } else if (code == KeyInput.KEY_ESCAPE) {
                editorModel.cancel();
            } else if (code == KeyInput.KEY_BACK) {
                editorModel.backspace();
            } else if (code == KeyInput.KEY_TAB) {
                editorModel.cycleNextField();
            } else {
                editorModel.typeChar(c);
            }
        } else if (code == KeyInput.KEY_LEFT) {
            editorModel.stepQuestion(-1);
        } else if (code == KeyInput.KEY_RIGHT) {
            editorModel.stepQuestion(1);
        } else if (code == KeyInput.KEY_ESCAPE) {
            closeEditor();
        }
    }

    private final RawInputListener rawInputListener = new RawInputListener() {
        private long lastTouchTime = 0;

        @Override public void onKeyEvent(KeyInputEvent evt) { handleKey(evt); }

        @Override public void onMouseMotionEvent(MouseMotionEvent evt) {
            pointerX = evt.getX();
            pointerY = evt.getY();
        }

        @Override public void onMouseButtonEvent(MouseButtonEvent evt) {
            // Touch screens also emit emulated mouse events; ignore those right after a real touch.
            if (System.currentTimeMillis() - lastTouchTime < 250) return;
            if (evt.isPressed() && evt.getButtonIndex() == MouseInput.BUTTON_LEFT) {
                handleClick(evt.getX(), evt.getY());
            }
        }

        @Override public void onTouchEvent(TouchEvent evt) {
            if (evt.getType() == TouchEvent.Type.DOWN) {
                lastTouchTime = System.currentTimeMillis();
                handleClick(evt.getX(), evt.getY());
                pointerX = pointerY = -1f;   // no cursor on touch screens: don't leave a button stuck in its hover state
            }
        }

        @Override public void beginInput() {}
        @Override public void endInput() {}
        @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
        @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
    };
}
