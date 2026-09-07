package dev.vidscreen.forgelegacy.client;

import dev.vidscreen.forgelegacy.ForgeControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client-side bounded metadata handler. It never treats payload bytes as frames. */
public final class LegacyForgeClientMessageHandler implements IMessageHandler<ForgeControlPayload, IMessage> {
    @Override
    public IMessage onMessage(final ForgeControlPayload message, MessageContext context) {
        if (context.side != Side.CLIENT) {
            return null;
        }
        final byte[] metadata = message.payload();
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                LegacyForgeScreenRenderer.acceptControl(metadata);
            }
        });
        return null;
    }
}
