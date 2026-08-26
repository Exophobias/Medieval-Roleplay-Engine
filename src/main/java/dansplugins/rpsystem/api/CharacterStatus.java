package dansplugins.rpsystem.api;

/**
 * Lifecycle state of a roleplay character.
 *
 * <p>A player may have at most one current record. It is a {@link #DRAFT} until the player gives
 * it a real character name and is then reported as {@link #ACTIVE}. Ended records are immutable.
 */
public enum CharacterStatus {
    DRAFT,
    ACTIVE,
    RETIRED,
    DECEASED
}
