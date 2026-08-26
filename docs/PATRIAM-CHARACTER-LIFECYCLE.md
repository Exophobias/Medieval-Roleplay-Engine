# Patriam character lifecycle and migration

This document defines the ownership, persistence and privacy boundaries for the Patriam packaging
of Medieval Roleplay Engine (MRE). It also records the safe migration from the old one-card-per-
account format. It is an operations contract, not a proposal to move every roleplay feature into
one plugin.

## Ownership boundaries

| Concern | Owner | MRE relationship |
| --- | --- | --- |
| Current card, stable character ID and ended-character history | MRE | Source of truth |
| Local/global/OOC/whisper/yell chat | PatriamChat | No competing MRE commands |
| Couriers and mail | PatriamPost | No `/bird` registration |
| Minecraft `/title` command | Paper/Minecraft | No MRE registration |
| TrueDeath declaration, objections and staff approval | PatriamUtils | Optional read-only event/service input |
| Self/staff analytics | Plan | Implemented read-only DataExtension |
| Public current and past characters | NamelessMC through PatriamNamelessBridge | Implemented privacy-filtered full snapshot |

MRE must never register `/truedeath`. PatriamUtils owns the irreversible workflow and persists the
approval before publishing its non-cancellable `TrueDeathEvent`. MRE consumes that outcome; it does
not decide whether a death is valid and PatriamUtils must not depend on MRE.

## Runtime and dependency contract

The fork compiles and deploys against Java 25 and Paper 26.2 build 92. PlaceholderAPI, Plan and
PatriamUtils are `softdepend` entries: MRE must still start when any of them is absent.

- PlaceholderAPI enables public-safe current-card placeholders.
- PatriamUtils enables TrueDeath lifecycle adoption.
- Plan enables the current/history DataExtension and remains a clean no-op when absent.

PatriamUtils is accessed only through its public service/event contract. No staff notes, internal
stores or approval commands belong in MRE. Website URLs, API keys and HTTP clients belong in
PatriamNamelessBridge, never in this public fork.

## Data model and on-disk layout

The Minecraft account UUID and character UUID are intentionally different. An account has at most
one current `DRAFT` or `ACTIVE` record and may have many immutable `DECEASED` records. `RETIRED` is
reserved in the public model for a later non-death retirement workflow; this baseline does not
invent one.

Current cards remain compatible with the historical line format:

```text
plugins/MedievalRoleplayEngine/
  cards.txt
  <account-uuid>.txt
```

Lines one through seven remain account UUID, character name, race, subculture, age, gender and the
legacy religion field. The fork appends character UUID, creation time in Unix epoch milliseconds,
and last known account name. A seven-line card receives those identifiers on its first successful
load and is rewritten atomically. The manifest is also rebuilt from UUID card files, so a crash
between the card and manifest writes does not orphan a character.

Approved deaths are archived separately:

```text
plugins/MedievalRoleplayEngine/history/<account-uuid>/<approved-at>.yml
plugins/MedievalRoleplayEngine/true-death-state.yml
```

History YAML has an explicit schema version and contains the immutable character snapshot plus the
public TrueDeath reason and timestamps. The state file is an idempotency journal of exact death
identities, not a second copy of PatriamUtils data. Writes use a same-directory temporary file,
forced file contents, and atomic replacement where the filesystem supports it.

## Migration rules

1. Stop the server and make a recoverable copy of the complete MRE data folder.
2. Install the fork without deleting `cards.txt` or UUID card files.
3. Keep `chatFeaturesEnabled: false`; an old config which already says `true` is not overwritten.
4. Start once and inspect the log before allowing character edits or TrueDeath approval.
5. Confirm current cards acquired stable IDs and that `true-death-state.yml` reports an adoption
   baseline when PatriamUtils already contains approved deaths.
6. Exercise card viewing, an atomic save, Plan/PAPI absence handling and one controlled lifecycle
   transition on a non-production copy before promotion.

The loader also recognizes the much older `plugins/medieval-roleplay-engine/` name-indexed layout.
It resolves owners, writes replacement UUID cards first, rebuilds the manifest, and renames the
source directory to a timestamped `.migrated-*` backup only after every source entry is resolved.
Missing, truncated or unknown-owner entries leave the source directory in place for a later retry.
It does not delete the only copy first. Seven-line UUID cards also hydrate their cached account name
from Bukkit's one-pass known-player index before the migrated record is saved.

The Patriam backup contains 193 structurally valid historical account cards. Of those, 167 contain
only auto-created `default*` values, 26 have at least one customized field, 24 have a configured
name, 17 have every public profile field, and 16 have all six legacy roleplay fields customized.
The production parser dry-run accepted all 193 and upgraded all of them to the ten-line shape
without touching the backup. These files are evidence of a current card, not a character timeline.
Migration may retain an unconfigured one as a `DRAFT`; it must never synthesize past characters
from defaults, Plan sessions, forum links or old TrueDeath rows.

