package dansplugins.rpsystem.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only character API published through Bukkit's services manager.
 *
 * <p>Every returned value is an immutable snapshot and may safely be read by Plan or an
 * asynchronous web publisher. Character mutations remain owned by this plugin.
 */
public interface CharacterService {

    Optional<CharacterRecord> currentCharacter(UUID playerId);

    Optional<CharacterRecord> character(UUID characterId);

    /** Current record followed by ended records, newest ended record first. */
    List<CharacterRecord> characters(UUID playerId);

    /** Snapshot of every current record, including unconfigured drafts. */
    Collection<CharacterRecord> currentCharacters();

    /** Snapshot of every immutable ended record, newest first. */
    Collection<CharacterRecord> endedCharacters();
}
