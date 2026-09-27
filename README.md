# TSA Anticheat

NeoForge 1.21.1 / 21.1.249 client mod and server-side SHA-256 verifier for resource packs and mods.

## How detection works

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

This means renaming a pack or mod does not bypass detection.

### Resource packs

ZIP resource packs are hashed from their logical files in sorted order. ZIP timestamps and compression metadata are ignored.

Folder resource packs use the same canonical relative-path + file-content hashing scheme.

### Mods

Mod JARs are treated as ZIP files and hashed from their logical entries in sorted order, so changing the JAR filename does not change its hash.

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
