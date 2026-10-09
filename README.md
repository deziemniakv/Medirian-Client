<p align="center">
  <img src="branding/medirian-icon-256.png" width="96" alt="Medirian Client" />
</p>

<h1 align="center">Medirian Client</h1>

<p align="center">Klient Minecraft z własnym launcherem: HUD w stylu Liquid Glass, moduły PvP, mody z Modrinth, wydajność — dla 1.8.9, 1.21.8, 1.21.11 i 26.3.</p>

<p align="center"><b>Pobierz:</b> Medirian Client → Windows → <code>MedirianClientSetup.exe</code> (strona: <code>website/</code>)</p>

---

## Co jest w repozytorium

| Część | Ścieżka | Technologia |
|---|---|---|
| **Medirian Launcher** | `launcher/` | Electron + React + TypeScript |
| **Medirian Shared** (rdzeń klienta, bez zależności od Minecrafta) | `client/shared/` | Java 8, Gson |
| **Adapter Minecraft 1.8.9** | `client/targets/mc-1.8.9/` | Legacy Fabric, Mixin, Java 8 |
| **Adapter Minecraft 1.21.11** | `client/targets/mc-1.21.11/` | Fabric, Mixin, Java 21 |
| **Adapter Minecraft 1.21.8** | `client/targets/mc-1.21.8/` | Fabric, Mixin, Java 21 (kopia 1.21.11 dla serwerów na 1.21.8) |
| **Adapter Minecraft 26.3** | `client/targets/mc-26.3/` | Fabric (bez obfuskacji, bez remapowania), Mixin, Java 25, SDL3 |
| Dokumentacja | `docs/OWNER_SETUP.md` (konfiguracja właściciela), `docs/ARCHITECTURE.md`, `docs/PROTOCOL.md`, `docs/RELEASING.md`, `docs/SERVICES.md`, `TODO.md` | |
| Konfiguracja właściciela | `.env.example` → `.env` (nie commitowany), `scripts/check-env.mjs` | |
| CI i wydania | `.github/workflows/`, `scripts/` | GitHub Actions, Node |
| **Usługi Medirian** (konta, kosmetyki, profile w chmurze) | `backend/` | Node 22+, bez zależności — [docs/SERVICES.md](docs/SERVICES.md) |
| **Strona internetowa** (GitHub Pages) | `website/` | statyczny HTML/CSS/JS, `scripts/build-website.mjs` |
| Branding | `branding/` (źródłowe logo w `branding/source/`) | |

Pełny opis architektury i decyzji technologicznych: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Wygląd

Medirian Client to klient w klimacie **cozy pixel-artowej nocy Halloween** — na stałe, nie sezonowo: fiolet z logo,
pomarańcz dyń, księżyc, mgła. Launcher i gra używają tej samej sceny, logo, ikon i palety, generowanych kodem przez
`node scripts/generate-pixel-art.mjs` (`scripts/pixel/`: scena, logo, 70+ ikon 16×16, własna czcionka Medirian Pixel).
W grze: własne menu główne, nowe Mod Menu (kafelki z ikonami modułów i lampką ON/OFF), wszystkie ekrany w stylu pixel art.

## Funkcje (0.4.0)

**HUD Liquid Glass** — mniejsze, osobne widżety na półprzezroczystym szkle: lekkie rozmycie świata pod spodem,
subtelna jasna krawędź, zaokrąglone rogi. *Ustawienia → HUD → Wygląd*: styl (Szkło / Klasyczny), krycie szkła, rozmycie,
obramowanie, zaokrąglenie, odstęp. Rozmycie to piramida zmniejszeń z filtrem liniowym (bez dodatkowych shaderów), robiona
raz na klatkę i tylko gdy widżet szklany jest na ekranie; każdy widżet nadal przesuwa się i skaluje osobno.

**Ustawienia → Zaawansowane** (w grze) — Grafika, Renderowanie, Widoczność, Wydajność, Interfejs. Opcje Minecrafta zmieniane
tak samo jak w jego ekranach opcji (te same efekty uboczne), tylko te, które ma dana wersja; dystanse rysowania graczy, encji,
przedmiotów, bloków-encji, cząsteczek, nazw, punktów nawigacyjnych i kosmetyków; mgła odległości (Minecraft / zmniejszona /
wyłączona); zatrzymanie animowanych tekstur. Każda opcja ma opis.

**Kody profili** — *Profile → Udostępnij* tworzy kod `MDN-XXXX-XXXX-XXXX` (usługi Medirian, ważny 90 dni, można go usunąć),
*Importuj kod* pokazuje zawartość i pyta przed nadpisaniem. Kod nigdy nie zawiera konta, tokenów, haseł ani ustawień Javy.


