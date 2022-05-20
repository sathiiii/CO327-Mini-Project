import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

public class VitalMonitor {
    public static void main(String[] args) {
        // Validate the number of arguments.
        if (args.length != 2) {
            System.out.println("Usage: java VitalMonitor <monitorID> <port>");
            System.exit(1);
        }

        String monitorId = args[0];
        int port = Integer.parseInt(args[1]);
        InetAddress ipAddress = null;

        // Get the IP address of the running computer
        ipAddress = getIPAddressOfComputer();

        // Create a new vital monitor
        Monitor monitor = new Monitor(ipAddress, monitorId, port);
        byte[] monitorInBytes = convertToByteArray(monitor);

        // Create a broadcast socket to publish monitor identity information
        int BROADCAST_PORT = 6000;
        DatagramSocket broadcastSocket = createBroadcastSocket();

        // Create a thread to wait for incoming TCP connections from the Gateway
        Thread gatewayConnectionWaitingThread = new Thread(monitor);
        gatewayConnectionWaitingThread.start();
        customDelayInSeconds(2);

        while (true) {
            // Broadcast monitor identity information only if the monitor is not connected to the Gateway.
            if (!monitor.isConnected()) broadcastMonitorIdentity(monitorInBytes, ipAddress, BROADCAST_PORT, broadcastSocket);
            customDelayInSeconds(1);
        }
    }

    private static InetAddress getIPAddressOfComputer() {
        InetAddress ip_address = null;
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.connect(InetAddress.getByName("8.8.8.8"), 80); // Send request to name server
            ip_address = InetAddress.getByName(socket.getLocalAddress().getHostAddress());
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ip_address;
    }

    private static byte[] convertToByteArray(Monitor monitor) {
        byte[] data = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = null;
            oos = new ObjectOutputStream(bos);
            oos.writeObject(monitor);
            data = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    private static DatagramSocket createBroadcastSocket() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return socket;
    }

    private static void customDelayInSeconds(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void broadcastMonitorIdentity(byte[] data, InetAddress ipAddress, int broadcastPort, DatagramSocket broadcastSocket) {
        DatagramPacket packet = new DatagramPacket(data, data.length, ipAddress, broadcastPort);
        try {
            broadcastSocket.send(packet);
            System.out.println("Vital monitor broadcasted!");
            customDelayInSeconds(2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
