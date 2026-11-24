package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.blocks.OBlock;

class BattleMatchTest {
    private static final Duration LIMIT = Duration.ofMinutes(3);

    @Test
    void startsTwoIndependentBoardsAndRoutesInputByPlayer() {
        GameEngineFixture first = fresh(GameMode.CLASSIC);
        GameEngineFixture second = fresh(GameMode.CLASSIC);
        BattleMatch match = match(first, second, GameMode.CLASSIC, new AtomicLong());

        BattleMatchSnapshot started = match.start().snapshot();
        assertEquals(GamePhase.ACTIVE, started.first().phase());
        assertEquals(GamePhase.ACTIVE, started.second().phase());

        BattleMatchSnapshot moved = match.handle(PlayerSide.FIRST, GameCommand.LEFT).snapshot();
        assertEquals(2, moved.first().x());
        assertEquals(3, moved.second().x());

        BattleMatchSnapshot opposite = match.handle(
            PlayerSide.SECOND, GameCommand.RIGHT).snapshot();
        assertEquals(2, opposite.first().x());
        assertEquals(4, opposite.second().x());
    }

    @ParameterizedTest
    @EnumSource(GameMode.class)
    void transfersAnExactAttackAndAppliesItAtTheOpponentsNextSpawnBoundary(GameMode mode) {
        GameEngineFixture first = activeTwoLineClear(mode);
        GameEngineFixture second = activeTarget(mode);
        BattleMatch match = match(first, second, mode, new AtomicLong());
        match.start();

        BattleMatchSnapshot sent = match.handle(PlayerSide.FIRST, GameCommand.HARD_DROP).snapshot();
        assertEquals(2, sent.secondPendingAttackRows().size());
        assertTrue(sent.firstPendingAttackRows().isEmpty());

        BattleMatchSnapshot applied = match.handle(PlayerSide.SECOND, GameCommand.HARD_DROP).snapshot();
        assertTrue(applied.secondPendingAttackRows().isEmpty());
        assertEquals("G", second.cellText(0, 18));
        assertEquals(null, second.cellText(4, 18));
        assertEquals(null, second.cellText(5, 18));
    }

    @Test
    void attackOverflowEndsTheMatchAndNamesTheOtherPlayerWinner() {
        GameEngineFixture first = activeTwoLineClear(GameMode.CLASSIC);
        GameEngineFixture second = activeTarget(GameMode.CLASSIC);
        second.fixed(0, 0);
        BattleMatch match = match(first, second, GameMode.CLASSIC, new AtomicLong());
        match.start();
        match.handle(PlayerSide.FIRST, GameCommand.HARD_DROP);

        BattleMatchSnapshot ended = match.handle(PlayerSide.SECOND, GameCommand.HARD_DROP).snapshot();

        MatchDecision result = ended.decision().orElseThrow();
        assertEquals(MatchOutcome.LOCAL_WIN, result.outcome());
        assertEquals(MatchEndReason.REMOTE_TOP_OUT, result.reason());
        assertTrue(ended.stopped());
        assertTrue(ended.firstPendingAttackRows().isEmpty());
        assertTrue(ended.secondPendingAttackRows().isEmpty());
    }

    @Test
    void simultaneousSpawnCollisionsAreOneDrawDecision() {
        GameEngineFixture first = activeTarget(GameMode.CLASSIC);
        GameEngineFixture second = activeTarget(GameMode.CLASSIC);
        first.fixed(3, 0);
        second.fixed(3, 0);
        BattleMatch match = match(first, second, GameMode.CLASSIC, new AtomicLong());
        match.start();

        BattleMatchSnapshot ended = match.advance(Duration.ofSeconds(1).toNanos()).snapshot();

        MatchDecision result = ended.decision().orElseThrow();
        assertEquals(MatchOutcome.DRAW, result.outcome());
        assertEquals(MatchEndReason.SIMULTANEOUS_TOP_OUT, result.reason());
        assertEquals(GamePhase.GAME_OVER, ended.first().phase());
        assertEquals(GamePhase.GAME_OVER, ended.second().phase());
        assertEquals(ended, match.advance(Duration.ofSeconds(1).toNanos()).snapshot());
    }

    @Test
    void itemOnlyRowsNeverBecomeAnAttack() {
        GameEngineFixture first = new GameEngineFixture(config(GameMode.ITEM), new OBlock());
        first.seed(new CrossBlock(), new OBlock(), 3, 18);
        GameEngineFixture second = activeTarget(GameMode.ITEM);
        BattleMatch match = match(first, second, GameMode.ITEM, new AtomicLong());
        match.start();

        match.handle(PlayerSide.FIRST, GameCommand.HARD_DROP);
        for (int frame = 0; frame < 11; frame++) {
            match.advance(ItemEffectState.FRAME_NANOS);
        }

        BattleMatchSnapshot resolved = match.snapshot();
        assertTrue(resolved.secondPendingAttackRows().isEmpty());
        assertEquals(2, resolved.first().clearedLines());
        assertEquals(GamePhase.ACTIVE, resolved.first().phase());
    }

