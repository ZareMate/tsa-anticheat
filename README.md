# TSA Anticheat

NeoForge 1.21.1 / 21.1.249 client mod and server-side SHA-256 verifier for resource packs, mods, and TSA packet-integrity checks.

## Resource pack and mod detection

When connecting to a server, the client scans:

- `resourcepacks/`
- `mods/`

For every entry it calculates a deterministic SHA-256 hash and sends:

```
TYPE<TAB>name<TAB>sha256
```

Examples:

```
RESOURCE_PACK\tXrayPack.zip\t012345...
MOD\tsome-cheat-mod.jar\tabcdef...
```

The server's detection decision uses only the SHA-256 hash. The filename is retained only so the resulting report is readable.

Renaming a pack or mod does not bypass detection.

### Resource packs

ZIP resource packs are hashed from their logical files in sorted order. ZIP timestamps and compression metadata are ignored.

Folder resource packs use the same canonical relative-path + file-content hashing scheme.

### Mods

Mod JARs are treated as ZIP files and hashed from their logical entries in sorted order, so changing the JAR filename does not change its hash.

## Packet integrity check

Operators can manually challenge a connected client with:

```
/tsa packetcheck <player>
```

Alias:

```
/tsa checkpacket <player>
```

The command has player autocomplete and requires permission level 3.

The server generates a fresh random challenge and sends it to the client. The client must return:

1. The exact challenge it received.
2. A SHA-256 response derived from:

```
tsa-anticheat:packet-integrity:v1|player-uuid|challenge
```

The server verifies both values.

Results are:

- `PASS` — challenge and response matched.
- `MODIFIED` — the challenge or response was changed.
- `TIMEOUT` — no valid response was received within 5 seconds.

Results are appended to:

```
config/tsa_anticheat/<player-uuid>.txt
```

Failed packet checks (`MODIFIED` and `TIMEOUT`) and blacklisted resource-pack detections can optionally be sent to a Discord webhook. Successful `PASS` checks do not send webhook notifications. Packet results are still broadcast to staff/operators. The default common configuration is:

```toml
webhook_enabled = false
webhook_url = ""
command_permission = "tsa_anticheat.command"
broadcast_permission = "tsa_anticheat.alerts"
packet_check_timeout_seconds = 5
```

Set `webhook_enabled = true` and provide `webhook_url` to enable Discord notifications. The packet-integrity response timeout defaults to 5 seconds and can be configured from 1 to 300 seconds.



The `command_permission` controls access to all `/tsa` commands. Players with permission level 3 always have access. By default, grant the LuckPerms node `tsa_anticheat.command`.
## Current detection command

Staff can force an immediate client-side scan of a connected player:

```
/tsa detect <player>
```

The scan checks the player's current `mods/` and `resourcepacks/` directories against the server blacklist.

- A clean result is shown to the command requester and staff with `tsa_anticheat.alerts`.
- Detected entries are shown with their type, filename, and SHA-256 hash.
- Detected results are saved to the player's TSA report.
- Detected results are sent to the configured Discord webhook.
- Clean results do not send a webhook.
- The command uses the same `tsa_anticheat.command` permission as the other `/tsa` commands.

## Hash generation command

Operators can generate the same deterministic SHA-256 hashes used by TSA's client scanner from the **local files of the player executing the command**.

Run from an in-game operator account:

```
/tsa hash mod <name>
/tsa hash resourcepack <name>
```

Use `*` to hash every local entry of that type:

```
/tsa hash mod *
/tsa hash resourcepack *
```

For named entries, the client resolves the name inside its own `mods/` or `resourcepacks/` directory. The server does not read its own directories for these commands, so the resulting hashes represent the player's installed files.

Generated hashes are sent back to the server and saved to:

```
config/tsa_anticheat/generated_hashes.txt
```

Duplicate entries are not written twice. The wildcard command reports the number of successful and failed local hashes.

The generated value can be pasted directly into:

```
config/tsa_anticheat/blacklisted_hashes.txt
```

The hash algorithm is identical to the normal client-side scanner: ZIP/JAR metadata such as timestamps and compression settings are ignored, while logical file names and contents are included.

The server cannot autocomplete arbitrary local filenames because it does not have access to the player's filesystem; `*` is available directly in the command tree.

## Public API

Other server-side mods can read TSA data through:

```java
import com.zaremate.tsa_anticheat.api.TsaAnticheatAPI;

Optional<TsaAnticheatAPI.PlayerRecord> record =
        TsaAnticheatAPI.getPlayer(playerUuid);

List<TsaAnticheatAPI.PlayerRecord> players =
        TsaAnticheatAPI.getPlayers();
```

A `PlayerRecord` contains:

- player UUID and name
- packet check totals
- PASS / MODIFIED / TIMEOUT totals
- latest packet-check status and timestamp
- stored resource-pack/mod detection lines

The API returns immutable snapshots and does not expose the internal file format.

## Admin Notes integration

TSA Anticheat includes an optional bridge for the [Admin Notes](https://github.com/ZareMate/admin-notes) mod.

The bridge uses reflection, so Admin Notes is **not required** and TSA can run normally without it. When Admin Notes is present, other integration code can use:

```java
com.zaremate.tsa_anticheat.integration.AdminNotesIntegration
```

Supported bridge operations include reading all notes, reading a note by ID, adding a system note, and removing a note.

Example:

```java
List<AdminNotesIntegration.NoteView> notes =
        AdminNotesIntegration.getNotes(playerUuid);

Optional<AdminNotesIntegration.NoteView> note =
        AdminNotesIntegration.getNote(playerUuid, noteId);

AdminNotesIntegration.addSystemNote(
        playerUuid,
        "Player was flagged by TSA Anticheat."
);
```

For the normal TSA integration pattern, Admin Notes should query `TsaAnticheatAPI` and render TSA's current data as a live section rather than creating duplicate notes.

## Blacklist

On the first received report the server creates:

```
config/tsa_anticheat/blacklisted_hashes.txt
```

Put one SHA-256 hash per line. The same file can contain hashes for both mods and resource packs:

```
# Resource pack
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# Mod
abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

Comments beginning with `#` and invalid lines are ignored.

When a received hash matches, the server records a report such as:

```
2026-09-27 | DETECTED | MOD some-cheat-mod.jar [abcdef...]
```

or:

```
2026-09-27 | DETECTED | RESOURCE_PACK XrayPack.zip [012345...]
```

Reports are stored in:

```
config/tsa_anticheat/<player-uuid>.txt
```

## Build

```bash
gradle clean build
```

The built mod is placed in `build/libs/`.
