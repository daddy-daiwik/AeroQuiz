package com.daddydaiwik.aeroquiz.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State and editing logic for the in-game quiz & settings editor. Kept separate from the view
 * ({@link EditorScreen}) so that the screen can be rebuilt (e.g. on window resize) without losing edits.
 */
final class EditorModel {

    enum Field { NONE, QUESTION_PROMPT, OPT_RED, OPT_BLUE, OPT_GREEN, OPT_YELLOW }

    static final String[] OPTION_KEYS = {"RED", "BLUE", "GREEN", "YELLOW"};
    private static final Field[] OPTION_FIELDS = {Field.OPT_RED, Field.OPT_BLUE, Field.OPT_GREEN, Field.OPT_YELLOW};
    private static final String[] OPTION_DEFAULTS = {"Option A", "Option B", "Option C", "Option D"};

    private static final String[] TERRAINS = {"GRASS", "WATER", "SAND", "SNOW"};
    private static final int[] LIVES = {1, 2, 3, 4, 5, -1};
    private static final float[] TIMERS = {5f, 10f, 15f, 20f, 30f, -1f};

    private final HudState hud;
    private final AeroQuiz app;

    private List<QuizQuestion> questions = new ArrayList<>();
    private QuizSettings settings = new QuizSettings();
    private int index = 0;
    private Field active = Field.NONE;
    private final StringBuilder buffer = new StringBuilder();
    private String status = "";
    private boolean statusIsError = false;

    private int terrainIdx = 0;
    private int livesIdx = 2;
    private int timerIdx = 2;

