package com.daddydaiwik.aeroquiz.game;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@link QuizQuestion} objects and {@link QuizSettings} from text/JSON formats.
 * Supports 2 to 6 options per question and custom quiz configurations.
 */
public class QuizLoader {

    private static final Logger LOG = Logger.getLogger(QuizLoader.class.getName());

    /** Recognized color keys in display order. */
    public static final String[] COLOR_KEYS = {
        "RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "CYAN"
    };

    /** Active game settings loaded from file (or defaults). */
    private static QuizSettings activeSettings = new QuizSettings();

    private QuizLoader() {}

    public static QuizSettings getSettings() {
        return activeSettings;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Tries to load questions from candidate disk paths, falling back to classpath.
     */
    public static List<QuizQuestion> load(String name) {
        // Also probe for separate settings file if present
        loadSettings();

        String[] candidatePaths = {
            name,                                // Working directory (e.g. ./questions.txt)
            "questions.txt",
            "game/src/main/resources/" + name,
            "game/src/main/resources/questions.txt",
            "desktop/" + name,
            "assets/" + name,
            "../" + name
        };

        for (String path : candidatePaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                LOG.info("Found questions file at: " + file.getAbsolutePath());
                List<QuizQuestion> questions = loadFromFile(file.getAbsolutePath());
                if (!questions.isEmpty()) {
                    LOG.info("Successfully loaded " + questions.size() + " question(s) from: " + file.getAbsolutePath());
                    return questions;
                }
            }
        }

        // Fall back to classpath
        LOG.info("Loading questions from classpath resource: " + name);
        return loadFromClasspath(name);
    }

