package dansplugins.rpsystem.welcome;

import org.bukkit.plugin.Plugin;

/** Optional linkage boundary: normal startup never resolves a Welcome type when it is absent. */
public final class WelcomeFeatureIntegration {
    private WelcomeFeatureIntegration() { }
    public static AutoCloseable start(Plugin owner) {
        Plugin welcome = owner.getServer().getPluginManager().getPlugin("PatriamWelcome");
        if (welcome == null || !welcome.isEnabled()) return () -> { };
        try {
            ClassLoader loader = WelcomeFeatureIntegration.class.getClassLoader();
            for (String type : new String[]{"HubFeatureProvider", "HubFeatureView", "HubFeature"})
                Class.forName("com.github.exophobias.patriamwelcome.api." + type, false, loader);
            return (AutoCloseable) Class.forName("dansplugins.rpsystem.welcome.WelcomeFeatureProvider", true, loader)
                    .getMethod("register", Plugin.class).invoke(null, owner);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            owner.getLogger().warning("Welcome feature pages are unavailable; owner commands remain available.");
            return () -> { };
        }
    }
    public static void close(Plugin owner, AutoCloseable registration) {
        try { registration.close(); }
        catch (Exception | LinkageError unavailable) { owner.getLogger().warning("Could not withdraw Welcome feature page."); }
    }
}
