import java.io.*;
import java.net.*;
import java.util.*;

public class AudioServer {
    private final int port;
    private final Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());
    private final Map<Integer, AudioServer> audioServers;

    public AudioServer(int port, Map<Integer, AudioServer> audioServers) {
        this.port = port;
        this.audioServers = audioServers;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Audio server running on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                clientSockets.add(socket);
                System.out.println("Audio client connected");
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "This server is running on port " + port + "\nConnected clients are\n" + clientSockets;
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (InputStream input = socket.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    broadcast(buffer, bytesRead);
                }
            } catch (IOException ignored) {

            } finally {
                closeSocket();
                System.out.println("Audio client disconnected");
                if (clientSockets.isEmpty()) {
                    synchronized (audioServers) {
                        audioServers.remove(port);
                        System.out.println("No clients left, shutting down audio server on port " + port);
                    }
                }
            }
        }

        private void broadcast(byte[] buffer, int bytesRead) {
            synchronized (clientSockets) {
                for (Socket clientSocket : clientSockets) {
                    if (clientSocket != socket && !clientSocket.isClosed()) {
                        try {
                            OutputStream output = clientSocket.getOutputStream();
                            output.write(buffer, 0, bytesRead);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        private void closeSocket() {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            synchronized (clientSockets) {
                clientSockets.remove(socket);
            }
        }
    }
}