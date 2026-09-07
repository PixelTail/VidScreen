package dev.vidscreen.server;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.message.ScreenSnapshot;

/**
 * Small, versioned, protocol-backed screen store. The file never contains media bytes;
 * it only contains the same bounded metadata representation sent to clients.
 */
public final class FileScreenRepository implements ScreenRepository {
    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final WireCodec codec = new WireCodec();

    public FileScreenRepository(Path file) {
        this.file = file;
    }

    @Override
    public List<ScreenState> load() throws IOException {
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        byte[] encoded = Files.readAllBytes(file);
        if (encoded.length < 4 || encoded[0] != 'V' || encoded[1] != 'S'
                || encoded[2] != 'C' || encoded[3] != '1') {
            throw new IOException("Unsupported VidScreen screen-store schema");
        }
        byte[] payload = new byte[encoded.length - 4];
        System.arraycopy(encoded, 4, payload, 0, payload.length);
        try {
            ScreenSnapshot snapshot = (ScreenSnapshot) codec.decode(payload);
            return snapshot.screens();
        } catch (ProtocolException | ClassCastException error) {
            throw new IOException("Invalid VidScreen screen-store payload", error);
        }
    }

    @Override
    public void save(long revision, List<ScreenState> screens) throws IOException {
        try {
            byte[] payload = codec.encode(new ScreenSnapshot(revision, screens));
            byte[] encoded = new byte[payload.length + 4];
            encoded[0] = 'V';
            encoded[1] = 'S';
            encoded[2] = 'C';
            encoded[3] = (byte) ('0' + SCHEMA_VERSION);
            System.arraycopy(payload, 0, encoded, 4, payload.length);

            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, encoded);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (ProtocolException error) {
            throw new IOException("Could not encode VidScreen screen-store payload", error);
        }
    }
}
