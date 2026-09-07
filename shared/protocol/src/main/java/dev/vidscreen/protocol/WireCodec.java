package dev.vidscreen.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.DimensionKey;
import dev.vidscreen.domain.Facing;
import dev.vidscreen.domain.MediaDescriptor;
import dev.vidscreen.domain.PlaybackState;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenDefinition;
import dev.vidscreen.domain.ScreenFit;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.VidScreenLimits;
import dev.vidscreen.protocol.message.ClientHello;
import dev.vidscreen.protocol.message.ClockRequest;
import dev.vidscreen.protocol.message.ClockResponse;
import dev.vidscreen.protocol.message.OperationResult;
import dev.vidscreen.protocol.message.PlaybackUpdate;
import dev.vidscreen.protocol.message.ScreenDelete;
import dev.vidscreen.protocol.message.ScreenSnapshot;
import dev.vidscreen.protocol.message.ScreenUpsert;
import dev.vidscreen.protocol.message.ServerHello;

public final class WireCodec {
    private static final int MAGIC = 0x56494453;

    public byte[] encode(WireMessage message) throws ProtocolException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(ProtocolVersion.MAJOR);
            output.writeShort(ProtocolVersion.MINOR);
            output.writeByte(message.type().id());
            writeMessage(output, message);
            output.flush();

