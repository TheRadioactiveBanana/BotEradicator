package eradicator;

import arc.*;
import arc.struct.*;
import arc.util.Threads;

@SuppressWarnings("unused")
public class Config {
    public static final Seq<ConfigValue> all = new Seq<>();
    public static ConfigValue
        kickMessage = new ConfigValue("Kick Message", "The message that is sent when a player is kicked by BotEradicator the first time,",
            "[#f]You are suspected of being a Bot. If you join again, you will be blacklisted."),
        ratekeeperSpacing = new ConfigValue("Ratekeeper Spacing", "The time to wait for an IP to connect Ratekeeper Count - times, before its kicked.", 1000),
        ratekeeperAmount = new ConfigValue("Ratekeeper Amount", "The amount of connections allowed by an IP within Ratekeeper Spacing.", 4),
        disableServerConnectFilter = new ConfigValue("Disable Connect Filter", "Disable the connect filter if true, the connect filter is most effective anti-bot but it will block VPN users with no message. This cannot be changed. Disabling this leaves your server vulnerable.", false),
        discordLink = new ConfigValue("Discord Link", "A link to access your discord or any other platform really, in the Kick Message {DISCORD} is replaced with this.", ""),
        loadDelay = new ConfigValue("Loading Delay", "The delay to load filters. All plugins must be loaded when this plugin's filters are loaded. Otherwise other plugins filters will be overwritten. Make this larger if your game does not work properly.", 1),
        threadCount = new ConfigValue("Kicking Thread Count", "The amount of threads to kick connections with. Default is twice your CPU threads.", Runtime.getRuntime().availableProcessors() * 2, ()->{
            BotEradicator.instance.executor.shutdownNow().forEach(Runnable::run);
            BotEradicator.instance.executor = Threads.boundedExecutor("Bot Killer", Config.all.find(t->t.name.startsWith("Kicking")).num()); //mild ASB.
        });


    public static class ConfigValue {

        public final Object defaultValue;
        public final String name, key, description;
        private final Runnable changed;

        public ConfigValue(String name, String description, Object def){
            this(name, description, def, null, null);
        }

        public ConfigValue(String name, String description, Object def, String key){
            this(name, description, def, key, null);
        }

        public ConfigValue(String name, String description, Object def, Runnable changed){
            this(name, description, def, null, changed);
        }

        public ConfigValue(String name, String description, Object def, String key, Runnable changed){
            this.name = name;
            this.description = description;
            this.key = key == null ? "eradicator" + "-" + name.toLowerCase().replaceAll(" ", "-") : key;
            this.defaultValue = def;
            this.changed = changed == null ? () -> {} : changed;

            all.add(this);
        }

        public boolean isNum(){
            return defaultValue instanceof Integer;
        }

        public boolean isBool(){
            return defaultValue instanceof Boolean;
        }

        public boolean isString(){
            return defaultValue instanceof String;
        }

        public Object get(){
            return Core.settings.get(key, defaultValue);
        }

        public boolean bool(){
            return Core.settings.getBool(key, (Boolean)defaultValue);
        }

        public int num(){
            return Core.settings.getInt(key, (Integer)defaultValue);
        }

        public String string(){
            return Core.settings.getString(key, (String)defaultValue);
        }

        public String shortName(){
            return name.toLowerCase().replaceAll(" ", "");
        }

        public void set(Object value){
            Core.settings.put(key, value);
            changed.run();
        }
    }
}
