package dev.meridian.mc1_21_11;

import net.minecraft.client.Minecraft;

/** Public entry to the package-private {@link SelfTest} for mixins. */
public final class SelfTestAccess {

    private SelfTestAccess() {
    }

    public static void tick(Minecraft minecraft) {
        SelfTest.tick(minecraft);
    }
}
