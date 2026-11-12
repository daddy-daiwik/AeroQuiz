import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.Color;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates the AeroQuiz UI bitmap fonts (AngelCode BMFont text format, which jME's BitmapFontLoader reads).
 *
 * <p>The glyph outlines come from the Inter typeface (SIL Open Font License 1.1). Java2D cannot select
 * weights from a variable font, so heavier weights are produced by stroking the outline.
 *
 * <p>Usage: {@code java tools/FontGen.java /path/to/InterVariable.ttf game/src/main/resources/Interface/Fonts}
 */
public class FontGen {

    /** name, atlas font size (px), extra embolden stroke (px at that size). */
    private static final Object[][] FACES = {
        {"AeroUI",      36f, 1.3f},   // body text: roughly "SemiBold"
        {"AeroDisplay", 64f, 4.2f},   // titles and big numbers: roughly "ExtraBold"
    };

    private record Glyph(int id, Shape shape, double minX, double minY, double w, double h, float advance) {}
    private record Placed(Glyph g, int x, int y, int pw, int ph) {}

    public static void main(String[] args) throws Exception {
        File ttf = new File(args[0]);
        File outDir = new File(args[1]);
        outDir.mkdirs();
        Font base = Font.createFont(Font.TRUETYPE_FONT, ttf);
        for (Object[] face : FACES) {
            generate(base, (String) face[0], (float) face[1], (float) face[2], outDir);
        }
    }

    private static List<Integer> charset() {
        List<Integer> ids = new ArrayList<>();
        for (int c = 32; c <= 126; c++) ids.add(c);
        for (int c = 160; c <= 255; c++) ids.add(c);
        int[] extra = {
            0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x2039, 0x203A,
            0x2190, 0x2191, 0x2192, 0x2193, 0x2713, 0x221E, 0x2605, 0x25B6, 0x25C0, 0x25BA, 0x25C4,
            0x25CF, 0x25CB, 0x25A0, 0x25A1, 0x25B2, 0x25BC, 0x2665, 0x21BA, 0x232B
        };
        for (int c : extra) ids.add(c);
        return ids;
    }

    private static void generate(Font base, String name, float size, float stroke, File outDir) throws Exception {
        Font font = base.deriveFont(size);
        FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);
        LineMetrics lm = font.getLineMetrics("Hg", frc);
        int lineHeight = (int) Math.ceil(lm.getAscent() + lm.getDescent() + lm.getLeading());
        int baseLine = (int) Math.ceil(lm.getAscent());
        int pad = 3 + (int) Math.ceil(stroke / 2f);

        List<Glyph> glyphs = new ArrayList<>();
        for (int id : charset()) {
            if (!font.canDisplay(id)) continue;
            GlyphVector gv = font.createGlyphVector(frc, new String(Character.toChars(id)));
            Shape outline = gv.getOutline();
            Rectangle2D b = outline.getBounds2D();
            float adv = gv.getGlyphMetrics(0).getAdvance() + stroke * 0.7f;
            boolean blank = b.isEmpty();
            glyphs.add(new Glyph(id, outline, b.getX(), b.getY(),
                    blank ? 0 : b.getWidth(), blank ? 0 : b.getHeight(), adv));
        }

        // Shelf-pack into the smallest power-of-two atlas that fits.
        int atlas = 256;
        List<Placed> placed;
        while ((placed = pack(glyphs, atlas, pad)) == null) atlas *= 2;

        BufferedImage img = new BufferedImage(atlas, atlas, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        File fnt = new File(outDir, name + ".fnt");
        try (PrintWriter w = new PrintWriter(fnt, "UTF-8")) {
            w.printf("info face=\"%s\" size=%d bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0%n",
                    name, (int) size);
            w.printf("common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0%n", lineHeight, baseLine, atlas, atlas);
            w.printf("page id=0 file=\"%s.png\"%n", name);
            w.printf("chars count=%d%n", glyphs.size());
            for (Placed p : placed) {
                Glyph g = p.g();
                int originX = p.x() + pad - (int) Math.floor(g.minX());   // pen origin inside the atlas
                int originY = p.y() + pad - (int) Math.floor(g.minY());   // baseline inside the atlas
                if (g.w() > 0) {
                    var t = g2.getTransform();
                    g2.translate(originX, originY);
                    g2.fill(g.shape());
                    if (stroke > 0) g2.draw(g.shape());
                    g2.setTransform(t);
                }
                int xoff = (int) Math.floor(g.minX()) - pad;
                int yoff = baseLine + (int) Math.floor(g.minY()) - pad;
                w.printf("char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15%n",
                        g.id(), p.x(), p.y(), p.pw(), p.ph(), xoff, yoff, Math.round(g.advance()));
            }
            List<String> kerns = kerning(font, glyphs, frc, stroke);
            w.printf("kernings count=%d%n", kerns.size());
            for (String k : kerns) w.println(k);
        }
        g2.dispose();
        ImageIO.write(img, "png", new File(outDir, name + ".png"));
        System.out.printf("%s: %d glyphs, atlas %dx%d, lineHeight=%d base=%d%n",
                name, glyphs.size(), atlas, atlas, lineHeight, baseLine);
    }

    private static List<Placed> pack(List<Glyph> glyphs, int atlas, int pad) {
        List<Placed> out = new ArrayList<>();
        int x = 1, y = 1, rowH = 0;
        for (Glyph g : glyphs) {
            int pw = (int) Math.ceil(g.w()) + pad * 2 + 1;
            int ph = (int) Math.ceil(g.h()) + pad * 2 + 1;
            if (g.w() == 0) { pw = 0; ph = 0; }
            if (x + pw + 1 > atlas) { x = 1; y += rowH + 1; rowH = 0; }
            if (y + ph + 1 > atlas) return null;
            out.add(new Placed(g, x, y, pw, ph));
            x += pw + 1;
            rowH = Math.max(rowH, ph);
        }
        return out;
    }

    /** Pair kerning measured from Java2D's own layout (non-zero pairs among printable ASCII only). */
    private static List<String> kerning(Font font, List<Glyph> glyphs, FontRenderContext frc, float stroke) {
        Font kf = font.deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));
        List<String> out = new ArrayList<>();
        for (int a = 33; a <= 126; a++) {
            float advA = font.createGlyphVector(frc, String.valueOf((char) a)).getGlyphMetrics(0).getAdvance();
            for (int b = 33; b <= 126; b++) {
                String pair = "" + (char) a + (char) b;
                GlyphVector gv = kf.layoutGlyphVector(frc, pair.toCharArray(), 0, 2, Font.LAYOUT_LEFT_TO_RIGHT);
                double amount = gv.getGlyphPosition(1).getX() - advA;
                if (Math.abs(amount) >= 0.5f) {
                    out.add(String.format("kerning first=%d second=%d amount=%d", a, b, Math.round(amount)));
                }
            }
        }
        return out;
    }
}
