package ru.nsu.naboka;

import java.io.IOException;
import java.net.*;

public class Main {
    public static void main(String[] args) {
        try {
            InetAddress groupAddr = InetAddress.getByName(args[0]);

            int port = Integer.parseInt(args[1]);

            InetSocketAddress groupInetSocketAddress = new InetSocketAddress(groupAddr, port);

            if(!groupAddr.isMulticastAddress()){
                throw new RuntimeException("address is not a multicast");
            }

            if (groupAddr instanceof Inet4Address){
                IpV4version ipV4version = new IpV4version(groupInetSocketAddress);
                ipV4version.getMulticastConnection();
            }
            if (groupAddr instanceof Inet6Address){
                IpV6version ipV6version = new IpV6version(groupInetSocketAddress);
                ipV6version.getMulticastConnection();
            }
            else {
                throw new RuntimeException("Cannot resolve address version");
            }
        }
        catch(UnknownHostException e){
            throw new RuntimeException("Cannot resolve group address" + " " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
