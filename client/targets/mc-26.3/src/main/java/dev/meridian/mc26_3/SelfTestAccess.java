package dev.meridian.mc26_3;

import net.minecraft.client.Minecraft;

/** Public entry to the package-private {@link SelfTest} for mixins. */
public final class SelfTestAccess {

    private SelfTestAccess() {
    }

    public static void tick(Minecraft minecraft) {
        SelfTest.tick(minecraft);
    }
}
