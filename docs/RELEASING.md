# Wydawanie Medirian

Wszystko, co da się zautomatyzować, robią workflowy w `.github/workflows/`. Ten dokument opisuje,
co robią. Konta, klucze, certyfikaty i serwer, które musi raz skonfigurować właściciel, opisuje krok po kroku
**[OWNER_SETUP.md](OWNER_SETUP.md)**.

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

1. Podnieś wersję: `VERSION`, `launcher/package.json` (+ `package-lock.json`), `backend/package.json`, sekcja w `CHANGELOG.md`
   (`## 0.4.1 — data — tytuł`; test w `scripts/test` pilnuje, że istnieje).
2. `git tag v0.4.1 && git push origin v0.4.1`.

Workflow:

- najpierw sprawdza konfigurację właściciela (`node scripts/check-env.mjs --release` na zmiennych i sekretach
  repozytorium): logowanie Microsoft, usługi Medirian (HTTPS), kontakt dla Modrinth i podpis Windows muszą być ustawione,
  inaczej wydanie się nie zbuduje (`MEDIRIAN_ALLOW_UNSIGNED=1` pozwala na niepodpisane wydanie testowe),

- buduje jary klientów i `release-manifest.json` z adresami
  `https://github.com/<repo>/releases/download/v0.1.5/…` (`build-clients.mjs --base-url`),
- buduje launcher na Windows (`MedirianClientSetup.exe`, NSIS), macOS (dmg + zip) i Linux (AppImage) z wbudowanym domyślnym
  manifestem `https://github.com/<repo>/releases/latest/download/release-manifest.json`
  (`MEDIRIAN_MANIFEST_URL`), publiczną konfiguracją z Variables (`MEDIRIAN_MSA_CLIENT_ID`, `MEDIRIAN_DISCORD_APP_ID`,
  `MEDIRIAN_SERVICES_URL`, `MEDIRIAN_CONTACT`), podpisem Windows (`WIN_CSC_*` albo Azure Artifact Signing) i macOS
  (`CSC_*`, `APPLE_*`) oraz z kanałem aktualizacji launchera na GitHub Releases (`app-update.yml`),
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

Wszystkie wartości, skąd je wziąć, gdzie wkleić i jak sprawdzić: **[OWNER_SETUP.md](OWNER_SETUP.md)**
(tabela w sekcji 1, lista kontrolna na końcu). W skrócie:

| Co | Gdzie (GitHub) | Skutek bez tego |
|---|---|---|
| `MEDIRIAN_MSA_CLIENT_ID` (Azure + zgoda Mojang) | Variables | wydanie zablokowane — bez logowania Microsoft launcher jest bezużyteczny |
| `MEDIRIAN_SERVICES_URL` (+ wdrożony `backend/` pod HTTPS) | Variables | wydanie zablokowane |
| `MEDIRIAN_CONTACT` | Variables | wydanie zablokowane (wymóg API Modrinth) |
| `MEDIRIAN_DISCORD_APP_ID` | Variables | brak Discord Rich Presence |
| `WIN_CSC_LINK` + `WIN_CSC_KEY_PASSWORD` **albo** `AZURE_SIGNING_*` (Variables) + `AZURE_TENANT_ID` / `AZURE_CLIENT_ID` / `AZURE_CLIENT_SECRET` | Secrets | wydanie zablokowane (Smart App Control blokuje niepodpisane pliki) |
| `CSC_LINK`, `CSC_KEY_PASSWORD`, `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` | Secrets | build macOS niepodpisany, bez notaryzacji i auto-aktualizacji |
| *Settings → Pages → Source: GitHub Actions* | ustawienia repozytorium | strona pobierania `website/` nie jest publikowana |
| *Settings → Actions → General → Workflow permissions: Read and write* | ustawienia repozytorium | workflow nie utworzy wydania |

Certyfikaty kupuje i przechowuje właściciel — nie trafiają do repozytorium (`*.pfx`, `*.p12`, `*.pem`, `*.key` są w `.gitignore`).
