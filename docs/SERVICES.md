# Usługi Medirian (`backend/`)

Mały serwer HTTP/JSON (Node 22.9+, bez zależności): konta Medirian, kosmetyki widoczne dla innych
graczy, profile konfiguracji w chmurze i kody profili (`MDN-XXXX-XXXX-XXXX`). Wdrożenie produkcyjne pod HTTPS
(domena, Caddy, Docker, kopie zapasowe): [OWNER_SETUP.md](OWNER_SETUP.md), sekcje 5 i 6. Bez skonfigurowanego adresu usług klient działa w pełni
lokalnie (peleryna widoczna tylko dla gracza, brak chmury) i mówi o tym w UI.

## Logowanie bez hasła i bez wysyłania tokenu do Medirian

Konto Medirian = konto Minecraft. Dowód tożsamości to ten sam mechanizm, którego używa każdy serwer
Minecraft w trybie online:

1. klient → `POST /v1/auth/challenge` → `{ "serverId": "<40 hex>" }` (ważne 2 min, jednorazowe),
2. klient → serwer sesji **Mojang** `POST /session/minecraft/join`
   `{ accessToken, selectedProfile, serverId }` (token gry trafia wyłącznie do Mojang),
3. klient → `POST /v1/auth/session { name, serverId }` → backend pyta Mojang
   `GET /session/minecraft/hasJoined?username&serverId` i wydaje token sesji (24 h) →
   `{ token, uuid, name, expiresAt }`.

Konta offline/deweloperskie nie przejdą kroku 3 (Mojang ich nie zna) — klient pokazuje
„Wymaga konta Microsoft”. Wygasła sesja jest odnawiana automatycznie przy pierwszym 401.

## API

Wszystkie odpowiedzi to JSON; błędy: `{ "error": "…" }` z kodem 4xx/5xx.
`Authorization: Bearer <token>` przy endpointach oznaczonych 🔒.

| Metoda | Ścieżka | Opis |
|---|---|---|
| GET | `/v1/status` | `{ name, version }` |
| POST | `/v1/auth/challenge` | nowe wyzwanie (limit 12/min na IP) |
| POST | `/v1/auth/session` | `{ name, serverId }` → token sesji |
| GET 🔒 | `/v1/me` | `{ uuid, name }` |
| GET | `/v1/cosmetics` | katalog (`backend/src/catalogue.json`, ta sama lista jest w kliencie) |
| GET 🔒 | `/v1/cosmetics/owned` | `{ owned: [id…] }` — darmowe + przyznane (`grants.json`) |
| GET 🔒 | `/v1/cosmetics/loadout` | własny loadout |
| PUT 🔒 | `/v1/cosmetics/loadout` | `{ loadout: { CAPE: "cape_moonlit" } }` — tylko posiadane |
| POST | `/v1/cosmetics/loadouts` | `{ players: [uuid…] }` (≤ 100) → `{ loadouts: { uuid: {…} } }` |
| POST 🔒 | `/v1/emotes/play` | `{ emote }` — zaczyna emotkę (tylko posiadane) |
| POST | `/v1/emotes/active` | `{ players: [uuid…] }` (≤ 100) → `{ emotes: { uuid: { emote, elapsedMs } } }` (emotki z ostatnich 10 s) |
| GET 🔒 | `/v1/profiles` | `{ profiles: [{ name, updatedAt, size }] }` |
| GET 🔒 | `/v1/profiles/<nazwa>` | `{ name, updatedAt, data }` |
| PUT 🔒 | `/v1/profiles/<nazwa>` | `{ data: {…} }` (≤ 64 KiB, ≤ 20 profili) |
| DELETE 🔒 | `/v1/profiles/<nazwa>` | usuwa profil |
| POST | `/v1/shares` | `{ profile: { format: "medirian-profile", name, … } }` (≤ 64 KiB, limit 6/min na IP) → `{ code, expiresAt, deleteKey }` |
| GET | `/v1/shares/<kod>` | `{ code, profile, createdAt, expiresAt }`; 404 — nie ma takiego kodu, 410 — wygasł |
| DELETE | `/v1/shares/<kod>` | `{ deleteKey }` — usuwa kod (klucz zna tylko twórca) |

