package com.daddydaiwik.aeroquiz.game;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * Small widget toolkit for the 2-D HUD: gradient rounded cards, buttons with hover feedback,
 * text with optional drop-shadow, and progress bars.
 *
 * <p>Colours are authored as sRGB hex values and converted here, because jME 3.9 renders with gamma
 * correction enabled and would otherwise show every hand-picked colour noticeably washed out.
 *
 * <p>All widgets are positioned in "UI space": a coordinate system with the origin at the bottom-left
 * of the screen, scaled uniformly by {@link HudState}. Layering is controlled by z (higher is drawn on top).
 */
final class UiKit {

    // ── Theme: flat pastel, sharp corners, ink outlines ───────────────────────
    /** Corner rounding multiplier. 0 = sharp squares (current theme); 1 = the old rounded look. */
    static final float ROUNDING = 0f;
    static final float BORDER = 2.5f;
    /** Offset of the hard drop-shadow under panels (UI px). */
    static final float SHADOW = 5f;

    static final int INK     = 0x1F2D26;   // near-black green: text, outlines, shadows
    static final int PAPER   = 0xF3F7EE;   // panel fill
    static final int PAPER_2 = 0xE0EBD9;   // inset fields, tracks
    static final int MINT    = 0xA8E6C1;   // primary
    static final int BUTTER  = 0xFBE7A1;
    static final int SKY     = 0xAAD8F4;
    static final int CORAL   = 0xF5A9A0;
    static final int LIME    = 0xCDEFA8;

    static final int TEXT   = INK;
    static final int DIM    = 0x56695E;
    static final int FAINT  = 0x879A8E;
    static final int ACCENT = 0x2F9E6A;    // deep green for small marks and headings
    static final int AMBER  = 0xB07400;
    static final int GREEN  = 0x2F9E6A;
    static final int RED    = 0xD2554B;

    // ── Layers ────────────────────────────────────────────────────────────────
    static final float Z_SCRIM   = 0f;
    static final float Z_PANEL   = 2f;
    static final float Z_CONTENT = 4f;
    static final float Z_TEXT    = 6f;
    static final float Z_POPUP   = 12f;

    private final AssetManager assets;
    private final boolean gamma;
    final BitmapFont fontUi;
    final BitmapFont fontDisplay;

