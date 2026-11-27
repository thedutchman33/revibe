import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Temporary audit helper. Coordinates are physical screenshot pixels at 125%
 * Windows scaling; Robot receives logical coordinates.
 */
public final class SpotifyRobot {
    private static final double SCALE = 1.25;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(25);
        String action = args[0];
        if (action.equals("where")) {
            java.awt.Point p = java.awt.MouseInfo.getPointerInfo().getLocation();
            System.out.println("pointer=" + p.x + "," + p.y);
            return;
        }
        switch (action) {
            case "click" -> click(robot, Double.parseDouble(args[1]), Double.parseDouble(args[2]));
            case "right" -> rightClick(robot, Double.parseDouble(args[1]), Double.parseDouble(args[2]));
            case "double" -> {
                click(robot, Double.parseDouble(args[1]), Double.parseDouble(args[2]));
                Thread.sleep(120);
                click(robot, Double.parseDouble(args[1]), Double.parseDouble(args[2]));
            }
            case "move" -> robot.mouseMove((int) (Double.parseDouble(args[1]) / SCALE), (int) (Double.parseDouble(args[2]) / SCALE));
            case "type" -> type(robot, args[1]);
            case "key" -> key(robot, args[1]);
            case "drag" -> drag(robot, Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
            case "wheel" -> robot.mouseWheel(Integer.parseInt(args[1]));
            default -> throw new IllegalArgumentException(action);
        }
        Thread.sleep(250);
    }

    private static void click(Robot r, double x, double y) {
        r.mouseMove((int) (x / SCALE), (int) (y / SCALE));
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void rightClick(Robot r, double x, double y) {
        r.mouseMove((int) (x / SCALE), (int) (y / SCALE));
        r.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        r.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    private static void drag(Robot r, double x1, double y1, double x2, double y2) {
        r.mouseMove((int) (x1 / SCALE), (int) (y1 / SCALE));
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        int steps = 20;
        for (int i = 1; i <= steps; i++) {
            r.mouseMove((int) ((x1 + (x2 - x1) * i / steps) / SCALE), (int) ((y1 + (y2 - y1) * i / steps) / SCALE));
            r.delay(15);
        }
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void type(Robot r, String text) {
        for (char ch : text.toCharArray()) {
            if (ch == ' ') {
                tap(r, KeyEvent.VK_SPACE);
            } else if (Character.isUpperCase(ch)) {
                r.keyPress(KeyEvent.VK_SHIFT);
                tap(r, KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(ch)));
                r.keyRelease(KeyEvent.VK_SHIFT);
            } else {
                tap(r, KeyEvent.getExtendedKeyCodeForChar(ch));
            }
        }
    }

    private static void tap(Robot r, int code) {
        r.keyPress(code);
        r.keyRelease(code);
    }

    private static void key(Robot r, String name) {
        switch (name) {
            case "enter" -> tap(r, KeyEvent.VK_ENTER);
            case "escape" -> tap(r, KeyEvent.VK_ESCAPE);
            case "tab" -> tap(r, KeyEvent.VK_TAB);
            case "space" -> tap(r, KeyEvent.VK_SPACE);
            case "right" -> tap(r, KeyEvent.VK_RIGHT);
            case "left" -> tap(r, KeyEvent.VK_LEFT);
            case "down" -> tap(r, KeyEvent.VK_DOWN);
            case "up" -> tap(r, KeyEvent.VK_UP);
            case "delete" -> tap(r, KeyEvent.VK_DELETE);
            case "ctrl_l" -> {
                r.keyPress(KeyEvent.VK_CONTROL);
                tap(r, KeyEvent.VK_L);
                r.keyRelease(KeyEvent.VK_CONTROL);
            }
            case "ctrl_a" -> {
                r.keyPress(KeyEvent.VK_CONTROL);
                tap(r, KeyEvent.VK_A);
                r.keyRelease(KeyEvent.VK_CONTROL);
            }
            case "ctrl_k" -> {
                r.keyPress(KeyEvent.VK_CONTROL);
                tap(r, KeyEvent.VK_K);
                r.keyRelease(KeyEvent.VK_CONTROL);
            }
            default -> throw new IllegalArgumentException(name);
        }
    }
}
