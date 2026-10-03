# Meridian — roadmapa

Stan: **0.1.2** (launcher, dwa targety klienta, HUD, 36 modułów, konfiguracja, wydajność).
Poniżej kolejne etapy. Zasada bez zmian: funkcja trafia do UI dopiero, gdy naprawdę działa.

## Etap 1 — przed pierwszym publicznym wydaniem

- [ ] **Rejestracja aplikacji Azure + zgoda Mojang** dla logowania Microsoft (`MERIDIAN_MSA_CLIENT_ID`).
      Kod device-code flow jest gotowy (`launcher/src/main/auth`), brakuje wyłącznie identyfikatora.
- [ ] **Hosting wydań**: opublikować `release-manifest.json` + jary (np. GitHub Releases / CDN),
      `node scripts/build-clients.mjs --base-url <url>`; ustawić domyślny `manifestUrl` (`MERIDIAN_MANIFEST_URL`).
- [ ] **Podpisywanie kodu** launchera (Windows Authenticode, macOS notarization) i **auto-update launchera**
      (`electron-updater`) — interfejs aktualizacji klienta już istnieje, launcher sam się jeszcze nie aktualizuje.
- [ ] Licencja projektu (do wyboru przez właściciela) i pola `license` w `fabric.mod.json` / `package.json`.
- [ ] CI: build wszystkich targetów + testy Shared + `npm run build` launchera na każdym PR.
- [ ] Test na macOS (arm64: `jre-legacy` przez Rosettę) i Linux.

## Etap 2 — moduły (zaplanowane, jeszcze nierejestrowane)

Każdy wymaga hooka w obu adapterach (nowe `Capability`):

- [ ] **Damage Indicator** — zdrowie nad głowami (renderowanie nametagów 3D).
- [ ] **Item Physics** — fizyka leżących przedmiotów.
- [x] **Waypoints** (0.1.2) — dane per świat/wymiar (`waypoints.json`, wspólne dla obu wersji), ekran zarządzania,
      znaczniki rzutowane na HUD (`render/Projection`), klawisz dodawania, punkt śmierci.
- [ ] Waypoints: promień (beam) w świecie, wskaźnik kierunku dla punktów poza ekranem, import/eksport.
- [x] **Chat** — znaczniki czasu, scalanie powtórzeń, dłuższa historia (0.1.2).
- [ ] **Chat: kopiowanie wiadomości** — kliknięcie linii w otwartym czacie kopiuje jej tekst
      (1.8.9: `ChatHud#getTextAt` daje tylko fragment — potrzebny indeks linii; modern: `ChatComponent#captureClickableText`).
- [x] **Discord Rich Presence** (0.1.3) — launcher (`main/discord`), status z kanału live. Do zrobienia przez właściciela:
      aplikacja w Discord Developer Portal + asset `meridian`, identyfikator wbudowany przez `MAIN_VITE_DISCORD_APP_ID`.
- [x] **Occlusion culling** w Entity Culling (0.1.2): promienie z kamery do środka i narożników hitboxa (`perf/OcclusionCuller`).
- [x] **Culling block entities** (skrzynie, tabliczki, głowy, banery) tym samym `OcclusionCuller` (0.1.2).
- [x] Pomiar zysku z okluzji w self-teście (160 świń za ścianą, okluzja wł./wył.; pomiar pomijany, gdy ktoś używa okna):
      1.8.9 — świat 2,99 → 1,57 ms, klatka 3,14 → 1,86 ms; 1.21.11 — świat 2,27 → 0,60 ms, klatka 2,80 → 0,91 ms.
- [ ] **Polityki serwerów** (np. wyłączenie Freelook tam, gdzie serwer go zabrania) — kanał plugin message.

## Etap 3 — kosmetyki

- [ ] `CosmeticRenderer` dla CAPE w obu adapterach (1.8.9: `AbstractClientPlayerEntity#getSkinId` — w Legacy Yarn 604 nazwy skin/cape są zamienione, modern: `PlayerSkin`).
- [ ] Usługa kosmetyków (katalog, własność, loadouty innych graczy) — `CosmeticsProvider` sieciowy.
- [ ] UI kosmetyków w mod menu (dopiero gdy istnieje renderer — wymóg „bez fake UI”).
- [ ] WINGS / HAT jako warstwy renderera gracza, EMOTE, TRAIL.

## Etap 4 — konto Meridian

- [ ] Backend kont (logowanie przez handshake sesji Mojang), `MeridianAccountService`.
- [ ] Synchronizacja profili konfiguracji w chmurze.

## Etap 5 — kolejne wersje Minecrafta

- [ ] Target **26.x** (bez obfuskacji: plugin `net.fabricmc.fabric-loom` bez remapowania, Java 25 `java-runtime-epsilon`).
- [ ] Opcjonalnie dodatkowe targety 1.21.x (np. 1.21.4/1.21.8) — kopia `mc-1.21.11` + poprawki API (zob. ARCHITECTURE §12).

## Etap 6 — UI / jakość

- [ ] Własny renderer czcionki (SDF/MSDF) dla ostrzejszego tekstu w HUD i menu.
- [ ] Zaokrąglone prostokąty shaderem zamiast rasteryzacji linii (mniej draw calli w 1.8.9).
- [ ] Batchowanie wypełnień w `LegacyGfx` (jeden draw call na klatkę HUD).
- [ ] Testy wizualne: porównywanie zrzutów z `runClient -Pselftest` w CI.
- [ ] Więcej języków (system i18n gotowy: `meridian/lang/*.json`, `launcher/src/renderer/src/i18n.ts`).
- [ ] Sezonowe motywy: Christmas (nowa instancja `Theme` + tokeny CSS).
