package seoultech.se.tetris.network;

import java.util.Objects;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.GameCommand;

/** Host and guest messages for one numbered round of an established connection. */
public sealed interface BattleMessage {
    long round();

    record Lobby(long round, GameMode mode, boolean hostReady, boolean guestReady) implements BattleMessage {
        public Lobby {
            requireRound(round);
            Objects.requireNonNull(mode);
        }
    }

    record Ready(long round) implements BattleMessage {
        public Ready { requireRound(round); }
    }

    record Input(long round, GameCommand command) implements BattleMessage {
        public Input {
            requireRound(round);
            Objects.requireNonNull(command);
        }
    }

    record State(long round, BattleMatchSnapshot snapshot) implements BattleMessage {
        public State {
            requireRound(round);
            Objects.requireNonNull(snapshot);
        }
    }

    record Rematch(long round) implements BattleMessage {
        public Rematch { requireRound(round); }
    }

    private static void requireRound(long round) {
        if (round < 1) throw new IllegalArgumentException("Round must be positive");
    }
}
