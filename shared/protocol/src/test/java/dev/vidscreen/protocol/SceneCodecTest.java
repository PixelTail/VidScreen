package dev.vidscreen.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.vidscreen.domain.*;
import dev.vidscreen.protocol.message.*;

class SceneCodecTest {
    @Test
    void extendedScenePreservesAreaCurveAndTranslationWhileLegacyRemainsReadable() throws Exception {
        ViewingArea area = new ViewingArea(UUID.randomUUID(), "cinema", new DimensionKey("minecraft:overworld"),
                new BlockPoint(0, 60, 0), new BlockPoint(20, 75, 20));
        ScreenDefinition definition = new ScreenDefinition(UUID.randomUUID(), "main", area.dimension(),
                ScreenGeometry.between(new BlockPoint(1, 64, 0), new BlockPoint(16, 72, 0), Facing.SOUTH),
                ScreenFit.CONTAIN, 96, new ScreenStyle(80, 32, 0.25, 1.5, -0.1), area.id());
        ScreenState state = new ScreenState(4, definition, null, PlaybackState.stopped(4, 1000));
        WireCodec codec = new WireCodec();
        SceneSnapshot scene = new SceneSnapshot(9, Collections.singletonList(state), Collections.singletonList(area));
        assertEquals(scene, codec.decode(codec.encode(scene)));
        SceneUpsert upsert = new SceneUpsert(state);
        assertEquals(upsert, codec.decode(PayloadFraming.decode(PayloadFraming.encode(codec.encode(upsert)))));
        ScreenSnapshot legacy = (ScreenSnapshot) codec.decode(codec.encode(new ScreenSnapshot(9, Collections.singletonList(state))));
        assertEquals(ScreenStyle.FLAT, legacy.screens().get(0).definition().style());
        assertNull(legacy.screens().get(0).definition().viewingAreaId());
        assertEquals(definition.geometry(), legacy.screens().get(0).definition().geometry());
    }

    @Test
    void refusesUnboundedOrNonfiniteCurveAndAreaData() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 171, -171}) {
            assertThrows(IllegalArgumentException.class, () -> new ScreenStyle(value, 32, 0, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new ScreenStyle(45, 129, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScreenStyle(45, 32, 0, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new ViewingArea(UUID.randomUUID(), "large",
                new DimensionKey("minecraft:overworld"), new BlockPoint(0,0,0), new BlockPoint(1024,0,0)));
    }
}
