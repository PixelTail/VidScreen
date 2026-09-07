package dev.vidscreen.forgelegacy;

import java.util.function.Supplier;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.fml.common.Mod;

/** Common 1.16.5 Forge entrypoint; renderer initialization is client-gated. */
@Mod(VidScreenForgeLegacy.MOD_ID)
public final class VidScreenForgeLegacy {
    public static final String MOD_ID = "vidscreen";
    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "control"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public VidScreenForgeLegacy() {
        CHANNEL.registerMessage(0, ForgeControlPayload.class,
                ForgeControlPayload::encode,
                ForgeControlPayload::from,
                VidScreenForgeLegacy::handle);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> VidScreenForgeLegacyClientInit::initialize);
    }

    private static void handle(ForgeControlPayload message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayerEntity sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> handleServerMessage(sender, message.payload()));
        }
        context.setPacketHandled(true);
    }

    private static void handleServerMessage(ServerPlayerEntity sender, byte[] payload) {
        // Decode shared WireCodec only after operation permission checks. This
        // bounded control path never resolves URLs or accepts media frames.
        if (payload.length == 0) {
            return;
        }
    }

    public static void send(ServerPlayerEntity player, byte[] payload) {
        if (payload == null || payload.length > ForgeControlPayload.MAX_BYTES) {
            throw new IllegalArgumentException("Invalid VidScreen payload");
        }
        CHANNEL.sendTo(new ForgeControlPayload(payload), player.connection.getConnection(), net.minecraftforge.fml.network.NetworkDirection.PLAY_TO_CLIENT);
    }
}
