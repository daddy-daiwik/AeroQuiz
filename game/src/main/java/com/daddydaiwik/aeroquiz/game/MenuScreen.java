package com.daddydaiwik.aeroquiz.game;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-flight "hangar" screen: pilot callsign, vehicle choice, mission summary and Hall of Fame.
 * The side panels leave the middle of the screen free so the aircraft idling on the runway stays visible.
 */
final class MenuScreen extends UiScreen {

    private static final float MARGIN = 36f;
    private static final float PANEL_W = 372f;
    private static final float PAD = 22f;
    private static final int LEADERBOARD_ROWS = 5;

    // Pilot field
    private final UiKit.Text callsignText;
    private final Geometry caret;
    private final float fieldX;
    private final float fieldW;

    // Vehicles
    private final QuizSettings.AircraftType[] vehicles = QuizSettings.AircraftType.values();
    private final UiKit.Button[] vehicleButtons = new UiKit.Button[vehicles.length];
    private final UiKit.Text[] vehicleMarks = new UiKit.Text[vehicles.length];

    // Hall of fame rows
    private final Geometry[] rankDiscs = new Geometry[LEADERBOARD_ROWS];
    private final UiKit.Text[] rankNumbers = new UiKit.Text[LEADERBOARD_ROWS];
    private final UiKit.Text[] rowNames = new UiKit.Text[LEADERBOARD_ROWS];
    private final UiKit.Text[] rowDetails = new UiKit.Text[LEADERBOARD_ROWS];
    private final UiKit.Text[] rowScores = new UiKit.Text[LEADERBOARD_ROWS];
    private final UiKit.Text emptyTitle;
    private final UiKit.Text emptyBody;

    // Mission summary
    private final UiKit.Text missionQuestions;
    private final UiKit.Text missionLives;
    private final UiKit.Text missionTimer;
    private final UiKit.Text missionTerrain;

    private float lastCaretX = -1f;

