package com.airport.baggage.service;

import com.airport.baggage.model.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core business service managing baggage routing, scheduling, state transitions,
 * conveyor failure handling, and operational metrics.
 */
public class BaggageService {

    private final CsvFileService csvService;
    private final ConveyorRoutePlanner routePlanner = new ConveyorRoutePlanner();
    private final Graph conveyorGraph = new Graph();

    // Data structures as required:
    // HashMap for O(1) lookups
    private final Map<String, Baggage> baggageMap = new ConcurrentHashMap<>();
    private final Map<String, Flight> flightMap = new ConcurrentHashMap<>();
    private final Map<String, LoadingBay> bayMap = new ConcurrentHashMap<>();
    private final Map<String, ConveyorSegment> segmentMap = new ConcurrentHashMap<>();

    // PriorityQueue for baggage scheduling by earliest loading cutoff + tie breakers
    private final PriorityQueue<Baggage> baggageSchedulingQueue = new PriorityQueue<>();

    // ArrayDeque for FIFO queues at conveyor entry points
    private final ArrayDeque<Baggage> infeedFifoQueue = new ArrayDeque<>();

    public BaggageService(CsvFileService csvService) {
        this.csvService = csvService;
        reloadFromCsv();
    }

    public synchronized void reloadFromCsv() {
        baggageMap.clear();
        flightMap.clear();
        bayMap.clear();
        segmentMap.clear();
        conveyorGraph.clear();
        baggageSchedulingQueue.clear();
        infeedFifoQueue.clear();

        for (Flight f : csvService.getAllFlights()) {
            flightMap.put(f.getFlightNumber().toUpperCase(), f);
        }

        for (LoadingBay b : csvService.getAllLoadingBays()) {
            bayMap.put(b.getBayId().toUpperCase(), b);
        }

        for (ConveyorSegment s : csvService.getAllConveyors()) {
            segmentMap.put(s.getSegmentId().toUpperCase(), s);
            conveyorGraph.addEdge(s.getSegmentId(), s.getStartNode(), s.getEndNode(),
                    s.getTravelTimeMinutes(), s.isActive());
        }

        for (Baggage b : csvService.getAllBaggage()) {
            baggageMap.put(b.getBaggageId().toUpperCase(), b);
            baggageSchedulingQueue.add(b);
            if ("AWAITING_CLEARANCE".equalsIgnoreCase(b.getStatus())) {
                infeedFifoQueue.addLast(b);
            }
        }
    }

    // ==================== ROUTE CALCULATION & DIJKSTRA ====================

    /**
     * Calculate or recalculate optimal conveyor route for a specific bag.
     * Enforces: Baggage cannot be routed for loading until operator clearance is confirmed!
     */
    public synchronized ConveyorRoutePlanner.RouteResult calculateRouteForBaggage(String baggageId, String triggeredBy) {
        Baggage bag = baggageMap.get(baggageId != null ? baggageId.toUpperCase() : "");
        if (bag == null) {
            return new ConveyorRoutePlanner.RouteResult(false, null, null, 0, "NO_ROUTE", "Baggage record not found: " + baggageId);
        }

        if ("NOT_FOUND".equalsIgnoreCase(bag.getStatus())) {
            return new ConveyorRoutePlanner.RouteResult(false, null, null, 0, "NOT_FOUND",
                    "Baggage is marked as not found. Confirm its location before calculating a route.");
        }

        // Rule: Baggage cannot be routed for loading until operator clearance is confirmed!
        if (!bag.isOperatorClearance()) {
            String msg = "Routing Rejected: Baggage " + baggageId + " has not received operator security clearance.";
            recordTracking(bag.getBaggageId(), bag.getCurrentLocation(), "AWAITING_CLEARANCE", msg, triggeredBy);
            return new ConveyorRoutePlanner.RouteResult(false, null, null, 0, "UNROUTED", msg);
        }

        Flight flight = flightMap.get(bag.getFlightNumber().toUpperCase());
        if (flight == null) {
            return new ConveyorRoutePlanner.RouteResult(false, null, null, 0, "NO_ROUTE", "Assigned flight " + bag.getFlightNumber() + " not found.");
        }

        String destinationBay = flight.getAssignedBayId();
        String startNode = bag.getCurrentLocation();
        // If current location is already a loading bay and loaded, return current status
        if ("LOADED".equalsIgnoreCase(bag.getStatus()) || startNode.equalsIgnoreCase(destinationBay)) {
            return new ConveyorRoutePlanner.RouteResult(true, Collections.singletonList(destinationBay),
                    Collections.emptyList(), 0, "FEASIBLE", "Baggage already arrived at " + destinationBay);
        }

        // Calculate route using Dijkstra
        double cutoffRemaining = flight.getCutoffMinutesRemaining();
        double availableTravelTime = cutoffRemaining - bag.getEstimatedRemainingProcessingTime();
        ConveyorRoutePlanner.RouteResult result = routePlanner.calculateRoute(
                conveyorGraph, startNode, destinationBay, availableTravelTime);

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        bag.setLastUpdated(now);
        bag.setAssignedBayId(destinationBay);
        bag.setCutoffMinutesRemaining(cutoffRemaining);

        if (result.isSuccess()) {
            bag.setRoutePath(result.getFormattedPath());
            bag.setEstimatedTravelTime(result.getTotalTimeMinutes());
            bag.setCutoffFeasibility(result.getFeasibility());

            if ("MISSED_CUTOFF".equalsIgnoreCase(result.getFeasibility())) {
                bag.setStatus("MISSED_CUTOFF");
            } else if ("AT_RISK".equalsIgnoreCase(result.getFeasibility())) {
                bag.setStatus("CUTOFF_AT_RISK");
            } else {
                if ("AWAITING_CLEARANCE".equalsIgnoreCase(bag.getStatus()) || "ROUTE_FAILURE".equalsIgnoreCase(bag.getStatus())) {
                    bag.setStatus("IN_TRANSIT");
                }
            }

            // Record routing history
            String routingId = "RTE-" + System.currentTimeMillis() % 100000;
            RoutingRecord rec = new RoutingRecord(routingId, bag.getBaggageId(), now, startNode,
                    destinationBay, result.getFormattedPath(), result.getTotalTimeMinutes(),
                    !"MISSED_CUTOFF".equalsIgnoreCase(result.getFeasibility()),
                    result.getMessage());
            csvService.addRoutingRecord(rec);

            recordTracking(bag.getBaggageId(), startNode, bag.getStatus(),
                    "Route calculated to " + destinationBay + " (" + result.getTotalTimeMinutes() + "m). Feasibility: " + result.getFeasibility(),
                    triggeredBy);
        } else {
            bag.setStatus("ROUTE_FAILURE");
            bag.setCutoffFeasibility("NO_ROUTE");
            bag.setRoutePath("No valid route from " + startNode + " to " + destinationBay);
            bag.setEstimatedTravelTime(0.0);

            String routingId = "RTE-" + System.currentTimeMillis() % 100000;
            RoutingRecord rec = new RoutingRecord(routingId, bag.getBaggageId(), now, startNode,
                    destinationBay, "NONE", 0, false, result.getMessage());
            csvService.addRoutingRecord(rec);

            recordTracking(bag.getBaggageId(), startNode, "ROUTE_FAILURE",
                    "Route calculation failed: " + result.getMessage(), triggeredBy);
        }

        csvService.updateBaggage(bag);
        return result;
    }

