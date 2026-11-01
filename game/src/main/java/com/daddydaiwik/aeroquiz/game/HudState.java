package com.daddydaiwik.aeroquiz.game;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AppState responsible for all 2-D HUD rendering, including:
 * <ul>
 *   <li>Pilot Name Registration & Flight Command Terminal</li>
 *   <li>In-Built Interactive Quiz Questions & Settings Editor</li>
 *   <li>In-flight Header, Question, Bottom Thumb buttons, Timer, Lives, and Score display</li>
 *   <li>Database Leaderboards on Game Over</li>
 * </ul>
 */
public class HudState extends AbstractAppState {

    private static final float FLASH_DURATION = 0.4f;

    public enum TerminalScreen {
        PILOT_TERMINAL,
        QUIZ_EDITOR
    }
    private TerminalScreen currentTerminalScreen = TerminalScreen.PILOT_TERMINAL;

    private static final String[] CALLSIGN_PRESETS = {
        "Maheep", "Ace", "Maverick", "Viper", "Phoenix", "Ghost", "Hawk", "Falcon", "SkyLord", "Eagle"
    };
    private int callsignIndex = 0;
    private String editorStatus = "";

    private AeroQuiz  app;

    public HudState() {}

    public HudState(AeroQuiz app) {
        this.app = app;
    }

    private GameState gameState;

    private int screenW;
    private int screenH;

    private Node hudNode;

    // Input debounce to prevent accidental pre-fire or touch leakage across phase transitions
    private float inputCooldown = 0.5f;

    // In-flight HUD Text elements
    private BitmapText questionText;
    private BitmapText timerText;
    private BitmapText livesText;
    private BitmapText scoreText;
    private BitmapText progressText;
    private BitmapText controlsHintText;

    // In-flight Bottom Thumb Buttons for mobile and touch screens
    private Node         thumbButtonsNode;
    private final Node[] thumbBtnNodes = new Node[6];
    private final Geometry[] thumbBtnBgs = new Geometry[6];
    private final Geometry[] thumbBtnBars = new Geometry[6];
    private final BitmapText[] thumbBtnTexts = new BitmapText[6];

    // ── Panel 1: Pilot Registration Screen elements ──────────────────────────
    private Node       nameInputNode;
    private BitmapText regTitleText;
    private BitmapText regPromptText;
    private BitmapText regInputText;
    private BitmapText regLeaderboardHeader;
    private BitmapText regLeaderboardList;
    private BitmapText regFooterHint;
    private Geometry   regPanelBg;
    private Geometry   regInputBoxBg;
    private BitmapText regAircraftText;
    private static final QuizSettings.AircraftType[] AIRCRAFT_PRESETS = QuizSettings.AircraftType.values();
    private int aircraftPresetIndex = 0;

    private final StringBuilder nameBuffer = new StringBuilder("Maheep");
    private float cursorTimer = 0f;
    private boolean showCursor = true;
    private boolean regLeaderboardLoaded = false;

    // ── Panel 2: In-Built Quiz Questions & Settings Editor elements ──────────
    public enum EditorField {
        NONE, QUESTION_PROMPT, OPT_RED, OPT_BLUE, OPT_GREEN, OPT_YELLOW
    }
    private EditorField activeEditField = EditorField.NONE;
    private final StringBuilder editorTextBuffer = new StringBuilder();

    private Node       editorNode;
    private BitmapText edTitleText;
    private BitmapText edTerrainText;
    private BitmapText edLivesText;
    private BitmapText edTimerText;
    private BitmapText edAircraftText;
    private BitmapText edQNavText;
    private BitmapText edQTitleText;
    private BitmapText edOpt1Text;
    private BitmapText edOpt2Text;
    private BitmapText edOpt3Text;
    private BitmapText edOpt4Text;
    private BitmapText edCorrectText;
    private BitmapText edStatusText;

    private BitmapText edQEditBtnText;
    private BitmapText edOpt1EditBtnText;
    private BitmapText edOpt2EditBtnText;
    private BitmapText edOpt3EditBtnText;
    private BitmapText edOpt4EditBtnText;

    private List<QuizQuestion> editorQuestions = new ArrayList<>();
    private QuizSettings       editorSettings  = new QuizSettings();
    private int                editorQuestionIndex = 0;

    private static final String[] TERRAIN_PRESETS = {"GRASS", "WATER", "SAND", "SNOW"};
    private int terrainPresetIndex = 0;
    private static final int[] LIVES_PRESETS = {1, 2, 3, 4, 5, -1};
    private int livesPresetIndex = 2; // Default 3 lives
    private static final float[] TIMER_PRESETS = {5f, 10f, 15f, 20f, 30f, -1f};
    private int timerPresetIndex = 2; // Default 15s

    // ── Game Over & Leaderboard elements ─────────────────────────────────────
    private BitmapText gameOverText;
    private BitmapText gameOverPilotText;
    private BitmapText gameOverScoreText;
    private BitmapText gameOverSubText;
    private BitmapText gameOverDbText;
    private BitmapText gameOverLeaderboardText;
    private boolean    gameOverDbLoaded = false;

    // Distinct Game Over touch buttons
    private Node       gameOverButtonsNode;
    private Geometry   gameOverRetryBg;
    private Geometry   gameOverRetryBorder;
    private BitmapText gameOverRetryText;
    private Geometry   gameOverTerminalBg;
    private Geometry   gameOverTerminalBorder;
    private BitmapText gameOverTerminalText;

    // Header banner & red damage flash
    private Geometry headerBanner;
    private Geometry flashQuad;
    private boolean  flashing   = false;
    private float    flashTimer = 0f;

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app       = (AeroQuiz) application;
        this.gameState = stateManager.getState(GameState.class);

        screenW = app.getCamera().getWidth();
        screenH = app.getCamera().getHeight();

        hudNode = new Node("HUD");
        app.getGuiNode().attachChild(hudNode);

        buildHud();
        QuizSettings s = QuizLoader.getSettings();
        if (s != null && s.getAircraft() != null) {
            for (int i = 0; i < AIRCRAFT_PRESETS.length; i++) {
                if (AIRCRAFT_PRESETS[i] == s.getAircraft()) {
                    aircraftPresetIndex = i;
                    break;
                }
            }
        }
        updateAircraftUiText();
        app.getInputManager().addRawInputListener(rawInputListener);
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        screenW = app.getCamera().getWidth();
        screenH = app.getCamera().getHeight();

        if (inputCooldown > 0f) {
            inputCooldown -= tpf;
        }

        if (headerBanner != null) {
            headerBanner.setLocalTranslation(0f, screenH - 110f, -0.5f);
            headerBanner.setLocalScale(screenW, 110f, 1f);
        }
        if (flashQuad != null) {
            flashQuad.setLocalScale(screenW, screenH, 1f);
        }

        // Red flash update
        if (flashing) {
            flashTimer += tpf;
            float alpha = Math.max(0f, 0.45f * (1f - flashTimer / FLASH_DURATION));
            flashQuad.getMaterial().setColor("Color", new ColorRGBA(1f, 0f, 0f, alpha));
            if (flashTimer >= FLASH_DURATION) {
                flashing = false;
                flashQuad.setCullHint(Spatial.CullHint.Always);
            }
        }

        if (gameState == null) return;

        // ── Phase 1: Pilot Name Registration / Command Terminal ───────────────
        if (gameState.getPhase() == GameState.Phase.NAME_INPUT) {
            updateTerminalScreens(tpf);
            return;
        } else {
            if (nameInputNode != null)   nameInputNode.setCullHint(Spatial.CullHint.Always);
            if (editorNode != null)      editorNode.setCullHint(Spatial.CullHint.Always);
        }

        // ── Phase 2: Game Over / Completion Screen ────────────────────────────
        if (gameState.isGameOver()) {
            if (headerBanner != null) headerBanner.setCullHint(Spatial.CullHint.Always);
            hideFlightHud();
            showGameOver();
            return;
        }

        // ── Phase 3: Active In-Flight HUD ─────────────────────────────────────
        hideGameOver();
        if (headerBanner != null) headerBanner.setCullHint(Spatial.CullHint.Inherit);

        // Score & Progress (top right)
        scoreText.setText("SCORE: " + gameState.getScore());
        scoreText.setLocalTranslation(screenW - 220f, screenH - 18f, 0f);
        scoreText.setCullHint(Spatial.CullHint.Inherit);

        int curQ = gameState.getCurrentQuestionNumber();
        int totQ = gameState.getTotalQuestions();
        progressText.setText("QUESTION: " + curQ + " / " + totQ);
        progressText.setLocalTranslation(screenW - 220f, screenH - 45f, 0f);
        progressText.setCullHint(Spatial.CullHint.Inherit);

        // Lives display (top left)
        updateLives();
        livesText.setCullHint(Spatial.CullHint.Inherit);

        // Timer update (top center below question)
        updateTimer();