    MenuScreen(HudState hud, UiKit kit, float uiW, float uiH) {
        super("MenuScreen", hud, kit, uiW, uiH);

        // ── Title block ───────────────────────────────────────────────────────
        root.attachChild(kit.shadowText("AEROQUIZ", 62f, UiKit.TEXT, true, UiKit.Align.CENTER)
                .place(uiW / 2f, fromTop(64f), UiKit.Z_TEXT));
        root.attachChild(kit.shadowText("FLIGHT QUIZ SIMULATOR", 15f, UiKit.TEXT, false, UiKit.Align.CENTER)
                .place(uiW / 2f, fromTop(114f), UiKit.Z_TEXT));
        Geometry titleBar = kit.fill("TitleBar", 64f, 5f, 2f, kit.c(UiKit.INK), kit.c(UiKit.INK));
        titleBar.setLocalTranslation(uiW / 2f - 32f, fromTop(136f), UiKit.Z_CONTENT);
        root.attachChild(titleBar);

        float panelTop = fromTop(168f);
        float panelH = 440f;
        float panelY = panelTop - panelH;

        // ══ Left panel: pilot + vehicle ═══════════════════════════════════════
        float lx = MARGIN;
        root.attachChild(kit.panel(PANEL_W, panelH).place(lx, panelY, UiKit.Z_PANEL));

        float cx = lx + PAD;
        float cw = PANEL_W - PAD * 2f;
        float y = panelTop - PAD;

        root.attachChild(kit.text("PILOT CALLSIGN", 12f, UiKit.DIM, false, UiKit.Align.LEFT)
                .place(cx, y - 6f, UiKit.Z_TEXT));
        y -= 22f;

        float fieldH = 48f;
        fieldX = cx;
        fieldW = cw;
        float fieldY = y - fieldH;
        root.attachChild(kit.inset(fieldW, fieldH, 12f).place(fieldX, fieldY, UiKit.Z_CONTENT));

        callsignText = kit.text("", 20f, UiKit.TEXT, false, UiKit.Align.LEFT);
        callsignText.place(fieldX + 16f, fieldY + fieldH / 2f, UiKit.Z_TEXT);
        root.attachChild(callsignText);

        caret = kit.fill("Caret", 2f, 24f, 1f, kit.c(UiKit.ACCENT), kit.c(UiKit.ACCENT));
        caret.setLocalTranslation(fieldX + 16f, fieldY + (fieldH - 24f) / 2f, UiKit.Z_TEXT);
        root.attachChild(caret);

        float chip = 32f;
        float chipY = fieldY + (fieldH - chip) / 2f;
        float chipX = fieldX + fieldW - 8f - chip;
        add(kit.button(UiKit.Style.CHIP, "›", chipX, chipY, chip, chip, 20f, () -> hud.cycleCallsign(1)));
        chipX -= chip + 6f;
        add(kit.button(UiKit.Style.CHIP, "‹", chipX, chipY, chip, chip, 20f, () -> hud.cycleCallsign(-1)));
        chipX -= chip + 6f;
        add(kit.button(UiKit.Style.CHIP, "×", chipX, chipY, chip, chip, 20f, hud::clearCallsign));

        y = fieldY - 22f;
        root.attachChild(kit.text("VEHICLE", 12f, UiKit.DIM, false, UiKit.Align.LEFT)
                .place(cx, y - 6f, UiKit.Z_TEXT));
        y -= 22f;

        float rowH = 62f;
        for (int i = 0; i < vehicles.length; i++) {
            final QuizSettings.AircraftType type = vehicles[i];
            float by = y - rowH;
            UiKit.Button b = add(kit.button(UiKit.Style.ROW, type.getDisplayName(), cx, by, cw, rowH, 15f,
                    () -> hud.selectAircraft(type)));
            b.leftAlignLabel(18f);
            b.label().place(18f, rowH / 2f + 11f, 1f);
            UiKit.Text desc = kit.text(kit.fit(type.getDescription(), 12f, false, cw - 56f),
                    12f, UiKit.DIM, false, UiKit.Align.LEFT);
            desc.place(18f, rowH / 2f - 11f, 1f);
            b.attachChild(desc);
            UiKit.Text mark = kit.text("●", 14f, UiKit.ACCENT, false, UiKit.Align.RIGHT);
            mark.place(cw - 18f, rowH / 2f, 1f);
            b.attachChild(mark);
            vehicleButtons[i] = b;
            vehicleMarks[i] = mark;
            y = by - 8f;
        }

        float edH = 44f;
        add(kit.button(UiKit.Style.SECONDARY, "Quiz & Settings Editor", cx, panelY + PAD, cw, edH, 15f, hud::openEditor));

        // ══ Right panel: hall of fame + mission ═══════════════════════════════
        float rx = uiW - MARGIN - PANEL_W;
        root.attachChild(kit.panel(PANEL_W, panelH).place(rx, panelY, UiKit.Z_PANEL));

        float rcx = rx + PAD;
        float rcw = PANEL_W - PAD * 2f;
        float ry = panelTop - PAD;

        root.attachChild(kit.text("HALL OF FAME", 13f, UiKit.AMBER, false, UiKit.Align.LEFT)
                .place(rcx, ry - 6f, UiKit.Z_TEXT));
        root.attachChild(kit.text("TOP PILOTS", 11f, UiKit.FAINT, false, UiKit.Align.RIGHT)
                .place(rcx + rcw, ry - 6f, UiKit.Z_TEXT));
        ry -= 26f;

        float lineH = 44f;
        for (int i = 0; i < LEADERBOARD_ROWS; i++) {
            float rowCy = ry - lineH / 2f - i * lineH;
            ColorRGBA disc = kit.c(0xC9D8C9);
            Geometry g = kit.fill("RankDisc" + i, 28f, 28f, 14f, disc, disc);
            g.setLocalTranslation(rcx, rowCy - 14f, UiKit.Z_CONTENT);
            root.attachChild(g);
            rankDiscs[i] = g;

            rankNumbers[i] = kit.text(String.valueOf(i + 1), 14f, UiKit.TEXT, false, UiKit.Align.CENTER);
            rankNumbers[i].place(rcx + 14f, rowCy, UiKit.Z_TEXT);
            root.attachChild(rankNumbers[i]);

            rowNames[i] = kit.text("", 16f, UiKit.TEXT, false, UiKit.Align.LEFT);
            rowNames[i].place(rcx + 42f, rowCy + 8f, UiKit.Z_TEXT);
            root.attachChild(rowNames[i]);

            rowDetails[i] = kit.text("", 11f, UiKit.DIM, false, UiKit.Align.LEFT);
            rowDetails[i].place(rcx + 42f, rowCy - 10f, UiKit.Z_TEXT);
            root.attachChild(rowDetails[i]);

            rowScores[i] = kit.text("", 20f, UiKit.AMBER, true, UiKit.Align.RIGHT);
            rowScores[i].place(rcx + rcw, rowCy, UiKit.Z_TEXT);
            root.attachChild(rowScores[i]);
        }
        float boardMidY = ry - lineH * LEADERBOARD_ROWS / 2f;
        emptyTitle = kit.text("No flights recorded yet", 16f, UiKit.TEXT, false, UiKit.Align.CENTER);
        emptyTitle.place(rx + PANEL_W / 2f, boardMidY + 12f, UiKit.Z_TEXT);
        emptyBody = kit.text("Complete a quiz to claim the top spot.", 12f, UiKit.DIM, false, UiKit.Align.CENTER);
        emptyBody.place(rx + PANEL_W / 2f, boardMidY - 10f, UiKit.Z_TEXT);
        root.attachChild(emptyTitle);
        root.attachChild(emptyBody);

        // Mission summary
        float missionY = panelY + PAD;
        float missionH = 96f;
        root.attachChild(kit.inset(rcw, missionH, 12f).place(rcx, missionY, UiKit.Z_CONTENT));
        float colW = rcw / 2f;
        missionQuestions = missionStat("QUESTIONS", rcx + 16f, missionY + missionH - 26f);
        missionLives     = missionStat("LIVES", rcx + colW + 16f, missionY + missionH - 26f);
        missionTimer     = missionStat("TIMER", rcx + 16f, missionY + 26f);
        missionTerrain   = missionStat("TERRAIN", rcx + colW + 16f, missionY + 26f);

        // ══ Launch ════════════════════════════════════════════════════════════
        float startW = 380f;
        float startH = 64f;
        add(kit.button(UiKit.Style.PRIMARY, "START FLIGHT", (uiW - startW) / 2f, 58f, startW, startH, 24f, hud::launchFlight));
        root.attachChild(kit.shadowText("ENTER  launch      TAB  next vehicle      ↑ ↓  callsign preset      type to rename",
                13f, UiKit.DIM, false, UiKit.Align.CENTER).place(uiW / 2f, 30f, UiKit.Z_TEXT));
    }

