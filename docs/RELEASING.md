# Wydawanie Medirian

Wszystko, co da się zautomatyzować, robią workflowy w `.github/workflows/`. Ten dokument opisuje,
co robią i co musi raz skonfigurować właściciel repozytorium.

## CI (`ci.yml`) — każdy push i pull request

| Job | Co sprawdza |
|---|---|
| Shared core tests | `client/shared`: `./gradlew test` (JUnit) |
| Client mc-1.8.9 / mc-1.21.8 / mc-1.21.11 / mc-26.3 | `./gradlew build` każdego targetu (jar jako artefakt) |
| Launcher (Windows, macOS, Linux) | `npm ci`, `npm run build` (typecheck + bundle), `npm test` |
| Medirian services | `backend`: `npm test` (logowanie z atrapą Mojang, kosmetyki, profile, limity) |
| Release scripts | `node --test scripts/test/` (kodek PNG, notatki wydania, analiza logu self-testu, zgodność katalogu kosmetyków) |
| Self-test and screenshots | `runClient -Pselftest` w Xvfb z programowym OpenGL (Mesa), `scripts/check-selftest.mjs`, `scripts/visual-test.mjs` |

Job wizualny jest na razie **nieblokujący** (`continue-on-error`): wzorce w `client/visual-baselines/`
powstały na Windowsie, a renderowania w Xvfb/llvmpipe nie dało się sprawdzić lokalnie. Po pierwszym
przebiegu na GitHubie: jeśli przeszedł — usunąć `continue-on-error`; jeśli różnice wynikają tylko ze
sterownika — pobrać artefakt `selftest-<target>`, obejrzeć zrzuty i zatwierdzić je jako wzorce.

### Testy wizualne lokalnie

```bash
cd client/targets/mc-1.8.9 && ./gradlew runClient -Pselftest
node scripts/visual-test.mjs --target mc-1.8.9
```

Self-test startuje zawsze ze świeżym `run/selftest-home` (domyślna konfiguracja, angielski), bez animacji,
powiadomień i motywu sezonowego, więc zrzuty menu są powtarzalne co do piksela (poza licznikiem FPS
w podglądzie edytora HUD). Porównywane są ekrany bez świata w tle: `1-modmenu`, `2-hudeditor`,
`3-settings`, `3b-cosmetics`, `3c-cosmetics-hats` i `0-title`. Self-test zapisuje też zbliżenia
każdej czapki na głowie bez hełmu (`4c-hat_*`) — do oceny osadzenia czapek na wszystkich wersjach. Zamierzona zmiana wyglądu: uruchomić self-test i `node scripts/visual-test.mjs --update`,
a nowe wzorce zatwierdzić razem ze zmianą. Różnice zapisują się w `run/visual-diff/` (na czerwono).

## Wydanie (`release.yml`) — tag `v<wersja>`

1. Podnieś wersję: `VERSION`, `launcher/package.json` (+ `package-lock.json`), sekcja w `CHANGELOG.md`
   (`## 0.1.5 — data — tytuł`; test w `scripts/test` pilnuje, że istnieje).
2. `git tag v0.1.5 && git push origin v0.1.5`.

Workflow:

- buduje jary klientów i `release-manifest.json` z adresami
  `https://github.com/<repo>/releases/download/v0.1.5/…` (`build-clients.mjs --base-url`),
- buduje launcher na Windows (`MedirianClientSetup.exe`, NSIS), macOS (dmg + zip) i Linux (AppImage) z wbudowanym domyślnym
  manifestem `https://github.com/<repo>/releases/latest/download/release-manifest.json`
  (`MAIN_VITE_MANIFEST_URL`), kluczem CurseForge z sekretu `CURSEFORGE_API_KEY` i z kanałem aktualizacji launchera
  na GitHub Releases (`app-update.yml`),
- publikuje wydanie z notatkami z `CHANGELOG.md` i wszystkimi plikami (jary, manifest, instalatory,
  `latest*.yml` + `.blockmap` dla `electron-updater`).

Zainstalowany launcher sam sprawdza nowe wydania (przy starcie i co 4 h), pobiera je w tle i instaluje
przy zamknięciu albo po kliknięciu „Restart to update”. Buildy deweloperskie i lokalne `npm run dist`
(bez `-c.publish.*`) nie mają kanału aktualizacji — interfejs aktualizacji launchera się w nich nie pokazuje.

### Instalator Windows (`MedirianClientSetup.exe`)