    // ==================== CONVEYOR FAILURE & RECOVERY ====================

    /**
     * Mark a conveyor segment as failed or restored. Route calculations remain an explicit admin action.
     */
    public synchronized Map<String, Object> toggleConveyorSegment(String segmentId, boolean makeActive, String operator) {
        Map<String, Object> response = new HashMap<>();
        ConveyorSegment seg = segmentMap.get(segmentId != null ? segmentId.toUpperCase() : "");
        if (seg == null) {
            response.put("success", false);
            response.put("message", "Conveyor segment not found: " + segmentId);
            return response;
        }

        seg.setStatus(makeActive ? "ACTIVE" : "FAILED");
        csvService.updateConveyor(seg);
        conveyorGraph.setSegmentStatus(seg.getSegmentId(), makeActive);

        List<String> affectedBags = new ArrayList<>();

        for (Baggage bag : baggageMap.values()) {
            if ("LOADED".equalsIgnoreCase(bag.getStatus()) || "NOT_FOUND".equalsIgnoreCase(bag.getStatus())
                    || !bag.isOperatorClearance()) {
                continue;
            }

            boolean routeUsesSegment = false;
            if (bag.getRoutePath() != null) {
                String[] routeNodes = bag.getRoutePath().split("\\s*->\\s*");
                for (int i = 0; i + 1 < routeNodes.length; i++) {
                    if (seg.getStartNode().equalsIgnoreCase(routeNodes[i])
                            && seg.getEndNode().equalsIgnoreCase(routeNodes[i + 1])) {
                        routeUsesSegment = true;
                        break;
                    }
                }
            }

            if (routeUsesSegment) affectedBags.add(bag.getBaggageId());
        }

        response.put("success", true);
        response.put("segmentId", seg.getSegmentId());
        response.put("newStatus", seg.getStatus());
        response.put("affectedBaggageCount", affectedBags.size());
        response.put("affectedBags", affectedBags);
        response.put("message", makeActive
                ? "Segment " + seg.getSegmentId() + " restored to ACTIVE. " + affectedBags.size()
                    + " current route(s) may be recalculated using the Calculate Route action."
                : "Segment " + seg.getSegmentId() + " marked as FAILED. " + affectedBags.size()
                    + " current route(s) are affected. Calculate Route to find the next optimal available path.");

        return response;
    }

    // ==================== FLIGHT & BAY MANAGEMENT ====================