    /** Adds a small label+value pair to the mission card and returns the (mutable) value text. */
    private UiKit.Text missionStat(String label, float x, float cy) {
        root.attachChild(kit.text(label, 10f, UiKit.FAINT, false, UiKit.Align.LEFT).place(x, cy + 11f, UiKit.Z_TEXT));
        UiKit.Text value = kit.text("-", 17f, UiKit.TEXT, false, UiKit.Align.LEFT);
        value.place(x, cy - 8f, UiKit.Z_TEXT);
        root.attachChild(value);
        return value;
    }

    @Override
    protected void onShow() {
        refreshLeaderboard();
        refreshMission();
    }

    private void refreshMission() {
        QuizSettings s = QuizLoader.getSettings();
        missionQuestions.setText(String.valueOf(QuizLoader.load("questions.txt").size()));
        int lives = s != null ? s.getMaxWrong() : 3;
        missionLives.setText(lives < 0 ? "∞" : String.valueOf(lives));
        float t = s != null ? s.getTimePerQuestion() : 15f;
        missionTimer.setText(t <= 0 ? "OFF" : (int) t + " s");
        missionTerrain.setText(s != null && s.getTerrain() != null ? s.getTerrain().toUpperCase() : "GRASS");
    }

    private void refreshLeaderboard() {
        QuizDatabase db = hud.app() != null ? hud.app().getQuizDatabase() : null;
        List<QuizDatabase.ScoreRecord> tops = db != null ? db.getTopScores(LEADERBOARD_ROWS) : new ArrayList<>();
        boolean empty = tops == null || tops.isEmpty();
        emptyTitle.setCullHint(empty ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        emptyBody.setCullHint(empty ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        for (int i = 0; i < LEADERBOARD_ROWS; i++) {
            boolean has = !empty && i < tops.size();
            var hint = has ? Spatial.CullHint.Inherit : Spatial.CullHint.Always;
            rankDiscs[i].setCullHint(hint);
            rankNumbers[i].setCullHint(hint);
            rowNames[i].setCullHint(hint);
            rowDetails[i].setCullHint(hint);
            rowScores[i].setCullHint(hint);
            if (!has) continue;
            QuizDatabase.ScoreRecord rec = tops.get(i);
            ColorRGBA disc = switch (i) {
                case 0  -> kit.c(0xF5C542);
                case 1  -> kit.c(0xC7D0DC);
                case 2  -> kit.c(0xD08A52);
                default -> kit.c(0xC9D8C9);
            };
            UiKit.remesh(rankDiscs[i], UiShapes.roundedFill(28f, 28f, 14f, disc, disc));
            rankNumbers[i].setColor(i < 3 ? kit.c(0x1A1305) : kit.c(UiKit.TEXT));
            rowNames[i].setText(kit.fit(rec.playerName(), 16f, false, 150f));
            String date = rec.playedAt() != null && rec.playedAt().length() >= 10 ? rec.playedAt().substring(0, 10) : "";
            rowDetails[i].setText(rec.correctAnswers() + "/" + rec.totalQuestions() + " correct   ·   " + date);
            rowScores[i].setText(String.valueOf(rec.score()));
        }
    }

    @Override
    protected void update(float tpf) {
        String name = hud.callsign();
        callsignText.setText(name);

        float textW = callsignText.width();
        float caretX = fieldX + 16f + textW + 2f;
        if (caretX != lastCaretX) {
            lastCaretX = caretX;
            caret.setLocalTranslation(caretX, caret.getLocalTranslation().y, UiKit.Z_TEXT);
        }
        caret.setCullHint(hud.caretVisible() ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);

        QuizSettings.AircraftType current = hud.aircraft();
        for (int i = 0; i < vehicles.length; i++) {
            boolean sel = vehicles[i] == current;
            vehicleButtons[i].setSelected(sel);
            vehicleMarks[i].setCullHint(sel ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }
}
