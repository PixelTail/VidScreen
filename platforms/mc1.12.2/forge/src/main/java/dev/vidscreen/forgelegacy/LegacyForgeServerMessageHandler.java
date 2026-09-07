package dev.vidscreen.forgelegacy;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-side handler for the bounded control/hello path. */
public final class LegacyForgeServerMessageHandler implements IMessageHandler<ForgeControlPayload, IMessage> {
    @Override
    public IMessage onMessage(ForgeControlPayload message, MessageContext context) {
        if (context.getServerHandler() == null) {
            return null;
        }
        EntityPlayerMP sender = context.getServerHandler().player;
        if (sender == null) {
            return null;
        }
        byte[] payload = message.payload();
        if (payload.length == 0) {
            return null;
        }
        // Decode shared WireCodec only after operation and permission checks.
        // This legacy path deliberately does not resolve URLs or accept frames.
        return null;
    }
}