    public synchronized Map<String, Object> addFlight(String flightNumber, String destination, String departure,
            String cutoff, double cutoffMinutesRemaining, String bayId, String status,
            boolean confirmed, String operator) {
        Map<String, Object> response = new LinkedHashMap<>();
        String normalizedFlightNumber = flightNumber == null ? "" : flightNumber.trim().toUpperCase();
        String normalizedDestination = destination == null ? "" : destination.trim();
        String normalizedBayId = bayId == null ? "" : bayId.trim().toUpperCase();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();

        if (!confirmed) {
            response.put("success", false);
            response.put("message", "Administrator confirmation is required before adding a flight.");
            return response;
        }
        if (!normalizedFlightNumber.matches("[A-Z0-9][A-Z0-9-]{1,9}")) {
            response.put("success", false);
            response.put("message", "Flight number must be 2 to 10 letters, numbers, or hyphens.");
            return response;
        }
        if (flightMap.containsKey(normalizedFlightNumber)) {
            response.put("success", false);
            response.put("message", "A flight with this flight number already exists.");
            return response;
        }
        if (normalizedDestination.isEmpty() || normalizedDestination.length() > 100) {
            response.put("success", false);
            response.put("message", "Enter a destination of 1–100 characters.");
            return response;
        }

        LocalTime departureTime;
        LocalTime cutoffTime;
        try {
            DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
            departureTime = LocalTime.parse(departure, timeFormat);
            cutoffTime = LocalTime.parse(cutoff, timeFormat);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", "Departure and loading cutoff must be valid 24-hour times (HH:mm).");
            return response;
        }
        if (!cutoffTime.isBefore(departureTime)) {
            response.put("success", false);
            response.put("message", "Loading cutoff must be earlier than the scheduled departure.");
            return response;
        }
        if (!Double.isFinite(cutoffMinutesRemaining) || cutoffMinutesRemaining < 0 || cutoffMinutesRemaining > 1440) {
            response.put("success", false);
            response.put("message", "Minutes remaining until cutoff must be between 0 and 1440.");
            return response;
        }

        LoadingBay bay = bayMap.get(normalizedBayId);
        if (bay == null || !"ACTIVE".equalsIgnoreCase(bay.getStatus())) {
            response.put("success", false);
            response.put("message", "Select an existing active loading bay.");
            return response;
        }
        Set<String> validStatuses = new HashSet<>(Arrays.asList("SCHEDULED", "BOARDING", "LOADING_CLOSED", "DEPARTED"));
        if (!validStatuses.contains(normalizedStatus)) {
            response.put("success", false);
            response.put("message", "Select a valid flight status.");
            return response;
        }

        Flight flight = new Flight(normalizedFlightNumber, normalizedDestination, departure.trim(), cutoff.trim(),
                cutoffMinutesRemaining, normalizedBayId, normalizedStatus);
        csvService.addFlight(flight);
        flightMap.put(normalizedFlightNumber, flight);
        if (bay.getAssignedFlight() == null || bay.getAssignedFlight().trim().isEmpty()) {
            bay.setAssignedFlight(normalizedFlightNumber);
            csvService.updateLoadingBay(bay);
        }

        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<FlightAuditEntry> auditEntries = new ArrayList<>();
        addFlightAuditIfChanged(auditEntries, normalizedFlightNumber, "flightNumber", "", normalizedFlightNumber, operator, createdAt);
        addFlightAuditIfChanged(auditEntries, normalizedFlightNumber, "destination", "", normalizedDestination, operator, createdAt);
        addFlightAuditIfChanged(auditEntries, normalizedFlightNumber, "scheduledDeparture", "", departure.trim(), operator, createdAt);
        addFlightAuditIfChanged(auditEntries, normalizedFlightNumber, "loadingCutoff", "", cutoff.trim(), operator, createdAt);
        addFlightAuditIfChanged(auditEntries, normalizedFlightNumber, "cutoffMinutesRemaining", "", String.valueOf(cutoffMinutesRemaining), operator, createdAt);
        addFlightAuditIfChanged(auditEntries, normalizedFlightNumber, "assignedBayId", "", normalizedBayId, operator, createdAt);
        addFlightAuditIfChanged(auditEntries, normalizedFlightNumber, "status", "", normalizedStatus, operator, createdAt);
        csvService.addFlightAuditEntries(auditEntries);
        response.put("success", true);
        response.put("flight", flight);
        response.put("message", "Flight " + normalizedFlightNumber + " was added successfully.");
        return response;
    }

