import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class AudioClient {
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
        private char disconnectIndicator = 'F';

        public Sender(TargetDataLine targetLine, Socket socket) {
            this.targetLine = targetLine;
            this.socket = socket;
        }

        protected char callDisconnectIndicator() {
            return disconnectIndicator;
        }

        protected void setDisconnectIndicator(char status) {
            disconnectIndicator = status;
        }

        @Override
        public void run() {
            try (OutputStream output = socket.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                Scanner sc = new Scanner(System.in);

                Thread inputThread = new Thread(() -> {
                    while (sc.hasNextLine()) {
                        setDisconnectIndicator(sc.next().charAt(0));
                        if (callDisconnectIndicator() == 'Q') {
                            break;
                        }
                    }
                });
                inputThread.start();
                while ((bytesRead = targetLine.read(buffer, 0, buffer.length)) != -1) {
                    if (!socket.isClosed()) {
                        if (disconnectIndicator == 'Q') {
                            socket.close();
                            break;
                        }
                        output.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException();
            } finally {
                System.out.println("Sender closed");
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
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Receiver closed");
            }
        }
    }
}