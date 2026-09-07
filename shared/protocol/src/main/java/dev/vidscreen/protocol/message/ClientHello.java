package dev.vidscreen.protocol.message;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import dev.vidscreen.protocol.MessageType;
import dev.vidscreen.protocol.WireMessage;

public final class ClientHello implements WireMessage {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_.-]{1,64}");

    private final int protocolMajor;
    private final int protocolMinor;
    private final String productVersion;
    private final String loader;
    private final String minecraftVersion;
    private final long capabilities;
    private final int maxTextureSize;

    public ClientHello(
            int protocolMajor,
            int protocolMinor,
            String productVersion,
            String loader,
            String minecraftVersion,
            long capabilities,
            int maxTextureSize) {
        if (protocolMajor < 0 || protocolMajor > 65_535 || protocolMinor < 0 || protocolMinor > 65_535) {
            throw new IllegalArgumentException("Protocol version components must be between 0 and 65535");
        }
        this.productVersion = validateToken(productVersion, "productVersion");
        this.loader = validateToken(loader, "loader").toLowerCase(Locale.ROOT);
        this.minecraftVersion = validateToken(minecraftVersion, "minecraftVersion");
        if (maxTextureSize < 256 || maxTextureSize > 65_536) {
            throw new IllegalArgumentException("maxTextureSize must be between 256 and 65536");
        }
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
        this.capabilities = capabilities;
        this.maxTextureSize = maxTextureSize;
    }

    private static String validateToken(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!TOKEN.matcher(value).matches() || value.getBytes(StandardCharsets.UTF_8).length > 64) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    @Override
    public MessageType type() {
        return MessageType.CLIENT_HELLO;
    }

    public int protocolMajor() {
        return protocolMajor;
    }

    public int protocolMinor() {
        return protocolMinor;
    }

    public String productVersion() {
        return productVersion;
    }

    public String loader() {
        return loader;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public long capabilities() {
        return capabilities;
    }

    public int maxTextureSize() {
        return maxTextureSize;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClientHello)) {
            return false;
        }
        ClientHello that = (ClientHello) other;
        return protocolMajor == that.protocolMajor
                && protocolMinor == that.protocolMinor
                && capabilities == that.capabilities
                && maxTextureSize == that.maxTextureSize
                && productVersion.equals(that.productVersion)
                && loader.equals(that.loader)
                && minecraftVersion.equals(that.minecraftVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolMajor, protocolMinor, productVersion, loader, minecraftVersion, capabilities, maxTextureSize);
    }
}
