package eradicator;

import java.net.InetAddress;

import static eradicator.Logging.*;

public class Subnet {

    public final int ip;
    public final int mask;
    public final String origin;

    public Subnet(String origin){
        try {
            this.origin = origin;
            var parts = origin.split("/");

            if (parts.length > 2) throw new IllegalArgumentException("Invalid IP address: " + origin);

            int ip = 0;
            int mask = -1;

            for (var token : InetAddress.getByName(parts[0]).getAddress()) ip = (ip << 8) + (token & 0xFF);

            if (parts.length == 2) {
                mask = Integer.parseInt(parts[1]);

                if (mask > 32 || mask < 0) throw new IllegalArgumentException("Invalid IP address: " + origin);

                mask = 0xFFFFFFFF << (32 - mask);
            }

            this.ip = ip;
            this.mask = mask;
        }catch(Throwable e){
            throw new RuntimeException("Error while parsing subnet", e);
        }
    }

    public Subnet(String origin, int ip, int mask){
        this.origin = origin;
        this.ip = ip;
        this.mask = mask;
    }

    public Subnet(int ip, int mask){
        this.origin = null;
        this.ip = ip;
        this.mask = mask;
    }

    public static Subnet parseUnmasked(String address){
        try {
            int ip = 0;

            for (var token : InetAddress.getByName(address).getAddress()) ip = (ip << 8) + (token & 0xFF);

            return new Subnet(address, ip, -1);
        }catch(Throwable e){
            err(e);
            throw new RuntimeException("Error while parsing subnet", e);
        }
    }
}
