package proxy;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class HttpProxyHandler implements Runnable {
    
    private Socket socket;
    private PrintStream logStream;
    private BufferedReader input;
    private DataOutputStream output;
    
    public HttpProxyHandler(Socket accept) {
        this.socket = accept;
        this.logStream = System.out;
    }

    public HttpProxyHandler(Socket accept, PrintStream logStream) {
        this.socket = accept;
        this.logStream = logStream;
    }

    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new DataOutputStream(socket.getOutputStream());

            String requestLine = input.readLine();
            logStream.println("Received request: " + requestLine);

            String[] requestParts = requestLine.split(" ");

            String url = requestParts[1];
            if (url.startsWith("http://localhost:8080/")) {
                url = url.replace("http://localhost:8080", "");
                

                String uri = url.substring(1); // get the number from the url
                int size;
                size = Integer.parseInt(uri);
                if(size>9999){
                    output.writeBytes("HTTP/1.0 414 Request-URI Too Long\n\n");
                    output.flush();
                    return;
                }


                requestParts[1] = url;
                requestLine = requestParts[0] + " " + requestParts[1] + " " + requestParts[2];
                try{
                    Socket localSocket = new Socket("localhost", 8080);
                    DataOutputStream localOutput = new DataOutputStream(localSocket.getOutputStream());
                    BufferedReader localInput = new BufferedReader(new InputStreamReader(localSocket.getInputStream()));

                    localOutput.writeBytes(requestLine + "\r\n");

                    String responseLine;
                    while ((responseLine = localInput.readLine()) != null) {
                        output.writeBytes(responseLine + "\r\n");
                    }
                    output.flush();
                    localSocket.close();

                }
                catch(IOException e){
                    logStream.println("Error connecting to local server: " + e.getMessage());
                    output.writeBytes("HTTP/1.0 404 Not Found\r\n\r\n");
                }
                
                
            } 
            else {
                // Handle requests to remote servers
                String host;
                int port = 80; // Default HTTP port
                String path = "/";
                
                if (url.startsWith("http://")) {
                    url = url.substring(7); // Remove "http://"
                }
    
                int slashIndex = url.indexOf("/");
                if (slashIndex > 0) {
                    host = url.substring(0, slashIndex);
                    path = url.substring(slashIndex); // Extract the path
                } else {
                    host = url; // No path specified
                }
    
                int colonIndex = host.indexOf(":");
                if (colonIndex > 0) {
                    port = Integer.parseInt(host.substring(colonIndex + 1));
                    host = host.substring(0, colonIndex); // Extract host without port
                }
    
                logStream.println("Forwarding request to remote server: " + host + ":" + port + path);
    
                // Connect to the remote server
                try (
                    Socket remoteSocket = new Socket(host, port);
                    DataOutputStream remoteOutput = new DataOutputStream(remoteSocket.getOutputStream());
                    BufferedReader remoteInput = new BufferedReader(new InputStreamReader(remoteSocket.getInputStream()));) {
    
                    // Forward the client's request
                    remoteOutput.writeBytes(requestParts[0] + " " + path + " " + requestParts[2] + "\r\n");
    
                    // Forward client headers
                    String headerLine;
                    while ((headerLine = input.readLine()) != null && !headerLine.isEmpty()) {
                        remoteOutput.writeBytes(headerLine + "\r\n");
                    }
                    remoteOutput.writeBytes("\r\n");
    
                    // Read and forward the remote server's response
                    String responseLine;
                    while ((responseLine = remoteInput.readLine()) != null) {
                        output.writeBytes(responseLine + "\r\n");
                    }
                    output.flush();
                } catch (IOException e) {
                    logStream.println("Error connecting to remote server: " + e.getMessage());
                    output.writeBytes("HTTP/1.0 502 Bad Gateway\r\n\r\n");
                }
            }
        } catch (IOException e) {
            logStream.println("Error: " + e.getMessage());
        } finally {
            try {
                if (input != null) input.close();
                if (output != null) output.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                logStream.println("Error closing resources: " + e.getMessage());
            }
        }
    }


    public void stop() {
        try {
            socket.close();
            logStream.println("Client connection closed: " + socket.getInetAddress());
        } catch (Exception e) {
            logStream.println("Error: " + e.getMessage());
        }
    }


}
