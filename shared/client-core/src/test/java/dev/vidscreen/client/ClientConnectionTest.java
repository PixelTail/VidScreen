package dev.vidscreen.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import dev.vidscreen.domain.*;
import dev.vidscreen.protocol.*;
import dev.vidscreen.protocol.message.*;

class ClientConnectionTest {
    @Test
    void delayedChannelRegistrationRetriesUntilAcceptedThenSamplesClock() {
        ClientScreenStore store = new ClientScreenStore();
        ClientConnection connection = new ClientConnection(store, "fabric", "26.2", Capabilities.MP4);
        assertNull(connection.poll(1000));
        connection.connect();
        assertInstanceOf(ClientHello.class, connection.poll(1000));
        assertNull(connection.poll(2999));
        assertInstanceOf(ClientHello.class, connection.poll(3000));
        connection.accept(new ServerHello(1, 0, true, Capabilities.MP4, 5000, "ok"), 3100);
        ClockRequest request = (ClockRequest) connection.poll(3100);
        connection.accept(new ClockResponse(request.requestId(), 3100, 5100, 5100), 3100);
        assertEquals(5200, connection.estimatedServerTimeMillis(3200));
        assertNull(connection.poll(4000));
        connection.disconnect();
        assertEquals(3200, connection.estimatedServerTimeMillis(3200));
        assertNull(connection.poll(9000));
    }

    @Test
    void handshakeScreenSnapshotAndPlaybackSurviveActualPluginEnvelope() throws Exception {
        ClientScreenStore store = new ClientScreenStore();
        ClientConnection connection = new ClientConnection(store, "neoforge", "26.2", Capabilities.MP4);
        WireCodec codec = new WireCodec();
        connection.connect();
        WireMessage hello = codec.decode(PayloadFraming.decode(PayloadFraming.encode(codec.encode(connection.poll(1000)))));
        assertInstanceOf(ClientHello.class, hello);
        ScreenState screen = screen(2);
        WireMessage[] replies = {new ServerHello(1, 0, true, Capabilities.MP4, 1000, "ok"),
                new ScreenSnapshot(10, Collections.<ScreenState>emptyList()), new ScreenUpsert(screen),
                new PlaybackUpdate(screen.definition().id(), new PlaybackState(11, PlaybackStatus.PLAYING, 0, 1000, 1, false))};
        for (WireMessage reply : replies) {
            connection.accept(codec.decode(PayloadFraming.decode(PayloadFraming.encode(codec.encode(reply)))), 1000);
        }
        assertEquals(ClientConnection.State.READY, connection.state());
        assertEquals(1, store.snapshot().size());
        assertEquals(PlaybackStatus.PLAYING, store.snapshot().iterator().next().playback().status());
        connection.disconnect();
        assertTrue(store.snapshot().isEmpty());
        connection.accept(new ScreenUpsert(screen), 1100);
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    void ignoresUnnegotiatedScreensAndRejectedOrMismatchedMajor() {
        for (ServerHello reply : new ServerHello[] {
                new ServerHello(1, 0, false, 0, 0, "rejected"),
                new ServerHello(2, 0, true, 0, 0, "ok")}) {
            ClientScreenStore store = new ClientScreenStore();
            ClientConnection connection = new ClientConnection(store, "fabric", "26.2", 0);
            connection.connect();
            connection.accept(new ScreenUpsert(screen(1)), 1000);
            assertTrue(store.snapshot().isEmpty());
            connection.accept(reply, 1000);
            assertEquals(ClientConnection.State.REJECTED, connection.state());
            assertNull(connection.poll(2000));
        }
    }

    @Test
    void missingDecoderKeepsAVisiblePlaceholderWithoutSourceDisclosure() {
        ScreenState original = screen(2);
        ScreenState media = original.withMedia(3,
                new MediaDescriptor("direct", "https://media.example/film.mp4?token=private"), original.playback());
        ScreenState projected = CapabilityRequirements.visibleState(0, media);
        assertNull(projected.media());
        assertEquals(original.definition(), projected.definition());
        assertEquals(PlaybackStatus.FAILED, projected.playback().status());
        assertSame(media, CapabilityRequirements.visibleState(Capabilities.MP4, media));
    }

    @Test
    void ignoresUnsolicitedClockSamplesAndClearsPriorConnection() {
        ClientConnection connection = new ClientConnection(new ClientScreenStore(), "fabric", "26.2", 0);
        connection.connect();
        connection.accept(new ServerHello(1, 0, true, 0, 0, "ok"), 1000);
        connection.accept(new ClockResponse(88, 1000, 9000, 9000), 1000);
        assertEquals(1000, connection.estimatedServerTimeMillis(1000));
        connection.connect();
        assertEquals(ClientConnection.State.CONNECTING, connection.state());
        assertInstanceOf(ClientHello.class, connection.poll(1000));
    }

    @Test
    void streamedSnapshotRetainsOldScreenRevisionsAndPerScreenDeletes() {
        ClientScreenStore store = new ClientScreenStore();
        store.accept(new ScreenSnapshot(20, Collections.<ScreenState>emptyList()));
        ScreenState old = screen(2);
        store.accept(new ScreenUpsert(old));
        assertEquals(1, store.snapshot().size());
        store.accept(new ScreenUpsert(screen(1)));
        assertEquals(2, store.snapshot().iterator().next().revision());
        store.accept(new ScreenDelete(old.definition().id(), 1));
        assertEquals(1, store.snapshot().size());
        store.accept(new ScreenDelete(old.definition().id(), 21));
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    void viewingAreaGatesPlaybackAndPreviewNeverStartsADecoder() {
        ClientScreenStore store = new ClientScreenStore();
        ScreenState original = screen(2);
        ViewingArea area = new ViewingArea(UUID.randomUUID(), "cinema", original.definition().dimension(),
                new BlockPoint(0,60,0), new BlockPoint(10,75,10));
        ScreenDefinition base = original.definition();
        ScreenDefinition definition = new ScreenDefinition(base.id(), base.name(), base.dimension(), base.geometry(),
                base.fit(), base.viewDistance(), ScreenStyle.FLAT, area.id());
        ScreenState bound = new ScreenState(2, definition, null, original.playback());
        store.accept(new SceneSnapshot(2, Collections.singletonList(bound), Collections.singletonList(area)));
        assertEquals(1, store.playbackSnapshot("minecraft:overworld", 5,64,5).size());
        assertTrue(store.playbackSnapshot("minecraft:overworld", 20,64,5).isEmpty());
        store.setPreview(bound);
        assertEquals(1, store.renderSnapshot().size());
        assertEquals(1, store.snapshot().size());
        store.clear();
        assertTrue(store.renderSnapshot().isEmpty());
        assertTrue(store.areas().isEmpty());
    }

    private static ScreenState screen(long revision) {
        return new ScreenState(revision, new ScreenDefinition(new UUID(0, 1), "lobby",
                new DimensionKey("minecraft:overworld"),
                ScreenGeometry.between(new BlockPoint(0, 64, 0), new BlockPoint(7, 68, 0), Facing.NORTH),
                ScreenFit.CONTAIN, 96), null, PlaybackState.stopped(revision, 1000));
    }
}
