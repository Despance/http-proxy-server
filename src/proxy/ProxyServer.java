package proxy;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ProxyServer implements Runnable {

    private boolean isRunning = false;
    private int portNumber;
    private PrintStream logStream;

    private ServerSocket serverSocket;
    private ArrayList<ProxyClientHandler> clients = new ArrayList<ProxyClientHandler>();
    private Map<String, File> cache = new HashMap<>();
    private int cacheSize;
    private String cacheDirectory = "src/cache";

    public ProxyServer(int port, PrintStream logStream, int cacheSize) {
        this.portNumber = port;
        this.logStream = logStream;
        this.cacheSize = cacheSize;
        ensureCacheDirectory();
        loadExistingCache();
    }

    private void ensureCacheDirectory() {
        File dir = new File(cacheDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            logStream.println("Cache directory created at: " + cacheDirectory);
        }
    }

    private void loadExistingCache() {
        File dir = new File(cacheDirectory);
        File[] files = dir.listFiles((d, name) -> name.startsWith("cache_") && name.endsWith(".txt"));
        if (files != null) {
            for (File file : files) {
                String key = file.getName().replace("cache_", "").replace(".txt", "");
                cache.put(key, file);
                logStream.println("Loaded cached file: " + file.getName());
            }
        }
        logStream.println("Cache initialized with " + cache.size() + " files.");
    }

    @Override
    public void run() {
        logStream.println("Starting Proxy server on port " + portNumber);

        try (ServerSocket newServer = new ServerSocket(portNumber)) {
            this.serverSocket = newServer;
            isRunning = true;

            while (isRunning) {
                ProxyClientHandler client = new ProxyClientHandler(serverSocket.accept(), logStream, cache, cacheSize, cacheDirectory);
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

        for (ProxyClientHandler client : clients) {
            client.stop();
        }
    }

}