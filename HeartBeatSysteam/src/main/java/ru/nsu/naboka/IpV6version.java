package ru.nsu.naboka;

import jdk.net.ExtendedSocketOptions;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;



public class IpV6version extends Application{

    private final ProtocolFamily family = StandardProtocolFamily.INET;
    //нужен для подключения к multicast group при использовании ipv6
    private NetworkInterface nInterface = null;

    private static final int BUFFER_SIZE = 61*4;

    IpV6version(InetSocketAddress groupInetSocketAddress){
        this.groupInetSocketAddress = groupInetSocketAddress;
    }

    void getMulticastConnection() throws IOException {
        try{
            DatagramChannel datagramChannel = DatagramChannel.open(family);
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()){
                NetworkInterface networkInterface = interfaces.nextElement();
                if(networkInterface.isLoopback() || !networkInterface.isUp() ||
                        !networkInterface.supportsMulticast() ){
                    continue;
                }

                Enumeration<InetAddress> interfaceAddresses = networkInterface.getInetAddresses();
                while(interfaceAddresses.hasMoreElements()){
                    InetAddress address = interfaceAddresses.nextElement();
                    if(address instanceof Inet6Address){
                        nInterface = networkInterface;
                        break;
                    }
                }
                if(nInterface != null){
                    break;
                }
            }

            if(nInterface == null){
                throw new RuntimeException("No interface that works with the fourth version of IP");
            }

            datagramChannel.bind(new InetSocketAddress(groupInetSocketAddress.getPort()));

            //опция для захвата единственного порта на отправку, действует только на отправку(send)
            //на чтение действует интерфейс, указанный в join
            datagramChannel.setOption(StandardSocketOptions.IP_MULTICAST_IF, nInterface);

            //необходимо, потому что по умолчанию ос не дает двум сокетам подключится к одному порту
            //в нашем случае это неприемлимо, потому что в мультикаст группе обязательно будет приходить
            // сразу несколько сообщений
            datagramChannel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
            datagramChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            datagramChannel.join(groupInetSocketAddress.getAddress(), nInterface);

            startSend(datagramChannel);
            startReceive(datagramChannel);

        }
        catch(IOException e){
            throw e;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    void startSend(DatagramChannel datagramChannel) throws InterruptedException {
        Thread sendThread = new Thread(() -> {
            while(true){

                String message = "App with uuid: " + uuid + " is alive\n";
                ByteBuffer byteBuffer = ByteBuffer.wrap(message.getBytes());
                try {
                    datagramChannel.send(byteBuffer, groupInetSocketAddress);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        sendThread.start();
    }

    void startReceive(DatagramChannel datagramChannel) throws InterruptedException {
        Thread recieveThread = new Thread(() -> {
            while(true){
                ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                try {
                    SocketAddress sender = datagramChannel.receive(byteBuffer);

                    byteBuffer.flip();
                    byte[] data = new byte[byteBuffer.remaining()];
                    byteBuffer.get(data);
                    String message = new String(data, StandardCharsets.UTF_8);
                    System.out.println(message);


                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        recieveThread.start();

    }


}
