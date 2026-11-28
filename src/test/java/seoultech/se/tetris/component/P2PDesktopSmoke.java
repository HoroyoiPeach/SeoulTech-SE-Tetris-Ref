package seoultech.se.tetris.component;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JTextField;

import static seoultech.se.tetris.component.LocalBattleDesktopSmoke.capture;
import static seoultech.se.tetris.component.LocalBattleDesktopSmoke.onEdt;

/** Connected production windows on loopback, with keyboard events routed through Swing. */
final class P2PDesktopSmoke {
    private P2PDesktopSmoke() { }

    static void run(Path evidence) throws Exception {
        System.setProperty("tetris.dataDir", Files.createTempDirectory("p2p-desktop-").toString());
        int port;
        try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
        Maincontainer host = open(port);
        Maincontainer guest = open(port);
        try {
            int menuWidth = onEdt(host::getWidth);
            onEdt(() -> { button(host, "HOST GAME").doClick(0); return null; });
            await("host listening", () -> button(host, "CANCEL") != null);
            onEdt(() -> {
                ((JTextField) named(guest, "peerAddress")).setText("127.0.0.1");
                button(guest, "JOIN GAME").doClick(0);
                return null;
            });
            await("both connected menus", () -> menu(host) != null && menu(guest) != null);
            onEdt(() -> {
                check(host.getWidth() == menuWidth, "connected menu must keep the original window width");
                capture(host, evidence.resolve("p2p-connected-menu.png"));
                click(named(host, "p2pMenu.MENU"));
                return null;
            });
            await("connected settings", () -> find(host, c -> c instanceof Settingpanel) != null);
            onEdt(() -> {
                Component settings = find(host, c -> c instanceof Settingpanel);
                for (int i = 0; i < 5; i++) press(settings, KeyEvent.VK_DOWN);
                press(settings, KeyEvent.VK_ENTER); // Change the host time limit.
                press(settings, KeyEvent.VK_DOWN);
                press(settings, KeyEvent.VK_DOWN);
                press(settings, KeyEvent.VK_ENTER); // Save and Exit.
                return null;
            });
            await("menu after saving settings", () -> menu(host) != null);
            onEdt(() -> { click(named(guest, "p2pMenu.SCORE")); return null; });
            await("connected scoreboard", () -> find(guest, c -> c instanceof Scoreboardpanel) != null);
            onEdt(() -> { click(named(host, "p2pMenu.CLASSIC")); return null; });
            await("host readiness while guest views scores", () -> button(host, "START") != null);
            onEdt(() -> {
                Component scores = find(guest, c -> c instanceof Scoreboardpanel);
                check(scores != null, "host mode selection must not close the guest's scoreboard");
                press(scores, KeyEvent.VK_ENTER);
                return null;
            });
            await("both readiness screens", () -> button(host, "START") != null && button(guest, "START") != null);
            onEdt(() -> {
                press(button(guest, "START"), KeyEvent.VK_ENTER);
                press(button(host, "START"), KeyEvent.VK_ENTER);
                return null;
            });
            await("both boards", () -> board(host, "opponentBoard") != null && board(guest, "ownBoard") != null);
            int x = onEdt(() -> board(guest, "ownBoard").snapshot().x());
            onEdt(() -> {
                var monitor = host.getGraphicsConfiguration();
                var reserved = host.getToolkit().getScreenInsets(monitor);
                int available = monitor.getBounds().width - reserved.left - reserved.right;
                int expectedWidth = Math.min(2 * host.getConfiguredWidth() + host.getInsets().left
                    + host.getInsets().right, available);
                check(host.getWidth() == expectedWidth, "P2P match must show both boards within the monitor");
                press(named(guest, "p2pMatch"), KeyEvent.VK_LEFT);
                return null;
            });
            await("one key moves one column on both boards", () ->
                board(host, "opponentBoard").snapshot().x() == x - 1 && board(guest, "ownBoard").snapshot().x() == x - 1);
            onEdt(() -> { capture(host, evidence.resolve("p2p-match.png")); return null; });
            for (int i = 0; i < 40 && onEdt(() -> named(guest, "p2pMatch") != null); i++) {
                onEdt(() -> {
                    Component match = named(guest, "p2pMatch");
                    if (match != null) press(match, KeyEvent.VK_SPACE);
                    return null;
                });
                Thread.sleep(35);
            }
            await("both result screens", () -> button(host, "CONTINUE") != null && button(guest, "CONTINUE") != null);
            onEdt(() -> {
                check(host.getWidth() == menuWidth, "results must restore the regular window width");
                capture(host, evidence.resolve("p2p-match-result.png"));
                press(button(host, "CONTINUE"), KeyEvent.VK_ENTER);
                return null;
            });
            await("host acknowledged", () -> !button(host, "CONTINUE").isEnabled());
            onEdt(() -> { press(button(guest, "CONTINUE"), KeyEvent.VK_ENTER); return null; });
            await("connected menu after rematch confirmation", () -> menu(host) != null && menu(guest) != null);
        } finally {
            onEdt(() -> { host.dispose(); guest.dispose(); return null; });
        }
        System.out.println("PASS two connected P2P windows, menu and board sizing, movement, results and rematch");
    }

    private static Maincontainer open(int port) throws Exception {
        return onEdt(() -> {
            Maincontainer frame = new Maincontainer();
            frame.enterStart();
            frame.enterP2PBattle(port);
            return frame;
        });
    }

    private static void click(Component target) {
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
            target.getWidth() / 2, target.getHeight() / 2, 1, false, MouseEvent.BUTTON1));
    }

    private static void press(Component target, int code) {
        var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        for (int id : new int[]{KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED}) {
            manager.redispatchEvent(target, new KeyEvent(target, id, System.currentTimeMillis(), 0,
                code, KeyEvent.CHAR_UNDEFINED));
        }
    }

    private static Component menu(Container root) { return named(root, "p2pConnectedMenu"); }
    private static Board board(Container root, String name) { return (Board) named(root, name); }
    private static Component named(Container root, String name) { return find(root, c -> name.equals(c.getName())); }
    private static JButton button(Container root, String label) {
        return (JButton) find(root, c -> c instanceof JButton b && label.equals(b.getText()));
    }

    private static Component find(Component root, Predicate<Component> predicate) {
        if (!root.isShowing()) return null;
        if (predicate.test(root)) return root;
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                Component found = find(child, predicate);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void await(String label, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!onEdt(condition)) {
            check(System.nanoTime() < deadline, label);
            Thread.sleep(15);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
