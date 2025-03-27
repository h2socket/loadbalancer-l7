
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class loadbalancer {
    private final List<InetSocketAddress> backends;
    private final int port;
    private final AtomicInteger index = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public loadbalancer(List<InetSocketAddress> backends, int port) {
        this.backends = Collections.unmodifiableList(backends);
        this.port = port;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleClient(clientSocket));
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }
            requestBuilder.append("\r\n");
            String request = requestBuilder.toString();

            InetSocketAddress backend = selectBackend();
            Socket backendSocket = new Socket(backend.getHostName(), backend.getPort());
            OutputStream backendOut = backendSocket.getOutputStream();
            backendOut.write(request.getBytes());
            backendOut.flush();

            executor.submit(() -> forward(clientSocket.getInputStream(), backendSocket.getOutputStream()));
            executor.submit(() -> forward(backendSocket.getInputStream(), clientSocket.getOutputStream()));
        } catch (IOException e) {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void forward(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
                out.flush();
            }
        } catch (IOException ignored) {}
        try {
            in.close();
            out.close();
        } catch (IOException ignored) {}
    }

    private InetSocketAddress selectBackend() {
        int i = Math.abs(index.getAndIncrement() % backends.size());
        return backends.get(i);
    }

    public static void main(String[] args) throws IOException {
        List<InetSocketAddress> backends = Arrays.asList(
            new InetSocketAddress("localhost", 9001),
            new InetSocketAddress("localhost", 9002),
            new InetSocketAddress("localhost", 9003)
        );
        new loadbalancer(backends, 8080).start();
    }
}
