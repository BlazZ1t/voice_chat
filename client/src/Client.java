import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Client {
    private static final String SERVER_ADDRESS = "0.0.0.0";
    private static final int SERVER_PORT = 5555;

    public static void main(String[] args) {
        AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
        DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Connected to server: " + socket.getInetAddress());

            TargetDataLine targetLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
            SourceDataLine sourceLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);

            targetLine.open(format);
            sourceLine.open(format);

            targetLine.start();
            sourceLine.start();

            Thread senderThread = new Thread(new Sender(targetLine, socket));
            Thread receiverThread = new Thread(new Receiver(sourceLine, socket));
            senderThread.start();
            receiverThread.start();

            senderThread.join(); // Wait for the sender thread to finish
            receiverThread.join();

        } catch (LineUnavailableException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class Sender implements Runnable {
        private final TargetDataLine targetLine;
        private final Socket socket;

        public Sender(TargetDataLine targetLine, Socket socket) {
            this.targetLine = targetLine;
            this.socket = socket;
        }

        @Override
        public void run() {
            try (OutputStream output = socket.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = targetLine.read(buffer, 0, buffer.length)) != -1) {
                    if (!socket.isClosed()) {
                        output.write(buffer, 0, bytesRead);
                    } else {
                        System.out.println("Socket is closed.");
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class Receiver implements Runnable {
        private final Socket socket;
        private final SourceDataLine sourceLine;

        public Receiver(SourceDataLine sourceLine, Socket socket) {
            this.socket = socket;
            this.sourceLine = sourceLine;
        }


        @Override
        public void run() {
            try (InputStream input = socket.getInputStream()){
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    if (!socket.isClosed()) {
                        sourceLine.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}