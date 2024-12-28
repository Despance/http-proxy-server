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
            // Create input and output streams for the client socket
            BufferedReader clientInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream());
        ) {
            String requestLine = clientInput.readLine();
            logStream.println("Proxy received request: " + requestLine);


            // Check if the request is a valid GET request
            String[] requestParts = requestLine.split(" ");
            if (requestLine == null || !requestLine.startsWith("GET ") || requestParts.length < 2) {
                // if the request is not a valid GET request, send a 400 response to the client
                sendError(clientOutput, 400, "Bad Request");
                return;
            }

            // default values for localhost server
            String uri = requestParts[1];
            String host = "localhost";
            int port = 8080;
            String path = uri;

            // Parse the URI to get the host, port and path
            if (uri.startsWith("http://")) {
                URL url = new URL(uri);
                host = url.getHost();
                // if the port is not specified, use the default port 80 for http and 8080 for localhost
                port = (url.getPort() == -1) ? (host.equals("localhost") ? 8080 : 80) : url.getPort(); 
                path = url.getPath();
            }
            // if the path does not start with "/", add it
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            // for localhost case, check if the URI size is within the limit
            if(host.contains("localhost") && port == 8080) {
                int size;
                try {
                    size = Integer.parseInt(path.substring(1));
                    if (size > MAX_URI_SIZE) {
                        sendError(clientOutput, 414, "Request-URI Too Long");
                        return;
                    }
                } 
                catch (Exception e) {
                    sendError(clientOutput, 400, "Bad Request");
                    return;
                }
            }

            // The cache files are stored and retrieved according to the hash of the URL
            // Check for cache hit, if found, send the cached file to the client
            String hash = String.valueOf(uri.hashCode());
            if (cache.containsKey(hash)) {
                logStream.println("Cache hit for URI hash: " + hash);
                File cachedFile = cache.get(hash);
                sendCachedFile(clientOutput, cachedFile);
                return;
            }

            // Forward the request to the target server
            try (Socket serverSocket = new Socket(host, port); // Create a new socket to the target server
            // Create input and output streams for the server socket
            PrintWriter serverOutput = new PrintWriter(serverSocket.getOutputStream(), true);
            BufferedReader serverInput = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));) {
                
                // Send the get request to the server   
                serverOutput.println("GET " + path + " HTTP/1.0");
                serverOutput.println("Host: " + host);
                serverOutput.println();
                
                // The cache files are stored and retrieved according to the hash of the URL
                // Read the server response and send it to the client and add it to the cache
                File cacheFile = new File(cacheDirectory + "/cache_" + hash + ".txt");
                try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(cacheFile))) {
                    String serverResponseLine;
                    while ((serverResponseLine = serverInput.readLine()) != null) {
                        fileWriter.write(serverResponseLine + "\r\n");
                        clientOutput.writeBytes(serverResponseLine + "\r\n");
                    }
                }
                addToCache(hash, cacheFile);
            } 
            catch (IOException e) {
                // if the target server is not reachable, send a 404 response to the client
                sendError(clientOutput, 404, "Not Found");
            }
        } catch (IOException e) {
            logStream.println("Error: " + e.getMessage());
        }
    }

    // Add to cache and remove the oldest entry if the cache is full(FIFO)
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

    // Send the cached file to the client
    private void sendCachedFile(DataOutputStream output, File file) throws IOException {
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                output.writeBytes(line + "\r\n");
            }
        }
    }

    // Send error response to the client
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
