# Patriam upstream integration

The character-lifecycle release branch includes official upstream main through d2d2d2f
(1.16.2-SNAPSHOT, October 14, 2026). Preserve Patriam character lifecycle, history,
optional integrations, nationality, strict schema-1 configuration and Paper shipping profiles.
Upstream trace-client source/history is retained but deliberately not wired into startup or
commands: no telemetry configuration, network worker or shared trace file is enabled.
Upstream append-default configuration handling and default permission changes are superseded by
Patriam strict migration and the existing descriptor. The two upstream legacy descriptor tests
are superseded by PluginDescriptorTest; upstream argument/color utility checks use Jupiter.
In-game help filters the same permission alternatives as execution, separates staff pages and
supports console documentation. Runtime/client acceptance is recorded in the shared v7 handoff.
