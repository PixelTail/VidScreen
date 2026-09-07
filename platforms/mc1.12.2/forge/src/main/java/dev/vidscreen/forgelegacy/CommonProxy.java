package dev.vidscreen.forgelegacy;

import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/** Dedicated-server-safe proxy. It must not import Minecraft client classes. */
public class CommonProxy {
    public void preInit() {
    }

    public void registerClientMessages(SimpleNetworkWrapper channel) {
    }
}
