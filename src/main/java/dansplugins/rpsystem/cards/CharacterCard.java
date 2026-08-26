package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Mutable current card. External consumers receive immutable {@link CharacterRecord} snapshots. */
public final class CharacterCard {

    public static final String DEFAULT_NAME = "defaultName";
    public static final String DEFAULT_RACE = "defaultRace";
    public static final String DEFAULT_SUBCULTURE = "defaultSubculture";
    public static final String DEFAULT_GENDER = "defaultGender";
    public static final String DEFAULT_RELIGION = "defaultReligion";
    private static final int MAX_FIELD_LENGTH = 128;

    private final UUID playerUUID;
    private final UUID characterId;
    private final long createdAt;
    private String lastKnownPlayerName;
    private String name;
    private String race;
    private String subculture;
    private int age;
    private String gender;
    private String religion;
    private boolean needsMigration;

    public CharacterCard(UUID playerUUID) {
        this(playerUUID, UUID.randomUUID(), System.currentTimeMillis(), "",
                DEFAULT_NAME, DEFAULT_RACE, DEFAULT_SUBCULTURE, 0,
                DEFAULT_GENDER, DEFAULT_RELIGION, false);
    }

    public static CharacterCard newDraft(UUID playerUUID, String playerName, long createdAt) {
        return new CharacterCard(playerUUID, UUID.randomUUID(), Math.max(0, createdAt), playerName,
                DEFAULT_NAME, DEFAULT_RACE, DEFAULT_SUBCULTURE, 0,
                DEFAULT_GENDER, DEFAULT_RELIGION, false);
    }

    private CharacterCard(UUID playerUUID, UUID characterId, long createdAt,
                          String lastKnownPlayerName, String name, String race,
                          String subculture, int age, String gender, String religion,
                          boolean needsMigration) {
        this.playerUUID = Objects.requireNonNull(playerUUID, "playerUUID");
        this.characterId = Objects.requireNonNull(characterId, "characterId");
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt cannot be negative");
        }
        this.createdAt = createdAt;
        this.lastKnownPlayerName = clean(lastKnownPlayerName);
        this.name = clean(name);
        this.race = clean(race);
        this.subculture = clean(subculture);
        this.age = checkedAge(age);
        this.gender = clean(gender);
        this.religion = clean(religion);
        this.needsMigration = needsMigration;
    }

    /** Reads the original seven-line format plus the fork's optional appended metadata. */
    public static CharacterCard fromLines(List<String> lines, long fallbackCreatedAt) {
        if (lines == null || lines.size() < 7) {
            throw new IllegalArgumentException("a character card must contain at least seven lines");
        }

        UUID playerId = UUID.fromString(lines.get(0).trim());
        UUID characterId = lines.size() > 7 && !lines.get(7).isBlank()
                ? UUID.fromString(lines.get(7).trim())
                : UUID.randomUUID();
        long createdAt = lines.size() > 8 && !lines.get(8).isBlank()
                ? Long.parseLong(lines.get(8).trim())
                : Math.max(0, fallbackCreatedAt);
        String accountName = lines.size() > 9 ? lines.get(9) : "";

        return new CharacterCard(playerId, characterId, createdAt, accountName,
                lines.get(1), lines.get(2), lines.get(3), Integer.parseInt(lines.get(4).trim()),
                lines.get(5), lines.get(6), lines.size() < 10);
    }

    public synchronized List<String> serializedLines() {
        List<String> lines = new ArrayList<>(10);
        lines.add(playerUUID.toString());
        lines.add(name);
        lines.add(race);
        lines.add(subculture);
        lines.add(Integer.toString(age));
        lines.add(gender);
        lines.add(religion);
        lines.add(characterId.toString());
        lines.add(Long.toString(createdAt));
        lines.add(lastKnownPlayerName);
        return List.copyOf(lines);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public synchronized UUID getCharacterId() {
        return characterId;
    }

    public synchronized long getCreatedAt() {
        return createdAt;
    }

    public synchronized String getLastKnownPlayerName() {
        return lastKnownPlayerName;
    }

    /** @return whether the cached account name changed */
    public synchronized boolean setLastKnownPlayerName(String playerName) {
        String cleaned = clean(playerName);
        if (lastKnownPlayerName.equals(cleaned)) {
            return false;
        }
        lastKnownPlayerName = cleaned;
        return true;
    }

    public synchronized void setName(String newName) {
        name = clean(newName);
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setRace(String newRace) {
        race = clean(newRace);
    }

    public synchronized String getRace() {
        return race;
    }

    public synchronized void setSubculture(String newSubculture) {
        subculture = clean(newSubculture);
    }

    public synchronized String getSubculture() {
        return subculture;
    }

    public synchronized void setAge(int newAge) {
        age = checkedAge(newAge);
    }

    public synchronized int getAge() {
        return age;
    }

    public synchronized void setGender(String newGender) {
        gender = clean(newGender);
    }

    public synchronized String getGender() {
        return gender;
    }

    public synchronized void setReligion(String newReligion) {
        religion = clean(newReligion);
    }

    public synchronized String getReligion() {
        return religion;
    }

    public synchronized boolean isConfigured() {
        return !name.isBlank() && !DEFAULT_NAME.equals(name);
    }

    public synchronized boolean needsMigration() {
        return needsMigration;
    }

    public synchronized void markPersisted() {
        needsMigration = false;
    }

    public synchronized CharacterRecord snapshot() {
        CharacterStatus status = isConfigured() ? CharacterStatus.ACTIVE : CharacterStatus.DRAFT;
        return record(status, 0, 0, null, "");
    }

    public synchronized CharacterRecord deceasedSnapshot(long declaredAt, long approvedAt,
                                                           UUID approvedBy, String reason) {
        if (approvedAt <= 0) {
            throw new IllegalArgumentException("approvedAt must be positive");
        }
        if (declaredAt < 0 || declaredAt > approvedAt) {
            throw new IllegalArgumentException("declaredAt must not be after approvedAt");
        }
        return record(CharacterStatus.DECEASED, approvedAt, declaredAt,
                Objects.requireNonNull(approvedBy, "approvedBy"), reason);
    }

    /** Restores a current snapshot after a durable write fails. */
    public synchronized void restore(CharacterRecord snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.isCurrent()
                || !playerUUID.equals(snapshot.playerId())
                || !characterId.equals(snapshot.characterId())) {
            throw new IllegalArgumentException("snapshot does not describe this current character");
        }
        if (createdAt != snapshot.createdAt()) {
            throw new IllegalArgumentException("snapshot changes the character creation time");
        }
        lastKnownPlayerName = snapshot.lastKnownPlayerName();
        name = snapshot.name();
        race = snapshot.race();
        subculture = snapshot.subculture();
        age = snapshot.age();
        gender = snapshot.gender();
        religion = snapshot.religion();
    }

    private CharacterRecord record(CharacterStatus status, long endedAt, long declaredAt,
                                   UUID approvedBy, String reason) {
        return new CharacterRecord(characterId, playerUUID, lastKnownPlayerName, status,
                createdAt, endedAt, declaredAt, approvedBy, clean(reason), name, race,
                subculture, age, gender, religion);
    }

    private static int checkedAge(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("age cannot be negative");
        }
        return value;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').replace('\0', ' ').trim();
        return singleLine.length() <= MAX_FIELD_LENGTH
                ? singleLine
                : singleLine.substring(0, MAX_FIELD_LENGTH);
    }
}
