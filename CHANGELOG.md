# Changelog

All notable changes to Medirian Client. The launcher shows the newest entries on its home screen.

## 0.4.0 — 2026-10-08 — Glass, Codes & a Calmer Launcher

- The HUD in Liquid Glass: smaller, separate widgets on translucent glass — a soft blur of the world behind them, a faint light edge and rounded corners. Settings → HUD → Look: style (Glass or Classic), glass opacity, blur, border, corner radius and padding. Every widget still moves and scales on its own in the HUD editor. The blur is made once per frame, only while a glass widget is on screen.
- Settings → Advanced in the game, in five sections: Graphics, Rendering, Visibility, Performance and Interface. Minecraft's own options (render distance, graphics, clouds, particles, mipmaps, VSync, field of view, GUI scale, chat and more) change with exactly the same effects as in Minecraft's option screens, and only the options your Minecraft version has are shown. Every option says what it does.
- Visibility distances: players, entities, dropped items, block entities, particles, name tags, waypoints and cosmetics can each stop being drawn beyond a distance you choose (default: no limit).
- Distance fog can start later or be switched off (fog under water, in lava and from blindness stays), and animated textures can be frozen to save a little CPU and GPU time.
- Share a profile with a code like MDN-7K4X-92QP-L8F2: Profiles → Share makes the code, Import code shows what the profile contains before you add it as a new profile or replace one of yours. A code never carries your account, tokens, passwords or Java settings, and you can delete it any time.
- A calmer launcher: your selected profile and a big Play button come first, your skin stands next to them as a large 3D figure, and the key facts (version, loader, memory, mods) are at a glance. The account window shows your skin large.
- Your skin in the game's main menu is large now: you stand on the path in front of the cabin with your name above you. Click yourself to open the cosmetics.
- Mods come from Modrinth only. CurseForge is gone; mods installed from it in 0.3.0 stay in your profiles as local files.
- The launcher reaches Medirian services only over HTTPS, and the developer fields in its settings are gone: an official build carries its own configuration (About shows what this build has).

## 0.3.0 — 2026-10-06 — Mods, Skins & a Real Home on Windows

- Mods: a new Mods tab in the launcher. Search Modrinth and CurseForge, see each mod's icon, author, description, downloads and category, and install it into the profile you pick — with the mods it needs. Medirian checks the Minecraft version and loader first and tells you plainly when a mod does not fit (for example "This mod is not compatible with Minecraft 1.8.9").
- Installed mods: switch a mod off without deleting it, remove it, check for updates and update, open its page, and see what it needs and what needs it.
- Every profile now has its own folder (mods, mod settings, worlds), so the mods of one profile never load in another. Duplicating a profile copies its mods. Existing worlds move to your first profile of that version.
- Your Minecraft skin everywhere: a 3D figure on the home screen and in the account window, your head in the top bar, and a player card with your skin in the game's main menu. A skin changed on minecraft.net shows up by itself; without an account you see Medirian's own skin.
- Hats sit on your head now instead of floating above it: they sink a little over the forehead, wrap the head and its hat layer, and follow every head movement on all four versions. They hide while you wear a helmet, a pumpkin or a skull.
- Medirian Client installs like a real Windows app: MedirianClientSetup.exe installs it for you without administrator rights, with shortcuts on the desktop and in the Start Menu and an entry in Apps & features. Updates install quietly in the same place and keep your profiles and worlds.
- A download page: Download Medirian Client → Windows → MedirianClientSetup.exe.
- Minecraft 1.8.9 opens straight into Medirian's main menu (it used to show Minecraft's title screen first).
- The launcher's top bar fits narrow windows, also with an update waiting.

## 0.2.0 — 2026-10-05 — A Night in Medirian

- Medirian Client finally carries its real name everywhere: Medirian. Your settings and profiles move over automatically.
- A brand-new look: cozy pixel art and a Halloween night all year round — the logo's purple, pumpkin orange, moonlight, pumpkins and drifting fog. Halloween is part of Medirian, not a season.
- Medirian's own main menu in the game instead of Minecraft's: a night scene with a cabin and glowing pumpkins, the Medirian Client logo and a new menu (Singleplayer, Multiplayer, Medirian Mods, Options, Language, Quit). You can switch back to Minecraft's in Settings → General.
- A new Mod Menu: every module has its own pixel-art icon and an ON/OFF lamp. Click a tile to see its settings, click its lamp (or right-click the tile) to switch it on or off. Category tabs on the side, search at the top, Edit HUD, Cosmetics, Waypoints and Settings at the bottom.
- Settings, Cosmetics, the HUD editor, Waypoints and notifications in the game use the new pixel style too.
- The launcher is rebuilt from scratch: an animated night behind it, a notice board with the latest news, a launch bar with your profile and a big pumpkin PLAY button, profiles listed like worlds, and Medirian's own pixel font.
- In winter (December to 6 January) snow may fall over the night; switch it off in the settings. The animated night can be switched off in the launcher too.
- New app and mod icon.

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
