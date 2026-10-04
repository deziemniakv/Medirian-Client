package dev.meridian.platform;

/**
 * Features a version adapter can implement. Modules declare which capabilities they need and are
 * only registered when the running adapter provides all of them — a module that cannot work in
 * a given Minecraft version is never shown to the user.
 */
public enum Capability {
    /** {@link Hooks#fovMultiplier()} is applied to the camera FOV and scroll is routed through {@link Hooks#mouseScroll}. */
    ZOOM,
    /** Camera rotation can be decoupled from the player ({@link Hooks#freelookActive()}). */
    FREELOOK,
    /** {@link Hooks#gamma(double)} is applied to the lightmap. */
    FULLBRIGHT,
    /** {@link Hooks#timeOfDay(long)} overrides the rendered time of day. */
    TIME_OVERRIDE,
    /** {@link Hooks#rainLevel(float)} / {@link Hooks#thunderLevel(float)} override rendered weather. */
    WEATHER_OVERRIDE,
    /** {@link Hooks#allowParticle(int)} is consulted for every new particle. */
    PARTICLE_CONTROL,
    /** {@link Hooks#shouldRenderEntity} is consulted for entities that pass the frustum check; {@link GameView#isOccluder} works. */
    ENTITY_CULLING,
    /** {@link Hooks#framerateLimit} controls the frame limiter. */
    DYNAMIC_FPS,
    /** The vanilla crosshair can be hidden ({@link Hooks#renderVanillaCrosshair()}). */
    CROSSHAIR,
    /** Sidebar data is exposed and the vanilla sidebar can be hidden. */
    SCOREBOARD,
    /** Vanilla HUD potion icons can be hidden (modern versions draw them top-right). */
    HIDE_VANILLA_EFFECTS,
    /** Sprint key state can be forced ({@link Hooks#forceSprint()}). */
    TOGGLE_SPRINT,
    /** Sneak key state can be forced ({@link Hooks#forceSneak()}). */
    TOGGLE_SNEAK,
    /** Attack and hurt events are posted. */
    COMBAT_EVENTS,
    /** Chat messages are posted and {@link ClientActions#sendChat} works. */
    CHAT,
    /** Screenshot events are posted. */
    SCREENSHOT_EVENTS,
    /** Items, effect icons and player heads can be drawn by {@link dev.meridian.render.Gfx}. */
    WORLD_ICONS,
    /** {@link GameView#biome()} is available. */
    BIOME,
    /** Block outline colour, width and fill come from {@link Hooks#blockOutlineColor(int)} and friends. */
    BLOCK_OUTLINE,
    /** The hurt overlay colour comes from {@link Hooks#hitColor()}. */
    HIT_COLOR,
    /** Chat lines are decorated by {@link Hooks#chatTimestamp}/{@link Hooks#chatStack} and the history length comes from {@link Hooks#chatHistory}. */
    CHAT_UTILITIES,
    /** {@link GameView#camera()}, {@link GameView#worldKey()} and {@link PlayerView#dimensionId()} work. */
    WAYPOINTS,
    /** Living entities' name tags get the text from {@link Hooks#healthTags(boolean)} appended. */
    HEALTH_TAGS,
    /** The hurt camera tilt is scaled by {@link Hooks#hurtCameraStrength()}. */
    HURT_CAMERA,
    /** The burning screen overlay uses {@link Hooks#fireOverlayOffset()} and {@link Hooks#fireOverlayAlpha(float)}. */
    FIRE_OVERLAY,
    /** Dropped items are positioned by {@link Hooks#itemPhysics()}. */
    ITEM_PHYSICS
}
