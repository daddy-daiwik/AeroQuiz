package com.daddydaiwik.aeroquiz.game;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * In-flight overlay: lives, question card with countdown, score/progress, and the answer buttons along the
 * bottom (colour-coded to match the gates floating in the sky).
 */
final class FlightHudScreen extends UiScreen {

    private static final float EDGE = 28f;
    private static final float QUESTION_PX = 24f;
    private static final float POP_DURATION = 1.1f;
    private static final int MAX_PIPS = 8;

    // Lives
    private final UiKit.Text livesInfinite;
    private final Geometry[] pips = new Geometry[MAX_PIPS];
    private final Geometry[] pipRings = new Geometry[MAX_PIPS];
    private final int[] pipState = new int[MAX_PIPS];   // 0 = hidden, 1 = lost, 2..4 = healthy tints
    private final UiKit.Text livesLabel;

    // Question + timer
    private UiKit.Card questionCard;
    private final List<UiKit.Text> questionLines = new ArrayList<>();
    private final UiKit.Bar timerBar;
    private final UiKit.Text timerText;
    private final float cardW;
    private final float cardTop;

    // Score + progress
    private final UiKit.Text scoreValue;
    private final UiKit.Text progressText;
    private final UiKit.Bar progressBar;

    // Answer buttons
    private final UiKit.Text hint;
    private final List<UiKit.Button> answerButtons = new ArrayList<>();
    private QuizQuestion boundQuestion;

    // Feedback
    private int lastScore = 0;
    private float scorePop = 0f;
    private final List<Popup> popups = new ArrayList<>();
    private final ColorRGBA tintGreen, tintAmber, tintRed, tintLost, tintMint;

    private static final class Popup {
        final UiKit.Text text;
        final float x, y;
        float age;
        Popup(UiKit.Text text, float x, float y) { this.text = text; this.x = x; this.y = y; }
    }

    FlightHudScreen(HudState hud, UiKit kit, float uiW, float uiH) {
        super("FlightHud", hud, kit, uiW, uiH);

        tintGreen = kit.c(0x6FD39A);
        tintAmber = kit.c(0xF2C94C);
        tintRed   = kit.c(0xF0786C);
        tintLost  = kit.c(UiKit.PAPER_2);
        tintMint  = kit.c(0x6FD39A);

        // ── Lives (top-left panel, mirrors the score panel) ───────────────────
        float lw = 220f, lh = 76f;
        float ly = uiH - 20f - lh;
        root.attachChild(kit.panel(lw, lh).place(EDGE, ly, UiKit.Z_PANEL));
        livesLabel = kit.text("LIVES", 11f, UiKit.DIM, false, UiKit.Align.LEFT);
        livesLabel.place(EDGE + 18f, ly + lh - 20f, UiKit.Z_TEXT);
        root.attachChild(livesLabel);
        for (int i = 0; i < MAX_PIPS; i++) {
            Geometry g = kit.fill("Pip" + i, 22f, 22f, 11f, ColorRGBA.White, ColorRGBA.White);
            g.setLocalTranslation(EDGE + 18f + i * 24f, ly + 16f, UiKit.Z_CONTENT);
            g.setCullHint(Spatial.CullHint.Always);
            root.attachChild(g);
            pips[i] = g;
            Geometry ring = kit.ring("PipRing" + i, 22f, 22f, 11f, 2f, kit.c(UiKit.INK));
            ring.setLocalTranslation(EDGE + 18f + i * 24f, ly + 16f, UiKit.Z_CONTENT + 0.5f);
            ring.setCullHint(Spatial.CullHint.Always);
            root.attachChild(ring);
            pipRings[i] = ring;
        }
        livesInfinite = kit.text("\u221E", 34f, UiKit.INK, true, UiKit.Align.LEFT);
        livesInfinite.place(EDGE + 18f, ly + 28f, UiKit.Z_TEXT);
        root.attachChild(livesInfinite);

        // ── Question card + timer (top-centre) ────────────────────────────────
        cardW = Math.min(uiW - 2f * 300f, 760f);
        cardTop = uiH - 20f;
        questionCard = kit.panel(cardW, 80f);
        questionCard.place((uiW - cardW) / 2f, cardTop - 80f, UiKit.Z_PANEL);
        root.attachChild(questionCard);

        timerBar = new UiKit.Bar(kit, cardW * 0.62f, 8f);
        timerBar.setLocalTranslation((uiW - cardW * 0.62f) / 2f - 24f, cardTop - 100f, UiKit.Z_CONTENT);
        root.attachChild(timerBar);
        timerText = kit.shadowText("", 18f, UiKit.GREEN, true, UiKit.Align.LEFT);
        timerText.place((uiW + cardW * 0.62f) / 2f - 8f, cardTop - 96f, UiKit.Z_TEXT);
        root.attachChild(timerText);

        // ── Score + progress (top-right) ──────────────────────────────────────
        float sw = 220f, sh = 92f;
        float sx = uiW - EDGE - sw;
        float sy = uiH - 20f - sh;
        root.attachChild(kit.panel(sw, sh).place(sx, sy, UiKit.Z_PANEL));
        root.attachChild(kit.text("SCORE", 11f, UiKit.DIM, false, UiKit.Align.LEFT).place(sx + 18f, sy + sh - 20f, UiKit.Z_TEXT));
        scoreValue = kit.text("0", 36f, UiKit.INK, true, UiKit.Align.LEFT);
        scoreValue.place(sx + 18f, sy + sh - 50f, UiKit.Z_TEXT);
        root.attachChild(scoreValue);
        progressText = kit.text("", 11f, UiKit.DIM, false, UiKit.Align.RIGHT);
        progressText.place(sx + sw - 18f, sy + sh - 20f, UiKit.Z_TEXT);
        root.attachChild(progressText);
        progressBar = new UiKit.Bar(kit, sw - 36f, 6f);
        progressBar.setLocalTranslation(sx + 18f, sy + 16f, UiKit.Z_CONTENT);
        root.attachChild(progressBar);
        progressBar.set(0f, tintMint);

        // ── Hint ──────────────────────────────────────────────────────────────
        hint = kit.shadowText("", 13f, UiKit.TEXT, false, UiKit.Align.CENTER);
        hint.place(uiW / 2f, 24f + 60f + 18f, UiKit.Z_TEXT);
        root.attachChild(hint);
    }

