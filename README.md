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
RESOURCE_PACK	XrayPack.zip	012345...
MOD	some-cheat-mod.jar	abcdef...
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

Example:

```
2026-09-27T17:30:00Z | PACKET_CHECK | PASS | challenge and response matched
2026-09-27T17:31:00Z | PACKET_CHECK | MODIFIED | response hash was modified
2026-09-27T17:32:00Z | PACKET_CHECK | TIMEOUT | no response received within 5 seconds
```

This specifically checks the integrity of the TSA challenge/response exchange. It is not a cryptographic proof that a modified client cannot emulate the TSA protocol, and it does not inspect or cryptographically authenticate arbitrary vanilla gameplay packets.

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
