# Patriam upstream integration

The character-lifecycle release branch includes official upstream main through 862dc839
(1.16.3-SNAPSHOT, November 2, 2026). Preserve Patriam character lifecycle, history,
optional integrations, nationality, strict schema-1 configuration and Paper shipping profiles.
Upstream trace-client source/history is retained but deliberately not wired into startup or
commands: no telemetry configuration, network worker or shared trace file is enabled.
Upstream append-default configuration handling and default permission changes are superseded by
Patriam strict migration and the existing descriptor. The upstream wildcard permission-resolution regression is retained and adapted to nationality
and Patriam default-false wildcard policy. The legacy source/guide descriptor contract is
superseded by PluginDescriptorTest; upstream argument/color utility checks use Jupiter.
In-game help filters the same permission alternatives as execution, separates staff pages and
supports console documentation. Runtime/client acceptance is recorded in the shared v7 handoff.

November 2 reconciliation: the 19 incoming commits retain compatible empty-emote and channel
permission-denial fixes, updated dice usage, and expanded command/chat/configuration tests.
The stricter current-card parser and CurrentCardFiles overwrite fence already supersede upstream's
malformed-file skip/rename patch: unreadable originals remain in place and cannot be overwritten.
Existing lifecycle/card repository and command/help tests supersede upstream's mutable-list,
legacy storage and permissive card-command fixture contracts. New placeholder coverage is adapted
to immutable public snapshots, hidden draft/religion fields and nationality. Strict integer/range
and Boolean config setters remain stronger than upstream; schema stays 1. Trace 0.7 source is
retained unconnected, with no new telemetry worker, config or reports. Published plugin metadata
advances to 2.0.0-patriam.7-SNAPSHOT; public character API remains compatible.