            byte[] result = bytes.toByteArray();
            if (result.length > VidScreenLimits.MAX_WIRE_PAYLOAD_BYTES) {
                throw new ProtocolException("Encoded payload exceeds maximum size");
            }
            return result;
        } catch (ProtocolException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new ProtocolException("Failed to encode " + message.type(), error);
        }
    }

    public WireMessage decode(byte[] payload) throws ProtocolException {
        if (payload == null) {
            throw new ProtocolException("Payload is null");
        }
        if (payload.length > VidScreenLimits.MAX_WIRE_PAYLOAD_BYTES) {
            throw new ProtocolException("Payload exceeds maximum size");
        }

        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            if (input.readInt() != MAGIC) {
                throw new ProtocolException("Invalid protocol magic");
            }
            int major = input.readUnsignedShort();
            int minor = input.readUnsignedShort();
            if (major != ProtocolVersion.MAJOR) {
                throw new ProtocolException("Unsupported protocol major version: " + major);
            }

            MessageType type = MessageType.fromId(input.readUnsignedByte());
            WireMessage message = readMessage(input, type);
            if (input.available() != 0 && minor <= ProtocolVersion.MINOR) {
                throw new ProtocolException("Trailing data after " + type);
            }
            return message;
        } catch (ProtocolException error) {
            throw error;
        } catch (EOFException error) {
            throw new ProtocolException("Truncated payload", error);
        } catch (IOException | RuntimeException error) {
            throw new ProtocolException("Failed to decode payload", error);
        }
    }

    private void writeMessage(DataOutputStream output, WireMessage message) throws IOException, ProtocolException {
        switch (message.type()) {
            case CLIENT_HELLO:
                writeClientHello(output, (ClientHello) message);
                return;
            case SERVER_HELLO:
                writeServerHello(output, (ServerHello) message);
                return;
            case CLOCK_REQUEST:
                writeClockRequest(output, (ClockRequest) message);
                return;
            case CLOCK_RESPONSE:
                writeClockResponse(output, (ClockResponse) message);
                return;
            case SCREEN_SNAPSHOT:
                writeScreenSnapshot(output, (ScreenSnapshot) message);
                return;
            case SCREEN_UPSERT:
                writeScreenState(output, ((ScreenUpsert) message).screen());
                return;
            case SCREEN_DELETE:
                writeScreenDelete(output, (ScreenDelete) message);
                return;
            case PLAYBACK_UPDATE:
                writePlaybackUpdate(output, (PlaybackUpdate) message);
                return;
            case OPERATION_RESULT:
                writeOperationResult(output, (OperationResult) message);
                return;
            default:
                throw new ProtocolException("Unsupported message type: " + message.type());
        }
    }

    private WireMessage readMessage(DataInputStream input, MessageType type) throws IOException, ProtocolException {
        switch (type) {
            case CLIENT_HELLO:
                return readClientHello(input);
            case SERVER_HELLO:
                return readServerHello(input);
            case CLOCK_REQUEST:
                return new ClockRequest(input.readLong(), input.readLong());
            case CLOCK_RESPONSE:
                return new ClockResponse(input.readLong(), input.readLong(), input.readLong(), input.readLong());
            case SCREEN_SNAPSHOT:
                return readScreenSnapshot(input);
            case SCREEN_UPSERT:
                return new ScreenUpsert(readScreenState(input));
            case SCREEN_DELETE:
                return new ScreenDelete(readUuid(input), readNonNegativeLong(input, "screen revision"));
            case PLAYBACK_UPDATE:
                return new PlaybackUpdate(readUuid(input), readPlaybackState(input));
            case OPERATION_RESULT:
                return new OperationResult(
                        readUuid(input),
                        input.readBoolean(),
                        readString(input, 64),
                        readString(input, VidScreenLimits.MAX_OPERATION_MESSAGE_BYTES));
            default:
                throw new ProtocolException("Unsupported message type: " + type);
        }
    }

    private void writeClientHello(DataOutputStream output, ClientHello hello) throws IOException, ProtocolException {
        output.writeShort(hello.protocolMajor());
        output.writeShort(hello.protocolMinor());
        writeString(output, hello.productVersion(), 64);
        writeString(output, hello.loader(), 64);
        writeString(output, hello.minecraftVersion(), 64);
        output.writeLong(hello.capabilities());
        output.writeInt(hello.maxTextureSize());
    }

    private ClientHello readClientHello(DataInputStream input) throws IOException, ProtocolException {
        return new ClientHello(
                input.readUnsignedShort(),
                input.readUnsignedShort(),
                readString(input, 64),
                readString(input, 64),
                readString(input, 64),
                input.readLong(),
                input.readInt());
    }

    private void writeServerHello(DataOutputStream output, ServerHello hello) throws IOException, ProtocolException {
        output.writeShort(hello.protocolMajor());
        output.writeShort(hello.protocolMinor());
        output.writeBoolean(hello.accepted());
        output.writeLong(hello.enabledCapabilities());
        output.writeLong(hello.serverTimeMillis());
        writeString(output, hello.message(), VidScreenLimits.MAX_OPERATION_MESSAGE_BYTES);
    }

    private ServerHello readServerHello(DataInputStream input) throws IOException, ProtocolException {
        return new ServerHello(
                input.readUnsignedShort(),
                input.readUnsignedShort(),
                input.readBoolean(),
                input.readLong(),
                input.readLong(),
                readString(input, VidScreenLimits.MAX_OPERATION_MESSAGE_BYTES));
    }

    private void writeClockRequest(DataOutputStream output, ClockRequest request) throws IOException {
        output.writeLong(request.requestId());
        output.writeLong(request.clientSendTimeMillis());
    }

    private void writeClockResponse(DataOutputStream output, ClockResponse response) throws IOException {
        output.writeLong(response.requestId());
        output.writeLong(response.clientSendTimeMillis());
        output.writeLong(response.serverReceiveTimeMillis());
        output.writeLong(response.serverSendTimeMillis());
    }

    private void writeScreenSnapshot(DataOutputStream output, ScreenSnapshot snapshot) throws IOException, ProtocolException {
        output.writeLong(snapshot.revision());
        output.writeInt(snapshot.screens().size());
        for (ScreenState screen : snapshot.screens()) {
            writeScreenState(output, screen);
        }
    }

    private ScreenSnapshot readScreenSnapshot(DataInputStream input) throws IOException, ProtocolException {
        long revision = readNonNegativeLong(input, "snapshot revision");
        int count = readBoundedCount(input, VidScreenLimits.MAX_SCREENS_PER_SNAPSHOT, "screen count");
        List<ScreenState> screens = new ArrayList<ScreenState>(count);
        for (int index = 0; index < count; index++) {
            screens.add(readScreenState(input));
        }
        return new ScreenSnapshot(revision, screens);
    }

    private void writeScreenDelete(DataOutputStream output, ScreenDelete message) throws IOException {
        writeUuid(output, message.screenId());
        output.writeLong(message.revision());
    }

    private void writePlaybackUpdate(DataOutputStream output, PlaybackUpdate message) throws IOException, ProtocolException {
        writeUuid(output, message.screenId());
        writePlaybackState(output, message.playback());
    }

    private void writeOperationResult(DataOutputStream output, OperationResult result) throws IOException, ProtocolException {
        writeUuid(output, result.operationId());
        output.writeBoolean(result.success());
        writeString(output, result.code(), 64);
        writeString(output, result.message(), VidScreenLimits.MAX_OPERATION_MESSAGE_BYTES);
    }

    private void writeScreenState(DataOutputStream output, ScreenState state) throws IOException, ProtocolException {
        output.writeLong(state.revision());
        writeScreenDefinition(output, state.definition());
        output.writeBoolean(state.media() != null);
        if (state.media() != null) {
            writeMediaDescriptor(output, state.media());
        }
        writePlaybackState(output, state.playback());
    }

    private ScreenState readScreenState(DataInputStream input) throws IOException, ProtocolException {
        long revision = readNonNegativeLong(input, "screen revision");
        ScreenDefinition definition = readScreenDefinition(input);
        MediaDescriptor media = input.readBoolean() ? readMediaDescriptor(input) : null;
        PlaybackState playback = readPlaybackState(input);
        return new ScreenState(revision, definition, media, playback);
    }

    private void writeScreenDefinition(DataOutputStream output, ScreenDefinition definition) throws IOException, ProtocolException {
        writeUuid(output, definition.id());
        writeString(output, definition.name(), VidScreenLimits.MAX_SCREEN_NAME_BYTES);
        writeString(output, definition.dimension().value(), VidScreenLimits.MAX_DIMENSION_KEY_BYTES);
        writeGeometry(output, definition.geometry());
        output.writeByte(definition.fit().ordinal());
        output.writeDouble(definition.viewDistance());
    }

    private ScreenDefinition readScreenDefinition(DataInputStream input) throws IOException, ProtocolException {
        return new ScreenDefinition(
                readUuid(input),
                readString(input, VidScreenLimits.MAX_SCREEN_NAME_BYTES),
                new DimensionKey(readString(input, VidScreenLimits.MAX_DIMENSION_KEY_BYTES)),
                readGeometry(input),
                readEnum(input, ScreenFit.values(), "screen fit"),
                input.readDouble());
    }

    private void writeGeometry(DataOutputStream output, ScreenGeometry geometry) throws IOException {
        writeBlockPoint(output, geometry.min());
        writeBlockPoint(output, geometry.max());
        output.writeByte(geometry.facing().ordinal());
    }

    private ScreenGeometry readGeometry(DataInputStream input) throws IOException, ProtocolException {
        BlockPoint first = readBlockPoint(input);
        BlockPoint second = readBlockPoint(input);
        Facing facing = readEnum(input, Facing.values(), "facing");
        return ScreenGeometry.between(first, second, facing);
    }

    private void writeBlockPoint(DataOutputStream output, BlockPoint point) throws IOException {
        output.writeInt(point.x());
        output.writeInt(point.y());
        output.writeInt(point.z());
    }

    private BlockPoint readBlockPoint(DataInputStream input) throws IOException {
        return new BlockPoint(input.readInt(), input.readInt(), input.readInt());
    }

    private void writeMediaDescriptor(DataOutputStream output, MediaDescriptor media) throws IOException, ProtocolException {
        writeString(output, media.resolverId(), VidScreenLimits.MAX_RESOLVER_ID_BYTES);
        writeString(output, media.source(), VidScreenLimits.MAX_MEDIA_URI_BYTES);
    }

    private MediaDescriptor readMediaDescriptor(DataInputStream input) throws IOException, ProtocolException {
        return new MediaDescriptor(
                readString(input, VidScreenLimits.MAX_RESOLVER_ID_BYTES),
                readString(input, VidScreenLimits.MAX_MEDIA_URI_BYTES));
    }

    private void writePlaybackState(DataOutputStream output, PlaybackState playback) throws IOException {
        output.writeLong(playback.revision());
        output.writeByte(playback.status().ordinal());
        output.writeLong(playback.mediaPositionMillis());
        output.writeLong(playback.effectiveServerTimeMillis());
        output.writeDouble(playback.playbackRate());
        output.writeBoolean(playback.looping());
    }

    private PlaybackState readPlaybackState(DataInputStream input) throws IOException, ProtocolException {
        return new PlaybackState(
                readNonNegativeLong(input, "playback revision"),
                readEnum(input, PlaybackStatus.values(), "playback status"),
                readNonNegativeLong(input, "media position"),
                input.readLong(),
                input.readDouble(),
                input.readBoolean());
    }

    private void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private void writeString(DataOutputStream output, String value, int maxBytes) throws IOException, ProtocolException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxBytes) {
            throw new ProtocolException("String exceeds maximum size of " + maxBytes + " bytes");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private String readString(DataInputStream input, int maxBytes) throws IOException, ProtocolException {
        int length = input.readInt();
        if (length < 0 || length > maxBytes || length > input.available()) {
            throw new ProtocolException("Invalid string length: " + length);
        }
        byte[] encoded = new byte[length];
        input.readFully(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private int readBoundedCount(DataInputStream input, int maximum, String label) throws IOException, ProtocolException {
        int value = input.readInt();
        if (value < 0 || value > maximum) {
            throw new ProtocolException("Invalid " + label + ": " + value);
        }
        return value;
    }

    private long readNonNegativeLong(DataInputStream input, String label) throws IOException, ProtocolException {
        long value = input.readLong();
        if (value < 0) {
            throw new ProtocolException(label + " must be non-negative");
        }
        return value;
    }

    private <T> T readEnum(DataInputStream input, T[] values, String label) throws IOException, ProtocolException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= values.length) {
            throw new ProtocolException("Invalid " + label + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