    public synchronized Map<String, Object> updateFlightSchedule(String flightNumber, String departure,
            String cutoff, double cutoffMinutesRemaining, String bayId, String status,
            boolean confirmed, String operator) {
        Map<String, Object> response = new HashMap<>();
        Flight flight = flightMap.get(flightNumber != null ? flightNumber.trim().toUpperCase() : "");
        if (flight == null) {
            response.put("success", false);
            response.put("message", "Flight not found: " + flightNumber);
            return response;
        }
        if (!confirmed) {
            response.put("success", false);
            response.put("message", "Administrator confirmation is required before saving flight schedule changes.");
            return response;
        }
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime departureTime;
        LocalTime cutoffTime;
        try {
            departureTime = LocalTime.parse(departure, timeFormat);
            cutoffTime = LocalTime.parse(cutoff, timeFormat);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", "Departure and loading cutoff must be valid 24-hour times (HH:mm).");
            return response;
        }
        if (!cutoffTime.isBefore(departureTime)) {
            response.put("success", false);
            response.put("message", "Loading cutoff must be earlier than the scheduled departure.");
            return response;
        }
        if (!Double.isFinite(cutoffMinutesRemaining) || cutoffMinutesRemaining < 0 || cutoffMinutesRemaining > 1440) {
            response.put("success", false);
            response.put("message", "Minutes remaining until cutoff must be between 0 and 1440.");
            return response;
        }
        LoadingBay newBay = bayMap.get(bayId != null ? bayId.trim().toUpperCase() : "");
        if (newBay == null || !"ACTIVE".equalsIgnoreCase(newBay.getStatus())) {
            response.put("success", false);
            response.put("message", "Select an existing active loading bay.");
            return response;
        }
        boolean bayChanged = flight.getAssignedBayId() == null
                || !flight.getAssignedBayId().equalsIgnoreCase(newBay.getBayId());
        if (bayChanged && flightMap.values().stream().anyMatch(otherFlight ->
                !otherFlight.getFlightNumber().equalsIgnoreCase(flight.getFlightNumber())
                        && otherFlight.getAssignedBayId() != null
                        && otherFlight.getAssignedBayId().equalsIgnoreCase(newBay.getBayId()))) {
            response.put("success", false);
            response.put("message", "The selected loading bay is already assigned to another flight.");
            return response;
        }
        Set<String> validStatuses = new HashSet<>(Arrays.asList("SCHEDULED", "BOARDING", "LOADING_CLOSED", "DEPARTED"));
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!validStatuses.contains(normalizedStatus)) {
            response.put("success", false);
            response.put("message", "Select a valid flight status.");
            return response;
        }
        String oldDeparture = flight.getScheduledDeparture();
        String oldCutoff = flight.getLoadingCutoff();
        String oldMinutes = String.valueOf(flight.getCutoffMinutesRemaining());
        String oldBayId = flight.getAssignedBayId();
        String oldStatus = flight.getStatus();
        String normalizedBay = newBay.getBayId();
        List<FlightAuditEntry> auditEntries = new ArrayList<>();
        String changedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        addFlightAuditIfChanged(auditEntries, flight.getFlightNumber(), "scheduledDeparture", oldDeparture, departure, operator, changedAt);
        addFlightAuditIfChanged(auditEntries, flight.getFlightNumber(), "loadingCutoff", oldCutoff, cutoff, operator, changedAt);
        addFlightAuditIfChanged(auditEntries, flight.getFlightNumber(), "cutoffMinutesRemaining", oldMinutes, String.valueOf(cutoffMinutesRemaining), operator, changedAt);
        addFlightAuditIfChanged(auditEntries, flight.getFlightNumber(), "assignedBayId", oldBayId, normalizedBay, operator, changedAt);
        addFlightAuditIfChanged(auditEntries, flight.getFlightNumber(), "status", oldStatus, normalizedStatus, operator, changedAt);

        flight.setScheduledDeparture(departure);
        flight.setLoadingCutoff(cutoff);
        flight.setCutoffMinutesRemaining(cutoffMinutesRemaining);
        flight.setAssignedBayId(normalizedBay);
        flight.setStatus(normalizedStatus);
        csvService.updateFlight(flight);
        if (!auditEntries.isEmpty()) csvService.addFlightAuditEntries(auditEntries);

        if (bayChanged && oldBayId != null) {
            LoadingBay oldBay = bayMap.get(oldBayId.toUpperCase());
            if (oldBay != null && flight.getFlightNumber().equalsIgnoreCase(oldBay.getAssignedFlight())) {
                oldBay.setAssignedFlight("");
                csvService.updateLoadingBay(oldBay);
            }
            newBay.setAssignedFlight(flight.getFlightNumber());
            csvService.updateLoadingBay(newBay);
        }

