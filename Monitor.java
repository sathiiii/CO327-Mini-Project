import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class Monitor implements Serializable, Runnable {
    private final InetAddress ip;
    private final String monitorID;
    private final int port;
    // Boolean variable to indicate whether the monitor is connected to the Gateway or not.
    private boolean isConnected;

    public InetAddress getIp() {
        return ip;
    }

    public String getMonitorID() {
        return monitorID;
    }

    public int getPort() {
        return port;
    }

    public String monitor_str() {
        return "Monitor ID: " + ip + " IP: " + monitorID + " PORT:" + port;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public Monitor(InetAddress ip, String monitorID, int port) {
        this.ip = ip;
        this.monitorID = monitorID;
        this.port = port;
        this.isConnected = false;
    }

    public void waitForGatewayConnection() {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        PrintWriter out = null;
        DataOutputStream os = null;
        int maxIncomingConnections = 10;

        try {
            serverSocket = new ServerSocket(this.port, maxIncomingConnections, this.ip);
            System.out.printf("Starting up on %s port %s\n", this.ip, this.port);

            // Wait for a connection
            System.out.println("Waiting for a connection");
            clientSocket = serverSocket.accept();
            // Connection established.
            isConnected = true;
            System.out.println("Connection from " + clientSocket);

            while (true) {
                // Receive the data in small chunks and retransmit it
                System.out.println("Sending data to the gateway");
                String message = "Hello from Vital Monitor: " + this.monitorID;
                // Send data
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println(message);
                TimeUnit.SECONDS.sleep(2);
                os = new DataOutputStream(clientSocket.getOutputStream());
                // Ping the gateway. This will fail if the monitor gets disconnected from the gateway.
                os.writeByte(0);
                os.flush();
            }
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            isConnected = false;
            try {
                // Close the socket connections.
                clientSocket.close();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Wait for a connection again.
            waitForGatewayConnection();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        this.waitForGatewayConnection();
    }
}
