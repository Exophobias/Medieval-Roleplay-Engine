package dansplugins.rpsystem.cards;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
    private String appearance;
    private String calling;
    private String originDescription;
    private String mannerisms;
    private String currentGoal;
    private String backstory;
    private boolean showStoryPublicly;
    private boolean needsMigration;

    public CharacterCard(UUID playerUUID) {
        this(playerUUID, UUID.randomUUID(), System.currentTimeMillis(), "",
                DEFAULT_NAME, DEFAULT_RACE, DEFAULT_SUBCULTURE, 0,
                DEFAULT_GENDER, DEFAULT_RELIGION, "", "", "", "", "", "", false, false);
    }

    public static CharacterCard newDraft(UUID playerUUID, String playerName, long createdAt) {
        return new CharacterCard(playerUUID, UUID.randomUUID(), Math.max(0, createdAt), playerName,
                DEFAULT_NAME, DEFAULT_RACE, DEFAULT_SUBCULTURE, 0,
                DEFAULT_GENDER, DEFAULT_RELIGION, "", "", "", "", "", "", false, false);
    }

    private CharacterCard(UUID playerUUID, UUID characterId, long createdAt,
                          String lastKnownPlayerName, String name, String race,
                          String subculture, int age, String gender, String religion,
                          String appearance, String calling, String originDescription,
                          String mannerisms, String currentGoal, String backstory,
                          boolean showStoryPublicly,
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
        this.appearance = CharacterRecord.cleanStoryText(appearance,
                CharacterRecord.MAX_APPEARANCE_LENGTH);
        this.calling = CharacterRecord.cleanStoryText(calling,
                CharacterRecord.MAX_CALLING_LENGTH);
        this.originDescription = CharacterRecord.cleanStoryText(originDescription,
                CharacterRecord.MAX_ORIGIN_DESCRIPTION_LENGTH);
        this.mannerisms = CharacterRecord.cleanStoryText(mannerisms,
                CharacterRecord.MAX_MANNERISMS_LENGTH);
        this.currentGoal = CharacterRecord.cleanStoryText(currentGoal,
                CharacterRecord.MAX_CURRENT_GOAL_LENGTH);
        this.backstory = CharacterRecord.cleanStoryText(backstory,
                CharacterRecord.MAX_BACKSTORY_LENGTH);
        this.showStoryPublicly = showStoryPublicly;
        this.needsMigration = needsMigration;
    }

    /** Reads seven-line legacy cards, ten-line cards, and the full story extension. */
    public static CharacterCard fromLines(List<String> lines, long fallbackCreatedAt) {
        if (lines == null || lines.size() < 7 || (lines.size() > 10 && lines.size() != 17)) {
            throw new IllegalArgumentException("a character card must contain 7-10 or 17 lines");
        }

        UUID playerId = UUID.fromString(lines.get(0).trim());
        UUID characterId = lines.size() > 7 && !lines.get(7).isBlank()
                ? UUID.fromString(lines.get(7).trim())
                : UUID.randomUUID();
        long createdAt = lines.size() > 8 && !lines.get(8).isBlank()
                ? Long.parseLong(lines.get(8).trim())
                : Math.max(0, fallbackCreatedAt);
        String accountName = lines.size() > 9 ? lines.get(9) : "";
        boolean hasStory = lines.size() == 17;
        String visibility = hasStory ? lines.get(16) : "false";
        if (!visibility.equals("true") && !visibility.equals("false")) {
            throw new IllegalArgumentException("invalid story visibility");
        }

        return new CharacterCard(playerId, characterId, createdAt, accountName,
                lines.get(1), lines.get(2), lines.get(3), Integer.parseInt(lines.get(4).trim()),
                lines.get(5), lines.get(6),
                hasStory ? decodeStory(lines.get(10)) : "",
                hasStory ? decodeStory(lines.get(11)) : "",
                hasStory ? decodeStory(lines.get(12)) : "",
                hasStory ? decodeStory(lines.get(13)) : "",
                hasStory ? decodeStory(lines.get(14)) : "",
                hasStory ? decodeStory(lines.get(15)) : "",
                Boolean.parseBoolean(visibility), lines.size() < 10);
    }

    public synchronized List<String> serializedLines() {
        List<String> lines = new ArrayList<>(17);
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
        lines.add(encodeStory(appearance));
        lines.add(encodeStory(calling));
        lines.add(encodeStory(originDescription));
        lines.add(encodeStory(mannerisms));
        lines.add(encodeStory(currentGoal));
        lines.add(encodeStory(backstory));
        lines.add(Boolean.toString(showStoryPublicly));
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

    public synchronized void setAppearance(String value) {
        appearance = CharacterRecord.cleanStoryText(value, CharacterRecord.MAX_APPEARANCE_LENGTH);
    }

    public synchronized String getAppearance() {
        return appearance;
    }

    public synchronized void setCalling(String value) {
        calling = CharacterRecord.cleanStoryText(value, CharacterRecord.MAX_CALLING_LENGTH);
    }

    public synchronized String getCalling() {
        return calling;
    }

    public synchronized void setOriginDescription(String value) {
        originDescription = CharacterRecord.cleanStoryText(value,
                CharacterRecord.MAX_ORIGIN_DESCRIPTION_LENGTH);
    }

    public synchronized String getOriginDescription() {
        return originDescription;
    }

    public synchronized void setMannerisms(String value) {
        mannerisms = CharacterRecord.cleanStoryText(value,
                CharacterRecord.MAX_MANNERISMS_LENGTH);
    }

    public synchronized String getMannerisms() {
        return mannerisms;
    }

    public synchronized void setCurrentGoal(String value) {
        currentGoal = CharacterRecord.cleanStoryText(value,
                CharacterRecord.MAX_CURRENT_GOAL_LENGTH);
    }

    public synchronized String getCurrentGoal() {
        return currentGoal;
    }

    public synchronized void setBackstory(String value) {
        backstory = CharacterRecord.cleanStoryText(value,
                CharacterRecord.MAX_BACKSTORY_LENGTH);
    }

    public synchronized String getBackstory() {
        return backstory;
    }

    public synchronized void setShowStoryPublicly(boolean value) {
        showStoryPublicly = value;
    }

    public synchronized boolean isShowStoryPublicly() {
        return showStoryPublicly;
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
        appearance = snapshot.appearance();
        calling = snapshot.calling();
        originDescription = snapshot.originDescription();
        mannerisms = snapshot.mannerisms();
        currentGoal = snapshot.currentGoal();
        backstory = snapshot.backstory();
        showStoryPublicly = snapshot.showStoryPublicly();
    }

    private CharacterRecord record(CharacterStatus status, long endedAt, long declaredAt,
                                   UUID approvedBy, String reason) {
        return new CharacterRecord(characterId, playerUUID, lastKnownPlayerName, status,
                createdAt, endedAt, declaredAt, approvedBy, clean(reason), name, race,
                subculture, age, gender, religion, appearance, calling, originDescription,
                mannerisms, currentGoal, backstory, showStoryPublicly);
    }

    private static String encodeStory(String value) {
        return value.isEmpty() ? "-" : Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeStory(String line) {
        if (line.equals("-")) {
            return "";
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(line);
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException e) {
            throw new IllegalArgumentException("invalid encoded character story", e);
        }
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
