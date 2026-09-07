package dev.vidscreen.forgelegacy.client;

import dev.vidscreen.forgelegacy.CommonProxy;
import dev.vidscreen.forgelegacy.ForgeControlPayload;
import dev.vidscreen.forgelegacy.LegacyScreenTileEntity;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client proxy; no class in this package may be loaded by a dedicated server. */
@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    private LegacyForgeScreenRenderer renderer;

    @Override
    public void registerClientMessages(SimpleNetworkWrapper channel) {
        channel.registerMessage(
                LegacyForgeClientMessageHandler.class,
                ForgeControlPayload.class,
                1,
                Side.CLIENT);
    }

    @Override
    public void preInit() {
        renderer = new LegacyForgeScreenRenderer();
        MinecraftForge.EVENT_BUS.register(renderer);
        ClientRegistry.bindTileEntitySpecialRenderer(
                LegacyScreenTileEntity.class,
                new LegacyForgeScreenTesr(renderer.textures()));
    }
}
