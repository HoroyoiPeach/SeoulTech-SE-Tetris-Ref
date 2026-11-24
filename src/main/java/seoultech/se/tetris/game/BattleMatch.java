package seoultech.se.tetris.game;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import seoultech.se.tetris.GameMode;

/**
 * UI-independent owner of a two-player battle.
 *
 * <p>The first player is the local side used by {@link MatchDecision}; the second player is the
 * remote side. A local UI may render both on one computer while a later transport adapter can
 * expose the same immutable state to a peer.
 */
public final class BattleMatch {
    private static final long TIME_DISPLAY_REFRESH_NANOS = 1_000_000_000L;

    private final GameMode mode;
    private final GameEngine first;
    private final GameEngine second;
    private final MatchClock matchClock;

    private boolean started;
    private boolean stopped;
    private MatchDecision decision;
    private long remainingSecondsAtStop;

    public BattleMatch(GameConfig config, Duration timeLimit) {
        this(config, timeLimit, System::nanoTime);
    }

    public BattleMatch(GameConfig config, Duration timeLimit, LongSupplier nanoTime) {
        this(
            new GameEngine(Objects.requireNonNull(config, "config"), new GeneratorBlockSource()),
            new GameEngine(config, new GeneratorBlockSource()),
            config.mode(),
            new MatchClock(Objects.requireNonNull(timeLimit, "timeLimit"),
                Objects.requireNonNull(nanoTime, "nanoTime")));
    }

    BattleMatch(GameEngine first, GameEngine second, GameMode mode, MatchClock matchClock) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = Objects.requireNonNull(second, "second");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.matchClock = Objects.requireNonNull(matchClock, "matchClock");
        if (first.snapshot().mode() != mode || second.snapshot().mode() != mode) {
            throw new IllegalArgumentException("both engines must use the match mode");
        }
    }

    public BattleMatchUpdate start() {
        if (started || stopped) return update();
        started = true;
        if (mode == GameMode.TIME_LIMIT) matchClock.start();
        process(first.start(), second.start());
        return update();
    }

    public BattleMatchUpdate handle(PlayerSide side, GameCommand command) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(command, "command");
        if (!canPlay() || expireIfNeeded()) return update();

        if (command == GameCommand.TOGGLE_PAUSE) {
            process(first.handle(command), second.handle(command));
        } else if (side == PlayerSide.FIRST) {
            process(first.handle(command), null);
        } else {
            process(null, second.handle(command));
        }
        return update();
    }

    public BattleMatchUpdate advance(long elapsedNanos) {
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("elapsedNanos must be nonnegative");
        }
        if (!canPlay() || expireIfNeeded()) return update();
        process(first.advance(elapsedNanos), second.advance(elapsedNanos));
        return update();
    }

    public BattleMatchUpdate stop() {
        if (stopped) return update();
        remainingSecondsAtStop = currentRemainingSeconds();
        stopped = true;
        first.stop();
        second.stop();
        return update();
    }

    public BattleMatchSnapshot snapshot() {
        return new BattleMatchSnapshot(
            first.snapshot(), second.snapshot(),
            first.pendingAttackRows(), second.pendingAttackRows(),
            stopped ? remainingSecondsAtStop : currentRemainingSeconds(),
            started, stopped, Optional.ofNullable(decision));
    }

    private boolean canPlay() {
        return started && !stopped;
    }

    private boolean expireIfNeeded() {
        if (mode != GameMode.TIME_LIMIT || !matchClock.isExpired()) return false;
        finish(MatchEndReason.TIME_EXPIRED);
        return true;
    }

    /** Delivers both outgoing attacks before either player promotes a queued block. */
    private void process(GameUpdate firstUpdate, GameUpdate secondUpdate) {
        if (firstUpdate != null) {
            firstUpdate.lockResolution().flatMap(LockResolution::attack)
                .ifPresent(second::receiveAttack);
        }
        if (secondUpdate != null) {
            secondUpdate.lockResolution().flatMap(LockResolution::attack)
                .ifPresent(first::receiveAttack);
        }

        GameUpdate settledFirst = continueAfterLock(first, firstUpdate);
        GameUpdate settledSecond = continueAfterLock(second, secondUpdate);
        boolean firstEnded = settledFirst != null && settledFirst.gameEnd().isPresent();
        boolean secondEnded = settledSecond != null && settledSecond.gameEnd().isPresent();
        if (firstEnded && secondEnded) {
            finish(MatchEndReason.SIMULTANEOUS_TOP_OUT);
        } else if (firstEnded) {
            finish(MatchEndReason.LOCAL_TOP_OUT);
        } else if (secondEnded) {
            finish(MatchEndReason.REMOTE_TOP_OUT);
        }
    }

    private static GameUpdate continueAfterLock(GameEngine engine, GameUpdate update) {
        if (update == null || update.lockResolution().isEmpty()) return update;
        return engine.continueAfterLock(update.lockResolution().orElseThrow().turnId());
    }

    private void finish(MatchEndReason reason) {
        if (decision != null) return;
        decision = MatchJudge.decide(reason, first.snapshot().score(), second.snapshot().score());
        remainingSecondsAtStop = currentRemainingSeconds();
        stopped = true;
        first.stop();
        second.stop();
    }

    private BattleMatchUpdate update() {
        return new BattleMatchUpdate(snapshot(), nextWakeupNanos());
    }

    private OptionalLong nextWakeupNanos() {
        if (!canPlay()) return OptionalLong.empty();
        long next = Long.MAX_VALUE;
        next = minimum(next, first.nextWakeupNanos());
        next = minimum(next, second.nextWakeupNanos());
        if (mode == GameMode.TIME_LIMIT) {
            next = Math.min(next, matchClock.remainingNanos());
            next = Math.min(next, TIME_DISPLAY_REFRESH_NANOS);
        }
        return next == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(Math.max(1, next));
    }

    private long currentRemainingSeconds() {
        return mode == GameMode.TIME_LIMIT ? matchClock.remainingSeconds() : 0;
    }

    private static long minimum(long current, OptionalLong candidate) {
        return candidate.isPresent() ? Math.min(current, candidate.getAsLong()) : current;
    }
}
