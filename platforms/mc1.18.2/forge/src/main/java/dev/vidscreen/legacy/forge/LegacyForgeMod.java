package dev.vidscreen.legacy.forge;

import net.minecraftforge.fml.common.Mod;

@Mod(LegacyForgeMod.MOD_ID)
public final class LegacyForgeMod {
    public static final String MOD_ID = "vidscreen";

    public LegacyForgeMod() {
        LegacyForgeNetwork.register();
    }
}
