# Changelog

All notable changes to Meridian Client. The launcher shows the newest entries on its home screen.

## 0.1.2 — 2026-10-03 — Chat & Waypoints

- New module: Waypoints — mark places per world and dimension and see them on screen with their distance, even through walls; add them at your position with a key, manage them in their own screen; optional death point. Shared between 1.8.9 and 1.21.11.
- New module: Chat — timestamps, repeated messages stacked into one line with a counter (x2, x3…) and a chat history of up to 1000 messages.
- Entity Culling now also skips entities and block entities (chests, signs, heads, banners…) completely hidden behind solid blocks; players are only hidden when you allow it. With 160 mobs behind a wall, frame time dropped by about 40% (1.8.9) and 65% (1.21.11) in our test scene.
- Fixed (1.8.9): Target HUD crashed the game when showing a player without a cape.

## 0.1.1 — 2026-10-03 — Block Overlay & Hit Color

- New module: Block Overlay — outline color, thickness and an optional translucent fill of the selected block.
- New module: Hit Color — custom damage flash color with intensity relative to vanilla.
- Fixed: the mod menu key (Right Shift) opened the menu and closed it again in the same key press.
- Launcher always checks for a newer client when you press Play.

## 0.1.0 — 2026-10-03 — Foundations

- Meridian Launcher: first-run setup with diagnostics, automatic Java (8 and 21) and game installation, launch profiles, Microsoft sign-in, repair and cache tools.
- Two client targets from one codebase: Minecraft 1.8.9 (Legacy Fabric) and 1.21.11 (Fabric).
- HUD system with a drag-and-drop HUD editor: snapping guides, scaling, per-element styling, add/remove and reset.
- 32 modules including Keystrokes, CPS, Reach Display, Combo Counter, Target HUD, Armor Status, Potion Effects, Zoom, Freelook and Toggle Sprint.
- Performance: Dynamic FPS, Particle Control, Entity Culling and three performance modes with real, measurable settings.
- Configuration profiles (Default, PvP, Performance and your own), shared between both Minecraft versions.
- Halloween theme with violet moonlight, fog and subtle orange accents.