    public static List<QuizQuestion> loadFromFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return parse(reader);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load questions from file: " + path, e);
            return new ArrayList<>();
        }
    }

    public static List<QuizQuestion> loadFromClasspath(String name) {
        InputStream stream = QuizLoader.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            LOG.warning("Classpath resource not found: " + name);
            return new ArrayList<>();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return parse(reader);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse classpath resource: " + name, e);
            return new ArrayList<>();
        }
    }

    public static List<QuizQuestion> parseFromString(String content) {
        if (content == null || content.trim().isEmpty()) return new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(content))) {
            return parse(reader);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse questions from string: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public static String serializeQuestions(List<QuizQuestion> questions) {
        StringBuilder sb = new StringBuilder();
        if (questions != null) {
            for (QuizQuestion q : questions) {
                sb.append("{\n");
                sb.append("  QUESTION: ").append(q.getQuestion()).append("\n");
                for (String key : q.getOptionKeys()) {
                    sb.append("  ").append(key).append(": ").append(q.getOption(key)).append("\n");
                }
                sb.append("  CORRECT: ").append(q.getCorrectColor()).append("\n");
                sb.append("}\n\n");
            }
        }
        return sb.toString();
    }

    public static String serializeSettings(QuizSettings s) {
        if (s == null) s = activeSettings;
        return "maxWrong: " + s.getMaxWrong() + "\n" +
               "timePerQuestion: " + (int) s.getTimePerQuestion() + "\n" +
               "terrain: " + s.getTerrain() + "\n" +
               "obstacleShape: " + s.getObstacleShape() + "\n";
    }

    public static void applySettingsFromString(String content) {
        if (content == null || content.trim().isEmpty()) return;
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(content))) {
            parseSettingsLines(reader);
        } catch (Exception e) {
            LOG.warning("applySettingsFromString error: " + e.getMessage());
        }
    }

    public static boolean saveQuestionsAndSettings(List<QuizQuestion> questions, QuizSettings settings) {
        try {
            if (questions != null && !questions.isEmpty()) {
                String qData = serializeQuestions(questions);
                java.nio.file.Files.writeString(new File("questions.txt").toPath(), qData);
            }
            if (settings != null) {
                activeSettings = settings;
                String sData = serializeSettings(settings);
                java.nio.file.Files.writeString(new File("quiz_settings.txt").toPath(), sData);
            }
            LOG.info("Saved quiz questions and settings to disk.");
            return true;
        } catch (Exception e) {
            LOG.warning("Failed saving questions and settings to disk: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads or reloads quiz settings from configuration files.
     */
    public static void loadSettings() {
        loadSettingsFile("quiz_settings.txt");
        loadSettingsFile("settings.txt");
    }

    private static void loadSettingsFile(String path) {
        String[] candidatePaths = {
            path,
            "../" + path,
            "desktop/../" + path,
            "game/src/main/resources/" + path,
            "assets/" + path
        };

        for (String cand : candidatePaths) {
            File f = new File(cand);
            if (f.exists() && f.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    parseSettingsLines(reader);
                    LOG.info("Loaded quiz settings from: " + f.getAbsolutePath() + " -> " + activeSettings);
                    return;
                } catch (Exception e) {
                    LOG.warning("Could not parse settings from " + cand + ": " + e.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Parsing logic
    // -------------------------------------------------------------------------

    private static List<QuizQuestion> parse(BufferedReader reader) throws Exception {
        List<QuizQuestion> questions = new ArrayList<>();

        String line;
        boolean inBlock = false;
        boolean inSettingsBlock = false;
        String questionText = null;
        Map<String, String> options = new LinkedHashMap<>();
        String correctColor = null;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Detect standalone settings lines (e.g. MAX_WRONG: 3)
            if (!inBlock && line.contains(":")) {
                if (checkAndApplySetting(line)) {
                    continue;
                }
            }

            if (line.startsWith("{")) {
                inBlock = true;
                inSettingsBlock = line.toUpperCase().contains("SETTING");
                questionText = null;
                options = new LinkedHashMap<>();
                correctColor = null;
                continue;
            }

            if (line.startsWith("}")) {
                if (inSettingsBlock) {
                    inSettingsBlock = false;
                    inBlock = false;
                    continue;
                }

                if (inBlock) {
                    // Normalize answer if given as index or letter
                    String finalAnswer = normalizeAnswerKey(correctColor, options);

                    if (questionText != null && finalAnswer != null && options.size() >= 2 && options.size() <= 6) {
                        questions.add(new QuizQuestion(questionText, options, finalAnswer));
                    } else if (questionText != null) {
                        LOG.warning("Skipping incomplete question: " + questionText +
                                " (options=" + options.size() + ", answer=" + finalAnswer + ")");
                    }
                }
                inBlock = false;
                continue;
            }

            if (!inBlock || line.startsWith("[") || line.startsWith("]")) {
                continue;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) continue;

            String key = cleanString(line.substring(0, colonIdx)).toUpperCase();
            String value = cleanString(line.substring(colonIdx + 1));

            if (inSettingsBlock || key.startsWith("SETTING") || key.equals("MAXWRONG") ||
                key.equals("MAX_WRONG") || key.equals("TIME") || key.equals("SHAPE")) {
                checkAndApplySetting(key + ": " + value);
                continue;
            }

            if (key.equals("QUESTION")) {
                questionText = value;
            } else if (key.equals("ANSWER") || key.equals("CORRECT")) {
                correctColor = value.toUpperCase();
            } else {
                // Map key to standard color or retain key name
                String mappedKey = mapOptionKey(key, options.size());
                options.put(mappedKey, value);
            }
        }

        LOG.info("QuizLoader parsed " + questions.size() + " questions. Settings: " + activeSettings);
        return questions;
    }

    private static boolean checkAndApplySetting(String line) {
        int idx = line.indexOf(':');
        if (idx < 0) return false;
        String k = cleanString(line.substring(0, idx)).toUpperCase().replace("_", "").replace(" ", "");
        String v = cleanString(line.substring(idx + 1)).toUpperCase();

        if (k.equals("MAXWRONG") || k.equals("LIVES") || k.equals("WRONGCOUNT")) {
            try {
                activeSettings.setMaxWrong(Integer.parseInt(v));
                LOG.info("Settings: maxWrong = " + activeSettings.getMaxWrong());
                return true;
            } catch (NumberFormatException ignored) {}
        } else if (k.equals("TIME") || k.equals("TIMEPERQUESTION") || k.equals("TIMELIMIT")) {
            try {
                activeSettings.setTimePerQuestion(Float.parseFloat(v));
                LOG.info("Settings: timePerQuestion = " + activeSettings.getTimePerQuestion());
                return true;
            } catch (NumberFormatException ignored) {}
        } else if (k.equals("SHAPE") || k.equals("OBSTACLESHAPE")) {
            try {
                activeSettings.setObstacleShape(QuizSettings.Shape.valueOf(v));
                LOG.info("Settings: obstacleShape = " + activeSettings.getObstacleShape());
                return true;
            } catch (IllegalArgumentException e) {
                if (v.contains("SPHERE") || v.contains("BALL") || v.contains("ROUND")) {
                    activeSettings.setObstacleShape(QuizSettings.Shape.SPHERE);
                } else if (v.contains("PYRAMID") || v.contains("TRIANGLE") || v.contains("CONE")) {
                    activeSettings.setObstacleShape(QuizSettings.Shape.PYRAMID);
                } else if (v.contains("CYLINDER")) {
                    activeSettings.setObstacleShape(QuizSettings.Shape.CYLINDER);
                } else {
                    activeSettings.setObstacleShape(QuizSettings.Shape.BOX);
                }
                return true;
            }
        } else if (k.equals("TERRAIN") || k.equals("GROUND") || k.equals("THEME")) {
            activeSettings.setTerrain(v);
            LOG.info("Settings: terrain = " + activeSettings.getTerrain());
            return true;
        }
        return false;
    }

    private static void parseSettingsLines(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            checkAndApplySetting(line);
        }
    }

    private static String mapOptionKey(String key, int currentCount) {
        for (String ck : COLOR_KEYS) {
            if (key.equals(ck)) return ck;
        }
        // Map 1/A -> RED, 2/B -> BLUE, etc.
        if (key.equals("1") || key.equals("A") || key.equals("OPT1")) return COLOR_KEYS[0];
        if (key.equals("2") || key.equals("B") || key.equals("OPT2")) return COLOR_KEYS[1];
        if (key.equals("3") || key.equals("C") || key.equals("OPT3")) return COLOR_KEYS[2];
        if (key.equals("4") || key.equals("D") || key.equals("OPT4")) return COLOR_KEYS[3];
        if (key.equals("5") || key.equals("E") || key.equals("OPT5")) return COLOR_KEYS[4];
        if (key.equals("6") || key.equals("F") || key.equals("OPT6")) return COLOR_KEYS[5];

        if (currentCount < COLOR_KEYS.length) {
            return COLOR_KEYS[currentCount];
        }
        return key;
    }

    private static String normalizeAnswerKey(String answer, Map<String, String> options) {
        if (answer == null) return null;
        answer = answer.trim().toUpperCase();

        if (options.containsKey(answer)) {
            return answer;
        }

        // Check if answer was given as number 1..6 or letter A..F
        List<String> keys = new ArrayList<>(options.keySet());
        if (answer.equals("1") || answer.equals("A")) return keys.size() > 0 ? keys.get(0) : "RED";
        if (answer.equals("2") || answer.equals("B")) return keys.size() > 1 ? keys.get(1) : "BLUE";
        if (answer.equals("3") || answer.equals("C")) return keys.size() > 2 ? keys.get(2) : "GREEN";
        if (answer.equals("4") || answer.equals("D")) return keys.size() > 3 ? keys.get(3) : "YELLOW";
        if (answer.equals("5") || answer.equals("E")) return keys.size() > 4 ? keys.get(4) : "PURPLE";
        if (answer.equals("6") || answer.equals("F")) return keys.size() > 5 ? keys.get(5) : "CYAN";

        // Check if answer matched any option text
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (entry.getValue().trim().equalsIgnoreCase(answer)) {
                return entry.getKey();
            }
        }
        return answer;
    }

    private static String cleanString(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.endsWith(",")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        } else if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
