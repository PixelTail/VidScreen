package dev.vidscreen.server;

import java.io.IOException;
import java.util.Collection;

import dev.vidscreen.domain.ScreenState;

public interface ScreenRepository {
    Collection<ScreenState> load() throws IOException;

    void save(long revision, Collection<ScreenState> screens) throws IOException;
}
