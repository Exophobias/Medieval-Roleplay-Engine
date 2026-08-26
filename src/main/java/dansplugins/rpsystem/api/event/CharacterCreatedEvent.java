package dansplugins.rpsystem.api.event;

import dansplugins.rpsystem.api.CharacterRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Fired after a player's first current-character draft has been saved durably. */
public final class CharacterCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CharacterRecord character;

    public CharacterCreatedEvent(CharacterRecord character) {
        this.character = Objects.requireNonNull(character, "character");
        if (!character.isCurrent()) {
            throw new IllegalArgumentException("a created character must be current");
        }
    }

    public CharacterRecord character() {
        return character;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
