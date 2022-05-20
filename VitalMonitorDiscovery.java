import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class VitalMonitorDiscovery implements Runnable {
    public static final int BROADCAST_PORT = 6000;
    // Socket to listen to all the UDP traffic that's coming to the BROADCAST_PORT from vital monitors.
    private static DatagramSocket socket;
    // Queue to store the newly discovered vital monitors.
    private static Queue<Monitor> discoveredMonitors;
    // Queue to store the vital monitors that are disconnected.
    private static Queue<Monitor> disconnectedMonitors;
    // List to store the IDs of the newly discovered vital monitors.
    private static List<String> discoveredMonitorIDs;
    // Boolean flag to indicate whether it's a cleaning thread or a discovery thread.
    private boolean cleaner;

    public VitalMonitorDiscovery(boolean cleaner) {
        this.cleaner = cleaner;
    }

    // Initialize the static variables.
    public static void init() {
        try {
            // Create a socket to listen to all the UDP traffic that's coming to the BROADCAST_PORT from vital monitors.
            socket = new DatagramSocket(BROADCAST_PORT, InetAddress.getByName("0.0.0.0"));
            socket.setBroadcast(true);
            discoveredMonitors = new LinkedList<>();
            disconnectedMonitors = new LinkedList<>();
            discoveredMonitorIDs = new ArrayList<>();
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }

    // Method with mutex synchronization to pop the next monitor from the queue of discovered monitors.
    public static synchronized Monitor getNextMonitor() {
        return discoveredMonitors.poll();
    }

    // Method with mutex synchronization to add a monitor to the queue of disconnected monitors.
    public static synchronized void addDisconnectedMonitor(Monitor monitor) {
        disconnectedMonitors.add(monitor);
    }

    public static int getBroadcastPort() {
        return BROADCAST_PORT;
    }

    // Method to deserialize a monitor object from the byte array.
    private Monitor deserializeByteArray(byte[] data) {
        Monitor res = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);
            res = (Monitor) ois.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return res;
    }

    @Override
    public void run() {
        // Runs if it's a discovery thread.
        if (!cleaner) {
            while (true) {
                byte[] buff = new byte[10000];
                DatagramPacket packet = new DatagramPacket(buff, buff.length);
                try {
                    // Wait for the UDP traffic from vital monitors.
                    socket.receive(packet);
                    Monitor monitor = deserializeByteArray(packet.getData());
                    boolean isNewMonitor = true;
                    // Acquire a mutex lock on the class itself to coordinate the access to the static variables discoveredMonitors and discoveredMonitorIDs.
                    synchronized (VitalMonitorDiscovery.class) {
                        // Check if the monitor is already in the list of discovered monitors.
                        for (String id : discoveredMonitorIDs)
                            if (id.equals(monitor.getMonitorID())) {
                                isNewMonitor = false;
                                break;
                            }
                        // If the monitor is new, add it to the queue of discovered monitors and add its ID to the list of discovered monitor IDs.
                        if (isNewMonitor) {
                                discoveredMonitorIDs.add(monitor.getMonitorID());
                                discoveredMonitors.add(monitor);
                                System.out.println("Discovered new vital monitor: " + monitor.getMonitorID());
                            }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // Runs if it's a cleaner thread.
        else {
            // Acquire a mutex lock on the class itself to coordinate the access to the static variables discoveredMonitors and disconnectedMonitors.
            synchronized (VitalMonitorDiscovery.class) {
                // Remove the monitors that are disconnected using the queue of disconnected monitors.
                while (!disconnectedMonitors.isEmpty()) {
                    Monitor disconnectedMonitor = disconnectedMonitors.poll();
                    discoveredMonitors.remove(disconnectedMonitor);
                    discoveredMonitorIDs.remove(disconnectedMonitor.getMonitorID());
                    System.out.println("Monitor disconnected: " + disconnectedMonitor.getMonitorID());
                }
            }
        }
    }   
}