        // Question text & thumb buttons
        QuizQuestion q = gameState.getCurrentQuestion();
        if (q != null) {
            showQuestion(q);
            updateThumbButtons();
        } else {
            questionText.setText("");
            timerText.setText("");
            controlsHintText.setText("");
            if (thumbButtonsNode != null) {
                thumbButtonsNode.setCullHint(Spatial.CullHint.Always);
            }
        }
    }

    private void updateTerminalScreens(float tpf) {
        hideFlightHud();
        hideGameOver();

        // Blinking cursor
        cursorTimer += tpf;
        if (cursorTimer >= 0.45f) {
            cursorTimer = 0f;
            showCursor = !showCursor;
        }

        if (currentTerminalScreen == TerminalScreen.PILOT_TERMINAL) {
            if (nameInputNode != null) {
                float pw = 780f;
                float ph = 520f;
                float px = (screenW - pw) / 2f;
                float py = (screenH - ph) / 2f;
                nameInputNode.setLocalTranslation(px, py, 0f);
                nameInputNode.setCullHint(Spatial.CullHint.Inherit);

                String displayStr = nameBuffer.toString() + (showCursor ? "|" : " ");
                regInputText.setText(displayStr);

                // Load leaderboard preview once from DB
                if (!regLeaderboardLoaded) {
                    regLeaderboardLoaded = true;
                    QuizDatabase db = (app != null) ? app.getQuizDatabase() : null;
                    if (db != null) {
                        List<QuizDatabase.ScoreRecord> tops = db.getTopScores(3);
                        if (tops.isEmpty()) {
                            regLeaderboardList.setText("No recorded scores yet — be the first pilot on the board!");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < tops.size(); i++) {
                                QuizDatabase.ScoreRecord rec = tops.get(i);
                                String dateStr = (rec.playedAt() != null && rec.playedAt().length() >= 10)
                                        ? rec.playedAt().substring(0, 10) : "";
                                sb.append(String.format(" #%d  %-16s %3d pts  (%d/%d)   %s\n",
                                        i + 1, rec.playerName(), rec.score(),
                                        rec.correctAnswers(), rec.totalQuestions(), dateStr));
                            }
                            regLeaderboardList.setText(sb.toString());
                        }
                    }
                }
            }
            if (editorNode != null) editorNode.setCullHint(Spatial.CullHint.Always);

        } else if (currentTerminalScreen == TerminalScreen.QUIZ_EDITOR) {
            if (editorNode != null) {
                float pw = 840f;
                float ph = 560f;
                float px = (screenW - pw) / 2f;
                float py = (screenH - ph) / 2f;
                editorNode.setLocalTranslation(px, py, 0f);
                editorNode.setCullHint(Spatial.CullHint.Inherit);

                updateEditorView();
            }
            if (nameInputNode != null) nameInputNode.setCullHint(Spatial.CullHint.Always);
        }
    }

    private void hideFlightHud() {
        if (headerBanner != null)     headerBanner.setCullHint(Spatial.CullHint.Always);
        if (questionText != null)     questionText.setCullHint(Spatial.CullHint.Always);
        if (timerText != null)        timerText.setCullHint(Spatial.CullHint.Always);
        if (livesText != null)        livesText.setCullHint(Spatial.CullHint.Always);
        if (scoreText != null)        scoreText.setCullHint(Spatial.CullHint.Always);
        if (progressText != null)     progressText.setCullHint(Spatial.CullHint.Always);
        if (controlsHintText != null) controlsHintText.setCullHint(Spatial.CullHint.Always);
        if (thumbButtonsNode != null) thumbButtonsNode.setCullHint(Spatial.CullHint.Always);
    }

    private void hideGameOver() {
        if (gameOverText != null)            gameOverText.setCullHint(Spatial.CullHint.Always);
        if (gameOverPilotText != null)       gameOverPilotText.setCullHint(Spatial.CullHint.Always);
        if (gameOverScoreText != null)       gameOverScoreText.setCullHint(Spatial.CullHint.Always);
        if (gameOverSubText != null)         gameOverSubText.setCullHint(Spatial.CullHint.Always);
        if (gameOverDbText != null)          gameOverDbText.setCullHint(Spatial.CullHint.Always);
        if (gameOverLeaderboardText != null) gameOverLeaderboardText.setCullHint(Spatial.CullHint.Always);
        if (gameOverButtonsNode != null)     gameOverButtonsNode.setCullHint(Spatial.CullHint.Always);
    }

    @Override
    public void cleanup() {
        if (app != null && app.getInputManager() != null) {
            app.getInputManager().removeRawInputListener(rawInputListener);
        }
        if (hudNode != null) {
            hudNode.removeFromParent();
        }
        super.cleanup();
    }

    public void triggerRedFlash() {
        flashing   = true;
        flashTimer = 0f;
        flashQuad.setCullHint(Spatial.CullHint.Inherit);
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    private void buildHud() {
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        // 0. Dark semi-transparent header bar across top of screen
        headerBanner = makeQuad("HeaderBanner", 1f, 1f, 0f, 0f, new ColorRGBA(0.04f, 0.06f, 0.12f, 0.65f));
        hudNode.attachChild(headerBanner);

        // 1. Lives / Damage status — top-left
        livesText = new BitmapText(font);
        livesText.setSize(font.getCharSet().getRenderedSize() * 1.15f);
        livesText.setColor(new ColorRGBA(0.2f, 0.95f, 0.35f, 1f));
        livesText.setLocalTranslation(25f, screenH - 18f, 0f);
        hudNode.attachChild(livesText);

        // 2. Score — top-right
        scoreText = new BitmapText(font);
        scoreText.setSize(font.getCharSet().getRenderedSize() * 1.25f);
        scoreText.setColor(ColorRGBA.White);
        scoreText.setText("SCORE: 0");
        scoreText.setLocalTranslation(screenW - 220f, screenH - 18f, 0f);
        hudNode.attachChild(scoreText);

        // 3. Question Progress — below score
        progressText = new BitmapText(font);
        progressText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        progressText.setColor(new ColorRGBA(0.85f, 0.85f, 0.85f, 1f));
        progressText.setText("QUESTION: 1 / 1");
        progressText.setLocalTranslation(screenW - 220f, screenH - 45f, 0f);
        hudNode.attachChild(progressText);

        // 4. Question Text — TOP of screen inside header bar
        questionText = new BitmapText(font);
        questionText.setSize(font.getCharSet().getRenderedSize() * 1.6f);
        questionText.setColor(ColorRGBA.White);
        questionText.setText("");
        questionText.setLocalTranslation(screenW / 2f, screenH - 18f, 0f);
        hudNode.attachChild(questionText);

        // 5. Timer Text — below the question text
        timerText = new BitmapText(font);
        timerText.setSize(font.getCharSet().getRenderedSize() * 1.15f);
        timerText.setColor(new ColorRGBA(1f, 0.9f, 0.2f, 1f));
        timerText.setText("");
        timerText.setLocalTranslation(screenW / 2f, screenH - 55f, 0f);
        hudNode.attachChild(timerText);

        // 6. Bottom Controls hint
        controlsHintText = new BitmapText(font);
        controlsHintText.setSize(font.getCharSet().getRenderedSize() * 0.92f);
        controlsHintText.setColor(new ColorRGBA(0.85f, 0.85f, 0.85f, 0.85f));
        controlsHintText.setText("");
        controlsHintText.setLocalTranslation(screenW / 2f, 85f, 0f);
        hudNode.attachChild(controlsHintText);

        // 7. Full-screen red flash quad
        flashQuad = makeQuad("RedFlash", 1f, 1f, 0f, 0f, new ColorRGBA(1f, 0f, 0f, 0f));
        flashQuad.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(flashQuad);

        // 8. Bottom thumb buttons for mobile touch control
        buildThumbButtons(font);

        // 9. Flight Command Terminal screens (Pilot Registration, Editor)
        buildPilotTerminalUi(font);
        buildEditorUi(font);

        // 10. Game Over / Completion overlay texts and dedicated touch buttons
        buildGameOverUi(font);
    }

    private void buildThumbButtons(BitmapFont font) {
        thumbButtonsNode = new Node("ThumbButtons");
        for (int i = 0; i < 6; i++) {
            Node btn = new Node("ThumbBtn_" + i);
            Geometry bg = makeQuad("ThumbBg_" + i, 1f, 1f, 0f, 0f, new ColorRGBA(0.04f, 0.08f, 0.16f, 0.92f));
            Geometry bar = makeQuad("ThumbBar_" + i, 1f, 4f, 0f, 0f, ColorRGBA.Cyan);
            BitmapText txt = new BitmapText(font);
            txt.setSize(font.getCharSet().getRenderedSize() * 1.05f);
            txt.setColor(ColorRGBA.White);

            btn.attachChild(bg);
            btn.attachChild(bar);
            btn.attachChild(txt);

            thumbBtnNodes[i] = btn;
            thumbBtnBgs[i] = bg;
            thumbBtnBars[i] = bar;
            thumbBtnTexts[i] = txt;

            thumbButtonsNode.attachChild(btn);
        }
        thumbButtonsNode.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(thumbButtonsNode);
    }

    private void updateThumbButtons() {
        if (gameState == null) return;
        QuizQuestion q = gameState.getCurrentQuestion();
        if (q == null) {
            thumbButtonsNode.setCullHint(Spatial.CullHint.Always);
            return;
        }

        int count = q.getOptionCount();
        if (count < 2) count = 2;
        if (count > 6) count = 6;

        float totalBarW = Math.min(screenW - 40f, 1000f);
        float btnMargin = 12f;
        float btnW = (totalBarW - btnMargin * (count - 1)) / count;
        float btnH = 62f;
        float btnY = 16f;
        float startX = (screenW - totalBarW) / 2f;

        for (int i = 0; i < 6; i++) {
            if (i < count) {
                thumbBtnNodes[i].setCullHint(Spatial.CullHint.Inherit);
                float bx = startX + i * (btnW + btnMargin);
                thumbBtnNodes[i].setLocalTranslation(bx, btnY, 0f);

                thumbBtnBgs[i].setLocalScale(btnW, btnH, 1f);
                thumbBtnBars[i].setLocalScale(btnW, 1f, 1f);
                thumbBtnBars[i].setLocalTranslation(0f, btnH - 4f, 0.1f);

                ColorRGBA col = ObstacleSpawner.getColorForKey(q.getKeyAt(i), i);
                thumbBtnBars[i].getMaterial().setColor("Color", col);

                String optText = q.getOptionAt(i);
                String fullBtnText = "[" + (i + 1) + "] " + optText;
                thumbBtnTexts[i].setText(fullBtnText);

                float maxTextW = btnW - 14f;
                float lw = thumbBtnTexts[i].getLineWidth();
                if (lw > maxTextW && lw > 0f) {
                    int chars = (int) (fullBtnText.length() * (maxTextW / lw)) - 2;
                    if (chars > 4 && chars < fullBtnText.length()) {
                        thumbBtnTexts[i].setText(fullBtnText.substring(0, chars) + "..");
                    }
                }
                float finalW = thumbBtnTexts[i].getLineWidth();
                float finalH = thumbBtnTexts[i].getLineHeight();
                thumbBtnTexts[i].setLocalTranslation((btnW - finalW) / 2f, (btnH + finalH) / 2f - 2f, 0.2f);
            } else {
                thumbBtnNodes[i].setCullHint(Spatial.CullHint.Always);
            }
        }
        thumbButtonsNode.setCullHint(Spatial.CullHint.Inherit);
    }

    private void buildPilotTerminalUi(BitmapFont font) {
        nameInputNode = new Node("NameInputNode");

        float pw = 780f;
        float ph = 520f;

        // Sleek outer black bezel frame with border accents
        Geometry outerFrame = makeQuad("RegOuterFrame", pw + 16f, ph + 16f, -8f, -8f, ColorRGBA.Black);
        nameInputNode.attachChild(outerFrame);
        Geometry frameBorderTop = makeQuad("RegFBorderT", pw + 16f, 2f, -8f, ph + 6f, new ColorRGBA(0.15f, 0.45f, 0.7f, 0.85f));
        nameInputNode.attachChild(frameBorderTop);
        Geometry frameBorderBot = makeQuad("RegFBorderB", pw + 16f, 2f, -8f, -8f, new ColorRGBA(0.15f, 0.45f, 0.7f, 0.85f));
        nameInputNode.attachChild(frameBorderBot);
        Geometry frameBorderL = makeQuad("RegFBorderL", 2f, ph + 16f, -8f, -8f, new ColorRGBA(0.15f, 0.45f, 0.7f, 0.85f));
        nameInputNode.attachChild(frameBorderL);
        Geometry frameBorderR = makeQuad("RegFBorderR", 2f, ph + 16f, pw + 6f, -8f, new ColorRGBA(0.15f, 0.45f, 0.7f, 0.85f));
        nameInputNode.attachChild(frameBorderR);

        regPanelBg = makeQuad("RegPanelBg", pw, ph, 0f, 0f, new ColorRGBA(0.03f, 0.06f, 0.14f, 0.95f));
        nameInputNode.attachChild(regPanelBg);

        Geometry panelBorder = makeQuad("RegBorder", pw, 4f, 0f, ph - 4f, new ColorRGBA(0.2f, 0.85f, 1.0f, 1f));
        nameInputNode.attachChild(panelBorder);

        // Title
        regTitleText = new BitmapText(font);
        regTitleText.setSize(font.getCharSet().getRenderedSize() * 2.1f);
        regTitleText.setColor(new ColorRGBA(1.0f, 0.85f, 0.2f, 1f));
        regTitleText.setText("AEROQUIZ — FLIGHT COMMAND TERMINAL");
        regTitleText.setLocalTranslation(45f, ph - 28f, 0f);
        nameInputNode.attachChild(regTitleText);

        // Callsign Prompt
        regPromptText = new BitmapText(font);
        regPromptText.setSize(font.getCharSet().getRenderedSize() * 1.05f);
        regPromptText.setColor(new ColorRGBA(0.85f, 0.95f, 1.0f, 1f));
        regPromptText.setText("1. PILOT CALLSIGN (SELECT OR TYPE):");
        regPromptText.setLocalTranslation(50f, ph - 70f, 0f);
        nameInputNode.attachChild(regPromptText);

        // Input text box
        regInputBoxBg = makeQuad("RegInputBoxBg", 420f, 42f, 50f, ph - 116f, new ColorRGBA(0.08f, 0.14f, 0.26f, 0.95f));
        nameInputNode.attachChild(regInputBoxBg);

        Geometry regInputBoxBorder = makeQuad("RegInputBoxBorder", 420f, 2f, 50f, ph - 116f, new ColorRGBA(0.2f, 0.8f, 1.0f, 0.8f));
        nameInputNode.attachChild(regInputBoxBorder);

        regInputText = new BitmapText(font);
        regInputText.setSize(font.getCharSet().getRenderedSize() * 1.4f);
        regInputText.setColor(new ColorRGBA(0.2f, 0.95f, 1.0f, 1f));
        regInputText.setText("Maheep|");
        regInputText.setLocalTranslation(65f, ph - 88f, 0.1f);
        nameInputNode.attachChild(regInputText);

        // [ < PREV ] button
        Geometry prevBtnBg = makeQuad("RegPrevBtnBg", 100f, 42f, 485f, ph - 116f, new ColorRGBA(0.12f, 0.22f, 0.40f, 0.95f));
        nameInputNode.attachChild(prevBtnBg);
        BitmapText prevBtnText = new BitmapText(font);
        prevBtnText.setSize(font.getCharSet().getRenderedSize() * 1.02f);
        prevBtnText.setColor(new ColorRGBA(0.85f, 0.95f, 1.0f, 1f));
        prevBtnText.setText("< PREV");
        prevBtnText.setLocalTranslation(498f, ph - 88f, 0.1f);
        nameInputNode.attachChild(prevBtnText);

        // [ NEXT > ] button
        Geometry nextBtnBg = makeQuad("RegNextBtnBg", 100f, 42f, 595f, ph - 116f, new ColorRGBA(0.12f, 0.22f, 0.40f, 0.95f));
        nameInputNode.attachChild(nextBtnBg);
        BitmapText nextBtnText = new BitmapText(font);
        nextBtnText.setSize(font.getCharSet().getRenderedSize() * 1.02f);
        nextBtnText.setColor(new ColorRGBA(0.85f, 0.95f, 1.0f, 1f));
        nextBtnText.setText("NEXT >");
        nextBtnText.setLocalTranslation(608f, ph - 88f, 0.1f);
        nameInputNode.attachChild(nextBtnText);

        // ── 2. Aircraft Selection Row ─────────────────────────────────────────
        BitmapText airPrompt = new BitmapText(font);
        airPrompt.setSize(font.getCharSet().getRenderedSize() * 1.05f);
        airPrompt.setColor(new ColorRGBA(1.0f, 0.85f, 0.25f, 1f));
        airPrompt.setText("2. FLIGHT VEHICLE (BIPLANE / ATTACK HELI / PAGANI CAR):");
        airPrompt.setLocalTranslation(50f, ph - 128f, 0f);
        nameInputNode.attachChild(airPrompt);

        // Aircraft Prev Button: x [50, 100], y [ph - 176, ph - 136]
        Geometry airPrevBg = makeQuad("AirPrevBg", 50f, 40f, 50f, ph - 176f, new ColorRGBA(0.12f, 0.22f, 0.40f, 0.95f));
        nameInputNode.attachChild(airPrevBg);
        BitmapText airPrevText = new BitmapText(font);
        airPrevText.setText("<");
        airPrevText.setLocalTranslation(68f, ph - 149f, 0.1f);
        nameInputNode.attachChild(airPrevText);

        // Aircraft Display Box: x [108, 637], y [ph - 176, ph - 136]
        Geometry airBoxBg = makeQuad("AirBoxBg", 529f, 40f, 108f, ph - 176f, new ColorRGBA(0.06f, 0.12f, 0.22f, 0.95f));
        nameInputNode.attachChild(airBoxBg);
        Geometry airBoxBorder = makeQuad("AirBoxBorder", 529f, 2f, 108f, ph - 176f, new ColorRGBA(1.0f, 0.75f, 0.2f, 0.85f));
        nameInputNode.attachChild(airBoxBorder);

        regAircraftText = new BitmapText(font);
        regAircraftText.setSize(font.getCharSet().getRenderedSize() * 1.02f);
        regAircraftText.setColor(new ColorRGBA(1.0f, 0.95f, 0.6f, 1f));
        regAircraftText.setText("[ ✈ STUNT BIPLANE ] — Aerobatic stunt plane with spinning propeller");
        regAircraftText.setLocalTranslation(120f, ph - 149f, 0.1f);
        nameInputNode.attachChild(regAircraftText);

        // Aircraft Next Button: x [645, 695], y [ph - 176, ph - 136]
        Geometry airNextBg = makeQuad("AirNextBg", 50f, 40f, 645f, ph - 176f, new ColorRGBA(0.12f, 0.22f, 0.40f, 0.95f));
        nameInputNode.attachChild(airNextBg);
        BitmapText airNextText = new BitmapText(font);
        airNextText.setText(">");
        airNextText.setLocalTranslation(663f, ph - 149f, 0.1f);
        nameInputNode.attachChild(airNextText);

        // ── 3. Action row: CLEAR, START FLIGHT, EDIT QUIZ & SETTINGS ─────────
        // y: ph - 232f, h: 42f
        Geometry delBtnBg = makeQuad("RegDelBtnBg", 110f, 42f, 50f, ph - 232f, new ColorRGBA(0.35f, 0.12f, 0.15f, 0.95f));
        nameInputNode.attachChild(delBtnBg);
        BitmapText delBtnText = new BitmapText(font);
        delBtnText.setSize(font.getCharSet().getRenderedSize() * 1.0f);
        delBtnText.setColor(new ColorRGBA(1.0f, 0.75f, 0.75f, 1f));
        delBtnText.setText("CLEAR");
        delBtnText.setLocalTranslation(75f, ph - 204f, 0.1f);
        nameInputNode.attachChild(delBtnText);

        // [ ► START FLIGHT ◄ ]
        Geometry soloBtnBg = makeQuad("RegSoloBtnBg", 280f, 42f, 180f, ph - 232f, new ColorRGBA(0.10f, 0.48f, 0.22f, 0.95f));
        nameInputNode.attachChild(soloBtnBg);
        Geometry soloBtnBorder = makeQuad("RegSoloBorder", 280f, 3f, 180f, ph - 192f, new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        nameInputNode.attachChild(soloBtnBorder);
        BitmapText soloBtnText = new BitmapText(font);
        soloBtnText.setSize(font.getCharSet().getRenderedSize() * 1.15f);
        soloBtnText.setColor(new ColorRGBA(1.0f, 1.0f, 0.4f, 1f));
        soloBtnText.setText("► START FLIGHT ◄");
        soloBtnText.setLocalTranslation(210f, ph - 203f, 0.1f);
        nameInputNode.attachChild(soloBtnText);

        // [ ✏ EDIT QUIZ & SETTINGS ]
        Geometry edBtnBg = makeQuad("RegEdBtnBg", 255f, 42f, 480f, ph - 232f, new ColorRGBA(0.55f, 0.40f, 0.10f, 0.95f));
        nameInputNode.attachChild(edBtnBg);
        Geometry edBtnBorder = makeQuad("RegEdBorder", 255f, 3f, 480f, ph - 192f, new ColorRGBA(1.0f, 0.85f, 0.3f, 1f));
        nameInputNode.attachChild(edBtnBorder);
        BitmapText edBtnText = new BitmapText(font);
        edBtnText.setSize(font.getCharSet().getRenderedSize() * 1.02f);
        edBtnText.setColor(new ColorRGBA(1.0f, 0.95f, 0.85f, 1f));
        edBtnText.setText("✏ EDIT QUIZ & SETTINGS");
        edBtnText.setLocalTranslation(495f, ph - 203f, 0.1f);
        nameInputNode.attachChild(edBtnText);

        // Leaderboard preview header
        regLeaderboardHeader = new BitmapText(font);
        regLeaderboardHeader.setSize(font.getCharSet().getRenderedSize() * 1.08f);
        regLeaderboardHeader.setColor(new ColorRGBA(1.0f, 0.85f, 0.25f, 1f));
        regLeaderboardHeader.setText("— DATABASE HALL OF FAME (TOP PILOTS) —");
        regLeaderboardHeader.setLocalTranslation(50f, ph - 258f, 0f);
        nameInputNode.attachChild(regLeaderboardHeader);

        regLeaderboardList = new BitmapText(font);
        regLeaderboardList.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        regLeaderboardList.setColor(new ColorRGBA(0.85f, 0.90f, 0.95f, 1f));
        regLeaderboardList.setText("Connecting to database...");
        regLeaderboardList.setLocalTranslation(50f, ph - 288f, 0f);
        nameInputNode.attachChild(regLeaderboardList);

        // Footer hint
        regFooterHint = new BitmapText(font);
        regFooterHint.setSize(font.getCharSet().getRenderedSize() * 0.92f);
        regFooterHint.setColor(new ColorRGBA(0.7f, 0.85f, 0.95f, 0.85f));
        regFooterHint.setText("► TAB / V: Switch Plane / Heli / Car  •  ENTER: Launch Flight  •  EDIT: Custom Quiz");
        regFooterHint.setLocalTranslation(50f, 32f, 0f);
        nameInputNode.attachChild(regFooterHint);

        nameInputNode.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(nameInputNode);
    }

    private void buildEditorUi(BitmapFont font) {
        editorNode = new Node("EditorNode");
        float pw = 840f;
        float ph = 560f;

        // Sleek outer black bezel frame with border accents
        Geometry outerFrame = makeQuad("EdOuterFrame", pw + 16f, ph + 16f, -8f, -8f, ColorRGBA.Black);
        editorNode.attachChild(outerFrame);
        Geometry frameBorderTop = makeQuad("EdFBorderT", pw + 16f, 2f, -8f, ph + 6f, new ColorRGBA(1.0f, 0.85f, 0.2f, 0.85f));
        editorNode.attachChild(frameBorderTop);
        Geometry frameBorderBot = makeQuad("EdFBorderB", pw + 16f, 2f, -8f, -8f, new ColorRGBA(1.0f, 0.85f, 0.2f, 0.85f));
        editorNode.attachChild(frameBorderBot);
        Geometry frameBorderL = makeQuad("EdFBorderL", 2f, ph + 16f, -8f, -8f, new ColorRGBA(1.0f, 0.85f, 0.2f, 0.85f));
        editorNode.attachChild(frameBorderL);
        Geometry frameBorderR = makeQuad("EdFBorderR", 2f, ph + 16f, pw + 6f, -8f, new ColorRGBA(1.0f, 0.85f, 0.2f, 0.85f));
        editorNode.attachChild(frameBorderR);

        Geometry bg = makeQuad("EdBg", pw, ph, 0f, 0f, new ColorRGBA(0.02f, 0.05f, 0.12f, 0.96f));
        editorNode.attachChild(bg);
        Geometry border = makeQuad("EdBorder", pw, 4f, 0f, ph - 4f, new ColorRGBA(1.0f, 0.85f, 0.2f, 1f));
        editorNode.attachChild(border);

        edTitleText = new BitmapText(font);
        edTitleText.setSize(font.getCharSet().getRenderedSize() * 2.0f);
        edTitleText.setColor(new ColorRGBA(1.0f, 0.85f, 0.2f, 1f));
        edTitleText.setText("IN-BUILT QUIZ & SETTINGS EDITOR");
        edTitleText.setLocalTranslation(45f, ph - 24f, 0f);
        editorNode.attachChild(edTitleText);

        // Section 1: Flight Parameters Container Frame
        Geometry paramsFrame = makeQuad("EdParamsFrame", pw - 70f, 78f, 35f, ph - 128f, new ColorRGBA(0.01f, 0.02f, 0.06f, 0.92f));
        editorNode.attachChild(paramsFrame);
        Geometry paramsBorder = makeQuad("EdParamsBorder", pw - 70f, 2f, 35f, ph - 52f, new ColorRGBA(0.2f, 0.6f, 0.9f, 0.8f));
        editorNode.attachChild(paramsBorder);

        BitmapText sHeader = new BitmapText(font);
        sHeader.setSize(font.getCharSet().getRenderedSize() * 1.15f);
        sHeader.setColor(new ColorRGBA(0.2f, 0.9f, 1.0f, 1f));
        sHeader.setText("1. FLIGHT PARAMETERS:");
        sHeader.setLocalTranslation(50f, ph - 60f, 0f);
        editorNode.attachChild(sHeader);

        // Terrain Switcher
        edTerrainText = new BitmapText(font);
        edTerrainText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edTerrainText.setText("TERRAIN: [ GRASS ]");
        edTerrainText.setLocalTranslation(48f, ph - 92f, 0f);
        editorNode.attachChild(edTerrainText);

        Geometry terPrev = makeQuad("EdTerPrev", 30f, 26f, 185f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(terPrev);
        BitmapText tpText = new BitmapText(font); tpText.setText("<"); tpText.setLocalTranslation(196f, ph - 92f, 0.1f);
        editorNode.attachChild(tpText);

        Geometry terNext = makeQuad("EdTerNext", 30f, 26f, 220f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(terNext);
        BitmapText tnText = new BitmapText(font); tnText.setText(">"); tnText.setLocalTranslation(231f, ph - 92f, 0.1f);
        editorNode.attachChild(tnText);

        // Lives Switcher
        edLivesText = new BitmapText(font);
        edLivesText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edLivesText.setText("LIVES: [ 3 ]");
        edLivesText.setLocalTranslation(265f, ph - 92f, 0f);
        editorNode.attachChild(edLivesText);

        Geometry livPrev = makeQuad("EdLivPrev", 30f, 26f, 355f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(livPrev);
        BitmapText lpText = new BitmapText(font); lpText.setText("-"); lpText.setLocalTranslation(366f, ph - 92f, 0.1f);
        editorNode.attachChild(lpText);

        Geometry livNext = makeQuad("EdLivNext", 30f, 26f, 390f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(livNext);
        BitmapText lnText = new BitmapText(font); lnText.setText("+"); lnText.setLocalTranslation(401f, ph - 92f, 0.1f);
        editorNode.attachChild(lnText);

        // Timer Switcher
        edTimerText = new BitmapText(font);
        edTimerText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edTimerText.setText("TIME: [ 15s ]");
        edTimerText.setLocalTranslation(435f, ph - 92f, 0f);
        editorNode.attachChild(edTimerText);

        Geometry timPrev = makeQuad("EdTimPrev", 30f, 26f, 520f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(timPrev);
        BitmapText tmText = new BitmapText(font); tmText.setText("-"); tmText.setLocalTranslation(531f, ph - 92f, 0.1f);
        editorNode.attachChild(tmText);

        Geometry timNext = makeQuad("EdTimNext", 30f, 26f, 555f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(timNext);
        BitmapText tp2Text = new BitmapText(font); tp2Text.setText("+"); tp2Text.setLocalTranslation(566f, ph - 92f, 0.1f);
        editorNode.attachChild(tp2Text);

        // Aircraft Switcher
        edAircraftText = new BitmapText(font);
        edAircraftText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edAircraftText.setColor(new ColorRGBA(1.0f, 0.85f, 0.3f, 1f));
        edAircraftText.setText("VEHICLE: [ ✈ PLANE ]");
        edAircraftText.setLocalTranslation(600f, ph - 92f, 0f);
        editorNode.attachChild(edAircraftText);

        Geometry airPrevEd = makeQuad("EdAirPrev", 30f, 26f, 735f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(airPrevEd);
        BitmapText apText = new BitmapText(font); apText.setText("<"); apText.setLocalTranslation(746f, ph - 92f, 0.1f);
        editorNode.attachChild(apText);

        Geometry airNextEd = makeQuad("EdAirNext", 30f, 26f, 770f, ph - 113f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(airNextEd);
        BitmapText anText = new BitmapText(font); anText.setText(">"); anText.setLocalTranslation(781f, ph - 92f, 0.1f);
        editorNode.attachChild(anText);

        // Section 2: Questions Catalog & Options Editor Container Frame
        Geometry qFrame = makeQuad("EdQFrame", pw - 70f, ph - 225f, 35f, 82f, new ColorRGBA(0.01f, 0.02f, 0.06f, 0.92f));
        editorNode.attachChild(qFrame);
        Geometry qBorder = makeQuad("EdQBorder", pw - 70f, 2f, 35f, ph - 142f, new ColorRGBA(1.0f, 0.8f, 0.2f, 0.8f));
        editorNode.attachChild(qBorder);

        edQNavText = new BitmapText(font);
        edQNavText.setSize(font.getCharSet().getRenderedSize() * 1.15f);
        edQNavText.setColor(new ColorRGBA(0.2f, 0.9f, 1.0f, 1f));
        edQNavText.setText("2. QUESTIONS CATALOG — QUESTION 1 of 5:");
        edQNavText.setLocalTranslation(50f, ph - 150f, 0f);
        editorNode.attachChild(edQNavText);

        // Question Nav buttons: < PREV Q, NEXT Q >, + ADD Q, ✕ DELETE
        Geometry qPrevBtn = makeQuad("EdQPrev", 95f, 30f, 50f, ph - 192f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(qPrevBtn);
        BitmapText qpText = new BitmapText(font); qpText.setText("< PREV"); qpText.setLocalTranslation(68f, ph - 170f, 0.1f);
        editorNode.attachChild(qpText);

        Geometry qNextBtn = makeQuad("EdQNext", 95f, 30f, 155f, ph - 192f, new ColorRGBA(0.12f, 0.25f, 0.45f, 0.95f));
        editorNode.attachChild(qNextBtn);
        BitmapText qnText = new BitmapText(font); qnText.setText("NEXT >"); qnText.setLocalTranslation(175f, ph - 170f, 0.1f);
        editorNode.attachChild(qnText);

        Geometry qAddBtn = makeQuad("EdQAdd", 95f, 30f, 260f, ph - 192f, new ColorRGBA(0.12f, 0.45f, 0.25f, 0.95f));
        editorNode.attachChild(qAddBtn);
        BitmapText qaText = new BitmapText(font); qaText.setText("+ NEW Q"); qaText.setLocalTranslation(275f, ph - 170f, 0.1f);
        editorNode.attachChild(qaText);

        Geometry qDelBtn = makeQuad("EdQDel", 95f, 30f, 365f, ph - 192f, new ColorRGBA(0.45f, 0.15f, 0.15f, 0.95f));
        editorNode.attachChild(qDelBtn);
        BitmapText qdText = new BitmapText(font); qdText.setText("✕ DELETE"); qdText.setLocalTranslation(375f, ph - 170f, 0.1f);
        editorNode.attachChild(qdText);

        // Row 0: Question Prompt
        Geometry edQBox = makeQuad("EdQBox", 640f, 32f, 50f, ph - 238f, new ColorRGBA(0.06f, 0.12f, 0.24f, 0.95f));
        editorNode.attachChild(edQBox);
        Geometry edQEditBtn = makeQuad("EdQEditBtn", 95f, 32f, 700f, ph - 238f, new ColorRGBA(0.18f, 0.45f, 0.85f, 0.95f));
        editorNode.attachChild(edQEditBtn);

        edQTitleText = new BitmapText(font);
        edQTitleText.setSize(font.getCharSet().getRenderedSize() * 1.05f);
        edQTitleText.setColor(new ColorRGBA(1.0f, 1.0f, 0.4f, 1f));
        edQTitleText.setText("Q: Loading...");
        edQTitleText.setLocalTranslation(55f, ph - 215f, 0.1f);
        editorNode.attachChild(edQTitleText);

        edQEditBtnText = new BitmapText(font);
        edQEditBtnText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edQEditBtnText.setColor(ColorRGBA.White);
        edQEditBtnText.setText("✏ EDIT");
        edQEditBtnText.setLocalTranslation(712f, ph - 215f, 0.1f);
        editorNode.attachChild(edQEditBtnText);

        // Row 1: Option RED
        Geometry edOpt1Box = makeQuad("EdOpt1Box", 640f, 30f, 50f, ph - 276f, new ColorRGBA(0.20f, 0.06f, 0.08f, 0.95f));
        editorNode.attachChild(edOpt1Box);
        Geometry edOpt1EditBtn = makeQuad("EdOpt1EditBtn", 95f, 30f, 700f, ph - 276f, new ColorRGBA(0.70f, 0.20f, 0.22f, 0.95f));
        editorNode.attachChild(edOpt1EditBtn);

        edOpt1Text = new BitmapText(font);
        edOpt1Text.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt1Text.setColor(new ColorRGBA(1.0f, 0.45f, 0.45f, 1f));
        edOpt1Text.setLocalTranslation(55f, ph - 254f, 0.1f);
        editorNode.attachChild(edOpt1Text);

        edOpt1EditBtnText = new BitmapText(font);
        edOpt1EditBtnText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt1EditBtnText.setColor(ColorRGBA.White);
        edOpt1EditBtnText.setText("✏ EDIT");
        edOpt1EditBtnText.setLocalTranslation(712f, ph - 254f, 0.1f);
        editorNode.attachChild(edOpt1EditBtnText);

        // Row 2: Option BLUE
        Geometry edOpt2Box = makeQuad("EdOpt2Box", 640f, 30f, 50f, ph - 312f, new ColorRGBA(0.06f, 0.14f, 0.26f, 0.95f));
        editorNode.attachChild(edOpt2Box);
        Geometry edOpt2EditBtn = makeQuad("EdOpt2EditBtn", 95f, 30f, 700f, ph - 312f, new ColorRGBA(0.18f, 0.45f, 0.85f, 0.95f));
        editorNode.attachChild(edOpt2EditBtn);

        edOpt2Text = new BitmapText(font);
        edOpt2Text.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt2Text.setColor(new ColorRGBA(0.45f, 0.75f, 1.0f, 1f));
        edOpt2Text.setLocalTranslation(55f, ph - 290f, 0.1f);
        editorNode.attachChild(edOpt2Text);

        edOpt2EditBtnText = new BitmapText(font);
        edOpt2EditBtnText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt2EditBtnText.setColor(ColorRGBA.White);
        edOpt2EditBtnText.setText("✏ EDIT");
        edOpt2EditBtnText.setLocalTranslation(712f, ph - 290f, 0.1f);
        editorNode.attachChild(edOpt2EditBtnText);

        // Row 3: Option GREEN
        Geometry edOpt3Box = makeQuad("EdOpt3Box", 640f, 30f, 50f, ph - 348f, new ColorRGBA(0.04f, 0.18f, 0.10f, 0.95f));
        editorNode.attachChild(edOpt3Box);
        Geometry edOpt3EditBtn = makeQuad("EdOpt3EditBtn", 95f, 30f, 700f, ph - 348f, new ColorRGBA(0.15f, 0.60f, 0.30f, 0.95f));
        editorNode.attachChild(edOpt3EditBtn);

        edOpt3Text = new BitmapText(font);
        edOpt3Text.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt3Text.setColor(new ColorRGBA(0.45f, 1.0f, 0.55f, 1f));
        edOpt3Text.setLocalTranslation(55f, ph - 326f, 0.1f);
        editorNode.attachChild(edOpt3Text);

        edOpt3EditBtnText = new BitmapText(font);
        edOpt3EditBtnText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt3EditBtnText.setColor(ColorRGBA.White);
        edOpt3EditBtnText.setText("✏ EDIT");
        edOpt3EditBtnText.setLocalTranslation(712f, ph - 326f, 0.1f);
        editorNode.attachChild(edOpt3EditBtnText);

        // Row 4: Option YELLOW
        Geometry edOpt4Box = makeQuad("EdOpt4Box", 640f, 30f, 50f, ph - 384f, new ColorRGBA(0.20f, 0.18f, 0.05f, 0.95f));
        editorNode.attachChild(edOpt4Box);
        Geometry edOpt4EditBtn = makeQuad("EdOpt4EditBtn", 95f, 30f, 700f, ph - 384f, new ColorRGBA(0.70f, 0.60f, 0.15f, 0.95f));
        editorNode.attachChild(edOpt4EditBtn);

        edOpt4Text = new BitmapText(font);
        edOpt4Text.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt4Text.setColor(new ColorRGBA(1.0f, 0.95f, 0.35f, 1f));
        edOpt4Text.setLocalTranslation(55f, ph - 362f, 0.1f);
        editorNode.attachChild(edOpt4Text);

        edOpt4EditBtnText = new BitmapText(font);
        edOpt4EditBtnText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edOpt4EditBtnText.setColor(ColorRGBA.White);
        edOpt4EditBtnText.setText("✏ EDIT");
        edOpt4EditBtnText.setLocalTranslation(712f, ph - 362f, 0.1f);
        editorNode.attachChild(edOpt4EditBtnText);

        // Row 5: Correct answer indicator & cycle button + mobile touch editing buttons
        edCorrectText = new BitmapText(font);
        edCorrectText.setSize(font.getCharSet().getRenderedSize() * 1.0f);
        edCorrectText.setColor(new ColorRGBA(0.2f, 1.0f, 0.5f, 1f));
        edCorrectText.setText("CORRECT: [ BLUE ]");
        edCorrectText.setLocalTranslation(50f, ph - 406f, 0f);
        editorNode.attachChild(edCorrectText);

        Geometry cycleCorrectBtn = makeQuad("EdCycleCorrect", 160f, 34f, 260f, ph - 430f, new ColorRGBA(0.15f, 0.35f, 0.65f, 0.95f));
        editorNode.attachChild(cycleCorrectBtn);
        BitmapText ccText = new BitmapText(font);
        ccText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        ccText.setColor(ColorRGBA.White);
        ccText.setText("★ CYCLE CORRECT");
        ccText.setLocalTranslation(275f, ph - 407f, 0.1f);
        editorNode.attachChild(ccText);

        // Mobile touch edit buttons: [ ⌫ DEL ], [ CLEAR ], [ ✔ DONE ], [ ↵ NEXT ]
        Geometry delBtn = makeQuad("EdTouchDel", 75f, 34f, 440f, ph - 430f, new ColorRGBA(0.50f, 0.15f, 0.15f, 0.95f));
        editorNode.attachChild(delBtn);
        BitmapText delText = new BitmapText(font);
        delText.setSize(font.getCharSet().getRenderedSize() * 0.9f);
        delText.setColor(ColorRGBA.White);
        delText.setText("⌫ DEL");
        delText.setLocalTranslation(452f, ph - 407f, 0.1f);
        editorNode.attachChild(delText);

        Geometry clrBtn = makeQuad("EdTouchClr", 80f, 34f, 525f, ph - 430f, new ColorRGBA(0.40f, 0.28f, 0.12f, 0.95f));
        editorNode.attachChild(clrBtn);
        BitmapText clrText = new BitmapText(font);
        clrText.setSize(font.getCharSet().getRenderedSize() * 0.9f);
        clrText.setColor(ColorRGBA.White);
        clrText.setText("CLEAR");
        clrText.setLocalTranslation(540f, ph - 407f, 0.1f);
        editorNode.attachChild(clrText);

        Geometry doneBtn = makeQuad("EdTouchDone", 85f, 34f, 615f, ph - 430f, new ColorRGBA(0.12f, 0.52f, 0.22f, 0.95f));
        editorNode.attachChild(doneBtn);
        BitmapText doneText = new BitmapText(font);
        doneText.setSize(font.getCharSet().getRenderedSize() * 0.9f);
        doneText.setColor(ColorRGBA.White);
        doneText.setText("✔ DONE");
        doneText.setLocalTranslation(627f, ph - 407f, 0.1f);
        editorNode.attachChild(doneText);

        Geometry nextFldBtn = makeQuad("EdTouchNextFld", 85f, 34f, 710f, ph - 430f, new ColorRGBA(0.18f, 0.35f, 0.65f, 0.95f));
        editorNode.attachChild(nextFldBtn);
        BitmapText nextFldText = new BitmapText(font);
        nextFldText.setSize(font.getCharSet().getRenderedSize() * 0.9f);
        nextFldText.setColor(ColorRGBA.White);
        nextFldText.setText("↵ NEXT");
        nextFldText.setLocalTranslation(725f, ph - 407f, 0.1f);
        editorNode.attachChild(nextFldText);

        // Status line
        edStatusText = new BitmapText(font);
        edStatusText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        edStatusText.setColor(new ColorRGBA(0.2f, 1.0f, 0.4f, 1f));
        edStatusText.setText(editorStatus);
        edStatusText.setLocalTranslation(50f, 92f, 0f);
        editorNode.attachChild(edStatusText);

        // Action Buttons: SAVE & BACK
        Geometry saveBtn = makeQuad("EdSaveBtn", 290f, 46f, 50f, 25f, new ColorRGBA(0.12f, 0.52f, 0.22f, 0.95f));
        editorNode.attachChild(saveBtn);
        Geometry saveBorder = makeQuad("EdSaveBorder", 290f, 2f, 50f, 69f, new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        editorNode.attachChild(saveBorder);
        BitmapText saveText = new BitmapText(font);
        saveText.setSize(font.getCharSet().getRenderedSize() * 1.1f);
        saveText.setColor(new ColorRGBA(1.0f, 1.0f, 0.4f, 1f));
        saveText.setText("💾 SAVE QUIZ & SETTINGS");
        saveText.setLocalTranslation(72f, 55f, 0.1f);
        editorNode.attachChild(saveText);

        Geometry backBtn = makeQuad("EdBackBtn", 260f, 46f, 360f, 25f, new ColorRGBA(0.12f, 0.18f, 0.28f, 0.95f));
        editorNode.attachChild(backBtn);
        BitmapText backText = new BitmapText(font);
        backText.setSize(font.getCharSet().getRenderedSize() * 1.05f);
        backText.setColor(new ColorRGBA(0.85f, 0.90f, 0.95f, 1f));
        backText.setText("◄ BACK TO TERMINAL");
        backText.setLocalTranslation(385f, 55f, 0.1f);
        editorNode.attachChild(backText);

        editorNode.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(editorNode);
    }

    private void updateEditorView() {
        if (editorQuestions.isEmpty()) {
            editorQuestions = new ArrayList<>(QuizLoader.load("questions.txt"));
            editorSettings = QuizLoader.getSettings();
        }

        edTerrainText.setText("TERRAIN: [ " + TERRAIN_PRESETS[terrainPresetIndex] + " ]");
        int lives = LIVES_PRESETS[livesPresetIndex];
        edLivesText.setText("LIVES: [ " + (lives == -1 ? "INF" : lives) + " ]");
        float timer = TIMER_PRESETS[timerPresetIndex];
        edTimerText.setText("TIMER: [ " + (timer == -1 ? "OFF" : (int) timer + "s") + " ]");
        if (edAircraftText != null) {
            QuizSettings.AircraftType chosenAir = AIRCRAFT_PRESETS[aircraftPresetIndex];
            String airLabel = switch (chosenAir) {
                case HELICOPTER -> "HELI";
                case FLYING_CAR -> "CAR";
                default         -> "PLANE";
            };
            edAircraftText.setText("VEHICLE: [ " + airLabel + " ]");
        }

        if (editorQuestions.isEmpty()) {
            edQNavText.setText("NO QUESTIONS (TAP + NEW Q TO ADD)");
            edQTitleText.setText("");
            edOpt1Text.setText("");
            edOpt2Text.setText("");
            edOpt3Text.setText("");
            edOpt4Text.setText("");
            edCorrectText.setText("");
            return;
        }

        if (editorQuestionIndex >= editorQuestions.size()) editorQuestionIndex = 0;
        QuizQuestion q = editorQuestions.get(editorQuestionIndex);

        edQNavText.setText("2. QUESTIONS CATALOG — QUESTION " + (editorQuestionIndex + 1) + " of " + editorQuestions.size() + ":");

        String curSign = showCursor ? "|" : " ";

        // Question Prompt display
        if (activeEditField == EditorField.QUESTION_PROMPT) {
            edQTitleText.setText("Q: " + editorTextBuffer.toString() + curSign);
            edQTitleText.setColor(new ColorRGBA(1.0f, 0.95f, 0.2f, 1f));
            edQEditBtnText.setText("✔ DONE");
            edQEditBtnText.setColor(new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        } else {
            edQTitleText.setText("Q: " + q.getQuestion());
            edQTitleText.setColor(new ColorRGBA(1.0f, 1.0f, 0.4f, 1f));
            edQEditBtnText.setText("✏ EDIT");
            edQEditBtnText.setColor(ColorRGBA.White);
        }

        // RED Option display
        if (activeEditField == EditorField.OPT_RED) {
            edOpt1Text.setText("[RED]:    " + editorTextBuffer.toString() + curSign);
            edOpt1Text.setColor(new ColorRGBA(1.0f, 0.85f, 0.85f, 1f));
            edOpt1EditBtnText.setText("✔ DONE");
            edOpt1EditBtnText.setColor(new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        } else {
            edOpt1Text.setText("[RED]:    " + q.getOption("RED"));
            edOpt1Text.setColor(new ColorRGBA(1.0f, 0.45f, 0.45f, 1f));
            edOpt1EditBtnText.setText("✏ EDIT");
            edOpt1EditBtnText.setColor(ColorRGBA.White);
        }

        // BLUE Option display
        if (activeEditField == EditorField.OPT_BLUE) {
            edOpt2Text.setText("[BLUE]:   " + editorTextBuffer.toString() + curSign);
            edOpt2Text.setColor(new ColorRGBA(0.85f, 0.92f, 1.0f, 1f));
            edOpt2EditBtnText.setText("✔ DONE");
            edOpt2EditBtnText.setColor(new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        } else {
            edOpt2Text.setText("[BLUE]:   " + q.getOption("BLUE"));
            edOpt2Text.setColor(new ColorRGBA(0.45f, 0.75f, 1.0f, 1f));
            edOpt2EditBtnText.setText("✏ EDIT");
            edOpt2EditBtnText.setColor(ColorRGBA.White);
        }

        // GREEN Option display
        if (activeEditField == EditorField.OPT_GREEN) {
            edOpt3Text.setText("[GREEN]:  " + editorTextBuffer.toString() + curSign);
            edOpt3Text.setColor(new ColorRGBA(0.85f, 1.0f, 0.85f, 1f));
            edOpt3EditBtnText.setText("✔ DONE");
            edOpt3EditBtnText.setColor(new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        } else {
            edOpt3Text.setText("[GREEN]:  " + q.getOption("GREEN"));
            edOpt3Text.setColor(new ColorRGBA(0.45f, 1.0f, 0.55f, 1f));
            edOpt3EditBtnText.setText("✏ EDIT");
            edOpt3EditBtnText.setColor(ColorRGBA.White);
        }

        // YELLOW Option display
        if (activeEditField == EditorField.OPT_YELLOW) {
            edOpt4Text.setText("[YELLOW]: " + editorTextBuffer.toString() + curSign);
            edOpt4Text.setColor(new ColorRGBA(1.0f, 1.0f, 0.85f, 1f));
            edOpt4EditBtnText.setText("✔ DONE");
            edOpt4EditBtnText.setColor(new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        } else {
            edOpt4Text.setText("[YELLOW]: " + q.getOption("YELLOW"));
            edOpt4Text.setColor(new ColorRGBA(1.0f, 0.95f, 0.35f, 1f));
            edOpt4EditBtnText.setText("✏ EDIT");
            edOpt4EditBtnText.setColor(ColorRGBA.White);
        }

        edCorrectText.setText("CORRECT: [ " + q.getCorrectColor() + " ]");
        edStatusText.setText(editorStatus);
    }

    private void applyEditorTextBuffer() {
        if (editorQuestions.isEmpty() || editorQuestionIndex >= editorQuestions.size()) return;
        QuizQuestion cur = editorQuestions.get(editorQuestionIndex);
        String text = editorTextBuffer.toString().trim();

        switch (activeEditField) {
            case QUESTION_PROMPT:
                if (text.isEmpty()) text = "Question " + (editorQuestionIndex + 1);
                editorQuestions.set(editorQuestionIndex, new QuizQuestion(text, cur.getOptions(), cur.getCorrectColor()));
                break;
            case OPT_RED:
                if (text.isEmpty()) text = "Option A";
                Map<String, String> oRed = new LinkedHashMap<>(cur.getOptions());
                oRed.put("RED", text);
                editorQuestions.set(editorQuestionIndex, new QuizQuestion(cur.getQuestion(), oRed, cur.getCorrectColor()));
                break;
            case OPT_BLUE:
                if (text.isEmpty()) text = "Option B";
                Map<String, String> oBlue = new LinkedHashMap<>(cur.getOptions());
                oBlue.put("BLUE", text);
                editorQuestions.set(editorQuestionIndex, new QuizQuestion(cur.getQuestion(), oBlue, cur.getCorrectColor()));
                break;
            case OPT_GREEN:
                if (text.isEmpty()) text = "Option C";
                Map<String, String> oGreen = new LinkedHashMap<>(cur.getOptions());
                oGreen.put("GREEN", text);
                editorQuestions.set(editorQuestionIndex, new QuizQuestion(cur.getQuestion(), oGreen, cur.getCorrectColor()));
                break;
            case OPT_YELLOW:
                if (text.isEmpty()) text = "Option D";
                Map<String, String> oYellow = new LinkedHashMap<>(cur.getOptions());
                oYellow.put("YELLOW", text);
                editorQuestions.set(editorQuestionIndex, new QuizQuestion(cur.getQuestion(), oYellow, cur.getCorrectColor()));
                break;
            default:
                break;
        }
    }

    private void cycleNextEditField() {
        applyEditorTextBuffer();
        if (editorQuestions.isEmpty()) return;
        QuizQuestion cur = editorQuestions.get(editorQuestionIndex);
        switch (activeEditField) {
            case NONE:
            case OPT_YELLOW:
                activeEditField = EditorField.QUESTION_PROMPT;
                editorTextBuffer.setLength(0);
                editorTextBuffer.append(cur.getQuestion());
                editorStatus = "Editing Question Prompt. Type or tap touch buttons.";
                break;
            case QUESTION_PROMPT:
                activeEditField = EditorField.OPT_RED;
                editorTextBuffer.setLength(0);
                editorTextBuffer.append(cur.getOption("RED"));
                editorStatus = "Editing RED Option. Type or tap touch buttons.";
                break;
            case OPT_RED:
                activeEditField = EditorField.OPT_BLUE;
                editorTextBuffer.setLength(0);
                editorTextBuffer.append(cur.getOption("BLUE"));
                editorStatus = "Editing BLUE Option. Type or tap touch buttons.";
                break;
            case OPT_BLUE:
                activeEditField = EditorField.OPT_GREEN;
                editorTextBuffer.setLength(0);
                editorTextBuffer.append(cur.getOption("GREEN"));
                editorStatus = "Editing GREEN Option. Type or tap touch buttons.";
                break;
            case OPT_GREEN:
                activeEditField = EditorField.OPT_YELLOW;
                editorTextBuffer.setLength(0);
                editorTextBuffer.append(cur.getOption("YELLOW"));
                editorStatus = "Editing YELLOW Option. Type or tap touch buttons.";
                break;
        }
    }

    private void buildGameOverUi(BitmapFont font) {
        gameOverText = new BitmapText(font);
        gameOverText.setSize(font.getCharSet().getRenderedSize() * 3.4f);
        gameOverText.setColor(new ColorRGBA(1f, 0.2f, 0.1f, 1f));
        gameOverText.setText("GAME OVER");
        gameOverText.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(gameOverText);

        gameOverPilotText = new BitmapText(font);
        gameOverPilotText.setSize(font.getCharSet().getRenderedSize() * 1.5f);
        gameOverPilotText.setColor(new ColorRGBA(0.2f, 0.95f, 1.0f, 1f));
        gameOverPilotText.setText("PILOT: ");
        gameOverPilotText.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(gameOverPilotText);

        gameOverScoreText = new BitmapText(font);
        gameOverScoreText.setSize(font.getCharSet().getRenderedSize() * 2.5f);
        gameOverScoreText.setColor(ColorRGBA.White);
        gameOverScoreText.setText("FINAL SCORE: 0");
        gameOverScoreText.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(gameOverScoreText);

        gameOverSubText = new BitmapText(font);
        gameOverSubText.setSize(font.getCharSet().getRenderedSize() * 1.1f);
        gameOverSubText.setColor(new ColorRGBA(0.85f, 0.85f, 0.85f, 1f));
        gameOverSubText.setText("");
        gameOverSubText.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(gameOverSubText);

        gameOverDbText = new BitmapText(font);
        gameOverDbText.setSize(font.getCharSet().getRenderedSize() * 1.05f);
        gameOverDbText.setColor(new ColorRGBA(0.2f, 1.0f, 0.4f, 1f));
        gameOverDbText.setText("✔ Score recorded to database (aeroquiz.db) via JDBC");
        gameOverDbText.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(gameOverDbText);

        gameOverLeaderboardText = new BitmapText(font);
        gameOverLeaderboardText.setSize(font.getCharSet().getRenderedSize() * 0.95f);
        gameOverLeaderboardText.setColor(new ColorRGBA(0.9f, 0.92f, 0.95f, 1f));
        gameOverLeaderboardText.setText("");
        gameOverLeaderboardText.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(gameOverLeaderboardText);

        // Dedicated Touch Buttons for Game Over / Victory
        gameOverButtonsNode = new Node("GameOverButtons");

        gameOverRetryBg = makeQuad("GameOverRetryBg", 260f, 50f, 0f, 0f, new ColorRGBA(0.10f, 0.48f, 0.20f, 0.95f));
        gameOverRetryBorder = makeQuad("GameOverRetryBorder", 260f, 3f, 0f, 47f, new ColorRGBA(0.3f, 1.0f, 0.5f, 1f));
        gameOverRetryText = new BitmapText(font);
        gameOverRetryText.setSize(font.getCharSet().getRenderedSize() * 1.2f);
        gameOverRetryText.setColor(new ColorRGBA(1.0f, 1.0f, 0.4f, 1f));
        gameOverRetryText.setText("[ ↺ RETRY FLIGHT ]");

        gameOverTerminalBg = makeQuad("GameOverTerminalBg", 260f, 50f, 0f, 0f, new ColorRGBA(0.12f, 0.25f, 0.50f, 0.95f));
        gameOverTerminalBorder = makeQuad("GameOverTerminalBorder", 260f, 3f, 0f, 47f, new ColorRGBA(0.3f, 0.8f, 1.0f, 1f));
        gameOverTerminalText = new BitmapText(font);
        gameOverTerminalText.setSize(font.getCharSet().getRenderedSize() * 1.2f);
        gameOverTerminalText.setColor(new ColorRGBA(0.85f, 0.95f, 1.0f, 1f));
        gameOverTerminalText.setText("[ ⚙ PILOT TERMINAL ]");

        gameOverButtonsNode.attachChild(gameOverRetryBg);
        gameOverButtonsNode.attachChild(gameOverRetryBorder);
        gameOverButtonsNode.attachChild(gameOverRetryText);
        gameOverButtonsNode.attachChild(gameOverTerminalBg);
        gameOverButtonsNode.attachChild(gameOverTerminalBorder);
        gameOverButtonsNode.attachChild(gameOverTerminalText);

        gameOverButtonsNode.setCullHint(Spatial.CullHint.Always);
        hudNode.attachChild(gameOverButtonsNode);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showQuestion(QuizQuestion q) {
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        float baseSize = font.getCharSet().getRenderedSize() * 2.2f;
        questionText.setSize(baseSize);
        questionText.setColor(new ColorRGBA(1.0f, 0.95f, 0.25f, 1f));
        questionText.setText(q.getQuestion());
        float qw = questionText.getLineWidth();
        if (qw > screenW - 480f) {
            float scale = (screenW - 480f) / qw;
            questionText.setSize(Math.max(16f, baseSize * scale));
            qw = questionText.getLineWidth();
        }
        questionText.setLocalTranslation((screenW - qw) / 2f, screenH - 22f, 0f);
        questionText.setCullHint(Spatial.CullHint.Inherit);

        controlsHintText.setText("TAP 3D BOX IN SKY or THUMB BUTTONS [1]..[N]");
        float hw = controlsHintText.getLineWidth();
        controlsHintText.setLocalTranslation((screenW - hw) / 2f, 85f, 0f);
        controlsHintText.setCullHint(Spatial.CullHint.Inherit);
    }

    private void updateTimer() {
        if (gameState.getPhase() != GameState.Phase.PLAYING) {
            timerText.setText("");
            timerText.setCullHint(Spatial.CullHint.Always);
            return;
        }

        float remaining = gameState.getQuestionTimer();
        QuizSettings s = QuizLoader.getSettings();

        if (s.getTimePerQuestion() > 0 && remaining >= 0f) {
            int secs = (int) Math.ceil(remaining);
            timerText.setText("TIME: " + secs + "s");
            if (secs <= 4) {
                timerText.setColor(new ColorRGBA(1f, 0.2f, 0.2f, 1f));
            } else if (secs <= 8) {
                timerText.setColor(new ColorRGBA(1f, 0.85f, 0.2f, 1f));
            } else {
                timerText.setColor(new ColorRGBA(0.2f, 1f, 0.4f, 1f));
            }
            float tw = timerText.getLineWidth();
            timerText.setLocalTranslation((screenW - tw) / 2f, screenH - 72f, 0f);
            timerText.setCullHint(Spatial.CullHint.Inherit);
        } else {
            timerText.setText("");
            timerText.setCullHint(Spatial.CullHint.Always);
        }
    }

    private void updateLives() {
        int maxWrong = gameState.getMaxWrong();
        int wrongHits = gameState.getWrongHits();

        if (maxWrong == -1) {
            livesText.setText("LIVES: INF");
            livesText.setColor(new ColorRGBA(0.4f, 0.8f, 1f, 1f));
        } else {
            int livesLeft = Math.max(0, maxWrong - wrongHits);
            StringBuilder sb = new StringBuilder("LIVES: ");
            for (int i = 0; i < maxWrong; i++) {
                if (i < livesLeft) {
                    sb.append("[+] ");
                } else {
                    sb.append("[-] ");
                }
            }
            sb.append("(").append(livesLeft).append("/").append(maxWrong).append(")");
            livesText.setText(sb.toString());

            if (livesLeft <= 0) {
                livesText.setColor(new ColorRGBA(1f, 0.25f, 0.25f, 1f));
            } else if (livesLeft == 1) {
                livesText.setColor(new ColorRGBA(1f, 0.85f, 0.2f, 1f));
            } else {
                livesText.setColor(new ColorRGBA(0.2f, 0.95f, 0.35f, 1f));
            }
        }
    }

    private void showGameOver() {
        questionText.setText("");
        timerText.setText("");
        controlsHintText.setText("");

        if (gameState.isQuizCompleted()) {
            gameOverText.setText("QUIZ COMPLETED!");
            gameOverText.setColor(new ColorRGBA(0.2f, 1.0f, 0.4f, 1f));
            gameOverScoreText.setText("FINAL SCORE: " + gameState.getScore() + " / " + gameState.getMaxScore());
            gameOverScoreText.setColor(new ColorRGBA(1.0f, 0.9f, 0.2f, 1f));
            gameOverSubText.setText(String.format("Accuracy: %d/%d  •  Flight time: %.1fs",
                    gameState.getCorrectAnswers(), gameState.getTotalQuestions(), gameState.getTotalFlightTime()));
        } else {
            gameOverText.setText("PLANE BURST!");
            gameOverText.setColor(new ColorRGBA(1f, 0.2f, 0.15f, 1f));
            gameOverScoreText.setText("FINAL SCORE: " + gameState.getScore());
            gameOverScoreText.setColor(ColorRGBA.White);
            gameOverSubText.setText(gameState.getMaxWrong() + " collisions sustained. Plane destroyed!");
        }

        gameOverPilotText.setText("PILOT CALLSIGN: " + gameState.getPlayerName().toUpperCase());

        gameOverDbText.setText("✔ Attempt & score recorded to database (aeroquiz.db) via JDBC");

        // Load leaderboard from local database
        if (!gameOverDbLoaded) {
            gameOverDbLoaded = true;
            List<QuizDatabase.ScoreRecord> tops = null;
            QuizDatabase db = (app != null) ? app.getQuizDatabase() : null;
            if (db != null) {
                tops = db.getTopScores(5);
            }

            if (tops != null && !tops.isEmpty()) {
                StringBuilder sb = new StringBuilder("— LEADERBOARD (JDBC DATABASE) —\n");
                for (int i = 0; i < tops.size(); i++) {
                    QuizDatabase.ScoreRecord rec = tops.get(i);
                    String dateStr = (rec.playedAt() != null && rec.playedAt().length() >= 10)
                            ? rec.playedAt().substring(0, 10) : "";
                    sb.append(String.format(" #%d  %-14s %3d pts (%d/%d)  %s\n",
                            i + 1, rec.playerName(), rec.score(),
                            rec.correctAnswers(), rec.totalQuestions(), dateStr));
                }
                gameOverLeaderboardText.setText(sb.toString());
            } else {
                gameOverLeaderboardText.setText("— LEADERBOARD —\nRecorded attempt for " + gameState.getPlayerName());
            }
        }

        float titleW = gameOverText.getLineWidth();
        gameOverText.setLocalTranslation((screenW - titleW) / 2f, screenH / 2f + 160f, 0f);
        gameOverText.setCullHint(Spatial.CullHint.Inherit);

        float pilotW = gameOverPilotText.getLineWidth();
        gameOverPilotText.setLocalTranslation((screenW - pilotW) / 2f, screenH / 2f + 110f, 0f);
        gameOverPilotText.setCullHint(Spatial.CullHint.Inherit);

        float scoreW = gameOverScoreText.getLineWidth();
        gameOverScoreText.setLocalTranslation((screenW - scoreW) / 2f, screenH / 2f + 65f, 0f);
        gameOverScoreText.setCullHint(Spatial.CullHint.Inherit);

        float subW = gameOverSubText.getLineWidth();
        gameOverSubText.setLocalTranslation((screenW - subW) / 2f, screenH / 2f + 15f, 0f);
        gameOverSubText.setCullHint(Spatial.CullHint.Inherit);

        float dbW = gameOverDbText.getLineWidth();
        gameOverDbText.setLocalTranslation((screenW - dbW) / 2f, screenH / 2f - 20f, 0f);
        gameOverDbText.setCullHint(Spatial.CullHint.Inherit);

        float lbW = gameOverLeaderboardText.getLineWidth();
        gameOverLeaderboardText.setLocalTranslation((screenW - lbW) / 2f, screenH / 2f - 55f, 0f);
        gameOverLeaderboardText.setCullHint(Spatial.CullHint.Inherit);

        // Dedicated Touch Buttons positioning
        float btnW = 260f;
        float btnH = 50f;
        float btnGap = 30f;
        float totalBtnsW = btnW * 2f + btnGap;
        float retryBtnX = (screenW - totalBtnsW) / 2f;
        float termBtnX = retryBtnX + btnW + btnGap;
        float btnY = Math.max(25f, screenH / 2f - 240f);

        gameOverRetryBg.setLocalTranslation(retryBtnX, btnY, 0f);
        gameOverRetryBorder.setLocalTranslation(retryBtnX, btnY + btnH - 3f, 0.1f);
        float rw = gameOverRetryText.getLineWidth();
        float rh = gameOverRetryText.getLineHeight();
        gameOverRetryText.setLocalTranslation(retryBtnX + (btnW - rw) / 2f, btnY + (btnH + rh) / 2f - 2f, 0.2f);

        gameOverTerminalBg.setLocalTranslation(termBtnX, btnY, 0f);
        gameOverTerminalBorder.setLocalTranslation(termBtnX, btnY + btnH - 3f, 0.1f);
        float tw = gameOverTerminalText.getLineWidth();
        float th = gameOverTerminalText.getLineHeight();
        gameOverTerminalText.setLocalTranslation(termBtnX + (btnW - tw) / 2f, btnY + (btnH + th) / 2f - 2f, 0.2f);

        gameOverButtonsNode.setCullHint(Spatial.CullHint.Inherit);
    }

    private Geometry makeQuad(String name, float w, float h, float x, float y, ColorRGBA color) {
        Quad mesh = new Quad(w, h);
        Geometry geo = new Geometry(name, mesh);
        geo.setLocalTranslation(x, y, 0f);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geo.setQueueBucket(RenderQueue.Bucket.Transparent);
        geo.setMaterial(mat);
        return geo;
    }

    // -------------------------------------------------------------------------
    // Touch & Mouse click event handler
    // -------------------------------------------------------------------------

    private void handleTouchOrClick(float x, float y) {
        if (gameState == null) return;
        if (inputCooldown > 0f) return;

        if (gameState.getPhase() == GameState.Phase.NAME_INPUT) {
            handleTerminalTouch(x, y);
            return;

        } else if (gameState.getPhase() == GameState.Phase.PLAYING) {
            QuizQuestion q = gameState.getCurrentQuestion();
            if (q == null) return;

            int count = q.getOptionCount();
            if (count < 2) count = 2;
            if (count > 6) count = 6;

            float totalBarW = Math.min(screenW - 40f, 1000f);
            float btnMargin = 12f;
            float btnW = (totalBarW - btnMargin * (count - 1)) / count;
            float btnH = 62f;
            float btnY = 16f;
            float startX = (screenW - totalBarW) / 2f;

            // 1. Check if tapped one of the dedicated bottom thumb buttons
            if (y >= btnY && y <= btnY + btnH) {
                for (int i = 0; i < count; i++) {
                    float bx = startX + i * (btnW + btnMargin);
                    if (x >= bx && x <= bx + btnW) {
                        inputCooldown = 0.4f;
                        gameState.handleAnswer(i);
                        return;
                    }
                }
            }

            // 2. Check 3D raycast picking directly on obstacle box in sky
            ObstacleSpawner spawner = (app != null && app.getStateManager() != null)
                    ? app.getStateManager().getState(ObstacleSpawner.class) : null;
            if (spawner != null) {
                String colorKey = spawner.pickObstacleColorAt(x, y);
                if (colorKey != null) {
                    inputCooldown = 0.4f;
                    gameState.handleAnswerColor(colorKey);
                    return;
                }
            }

            // Off-target taps do nothing

        } else if (gameState.isGameOver()) {
            float btnW = 260f;
            float btnH = 50f;
            float btnGap = 30f;
            float totalBtnsW = btnW * 2f + btnGap;
            float retryBtnX = (screenW - totalBtnsW) / 2f;
            float termBtnX = retryBtnX + btnW + btnGap;
            float btnY = Math.max(25f, screenH / 2f - 240f);

            if (y >= btnY && y <= btnY + btnH) {
                if (x >= retryBtnX && x <= retryBtnX + btnW) {
                    // [ ↺ RETRY FLIGHT ]
                    inputCooldown = 0.5f;
                    gameOverDbLoaded = false;
                    regLeaderboardLoaded = false;
                    gameState.restartCurrentGame();
                } else if (x >= termBtnX && x <= termBtnX + btnW) {
                    // [ ⚙ PILOT TERMINAL ]
                    inputCooldown = 0.5f;
                    gameOverDbLoaded = false;
                    regLeaderboardLoaded = false;
                    gameState.resetToRegistration();
                }
            }
        }
    }

    private void handleTerminalTouch(float x, float y) {
        if (currentTerminalScreen == TerminalScreen.PILOT_TERMINAL) {
            float pw = 780f;
            float ph = 520f;
            float px = (screenW - pw) / 2f;
            float py = (screenH - ph) / 2f;
            float lx = x - px;
            float ly = y - py;

            // [ < PREV ] callsign: x [485, 585], y [ph - 138, ph - 88]
            if (lx >= 485f && lx <= 585f && ly >= ph - 138f && ly <= ph - 88f) {
                callsignIndex = (callsignIndex - 1 + CALLSIGN_PRESETS.length) % CALLSIGN_PRESETS.length;
                nameBuffer.setLength(0);
                nameBuffer.append(CALLSIGN_PRESETS[callsignIndex]);
                return;
            }

            // [ NEXT > ] callsign: x [595, 695], y [ph - 138, ph - 88]
            if (lx >= 595f && lx <= 695f && ly >= ph - 138f && ly <= ph - 88f) {
                callsignIndex = (callsignIndex + 1) % CALLSIGN_PRESETS.length;
                nameBuffer.setLength(0);
                nameBuffer.append(CALLSIGN_PRESETS[callsignIndex]);
                return;
            }

            // Aircraft Prev: x [50, 100], y [ph - 176, ph - 136]
            if (lx >= 50f && lx <= 100f && ly >= ph - 176f && ly <= ph - 136f) {
                cycleAircraft(-1);
                return;
            }

            // Aircraft Next: x [645, 695], y [ph - 176, ph - 136]
            if (lx >= 645f && lx <= 695f && ly >= ph - 176f && ly <= ph - 136f) {
                cycleAircraft(1);
                return;
            }

            // Aircraft Display Box (tap anywhere to cycle): x [108, 637], y [ph - 176, ph - 136]
            if (lx >= 108f && lx <= 637f && ly >= ph - 176f && ly <= ph - 136f) {
                cycleAircraft(1);
                return;
            }

            // [ CLEAR ]: x [50, 160], y [ph - 232, ph - 190]
            if (lx >= 50f && lx <= 160f && ly >= ph - 232f && ly <= ph - 190f) {
                nameBuffer.setLength(0);
                return;
            }

            // [ ► START FLIGHT ◄ ]: x [180, 460], y [ph - 232, ph - 190]
            if (lx >= 180f && lx <= 460f && ly >= ph - 232f && ly <= ph - 190f) {
                launchSoloFlight();
                return;
            }

            // [ ✏ EDIT QUIZ & SETTINGS ]: x [480, 745], y [ph - 232, ph - 190]
            if (lx >= 480f && lx <= 745f && ly >= ph - 232f && ly <= ph - 190f) {
                inputCooldown = 0.3f;
                currentTerminalScreen = TerminalScreen.QUIZ_EDITOR;
                editorQuestions = new ArrayList<>(QuizLoader.load("questions.txt"));
                editorSettings = QuizLoader.getSettings();
                editorStatus = "Loaded " + editorQuestions.size() + " question(s) from catalog.";
                return;
            }



        } else if (currentTerminalScreen == TerminalScreen.QUIZ_EDITOR) {
            float pw = 840f;
            float ph = 560f;
            float px = (screenW - pw) / 2f;
            float py = (screenH - ph) / 2f;
            float lx = x - px;
            float ly = y - py;

            // Terrain Prev/Next: x [185, 215] / [220, 250], y [ph - 113, ph - 87]
            if (lx >= 185f && lx <= 215f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                terrainPresetIndex = (terrainPresetIndex - 1 + TERRAIN_PRESETS.length) % TERRAIN_PRESETS.length;
                return;
            }
            if (lx >= 220f && lx <= 250f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                terrainPresetIndex = (terrainPresetIndex + 1) % TERRAIN_PRESETS.length;
                return;
            }

            // Lives Prev/Next: x [355, 385] / [390, 420], y [ph - 113, ph - 87]
            if (lx >= 355f && lx <= 385f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                livesPresetIndex = (livesPresetIndex - 1 + LIVES_PRESETS.length) % LIVES_PRESETS.length;
                return;
            }
            if (lx >= 390f && lx <= 420f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                livesPresetIndex = (livesPresetIndex + 1) % LIVES_PRESETS.length;
                return;
            }

            // Timer Prev/Next: x [520, 550] / [555, 585], y [ph - 113, ph - 87]
            if (lx >= 520f && lx <= 550f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                timerPresetIndex = (timerPresetIndex - 1 + TIMER_PRESETS.length) % TIMER_PRESETS.length;
                return;
            }
            if (lx >= 555f && lx <= 585f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                timerPresetIndex = (timerPresetIndex + 1) % TIMER_PRESETS.length;
                return;
            }

            // Aircraft Prev/Next: x [735, 765] / [770, 800], y [ph - 113, ph - 87]
            if (lx >= 735f && lx <= 765f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                cycleAircraft(-1);
                return;
            }
            if (lx >= 770f && lx <= 800f && ly >= ph - 113f && ly <= ph - 87f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                cycleAircraft(1);
                return;
            }

            // Question Prev Q: x [50, 145], y [ph - 192, ph - 162]
            if (lx >= 50f && lx <= 145f && ly >= ph - 192f && ly <= ph - 162f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                if (!editorQuestions.isEmpty()) {
                    editorQuestionIndex = (editorQuestionIndex - 1 + editorQuestions.size()) % editorQuestions.size();
                }
                return;
            }
            // Question Next Q: x [155, 250], y [ph - 192, ph - 162]
            if (lx >= 155f && lx <= 250f && ly >= ph - 192f && ly <= ph - 162f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                if (!editorQuestions.isEmpty()) {
                    editorQuestionIndex = (editorQuestionIndex + 1) % editorQuestions.size();
                }
                return;
            }
            // + NEW Q: x [260, 355], y [ph - 192, ph - 162]
            if (lx >= 260f && lx <= 355f && ly >= ph - 192f && ly <= ph - 162f) {
                applyEditorTextBuffer();
                Map<String, String> opts = new LinkedHashMap<>();
                opts.put("RED", "Alpha Option");
                opts.put("BLUE", "Beta Option");
                opts.put("GREEN", "Gamma Option");
                opts.put("YELLOW", "Delta Option");
                editorQuestions.add(new QuizQuestion("Custom Flight Question #" + (editorQuestions.size() + 1), opts, "RED"));
                editorQuestionIndex = editorQuestions.size() - 1;
                activeEditField = EditorField.QUESTION_PROMPT;
                editorTextBuffer.setLength(0);
                editorTextBuffer.append(editorQuestions.get(editorQuestionIndex).getQuestion());
                editorStatus = "Added Question #" + (editorQuestionIndex + 1) + ". Edit prompt & options.";
                return;
            }
            // ✕ DELETE: x [365, 460], y [ph - 192, ph - 162]
            if (lx >= 365f && lx <= 460f && ly >= ph - 192f && ly <= ph - 162f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                if (editorQuestions.size() > 1) {
                    editorQuestions.remove(editorQuestionIndex);
                    if (editorQuestionIndex >= editorQuestions.size()) editorQuestionIndex = editorQuestions.size() - 1;
                    editorStatus = "Deleted question. " + editorQuestions.size() + " remaining.";
                } else {
                    editorStatus = "Cannot delete: minimum 1 question required.";
                }
                return;
            }

            // Question Prompt Row: x [50, 795], y [ph - 238, ph - 206]
            if (lx >= 50f && lx <= 795f && ly >= ph - 238f && ly <= ph - 206f) {
                if (!editorQuestions.isEmpty()) {
                    if (activeEditField == EditorField.QUESTION_PROMPT) {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.NONE;
                        editorStatus = "Question prompt saved.";
                    } else {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.QUESTION_PROMPT;
                        editorTextBuffer.setLength(0);
                        editorTextBuffer.append(editorQuestions.get(editorQuestionIndex).getQuestion());
                        editorStatus = "Editing Question Prompt. Type or tap buttons.";
                    }
                }
                return;
            }

            // Option 1 (RED) Row: x [50, 795], y [ph - 276, ph - 246]
            if (lx >= 50f && lx <= 795f && ly >= ph - 276f && ly <= ph - 246f) {
                if (!editorQuestions.isEmpty()) {
                    if (activeEditField == EditorField.OPT_RED) {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.NONE;
                        editorStatus = "RED option saved.";
                    } else {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.OPT_RED;
                        editorTextBuffer.setLength(0);
                        editorTextBuffer.append(editorQuestions.get(editorQuestionIndex).getOption("RED"));
                        editorStatus = "Editing RED option. Type or tap buttons.";
                    }
                }
                return;
            }

            // Option 2 (BLUE) Row: x [50, 795], y [ph - 312, ph - 282]
            if (lx >= 50f && lx <= 795f && ly >= ph - 312f && ly <= ph - 282f) {
                if (!editorQuestions.isEmpty()) {
                    if (activeEditField == EditorField.OPT_BLUE) {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.NONE;
                        editorStatus = "BLUE option saved.";
                    } else {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.OPT_BLUE;
                        editorTextBuffer.setLength(0);
                        editorTextBuffer.append(editorQuestions.get(editorQuestionIndex).getOption("BLUE"));
                        editorStatus = "Editing BLUE option. Type or tap buttons.";
                    }
                }
                return;
            }

            // Option 3 (GREEN) Row: x [50, 795], y [ph - 348, ph - 318]
            if (lx >= 50f && lx <= 795f && ly >= ph - 348f && ly <= ph - 318f) {
                if (!editorQuestions.isEmpty()) {
                    if (activeEditField == EditorField.OPT_GREEN) {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.NONE;
                        editorStatus = "GREEN option saved.";
                    } else {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.OPT_GREEN;
                        editorTextBuffer.setLength(0);
                        editorTextBuffer.append(editorQuestions.get(editorQuestionIndex).getOption("GREEN"));
                        editorStatus = "Editing GREEN option. Type or tap buttons.";
                    }
                }
                return;
            }

            // Option 4 (YELLOW) Row: x [50, 795], y [ph - 384, ph - 354]
            if (lx >= 50f && lx <= 795f && ly >= ph - 384f && ly <= ph - 354f) {
                if (!editorQuestions.isEmpty()) {
                    if (activeEditField == EditorField.OPT_YELLOW) {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.NONE;
                        editorStatus = "YELLOW option saved.";
                    } else {
                        applyEditorTextBuffer();
                        activeEditField = EditorField.OPT_YELLOW;
                        editorTextBuffer.setLength(0);
                        editorTextBuffer.append(editorQuestions.get(editorQuestionIndex).getOption("YELLOW"));
                        editorStatus = "Editing YELLOW option. Type or tap buttons.";
                    }
                }
                return;
            }

            // ★ CYCLE CORRECT: x [260, 420], y [ph - 430, ph - 396]
            if (lx >= 260f && lx <= 420f && ly >= ph - 430f && ly <= ph - 396f) {
                applyEditorTextBuffer();
                if (!editorQuestions.isEmpty()) {
                    QuizQuestion cur = editorQuestions.get(editorQuestionIndex);
                    List<String> keys = cur.getOptionKeys();
                    int curIdx = keys.indexOf(cur.getCorrectColor());
                    String nextKey = keys.get((curIdx + 1) % keys.size());
                    editorQuestions.set(editorQuestionIndex, new QuizQuestion(cur.getQuestion(), cur.getOptions(), nextKey));
                    editorStatus = "Set correct option to [" + nextKey + "]";
                }
                return;
            }

            // [ ⌫ DEL ]: x [440, 515], y [ph - 430, ph - 396]
            if (lx >= 440f && lx <= 515f && ly >= ph - 430f && ly <= ph - 396f) {
                if (activeEditField != EditorField.NONE && editorTextBuffer.length() > 0) {
                    editorTextBuffer.deleteCharAt(editorTextBuffer.length() - 1);
                    applyEditorTextBuffer();
                }
                return;
            }

            // [ CLEAR ]: x [525, 605], y [ph - 430, ph - 396]
            if (lx >= 525f && lx <= 605f && ly >= ph - 430f && ly <= ph - 396f) {
                if (activeEditField != EditorField.NONE) {
                    editorTextBuffer.setLength(0);
                    applyEditorTextBuffer();
                }
                return;
            }

            // [ ✔ DONE ]: x [615, 700], y [ph - 430, ph - 396]
            if (lx >= 615f && lx <= 700f && ly >= ph - 430f && ly <= ph - 396f) {
                if (activeEditField != EditorField.NONE) {
                    applyEditorTextBuffer();
                    activeEditField = EditorField.NONE;
                    editorStatus = "Field saved. Tap another field or [SAVE QUIZ & SETTINGS].";
                }
                return;
            }

            // [ ↵ NEXT ]: x [710, 795], y [ph - 430, ph - 396]
            if (lx >= 710f && lx <= 795f && ly >= ph - 430f && ly <= ph - 396f) {
                cycleNextEditField();
                return;
            }

            // 💾 SAVE QUIZ & SETTINGS: x [50, 340], y [25, 71]
            if (lx >= 50f && lx <= 340f && ly >= 25f && ly <= 71f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                editorSettings.setTerrain(TERRAIN_PRESETS[terrainPresetIndex]);
                editorSettings.setMaxWrong(LIVES_PRESETS[livesPresetIndex]);
                editorSettings.setTimePerQuestion(TIMER_PRESETS[timerPresetIndex]);
                editorSettings.setAircraft(AIRCRAFT_PRESETS[aircraftPresetIndex]);

                boolean saved = QuizLoader.saveQuestionsAndSettings(editorQuestions, editorSettings);
                if (app != null) {
                    app.updateGroundTerrain();
                    PlaneController pc = app.getStateManager().getState(PlaneController.class);
                    if (pc != null) {
                        pc.switchAircraft(AIRCRAFT_PRESETS[aircraftPresetIndex]);
                    }
                    QuizDatabase db = app.getQuizDatabase();
                    if (db != null) {
                        db.saveCustomQuiz("Custom AeroQuiz",
                                QuizLoader.serializeSettings(editorSettings),
                                QuizLoader.serializeQuestions(editorQuestions));
                    }
                }
                editorStatus = saved ? "✔ Quiz & Settings saved to disk & database!" : "Error saving to disk.";
                return;
            }

            // ◄ BACK TO TERMINAL: x [360, 620], y [25, 71]
            if (lx >= 360f && lx <= 620f && ly >= 25f && ly <= 71f) {
                applyEditorTextBuffer();
                activeEditField = EditorField.NONE;
                inputCooldown = 0.3f;
                currentTerminalScreen = TerminalScreen.PILOT_TERMINAL;
                return;
            }
        }
    }

    private void cycleAircraft(int dir) {
        aircraftPresetIndex = (aircraftPresetIndex + dir + AIRCRAFT_PRESETS.length) % AIRCRAFT_PRESETS.length;
        QuizSettings.AircraftType chosen = AIRCRAFT_PRESETS[aircraftPresetIndex];
        if (QuizLoader.getSettings() != null) {
            QuizLoader.getSettings().setAircraft(chosen);
            QuizLoader.saveQuestionsAndSettings(null, QuizLoader.getSettings());
        }
        if (editorSettings != null) {
            editorSettings.setAircraft(chosen);
        }
        PlaneController pc = (app != null) ? app.getStateManager().getState(PlaneController.class) : null;
        if (pc != null) {
            pc.switchAircraft(chosen);
        }
        updateAircraftUiText();
    }

    private void updateAircraftUiText() {
        if (aircraftPresetIndex < 0 || aircraftPresetIndex >= AIRCRAFT_PRESETS.length) {
            aircraftPresetIndex = 0;
        }
        QuizSettings.AircraftType chosen = AIRCRAFT_PRESETS[aircraftPresetIndex];
        if (regAircraftText != null) {
            String title = switch (chosen) {
                case HELICOPTER -> "[ 🚁 ATTACK HELI ]";
                case FLYING_CAR -> "[ 🏎 PAGANI AERO-CAR ]";
                default         -> "[ ✈ STUNT BIPLANE ]";
            };
            regAircraftText.setText(title + " — " + chosen.getDescription());
        }
        if (edAircraftText != null) {
            String airLabel = switch (chosen) {
                case HELICOPTER -> "HELI";
                case FLYING_CAR -> "CAR";
                default         -> "PLANE";
            };
            edAircraftText.setText("VEHICLE: [ " + airLabel + " ]");
        }
    }

    private void launchSoloFlight() {
        String name = nameBuffer.toString().trim();
        if (name.isEmpty()) name = "Pilot";
        inputCooldown = 0.5f;
        gameState.startQuizWithPlayerName(name);
        if (nameInputNode != null) nameInputNode.setCullHint(Spatial.CullHint.Always);
    }

    private final RawInputListener rawInputListener = new RawInputListener() {
        private long lastTouchTime = 0;

        @Override
        public void onKeyEvent(KeyInputEvent evt) {
            if (!evt.isPressed()) return;
            if (gameState == null) return;
            if (inputCooldown > 0f) return;

            if (gameState.getPhase() == GameState.Phase.NAME_INPUT) {
                if (currentTerminalScreen == TerminalScreen.PILOT_TERMINAL) {
                    int code = evt.getKeyCode();
                    char c = evt.getKeyChar();

                    if (code == KeyInput.KEY_RETURN || code == KeyInput.KEY_NUMPADENTER) {
                        launchSoloFlight();
                    } else if (code == KeyInput.KEY_V || code == KeyInput.KEY_TAB) {
                        cycleAircraft(1);
                    } else if (code == KeyInput.KEY_BACK) {
                        if (nameBuffer.length() > 0) {
                            nameBuffer.deleteCharAt(nameBuffer.length() - 1);
                        }
                    } else if (nameBuffer.length() < 16 && (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ')) {
                        nameBuffer.append(c);
                    }
                } else if (currentTerminalScreen == TerminalScreen.QUIZ_EDITOR) {
                    int code = evt.getKeyCode();
                    char c = evt.getKeyChar();

                    if (activeEditField != EditorField.NONE) {
                        if (code == KeyInput.KEY_RETURN || code == KeyInput.KEY_NUMPADENTER) {
                            applyEditorTextBuffer();
                            activeEditField = EditorField.NONE;
                            editorStatus = "Field saved. Tap another field or [SAVE QUIZ & SETTINGS].";
                        } else if (code == KeyInput.KEY_ESCAPE) {
                            activeEditField = EditorField.NONE;
                            editorStatus = "Edit cancelled.";
                        } else if (code == KeyInput.KEY_BACK) {
                            if (editorTextBuffer.length() > 0) {
                                editorTextBuffer.deleteCharAt(editorTextBuffer.length() - 1);
                                applyEditorTextBuffer();
                            }
                        } else if (code == KeyInput.KEY_TAB) {
                            cycleNextEditField();
                        } else if (editorTextBuffer.length() < 70 && (Character.isLetterOrDigit(c) || c == ' ' || "?!.,-':;/()#%&*+=@\"[]{}".indexOf(c) != -1)) {
                            editorTextBuffer.append(c);
                            applyEditorTextBuffer();
                        }
                    } else {
                        if (code == KeyInput.KEY_LEFT) {
                            if (!editorQuestions.isEmpty()) {
                                editorQuestionIndex = (editorQuestionIndex - 1 + editorQuestions.size()) % editorQuestions.size();
                            }
                        } else if (code == KeyInput.KEY_RIGHT) {
                            if (!editorQuestions.isEmpty()) {
                                editorQuestionIndex = (editorQuestionIndex + 1) % editorQuestions.size();
                            }
                        } else if (code == KeyInput.KEY_ESCAPE) {
                            currentTerminalScreen = TerminalScreen.PILOT_TERMINAL;
                        }
                    }
                }
            } else if (gameState.isGameOver()) {
                int code = evt.getKeyCode();
                if (code == KeyInput.KEY_RETURN || code == KeyInput.KEY_SPACE) {
                    inputCooldown = 0.5f;
                    gameOverDbLoaded = false;
                    regLeaderboardLoaded = false;
                    gameState.resetToRegistration();
                } else if (code == KeyInput.KEY_R) {
                    inputCooldown = 0.5f;
                    gameOverDbLoaded = false;
                    regLeaderboardLoaded = false;
                    gameState.restartCurrentGame();
                }
            }
        }

        @Override public void beginInput() {}
        @Override public void endInput() {}
        @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
        @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
        @Override public void onMouseMotionEvent(MouseMotionEvent evt) {}

        @Override
        public void onMouseButtonEvent(MouseButtonEvent evt) {
            long now = System.currentTimeMillis();
            if (now - lastTouchTime < 250) return;
            if (evt.isPressed() && evt.getButtonIndex() == MouseInput.BUTTON_LEFT) {
                handleTouchOrClick(evt.getX(), evt.getY());
            }
        }

        @Override
        public void onTouchEvent(TouchEvent evt) {
            if (evt.getType() == TouchEvent.Type.DOWN) {
                lastTouchTime = System.currentTimeMillis();
                handleTouchOrClick(evt.getX(), evt.getY());
            }
        }
    };
}
