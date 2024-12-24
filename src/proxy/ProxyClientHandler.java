package proxy;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Map;

class ProxyClientHandler implements Runnable {
    private static final int MAX_URI_SIZE = 9999;
    private Socket clientSocket;
    private PrintStream logStream;
    private Map<String, File> cache;
    private int cacheSize;
    private String cacheDirectory;

    public ProxyClientHandler(Socket clientSocket, PrintStream logStream, Map<String, File> cache, int cacheSize, String cacheDirectory) {
        this.clientSocket = clientSocket;
        this.logStream = logStream;
        this.cache = cache;
        this.cacheSize = cacheSize;
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    public void run() {
        try (
            BufferedReader clientInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());
        ) {
            String requestLine = clientInput.readLine();
            logStream.println("Proxy received request: " + requestLine);

            if (requestLine == null || !requestLine.startsWith("GET ")) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            String uri = requestParts[1];
            String host = "localhost";
            int port = 8080;
            String path = uri;

            if (uri.startsWith("http://")) {
                URL url = new URL(uri);
                host = url.getHost();
                port = (url.getPort() == -1) ? (host.equals("localhost") ? 8080 : 80) : url.getPort();
                path = url.getPath();
            }

            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            if(host.contains("localhost")) {
                int size;
                try {
                    size = Integer.parseInt(path.substring(1));
                    if (size > MAX_URI_SIZE) {
                        sendError(clientOutput, 414, "Request-URI Too Long");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendError(clientOutput, 400, "Bad Request");
                    return;
                }
            }

            String hash = String.valueOf(uri.hashCode());
            if (cache.containsKey(hash)) {
                logStream.println("Cache hit for URI hash: " + hash);
                File cachedFile = cache.get(hash);
                sendCachedFile(clientOutput, cachedFile);
                return;
            }

            // Forward the request to the target server
            try (Socket serverSocket = new Socket(host, port);
                 PrintWriter serverOutput = new PrintWriter(serverSocket.getOutputStream(), true);
                 BufferedReader serverInput = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));) {
                
                serverOutput.println("GET " + path + " HTTP/1.0");
                serverOutput.println("Host: " + host);
                serverOutput.println();

                File cacheFile = new File(cacheDirectory + "/cache_" + hash + ".txt");
                try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(cacheFile))) {
                    String serverResponseLine;
                    while ((serverResponseLine = serverInput.readLine()) != null) {
                        fileWriter.write(serverResponseLine + "\r\n");
                        clientOutput.writeBytes(serverResponseLine + "\r\n");
                    }
                }
                addToCache(hash, cacheFile);
            } catch (IOException e) {
                sendError(clientOutput, 404, "Not Found");
            }
        } catch (IOException e) {
            logStream.println("Error: " + e.getMessage());
        }
    }

    private void addToCache(String hash, File file) {
        if (cache.size() >= cacheSize) {
            String oldestKey = cache.keySet().iterator().next();
            File oldestFile = cache.remove(oldestKey);
            if (oldestFile.exists()) {
                oldestFile.delete();
                logStream.println("Deleted expired cache file: " + oldestFile.getName());
            }
        }
        cache.put(hash, file);
        logStream.println("Cached URI: " + hash);
    }

    private void sendCachedFile(DataOutputStream output, File file) throws IOException {
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                output.writeBytes(line + "\r\n");
            }
        }
    }

    private void sendError(DataOutputStream output, int statusCode, String message) throws IOException {
        String errorHtml = "<html><body><h1>" + statusCode + " " + message + "</h1></body></html>";
        output.writeBytes("HTTP/1.1 " + statusCode + " " + message + "\r\n");
        output.writeBytes("Content-Type: text/html\r\n");
        output.writeBytes("Content-Length: " + errorHtml.length() + "\r\n");
        output.writeBytes("\r\n");
        output.writeBytes(errorHtml);
        output.flush();
    }

    public void stop() {
        try {
            clientSocket.close();
            logStream.println("Proxy client connection closed: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            logStream.println("Error closing client connection: " + e.getMessage());
        }
    }
}
