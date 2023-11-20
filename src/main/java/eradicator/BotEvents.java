package eradicator;

import arc.Events;

public class BotEvents {

    /**Generic bot kick event.**/
    public record BotKicked(String address){}

    public record BotFiltered(String address){}

    /**When a new IP connects**/
    public record NewBotIPBlocked(String address){}

    public static void init(){
        Events.on(NewBotIPBlocked.class, e->{
            if(!Config.logEvents.bool()) return;
            BotEradicator.log("New bot IP blocked: @. Total count is now @", e.address, BotEradicator.instance.botsBlockedTotal);
        });
    }
}