**Instalacja (Windows)** — `MedirianClientSetup.exe` instaluje Medirian Client dla bieżącego użytkownika bez uprawnień
administratora (`%LOCALAPPDATA%\Programs\Medirian Client\Medirian Client.exe`; opcjonalnie „dla wszystkich”), tworzy skrót
na pulpicie i w menu Start oraz wpis w „Aplikacje i funkcje” z deinstalatorem. Launcher aktualizuje się sam (electron-updater,
cicha instalacja w tym samym folderze); dane graczy w `MEDIRIAN_HOME` nie są ruszane przy instalacji, aktualizacji ani deinstalacji.

**Mody** — zakładka *Mods*: wyszukiwanie w Modrinth (oficjalne API v2, bez klucza), ikona, autor, opis, pobrania, kategorie,
sortowanie, paginacja; instalacja do wybranego profilu z wymaganymi zależnościami, sprawdzanie zgodności z wersją Minecrafta
i loaderem (Fabric / Legacy Fabric) z czytelnym komunikatem („Ten mod nie jest kompatybilny z Minecraft 1.8.9”), lista
zainstalowanych: włącz/wyłącz (`.jar.disabled`), usuń, aktualizacje, strona moda, zależności i „wymagany przez”.
Każdy profil ma własny folder gry (`MEDIRIAN_HOME/profiles/<profil>/` — mody, konfiguracja, światy), więc mody jednego
profilu nigdy nie trafiają do innego.

**Skin** — aktualny skin konta Minecraft (serwer sesji Mojang) w launcherze (duży model 3D obok wybranego profilu na ekranie
głównym i w oknie konta, głowa w pasku) i w menu głównym gry (duża postać na ścieżce przed chatką, z nickiem; kliknięcie otwiera
kosmetyki); odświeżany przy zmianie konta, powrocie do launchera i co 10 minut. Bez konta lub bez skina: domyślny skin Medirian.

**Launcher** — kreator pierwszego uruchomienia z diagnostyką i automatycznymi naprawami, automatyczna
instalacja Javy (Mojang: Java 8 dla 1.8.9, Java 21 dla 1.21.8 i 1.21.11, Java 25 dla 26.3), pobieranie i weryfikacja SHA-1 plików
gry, ponowne użycie assetów z istniejącego `.minecraft`, profile uruchomieniowe (wersja, RAM, Java,
argumenty JVM, rozdzielczość, profil konfiguracji Medirian), logowanie Microsoft (device code),
aktualizacje klienta z manifestu wydań (kanał stabilny / lokalny), automatyczna aktualizacja samego launchera, naprawa instalacji, czyszczenie cache,
podgląd logu gry, status gry na żywo (kanał launcher ↔ klient), changelog, Discord Rich Presence, animowana nocna scena (śnieg zimą), PL/EN/DE/ES.

**Klient** — własne menu główne, mod menu (zakładki kategorii, kafelki modułów z ikonami i lampką ON/OFF, strona ustawień modułu, wyszukiwarka), edytor HUD (przeciąganie,
przyciąganie z liniami pomocniczymi, skalowanie, panel właściwości, dodawanie/usuwanie, reset),
ustawienia globalne, profile konfiguracji (Default / PvP / Performance / własne) wspólne dla obu
wersji gry, powiadomienia, i18n PL/EN/DE/ES, śnieg zimą, kosmetyki (peleryny, czapki osadzone na głowie
i ukrywane pod hełmem, skrzydła, ślady, emotki), karta gracza ze skinem w menu głównym, konto Medirian
(logowanie kontem Minecraft przez handshake sesji Mojang) i profile w chmurze.

Moduły (41): CPS, Combo Counter, Reach Display, Target HUD, Hit Color, Health Tags, Toggle Sprint, Toggle Sneak, Zoom, Freelook,
Armor Status, Potion Effects, Coordinates, Custom Crosshair, Block Overlay, Hurt Camera, Fire Overlay, Item Physics, Fullbright, Time Changer, Weather Changer,
Scoreboard, Direction (kompas), Biome, Waypoints (punkty nawigacyjne), FPS, Keystrokes, Ping, Speed, Clock, Stopwatch, Session Info,
Dynamic FPS, Particle Control, Entity Culling (odległość + okluzja), Memory Monitor, FPS Graph, Chat (znaczniki czasu, scalanie
powtórzeń, dłuższa historia), Screenshot Tool, Auto GG, Server Info. Moduły zaplanowane (jeszcze bez UI): patrz [TODO.md](TODO.md).

