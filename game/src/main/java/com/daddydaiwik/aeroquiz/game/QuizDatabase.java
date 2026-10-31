package com.daddydaiwik.aeroquiz.game;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages database persistence of quiz flight attempts, player scores,
 * and custom quizzes using Java Database Connectivity (JDBC) with SQLite.
 */
public class QuizDatabase {

    private static final Logger LOG = Logger.getLogger(QuizDatabase.class.getName());
    private static final String DB_FILE_NAME = "aeroquiz.db";

    public record ScoreRecord(
            int id,
            String playerName,
            int score,
            int totalQuestions,
            int correctAnswers,
            int wrongAnswers,
            float timeTakenSecs,
            String playedAt
    ) {}

    public record CustomQuizRecord(
            int id,
            String title,
            String settingsData,
            String questionsData,
            String createdAt
    ) {}

    private String dbUrl;
    private boolean isJdbcAvailable = false;
    private final List<ScoreRecord> fallbackRecords = new ArrayList<>();
    private final List<CustomQuizRecord> fallbackQuizzes = new ArrayList<>();

    public QuizDatabase() {
        initDbUrl();
        initSchema();
    }

    private void initDbUrl() {
        try {
            File rootDir = new File(".");
            File targetDb;
            if (rootDir.getAbsolutePath().contains("desktop" + File.separator)) {
                targetDb = new File("..", DB_FILE_NAME);
            } else {
                targetDb = new File(rootDir, DB_FILE_NAME);
            }
            this.dbUrl = "jdbc:sqlite:" + targetDb.getAbsolutePath();
            LOG.info("QuizDatabase initialized with JDBC URL: " + dbUrl);
        } catch (Exception e) {
            LOG.warning("QuizDatabase: path resolution failed: " + e.getMessage());
            this.dbUrl = null;
        }
    }

    private void initSchema() {
        if (dbUrl == null) {
            isJdbcAvailable = false;
            return;
        }
        String scoresSql = """
            CREATE TABLE IF NOT EXISTS quiz_scores (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_name TEXT NOT NULL,
                score INTEGER NOT NULL,
                total_questions INTEGER NOT NULL,
                correct_answers INTEGER NOT NULL,
                wrong_answers INTEGER NOT NULL,
                time_taken_secs REAL DEFAULT 0,
                played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;
        String quizzesSql = """
            CREATE TABLE IF NOT EXISTS custom_quizzes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                settings_data TEXT NOT NULL,
                questions_data TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(scoresSql);
            stmt.execute(quizzesSql);
            isJdbcAvailable = true;
            LOG.info("QuizDatabase: schema verified/created successfully.");
        } catch (Throwable e) {
            isJdbcAvailable = false;
            LOG.warning("QuizDatabase: SQLite JDBC unavailable on this platform (" + e.getMessage() +
                    ") — using active in-memory store.");
        }
    }

    public boolean saveScore(String playerName, int score, int totalQuestions,
                             int correctAnswers, int wrongAnswers, float timeTakenSecs) {
        String cleanName = (playerName == null || playerName.trim().isEmpty()) ? "Pilot" : playerName.trim();

        if (isJdbcAvailable && dbUrl != null) {
            String sql = """
                INSERT INTO quiz_scores (player_name, score, total_questions, correct_answers, wrong_answers, time_taken_secs)
                VALUES (?, ?, ?, ?, ?, ?);
                """;
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, cleanName);
                pstmt.setInt(2, score);
                pstmt.setInt(3, totalQuestions);
                pstmt.setInt(4, correctAnswers);
                pstmt.setInt(5, wrongAnswers);
                pstmt.setFloat(6, timeTakenSecs);
                int rows = pstmt.executeUpdate();
                LOG.info("QuizDatabase: saved score for '" + cleanName + "' (" + score + " pts).");
                return rows > 0;
            } catch (Throwable e) {
                LOG.warning("QuizDatabase: JDBC save failed, falling back to memory store: " + e.getMessage());
            }
        }

        // In-memory fallback
        int id = fallbackRecords.size() + 1;
        String now = java.time.LocalDateTime.now().toString().replace('T', ' ').substring(0, 19);
        fallbackRecords.add(new ScoreRecord(id, cleanName, score, totalQuestions, correctAnswers, wrongAnswers, timeTakenSecs, now));
        LOG.info("QuizDatabase (Memory): saved score for '" + cleanName + "'. Total: " + fallbackRecords.size());
        return true;
    }

    public List<ScoreRecord> getTopScores(int limit) {
        List<ScoreRecord> list = new ArrayList<>();

        if (isJdbcAvailable && dbUrl != null) {
            String sql = """
                  SELECT id, player_name, score, total_questions, correct_answers, wrong_answers, time_taken_secs, played_at
                  FROM quiz_scores
                  ORDER BY score DESC, time_taken_secs ASC, id DESC
                  LIMIT ?;
                  """;
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new ScoreRecord(
                                rs.getInt("id"),
                                rs.getString("player_name"),
                                rs.getInt("score"),
                                rs.getInt("total_questions"),
                                rs.getInt("correct_answers"),
                                rs.getInt("wrong_answers"),
                                rs.getFloat("time_taken_secs"),
                                rs.getString("played_at")
                        ));
                    }
                }
                return list;
            } catch (Throwable e) {
                LOG.warning("QuizDatabase: JDBC query failed, returning memory store: " + e.getMessage());
            }
        }

        // Return from fallbackRecords sorted
        List<ScoreRecord> sorted = new ArrayList<>(fallbackRecords);
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(b.score(), a.score());
            if (cmp != 0) return cmp;
            return Float.compare(a.timeTakenSecs(), b.timeTakenSecs());
        });
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public boolean saveCustomQuiz(String title, String settingsData, String questionsData) {
        String cleanTitle = (title == null || title.trim().isEmpty()) ? "AeroQuiz Challenge" : title.trim();

        if (isJdbcAvailable && dbUrl != null) {
            String sql = """
                INSERT INTO custom_quizzes (title, settings_data, questions_data)
                VALUES (?, ?, ?);
                """;
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, cleanTitle);
                pstmt.setString(2, settingsData);
                pstmt.setString(3, questionsData);
                return pstmt.executeUpdate() > 0;
            } catch (Throwable e) {
                LOG.warning("QuizDatabase: saveCustomQuiz failed: " + e.getMessage());
            }
        }

        int id = fallbackQuizzes.size() + 1;
        String now = java.time.LocalDateTime.now().toString();
        fallbackQuizzes.add(new CustomQuizRecord(id, cleanTitle, settingsData, questionsData, now));
        return true;
    }
}
