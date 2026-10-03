<p align="center">
  <img src="branding/meridian-icon-256.png" width="96" alt="Meridian Client" />
</p>

<h1 align="center">Meridian Client</h1>

<p align="center">Klient Minecraft z własnym launcherem: HUD, moduły PvP i wydajność — dla 1.8.9 i 1.21.11.</p>

---

## Co jest w repozytorium

| Część | Ścieżka | Technologia |
|---|---|---|
| **Meridian Launcher** | `launcher/` | Electron + React + TypeScript |
| **Meridian Shared** (rdzeń klienta, bez zależności od Minecrafta) | `client/shared/` | Java 8, Gson |
| **Adapter Minecraft 1.8.9** | `client/targets/mc-1.8.9/` | Legacy Fabric, Mixin, Java 8 |
| **Adapter Minecraft 1.21.11** | `client/targets/mc-1.21.11/` | Fabric, Mixin, Java 21 |
| Dokumentacja | `docs/ARCHITECTURE.md`, `docs/PROTOCOL.md`, `TODO.md` | |
| Branding | `branding/` (źródłowe logo w `branding/source/`) | |

Pełny opis architektury i decyzji technologicznych: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Funkcje (0.1.2)

**Launcher** — kreator pierwszego uruchomienia z diagnostyką i automatycznymi naprawami, automatyczna
instalacja Javy (Mojang: Java 8 dla 1.8.9, Java 21 dla 1.21.11), pobieranie i weryfikacja SHA-1 plików
gry, ponowne użycie assetów z istniejącego `.minecraft`, profile uruchomieniowe (wersja, RAM, Java,
argumenty JVM, rozdzielczość, profil konfiguracji Meridian), logowanie Microsoft (device code),
aktualizacje klienta z manifestu wydań (kanał stabilny / lokalny), naprawa instalacji, czyszczenie cache,
podgląd logu gry, status gry na żywo (kanał launcher ↔ klient), changelog, motyw Halloween, PL/EN.

**Klient** — mod menu (kategorie | moduły | ustawienia, wyszukiwarka), edytor HUD (przeciąganie,
przyciąganie z liniami pomocniczymi, skalowanie, panel właściwości, dodawanie/usuwanie, reset),
ustawienia globalne, profile konfiguracji (Default / PvP / Performance / własne) wspólne dla obu
wersji gry, powiadomienia, i18n PL/EN, motywy sezonowe.

Moduły (35): CPS, Combo Counter, Reach Display, Target HUD, Hit Color, Toggle Sprint, Toggle Sneak, Zoom, Freelook,
Armor Status, Potion Effects, Coordinates, Custom Crosshair, Block Overlay, Fullbright, Time Changer, Weather Changer,
Scoreboard, Direction (kompas), Biome, FPS, Keystrokes, Ping, Clock, Stopwatch, Session Info,
Dynamic FPS, Particle Control, Entity Culling (odległość + okluzja), Memory Monitor, FPS Graph, Chat (znaczniki czasu, scalanie
powtórzeń, dłuższa historia), Screenshot Tool, Auto GG, Server Info. Moduły zaplanowane (jeszcze bez UI): patrz [TODO.md](TODO.md).

## Wymagania deweloperskie

* **Node.js 22+** (launcher)
* **Java** — dowolna; Gradle sam pobiera właściwe JDK (21/25) przez *daemon JVM criteria*
  (`gradle/gradle-daemon-jvm.properties`), a toolchain Javy 8 dla `runClient` 1.8.9 przez foojay.
* Git, ~3 GB miejsca (cache Gradle/Loom, pliki gry).

## Budowanie

```bash
# 1. Klient: oba targety + manifest kanału lokalnego (distribution/)
node scripts/build-clients.mjs

# 2. Launcher
cd launcher
npm install
npm run dev          # tryb deweloperski (kanał "local" wskazuje na ../distribution)
npm run build        # typecheck + bundle
npm run dist         # instalator (electron-builder → launcher/dist/)
```

Pojedynczy target / testy:

```bash
cd client/shared && ./gradlew test            # testy jednostkowe rdzenia
cd client/targets/mc-1.21.11 && ./gradlew build
cd client/targets/mc-1.21.11 && ./gradlew runClient            # gra z Meridian (konto dev)
cd client/targets/mc-1.21.11 && ./gradlew runClient -Pselftest # zrzuty ekranów UI + audyt mixinów
```

`-Pselftest` przechodzi przez mod menu, edytor HUD, ustawienia i HUD w świecie, zapisuje zrzuty do
`run/screenshots/` i weryfikuje wszystkie wstrzyknięcia Mixin (`MixinEnvironment.audit()`).

Automatyzacja launchera (tylko build deweloperski): `MERIDIAN_DEV_CAPTURE=<dir>` robi zrzuty
wszystkich ekranów, `MERIDIAN_DEV_LAUNCH=<profil>` uruchamia grę przez pełny pipeline instalacji
(szczegóły w `launcher/src/main/devAutomation.ts`).

## Konfiguracja wydania

| Zmienna | Gdzie | Znaczenie |
|---|---|---|
| `MERIDIAN_MSA_CLIENT_ID` | build/uruchomienie launchera (lub *Ustawienia → Deweloperskie*) | identyfikator aplikacji Azure dla logowania Microsoft |
| `MERIDIAN_MANIFEST_URL` | uruchomienie launchera | domyślny URL manifestu wydań (kanał stabilny) |
| `MERIDIAN_HOME` | launcher i klient | zmiana folderu danych (domyślnie `%APPDATA%\.meridian`) |

**Logowanie Microsoft** wymaga własnej rejestracji aplikacji w Azure (konta osobiste, przepływ
„device code”, uprawnienie `XboxLive.signin`) oraz zatwierdzenia przez Mojang dostępu do API Minecraft
Services — to wymóg Mojang dla każdego launchera. Do tego czasu build deweloperski udostępnia konto
offline do testów w singleplayer.

**Publikacja**: `node scripts/build-clients.mjs --base-url https://twoj-cdn/meridian/0.1.0/`, wgraj
zawartość `distribution/` pod ten adres i ustaw URL manifestu w launcherze.

## Struktura danych

Launcher i klient współdzielą `MERIDIAN_HOME` — opis plików i protokołu: [docs/PROTOCOL.md](docs/PROTOCOL.md).
