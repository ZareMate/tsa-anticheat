# TSA Anticheat

NeoForge 1.21.1 / 21.1.249 resource-pack hash verifier.

## How detection works

The client scans the Minecraft `resourcepacks` directory when connecting to a server.

For every resource pack it sends:

```
resource-pack-name|sha256
```

The server ignores the filename when deciding whether a pack is blacklisted. It compares only the SHA-256 hash.

ZIP resource packs are hashed from their logical files in sorted order, so changing the ZIP filename does not change the hash. ZIP timestamps/compression metadata are also ignored.

Folder resource packs use the same canonical filename + file-content hashing scheme.

## Blacklist

On the first received report the server creates:

```
config/tsa_anticheat/blacklisted_hashes.txt
```

Put one SHA-256 hash per line:

```
# Example
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

Comments beginning with `#` and invalid lines are ignored.

When a received hash matches this file, the server records:

```
YYYY-MM-DD | DETECTED | pack-name [sha256]
```

in:

```
config/tsa_anticheat/<player-uuid>.txt
```

The pack name is retained only for identifying what the player had installed; detection is based on the hash.

## Build

```bash
gradle clean build
```

The built mod is placed in `build/libs/`.
