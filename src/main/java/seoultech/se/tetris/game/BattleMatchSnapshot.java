package seoultech.se.tetris.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import seoultech.se.tetris.attack.AttackRow;

/** Immutable state required to render both players and the shared match result. */
public record BattleMatchSnapshot(
        GameSnapshot first,
        GameSnapshot second,
        List<AttackRow> firstPendingAttackRows,
        List<AttackRow> secondPendingAttackRows,
        long remainingSeconds,
        boolean started,
        boolean stopped,
        Optional<MatchDecision> decision) {
    public BattleMatchSnapshot {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        firstPendingAttackRows = List.copyOf(firstPendingAttackRows);
        secondPendingAttackRows = List.copyOf(secondPendingAttackRows);
        Objects.requireNonNull(decision, "decision");
        if (remainingSeconds < 0) {
            throw new IllegalArgumentException("remainingSeconds must be nonnegative");
        }
    }
}
