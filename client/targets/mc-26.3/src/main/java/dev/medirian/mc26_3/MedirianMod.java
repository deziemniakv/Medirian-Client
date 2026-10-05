package dev.medirian.mc26_3;

import dev.medirian.core.Log;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entrypoint. Only wires logging here: Medirian boots once the Minecraft client is fully
 * constructed (see {@code MinecraftMixin}), because the window, options and texture manager do
 * not exist yet when Fabric calls client initialisers.
 */
public final class MedirianMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Medirian");

    @Override
    public void onInitializeClient() {
        Log.setSink(new Log.Sink() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message, Throwable error) {
                LOGGER.warn(message, error);
            }

            @Override
            public void error(String message, Throwable error) {
                LOGGER.error(message, error);
            }
        });
    }
}
