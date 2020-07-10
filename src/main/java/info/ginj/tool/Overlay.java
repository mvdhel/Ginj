package info.ginj.tool;

import com.jhlabs.image.GaussianFilter;
import info.ginj.util.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

public abstract class Overlay extends JPanel {
    public static final RenderingHints ANTI_ALIASING_ON = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    public static final RenderingHints ANTI_ALIASING_OFF = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

    public static final int HANDLE_WIDTH = 8;
    public static final int HANDLE_HEIGHT = 8;
    public static final int NO_INDEX = -1;

    // Caching
    private BufferedImage shadowImage;
    private BufferedImage handleImg;

    // State
    protected boolean editInProgress = true; // Upon creation, the drag/drop is an edit.
    private boolean selected = false;

    // Actual fields to persist and restore
    private Color color;


    ////////////////////////////////
    // Constructor

    public Overlay() {
        setOpaque(false);
        setLayout(null);
    }


    ////////////////////////////////
    // Accessors

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEditInProgress() {
        return editInProgress;
    }

    public void setEditInProgress(boolean editInProgress) {
        this.editInProgress = editInProgress;
        // purge shadow image so that it is redrawn when editing is finished
        if (editInProgress) {
            shadowImage = null;
        }
    }

    /**
     * This is the main drawing method called to render the component.
     * This method draws:
     * 1. the drop shadow (if required by the overlay and if not dragging/resizing),
     * 2. the overlay itself
     * 3. its handles (if selected)
     * @param g the graphics canvas to draw on
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHints(ANTI_ALIASING_ON);

        // Draw shadow;
        if (!isEditInProgress() && mustDrawShadow()) {
            if (shadowImage == null) {
                BufferedImageOp op = new GaussianFilter(8);
                BufferedImage maskImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                final Graphics2D maskImageG2D = maskImage.createGraphics();
                drawComponent(maskImageG2D, 3, 3);
                maskImageG2D.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 0.7f));
                maskImageG2D.setColor(Color.BLACK);
                maskImageG2D.fillRect(0, 0, getWidth(), getHeight());

                maskImageG2D.dispose();
                shadowImage = op.filter(maskImage, null);
            }
            g2d.drawImage(shadowImage, 0, 0, this);
        }

        // Draw component
        drawComponent(g2d, 0, 0);

        // Draw handles
        if (selected) {
            for (Point handle : getHandles()) {
                drawHandle(g2d, handle);
            }
        }
    }

    private void drawHandle(Graphics2D graphics2D, Point point) {
        if (handleImg == null) {
            // Compute and cache handle graphics
            handleImg = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g2d = handleImg.createGraphics();
            g2d.setRenderingHints(ANTI_ALIASING_OFF);
            // Blueish center square
            g2d.setColor(Util.HANDLE_CENTER_COLOR);
            g2d.fillRect(2, 2, 6,6);

            // 3D effect grey border
            g2d.setStroke( new BasicStroke( 1 ) );
            g2d.setColor(Util.HANDLE_GREY_1_COLOR);
            g2d.drawLine(1, 1, 7, 1);
            g2d.drawLine(1, 2, 1, 7);
            g2d.setColor(Util.HANDLE_GREY_2_COLOR);
            g2d.drawLine(0, 0, 8, 0);
            g2d.drawLine(0, 1, 0, 8);
            g2d.setColor(Util.HANDLE_GREY_3_COLOR);
            g2d.drawLine(8, 1, 8, 7);
            g2d.drawLine(1, 8, 8, 8);
            g2d.setColor(Util.HANDLE_GREY_4_COLOR);
            g2d.drawLine(9, 0, 9, 8);
            g2d.drawLine(0, 9, 9, 9);
            g2d.dispose();
        }
        graphics2D.drawImage(handleImg, point.x - 5, point.y - 5, null);
    }

    /**
     * Hit detection: this method is called to know if a given point is on the overlay (and can be used to select or drag it).
     * Note: this is similar to overriding contains(), except it is called only on click (and not on mouseover),
     * @param point the point to test
     * @return true if the point is on the overlay
     */
    public boolean containsPoint(Point point) {
        // First see if we're in a handle
        if (isSelected() && getHandleIndexAt(point) != NO_INDEX) return true;

        // No. Render the item in an image
        // TODO: should be cached if called often.
        BufferedImage renderedImage = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = (Graphics2D) renderedImage.getGraphics();
        drawComponent(g2d, 0, 0);
        g2d.dispose();

        // And Return true if the pixel at (x,y) is not transparent
        final int rgb = renderedImage.getRGB(point.x, point.y);
        return ((rgb & 0xFF000000) != 0);
    }


