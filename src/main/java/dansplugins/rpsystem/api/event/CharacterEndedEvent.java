package dansplugins.rpsystem.api.event;

import dansplugins.rpsystem.api.CharacterRecord;
import dansplugins.rpsystem.api.CharacterStatus;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Fired after a configured current card has been durably archived and replaced by a fresh draft. */
public final class CharacterEndedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CharacterRecord ended;
    private final CharacterRecord replacementDraft;

    public CharacterEndedEvent(CharacterRecord ended, CharacterRecord replacementDraft) {
        this.ended = Objects.requireNonNull(ended, "ended");
        this.replacementDraft = Objects.requireNonNull(replacementDraft, "replacementDraft");
        if (ended.isCurrent()) {
            throw new IllegalArgumentException("ended record must be historical");
        }
        if (replacementDraft.status() != CharacterStatus.DRAFT) {
            throw new IllegalArgumentException("replacement record must be a draft");
        }
        if (!ended.playerId().equals(replacementDraft.playerId())) {
            throw new IllegalArgumentException("replacement draft must have the same owner");
        }
        if (ended.characterId().equals(replacementDraft.characterId())) {
            throw new IllegalArgumentException("replacement draft needs a new character id");
        }
        if (replacementDraft.createdAt() < ended.endedAt()) {
            throw new IllegalArgumentException("replacement draft cannot predate the ending");
        }
    }

    public CharacterRecord ended() {
        return ended;
    }

    public CharacterRecord replacementDraft() {
        return replacementDraft;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
