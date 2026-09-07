package dev.vidscreen.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class MediaUrlPolicyTest {
    @Test
    void acceptsPublicHttpsAddress() throws Exception {
        MediaUrlPolicy policy = new MediaUrlPolicy(host -> addresses("93.184.216.34"), false);
        URI source = URI.create("https://video.example/movie.mp4");

        assertEquals(source, policy.validate(source));
    }

    @Test
    void rejectsPrivateAndLoopbackDestinations() {
        MediaUrlPolicy privatePolicy = new MediaUrlPolicy(host -> addresses("192.168.1.10"), false);
        MediaUrlPolicy loopbackPolicy = new MediaUrlPolicy(host -> addresses("127.0.0.1"), false);

        assertThrows(MediaUrlRejectedException.class,
                () -> privatePolicy.validate(URI.create("https://video.example/movie.mp4")));
        assertThrows(MediaUrlRejectedException.class,
                () -> loopbackPolicy.validate(URI.create("https://video.example/movie.mp4")));
    }

    @Test
    void rejectsWhenAnyResolvedAddressIsPrivate() {
        MediaUrlPolicy policy = new MediaUrlPolicy(host -> addresses("93.184.216.34", "10.0.0.1"), false);

        assertThrows(MediaUrlRejectedException.class,
                () -> policy.validate(URI.create("https://video.example/movie.mp4")));
    }

    @Test
    void rejectsAdditionalNonGlobalAddressRanges() {
        String[] blocked = {
                "100.64.0.1",
                "198.18.0.1",
                "203.0.113.1",
                "fc00::1",
                "2001:db8::1",
                "2002:7f00:1::1"
        };
        for (String address : blocked) {
            MediaUrlPolicy policy = new MediaUrlPolicy(host -> addresses(address), false);
            assertThrows(MediaUrlRejectedException.class,
                    () -> policy.validate(URI.create("https://video.example/movie.mp4")), address);
        }
    }

    @Test
    void acceptsGlobalIpv6Address() throws Exception {
        MediaUrlPolicy policy = new MediaUrlPolicy(host -> addresses("2606:4700:4700::1111"), false);
        URI source = URI.create("https://video.example/movie.mp4");

        assertEquals(source, policy.validate(source));
    }

    @Test
    void rejectsCredentialsAndNonDefaultPorts() {
        MediaUrlPolicy policy = new MediaUrlPolicy(host -> addresses("93.184.216.34"), false);

        assertThrows(MediaUrlRejectedException.class,
                () -> policy.validate(URI.create("https://user:secret@video.example/movie.mp4")));
        assertThrows(MediaUrlRejectedException.class,
                () -> policy.validate(URI.create("https://video.example:8443/movie.mp4")));
    }

    private static InetAddress[] addresses(String... values) throws UnknownHostException {
        InetAddress[] result = new InetAddress[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = InetAddress.getByName(values[index]);
        }
        return result;
    }
}
