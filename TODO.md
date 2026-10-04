# Meridian — roadmapa

Stan: **0.1.4** (launcher, dwa targety klienta, HUD, 41 modułów, konfiguracja, wydajność).
Poniżej kolejne etapy. Zasada bez zmian: funkcja trafia do UI dopiero, gdy naprawdę działa.

## Etap 1 — przed pierwszym publicznym wydaniem

- [ ] **Rejestracja aplikacji Azure + zgoda Mojang** dla logowania Microsoft (`MERIDIAN_MSA_CLIENT_ID`).
      Kod device-code flow jest gotowy (`launcher/src/main/auth`), brakuje wyłącznie identyfikatora.
- [x] **Hosting wydań** (0.1.4) — `.github/workflows/release.yml`: tag `v<wersja>` → GitHub Release z jarami,
      `release-manifest.json` (adresy z `--base-url`), instalatorami i notatkami z CHANGELOG; domyślny manifest
      wbudowany przez `MAIN_VITE_MANIFEST_URL` (`releases/latest/download/release-manifest.json`). Opis: docs/RELEASING.md.
- [x] **Auto-update launchera** (0.1.4) — `electron-updater` (`main/updates/launcherUpdate.ts`), feed z GitHub Releases;
      sprawdzone lokalnie end-to-end (0.1.4 wykrył i pobrał 0.1.5 z lokalnego serwera).
- [ ] **Podpisywanie kodu** — konfiguracja gotowa (sekrety `CSC_LINK`, `CSC_KEY_PASSWORD`, `APPLE_*` w workflow),
      brakuje certyfikatów właściciela (Authenticode, Apple Developer ID). Bez podpisu auto-update na macOS nie działa.
- [ ] Licencja projektu (do wyboru przez właściciela) i pola `license` w `fabric.mod.json` / `package.json`.
- [x] CI (0.1.4) — `.github/workflows/ci.yml`: testy Shared, build obu targetów, launcher (build + testy) na
      Windows/macOS/Linux, testy skryptów, self-test w Xvfb z porównaniem zrzutów.
- [ ] Test na macOS (arm64: `jre-legacy` przez Rosettę) i Linux — launcher jest budowany i testowany na wszystkich
      trzech systemach w CI, a self-test klienta działa w Xvfb; ręczny test gry na prawdziwym macOS nadal potrzebny.
- [ ] Job wizualny CI jest nieblokujący, dopóki pierwszy przebieg na GitHubie nie potwierdzi wzorców (renderowanie
      Mesa/llvmpipe nie mogło być sprawdzone lokalnie).

## Etap 2 — moduły (zaplanowane, jeszcze nierejestrowane)

Każdy wymaga hooka w obu adapterach (nowe `Capability`):

- [x] **Damage Indicator** → moduł **Health Tags** (0.1.3): zdrowie doklejane do nametagu (1.8.9: `renderName`,
      modern: `EntityRenderer#extractRenderState`), plus Hurt Camera, Fire Overlay i HUD Speed.
- [x] **Item Physics** (0.1.3) — przedmioty leżą płasko, bloki stoją na ziemi, bez unoszenia i obrotu.
- [x] **Waypoints** (0.1.2) — dane per świat/wymiar (`waypoints.json`, wspólne dla obu wersji), ekran zarządzania,
      znaczniki rzutowane na HUD (`render/Projection`), klawisz dodawania, punkt śmierci.
- [x] Waypoints (0.1.3): promień w świecie (1.8.9: `renderEntities`, modern: `renderBlockOutline` HEAD), znaczniki przy
      krawędzi ekranu dla punktów poza widokiem (`Projection.edge`), eksport/import przez schowek (JSON lub „nazwa x y z”).
- [x] **Chat** — znaczniki czasu, scalanie powtórzeń, dłuższa historia (0.1.2).
- [x] **Chat: kopiowanie wiadomości** (0.1.3) — prawy klik linii w otwartym czacie kopiuje całą wiadomość
      (bez znacznika czasu i licznika); 1.8.9: mapa linia→wiadomość, modern: `endOfEntry`.
- [x] **Discord Rich Presence** (0.1.3) — launcher (`main/discord`), status z kanału live. Do zrobienia przez właściciela:
      aplikacja w Discord Developer Portal + asset `meridian`, identyfikator wbudowany przez `MAIN_VITE_DISCORD_APP_ID`.
- [x] **Occlusion culling** w Entity Culling (0.1.2): promienie z kamery do środka i narożników hitboxa (`perf/OcclusionCuller`).
- [x] **Culling block entities** (skrzynie, tabliczki, głowy, banery) tym samym `OcclusionCuller` (0.1.2).
- [x] Pomiar zysku z okluzji w self-teście (160 świń za ścianą, okluzja wł./wył.; pomiar pomijany, gdy ktoś używa okna):
      1.8.9 — świat 2,99 → 1,57 ms, klatka 3,14 → 1,86 ms; 1.21.11 — świat 2,27 → 0,60 ms, klatka 2,80 → 0,91 ms.
