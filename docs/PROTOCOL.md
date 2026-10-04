# Meridian — kontrakty danych i protokół launcher ↔ klient

Ten dokument opisuje wszystko, co launcher i klient (w każdej wersji Minecrafta) muszą rozumieć
identycznie. Zmiana któregokolwiek formatu = zmiana obu stron + wpis w CHANGELOG.

## 1. `MERIDIAN_HOME`

| System | Domyślna lokalizacja |
|---|---|
| Windows | `%APPDATA%\.meridian` |
| macOS | `~/Library/Application Support/meridian` |
| Linux | `~/.meridian` |

Nadpisanie: zmienna środowiskowa `MERIDIAN_HOME` (launcher i klient) albo `-Dmeridian.home` (klient).
Implementacje: `launcher/src/main/core/paths.ts`, `client/shared/.../core/MeridianHome.java`.

```
MERIDIAN_HOME/
├── launcher/            settings.json, profiles.json, accounts.json (tokeny zaszyfrowane), logs/
├── runtime/<component>/ Java od Mojang (jre-legacy, java-runtime-delta…)
├── game/                versions/, libraries/, assets/  (współdzielone przez wszystkie targety)
├── clients/<target>/    zainstalowany jar Meridian + installed.json
├── instances/<target>/  katalog gry (mods/, saves/, options.txt, screenshots/)
├── config/              client.json, profiles/*.json, waypoints.json, cosmetics.json   ← klient
└── cache/               manifesty, natywki — można bezpiecznie usunąć
```

## 2. Manifest wydania (`release-manifest.json`)

Generowany przez `scripts/build-clients.mjs`. Kanał `local` czyta go z `distribution/`, kanał
`stable` pobiera z URL-a ustawionego w launcherze.

```json
{
  "schema": 1,
  "channel": "stable",
  "generatedAt": "2026-10-03T14:21:35.336Z",
  "client": { "version": "0.1.0" },
  "launcher": { "version": "0.1.0" },
  "targets": [
    {
      "id": "1.8.9",
      "displayName": "Minecraft 1.8.9",
      "minecraftVersion": "1.8.9",
      "branch": "legacy",
      "loader": { "type": "legacy-fabric", "version": "0.19.5" },
      "java": { "component": "jre-legacy", "majorVersion": 8 },
      "artifact": { "file": "meridian-1.8.9-0.1.0.jar", "url": "https://…/meridian-1.8.9-0.1.0.jar", "sha1": "…", "size": 380907 },
      "recommended": true,
      "tags": ["pvp"],
      "description": "PvP"
    }
  ]
}
```

* `artifact.file` — ścieżka względem manifestu (kanał lokalny); `artifact.url` — adres pobrania.
* Launcher zawsze weryfikuje `sha1` i `size`.
* `loader.version` jest przypięty — wydanie jest w pełni reprodukowalne.

## 3. Argumenty JVM przekazywane klientowi

| Właściwość | Znaczenie |
|---|---|
| `-Dmeridian.home=<dir>` | `MERIDIAN_HOME` |
| `-Dmeridian.target=<id>` | target z manifestu |
| `-Dmeridian.profile=<name>` | profil konfiguracji Meridian do wczytania (opcjonalnie) |
| `-Dmeridian.launcher.port=<port>` | port kanału live (opcjonalnie) |
| `-Dmeridian.launcher.token=<hex>` | jednorazowy token kanału live |

## 4. Kanał live (TCP, JSON Lines)

Launcher nasłuchuje na `127.0.0.1:<losowy port>`. Klient łączy się po starcie. Każda wiadomość to
jeden obiekt JSON zakończony `\n`. Pierwsza wiadomość klienta musi być `hello` z poprawnym tokenem,
inaczej połączenie jest zamykane.

Klient → launcher:

```json
{"type":"hello","token":"…","clientVersion":"0.1.0","target":"1.8.9","minecraft":"1.8.9","profile":"PvP","player":"Steve"}
{"type":"status","state":"multiplayer","server":"mc.hypixel.net"}
{"type":"status","state":"singleplayer"}
{"type":"status","state":"menu"}
{"type":"profile","name":"Performance"}
```

