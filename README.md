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

The server evaluates both the SHA-256 hash and the filename. Before filename rules are checked, the name is Unicode-normalized, converted to lowercase, and sanitized to ASCII letters and digits only. TSA then checks the sanitized filename against the configurable \`filename_detection_keywords\` list. A matching keyword is reported as a filename-based detection and the observed SHA-256 hash is automatically added to \`config/tsa_anticheat/blacklisted_hashes.txt\`.

Renaming a pack or mod does not bypass a hash blacklist entry. A legitimate file whose name contains \`ray\` can be exempted with the filename allowlist below.

### Resource packs

ZIP resource packs are hashed from their logical files in sorted order. ZIP timestamps and compression metadata are ignored.

Folder resource packs use the same canonical relative-path + file-content hashing scheme.

### Mods

Mod JARs are treated as ZIP files and hashed from their logical entries in sorted order, so changing the JAR filename does not change its hash.

## Configurable filename detection

TSA detects entries whose sanitized filename contains any configured keyword. Sanitization is case-insensitive and removes punctuation, spaces, underscores, hyphens, dots, and other non-alphanumeric characters before the substring check.

Keywords are configured in \`config/tsa_anticheat-common.toml\`:

```toml
filename_detection_keywords = [
    "ray",
    "xray",
    "cheat",
    "freecam"
]
```

Add or remove keywords in this list without updating the mod. Each keyword is sanitized using the same rules as filenames, and matching is performed as a substring check against the sanitized filename.

The allowlist is configured in \`config/tsa_anticheat-common.toml\`:

\`\`\`toml
ray_filename_allowlist = [
    "Ray-Tracing-Textures.zip",
    "my-ray-resource-pack.zip"
]
\`\`\`

Allowlist entries are sanitized using the same rules, then compared exactly against the sanitized filename. This means \`Ray-Tracing-Textures.zip\`, \`ray_tracing_textures.zip\`, and similar punctuation/case variants can be handled consistently by putting the intended filename in the allowlist.

A filename match is independent of the existing hash blacklist. An already-blacklisted hash still detects even when its current filename is allowlisted. For a new keyword filename match, TSA immediately persists the observed SHA-256 hash to \`blacklisted_hashes.txt\`, so later scans continue to detect that file even after it is renamed.

## Disabled JAR bypass

By default, TSA ignores files reported as `MOD` whose filenames end in
`.jar.disabled` (case-insensitive). This bypass applies to normal join scans,
manual `/tsa detect`, and hash enforcement. Disabled files do not produce
detections, blacklist learning, or punishments. Normal `.jar` files and
resource packs remain checked. The `/tsa hash` command still hashes files
normally even when the bypass is enabled.

```text
/tsa disabledjars           # show current setting
/tsa disabledjars true      # ignore .jar.disabled mods
/tsa disabledjars false     # scan .jar.disabled mods as usual
```

The setting is persisted in `config/tsa_anticheat-common.toml` as
`bypass_disabled_jars = true`, and uses the existing
`tsa_anticheat.command` permission (or OP level 3).

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
bypass_permission = "tsa.bypass"
packet_check_timeout_seconds = 5
```

Set `webhook_enabled = true` and provide `webhook_url` to enable Discord notifications. The packet-integrity response timeout defaults to 5 seconds and can be configured from 1 to 300 seconds.



The `command_permission` controls access to all `/tsa` commands. Players with permission level 3 always have access. By default, grant the LuckPerms node `tsa_anticheat.command`.
## Staff bypass

Grant `tsa.bypass` through LuckPerms (for example, `/lp group admin permission set tsa.bypass true`). This exempts a player from new TSA kicks and tempbans. It does not override an existing ban already recorded by Punish. Automatic join-scan enforcement notifications and automatic packet-check broadcasts are suppressed for bypassed staff. The scans can still run and detections can still be recorded. Explicit `/tsa detect <player>` and `/tsa packetcheck <player>` continue reporting their results. OP level alone does not grant `tsa.bypass`.

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

## Filename keyword commands

Staff can manage filename detection keywords in-game without editing the config file:

```text
/tsa keyword list
/tsa keyword add <keyword>
/tsa keyword remove <keyword>
```

Keywords are sanitized using the same rules as filename detection. For example, adding `x-ray` stores and matches it as `xray`. Changes are saved directly to `config/tsa-anticheat-common.toml`.

## Allow command

Staff can permanently allow a filename or SHA-256 hash with:

```
/tsa allow <name-or-hash>
```

For unambiguous use, the command also supports:

```
/tsa allow name <filename>
/tsa allow hash <sha256>
```

A 64-character hexadecimal value is automatically treated as a SHA-256 hash. Other values are treated as filenames. Filename entries are stored in `config/tsa_anticheat/allowed_names.txt` and use the same sanitization rules as the filename detector. Hash entries are stored in `config/tsa_anticheat/allowed_hashes.txt`.

An allowed hash overrides the blacklist and filename detection, while an allowed filename only overrides the filename detector.

## Allowed hashes

Specific SHA-256 hashes can be exempted from TSA detection with:

