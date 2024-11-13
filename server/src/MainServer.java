import java.net.*;
import java.io.*;
import java.util.*;

public class MainServer {
    private static final int PORT = 5555;
    private static final Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());
    //TODO: Decide how to manage multiple audio servers and connect clients to existent audio servers
    private static final Map<Integer, AudioServer> audioServers = Collections.synchronizedMap(new HashMap<>());
    private static int audioServerPort = 6000; // Starting port for audio servers

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("The server is running on " + serverSocket.getInetAddress() + " " + serverSocket.getLocalPort());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientSockets.add(clientSocket);
                System.out.println("Client connected");
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record ClientHandler(Socket socket) implements Runnable {

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Client sent: " + message);
                    if (message.equalsIgnoreCase("START_AUDIO_SERVER")) {
                        startAudioServer(socket);
                    } else {
                        broadcast(message, socket);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                clientSockets.remove(socket);
                System.out.println("Client disconnected");
            }
        }

        private void broadcast(String message, Socket senderSocket) {
            synchronized (clientSockets) {
                for (Socket clientSocket : clientSockets) {
                    if (clientSocket != senderSocket) {
                        try {
                            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                            out.println(message);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        private void startAudioServer(Socket clientSocket) {
            int port = audioServerPort++;
            AudioServer audioServer = new AudioServer(port);
            new Thread(audioServer::start).start();

            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println("AUDIO_SERVER_STARTED " + port);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

class AudioServer {
    private final int port;
    private final Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());

    public AudioServer(int port) {
        this.port = port;
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                closeSocket();
                System.out.println("Audio client disconnected");
                if (clientSockets.isEmpty()) {
                    System.out.println("No clients left, shutting down audio server on port " + port);
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