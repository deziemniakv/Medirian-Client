# Wydawanie Meridian

Wszystko, co da się zautomatyzować, robią workflowy w `.github/workflows/`. Ten dokument opisuje,
co robią i co musi raz skonfigurować właściciel repozytorium.

## CI (`ci.yml`) — każdy push i pull request

| Job | Co sprawdza |
|---|---|
| Shared core tests | `client/shared`: `./gradlew test` (JUnit) |
| Client mc-1.8.9 / mc-1.21.8 / mc-1.21.11 / mc-26.3 | `./gradlew build` każdego targetu (jar jako artefakt) |
| Launcher (Windows, macOS, Linux) | `npm ci`, `npm run build` (typecheck + bundle), `npm test` |
| Meridian services | `backend`: `npm test` (logowanie z atrapą Mojang, kosmetyki, profile, limity) |
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
`3-settings`, `3b-cosmetics`. Zamierzona zmiana wyglądu: uruchomić self-test i `node scripts/visual-test.mjs --update`,
a nowe wzorce zatwierdzić razem ze zmianą. Różnice zapisują się w `run/visual-diff/` (na czerwono).

## Wydanie (`release.yml`) — tag `v<wersja>`

1. Podnieś wersję: `VERSION`, `launcher/package.json` (+ `package-lock.json`), sekcja w `CHANGELOG.md`
   (`## 0.1.5 — data — tytuł`; test w `scripts/test` pilnuje, że istnieje).
2. `git tag v0.1.5 && git push origin v0.1.5`.

Workflow:

- buduje jary klientów i `release-manifest.json` z adresami
  `https://github.com/<repo>/releases/download/v0.1.5/…` (`build-clients.mjs --base-url`),
- buduje launcher na Windows (NSIS), macOS (dmg + zip) i Linux (AppImage) z wbudowanym domyślnym
  manifestem `https://github.com/<repo>/releases/latest/download/release-manifest.json`
  (`MAIN_VITE_MANIFEST_URL`) i z kanałem aktualizacji launchera na GitHub Releases (`app-update.yml`),
- publikuje wydanie z notatkami z `CHANGELOG.md` i wszystkimi plikami (jary, manifest, instalatory,
  `latest*.yml` + `.blockmap` dla `electron-updater`).

Zainstalowany launcher sam sprawdza nowe wydania (przy starcie i co 4 h), pobiera je w tle i instaluje
przy zamknięciu albo po kliknięciu „Restart to update”. Buildy deweloperskie i lokalne `npm run dist`
(bez `-c.publish.*`) nie mają kanału aktualizacji — interfejs aktualizacji launchera się w nich nie pokazuje.

Sprawdzone lokalnie (Windows): launcher 0.1.4 zbudowany z kanałem `generic` na `127.0.0.1` wykrył
0.1.5, pobrał instalator i pokazał przycisk restartu.

## Do zrobienia przez właściciela (jednorazowo)

| Co | Gdzie | Skutek bez tego |
|---|---|---|
| Zmienna `MSA_CLIENT_ID` | Settings → Secrets and variables → Actions → Variables | Logowanie Microsoft wyłączone w wydanych buildach (wymaga rejestracji aplikacji Azure + zgody Mojang) |
| Zmienna `DISCORD_APP_ID` | jw. | Discord Rich Presence wymaga ręcznego wpisania ID w ustawieniach |
| Zmienna `SERVICES_URL` (+ wdrożony `backend/`, docs/SERVICES.md) | jw. | Peleryny widzi tylko sam gracz, brak profili w chmurze |
| Sekrety `CSC_LINK`, `CSC_KEY_PASSWORD` | Secrets | Instalatory niepodpisane (ostrzeżenie SmartScreen; na macOS auto-update **wymaga** podpisu) |
| Sekrety `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` | Secrets | Brak notaryzacji macOS (Gatekeeper blokuje pierwsze uruchomienie) |

`CSC_LINK` to certyfikat `.p12`/`.pfx` zakodowany base64 (Windows: Authenticode, macOS: Developer ID
Application). Certyfikaty kupuje i przechowuje właściciel — nie trafiają do repozytorium.
