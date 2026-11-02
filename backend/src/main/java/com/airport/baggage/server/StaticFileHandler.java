package com.airport.baggage.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Static file server for HTML, CSS, JavaScript, and SVG assets.
 */
public class StaticFileHandler implements HttpHandler {

    private final String webRoot;
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=UTF-8");
        MIME_TYPES.put("htm", "text/html; charset=UTF-8");
        MIME_TYPES.put("css", "text/css; charset=UTF-8");
        MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put("json", "application/json; charset=UTF-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
    }

    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        // Prevent path traversal
        if (path.contains("..")) {
            sendNotFound(exchange);
            return;
        }

        Path filePath = Paths.get(webRoot, path);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            // Fallback to index.html for SPA client-side routing
            filePath = Paths.get(webRoot, "index.html");
            if (!Files.exists(filePath)) {
                sendNotFound(exchange);
                return;
            }
        }

        String fileName = filePath.getFileName().toString();
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) {
            ext = fileName.substring(dotIdx + 1).toLowerCase();
        }

        String mime = MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Type", mime);

        byte[] fileBytes = Files.readAllBytes(filePath);
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        String notFound = "<html><body><h1>404 Not Found</h1><p>Resource does not exist.</p></body></html>";
        byte[] bytes = notFound.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(404, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

