package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
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
        BlockingQueue<Runnable> addressTasks = new LinkedBlockingQueue<>();
        P2PPanel client = edt(() -> new P2PPanel(Difficulty.NORMAL, 180, port, () -> {}, addressTasks::add));
        AtomicReference<String> hostPage = new AtomicReference<>();
        AtomicReference<Runnable> hostPageReturn = new AtomicReference<>();
        AtomicReference<String> clientPage = new AtomicReference<>();
        AtomicReference<Runnable> clientPageReturn = new AtomicReference<>();
        try {
            edt(() -> {
                host.setLobbyPageOpener((page, done) -> {
                    hostPage.set(page);
                    hostPageReturn.set(done);
                });
                client.setLobbyPageOpener((page, done) -> {
                    clientPage.set(page);
                    clientPageReturn.set(done);
                });
                return null;
            });
            Runnable loadAddress = addressTasks.poll(2, TimeUnit.SECONDS);
            assertNotNull(loadAddress);
            loadAddress.run();
            edt(() -> { address(client).setText("127.0.0.2"); click(client, "JOIN GAME"); return null; });
            await(() -> button(client, "JOIN GAME") != null && button(client, "JOIN GAME").isEnabled()
                && text(client, "p2pStatus").contains("Could not connect"));
            assertEquals("", LastPeerAddress.load());

            edt(() -> { click(host, "HOST GAME"); return null; });
            await(() -> button(host, "CANCEL") != null);
            edt(() -> { address(client).setText("127.0.0.1"); click(client, "JOIN GAME"); return null; });
            await(() -> connectedMenu(host) != null && connectedMenu(client) != null);
            Runnable saveAddress = addressTasks.poll(2, TimeUnit.SECONDS);
            assertNotNull(saveAddress);
            edt(() -> {
                assertConnectedMenuChoices(host);
                assertConnectedMenuChoices(client);
                assertNull(button(host, "START"));
                assertNull(button(client, "START"));
                menuKey(host, KeyEvent.VK_DOWN, 3);
                menuKey(host, KeyEvent.VK_ENTER, 1);
                return null;
            });
            assertEquals("MENU", hostPage.get());
            assertNotNull(hostPageReturn.get());
            hostPageReturn.get().run();
            await(() -> connectedMenu(host) != null);
            edt(() -> {
                menuKey(client, KeyEvent.VK_DOWN, 1);
                menuKey(client, KeyEvent.VK_ENTER, 1);
                return null;
            });
            assertEquals("SCORE", clientPage.get());
            edt(() -> {
                menuKey(host, KeyEvent.VK_UP, 3);
                menuKey(host, KeyEvent.VK_ENTER, 1);
                JButton start = button(host, "START");
                assertNotNull(start);
                assertFalse(start.isEnabled(), "START must wait for the fresh selected-mode round");
                return null;
            });
            await(() -> button(host, "START") != null && button(host, "START").isEnabled());
            assertNull(edt(() -> button(client, "START")), "open SCORE page must not be preempted");
            clientPageReturn.get().run();
            await(() -> button(client, "START") != null && button(client, "START").isEnabled());
            assertEquals("CLASSIC", edt(() -> text(host, "selectedBattleMode")));
            assertEquals("CLASSIC", edt(() -> text(client, "selectedBattleMode")));
            edt(() -> { click(client, "BACK"); return null; });
            await(() -> connectedMenu(client) != null);
            edt(() -> {
                menuKey(client, KeyEvent.VK_ENTER, 1);
                return null;
            });
            await(() -> button(client, "START") != null);
            edt(() -> { click(client, "START"); return null; });
            await(() -> button(client, "START") != null && !button(client, "START").isEnabled());
            assertFalse(edt(() -> button(client, "BACK").isEnabled()),
                "ready player must not appear to cancel readiness locally");
            assertNull(edt(() -> board(client, "ownBoard")));
            edt(() -> { click(host, "START"); return null; });
            await(() -> board(host, "ownBoard") != null && board(client, "ownBoard") != null);

            int clientX = edt(() -> board(client, "ownBoard").snapshot().x());
            int hostX = edt(() -> board(host, "ownBoard").snapshot().x());
            edt(() -> { boundKey(client, Maincontainer.LEFT); return null; });
            await(() -> board(client, "ownBoard").snapshot().x() == clientX - 1
                && board(host, "opponentBoard").snapshot().x() == clientX - 1);
            assertEquals(hostX, edt(() -> board(host, "ownBoard").snapshot().x()));
            // The lobby, both START buttons and the first move work while the disk write is pending.
            assertEquals("", LastPeerAddress.load());
            saveAddress.run();
            assertEquals("127.0.0.1", LastPeerAddress.load());
            edt(() -> {
                for (int i = 0; i < 32; i++) boundKey(client, Maincontainer.HARD_DROP);
                return null;
            });
            await(() -> "YOU WIN".equals(text(host, "matchOutcome"))
                && "YOU LOSE".equals(text(client, "matchOutcome")));
            edt(() -> {
                assertNull(button(host, "START"));
                assertNull(button(client, "START"));
                assertNotNull(button(host, "DISCONNECT"));
                focusedKey(button(host, "CONTINUE"), KeyEvent.VK_ENTER);
                return null;
            });
            await(() -> text(client, "matchResultReadiness").equals("Opponent is ready"));
            assertEquals("YOU WIN", edt(() -> text(host, "matchOutcome")));
            assertEquals("YOU LOSE", edt(() -> text(client, "matchOutcome")));
            edt(() -> { focusedKey(button(client, "CONTINUE"), KeyEvent.VK_ENTER); return null; });
            await(() -> connectedMenu(host) != null && connectedMenu(client) != null);
            assertNull(edt(() -> button(host, "START")));
            edt(() -> {
                menuKey(host, KeyEvent.VK_DOWN, 5);
                menuKey(host, KeyEvent.VK_ENTER, 1);
                return null;
            });
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

    @Test @Timeout(10)
    void hostAddressLookupRunsOffTheEdtAndShowsEveryProvidedEndpoint() throws Exception {
        int port;
        try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
        BlockingQueue<Runnable> addressTasks = new LinkedBlockingQueue<>();
        BlockingQueue<Runnable> networkTasks = new LinkedBlockingQueue<>();
        String endpoints = "Enter one server address on the other computer:\n"
                + "Wi-Fi  ·  192.168.1.40\n"
                + "Ethernet  ·  fd00:0:0:0:0:0:0:40\n"
                + "Port: " + port + " (configured automatically)";
        P2PPanel host = edt(() -> new P2PPanel(
                Difficulty.NORMAL, 180, port, () -> {}, addressTasks::add, networkTasks::add, ignored -> endpoints));
        try {
            Runnable loadSavedAddress = addressTasks.poll(2, TimeUnit.SECONDS);
            assertNotNull(loadSavedAddress);
            loadSavedAddress.run();

            edt(() -> { click(host, "HOST GAME"); return null; });
            await(() -> button(host, "CANCEL") != null);
            Runnable lookupAddresses = networkTasks.poll(2, TimeUnit.SECONDS);
            assertNotNull(lookupAddresses);
            assertNull(addressTasks.poll(), "NIC lookup must not use the recent-address file executor");
            assertEquals("Checking local network addresses…", edt(() -> hostAddresses(host).getText()));

            lookupAddresses.run();
            await(() -> endpoints.equals(hostAddresses(host).getText()));
            assertFalse(edt(() -> hostAddresses(host).isEditable()));
        } finally {
            edt(() -> { host.shutdown(); return null; });
        }
    }

    private static P2PPanel panel(int port) {
        return new P2PPanel(Difficulty.NORMAL, 180, port, () -> {});
    }

    private static void boundKey(P2PPanel target, int code) {
        KeyStroke stroke = KeyStroke.getKeyStroke(code, 0);
        JPanel match = find(target, JPanel.class, p -> "p2pMatch".equals(p.getName()));
        assertNotNull(match);
        Object actionKey = match.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
        assertNotNull(actionKey, "P2P keyboard binding must be available while a child owns focus");
        Action action = match.getActionMap().get(actionKey);
        assertNotNull(action);
        action.actionPerformed(new ActionEvent(target, ActionEvent.ACTION_PERFORMED, actionKey.toString()));
    }

    private static void focusedKey(JComponent target, int code) {
        KeyStroke stroke = KeyStroke.getKeyStroke(code, 0);
        Object actionKey = target.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        assertNotNull(actionKey);
        Action action = target.getActionMap().get(actionKey);
        assertNotNull(action);
        action.actionPerformed(new ActionEvent(target, ActionEvent.ACTION_PERFORMED, actionKey.toString()));
    }

    private static void menuKey(P2PPanel panel, int code, int count) {
        P2PConnectedMenu menu = connectedMenu(panel);
        assertNotNull(menu);
        for (int turn = 0; turn < count; turn++) menu.dispatchKey(code);
    }

    private static void assertConnectedMenuChoices(P2PPanel panel) {
        assertNotNull(textLabel(panel, "CLASSIC"));
        assertNotNull(textLabel(panel, "ITEM"));
        assertNotNull(textLabel(panel, "TIME LIMIT"));
        assertNotNull(textLabel(panel, "MENU"));
        assertNotNull(textLabel(panel, "SCORE"));
        assertNotNull(textLabel(panel, "EXIT"));
        assertNull(textLabel(panel, "LOCAL BATTLE"));
        assertNull(textLabel(panel, "P2P BATTLE"));
        assertEquals(1, opaqueMenuChoiceCount(panel), "only the keyboard selection is filled");
    }

    private static int opaqueMenuChoiceCount(Component root) {
        int count = root instanceof JPanel panel && panel.getName() != null
            && panel.getName().startsWith("p2pMenu.") && panel.isOpaque() ? 1 : 0;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) count += opaqueMenuChoiceCount(child);
        }
        return count;
    }

    private static void click(P2PPanel panel, String name) { button(panel, name).doClick(0); }
    private static JButton button(P2PPanel panel, String caption) {
        return find(panel, JButton.class, b -> caption.equals(b.getText()));
    }
    private static JTextField address(P2PPanel panel) {
        return find(panel, JTextField.class, c -> "peerAddress".equals(c.getName()));
    }
    private static JTextArea hostAddresses(P2PPanel panel) {
        return find(panel, JTextArea.class, c -> "hostAddresses".equals(c.getName()));
    }
    private static P2PConnectedMenu connectedMenu(P2PPanel panel) {
        return find(panel, P2PConnectedMenu.class, c -> "p2pConnectedMenu".equals(c.getName()));
    }
    private static JLabel textLabel(P2PPanel panel, String value) {
        return find(panel, JLabel.class, c -> value.equals(c.getText()));
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
