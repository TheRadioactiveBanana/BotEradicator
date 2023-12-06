package eradicator;

import arc.Events;

import static eradicator.Logging.*;

public class BotEvents {

    /**Generic bot kick event.**/
    public record BotKicked(String address){}

    /**When a bot is kicked via the ServerConnectFilter**/
    public record BotFiltered(String address){}

    /**When a new IP connects**/
    public record NewBotIPBlocked(String address){}

    /**When a Connection has been rate limited**/
    public record ConnectionRateLimited(String address){}

    public static void init(){
        Events.on(NewBotIPBlocked.class, e->{
            if(Config.logEvents.bool()) info("New bot IP blocked: @. Total count is now @", e.address, BotEradicator.botsBlockedTotal);
        });
    }
}

