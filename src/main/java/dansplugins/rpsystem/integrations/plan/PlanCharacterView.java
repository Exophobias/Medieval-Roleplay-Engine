package dansplugins.rpsystem.integrations.plan;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterService;
import dansplugins.rpsystem.api.CharacterStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The complete data boundary between character storage and Plan.
 *
 * <p>This is an allowlist, not a redaction pass. Plan can only receive the fields carried by
 * {@link PublicCharacter}; the source record's religion, end reason, death approver, death
 * declaration time and character id are never copied into this view. Unconfigured drafts are
 * rejected before they reach any provider.
 */
final class PlanCharacterView {

    private static final Comparator<PublicCharacter> CURRENT_ORDER = Comparator
            .comparing(PublicCharacter::playerName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PublicCharacter::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PublicCharacter::playerId);

    private static final Comparator<PublicCharacter> HISTORY_ORDER = Comparator
            .comparingLong(PublicCharacter::endedAt).reversed()
            .thenComparing(PublicCharacter::name, String.CASE_INSENSITIVE_ORDER);

    private final CharacterService characters;

    PlanCharacterView(CharacterService characters) {
        this.characters = Objects.requireNonNull(characters, "characters");
    }

    Optional<PublicCharacter> current(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try {
            return characters.currentCharacter(playerId).flatMap(PublicCharacter::active);
        } catch (RuntimeException ignored) {
            // Plan calls providers asynchronously. A briefly unavailable character store should
            // produce an empty analytics value, not fail Plan's entire extension pass.
            return Optional.empty();
        }
    }

    List<PublicCharacter> history(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try {
            return characters.characters(playerId).stream()
                    .map(PublicCharacter::ended)
                    .flatMap(Optional::stream)
                    .sorted(HISTORY_ORDER)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    List<PublicCharacter> currentCharacters() {
        try {
            return characters.currentCharacters().stream()
                    .map(PublicCharacter::active)
                    .flatMap(Optional::stream)
                    .sorted(CURRENT_ORDER)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    /** Every player for whom Plan has some public character data to refresh. */
    List<PlayerIdentity> publicPlayers() {
        Map<UUID, String> names = new LinkedHashMap<>();
        try {
            for (CharacterRecord record : characters.endedCharacters()) {
                PublicCharacter.ended(record).ifPresent(character ->
                        names.putIfAbsent(character.playerId(), character.playerName()));
            }
            for (CharacterRecord record : characters.currentCharacters()) {
                PublicCharacter.active(record).ifPresent(character ->
                        names.put(character.playerId(), character.playerName()));
            }
        } catch (RuntimeException ignored) {
            return List.of();
        }

        ArrayList<PlayerIdentity> players = new ArrayList<>(names.size());
        names.forEach((playerId, playerName) ->
                players.add(new PlayerIdentity(playerId, emptyToNull(playerName))));
        players.sort(Comparator
                .comparing((PlayerIdentity player) -> Objects.toString(player.playerName(), ""),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlayerIdentity::playerId));
        return List.copyOf(players);
    }

    Optional<String> publicPlayerName(UUID playerId) {
        Optional<PublicCharacter> active = current(playerId);
        if (active.isPresent() && !active.get().playerName().isBlank()) {
            return Optional.of(active.get().playerName());
        }
        return history(playerId).stream()
                .map(PublicCharacter::playerName)
                .filter(name -> !name.isBlank())
                .findFirst();
    }

    record PlayerIdentity(UUID playerId, String playerName) {
        PlayerIdentity {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    /** Only fields approved for Plan. Deliberately contains no source record or character id. */
    record PublicCharacter(UUID playerId, String playerName, String name, String race,
                           String subculture, int age, String gender, CharacterStatus status,
                           long endedAt) {

        PublicCharacter {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(status, "status");
            playerName = clean(playerName);
            name = clean(name);
            race = displayOrUnspecified(race, "defaultRace");
            subculture = displayOrUnspecified(subculture, "defaultSubculture");
            gender = displayOrUnspecified(gender, "defaultGender");
            age = Math.max(0, age);
        }

        static Optional<PublicCharacter> active(CharacterRecord record) {
            if (record.status() != CharacterStatus.ACTIVE || !publiclyConfigured(record)) {
                return Optional.empty();
            }
            return Optional.of(copyAllowedFields(record));
        }

        static Optional<PublicCharacter> ended(CharacterRecord record) {
            if ((record.status() != CharacterStatus.RETIRED
                    && record.status() != CharacterStatus.DECEASED)
                    || !publiclyConfigured(record)) {
                return Optional.empty();
            }
            return Optional.of(copyAllowedFields(record));
        }

        String culture() {
            boolean noRace = "Unspecified".equals(race);
            boolean noSubculture = "Unspecified".equals(subculture);
            if (noRace && noSubculture) {
                return "Unspecified";
            }
            if (noRace) {
                return subculture;
            }
            if (noSubculture) {
                return race;
            }
            return race + " / " + subculture;
        }

        String fate() {
            return status == CharacterStatus.DECEASED ? "Deceased" : "Retired";
        }

        private static boolean publiclyConfigured(CharacterRecord record) {
            return record.isPubliclyVisible();
        }

        private static PublicCharacter copyAllowedFields(CharacterRecord record) {
            return new PublicCharacter(
                    record.playerId(),
                    record.lastKnownPlayerName(),
                    record.name(),
                    record.race(),
                    record.subculture(),
                    record.age(),
                    record.gender(),
                    record.status(),
                    record.endedAt());
        }
    }

    private static String displayOrUnspecified(String value, String defaultToken) {
        String cleaned = clean(value);
        return cleaned.isBlank() || cleaned.equalsIgnoreCase(defaultToken)
                ? "Unspecified"
                : cleaned;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
