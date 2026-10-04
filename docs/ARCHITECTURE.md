# Meridian Client — Architektura

Dokument opisuje architekturę całego produktu **Meridian Client**: launchera, wspólnego rdzenia klienta,
adapterów wersji Minecrafta oraz kontraktów, które je łączą. Jest to dokument „żywy” — każda większa
zmiana systemu powinna być tu odnotowana.

Spis treści

1. [Plan architektury (widok z góry)](#1-plan-architektury)
2. [Stack technologiczny i decyzje](#2-stack-technologiczny-i-decyzje)
3. [Struktura folderów](#3-struktura-folderów)
4. [Architektura launchera](#4-architektura-launchera)
5. [Architektura klienta](#5-architektura-klienta)
6. [Architektura modułów](#6-architektura-modułów)
7. [Architektura HUD](#7-architektura-hud)
8. [Architektura konfiguracji](#8-architektura-konfiguracji)
9. [Architektura aktualizacji](#9-architektura-aktualizacji)
10. [Komunikacja launcher ↔ klient](#10-komunikacja-launcher--klient)
11. [Design system i motywy sezonowe](#11-design-system-i-motywy-sezonowe)
12. [Dodawanie nowej wersji Minecrafta](#12-dodawanie-nowej-wersji-minecrafta)

---

## 1. Plan architektury

```
                         ┌──────────────────────────────────────────────┐
                         │            MERIDIAN LAUNCHER (Electron)       │
                         │  UI (React)  ◄─IPC─►  Main process services   │
                         │  Home/Play · Profiles · Settings · Account    │
                         │  Installer · Repair · Updates · Java · Auth   │
                         └───────────────┬───────────────▲──────────────┘
              pobiera i weryfikuje pliki │               │ status gry (TCP localhost, JSON lines)
                                         ▼               │
   ~/.meridian (MERIDIAN_HOME) ── wspólne dane: config/, profiles, runtime/, game/, instances/
                                         │
                ┌────────────────────────┴─────────────────────────┐
                ▼                                                  ▼
   ┌─────────────────────────────┐                ┌──────────────────────────────┐
   │ Minecraft 1.8.9             │                │ Minecraft 1.21.11            │
   │ + Legacy Fabric Loader      │                │ + Fabric Loader              │
   │ + meridian-1.8.9.jar        │                │ + meridian-1.21.11.jar       │
   │   ├─ Meridian Shared (Java8)│                │   ├─ Meridian Shared (Java8) │
   │   └─ Adapter 1.8.9          │                │   └─ Adapter 1.21.11         │
   │      mixiny, render, eventy │                │      mixiny, render, eventy  │
   └─────────────────────────────┘                └──────────────────────────────┘
```

Trzy warstwy:

| Warstwa | Odpowiedzialność | Zależy od Minecrafta? |
|---|---|---|
| **Launcher** | instalacja, aktualizacje, konto, profile uruchomieniowe, uruchamianie procesu gry | nie (zna tylko formaty plików Mojang/Fabric) |
| **Meridian Shared** | moduły, HUD, edytor HUD, mod menu, konfiguracja, eventy, powiadomienia, wydajność, kosmetyki (API), i18n, motywy | **nie** — zero importów `net.minecraft` |
| **Adapter wersji** | integracja z grą: mixiny, eventy, backend renderowania, dostęp do danych gry, funkcje zależne od wersji | tak — jedna wersja na adapter |

Kluczowa zasada: **Shared nigdy nie importuje klas Minecrafta**. Każdy adapter implementuje interfejsy
platformy (`dev.meridian.platform.*`). Dzięki temu moduł HUD „FPS” czy edytor HUD są napisane raz,
a wyglądają i działają identycznie w 1.8.9 i 1.21.11 — użytkownik ma wrażenie jednego produktu.
Jednocześnie nie istnieje żaden „wspólny moduł minecraftowy” — każda wersja ma własny, niezależny
projekt Gradle, własne mixiny i własny backend renderowania.

## 2. Stack technologiczny i decyzje

### 2.1 Loader i sposób wstrzykiwania klienta — **Fabric (1.21.11) i Legacy Fabric (1.8.9)**

Rozważane opcje:

| Opcja | Zalety | Wady |
|---|---|---|
| Forge 1.8.9 (ForgeGradle 2) | duża scena PvP | wymaga Gradle 2–4 + Java 8 do budowania, martwy toolchain |
| Własny agent/injektor (jak Lunar) | pełna kontrola | ogromny koszt (własny remapper, mixin bootstrap), wysokie ryzyko |
| **Legacy Fabric 1.8.9 + Fabric 1.21.x** | ten sam model (Fabric Loader + Mixin) dla obu gałęzi, nowoczesny Gradle 9 + Loom, aktywnie utrzymywane | 1.8.9 nie korzysta z ekosystemu modów Forge |

**Decyzja:** Fabric Loader w obu gałęziach. Launcher uruchamia czystego Minecrafta z Fabric Loaderem
(profil z `meta.fabricmc.net` / `meta.legacyfabric.net`) i wrzuca jeden plik `meridian-<target>.jar`
do folderu `mods/` instancji. Ten sam model uruchamiania dla każdej wersji upraszcza launcher, a Mixin
daje precyzyjne, wydajne hooki bez reflection w gorących ścieżkach.

### 2.2 Wersje docelowe

* **Gałąź legacy:** Minecraft **1.8.9**, Legacy Fabric Loader, mapowania Legacy Yarn, bytecode Java 8,
  runtime Java 8 (`jre-legacy` od Mojang — LWJGL2 wymaga Javy 8).
* **Gałąź modern:** Minecraft **1.21.11** (ostatnie wydanie linii 1.21), Fabric Loader, **oficjalne
  mapowania Mojang**, Java 21 (`java-runtime-delta`).
  * Dlaczego Mojmap, a nie Yarn: od 26.1 Minecraft jest wydawany bez obfuskacji z nazwami Mojang.
    Pisząc adapter 1.21.11 na nazwach Mojang, port na 26.x to głównie zmiana konfiguracji Gradle.
  * Dlaczego 1.21.11 jako pierwszy target, a nie 26.3: gałąź „1.21+” w wymaganiach; 1.21.11 ma
    najszerzej wspierany ekosystem serwerów i jest punktem wyjścia do kolejnych targetów
    (26.x dodajemy jako osobny target — patrz [§12](#12-dodawanie-nowej-wersji-minecrafta)).
* **Najnowsza wersja:** Minecraft **26.3** (0.1.4), wydawany bez obfuskacji — plugin Loom `fabric-loom`
  bez remapowania i bez mappings, Java 25 (`java-runtime-epsilon`). Adapter powstał z kopii 1.21.11; główne
  zmiany 26.x: okno i wejście na **SDL3** (zamiast GLFW: `InputConstants` to kody SDL, stan przycisków myszy
  zapisywany z `MouseHandler#onButton`), GUI jako ekstrakcja stanu (`GuiGraphicsExtractor`, `extractRenderState`),
  HUD w osobnej klasie `Hud`, ekran w `Gui#screen()`, świat jako „submit” (`SubmitNodeCollector`), czas dnia
  w zegarach świata (`ClientClockManager`), lightmap w `LightmapRenderStateExtractor`.
* Fabric API w 1.21.11 jest **zagnieżdżone (jar-in-jar)** — tylko potrzebne moduły — więc launcher
  dystrybuuje jeden plik na target.

### 2.3 Meridian Shared — Java 8

Shared jest kompilowany z `--release 8`, bo musi działać w 1.8.9 (Java 8). Ograniczenia: brak `var`,
`record`, switch expressions, `List.of`. Jedyna zależność zewnętrzna to **Gson** (`compileOnly`),
dostarczany przez obie wersje gry (1.8.9 ma Gson 2.2.4 — używamy wyłącznie API dostępnego w 2.2.4).

### 2.4 Launcher — Electron + React + TypeScript

| Opcja | Werdykt |
|---|---|
| Tauri (Rust) | lżejszy, ale wymaga toolchainu Rust + MSVC; słabsza kontrola nad procesami Javy/streamami |
| Compose/JavaFX | wspólny JVM z klientem, ale słabszy ekosystem UI i trudniejszy premium-look |
| **Electron + React + TS** | dojrzały, pełny dostęp do Node (pliki, procesy, sieć, named pipes), standard rynkowy launcherów |

Zależności ograniczone do minimum: `react`, `react-dom`, `zustand` (stan UI). Brak bibliotek UI —
własny design system w CSS (tokeny). Build: `electron-vite`, pakowanie: `electron-builder`.
Logika launchera (instalator, pobieranie, uruchamianie) żyje **wyłącznie w procesie main**; renderer
komunikuje się przez wąskie, typowane API wystawione w `preload` (`contextIsolation: true`,
`sandbox: true`, `nodeIntegration: false`).

### 2.5 Toolchain budowania

* Gradle 9.7.1 (wrapper w każdym targetcie), Fabric Loom 1.18 (`fabric-loom-remap`) dla 1.21.11,
  Loom 1.16 + `legacy-looming` 1.16 dla 1.8.9 (wersje muszą być równe — dlatego **każdy target jest
  osobnym buildem Gradle**, a nie subprojektem jednego buildu).
* JVM Gradle wybierają **daemon JVM criteria** (`gradle/gradle-daemon-jvm.properties`): Java 21 dla
  Shared, Java 25 dla targetów (wymóg Loom 1.18). Gradle pobiera je sam — systemowa Java może być
  dowolna. `runClient` dla 1.8.9 działa na toolchainie Java 8 (jak w produkcji). Fabric API nie jest
  używane — tylko Fabric Loader + Mixin (jeden jar na target).
* Shared jest dołączany do każdego targetu jako *included build* (`includeBuild`) i „wtapiany” do jara
  moda.

### 2.6 Konto Microsoft

Logowanie: Microsoft OAuth 2.0 **device code flow** → Xbox Live → XSTS → Minecraft Services
(`login_with_xbox`) → weryfikacja posiadania gry (`entitlements`) → profil. Wymaga identyfikatora
aplikacji Azure (`MERIDIAN_MSA_CLIENT_ID`) zatwierdzonego przez Mojang do API Minecraft Services —
to wymóg Mojang dla każdego launchera. Tokeny są szyfrowane przez `safeStorage` (DPAPI na Windows).
W trybie deweloperskim (niespakowany launcher) dostępne jest konto offline do testów singleplayer.

## 3. Struktura folderów

```
Meridian/
├── README.md                    przegląd, szybki start, budowanie
├── TODO.md                      roadmapa etapów
├── CHANGELOG.md                 źródło changelogu (wbudowywany w launcher)
├── docs/
│   ├── ARCHITECTURE.md          ten dokument
│   └── PROTOCOL.md              kontrakty plików i IPC launcher ↔ klient
├── branding/                    logo (SVG/PNG), paleta
├── scripts/
│   └── build-clients.mjs        buduje wszystkie targety + generuje manifest kanału „local”
├── distribution/                wynik build-clients: manifest + jary (gitignored)
├── client/
│   ├── shared/                  Meridian Shared (Java 8, bez Minecrafta) + testy JUnit
│   └── targets/
│       ├── mc-1.8.9/            adapter Legacy Fabric 1.8.9
│       ├── mc-1.21.11/          adapter Fabric 1.21.11
│       └── mc-26.3/             adapter Fabric 26.3 (bez obfuskacji)
└── launcher/                    Electron + React + TypeScript
    ├── src/main/                proces main: serwisy (install, launch, auth, java, updates…)
    ├── src/preload/             wąskie API dla UI
    ├── src/renderer/            UI (React)
    └── src/common/              typy współdzielone main ↔ renderer
```

## 4. Architektura launchera

### 4.1 Procesy

* **Main** — jedyne miejsce z dostępem do dysku, sieci i procesów. Podzielony na serwisy:

| Serwis | Plik | Odpowiedzialność |
|---|---|---|
| `paths` | `core/paths.ts` | lokalizacja `MERIDIAN_HOME` i podkatalogów |
| `SettingsStore` | `core/settings.ts` | ustawienia launchera (JSON, atomowy zapis) |
| `ProfileStore` | `profiles/profiles.ts` | profile uruchomieniowe |
| `Downloader` | `net/downloader.ts` | kolejka pobierania: równoległość, retry z backoffem, SHA-1, atomowe `.part` → rename |
| `MojangService` | `minecraft/mojang.ts` | manifest wersji, version JSON, biblioteki (reguły OS), assety, natywki |
| `LoaderService` | `minecraft/loader.ts` | profile Fabric / Legacy Fabric z meta API, scalanie z version JSON |
| `JavaService` | `java/*.ts` | wykrywanie zainstalowanych Jav, instalacja runtime'ów Mojang |
| `UpdateService` | `updates/updates.ts` | manifest wydań Meridian, kanały stable/local |
| `Installer` | `install/installer.ts` | plan instalacji targetu, weryfikacja, naprawa |
| `SetupService` | `install/setup.ts` | kreator pierwszego uruchomienia (diagnostyka + automatyczne naprawy) |
| `GameLauncher` | `launch/launcher.ts` | budowa classpath/argumentów, uruchomienie procesu, logi |
| `AccountService` | `auth/*.ts` | Microsoft device code flow, odświeżanie tokenów |
| `ClientBridge` | `launch/bridge.ts` | serwer TCP localhost dla klienta w grze |

* **Preload** — `window.meridian` z metodami `invoke` (request/response) i `on` (zdarzenia postępu).
  Wszystkie kanały i typy są zdefiniowane w `src/common/ipc.ts` — jedno źródło prawdy.
* **Renderer** — React, bez dostępu do Node. Strony: Home (hero + PLAY), Profiles, Versions,
  Settings, Account, Changelog, Setup (kreator), Logs.

### 4.2 Pipeline „PLAY”

```
resolveTarget(profile) → ensureJava(target) → ensureVersion(mc) → ensureLoader(target)
 → ensureClientJar(target) → ensureAssets → extractNatives (1.8.9) → buildLaunchCommand
 → spawn(java) → ClientBridge.waitForHello → status „In game”
```

Każdy krok raportuje postęp (`TaskProgress`) do UI. Wszystkie pobierane pliki mają znany SHA-1
(z manifestów Mojang/Meridian) i są weryfikowane; biblioteki Fabric z Maven są weryfikowane przez
pliki `.sha1` z repozytorium.

### 4.3 Magazyn danych gry

Pliki gry są przechowywane raz (`game/versions`, `game/libraries`, `game/assets`) i współdzielone
przez wszystkie targety; każdy target ma osobny `gameDir` (`instances/<target>/`) — inne `mods/`,
inny format `options.txt`, niekompatybilne światy. Jeśli w systemie istnieje `.minecraft`, assety są
kopiowane z niego (po weryfikacji hash), zamiast pobierania z sieci.

## 5. Architektura klienta

Shared (`client/shared`, pakiet `dev.meridian`):

| Pakiet | System | Zawartość |
|---|---|---|
| `core` | **Core** | `Meridian` (fasada/bootstrap), `MeridianHome`, `Version`, cykl życia |
| `event` | **Event System** | `EventBus` (typowane, bez reflection przy dispatchu), zdarzenia gry |
| `module` | **Module System** | `Module`, `Category`, `ModuleManager`, `Capability` |
| `setting` | (Module System) | `BooleanSetting`, `NumberSetting`, `ColorSetting`, `EnumSetting`, `KeySetting`, `TextSetting` |
| `hud` | **HUD System** | `HudElement`, `HudManager`, `HudLayout`, `Anchor`, snapping |
| `config` | **Config System** | `ConfigManager`, profile, serializacja JSON, migracje |
| `render` | **Render System** | `Gfx` (backend renderowania — implementuje adapter), `Colors`, `Theme`, `UiDraw` |
| `perf` | **Performance System** | `PerformanceManager`, `FrameStats`, `PerformanceProfile` |
| `ui` | **UI System** | `MeridianScreen`, widgety, `ModMenuScreen`, `HudEditorScreen`, `SettingsScreen` |
| `cosmetics` | **Cosmetics System** | `CosmeticType`, `Cosmetic`, `CosmeticsProvider`, `Loadout` |
| `account` | **Account System** | `PlayerIdentity`, `MeridianAccountService` (interfejs) |
| `notify` | Notifications | `NotificationManager`, `Toast` |
| `input` | Input | `Key` (przenośne nazwy klawiszy), `ClickTracker`, `KeybindManager` |
| `i18n` | Lokalizacja | `I18n`, `lang/en_us.json`, `lang/pl_pl.json` |
| `ipc` | Bridge | `LauncherBridge` (klient TCP do launchera) |
| `platform` | **Platform SPI** | interfejsy, które implementuje adapter: `Platform`, `GameView`, `PlayerView`, `EntityView`, `Gfx`… |

Adapter (`client/targets/mc-X`, pakiet `dev.meridian.mc<ver>`):

* `MeridianMod` — entrypoint Fabric (`ClientModInitializer`), tworzy `XPlatform` i wywołuje `Meridian.boot(platform)`.
* `XPlatform` — implementacja SPI: dane gracza, świata, serwera, klawiszy, opcji wideo.
* `XGfx` — backend `Gfx` (1.8.9: `Gui`/`FontRenderer`/`GlStateManager`; 1.21.11: `GuiGraphics`).
* `ScreenBridge` — ekran Minecrafta, który deleguje do `MeridianScreen` (shared).
* `mixin/*` — hooki: tick, render HUD, kliknięcia, atak, FOV (zoom), kamera (freelook), cząsteczki,
  culling encji, limit FPS, crosshair, scoreboard.

Cykl życia: `boot` → wczytanie konfiguracji → rejestracja modułów (filtrowanych przez capabilities
platformy) → zastosowanie profilu → połączenie z launcherem (jeśli uruchomiony przez launcher) →
pętla: `TickEvent` (20/s), `RenderHudEvent` (co klatkę), `FrameEvent` (pomiary).

## 6. Architektura modułów

```java
public abstract class Module {
    String id;            // stabilny identyfikator, klucz w configu i i18n: "keystrokes"
    Category category;    // COMBAT, MOVEMENT, PLAYER, RENDER, WORLD, HUD, MISC, PERFORMANCE
    List<Setting<?>> settings;
    KeySetting keybind;   // przenośna nazwa klawisza ("R", "RSHIFT")
    boolean enabled;
    Set<Capability> requires();  // np. ZOOM, FREELOOK, PARTICLE_CONTROL
    HudElement hud();            // opcjonalny komponent HUD
    void onEnable(); void onDisable();
}
```

* **Capabilities zamiast fake'ów.** Moduł deklaruje, czego potrzebuje od platformy (np. `Capability.FOV_HOOK`).
  Adapter deklaruje, co implementuje. `ModuleManager` rejestruje tylko moduły, których wymagania są
  spełnione — w UI nigdy nie pojawi się moduł, który w danej wersji nic nie robi.
* **Logika modułu jest wspólna, hook jest wersyjny.** Np. `ZoomModule` (shared) liczy płynny mnożnik
  FOV; mixin `GameRenderer#getFov` w każdym adapterze tylko mnoży wynik przez `zoom.fovMultiplier()`.
* **Keybindy** są obsługiwane centralnie (`KeybindManager`) — tryby `TOGGLE` i `HOLD`.
* **Settings** są typowane, mają wartość domyślną, zakres/opcje, opcjonalną widoczność warunkową
  (`visibleWhen`) i są automatycznie serializowane.
* **i18n:** nazwy/opisy mają angielski fallback w kodzie, tłumaczenia z kluczy
  `module.<id>.name`, `module.<id>.desc`, `setting.<id>.<setting>`.

## 7. Architektura HUD

* `HudElement` ma `id`, rozmiar liczony z zawartości (`measure()`), `render(Gfx)` i wspólne
  ustawienia wyglądu: skala, kolor tekstu, tło (kolor + przezroczystość), obramowanie, cień tekstu.
* Pozycja zapisana jako **kotwica + przesunięcie** (`Anchor.TOP_LEFT … BOTTOM_RIGHT`, 9 kotwic) —
  układ przetrwa zmianę rozdzielczości i skali GUI. Kotwica jest wybierana automatycznie z położenia
  elementu (najbliższa tercja ekranu).
* `HudManager` renderuje elementy w jednym przebiegu; elementy z kosztowną treścią cachują tekst
  i odświeżają się z ograniczoną częstotliwością (`refreshIntervalMs`). Koszt renderowania HUD jest
  mierzony (`PerformanceManager`) i widoczny w ustawieniach.
* **HUD Editor** (`HudEditorScreen`): przeciąganie, przyciąganie do krawędzi/środka/innych elementów
  (z liniami pomocniczymi), skalowanie uchwytem i kółkiem myszy, panel właściwości zaznaczonego
  elementu, lista elementów do dodania/usunięcia, reset układu, zapis do profilu.

## 8. Architektura konfiguracji

```
MERIDIAN_HOME/config/
├── client.json            { activeProfile, language, theme, notifications, animations, hudScale }
├── profiles/
│   ├── default.json       ┐
│   ├── pvp.json           │ { version, modules: { id: { enabled, keybind, settings{} } },
│   ├── performance.json   │   hud: { id: { anchor, x, y, scale } }, performance: {...} }
│   └── <custom>.json      ┘
└── cosmetics.json         { loadout: { CAPE: "meridian_moon", ... } }
```

* Zapisy atomowe (`*.tmp` → rename), debounce 1 s, zapis przy zamknięciu gry.
* Pole `version` + migracje (`ConfigMigrations`).
* Profile wbudowane (Default, PvP, Performance) są generowane z presetów przy pierwszym uruchomieniu
  i mogą być edytowane; profil „Custom” = dowolny profil utworzony przez użytkownika.
* Klawisze zapisane przenośnymi nazwami → ten sam profil działa w 1.8.9 i 1.21.11.
* Launcher czyta `config/profiles/` (lista profili do wyboru w profilu uruchomieniowym) i przekazuje
  wybrany profil przez `-Dmeridian.profile=<name>`.

## 9. Architektura aktualizacji

* **Manifest wydań** (`release-manifest.json`, schemat w `docs/PROTOCOL.md`): wersja klienta, lista
  targetów (wersja MC, loader + wersja, komponent Javy, artefakt: URL, SHA-1, rozmiar), changelog,
  minimalna wersja launchera.
* **Kanały:**
  * `stable` — manifest pod URL-em z ustawień (np. GitHub Releases / CDN).
  * `local` — manifest z katalogu `distribution/` generowany przez `scripts/build-clients.mjs`
    (development, testy przed wydaniem).
* Launcher przy starcie (i na żądanie) sprawdza manifest → porównuje z zainstalowanymi plikami
  (`clients/<target>/<version>/`) → pobiera nowe jary, weryfikuje SHA-1, przełącza atomowo.
  Stare wersje są usuwane przy „Clear cache”.
* Wersje loaderów są **przypięte w manifeście** (reprodukowalność — nie „latest”).
* Aktualizacja samego launchera: interfejs `LauncherUpdater` (TODO: `electron-updater` + podpisywanie
  kodu; wymaga hostingu wydań).

## 10. Komunikacja launcher ↔ klient

1. **Pliki** — wspólny `MERIDIAN_HOME/config` (profile, ustawienia).
2. **Argumenty JVM** — `-Dmeridian.home`, `-Dmeridian.profile`, `-Dmeridian.target`,
   `-Dmeridian.launcher.port`, `-Dmeridian.launcher.token`.
3. **Kanał live** — klient łączy się z `127.0.0.1:<port>` i wysyła/odbiera linie JSON
   (`hello`, `status`, `notify`). Token jednorazowy chroni przed obcymi połączeniami.
   Szczegóły: [`PROTOCOL.md`](PROTOCOL.md).

## 11. Design system i motywy sezonowe

Tokeny kolorów są zdefiniowane identycznie w launcherze (`launcher/src/renderer/styles/tokens.css`)
i kliencie (`dev.meridian.render.Theme`). Motywy: `DEFAULT` (Meridian Violet), `HALLOWEEN`
(mgła, księżyc, oszczędne pomarańczowe akcenty), `AUTO` (wybiera motyw sezonowy po dacie).
Nowy sezon = nowa instancja `Theme` + zestaw tokenów CSS — bez zmian w komponentach.

| Token | Default | Halloween |
|---|---|---|
| `bg` | `#0B0A10` | `#0A0810` |
| `surface` | `#13111A` | `#130F1B` |
| `accent` | `#9B55D6` (fiolet z logo, rozjaśniony na ciemne tło) | `#9B55D6` |
| `seasonal` | = accent | `#E8833A` (rzadko: znacznik sezonu, linie pomocnicze edytora) |
| `text` / `textDim` | `#ECEAF2` / `#9A94AB` | jw. |

## 12. Dodawanie nowej wersji Minecrafta

1. Skopiuj najbliższy target (`client/targets/mc-1.21.11` → `mc-1.21.4` lub `mc-26.3`).
2. Zmień `gradle.properties` (wersja MC, loader, Fabric API, Loom; dla 26.x plugin `fabric-loom`
   bez remapowania).
3. Popraw mixiny i backend `Gfx` pod zmiany API — shared pozostaje bez zmian. Kompilator wskaże zmienione
   klasy; cele mixinów (nazwy metod, deskryptory, cele `INVOKE`) sprawdza dopiero uruchomienie z
   `-Pselftest` (audyt mixinów) — przy dużych zmianach szybciej jest porównać je z `javap` na jarze gry.
4. Zadeklaruj capabilities w `XPlatform`.
5. Dodaj target do `scripts/build-clients.mjs` — launcher wykryje go z manifestu automatycznie.

Launcher nie wymaga zmian: każdy target jest opisany danymi w manifeście.
