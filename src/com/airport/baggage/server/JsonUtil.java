package com.airport.baggage.server;

import com.airport.baggage.model.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Lightweight JSON utility for formatting and parsing REST API payloads.
 */
public class JsonUtil {

    public static String toJson(Object obj) {
        if (obj == null) return "null";

        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            Collection<?> col = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }

        // Domain models
        if (obj instanceof User) {
            User u = (User) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getUserId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            m.put("fullName", u.getFullName());
            m.put("mobileNumber", u.getMobileNumber());
            m.put("createdAt", u.getCreatedAt());
            return toJson(m);
        }

        if (obj instanceof Passenger) {
            Passenger p = (Passenger) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("passengerId", p.getPassengerId());
            m.put("userId", p.getUserId());
            m.put("fullName", p.getFullName());
            m.put("email", p.getEmail());
            m.put("mobileNumber", p.getMobileNumber());
            m.put("bookingReference", p.getBookingReference());
            return toJson(m);
        }

        if (obj instanceof Flight) {
            Flight f = (Flight) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flightNumber", f.getFlightNumber());
            m.put("destination", f.getDestination());
            m.put("scheduledDeparture", f.getScheduledDeparture());
            m.put("loadingCutoff", f.getLoadingCutoff());
            m.put("cutoffMinutesRemaining", f.getCutoffMinutesRemaining());
            m.put("assignedBayId", f.getAssignedBayId());
            m.put("status", f.getStatus());
            return toJson(m);
        }

        if (obj instanceof LoadingBay) {
            LoadingBay b = (LoadingBay) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bayId", b.getBayId());
            m.put("bayName", b.getBayName());
            m.put("assignedFlight", b.getAssignedFlight());
            m.put("capacity", b.getCapacity());
            m.put("status", b.getStatus());
            return toJson(m);
        }

        if (obj instanceof ConveyorSegment) {
            ConveyorSegment s = (ConveyorSegment) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("segmentId", s.getSegmentId());
            m.put("startNode", s.getStartNode());
            m.put("endNode", s.getEndNode());
            m.put("travelTimeMinutes", s.getTravelTimeMinutes());
            m.put("status", s.getStatus());
            m.put("active", s.isActive());
            return toJson(m);
        }

