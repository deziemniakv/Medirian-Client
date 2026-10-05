# Changelog

All notable changes to Medirian Client. The launcher shows the newest entries on its home screen.

## 0.1.4 — 2026-10-04 — Polish & Platform

- Smoother menus and HUD: rounded corners, switches and circles now have anti-aliased edges in both versions.
- 1.8.9: the HUD is drawn with a handful of draw calls instead of one per rectangle — about 5× less time per frame in our test (0.20 ms instead of 1.00 ms).
- 1.8.9: very faint shapes (shadows, highlights) are no longer cut off, so menus look the same as in 1.21.11.
- Minecraft 26.3: Medirian now supports the newest Minecraft, with every module, the HUD, capes and Medirian services. A "Latest 26.3" profile is created on first start (Java 25 is installed automatically).
- Minecraft 1.21.8: for servers that have not moved to 1.21.11 yet — the same modules, HUD and cosmetics (the block outline keeps its fixed width there).
- New smooth font for Medirian's menus and HUD (Settings → General → Font): text is drawn at the exact resolution of your screen, so it stays sharp at every GUI scale.
- Hats, wings, trails and emotes: four hats (Top Hat, Crown, Witch Hat, Santa Hat), three pairs of animated wings, four particle trails and three emotes (Wave, Cheer, Dance — press B). All in the Cosmetics screen, all versions; other Medirian players see them when you are signed in.
- Capes: six original Medirian capes (Medirian, Moonlit, Ember, Frost, Aurora and Founder) in a new Cosmetics screen in the mod menu. Your cape is shown in third person and in your inventory, and to other Medirian players when you are signed in to Medirian services.
- Medirian account: signs in with your Minecraft account automatically (no password; your game token only goes to Mojang). It shares your cape with other players and keeps configuration profiles in the cloud (Settings → Profiles → Medirian Cloud).
- The launcher updates itself: installed versions download new releases in the background and install them when you close the launcher or click "Restart to update".
- Releases are built and published automatically, with release notes from this changelog.

## 0.1.3 — 2026-10-04 — Discord & PvP

- New module: Health Tags — health next to player name tags (hearts or HP, coloured by what is left, absorption as +N).
- New module: Hurt Camera — reduce or remove the camera shake when you take damage.
- New module: Fire Overlay — lower and fade the flames on screen while you burn.
- Waypoints: light beams in the world, markers at the screen edge for waypoints out of view, export and import through the clipboard.
- Chat: right-click a line in the open chat to copy the whole message (without timestamps or counters).
- New module: Item Physics — dropped items lie on the ground instead of floating and spinning.
- New HUD module: Speed — blocks per second (or km/h) over the last half second.
- Servers can switch off chosen Medirian modules (for example Freelook) through the medirian:policy plugin channel; they are restored when you leave.
- German and Spanish for the client and the launcher.
- Christmas theme for the client and the launcher, with snowfall; chosen automatically from December to 6 January.
- Discord Rich Presence: your Discord profile shows the Minecraft version, Medirian profile, whether you are in the menus, in singleplayer or on a server (the address can be hidden) and how long you have been playing. Settings → Discord.

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

- Medirian Launcher: first-run setup with diagnostics, automatic Java (8 and 21) and game installation, launch profiles, Microsoft sign-in, repair and cache tools.
- Two client targets from one codebase: Minecraft 1.8.9 (Legacy Fabric) and 1.21.11 (Fabric).
- HUD system with a drag-and-drop HUD editor: snapping guides, scaling, per-element styling, add/remove and reset.
- 32 modules including Keystrokes, CPS, Reach Display, Combo Counter, Target HUD, Armor Status, Potion Effects, Zoom, Freelook and Toggle Sprint.
- Performance: Dynamic FPS, Particle Control, Entity Culling and three performance modes with real, measurable settings.
- Configuration profiles (Default, PvP, Performance and your own), shared between both Minecraft versions.
- Halloween theme with violet moonlight, fog and subtle orange accents.
