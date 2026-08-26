package dansplugins.rpsystem.placeholders;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.api.CharacterRecord;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class PlaceholderAPI extends PlaceholderExpansion {
    private final MedievalRoleplayEngine medievalRoleplayEngine;

    public PlaceholderAPI(MedievalRoleplayEngine medievalRoleplayEngine) {
        this.medievalRoleplayEngine = medievalRoleplayEngine;
    }

    @Override
    public @NotNull
    String getIdentifier() {
        return "medievalroleplayengine";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", medievalRoleplayEngine.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return medievalRoleplayEngine.getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {

        params = params.toLowerCase(Locale.ROOT);

        if (player == null) return null;

        CharacterRecord card = medievalRoleplayEngine.characterService
                .currentCharacter(player.getUniqueId()).orElse(null);

        boolean known = params.equals("card_name")
                || params.equals("card_age")
                || params.equals("card_race")
                || params.equals("card_subculture")
                || params.equals("card_gender")
                || params.equals("card_religion")
                || params.equals("character_id")
                || params.equals("character_status")
                || params.equals("past_count");
        if (!known) {
            return null;
        }
        if (params.equals("past_count")) {
            long count = medievalRoleplayEngine.characterService.characters(player.getUniqueId())
                    .stream()
                    .filter(record -> !record.isCurrent() && record.isPubliclyVisible())
                    .count();
            return Long.toString(count);
        }
        if (card == null || !card.isPubliclyVisible()) {
            return "";
        }

        if (params.equalsIgnoreCase("card_name")) {
            return card.name();
        }
        if (params.equalsIgnoreCase("card_age")) {
            return Integer.toString(card.age());
        }
        if (params.equalsIgnoreCase("card_race")) {
            return card.race();
        }
        if (params.equalsIgnoreCase("card_subculture")) {
            return card.subculture();
        }
        if (params.equalsIgnoreCase("card_gender")) {
            return card.gender();
        }
        if (params.equalsIgnoreCase("card_religion")) {
            return medievalRoleplayEngine.getConfig()
                    .getBoolean("exposeReligionPlaceholder", false) ? card.religion() : "";
        }
        if (params.equals("character_id")) {
            return card.characterId().toString();
        }
        if (params.equals("character_status")) {
            return card.status().name();
        }
        return null;
    }
}