## Zasoby zewnętrzne

* Czcionka **Lato** (Łukasz Dziedzic) — SIL Open Font License 1.1, plik licencji: `client/shared/src/main/resources/assets/medirian/fonts/OFL.txt`.
* Pozostałe grafiki (logo, peleryny, czapki, skrzydła, ikony) są oryginalne i generowane skryptami w `scripts/`.

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
npm run dist         # instalator Windows: launcher/dist/MedirianClientSetup.exe (electron-builder, NSIS)
npm test             # testy jednostkowe launchera (mody, profile, skiny, aktualizacje, Discord)
```

Lokalny `npm run dist` nie ma kanału auto-aktualizacji launchera (to dodaje workflow wydań). Konfigurację (logowanie
Microsoft, usługi, manifest) bierze z `.env` — patrz niżej.

Pojedynczy target / testy:

```bash
cd client/shared && ./gradlew test            # testy jednostkowe rdzenia
cd client/targets/mc-1.21.11 && ./gradlew build
cd client/targets/mc-1.21.11 && ./gradlew runClient            # gra z Medirian (konto dev)
cd client/targets/mc-1.21.11 && ./gradlew runClient -Pselftest # zrzuty ekranów UI + audyt mixinów
```

`-Pselftest` przechodzi przez mod menu, edytor HUD, ustawienia i HUD w świecie, zapisuje zrzuty do
`run/screenshots/` i weryfikuje wszystkie wstrzyknięcia Mixin (`MixinEnvironment.audit()`).
Zrzuty menu porównuje z wzorcami `node scripts/visual-test.mjs` (`--update` zapisuje nowe wzorce);
`node scripts/check-selftest.mjs <log>` sprawdza log. Testy skryptów: `node --test scripts/test/`.

Automatyzacja launchera (tylko build deweloperski): `MEDIRIAN_DEV_CAPTURE=<dir>` robi zrzuty
wszystkich ekranów, `MEDIRIAN_DEV_LAUNCH=<profil>` uruchamia grę przez pełny pipeline instalacji
(szczegóły w `launcher/src/main/devAutomation.ts`).

## Konfiguracja wydania

Cała konfiguracja właściciela jest w jednym pliku **`.env`** (wzór: [`.env.example`](.env.example), nie commitowany),
a na GitHubie w *Settings → Secrets and variables → Actions* pod tymi samymi nazwami. Do launchera trafia tylko pięć
wartości publicznych (`MEDIRIAN_MSA_CLIENT_ID`, `MEDIRIAN_DISCORD_APP_ID`, `MEDIRIAN_SERVICES_URL`, `MEDIRIAN_MANIFEST_URL`,
`MEDIRIAN_CONTACT`); sekrety podpisu służą tylko do budowania. Wszystkie adresy muszą być HTTPS.

```bash
cp .env.example .env
node scripts/check-env.mjs            # co jest ustawione / czego brakuje (bez wypisywania sekretów)
node scripts/check-env.mjs --release  # czy można zbudować publiczne wydanie
```

Krok po kroku — Microsoft (Azure + zgoda Mojang), Discord, domena i HTTPS, backend, Modrinth, podpis kodu Windows
(Azure Artifact Signing, certyfikat na tokenie albo plik .pfx), macOS, instalator, aktualizacje, publikacja i lista kontrolna:
**[docs/OWNER_SETUP.md](docs/OWNER_SETUP.md)**.

Zmienne deweloperskie: `MEDIRIAN_HOME` (inny folder danych niż `%APPDATA%\.medirian`), `MEDIRIAN_SESSION_SERVER`
(atrapa serwera sesji Mojang do testów usług).

**Publikacja**: tag `v<wersja>` uruchamia `.github/workflows/release.yml` — sprawdzenie konfiguracji, jary, manifest,
podpisane instalatory launchera i kanał jego auto-aktualizacji trafiają do GitHub Releases ([docs/RELEASING.md](docs/RELEASING.md)).
**Strona internetowa**: `.github/workflows/pages.yml` buduje i sprawdza `website/` (`node scripts/build-website.mjs`) i publikuje
ją na GitHub Pages; przycisk „Download for Windows” prowadzi do najnowszego `MedirianClientSetup.exe` z GitHub Releases
([docs/OWNER_SETUP.md §16](docs/OWNER_SETUP.md#16-strona-internetowa)).

## Struktura danych

Launcher i klient współdzielą `MEDIRIAN_HOME` — opis plików i protokołu: [docs/PROTOCOL.md](docs/PROTOCOL.md).
