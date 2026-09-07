package dev.vidscreen.forgelegacy;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Common 1.12.2 Forge entrypoint with an explicit client proxy boundary. */
@Mod(modid = VidScreenForgeLegacy.MOD_ID, name = "VidScreen", version = "0.1.0-SNAPSHOT")
public final class VidScreenForgeLegacy {
    public static final String MOD_ID = "vidscreen";
    public static final String PROTOCOL_VERSION = "1";
    public static SimpleNetworkWrapper CHANNEL;

    @SidedProxy(
            clientSide = "dev.vidscreen.forgelegacy.client.ClientProxy",
            serverSide = "dev.vidscreen.forgelegacy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID + "_control");
        CHANNEL.registerMessage(
                LegacyForgeServerMessageHandler.class,
                ForgeControlPayload.class,
                0,
                net.minecraftforge.fml.relauncher.Side.SERVER);
        GameRegistry.registerTileEntity(
                LegacyScreenTileEntity.class,
                new ResourceLocation(MOD_ID, "screen"));
        proxy.registerClientMessages(CHANNEL);
        proxy.preInit();
    }

    public static void sendControl(net.minecraft.entity.player.EntityPlayerMP player, byte[] payload) {
        if (payload == null || payload.length > ForgeControlPayload.MAX_BYTES) {
            throw new IllegalArgumentException("Invalid VidScreen payload");
        }
        CHANNEL.sendTo(new ForgeControlPayload(payload), player);
    }
}