    EditorModel(HudState hud, AeroQuiz app) {
        this.hud = hud;
        this.app = app;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Reloads the catalogue from disk and syncs the setting steppers to the live settings. */
    void open() {
        questions = new ArrayList<>(QuizLoader.load("questions.txt"));
        settings = QuizLoader.getSettings();
        index = 0;
        active = Field.NONE;
        buffer.setLength(0);

        terrainIdx = indexOf(TERRAINS, settings.getTerrain());
        livesIdx = 2;
        for (int i = 0; i < LIVES.length; i++) if (LIVES[i] == settings.getMaxWrong()) livesIdx = i;
        timerIdx = 2;
        for (int i = 0; i < TIMERS.length; i++) if (TIMERS[i] == settings.getTimePerQuestion()) timerIdx = i;

        setStatus("Loaded " + questions.size() + " question(s). Click a field to edit it.", false);
    }

    private static int indexOf(String[] arr, String value) {
        if (value != null) {
            for (int i = 0; i < arr.length; i++) if (arr[i].equalsIgnoreCase(value.trim())) return i;
        }
        return 0;
    }

    // ── Read access for the view ──────────────────────────────────────────────

    boolean hasQuestions() { return !questions.isEmpty(); }
    int questionCount() { return questions.size(); }
    int questionNumber() { return index + 1; }
    Field active() { return active; }
    boolean isEditing() { return active != Field.NONE; }
    String buffer() { return buffer.toString(); }
    String status() { return status; }
    boolean statusIsError() { return statusIsError; }

    QuizQuestion current() {
        if (questions.isEmpty()) return null;
        if (index >= questions.size()) index = 0;
        return questions.get(index);
    }

    String terrainLabel() { return TERRAINS[terrainIdx]; }
    String livesLabel() { return LIVES[livesIdx] < 0 ? "∞" : String.valueOf(LIVES[livesIdx]); }
    String timerLabel() { return TIMERS[timerIdx] < 0 ? "OFF" : (int) TIMERS[timerIdx] + " s"; }
    String vehicleLabel() {
        return switch (hud.aircraft()) {
            case PLANE       -> "BIPLANE";
            case HELICOPTER  -> "HELICOPTER";
            case FLYING_CAR  -> "AERO-CAR";
        };
    }

    Field optionField(int i) { return OPTION_FIELDS[i]; }

    /** Text shown in a field: the live buffer while it is being edited, otherwise the stored value. */
    String valueOf(Field f) {
        if (f == active) return buffer.toString();
        QuizQuestion q = current();
        return q == null ? "" : stored(f, q);
    }

    // ── Settings steppers ─────────────────────────────────────────────────────

    void stepTerrain(int dir) { leaveField(); terrainIdx = (terrainIdx + dir + TERRAINS.length) % TERRAINS.length; }
    void stepLives(int dir)   { leaveField(); livesIdx = (livesIdx + dir + LIVES.length) % LIVES.length; }
    void stepTimer(int dir)   { leaveField(); timerIdx = (timerIdx + dir + TIMERS.length) % TIMERS.length; }
    void stepVehicle(int dir) { leaveField(); hud.cycleAircraft(dir); }

    private void leaveField() {
        applyBuffer();
        active = Field.NONE;
    }

    // ── Question navigation ───────────────────────────────────────────────────

    void stepQuestion(int dir) {
        leaveField();
        if (!questions.isEmpty()) index = (index + dir + questions.size()) % questions.size();
    }

    void newQuestion() {
        applyBuffer();
        Map<String, String> opts = new LinkedHashMap<>();
        opts.put("RED", "Alpha Option");
        opts.put("BLUE", "Beta Option");
        opts.put("GREEN", "Gamma Option");
        opts.put("YELLOW", "Delta Option");
        questions.add(new QuizQuestion("Custom Flight Question #" + (questions.size() + 1), opts, "RED"));
        index = questions.size() - 1;
        beginEdit(Field.QUESTION_PROMPT);
        setStatus("Added question #" + (index + 1) + ". Edit the prompt and options.", false);
    }

    void deleteQuestion() {
        leaveField();
        if (questions.size() > 1) {
            questions.remove(index);
            if (index >= questions.size()) index = questions.size() - 1;
            setStatus("Deleted question. " + questions.size() + " remaining.", false);
        } else {
            setStatus("Cannot delete: at least one question is required.", true);
        }
    }

    void setCorrect(String key) {
        applyBuffer();
        QuizQuestion q = current();
        if (q == null) return;
        questions.set(index, new QuizQuestion(q.getQuestion(), q.getOptions(), key));
        setStatus("Correct answer set to " + key + ".", false);
    }

    // ── Field editing ─────────────────────────────────────────────────────────

    void beginEdit(Field f) {
        applyBuffer();
        QuizQuestion q = current();
        if (q == null) return;
        active = f;
        buffer.setLength(0);
        buffer.append(stored(f, q));
        setStatus("Editing " + label(f) + ". Type, then press ENTER or Done.", false);
    }

    /** The value currently stored in the question for a field (ignores the live buffer). */
    private static String stored(Field f, QuizQuestion q) {
        if (f == Field.QUESTION_PROMPT) return q.getQuestion();
        for (int i = 0; i < OPTION_FIELDS.length; i++) {
            if (OPTION_FIELDS[i] == f) return q.getOption(OPTION_KEYS[i]);
        }
        return "";
    }

    void toggleEdit(Field f) {
        if (!hasQuestions()) return;
        if (active == f) {
            commit();
        } else {
            beginEdit(f);
        }
    }

    void commit() {
        if (active == Field.NONE) return;
        Field was = active;
        applyBuffer();
        active = Field.NONE;
        setStatus(label(was) + " saved. Click another field or save the quiz.", false);
    }

    void cancel() {
        if (active == Field.NONE) return;
        active = Field.NONE;
        setStatus("Edit cancelled.", false);
    }

    void cycleNextField() {
        applyBuffer();
        if (questions.isEmpty()) return;
        Field next = switch (active) {
            case NONE, OPT_YELLOW -> Field.QUESTION_PROMPT;
            case QUESTION_PROMPT  -> Field.OPT_RED;
            case OPT_RED          -> Field.OPT_BLUE;
            case OPT_BLUE         -> Field.OPT_GREEN;
            case OPT_GREEN        -> Field.OPT_YELLOW;
        };
        beginEdit(next);
    }

    void typeChar(char c) {
        if (active == Field.NONE || buffer.length() >= 70) return;
        if (Character.isLetterOrDigit(c) || c == ' ' || "?!.,-':;/()#%&*+=@\"[]{}".indexOf(c) != -1) {
            buffer.append(c);
            applyBuffer();
        }
    }

    void backspace() {
        if (active != Field.NONE && buffer.length() > 0) {
            buffer.deleteCharAt(buffer.length() - 1);
            applyBuffer();
        }
    }

    void clearBuffer() {
        if (active != Field.NONE) {
            buffer.setLength(0);
            applyBuffer();
        }
    }

    /** Writes the live buffer into the current question (blank values fall back to a placeholder). */
    private void applyBuffer() {
        if (active == Field.NONE || questions.isEmpty() || index >= questions.size()) return;
        QuizQuestion cur = questions.get(index);
        String text = buffer.toString().trim();

        if (active == Field.QUESTION_PROMPT) {
            if (text.isEmpty()) text = "Question " + (index + 1);
            questions.set(index, new QuizQuestion(text, cur.getOptions(), cur.getCorrectColor()));
            return;
        }
        for (int i = 0; i < OPTION_FIELDS.length; i++) {
            if (OPTION_FIELDS[i] == active) {
                if (text.isEmpty()) text = OPTION_DEFAULTS[i];
                Map<String, String> opts = new LinkedHashMap<>(cur.getOptions());
                opts.put(OPTION_KEYS[i], text);
                questions.set(index, new QuizQuestion(cur.getQuestion(), opts, cur.getCorrectColor()));
                return;
            }
        }
    }

    private static String label(Field f) {
        return switch (f) {
            case QUESTION_PROMPT -> "question prompt";
            case OPT_RED         -> "RED option";
            case OPT_BLUE        -> "BLUE option";
            case OPT_GREEN       -> "GREEN option";
            case OPT_YELLOW      -> "YELLOW option";
            default              -> "field";
        };
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    void save() {
        leaveField();
        settings.setTerrain(TERRAINS[terrainIdx]);
        settings.setMaxWrong(LIVES[livesIdx]);
        settings.setTimePerQuestion(TIMERS[timerIdx]);
        settings.setAircraft(hud.aircraft());

        boolean saved = QuizLoader.saveQuestionsAndSettings(questions, settings);
        if (app != null) {
            app.updateGroundTerrain();
            PlaneController pc = app.getStateManager().getState(PlaneController.class);
            if (pc != null) pc.switchAircraft(hud.aircraft());
            QuizDatabase db = app.getQuizDatabase();
            if (db != null) {
                db.saveCustomQuiz("Custom AeroQuiz",
                        QuizLoader.serializeSettings(settings),
                        QuizLoader.serializeQuestions(questions));
            }
        }
        setStatus(saved ? "Saved to disk and database." : "Error saving to disk.", !saved);
    }

    private void setStatus(String s, boolean error) {
        status = s;
        statusIsError = error;
    }
}