        if (obj instanceof Baggage) {
            Baggage b = (Baggage) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("baggageId", b.getBaggageId());
            m.put("baggageName", b.getBaggageName());
            m.put("baggageType", b.getBaggageType());
            m.put("passengerId", b.getPassengerId());
            m.put("bookingReference", b.getBookingReference());
            m.put("flightNumber", b.getFlightNumber());
            m.put("weightKg", b.getWeightKg());
            m.put("operatorClearance", b.isOperatorClearance());
            m.put("currentLocation", b.getCurrentLocation());
            m.put("status", b.getStatus());
            m.put("assignedBayId", b.getAssignedBayId());
            m.put("routePath", b.getRoutePath());
            m.put("estimatedTravelTime", b.getEstimatedTravelTime());
            String estimatedArrivalTime = "";
            if (b.isOperatorClearance() && !"LOADED".equalsIgnoreCase(b.getStatus())
                    && !"ROUTE_FAILURE".equalsIgnoreCase(b.getStatus())
                    && !"NOT_FOUND".equalsIgnoreCase(b.getStatus())
                    && !"NO_ROUTE".equalsIgnoreCase(b.getCutoffFeasibility())) {
                long remainingSeconds = Math.round((b.getEstimatedRemainingProcessingTime()
                        + b.getEstimatedTravelTime()) * 60.0);
                estimatedArrivalTime = LocalDateTime.now().plusSeconds(remainingSeconds)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            m.put("estimatedArrivalTime", estimatedArrivalTime);
            m.put("estimatedRemainingProcessingTime", b.getEstimatedRemainingProcessingTime());
            m.put("arrivalOrder", b.getArrivalOrder());
            m.put("cutoffFeasibility", b.getCutoffFeasibility());
            m.put("cutoffMinutesRemaining", b.getCutoffMinutesRemaining());
            m.put("infeedTime", b.getInfeedTime());
            m.put("lastUpdated", b.getLastUpdated());
            m.put("typeDetails", b.getSpecificTypeDetails());
            m.put("priorityScore", b.getHandlingPriorityScore());

            if (b instanceof OriginBag) {
                OriginBag ob = (OriginBag) b;
                m.put("checkInDesk", ob.getCheckInDesk());
                m.put("passengerCategory", ob.getPassengerCategory());
            } else if (b instanceof TransferBag) {
                TransferBag tb = (TransferBag) b;
                m.put("inboundFlight", tb.getInboundFlight());
                m.put("connectionWindowMinutes", tb.getConnectionWindowMinutes());
            }
            return toJson(m);
        }

        if (obj instanceof TrackingEvent) {
            TrackingEvent t = (TrackingEvent) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("historyId", t.getHistoryId());
            m.put("baggageId", t.getBaggageId());
            m.put("timestamp", t.getTimestamp());
            m.put("locationNode", t.getLocationNode());
            m.put("status", t.getStatus());
            m.put("message", t.getMessage());
            m.put("recordedBy", t.getRecordedBy());
            return toJson(m);
        }

        if (obj instanceof RoutingRecord) {
            RoutingRecord r = (RoutingRecord) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("routingId", r.getRoutingId());
            m.put("baggageId", r.getBaggageId());
            m.put("calculatedAt", r.getCalculatedAt());
            m.put("startNode", r.getStartNode());
            m.put("destinationBay", r.getDestinationBay());
            m.put("path", r.getPath());
            m.put("totalTimeMinutes", r.getTotalTimeMinutes());
            m.put("feasible", r.isFeasible());
            m.put("failureReason", r.getFailureReason());
            return toJson(m);
        }

        if (obj instanceof com.airport.baggage.test.ScenarioRunner.ScenarioResult) {
            com.airport.baggage.test.ScenarioRunner.ScenarioResult s = (com.airport.baggage.test.ScenarioRunner.ScenarioResult) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("title", s.getTitle());
            m.put("description", s.getDescription());
            m.put("passed", s.isPassed());
            m.put("details", s.getDetails());
            return toJson(m);
        }

        if (obj instanceof com.airport.baggage.model.Complaint) {
            com.airport.baggage.model.Complaint c = (com.airport.baggage.model.Complaint) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("complaintId",   c.getComplaintId());
            m.put("passengerId",   c.getPassengerId());
            m.put("baggageId",     c.getBaggageId());
            m.put("flightNumber",  c.getFlightNumber());
            m.put("category",      c.getCategory());
            m.put("subject",       c.getSubject());
            m.put("description",   c.getDescription());
            m.put("status",        c.getStatus());
            m.put("adminResponse", c.getAdminResponse());
            m.put("createdAt",     c.getCreatedAt());
            m.put("updatedAt",     c.getUpdatedAt());
            return toJson(m);
        }

        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch <= '\u001F' || ch >= '\u007F' && ch <= '\u009F') {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Simple parser for JSON key-value string pairs from request bodies.
     */
    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inQuotes = false;
        boolean readingKey = true;
        boolean escape = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (escape) {
                if (readingKey) key.append(c);
                else value.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes) {
                if (c == ':') {
                    readingKey = false;
                    continue;
                }
                if (c == ',') {
                    String k = key.toString().trim();
                    String v = value.toString().trim();
                    if (!k.isEmpty()) {
                        map.put(k, v);
                    }
                    key.setLength(0);
                    value.setLength(0);
                    readingKey = true;
                    continue;
                }
            }

            if (readingKey) {
                key.append(c);
            } else {
                value.append(c);
            }
        }

        String k = key.toString().trim();
        String v = value.toString().trim();
        if (!k.isEmpty()) {
            map.put(k, v);
        }

        return map;
    }
}
