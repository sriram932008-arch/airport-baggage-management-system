package com.airport.baggage.server;

import com.airport.baggage.service.AuthService;
import com.airport.baggage.service.BaggageService;
import com.airport.baggage.service.ComplaintService;
import com.airport.baggage.service.CsvFileService;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Built-in Lightweight HTTP Server instance configuring REST APIs and static web assets.
 */
public class HttpServerApp {

    private final int port;
    private final String dataDir;
    private final String webDir;
    private HttpServer server;

    public HttpServerApp(int port, String dataDir, String webDir) {
        this.port = port;
        this.dataDir = dataDir;
        this.webDir = webDir;
    }

    public void start() throws IOException {
        CsvFileService csvService = new CsvFileService(dataDir);
        AuthService authService = new AuthService(csvService);
        BaggageService baggageService = new BaggageService(csvService);
        ComplaintService complaintService = new ComplaintService(csvService, baggageService);

        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Bind API and Static routes
        server.createContext("/api/", new ApiHandler(authService, baggageService, complaintService));
        server.createContext("/", new StaticFileHandler(webDir));

        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();

        System.out.println("================================================================================");
        System.out.println("  Intelligent Airport Baggage Handling and Routing System");
        System.out.println("  Server successfully started on: http://localhost:" + port);
        System.out.println("  Data Storage: CSV files located at " + new File(dataDir).getAbsolutePath());
        System.out.println("  Web UI Directory: " + new File(webDir).getAbsolutePath());
        System.out.println("================================================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            System.out.println("Server stopped.");
        }
    }
}