```
config/tsa_anticheat/allowed_hashes.txt
```

Put one SHA-256 hash per line. An allowed hash overrides both the normal `blacklisted_hashes.txt` check and the `ray` filename detector. This also prevents an allowed file's hash from being newly added to the blacklist because its filename contains `ray`.

Example:

```
# Legitimate ray-tracing resource pack
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

The hash remains allowed even if the file is renamed. Invalid lines and comments beginning with `#` are ignored.

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

### Server threading

TSA uses one bounded `tsa-anticheat-worker` thread for detection parsing,
blacklist/allowlist file access, generated hashes, report writes/removals,
packet-integrity verification and expiry, keyword persistence, and history
refreshes. Work is ordered so TSA's report writes and removals cannot overwrite
each other. The queue holds up to 1,024 waiting jobs; overload rejects work and
logs an error instead of running it on the tick thread. Admin commands report
background failures to their source.

Minecraft player lookup, permissions, packet sends, chat delivery, and the small
login-delay scheduler remain on the server thread. Async work captures player
identity first; direct replies and challenges are discarded if that connection
has left. Packet response timeout checks use the response receipt time, excluding
worker queue delay. Accepted jobs drain during server shutdown, and pending checks
and cached history are reset between server sessions.

File-changing admin commands now return `1` when submitted; their final success
or failure arrives asynchronously in chat/console. Command-chain return values
therefore indicate submission rather than the eventual file operation result.

`TsaAnticheatAPI.getPlayer` and `getPlayers` now return an immutable cached history
snapshot without disk access. It refreshes at startup and every 20 server ticks
(subject to worker backlog), and is initially empty. Integrations needing fresh
history should use `getPlayerAsync(UUID)` or `getPlayersAsync()`. Never call
`join()`/`get()` on those futures from the server thread; schedule any Minecraft
state changes from their callbacks back onto the server. Low-level storage helpers
remain synchronous and must only be called from background work.

### Automatically allow server files

At every server start, TSA hashes all immediate entries in the server directory's
`mods/` and `resourcepacks/` folders (not `/mods` at the filesystem root). Files,
JAR/ZIP archives, and directory packs use the same content hashing algorithm as
client detection. Archive metadata/order does not change the hash; directory packs
include their relative file paths and contents.

The scan runs first on the TSA background worker, before queued detection work.
Hashes are merged into `config/tsa_anticheat/allowed_hashes.txt`, preserving manual
entries and comments. These hashes override both blacklist and filename detections.
Existing detection history and the blacklist are not deleted. Exemptions persist
if files are removed; newly added or changed files are scanned on the next restart.
Missing folders are skipped, unreadable entries are logged, and the server log
reports the number hashed, newly allowed, and failed.

### Manual hash enforcement: kick and tempban

Enforcement is **off by default**. Only hashes explicitly listed in
`config/tsa_anticheat/enforcement_hashes.txt` qualify. Neither filename matches,
learned blacklist hashes, nor failed packet checks populate that list or trigger
punishment on their own. Both automatic client join reports and `/tsa detect`
check it. Hash allowlists, including automatically allowed server files, take
precedence; filename allowlisting alone does not exempt a prohibited hash.

Commands use the existing `/tsa` permission (`tsa_anticheat.command`, or OP level 3):

```text
/tsa enforcement                             # show status
/tsa enforcement status
/tsa enforcement hash add <sha256>
/tsa enforcement hash remove <sha256>
/tsa enforcement hash list
/tsa enforcement mode off
/tsa enforcement mode kick
/tsa enforcement mode tempban
/tsa enforcement kick true                   # enables kick, disables tempban
/tsa enforcement kick false                  # disables kick if currently selected
/tsa enforcement tempban true                # enables tempban, disables kick
/tsa enforcement tempban false
/tsa enforcement duration 30m                # seconds, s, m, h, or d; maximum 365d
```

The mode and duration persist in the common config as `enforcement_mode` and
`tempban_seconds` (default 3600). Hash enforcement requires the separate
[Punish](https://github.com/ZareMate/punish) mod on the server. TSA calls its
public API on the server thread to create a silent UUID-based KICK or timed BAN
with issuer `TSA Anticheat` and a reason naming the prohibited files.
Punish handles persistence, login blocking, history, notifications, and unbanning;
use `/unban <player>` to lift a Punish ban. Repeated scans do not renew an
already-active Punish ban. Manual `/tsa detect` still checks hashes, and
`tsa.bypass` prevents automatic enforcement.

TSA no longer reads or enforces old `config/tsa_anticheat/tempbans.properties`
records. Existing bans in that legacy file are **not automatically migrated** to
Punish. Remove/archive that file only after reviewing outstanding bans.
When Punish is missing or the API call fails, TSA reports a failure instead of
silently falling back to the former internal tempban system.

With webhooks enabled, join scans and manual scans identify their context and
include **Action taken**: none (and why), kicked, tempban expiry, or a failure.
Clean reported scans also send an explicit no-action result. Punish handles login refusal for active bans; TSA webhooks report whether Punish accepted the enforcement request. Large detection lists are truncated to fit Discord.
