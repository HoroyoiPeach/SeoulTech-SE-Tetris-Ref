package seoultech.se.tetris.game;

import java.util.Objects;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;

/** Rules fixed for one game; display, key bindings, and storage remain outside it. */
public record GameConfig(GameMode mode, Difficulty difficulty) {
    public GameConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
    }
}
