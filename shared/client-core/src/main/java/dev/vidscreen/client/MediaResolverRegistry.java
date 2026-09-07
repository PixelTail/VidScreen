package dev.vidscreen.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.vidscreen.media.MediaResolver;

public final class MediaResolverRegistry {
    private final Map<String, MediaResolver> resolvers;

    public MediaResolverRegistry(Iterable<? extends MediaResolver> resolvers) {
        Map<String, MediaResolver> byId = new LinkedHashMap<String, MediaResolver>();
        for (MediaResolver resolver : resolvers) {
            if (byId.put(resolver.id(), resolver) != null) {
                throw new IllegalArgumentException("Duplicate media resolver ID: " + resolver.id());
            }
        }
        this.resolvers = Collections.unmodifiableMap(byId);
    }

    public MediaResolver require(String id) {
        MediaResolver resolver = resolvers.get(id);
        if (resolver == null) {
            throw new IllegalArgumentException("No media resolver is available for: " + id);
        }
        return resolver;
    }
}
