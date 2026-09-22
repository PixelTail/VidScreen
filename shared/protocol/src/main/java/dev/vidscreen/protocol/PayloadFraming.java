package dev.vidscreen.protocol;

import java.util.Arrays;

/** Minecraft custom-payload body: a VarInt byte length followed by a VIDS message.
 * The same envelope is used by plugin messaging, Fabric and NeoForge.
 * Persistence and golden wire fixtures continue to use the unframed WireCodec.
 */
public final class PayloadFraming {
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    public static final int MAX_FRAME_BYTES = MAX_PAYLOAD_BYTES + 3;

    private PayloadFraming() {
    }

    public static byte[] encode(byte[] payload) throws ProtocolException {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new ProtocolException("Invalid custom-payload size");
        }
        int length = payload.length;
        int prefixSize = length < 128 ? 1 : length < 16_384 ? 2 : 3;
        byte[] framed = new byte[prefixSize + length];
        int offset = 0;
        do {
            int next = length & 0x7f;
            length >>>= 7;
            framed[offset++] = (byte) (next | (length == 0 ? 0 : 0x80));
        } while (length != 0);
        System.arraycopy(payload, 0, framed, offset, payload.length);
        return framed;
    }

    public static byte[] decode(byte[] framed) throws ProtocolException {
        if (framed == null || framed.length == 0 || framed.length > MAX_FRAME_BYTES) {
            throw new ProtocolException("Invalid custom-payload frame size");
        }
        int length = 0;
        for (int offset = 0; offset < 3 && offset < framed.length; offset++) {
            int next = framed[offset] & 0xff;
            length |= (next & 0x7f) << (7 * offset);
            if ((next & 0x80) == 0) {
                if (length == 0 || length > MAX_PAYLOAD_BYTES
                        || length != framed.length - offset - 1
                        || (offset > 0 && (next & 0x7f) == 0)) {
                    throw new ProtocolException("Invalid custom-payload length");
                }
                return Arrays.copyOfRange(framed, offset + 1, framed.length);
            }
        }
        throw new ProtocolException("Invalid custom-payload length prefix");
    }
}
