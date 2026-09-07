package dev.vidscreen.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(VidScreenForge.MOD_ID)
public final class VidScreenForge {
    public static final String MOD_ID = "vidscreen";

    public VidScreenForge() {
        VidScreenNetwork.register();
        VidScreenNetwork.setServerHandler(ForgeServerRuntime::handle);
        MinecraftForge.EVENT_BUS.register(new ForgeServerRuntime());
    }
}
