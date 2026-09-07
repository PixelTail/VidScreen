package dev.vidscreen.media;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

public final class MediaUrlPolicy {
    private final HostResolver hostResolver;
    private final boolean allowNonDefaultHttpsPorts;

    public MediaUrlPolicy(HostResolver hostResolver, boolean allowNonDefaultHttpsPorts) {
        this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver");
        this.allowNonDefaultHttpsPorts = allowNonDefaultHttpsPorts;
    }

    public static MediaUrlPolicy strictPublicHttps() {
        return new MediaUrlPolicy(InetAddress::getAllByName, false);
    }

    public URI validate(URI source) throws MediaUrlRejectedException {
        Objects.requireNonNull(source, "source");
        if (!"https".equalsIgnoreCase(source.getScheme())) {
            throw new MediaUrlRejectedException("Only HTTPS media URLs are allowed");
        }
        if (source.getRawUserInfo() != null) {
            throw new MediaUrlRejectedException("Media URLs cannot contain credentials");
        }
        if (source.getRawFragment() != null) {
            throw new MediaUrlRejectedException("Media URLs cannot contain fragments");
        }
        if (source.getHost() == null || source.getHost().isEmpty()) {
            throw new MediaUrlRejectedException("Media URL must contain a host");
        }
        if (!allowNonDefaultHttpsPorts && source.getPort() != -1 && source.getPort() != 443) {
            throw new MediaUrlRejectedException("Non-default HTTPS ports are disabled");
        }

        String asciiHost;
        try {
            asciiHost = IDN.toASCII(source.getHost(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException error) {
            throw new MediaUrlRejectedException("Invalid media host", error);
        }
        if (asciiHost.endsWith(".local") || asciiHost.endsWith(".localhost") || "localhost".equals(asciiHost)) {
            throw new MediaUrlRejectedException("Local network hosts are blocked");
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(asciiHost);
        } catch (UnknownHostException error) {
            throw new MediaUrlRejectedException("Media host could not be resolved", error);
        }
        if (addresses.length == 0) {
            throw new MediaUrlRejectedException("Media host did not resolve to any address");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new MediaUrlRejectedException("Media host resolves to a blocked network address");
            }
        }
        return source;
    }

    private boolean isPublic(InetAddress address) {
        return !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isSiteLocalAddress()
                && !address.isMulticastAddress()
                && !isBlockedRange(address.getAddress());
    }

    private static boolean isBlockedRange(byte[] address) {
        if (address.length == 4) {
            return matches(address, new byte[] {0}, 8)
                    || matches(address, new byte[] {10}, 8)
                    || matches(address, new byte[] {100, 64}, 10)
                    || matches(address, new byte[] {127}, 8)
                    || matches(address, new byte[] {(byte) 169, (byte) 254}, 16)
                    || matches(address, new byte[] {(byte) 172, 16}, 12)
                    || matches(address, new byte[] {(byte) 192, 0, 0}, 24)
                    || matches(address, new byte[] {(byte) 192, 0, 2}, 24)
                    || matches(address, new byte[] {(byte) 192, (byte) 168}, 16)
                    || matches(address, new byte[] {(byte) 192, 88, 99}, 24)
                    || matches(address, new byte[] {(byte) 198, 18}, 15)
                    || matches(address, new byte[] {(byte) 198, 51, 100}, 24)
                    || matches(address, new byte[] {(byte) 203, 0, 113}, 24)
                    || matches(address, new byte[] {(byte) 224}, 4)
                    || matches(address, new byte[] {(byte) 240}, 4);
        }
        if (address.length == 16) {
            return matches(address, new byte[] {0}, 96)
                    || matches(address, new byte[] {0x00, 0x64, (byte) 0xff, (byte) 0x9b}, 96)
                    || matches(address, new byte[] {0x01, 0x00}, 64)
                    || matches(address, new byte[] {0x20, 0x01, 0x00, 0x00}, 32)
                    || matches(address, new byte[] {0x20, 0x01, 0x00, 0x10}, 28)
                    || matches(address, new byte[] {0x20, 0x01, 0x00, 0x20}, 28)
                    || matches(address, new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8}, 32)
                    || matches(address, new byte[] {0x20, 0x02}, 16)
                    || matches(address, new byte[] {(byte) 0xfc}, 7)
                    || matches(address, new byte[] {(byte) 0xfe, (byte) 0x80}, 10)
                    || matches(address, new byte[] {(byte) 0xff}, 8);
        }
        return true;
    }

    private static boolean matches(byte[] address, byte[] prefix, int prefixBits) {
        int wholeBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;
        for (int index = 0; index < wholeBytes; index++) {
            byte expected = index < prefix.length ? prefix[index] : 0;
            if (address[index] != expected) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xff << (8 - remainingBits)) & 0xff;
        int expected = wholeBytes < prefix.length ? prefix[wholeBytes] & 0xff : 0;
        return ((address[wholeBytes] & 0xff) & mask) == (expected & mask);
    }
}
