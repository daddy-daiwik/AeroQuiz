package com.daddydaiwik.aeroquiz.game;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

import java.util.List;

/** Result card shown after the quiz is finished or the aircraft is destroyed. */
final class GameOverScreen extends UiScreen {

    private static final float CARD_W = 600f;
    private static final float PAD = 30f;
    private static final int ROWS = 5;
    private static final float ROW_H = 28f;

    private final UiKit.Text headline;
    private final UiKit.Text subline;
    private final UiKit.Text scoreValue;
    private final UiKit.Text scoreNote;
    private final UiKit.Text accuracyValue;
    private final UiKit.Text timeValue;
    private final UiKit.Text hitsValue;

    private final Geometry[] rowHighlight = new Geometry[ROWS];
    private final UiKit.Text[] rowRank = new UiKit.Text[ROWS];
    private final UiKit.Text[] rowName = new UiKit.Text[ROWS];
    private final UiKit.Text[] rowDetail = new UiKit.Text[ROWS];
    private final UiKit.Text[] rowScore = new UiKit.Text[ROWS];
    private final UiKit.Text emptyBoard;

    GameOverScreen(HudState hud, UiKit kit, float uiW, float uiH) {
        super("GameOverScreen", hud, kit, uiW, uiH);

        Geometry dim = kit.rect("GameOverDim", uiW, uiH, kit.c(UiKit.PAPER, 0.40f), kit.c(UiKit.PAPER, 0.55f));
        dim.setLocalTranslation(0f, 0f, UiKit.Z_SCRIM);
        root.attachChild(dim);

        float cardH = Math.min(636f, uiH - 36f);
        float cardX = (uiW - CARD_W) / 2f;
        float cardY = (uiH - cardH) / 2f;
        float top = cardY + cardH;
        float cx = cardX + PAD;
        float cw = CARD_W - PAD * 2f;
        float mid = uiW / 2f;
        root.attachChild(kit.panel(CARD_W, cardH).place(cardX, cardY, UiKit.Z_PANEL));

        headline = kit.shadowText("", 36f, UiKit.TEXT, true, UiKit.Align.CENTER);
        headline.place(mid, top - 56f, UiKit.Z_TEXT);
        root.attachChild(headline);

        subline = kit.text("", 13f, UiKit.DIM, false, UiKit.Align.CENTER);
        subline.place(mid, top - 94f, UiKit.Z_TEXT);
        root.attachChild(subline);

        root.attachChild(kit.text("FINAL SCORE", 11f, UiKit.FAINT, false, UiKit.Align.CENTER).place(mid, top - 134f, UiKit.Z_TEXT));
        scoreValue = kit.shadowText("0", 72f, UiKit.AMBER, true, UiKit.Align.CENTER);
        scoreValue.place(mid, top - 182f, UiKit.Z_TEXT);
        root.attachChild(scoreValue);
        scoreNote = kit.text("", 13f, UiKit.DIM, false, UiKit.Align.CENTER);
        scoreNote.place(mid, top - 228f, UiKit.Z_TEXT);
        root.attachChild(scoreNote);

        // ── Stat tiles ────────────────────────────────────────────────────────
        float tileGap = 12f;
        float tileW = (cw - tileGap * 2f) / 3f;
        float tileH = 62f;
        float tileY = top - 256f - tileH;
        accuracyValue = statTile("ACCURACY", cx, tileY, tileW, tileH);
        timeValue     = statTile("FLIGHT TIME", cx + tileW + tileGap, tileY, tileW, tileH);
        hitsValue     = statTile("HITS TAKEN", cx + (tileW + tileGap) * 2f, tileY, tileW, tileH);

        // ── Leaderboard ───────────────────────────────────────────────────────
        float lbTop = tileY - 22f;
        root.attachChild(kit.text("TOP PILOTS", 12f, UiKit.AMBER, false, UiKit.Align.LEFT).place(cx, lbTop - 6f, UiKit.Z_TEXT));
        root.attachChild(kit.text("ALL-TIME LEADERBOARD", 10f, UiKit.FAINT, false, UiKit.Align.RIGHT).place(cx + cw, lbTop - 6f, UiKit.Z_TEXT));
        float rowsTop = lbTop - 22f;
        for (int i = 0; i < ROWS; i++) {
            float cy = rowsTop - ROW_H * (i + 0.5f);
            ColorRGBA hl = kit.c(UiKit.MINT, 0.95f);
            Geometry g = kit.fill("RowHighlight" + i, cw, ROW_H - 2f, 8f, hl, hl);
            g.setLocalTranslation(cx, cy - (ROW_H - 2f) / 2f, UiKit.Z_CONTENT);
            g.setCullHint(Spatial.CullHint.Always);
            root.attachChild(g);
            rowHighlight[i] = g;

            rowRank[i] = kit.text(String.valueOf(i + 1), 12f, UiKit.FAINT, false, UiKit.Align.CENTER);
            rowRank[i].place(cx + 18f, cy, UiKit.Z_TEXT);
            rowName[i] = kit.text("", 14f, UiKit.TEXT, false, UiKit.Align.LEFT);
            rowName[i].place(cx + 42f, cy, UiKit.Z_TEXT);
            rowDetail[i] = kit.text("", 12f, UiKit.DIM, false, UiKit.Align.RIGHT);
            rowDetail[i].place(cx + cw - 80f, cy, UiKit.Z_TEXT);
            rowScore[i] = kit.text("", 15f, UiKit.AMBER, true, UiKit.Align.RIGHT);
            rowScore[i].place(cx + cw - 14f, cy, UiKit.Z_TEXT);
            root.attachChild(rowRank[i]);
            root.attachChild(rowName[i]);
            root.attachChild(rowDetail[i]);
            root.attachChild(rowScore[i]);
        }
        emptyBoard = kit.text("Your result has been recorded.", 13f, UiKit.DIM, false, UiKit.Align.CENTER);
        emptyBoard.place(mid, rowsTop - ROW_H * ROWS / 2f, UiKit.Z_TEXT);
        root.attachChild(emptyBoard);

        // ── Buttons ───────────────────────────────────────────────────────────
        float bw = (cw - 16f) / 2f;
        float bh = 54f;
        float by = cardY + 24f;
        add(kit.button(UiKit.Style.PRIMARY, "RETRY FLIGHT   [R]", cx, by, bw, bh, 16f, hud::retryFlight));
        add(kit.button(UiKit.Style.SECONDARY, "Pilot Terminal   [Enter]", cx + bw + 16f, by, bw, bh, 15f, hud::backToTerminal));
    }

