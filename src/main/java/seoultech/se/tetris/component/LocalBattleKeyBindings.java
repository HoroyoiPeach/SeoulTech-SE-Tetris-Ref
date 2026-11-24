package seoultech.se.tetris.component;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import seoultech.se.tetris.game.GameCommand;

/** Keyboard assignments for a local match, kept separate from Swing event handling. */
final class LocalBattleKeyBindings {
    private static final int[][] SECOND_PLAYER_CANDIDATES = {
        {KeyEvent.VK_W, KeyEvent.VK_R, KeyEvent.VK_U, KeyEvent.VK_I},
        {KeyEvent.VK_S, KeyEvent.VK_F, KeyEvent.VK_K, KeyEvent.VK_N},
        {KeyEvent.VK_A, KeyEvent.VK_J, KeyEvent.VK_Z, KeyEvent.VK_B},
        {KeyEvent.VK_D, KeyEvent.VK_L, KeyEvent.VK_C, KeyEvent.VK_M},
        {KeyEvent.VK_X, KeyEvent.VK_V, KeyEvent.VK_G, KeyEvent.VK_H}
    };
    private static final GameCommand[] GAMEPLAY_COMMANDS = {
        GameCommand.ROTATE,
        GameCommand.SOFT_DROP,
        GameCommand.LEFT,
        GameCommand.RIGHT,
        GameCommand.HARD_DROP
    };

    private final Map<Integer, GameCommand> first;
    private final Map<Integer, GameCommand> second;
    private final int pauseKey;

    private LocalBattleKeyBindings(Map<Integer, GameCommand> first,
            Map<Integer, GameCommand> second, int pauseKey) {
        this.first = Collections.unmodifiableMap(new LinkedHashMap<>(first));
        this.second = Collections.unmodifiableMap(new LinkedHashMap<>(second));
        this.pauseKey = pauseKey;
    }

    static LocalBattleKeyBindings configured() {
        LinkedHashMap<Integer, GameCommand> first = new LinkedHashMap<>();
        first.put(Maincontainer.ROTATE, GameCommand.ROTATE);
        first.put(Maincontainer.DOWN, GameCommand.SOFT_DROP);
        first.put(Maincontainer.LEFT, GameCommand.LEFT);
        first.put(Maincontainer.RIGHT, GameCommand.RIGHT);
        first.put(Maincontainer.HARD_DROP, GameCommand.HARD_DROP);

        Set<Integer> reserved = new LinkedHashSet<>(first.keySet());
        reserved.add(Maincontainer.PAUSE);
        reserved.add(KeyEvent.VK_ESCAPE);
        reserved.add(KeyEvent.VK_ENTER);
        LinkedHashMap<Integer, GameCommand> second = new LinkedHashMap<>();
        for (int i = 0; i < GAMEPLAY_COMMANDS.length; i++) {
            int key = chooseAvailable(SECOND_PLAYER_CANDIDATES[i], reserved);
            reserved.add(key);
            second.put(key, GAMEPLAY_COMMANDS[i]);
        }
        return new LocalBattleKeyBindings(first, second, Maincontainer.PAUSE);
    }

    private static int chooseAvailable(int[] candidates, Set<Integer> reserved) {
        for (int candidate : candidates) {
            if (!reserved.contains(candidate)) return candidate;
        }
        for (int candidate = KeyEvent.VK_A; candidate <= KeyEvent.VK_Z; candidate++) {
            if (!reserved.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("No collision-free local battle key is available");
    }

    Optional<GameCommand> firstCommand(int keyCode) {
        return Optional.ofNullable(first.get(keyCode));
    }

    Optional<GameCommand> secondCommand(int keyCode) {
        return Optional.ofNullable(second.get(keyCode));
    }

    boolean isPause(int keyCode) {
        return keyCode == pauseKey;
    }

    int pauseKey() {
        return pauseKey;
    }

    Map<Integer, GameCommand> first() {
        return first;
    }

    Map<Integer, GameCommand> second() {
        return second;
    }

    String firstSummary() {
        return summary(first);
    }

    String secondSummary() {
        return summary(second);
    }

    private static String summary(Map<Integer, GameCommand> bindings) {
        return "L " + keyFor(bindings, GameCommand.LEFT)
            + "  R " + keyFor(bindings, GameCommand.RIGHT)
            + "  D " + keyFor(bindings, GameCommand.SOFT_DROP)
            + "  TURN " + keyFor(bindings, GameCommand.ROTATE)
            + "  DROP " + keyFor(bindings, GameCommand.HARD_DROP);
    }

    private static String keyFor(Map<Integer, GameCommand> bindings, GameCommand command) {
        return bindings.entrySet().stream()
            .filter(entry -> entry.getValue() == command)
            .map(entry -> KeyEvent.getKeyText(entry.getKey()))
            .findFirst()
            .orElseThrow();
    }
}
