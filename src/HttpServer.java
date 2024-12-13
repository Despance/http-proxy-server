import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.ArrayList;

public class HttpServer implements Runnable {

    private boolean isRunning = false;
    private int portNumber;
    private PrintStream logStream;

    private ServerSocket serverSocket;
    private ArrayList<HttpClientHandler> clients = new ArrayList<HttpClientHandler>();

    public HttpServer(int port) {
        this.portNumber = port;
        this.logStream = System.out;
    }

    public HttpServer(int port, PrintStream logStream) {
        this.portNumber = port;
        this.logStream = logStream;
    }

    @Override
    public void run() {
        logStream.println("Starting server on port " + portNumber);

        try (ServerSocket newServer = new ServerSocket(portNumber)) {
            this.serverSocket = newServer;
            isRunning = true;

            while (isRunning) {
                HttpClientHandler client = new HttpClientHandler(serverSocket.accept(), logStream);
                Thread clientThread = new Thread(client);
                clientThread.start();
                clients.add(client);
            }
        } catch (Exception e) {
            logStream.println("Error: " + e.getMessage());
            isRunning = false;
        }

    }

    public void stop() {
        try {
            serverSocket.close();
            isRunning = false;
            logStream.println("Server stopped");
        } catch (Exception e) {
            logStream.println("Error: " + e.getMessage());
        }

        for (HttpClientHandler client : clients) {
            client.stop();
        }
    }

}
