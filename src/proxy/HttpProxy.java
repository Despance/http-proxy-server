package proxy;

import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.ArrayList;

public class HttpProxy {
    
    private boolean isRunning = false;
    private int portNumber;
    private PrintStream logStream;

    private ServerSocket serverSocket;
    private ArrayList<HttpProxyHandler> clients = new ArrayList<HttpProxyHandler>();

    public HttpProxy(int port) {
        this.portNumber = port;
        this.logStream = System.out;
    }

    public HttpProxy(int port, PrintStream logStream) {
        this.portNumber = port;
        this.logStream = logStream;
    }

    public void run() {
        logStream.println("Starting proxy on port " + portNumber);

        try (ServerSocket newServer = new ServerSocket(portNumber)) {
            this.serverSocket = newServer;
            isRunning = true;

            while (isRunning) {
                HttpProxyHandler client = new HttpProxyHandler(serverSocket.accept(), logStream);
                Thread clientThread = new Thread(client);
                clientThread.start();
                clients.add(client);
            }
        } catch (Exception e) {
            logStream.println("Error: " + e.getMessage());
            isRunning = false;
        }

    }


}
