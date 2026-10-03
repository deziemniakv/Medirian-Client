package dev.meridian.ipc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.meridian.core.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live channel to the Meridian Launcher (see docs/PROTOCOL.md).
 *
 * <p>When the game is started by the launcher it passes {@code -Dmeridian.launcher.port} and a
 * one-time {@code -Dmeridian.launcher.token}. The client connects to {@code 127.0.0.1:port} and
 * exchanges newline-delimited JSON messages. All I/O runs on daemon threads; incoming messages
 * are handed to {@link MessageHandler} on the socket thread (handlers must hop to the client thread).
 */
public final class LauncherBridge {

    /** Receives messages from the launcher. */
    public interface MessageHandler {
        void onMessage(JsonObject message);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Gson GSON = new Gson();

    private final int port;
    private final String token;
    private final ExecutorService writer;
    private volatile Socket socket;
    private volatile boolean connected;

    private LauncherBridge(int port, String token) {
        this.port = port;
        this.token = token;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Meridian-Launcher-Bridge-Writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Creates a bridge from system properties, or returns null when not started by the launcher. */
    public static LauncherBridge fromSystemProperties() {
        String port = System.getProperty("meridian.launcher.port");
        String token = System.getProperty("meridian.launcher.token");
        if (port == null || token == null) {
            return null;
        }
        try {
            return new LauncherBridge(Integer.parseInt(port), token);
        } catch (NumberFormatException e) {
            Log.warn("Invalid meridian.launcher.port '{}'", port);
            return null;
        }
    }

    /** Connects in the background and sends {@code hello}. */
    public void start(final JsonObject hello, final MessageHandler handler) {
        Thread reader = new Thread(() -> run(hello, handler), "Meridian-Launcher-Bridge");
        reader.setDaemon(true);
        reader.start();
    }

    private void run(JsonObject hello, MessageHandler handler) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket = s;
            connected = true;
            hello.addProperty("type", "hello");
            hello.addProperty("token", token);
            send(hello);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), UTF8));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    handler.onMessage(GSON.fromJson(line, JsonObject.class));
                } catch (RuntimeException e) {
                    Log.warn("Ignoring malformed launcher message: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.info("Launcher bridge unavailable: {}", e.getMessage());
        } finally {
            connected = false;
        }
    }

    public boolean connected() {
        return connected;
    }

    /** Sends a message asynchronously; silently dropped when not connected. */
    public void send(final JsonObject message) {
        if (writer.isShutdown()) {
            return;
        }
        final String line = GSON.toJson(message) + "\n";
        writer.execute(() -> {
            Socket s = socket;
            if (s == null || s.isClosed()) {
                return;
            }
            try {
                OutputStream out = s.getOutputStream();
                out.write(line.getBytes(UTF8));
                out.flush();
            } catch (Exception e) {
                connected = false;
            }
        });
    }

    public void close() {
        writer.shutdown();
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
                // closing anyway
            }
        }
    }
}
