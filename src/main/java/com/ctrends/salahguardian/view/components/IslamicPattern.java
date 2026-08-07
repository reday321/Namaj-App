package com.ctrends.salahguardian.view.components;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Decorative backdrop that tiles the classical eight pointed star - the
 * {@code khatim} motif found in Islamic geometric ornament - across an
 * arbitrarily sized area.
 *
 * <p>The pattern is generated rather than shipped as a bitmap so it stays crisp
 * on any display scaling, and so the overlay has no image asset to fail to
 * load. It is drawn once per resize on a {@link Canvas}, which costs nothing
 * during the countdown itself.</p>
 *
 * @author CTrends Software
 */
public class IslamicPattern extends Canvas {

    /** Edge length of one repeating tile, in pixels. */
    private static final double TILE = 96.0;

    private final Color strokeColor;
    private final double strokeAlpha;

    /**
     * Creates the backdrop.
     *
     * <p>A {@link Canvas} has no preferred size and is not resizable, so the
     * caller is expected to bind {@link #widthProperty()} and
     * {@link #heightProperty()} to the container it should fill. The pattern
     * repaints itself whenever either changes.</p>
     *
     * @param strokeColor colour of the pattern lines
     * @param strokeAlpha opacity of the lines, {@code 0..1}
     */
    public IslamicPattern(Color strokeColor, double strokeAlpha) {
        this.strokeColor = strokeColor;
        this.strokeAlpha = strokeAlpha;
        widthProperty().addListener((obs, was, is) -> redraw());
        heightProperty().addListener((obs, was, is) -> redraw());
        setMouseTransparent(true);
    }

    /**
     * Repaints the whole surface.
     */
    public void redraw() {
        double width = getWidth();
        double height = getHeight();
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        if (width <= 0 || height <= 0) {
            return;
        }
        gc.setStroke(strokeColor.deriveColor(0, 1, 1, strokeAlpha));
        gc.setLineWidth(1.1);

        for (double y = -TILE; y < height + TILE; y += TILE) {
            for (double x = -TILE; x < width + TILE; x += TILE) {
                drawStar(gc, x + TILE / 2, y + TILE / 2, TILE * 0.42);
                drawStar(gc, x + TILE, y + TILE, TILE * 0.18);
            }
        }
    }

    /**
     * Draws one eight pointed star as two overlaid squares rotated 45 degrees
     * from one another, which is how the motif is constructed traditionally.
     *
     * @param gc     the surface to draw on
     * @param cx     centre x
     * @param cy     centre y
     * @param radius distance from the centre to a point
     */
    private void drawStar(GraphicsContext gc, double cx, double cy, double radius) {
        drawRotatedSquare(gc, cx, cy, radius, 0);
        drawRotatedSquare(gc, cx, cy, radius, Math.PI / 4);
    }

    private void drawRotatedSquare(GraphicsContext gc, double cx, double cy,
                                   double radius, double rotation) {
        double[] xs = new double[4];
        double[] ys = new double[4];
        for (int corner = 0; corner < 4; corner++) {
            double angle = rotation + corner * Math.PI / 2;
            xs[corner] = cx + radius * Math.cos(angle);
            ys[corner] = cy + radius * Math.sin(angle);
        }
        gc.strokePolygon(xs, ys, 4);
    }
}
