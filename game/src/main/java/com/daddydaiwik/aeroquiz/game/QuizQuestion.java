package com.daddydaiwik.aeroquiz.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single quiz question with 2 to 6 answer options.
 * The correctColor field stores the key (e.g. "RED" or "BLUE") of the correct answer.
 */
public class QuizQuestion {

    public static final String[] DEFAULT_COLOR_KEYS = {
        "RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "CYAN"
    };

    /** The question text displayed in the sky. */
    private final String question;

    /** Preserves option order (e.g. key -> answer text). */
    private final Map<String, String> options;

    /** Ordered list of option keys present in this question. */
    private final List<String> optionKeys;

    /** The key of the correct answer (e.g. "RED"). */
    private final String correctColor;

    /**
     * Constructs a QuizQuestion.
     *
     * @param question     the question text
     * @param options      map of option keys to answer text (preserves order)
     * @param correctColor the key for the correct answer
     */
    public QuizQuestion(String question, Map<String, String> options, String correctColor) {
        this.question = question;
        this.options = new LinkedHashMap<>(options);
        this.optionKeys = new ArrayList<>(this.options.keySet());
        this.correctColor = correctColor.trim().toUpperCase();
    }

    /** @return the question text */
    public String getQuestion() {
        return question;
    }

    /**
     * Returns the answer text for the given option key.
     *
     * @param key color or index key (e.g. "RED", "BLUE")
     * @return answer text or empty string if not found
     */
    public String getOption(String key) {
        return options.getOrDefault(key.trim().toUpperCase(), "");
    }

    /** @return the number of options (between 2 and 6) */
    public int getOptionCount() {
        return optionKeys.size();
    }

    /** @return ordered list of option keys */
    public List<String> getOptionKeys() {
        return Collections.unmodifiableList(optionKeys);
    }

    /** @return key at specific index (0 to optionCount - 1) */
    public String getKeyAt(int index) {
        if (index >= 0 && index < optionKeys.size()) {
            return optionKeys.get(index);
        }
        return "";
    }

    /** @return option text at specific index (0 to optionCount - 1) */
    public String getOptionAt(int index) {
        String key = getKeyAt(index);
        return options.getOrDefault(key, "");
    }

    /** @return a copy of the options map */
    public Map<String, String> getOptions() {
        return new LinkedHashMap<>(options);
    }

    /** @return the key of the correct answer */
    public String getCorrectColor() {
        return correctColor;
    }

    @Override
    public String toString() {
        return "QuizQuestion{question='" + question + "', count=" + optionKeys.size() +
                ", correctColor='" + correctColor + "'}";
    }
}
