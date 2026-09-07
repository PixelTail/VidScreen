package dev.vidscreen.media;

import java.net.URI;
import java.util.concurrent.CompletionStage;

public interface MediaResolver {
    String id();

    boolean supports(URI source);

    CompletionStage<ResolvedMedia> resolve(MediaRequest request);
}