- [x] **Polityki serwerów** (0.1.3) — kanał `meridian:policy` (JSON z listą modułów do wyłączenia), `meridian:hello`;
      opis i przykład pluginu: docs/PROTOCOL.md §6.

## Etap 3 — kosmetyki

- [x] **Peleryny** (0.1.4) w obu adapterach — 1.8.9: getter peleryny (`getSkinId` w Legacy Yarn 604), modern:
      `AbstractClientPlayer#getSkin` (`PlayerSkin.cape`, elytra korzysta z tej samej tekstury). 6 oryginalnych peleryn HD
      (256×128, `scripts/generate-capes.mjs`), self-test sprawdza teksturę i robi zrzut z trzeciej osoby.
- [x] **Usługa kosmetyków** (0.1.4) — `RemoteCosmetics`: posiadanie z serwera, loadouty innych graczy pobierane
      partiami (≤ 100, cache 5 min), własny loadout wysyłany po zalogowaniu i po każdej zmianie. Backend: `backend/`.
- [x] **UI kosmetyków** (0.1.4) — ekran „Cosmetics” z mod menu (tylko gdy wersja renderuje peleryny), podgląd, status widoczności.
- [x] **Czapki i skrzydła** (0.1.4) — warstwa renderera gracza we wszystkich wersjach (1.8.9: feature renderer, modern:
      `RenderLayer` + `submitCustomGeometry`); modele czapek jako bryły w JSON (`meridian/cosmetics/models`), skrzydła
      generowane z animacją machania; tekstury i ikony z `scripts/generate-cosmetics.mjs`.
- [x] **Ślady** (0.1.4) — cząsteczki za poruszającymi się graczami (`Trails`), widoczne dla innych przez loadouty.
- [x] **Emotki** (0.1.4) — Wave/Cheer/Dance, klawisz emotki (domyślnie B), widok z przodu podczas emotki; inni gracze
      widzą emotki przez usługi (`/v1/emotes/play`, `/v1/emotes/active`, odpytywanie co sekundę).

## Etap 4 — konto Meridian

- [x] **Backend** (0.1.4, `backend/`, Node bez zależności, Dockerfile) — logowanie przez handshake sesji Mojang
      (join/hasJoined, token gry trafia tylko do Mojang), kosmetyki, profile; opis: docs/SERVICES.md. Klient: `MeridianServices`.
- [x] **Profile w chmurze** (0.1.4) — Ustawienia → Profile → Chmura Meridian: wyślij aktywny, pobierz, usuń.
- [x] Test end-to-end (0.1.4): backend + atrapa serwera sesji + oba klienty w self-teście (logowanie, profil tam i z powrotem,
      własna peleryna widoczna „jako inny gracz”).
- [ ] **Wdrożenie usług** (właściciel): serwer z HTTPS, zmienna repozytorium `SERVICES_URL` dla buildów launchera.

## Etap 5 — kolejne wersje Minecrafta

- [x] Target **26.3** (0.1.4) — `client/targets/mc-26.3`, plugin `fabric-loom` bez remapowania, Java 25. Port objął SDL3
      (wejście), `GuiGraphicsExtractor`/`Hud`, submit świata, zegary świata; self-test (audyt mixinów, wszystkie kontrole,
      peleryny), wzorce wizualne, CI i pełna ścieżka launchera (Java 25 → gra → mostek) sprawdzone.
- [ ] Opcjonalnie dodatkowe targety 1.21.x (np. 1.21.4/1.21.8) — kopia `mc-1.21.11` + poprawki API (zob. ARCHITECTURE §12).

## Etap 6 — UI / jakość

- [ ] Własny renderer czcionki (SDF/MSDF) dla ostrzejszego tekstu w HUD i menu.
- [x] Zaokrąglone prostokąty (0.1.4) — zamiast shadera: batchowanie (poniżej) sprawia, że zaokrąglenia nie kosztują
      dodatkowych draw calli, a krawędzie są wygładzane na CPU (pokrycie piksela, `UiDraw.coverage`) — identycznie w obu
      wersjach, bez GLSL w 1.8.9 i bez własnego `RenderPipeline` w 1.21.11.
- [x] Batchowanie wypełnień w `LegacyGfx` (0.1.4) — jeden bufor `POSITION_COLOR`, opróżniany przed tekstem/teksturą/scissorem
      i na końcu klatki: 586 wypełnień → 14 draw calli, HUD 1,00 → 0,20 ms/klatkę (self-test, PvP profile).
- [x] Testy wizualne (0.1.4): `scripts/visual-test.mjs` porównuje zrzuty menu z `client/visual-baselines/`; self-test
      startuje z czystą konfiguracją bez animacji/powiadomień/motywu sezonowego — dwa przebiegi lokalnie: 0 px różnicy
      (edytor HUD: 148 px = licznik FPS).
- [x] Więcej języków (0.1.3): niemiecki i hiszpański w kliencie i launcherze; test pilnuje kompletności plików `lang`.
- [x] Sezonowe motywy: Christmas (0.1.3) — `Theme.CHRISTMAS` z opadami śniegu, tokeny CSS i śnieg w launcherze; AUTO: grudzień–6 stycznia.
