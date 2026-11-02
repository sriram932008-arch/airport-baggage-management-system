package com.airport.baggage.server;

import com.airport.baggage.model.*;
import com.airport.baggage.service.*;
import com.airport.baggage.test.ScenarioRunner;import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Main HTTP Handler routing API requests and enforcing security & access control.
 */
public class ApiHandler implements HttpHandler {

    private final AuthService authService;
    private final BaggageService baggageService;
    private final ComplaintService complaintService;

    public ApiHandler(AuthService authService, BaggageService baggageService, ComplaintService complaintService) {
        this.authService = authService;
        this.baggageService = baggageService;
        this.complaintService = complaintService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS and JSON response headers
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token");

        String method = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String body = readRequestBody(exchange);
        Map<String, String> bodyMap = JsonUtil.parseSimpleJson(body);

        try {
            // Routing by path prefix
            if (path.startsWith("/api/auth/")) {
                handleAuth(exchange, path, method, bodyMap);
            } else if (path.startsWith("/api/public/")) {
                handlePublic(exchange, path, method, queryParams);
            } else if (path.startsWith("/api/passenger/")) {
                handlePassenger(exchange, path, method, queryParams, bodyMap);
            } else if (path.startsWith("/api/admin/")) {
                handleAdmin(exchange, path, method, queryParams, bodyMap);
            } else if (path.equals("/api/test/run-scenarios")) {
                handleTestScenarios(exchange);
            } else {
                sendError(exchange, 404, "Endpoint not found: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    // ==================== AUTH CONTROLLERS ====================

    private void handleAuth(HttpExchange exchange, String path, String method, Map<String, String> body) throws IOException {
        if ("/api/auth/login".equals(path) && "POST".equals(method)) {
            String identifier = body.get("identifier");
            String password = body.get("password");
            String role = body.get("role"); // "PASSENGER" or "ADMIN"

            AuthService.Session session = authService.login(identifier, password, role);
            if (session == null) {
                sendError(exchange, 401, "Invalid credentials or unauthorized portal access.");
                return;
            }

            User user = session.getUser();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("token", session.getToken());
            resp.put("user", user);

            if (!user.isAdmin()) {
                Passenger pax = authService.getPassengerForUser(user.getUserId());
                resp.put("passenger", pax);
            }

            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/auth/register".equals(path) && "POST".equals(method)) {
            String fullName = body.get("fullName");
            String mobileNumber = body.get("mobileNumber");
            String email = body.get("email");
            String username = body.get("username");
            String password = body.get("password");
            String confirmPassword = body.get("confirmPassword");

            int baggageCount;
            try {
                baggageCount = Integer.parseInt(body.getOrDefault("baggageCount", "0"));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Enter a valid baggage count.");
                return;
            }
            if (baggageCount < 1 || baggageCount > 10) {
                sendError(exchange, 400, "Register between 1 and 10 baggage units.");
                return;
            }
            List<Map<String, String>> baggageDetails = new ArrayList<>();
            for (int i = 1; i <= baggageCount; i++) {
                Map<String, String> detail = new HashMap<>();
                detail.put("name", body.get("baggageName" + i));
                detail.put("type", body.get("baggageType" + i));
                detail.put("flightNumber", body.get("baggageFlight" + i));
                baggageDetails.add(detail);
            }
            String baggageValidationError = baggageService.validatePassengerBaggage(baggageDetails);
            if (baggageValidationError != null) {
                sendError(exchange, 400, baggageValidationError);
                return;
            }

            String result = authService.registerPassenger(fullName, mobileNumber, email, username, password, confirmPassword);
            if (!"SUCCESS".equals(result)) {
                sendError(exchange, 400, result);
                return;
            }

            Passenger passenger = authService.getPassengerForUsername(username);
            if (passenger == null) {
                sendError(exchange, 500, "Passenger registration completed but the new profile could not be loaded.");
                return;
            }
            List<String> baggageIds = baggageService.registerPassengerBaggage(passenger, baggageDetails);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Registration successful. Your baggage IDs are ready.");
            resp.put("baggageCount", baggageIds.size());
            resp.put("baggageIds", baggageIds);
            sendJson(exchange, 201, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/auth/logout".equals(path) && "POST".equals(method)) {
            String token = getSessionToken(exchange);
            authService.logout(token);
            Map<String, Object> resp = Collections.singletonMap("success", true);
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/auth/me".equals(path) && "GET".equals(method)) {
            AuthService.Session session = authenticate(exchange);
            if (session == null) {
                sendError(exchange, 401, "Authentication session required.");
                return;
            }
            User u = session.getUser();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("user", u);
            if (!u.isAdmin()) {
                resp.put("passenger", authService.getPassengerForUser(u.getUserId()));
            }
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/auth/forgot-password".equals(path) && "POST".equals(method)) {
            String identifier = body.get("identifier");
            String phoneNumber = body.get("phoneNumber");
            Map<String, String> res = authService.requestPasswordReset(identifier, phoneNumber);
            if (res.containsKey("error")) {
                sendError(exchange, 400, res.get("error"));
            } else {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("otp", res.get("otp"));
                resp.put("username", res.get("username"));
                resp.put("message", "OTP generated successfully.");
                sendJson(exchange, 200, JsonUtil.toJson(resp));
            }
            return;
        }

        if ("/api/auth/reset-password".equals(path) && "POST".equals(method)) {
            String identifier = body.get("identifier");
            String otp        = body.get("otp");
            String newPw      = body.get("newPassword");
            String confPw     = body.get("confirmPassword");
            Map<String, String> res = authService.verifyOtpAndResetPassword(identifier, otp, newPw, confPw);
            if (res.containsKey("error")) {
                sendError(exchange, 400, res.get("error"));
            } else {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("message", res.get("message"));
                sendJson(exchange, 200, JsonUtil.toJson(resp));
            }
            return;
        }

        sendError(exchange, 404, "Unknown auth action");
    }

    // ==================== PUBLIC CONTROLLERS ====================

    private void handlePublic(HttpExchange exchange, String path, String method, Map<String, String> query) throws IOException {
        if ("/api/public/track".equals(path) && "GET".equals(method)) {
            String id = query.get("id");
            if (id == null || id.trim().isEmpty()) {
                sendError(exchange, 400, "Baggage ID or Booking Reference is required.");
                return;
            }

            Baggage found = null;
            for (Baggage b : baggageService.getAllBaggage()) {
                if (b.getBaggageId().equalsIgnoreCase(id.trim()) || b.getBookingReference().equalsIgnoreCase(id.trim())) {
                    found = b;
                    break;
                }
            }

            if (found == null) {
                sendError(exchange, 404, "No baggage record found matching query: " + id);
                return;
            }

            List<TrackingEvent> events = baggageService.getTrackingHistoryForBaggage(found.getBaggageId());
            Flight flight = baggageService.getFlightMap().get(found.getFlightNumber());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("baggage", found);
            resp.put("flight", flight);
            resp.put("trackingEvents", events);
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/public/flights".equals(path) && "GET".equals(method)) {
            List<Flight> flights = new ArrayList<>(baggageService.getFlightMap().values());
            sendJson(exchange, 200, JsonUtil.toJson(flights));
            return;
        }

        sendError(exchange, 404, "Public endpoint not found");
    }

    // ==================== PASSENGER CONTROLLERS ====================

    private void handlePassenger(HttpExchange exchange, String path, String method, Map<String, String> query,
                                Map<String, String> body) throws IOException {
        AuthService.Session session = authenticate(exchange);
        if (session == null) {
            sendError(exchange, 401, "Passenger authentication session required.");
            return;
        }

        User user = session.getUser();
        Passenger pax = authService.getPassengerForUser(user.getUserId());
        if (pax == null) {
            sendError(exchange, 403, "No passenger profile associated with this account.");
            return;
        }

        if ("/api/passenger/baggage".equals(path) && "GET".equals(method)) {
            // Return only the passenger-facing status fields for this authenticated account.
            List<Map<String, Object>> list = baggageService.getPriorityStatusForPassenger(pax.getPassengerId());
            sendJson(exchange, 200, JsonUtil.toJson(list));
            return;
        }

        if ("/api/passenger/track".equals(path) && "GET".equals(method)) {
            String bagId = query.get("id");
            Baggage b = baggageService.getBaggageById(bagId);
            if (b == null) {
                sendError(exchange, 404, "Baggage not found: " + bagId);
                return;
            }

            // CRITICAL SECURITY ENFORCEMENT:
            // Ensure the passenger owns this baggage. Never allow passenger to view another passenger's luggage!
            if (!pax.getPassengerId().equalsIgnoreCase(b.getPassengerId())) {
                sendError(exchange, 403, "Access Denied: You are not authorized to view baggage records belonging to other passengers.");
                return;
            }

            List<TrackingEvent> events = baggageService.getTrackingHistoryForBaggage(b.getBaggageId());
            Flight flight = baggageService.getFlightMap().get(b.getFlightNumber());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("baggage", b);
            resp.put("flight", flight);
            resp.put("estimatedArrivalTime", baggageService.getEstimatedArrivalTime(b));
            resp.put("trackingEvents", events);
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        // --- Priority status ---
        if ("/api/passenger/priority-status".equals(path) && "GET".equals(method)) {
            List<Map<String, Object>> pq = baggageService.getPriorityStatusForPassenger(pax.getPassengerId());
            sendJson(exchange, 200, JsonUtil.toJson(pq));
            return;
        }

        // --- Complaints ---
        if ("/api/passenger/complaints".equals(path) && "GET".equals(method)) {
            List<com.airport.baggage.model.Complaint> list =
                complaintService.getComplaintsForPassenger(pax.getPassengerId());
            sendJson(exchange, 200, JsonUtil.toJson(list));
            return;
        }

        if ("/api/passenger/complaints".equals(path) && "POST".equals(method)) {
            // passengerId is ALWAYS from the authenticated session
            String baggageId    = body.get("baggageId");
            String category     = body.get("category");
            String subject      = body.get("subject");
            String description  = body.get("description");
            Map<String, String> res = complaintService.submitComplaint(
                pax.getPassengerId(), baggageId, category, subject, description);
            if (res.containsKey("error")) {
                sendError(exchange, "403".equals(res.get("httpStatus")) ? 403 : 400, res.get("error"));
            } else {
                Map<String, Object> resp2 = new LinkedHashMap<>();
                resp2.put("success", true);
                resp2.put("complaintId", res.get("complaintId"));
                resp2.put("message", res.get("message"));
                sendJson(exchange, 201, JsonUtil.toJson(resp2));
            }
            return;
        }

        sendError(exchange, 404, "Passenger endpoint not found");
    }

    // ==================== ADMIN CONTROLLERS ====================

    private void handleAdmin(HttpExchange exchange, String path, String method,
                             Map<String, String> query, Map<String, String> body) throws IOException {
        AuthService.Session session = authenticate(exchange);
        if (session == null || !session.getUser().isAdmin()) {
            sendError(exchange, 403, "Access Denied: Authorized airport personnel only.");
            return;
        }

        String adminName = session.getUser().getFullName();

        if ("/api/admin/dashboard-stats".equals(path) && "GET".equals(method)) {
            Map<String, Object> stats = baggageService.getDashboardStats();
            sendJson(exchange, 200, JsonUtil.toJson(stats));
            return;
        }

        if ("/api/admin/baggage".equals(path) && "GET".equals(method)) {
            String search = query.get("search");
            String flightFilter = query.get("flight");
            String statusFilter = query.get("status");

            List<Baggage> list = new ArrayList<>(baggageService.getAllBaggage());

            // Filter logic
            if (search != null && !search.trim().isEmpty()) {
                String s = search.trim().toLowerCase();
                Map<String, String> passengerNames = new HashMap<>();
                for (Passenger passenger : baggageService.getCsvService().getAllPassengers()) {
                    passengerNames.put(passenger.getPassengerId().toLowerCase(), passenger.getFullName().toLowerCase());
                }
                list.removeIf(b -> !b.getBaggageId().toLowerCase().contains(s)
                        && !b.getPassengerId().toLowerCase().contains(s)
                        && !passengerNames.getOrDefault(b.getPassengerId().toLowerCase(), "").contains(s)
                        && !b.getBookingReference().toLowerCase().contains(s)
                        && !b.getFlightNumber().toLowerCase().contains(s)
                        && !b.getCurrentLocation().toLowerCase().contains(s)
                        && !b.getStatus().toLowerCase().contains(s));
            }
            if (flightFilter != null && !flightFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(flightFilter)) {
                list.removeIf(b -> !b.getFlightNumber().equalsIgnoreCase(flightFilter.trim()));
            }
            if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                list.removeIf(b -> !b.getStatus().equalsIgnoreCase(statusFilter.trim()));
            }

            sendJson(exchange, 200, JsonUtil.toJson(list));
            return;
        }

        if ("/api/admin/baggage/clearance".equals(path) && "POST".equals(method)) {
            String baggageId = body.get("baggageId");
            boolean cleared = Boolean.parseBoolean(body.get("cleared"));
            Map<String, Object> res = baggageService.setOperatorClearance(baggageId, cleared, adminName);
            sendJson(exchange, 200, JsonUtil.toJson(res));
            return;
        }

        if ("/api/admin/baggage/status".equals(path) && "POST".equals(method)) {
            String baggageId = body.get("baggageId");
            String status = body.get("status");
            String location = body.get("location");
            boolean loadingConfirmed = Boolean.parseBoolean(body.get("loadingConfirmed"));
            Map<String, Object> res = baggageService.updateBaggageStatus(baggageId, status, location, adminName, loadingConfirmed);
            sendJson(exchange, Boolean.TRUE.equals(res.get("success")) ? 200 : 400, JsonUtil.toJson(res));
            return;
        }

        if ("/api/admin/baggage/add".equals(path) && "POST".equals(method)) {
            String type = body.getOrDefault("baggageType", "ORIGIN");
            String flightNo = body.get("flightNumber");
            String bookingRef = body.getOrDefault("bookingReference", "BK-" + (1000 + (int)(Math.random() * 8999)));
            double weight = 20.0;
            try { weight = Double.parseDouble(body.get("weightKg")); } catch (Exception ignored) {}

            Flight f = baggageService.getFlightMap().get(flightNo);
            if (f == null) {
                sendError(exchange, 400, "Invalid flight number: " + flightNo);
                return;
            }

            String bagId = String.format("BAG-%04d", 1000 + baggageService.getAllBaggage().size() + 1);
            String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            Baggage newBag;
            if ("TRANSFER".equalsIgnoreCase(type)) {
                newBag = new TransferBag(bagId, "PAX-1001", bookingRef, flightNo, weight, false,
                        "N3", "AWAITING_CLEARANCE", f.getAssignedBayId(), "Pending Operator Security Clearance",
                        0.0, "UNROUTED", f.getCutoffMinutesRemaining(), "08:30", now,
                        body.getOrDefault("inboundFlight", "AI101"), 45.0);
            } else {
                newBag = new OriginBag(bagId, "PAX-1001", bookingRef, flightNo, weight, false,
                        "N1", "AWAITING_CLEARANCE", f.getAssignedBayId(), "Pending Operator Security Clearance",
                        0.0, "UNROUTED", f.getCutoffMinutesRemaining(), "08:30", now,
                        body.getOrDefault("checkInDesk", "DESK-A1"), body.getOrDefault("passengerCategory", "ECONOMY"));
            }

            baggageService.getCsvService().addBaggage(newBag);
            baggageService.reloadFromCsv();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("baggageId", bagId);
            resp.put("message", "New baggage registered. Operator clearance required before routing.");
            sendJson(exchange, 201, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/admin/flights".equals(path) && "GET".equals(method)) {
            String search = query.getOrDefault("search", "").trim().toLowerCase();
            List<Flight> flights = new ArrayList<>(baggageService.getFlightMap().values());
            if (!search.isEmpty()) {
                flights.removeIf(flight -> !flight.getFlightNumber().toLowerCase().contains(search)
                        && !flight.getDestination().toLowerCase().contains(search));
            }
            sendJson(exchange, 200, JsonUtil.toJson(flights));
            return;
        }

        if ("/api/admin/flights/add".equals(path) && "POST".equals(method)) {
            double cutoffMinutes;
            try {
                cutoffMinutes = Double.parseDouble(body.getOrDefault("cutoffMinutesRemaining", ""));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Enter a valid minutes-remaining value.");
                return;
            }
            boolean confirmed = Boolean.parseBoolean(body.getOrDefault("confirmed", "false"));
            Map<String, Object> result = baggageService.addFlight(
                    body.get("flightNumber"), body.get("destination"), body.get("scheduledDeparture"),
                    body.get("loadingCutoff"), cutoffMinutes, body.get("assignedBayId"),
                    body.get("status"), confirmed, adminName);
            sendJson(exchange, Boolean.TRUE.equals(result.get("success")) ? 201 : 400, JsonUtil.toJson(result));
            return;
        }

        if ("/api/admin/flights/schedule".equals(path) && "POST".equals(method)) {
            double cutoffMinutes;
            try {
                cutoffMinutes = Double.parseDouble(body.getOrDefault("cutoffMinutesRemaining", ""));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Enter a valid minutes-remaining value.");
                return;
            }
            boolean confirmed = Boolean.parseBoolean(body.getOrDefault("confirmed", "false"));
            Map<String, Object> result = baggageService.updateFlightSchedule(
                    body.get("flightNumber"), body.get("scheduledDeparture"), body.get("loadingCutoff"),
                    cutoffMinutes, body.get("assignedBayId"), body.get("status"), confirmed, adminName);
            sendJson(exchange, Boolean.TRUE.equals(result.get("success")) ? 200 : 400, JsonUtil.toJson(result));
            return;
        }

        if ("/api/admin/flights/bay".equals(path) && "POST".equals(method)) {
            String flightNo = body.get("flightNumber");
            String newBay = body.get("newBayId");
            Flight flight = baggageService.getFlightMap().get(flightNo == null ? "" : flightNo.toUpperCase());
            if (flight == null) {
                sendError(exchange, 404, "Flight not found: " + flightNo);
                return;
            }
            Map<String, Object> res = baggageService.updateFlightSchedule(flightNo,
                    body.getOrDefault("scheduledDeparture", flight.getScheduledDeparture()),
                    body.getOrDefault("loadingCutoff", flight.getLoadingCutoff()),
                    flight.getCutoffMinutesRemaining(),
                    newBay, body.getOrDefault("status", flight.getStatus()),
                    Boolean.parseBoolean(body.getOrDefault("confirmed", "false")), adminName);
            sendJson(exchange, Boolean.TRUE.equals(res.get("success")) ? 200 : 400, JsonUtil.toJson(res));
            return;
        }

        if ("/api/admin/flights/cutoff".equals(path) && "POST".equals(method)) {
            String flightNo = body.get("flightNumber");
            Flight flight = baggageService.getFlightMap().get(flightNo == null ? "" : flightNo.toUpperCase());
            if (flight == null) {
                sendError(exchange, 404, "Flight not found: " + flightNo);
                return;
            }
            double cutoffMinutes;
            try {
                cutoffMinutes = Double.parseDouble(body.getOrDefault("cutoffMinutes", ""));
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Enter a valid minutes-remaining value.");
                return;
            }
            Map<String, Object> res = baggageService.updateFlightSchedule(flightNo,
                    body.getOrDefault("scheduledDeparture", flight.getScheduledDeparture()),
                    body.getOrDefault("cutoffTime", flight.getLoadingCutoff()),
                    cutoffMinutes, body.getOrDefault("assignedBayId", flight.getAssignedBayId()),
                    body.getOrDefault("status", flight.getStatus()),
                    Boolean.parseBoolean(body.getOrDefault("confirmed", "false")), adminName);
            sendJson(exchange, Boolean.TRUE.equals(res.get("success")) ? 200 : 400, JsonUtil.toJson(res));
            return;
        }

        if ("/api/admin/conveyors".equals(path) && "GET".equals(method)) {
            List<ConveyorSegment> list = new ArrayList<>(baggageService.getSegmentMap().values());
            sendJson(exchange, 200, JsonUtil.toJson(list));
            return;
        }

        if ("/api/admin/conveyors/toggle".equals(path) && "POST".equals(method)) {
            String segId = body.get("segmentId");
            boolean active = Boolean.parseBoolean(body.get("active"));
            Map<String, Object> res = baggageService.toggleConveyorSegment(segId, active, adminName);
            sendJson(exchange, 200, JsonUtil.toJson(res));
            return;
        }

        if ("/api/admin/loading-bays".equals(path) && "GET".equals(method)) {
            List<LoadingBay> list = new ArrayList<>(baggageService.getBayMap().values());
            sendJson(exchange, 200, JsonUtil.toJson(list));
            return;
        }

        if ("/api/admin/route/calculate".equals(path) && "POST".equals(method)) {
            String bagId = body.get("baggageId");
            ConveyorRoutePlanner.RouteResult res = baggageService.calculateRouteForBaggage(bagId, adminName);
            Baggage updatedBag = baggageService.getBaggageById(bagId);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", res.isSuccess());
            resp.put("path", res.getPath());
            resp.put("segmentIds", res.getSegmentIds());
            resp.put("totalTimeMinutes", res.getTotalTimeMinutes());
            resp.put("feasibility", res.getFeasibility());
            resp.put("message", res.getMessage());
            resp.put("baggage", updatedBag);
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/admin/route/recalculate-all".equals(path) && "POST".equals(method)) {
            int recalculated = 0;
            int failed = 0;
            int skipped = 0;
            for (Baggage b : baggageService.getAllBaggage()) {
                if (b.isOperatorClearance() && !"LOADED".equalsIgnoreCase(b.getStatus())
                        && !"NOT_FOUND".equalsIgnoreCase(b.getStatus())) {
                    ConveyorRoutePlanner.RouteResult result =
                            baggageService.calculateRouteForBaggage(b.getBaggageId(), "Recalculate All by Admin");
                    recalculated++;
                    if (!result.isSuccess()) failed++;
                } else {
                    skipped++;
                }
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("recalculatedCount", recalculated);
            resp.put("failedRouteCount", failed);
            resp.put("skippedCount", skipped);
            resp.put("message", "Calculated paths for " + recalculated + " cleared baggage units; "
                    + failed + " route failures, " + skipped + " uncleared or already loaded units skipped.");
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        if ("/api/admin/reports".equals(path) && "GET".equals(method)) {
            Map<String, Object> reports = new LinkedHashMap<>();
            reports.put("stats", baggageService.getDashboardStats());
            reports.put("routingHistory", baggageService.getCsvService().getAllRoutingHistory());
            reports.put("trackingHistory", baggageService.getCsvService().getAllTrackingHistory());
            sendJson(exchange, 200, JsonUtil.toJson(reports));
            return;
        }

        // --- Priority Queue ---
        if ("/api/admin/baggage/priority-queue".equals(path) && "GET".equals(method)) {
            List<Map<String, Object>> pq = baggageService.getPriorityQueue();
            List<Map<String, Object>> urgent = baggageService.getUrgentBaggage();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("priorityQueue", pq);
            resp.put("urgentBaggage", urgent);
            resp.put("totalEligible", pq.size());
            resp.put("urgentCount", urgent.size());
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        // --- Admin Complaints ---
        if ("/api/admin/complaints".equals(path) && "GET".equals(method)) {
            String statusFilter = query.get("status");
            List<Map<String, Object>> list = new ArrayList<>();
            List<Passenger> passengers = baggageService.getCsvService().getAllPassengers();
            for (com.airport.baggage.model.Complaint complaint : complaintService.getAllComplaints(statusFilter)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("complaintId", complaint.getComplaintId());
                item.put("passengerId", complaint.getPassengerId());
                item.put("passengerName", passengers.stream()
                        .filter(passenger -> passenger.getPassengerId().equalsIgnoreCase(complaint.getPassengerId()))
                        .map(Passenger::getFullName).findFirst().orElse(complaint.getPassengerId()));
                item.put("baggageId", complaint.getBaggageId());
                item.put("flightNumber", complaint.getFlightNumber());
                item.put("category", complaint.getCategory());
                item.put("subject", complaint.getSubject());
                item.put("description", complaint.getDescription());
                item.put("status", complaint.getStatus());
                item.put("adminResponse", complaint.getAdminResponse());
                item.put("createdAt", complaint.getCreatedAt());
                item.put("updatedAt", complaint.getUpdatedAt());
                list.add(item);
            }
            sendJson(exchange, 200, JsonUtil.toJson(list));
            return;
        }

        if ("/api/admin/complaints/update".equals(path) && "POST".equals(method)) {
            String complaintId   = body.get("complaintId");
            String newStatus     = body.get("status");
            String adminResponse = body.get("adminResponse");
            Map<String, String> res = complaintService.updateComplaint(complaintId, newStatus, adminResponse, adminName);
            if (res.containsKey("error")) {
                sendError(exchange, 400, res.get("error"));
            } else {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("complaintId", res.get("complaintId"));
                resp.put("status", res.get("status"));
                resp.put("message", res.get("message"));
                sendJson(exchange, 200, JsonUtil.toJson(resp));
            }
            return;
        }

        if ("/api/admin/complaints/pending-count".equals(path) && "GET".equals(method)) {
            int count = complaintService.getPendingCount();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("count", count);
            sendJson(exchange, 200, JsonUtil.toJson(resp));
            return;
        }

        sendError(exchange, 404, "Admin endpoint not found");
    }

    // ==================== SCENARIO RUNNER ====================

    private void handleTestScenarios(HttpExchange exchange) throws IOException {
        ScenarioRunner runner = new ScenarioRunner(baggageService, authService);
        Map<String, Object> results = runner.runAllScenarios();
        sendJson(exchange, 200, JsonUtil.toJson(results));
    }

    // ==================== HELPER METHODS ====================

    private AuthService.Session authenticate(HttpExchange exchange) {
        String token = getSessionToken(exchange);
        if (token == null) return null;
        return authService.getSession(token);
    }

    private String getSessionToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String tokenHeader = exchange.getRequestHeaders().getFirst("X-Session-Token");
        if (tokenHeader != null && !tokenHeader.trim().isEmpty()) {
            return tokenHeader.trim();
        }
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "session_token".equals(parts[0].trim())) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("status", statusCode);
        err.put("error", message);
        sendJson(exchange, statusCode, JsonUtil.toJson(err));
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            try {
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    params.put(key, val);
                } else if (!pair.isEmpty()) {
                    params.put(URLDecoder.decode(pair, "UTF-8"), "");
                }
            } catch (UnsupportedEncodingException ignored) {}
        }
        return params;
    }
}
