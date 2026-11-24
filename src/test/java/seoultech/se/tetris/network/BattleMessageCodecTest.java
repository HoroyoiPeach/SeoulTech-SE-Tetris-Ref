package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.game.BattleMatch;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GamePhase;
import seoultech.se.tetris.game.GameSnapshot;

class BattleMessageCodecTest {
    private static void roundTrip(BattleMessage message) {
        assertEquals(message, BattleMessageCodec.decode(BattleMessageCodec.encode(message)));
    }

    @Test
    void controlMessagesPreserveRoundModeReadinessAndLogicalCommands() {
        for (GameMode mode : GameMode.values()) {
            roundTrip(new BattleMessage.Lobby(5, mode, true, false));
        }
        roundTrip(new BattleMessage.Ready(6));
        roundTrip(new BattleMessage.Link(6, true));
        roundTrip(new BattleMessage.Link(6, false));
        roundTrip(new BattleMessage.LinkAck(6));
        roundTrip(new BattleMessage.Rematch(8));
        for (GameCommand command : GameCommand.values()) {
            roundTrip(new BattleMessage.Input(70_000, command));
        }
    }

    @Test
    void hostSnapshotsRoundTripFromEmptyBoardsToAFinalDecision() {
        AtomicLong now = new AtomicLong();
        BattleMatch match = new BattleMatch(new GameConfig(GameMode.TIME_LIMIT, Difficulty.HARD),
            Duration.ofSeconds(3), now::get);
        roundTrip(new BattleMessage.State(1, match.snapshot()));
        roundTrip(new BattleMessage.State(1, match.start().snapshot()));
        now.set(Duration.ofSeconds(3).toNanos());
        var finished = match.advance(now.get()).snapshot();
        assertTrue(finished.decision().isPresent());
        roundTrip(new BattleMessage.State(1, finished));
    }

    @Test
    void denseBoardsCarryFullMarkersColorsEffectsAndIndependentIncomingRows() {
        var cell = new GameSnapshot.Cell(1, "G", 0xff808080);
        List<List<GameSnapshot.Cell>> fixed = new ArrayList<>();
        for (int y = 0; y < 20; y++) fixed.add(Collections.nCopies(10, cell));
        var piece = new GameSnapshot.Piece("Row", List.of(
            Arrays.asList(null, new GameSnapshot.Cell(1, "L", 0xff0044ff)), Arrays.asList(cell, cell)));
        var effect = new GameSnapshot.Effect("CROSS", 3,
            List.of(new GameSnapshot.OverlayCell(9, 19, " ", 0xffffffff)));
        var first = new GameSnapshot(10, 20, GameMode.ITEM, Difficulty.EASY, GamePhase.ACTIVE,
            true, fixed, piece, piece, -1, -2, 17, 735, 9, 1, 1_000_000_000L, List.of(0, 19), null);
        var second = new GameSnapshot(10, 20, GameMode.ITEM, Difficulty.EASY, GamePhase.RESOLVING,
            false, fixed, null, piece, null, null, null, 1200, 11, 2, 900_000_000L, List.of(4), effect);
        boolean[] holes = new boolean[10];
        holes[0] = true;
        holes[9] = true;
        var pending = Collections.nCopies(10, new AttackRow(holes));
        var state = new BattleMessage.State(22,
            new BattleMatchSnapshot(first, second, pending, List.of(), 0, true, false, Optional.empty()));
        String payload = BattleMessageCodec.encode(state);
        assertTrue(payload.length() > P2PPacket.MAX_PACKET_LENGTH);
        assertEquals(state, BattleMessageCodec.decode(payload));
    }

    @Test
    void rejectsUnknownVersionsRoundsEnumsFlagsAndIncompleteOrOverlongPayloads() {
        byte[] ready = Base64.getDecoder().decode(BattleMessageCodec.encode(new BattleMessage.Ready(1)));
        assertRejected(Arrays.copyOf(ready, ready.length - 1));
        assertRejected(Arrays.copyOf(ready, ready.length + 1));
        ready[0] = 2;
        assertRejected(ready);
        ready[0] = 1;
        ready[9] = 'X';
        assertRejected(ready);
        ready[9] = 'R';
        ready[8] = 0;
        assertRejected(ready);

        byte[] lobby = Base64.getDecoder().decode(BattleMessageCodec.encode(
            new BattleMessage.Lobby(1, GameMode.ITEM, false, true)));
        lobby[lobby.length - 1] = 3;
        assertRejected(lobby);
        lobby[lobby.length - 1] = 1;
        lobby[lobby.length - 3] = '?';
        assertRejected(lobby);
        assertThrows(IllegalArgumentException.class, () -> BattleMessageCodec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> BattleMessageCodec.decode("!"));
        assertThrows(IllegalArgumentException.class, () -> BattleMessageCodec.decode("A".repeat(65_537)));
    }

    @Test
    void refusesAnOverfullAttackQueueOrAWrongWidthBeforeSending() {
        BattleMatch match = new BattleMatch(new GameConfig(GameMode.CLASSIC, Difficulty.NORMAL),
            Duration.ofMinutes(3));
        var initial = match.snapshot();
        for (var rows : List.of(List.of(new AttackRow(new boolean[9])),
                Collections.nCopies(11, new AttackRow(new boolean[10])))) {
            var state = new BattleMatchSnapshot(initial.first(), initial.second(), rows, List.of(),
                0, false, false, Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> BattleMessageCodec.encode(
                new BattleMessage.State(1, state)));
        }
    }

    private static void assertRejected(byte[] data) {
        String payload = Base64.getEncoder().encodeToString(data);
        assertThrows(IllegalArgumentException.class, () -> BattleMessageCodec.decode(payload));
    }
}
