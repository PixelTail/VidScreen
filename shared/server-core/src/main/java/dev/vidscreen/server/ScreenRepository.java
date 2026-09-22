package dev.vidscreen.server;

import java.io.IOException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.protocol.message.SceneSnapshot;

public interface ScreenRepository {
    Collection<ScreenState> load() throws IOException;

    void save(long revision, Collection<ScreenState> screens) throws IOException;

    default SceneSnapshot loadScene() throws IOException {
        Collection<ScreenState> screens = load();
        long revision = 0;
        for (ScreenState screen : screens) { revision = Math.max(revision, screen.revision()); }
        return new SceneSnapshot(revision, new ArrayList<ScreenState>(screens), Collections.<ViewingArea>emptyList());
    }

    default void saveScene(SceneSnapshot scene) throws IOException {
        if (!scene.areas().isEmpty()) { throw new IOException("Repository does not support viewing areas"); }
        save(scene.revision(), scene.screens());
    }
}
