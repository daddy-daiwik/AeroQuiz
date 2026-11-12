package com.daddydaiwik.aeroquiz.game;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Central coordinator and state machine for AeroQuiz.
 * Handles inputs (Keys 1-6), question timer countdown, wrong hits count,
 * and game completion/game over conditions.
 */
public class GameState extends AbstractAppState {

    private static final Logger LOG = Logger.getLogger(GameState.class.getName());

    public enum Phase {
        LOADING,
        NAME_INPUT,
        PLAYING,
        QUESTION_COOLDOWN,
        CRASHING,
        GAME_OVER
    }

    private static final int CORRECT_SCORE_INC = 10;
    private static final float CRASH_SEQUENCE_DURATION = 2.8f;

    /** Key action names registered with input manager (1 to 6). */
    private static final String[] ACTION_NAMES = {
        "Answer1", "Answer2", "Answer3", "Answer4", "Answer5", "Answer6"
    };

    private static final int[] KEY_CODES = {
        KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3,
        KeyInput.KEY_4, KeyInput.KEY_5, KeyInput.KEY_6
    };

    private AeroQuiz app;

    public GameState() {}

    public GameState(AeroQuiz app) {
        this.app = app;
    }

    private Phase   phase = Phase.LOADING;
    private int     score = 0;
    private int     wrongHits = 0;
    private int     correctAnswers = 0;
    private String  playerName = "Pilot";
    private float   totalFlightTime = 0f;
    private boolean scoreSaved = false;

    private List<QuizQuestion> questions;
    private List<QuizQuestion> masterQuestions = new ArrayList<>();
    private int currentIndex = 0;

    // Per-question timer
    private float questionTimer = -1f;

    private boolean quizCompleted = false;

    private List<QuizQuestion> pendingQuestions;

    public void initGame(List<QuizQuestion> questions) {
        this.masterQuestions = new ArrayList<>(questions);
        this.pendingQuestions = new ArrayList<>(questions);
    }

    @Override
    public void initialize(AppStateManager stateManager, Application application) {
        super.initialize(stateManager, application);
        this.app = (AeroQuiz) application;

        score     = 0;
        wrongHits = 0;
        correctAnswers = 0;
        totalFlightTime = 0f;
        scoreSaved = false;

        registerKeys();
    }

    private float cooldownSafetyTimer = 0f;
    private float delayedCompletionTimer = 0f;
    private float delayedCrashTimer = 0f;

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // Delayed crash sequence: let player watch the full 3D plane crash and fall animation
        if (delayedCrashTimer > 0f) {
            delayedCrashTimer -= tpf;
            if (delayedCrashTimer <= 0f) {
                delayedCrashTimer = 0f;
                triggerGameOver();
            }
            return;
        }

        // Delayed victory completion after last question bullet impacts and explodes
        if (delayedCompletionTimer > 0f) {
            delayedCompletionTimer -= tpf;
            if (delayedCompletionTimer <= 0f) {
                delayedCompletionTimer = 0f;
                triggerQuizCompleted();
            }
            return;
        }

        // Transition to pilot name input on first frame once states are initialized
        if (phase == Phase.LOADING) {
            phase = Phase.NAME_INPUT;
            return;
        }

        if (phase == Phase.PLAYING) {
            totalFlightTime += tpf;
        }

