package dev.vidscreen.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.ScreenGeometry;
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
            double distanceSquared = distanceSquared(screen.definition().geometry(), viewerX, viewerY, viewerZ);
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
        return distanceSquared(screen.definition().geometry(), viewerX, viewerY, viewerZ)
                <= viewDistance * viewDistance;
    }

    public static int anchorChunkX(ScreenState screen) {
        return Math.floorDiv(centerCoordinate(
                screen.definition().geometry().min().x(),
                screen.definition().geometry().max().x()), 16);
    }

    public static int anchorChunkZ(ScreenState screen) {
        return Math.floorDiv(centerCoordinate(
                screen.definition().geometry().min().z(),
                screen.definition().geometry().max().z()), 16);
    }

    private static int centerCoordinate(int minimum, int maximum) {
        return minimum + (maximum - minimum + 1) / 2;
    }

    private static double distanceSquared(
            ScreenGeometry geometry,
            double viewerX,
            double viewerY,
            double viewerZ) {
        BlockPoint min = geometry.min();
        BlockPoint max = geometry.max();
        double centerX = min.x() + (max.x() - min.x() + 1.0) / 2.0;
        double centerY = min.y() + (max.y() - min.y() + 1.0) / 2.0;
        double centerZ = min.z() + (max.z() - min.z() + 1.0) / 2.0;
        double deltaX = centerX - viewerX;
        double deltaY = centerY - viewerY;
        double deltaZ = centerZ - viewerZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
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
