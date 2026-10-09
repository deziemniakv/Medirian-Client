# Medirian Client website

The official site: what Medirian is, what it does, and the download. Static HTML, CSS and JavaScript —
no framework, no build tooling, no dependencies. This file is not published.

| File | What |
|---|---|
| `index.html` | the page, in English |
| `pl.js` | Polish texts, by `data-t` key; loaded only when the page is shown in Polish |
| `style.css` | the launcher's palette, pixel buttons and the Medirian Pixel font |
| `app.js` | the download buttons (newest GitHub release), the Medirian Services status, navigation, the demos, EN/PL |
| `assets/` | art from `scripts/generate-pixel-art.mjs` (scene, logo, icons, capes, skin) and `screens/` |

## Build, check, publish

```bash
node scripts/build-website.mjs        # → distribution/website, configuration from .env / the environment
```

The build fills in the configuration (`#medirian-config` in `index.html`) and fails on: a missing file,
a `#link` without its target, an image without `alt` or with the wrong `width`/`height`, a text without its
Polish version (or an unused one), Minecraft versions that differ from `scripts/build-clients.mjs`, a module
count that differs from the client, template leftovers, and a size over budget (1.5 MB in total, 250 KB for
the first screen). `.github/workflows/pages.yml` runs it and publishes the result on GitHub Pages; the
configuration values are described in `docs/OWNER_SETUP.md` §16.

Opening `website/index.html` straight from the disk works too, as an unconfigured preview: the download
buttons then say "Coming soon".

## Texts

Every text element has `data-t="key"`; English stays in `index.html`, Polish goes into `pl.js` under the same
key. Texts `app.js` writes itself (download states, status) are in `TEXT` at its top. Only write what
Medirian really does: every claim on the page is checked against the code and `CHANGELOG.md`, and nothing
shows numbers we don't have (players, downloads, uptime).

## Art

`node scripts/generate-pixel-art.mjs` regenerates `assets/scene/*`, `logo.png`, `icon.png`, `icons.png` +
`icons.css` (the icon sprite: `<span class="ico ico--fps"></span>`, `--s` scales it), `capes/*`,
`cosmetics/*`, `skin-back.png`, `social.png` (link preview) and the font. To use another icon, add its name
to `WEB_ICONS` in that script.

## Screenshots

`assets/screens/*.webp` are real screens, saved as lossless WebP (pixel UI compresses to 5–25 KB):

| File | Source |
|---|---|
| `main-menu`, `mod-menu`, `settings`, `cosmetics` | the client's self-test captures, `client/visual-baselines/mc-1.21.11/` (`0-title`, `1-modmenu`, `3-settings`, `3b-cosmetics`) |
| `launcher-profiles` | the launcher's development capture (`MEDIRIAN_DEV_CAPTURE`, `page-profiles`) |

To add one (for example the launcher's Mods tab with Modrinth results, the HUD in a world, or Settings →
Advanced):

1. Capture it: the launcher with `MEDIRIAN_DEV_CAPTURE=<folder> npm run dev` in `launcher/` (needs network
   access to Modrinth for the Mods tab), the game with `./gradlew runClient -Pselftest` or F2 in a world.
   Never edit a screenshot to show something the product doesn't do.
2. Convert it losslessly: `cwebp -lossless -z 9 shot.png -o assets/screens/<name>.webp`.
3. In `index.html` add a tab button and a `<figure>` in the `#screenshots` section (copy an existing pair;
   give the image its real `width`/`height` and an `alt` text), then add the tab's and caption's keys to `pl.js`.
4. `node scripts/build-website.mjs` tells you if anything is missing.
