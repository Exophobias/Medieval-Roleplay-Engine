package dansplugins.rpsystem.integrations.truedeath;

import dansplugins.rpsystem.MedievalRoleplayEngine;
import dansplugins.rpsystem.cards.CharacterServiceImpl.EndResult;
import dansplugins.rpsystem.cards.CharacterServiceImpl.TrueDeathContext;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

/** Optional adapter from PatriamUtils' approved TrueDeath event/service to character history. */
public final class TrueDeathIntegration {

    private static final String PLUGIN_NAME = "PatriamUtils";
    private static final String EVENT_CLASS =
            "com.github.exophobias.patriamutils.api.event.TrueDeathEvent";
    private static final String SERVICE_CLASS =
            "com.github.exophobias.patriamutils.api.TrueDeathService";

    private final MedievalRoleplayEngine plugin;
    private final TrueDeathJournal journal;
    private final Listener listener = new Listener() { };
    private BukkitTask reconciliationTask;
    private boolean registered;

    public TrueDeathIntegration(MedievalRoleplayEngine plugin) {
        this.plugin = plugin;
        this.journal = new TrueDeathJournal(plugin.getDataFolder().toPath());
    }

    public void registerIfAvailable() {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (dependency == null || !dependency.isEnabled()) {
            plugin.getLogger().info("PatriamUtils is absent; TrueDeath character archiving is idle.");
            return;
        }

        TrueDeathJournal.LoadResult journalState = journal.load();
        if (journalState == TrueDeathJournal.LoadResult.INVALID) {
            plugin.getLogger().severe("true-death-state.yml is invalid; TrueDeath integration was "
                    + "disabled rather than risking a second character retirement.");
            return;
        }

        try {
            List<TrueDeathContext> durableDeaths = readDurableDeaths();
            if (journalState == TrueDeathJournal.LoadResult.NEW) {
                // Adoption baseline: historical deaths predate this character-lifecycle fork and
                // cannot be matched to an exact overwritten card without fabricating history.
                journal.establishBaseline(durableDeaths);
                plugin.getLogger().info("Established TrueDeath adoption baseline at "
                        + durableDeaths.size() + " existing approved deaths.");
            } else {
                reconcile(durableDeaths);
            }
            registerEvent();
            reconciliationTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                    this::reconcileSafely, 6_000L, 6_000L);
            registered = true;
            plugin.getLogger().info("TrueDeath character archiving is active.");
        } catch (ReflectiveOperationException | LinkageError | IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not initialize the optional TrueDeath integration", e);
        }
    }

    public void close() {
        if (reconciliationTask != null) {
            reconciliationTask.cancel();
            reconciliationTask = null;
        }
        if (registered) {
            HandlerList.unregisterAll(listener);
            registered = false;
        }
    }

    private void reconcile(List<TrueDeathContext> durableDeaths) {
        Map<String, TrueDeathContext> uniquePending = new LinkedHashMap<>();
        for (TrueDeathContext death : durableDeaths) {
            if (!journal.processed(death)) {
                uniquePending.putIfAbsent(TrueDeathJournal.identity(death), death);
            }
        }
        Map<UUID, List<TrueDeathContext>> pending = uniquePending.values().stream()
                .collect(Collectors.groupingBy(TrueDeathContext::playerId));

        for (Map.Entry<UUID, List<TrueDeathContext>> entry : pending.entrySet()) {
            ReconciliationPlan plan = planForPlayer(
                    entry.getValue(), plugin.characterService::hasArchivedDeath);
            if (plan.blocked()) {
                plugin.getLogger().severe("Cannot reconstruct " + plan.unresolved()
                        + " missed character endings for " + entry.getKey()
                        + " from one current card. No history was fabricated; manual review is required.");
                continue;
            }
            for (TrueDeathContext death : plan.deaths()) {
                process(death);
            }
        }
    }

    static ReconciliationPlan planForPlayer(Collection<TrueDeathContext> deaths,
                                             Predicate<TrueDeathContext> archived) {
        List<TrueDeathContext> ordered = deaths.stream()
                .sorted(Comparator.comparingLong(TrueDeathContext::approvedAt))
                .toList();
        long unresolved = ordered.stream().filter(archived.negate()).count();
        return new ReconciliationPlan(ordered, unresolved);
    }

    record ReconciliationPlan(List<TrueDeathContext> deaths, long unresolved) {
        ReconciliationPlan {
            deaths = List.copyOf(deaths);
            if (unresolved < 0 || unresolved > deaths.size()) {
                throw new IllegalArgumentException("invalid unresolved death count");
            }
        }

        boolean blocked() {
            return unresolved > 1;
        }
    }

    private void reconcileSafely() {
        try {
            reconcile(readDurableDeaths());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Periodic TrueDeath character reconciliation failed", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerEvent() throws ReflectiveOperationException {
        Class<?> rawEvent = Class.forName(EVENT_CLASS, false, plugin.getClass().getClassLoader());
        if (!Event.class.isAssignableFrom(rawEvent)) {
            throw new ReflectiveOperationException(EVENT_CLASS + " is not a Bukkit event");
        }
        plugin.getServer().getPluginManager().registerEvent((Class) rawEvent, listener,
                EventPriority.MONITOR, (ignored, event) -> handleEvent(event), plugin, true);
    }

    private void handleEvent(Event event) {
        try {
            TrueDeathContext death = TrueDeathRecordReader.fromEvent(event);
            // PatriamUtils persists before firing. Re-read that durable ordering instead of
            // processing the event in isolation: if an older ending failed before writing its
            // archive, letting a newer event jump the queue could attach the one current card to
            // the wrong death.
            List<TrueDeathContext> durableDeaths = readDurableDeaths();
            String eventIdentity = TrueDeathJournal.identity(death);
            boolean durablyPresent = durableDeaths.stream()
                    .anyMatch(candidate -> TrueDeathJournal.identity(candidate)
                            .equals(eventIdentity));
            if (!durablyPresent) {
                plugin.getLogger().severe("Ignored live TrueDeath for " + death.playerId()
                        + " because it was not present in PatriamUtils' durable service yet.");
                return;
            }
            reconcile(durableDeaths);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not consume TrueDeathEvent", e);
        }
    }

    private void process(TrueDeathContext death) {
        if (journal.processed(death)) {
            return;
        }
        EndResult result = plugin.characterService.endForTrueDeath(death);
        if (!result.complete()) {
            return;
        }
        try {
            journal.markProcessed(death);
            plugin.getLogger().info("Character lifecycle consumed TrueDeath for "
                    + death.playerId() + " at " + death.approvedAt() + " (" + result + ").");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Character ended, but its TrueDeath watermark could not be saved; "
                            + "startup reconciliation will retry safely", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<TrueDeathContext> readDurableDeaths() throws ReflectiveOperationException {
        Class<?> serviceType = Class.forName(SERVICE_CLASS, false, plugin.getClass().getClassLoader());
        Object service = plugin.getServer().getServicesManager().load((Class) serviceType);
        if (service == null) {
            throw new ReflectiveOperationException("PatriamUtils did not publish TrueDeathService");
        }
        Method allDeaths = serviceType.getMethod("allDeaths");
        Object result = allDeaths.invoke(service);
        if (!(result instanceof Collection<?> records)) {
            throw new ReflectiveOperationException("TrueDeathService.allDeaths returned no collection");
        }
        ArrayList<TrueDeathContext> converted = new ArrayList<>(records.size());
        for (Object record : records) {
            converted.add(TrueDeathRecordReader.fromRecord(record));
        }
        return List.copyOf(converted);
    }
}
