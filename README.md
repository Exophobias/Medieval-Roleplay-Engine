# Medieval Roleplay Engine

Medieval Roleplay Engine is a GPL-licensed Minecraft roleplay plugin created by
[DanTheTechMan and the Dans-Plugins contributors](https://github.com/Dans-Plugins/Medieval-Roleplay-Engine).
This fork keeps the upstream character-card foundation while packaging it as Patriam's character
and lifecycle service.

The Patriam profile intentionally registers only character cards, emotes, dice, help and
configuration. PatriamChat owns local/global/OOC/whisper/yell chat, PatriamPost owns couriers, and
Minecraft owns `/title`; registering competing commands here would make the result depend on plugin
load order.

## Requirements

- Java 25
- Paper 26.2 build 92 (the compile and deployment pin)
- Optional: PlaceholderAPI 2.12.3 for current-card placeholders
- Optional: PatriamUtils for approved TrueDeath lifecycle adoption
- Optional: Plan 5.7+ for self/staff current-character and history analytics

The descriptor and compile dependency both target Paper 26.2. Historical card compatibility is
handled by the storage migration rather than by pretending the Java 25 jar can run on an old
server.

## Commands

| Command | Purpose |
| --- | --- |
| `/card` | View or update the current character card; staff can force-save or force-load |
| `/emote`, `/me` | Describe a nearby roleplay action |
| `/roll`, `/dice` | Roll a die, with d20 as the default |
| `/rphelp` | Show plugin help |
| `/rpconfig` | View or change configuration as an operator |

The legacy `/bird`, `/local`, `/rp`, `/global`, `/ooc`, `/title`, `/yell`, `/whisper` and `/lo`
commands are not registered by this package. `chatFeaturesEnabled` is also `false` in the shipped
configuration. Servers upgrading an existing data folder must set that option to `false`
explicitly because existing values are preserved.

Permission nodes and their defaults are documented directly in
[`plugin.yml`](src/main/resources/plugin.yml). The descriptor includes the aggregate nodes which
the upstream command code has always recognized (`rp.default`, `rp.card.*` and `rp.admin`) as well
as every permission checked by a retained command.

## Character lifecycle

Each Minecraft account has at most one current character, identified separately from the account
UUID. Ended characters are immutable history. When PatriamUtils is present, only an approved
TrueDeath can move the exact current card into deceased history and create a fresh draft. MRE does
not register `/truedeath`, reproduce its staff workflow, or read its private staff notes.

The first deployment records an adoption baseline for deaths which already exist in PatriamUtils.
It does not invent past characters from the one overwritten legacy card. Later approved deaths are
processed idempotently, and startup reconciliation repairs a missed event when it can do so without
guessing.

See [Patriam character lifecycle and migration](docs/PATRIAM-CHARACTER-LIFECYCLE.md) for persistence,
migration, privacy, Plan and NamelessMC integration boundaries.

## Optional integrations

- **PlaceholderAPI:** exposes public-safe `medievalroleplayengine` current-card placeholders plus
  `character_id`, `character_status` and `past_count`. Incomplete/default cards return empty values;
  legacy religion remains hidden unless explicitly enabled.
- **PatriamUtils:** remains the sole owner of `/truedeath`; its public service/event is an optional
  lifecycle input.
- **Plan:** registers a read-only DataExtension with current name/race/subculture/age/gender,
  deceased history, and a staff server overview. It follows Plan reloads and refreshes after durable
  card changes. Plan is not a public character directory and does not edit characters.
- **NamelessMC:** the separate Characters module displays current and past characters on verified,
  public profiles. PatriamNamelessBridge publishes a strict full snapshot; MRE never performs HTTP
  or stores website credentials.

## Build and test

The project has no Maven wrapper, so Maven and JDK 25 must be installed:

```text
mvn clean verify
```

The resulting jar is under `target/`. The supplied container also builds the tests and downloads
the pinned Paper 26.2 build 92 server for a manual smoke test. Validate the final jar with Patriam's
complete staged plugin set before deployment.

## Upstream documentation and attribution

- [Upstream user guide](USER_GUIDE.md)
- [Upstream command reference](COMMANDS.md) — includes legacy commands not registered by the
  Patriam profile
- [Configuration reference](CONFIG.md)
- [Contributing](CONTRIBUTING.md)
- [Upstream wiki](https://github.com/Dans-Plugins/Medieval-Roleplay-Engine/wiki/Guide)
- [Upstream issue tracker](https://github.com/Dans-Plugins/Medieval-Roleplay-Engine/issues)

DanTheTechMan created Medieval Roleplay Engine. UndeadZeratul contributed the alternative roll
implementation, and Caibinus contributed the PlaceholderAPI integration. Patriam-specific changes
remain derived work under the same license.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE). Source must remain
available under GPL-3.0 when modified binaries are distributed; no additional restrictions are
added by the Patriam profile.

Upstream bStats project: [Medieval Roleplay Engine 8996](https://bstats.org/plugin/bukkit/Medieval%20Roleplay%20Engine/8996).
