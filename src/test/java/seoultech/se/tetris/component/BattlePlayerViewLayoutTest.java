package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class BattlePlayerViewLayoutTest {
    @Test
    void boardKeepsNormalGameProportionsInWideBattleColumn() throws Exception {
        BattlePlayerView view = new BattlePlayerView("YOU", "own");

        SwingUtilities.invokeAndWait(() -> {
            view.setSize(480, 520);
            layoutTree(view);
        });

        Board board = find(view, Board.class);
        assertEquals(33.0 / 58.0, (double) board.getWidth() / board.getHeight(), 0.01);
        assertTrue(board.getWidth() < 300, "wide P2P columns must not stretch the board horizontally");
        assertTrue(board.getHeight() <= 520);
        Nextblockpanel preview = find(view, Nextblockpanel.class);
        assertEquals(preview.getWidth(), preview.getHeight());
    }

    @Test
    void boardShrinksWithoutChangingProportionsOnSmallDisplays() throws Exception {
        BattlePlayerView view = new BattlePlayerView("YOU", "own");

        SwingUtilities.invokeAndWait(() -> {
            view.setSize(160, 360);
            layoutTree(view);
        });

        Board board = find(view, Board.class);
        assertTrue(board.getWidth() <= 160);
        assertTrue(board.getHeight() <= 360);
        assertEquals(33.0 / 58.0, (double) board.getWidth() / board.getHeight(), 0.01);
    }

    @Test
    void localBattleKeepsItsOriginalBoardAndBottomInformationLayout() throws Exception {
        BattlePlayerView view = BattlePlayerView.local("PLAYER 1", "player1");

        SwingUtilities.invokeAndWait(() -> {
            view.setSize(240, 520);
            layoutTree(view);
        });

        Board board = find(view, Board.class);
        Nextblockpanel preview = find(view, Nextblockpanel.class);
        IncomingAttackPanel incoming = find(view, IncomingAttackPanel.class);
        assertSame(view, board.getParent(), "local board must remain the direct center component");
        assertEquals(180, board.getPreferredSize().width);
        assertEquals(330, board.getPreferredSize().height);
        Container information = incoming.getParent();
        assertSame(view, information.getParent());
        assertTrue(information.getY() >= board.getY() + board.getHeight(),
            "local NEXT preview must remain below the board");
        assertEquals(preview.getParent().getParent(), information);
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static <T> T find(Container root, Class<T> type) {
        T found = findOrNull(root, type);
        if (found != null) return found;
        throw new AssertionError(type.getSimpleName() + " not found");
    }

    private static <T> T findOrNull(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = findOrNull(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
