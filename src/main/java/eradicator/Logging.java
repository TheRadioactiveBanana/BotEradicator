package eradicator;

import arc.util.Log;

import java.io.*;

@SuppressWarnings("unused")
public class Logging {

    public static void info(String text){
        Log.info("&lk[&lyBOT&lk]: &fr" + text);
    }

    public static void info(Object o){
        Log.info("&lk[&lyBOT&lk]: &fr" + o);
    }

    public static void info(String text, Object... args){
        info(Log.format(text, args));
    }

    public static void err(String text){
        Log.err("&lk[&lyBOT&lk]: &r" + text);
    }

    public static void err(Throwable e){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        err(e.getMessage() + ": \n" + sw);
    }

    public static void err(String text, Throwable e){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        err(text + ": \n" + sw);
    }

    public static void debug(String text){
        Log.debug("&lk[&lyBOT&lk]: &fr" + text);
    }

    public static void debug(String text, Object... args){
        debug(Log.format(text, args));
    }
}
