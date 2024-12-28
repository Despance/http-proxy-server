import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import proxy.ProxyServer;

public class App {

    private static int portNumber = 8080;
    private static PrintStream logStream;
    private static boolean logToTextFile = true;

    private static int cacheSize = 10;

    public static void main(String[] args) {

        if (logToTextFile) {
            File logFile = new File("log.txt");

            try {
                logStream = new PrintStream(logFile);
            } catch (FileNotFoundException e) {
                logStream = System.out;
                System.out.println("Error: " + e.getMessage());

            }
        } else {
            logStream = System.out;
        }

        if (args.length > 0) {
            try {
                portNumber = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number, using default port 8080");
            }
        }

        HttpServer server = new HttpServer(portNumber, logStream);
        Thread serverThread = new Thread(server);
        serverThread.start();

        // Start the proxy server
        ProxyServer proxy = new ProxyServer(8888, logStream, cacheSize);
        Thread proxyThread = new Thread(proxy);
        proxyThread.start();

    }

    private static void addShutdownHook(PrintStream logStream) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (logToTextFile && logStream != null && logStream != System.out) {
                logStream.close();
            }
        }));
    }

    static {
        addShutdownHook(logStream);
    }

}
