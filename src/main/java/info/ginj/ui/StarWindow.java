package info.ginj.ui;

import info.ginj.Ginj;
import info.ginj.model.Prefs;
import info.ginj.ui.listener.DragInsensitiveMouseClickListener;
import info.ginj.util.UI;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This "Star Window" is the original widget displayed at the border of the screen to initiate a capture
 * Note: at first the full star was drawn no matter where it stood, half-visible, half-hidden out-of-screen,
 * but when Windows notices a resolution change (or a taskbar show/hide), it moves all windows inwards so that they are 100% on screen.
 * So now the "Star Window" is a full star while dragging, but becomes a half star once dropped against a screen border.
 * <p>
 * UI transparency based on sample code by MadProgrammer at https://stackoverflow.com/questions/26205164/java-custom-shaped-frame-using-image
 */
public class StarWindow extends JWindow {

    public enum Border {TOP, LEFT, BOTTOM, RIGHT}

    public static final Dimension SPLASH_SIZE = new Dimension(508, 292);

    public static final int CIRCLE_WIDTH_PIXELS = 50;
    public static final int CIRCLE_HEIGHT_PIXELS = 50;

    public static final int STAR_WIDTH_PIXELS = 150;
    public static final int STAR_HEIGHT_PIXELS = 150;

    public static final int SCREEN_CORNER_DEAD_ZONE_Y_PIXELS = 100;
    public static final int SCREEN_CORNER_DEAD_ZONE_X_PIXELS = 100;

    public static final float OPACITY_HALF = 0.5f;
    public static final float OPACITY_FULL = 1.0f;

    public static final int STAR_ONLY_RADIUS = 25;

    // Button sizes
    public static final int LARGE_SIZE_PIXELS = 40;
    public static final int MEDIUM_SIZE_PIXELS = 30;
    public static final int SMALL_SIZE_PIXELS = 20;

    // Button distance from center of star to center of button
    public static final int LARGE_RADIUS_PIXELS = 55;
    public static final int MEDIUM_RADIUS_PIXELS = 55;
    public static final int SMALL_RADIUS_PIXELS = 45;

    // Button index
    public static final int BTN_NONE = -1;
    public static final int BTN_CAPTURE = 0;
    public static final int BTN_HISTORY = 1;
    public static final int BTN_MORE = 2;

    // Button "states"
    public static final int LARGE = 0;
    public static final int MEDIUM = 1;
    public static final int SMALL = 2;

    // Precomputed constants
    private static final int SMALL_RADIUS_PIXELS_DIAG = (int) Math.round((SMALL_RADIUS_PIXELS * Math.sqrt(2)) / 2);
    private static final int MEDIUM_RADIUS_PIXELS_DIAG = (int) Math.round((MEDIUM_RADIUS_PIXELS * Math.sqrt(2)) / 2);
    private static final int LARGE_RADIUS_PIXELS_DIAG = (int) Math.round((LARGE_RADIUS_PIXELS * Math.sqrt(2)) / 2);

    // Caching
    private Image starOnlyImg;
    private Image starRaysImg;
    // Array of images for the buttons.
    private final Image[][] buttonImg = new Image[3][3]; // 3 buttons x 3 sizes
    // This array contains the offset position between top left corner of the window and the top left corner of the button
    Point[][] deltasByPosAndSize = new Point[3][3]; // 3 buttons x 3 sizes
    private Color defaultPaneBackground;

    public static Image appIcon = null;

    // Current state
    private Rectangle currentDisplayBounds;
    private Border currentBorder = Border.TOP;
    private boolean isWindowDeployed = false;
    private boolean isDragging = false;
    private int highlightedButtonId = BTN_NONE;

    // Monitor opened frames
    private HistoryFrame historyFrame;
    private MoreFrame moreFrame;
    private TargetManagementFrame targetManagementFrame;
    private final Set<TargetListChangeListener> targetListChangeListener = new HashSet<>();

