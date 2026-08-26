package dansplugins.rpsystem.integrations.plan;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ElementOrder;
import com.djrapitops.plan.extension.annotation.BooleanProvider;
import com.djrapitops.plan.extension.annotation.Conditional;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.annotation.StringProvider;
import com.djrapitops.plan.extension.annotation.Tab;
import com.djrapitops.plan.extension.annotation.TabInfo;
import com.djrapitops.plan.extension.annotation.TableProvider;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.djrapitops.plan.extension.icon.Icon;
import com.djrapitops.plan.extension.table.Table;
import com.djrapitops.plan.extension.table.TableColumnFormat;

import java.util.UUID;

/**
 * Public character information for Plan's player and server pages.
 *
 * <p>The extension has no reference to {@code CharacterService} or {@code CharacterRecord}. It can
 * only read the allowlisted {@link PlanCharacterView}, which excludes drafts, religion, death
 * moderation details, end reasons and internal identifiers before Plan invokes a provider.
 */
@PluginInfo(
        name = "MedievalRoleplayEngine",
        iconName = "address-card",
        iconFamily = Family.SOLID,
        color = Color.AMBER)
@TabInfo(
        tab = "Characters",
        iconName = "address-card",
        iconFamily = Family.SOLID,
        elementOrder = {ElementOrder.VALUES, ElementOrder.TABLE})
public final class CharacterPlanExtension implements DataExtension {

    private static final String ACTIVE_CONDITION = "hasActiveCharacter";

    private final PlanCharacterView view;

    CharacterPlanExtension(PlanCharacterView view) {
        this.view = view;
    }

    @Override
    public CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[]{
                CallEvents.PLAYER_JOIN,
                CallEvents.PLAYER_LEAVE,
                CallEvents.PLAYER_PERIODICAL,
                CallEvents.SERVER_EXTENSION_REGISTER,
                CallEvents.SERVER_PERIODICAL
        };
    }

    // ---- player: current character ------------------------------------------------------------

    @BooleanProvider(
            text = "Has an active character",
            description = "Whether this player has a configured active character",
            conditionName = ACTIVE_CONDITION,
            hidden = true,
            iconName = "address-card",
            iconColor = Color.AMBER)
    @Tab("Characters")
    public boolean hasActiveCharacter(UUID playerId) {
        return view.current(playerId).isPresent();
    }

    /**
     * Intentionally unconditional so a newly-created draft overwrites the previously active name
     * with "None" in Plan's player table instead of leaving stale character data visible.
     */
    @StringProvider(
            text = "Current character",
            description = "This player's configured active roleplay character",
            priority = 100,
            iconName = "address-card",
            iconColor = Color.AMBER,
            showInPlayerTable = true)
    @Tab("Characters")
    public String currentCharacterName(UUID playerId) {
        return view.current(playerId)
                .map(PlanCharacterView.PublicCharacter::name)
                .orElse("None");
    }

    @StringProvider(
            text = "Race",
            description = "The active character's race",
            priority = 90,
            iconName = "people-group",
            iconColor = Color.BROWN)
    @Conditional(ACTIVE_CONDITION)
    @Tab("Characters")
    public String currentRace(UUID playerId) {
        return view.current(playerId)
                .map(PlanCharacterView.PublicCharacter::race)
                .orElse("Unspecified");
    }

    @StringProvider(
            text = "Subculture",
            description = "The active character's subculture",
            priority = 80,
            iconName = "landmark",
            iconColor = Color.ORANGE)
    @Conditional(ACTIVE_CONDITION)
    @Tab("Characters")
    public String currentSubculture(UUID playerId) {
        return view.current(playerId)
                .map(PlanCharacterView.PublicCharacter::subculture)
                .orElse("Unspecified");
    }

    @NumberProvider(
            text = "Age",
            description = "The active character's stated age",
            priority = 70,
            iconName = "cake-candles",
            iconColor = Color.LIGHT_BLUE)
    @Conditional(ACTIVE_CONDITION)
    @Tab("Characters")
    public long currentAge(UUID playerId) {
        return view.current(playerId)
                .map(PlanCharacterView.PublicCharacter::age)
                .orElse(0);
    }

    @StringProvider(
            text = "Gender",
            description = "The active character's stated gender",
            priority = 60,
            iconName = "venus-mars",
            iconColor = Color.PURPLE)
    @Conditional(ACTIVE_CONDITION)
    @Tab("Characters")
    public String currentGender(UUID playerId) {
        return view.current(playerId)
                .map(PlanCharacterView.PublicCharacter::gender)
                .orElse("Unspecified");
    }

    /** Public past characters on this player's page, newest ending first. */
    @TableProvider(tableColor = Color.BLUE_GREY)
    @Tab("Characters")
    public Table characterHistory(UUID playerId) {
        Table.Factory table = Table.builder()
                .columnOne("Character", Icon.called("address-card").build())
                .columnTwo("Culture", Icon.called("people-group").build())
                .columnThree("Fate", Icon.called("flag-checkered").build())
                .columnFour("Ended", Icon.called("calendar-xmark").build())
                .columnFourFormat(TableColumnFormat.DATE_SECOND);

        for (PlanCharacterView.PublicCharacter character : view.history(playerId)) {
            table.addRow(character.name(), character.culture(), character.fate(), character.endedAt());
        }
        return table.build();
    }

    // ---- server: current overview -------------------------------------------------------------

    @NumberProvider(
            text = "Active characters",
            description = "Configured characters currently active on this server",
            priority = 100,
            iconName = "users",
            iconColor = Color.AMBER)
    @Tab("Characters")
    public long activeCharacterCount() {
        return view.currentCharacters().size();
    }

    /** Staff overview of configured current characters. Drafts never produce a row. */
    @TableProvider(tableColor = Color.AMBER)
    @Tab("Characters")
    public Table currentCharacterOverview() {
        Table.Factory table = Table.builder()
                .columnOne("Player", Icon.called("user").build())
                .columnOneFormat(TableColumnFormat.PLAYER_NAME)
                .columnTwo("Character", Icon.called("address-card").build())
                .columnThree("Race", Icon.called("people-group").build())
                .columnFour("Subculture", Icon.called("landmark").build())
                .columnFive("Age", Icon.called("cake-candles").build());

        for (PlanCharacterView.PublicCharacter character : view.currentCharacters()) {
            if (character.playerName().isBlank()) {
                continue;
            }
            table.addRow(
                    character.playerName(),
                    character.name(),
                    character.race(),
                    character.subculture(),
                    character.age());
        }
        return table.build();
    }
}
