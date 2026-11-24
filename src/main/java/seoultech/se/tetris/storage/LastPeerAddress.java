package seoultech.se.tetris.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Last successful client destination, kept apart from game settings and scores. */
public final class LastPeerAddress {
    private static final String FILE = ".tetris-last-peer";

    private LastPeerAddress() {}

    public static String load() throws IOException {
        Path address = DataPaths.resolve(FILE);
        return Files.exists(address) ? Files.readString(address).strip() : "";
    }

    public static void save(String host) throws IOException {
        Path address = DataPaths.resolve(FILE).toAbsolutePath();
        Path temporary = Files.createTempFile(address.getParent(), FILE, ".tmp");
        try {
            Files.writeString(temporary, host);
            try {
                Files.move(temporary, address, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, address, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