    public StarWindow() {
        super();

//        showSplashIntro();

        appIcon = new ImageIcon(getClass().getResource("/img/app-icon-64.png")).getImage();

        // Background is transparent. Only the "star icon" is visible, and even then, it has half opacity
        setBackground(new Color(0, 0, 0, 0));
        setOpacity(OPACITY_HALF);

        // Prepare the main pane to paint and receive event
        JComponent contentPane = new MainPane();
        setContentPane(contentPane);
        addMouseBehaviour(contentPane);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                super.componentMoved(e);
                if (!isDragging) {
                    // Happens e.g. if 2nd screen is unplugged
                    positionAndSizeHalfWindow(getClosestCenterOnScreenBorder());
                }
            }
        });

        // Prepare to show Window
        pack();
        setLocationOnStartup();
        setAlwaysOnTop(true);
    }

    public static Image getAppIcon() {
        return appIcon;
    }


    public HistoryFrame getHistoryFrame() {
        return historyFrame;
    }

    public void setHistoryFrame(HistoryFrame historyFrame) {
        this.historyFrame = historyFrame;
    }

    public MoreFrame getMoreFrame() {
        return moreFrame;
    }

    public void setMoreFrame(MoreFrame moreFrame) {
        this.moreFrame = moreFrame;
    }

    public TargetManagementFrame getTargetManagementFrame() {
        return targetManagementFrame;
    }

    public void setTargetManagementFrame(TargetManagementFrame targetManagementFrame) {
        this.targetManagementFrame = targetManagementFrame;
    }

    public class MainPane extends JPanel {

        public MainPane() {
            try {
                starOnlyImg = ImageIO.read(getClass().getResource("/img/star-only.png")).getScaledInstance(STAR_WIDTH_PIXELS, STAR_HEIGHT_PIXELS, Image.SCALE_SMOOTH);
                starRaysImg = ImageIO.read(getClass().getResource("/img/star-rays.png")).getScaledInstance(STAR_WIDTH_PIXELS, STAR_HEIGHT_PIXELS, Image.SCALE_SMOOTH);

                Image originalImg = ImageIO.read(getClass().getResource("/img/capture.png"));
                buttonImg[BTN_CAPTURE][LARGE] = originalImg.getScaledInstance(LARGE_SIZE_PIXELS, LARGE_SIZE_PIXELS, Image.SCALE_SMOOTH);
                buttonImg[BTN_CAPTURE][MEDIUM] = originalImg.getScaledInstance(MEDIUM_SIZE_PIXELS, MEDIUM_SIZE_PIXELS, Image.SCALE_SMOOTH);
                buttonImg[BTN_CAPTURE][SMALL] = originalImg.getScaledInstance(SMALL_SIZE_PIXELS, SMALL_SIZE_PIXELS, Image.SCALE_SMOOTH);

                originalImg = ImageIO.read(getClass().getResource("/img/history.png"));
                buttonImg[BTN_HISTORY][LARGE] = originalImg.getScaledInstance(LARGE_SIZE_PIXELS, LARGE_SIZE_PIXELS, Image.SCALE_SMOOTH);
                buttonImg[BTN_HISTORY][MEDIUM] = originalImg.getScaledInstance(MEDIUM_SIZE_PIXELS, MEDIUM_SIZE_PIXELS, Image.SCALE_SMOOTH);
                buttonImg[BTN_HISTORY][SMALL] = originalImg.getScaledInstance(SMALL_SIZE_PIXELS, SMALL_SIZE_PIXELS, Image.SCALE_SMOOTH);

                originalImg = ImageIO.read(getClass().getResource("/img/more.png"));
                buttonImg[BTN_MORE][LARGE] = originalImg.getScaledInstance(LARGE_SIZE_PIXELS, LARGE_SIZE_PIXELS, Image.SCALE_SMOOTH);
                buttonImg[BTN_MORE][MEDIUM] = originalImg.getScaledInstance(MEDIUM_SIZE_PIXELS, MEDIUM_SIZE_PIXELS, Image.SCALE_SMOOTH);
                buttonImg[BTN_MORE][SMALL] = originalImg.getScaledInstance(SMALL_SIZE_PIXELS, SMALL_SIZE_PIXELS, Image.SCALE_SMOOTH);

            }
            catch (IOException e) {
                System.err.println("Error loading images for the main star UI");
                e.printStackTrace();
                System.exit(Ginj.ERR_STATUS_LOAD_IMG);
            }

            // Store the background so it can be changed and restored later on
            defaultPaneBackground = getBackground();
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(STAR_WIDTH_PIXELS, STAR_HEIGHT_PIXELS);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            if (isDragging) {
                // Full image with rays
                g2d.drawImage(starRaysImg, 0, 0, this);
            }
            else {
                if (isWindowDeployed) {
                    // Half image with rays
                    switch (currentBorder) {
                        case TOP -> g2d.drawImage(starRaysImg, 0, -STAR_HEIGHT_PIXELS / 2, this);
                        case LEFT -> g2d.drawImage(starRaysImg, -STAR_WIDTH_PIXELS / 2, 0, this);
                        case BOTTOM, RIGHT -> g2d.drawImage(starRaysImg, 0, 0, this);
                    }
                    // Draw 3 action icons, with size and position depending on highlight state
                    for (int button = 0; button < 3; button++) {
                        if (highlightedButtonId == BTN_NONE) {
                            g2d.drawImage(buttonImg[button][MEDIUM], deltasByPosAndSize[button][MEDIUM].x, deltasByPosAndSize[button][MEDIUM].y, this);
                        }
                        else {
                            if (button == highlightedButtonId) {
                                g2d.drawImage(buttonImg[button][LARGE], deltasByPosAndSize[button][LARGE].x, deltasByPosAndSize[button][LARGE].y, this);
                            }
                            else {
                                g2d.drawImage(buttonImg[button][SMALL], deltasByPosAndSize[button][SMALL].x, deltasByPosAndSize[button][SMALL].y, this);
                            }
                        }
                    }
                }
                else {
                    // Half image without rays
                    switch (currentBorder) {
                        case TOP -> g2d.drawImage(starOnlyImg, 0, -STAR_HEIGHT_PIXELS / 2, this);
                        case LEFT -> g2d.drawImage(starOnlyImg, -STAR_WIDTH_PIXELS / 2, 0, this);
                        case BOTTOM, RIGHT -> g2d.drawImage(starOnlyImg, 0, 0, this);
                    }
                }
            }
            g2d.dispose();
        }
    }


    private void addMouseBehaviour(JComponent contentPane) {
        MouseInputListener mouseInputListener = new DragInsensitiveMouseClickListener(new MouseInputAdapter() {
            private Point mousePressedPoint;

            @Override
            public void mouseEntered(MouseEvent e) {
                setDeployed(contentPane, true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setDeployed(contentPane, false);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Get clicked point (relative to Window) and store it
                mousePressedPoint = e.getPoint();
                // If click happened on center of star
                Point relativeStarCenter = switch (currentBorder) {
                    case TOP -> new Point(STAR_WIDTH_PIXELS / 2, 0);
                    case LEFT -> new Point(0, STAR_HEIGHT_PIXELS / 2);
                    case BOTTOM, RIGHT -> new Point(STAR_WIDTH_PIXELS / 2, STAR_HEIGHT_PIXELS / 2);
                };
                // That is if distance between click point and center is less than radius
                if (Math.pow(relativeStarCenter.x - mousePressedPoint.x, 2) + Math.pow(relativeStarCenter.y - mousePressedPoint.y, 2) < Math.pow(STAR_ONLY_RADIUS, 2)) {
                    // Start dragging
                    isDragging = true;
                    // Switch to full star
                    setSize(STAR_WIDTH_PIXELS, STAR_HEIGHT_PIXELS);
                    // And move the "drag point" accordingly
                    switch (currentBorder) {
                        case TOP -> {
                            mousePressedPoint.y += STAR_HEIGHT_PIXELS / 2;
                            setLocation(getLocation().x, getLocation().y - STAR_HEIGHT_PIXELS / 2);
                        }
                        case LEFT -> {
                            mousePressedPoint.x += STAR_WIDTH_PIXELS / 2;
                            setLocation(getLocation().x - STAR_WIDTH_PIXELS / 2, getLocation().y);
                        }
                    }
                    StarWindow.this.repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging) {
                    StarWindow.this.setLocation(
                            StarWindow.this.getLocation().x + e.getX() - mousePressedPoint.x,
                            StarWindow.this.getLocation().y + e.getY() - mousePressedPoint.y
                    );
                }
            }

            // Move window to border closest to center
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDragging) {
                    final Point center = getClosestCenterOnScreenBorder();
                    positionAndSizeHalfWindow(center);
                    isDragging = false;
                    StarWindow.this.repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (!isDragging) {
                    int hoveredButtonId = getButtonIdAtLocation(e.getX(), e.getY());
                    if (hoveredButtonId != highlightedButtonId) {
                        highlightedButtonId = hoveredButtonId;
                        StarWindow.this.repaint();
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isDragging) {
                    int clickedButtonId = getButtonIdAtLocation(e.getX(), e.getY());
                    // ignore other clicks
                    switch (clickedButtonId) {
                        case BTN_CAPTURE -> onCapture();
                        case BTN_HISTORY -> onHistory();
                        case BTN_MORE -> onMore();
                    }
                    isWindowDeployed = false;
                    StarWindow.this.repaint();
                }
            }

            // Find which button is being hovered, if any
            private int getButtonIdAtLocation(int x, int y) {
                int buttonId = BTN_NONE;
                for (int buttonIndex = 0; buttonIndex < 3; buttonIndex++) {
                    if (x >= deltasByPosAndSize[buttonIndex][LARGE].x
                            && x < deltasByPosAndSize[buttonIndex][LARGE].x + LARGE_SIZE_PIXELS
                            && y >= deltasByPosAndSize[buttonIndex][LARGE].y
                            && y < deltasByPosAndSize[buttonIndex][LARGE].y + LARGE_SIZE_PIXELS
                    ) {
                        buttonId = buttonIndex;
                        break;
                    }
                }
                return buttonId;
            }
        });

        StarWindow.this.addMouseListener(mouseInputListener);
        StarWindow.this.addMouseMotionListener(mouseInputListener);
    }

    private void positionAndSizeHalfWindow(Point center) {
        switch (currentBorder) {
            case TOP -> {
                setSize(STAR_WIDTH_PIXELS, STAR_HEIGHT_PIXELS / 2);
                setLocation(center.x - STAR_WIDTH_PIXELS / 2, center.y);
            }
            case BOTTOM -> {
                setSize(STAR_WIDTH_PIXELS, STAR_HEIGHT_PIXELS / 2);
                setLocation(center.x - STAR_WIDTH_PIXELS / 2, center.y - STAR_HEIGHT_PIXELS / 2);
            }
            case LEFT -> {
                setSize(STAR_WIDTH_PIXELS / 2, STAR_HEIGHT_PIXELS);
                setLocation(center.x, center.y - STAR_HEIGHT_PIXELS / 2);
            }
            case RIGHT -> {
                setSize(STAR_WIDTH_PIXELS / 2, STAR_HEIGHT_PIXELS);
                setLocation(center.x - STAR_WIDTH_PIXELS / 2, center.y - STAR_HEIGHT_PIXELS / 2);
            }
        }
    }

    // For debugging only
    @Override
    public void setLocation(int x, int y) {
        if (!isDragging) {
            System.out.println("Widget location = (" + x + ", " + y + ").");
        }
        super.setLocation(x, y);
    }

    private void setDeployed(JComponent contentPane, boolean deployed) {
        isWindowDeployed = deployed;
        if (deployed) {
            // Show the handle as opaque
            setOpacity(OPACITY_FULL);
            // And make the background of the window "visible", but filled with an almost transparent color
            // This has the effect of capturing mouse events on the full rectangular Window once it is deployed,
            // which is necessary so that mouse doesn't "fall in the transparent holes" causing MouseExited events that
            // make the window "retract" to the handle-only view
            // TODO: this does not work on Linux where such a low opacity is not supported, so square is visible
            // TODO: replace transparency by a screenshot of the desktop below and printing it as an opaque background
            //       before drawing the star icon
            contentPane.setOpaque(true);
            contentPane.setBackground(new Color(0, 0, 0, 1)); // 1/255 opacity
        }
        else {
            // Show the handle as semi-transparent
            setOpacity(OPACITY_HALF);
            // And make the background of the window invisible, so that all mouse events and clicks "pass through"
            contentPane.setOpaque(false);
            contentPane.setBackground(defaultPaneBackground); // Strangely enough, setting it to a transparent color break things up
        }
    }

    private void setLocationOnStartup() {
        Point savedCenterLocation = getSavedCenterLocation();
        positionAndSizeHalfWindow(savedCenterLocation);
        computeButtonPositions();
    }

    public Point getSavedCenterLocation() {
        // Default to center of top border of main display

        // Load prefs and retrieve previous display
        int displayNumber = 0;
        try {
            displayNumber = Integer.parseInt(Prefs.get(Prefs.Key.STAR_WINDOW_DISPLAY_NUMBER));
        }
        catch (NumberFormatException e) {
            // No (or unrecognized) display number. Keep default value
        }
        currentDisplayBounds = getDisplayBounds(displayNumber);
        if (currentDisplayBounds == null) {
            currentDisplayBounds = getDisplayBounds(0);
        }

        // Load prefs and retrieve previous position
        Point centerLocation = null;

        try {
            Border border = Border.valueOf(Prefs.get(Prefs.Key.STAR_WINDOW_POSTION_ON_BORDER));
            int distanceFromCorner = Integer.parseInt(Prefs.get(Prefs.Key.STAR_WINDOW_DISTANCE_FROM_CORNER));
            switch (border) {
                case TOP -> centerLocation = new Point(
                        currentDisplayBounds.x + Math.min(Math.max(distanceFromCorner, SCREEN_CORNER_DEAD_ZONE_X_PIXELS), currentDisplayBounds.width - SCREEN_CORNER_DEAD_ZONE_X_PIXELS),
                        currentDisplayBounds.y
                );
                case BOTTOM -> centerLocation = new Point(
                        currentDisplayBounds.x + Math.min(Math.max(distanceFromCorner, SCREEN_CORNER_DEAD_ZONE_X_PIXELS), currentDisplayBounds.width - SCREEN_CORNER_DEAD_ZONE_X_PIXELS),
                        currentDisplayBounds.y + currentDisplayBounds.height - 1
                );
                case LEFT -> centerLocation = new Point(
                        currentDisplayBounds.x,
                        currentDisplayBounds.y + Math.min(Math.max(distanceFromCorner, SCREEN_CORNER_DEAD_ZONE_Y_PIXELS), currentDisplayBounds.height - SCREEN_CORNER_DEAD_ZONE_Y_PIXELS)
                );
                case RIGHT -> centerLocation = new Point(
                        currentDisplayBounds.x + currentDisplayBounds.width - 1,
                        currentDisplayBounds.y + Math.min(Math.max(distanceFromCorner, SCREEN_CORNER_DEAD_ZONE_Y_PIXELS), currentDisplayBounds.height - SCREEN_CORNER_DEAD_ZONE_Y_PIXELS)
                );
            }
            currentBorder = border;
        }
        catch (NullPointerException | IllegalArgumentException e) {
            // No (or unrecognized) position.
        }
        if (centerLocation == null || !currentDisplayBounds.contains(centerLocation)) {
            centerLocation = new Point(currentDisplayBounds.x + (currentDisplayBounds.width / 2), currentDisplayBounds.y);
            currentBorder = Border.TOP;
        }
        return centerLocation;
    }

    private Rectangle getDisplayBounds(int displayNumber) {
        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice[] screenDevices = graphicsEnvironment.getScreenDevices();
        for (int currentDisplay = 0; currentDisplay < screenDevices.length; currentDisplay++) {
            GraphicsConfiguration screenConfiguration = screenDevices[currentDisplay].getDefaultConfiguration();
            if (currentDisplay == displayNumber) {
                final Rectangle screenBounds = screenConfiguration.getBounds();
                // remove the "borders" (taskbars, menus):
                final Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(screenConfiguration);
                return new Rectangle(screenBounds.x + screenInsets.left, screenBounds.y + screenInsets.top, screenBounds.width - screenInsets.left - screenInsets.right, screenBounds.height - screenInsets.top - screenInsets.bottom);
            }
        }
        // No match was found
        if (displayNumber == 0) {
            UI.alertError(this, "Display error", "Cannot find bounds of main display!\nDefaulting to Toolkit diaplay.\nPlease report this message as an issue on Github.\nThanks");
            return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }
        else {
            // Requested display was not found
            return null;
        }
    }

    private Point getClosestCenterOnScreenBorder() {
        // Compute current icon center
        Point center = switch (currentBorder) {
            case TOP -> new Point(getLocation().x + getWidth() / 2, getLocation().y);
            case LEFT -> new Point(getLocation().x, getLocation().y + getHeight() / 2);
            case BOTTOM, RIGHT -> new Point(getLocation().x + getWidth() / 2, getLocation().y + getHeight() / 2);
        };

        Rectangle bestDisplay = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        int bestDisplayNumber = 0;
        Border bestBorder = Border.TOP;
        int bestDistance = Integer.MAX_VALUE;

        // Iterate on displays
        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice[] screenDevices = graphicsEnvironment.getScreenDevices();
        for (int displayNumber = 0; displayNumber < screenDevices.length; displayNumber++) {
            GraphicsConfiguration screenConfiguration = screenDevices[displayNumber].getDefaultConfiguration();
            Rectangle screenBounds = screenConfiguration.getBounds();
            // remove the "borders" (taskbars, menus):
            final Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(screenConfiguration);
            Rectangle usableBounds = new Rectangle(screenBounds.x + screenInsets.left, screenBounds.y + screenInsets.top, screenBounds.width - screenInsets.left - screenInsets.right, screenBounds.height - screenInsets.top - screenInsets.bottom);
            // Distance from top
            int distance = Math.abs(usableBounds.y - center.y);
            if (distance < bestDistance && center.x >= screenBounds.x && center.x < screenBounds.x + screenBounds.width) {
                bestDisplay = usableBounds;
                bestDisplayNumber = displayNumber;
                bestBorder = Border.TOP;
                bestDistance = distance;
            }
            // Distance from bottom
            distance = Math.abs(usableBounds.y + usableBounds.height - 1 - center.y);
            if (distance < bestDistance && center.x >= screenBounds.x && center.x < screenBounds.x + screenBounds.width) {
                bestDisplay = usableBounds;
                bestDisplayNumber = displayNumber;
                bestBorder = Border.BOTTOM;
                bestDistance = distance;
            }
            // Distance from left
            distance = Math.abs(usableBounds.x - center.x);
            if (distance < bestDistance && center.y >= screenBounds.y && center.y < screenBounds.y + screenBounds.height) {
                bestDisplay = usableBounds;
                bestDisplayNumber = displayNumber;
                bestBorder = Border.LEFT;
                bestDistance = distance;
            }
            // Distance from right
            distance = Math.abs(usableBounds.x + usableBounds.width - 1 - center.x);
            if (distance < bestDistance && center.y >= screenBounds.y && center.y < screenBounds.y + screenBounds.height) {
                bestDisplay = usableBounds;
                bestDisplayNumber = displayNumber;
                bestBorder = Border.RIGHT;
                bestDistance = distance;
            }
        }

        int targetX, targetY, distanceFromCorner;
        switch (bestBorder) {
            case TOP -> {
                targetX = bestDisplay.x + Math.min(Math.max(center.x - bestDisplay.x, SCREEN_CORNER_DEAD_ZONE_Y_PIXELS), bestDisplay.width - SCREEN_CORNER_DEAD_ZONE_Y_PIXELS);
                targetY = bestDisplay.y;
                distanceFromCorner = targetX - bestDisplay.x;
            }
            case BOTTOM -> {
                targetX = bestDisplay.x + Math.min(Math.max(center.x - bestDisplay.x, SCREEN_CORNER_DEAD_ZONE_Y_PIXELS), bestDisplay.width - SCREEN_CORNER_DEAD_ZONE_Y_PIXELS);
                targetY = bestDisplay.y + bestDisplay.height - 1;
                distanceFromCorner = targetX - bestDisplay.x;
            }
            case LEFT -> {
                targetX = bestDisplay.x;
                targetY = bestDisplay.y + Math.min(Math.max(center.y - bestDisplay.y, SCREEN_CORNER_DEAD_ZONE_Y_PIXELS), bestDisplay.height - SCREEN_CORNER_DEAD_ZONE_Y_PIXELS);
                distanceFromCorner = targetY - bestDisplay.y;
            }
            case RIGHT -> {
                targetX = bestDisplay.x + bestDisplay.width - 1;
                targetY = bestDisplay.y + Math.min(Math.max(center.y - bestDisplay.y, SCREEN_CORNER_DEAD_ZONE_Y_PIXELS), bestDisplay.height - SCREEN_CORNER_DEAD_ZONE_Y_PIXELS);
                distanceFromCorner = targetY - bestDisplay.y;
            }
            default -> throw new IllegalStateException("Unexpected value: " + bestBorder);
        }

        currentDisplayBounds = bestDisplay;
        currentBorder = bestBorder;
        computeButtonPositions();

        Prefs.set(Prefs.Key.STAR_WINDOW_DISPLAY_NUMBER, String.valueOf(bestDisplayNumber));
        Prefs.set(Prefs.Key.STAR_WINDOW_POSTION_ON_BORDER, bestBorder.name());
        Prefs.set(Prefs.Key.STAR_WINDOW_DISTANCE_FROM_CORNER, String.valueOf(distanceFromCorner));
        Prefs.save();
        return new Point(targetX, targetY);
    }

    // This fills up the deltasByPosAndSize array each time the window is moved so that paintComponent() does not have to compute relative positions over and over again
    private void computeButtonPositions() {
        switch (currentBorder) {
            case TOP -> {
                deltasByPosAndSize[0][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG, -LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG, -MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG, -SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[1][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2, -LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS);
                deltasByPosAndSize[1][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2, -MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS);
                deltasByPosAndSize[1][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2, -SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS);
                deltasByPosAndSize[2][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG, -LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG, -MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG, -SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG);
            }
            case BOTTOM -> {
                deltasByPosAndSize[0][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[1][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS);
                deltasByPosAndSize[1][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS);
                deltasByPosAndSize[1][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS);
                deltasByPosAndSize[2][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG);
            }
            case LEFT -> {
                deltasByPosAndSize[0][LARGE] = new Point(-LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][MEDIUM] = new Point(-MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][SMALL] = new Point(-SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[1][LARGE] = new Point(-LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2);
                deltasByPosAndSize[1][MEDIUM] = new Point(-MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2);
                deltasByPosAndSize[1][SMALL] = new Point(-SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2);
                deltasByPosAndSize[2][LARGE] = new Point(-LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][MEDIUM] = new Point(-MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][SMALL] = new Point(-SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG);
            }
            case RIGHT -> {
                deltasByPosAndSize[0][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[0][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[1][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2);
                deltasByPosAndSize[1][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2);
                deltasByPosAndSize[1][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2);
                deltasByPosAndSize[2][LARGE] = new Point(STAR_WIDTH_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 - LARGE_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - LARGE_SIZE_PIXELS / 2 + LARGE_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][MEDIUM] = new Point(STAR_WIDTH_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 - MEDIUM_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - MEDIUM_SIZE_PIXELS / 2 + MEDIUM_RADIUS_PIXELS_DIAG);
                deltasByPosAndSize[2][SMALL] = new Point(STAR_WIDTH_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 - SMALL_RADIUS_PIXELS_DIAG, STAR_HEIGHT_PIXELS / 2 - SMALL_SIZE_PIXELS / 2 + SMALL_RADIUS_PIXELS_DIAG);
            }
        }
    }

    // Util for other Windows

    public void positionFrameNextToStarIcon(JFrame frame) {
        final Point starCenter = getSavedCenterLocation();

        Point location = switch (currentBorder) {
            case TOP -> new Point(starCenter.x - (frame.getWidth() / 2), STAR_HEIGHT_PIXELS / 2);
            case LEFT -> new Point(STAR_WIDTH_PIXELS / 2, starCenter.y - (frame.getHeight() / 2));
            case RIGHT -> new Point(starCenter.x - STAR_WIDTH_PIXELS / 2 - frame.getWidth(), starCenter.y - (frame.getHeight() / 2));
            case BOTTOM -> new Point(starCenter.x - (frame.getWidth() / 2), starCenter.y - STAR_HEIGHT_PIXELS / 2 - frame.getHeight());
        };
        frame.setLocation(Math.min(Math.max(location.x, currentDisplayBounds.x), currentDisplayBounds.x + currentDisplayBounds.width - frame.getWidth()),
                Math.min(Math.max(location.y, currentDisplayBounds.y), currentDisplayBounds.y + currentDisplayBounds.height - frame.getHeight()));
    }


    public void centerFrameOnStarIconDisplay(JFrame frame) {
        frame.setLocation(currentDisplayBounds.x + (currentDisplayBounds.width - frame.getWidth())/2,
                currentDisplayBounds.y + (currentDisplayBounds.height - frame.getHeight())/2);
    }


    ////////////////////////////
    // EVENT HANDLERS

    private void onCapture() {
        // Hide star icon during the capture
        setVisible(false);
        // Creating the capture selection window will cause the screenshot to happen
        CaptureSelectionFrame frame = new CaptureSelectionFrame(this);
        // Show star icon again
        setVisible(true);
        // And show capture selection window
        frame.setVisible(true);
    }


    private void onHistory() {
        if (historyFrame == null) {
            historyFrame = new HistoryFrame(this);
        }
        historyFrame.setVisible(true);
        historyFrame.requestFocus();
    }


    private void onMore() {
        if (moreFrame == null) {
            moreFrame = new MoreFrame(this);
        }
        moreFrame.setVisible(true);
        moreFrame.requestFocus();
    }


    public void addTargetChangeListener(TargetListChangeListener listener) {
        targetListChangeListener.add(listener);
    }

    public void removeTargetChangeListener(TargetListChangeListener listener) {
        targetListChangeListener.add(listener);
    }

    public void notifyTargetListChange() {
        for (TargetListChangeListener targetListChangeListener : targetListChangeListener) {
            targetListChangeListener.onTargetListChange();
        }
    }

//
//    private void showSplashIntro() {
//        final Point startCenter = new Point(screenSize.width / 2, screenSize.height / 2);
//        final Point endCenter = getSavedCenterLocation();
//        Point currentCenter = new Point();
//        try {
//            AnimationJWindow window = new AnimationJWindow();
//            window.setBackground(new Color(0, 0, 0, 0));
//            window.setSize(SPLASH_SIZE);
//            window.setVisible(true);
//
//            final int[] progress = {0};
////            javax.swing.Timer timer = new javax.swing.Timer(100, arg0 -> {
////                // Linear progression
////                currentCenter.x = ((100 - progress[0]) * startCenter.x + progress[0] * endCenter.x) / 100;
////                currentCenter.y = ((100 - progress[0]) * startCenter.y + progress[0] * endCenter.y) / 100;
////                window.setProgress(progress[0]);
////                window.setLocation(currentCenter.x - (window.currentWidth / 2), currentCenter.y - (window.currentHeight / 2));
////                window.repaint();
////                if (progress[0] < 100) progress[0]++;
////            });
////            timer.start();
//
//            while (progress[0] < 100) {
//                Thread.sleep(10);
//            }
//            //timer.stop();
//        }
//        catch (Exception e){
//            e.printStackTrace();
//            // In all cases, ignore exceptions and skip animation
//        }
//    }
//
//    private static class AnimationJWindow extends JWindow {
//        private final BufferedImage image;
//        private int progress;
//        private int currentWidth;
//        private int currentHeight;
//
//        public AnimationJWindow() throws IOException {
//            image = ImageIO.read(getClass().getResource("/img/app-logo.png"));
//        }
//
//        public void setProgress(int progress) {
//            this.progress = progress;
//        }
//
//        public int getCurrentWidth() {
//            return currentWidth;
//        }
//
//        public int getCurrentHeight() {
//            return currentHeight;
//        }
//
//        @Override
//        public void paintComponents(Graphics g) {
//            super.paintComponents(g);
//            currentWidth = ((100 - progress) * image.getWidth() + progress * CIRCLE_WIDTH_PIXELS) / 100;
//            currentHeight = ((100 - progress) * image.getHeight() + progress * CIRCLE_HEIGHT_PIXELS) / 100;
//            // Always draw at (0,0)
//            g.drawImage(image, 0, 0, currentWidth, currentHeight, null);
//        }
//    }
}