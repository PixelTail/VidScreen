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
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        try {
            return decode(Files.readAllBytes(file));
        } catch (IOException primaryFailure) {
            if (!Files.exists(backup)) {
                throw primaryFailure;
            }
            return decode(Files.readAllBytes(backup));
        }
    }

    private Collection<ScreenState> decode(byte[] bytes) throws IOException {
        WireMessage message = codec.decode(bytes);
        if (!(message instanceof ScreenSnapshot)) {
            throw new ProtocolException("Persistence file does not contain a screen snapshot");
        }
        return ((ScreenSnapshot) message).screens();
    }

    @Override
    public void save(long revision, Collection<ScreenState> screens) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
        byte[] encoded = codec.encode(new ScreenSnapshot(revision, new java.util.ArrayList<ScreenState>(screens)));
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
