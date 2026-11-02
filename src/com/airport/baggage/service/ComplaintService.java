package com.airport.baggage.service;

import com.airport.baggage.model.Complaint;
import com.airport.baggage.model.Baggage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Business logic for Passenger Complaints and Feedback management.
 * PassengerID is ALWAYS derived from the authenticated server-side session.
 */
public class ComplaintService {

    private static final Set<String> VALID_CATEGORIES = new LinkedHashSet<>(Arrays.asList(
        "STAFF_BEHAVIOUR", "BAGGAGE_DELAY", "INCORRECT_STATUS", "ROUTING_ERROR",
        "FLIGHT_INFO_ERROR", "BAGGAGE_MISSING", "TECHNICAL_ERROR", "OTHER"
    ));

    private static final Set<String> VALID_STATUSES = new LinkedHashSet<>(Arrays.asList(
        "SUBMITTED", "UNDER_REVIEW", "IN_PROGRESS", "RESOLVED", "CLOSED"
    ));

    private final CsvFileService csvFileService;
    private final BaggageService baggageService;

    public ComplaintService(CsvFileService csvFileService, BaggageService baggageService) {
        this.csvFileService = csvFileService;
        this.baggageService = baggageService;
    }

    /**
     * Submit a new complaint.
     * passengerId is always from the authenticated session – never from the client request body.
     */
    public synchronized Map<String, String> submitComplaint(String passengerId, String baggageId,
                                               String category,
                                               String subject, String description) {
        Map<String, String> result = new LinkedHashMap<>();

        // --- Validation ---
        if (passengerId == null || passengerId.trim().isEmpty()) {
            result.put("error", "Passenger session is required.");
            return result;
        }
        if (category == null || !VALID_CATEGORIES.contains(category.trim().toUpperCase())) {
            result.put("error", "Invalid complaint category. Must be one of: " + VALID_CATEGORIES);
            return result;
        }
        if (subject == null || subject.trim().length() < 5 || subject.trim().length() > 120) {
            result.put("error", "Subject is required and must be between 5 and 120 characters.");
            return result;
        }
        if (description == null || description.trim().length() < 10 || description.trim().length() > 2000) {
            result.put("error", "Description is required and must be between 10 and 2000 characters.");
            return result;
        }

        String cleanBaggageId = baggageId == null ? "" : baggageId.trim();
        String flightNumber = "";
        if (!cleanBaggageId.isEmpty()) {
            Baggage bag = baggageService.getBaggageById(cleanBaggageId);
            if (bag == null) {
                result.put("error", "Selected baggage record was not found.");
                return result;
            }
            if (!passengerId.equalsIgnoreCase(bag.getPassengerId())) {
                result.put("error", "You can only associate a complaint with your own baggage.");
                result.put("httpStatus", "403");
                return result;
            }
            flightNumber = bag.getFlightNumber();
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String complaintId;
        boolean duplicate;
        do {
            complaintId = "CMP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
            duplicate = false;
            for (Complaint existing : csvFileService.getAllComplaints()) {
                if (complaintId.equalsIgnoreCase(existing.getComplaintId())) {
                    duplicate = true;
                    break;
                }
            }
        } while (duplicate);

        Complaint c = new Complaint(
            complaintId,
            passengerId.trim(),
            cleanBaggageId,
            flightNumber,
            category.trim().toUpperCase(),
            subject.trim(),
            description.trim(),
            "SUBMITTED",
            "",
            now,
            now
        );

        csvFileService.addComplaint(c);

        result.put("success", "true");
        result.put("complaintId", complaintId);
        result.put("message", "Your complaint has been submitted successfully.");
        return result;
    }

    /**
     * Get all complaints for a specific passenger (strict ownership check).
     */
    public List<Complaint> getComplaintsForPassenger(String passengerId) {
        List<Complaint> list = new ArrayList<>();
        if (passengerId == null) return list;
        for (Complaint c : csvFileService.getAllComplaints()) {
            if (passengerId.equalsIgnoreCase(c.getPassengerId())) {
                list.add(c);
            }
        }
        // Sort newest first
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }

    /**
     * Get all complaints (admin only – called from authenticated admin route).
     */
    public List<Complaint> getAllComplaints(String statusFilter) {
        List<Complaint> list = new ArrayList<>(csvFileService.getAllComplaints());
        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            list.removeIf(c -> !c.getStatus().equalsIgnoreCase(statusFilter.trim()));
        }
        // Sort newest first
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }

    /**
     * Admin: update complaint status and/or add response.
     */
    public synchronized Map<String, String> updateComplaint(String complaintId, String newStatus,
                                               String adminResponse, String adminName) {
        Map<String, String> result = new LinkedHashMap<>();

        if (complaintId == null || complaintId.trim().isEmpty()) {
            result.put("error", "Complaint ID is required.");
            return result;
        }
        String normalizedStatus = newStatus == null ? "" : newStatus.trim().toUpperCase().replaceAll("\\s+", "_");
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            result.put("error", "Invalid status. Must be one of: " + VALID_STATUSES);
            return result;
        }
        if (adminResponse != null && adminResponse.trim().length() > 2000) {
            result.put("error", "Admin response must not exceed 2000 characters.");
            return result;
        }

        Complaint found = null;
        for (Complaint c : csvFileService.getAllComplaints()) {
            if (complaintId.trim().equalsIgnoreCase(c.getComplaintId())) {
                found = c;
                break;
            }
        }

        if (found == null) {
            result.put("error", "Complaint not found: " + complaintId);
            return result;
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        found.setStatus(normalizedStatus);
        found.setAdminResponse(adminResponse == null ? "" : adminResponse.trim());
        found.setUpdatedAt(now);
        csvFileService.updateComplaint(found);

        result.put("success", "true");
        result.put("complaintId", found.getComplaintId());
        result.put("status", found.getStatus());
        result.put("message", "Complaint updated successfully by " + adminName + ".");
        return result;
    }

    /**
     * Count of unreviewed (SUBMITTED) complaints for admin notification badge.
     */
    public int getPendingCount() {
        int count = 0;
        for (Complaint c : csvFileService.getAllComplaints()) {
            if ("SUBMITTED".equalsIgnoreCase(c.getStatus())) count++;
        }
        return count;
    }
}
