package dansplugins.rpsystem.api.event;

import dansplugins.rpsystem.api.CharacterRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Fired after an edit to a current character has been saved durably. */
public final class CharacterUpdatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CharacterRecord previous;
    private final CharacterRecord current;

    public CharacterUpdatedEvent(CharacterRecord previous, CharacterRecord current) {
        this.previous = Objects.requireNonNull(previous, "previous");
        this.current = Objects.requireNonNull(current, "current");
        if (!previous.characterId().equals(current.characterId())) {
            throw new IllegalArgumentException("an update cannot change character identity");
        }
        if (!previous.playerId().equals(current.playerId())) {
            throw new IllegalArgumentException("an update cannot change character ownership");
        }
        if (previous.createdAt() != current.createdAt()) {
            throw new IllegalArgumentException("an update cannot change character creation time");
        }
        if (!previous.isCurrent() || !current.isCurrent()) {
            throw new IllegalArgumentException("updates describe current characters only");
        }
    }

    public CharacterRecord previous() {
        return previous;
    }

    public CharacterRecord current() {
        return current;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
