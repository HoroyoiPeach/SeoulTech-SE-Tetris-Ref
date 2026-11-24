package seoultech.se.tetris.game;

import java.util.Objects;
import java.util.OptionalLong;

/** One serialized match transition plus the next time its owner should advance it. */
public record BattleMatchUpdate(
        BattleMatchSnapshot snapshot,
        OptionalLong nextWakeupNanos) {
    public BattleMatchUpdate {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(nextWakeupNanos, "nextWakeupNanos");
    }
}
