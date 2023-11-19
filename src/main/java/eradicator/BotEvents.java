package eradicator;

public class BotEvents {

    /**Generic bot kick event.**/
    public record BotKicked(String address){}

    public record BotFiltered(String address){}

    /**When a new IP connects**/
    public record NewBotIPBlocked(String address){}
}
