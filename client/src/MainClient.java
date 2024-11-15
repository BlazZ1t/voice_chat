import java.io.*;
import java.net.*;
import java.util.*;
import javax.sound.sampled.*;

public class MainClient {
    private static final String SERVER_ADDRESS = "0.0.0.0";
    private static final int SERVER_PORT = 5555;

    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Connected to server: " + socket.getInetAddress());

            Thread senderThread = new Thread(new Sender(socket));
            Thread receiverThread = new Thread(new Receiver(socket));

            senderThread.start();
            receiverThread.start();

            senderThread.join();
            receiverThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private record Sender(Socket socket) implements Runnable {
        //TODO: Debug connection to existent audio servers
        //TODO: Add ability to disconnect from audio servers
        @Override
        public void run() {
            try {
                Scanner sc = new Scanner(System.in);
                while (sc.hasNextLine()) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    String command = sc.nextLine();
                    out.println(command);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }


    }

    private record Receiver(Socket socket) implements Runnable {

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("AUDIO_SERVER")) {
                        int audioPort = Integer.parseInt(message.split(" ")[1]);
                        System.out.println(message);
                        connectToAudioServer(audioPort);
                        continue;
                    } else if (message.equalsIgnoreCase("AUDIO_SERVER_NOT_EXISTS")) {
                        System.out.println("Server not found");
                        continue;
                    }
                    System.out.println(message);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void connectToAudioServer(int audioPort) {
            try {
                Socket audioSocket = new Socket(SERVER_ADDRESS, audioPort);
                System.out.println("Connected to audio server on port: " + audioPort);

                AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
                DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
                DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);

                TargetDataLine targetLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                SourceDataLine sourceLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);

                targetLine.open(format);
                sourceLine.open(format);

                targetLine.start();
                sourceLine.start();

                Thread senderThread = new Thread(new AudioClient.Sender(targetLine, audioSocket));
                Thread receiverThread = new Thread(new AudioClient.Receiver(sourceLine, audioSocket));
                senderThread.start();
                receiverThread.start();

                senderThread.join();
                receiverThread.join();

            } catch (LineUnavailableException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


}
