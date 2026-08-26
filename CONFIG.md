# Configuration Guide

All options are set in the `plugins/MedievalRoleplayEngine/config.yml` file. Options are listed in the order they appear in the default configuration.

Options can also be inspected and changed in-game by an operator with `/rpconfig show` and `/rpconfig set <option> <value>`; changes made that way are written straight back to `config.yml`.

The defaults below are those written when the plugin creates `config.yml` for the first time. An
ordered schema migration rebuilds an older `config.yml` in the latest bundled order and with the
latest bundled comments. It overlays explicit administrator values and retains unknown extension
keys after the known options in their nearest matching section.

The integer `config-version` is the configuration schema and is independent of the plugin release.
An unversioned file is schema 0. Before changing an older file, the plugin writes a byte-identical
sibling `config.yml.v<old-version>.bak`, then replaces the installed file atomically. A newer schema,
an invalid marker, an invalid known value, or invalid YAML is left untouched and the plugin disables
itself rather than guessing. An existing backup is never overwritten; later attempts add `.1`,
`.2`, and so on.

The schema-0 migration also renames the historical `neurtalAlertColor` typo and removes the retired
development-only `test` key. Other unknown keys are retained.

---

## config-version

**Type:** integer

**Default:** `1`

**Description:** Configuration schema used for ordered migrations. Do not change this manually.

---

## version

**Type:** string  
**Default:** *(set automatically by the plugin)*  
**Description:** Tracks the plugin release that last migrated this config file. It is not the schema version. Do not change this manually.

---

## localChatRadius

**Type:** integer  
**Default:** `25`  
**Description:** The radius in blocks within which players can see local roleplay chat — that is, normal chat typed by a player who has switched into local chat with `/local` or `/rp`.

**Example:**

```yaml
localChatRadius: 30
```

---

## whisperChatRadius

**Type:** integer  
**Default:** `2`  
**Description:** The radius in blocks within which players can see whispered messages (`/whisper`).

**Example:**

```yaml
whisperChatRadius: 3
```

---

## yellChatRadius

**Type:** integer  
**Default:** `50`  
**Description:** The radius in blocks within which players can see yelled messages (`/yell`).

**Example:**

```yaml
yellChatRadius: 75
```

---

## emoteRadius

**Type:** integer  
**Default:** `25`  
**Description:** The radius in blocks within which players can see emote actions, whether sent with `/emote` / `/me` or written inline between asterisks while in local chat.

**Example:**

```yaml
emoteRadius: 20
```

---

## changeNameCooldown

**Type:** integer  
**Default:** `300`  
**Description:** The cooldown in seconds before a player can change their character's name again using `/card name`.

**Example:**

```yaml
changeNameCooldown: 600
```

---

## localChatColor

**Type:** string  
**Default:** `gray`  
**Description:** The color used for local roleplay chat messages. Accepts Minecraft color names (e.g. `white`, `yellow`, `green`, `aqua`, `red`, `blue`, `gray`, `dark_gray`, etc.).

**Example:**

```yaml
localChatColor: white
```

---

## whisperChatColor

**Type:** string  
**Default:** `blue`  
**Description:** The color used for whispered messages.

**Example:**

```yaml
whisperChatColor: dark_aqua
```

---

## yellChatColor

**Type:** string  
**Default:** `red`  
**Description:** The color used for yelled messages.

**Example:**

```yaml
yellChatColor: dark_red
```

---

## emoteColor

**Type:** string  
**Default:** `gray`  
**Description:** The color used for emote actions.

**Example:**

```yaml
emoteColor: yellow
```

---

## rightClickToViewCard

**Type:** boolean  
**Default:** `true`  
**Description:** When `true`, players can right-click another player to view their character card, subject to a two-second cooldown per viewer. Viewing requires the `rp.card.lookup` permission, which is granted to everyone by default.

**Example:**

```yaml
rightClickToViewCard: false
```

---

## localOOCChatRadius

**Type:** integer  
**Default:** `25`  
**Description:** The radius in blocks within which players can see local out-of-character messages (`/lo`).

**Example:**

```yaml
localOOCChatRadius: 20
```

---

## localOOCChatColor

**Type:** string  
**Default:** `gray`  
**Description:** The color used for local OOC chat messages.

**Example:**

```yaml
localOOCChatColor: dark_gray
```

---

## positiveAlertColor

**Type:** string  
**Default:** `green`  
**Description:** The color used for positive feedback messages (e.g. success confirmations).

**Example:**

```yaml
positiveAlertColor: green
```

---

## neutralAlertColor

**Type:** string  
**Default:** `aqua`  
**Description:** The color used for neutral informational messages.

**Example:**

```yaml
neutralAlertColor: aqua
```

---

## negativeAlertColor

**Type:** string  
**Default:** `red`  
**Description:** The color used for error or failure messages.

**Example:**

```yaml
negativeAlertColor: dark_red
```

---

## chatFeaturesEnabled

**Type:** boolean  
**Default:** `false`
**Description:** Retained for compatibility with older MRE configurations. PatriamChat owns local,
global, OOC, whisper and yell chat, so the Patriam package never registers those legacy commands.
Setting this to `true` only produces a warning. `/emote` and `/me` remain available either way.

**Example:**

```yaml
chatFeaturesEnabled: false
```

---

## debugMode

**Type:** boolean  
**Default:** `false`  
**Description:** When `true`, the plugin outputs additional debug information to the server console.

**Example:**

```yaml
debugMode: true
```

---

## birdSpeed

**Type:** integer  
**Default:** `20`  
**Description:** The speed (in blocks per second) at which birds travel when delivering messages via `/bird`. Higher values mean faster delivery, since delivery delay is calculated as `distance / birdSpeed`.

**Example:**

```yaml
birdSpeed: 10
```

---

## logChat

**Type:** boolean  
**Default:** `true`  
**Description:** When `true`, messages the plugin broadcasts to nearby players are logged to the server console, tagged `[RP]` for roleplay chat (local chat, whisper, yell, emote, dice results, bird landing notices) or `[OOC]` for local out-of-character chat. Messages sent privately to a single player — card views, bird contents and command feedback — are not logged.

**Example:**

```yaml
logChat: true
```