`launcher/electron-builder.yml`: NSIS z kreatorem (panel boczny i nagłówek w pixel arcie z `scripts/generate-pixel-art.mjs`,
języki EN/PL/DE/ES), instalacja **dla bieżącego użytkownika bez administratora** do `%LOCALAPPDATA%\Programs\Medirian Client`
(„dla wszystkich” jest opcją i wtedy prosi o uprawnienia), program `Medirian Client.exe`, skróty „Medirian Client” na pulpicie
i w menu Start, wpis w *Aplikacje i funkcje* (`Uninstall Medirian Client.exe`), `deleteAppDataOnUninstall: false`.
Nazwa pliku nie ma wersji, więc `https://github.com/<repo>/releases/latest/download/MedirianClientSetup.exe` zawsze wskazuje
najnowszy instalator (z tego korzysta strona pobierania `website/`). Dane graczy są w `MEDIRIAN_HOME` (`%APPDATA%\.medirian`),
poza folderem programu — instalacja, aktualizacja i deinstalacja ich nie ruszają.

„Aktualizuj” w launcherze instaluje pobraną wersję **po cichu** (`quitAndInstall(true, true)`: bez okien instalatora,
w ten sam folder, dla tego samego użytkownika) i uruchamia nową wersję; to samo dzieje się przy zamknięciu launchera.

Sprawdzone lokalnie na Windows 11 (0.3.0, buildy z kanałem `generic` na `127.0.0.1`):

- `MedirianClientSetup.exe /S` → `%LOCALAPPDATA%\Programs\Medirian Client\Medirian Client.exe`, oba skróty, wpis w rejestrze
  HKCU (DisplayName „Medirian Client”, wersja, ikona, ciche odinstalowanie),
- zainstalowana aplikacja startuje (kreator, zakładka Mods z wynikami z Modrinth),
- auto-aktualizacja 0.3.0 → 0.3.2: wykrycie, pobranie (z próbą różnicową po `.blockmap`), cicha instalacja, ponowne
  uruchomienie, skróty i profile zachowane,
- deinstalacja usuwa program, skróty i wpis, a `MEDIRIAN_HOME` zostaje.

**Smart App Control** (Windows 11) blokuje niepodpisane pliki wykonywalne bez reputacji: w testach blokował losowo
instalator, zainstalowany `Medirian Client.exe` i kopię deinstalatora uruchamianą z `%TEMP%` („NSIS Error: Error launching
installer”). Rozwiązaniem jest podpis Authenticode (sekrety niżej) — bez niego publiczne wydanie nie zadziała na komputerach
z włączonym Smart App Control.

## Strona pobierania (`pages.yml`)

`website/` (HTML/CSS/JS, grafiki z `scripts/generate-pixel-art.mjs`) trafia na GitHub Pages przy zmianie strony, przy
każdym opublikowanym wydaniu i ręcznie. Workflow wpisuje nazwę repozytorium w `data-repo` w `index.html`; strona pobiera
z API GitHuba wersję i rozmiar najnowszego instalatora oraz pliki dla macOS i Linuxa (bez API działa stały link do
`MedirianClientSetup.exe`). Teksty EN/PL wg języka przeglądarki.

## Do zrobienia przez właściciela (jednorazowo)

| Co | Gdzie | Skutek bez tego |
|---|---|---|
| Zmienna `MSA_CLIENT_ID` | Settings → Secrets and variables → Actions → Variables | Logowanie Microsoft wyłączone w wydanych buildach (wymaga rejestracji aplikacji Azure + zgody Mojang) |
| Zmienna `DISCORD_APP_ID` | jw. | Discord Rich Presence wymaga ręcznego wpisania ID w ustawieniach |
| Zmienna `SERVICES_URL` (+ wdrożony `backend/`, docs/SERVICES.md) | jw. | Peleryny widzi tylko sam gracz, brak profili w chmurze |
| Sekret `CURSEFORGE_API_KEY` (klucz z [console.curseforge.com](https://console.curseforge.com/) → API keys) | Secrets | Zakładka Mods działa tylko z Modrinth, dopóki użytkownik nie wpisze własnego klucza w *Ustawienia → Mody* |
| GitHub Pages: *Settings → Pages → Source: GitHub Actions* | ustawienia repozytorium | Strona pobierania `website/` nie jest publikowana |
| Sekrety `CSC_LINK`, `CSC_KEY_PASSWORD` | Secrets | Instalatory niepodpisane (ostrzeżenie SmartScreen, blokady Smart App Control w Windows 11; na macOS auto-update **wymaga** podpisu) |
| Sekrety `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` | Secrets | Brak notaryzacji macOS (Gatekeeper blokuje pierwsze uruchomienie) |

`CSC_LINK` to certyfikat `.p12`/`.pfx` zakodowany base64 (Windows: Authenticode, macOS: Developer ID
Application). Certyfikaty kupuje i przechowuje właściciel — nie trafiają do repozytorium.