Launcher → klient:

```json
{"type":"notify","title":"Meridian","message":"Update downloaded","level":"info|success|warning"}
```

Nieznane typy wiadomości są ignorowane (zgodność w przód).

## 5. Konfiguracja klienta

`config/client.json`:

```json
{ "version": 1, "activeProfile": "PvP", "settings": { "language": "PL_PL", "theme": "AUTO", "modMenuKey": "RSHIFT", "…": "…" } }
```

`config/profiles/<slug>.json`:

```json
{
  "version": 1,
  "name": "PvP",
  "settings": { "hudScale": 1.0, "performanceProfile": "BALANCED" },
  "modules": {
    "keystrokes": {
      "enabled": true,
      "keybind": "NONE",
      "settings": { "keySize": 22, "background": "#8C0B0A10" },
      "hud": { "anchor": "TOP_RIGHT", "x": -4.0, "y": 4.0, "scale": 1.0 }
    }
  }
}
```

* Klawisze to przenośne nazwy (`dev.meridian.input.Key`), kolory `#AARRGGBB`.
* Moduły nieznane danej wersji gry są zachowywane przy zapisie (profil współdzielony przez 1.8.9 i 1.21.11).
* Zmiany formatu: podnieś `version` i dodaj krok w `ConfigMigrations`.

`config/waypoints.json` (moduł Waypoints, wspólny dla wszystkich wersji gry):

```json
{
  "schema": 1,
  "worlds": {
    "server:mc.example.net": [
      { "name": "Base", "x": 120, "y": 64, "z": -38, "dimension": "minecraft:overworld", "color": "#FF9B55D6", "visible": true }
    ],
    "local:Nowy świat": [
      { "name": "Death", "x": 4, "y": 31, "z": 9, "dimension": "minecraft:the_nether", "color": "#FFE5484D", "visible": true, "death": true }
    ]
  }
}
```

* Klucz świata (`GameView#worldKey`, `WorldKeys`): `server:<host[:port]>` (małe litery, bez domyślnego portu 25565)
  albo `local:<folder zapisu>`. Wymiary jako identyfikatory przestrzeni nazw (1.8.9 mapuje 0 / −1 / 1).
* Dla każdego świata przechowywany jest tylko ostatni punkt śmierci (`death: true`).

## 6. Polityki serwerów (kanały plugin message)

Serwer może wyłączyć wybrane moduły Meridian na czas gry na nim (np. Freelook na serwerze, który go
zabrania). Działa w obu wersjach gry, bez Fabric API.

Po wejściu na serwer klient:

1. rejestruje kanały `meridian:policy` i `meridian:hello` (`REGISTER` w 1.8.9, `minecraft:register` od 1.13),
2. wysyła na `meridian:hello` JSON `{"client":"Meridian","version":"0.1.4","minecraft":"1.8.9"}`.

Serwer → klient, kanał `meridian:policy`, treść UTF-8 JSON (≤ 32 KiB):

```json
{ "version": 1, "disable": ["freelook", "zoom"], "message": "Freelook jest tu zabroniony" }
```

* `disable` — identyfikatory modułów (jak w `config/profiles/*.json`); nieznane są ignorowane.
  Wymienione moduły zostają wyłączone i zablokowane (w mod menu „Zablokowane” z komunikatem serwera).
* Każda kolejna polityka zastępuje poprzednią; pusta lista `disable` znosi ograniczenia.
* Po wyjściu z serwera moduły wracają do stanu sprzed blokady.

Przykład dla pluginu Paper/Spigot (Java):

```java
getServer().getMessenger().registerOutgoingPluginChannel(this, "meridian:policy");
getServer().getMessenger().registerIncomingPluginChannel(this, "meridian:hello", (channel, player, data) -> {
    byte[] policy = "{\"disable\":[\"freelook\"],\"message\":\"No freelook here\"}".getBytes(StandardCharsets.UTF_8);
    player.sendPluginMessage(this, "meridian:policy", policy);
});
```
