import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class HttpClientHandler implements Runnable {

    private static final String BAD_REQUEST = "Bad Request";
    private Socket socket;
    private PrintStream logStream;
    private BufferedReader input;
    private DataOutputStream output;

    public HttpClientHandler(Socket accept) {
        this.socket = accept;
        this.logStream = System.out;
    }

    public HttpClientHandler(Socket accept, PrintStream logStream) {
        this.socket = accept;
        this.logStream = logStream;
    }

    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Request buffer
            output = new DataOutputStream(socket.getOutputStream()); // Response buffer

            String requestLine = input.readLine(); // Read the first line of the request
            logStream.println("Received request: " + requestLine);

            if (requestLine == null || !requestLine.startsWith("GET ")) {
                sendError(400, BAD_REQUEST);
                return;
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                sendError(400, BAD_REQUEST);
                return;
            }

            String uri = requestParts[1];
            if (uri.startsWith("/")) {
                uri = uri.substring(1); // Remove leading slash
            }

            int size;
            try {
                size = Integer.parseInt(uri); // Convert to integer
                if (size < 100 || size > 20000) {
                    sendError(400, BAD_REQUEST);
                    return;
                }
            } catch (NumberFormatException e) {
                sendError(400, BAD_REQUEST);
                return;
            }

            // Generate the HTML response
            String htmlContent = generateHtml(size);

            // Send response headers
            output.writeBytes("HTTP/1.1 200 OK\r\n");
            output.writeBytes("Content-Type: text/html\r\n");
            output.writeBytes("Content-Length: " + htmlContent.length() + "\r\n");
            output.writeBytes("\r\n");

            // Send the HTML content
            output.writeBytes(htmlContent);
            output.flush();

        } catch (Exception e) {
            logStream.println("Error: " + e.getMessage());
        } finally {
            try {

                output.close();
                input.close();
                socket.close();
                logStream.println("Client connection closed: " + socket.getInetAddress() + "\n");
            } catch (Exception e) {
                logStream.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    private void sendError(int statusCode, String message) throws Exception {
        // Construct the error HTML
        String errorHtml = "<html><body><h1>" + statusCode + " " + message + "</h1></body></html>";

        // Send response headers
        output.writeBytes("HTTP/1.1 " + statusCode + " " + message + "\r\n");
        output.writeBytes("Content-Type: text/html\r\n");
        output.writeBytes("Content-Length: " + errorHtml.length() + "\r\n");
        output.writeBytes("\r\n");

        output.writeBytes(errorHtml);
        output.flush();
    }

    private String generateHtml(int size) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>I am " + size + " bytes long</title></head><body>");
        int contentSize = size - 14;
        while (html.length() < contentSize) {
            html.append("a"); // Add filler content
        }
        html.append("</body></html>");
        return html.toString();
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