    @Test
    void pauseCommandPausesAndResumesBothPlayersTogether() {
        GameEngineFixture first = fresh(GameMode.ITEM);
        GameEngineFixture second = fresh(GameMode.ITEM);
        BattleMatch match = match(first, second, GameMode.ITEM, new AtomicLong());
        match.start();

        BattleMatchSnapshot paused = match.handle(
            PlayerSide.SECOND, GameCommand.TOGGLE_PAUSE).snapshot();
        assertTrue(paused.first().paused());
        assertTrue(paused.second().paused());

        BattleMatchSnapshot resumed = match.handle(
            PlayerSide.FIRST, GameCommand.TOGGLE_PAUSE).snapshot();
        assertFalse(resumed.first().paused());
        assertFalse(resumed.second().paused());
    }

    @Test
    void timeLimitUsesBothScoresAndBlocksFurtherInputAfterOneDecision() {
        AtomicLong now = new AtomicLong();
        GameEngineFixture first = activeTarget(GameMode.TIME_LIMIT);
        GameEngineFixture second = activeTarget(GameMode.TIME_LIMIT);
        first.progress(100, 0, 1);
        second.progress(200, 0, 1);
        BattleMatch match = match(first, second, GameMode.TIME_LIMIT, now);
        match.start();
        now.set(LIMIT.toNanos());

        BattleMatchSnapshot expired = match.advance(LIMIT.toNanos()).snapshot();

        MatchDecision result = expired.decision().orElseThrow();
        assertEquals(MatchOutcome.REMOTE_WIN, result.outcome());
        assertEquals(MatchEndReason.TIME_EXPIRED, result.reason());
        assertEquals(0, expired.remainingSeconds());
        assertEquals(GamePhase.STOPPED, expired.first().phase());
        assertEquals(GamePhase.STOPPED, expired.second().phase());
        assertEquals(expired, match.handle(PlayerSide.FIRST, GameCommand.LEFT).snapshot());
    }

    @Test
    void timeDisplayKeepsUpdatingWhilePausedAndFreezesWhenTheMatchStops() {
        AtomicLong now = new AtomicLong();
        BattleMatch match = match(fresh(GameMode.TIME_LIMIT), fresh(GameMode.TIME_LIMIT),
            GameMode.TIME_LIMIT, now);
        match.start();

        BattleMatchUpdate paused = match.handle(PlayerSide.FIRST, GameCommand.TOGGLE_PAUSE);
        assertEquals(1_000_000_000L, paused.nextWakeupNanos().orElseThrow());
        now.set(Duration.ofSeconds(12).toNanos());
        assertEquals(168, match.advance(Duration.ofSeconds(12).toNanos())
            .snapshot().remainingSeconds());

        BattleMatchSnapshot stopped = match.stop().snapshot();
        now.set(Duration.ofMinutes(2).toNanos());
        assertEquals(stopped.remainingSeconds(), match.snapshot().remainingSeconds());
    }

    @Test
    void rejectsMismatchedModesAndNegativeElapsedTime() {
        GameEngineFixture first = fresh(GameMode.CLASSIC);
        GameEngineFixture second = fresh(GameMode.ITEM);
        assertThrows(IllegalArgumentException.class,
            () -> match(first, second, GameMode.CLASSIC, new AtomicLong()));

        BattleMatch match = match(fresh(GameMode.CLASSIC), fresh(GameMode.CLASSIC),
            GameMode.CLASSIC, new AtomicLong());
        assertThrows(IllegalArgumentException.class, () -> match.advance(-1));
    }

    private static BattleMatch match(GameEngineFixture first, GameEngineFixture second,
            GameMode mode, AtomicLong now) {
        return new BattleMatch(first.engine, second.engine, mode,
            new MatchClock(LIMIT, now::get));
    }

    private static GameEngineFixture fresh(GameMode mode) {
        return new GameEngineFixture(config(mode), new OBlock(), new OBlock(), new OBlock());
    }

    private static GameEngineFixture activeTarget(GameMode mode) {
        GameEngineFixture fixture = new GameEngineFixture(config(mode), new OBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        return fixture;
    }

    private static GameEngineFixture activeTwoLineClear(GameMode mode) {
        GameEngineFixture fixture = new GameEngineFixture(config(mode), new OBlock());
        fixture.seed(new OBlock(), new OBlock(), 4, 18);
        for (int row = 18; row <= 19; row++) {
            for (int column = 0; column < GameState.WIDTH; column++) {
                if (column != 4 && column != 5) fixture.fixed(column, row);
            }
        }
        return fixture;
    }

    private static GameConfig config(GameMode mode) {
        return new GameConfig(mode, Difficulty.NORMAL);
    }
}
