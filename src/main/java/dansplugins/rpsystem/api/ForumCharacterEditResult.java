package dansplugins.rpsystem.api;

import java.util.Objects;

/** A bounded result safe to relay to the forum; current is null if no card exists. */
public record ForumCharacterEditResult(Status status, CharacterRecord current) {

    public ForumCharacterEditResult {
        Objects.requireNonNull(status, "status");
    }

    public boolean applied() {
        return status == Status.APPLIED || status == Status.UNCHANGED;
    }

    public String message() {
        return switch (status) {
            case APPLIED -> "Character saved.";
            case UNCHANGED -> "Character already has these details.";
            case STALE -> "The character changed since this form was opened. Refresh and try again.";
            case INVALID -> "The character details are invalid. Check the fields and try again.";
            case NAME_COOLDOWN -> "The character name is still on cooldown. Try again later.";
            case STORAGE_FAILED -> "The character could not be saved. Try again later.";
        };
    }

    public enum Status {
        APPLIED,
        UNCHANGED,
        STALE,
        INVALID,
        NAME_COOLDOWN,
        STORAGE_FAILED
    }
}