    @Override
    protected void onShow() {
        GameState gs = hud.gameState();
        lastScore = gs != null ? gs.getScore() : 0;
        boundQuestion = null;
    }

    @Override
    protected void update(float tpf) {
        GameState gs = hud.gameState();
        if (gs == null) return;

        updateScore(gs, tpf);
        updateLives(gs);

        QuizQuestion q = gs.getCurrentQuestion();
        if (q != boundQuestion) bindQuestion(q, gs);
        updateTimer(gs);
        updatePopups(tpf);
    }

    // ── Score / progress ──────────────────────────────────────────────────────

    private void updateScore(GameState gs, float tpf) {
        int score = gs.getScore();
        scoreValue.setText(String.valueOf(score));
        if (score > lastScore) {
            scorePop = 0.35f;
            spawnPopup("+" + (score - lastScore));
            hud.flashGreen();
        }
        lastScore = score;

        scorePop = Math.max(0f, scorePop - tpf);
        float s = 1f + 0.35f * (scorePop / 0.35f);
        scoreValue.setLocalScale(s, s, 1f);

        int total = Math.max(1, gs.getTotalQuestions());
        int cur = Math.min(gs.getCurrentQuestionNumber(), total);
        progressText.setText("QUESTION " + cur + " / " + total);
        float done = Math.max(0, cur - 1) / (float) total;
        progressBar.set(done, tintMint);
    }

    private void spawnPopup(String text) {
        UiKit.Text t = kit.shadowText(text, 26f, UiKit.GREEN, true, UiKit.Align.LEFT);
        float x = uiW - EDGE - 220f + 120f;
        float y = uiH - 20f - 50f;
        t.place(x, y, UiKit.Z_POPUP);
        root.attachChild(t);
        popups.add(new Popup(t, x, y));
    }

    private void updatePopups(float tpf) {
        for (Iterator<Popup> it = popups.iterator(); it.hasNext(); ) {
            Popup p = it.next();
            p.age += tpf;
            float t = p.age / POP_DURATION;
            if (t >= 1f) {
                p.text.removeFromParent();
                it.remove();
                continue;
            }
            float ease = 1f - (1f - t) * (1f - t);
            p.text.place(p.x, p.y - 34f * ease, UiKit.Z_POPUP);
            float alpha = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
            p.text.setColor(kit.c(UiKit.GREEN, alpha));
        }
    }

    // ── Lives ─────────────────────────────────────────────────────────────────

