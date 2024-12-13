import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

public class App {

    private static int portNumber = 8080;
    private static PrintStream logStream;
    private static boolean logToTextFile = false;

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

    }

    private static void addShutdownHook(PrintStream logStream) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (logStream != System.out) {
                logStream.close();
            }
        }));
    }

    static {
        addShutdownHook(logStream);
    }

}