        // Check per-question timer
        QuizSettings settings = QuizLoader.getSettings();
        if (phase == Phase.PLAYING && settings.getTimePerQuestion() > 0) {
            questionTimer -= tpf;
            if (questionTimer <= 0f) {
                LOG.info("Question timed out (" + settings.getTimePerQuestion() + "s limit elapsed)!");
                autoSkipQuestion();
                ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
                if (spawner != null) {
                    spawner.scheduleNextSpawn();
                }
            }
        } else if (phase == Phase.QUESTION_COOLDOWN) {
            cooldownSafetyTimer += tpf;
            if (cooldownSafetyTimer >= 3.5f) {
                LOG.warning("GameState: cooldownSafetyTimer triggered — recovering and spawning next question!");
                cooldownSafetyTimer = 0f;
                ObstacleSpawner spawner = (app != null && app.getStateManager() != null)
                        ? app.getStateManager().getState(ObstacleSpawner.class) : null;
                if (spawner != null) {
                    spawner.removeAllObstacles();
                    spawner.spawnObstacles();
                }
            }
        } else {
            cooldownSafetyTimer = 0f;
        }
    }

    @Override
    public void cleanup() {
        unregisterKeys();
        super.cleanup();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void startGame(List<QuizQuestion> questionsList) {
        if (questionsList != null && !questionsList.isEmpty()) {
            this.questions = new ArrayList<>(questionsList);
        } else if (masterQuestions != null && !masterQuestions.isEmpty()) {
            this.questions = new ArrayList<>(masterQuestions);
        }
        if (this.questions != null) {
            Collections.shuffle(this.questions);
        }
        currentIndex   = 0;
        wrongHits      = 0;
        correctAnswers = 0;
        score          = 0;
        scoreSaved     = false;
        quizCompleted  = false;
        totalFlightTime = 0f;
        phase          = Phase.PLAYING;
        cooldownSafetyTimer = 0f;
        delayedCrashTimer = 0f;
        resetTimer();

        // Reset plane transform, FX, camera, and death state
        PlaneController plane = (app != null && app.getStateManager() != null)
                ? app.getStateManager().getState(PlaneController.class) : null;
        if (plane != null) {
            plane.resetPlane();
        }

        // Reset and spawn obstacle grid
        ObstacleSpawner spawner = (app != null && app.getStateManager() != null)
                ? app.getStateManager().getState(ObstacleSpawner.class) : null;
        if (spawner != null) {
            spawner.reset();
            spawner.spawnObstacles();
        }
        LOG.info("Game started for pilot '" + playerName + "' with " + (this.questions != null ? this.questions.size() : 0) + " questions.");
    }

    public void startQuizWithPlayerName(String name) {
        this.playerName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Pilot";
        LOG.info("Pilot registered: '" + this.playerName + "' — launching flight!");
        if (pendingQuestions != null && !pendingQuestions.isEmpty()) {
            startGame(pendingQuestions);
            pendingQuestions = null;
        } else if (questions != null && !questions.isEmpty()) {
            startGame(questions);
        } else if (masterQuestions != null && !masterQuestions.isEmpty()) {
            startGame(masterQuestions);
        }
        if (app != null && app.getAudioFX() != null) {
            app.getAudioFX().startMusic();
        }
    }

    /**
     * Resets flight environment, plane, and scores, then returns to the Pilot Registration screen.
     */
    public void resetToRegistration() {
        QuizLoader.loadSettings();
        if (app != null) {
            app.updateGroundTerrain();
        }

        PlaneController plane = (app != null && app.getStateManager() != null)
                ? app.getStateManager().getState(PlaneController.class) : null;
        if (plane != null) {
            plane.resetPlane();
        }

        ObstacleSpawner spawner = (app != null && app.getStateManager() != null)
                ? app.getStateManager().getState(ObstacleSpawner.class) : null;
        if (spawner != null) {
            spawner.reset();
        }

        if (app != null && app.getAudioFX() != null) {
            app.getAudioFX().stopMusic();
        }

        score = 0;
        wrongHits = 0;
        correctAnswers = 0;
        totalFlightTime = 0f;
        scoreSaved = false;
        quizCompleted = false;
        currentIndex = 0;
        cooldownSafetyTimer = 0f;
        delayedCompletionTimer = 0f;
        delayedCrashTimer = 0f;

        if (masterQuestions != null && !masterQuestions.isEmpty()) {
            questions = new ArrayList<>(masterQuestions);
            Collections.shuffle(questions);
        }

        phase = Phase.NAME_INPUT;
        LOG.info("Game reset: returned to Pilot Registration terminal.");
    }

    /**
     * Instantly restarts flight for the current registered pilot without retyping callsign.
     */
    public void restartCurrentGame() {
        resetToRegistration();
        startQuizWithPlayerName(this.playerName);
    }

    public Phase   getPhase()         { return phase; }
    public int     getScore()         { return score; }
    public int     getWrongHits()     { return wrongHits; }
    public boolean isGameOver()       { return phase == Phase.GAME_OVER; }
    public boolean isCrashing()       { return phase == Phase.CRASHING; }
    public boolean isQuizCompleted()  { return quizCompleted; }
    public float   getQuestionTimer() { return questionTimer; }

    public int getMaxWrong() {
        return QuizLoader.getSettings().getMaxWrong();
    }

    public int getMaxScore() {
        return (questions != null ? questions.size() : 0) * CORRECT_SCORE_INC;
    }

    public int getTotalQuestions() {
        return questions != null ? questions.size() : 0;
    }

    public int getCurrentQuestionNumber() {
        return currentIndex + 1;
    }

    public QuizQuestion getCurrentQuestion() {
        if (phase == Phase.GAME_OVER) return null;
        if (questions == null || currentIndex >= questions.size()) return null;
        return questions.get(currentIndex);
    }

    /**
     * Called when new obstacles have spawned and the player can now answer.
     */
    public void onQuestionSpawned() {
        if (phase != Phase.GAME_OVER) {
            phase = Phase.PLAYING;
            resetTimer();
        }
    }

    public void autoSkipQuestion() {
        if (phase != Phase.PLAYING) return;
        LOG.info("Question skipped without answer.");
        ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
        if (spawner != null) {
            spawner.removeAllObstacles();
        }
        advanceQuestion();
    }

    /**
     * Called by {@link PlaneController} when a ricocheting bullet hits the plane.
     * Increments wrong hits and triggers plane burst/death if limit reached.
     */
    public void registerWrongHit() {
        wrongHits++;
        QuizSettings settings = QuizLoader.getSettings();
        LOG.info("Wrong hit registered! Total=" + wrongHits + " / " + settings.getMaxWrong());

        if (settings.getMaxWrong() != -1 && wrongHits >= settings.getMaxWrong()) {
            LOG.info("Max wrong hits reached (" + settings.getMaxWrong() + ") — starting plane crash sequence!");
            startCrashSequence();
        } else {
            ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
            if (spawner != null) {
                spawner.removeAllObstacles();
            }
            cooldownSafetyTimer = 0f;
            currentIndex++;
            if (currentIndex >= questions.size()) {
                phase = Phase.QUESTION_COOLDOWN;
                delayedCompletionTimer = 0.85f;
            } else {
                resetTimer();
                if (spawner != null) {
                    spawner.scheduleNextSpawn();
                }
            }
        }
    }

    /**
     * Called by {@link PlaneController} when a projectile successfully impacts and destroys the target box.
     */
    public void onCorrectHit() {
        cooldownSafetyTimer = 0f;
        currentIndex++;
        if (currentIndex >= questions.size()) {
            phase = Phase.QUESTION_COOLDOWN;
            delayedCompletionTimer = 0.85f;
            LOG.info("Final question destroyed! Triggering victory in 0.85s...");
        } else {
            resetTimer();
            ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
            if (spawner != null) {
                spawner.scheduleNextSpawn();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Key Handling & Answer Logic
    // -------------------------------------------------------------------------

    private void registerKeys() {
        for (int i = 0; i < ACTION_NAMES.length; i++) {
            app.getInputManager().addMapping(ACTION_NAMES[i], new KeyTrigger(KEY_CODES[i]));
        }
        app.getInputManager().addListener(answerListener, ACTION_NAMES);
    }

    private void unregisterKeys() {
        if (app == null || app.getInputManager() == null) return;
        app.getInputManager().removeListener(answerListener);
    }

    private final ActionListener answerListener = (name, isPressed, tpf) -> {
        if (!isPressed) return;
        if (phase != Phase.PLAYING) return;

        int idx = -1;
        for (int i = 0; i < ACTION_NAMES.length; i++) {
            if (ACTION_NAMES[i].equals(name)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;

        handleAnswer(idx);
    };

    public void handleAnswerColor(String colorKey) {
        if (colorKey == null || phase != Phase.PLAYING) return;
        QuizQuestion q = getCurrentQuestion();
        if (q == null) return;
        for (int i = 0; i < q.getOptionCount(); i++) {
            if (q.getKeyAt(i).equalsIgnoreCase(colorKey)) {
                handleAnswer(i);
                return;
            }
        }
    }

    public void handleAnswer(int optionIndex) {
        QuizQuestion q = getCurrentQuestion();
        if (q == null) return;

        if (optionIndex >= q.getOptionCount()) {
            LOG.fine("Pressed option " + (optionIndex + 1) + " but question only has " + q.getOptionCount());
            return;
        }

        String pressedKey = q.getKeyAt(optionIndex);
        boolean isCorrect = pressedKey.equalsIgnoreCase(q.getCorrectColor());

        ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
        PlaneController plane   = app.getStateManager().getState(PlaneController.class);
        AudioFX         audio   = app.getAudioFX();

        Geometry targetObs = (spawner != null) ? spawner.getObstacleByColor(pressedKey) : null;
        Vector3f targetPos = (spawner != null) ? spawner.getObstacleTarget(pressedKey) : null;

        float targetX = targetPos != null ? targetPos.x : 0f;
        float targetY = targetPos != null ? targetPos.y : 1.8f;

        if (isCorrect) {
            score += CORRECT_SCORE_INC;
            correctAnswers++;
            LOG.info("Correct answer [" + pressedKey + "]! Firing projectile at target box.");

            if (plane != null) {
                plane.shiftThenFire(targetX, targetY, targetObs, true);
            }
            cooldownSafetyTimer = 0f;
            phase = Phase.QUESTION_COOLDOWN; // Wait for projectile to impact target box!
        } else {
            LOG.info("Wrong answer [" + pressedKey + "]! Firing ricochet projectile.");
            if (plane != null) {
                plane.shiftThenFire(targetX, targetY, targetObs, false);
            }
            cooldownSafetyTimer = 0f;
            phase = Phase.QUESTION_COOLDOWN; // Wait for ricochet animation to play
        }
    }

    private void advanceQuestion() {
        phase = Phase.QUESTION_COOLDOWN;
        currentIndex++;
        if (currentIndex >= questions.size()) {
            delayedCompletionTimer = 0.85f;
        } else {
            resetTimer();
        }
    }

    private void resetTimer() {
        QuizSettings settings = QuizLoader.getSettings();
        questionTimer = settings.getTimePerQuestion();
    }

    private void triggerQuizCompleted() {
        phase = Phase.GAME_OVER;
        quizCompleted = true;

        ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
        if (spawner != null) spawner.removeAllObstacles();

        AudioFX audio = app.getAudioFX();
        if (audio != null) audio.stopMusic();

        persistScoreToDatabase();
        LOG.info("QUIZ COMPLETED! Final score: " + score + "/" + getMaxScore());
    }

    private void startCrashSequence() {
        phase = Phase.CRASHING;
        quizCompleted = false;
        delayedCrashTimer = CRASH_SEQUENCE_DURATION;

        PlaneController plane = app.getStateManager().getState(PlaneController.class);
        if (plane != null) plane.triggerDeath();

        ObstacleSpawner spawner = app.getStateManager().getState(ObstacleSpawner.class);
        if (spawner != null) spawner.removeAllObstacles();

        AudioFX audio = app.getAudioFX();
        if (audio != null) audio.stopMusic();

        LOG.info("CRASH SEQUENCE STARTED: Playing full 3D plane falling/crashing animation for "
                + CRASH_SEQUENCE_DURATION + "s before displaying Game Over UI.");
    }

    private void triggerGameOver() {
        phase = Phase.GAME_OVER;
        quizCompleted = false;

        persistScoreToDatabase();
        LOG.info("GAME OVER — Displaying Game Over UI. Final score: " + score);
    }

    private void persistScoreToDatabase() {
        if (scoreSaved) return;
        scoreSaved = true;
        int total = (questions != null) ? questions.size() : 0;

        QuizDatabase db = (app != null) ? app.getQuizDatabase() : null;
        if (db != null) {
            db.saveScore(playerName, score, total, correctAnswers, wrongHits, totalFlightTime);
        }
    }

    public String  getPlayerName()      { return playerName; }
    public int     getCorrectAnswers()  { return correctAnswers; }
    public float   getTotalFlightTime() { return totalFlightTime; }
    public boolean isScoreSaved()       { return scoreSaved; }
}
