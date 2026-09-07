package dev.vidscreen.domain;

import java.util.Objects;

public final class PlaybackState {
    private final long revision;
    private final PlaybackStatus status;
    private final long mediaPositionMillis;
    private final long effectiveServerTimeMillis;
    private final double playbackRate;
    private final boolean looping;

    public PlaybackState(
            long revision,
            PlaybackStatus status,
            long mediaPositionMillis,
            long effectiveServerTimeMillis,
            double playbackRate,
            boolean looping) {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        if (mediaPositionMillis < 0) {
            throw new IllegalArgumentException("mediaPositionMillis must be non-negative");
        }
        if (!Double.isFinite(playbackRate) || playbackRate < 0.25 || playbackRate > 4.0) {
            throw new IllegalArgumentException("playbackRate must be finite and between 0.25 and 4.0");
        }
        this.revision = revision;
        this.status = Objects.requireNonNull(status, "status");
        this.mediaPositionMillis = mediaPositionMillis;
        this.effectiveServerTimeMillis = effectiveServerTimeMillis;
        this.playbackRate = playbackRate;
        this.looping = looping;
    }

    public static PlaybackState stopped(long revision, long serverTimeMillis) {
        return new PlaybackState(revision, PlaybackStatus.STOPPED, 0, serverTimeMillis, 1.0, false);
    }

    public long revision() {
        return revision;
    }

    public PlaybackStatus status() {
        return status;
    }

    public long mediaPositionMillis() {
        return mediaPositionMillis;
    }

    public long effectiveServerTimeMillis() {
        return effectiveServerTimeMillis;
    }

    public double playbackRate() {
        return playbackRate;
    }

    public boolean looping() {
        return looping;
    }

    public long targetPositionMillis(long estimatedServerTimeMillis) {
        if (status != PlaybackStatus.PLAYING) {
            return mediaPositionMillis;
        }
        long elapsed = Math.max(0, estimatedServerTimeMillis - effectiveServerTimeMillis);
        double advanced = elapsed * playbackRate;
        if (advanced >= Long.MAX_VALUE - mediaPositionMillis) {
            return Long.MAX_VALUE;
        }
        return mediaPositionMillis + Math.round(advanced);
    }

    public PlaybackState transition(
            long nextRevision,
            PlaybackStatus nextStatus,
            long positionMillis,
            long serverTimeMillis) {
        if (nextRevision <= revision) {
            throw new IllegalArgumentException("nextRevision must be greater than the current revision");
        }
        return new PlaybackState(nextRevision, nextStatus, positionMillis, serverTimeMillis, playbackRate, looping);
    }

    public PlaybackState withRateAndLoop(long nextRevision, double nextRate, boolean nextLooping, long serverTimeMillis) {
        if (nextRevision <= revision) {
            throw new IllegalArgumentException("nextRevision must be greater than the current revision");
        }
        long currentPosition = targetPositionMillis(serverTimeMillis);
        return new PlaybackState(nextRevision, status, currentPosition, serverTimeMillis, nextRate, nextLooping);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlaybackState)) {
            return false;
        }
        PlaybackState that = (PlaybackState) other;
        return revision == that.revision
                && mediaPositionMillis == that.mediaPositionMillis
                && effectiveServerTimeMillis == that.effectiveServerTimeMillis
                && Double.compare(playbackRate, that.playbackRate) == 0
                && looping == that.looping
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(revision, status, mediaPositionMillis, effectiveServerTimeMillis, playbackRate, looping);
    }
}