    private UiKit.Text statTile(String label, float x, float y, float w, float h) {
        root.attachChild(kit.inset(w, h, 12f).place(x, y, UiKit.Z_CONTENT));
        root.attachChild(kit.text(label, 10f, UiKit.FAINT, false, UiKit.Align.CENTER).place(x + w / 2f, y + h - 16f, UiKit.Z_TEXT));
        UiKit.Text value = kit.text("-", 20f, UiKit.TEXT, true, UiKit.Align.CENTER);
        value.place(x + w / 2f, y + 22f, UiKit.Z_TEXT);
        root.attachChild(value);
        return value;
    }

    @Override
    protected void onShow() {
        GameState gs = hud.gameState();
        if (gs == null) return;
        boolean won = gs.isQuizCompleted();
        QuizSettings settings = QuizLoader.getSettings();
        String vehicle = settings != null && settings.getAircraft() != null ? settings.getAircraft().getDisplayName() : "";

        headline.setText(won ? "MISSION COMPLETE" : "MISSION FAILED");
        headline.setColor(won ? kit.c(UiKit.GREEN) : kit.c(UiKit.RED));
        subline.setText("PILOT  " + gs.getPlayerName().toUpperCase() + (vehicle.isEmpty() ? "" : "   ·   " + vehicle));

        scoreValue.setText(String.valueOf(gs.getScore()));
        scoreNote.setText(won
                ? "out of " + gs.getMaxScore() + " possible points"
                : "Your aircraft was destroyed after " + gs.getWrongHits() + " hit" + (gs.getWrongHits() == 1 ? "" : "s"));

        int total = Math.max(gs.getTotalQuestions(), 1);
        accuracyValue.setText(gs.getCorrectAnswers() + " / " + total);
        timeValue.setText(String.format("%.1f s", gs.getTotalFlightTime()));
        hitsValue.setText(String.valueOf(gs.getWrongHits()));

        refreshLeaderboard(gs);
    }

    private void refreshLeaderboard(GameState gs) {
        QuizDatabase db = hud.app() != null ? hud.app().getQuizDatabase() : null;
        List<QuizDatabase.ScoreRecord> tops = db != null ? db.getTopScores(ROWS) : null;
        boolean empty = tops == null || tops.isEmpty();
        emptyBoard.setCullHint(empty ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);

        boolean highlighted = false;
        for (int i = 0; i < ROWS; i++) {
            boolean has = !empty && i < tops.size();
            Spatial.CullHint hint = has ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
            rowRank[i].setCullHint(hint);
            rowName[i].setCullHint(hint);
            rowDetail[i].setCullHint(hint);
            rowScore[i].setCullHint(hint);
            rowHighlight[i].setCullHint(Spatial.CullHint.Always);
            if (!has) continue;

            QuizDatabase.ScoreRecord rec = tops.get(i);
            rowName[i].setText(kit.fit(rec.playerName(), 14f, false, 220f));
            rowDetail[i].setText(rec.correctAnswers() + "/" + rec.totalQuestions());
            rowScore[i].setText(String.valueOf(rec.score()));
            boolean mine = !highlighted && rec.playerName().equalsIgnoreCase(gs.getPlayerName())
                    && rec.score() == gs.getScore();
            if (mine) {
                highlighted = true;
                rowHighlight[i].setCullHint(Spatial.CullHint.Inherit);
            }
        }
    }
}
