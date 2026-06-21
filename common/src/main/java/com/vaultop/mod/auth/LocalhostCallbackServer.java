package com.vaultop.mod.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vaultop.mod.VaultOPMod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class LocalhostCallbackServer {
    private static final int PORT = 24567;
    private HttpServer server;
    private final Callback callback;

    public interface Callback {
        void onSuccess(String token);
        void onFailure(String reason);
    }

    public LocalhostCallbackServer(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/callback", new CallbackHandler());
            server.setExecutor(null); // default executor
            server.start();
            VaultOPMod.LOGGER.info("[VaultOP] Localhost callback server started on port " + PORT);
        } catch (IOException e) {
            VaultOPMod.LOGGER.error("[VaultOP] Failed to start localhost callback server", e);
            callback.onFailure("Failed to start local listener: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            VaultOPMod.LOGGER.info("[VaultOP] Localhost callback server stopped.");
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String token = params.get("token");

            String response;
            int responseCode;

            if (token != null && !token.trim().isEmpty()) {
                response = "<html><body style=\"font-family: sans-serif; background-color: #121212; color: #ffffff; text-align: center; padding-top: 50px;\">" +
                           "<h1 style=\"color: #3cc83c;\">AUTHENTICATION SUCCESSFUL!</h1>" +
                           "<p>Your Minecraft client has been linked. You can close this tab and return to Minecraft.</p>" +
                           "</body></html>";
                responseCode = 200;
                
                // Signal back
                callback.onSuccess(token);
            } else {
                response = "<html><body style=\"font-family: sans-serif; background-color: #121212; color: #ffffff; text-align: center; padding-top: 50px;\">" +
                           "<h1 style=\"color: #e63946;\">AUTHENTICATION FAILED</h1>" +
                           "<p>No session token was received from the server. Please try again.</p>" +
                           "</body></html>";
                responseCode = 400;
                
                callback.onFailure("No token received");
            }

            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(responseCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

            // Stop the server in a new thread so we don't block the request handler
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                stop();
            }).start();
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
