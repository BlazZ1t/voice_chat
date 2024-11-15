import java.net.*;
import java.io.*;
import java.util.*;

public class MainServer {
    private static final int PORT = 5555;
    private static final Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());
    //TODO: Debug connection to existent audio servers
    private static final Map<Integer, AudioServer> audioServers = Collections.synchronizedMap(new HashMap<>());
    private static int audioServerPort = 6000; // Starting port for audio servers

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("The server is running on " + serverSocket.getInetAddress() + " " + serverSocket.getLocalPort());

            new Thread(new ServerInteractions()).start();

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

                    if (message.startsWith("CONNECT_SERVER")) {
                        startAudioServer(socket, message);
                    } else {
                        broadcast(message, socket);
                    }
                }
            } catch (IOException ignored) {

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

        private void startAudioServer(Socket clientSocket, String command) {

            int port;

            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                String[] commandSplit = command.split(" ");
                if (commandSplit.length == 2) {
                    if (!audioServers.containsKey(Integer.parseInt(commandSplit[1]))) {
                        out.println("AUDIO_SERVER_NOT_EXISTS");
                    } else {
                        port = Integer.parseInt(commandSplit[1]);
                        out.println("AUDIO_SERVER " + port);
                    }
                } else {
                    port = audioServerPort++;
                    AudioServer audioServer = new AudioServer(port, audioServers);
                    audioServers.put(port, audioServer);
                    out.println("AUDIO_SERVER " + port);
                    new Thread(audioServer::start).start();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ServerInteractions implements Runnable {

        @Override
        public void run() {
            Scanner sc = new Scanner(System.in);
            String command;
            while ((command = sc.nextLine()) != null) {
                if (command.startsWith("GET_CLIENTS")) {
                    System.out.println(clientSockets);
                }
                if (command.startsWith("GET_AUDIO_SERVERS")) {
                    System.out.println(audioServers);
                }
                if (command.startsWith("GET_SERVER")) {
                    System.out.println(audioServers.get(Integer.parseInt(command.split(" ")[1])));
                }
            }
        }
    }
}

