package com.daddydaiwik.aeroquiz.game;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

/** View for the in-game quiz & settings editor. All editing logic lives in {@link EditorModel}. */
final class EditorScreen extends UiScreen {

    private static final float CARD_W = 960f;
    private static final float PAD = 28f;
    private static final float ROW_H = 44f;
    private static final float FIELD_TEXT_PX = 16f;

    private final EditorModel model;

    // Setting steppers
    private final UiKit.Text terrainValue;
    private final UiKit.Text livesValue;
    private final UiKit.Text timerValue;
    private final UiKit.Text vehicleValue;

    // Question header
    private final UiKit.Text questionHeader;

    // Field rows: row 0 is the prompt, rows 1-4 are the RED/BLUE/GREEN/YELLOW options
    private final EditorModel.Field[] rowFields = new EditorModel.Field[5];
    private final UiKit.Button[] fieldButtons = new UiKit.Button[5];
    private final UiKit.Button[] editButtons = new UiKit.Button[5];
    private final UiKit.Button[] starButtons = new UiKit.Button[5];
    private final Geometry caret;

    // Edit toolbar + footer
    private final List<UiKit.Button> toolbar = new ArrayList<>();
    private final UiKit.Text toolbarHint;
    private final UiKit.Text statusText;

    EditorScreen(HudState hud, UiKit kit, float uiW, float uiH, EditorModel model) {
        super("EditorScreen", hud, kit, uiW, uiH);
        this.model = model;

        // Dim the world behind the editor a little so the card reads clearly.
        Geometry dim = kit.rect("EditorDim", uiW, uiH, kit.c(UiKit.PAPER, 0.28f), kit.c(UiKit.PAPER, 0.28f));
        dim.setLocalTranslation(0f, 0f, UiKit.Z_SCRIM);
        root.attachChild(dim);

        float cardH = Math.min(650f, uiH - 40f);
        float cardX = (uiW - CARD_W) / 2f;
        float cardY = (uiH - cardH) / 2f;
        float top = cardY + cardH;
        float cx = cardX + PAD;
        float cw = CARD_W - PAD * 2f;
        root.attachChild(kit.panel(CARD_W, cardH).place(cardX, cardY, UiKit.Z_PANEL));

        // ── Header ────────────────────────────────────────────────────────────
        root.attachChild(kit.text("QUIZ & SETTINGS EDITOR", 22f, UiKit.TEXT, true, UiKit.Align.LEFT)
                .place(cx, top - 38f, UiKit.Z_TEXT));
        add(kit.button(UiKit.Style.GHOST, "‹  Back", cx + cw - 110f, top - 58f, 110f, 36f, 14f, hud::closeEditor));

        // ── Flight parameter steppers ─────────────────────────────────────────
        float y = top - 78f;
        float stripH = 72f;
        float stripY = y - stripH;
        root.attachChild(kit.inset(cw, stripH, 14f).place(cx, stripY, UiKit.Z_CONTENT));
        float cell = cw / 4f;
        terrainValue = stepper("TERRAIN", cx + cell * 0f, stripY, cell, () -> model.stepTerrain(-1), () -> model.stepTerrain(1));
        livesValue   = stepper("LIVES",   cx + cell * 1f, stripY, cell, () -> model.stepLives(-1),   () -> model.stepLives(1));
        timerValue   = stepper("TIME PER QUESTION", cx + cell * 2f, stripY, cell, () -> model.stepTimer(-1), () -> model.stepTimer(1));
        vehicleValue = stepper("VEHICLE", cx + cell * 3f, stripY, cell, () -> model.stepVehicle(-1), () -> model.stepVehicle(1));

        // ── Question navigation ───────────────────────────────────────────────
        y = stripY - 24f;
        questionHeader = kit.text("", 13f, UiKit.AMBER, false, UiKit.Align.LEFT);
        questionHeader.place(cx, y - 16f, UiKit.Z_TEXT);
        root.attachChild(questionHeader);

        float bh = 32f;
        float by = y - bh;
        float bx = cx + cw;
        bx -= 92f;  add(kit.button(UiKit.Style.DANGER,    "Delete",    bx, by, 92f, bh, 13f, model::deleteQuestion));
        bx -= 100f; add(kit.button(UiKit.Style.SUCCESS,   "+ New",     bx, by, 92f, bh, 13f, model::newQuestion));
        bx -= 100f; add(kit.button(UiKit.Style.SECONDARY, "Next ›", bx, by, 92f, bh, 13f, () -> model.stepQuestion(1)));
        bx -= 100f; add(kit.button(UiKit.Style.SECONDARY, "‹ Prev", bx, by, 92f, bh, 13f, () -> model.stepQuestion(-1)));

        // ── Prompt + option rows ──────────────────────────────────────────────
        y = by - 14f;
        float editW = 80f;
        float starW = 52f;
        float badge = ROW_H;
        float fieldX = cx + badge + 10f;
        float fieldW = cw - badge - 10f - (editW + 8f) - (starW + 8f);

        rowFields[0] = EditorModel.Field.QUESTION_PROMPT;
        for (int i = 0; i < 4; i++) rowFields[i + 1] = model.optionField(i);

        for (int r = 0; r < 5; r++) {
            final EditorModel.Field f = rowFields[r];
            float ry = y - ROW_H;

            // badge: "Q" for the prompt, gate colour + number for options
            ColorRGBA badgeColor = r == 0 ? kit.c(UiKit.SKY) : ObstacleSpawner.getColorForKey(EditorModel.OPTION_KEYS[r - 1], r - 1);
            Geometry badgeGeo = kit.fill("Badge" + r, badge, badge, 12f, badgeColor, badgeColor);
            badgeGeo.setLocalTranslation(cx, ry, UiKit.Z_CONTENT);
            root.attachChild(badgeGeo);
            root.attachChild(kit.text(r == 0 ? "Q" : String.valueOf(r), 18f, UiKit.INK, true, UiKit.Align.CENTER)
                    .place(cx + badge / 2f, ry + badge / 2f, UiKit.Z_TEXT));

            UiKit.Button field = add(kit.button(UiKit.Style.ROW, "", fieldX, ry, fieldW, ROW_H, FIELD_TEXT_PX, () -> model.toggleEdit(f)));
            field.leftAlignLabel(16f);
            fieldButtons[r] = field;

            if (r > 0) {
                final String key = EditorModel.OPTION_KEYS[r - 1];
                UiKit.Button star = add(kit.button(UiKit.Style.CHIP, "★", fieldX + fieldW + 8f, ry, starW, ROW_H, 20f, () -> model.setCorrect(key)));
                starButtons[r] = star;
            }
            editButtons[r] = add(kit.button(UiKit.Style.SECONDARY, "Edit", cx + cw - editW, ry, editW, ROW_H, 14f, () -> model.toggleEdit(f)));
            y = ry - 8f;
        }

        caret = kit.fill("EditorCaret", 2f, 22f, 1f, kit.c(UiKit.ACCENT), kit.c(UiKit.ACCENT));
        caret.setCullHint(Spatial.CullHint.Always);
        root.attachChild(caret);

        // ── Edit toolbar (touch-friendly) ─────────────────────────────────────
        float tbH = 34f;
        float tby = y - 4f - tbH;
        toolbarHint = kit.text("", 12f, UiKit.DIM, false, UiKit.Align.LEFT);
        toolbarHint.place(cx, tby + tbH / 2f, UiKit.Z_TEXT);
        root.attachChild(toolbarHint);

        float tx = cx + cw;
        tx -= 100f; toolbar.add(add(kit.button(UiKit.Style.SUCCESS,   "✓  Done",  tx, tby, 100f, tbH, 13f, model::commit)));
        tx -= 112f; toolbar.add(add(kit.button(UiKit.Style.SECONDARY, "Next field",   tx, tby, 104f, tbH, 13f, model::cycleNextField)));
        tx -= 92f;  toolbar.add(add(kit.button(UiKit.Style.GHOST,     "Clear",        tx, tby, 84f, tbH, 13f, model::clearBuffer)));
        tx -= 92f;  toolbar.add(add(kit.button(UiKit.Style.GHOST,     "⌫  Delete", tx, tby, 84f, tbH, 13f, model::backspace)));

        // ── Footer ────────────────────────────────────────────────────────────
        float saveH = 48f;
        float saveY = cardY + 22f;
        statusText = kit.text("", 13f, UiKit.GREEN, false, UiKit.Align.LEFT);
        statusText.place(cx, saveY + saveH / 2f, UiKit.Z_TEXT);
        root.attachChild(statusText);
        add(kit.button(UiKit.Style.PRIMARY, "Save Quiz & Settings", cx + cw - 270f, saveY, 270f, saveH, 16f, model::save));
    }

