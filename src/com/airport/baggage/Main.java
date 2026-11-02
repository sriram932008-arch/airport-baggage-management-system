package com.airport.baggage;

import com.airport.baggage.server.HttpServerApp;

import java.io.File;

/**
 * Entry point for the Intelligent Airport Baggage Handling and Routing System.
 */
public class Main {

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        // Determine working directories
        String workingDir = System.getProperty("user.dir");
        String dataDir = new File(workingDir, "data").getAbsolutePath();
        String webDir = new File(workingDir, "web").getAbsolutePath();

        try {
            HttpServerApp app = new HttpServerApp(port, dataDir, webDir);
            app.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down airport baggage management server...");
                app.stop();
            }));

        } catch (Exception e) {
            System.err.println("Fatal Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

