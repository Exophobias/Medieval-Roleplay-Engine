package dansplugins.rpsystem.api;

import java.util.UUID;

/** Full replacement of the forum-editable fields for one linked Minecraft account. */
public record ForumCharacterEdit(
        UUID playerId,
        UUID expectedCharacterId,
        String expectedFingerprint,
        String name,
        String race,
        String subculture,
        int age,
        String gender,
        String appearance,
        String calling,
        String originDescription,
        String mannerisms,
        String currentGoal,
        String backstory,
        boolean showStoryPublicly) {

    /** Compatibility for an in-flight edit from the five-field protocol. */
    public ForumCharacterEdit(UUID playerId, UUID expectedCharacterId,
                              String expectedFingerprint, String name, String race,
                              String subculture, int age, String gender) {
        this(playerId, expectedCharacterId, expectedFingerprint, name, race, subculture,
                age, gender, "", "", "", "", "", "", false);
    }
}
