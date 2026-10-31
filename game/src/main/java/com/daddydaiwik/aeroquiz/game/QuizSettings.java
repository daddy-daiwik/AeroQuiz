package com.daddydaiwik.aeroquiz.game;

/**
 * Configuration settings for the quiz game.
 *
 * <p>Configurable fields:
 * <ul>
 *   <li>{@code maxWrong} — Number of wrong answers before plane bursts (-1 for infinite lives). Default: 3</li>
 *   <li>{@code timePerQuestion} — Time limit in seconds for each question (-1 for infinite). Default: 15</li>
 *   <li>{@code obstacleShape} — 3D shape of option boxes: BOX, SPHERE, PYRAMID, CYLINDER. Default: BOX</li>
 * </ul>
 */
public class QuizSettings {

    public enum Shape {
        BOX,
        SPHERE,
        PYRAMID,
        CYLINDER
    }

    /** Maximum wrong answers before plane explodes. Default 3; -1 means infinite lives. */
    private int maxWrong = 3;

    /** Time limit in seconds for each question. Default 15; -1 means no time limit. */
    private float timePerQuestion = 15f;

    /** 3D geometry shape for obstacle boxes. */
    private Shape obstacleShape = Shape.BOX;

    /** Ground terrain type: GRASS, SAND (DESERT), SNOW (ARCTIC), WATER (OCEAN). */
    private String terrain = "GRASS";

    public int getMaxWrong() {
        return maxWrong;
    }

    public void setMaxWrong(int maxWrong) {
        this.maxWrong = maxWrong;
    }

    public float getTimePerQuestion() {
        return timePerQuestion;
    }

    public void setTimePerQuestion(float timePerQuestion) {
        this.timePerQuestion = timePerQuestion;
    }

    public Shape getObstacleShape() {
        return obstacleShape;
    }

    public void setObstacleShape(Shape obstacleShape) {
        this.obstacleShape = obstacleShape;
    }

    public String getTerrain() {
        return terrain;
    }

    public void setTerrain(String terrain) {
        this.terrain = terrain;
    }

    @Override
    public String toString() {
        return "QuizSettings{" +
                "maxWrong=" + maxWrong +
                ", timePerQuestion=" + timePerQuestion +
                ", obstacleShape=" + obstacleShape +
                ", terrain=" + terrain +
                '}';
    }
}
