package eradicator;

import arc.*;
import arc.func.*;
import arc.net.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.net.*;

import java.util.concurrent.*;

import static arc.util.Strings.*;
import static eradicator.Logging.*;
import static mindustry.Vars.*;

public class BotEradicator extends Plugin implements ApplicationListener {

    public static final ObjectSet<Subnet> bannedSubnets = new ObjectSet<>();
    public static final ObjectIntMap<String> botsBlockedPerAddress = new ObjectIntMap<>();
    public static final ObjectSet<String> alreadyBlockedIps = new ObjectSet<>();
    public static final ObjectMap<String, Ratekeeper> ipRatekeepers = new ObjectMap<>();
    public static BotEradicator instance;
    public static ExecutorService executor = Threads.boundedExecutor("bot-eradicator",Config.threadCount.num());
    public static int botsBlocked;
    public static int botsBlockedTotal;

    public BotEradicator(){
        Core.app.addListener(instance = this);

        Threads.daemon(()-> {
            Http.post("https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/datacenter/ipv4.txt")
                    .timeout(30)
                    .error(Logging::err)
                    .block(t -> {
                        for (var s : t.getResultAsString().split("\n")) bannedSubnets.add(new Subnet(s));
                        debug("Fetched @ addresses from online datacenter list.", t.getResultAsString().split("\n").length);
                    });
            Http.post("https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/vpn/ipv4.txt")
                    .timeout(30)
                    .error(Logging::err)
                    .block(t -> {
                        for (var s : t.getResultAsString().split("\n")) bannedSubnets.add(new Subnet(s));
                    });
            fetchFromFiles();
            info("Fetched @ banned subnets.", bannedSubnets.size);
        });
    }

    private boolean alreadyInit = false;
    @Override
    public void init(){
        if(alreadyInit) return;
        alreadyInit = true; //this is very gay, and I am much too lazy to fix it.

        botsBlocked = Core.settings.getInt("eradicator-bots-blocked", 0);

        BotEvents.init();

        Timer.schedule(this::loadFilters, Config.loadDelay.num());
    }

    private void loadFilters() {
        var server = Reflect.<Server>get(Reflect.<ArcNetProvider>get(net, "provider"), "server");

        var filter = Reflect.<Server.ServerConnectFilter>get(server, "connectFilter");

        var serverListeners = Reflect.<ObjectMap<Class<?>, Cons2<NetConnection, Object>>>get(net, "serverListeners");

        server.setConnectFilter((t) -> {
            if (alreadyBlockedIps.contains(t)) {
                botsBlockedPerAddress.increment(t);
                botsBlocked++;
                botsBlockedTotal++;
                Events.fire(new BotEvents.BotFiltered(t));
                return false;
            }

            return filter == null || filter.accept(t);
        });

        var connectListener = serverListeners.get(Packets.Connect.class);

        net.handleServer(Packets.Connect.class, (t, e)->{
            if(isBot(t.address)){
                botsBlocked++;
                botsBlockedTotal++;
                botsBlockedPerAddress.increment(t.address);
                Events.fire(new BotEvents.BotKicked(t.address));
                executor.submit(()->kickConnectionWithoutLogging(t, Config.kickMessage.string()));
                return;
            }

            connectListener.get(t, e);
        });

        var connectPacketListener = serverListeners.get(Packets.ConnectPacket.class);

        net.handleServer(Packets.ConnectPacket.class, (t, e)->{
            if(isBot(t.address)){
                botsBlocked++;
                botsBlockedPerAddress.increment(t.address);
                executor.submit(()->kickConnectionWithoutLogging(t, Config.kickMessage.string()));
                Events.fire(new BotEvents.BotKicked(t.address));
                return;
            }

            connectPacketListener.get(t, e);
        });

        var connectConfirmListener = serverListeners.get(ConnectConfirmCallPacket.class);

        net.handleServer(ConnectConfirmCallPacket.class, (t, e)->{
            if(isBot(t.address)){
                botsBlocked++;
                botsBlockedTotal++;
                botsBlockedPerAddress.increment(t.address);
                executor.submit(()->kickConnectionWithoutLogging(t, Config.kickMessage.string()));
                Events.fire(new BotEvents.BotKicked(t.address));
                return;
            }

            if(connectConfirmListener != null) connectConfirmListener.get(t, e);
            else NetServer.connectConfirm(t.player);
        });
    }

    @Override
    public void update(){
        Core.settings.put("eradicator-bots-blocked", botsBlocked);
        Core.settings.put("eradicator-bots-blocked-total", botsBlockedTotal);
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("botstatus", "View the status of the plugin, how many bots blocked, etc", (args)->{
            info("Bots blocked on this session: @", botsBlocked);
            info("Bots blocked always: @", botsBlockedTotal);
            for(var s : botsBlockedPerAddress.entries()) info("Bots blocked for @: @", s.key, s.value);
            info("Plugin by TheRadioactiveBanana#0545 (@theradioactivebanana) On discord. ");
        });

        handler.register("botconfig", "[config] [value]","Configure the plugin.", (args)->{
            switch (args.length){
                case 0 -> {
                    for (var i : Config.values()) info("@ - @ Current value: @", i.name, i.description, i.get());
                }

                case 1 -> {
                    var c = Config.valuesAsSeq().find(t->t.name.toLowerCase().startsWith(args[0]));
                    if(c == null) err("Couldn't find that config.");
                    else info("@ - @ Current value: @", c.name, c.description, c.get());
                }

                default -> {
                    var c = Config.valuesAsSeq().find(conf -> conf.name.equalsIgnoreCase(args[0]));

                    if(c != null){
                        if(args[1].equals("default")) c.set(c.defaultValue);
                        else if(c.isBool()) c.set(Boolean.parseBoolean(args[1]));
                        else if(c.isNum() && canParseInt(args[1])) c.set(Strings.parseInt(args[1]));
                        else if(c.isString()) c.set(args[1].replace("\\n", "\n"));
                        else err("An error has occurred while setting that config.");

                        info("@ set to @.", c.name, c.get());
                        Core.settings.forceSave();
                    }else{
                        Log.err("Unknown config: '@'. Run the command with no arguments to get a list of valid configs.", args[0]);
                    }
                }
            }
        });
    }

    private void kickConnectionWithoutLogging(NetConnection con, String r) {
        con.send(new KickCallPacket(){{
            this.reason = r;
        }}, true);
        con.close();
    }

    private boolean isBot(String s){
        if(!ipRatekeepers.containsKey(s)) ipRatekeepers.put(s, new Ratekeeper());
        if(!ipRatekeepers.get(s).allow(Config.ratekeeperSpacing.num(), Config.ratekeeperAmount.num())) {
            bannedSubnets.add(Subnet.parseUnmasked(s));
            Events.fire(new BotEvents.ConnectionRateLimited(s));
            Timer.schedule(()->bannedSubnets.remove(Subnet.parseUnmasked(s)), Config.ratekeeperExceedUnbanTime.num());
        }

        return bannedSubnets.contains(Subnet.parseUnmasked(s)) ;
    }

    private void fetchFromFiles() {
        for(var s : mods.getMod(BotEradicator.class).root.child("blacklist").child("blacklist.txt").readString().split("\n")){
            int ip = parseInt(s.split("\\|")[0]), mask = parseInt(s.split("\\|")[1]);
            bannedSubnets.add(new Subnet(ip, mask));
        }
    }
}