        int updatedCount = 0;
        int recalculatedCount = 0;
        int atRiskCount = 0;
        int missedCount = 0;
        List<String> urgentWarnings = new ArrayList<>();
        for (Baggage bag : baggageMap.values()) {
            if (!bag.getFlightNumber().equalsIgnoreCase(flight.getFlightNumber())) continue;
            bag.setCutoffMinutesRemaining(cutoffMinutesRemaining);
            bag.setAssignedBayId(normalizedBay);
            boolean unavailable = "LOADED".equalsIgnoreCase(bag.getStatus())
                    || "NOT_FOUND".equalsIgnoreCase(bag.getStatus());
            if (!unavailable && !bag.isOperatorClearance()) {
                bag.setCutoffFeasibility("UNROUTED");
            } else if (!unavailable && bayChanged) {
                ConveyorRoutePlanner.RouteResult route = calculateRouteForBaggage(
                        bag.getBaggageId(), "Flight schedule bay reassignment by " + operator);
                recalculatedCount++;
                if (!route.isSuccess()) {
                    urgentWarnings.add(bag.getBaggageId() + " has no available route to " + normalizedBay + ".");
                }
            }
            if (!unavailable && bag.isOperatorClearance()
                    && !"ROUTE_FAILURE".equalsIgnoreCase(bag.getStatus()) && bag.getEstimatedTravelTime() > 0) {
                double slack = cutoffMinutesRemaining
                        - bag.getEstimatedRemainingProcessingTime() - bag.getEstimatedTravelTime();
                if (slack < 0) {
                    bag.setStatus("MISSED_CUTOFF");
                    bag.setCutoffFeasibility("MISSED_CUTOFF");
                    missedCount++;
                    urgentWarnings.add(bag.getBaggageId() + " is estimated to miss the loading cutoff.");
                } else if (slack <= 10) {
                    bag.setStatus("CUTOFF_AT_RISK");
                    bag.setCutoffFeasibility("AT_RISK");
                    atRiskCount++;
                    urgentWarnings.add(bag.getBaggageId() + " is within 10 minutes of the loading cutoff.");
                } else {
                    bag.setCutoffFeasibility("FEASIBLE");
                    if ("CUTOFF_AT_RISK".equalsIgnoreCase(bag.getStatus())
                            || "MISSED_CUTOFF".equalsIgnoreCase(bag.getStatus())) bag.setStatus("IN_TRANSIT");
                }
            }
            csvService.updateBaggage(bag);
            updatedCount++;
        }
        refreshSchedulingQueue();
        response.put("success", true);
        response.put("flightNumber", flight.getFlightNumber());
        response.put("newCutoffMinutes", cutoffMinutesRemaining);
        response.put("evaluatedBags", updatedCount);
        response.put("recalculatedRoutes", recalculatedCount);
        response.put("atRiskCount", atRiskCount);
        response.put("missedCount", missedCount);
        response.put("urgentWarnings", urgentWarnings);
        response.put("message", "Flight schedule saved. Reevaluated " + updatedCount + " baggage records: "
                + atRiskCount + " at risk, " + missedCount + " estimated to miss cutoff.");
        return response;
    }

    private void addFlightAuditIfChanged(List<FlightAuditEntry> entries, String flightNumber, String field,
                                         String previous, String updated, String operator, String changedAt) {
        String oldValue = previous == null ? "" : previous;
        String newValue = updated == null ? "" : updated;
        if (!oldValue.equals(newValue)) {
            entries.add(new FlightAuditEntry("AUD-" + UUID.randomUUID(), flightNumber, field,
                    oldValue, newValue, operator, changedAt));
        }
    }

    private void refreshSchedulingQueue() {
        baggageSchedulingQueue.clear();
        for (Baggage baggage : baggageMap.values()) {
            if (baggage.isOperatorClearance() && !"LOADED".equalsIgnoreCase(baggage.getStatus())
                    && !"NOT_FOUND".equalsIgnoreCase(baggage.getStatus())) baggageSchedulingQueue.add(baggage);
        }
    }

    public synchronized Map<String, Object> updateFlightLoadingBay(String flightNumber, String newBayId, String operator) {
        Flight flight = flightMap.get(flightNumber != null ? flightNumber.toUpperCase() : "");
        if (flight == null) return Collections.singletonMap("success", false);
        return updateFlightSchedule(flightNumber, flight.getScheduledDeparture(), flight.getLoadingCutoff(),
                flight.getCutoffMinutesRemaining(), newBayId, flight.getStatus(), true, operator);
    }

    public synchronized Map<String, Object> updateFlightCutoff(String flightNumber, double newCutoffMinutesRemaining,
                                                                String cutoffTimeStr, String operator) {
        Flight flight = flightMap.get(flightNumber != null ? flightNumber.toUpperCase() : "");
        if (flight == null) return Collections.singletonMap("success", false);
        return updateFlightSchedule(flightNumber, flight.getScheduledDeparture(),
                cutoffTimeStr == null ? flight.getLoadingCutoff() : cutoffTimeStr,
                newCutoffMinutesRemaining, flight.getAssignedBayId(), flight.getStatus(), true, operator);
    }

    // ==================== OPERATIONAL CLEARANCE & STATUS ====================

    /**
     * Grant or revoke operator clearance for baggage.
     */
    public synchronized Map<String, Object> setOperatorClearance(String baggageId, boolean cleared, String operator) {
        Map<String, Object> response = new HashMap<>();
        Baggage bag = baggageMap.get(baggageId != null ? baggageId.toUpperCase() : "");
        if (bag == null) {
            response.put("success", false);
            response.put("message", "Baggage not found: " + baggageId);
            return response;
        }

        bag.setOperatorClearance(cleared);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        bag.setLastUpdated(now);

        if (cleared) {
            bag.setStatus("IN_TRANSIT");
            csvService.updateBaggage(bag);
            recordTracking(bag.getBaggageId(), bag.getCurrentLocation(), "IN_TRANSIT",
                    "Operator clearance granted. Baggage approved for automated conveyor routing.", operator);
            // Automatically calculate initial route once cleared
            calculateRouteForBaggage(bag.getBaggageId(), operator);
        } else {
            bag.setStatus("AWAITING_CLEARANCE");
            bag.setCutoffFeasibility("UNROUTED");
            bag.setRoutePath("Pending Operator Security Clearance");
            bag.setEstimatedTravelTime(0.0);
            csvService.updateBaggage(bag);
            recordTracking(bag.getBaggageId(), bag.getCurrentLocation(), "AWAITING_CLEARANCE",
                    "Operator clearance revoked / held for manual inspection.", operator);
        }

        response.put("success", true);
        response.put("baggageId", bag.getBaggageId());
        response.put("cleared", cleared);
        response.put("status", bag.getStatus());
        response.put("message", cleared ? "Operator clearance granted. Route calculated." : "Clearance held.");
        return response;
    }

    /**
     * Update baggage operational status.
     */
    public synchronized Map<String, Object> updateBaggageStatus(String baggageId, String newStatus,
                                                                 String locationNode, String operator,
                                                                 boolean loadingConfirmed) {
        Map<String, Object> response = new HashMap<>();
        Baggage bag = baggageMap.get(baggageId != null ? baggageId.toUpperCase() : "");
        if (bag == null) {
            response.put("success", false);
            response.put("message", "Baggage not found: " + baggageId);
            return response;
        }

        if (newStatus == null || newStatus.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "A baggage status is required.");
            return response;
        }
        String normalizedStatus = newStatus.trim().toUpperCase();
        Set<String> supportedStatuses = new HashSet<>(Arrays.asList(
                "AWAITING_CLEARANCE", "IN_TRANSIT", "CUTOFF_AT_RISK", "MISSED_CUTOFF", "ROUTE_FAILURE", "LOADED", "NOT_FOUND"));
        if (!supportedStatuses.contains(normalizedStatus)) {
            response.put("success", false);
            response.put("message", "Unsupported baggage status: " + newStatus);
            return response;
        }
        if ("LOADED".equals(normalizedStatus)
                && (!loadingConfirmed || !bag.isOperatorClearance())) {
            response.put("success", false);
            response.put("message", "Loading cannot be recorded until an operator confirms loading for cleared baggage.");
            return response;
        }
        String targetLocation = locationNode == null || locationNode.trim().isEmpty()
                ? bag.getCurrentLocation() : locationNode.trim().toUpperCase();
        if ("LOADED".equals(normalizedStatus)
                && !bag.getAssignedBayId().equalsIgnoreCase(targetLocation)) {
            response.put("success", false);
            response.put("message", "Loading confirmation must be recorded at the baggage's assigned loading bay.");
            return response;
        }

        bag.setStatus(normalizedStatus);
        if (locationNode != null && !locationNode.trim().isEmpty()) {
            bag.setCurrentLocation(locationNode.trim().toUpperCase());
        }
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        bag.setLastUpdated(now);

        if ("LOADED".equals(normalizedStatus)) {
            bag.setCutoffFeasibility("FEASIBLE");
        }

        csvService.updateBaggage(bag);
        recordTracking(bag.getBaggageId(), bag.getCurrentLocation(), bag.getStatus(),
                "Operational status updated to " + bag.getStatus() + " at node " + bag.getCurrentLocation(), operator);

        response.put("success", true);
        response.put("baggageId", bag.getBaggageId());
        response.put("status", bag.getStatus());
        response.put("location", bag.getCurrentLocation());
        return response;
    }

    private void recordTracking(String baggageId, String location, String status, String message, String recordedBy) {
        String historyId = "EVT-" + System.currentTimeMillis() % 1000000;
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        TrackingEvent event = new TrackingEvent(historyId, baggageId, now, location, status, message,
                recordedBy != null ? recordedBy : "System");
        csvService.addTrackingEvent(event);
    }

    // ==================== DASHBOARD & METRICS ====================

    /**
     * Dynamic metrics calculation from CSV data (NOT hardcoded!).
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        int total = baggageMap.size();
        int awaitingClearance = 0;
        int inTransit = 0;
        int loaded = 0;
        int cutoffAtRisk = 0;
        int missedCutoff = 0;
        int routeFailures = 0;

        for (Baggage b : baggageMap.values()) {
            String s = b.getStatus().toUpperCase();
            switch (s) {
                case "AWAITING_CLEARANCE": awaitingClearance++; break;
                case "IN_TRANSIT": inTransit++; break;
                case "LOADED": loaded++; break;
                case "CUTOFF_AT_RISK": cutoffAtRisk++; break;
                case "MISSED_CUTOFF": missedCutoff++; break;
                case "ROUTE_FAILURE": routeFailures++; break;
            }
        }

        int failedSegments = 0;
        for (ConveyorSegment c : segmentMap.values()) {
            if (!c.isActive()) {
                failedSegments++;
            }
        }

        stats.put("totalBaggage", total);
        stats.put("awaitingClearance", awaitingClearance);
        stats.put("baggageInTransit", inTransit);
        stats.put("successfullyLoaded", loaded);
        stats.put("cutoffAtRisk", cutoffAtRisk);
        stats.put("missedCutoff", missedCutoff);
        stats.put("routeFailures", routeFailures);
        stats.put("failedConveyorSegments", failedSegments);
        stats.put("activeFlights", flightMap.size());
        stats.put("loadingBaysCount", bayMap.size());
        return stats;
    }

    public List<Baggage> getAllBaggage() {
        return new ArrayList<>(baggageMap.values());
    }

    public Baggage getBaggageById(String id) {
        return baggageMap.get(id != null ? id.toUpperCase() : "");
    }

    public List<Baggage> getBaggageForPassenger(String passengerId) {
        List<Baggage> list = new ArrayList<>();
        if (passengerId == null) return list;
        for (Baggage b : baggageMap.values()) {
            if (passengerId.equalsIgnoreCase(b.getPassengerId())) {
                list.add(b);
            }
        }
        return list;
    }

    public List<TrackingEvent> getTrackingHistoryForBaggage(String baggageId) {
        List<TrackingEvent> events = new ArrayList<>();
        if (baggageId == null) return events;
        for (TrackingEvent e : csvService.getAllTrackingHistory()) {
            if (baggageId.equalsIgnoreCase(e.getBaggageId())) {
                events.add(e);
            }
        }
        return events;
    }

    public String validatePassengerBaggage(List<Map<String, String>> details) {
        if (details == null || details.isEmpty() || details.size() > 10) {
            return "Register between 1 and 10 baggage units.";
        }
        for (int i = 0; i < details.size(); i++) {
            Map<String, String> detail = details.get(i);
            String name = detail.get("name");
            String type = detail.get("type");
            String flightNumber = detail.get("flightNumber");
            if (name == null || name.trim().isEmpty() || name.trim().length() > 80) {
                return "Baggage " + (i + 1) + " needs a name between 1 and 80 characters.";
            }
            if (!"ORIGIN".equalsIgnoreCase(type) && !"TRANSFER".equalsIgnoreCase(type)) {
                return "Choose a valid baggage type for baggage " + (i + 1) + ".";
            }
            if (flightNumber == null || !flightMap.containsKey(flightNumber.trim().toUpperCase())) {
                return "Choose a valid flight for baggage " + (i + 1) + ".";
            }
        }
        return null;
    }

    public synchronized List<String> registerPassengerBaggage(Passenger passenger, List<Map<String, String>> details) {
        String validationError = validatePassengerBaggage(details);
        if (validationError != null) throw new IllegalArgumentException(validationError);

        int nextId = 1000;
        for (String id : baggageMap.keySet()) {
            if (id.matches("(?i)BAG-\\d+")) {
                try { nextId = Math.max(nextId, Integer.parseInt(id.substring(4))); }
                catch (NumberFormatException ignored) {}
            }
        }

        List<String> generatedIds = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        for (Map<String, String> detail : details) {
            Flight flight = flightMap.get(detail.get("flightNumber").trim().toUpperCase());
            String baggageId = String.format("BAG-%04d", ++nextId);
            String type = detail.get("type").trim().toUpperCase();
            String initialLocation = "TRANSFER".equals(type) ? "N3" : "N1";
            Baggage bag;
            if ("TRANSFER".equals(type)) {
                bag = new TransferBag(baggageId, passenger.getPassengerId(), passenger.getBookingReference(),
                        flight.getFlightNumber(), 0.0, false, initialLocation, "AWAITING_CLEARANCE",
                        flight.getAssignedBayId(), "Pending Operator Clearance", 0.0, "UNROUTED",
                        flight.getCutoffMinutesRemaining(), now, now, "Passenger registration", 0.0);
            } else {
                bag = new OriginBag(baggageId, passenger.getPassengerId(), passenger.getBookingReference(),
                        flight.getFlightNumber(), 0.0, false, initialLocation, "AWAITING_CLEARANCE",
                        flight.getAssignedBayId(), "Pending Operator Clearance", 0.0, "UNROUTED",
                        flight.getCutoffMinutesRemaining(), now, now, "Passenger registration", "ECONOMY");
            }
            bag.setBaggageName(detail.get("name").trim());
            csvService.addBaggage(bag);
            baggageMap.put(baggageId, bag);
            generatedIds.add(baggageId);
            recordTracking(baggageId, initialLocation, "AWAITING_CLEARANCE",
                    "Baggage registered by passenger and awaiting operator clearance.", passenger.getFullName());
        }
        return generatedIds;
    }

    public Map<String, Flight> getFlightMap() { return flightMap; }
    public Map<String, LoadingBay> getBayMap() { return bayMap; }
    public Map<String, ConveyorSegment> getSegmentMap() { return segmentMap; }
    public Graph getConveyorGraph() { return conveyorGraph; }
    public CsvFileService getCsvService() { return csvService; }

    // ==================== PRIORITY QUEUE METHODS ====================

    /**
     * Returns operator-cleared, non-loaded baggage sorted by priority:
     * 1. Earliest cutoff first.
     * 2. Transfer > Origin (higher priority score) for equal cutoffs.
     * 3. Passenger category (FIRST_CLASS > BUSINESS > ECONOMY).
     * 4. Infeed time (arrival order).
     */
    public List<Map<String, Object>> getPriorityQueue() {
        PriorityQueue<Baggage> pq = new PriorityQueue<>();
        for (Baggage b : baggageMap.values()) {
            if (b.isOperatorClearance() && !"LOADED".equalsIgnoreCase(b.getStatus())
                    && !"NOT_FOUND".equalsIgnoreCase(b.getStatus())) {
                pq.add(b);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        while (!pq.isEmpty()) {
            Baggage b = pq.poll();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", rank++);
            entry.put("baggageId", b.getBaggageId());
            entry.put("passengerId", b.getPassengerId());
            entry.put("flightNumber", b.getFlightNumber());
            entry.put("currentLocation", b.getCurrentLocation());
            entry.put("assignedBayId", b.getAssignedBayId());
            entry.put("cutoffMinutesRemaining", b.getCutoffMinutesRemaining());
            entry.put("estimatedTravelTime", b.getEstimatedTravelTime());
            entry.put("estimatedRemainingProcessingTime", b.getEstimatedRemainingProcessingTime());
            entry.put("estimatedArrivalTime", getEstimatedArrivalTime(b));
            entry.put("cutoffFeasibility", b.getCutoffFeasibility());
            entry.put("status", b.getStatus());
            entry.put("baggageType", b.getBaggageType());
            entry.put("priorityScore", b.getHandlingPriorityScore());
            entry.put("infeedTime", b.getInfeedTime());
            double remainingWork = b.getEstimatedRemainingProcessingTime() + b.getEstimatedTravelTime();
            boolean urgent = b.getEstimatedTravelTime() > 0 &&
                (b.getCutoffMinutesRemaining() - remainingWork <= 10);
            entry.put("isUrgent", urgent);
            String cat = (b instanceof OriginBag) ? ((OriginBag) b).getPassengerCategory() : "TRANSFER";
            entry.put("passengerCategory", cat);
            result.add(entry);
        }
        return result;
    }

    /**
     * Returns baggage flagged as urgent (at-risk, missed cutoff, route failure, or travel time within 10 min of cutoff).
     */
    public List<Map<String, Object>> getUrgentBaggage() {
        List<Baggage> urgentBags = new ArrayList<>();
        for (Baggage b : baggageMap.values()) {
            if (!b.isOperatorClearance() || "LOADED".equalsIgnoreCase(b.getStatus())
                    || "NOT_FOUND".equalsIgnoreCase(b.getStatus())) continue;
            boolean statusRisk = "CUTOFF_AT_RISK".equalsIgnoreCase(b.getStatus())
                              || "MISSED_CUTOFF".equalsIgnoreCase(b.getStatus())
                              || "ROUTE_FAILURE".equalsIgnoreCase(b.getStatus());
            boolean timeRisk = b.getEstimatedTravelTime() > 0 &&
                (b.getCutoffMinutesRemaining() - b.getEstimatedRemainingProcessingTime() - b.getEstimatedTravelTime() <= 10);
            if (statusRisk || timeRisk) urgentBags.add(b);
        }
        urgentBags.sort(Comparator.comparingDouble(Baggage::getCutoffMinutesRemaining));
        List<Map<String, Object>> urgent = new ArrayList<>();
        for (Baggage b : urgentBags) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("baggageId", b.getBaggageId());
            item.put("flightNumber", b.getFlightNumber());
            item.put("currentLocation", b.getCurrentLocation());
            item.put("assignedBayId", b.getAssignedBayId());
            item.put("status", b.getStatus());
            item.put("cutoffFeasibility", b.getCutoffFeasibility());
            item.put("cutoffMinutesRemaining", b.getCutoffMinutesRemaining());
            item.put("estimatedTravelTime", b.getEstimatedTravelTime());
            item.put("estimatedRemainingProcessingTime", b.getEstimatedRemainingProcessingTime());
            item.put("estimatedArrivalTime", getEstimatedArrivalTime(b));
            urgent.add(item);
        }
        return urgent;
    }

    /**
     * Returns priority status for a specific passenger's baggage.
     */
    public List<Map<String, Object>> getPriorityStatusForPassenger(String passengerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (passengerId == null) return result;
        for (Baggage b : baggageMap.values()) {
            if (!passengerId.equalsIgnoreCase(b.getPassengerId())) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("baggageId", b.getBaggageId());
            entry.put("baggageName", b.getBaggageName());
            entry.put("baggageType", b.getBaggageType());
            entry.put("flightNumber", b.getFlightNumber());
            Flight flight = flightMap.get(b.getFlightNumber().toUpperCase());
            if (flight != null) {
                entry.put("destination", flight.getDestination());
                entry.put("scheduledDeparture", flight.getScheduledDeparture());
                entry.put("loadingCutoff", flight.getLoadingCutoff());
                entry.put("flightStatus", flight.getStatus());
                entry.put("assignedBayId", flight.getAssignedBayId());
            }
            entry.put("currentLocation", b.getCurrentLocation());
            entry.put("status", b.getStatus());
            entry.put("cutoffFeasibility", b.getCutoffFeasibility());
            entry.put("estimatedTravelTime", b.getEstimatedTravelTime());
            entry.put("estimatedArrivalTime", getEstimatedArrivalTime(b));
            entry.put("estimatedRemainingProcessingTime", b.getEstimatedRemainingProcessingTime());
            entry.put("cutoffMinutesRemaining", b.getCutoffMinutesRemaining());
            entry.put("cutoffStatus", passengerCutoffStatus(b));
            boolean atRisk = b.getEstimatedTravelTime() > 0 &&
                b.getCutoffMinutesRemaining() - b.getEstimatedRemainingProcessingTime() - b.getEstimatedTravelTime() <= 10;
            entry.put("isAtRisk", atRisk);
            result.add(entry);
        }
        return result;
    }

    private String passengerCutoffStatus(Baggage bag) {
        if ("LOADED".equalsIgnoreCase(bag.getStatus())) return "LOADED";
        if ("NOT_FOUND".equalsIgnoreCase(bag.getStatus())) return "UNROUTED";
        if ("MISSED_CUTOFF".equalsIgnoreCase(bag.getStatus())) return "MISSED_CUTOFF";
        if ("ROUTE_FAILURE".equalsIgnoreCase(bag.getStatus()) || "NO_ROUTE".equalsIgnoreCase(bag.getCutoffFeasibility())) return "ROUTE_FAILURE";
        if ("CUTOFF_AT_RISK".equalsIgnoreCase(bag.getStatus()) || "AT_RISK".equalsIgnoreCase(bag.getCutoffFeasibility())) return "AT_RISK";
        if (!bag.isOperatorClearance()) return "AWAITING_CLEARANCE";
        return bag.getCutoffFeasibility() == null ? "PENDING" : bag.getCutoffFeasibility();
    }

    public String getEstimatedArrivalTime(Baggage bag) {
        if (!bag.isOperatorClearance() || "ROUTE_FAILURE".equalsIgnoreCase(bag.getStatus())
                || "NOT_FOUND".equalsIgnoreCase(bag.getStatus())
                || "NO_ROUTE".equalsIgnoreCase(bag.getCutoffFeasibility())) return "";
        double totalMinutes = bag.getEstimatedRemainingProcessingTime() + bag.getEstimatedTravelTime();
        long seconds = Math.round(totalMinutes * 60.0);
        return LocalDateTime.now().plusSeconds(seconds)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
