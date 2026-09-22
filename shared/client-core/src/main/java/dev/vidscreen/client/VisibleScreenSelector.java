package dev.vidscreen.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.vidscreen.domain.ScreenState;

public final class VisibleScreenSelector {
    private final int maximumActiveScreens;

    public VisibleScreenSelector(int maximumActiveScreens) {
        if (maximumActiveScreens < 1 || maximumActiveScreens > 64) {
            throw new IllegalArgumentException("maximumActiveScreens must be between 1 and 64");
        }
        this.maximumActiveScreens = maximumActiveScreens;
    }

    public List<ScreenState> select(
            Collection<ScreenState> screens,
            String dimension,
            double viewerX,
            double viewerY,
            double viewerZ) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (ScreenState screen : screens) {
            if (screen.media() == null || !screen.definition().dimension().value().equals(dimension)) {
                continue;
            }
            double distanceSquared = distanceSquared(screen, viewerX, viewerY, viewerZ);
            if (isWithinViewDistance(screen, viewerX, viewerY, viewerZ)) {
                candidates.add(new Candidate(screen, distanceSquared));
            }
        }
        Collections.sort(candidates, Comparator
                .comparingDouble(Candidate::distanceSquared)
                .thenComparing(candidate -> candidate.screen().definition().id()));

        int resultSize = Math.min(maximumActiveScreens, candidates.size());
        List<ScreenState> selected = new ArrayList<ScreenState>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            selected.add(candidates.get(index).screen());
        }
        return Collections.unmodifiableList(selected);
    }

    public static boolean isWithinViewDistance(
            ScreenState screen,
            double viewerX,
            double viewerY,
            double viewerZ) {
        double viewDistance = screen.definition().viewDistance();
        return distanceSquared(screen, viewerX, viewerY, viewerZ)
                <= viewDistance * viewDistance;
    }

    public static int anchorChunkX(ScreenState screen) {
        return (int) Math.floor(midpoint(screen).x() / 16.0);
    }

    public static int anchorChunkZ(ScreenState screen) {
        return (int) Math.floor(midpoint(screen).z() / 16.0);
    }

    private static double distanceSquared(
            ScreenState screen,
            double viewerX,
            double viewerY,
            double viewerZ) {
        ScreenMesh.Vertex center = midpoint(screen);
        double deltaX = center.x() - viewerX;
        double deltaY = center.y() - viewerY;
        double deltaZ = center.z() - viewerZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static ScreenMesh.Vertex midpoint(ScreenState screen) {
        return ScreenMesh.midpoint(screen.definition().geometry(), screen.definition().style());
    }

    private static final class Candidate {
        private final ScreenState screen;
        private final double distanceSquared;

        private Candidate(ScreenState screen, double distanceSquared) {
            this.screen = screen;
            this.distanceSquared = distanceSquared;
        }

        ScreenState screen() {
            return screen;
        }

        double distanceSquared() {
            return distanceSquared;
        }
    }
}
