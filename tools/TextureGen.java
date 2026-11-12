import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Generates the flat, pastel world textures used by AeroQuiz: one equirectangular sky and four tileable
 * ground textures (grass, sand, snow, water). Everything is drawn procedurally so it is original art with
 * no licence strings attached.
 *
 * <p>Usage: {@code java -Djava.awt.headless=true tools/TextureGen.java game/src/main/resources/Textures/Aero}
 */
public class TextureGen {

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        ImageIO.write(sky(4096, 2048), "png", new File(out, "sky.png"));
        ImageIO.write(ground(0xB8E5AA, 0xA8DB9B, 0x8FCB84, Style.GRASS, 11), "png", new File(out, "ground_grass.png"));
        ImageIO.write(ground(0xF5E4B6, 0xEBD7A0, 0xD9C282, Style.DUNES, 12), "png", new File(out, "ground_sand.png"));
        ImageIO.write(ground(0xF6FAFD, 0xE4EEF8, 0xCADBEB, Style.SPARKLE, 13), "png", new File(out, "ground_snow.png"));
        ImageIO.write(ground(0x9ED5F1, 0x8CC9EC, 0xFFFFFF, Style.WAVES, 14), "png", new File(out, "ground_water.png"));
        System.out.println("textures written to " + out);
    }

    private static Color c(int rgb) { return new Color(rgb); }
    private static Color c(int rgb, int alpha) { return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha); }

    private static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Sky: equirectangular, y=0 is straight up, y=h/2 is the horizon
    // ═════════════════════════════════════════════════════════════════════════

    private static BufferedImage sky(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(img);
        int horizon = h / 2;

        // Banded (posterised) gradient: flat colour steps feel hand-drawn rather than photographic.
        int[] bands = {0x6FB7EA, 0x80C3EE, 0x93CEF1, 0xA6D8F3, 0xBAE2F4, 0xCFEAF2, 0xE3F1EA, 0xF4F3DF, 0xFCEFD0, 0xFDE8C9};
        int bandH = horizon / bands.length;
        for (int i = 0; i < bands.length; i++) {
            g.setColor(c(bands[i]));
            g.fillRect(0, i * bandH, w, bandH + 1);
        }
        g.setColor(c(bands[bands.length - 1]));
        g.fillRect(0, bands.length * bandH, w, horizon - bands.length * bandH + 1);

        // Lower hemisphere: pale haze, only ever seen behind the camera or past the ground.
        g.setPaint(new GradientPaint(0, horizon, c(0xFDE8C9), 0, h, c(0xE6F3DA)));
        g.fillRect(0, horizon, w, h - horizon);

        // Sun: flat disc with two concentric halos, repeated nowhere (a single bright spot).
        int sunX = w / 2 + 200, sunY = horizon - 190;
        for (int i = 3; i >= 0; i--) {
            int r = 70 + i * 55;
            g.setColor(c(0xFFF6D8, i == 0 ? 255 : 50 + (3 - i) * 8));
            g.fill(new Ellipse2D.Double(sunX - r, sunY - r, r * 2, r * 2));
        }

        Random rnd = new Random(7);
        // Far and near hill silhouettes along the horizon
        hills(g, w, horizon, rnd, 0xCDE6DD, 70, 0.0035, 1);
        hills(g, w, horizon, rnd, 0xB4DCC4, 46, 0.0055, 2);
        hills(g, w, horizon, rnd, 0x9FD3B0, 26, 0.0085, 3);

        // Flat clouds: clumps of overlapping circles on a flat base, with a flat underside shade.
        // The camera only sees roughly +-22 degrees around the horizon (~250px here), so keep clouds in that band.
        int clouds = 64;
        for (int i = 0; i < clouds; i++) {
            double cx = (i + rnd.nextDouble() * 0.8) / clouds * w;   // evenly spread so no view direction is empty
            double rise = 70 + Math.pow(rnd.nextDouble(), 0.8) * 330;   // px above the horizon
            double cy = horizon - rise;
            double scale = 0.30 + rnd.nextDouble() * 0.38 + (rise / 330.0) * 0.18;
            cloud(g, cx, cy, scale, rnd);
            if (cx < 300) cloud(g, cx + w, cy, scale, rnd);       // wrap seam
            if (cx > w - 300) cloud(g, cx - w, cy, scale, rnd);
        }
        g.dispose();
        return img;
    }

    private static void hills(Graphics2D g, int w, int horizon, Random rnd, int color, double amp, double freq, int octave) {
        double p1 = rnd.nextDouble() * 6.28, p2 = rnd.nextDouble() * 6.28, p3 = rnd.nextDouble() * 6.28;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0, horizon + 4);
        for (int x = 0; x <= w; x += 8) {
            // build from sines whose period divides the width so the left/right edges meet
            double t = x / (double) w * Math.PI * 2;
            double y = Math.sin(t * (3 * octave) + p1) * 0.5 + Math.sin(t * (7 * octave) + p2) * 0.3 + Math.sin(t * (13 * octave) + p3) * 0.2;
            path.lineTo(x, horizon - amp * 0.55 - y * amp * 0.45);
        }
        path.lineTo(w, horizon + 4);
        path.closePath();
        g.setColor(c(color));
        g.fill(path);
    }

    private static void cloud(Graphics2D g, double cx, double cy, double s, Random rnd) {
        Area body = new Area();
        int puffs = 4 + rnd.nextInt(3);
        double width = 0;
        for (int i = 0; i < puffs; i++) {
            double r = (34 + rnd.nextDouble() * 36) * s;
            double x = cx + width;
            double y = cy - (i % 2 == 0 ? 0 : r * 0.35);
            body.add(new Area(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2)));
            width += r * 1.15;
        }
        double baseH = 34 * s;
        body.add(new Area(new RoundRectangle2D.Double(cx - 30 * s, cy - baseH * 0.2, width + 20 * s, baseH, baseH, baseH)));
        // Cut a flat bottom for a stylised "cartoon cloud" look.
        body.intersect(new Area(new Rectangle2D.Double(cx - 400 * s, cy - 400 * s, 2000 * s, 400 * s + baseH * 0.8)));

        g.setColor(c(0xFFFFFF));
        g.fill(body);
        // underside shade
        Area shade = new Area(body);
        shade.intersect(new Area(new Rectangle2D.Double(cx - 400 * s, cy + baseH * 0.15, 2000 * s, 200 * s)));
        g.setColor(c(0xDCEBF6));
        g.fill(shade);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Ground: seamless, flat two-tone check plus a little hand-drawn detail
    // ═════════════════════════════════════════════════════════════════════════

    private enum Style { GRASS, DUNES, SPARKLE, WAVES }

    private static BufferedImage ground(int a, int b, int accent, Style style, long seed) {
        int n = 512, cell = n / 4;   // 4x4 cells, so the pattern tiles cleanly and reads as motion at speed
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(img);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                g.setColor(c((x + y) % 2 == 0 ? a : b));
                g.fillRect(x * cell, y * cell, cell, cell);
            }
        }
        Random rnd = new Random(seed);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 30; i++) {
            int x = rnd.nextInt(n), y = rnd.nextInt(n);
            // draw each mark at the 9 wrap offsets so tiles stay seamless
            for (int ox = -n; ox <= n; ox += n) {
                for (int oy = -n; oy <= n; oy += n) {
                    mark(g, style, x + ox, y + oy, accent, rnd.nextLong());
                }
            }
        }
        g.dispose();
        return img;
    }

    private static void mark(Graphics2D g, Style style, int x, int y, int accent, long s) {
        Random r = new Random(s);
        switch (style) {
            case GRASS -> {              // little tufts: three short strokes
                g.setColor(c(accent));
                g.drawLine(x, y, x - 5, y - 9);
                g.drawLine(x, y, x, y - 12);
                g.drawLine(x, y, x + 5, y - 9);
            }
            case DUNES -> {              // soft wind ripples
                g.setColor(c(accent));
                Path2D.Double p = new Path2D.Double();
                p.moveTo(x - 20, y);
                p.curveTo(x - 8, y - 9, x + 8, y + 9, x + 20, y);
                g.draw(p);
            }
            case SPARKLE -> {            // tiny snow glints
                g.setColor(c(accent));
                int d = 4 + r.nextInt(5);
                g.fillOval(x, y, d, d);
            }
            case WAVES -> {              // white wave dashes
                g.setColor(c(accent, 190));
                g.drawLine(x - 16, y, x + 16, y);
                g.drawLine(x - 6, y + 11, x + 22, y + 11);
            }
        }
    }
}
