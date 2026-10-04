# Usługi Meridian (`backend/`)

Mały serwer HTTP/JSON (Node 22+, bez zależności): konta Meridian, kosmetyki widoczne dla innych
graczy i profile konfiguracji w chmurze. Bez skonfigurowanego adresu usług klient działa w pełni
lokalnie (peleryna widoczna tylko dla gracza, brak chmury) i mówi o tym w UI.

## Logowanie bez hasła i bez wysyłania tokenu do Meridian

Konto Meridian = konto Minecraft. Dowód tożsamości to ten sam mechanizm, którego używa każdy serwer
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

Klient pobiera loadouty innych graczy leniwie: pierwsza klatka, w której gracz jest renderowany,
dodaje jego UUID do kolejki; kolejka jest wysyłana jednym zapytaniem (do 100 graczy, opóźnienie
0,4 s), wynik jest buforowany 5 min (błąd → ponowienie po minucie).

Emotki innych graczy klient odpytuje co sekundę (tylko o graczy, których widzi) i odtwarza je od momentu
wskazanego przez `elapsedMs`, więc różnice zegarów nie mają znaczenia.

Tekstury kosmetyków są w kliencie (`assets/meridian/textures/cosmetics/`); serwer przechowuje tylko
identyfikatory. Nowa peleryna = PNG wygenerowany przez `node scripts/generate-capes.mjs` (albo
własny, układ 64×32 w dowolnej skali 2:1) + wpis w obu `catalogue.json` (test pilnuje zgodności). Czapka = model z bryłami
w `client/shared/src/main/resources/meridian/cosmetics/models/<id>.json` (układ UV jak w modelach Minecrafta); tekstury
czapek, skrzydeł i ikony podglądu robi `node scripts/generate-cosmetics.mjs`.

## Uruchomienie

```bash
cd backend
npm test                                   # testy (Mojang zastąpiony atrapą)
PORT=8080 DATA_DIR=/var/lib/meridian node src/index.mjs
```

Docker: `docker build -t meridian-services backend && docker run -p 8080:8080 -v meridian-data:/data meridian-services`.
Dane to pliki JSON (`users/<uuid>.json`, `grants.json`), zapisywane atomowo. Przed serwerem
powinien stać reverse proxy z HTTPS (np. Caddy/nginx).

Kosmetyki przyznawane (`"access": "grant"`, np. Founder): `DATA_DIR/grants.json`
`{ "cape_founder": ["<uuid>", …] }` — odczytywane przy każdym zapytaniu, bez restartu.

## Klient i launcher

* Klient czyta adres z `-Dmeridian.api` (launcher dodaje go sam) albo `MERIDIAN_API_URL`.
* Launcher: *Ustawienia → Deweloperskie → Adres usług Meridian*, domyślnie wbudowany przy buildzie
  (`MAIN_VITE_SERVICES_URL`, w workflow wydania zmienna repozytorium `SERVICES_URL`) albo `MERIDIAN_SERVICES_URL`.

## Test end-to-end z kontem offline

```bash
node backend/dev/fake-session-server.mjs 18091
PORT=18080 MOJANG_SESSION_URL=http://127.0.0.1:18091/session/minecraft node backend/src/index.mjs
cd client/targets/mc-1.8.9
MERIDIAN_API_URL=http://127.0.0.1:18080 MERIDIAN_SESSION_SERVER=http://127.0.0.1:18091/session/minecraft ./gradlew runClient -Pselftest
```

Self-test loguje wtedy `Self-test: services OK (cloud profile round trip, own cape visible to others…)`.
Atrapa serwera sesji akceptuje każde logowanie — **wyłącznie do testów**.
