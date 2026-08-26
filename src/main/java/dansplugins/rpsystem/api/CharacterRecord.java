package dansplugins.rpsystem.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Immutable public view of one character.
 *
 * <p>The character id identifies the roleplay character; the player id identifies the Minecraft
 * account. They are intentionally different so one account can have an append-only history.
 */
public record CharacterRecord(
        UUID characterId,
        UUID playerId,
        String lastKnownPlayerName,
        CharacterStatus status,
        long createdAt,
        long endedAt,
        long deathDeclaredAt,
        UUID deathApprovedBy,
        String endReason,
        String name,
        String race,
        String subculture,
        int age,
        String gender,
        String religion) {

    /** Largest age accepted by the interactive editor and public integrations. */
    public static final int MAX_PUBLIC_AGE = 1_000_000;

    public CharacterRecord {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(status, "status");
        lastKnownPlayerName = clean(lastKnownPlayerName);
        endReason = clean(endReason);
        name = clean(name);
        race = clean(race);
        subculture = clean(subculture);
        gender = clean(gender);
        religion = clean(religion);
        if (createdAt < 0 || endedAt < 0 || deathDeclaredAt < 0) {
            throw new IllegalArgumentException("character timestamps cannot be negative");
        }
        if (age < 0) {
            throw new IllegalArgumentException("character age cannot be negative");
        }
        if ((status == CharacterStatus.DRAFT || status == CharacterStatus.ACTIVE) && endedAt != 0) {
            throw new IllegalArgumentException("a current character cannot have an end timestamp");
        }
        if ((status == CharacterStatus.RETIRED || status == CharacterStatus.DECEASED)
                && endedAt == 0) {
            throw new IllegalArgumentException("an ended character must have an end timestamp");
        }
        if (status == CharacterStatus.DECEASED) {
            Objects.requireNonNull(deathApprovedBy, "a deceased character needs an approver");
            if (deathDeclaredAt > endedAt) {
                throw new IllegalArgumentException("death cannot be declared after it was approved");
            }
        } else if (deathApprovedBy != null || deathDeclaredAt != 0) {
            throw new IllegalArgumentException("only deceased characters can carry true-death data");
        }
    }

    public boolean isCurrent() {
        return status == CharacterStatus.DRAFT || status == CharacterStatus.ACTIVE;
    }

    public boolean isConfigured() {
        return !name.isBlank() && !"defaultName".equals(name);
    }

    /**
     * Whether this record is complete and structurally safe for Plan, PlaceholderAPI, or a
     * website. Lifecycle storage is deliberately more permissive so unusual legacy records can
     * still be migrated and archived without data loss.
     */
    public boolean isPubliclyVisible() {
        return isConfigured()
                && createdAt > 0
                && age <= MAX_PUBLIC_AGE
                && isSet(name, "defaultName")
                && isSet(race, "defaultRace")
                && isSet(subculture, "defaultSubculture")
                && isSet(gender, "defaultGender")
                && (isCurrent() || endedAt >= createdAt);
    }

    public OptionalLong endedAtOptional() {
        return endedAt == 0 ? OptionalLong.empty() : OptionalLong.of(endedAt);
    }

    public OptionalLong deathDeclaredAtOptional() {
        return deathDeclaredAt == 0
                ? OptionalLong.empty()
                : OptionalLong.of(deathDeclaredAt);
    }

    public Optional<UUID> deathApprovedByOptional() {
        return Optional.ofNullable(deathApprovedBy);
    }

    public Optional<String> endReasonOptional() {
        return endReason.isBlank() ? Optional.empty() : Optional.of(endReason);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').replace('\0', ' ').trim();
        return singleLine.length() <= 128 ? singleLine : singleLine.substring(0, 128);
    }

    private static boolean isSet(String value, String defaultToken) {
        return !value.isBlank() && !value.equalsIgnoreCase(defaultToken);
    }
}
