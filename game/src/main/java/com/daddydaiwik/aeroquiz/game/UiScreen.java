package com.daddydaiwik.aeroquiz.game;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for a full HUD screen. A screen owns a scene-graph root and a list of buttons; because the
 * buttons carry their own hit rectangles, the clickable area can never drift out of sync with what is drawn.
 *
 * <p>Screens are rebuilt from scratch when the window is resized, so they must keep no state of their own
 * that cannot be re-derived from {@link HudState}, the game state or an editor model.
 */
abstract class UiScreen {

    protected final HudState hud;
    protected final UiKit kit;
    /** Width/height of the UI space (the window size divided by the UI scale). */
    protected final float uiW;
    protected final float uiH;

    final Node root;
    private final List<UiKit.Button> buttons = new ArrayList<>();

    protected UiScreen(String name, HudState hud, UiKit kit, float uiW, float uiH) {
        this.hud = hud;
        this.kit = kit;
        this.uiW = uiW;
        this.uiH = uiH;
        this.root = new Node(name);
        root.setCullHint(Spatial.CullHint.Always);
    }

    protected <T extends UiKit.Button> T add(T button) {
        buttons.add(button);
        root.attachChild(button);
        return button;
    }

    protected void remove(UiKit.Button button) {
        buttons.remove(button);
        button.removeFromParent();
    }

    final boolean isVisible() {
        return root.getCullHint() != Spatial.CullHint.Always;
    }

    final void setVisible(boolean visible) {
        boolean was = isVisible();
        root.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (visible && !was) onShow();
    }

    /** Called when the screen goes from hidden to visible. */
    protected void onShow() {}

    /** Per-frame update while the screen is visible. */
    protected void update(float tpf) {}

    /** The topmost interactive button under the pointer, or null. */
    final UiKit.Button buttonAt(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UiKit.Button b = buttons.get(i);
            if (b.contains(x, y)) return b;
        }
        return null;
    }

    final void tickButtons(float tpf, UiKit.Button hovered) {
        for (UiKit.Button b : buttons) {
            b.tick(tpf, b == hovered);
        }
    }

    /** Convenience: top-edge-relative y (distance from the top of the UI space) to a bottom-origin y. */
    protected final float fromTop(float distance) {
        return uiH - distance;
    }
}
