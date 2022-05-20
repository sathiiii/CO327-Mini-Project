import java.net.Socket;

public class ConnectedMonitor {
    public Socket socket;
    public Monitor monitor;

    public ConnectedMonitor(Socket socket, Monitor monitor) {
        this.socket = socket;
        this.monitor = monitor;
    }
}
