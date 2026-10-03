package dev.meridian.mc1_8_9;

import dev.meridian.core.Log;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Legacy Fabric entrypoint. Only wires logging; Meridian boots after the game finished
 * initialising (see {@code MinecraftClientMixin}).
 */
public final class MeridianMod implements ClientModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("Meridian");

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
