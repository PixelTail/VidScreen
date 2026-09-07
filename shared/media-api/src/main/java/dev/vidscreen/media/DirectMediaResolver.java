package dev.vidscreen.media;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class DirectMediaResolver implements MediaResolver {
    private final MediaUrlPolicy urlPolicy;

    public DirectMediaResolver(MediaUrlPolicy urlPolicy) {
        this.urlPolicy = urlPolicy;
    }

    @Override
    public String id() {
        return "direct";
    }

    @Override
    public boolean supports(URI source) {
        String path = source.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".mp4") || path.endsWith(".m3u8");
    }

    @Override
    public CompletionStage<ResolvedMedia> resolve(MediaRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI validated = urlPolicy.validate(request.source());
                String path = validated.getPath().toLowerCase(Locale.ROOT);
                MediaKind kind = path.endsWith(".m3u8") ? MediaKind.HLS : MediaKind.MP4;
                return new ResolvedMedia(validated, kind, null);
            } catch (MediaUrlRejectedException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }
}