**Kody profili** nie wymagają konta: każdy może zamienić profil w kod (z limitem) i każdy, kto zna kod, może go
odczytać przez 90 dni. Serwer przyjmuje tylko dokument `format: "medirian-profile"` i dodatkowo usuwa z niego każdy klucz,
który wygląda na prywatny (`token`, `password`, `secret`, `session`, `auth`, `apikey`…), zanim go zapisze — launcher
i tak wysyła tylko ustawienia gry, konfigurację Medirian i bezpieczne ustawienia klienta (nigdy konta, ścieżek Javy
ani argumentów JVM). Kod ma 12 znaków z alfabetu bez mylących liter (`0/O`, `1/I/L`), launcher normalizuje wpisany tekst.

**Za reverse proxy** (`MEDIRIAN_SERVICES_TRUST_PROXY=1`, ustawione w `backend/deploy`): adres gracza do limitów pochodzi
z `X-Forwarded-For`, żądania bez `X-Forwarded-Proto: https` dostają 403, odpowiedzi mają nagłówek HSTS.

Klient pobiera loadouty innych graczy leniwie: pierwsza klatka, w której gracz jest renderowany,
dodaje jego UUID do kolejki; kolejka jest wysyłana jednym zapytaniem (do 100 graczy, opóźnienie
0,4 s), wynik jest buforowany 5 min (błąd → ponowienie po minucie).

Emotki innych graczy klient odpytuje co sekundę (tylko o graczy, których widzi) i odtwarza je od momentu
wskazanego przez `elapsedMs`, więc różnice zegarów nie mają znaczenia.

Tekstury kosmetyków są w kliencie (`assets/medirian/textures/cosmetics/`); serwer przechowuje tylko
identyfikatory. Nowa peleryna = PNG wygenerowany przez `node scripts/generate-capes.mjs` (albo
własny, układ 64×32 w dowolnej skali 2:1) + wpis w obu `catalogue.json` (test pilnuje zgodności). Czapka = model z bryłami
w `client/shared/src/main/resources/medirian/cosmetics/models/<id>.json` (układ UV jak w modelach Minecrafta); tekstury
czapek, skrzydeł i ikony podglądu robi `node scripts/generate-cosmetics.mjs`.

## Uruchomienie

```bash
cd backend
npm test                                   # testy (Mojang zastąpiony atrapą)
npm start                                  # czyta ../.env: MEDIRIAN_SERVICES_PORT / _HOST / _DATA_DIR / _TRUST_PROXY
```

Produkcja: `cd backend/deploy && docker compose up -d --build` — usługa za Caddy, który sam uzyskuje certyfikat
HTTPS dla `MEDIRIAN_SERVICES_DOMAIN` (szczegóły: [OWNER_SETUP.md](OWNER_SETUP.md#6-backend--medirian-services)).
Dane to pliki JSON (`users/<uuid>.json`, `shares/`, `grants.json`), zapisywane atomowo w `MEDIRIAN_SERVICES_DATA_DIR`.
Usługa bez `MEDIRIAN_SERVICES_TRUST_PROXY=1`, słuchająca na innym interfejsie niż `127.0.0.1`, ostrzega w logu, że jest
dostępna bez HTTPS.

Kosmetyki przyznawane (`"access": "grant"`, np. Founder): `<MEDIRIAN_SERVICES_DATA_DIR>/grants.json`
`{ "cape_founder": ["<uuid>", …] }` — odczytywane przy każdym zapytaniu, bez restartu.

## Klient i launcher

* Adres usług to jedna wartość: `MEDIRIAN_SERVICES_URL` (`.env` / zmienna repozytorium), wbudowana w launcher przy buildzie.
  Launcher przekazuje ją grze jako `-Dmedirian.services=…`; klient uruchomiony bez launchera (`./gradlew runClient`) czyta
  zmienną środowiska `MEDIRIAN_SERVICES_URL`.
* Adres musi być HTTPS; `http://` jest akceptowane tylko dla `localhost` / `127.0.0.1` (testy). Klient odrzuca inny adres
  i działa wtedy lokalnie.

## Test end-to-end z kontem offline

```bash
node backend/dev/fake-session-server.mjs 18091
MEDIRIAN_SERVICES_PORT=18080 MEDIRIAN_SESSION_SERVER=http://127.0.0.1:18091/session/minecraft node backend/src/index.mjs
cd client/targets/mc-1.8.9
MEDIRIAN_SERVICES_URL=http://127.0.0.1:18080 MEDIRIAN_SESSION_SERVER=http://127.0.0.1:18091/session/minecraft ./gradlew runClient -Pselftest
```

Self-test loguje wtedy `Self-test: services OK (cloud profile round trip, own cape visible to others…)`.
Atrapa serwera sesji akceptuje każde logowanie — **wyłącznie do testów**.
