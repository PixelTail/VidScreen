package dev.vidscreen.server;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.ProtocolException;
import dev.vidscreen.protocol.WireCodec;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.SceneSnapshot;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.domain.VidScreenLimits;

public final class FileScreenRepository implements ScreenRepository {
    private final Path file;
    private final Path backup;
    private final WireCodec codec;

    public FileScreenRepository(Path file) {
        this.file = file;
        this.backup = file.resolveSibling(file.getFileName().toString() + ".bak");
        this.codec = new WireCodec();
    }

    @Override
    public Collection<ScreenState> load() throws IOException {
        return loadScene().screens();
    }

    @Override
    public SceneSnapshot loadScene() throws IOException {
        if (!Files.exists(file)) {
            return new SceneSnapshot(0, Collections.<ScreenState>emptyList(), Collections.<ViewingArea>emptyList());
        }
        try {
            return decode(readBounded(file));
        } catch (IOException primaryFailure) {
            if (!Files.exists(backup)) {
                throw primaryFailure;
            }
            return decode(readBounded(backup));
        }
    }

    private byte[] readBounded(Path path) throws IOException {
        if (Files.size(path) > VidScreenLimits.MAX_WIRE_PAYLOAD_BYTES) { throw new IOException("Scene file exceeds maximum size"); }
        return Files.readAllBytes(path);
    }

    private SceneSnapshot decode(byte[] bytes) throws IOException {
        WireMessage message = codec.decode(bytes);
        if (message instanceof SceneSnapshot) { return (SceneSnapshot) message; }
        if (!(message instanceof ScreenSnapshot)) {
            throw new ProtocolException("Persistence file does not contain a screen snapshot");
        }
        ScreenSnapshot legacy = (ScreenSnapshot) message;
        return new SceneSnapshot(legacy.revision(), legacy.screens(), Collections.<ViewingArea>emptyList());
    }

    @Override
    public void save(long revision, Collection<ScreenState> screens) throws IOException {
        saveScene(new SceneSnapshot(revision, new java.util.ArrayList<ScreenState>(screens), Collections.<ViewingArea>emptyList()));
    }

    @Override
    public void saveScene(SceneSnapshot scene) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
        byte[] encoded = codec.encode(scene);
        Files.write(temporary, encoded);

        if (Files.exists(file)) {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
