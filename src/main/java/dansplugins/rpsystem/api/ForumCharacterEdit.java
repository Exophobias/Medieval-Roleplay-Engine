package dansplugins.rpsystem.api;

import java.util.UUID;

/** Full replacement of the five forum-editable fields for one linked Minecraft account. */
public record ForumCharacterEdit(
        UUID playerId,
        UUID expectedCharacterId,
        String expectedFingerprint,
        String name,
        String race,
        String subculture,
        int age,
        String gender) {
}