    /**
     * This method iterates on all handles of this overlay and returns the index at the given position
     * @param position the location to find a handle
     * @return the index of the found handle, or NO_INDEX if there is no handle at that position
     */
    public int getHandleIndexAt(Point position) {
        final Point[] handles = getHandles();
        for (int i = 0; i < handles.length; i++) {
            Point handle = handles[i];
            // Give tolerance: double handle sizes
            if (position.x >= handle.x - HANDLE_WIDTH/2 && position.x <= handle.x + HANDLE_WIDTH/2
                    && position.y >= handle.y - HANDLE_HEIGHT/2 && position.y <= handle.y + HANDLE_HEIGHT/2) {
                return i;
            }
        }
        return NO_INDEX;
    }


    /**
     * This method is called when the given handle must move to a new position
     * By convention, when a component is first drawn, the end of the drawing (arrowhead or second point of rectangle) is returned with index 0
     * @param handleIndex the index of the handle
     * @param newPosition the new position of that handle
     */
    public final void moveHandle(int handleIndex, Point newPosition) {
        if (handleIndex != NO_INDEX) {
            // This is a move of one handle
            setHandlePosition(handleIndex, newPosition);
            shadowImage = null;
        }
        else {
            System.err.printf("moveHandle with a handleIndex = NO_INDEX");
        }
    }


    /**
     * This method is called when the whole overlay must move to a new position
     * @param deltaX the horizontal offset to draw to move the drawing
     * @param deltaY the vertical offset to draw to move the drawing
     */
    public void moveDrawing(int deltaX, int deltaY) {
        // This is a drag'n'drop move => move all points
        final Point[] handles = getHandles();
        for (int i = 0; i < handles.length; i++) {
            setHandlePosition(i, new Point(handles[i].x + deltaX, handles[i].y + deltaY));
        }
        shadowImage = null;
    }


    /**
     * Indicate if this overlay must have a shadow.
     * Can be overridden to disable shadow (e.g. for the "highlight" overlay)
     * @return true by default, to draw the shadow
     */
    protected boolean mustDrawShadow() {
        return true;
    }



    //////////////////////////////////////////////////////
    // ABSTRACT METHODS TO BE IMPLEMENTED BY ALL OVERLAYS
    //

    /**
     * Returns a short String describing this Overlay
     * @return the name to present
     */
    @java.beans.Transient
    public abstract String getPresentationName();


    /**
     * This method is  called just after instantiating the component, to provide it's initial position and color
     * @param initialPoint the initial position of the Overlay
     * @param initialColor the initial color of the Overlay
     * @return
     */
    public abstract Overlay initialize(Point initialPoint, Color initialColor);


    /**
     * This method must draw the raw component on the given canvas
     * @param g2d the graphics canvas to draw on
     * @param xOffset an optional horizontal offset to shift the drawing, for example to create the drop shadow
     * @param yOffset an optional vertical offset to shift the drawing, for example to create the drop shadow
     */
    public abstract void drawComponent(Graphics2D g2d, int xOffset, int yOffset);


    /**
     * Returns all handles of the component. Handles are squares displayed over the selected overlay, providing ways to change its shape.
     * By convention, when a component is first drawn, getHandles()[0] is the handle at the "end" of the drawing (arrowhead or second point of rectangle).
     * @return the array of all handles for this overlay
     */
    @java.beans.Transient
    public abstract Point[] getHandles();


    /**
     * This method is called when the given handle must be moved to the given position
     * @param handleIndex the index of the handle to move
     * @param newPosition the new position of the handle
     */
    protected abstract void setHandlePosition(int handleIndex, Point newPosition);

}