### TrueDeath adoption baseline

On the first run with PatriamUtils, MRE reads the durable approved-death service and records every
existing composite death identity as its adoption baseline. It intentionally archives none of
them: the legacy format retained only the latest mutable card, so pairing that card with an
arbitrary older death would fabricate history.

After the baseline:

- an approved `TrueDeathEvent` archives the exact current card before installing a fresh draft;
- `(account UUID, declared-at, approved-at, approver UUID)` is the idempotency identity;
- startup and five-minute reconciliation consume an event missed after the baseline;
- an already-written archive is completed safely if replacement-draft persistence was interrupted;
- an invalid journal disables the integration instead of risking a duplicate ending; and
- multiple unprocessed deaths for one account are blocked for manual review when one current card
  cannot prove which character belonged to each death.

Do not delete or hand-edit the journal to force migration. Restore the pre-deployment backup or
perform a reviewed repair which preserves the archived character IDs and approval timestamps.

## Privacy projections

The in-JVM character service returns immutable operational records. A consumer must still build an
audience-specific projection; having API access does not make every field suitable for publication.

- PlaceholderAPI has no reliable viewer context. Expose only complete, non-secret current roleplay
  fields and return empty output for draft/default/out-of-range values. Never place moderation or
  hidden-faith data there.
- The MRE `religion` value is legacy free text, not authoritative PatriamReligion membership.
  Public integrations should omit it or replace it with PatriamReligion's explicitly public view.
- Public web payloads should not expose the approving staff UUID, internal account UUIDs, sync
  watermarks or staff notes. PatriamUtils staff notes are not present in MRE's public contract at all.
- OOC/staff/audit chat must retain the Minecraft account identity even if local roleplay chat later
  displays a character name. Character names are not authentication identities.

## Plan: self/staff analytics

Plan is appropriate for a per-player **Character** tab showing the current record and ended history
to that player and authorized staff. It is not the public directory.

Plan web permissions are deliberately coarse: `access.player.self` opens one's own page,
`access.player` opens anyone's, and `page.player.plugins` exposes all extension tabs on a permitted
page. A broad player grant could therefore expose unrelated Absence or Devotion data. Ordinary
players should receive self-page access only; cross-player and server extension pages remain staff
permissions. Plan DataExtensions are display-only, so card edits and lifecycle actions stay in game.

The implemented extension consumes immutable MRE snapshots and publishes only complete public
fields. Player data contains current name, race, subculture, age and gender plus ended-character
name, culture, fate and date. The server view contains active count and a current-character table.
Religion, character IDs, death approver/declaration, reason and source records never enter the Plan
view. Durable changes refresh the affected player and server data; bulk reload refreshes all public
owners, and an internal Plan reload re-registers the exact extension instance. Plan absence is a
clean no-op.

## NamelessMC: public profiles

Public viewing belongs in the independent NamelessMC **Characters** module with a profile tab for a
verified player's current and past characters. It follows the existing Realms integration:

1. MRE exposes an immutable, in-process snapshot and performs no HTTP.
2. PatriamNamelessBridge gathers Bukkit-dependent data on the main thread.
3. The bridge sends a bounded, authenticated full snapshot asynchronously.
4. The website validates strict types/counts and transactionally replaces one snapshot generation.
5. Profiles appear only for verified Minecraft links with `show_publicly=1`.

The bridge and module use `/index.php?route=/api/v2/minecraft/characters` rather than nesting characters under a
realm: characters can be factionless and ended history outlives realm membership. Periodic complete
snapshots remain the source of self-healing even if an event-triggered upload is later added for
lower latency. The official Nameless plugin's placeholder sender is not a substitute for this route,
and XenForo/legacy `ncms_*` tables are migration evidence rather than the current target.

## Deployment acceptance checks

- Paper reports 26.2 build 92 and the JVM reports Java 25.
- Only `/card`, `/emote`, `/me`, `/roll`, `/dice`, `/rphelp` and `/rpconfig` are registered by MRE.
- PatriamChat, PatriamPost and vanilla retain their command namespaces regardless of plugin order.
- MRE starts successfully with Plan, PlaceholderAPI and PatriamUtils independently absent.
- Seven-line current cards upgrade once, preserve all seven visible fields and keep stable IDs on
  restart.
- An approved post-baseline TrueDeath produces exactly one immutable history record and one fresh
  draft, including after a restart/replay.
- No default-only legacy card is presented as historical fact.
- PAPI, Plan and website views pass their audience/privacy checks before production exposure.
