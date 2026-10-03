package dev.meridian.event;

import dev.meridian.input.Key;
import dev.meridian.platform.EntityView;
import dev.meridian.render.Gfx;

import java.io.File;

/**
 * All events posted by Meridian. Adapters never post events directly — they call
 * {@link dev.meridian.platform.Hooks}, which owns the reusable instances of per-frame events.
 */
public final class Events {

    private Events() {
    }

    /** Posted once per client tick (20/s) after the game tick. Reused instance. */
    public static final class Tick {
        public static final Tick INSTANCE = new Tick();

        private Tick() {
        }
    }

    /** Posted every frame while the in-game HUD is rendered. Reused instance. */
    public static final class RenderHud {
        public static final RenderHud INSTANCE = new RenderHud();
        public Gfx gfx;
        public float partialTicks;

        private RenderHud() {
        }
    }

    /** A mouse button changed state while playing (no screen open). */
    public static final class MouseButton {
        public final int button;
        public final boolean pressed;

        public MouseButton(int button, boolean pressed) {
            this.button = button;
            this.pressed = pressed;
        }
    }

    /** A keyboard key changed state while playing (no screen open). */
    public static final class KeyInput {
        public final Key key;
        public final boolean pressed;

        public KeyInput(Key key, boolean pressed) {
            this.key = key;
            this.pressed = pressed;
        }
    }

    /** The local player attacked an entity. {@code reach} is eye→hit point distance in blocks, or -1. */
    public static final class AttackEntity {
        public final EntityView target;
        public final double reach;

        public AttackEntity(EntityView target, double reach) {
            this.target = target;
            this.reach = reach;
        }
    }

    /** The local player took damage (hurt animation started). Derived from game state by the core. */
    public static final class PlayerHurt {
        public static final PlayerHurt INSTANCE = new PlayerHurt();

        private PlayerHurt() {
        }
    }

    /** The local player died (health reached zero). Derived from game state by the core. */
    public static final class PlayerDeath {
        public static final PlayerDeath INSTANCE = new PlayerDeath();

        private PlayerDeath() {
        }
    }

    /** A chat/system message was received. {@code plainText} has formatting codes removed. */
    public static final class ChatReceived {
        public final String plainText;

        public ChatReceived(String plainText) {
            this.plainText = plainText;
        }
    }

    /** A screenshot was written to disk. */
    public static final class ScreenshotTaken {
        public final File file;

        public ScreenshotTaken(File file) {
            this.file = file;
        }
    }

    /** The player joined a world. {@code serverAddress} is null in singleplayer. Derived by the core. */
    public static final class WorldJoin {
        public final String serverAddress;

        public WorldJoin(String serverAddress) {
            this.serverAddress = serverAddress;
        }
    }

    /** The player left the current world/server. */
    public static final class WorldLeave {
        public static final WorldLeave INSTANCE = new WorldLeave();

        private WorldLeave() {
        }
    }

    /** A configuration profile was applied. */
    public static final class ProfileLoaded {
        public final String profile;

        public ProfileLoaded(String profile) {
            this.profile = profile;
        }
    }
}
