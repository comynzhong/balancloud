package com.balancloud.system.captcha;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 纯绘制生成拼图验证码素材，不依赖外部图片，避免第三方素材授权问题。
 */
@Component
public class SliderCaptchaGenerator {

    public static final int CANVAS_WIDTH = 320;
    public static final int CANVAS_HEIGHT = 140;
    public static final int PIECE_SIZE = 48;
    public static final int KNOB_RADIUS = 9;
    public static final int MAX_OFFSET = CANVAS_WIDTH - PIECE_SIZE - KNOB_RADIUS;

    private static final int MIN_TARGET = PIECE_SIZE * 2;
    private static final int MARGIN_Y = 10;

    private final SecureRandom random = new SecureRandom();

    public Challenge generate() {
        int targetX = MIN_TARGET + random.nextInt(MAX_OFFSET - MIN_TARGET + 1);
        int pieceY = MARGIN_Y + random.nextInt(CANVAS_HEIGHT - PIECE_SIZE - MARGIN_Y * 2 + 1);
        BufferedImage base = paintBackground();
        return new Challenge(
                toDataUrl(punchHole(base, targetX, pieceY), "jpg", "image/jpeg"),
                toDataUrl(cutPiece(base, targetX, pieceY), "png", "image/png"),
                pieceY,
                targetX);
    }

    public record Challenge(String backgroundImage, String pieceImage, int pieceY, int targetX) {
    }

    private BufferedImage paintBackground() {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(5, 44, 35),
                CANVAS_WIDTH, CANVAS_HEIGHT, new Color(9, 110, 84)));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        g.setComposite(AlphaComposite.SrcOver.derive(0.34f));
        for (int i = 0; i < 7; i++) {
            int radius = 26 + random.nextInt(64);
            int cx = random.nextInt(CANVAS_WIDTH);
            int cy = random.nextInt(CANVAS_HEIGHT);
            Color tint = i % 2 == 0 ? new Color(0, 226, 158) : new Color(126, 255, 218);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius,
                    new float[]{0f, 1f},
                    new Color[]{tint, new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 0)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        }

        g.setComposite(AlphaComposite.SrcOver.derive(0.09f));
        g.setColor(new Color(214, 255, 243));
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x <= CANVAS_WIDTH; x += 22) {
            g.drawLine(x, 0, x, CANVAS_HEIGHT);
        }
        for (int y = 0; y <= CANVAS_HEIGHT; y += 22) {
            g.drawLine(0, y, CANVAS_WIDTH, y);
        }

        g.setComposite(AlphaComposite.SrcOver.derive(0.3f));
        for (int i = 0; i < 320; i++) {
            g.setColor(new Color(255, 255, 255, 70 + random.nextInt(120)));
            g.fillRect(random.nextInt(CANVAS_WIDTH), random.nextInt(CANVAS_HEIGHT), 1, 1);
        }
        g.dispose();
        return image;
    }

    private BufferedImage punchHole(BufferedImage base, int x, int y) {
        BufferedImage image = copyOf(base);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape hole = shapeAt(x, y);
        g.setColor(new Color(2, 24, 19, 150));
        g.fill(hole);
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(3f));
        g.draw(hole);
        g.setColor(new Color(255, 255, 255, 145));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(hole);
        g.dispose();
        return image;
    }

    private BufferedImage cutPiece(BufferedImage base, int x, int y) {
        BufferedImage piece = new BufferedImage(PIECE_SIZE + KNOB_RADIUS, CANVAS_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = piece.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape shape = shapeAt(0, y);
        g.setClip(shape);
        g.drawImage(base, -x, 0, null);
        g.setClip(null);
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(3f));
        g.draw(shape);
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(shape);
        g.dispose();
        return piece;
    }

    private Shape shapeAt(int x, int y) {
        Area area = new Area(new RoundRectangle2D.Double(0, 0, PIECE_SIZE, PIECE_SIZE, 14, 14));
        area.add(new Area(new Ellipse2D.Double(PIECE_SIZE - KNOB_RADIUS,
                PIECE_SIZE / 2.0 - KNOB_RADIUS, KNOB_RADIUS * 2.0, KNOB_RADIUS * 2.0)));
        area.subtract(new Area(new Ellipse2D.Double(PIECE_SIZE / 2.0 - KNOB_RADIUS, -KNOB_RADIUS,
                KNOB_RADIUS * 2.0, KNOB_RADIUS * 2.0)));
        return AffineTransform.getTranslateInstance(x, y).createTransformedShape(area);
    }

    private BufferedImage copyOf(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    private String toDataUrl(BufferedImage image, String format, String mimeType) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render slider captcha", e);
        }
    }
}