    /** Builds a "label ‹ value ›" cell inside the settings strip and returns the value text. */
    private UiKit.Text stepper(String label, float cellX, float stripY, float cellW, Runnable prev, Runnable next) {
        root.attachChild(kit.text(label, 10f, UiKit.FAINT, false, UiKit.Align.LEFT)
                .place(cellX + 16f, stripY + 58f, UiKit.Z_TEXT));
        float chip = 28f;
        float cy = stripY + 26f;
        add(kit.button(UiKit.Style.CHIP, "‹", cellX + 12f, cy - chip / 2f, chip, chip, 18f, prev));
        add(kit.button(UiKit.Style.CHIP, "›", cellX + cellW - 12f - chip - 6f, cy - chip / 2f, chip, chip, 18f, next));
        UiKit.Text value = kit.text("", 15f, UiKit.TEXT, false, UiKit.Align.CENTER);
        value.place(cellX + (cellW - 6f) / 2f, cy, UiKit.Z_TEXT);
        root.attachChild(value);
        return value;
    }

    @Override
    protected void update(float tpf) {
        terrainValue.setText(model.terrainLabel());
        livesValue.setText(model.livesLabel());
        timerValue.setText(model.timerLabel());
        vehicleValue.setText(model.vehicleLabel());

        questionHeader.setText(model.hasQuestions()
                ? "QUESTION " + model.questionNumber() + " OF " + model.questionCount()
                : "NO QUESTIONS — PRESS + NEW");

        QuizQuestion q = model.current();
        boolean editing = model.isEditing();
        boolean blink = hud.caretVisible();
        caret.setCullHint(Spatial.CullHint.Always);

        for (int r = 0; r < 5; r++) {
            EditorModel.Field f = rowFields[r];
            boolean active = model.active() == f;
            UiKit.Button fb = fieldButtons[r];
            float maxW = fb.w - 36f;
            String raw = model.valueOf(f);
            String shown = active ? tail(raw, maxW) : kit.fit(raw, FIELD_TEXT_PX, false, maxW);
            fb.setText(shown);
            fb.setSelected(active);
            editButtons[r].setText(active ? "Done" : "Edit");
            editButtons[r].setSelected(active);

            if (active && blink) {
                float caretX = fb.x + 16f + kit.measure(shown, FIELD_TEXT_PX, false) + 2f;
                caret.setLocalTranslation(caretX, fb.y + (fb.h - 22f) / 2f, UiKit.Z_TEXT);
                caret.setCullHint(Spatial.CullHint.Inherit);
            }

            if (r > 0) {
                boolean correct = q != null && EditorModel.OPTION_KEYS[r - 1].equalsIgnoreCase(q.getCorrectColor());
                starButtons[r].setSelected(correct);
                starButtons[r].label().setColor(correct ? kit.c(UiKit.AMBER) : kit.c(UiKit.FAINT));
            }
        }

        for (UiKit.Button b : toolbar) {
            b.setCullHint(editing ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
        toolbarHint.setText(editing
                ? "TAB next field   ·   ENTER save field   ·   ESC cancel"
                : "Click a field to edit it   ·   ★ marks the correct answer");

        statusText.setText(kit.fit(model.status(), 13f, false, 480f));
        statusText.setColor(model.statusIsError() ? kit.c(UiKit.RED) : kit.c(UiKit.GREEN));
    }

    /** Shows the end of a string that is too wide, prefixed with an ellipsis. */
    private String tail(String s, float maxW) {
        if (kit.measure(s, FIELD_TEXT_PX, false) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && kit.measure("…" + t, FIELD_TEXT_PX, false) > maxW) t = t.substring(1);
        return "…" + t;
    }
}
