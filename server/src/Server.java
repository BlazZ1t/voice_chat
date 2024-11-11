import java.net.*;
import java.util.*;
import java.io.*;

public class Server {
    private static final int PORT = 5555;
    private static Set<Socket> clientSockets = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            System.out.println("The server is running on " + serverSocket.getInetAddress() + " " + serverSocket.getLocalPort());

            while (true) {
                Socket socket = serverSocket.accept();
                clientSockets.add(socket);
                new Thread(new ClientHandler(socket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {this.socket = socket;}

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
                e.printStackTrace();
            }
            clientSockets.remove(socket);
        }
    }
}