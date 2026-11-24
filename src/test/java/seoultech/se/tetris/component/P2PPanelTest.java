package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.storage.LastPeerAddress;

class P2PPanelTest {
    @TempDir Path data;

    @Test @Timeout(30)
    void twoScreensConnectPlayRematchAndRecoverWithTheLastSuccessfulIp() throws Exception {
        String previousDirectory = System.getProperty("tetris.dataDir");
        System.setProperty("tetris.dataDir", data.toString());
        int port;
        try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
        P2PPanel host = edt(() -> panel(port));
        P2PPanel client = edt(() -> panel(port));
        try {
            edt(() -> { address(client).setText("127.0.0.2"); click(client, "JOIN GAME"); return null; });
            await(() -> button(client, "JOIN GAME") != null && button(client, "JOIN GAME").isEnabled()
                && text(client, "p2pStatus").contains("Could not connect"));
            assertEquals("", LastPeerAddress.load());

            edt(() -> { click(host, "HOST GAME"); return null; });
            await(() -> button(host, "CANCEL") != null);
            edt(() -> { address(client).setText("127.0.0.1"); click(client, "JOIN GAME"); return null; });
            await(() -> button(host, "START") != null && button(client, "START") != null);
            assertEquals("127.0.0.1", LastPeerAddress.load());
            edt(() -> {
                mode(host).setSelectedItem("Item");
                assertFalse(mode(client).isEnabled());
                return null;
            });
            await(() -> "Item".equals(mode(client).getSelectedItem()));
            edt(() -> { click(client, "START"); return null; });
            await(() -> button(client, "START") != null && !button(client, "START").isEnabled());
            assertNull(edt(() -> board(client, "ownBoard")));
            edt(() -> { click(host, "START"); return null; });
            await(() -> board(host, "ownBoard") != null && board(client, "ownBoard") != null);

            int clientX = edt(() -> board(client, "ownBoard").snapshot().x());
            int hostX = edt(() -> board(host, "ownBoard").snapshot().x());
            edt(() -> { key(client, Maincontainer.LEFT); return null; });
            await(() -> board(client, "ownBoard").snapshot().x() == clientX - 1
                && board(host, "opponentBoard").snapshot().x() == clientX - 1);
            assertEquals(hostX, edt(() -> board(host, "ownBoard").snapshot().x()));
            edt(() -> {
                for (int i = 0; i < 32; i++) key(client, Maincontainer.HARD_DROP);
                return null;
            });
            await(() -> "YOU WIN".equals(text(host, "matchOutcome"))
                && "YOU LOSE".equals(text(client, "matchOutcome")));
            await(() -> button(host, "START") != null && button(client, "START") != null);
            assertEquals("YOU WIN", edt(() -> text(host, "matchOutcome")));
            assertEquals("YOU LOSE", edt(() -> text(client, "matchOutcome")));
            edt(() -> { click(host, "START"); click(client, "START"); return null; });
            await(() -> "SCORE 0".equals(text(client, "ownScore")) && "SCORE 0".equals(text(host, "ownScore")));
            edt(() -> { click(host, "DISCONNECT"); return null; });
            await(() -> button(host, "HOST GAME") != null && button(client, "JOIN GAME") != null);
            assertFalse(edt(() -> text(client, "p2pStatus").isBlank()), "client must see why its match ended");
            P2PPanel reopened = edt(() -> panel(port));
            try {
                await(() -> "127.0.0.1".equals(address(reopened).getText()));
            } finally {
                edt(() -> { reopened.shutdown(); return null; });
            }
        } finally {
            edt(() -> { host.shutdown(); client.shutdown(); return null; });
            if (previousDirectory == null) System.clearProperty("tetris.dataDir");
            else System.setProperty("tetris.dataDir", previousDirectory);
        }
    }

    private static P2PPanel panel(int port) {
        return new P2PPanel(Difficulty.NORMAL, 180, port, () -> {});
    }

    private static void key(P2PPanel target, int code) {
        var event = new KeyEvent(target, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
        for (var listener : target.getKeyListeners()) listener.keyPressed(event);
    }

    private static void click(P2PPanel panel, String name) { button(panel, name).doClick(0); }
    private static JButton button(P2PPanel panel, String caption) {
        return find(panel, JButton.class, b -> caption.equals(b.getText()));
    }
    private static JTextField address(P2PPanel panel) {
        return find(panel, JTextField.class, c -> "peerAddress".equals(c.getName()));
    }
    private static JComboBox<?> mode(P2PPanel panel) {
        return find(panel, JComboBox.class, c -> "battleMode".equals(c.getName()));
    }
    private static Board board(P2PPanel panel, String name) {
        return find(panel, Board.class, c -> name.equals(c.getName()));
    }
    private static String text(P2PPanel panel, String name) {
        JLabel label = find(panel, JLabel.class, c -> name.equals(c.getName()));
        return label == null ? "" : label.getText();
    }
    private static <T extends Component> T find(Component root, Class<T> type, Predicate<T> match) {
        if (!root.isVisible()) return null;
        if (type.isInstance(root) && match.test(type.cast(root))) return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T result = find(child, type, match);
                if (result != null) return result;
            }
        }
        return null;
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!edt(condition)) {
            if (System.nanoTime() >= deadline) fail("Timed out waiting for both P2P screens");
            Thread.sleep(10);
        }
    }
}
