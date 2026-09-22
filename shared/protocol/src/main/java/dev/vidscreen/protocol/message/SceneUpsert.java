package dev.vidscreen.protocol.message;

import java.util.Objects;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class SceneUpsert implements WireMessage {
    private final ScreenState screen;
    public SceneUpsert(ScreenState screen) { this.screen = Objects.requireNonNull(screen, "screen"); }
    public ScreenState screen() { return screen; }
    @Override public MessageType type() { return MessageType.SCENE_UPSERT; }
    @Override public boolean equals(Object other) { return other instanceof SceneUpsert && screen.equals(((SceneUpsert) other).screen); }
    @Override public int hashCode() { return screen.hashCode(); }
}
