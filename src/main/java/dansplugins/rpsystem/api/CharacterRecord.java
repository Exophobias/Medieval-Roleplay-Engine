package dansplugins.rpsystem.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Immutable operational snapshot of one character. Consumers must apply audience privacy rules.
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
        String religion,
        String appearance,
        String calling,
        String originDescription,
        String mannerisms,
        String currentGoal,
        String backstory,
        boolean showStoryPublicly) {

    /** Largest age accepted by the interactive editor and public integrations. */
    public static final int MAX_PUBLIC_AGE = 1_000_000;
    public static final int MAX_APPEARANCE_LENGTH = 300;
    public static final int MAX_CALLING_LENGTH = 120;
    public static final int MAX_ORIGIN_DESCRIPTION_LENGTH = 300;
    public static final int MAX_MANNERISMS_LENGTH = 300;
    public static final int MAX_CURRENT_GOAL_LENGTH = 300;
    public static final int MAX_BACKSTORY_LENGTH = 1_000;

    /** Old callers and schema-1 archives have no optional story fields. */
    public CharacterRecord(UUID characterId, UUID playerId, String lastKnownPlayerName,
                           CharacterStatus status, long createdAt, long endedAt,
                           long deathDeclaredAt, UUID deathApprovedBy, String endReason,
                           String name, String race, String subculture, int age, String gender,
                           String religion) {
        this(characterId, playerId, lastKnownPlayerName, status, createdAt, endedAt,
                deathDeclaredAt, deathApprovedBy, endReason, name, race, subculture, age,
                gender, religion, "", "", "", "", "", "", false);
    }

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
        appearance = cleanStoryText(appearance, MAX_APPEARANCE_LENGTH);
        calling = cleanStoryText(calling, MAX_CALLING_LENGTH);
        originDescription = cleanStoryText(originDescription, MAX_ORIGIN_DESCRIPTION_LENGTH);
        mannerisms = cleanStoryText(mannerisms, MAX_MANNERISMS_LENGTH);
        currentGoal = cleanStoryText(currentGoal, MAX_CURRENT_GOAL_LENGTH);
        backstory = cleanStoryText(backstory, MAX_BACKSTORY_LENGTH);
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
     * Opaque revision of all forum-editable fields and immutable current-card identity.
     * Religion and the cached Minecraft account name remain outside this editor's conflict scope.
     */
    public String editFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("MRE-forum-edit-v2".getBytes(StandardCharsets.US_ASCII));
            updateUuid(digest, characterId);
            updateUuid(digest, playerId);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(createdAt).array());
            updateString(digest, name);
            updateString(digest, race);
            updateString(digest, subculture);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(age).array());
            updateString(digest, gender);
            updateString(digest, appearance);
            updateString(digest, calling);
            updateString(digest, originDescription);
            updateString(digest, mannerisms);
            updateString(digest, currentGoal);
            updateString(digest, backstory);
            digest.update((byte) (showStoryPublicly ? 1 : 0));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateUuid(MessageDigest digest, UUID value) {
        digest.update(ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits()).array());
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
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

    /** Preserve prose line breaks while rejecting controls and lossy overlong values. */
    public static String cleanStoryText(String value, int maxCodePoints) {
        String raw = value == null ? "" : value;
        if (raw.codePoints().anyMatch(codePoint ->
                (Character.isISOControl(codePoint) && codePoint != '\n')
                        || (codePoint >= 0xD800 && codePoint <= 0xDFFF))) {
            throw new IllegalArgumentException("invalid character story field");
        }
        String cleaned = raw.trim();
        if (cleaned.codePointCount(0, cleaned.length()) > maxCodePoints) {
            throw new IllegalArgumentException("character story field is too long");
        }
        return cleaned;
    }

    private static boolean isSet(String value, String defaultToken) {
        return !value.isBlank() && !value.equalsIgnoreCase(defaultToken);
    }
}
