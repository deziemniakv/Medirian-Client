# Medirian Client — konfiguracja właściciela

Ten dokument prowadzi właściciela projektu przez wszystko, czego nie da się zrobić w kodzie: konta,
klucze, domenę, serwer, certyfikaty i pierwsze publiczne wydanie. Każda sekcja mówi **gdzie** wejść,
**co** utworzyć, **co** skopiować, **gdzie** to wkleić (nazwa zmiennej) i **jak sprawdzić**, że działa.

Kolejność ma znaczenie: GitHub → `.env` → Microsoft → Discord → domena i HTTPS → backend → Modrinth →
podpis kodu → wydanie. Na końcu jest [lista kontrolna](#lista-kontrolna).

---

## Spis treści

1. [Jak działa konfiguracja (`.env`, publiczne i tajne wartości)](#1-jak-działa-konfiguracja)
2. [Repozytorium GitHub i GitHub Actions](#2-repozytorium-github-i-github-actions)
3. [Microsoft — logowanie kontem Minecraft (Azure App Registration)](#3-microsoft--logowanie-kontem-minecraft)
4. [Discord — Rich Presence](#4-discord--rich-presence)
5. [Domena i HTTPS](#5-domena-i-https)
6. [Backend — Medirian Services](#6-backend--medirian-services)
7. [Modrinth](#7-modrinth)
8. [Konfiguracja produkcyjna launchera](#8-konfiguracja-produkcyjna-launchera)
9. [Manifest wydań i system aktualizacji](#9-manifest-wydań-i-system-aktualizacji)
10. [Podpis kodu Windows (Authenticode)](#10-podpis-kodu-windows-authenticode)
11. [Podpis i notaryzacja macOS (opcjonalnie)](#11-podpis-i-notaryzacja-macos-opcjonalnie)
12. [Instalator Windows](#12-instalator-windows)
13. [Budowanie wydania lokalnie](#13-budowanie-wydania-lokalnie)
14. [Publikacja nowej wersji](#14-publikacja-nowej-wersji)
15. [Przed pierwszym publicznym wydaniem](#15-przed-pierwszym-publicznym-wydaniem)
16. [Strona internetowa](#16-strona-internetowa)
17. [Lista kontrolna](#lista-kontrolna)

---

## 1. Jak działa konfiguracja

Cała konfiguracja właściciela jest w **jednym pliku `.env`** w katalogu głównym repozytorium.
Wzór z opisem każdej wartości: [`.env.example`](../.env.example).

```bash
cp .env.example .env        # Windows PowerShell: Copy-Item .env.example .env
node scripts/check-env.mjs  # co jest ustawione, czego brakuje, co jest błędne (nie wypisuje sekretów)
```

* `.env` jest w `.gitignore` (razem z `.env.*`, `*.pfx`, `*.p12`, `*.pem`, `*.key` i innymi plikami
  certyfikatów) — **nigdy go nie commituj**. `git status` nie powinien go pokazywać.
* Na GitHubie te same nazwy ustawia się w *Settings → Secrets and variables → Actions*:
  wartości **publiczne** w zakładce *Variables*, **sekrety** w zakładce *Secrets*.
* Nic z `.env` nie jest wyświetlane w interfejsie launchera. *Ustawienia → O programie → Ten build*
  pokazuje tylko, **czy** dana funkcja jest skonfigurowana (Dostępne / Nieskonfigurowane) i host usług —
  nigdy kluczy ani haseł.

### Trzy rodzaje wartości

| Rodzaj | Gdzie trafia | Kto może je zobaczyć | Przykłady |
|---|---|---|---|
| **Publiczne** | wbudowane w launcher (plik aplikacji) | każdy, kto zainstaluje launcher | `MEDIRIAN_MSA_CLIENT_ID`, `MEDIRIAN_DISCORD_APP_ID`, `MEDIRIAN_SERVICES_URL`, `MEDIRIAN_MANIFEST_URL`, `MEDIRIAN_CONTACT` |
| **Sekrety** | tylko maszyna budująca wydanie (podpisywanie) | nikt poza Tobą i GitHub Actions | `WIN_CSC_LINK`, `WIN_CSC_KEY_PASSWORD`, `AZURE_CLIENT_SECRET`, `CSC_LINK`, `APPLE_APP_SPECIFIC_PASSWORD` |
| **Serwerowe** | tylko serwer z backendem | nikt poza serwerem | `MEDIRIAN_SERVICES_DOMAIN`, `MEDIRIAN_SERVICES_ACME_EMAIL`, `MEDIRIAN_SERVICES_TRUST_PROXY` |

**Dlaczego wartości publiczne mogą być publiczne.** Launcher to aplikacja na komputerze gracza —
wszystko, co jest w nim wbudowane, da się odczytać. Dlatego wbudowane są wyłącznie identyfikatory
i adresy, które z natury są jawne (tak jak adres strony czy identyfikator aplikacji OAuth w każdej
aplikacji desktopowej):

* identyfikator aplikacji Azure (`MEDIRIAN_MSA_CLIENT_ID`) — logowanie Microsoft w aplikacjach
  desktopowych to „klient publiczny” (przepływ *device code*) i **nie używa żadnego sekretu**;
  identyfikator sam w sobie nie daje dostępu do niczego,
* identyfikator aplikacji Discord (`MEDIRIAN_DISCORD_APP_ID`) — Rich Presence używa tylko ID,
* adresy (`MEDIRIAN_SERVICES_URL`, `MEDIRIAN_MANIFEST_URL`) i kontakt dla Modrinth (`MEDIRIAN_CONTACT`).

Do launchera trafia **tylko tych pięć nazw** — lista jest w kodzie (`launcher/src/main/core/config.ts`,
`PUBLIC_CONFIG_KEYS`), a `launcher/electron.vite.config.ts` wbudowuje wyłącznie je. Nawet jeśli w `.env`
leżą sekrety podpisu, nie ma drogi, by trafiły do aplikacji. Nigdy nie twórz „client secret” dla aplikacji
Azure ani „bot token” dla Discorda — launcher ich nie potrzebuje, a wbudowany sekret przestaje być sekretem.

### Reguła HTTPS

Każdy adres w wydaniu musi używać `https://`. Build launchera **przerywa się błędem**, gdy adres
publiczny jest `http://` (wyjątek: `localhost` / `127.0.0.1` do testów lokalnych) albo nadal jest
wzorem z `.env.example`. Klient w grze też odrzuca adres usług bez HTTPS (`ServicesConfig`), a backend
za proxy odrzuca żądania, które nie przyszły przez HTTPS.

### Wszystkie wartości w jednym miejscu

| Zmienna | Rodzaj | Skąd ją wziąć | GitHub | Bez niej |
|---|---|---|---|---|
| `MEDIRIAN_MSA_CLIENT_ID` | publiczna | [§3](#3-microsoft--logowanie-kontem-minecraft) | Variable | brak logowania Microsoft → wydanie zablokowane |
| `MEDIRIAN_DISCORD_APP_ID` | publiczna | [§4](#4-discord--rich-presence) | Variable | brak Discord Rich Presence |
| `MEDIRIAN_SERVICES_URL` | publiczna | [§6](#6-backend--medirian-services) | Variable | brak kont Medirian, kosmetyków dla innych graczy, profili w chmurze i kodów profili → wydanie zablokowane |
| `MEDIRIAN_MANIFEST_URL` | publiczna | [§9](#9-manifest-wydań-i-system-aktualizacji) | ustawia ją workflow | (lokalnie) brak kanału stabilnego |
| `MEDIRIAN_CONTACT` | publiczna | [§7](#7-modrinth) | Variable | wydanie zablokowane (wymóg regulaminu API Modrinth) |
| `WIN_CSC_LINK`, `WIN_CSC_KEY_PASSWORD` | sekrety | [§10](#10-podpis-kodu-windows-authenticode), wariant A | Secrets | niepodpisany instalator (alternatywy: warianty B i C) |
| `AZURE_SIGNING_ENDPOINT`, `AZURE_SIGNING_ACCOUNT`, `AZURE_SIGNING_PROFILE`, `AZURE_SIGNING_PUBLISHER` | konfiguracja buildu | [§10](#10-podpis-kodu-windows-authenticode), wariant B | Variables | — |
| `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET` | sekrety | [§10](#10-podpis-kodu-windows-authenticode), wariant B | Secrets | — |
| `MEDIRIAN_ALLOW_UNSIGNED` | konfiguracja buildu | `1` dla wydania testowego albo wariantu C (§10) | Variable | wydanie bez podpisu w CI jest zablokowane |
| `MEDIRIAN_RELEASE_DRAFT` | konfiguracja buildu | `1` — wydanie powstaje jako szkic (§10 wariant C, §14) | Variable | wydanie od razu publiczne |
| `CSC_LINK`, `CSC_KEY_PASSWORD`, `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` | sekrety | [§11](#11-podpis-i-notaryzacja-macos-opcjonalnie) | Secrets | build macOS niepodpisany |
| `MEDIRIAN_SERVICES_DOMAIN`, `MEDIRIAN_SERVICES_ACME_EMAIL` | serwerowe | [§5](#5-domena-i-https) | — (tylko serwer) | brak HTTPS dla backendu |
| `MEDIRIAN_SERVICES_PORT`, `…_HOST`, `…_DATA_DIR`, `…_TRUST_PROXY` | serwerowe | [§6](#6-backend--medirian-services) | — | wartości domyślne |
| `MEDIRIAN_DISCORD_URL` | strona | zaproszenie na serwer Discord ([§16](#16-strona-internetowa)) | Variable | brak linku Discord w stopce strony |
| `MEDIRIAN_PRIVACY_URL`, `MEDIRIAN_TERMS_URL` | strona | polityka prywatności i regulamin ([§16](#16-strona-internetowa)) | Variables | brak linków Privacy / Terms w stopce strony |
| `MEDIRIAN_SITE_URL` | strona | własna domena strony ([§16](#16-strona-internetowa)) | Variable (opcjonalnie) | adres GitHub Pages |
| `MEDIRIAN_HOME`, `MEDIRIAN_SESSION_SERVER` | tylko deweloperskie | — | — | — (zostaw puste w wydaniach) |

Wycofane nazwy (nieużywane od 0.4.0): `MEDIRIAN_API_URL`, `MAIN_VITE_*`, `MEDIRIAN_CURSEFORGE_API_KEY`,
`CURSEFORGE_API_KEY`, sekrety `MSA_CLIENT_ID` / `DISCORD_APP_ID` / `SERVICES_URL`. Pola *Ustawienia →
Deweloperskie* w launcherze zostały usunięte — wydanie ma konfigurację wbudowaną.

---

## 2. Repozytorium GitHub i GitHub Actions

**Stan na dziś:** repozytorium lokalne nie ma zdalnego `origin`. Wypchnięcie kodu i konfiguracja GitHuba
wymagają Twojego konta — poniższe kroki wykonujesz Ty.

### 2.1. Utwórz repozytorium

1. Wejdź na <https://github.com/new>.
2. *Repository name*: np. `medirian-client`. *Owner*: Twoje konto lub organizacja.
3. Widoczność: **Public** (GitHub Pages i pobieranie instalatora z Releases bez logowania wymagają
   publicznego repozytorium na planie Free; prywatne repozytorium wymaga płatnego planu dla Pages,
   a pobieranie aktualizacji z prywatnych Releases przez launcher **nie zadziała**).
4. **Nie** zaznaczaj *Add a README*, *.gitignore* ani *license* — repozytorium musi być puste.
5. *Create repository*.

### 2.2. Wypchnij kod

```bash
cd "Medirian Client"
git status                     # .env NIE może być na liście
git branch -M main             # domyślna gałąź: main
git remote add origin https://github.com/<owner>/<repo>.git
git push -u origin main
```

### 2.3. Ustawienia repozytorium

| Gdzie | Co ustawić | Po co |
|---|---|---|
| *Settings → Actions → General → Workflow permissions* | **Read and write permissions** | workflow `release.yml` tworzy wydanie (`contents: write`) |
| *Settings → Pages → Build and deployment → Source* | **GitHub Actions** | strona pobierania `website/` (`pages.yml`) |
| *Settings → Secrets and variables → Actions → Variables* | wartości publiczne z §3–§10 | build wydania |
| *Settings → Secrets and variables → Actions → Secrets* | sekrety z §10–§11 | podpis wydania |
| *Settings → Branches → Add rule* (`main`) | *Require status checks to pass* → zaznacz joby CI | (zalecane) nic nie trafia do `main` bez zielonego CI |

### 2.4. Co robią workflowy

| Plik | Kiedy | Co robi |
|---|---|---|
| `.github/workflows/ci.yml` | każdy push na `main`/`master` i pull request | testy rdzenia (JUnit), build 4 wersji klienta, launcher na Windows/macOS/Linux (typecheck, build, testy), testy backendu, testy skryptów, self-test w grze ze zrzutami (nieblokujący) |
| `.github/workflows/release.yml` | push taga `v<wersja>` | sprawdza konfigurację właściciela (`check-env --release`), buduje jary + `release-manifest.json`, launcher na 3 systemy (podpisany), publikuje GitHub Release z notatkami z `CHANGELOG.md` |
| `.github/workflows/pages.yml` | zmiana w `website/`, opublikowane wydanie, ręcznie | buduje i sprawdza stronę (`scripts/build-website.mjs`) i publikuje ją na GitHub Pages ([§16](#16-strona-internetowa)) |

**Jak sprawdzić:** zakładka *Actions* → workflow *CI* po pierwszym pushu powinien być zielony
(job *Self-test and screenshots* może być żółty — jest nieblokujący, patrz `docs/RELEASING.md`).

---

## 3. Microsoft — logowanie kontem Minecraft

Launcher loguje graczy kontem Microsoft przepływem *device code* (gracz wpisuje kod na microsoft.com),
a potem przez Xbox Live do Minecraft Services. Potrzebujesz własnej rejestracji aplikacji w Azure
**i zgody Mojang** na dostęp do API Minecrafta — to wymóg Mojang dla każdego launchera.

### 3.1. Rejestracja aplikacji (Azure)

1. Wejdź na <https://portal.azure.com> (wystarczy darmowe konto Microsoft; subskrypcja płatna nie jest potrzebna
   do samej rejestracji aplikacji) → **Microsoft Entra ID** → **App registrations** → **New registration**.
2. *Name*: `Medirian Client` (gracze zobaczą tę nazwę przy logowaniu).
3. *Supported account types*: **Personal Microsoft accounts only**
   (launcher loguje przez `login.microsoftonline.com/consumers` — konta Minecrafta to konta osobiste).
4. *Redirect URI*: **zostaw puste**. Przepływ *device code* nie przekierowuje przeglądarki, więc nie
   potrzebuje redirect URI. (Jeśli formularz go wymaga albo chcesz go mieć, wybierz platformę
   *Public client/native (mobile & desktop)* i adres `https://login.microsoftonline.com/common/oauth2/nativeclient`
   — nie zaszkodzi, launcher go nie używa.)
5. *Register*.
6. W nowej aplikacji: **Authentication** → *Advanced settings* → **Allow public client flows: Yes** → *Save*.
   Bez tego przepływ *device code* kończy się błędem `AADSTS7000218`.
7. **Nie** twórz niczego w *Certificates & secrets*. Launcher jest klientem publicznym — sekret byłby
   wbudowany w aplikację, czyli jawny.
8. *API permissions*: nic nie dodawaj. Launcher prosi o `XboxLive.signin offline_access` w chwili
   logowania, a gracz zgadza się sam.
9. **Overview** → skopiuj **Application (client) ID** (GUID, np. `0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0`).

**Gdzie wkleić:** `.env` → `MEDIRIAN_MSA_CLIENT_ID=<GUID>`; GitHub → *Variables* → `MEDIRIAN_MSA_CLIENT_ID`.

### 3.2. Zgoda Mojang na Minecraft Services API

Nowe identyfikatory aplikacji są przez Mojang blokowane, dopóki ich nie zatwierdzi.

1. Wypełnij formularz **Minecraft Java Edition — App ID review**: <https://aka.ms/mce-reviewappid>
   (podajesz *Application (client) ID* z §3.1, nazwę i opis launchera, link do strony/repozytorium).
2. Czekaj na odpowiedź e-mailem (zwykle kilka dni do kilku tygodni).

Do czasu zatwierdzenia logowanie kończy się komunikatem
„Minecraft login was rejected for this launcher application id (Mojang approval required).” — to nie jest
błąd launchera, tylko brak zgody Mojang.

### 3.3. Jak sprawdzić

1. `node scripts/check-env.mjs` → `OK  MEDIRIAN_MSA_CLIENT_ID`.
2. Zbuduj launcher (`cd launcher && npm run dev` albo `npm run dist`) → *Ustawienia → O programie → Ten build →
   Logowanie Microsoft: **Dostępne***.
3. *Konto → Zaloguj przez Microsoft* → kod → zaloguj się na microsoft.com kontem z kupionym Minecraftem →
   w launcherze pojawia się nick i skin.

---

## 4. Discord — Rich Presence

1. Wejdź na <https://discord.com/developers/applications> → **New Application**.
2. *Name*: `Medirian Client` — **ta nazwa wyświetla się na profilu gracza** („Gra w Medirian Client”).
3. **General Information** → skopiuj **Application ID** (same cyfry, 17–20 znaków).
4. **Rich Presence → Art Assets** → *Add Image(s)* → wgraj `branding/medirian-icon-512.png` i nazwij go
   dokładnie **`medirian`** (launcher używa klucza `medirian` jako dużego obrazka). Nowe obrazki pojawiają
   się po kilku–kilkunastu minutach.
5. Nie twórz bota, nie kopiuj *Client Secret*, nie dodawaj *OAuth2 Redirects* — Rich Presence przez lokalnego
   Discorda potrzebuje tylko Application ID.

**Gdzie wkleić:** `.env` → `MEDIRIAN_DISCORD_APP_ID=<Application ID>`; GitHub → *Variables* →
`MEDIRIAN_DISCORD_APP_ID`.

**Jak sprawdzić:** uruchom aplikację Discord na komputerze → launcher *Ustawienia → Discord* (włączone) →
uruchom grę → w Discordzie na Twoim profilu: „Medirian Client”, wersja gry, profil, menu/singleplayer/serwer
i czas gry. *Ustawienia → O programie → Ten build → Discord Rich Presence: Dostępne*.

---

## 5. Domena i HTTPS

Backend (Medirian Services) musi być dostępny pod własną domeną po HTTPS, np. `https://api.medirian.net`.

1. **Domena**: kup domenę u dowolnego rejestratora (np. OVH, Cloudflare Registrar, Namecheap) albo użyj
   subdomeny posiadanej domeny.
2. **Serwer**: VPS z Linuksem (np. Hetzner, OVH, DigitalOcean; 1 vCPU / 1 GB RAM wystarczy na start)
   z publicznym adresem IPv4 (opcjonalnie IPv6).
3. **DNS**: u rejestratora/w strefie DNS dodaj rekord **A** `api` → adres IPv4 serwera (i **AAAA** → IPv6,
   jeśli jest). Przy Cloudflare ustaw rekord jako *DNS only* (szara chmurka), żeby Caddy mógł sam
   uzyskać certyfikat.
4. **Firewall serwera**: otwarte porty **80** i **443** (TCP; 443 także UDP dla HTTP/3) i SSH. Port 8080
   backendu **nie** może być otwarty na świat — w konfiguracji Docker nie jest publikowany.
5. **Certyfikat**: niczego nie kupujesz. Caddy (`backend/deploy/Caddyfile`) sam pobiera i odnawia darmowy
   certyfikat Let's Encrypt dla domeny i przekierowuje HTTP na HTTPS.

**Gdzie wkleić (na serwerze, w `.env` obok repozytorium):**

```dotenv
MEDIRIAN_SERVICES_DOMAIN=api.medirian.net        # sama domena, bez https://
MEDIRIAN_SERVICES_ACME_EMAIL=ty@medirian.net     # tu Let's Encrypt wyśle ostrzeżenia o certyfikacie
```

**Jak sprawdzić:** `nslookup api.medirian.net` zwraca IP serwera; po uruchomieniu backendu (§6)
<https://api.medirian.net/v1/status> otwiera się w przeglądarce z kłódką.

---

## 6. Backend — Medirian Services

`backend/` to usługa Node bez zależności: konta Medirian (logowanie kontem Minecraft przez handshake
sesji Mojang — bez haseł), kosmetyki widoczne dla innych graczy, profile w chmurze i kody profili
(`MDN-XXXX-XXXX-XXXX`, ważne 90 dni). Opis API: [docs/SERVICES.md](SERVICES.md).

### 6.1. Wdrożenie z Dockerem (zalecane)

Na serwerze (Ubuntu/Debian):

```bash
# Docker z wtyczką compose: https://docs.docker.com/engine/install/
git clone https://github.com/<owner>/<repo>.git medirian
cd medirian
cp .env.example .env
nano .env        # zostaw TYLKO sekcję "3. SERVER" (domena i e-mail z §5); usuń sekrety podpisu
cd backend/deploy
docker compose up -d --build
docker compose logs -f        # "Medirian Services listening on http://0.0.0.0:8080 (... behind an HTTPS proxy)"
```

`docker-compose.yml` uruchamia usługę za Caddy z `MEDIRIAN_SERVICES_TRUST_PROXY=1`: adresy graczy (limity)
pochodzą z nagłówka Caddy, żądania bez HTTPS są odrzucane, odpowiedzi mają HSTS. Dane graczy są w wolumenie
`medirian-data` — **rób jego kopie zapasowe**:

```bash
docker run --rm -v deploy_medirian-data:/data -v "$PWD":/backup alpine tar czf /backup/medirian-data.tgz -C /data .
```

(Nazwę wolumenu pokaże `docker volume ls`; prefiks to nazwa katalogu, tu `deploy`.)

**Aktualizacja backendu:** `git pull && cd backend/deploy && docker compose up -d --build`.

### 6.2. Bez Dockera

Node **22.9+**, własny reverse proxy z HTTPS (Caddy/nginx) przed usługą:

```bash
cd backend
# w ../.env: MEDIRIAN_SERVICES_HOST=127.0.0.1, MEDIRIAN_SERVICES_PORT=8080,
#            MEDIRIAN_SERVICES_DATA_DIR=/var/lib/medirian, MEDIRIAN_SERVICES_TRUST_PROXY=1
npm start            # czyta ../.env (node --env-file-if-exists)
```

Proxy musi ustawiać `X-Forwarded-For` i `X-Forwarded-Proto: https` (Caddy robi to sam). Usługę uruchamiaj
jako zwykły użytkownik (np. jednostka systemd z `User=medirian`).

### 6.3. Połącz launcher z backendem

**Gdzie wkleić:** `.env` (komputer budujący) → `MEDIRIAN_SERVICES_URL=https://api.medirian.net`
(bez ukośnika na końcu); GitHub → *Variables* → `MEDIRIAN_SERVICES_URL`. Launcher przekazuje adres grze
(`-Dmedirian.services=…`), więc klient nie potrzebuje osobnej konfiguracji.

### 6.4. Jak sprawdzić

```bash
curl https://api.medirian.net/v1/status
# {"name":"medirian-services","version":"0.4.0"}
curl -I http://api.medirian.net/v1/status     # 308 → https://
```

W launcherze: *Ustawienia → O programie → Ten build → Usługi Medirian: Dostępne (api.medirian.net)*;
*Profile → Udostępnij* tworzy kod `MDN-…`; w grze *Ustawienia → Konto* pokazuje zalogowane konto Medirian,
a peleryna jest widoczna dla drugiego gracza z Medirian.

---

## 7. Modrinth

Zakładka *Mody* korzysta wyłącznie z publicznego API Modrinth (v2). **Klucz API nie jest potrzebny**
do wyszukiwania i pobierania modów. Regulamin API Modrinth wymaga natomiast, by aplikacja przedstawiała
się nagłówkiem `User-Agent` z danymi kontaktowymi — launcher wysyła
`medirian-client/launcher/<wersja> (<MEDIRIAN_CONTACT>)`.

1. Wybierz kontakt: adres e-mail projektu albo URL (np. strona repozytorium) — taki, pod którym Modrinth
   może Cię zawiadomić, gdy launcher zachowuje się źle.
2. **Gdzie wkleić:** `.env` → `MEDIRIAN_CONTACT=projekt@medirian.net`; GitHub → *Variables* → `MEDIRIAN_CONTACT`.
3. Limit Modrinth: 300 żądań na minutę z jednego IP — launcher mieści się w nim z zapasem (wyszukiwanie
   z opóźnieniem, cache ikon).

**Jak sprawdzić:** launcher → *Mody* → wyszukaj „sodium” → wyniki z ikonami; *Zainstaluj* w profilu 1.21.11
pobiera mod i zależności. CurseForge został usunięty w 0.4.0 — nie ma i nie będzie klucza CurseForge.

---

## 8. Konfiguracja produkcyjna launchera

Nie ma osobnego pliku konfiguracyjnego wydania — konfiguracją jest `.env` (lokalnie) albo Variables
(GitHub). Przy `npm run build` / `npm run dist`:

1. `launcher/electron.vite.config.ts` czyta `.env` z katalogu głównego repozytorium i zmienne środowiska
   (CI),
2. sprawdza adresy (HTTPS, brak wzorów z `.env.example`) — błąd przerywa build,
3. wbudowuje **tylko** pięć wartości publicznych (stała `__MEDIRIAN_CONFIG__`),
4. spakowana aplikacja zawsze używa kanału **stable**; kanał lokalny (`distribution/`) istnieje tylko
   w buildzie deweloperskim (`npm run dev`).

Build deweloperski (`npm run dev`) dodatkowo czyta zmienne środowiska przy starcie, a konto offline jest
dostępne tylko w nim.

**Jak sprawdzić gotowy build:** *Ustawienia → O programie → Ten build* — każda pozycja „Dostępne”,
host usług i kanału wydań się zgadza, brak sekcji z problemami. Log launchera
(`%APPDATA%\.medirian\launcher\logs\`) przy starcie wypisuje ewentualne problemy konfiguracji.

---

## 9. Manifest wydań i system aktualizacji

Są dwa niezależne mechanizmy aktualizacji, oba oparte o **GitHub Releases** Twojego repozytorium:

| Co | Plik | Kto go czyta | Weryfikacja |
|---|---|---|---|
| **Klient (mody Medirian dla 4 wersji gry)** | `release-manifest.json` (+ jary) | launcher przy każdym uruchomieniu gry | SHA-1 każdego jara z manifestu |
| **Sam launcher** | `latest.yml` / `latest-mac.yml` / `latest-linux.yml` (+ instalator, `.blockmap`) | `electron-updater` w launcherze (start + co 4 h) | SHA-512 + (Windows, gdy podpisany) podpis wydawcy |

* `MEDIRIAN_MANIFEST_URL` = `https://github.com/<owner>/<repo>/releases/latest/download/release-manifest.json`.
  Workflow wydania ustawia go sam; w `.env` wpisz go tylko dla lokalnych buildów wydania.
* Workflow buduje manifest poleceniem `node scripts/build-clients.mjs --base-url https://github.com/<repo>/releases/download/v<wersja>/`
  — adresy jarów w manifeście są stałe dla danej wersji, a `latest/download/` zawsze wskazuje najnowsze wydanie.
* Kanał aktualizacji launchera (`app-update.yml` w aplikacji) wskazuje `owner/repo` z workflowu.
  **Nie zmieniaj nazwy repozytorium ani właściciela po pierwszym wydaniu** — zainstalowane launchery
  szukałyby aktualizacji pod starym adresem (GitHub przekierowuje, ale nie należy na tym polegać).
* Po pierwszym **podpisanym** wydaniu `electron-updater` na Windows instaluje tylko aktualizacje podpisane
  przez **tego samego wydawcę** (nazwa z certyfikatu). Zmiana certyfikatu na inny podmiot (inne CN) zatrzyma
  aktualizacje — wtedy wydaj wersję przejściową podpisaną starym certyfikatem albo poproś graczy o ponowną
  instalację.

**Własny serwer zamiast GitHub Releases:** `node scripts/build-clients.mjs --base-url https://cdn.medirian.net/0.4.0/`,
wgraj `distribution/` pod ten adres i ustaw `MEDIRIAN_MANIFEST_URL` na `https://cdn.medirian.net/0.4.0/release-manifest.json`
(lub stały alias „latest”). Kanał launchera wymaga wtedy `-c.publish.provider=generic -c.publish.url=…`.

**Jak sprawdzić:** zainstaluj wersję N, opublikuj N+1 → launcher pokazuje „Uruchom ponownie, aby zaktualizować”;
po kliknięciu instaluje się po cichu w tym samym folderze, profile i światy zostają.
Przy następnym *Graj* launcher pobiera jary klienta nowej wersji z manifestu (postęp na przycisku).

---

## 10. Podpis kodu Windows (Authenticode)

Bez podpisu Windows SmartScreen ostrzega „Nieznany wydawca”, a **Windows 11 ze Smart App Control blokuje**
niepodpisany instalator, `Medirian Client.exe` i deinstalator (sprawdzone w testach). **Publiczne wydanie
musi być podpisane** — workflow wydania odmówi zbudowania wydania bez podpisu (wyjątek: zmienna
`MEDIRIAN_ALLOW_UNSIGNED=1`, patrz wariant C i wydania testowe).

> **Ważne (od 1 czerwca 2023):** nowe certyfikaty do podpisu kodu (OV i EV) mają klucz prywatny
> **wyłącznie** na sprzętowym tokenie USB albo w chmurowym HSM dostawcy — nie da się ich wyeksportować
> do pliku `.pfx`. Wybierz wariant według tego, kim jesteś:

| Kim jesteś | Wariant |
|---|---|
| Firma (np. sp. z o.o.) z UE, Wielkiej Brytanii, USA, Kanady i kilku innych krajów, z numerem DUNS | **B — Azure Artifact Signing** (podpis w GitHub Actions, bez tokenu) |
| Osoba prywatna w USA lub Kanadzie | **B — Azure Artifact Signing** |
| Osoba prywatna w Polsce/UE (bez firmy) | **C — certyfikat OV od urzędu certyfikacji, podpis lokalny** (Artifact Signing nie wystawia certyfikatów publicznych osobom spoza USA/Kanady) |
| Masz już certyfikat z kluczem w pliku `.pfx` (starszy lub firmowy) | **A — plik certyfikatu** |

Aktualne warunki sprawdź przed zakupem w dokumentacji Microsoftu (*Artifact Signing → Quickstart*) — zmieniają się.

### Wariant B: Azure Artifact Signing (dawniej „Trusted Signing”)

Usługa Microsoftu: Microsoft weryfikuje Twoją tożsamość, wystawia certyfikat i trzyma klucz; podpis
odbywa się przez API. Koszt: plan *Basic* (miesięczna opłata, cennik na stronie Azure).

1. <https://portal.azure.com> → subskrypcja (wymaga karty płatniczej) → *Create a resource* →
   **Artifact Signing Account** → nazwa (np. `medirian`), region (np. *West Europe*), plan *Basic*.
2. W koncie → **Identity validations** → *New identity* → *Public* → weryfikacja (firma: DUNS, dwa adresy
   e-mail, działająca strona; trwa od kilku godzin do kilku dni).
3. **Certificate profiles** → *Create* → *Public Trust* → zatwierdzona tożsamość → nazwa profilu (np. `public`).
4. **Overview** konta → skopiuj **Account URI** (np. `https://weu.codesigning.azure.net/`).
5. Dostęp dla GitHub Actions: **Microsoft Entra ID → App registrations → New registration** (nazwa np.
   `medirian-signing`, *Accounts in this organizational directory only*) → skopiuj *Directory (tenant) ID*
   i *Application (client) ID* → *Certificates & secrets → New client secret* → skopiuj **Value** (widoczne
   tylko raz).
6. W koncie Artifact Signing → **Access control (IAM)** → *Add role assignment* → rola
   **Artifact Signing Certificate Profile Signer** (dawniej *Trusted Signing Certificate Profile Signer*) →
   członek: aplikacja `medirian-signing`.
7. Nazwa wydawcy: *Certificate profiles* → profil → **Subject name** — część `CN=` (np. nazwa firmy). Tę nazwę
   Windows pokaże jako wydawcę.

**Gdzie wkleić (GitHub):**

| Nazwa | Wartość | Gdzie |
|---|---|---|
| `AZURE_SIGNING_ENDPOINT` | Account URI (krok 4) | Variables |
| `AZURE_SIGNING_ACCOUNT` | nazwa konta Artifact Signing (krok 1) | Variables |
| `AZURE_SIGNING_PROFILE` | nazwa profilu certyfikatu (krok 3) | Variables |
| `AZURE_SIGNING_PUBLISHER` | CN z kroku 7 (dokładnie jak w certyfikacie) | Variables |
| `AZURE_TENANT_ID` | Directory (tenant) ID (krok 5) | Secrets |
| `AZURE_CLIENT_ID` | Application (client) ID aplikacji `medirian-signing` (krok 5) — **to nie jest** `MEDIRIAN_MSA_CLIENT_ID` | Secrets |
| `AZURE_CLIENT_SECRET` | wartość sekretu (krok 5) | Secrets |

Workflow (`release.yml`, krok *Package*) przekazuje je electron-builderowi jako `win.azureSignOptions`;
electron-builder instaluje moduł PowerShell `TrustedSigning` (nazwa modułu się nie zmieniła) i podpisuje
instalator, aplikację i deinstalator. **Ten wariant jest przygotowany w workflowie, ale nie był uruchomiony —
wymaga Twojego konta Azure.** Pierwsze wydanie zrób jako szkic (`MEDIRIAN_RELEASE_DRAFT=1`) i sprawdź podpis.

### Wariant C: certyfikat OV na tokenie / w chmurze dostawcy, podpis lokalny

Dla osób prywatnych w Polsce. Certyfikat Authenticode OV kupujesz u urzędu certyfikacji (np. **Certum** —
polski CA, oferuje certyfikaty dla osób prywatnych, także tańszy „Open Source Code Signing” dla projektów
open source; klucz na karcie/tokenie albo w chmurze SimplySign; inni: SSL.com, Sectigo, DigiCert).
Weryfikacja tożsamości (dowód osobisty, czasem wideo/notariusz) trwa kilka dni.

Taki certyfikat działa na Twoim komputerze z Windows (token USB albo aplikacja *SimplySign Desktop*, która
udostępnia certyfikat w magazynie Windows). GitHub Actions go nie użyje, więc instalator Windows podpisujesz
lokalnie i podmieniasz w wydaniu:

1. GitHub → *Variables*: `MEDIRIAN_ALLOW_UNSIGNED=1` (workflow zbuduje resztę) i `MEDIRIAN_RELEASE_DRAFT=1`
   (wydanie powstanie jako **szkic**, niewidoczny dla graczy i dla `latest/download`).
2. Wypchnij tag (§14) → workflow tworzy szkic wydania z niepodpisanym instalatorem Windows.
3. Na swoim Windows (token włożony / SimplySign zalogowany), w tej samej wersji kodu:
   ```bash
   git checkout v0.4.1
   cd launcher && npm ci && npm run build
   node --env-file-if-exists=../.env node_modules/electron-builder/cli.js --win --publish never \
     -c.publish.provider=github -c.publish.owner=<owner> -c.publish.repo=<repo> \
     -c.win.signtoolOptions.certificateSubjectName="<CN z certyfikatu>"
   ```
   (`.env` musi zawierać te same wartości publiczne co Variables na GitHubie — inaczej ten instalator miałby
   inną konfigurację niż reszta wydania.)
4. Sprawdź podpis (`Get-AuthenticodeSignature`, niżej), potem podmień pliki w szkicu:
   ```bash
   gh release upload v0.4.1 dist/MedirianClientSetup.exe dist/MedirianClientSetup.exe.blockmap dist/latest.yml --clobber
   ```
   (albo ręcznie w *Releases → Edit*: usuń trzy stare pliki, wgraj nowe). `latest.yml` **musi** pochodzić z tego
   samego buildu co instalator — zawiera jego sumę SHA-512.
5. *Releases → Edit → Publish release*.

### Wariant A: certyfikat w pliku `.pfx`

Tylko jeśli masz certyfikat Authenticode z kluczem w pliku (wystawiony przed czerwcem 2023 albo firmowy).

1. Zakoduj plik base64:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\sciezka\medirian.pfx")) | Set-Clipboard
   ```
2. **Gdzie wkleić:** GitHub → *Secrets* → `WIN_CSC_LINK` (base64 ze schowka) i `WIN_CSC_KEY_PASSWORD`
   (hasło pliku). Lokalnie w `.env`: `WIN_CSC_LINK=C:\sciezka\medirian.pfx` (ścieżka wystarczy)
   i `WIN_CSC_KEY_PASSWORD=…`. Plików `.pfx` **nie** trzymaj w repozytorium (są w `.gitignore`).

### Jak sprawdzić podpis

```powershell
Get-AuthenticodeSignature .\launcher\dist\MedirianClientSetup.exe | Format-List Status, SignerCertificate
# Status: Valid, SignerCertificate: CN=<Twoja nazwa>
```

albo: prawy przycisk na pliku → *Właściwości* → *Podpisy cyfrowe*. `node scripts/check-env.mjs --release`
wypisuje na końcu, czy wydania Windows będą podpisane w GitHub Actions.

SmartScreen: nawet podpisany plik nowego wydawcy może przez pierwsze dni pokazywać ostrzeżenie — reputacja
rośnie z liczbą pobrań (certyfikaty EV od 2024 r. nie dają już natychmiastowej reputacji).

**Nie zmieniaj wydawcy bez potrzeby:** po pierwszym podpisanym wydaniu launcher instaluje tylko aktualizacje
podpisane przez ten sam podmiot (§9).

## 11. Podpis i notaryzacja macOS (opcjonalnie)

Potrzebne tylko, jeśli publikujesz build macOS (workflow buduje go zawsze; bez podpisu Gatekeeper blokuje
pierwsze uruchomienie, a **auto-aktualizacja na macOS wymaga podpisu**). Jeśli nie wspierasz macOS, pomiń
tę sekcję i nie linkuj plików `.dmg` na stronie.

1. Apple Developer Program: <https://developer.apple.com/programs/> (płatny, roczny).
2. *Certificates, Identifiers & Profiles* → **Developer ID Application** → utwórz certyfikat (CSR z Pęku kluczy
   na Macu) → zainstaluj → wyeksportuj z Pęku kluczy jako `.p12` z hasłem.
3. <https://appleid.apple.com> → *App-Specific Passwords* → utwórz hasło dla „Medirian notarization”.
4. *Membership* → **Team ID**.

**Gdzie wkleić (GitHub → Secrets):** `CSC_LINK` (base64 pliku `.p12`), `CSC_KEY_PASSWORD`, `APPLE_ID`
(e-mail konta Apple), `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`. Workflow podaje `CSC_*` tylko jobowi
macOS (Windows używa `WIN_CSC_*`), a electron-builder notaryzuje automatycznie, gdy trzy zmienne `APPLE_*`
są ustawione.

**Jak sprawdzić:** na Macu `spctl -a -vv "Medirian Client.app"` → `accepted, source=Notarized Developer ID`.

---

## 12. Instalator Windows

`launcher/electron-builder.yml` buduje **`MedirianClientSetup.exe`** (NSIS):

* instalacja dla bieżącego użytkownika bez uprawnień administratora do
  `%LOCALAPPDATA%\Programs\Medirian Client\Medirian Client.exe` (opcja „dla wszystkich” prosi o uprawnienia),
* skróty na pulpicie i w menu Start, wpis w *Aplikacje i funkcje* z deinstalatorem,
* języki instalatora EN/PL/DE/ES, grafiki pixel art z `scripts/generate-pixel-art.mjs`,
* nazwa bez wersji — `https://github.com/<repo>/releases/latest/download/MedirianClientSetup.exe` zawsze
  daje najnowszy instalator (z tego linku korzysta strona `website/`),
* dane graczy (`%APPDATA%\.medirian`) nigdy nie są ruszane przy instalacji, aktualizacji ani deinstalacji.

Nic w instalatorze nie wymaga konfiguracji poza podpisem (§10).

**Jak sprawdzić (czysty komputer lub maszyna wirtualna z Windows 11):**

1. Pobierz instalator z GitHub Releases → uruchom → brak ostrzeżenia „Nieznany wydawca” (przy podpisie).
2. Instalacja → skrót na pulpicie → launcher startuje (kreator pierwszego uruchomienia).
3. *Aplikacje i funkcje* → „Medirian Client” → *Odinstaluj* → program i skróty znikają, `%APPDATA%\.medirian` zostaje.
4. Cicha instalacja (dla administratorów): `MedirianClientSetup.exe /S`.

---

## 13. Budowanie wydania lokalnie

Na Windows, z wypełnionym `.env`:

```bash
node scripts/check-env.mjs --release     # wszystko "OK", "Windows releases will be signed ..."
node scripts/build-clients.mjs           # jary 4 wersji + distribution/ (kanał lokalny)
cd launcher
npm ci
npm test                                 # testy launchera
npm run dist                             # dist/MedirianClientSetup.exe (czyta ../.env: WIN_CSC_* do podpisu)
```

* `npm run dist` buduje instalator **bez kanału auto-aktualizacji** (to dodaje workflow wydania przez
  `-c.publish.*`). Do publicznej dystrybucji używaj instalatorów z GitHub Actions.
* Testy reszty: `cd client/shared && ./gradlew test`, `cd backend && npm test`, `node --test scripts/test/`,
  self-test w grze: `cd client/targets/mc-1.21.11 && ./gradlew runClient -Pselftest`.

---

## 14. Publikacja nowej wersji

1. Podnieś wersję w trzech miejscach (muszą się zgadzać):
   * `VERSION` (np. `0.4.1`),
   * `launcher/package.json` → `"version"` (+ `npm install --package-lock-only` w `launcher/`),
   * `backend/package.json` → `"version"` (wersja widoczna w `/v1/status`).
2. Dopisz na górze `CHANGELOG.md` sekcję `## 0.4.1 — RRRR-MM-DD — Tytuł` (test `scripts/test` pilnuje, że
   istnieje; launcher pokazuje ją graczom).
3. Commit i tag:
   ```bash
   git commit -am "Medirian 0.4.1"
   git tag v0.4.1
   git push origin main v0.4.1
   ```
4. *Actions → Release*: kolejno *Owner configuration* → *Client jars and manifest* + *Launcher win/mac/linux*
   → *GitHub release*. Czerwony *Owner configuration* = brakuje wartości (log mówi której).
5. Sprawdź wydanie w *Releases*: `MedirianClientSetup.exe`, `latest.yml`, `*.blockmap`, `release-manifest.json`,
   jary `medirian-*.jar`, pliki macOS/Linux.
6. `pages.yml` uruchamia się sam po publikacji i odświeża stronę pobierania.
7. Zaktualizuj backend, jeśli zmienił się `backend/` (§6.1).

Wycofanie wydania: na GitHubie *Edit release → Set as a pre-release* (albo usuń wydanie) — `latest/download/`
wskaże poprzednie wydanie. Zainstalowane launchery, które już się zaktualizowały, zostaną na nowej wersji;
popraw błąd wydaniem N+1.

---

## 15. Przed pierwszym publicznym wydaniem

1. **Prawa**: Medirian Client nie jest powiązany z Mojang/Microsoft — zostaw tę informację na stronie
   pobierania; przestrzegaj [Minecraft Usage Guidelines](https://www.minecraft.net/usage-guidelines)
   (bez sprzedawania dostępu do gry, bez logo Minecrafta jako własnego).
2. **Polityka prywatności**: backend przechowuje UUID i nick gracza, jego kosmetyki, profile w chmurze
   i kody profili. Opublikuj krótką politykę prywatności i podaj kontakt do usuwania danych; jej adres wpisz
   w `MEDIRIAN_PRIVACY_URL`, a strona pokaże link w stopce (§16).
3. **Zgoda Mojang** (§3.2) — bez niej nikt się nie zaloguje.
4. **Podpis** (§10) — bez niego Smart App Control zablokuje instalację u części graczy. Zamów certyfikat
   wcześnie: weryfikacja tożsamości trwa od kilku dni do kilku tygodni.
5. **Backend** działa pod HTTPS i ma kopie zapasowe (§6).
6. **Test produkcyjny** (lista kontrolna niżej) na czystej maszynie z buildem z GitHub Actions.
7. **Wydanie testowe**: ustaw tymczasowo zmienną `MEDIRIAN_ALLOW_UNSIGNED=1` tylko jeśli musisz
   przetestować cały pipeline przed otrzymaniem certyfikatu, opublikuj je jako *pre-release* i usuń
   zmienną przed publicznym wydaniem.

---

## 16. Strona internetowa

`website/` to oficjalna strona Medirian Client (statyczny HTML/CSS/JS, bez frameworka i bez zależności):
czym jest Medirian, funkcje, prawdziwe zrzuty ekranu, mody z Modrinth, profile, HUD, ustawienia, kosmetyki,
pobieranie i FAQ, po angielsku i po polsku. `.github/workflows/pages.yml` buduje ją poleceniem
`node scripts/build-website.mjs` i publikuje na GitHub Pages przy zmianie strony, przy każdym opublikowanym
wydaniu i ręcznie. Build sprawdza stronę (pliki, linki `#`, teksty alternatywne i rozmiary obrazków,
tłumaczenia, wersje Minecrafta i liczbę modułów zgodne z kodem, rozmiar) i przerywa publikację przy błędzie;
ten sam test działa w CI (`scripts/test/website.test.mjs`).

**Co strona bierze sama**

| Dane | Skąd |
|---|---|
| przycisk „Download” | `https://github.com/<repo>/releases/latest/download/MedirianClientSetup.exe` — repozytorium, w którym działa workflow |
| wersja, rozmiar i data instalatora; pliki macOS / Linux | API GitHuba: najnowsze wydanie (`/releases/latest`, bez szkiców i pre-release) |
| status „Medirian Services” | `GET <MEDIRIAN_SERVICES_URL>/v1/status` (backend odpowiada na nie z `Access-Control-Allow-Origin: *`) |
| link GitHub w stopce | repozytorium, w którym działa workflow |

Dopóki repozytorium nie ma opublikowanego wydania z `MedirianClientSetup.exe`, przyciski pobierania pokazują
„Coming soon” i link do wydań — strona nigdy nie podaje wymyślonej wersji ani linku. Pierwsze wydanie (§14)
uruchamia workflow strony i od tej chwili wszystko pojawia się samo.

**Do ustawienia (GitHub → Settings → Secrets and variables → Actions → Variables; puste = element ukryty)**

| Variable | Przykład | Co włącza |
|---|---|---|
| `MEDIRIAN_SERVICES_URL` | `https://api.medirian.example` | linia „Medirian Services · Online” w sekcji Download (ta sama zmienna co dla launchera, §6) |
| `MEDIRIAN_DISCORD_URL` | `https://discord.gg/…` | link Discord w stopce — zaproszenie z Discorda: *Server → Invite People → Edit invite link → Expire after: Never* |
| `MEDIRIAN_PRIVACY_URL` | `https://…/privacy` | link Privacy w stopce (§15 pkt 2) |
| `MEDIRIAN_TERMS_URL` | `https://…/terms` | link Terms w stopce |
| `MEDIRIAN_SITE_URL` | `https://medirian.example/` | tylko przy własnej domenie: adres do podglądu linków (obrazek `og:image`); domyślnie adres GitHub Pages |

Wszystkie adresy muszą być HTTPS; build odrzuca `http://`, wartości z `.env.example` i zaproszenia spoza
`discord.gg` / `discord.com`.

**Jednorazowo**: *Settings → Pages → Build and deployment → Source: GitHub Actions* (§2.3). Własna domena:
*Settings → Pages → Custom domain* + rekord CNAME u rejestratora, potem `MEDIRIAN_SITE_URL`. Ta sama strona
działa też na Vercelu lub dowolnym hostingu statycznym: zbuduj ją z tymi samymi zmiennymi
(`GITHUB_REPOSITORY=<owner>/<repo>` zamiast workflow) i opublikuj folder `distribution/website`.

**Lokalnie**

```bash
node scripts/build-website.mjs              # → distribution/website, z wartościami z .env
GITHUB_REPOSITORY=owner/repo node scripts/build-website.mjs --external   # + sprawdza linki zewnętrzne
npx serve distribution/website              # podgląd (albo dowolny serwer plików)
```

Zrzuty ekranu, grafiki i teksty: `website/README.md`.

**Jak sprawdzić**: na opublikowanej stronie przycisk *Download for Windows* pobiera `MedirianClientSetup.exe`
najnowszego wydania, pod nim widać numer wersji i rozmiar, linia *Medirian Services* mówi *Online*, a linki
w stopce prowadzą tam, gdzie trzeba.

---

## Lista kontrolna

Zaznaczaj po kolei; każda pozycja ma sposób sprawdzenia w sekcji obok.

- [ ] **Microsoft** — aplikacja w Entra ID (konta osobiste, *Allow public client flows*), `MEDIRIAN_MSA_CLIENT_ID`
      w Variables, zgoda Mojang otrzymana, logowanie działa w zbudowanym launcherze (§3)
- [ ] **Discord** — aplikacja „Medirian Client”, obrazek `medirian` w Art Assets, `MEDIRIAN_DISCORD_APP_ID`
      w Variables, status widoczny na profilu (§4)
- [ ] **GitHub** — repozytorium publiczne, kod wypchnięty, *Workflow permissions: Read and write*,
      *Pages: GitHub Actions*, CI zielone (§2)
- [ ] **HTTPS** — domena, rekord A/AAAA, porty 80/443, certyfikat Caddy, `https://<domena>/v1/status`
      z kłódką, `http://` przekierowuje (§5)
- [ ] **Backend** — `docker compose up -d --build`, `MEDIRIAN_SERVICES_URL` w Variables, kopie zapasowe
      wolumenu `medirian-data`, kod profilu tworzy się w launcherze (§6)
- [ ] **Modrinth** — `MEDIRIAN_CONTACT` w Variables, wyszukiwanie i instalacja moda działa (§7)
- [ ] **Code signing** — wariant B (Azure Artifact Signing), C (certyfikat OV na tokenie, podpis lokalny + szkic wydania)
      albo A (`WIN_CSC_*`); `Get-AuthenticodeSignature` → *Valid* na instalatorze z Releases (§10); macOS opcjonalnie (§11)
- [ ] **Website** — *Pages: GitHub Actions*, workflow „Website” zielony, przycisk pobierania daje instalator
      najnowszego wydania z wersją i rozmiarem, status usług *Online*, Discord / Privacy / Terms w stopce (§16)
- [ ] **Installer** — instalacja, skróty, uruchomienie, deinstalacja na czystym Windows 11 (§12)
- [ ] **Updates** — wersja N zainstalowana, N+1 opublikowana, launcher aktualizuje się po cichu; klient
      w grze pobiera nowe jary z manifestu (§9)
- [ ] **Production test** — na buildzie z GitHub Actions: logowanie Microsoft, skin w launcherze i w menu gry,
      uruchomienie 1.8.9 / 1.21.8 / 1.21.11 / 26.3, mod z Modrinth w profilu, kod profilu (eksport i import
      na drugim komputerze), Discord, *Ustawienia → Zaawansowane* w grze, HUD Liquid Glass, peleryna widoczna
      dla drugiego gracza (§3–§9)
- [ ] **Release** — wersja podniesiona w `VERSION` / `package.json`, sekcja w `CHANGELOG.md`, tag `v<wersja>`,
      workflow *Release* zielony, pliki w Releases, strona pobierania odświeżona (§14)
