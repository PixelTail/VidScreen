package dev.vidscreen.protocol.message;

import java.util.Objects;
import java.util.UUID;

import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class ScreenUpsert implements WireMessage {
    private final ScreenState screen;

    public ScreenUpsert(ScreenState screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    @Override
    public MessageType type() {
        return MessageType.SCREEN_UPSERT;
    }

    public ScreenState screen() {
        return screen;
    }

    public UUID screenId() {
        return screen.definition().id();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ScreenUpsert && screen.equals(((ScreenUpsert) other).screen);
    }

    @Override
    public int hashCode() {
        return screen.hashCode();
    }
}