    private void updateLives(GameState gs) {
        int max = gs.getMaxWrong();
        boolean infinite = max < 0;
        livesInfinite.setCullHint(infinite ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        int shown = infinite ? 0 : Math.min(max, MAX_PIPS);
        int left = Math.max(0, max - gs.getWrongHits());

        for (int i = 0; i < MAX_PIPS; i++) {
            if (i >= shown) {
                if (pipState[i] != 0) {
                    pips[i].setCullHint(Spatial.CullHint.Always);
                    pipRings[i].setCullHint(Spatial.CullHint.Always);
                    pipState[i] = 0;
                }
                continue;
            }
            int state;
            if (i >= left) state = 1;
            else state = left <= 1 ? 4 : left == 2 && max > 3 ? 3 : 2;
            if (pipState[i] != state) {
                pipState[i] = state;
                pips[i].setCullHint(Spatial.CullHint.Inherit);
                pipRings[i].setCullHint(Spatial.CullHint.Inherit);
                ColorRGBA tint = switch (state) {
                    case 1  -> tintLost;
                    case 3  -> tintAmber;
                    case 4  -> tintRed;
                    default -> tintGreen;
                };
                pips[i].getMaterial().setColor("Color", tint);
            }
        }
    }

    // ── Question card, timer, answers ─────────────────────────────────────────

    private void bindQuestion(QuizQuestion q, GameState gs) {
        boundQuestion = q;
        for (UiKit.Text t : questionLines) t.removeFromParent();
        questionLines.clear();
        for (UiKit.Button b : answerButtons) remove(b);
        answerButtons.clear();

        if (q == null) {
            questionCard.setCullHint(Spatial.CullHint.Always);
            hint.setText("");
            return;
        }

        // Question card: shrink the type until it fits in at most 3 lines.
        float px = QUESTION_PX;
        List<String> lines = kit.wrap(q.getQuestion(), px, false, cardW - 56f, 3);
        while (lines.size() > 2 && px > 18f) {
            px -= 2f;
            lines = kit.wrap(q.getQuestion(), px, false, cardW - 56f, 3);
        }
        float lineH = px * 1.3f;
        float cardH = lines.size() * lineH + 34f;
        questionCard.setCullHint(Spatial.CullHint.Inherit);
        questionCard.resize(cardW, cardH);
        questionCard.place((uiW - cardW) / 2f, cardTop - cardH, UiKit.Z_PANEL);
        for (int i = 0; i < lines.size(); i++) {
            UiKit.Text t = kit.text(lines.get(i), px, UiKit.TEXT, false, UiKit.Align.CENTER);
            t.place(uiW / 2f, cardTop - 17f - lineH * (i + 0.5f), UiKit.Z_TEXT);
            root.attachChild(t);
            questionLines.add(t);
        }
        float barY = cardTop - cardH - 26f;
        timerBar.setLocalTranslation(timerBar.getLocalTranslation().x, barY, UiKit.Z_CONTENT);
        timerText.place(timerText.getLocalTranslation().x, barY + 4f, UiKit.Z_TEXT);

        // Answer buttons
        int count = Math.max(2, Math.min(6, q.getOptionCount()));
        float totalW = Math.min(uiW - 60f, 1120f);
        float gap = 12f;
        float bw = (totalW - gap * (count - 1)) / count;
        float bh = 60f;
        float startX = (uiW - totalW) / 2f;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            ColorRGBA gate = ObstacleSpawner.getColorForKey(q.getKeyAt(i), i);
            UiKit.Button b = add(kit.button(UiKit.Style.ROW, "", startX + i * (bw + gap), 24f, bw, bh, 15f,
                    () -> hud.answerOption(idx)));
            b.leftAlignLabel(58f);
            b.label().setText(kit.fit(q.getOptionAt(i), 15f, false, bw - 74f));

            Geometry badge = kit.fill("AnswerBadge" + i, 32f, 32f, 16f, gate, gate);
            badge.setLocalTranslation(14f, (bh - 32f) / 2f, 1f);
            b.attachChild(badge);
            b.attachChild(kit.text(String.valueOf(i + 1), 16f, 0x0A0F1A, true, UiKit.Align.CENTER)
                    .place(14f + 16f, bh / 2f, 1.2f));
            Geometry bar = kit.fill("AnswerBar" + i, bw - 36f, 3f, 1.5f, gate, gate);
            bar.setLocalTranslation(18f, 6f, 1f);
            b.attachChild(bar);
            answerButtons.add(b);
        }
        hint.setText("Press 1–" + count + " or click a gate to answer");
    }

    private void updateTimer(GameState gs) {
        QuizSettings s = QuizLoader.getSettings();
        boolean show = gs.getPhase() == GameState.Phase.PLAYING
                && s != null && s.getTimePerQuestion() > 0f && gs.getQuestionTimer() >= 0f
                && boundQuestion != null;
        timerBar.setCullHint(show ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        timerText.setCullHint(show ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (!show) return;

        float remaining = gs.getQuestionTimer();
        float frac = UiKit.clamp01(remaining / s.getTimePerQuestion());
        ColorRGBA tint = frac > 0.5f ? tintGreen : frac > 0.25f ? tintAmber : tintRed;
        timerBar.set(frac, tint);
        timerText.setText((int) Math.ceil(remaining) + "s");
        timerText.setColor(frac > 0.5f ? kit.c(UiKit.GREEN) : frac > 0.25f ? kit.c(UiKit.AMBER) : kit.c(UiKit.RED));
    }
}
