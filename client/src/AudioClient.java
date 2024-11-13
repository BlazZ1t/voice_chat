import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class AudioClient {

    public static class Sender implements Runnable {
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

    public static class Receiver implements Runnable {
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