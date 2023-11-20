package eradicator;

import arc.*;
import arc.func.*;
import arc.net.*;
import arc.struct.*;
import arc.util.*;

import mindustry.core.*;
import mindustry.gen.*;
import mindustry.net.*;


import mindustry.mod.*;
import org.json.simple.*;
import org.json.simple.parser.*;

import java.io.*;
import java.net.*;

import java.util.concurrent.ExecutorService;

import static arc.util.Strings.*;
import static mindustry.Vars.*;

@SuppressWarnings("unused") //it is used inside mindustry.
public class BotEradicator extends Plugin implements ApplicationListener {

    public static BotEradicator instance;
    private final ObjectSet<String> filteredIps = new ObjectSet<>();
    private final Seq<Subnet> bannedSubnets = new Seq<>();
    public int botsBlocked = 0;
    public int botsBlockedTotal = Core.settings.getInt("eradicator-total-count", 0);
    private final ObjectIntMap<String> ipCounts = new ObjectIntMap<>();
    private final ObjectMap<String, Ratekeeper> ipRates = new ObjectMap<>();
    protected ExecutorService executor = Threads.boundedExecutor("Bot Killer", Config.threadCount.num());

    public BotEradicator() {
        instance = this;
        //fetch as soon as possible.
        Http.get("https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/datacenter/ipv4.txt").timeout(0)
                .error(error ->{

                    fetchFromFiles();

                    logError("Failed to fetch online datacenter subnets, fetched " + bannedSubnets.size + " backups instead.", error);
                })
                .submit(response -> {
                    var result = response.getResultAsString().split("\n");

                    for (var address : result) bannedSubnets.add(parseSubnet(address));

                    fetchFromFiles();

                    log("Fetched @ datacenter subnets.", bannedSubnets.size);
                });
    }

    private boolean init = false;
    @Override
    public void init() {
        BotEvents.init();

        if (init) return; //WHY YOU RUN ON MOD AND APPLICATION LISTENER AAAAAA
        init = true;
        Time.runTask(Config.loadDelay.num(),()-> //Run delayed so that whatever other mods can place their filters first.
                Core.app.post(this::loadFilters));
    }

