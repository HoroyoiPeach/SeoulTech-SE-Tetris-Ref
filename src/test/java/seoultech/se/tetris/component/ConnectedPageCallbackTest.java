package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.Difficulty;
import sun.misc.Unsafe;

class ConnectedPageCallbackTest {
    private final String originalDataDir = System.getProperty("tetris.dataDir");

    @TempDir Path data;

    @AfterEach
    void restoreDataDirectory() {
        if (originalDataDir == null) System.clearProperty("tetris.dataDir");
        else System.setProperty("tetris.dataDir", originalDataDir);
    }

    @Test
    void connectedSettingsSaveThroughTheirOwnReturnCallback() throws Exception {
        System.setProperty("tetris.dataDir", data.toString());
        TestMaincontainer owner = owner();
        AtomicInteger returns = new AtomicInteger();
        Settingpanel settings = new Settingpanel(owner, returns::incrementAndGet);
        for (int i = 0; i < 7; i++) press(settings, KeyEvent.VK_DOWN);
        press(settings, KeyEvent.VK_ENTER);

        assertEquals(1, returns.get());
        assertEquals(0, owner.legacySettingsExits);
        assertEquals(owner.setting.getDifficulty(), owner.new Setting().getDifficulty());
    }

    @Test
    void connectedScoreboardReturnsThroughItsOwnCallback() throws Exception {
        TestMaincontainer owner = owner();
        AtomicInteger returns = new AtomicInteger();
        Scoreboardpanel scoreboard = new Scoreboardpanel(owner, null, returns::incrementAndGet);
        scoreboard.selectEnter();

        assertEquals(1, returns.get());
        assertEquals(0, owner.legacyScoreExits);
    }

    private static void press(Settingpanel panel, int key) {
        KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key,
            KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : panel.getKeyListeners()) listener.keyPressed(event);
    }

    private static TestMaincontainer owner() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestMaincontainer owner = (TestMaincontainer) ((Unsafe) unsafeField.get(null))
            .allocateInstance(TestMaincontainer.class);
        owner.setting = owner.new Setting();
        owner.setDifficulty(Difficulty.parse(owner.setting.getDifficulty()).orElse(Difficulty.NORMAL));
        return owner;
    }

    private static final class TestMaincontainer extends Maincontainer {
        int legacySettingsExits;
        int legacyScoreExits;

        @Override protected void exitSettingEnterStart() { legacySettingsExits++; }
        @Override protected void exitScoreboardEnterStart() { legacyScoreExits++; }
    }
}
