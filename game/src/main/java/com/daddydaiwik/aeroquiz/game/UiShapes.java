package com.daddydaiwik.aeroquiz.game;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;

/**
 * Procedural 2-D meshes for the HUD: rounded rectangles (optionally with a vertical colour gradient)
 * and rounded outline rings. All meshes are built in a local space whose origin is the bottom-left corner.
 */
final class UiShapes {

    private UiShapes() {}

    /** Number of straight segments used to approximate each rounded corner. */
    static final int CORNER_SEGMENTS = 8;

    /**
     * Perimeter points (x,y pairs, counter-clockwise) of a rounded rectangle inset by {@code inset} on every side.
     * The inset outline is a true offset (corner radius shrinks accordingly), so it also works for sharp corners.
     */
    private static float[] perimeter(float w, float h, float r, float inset) {
        float[] pts = new float[4 * (CORNER_SEGMENTS + 1) * 2];
        float rr = Math.max(0f, r - inset);
        float x0 = inset, y0 = inset, x1 = w - inset, y1 = h - inset;
        float[][] centers = {{x1 - rr, y0 + rr}, {x1 - rr, y1 - rr}, {x0 + rr, y1 - rr}, {x0 + rr, y0 + rr}};
        float[] startDeg = {-90f, 0f, 90f, 180f};
        int k = 0;
        for (int c = 0; c < 4; c++) {
            for (int s = 0; s <= CORNER_SEGMENTS; s++) {
                float a = (startDeg[c] + 90f * s / CORNER_SEGMENTS) * FastMath.DEG_TO_RAD;
                pts[k++] = centers[c][0] + rr * FastMath.cos(a);
                pts[k++] = centers[c][1] + rr * FastMath.sin(a);
            }
        }
        return pts;
    }

    private static float clampRadius(float w, float h, float r) {
        return Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
    }

    /**
     * Filled rounded rectangle; colour fades from {@code bottom} (y=0) to {@code top} (y=h).
     *
     * <p>Triangulated as a ladder between the left and right outlines (which are mirror images, so matching
     * rungs share the same y). That keeps the colour a pure function of y, unlike a fan from the centre.
     */
    static Mesh roundedFill(float w, float h, float r, ColorRGBA top, ColorRGBA bottom) {
        r = clampRadius(w, h, r);
        float[] p = perimeter(w, h, r, 0f);
        int s = CORNER_SEGMENTS;
        int rungs = 2 * s + 2;

        float[] pos = new float[rungs * 2 * 3];
        float[] col = new float[rungs * 2 * 4];
        for (int k = 0; k < rungs; k++) {
            // perimeter order: arc0 = bottom-right, arc1 = top-right, arc2 = top-left, arc3 = bottom-left
            int rightPt = k <= s ? k : (s + 1) + (k - (s + 1));
            int leftPt  = k <= s ? 3 * (s + 1) + (s - k) : 2 * (s + 1) + (s - (k - (s + 1)));
            for (int side = 0; side < 2; side++) {
                int pt = side == 0 ? rightPt : leftPt;
                int v = k * 2 + side;
                float y = p[pt * 2 + 1];
                pos[v * 3]     = p[pt * 2];
                pos[v * 3 + 1] = y;
                putColor(col, v, mix(bottom, top, h <= 0f ? 0f : y / h));
            }
        }
        int[] idx = new int[(rungs - 1) * 6];
        for (int k = 0; k < rungs - 1; k++) {
            int r0 = k * 2, l0 = r0 + 1, r1 = (k + 1) * 2, l1 = r1 + 1;
            idx[k * 6]     = r0;
            idx[k * 6 + 1] = r1;
            idx[k * 6 + 2] = l1;
            idx[k * 6 + 3] = r0;
            idx[k * 6 + 4] = l1;
            idx[k * 6 + 5] = l0;
        }
        return build(pos, col, idx);
    }

    /** Rounded outline of the given thickness drawn just inside the {@code w x h} rectangle. */
    static Mesh roundedRing(float w, float h, float r, float thickness, ColorRGBA color) {
        r = clampRadius(w, h, r);
        float[] outer = perimeter(w, h, r, 0f);
        float[] inner = perimeter(w, h, r, thickness);
        int n = outer.length / 2;

        float[] pos = new float[n * 2 * 3];
        float[] col = new float[n * 2 * 4];
        for (int i = 0; i < n; i++) {
            pos[(i * 2) * 3]         = outer[i * 2];
            pos[(i * 2) * 3 + 1]     = outer[i * 2 + 1];
            pos[(i * 2 + 1) * 3]     = inner[i * 2];
            pos[(i * 2 + 1) * 3 + 1] = inner[i * 2 + 1];
            putColor(col, i * 2, color);
            putColor(col, i * 2 + 1, color);
        }
        int[] idx = new int[n * 6];
        for (int i = 0; i < n; i++) {
            int o0 = i * 2, i0 = o0 + 1;
            int o1 = ((i + 1) % n) * 2, i1 = o1 + 1;
            idx[i * 6]     = o0;
            idx[i * 6 + 1] = o1;
            idx[i * 6 + 2] = i1;
            idx[i * 6 + 3] = o0;
            idx[i * 6 + 4] = i1;
            idx[i * 6 + 5] = i0;
        }
        return build(pos, col, idx);
    }

    private static Mesh build(float[] pos, float[] col, int[] idx) {
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.Color, 4, col);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();
        m.updateCounts();
        return m;
    }

    private static void putColor(float[] buf, int vertex, ColorRGBA c) {
        buf[vertex * 4]     = c.r;
        buf[vertex * 4 + 1] = c.g;
        buf[vertex * 4 + 2] = c.b;
        buf[vertex * 4 + 3] = c.a;
    }

    static ColorRGBA mix(ColorRGBA a, ColorRGBA b, float t) {
        t = FastMath.clamp(t, 0f, 1f);
        return new ColorRGBA(
                a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t,
                a.b + (b.b - a.b) * t, a.a + (b.a - a.a) * t);
    }
}