    /**Place the plugin's filter BEFORE every other filter. This does not interfere with existing filters.**/
    private void loadFilters(){
        var server = Reflect.<Server>get(Reflect.<ArcNetProvider>get(net, "provider"), "server");

        var filter = Reflect.<Server.ServerConnectFilter>get(server, "connectFilter");

        server.setConnectFilter((t) -> {
            if (filteredIps.contains(t)) {
                ipCounts.increment(t);
                botsBlocked++;
                botsBlockedTotal++;
                Events.fire(new BotEvents.BotFiltered(t));
                return false;
            }

            return filter == null || filter.accept(t);
        });

        var s = Reflect.<ObjectMap<Class<?>, Cons2<NetConnection, Object>>>get(net, "serverListeners");

        var connectPacketListener = s.get(Packets.ConnectPacket.class);

        net.handleServer(Packets.ConnectPacket.class, (t, e)->{
            if(isBot(t.address)){
                botsBlocked++;
                ipCounts.increment(t.address);
                executor.submit(()->kickConnectionWithoutLogging(t, Config.kickMessage.string()));
                Events.fire(new BotEvents.BotKicked(t.address));
                return;
            }

            connectPacketListener.get(t, e);
        });

        var connectConfirmListener = s.get(ConnectConfirmCallPacket.class);

        net.handleServer(ConnectConfirmCallPacket.class, (t, e)->{
            if(isBot(t.address)){
                botsBlocked++;
                botsBlockedTotal++;
                ipCounts.increment(t.address);
                executor.submit(()->kickConnectionWithoutLogging(t, Config.kickMessage.string()));
                Events.fire(new BotEvents.BotKicked(t.address));
                return;
            }

            if(connectConfirmListener != null) connectConfirmListener.get(t, e);
            else NetServer.connectConfirm(t.player);
        });

        var connectListener = s.get(Packets.Connect.class);

        net.handleServer(Packets.Connect.class, (t, e)->{
            if(isBot(t.address)){
                botsBlocked++;
                botsBlockedTotal++;
                ipCounts.increment(t.address);
                Events.fire(new BotEvents.BotKicked(t.address));
                executor.submit(()->kickConnectionWithoutLogging(t, Config.kickMessage.string()));
                return;
            }

            connectListener.get(t, e);
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("botstatus", "Get a status of the plugin.", (t)->{
            log("@ Banned Subnets are currently loaded in memory.", bannedSubnets.size);
            log("@ IPs are currently blocked.", filteredIps.size > 0 ? filteredIps.size : "No");
            log("@ Bots were blocked on this session, and @ bots were blocked during the lifetime of the plugin.", botsBlocked, botsBlockedTotal);
            for (var s : filteredIps) log("@@ - @ Bots Blocked.", getFancyChar(s, filteredIps), s, ipCounts.get(s));
            log("Plugin by TheRadioactiveBanana#0545. (@TheRadioactiveBanana) | Report any bugs to me, Please. I want to keep this plugin as good as possible.");
        });

        handler.register("botconfig", "[setting] [value...]", "Configure bot eradicator.", (args)->{
            if(args.length == 0){
                for (var c : Config.all){
                    var fancyChar = getFancyChar(c, Config.all);
                    log("@@ - @ Current value: @", fancyChar, c.name, c.description, c.get());
                }
            }else if(args.length == 1){
                var c = Config.all.find(t->t.name.startsWith(args[0]));
                if(c == null) logError("Couldn't find that config.");
                else log("@ - @ Current value @.", c.name, c.description, c.get());
            }else{
                String key = args[0];
                String value = args[1];
                for(var c : Config.all){
                    if(!c.shortName().startsWith(key)) return;
                    if(value.equals("true") || value.equals("false") && c.isBool()){
                        c.set(Boolean.parseBoolean(value));
                        log("@ set to @.", c.name, value);
                        return;
                    }else if(canParseInt(value) && c.isNum()){
                        c.set(parseInt(value));
                        log("@ set to @.", c.name, value);
                        return;
                    }else{
                        c.set(value);
                        log("@ set to @.", c.name, value);
                        return;
                    }
                }
            }
        });
    }

    @Override
    public void dispose() {
        executor.shutdownNow().forEach(Runnable::run);
        Core.settings.put("eradicator-total-count", botsBlocked);
        log("@ Bots blocked on this session.", botsBlocked);
    }

    @Override
    public void update(){
        Core.settings.put("eradicator-total-count", botsBlocked); //keep up to date
    }

    private boolean isBot(String ip){
        if(!ipRates.containsKey(ip)) ipRates.put(ip, new Ratekeeper());

        if(!ipRates.get(ip).allow(Config.ratekeeperSpacing.num(), Config.ratekeeperAmount.num())) return true;

        if(Groups.player.count(t->t.ip().equals(ip)) > Config.duplicateConnectionLimit.num()) return true;

        if(Groups.player.count(t->t.ip().equals(ip)) > 1){
            filteredIps.add(ip);
            Timer.schedule(()-> filteredIps.remove(ip), 10);
            return true;
        }
        try {
            int i = parseSubnetFromPlayer(ip).ip;

            for (var subnet : bannedSubnets) if ((i & subnet.mask) == subnet.ip){
                if(!Config.disableServerConnectFilter.bool()) filteredIps.add(ip);
                if(!ipCounts.containsKey(ip)){
                    ipCounts.put(ip, 1);
                    Events.fire(new BotEvents.NewBotIPBlocked(ip));
                }
                return true;
            }

            return false;
        }catch (Exception e){
            logError("How did we get here?", e);
            return true;
        }
    }

    public record Subnet(int ip, int mask){}

    /**Taken from <a href="https://github.com/zxzADIzxz/Useful-Stuffs">...</a> mostly.**/
    public static Subnet parseSubnet(String address) throws UnknownHostException {
        var parts = address.split("/");

        if (parts.length > 2) throw new IllegalArgumentException("Invalid IP address: " + address);

        int ip = 0;
        int mask = -1;

        for (var token : InetAddress.getByName(parts[0]).getAddress()) ip = (ip << 8) + (token & 0xFF);

        if (parts.length == 2) {
            mask = Integer.parseInt(parts[1]);

            if (mask > 32 || mask < 0) throw new IllegalArgumentException("Invalid IP address: " + address);

            mask = 0xFFFFFFFF << (32 - mask);
        }

        return new Subnet(ip, mask);
    }
    //Most likely faster for just player ip.
    public static Subnet parseSubnetFromPlayer(String address) throws UnknownHostException {

        int ip = 0;

        for (var token : InetAddress.getByName(address).getAddress()) ip = (ip << 8) + (token & 0xFF);

        return new Subnet(ip, -1);
    }

    private void fetchFromFiles(){
        logDebug("Fetching Subnets from files...");
        Time.mark();
        for (var i : mods.getMod(BotEradicator.class).root.child("blacklist").list())
            bannedSubnets.addAll(fetchFromFile(i.name()));
        logDebug("Fetched Subnets from files in @ ms.", Time.elapsed());
    }

    /**Now you can Kick a connection with a reason but without logging it in the console. No more spam.**/
    private static void kickConnectionWithoutLogging(NetConnection connection, String reason){
        KickCallPacket packet = new KickCallPacket();
        packet.reason = reason;
        connection.send(packet, true);
        connection.close();
    }

    @SuppressWarnings("unchecked")
    private static Seq<Subnet> fetchFromFile(String fileName) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject object = (JSONObject) parser.parse((getFileReader(fileName)));
            return new Seq<>(){{
                for(var s : Seq.<String>with((JSONArray) object.get("addresses"))) add(parseSubnet((String) s));
            }};
        }catch (Throwable e){
            logError("Error while fetching addresses from " + fileName, e);
            throw new RuntimeException(e);
        }
    }

    private static InputStreamReader getFileReader(String name){
        if(name == null) throw new IllegalArgumentException("Name can not be null!");

        var res = BotEradicator.class.getResourceAsStream("/blacklist/" + name);
        if(res == null) throw new RuntimeException("Couldn't find a file with name " + name);

        return new InputStreamReader(res);
    }

    //Arc logger is mild sin.
    public static void log(String s){
        Log.info("&lk[&yBOTKILLER&lk]:&fr " + s);
    }

    public static void log(String s, Object... args){
        log(Strings.format(s, args));
    }

    public static void logDebug(String s){
        if(!Administration.Config.debug.bool()) return;
        Log.debug("&lk[&lcBOTKILLER&lk]:&fr " + s);
    }

    public static void logDebug(String s, Object... args){
        if(!Administration.Config.debug.bool()) return;
        logDebug(Strings.format(s, args));
    }

    public static void logError(String s, Throwable e){
        logError("&lk[&BOTKILLER&lk]:&fr " + s);
        Log.err(e);
    }

    public static void logError(String s){
        Log.err("[&rBOTERROR&lk]:&fr " + s);
    }

    /**Literally just so a couple list commands look good.**/
    private static <T> String getFancyChar(T object, Iterable<T> iterable){
        var s = Seq.with(iterable);
        return Config.enhance.bool() ? (object == s.get(s.size - 1) ? "└" : "├") : "  ";
    }
}