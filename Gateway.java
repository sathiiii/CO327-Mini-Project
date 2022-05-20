import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Gateway implements Runnable {
    // Mutex lock object to synchronize the access to the gateway's static variables.
    private static final Object lock = new Object();
    // List of all the monitors that are connected to the gateway via TCP.
    // A ConnectedMonitor object is used to store the monitor object and the socket that is used to communicate with the monitor.
    private static List<ConnectedMonitor> connectedMonitors;
    // Static index of the next monitor to be communicated with via the TCP connections.
    private static int id = 0;

    // Gateway constructor takes a monitor object and creates a socket with its IP and Port number to communicate with the monitor.
    public Gateway(Monitor vitalMonitor) {
        if (vitalMonitor != null) {
            try {
                Socket clientSocket = new Socket(vitalMonitor.getIp(), vitalMonitor.getPort());
                // Acquire the mutex lock to synchronize the access to the list of connected monitors.
                synchronized (lock) {
                    // Add the socket and the monitor instances to the list of connected monitors.
                    connectedMonitors.add(new ConnectedMonitor(clientSocket, vitalMonitor));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            Socket clientSocket;
            // Acquire the mutex lock to coordinate the access to the list of connected monitors and the index of the next monitor to be communicated with.
            synchronized (lock) {
                // If there are no connected monitors yet, terminate the thread.
                if (connectedMonitors.size() == 0) return;
                // Get the socket instance of the next monitor to be communicated with.
                clientSocket = connectedMonitors.get(id % connectedMonitors.size()).socket;
                // Increment the index of the next monitor to be communicated with.
                id = (id + 1) % connectedMonitors.size();
            }
            // Read the data sent by the monitor.
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String message = in.readLine();
            if (message != null)
                System.out.println(message);
            // Read the ping message sent by the monitor.
            in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Initialize the static variables of the monitor discovery class.
        VitalMonitorDiscovery.init();
        System.out.printf("Listening to UDP broadcast traffic on %d...\n", VitalMonitorDiscovery.getBroadcastPort());
        // Initialize the list of connected monitors.
        connectedMonitors = new ArrayList<>();

        // Create a scheduled executor service to periodically scan the UDP broadcast port to discover new vital monitors.
        ScheduledExecutorService discoveryThreadExecutor = Executors.newScheduledThreadPool(1);
        // The discovery thread will run every 5 seconds.
        discoveryThreadExecutor.scheduleAtFixedRate(new VitalMonitorDiscovery(false), 0, 5, TimeUnit.SECONDS);
        // Create a scheduled executor service to run the garbage collector.
        ScheduledExecutorService tcpGarbageCollector = Executors.newScheduledThreadPool(1);
        // The garbage collector thread will run after 10 seconds of delay from the last time the garbage collector was run.
        // Garbage collector is used to remove the sockets of the disconnected monitors from the list of connected monitors.
        tcpGarbageCollector.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                // List to store the sockets of the disconnected monitors.
                List<ConnectedMonitor> toRemove = new ArrayList<>();
                // Acquire the mutex lock to synchronize the access to the list of connected monitors.
                synchronized (lock) {
                    // Iterate over the list of connected monitors.
                    for (ConnectedMonitor connectedMonitor : connectedMonitors) {
                        // Try sending ping messages to the monitor.
                        // Multiple messages are sent because of the buffering in the TCP connection.
                        try {
                            DataOutputStream out = new DataOutputStream(connectedMonitor.socket.getOutputStream());
                            out.writeByte(0);
                            out.writeByte(0);
                            out.writeByte(0);
                            out.flush();
                        }
                        // If the monitor is disconnected from the socket, an IOException is thrown.
                        catch (IOException e) {
                            toRemove.add(connectedMonitor);
                            // Add the disconnected monitor to the queue in the monitor discovery class.
                            VitalMonitorDiscovery.addDisconnectedMonitor(connectedMonitor.monitor);
                        }
                    }

                    if (toRemove.size() > 0)
                        connectedMonitors.removeAll(toRemove);
                }
            }
        }, 5, 10, TimeUnit.SECONDS);
        // Add a cleaner thread to the executor service that will clean the instances belong to the disconnected monitors in the monitor discovery class.
        // The cleaner thread also runs after 10 seconds of delay from the last time the cleaner thread was run.
        tcpGarbageCollector.scheduleWithFixedDelay(new VitalMonitorDiscovery(true), 5, 10, TimeUnit.SECONDS);

        // An array of threads that is used to run gateway tasks.
        Thread gatewayThreads[] = new Thread[10];
        // Index of the next gateway task to be run.
        int i = 0;

        while (true) {
            // Pop the next monitor from the queue of discovered monitors.
            Monitor monitor = VitalMonitorDiscovery.getNextMonitor();
            // If the current gateway thread is not null, try to wait for it to finish.
            if (gatewayThreads[i] != null)
                try {
                    gatewayThreads[i].join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            if (monitor != null)
                System.out.println("Connecting to " + monitor.getMonitorID());
            // Assign a new gateway task to the current gateway thread.
            gatewayThreads[i] = new Thread(new Gateway(monitor));
            // Start the current gateway thread.
            gatewayThreads[i].start();
            // Increment the index of the next gateway task to be run.
            i = (i + 1) % gatewayThreads.length;
        }
    }
}