    UiKit(AssetManager assets, boolean gammaCorrection) {
        this.assets = assets;
        this.gamma = gammaCorrection;
        BitmapFont ui;
        BitmapFont display;
        try {
            ui = assets.loadFont("Interface/Fonts/AeroUI.fnt");
            display = assets.loadFont("Interface/Fonts/AeroDisplay.fnt");
        } catch (Exception e) {
            // Fall back to jME's built-in font so the game stays playable if the font assets are missing.
            ui = assets.loadFont("Interface/Fonts/Default.fnt");
            display = ui;
        }
        this.fontUi = ui;
        this.fontDisplay = display;
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    ColorRGBA c(int hex) {
        return c(hex, 1f);
    }

    ColorRGBA c(int hex, float alpha) {
        float r = ((hex >> 16) & 0xFF) / 255f;
        float g = ((hex >> 8) & 0xFF) / 255f;
        float b = (hex & 0xFF) / 255f;
        return gamma ? new ColorRGBA().setAsSrgb(r, g, b, alpha) : new ColorRGBA(r, g, b, alpha);
    }

    /** Converts an already-linear game colour (e.g. a gate colour) to a UI colour with the given alpha. */
    static ColorRGBA withAlpha(ColorRGBA base, float alpha) {
        return new ColorRGBA(base.r, base.g, base.b, alpha);
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private Material material() {
        Material mat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        mat.setColor("Color", ColorRGBA.White);
        RenderState rs = mat.getAdditionalRenderState();
        rs.setBlendMode(RenderState.BlendMode.Alpha);
        rs.setDepthTest(false);
        rs.setDepthWrite(false);
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        return mat;
    }

    /** Filled rounded rectangle with a vertical gradient. */
    Geometry fill(String name, float w, float h, float radius, ColorRGBA top, ColorRGBA bottom) {
        Geometry g = new Geometry(name, UiShapes.roundedFill(w, h, radius * ROUNDING, top, bottom));
        g.setMaterial(material());
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        return g;
    }

    /** Rounded outline. */
    Geometry ring(String name, float w, float h, float radius, float thickness, ColorRGBA color) {
        Geometry g = new Geometry(name, UiShapes.roundedRing(w, h, radius * ROUNDING, thickness, color));
        g.setMaterial(material());
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        return g;
    }

    static void remesh(Geometry g, Mesh mesh) {
        g.setMesh(mesh);
        g.updateModelBound();
    }

    // ── Text measuring ────────────────────────────────────────────────────────

    float measure(String s, float px, boolean display) {
        BitmapFont f = display ? fontDisplay : fontUi;
        return f.getLineWidth(s) * px / f.getCharSet().getRenderedSize();
    }

    /** Truncates with an ellipsis so the text fits within {@code maxWidth}. */
    String fit(String s, float px, boolean display, float maxWidth) {
        if (s == null) return "";
        if (measure(s, px, display) <= maxWidth) return s;
        String t = s;
        while (t.length() > 1 && measure(t + "…", px, display) > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t.stripTrailing() + "…";
    }

    /** Greedy word-wrap into at most {@code maxLines} lines (the last line is ellipsised if needed). */
    java.util.List<String> wrap(String s, float px, boolean display, float maxWidth, int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (measure(candidate, px, display) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        if (lines.size() > maxLines) {
            String rest = String.join(" ", lines.subList(maxLines - 1, lines.size()));
            lines = new java.util.ArrayList<>(lines.subList(0, maxLines - 1));
            lines.add(rest);
        }
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, fit(lines.get(i), px, display, maxWidth));
        }
        return lines;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Text
    // ═════════════════════════════════════════════════════════════════════════

    enum Align { LEFT, CENTER, RIGHT }

    /**
     * A single line of text anchored by its horizontal anchor point and the vertical centre of its capital
     * letters, so that text can be centred in a button or row without per-font fudge factors.
     */
    static final class Text extends Node {
        private final UiKit kit;
        private final BitmapFont font;
        private final boolean display;
        private final float px;
        private final Align align;
        private final BitmapText main;
        private final BitmapText shadow;
        private final float capCenter;
        private String text = "";
        private ColorRGBA color;

        Text(UiKit kit, String text, float px, ColorRGBA color, boolean display, Align align, boolean shadowed) {
            super("Text");
            this.kit = kit;
            this.display = display;
            this.font = display ? kit.fontDisplay : kit.fontUi;
            this.px = px;
            this.align = align;
            // distance from the top of the text line to the middle of the capital letters (Inter cap height ~0.727em)
            float baseRatio = (float) font.getCharSet().getBase() / font.getCharSet().getRenderedSize();
            this.capCenter = (baseRatio - 0.3635f) * px;

            this.color = color.clone();
            main = new BitmapText(font);
            main.setSize(px);
            main.setColor(color);
            if (shadowed) {
                shadow = new BitmapText(font);
                shadow.setSize(px);
                shadow.setColor(kit.c(PAPER, 0.95f));
                attachChild(shadow);
            } else {
                shadow = null;
            }
            attachChild(main);
            setText(text);
        }

        Text setText(String s) {
            if (s == null) s = "";
            if (s.equals(text)) return this;
            text = s;
            main.setText(s);
            if (shadow != null) shadow.setText(s);
            float lw = main.getLineWidth();
            float ox = align == Align.LEFT ? 0f : align == Align.CENTER ? -lw / 2f : -lw;
            main.setLocalTranslation(ox, capCenter, 0f);
            if (shadow != null) shadow.setLocalTranslation(ox + px * 0.05f, capCenter - px * 0.07f, -0.01f);
            return this;
        }

        Text setColor(ColorRGBA c) {
            if (c.equals(color)) return this;     // BitmapText re-uploads vertex colours on every call
            color = c.clone();
            main.setColor(color);
            return this;
        }

        String getText() { return text; }

        float width() { return kit.measure(text, px, display); }

        /** Places the text anchor at (x, cy) with the given layer. */
        Text place(float x, float cy, float z) {
            setLocalTranslation(x, cy, z);
            return this;
        }
    }

    Text text(String s, float px, int hex, boolean display, Align align) {
        return new Text(this, s, px, c(hex), display, align, false);
    }

    Text shadowText(String s, float px, int hex, boolean display, Align align) {
        return new Text(this, s, px, c(hex), display, align, true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Card
    // ═════════════════════════════════════════════════════════════════════════

    /** A rounded panel with gradient fill, hairline border and an optional soft shadow. */
    static final class Card extends Node {
        private final UiKit kit;
        private final boolean shadowed;
        private final Geometry shadowNear;
        private final Geometry fill;
        private final Geometry ring;
        float w, h, r;
        private ColorRGBA top, bottom, border;
        private float ringThickness = BORDER;

        Card(UiKit kit, float w, float h, float r, ColorRGBA top, ColorRGBA bottom, ColorRGBA border, boolean shadowed) {
            super("Card");
            this.kit = kit;
            this.w = w;
            this.h = h;
            this.r = r;
            this.top = top;
            this.bottom = bottom;
            this.border = border;
            this.shadowed = shadowed;
            shadowNear = kit.fill("ShadowNear", w, h, r, ColorRGBA.Black, ColorRGBA.Black);
            fill = kit.fill("CardFill", w, h, r, top, bottom);
            ring = kit.ring("CardRing", w, h, r, BORDER, border);
            if (shadowed) attachChild(shadowNear);
            attachChild(fill);
            attachChild(ring);
            fill.setLocalTranslation(0f, 0f, 0.2f);
            ring.setLocalTranslation(0f, 0f, 0.3f);
            rebuild();
        }

        private void rebuild() {
            remesh(fill, UiShapes.roundedFill(w, h, r * ROUNDING, top, bottom));
            remesh(ring, UiShapes.roundedRing(w, h, r * ROUNDING, ringThickness, border));
            if (shadowed) {
                // one hard, offset block of ink: reads as a flat "sticker" rather than a soft glow
                remesh(shadowNear, UiShapes.roundedFill(w, h, r * ROUNDING, kit.c(INK, 0.9f), kit.c(INK, 0.9f)));
                shadowNear.setLocalTranslation(SHADOW, -SHADOW, 0.1f);
            }
        }

        Card resize(float w, float h) {
            if (w == this.w && h == this.h) return this;
            this.w = w;
            this.h = h;
            rebuild();
            return this;
        }

        Card setColors(ColorRGBA top, ColorRGBA bottom, ColorRGBA border) {
            this.top = top;
            this.bottom = bottom;
            this.border = border;
            rebuild();
            return this;
        }

        Card place(float x, float y, float z) {
            setLocalTranslation(x, y, z);
            return this;
        }
    }

    Card panel(float w, float h) {
        return new Card(this, w, h, 16f, c(PAPER, 0.97f), c(PAPER, 0.97f), c(INK), true);
    }

    Card inset(float w, float h, float r) {
        return new Card(this, w, h, r, c(PAPER_2), c(PAPER_2), c(INK, 0.45f), false);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Button
    // ═════════════════════════════════════════════════════════════════════════

    enum Style { PRIMARY, SECONDARY, GHOST, DANGER, SUCCESS, CHIP, ROW }

    /** A clickable rounded button. Position and size are in absolute UI-space coordinates. */
    static final class Button extends Node {
        final float x, y, w, h;
        private final UiKit kit;
        private final Style style;
        private final Card face;
        private final Geometry overlay;
        private Runnable onClick;
        private boolean selected;
        private float hover;
        private float pulse;
        private float lastOverlay = -1f;
        private final float px;
        private Text label;

        Button(UiKit kit, Style style, String text, float x, float y, float w, float h, float px, Runnable onClick) {
            super("Button_" + text);
            this.px = px;
            this.kit = kit;
            this.style = style;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.onClick = onClick;

            float radius = Math.min(h / 2f, style == Style.CHIP ? 9f : 12f);
            face = new Card(kit, w, h, radius, top(false), bottom(false), border(false), false);
            attachChild(face);

            overlay = kit.fill("Overlay", w, h, radius, ColorRGBA.White, ColorRGBA.White);
            overlay.getMaterial().setColor("Color", kit.c(INK, 0f));
            overlay.setLocalTranslation(0f, 0f, 0.6f);
            attachChild(overlay);

            label = new Text(kit, text, px, textColor(), style == Style.PRIMARY, Align.CENTER, false);
            label.place(w / 2f, h / 2f, 1f);
            attachChild(label);

            setLocalTranslation(x, y, Z_CONTENT);
        }

        /** Flat fill colour for the style; selected buttons turn mint. */
        private ColorRGBA fillColor(boolean sel) {
            if (sel) return kit.c(MINT);
            return switch (style) {
                case PRIMARY   -> kit.c(MINT);
                case SECONDARY -> kit.c(SKY);
                case GHOST     -> kit.c(PAPER, 0.92f);
                case DANGER    -> kit.c(CORAL);
                case SUCCESS   -> kit.c(LIME);
                case CHIP      -> kit.c(BUTTER);
                case ROW       -> kit.c(PAPER_2);
            };
        }

        private ColorRGBA top(boolean sel) { return fillColor(sel); }
        private ColorRGBA bottom(boolean sel) { return fillColor(sel); }

        private ColorRGBA border(boolean sel) {
            if (style == Style.ROW && !sel) return kit.c(INK, 0.8f);
            if (style == Style.GHOST && !sel) return kit.c(INK, 0.7f);
            return kit.c(INK);
        }

        private ColorRGBA textColor() {
            return kit.c(INK);
        }

        Button setSelected(boolean sel) {
            if (sel == selected) return this;
            selected = sel;
            face.setColors(top(sel), bottom(sel), border(sel));
            return this;
        }

        boolean isSelected() { return selected; }

        Button setText(String s) {
            label.setText(s);
            return this;
        }

        Text label() { return label; }

        Button onClick(Runnable r) {
            this.onClick = r;
            return this;
        }

        /** Moves the label from the centre to a left-aligned position inside the button. */
        Button leftAlignLabel(float padding) {
            detachChild(label);
            label = new Text(kit, label.getText(), px, textColor(), false, Align.LEFT, false);
            label.place(padding, h / 2f, 1f);
            attachChild(label);
            return this;
        }

        boolean isInteractive() {
            for (Spatial s = this; s != null; s = s.getParent()) {
                if (s.getCullHint() == CullHint.Always) return false;
            }
            return true;
        }

        boolean contains(float px, float py) {
            return isInteractive() && px >= x && px <= x + w && py >= y && py <= y + h;
        }

        void click() {
            pulse = 1f;
            if (onClick != null) onClick.run();
        }

        void tick(float tpf, boolean hovered) {
            float target = hovered ? 1f : 0f;
            hover += (target - hover) * Math.min(1f, tpf * 16f);
            pulse = Math.max(0f, pulse - tpf * 4.5f);
            float a = 0.10f * hover + 0.20f * pulse;
            if (Math.abs(a - lastOverlay) > 0.004f) {
                lastOverlay = a;
                overlay.getMaterial().setColor("Color", kit.c(INK, a));
            }
        }
    }

    Button button(Style style, String text, float x, float y, float w, float h, float px, Runnable onClick) {
        return new Button(this, style, text, x, y, w, h, px, onClick);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Progress bar
    // ═════════════════════════════════════════════════════════════════════════

    /** A horizontal progress bar with a rounded track and fill. */
    static final class Bar extends Node {
        private final UiKit kit;
        private final float w, h;
        private final Geometry track;
        private final Geometry fill;
        private float shownWidth = -1f;
        private ColorRGBA shownColor;

        Bar(UiKit kit, float w, float h) {
            super("Bar");
            this.kit = kit;
            this.w = w;
            this.h = h;
            track = kit.fill("BarTrack", w, h, h / 2f, kit.c(PAPER), kit.c(PAPER));
            fill = kit.fill("BarFill", w - 4f, h - 4f, h / 2f, ColorRGBA.White, ColorRGBA.White);
            fill.setLocalTranslation(2f, 2f, 0.2f);
            Geometry outline = kit.ring("BarRing", w, h, h / 2f, 2f, kit.c(INK));
            outline.setLocalTranslation(0f, 0f, 0.4f);
            attachChild(track);
            attachChild(fill);
            attachChild(outline);
            set(0f, kit.c(ACCENT));
        }

        float width() { return w; }

        void set(float fraction, ColorRGBA color) {
            float innerW = w - 4f;
            float fw = Math.max(0f, Math.min(1f, fraction)) * innerW;
            if (fw < 0.5f) {
                fill.setCullHint(CullHint.Always);
                shownWidth = 0f;
                return;
            }
            fill.setCullHint(CullHint.Inherit);
            if (Math.abs(fw - shownWidth) >= 0.75f) {
                shownWidth = fw;
                remesh(fill, UiShapes.roundedFill(fw, h - 4f, (h / 2f) * ROUNDING, ColorRGBA.White, ColorRGBA.White));
            }
            if (shownColor != color) {
                shownColor = color;
                fill.getMaterial().setColor("Color", color);
            }
        }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    /** A flat, solid rectangle (no rounding) – used for full-screen scrims and gradients. */
    Geometry rect(String name, float w, float h, ColorRGBA top, ColorRGBA bottom) {
        return fill(name, w, h, 0f, top, bottom);
    }

    static float clamp01(float v) {
        return FastMath.clamp(v, 0f, 1f);
    }
}
