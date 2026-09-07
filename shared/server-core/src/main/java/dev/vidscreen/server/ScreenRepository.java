package dev.vidscreen.server;

import java.io.IOException;
import java.util.List;

import dev.vidscreen.domain.ScreenState;

/** Durable store for the authoritative screen set. Implementations must replace atomically. */
public interface ScreenRepository {
    List<ScreenState> load() throws IOException;

    void save(long revision, List<ScreenState> screens) throws IOException;
}
