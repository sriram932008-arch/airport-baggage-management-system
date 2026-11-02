package com.airport.baggage.service;

import com.airport.baggage.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service managing CSV persistence, thread-safe synchronization,
 * and synthetic sample data generation.
 */
public class CsvFileService {

    private final String dataDir;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final List<User> users = new ArrayList<>();
    private final List<Passenger> passengers = new ArrayList<>();
    private final List<Flight> flights = new ArrayList<>();
    private final List<FlightAuditEntry> flightAuditEntries = new ArrayList<>();
    private final List<LoadingBay> loadingBays = new ArrayList<>();
    private final List<ConveyorSegment> conveyorSegments = new ArrayList<>();
    private final List<Baggage> baggageList = new ArrayList<>();
    private final List<TrackingEvent> trackingHistory = new ArrayList<>();
    private final List<RoutingRecord> routingHistory = new ArrayList<>();
    private final List<com.airport.baggage.model.Complaint> complaints = new ArrayList<>();

    public CsvFileService(String dataDir) {
        this.dataDir = dataDir;
        initDirectoryAndFiles();
        loadAll();
    }

    private void initDirectoryAndFiles() {
        try {
            Path dir = Paths.get(dataDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create CSV data directory.", e);
        }
    }

    /**
     * Load all data from CSV files. If files do not exist or are empty, create default synthetic data.
     */
    public void loadAll() {
        lock.writeLock().lock();
        try {
            users.clear();
            passengers.clear();
            flights.clear();
            flightAuditEntries.clear();
            loadingBays.clear();
            conveyorSegments.clear();
            baggageList.clear();
            trackingHistory.clear();
            routingHistory.clear();
            complaints.clear();

            File userFile = new File(dataDir, "users.csv");
            if (!userFile.exists() || userFile.length() == 0) {
                seedInitialData();
                ensureMissingCsvHeaders();
            } else {
                readUsers();
                readPassengers();
                readFlights();
                readFlightAuditLog();
                readLoadingBays();
                readConveyors();
                readBaggage();
                readTrackingHistory();
                readRoutingHistory();
                readComplaints();
                ensureMissingCsvHeaders();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureMissingCsvHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("users.csv", "userId,username,email,passwordHash,salt,role,fullName,mobileNumber,createdAt");
        headers.put("passengers.csv", "passengerId,userId,fullName,email,mobileNumber,bookingReference");
        headers.put("flights.csv", "flightNumber,destination,scheduledDeparture,loadingCutoff,cutoffMinutesRemaining,assignedBayId,status");
        headers.put("audit_log.csv", "auditId,flightNumber,fieldName,previousValue,newValue,changedBy,changedAt");
        headers.put("loading_bays.csv", "bayId,bayName,assignedFlight,capacity,status");
        headers.put("conveyor_segments.csv", "segmentId,startNode,endNode,travelTimeMinutes,status");
        headers.put("baggage.csv", "baggageId,baggageType,passengerId,bookingReference,flightNumber,weightKg,operatorClearance,currentLocation,status,assignedBayId,routePath,estimatedTravelTime,cutoffFeasibility,cutoffMinutesRemaining,infeedTime,lastUpdated,detail1,detail2,estimatedRemainingProcessingTime,arrivalOrder,baggageName");
        headers.put("tracking_history.csv", "historyId,baggageId,timestamp,locationNode,status,message,recordedBy");
        headers.put("routing_history.csv", "routingId,baggageId,calculatedAt,startNode,destinationBay,path,totalTimeMinutes,feasible,failureReason");
        headers.put("complaints.csv", "ComplaintID,PassengerID,BaggageID,Category,Subject,Description,Status,AdminResponse,CreatedAt,UpdatedAt");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            File file = new File(dataDir, entry.getKey());
            if (!file.exists() || file.length() == 0) writeCsvLines(file, entry.getValue(), Collections.emptyList());
        }
    }

    // ==================== CSV HELPERS ====================

    private List<String[]> parseCsv(File file) {
        List<String[]> rows = new ArrayList<>();
        if (!file.exists()) return rows;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            boolean inQuotes = false;
            StringBuilder record = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (record.length() > 0) record.append('\n');
                record.append(line);
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) != '"') continue;
                    if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                    }
                }
                if (inQuotes) continue;
                String completeRecord = record.toString();
                if (isHeader) isHeader = false;
                else if (!completeRecord.trim().isEmpty()) rows.add(parseCsvLine(completeRecord));
                record.setLength(0);
            }
            if (record.length() > 0) throw new IllegalArgumentException("Unterminated quoted CSV record in " + file.getName());
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading " + file.getName(), e);
        }
        return rows;
    }

    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeCsvLines(File file, String header, List<String> lines) {
        Path target = file.toPath();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), file.getName(), ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(header);
                writer.newLine();
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw new UncheckedIOException("Error writing " + file.getName(), e);
        }
    }

    // ==================== READ ENTITIES ====================

    private void readUsers() {
        for (String[] r : parseCsv(new File(dataDir, "users.csv"))) {
            if (r.length >= 9) {
                users.add(new User(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8]));
            }
        }
    }

    private void readPassengers() {
        for (String[] r : parseCsv(new File(dataDir, "passengers.csv"))) {
            if (r.length >= 6) {
                passengers.add(new Passenger(r[0], r[1], r[2], r[3], r[4], r[5]));
            }
        }
    }

    private void readFlights() {
        for (String[] r : parseCsv(new File(dataDir, "flights.csv"))) {
            if (r.length >= 7) {
                double cutoffMins = 30.0;
                try { cutoffMins = Double.parseDouble(r[4]); } catch (Exception ignored) {}
                flights.add(new Flight(r[0], r[1], r[2], r[3], cutoffMins, r[5], r[6]));
            }
        }
    }

    private void readLoadingBays() {
        for (String[] r : parseCsv(new File(dataDir, "loading_bays.csv"))) {
            if (r.length >= 5) {
                int cap = 150;
                try { cap = Integer.parseInt(r[3]); } catch (Exception ignored) {}
                loadingBays.add(new LoadingBay(r[0], r[1], r[2], cap, r[4]));
            }
        }
    }

    private void readFlightAuditLog() {
        for (String[] row : parseCsv(new File(dataDir, "audit_log.csv"))) {
            if (row.length >= 7) {
                flightAuditEntries.add(new FlightAuditEntry(row[0], row[1], row[2], row[3], row[4], row[5], row[6]));
            }
        }
    }

    public void addFlightAuditEntries(List<FlightAuditEntry> entries) {
        lock.writeLock().lock();
        try {
            flightAuditEntries.addAll(entries);
            List<String> lines = new ArrayList<>();
            for (FlightAuditEntry entry : flightAuditEntries) {
                lines.add(String.join(",",
                        escapeCsv(entry.getAuditId()),
                        escapeCsv(entry.getFlightNumber()),
                        escapeCsv(entry.getFieldName()),
                        escapeCsv(entry.getPreviousValue()),
                        escapeCsv(entry.getNewValue()),
                        escapeCsv(entry.getChangedBy()),
                        escapeCsv(entry.getChangedAt())));
            }
            writeCsvLines(new File(dataDir, "audit_log.csv"),
                    "auditId,flightNumber,fieldName,previousValue,newValue,changedBy,changedAt", lines);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void readConveyors() {
        for (String[] r : parseCsv(new File(dataDir, "conveyor_segments.csv"))) {
            if (r.length >= 5) {
                double time = 2.0;
                try { time = Double.parseDouble(r[3]); } catch (Exception ignored) {}
                conveyorSegments.add(new ConveyorSegment(r[0], r[1], r[2], time, r[4]));
            }
        }
    }

    private void readBaggage() {
        long legacyArrivalOrder = 0;
        for (String[] r : parseCsv(new File(dataDir, "baggage.csv"))) {
            if (r.length >= 17) {
                String id = r[0];
                String type = r[1];
                String paxId = r[2];
                String bookingRef = r[3];
                String flightNo = r[4];
                double weight = Double.parseDouble(r[5]);
                boolean clearance = Boolean.parseBoolean(r[6]);
                String location = r[7];
                String status = r[8];
                String bay = r[9];
                String path = r[10];
                double travelTime = Double.parseDouble(r[11]);
                String feasibility = r[12];
                double cutoffRemaining = Double.parseDouble(r[13]);
                String infeed = r[14];
                String lastUpdated = r[15];
                String extra1 = r[16];
                String extra2 = r.length > 17 ? r[17] : "";

                if ("TRANSFER".equalsIgnoreCase(type)) {
                    double connWindow = 45.0;
                    try { connWindow = Double.parseDouble(extra2); } catch (Exception ignored) {}
                    baggageList.add(new TransferBag(id, paxId, bookingRef, flightNo, weight, clearance,
                            location, status, bay, path, travelTime, feasibility, cutoffRemaining, infeed, lastUpdated, extra1, connWindow));
                } else {
                    baggageList.add(new OriginBag(id, paxId, bookingRef, flightNo, weight, clearance,
                            location, status, bay, path, travelTime, feasibility, cutoffRemaining, infeed, lastUpdated, extra1, extra2));
                }
                Baggage bag = baggageList.get(baggageList.size() - 1);
                double processingTime = 0;
                long arrivalOrder = legacyArrivalOrder;
                try {
                    if (r.length > 18) {
                        double parsed = Double.parseDouble(r[18]);
                        if (Double.isFinite(parsed) && parsed >= 0) processingTime = parsed;
                    }
                } catch (NumberFormatException ignored) {}
                try { if (r.length > 19) arrivalOrder = Math.max(0, Long.parseLong(r[19])); } catch (NumberFormatException ignored) {}
                bag.setEstimatedRemainingProcessingTime(processingTime);
                bag.setArrivalOrder(arrivalOrder);
                bag.setBaggageName(r.length > 20 && !r[20].trim().isEmpty()
                        ? r[20] : ("TRANSFER".equalsIgnoreCase(type) ? "Transfer bag" : "Checked bag"));
                legacyArrivalOrder++;
            }
        }
    }

    private void readTrackingHistory() {
        for (String[] r : parseCsv(new File(dataDir, "tracking_history.csv"))) {
            if (r.length >= 7) {
                trackingHistory.add(new TrackingEvent(r[0], r[1], r[2], r[3], r[4], r[5], r[6]));
            }
        }
    }

    private void readRoutingHistory() {
        for (String[] r : parseCsv(new File(dataDir, "routing_history.csv"))) {
            if (r.length >= 8) {
                double time = Double.parseDouble(r[6]);
                boolean feasible = Boolean.parseBoolean(r[7]);
                String reason = r.length > 8 ? r[8] : "";
                routingHistory.add(new RoutingRecord(r[0], r[1], r[2], r[3], r[4], r[5], time, feasible, reason));
            }
        }
    }

    private void readComplaints() {
        File f = new File(dataDir, "complaints.csv");
        if (!f.exists()) {
            writeCsvLines(f, "ComplaintID,PassengerID,BaggageID,Category,Subject,Description,Status,AdminResponse,CreatedAt,UpdatedAt",
                    new ArrayList<>());
            return;
        }
        boolean legacyFlightColumn = false;
        for (String[] r : parseCsv(f)) {
            if (r.length >= 11) {
                legacyFlightColumn = true;
                complaints.add(new com.airport.baggage.model.Complaint(
                    r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10]));
            } else if (r.length >= 10) {
                String flightNumber = "";
                for (Baggage bag : baggageList) {
                    if (r[2].equalsIgnoreCase(bag.getBaggageId())) {
                        flightNumber = bag.getFlightNumber();
                        break;
                    }
                }
                complaints.add(new com.airport.baggage.model.Complaint(
                    r[0], r[1], r[2], flightNumber, r[3], r[4], r[5], r[6], r[7], r[8], r[9]));
            }
        }
        if (legacyFlightColumn) saveComplaints();
    }

    // ==================== SAVE METHODS ====================

    public void saveUsers() {
        List<String> lines = new ArrayList<>();
        for (User u : users) {
            lines.add(String.join(",",
                    escapeCsv(u.getUserId()),
                    escapeCsv(u.getUsername()),
                    escapeCsv(u.getEmail()),
                    escapeCsv(u.getPasswordHash()),
                    escapeCsv(u.getSalt()),
                    escapeCsv(u.getRole()),
                    escapeCsv(u.getFullName()),
                    escapeCsv(u.getMobileNumber()),
                    escapeCsv(u.getCreatedAt())));
        }
        writeCsvLines(new File(dataDir, "users.csv"),
                "userId,username,email,passwordHash,salt,role,fullName,mobileNumber,createdAt", lines);
    }

    public void savePassengers() {
        List<String> lines = new ArrayList<>();
        for (Passenger p : passengers) {
            lines.add(String.join(",",
                    escapeCsv(p.getPassengerId()),
                    escapeCsv(p.getUserId()),
                    escapeCsv(p.getFullName()),
                    escapeCsv(p.getEmail()),
                    escapeCsv(p.getMobileNumber()),
                    escapeCsv(p.getBookingReference())));
        }
        writeCsvLines(new File(dataDir, "passengers.csv"),
                "passengerId,userId,fullName,email,mobileNumber,bookingReference", lines);
    }

    public void saveFlights() {
        List<String> lines = new ArrayList<>();
        for (Flight f : flights) {
            lines.add(String.join(",",
                    escapeCsv(f.getFlightNumber()),
                    escapeCsv(f.getDestination()),
                    escapeCsv(f.getScheduledDeparture()),
                    escapeCsv(f.getLoadingCutoff()),
                    String.valueOf(f.getCutoffMinutesRemaining()),
                    escapeCsv(f.getAssignedBayId()),
                    escapeCsv(f.getStatus())));
        }
        writeCsvLines(new File(dataDir, "flights.csv"),
                "flightNumber,destination,scheduledDeparture,loadingCutoff,cutoffMinutesRemaining,assignedBayId,status", lines);
    }

    public void saveLoadingBays() {
        List<String> lines = new ArrayList<>();
        for (LoadingBay b : loadingBays) {
            lines.add(String.join(",",
                    escapeCsv(b.getBayId()),
                    escapeCsv(b.getBayName()),
                    escapeCsv(b.getAssignedFlight()),
                    String.valueOf(b.getCapacity()),
                    escapeCsv(b.getStatus())));
        }
        writeCsvLines(new File(dataDir, "loading_bays.csv"),
                "bayId,bayName,assignedFlight,capacity,status", lines);
    }

    public void saveConveyors() {
        List<String> lines = new ArrayList<>();
        for (ConveyorSegment c : conveyorSegments) {
            lines.add(String.join(",",
                    escapeCsv(c.getSegmentId()),
                    escapeCsv(c.getStartNode()),
                    escapeCsv(c.getEndNode()),
                    String.valueOf(c.getTravelTimeMinutes()),
                    escapeCsv(c.getStatus())));
        }
        writeCsvLines(new File(dataDir, "conveyor_segments.csv"),
                "segmentId,startNode,endNode,travelTimeMinutes,status", lines);
    }

    public void saveBaggage() {
        List<String> lines = new ArrayList<>();
        for (Baggage b : baggageList) {
            String extra1 = "";
            String extra2 = "";
            if (b instanceof OriginBag) {
                OriginBag ob = (OriginBag) b;
                extra1 = ob.getCheckInDesk();
                extra2 = ob.getPassengerCategory();
            } else if (b instanceof TransferBag) {
                TransferBag tb = (TransferBag) b;
                extra1 = tb.getInboundFlight();
                extra2 = String.valueOf(tb.getConnectionWindowMinutes());
            }

            lines.add(String.join(",",
                    escapeCsv(b.getBaggageId()),
                    escapeCsv(b.getBaggageType()),
                    escapeCsv(b.getPassengerId()),
                    escapeCsv(b.getBookingReference()),
                    escapeCsv(b.getFlightNumber()),
                    String.valueOf(b.getWeightKg()),
                    String.valueOf(b.isOperatorClearance()),
                    escapeCsv(b.getCurrentLocation()),
                    escapeCsv(b.getStatus()),
                    escapeCsv(b.getAssignedBayId()),
                    escapeCsv(b.getRoutePath()),
                    String.valueOf(b.getEstimatedTravelTime()),
                    escapeCsv(b.getCutoffFeasibility()),
                    String.valueOf(b.getCutoffMinutesRemaining()),
                    escapeCsv(b.getInfeedTime()),
                    escapeCsv(b.getLastUpdated()),
                    escapeCsv(extra1),
                    escapeCsv(extra2),
                    String.valueOf(b.getEstimatedRemainingProcessingTime()),
                    String.valueOf(b.getArrivalOrder()),
                    escapeCsv(b.getBaggageName())));
        }
        writeCsvLines(new File(dataDir, "baggage.csv"),
                "baggageId,baggageType,passengerId,bookingReference,flightNumber,weightKg,operatorClearance,currentLocation,status,assignedBayId,routePath,estimatedTravelTime,cutoffFeasibility,cutoffMinutesRemaining,infeedTime,lastUpdated,detail1,detail2,estimatedRemainingProcessingTime,arrivalOrder,baggageName", lines);
    }

    public void saveTrackingHistory() {
        List<String> lines = new ArrayList<>();
        for (TrackingEvent t : trackingHistory) {
            lines.add(String.join(",",
                    escapeCsv(t.getHistoryId()),
                    escapeCsv(t.getBaggageId()),
                    escapeCsv(t.getTimestamp()),
                    escapeCsv(t.getLocationNode()),
                    escapeCsv(t.getStatus()),
                    escapeCsv(t.getMessage()),
                    escapeCsv(t.getRecordedBy())));
        }
        writeCsvLines(new File(dataDir, "tracking_history.csv"),
                "historyId,baggageId,timestamp,locationNode,status,message,recordedBy", lines);
    }

    public void saveRoutingHistory() {
        List<String> lines = new ArrayList<>();
        for (RoutingRecord r : routingHistory) {
            lines.add(String.join(",",
                    escapeCsv(r.getRoutingId()),
                    escapeCsv(r.getBaggageId()),
                    escapeCsv(r.getCalculatedAt()),
                    escapeCsv(r.getStartNode()),
                    escapeCsv(r.getDestinationBay()),
                    escapeCsv(r.getPath()),
                    String.valueOf(r.getTotalTimeMinutes()),
                    String.valueOf(r.isFeasible()),
                    escapeCsv(r.getFailureReason())));
        }
        writeCsvLines(new File(dataDir, "routing_history.csv"),
                "routingId,baggageId,calculatedAt,startNode,destinationBay,path,totalTimeMinutes,feasible,failureReason", lines);
    }

    public void saveComplaints() {
        List<String> lines = new ArrayList<>();
        for (com.airport.baggage.model.Complaint c : complaints) {
            lines.add(String.join(",",
                    escapeCsv(c.getComplaintId()),
                    escapeCsv(c.getPassengerId()),
                    escapeCsv(c.getBaggageId()),
                    escapeCsv(c.getCategory()),
                    escapeCsv(c.getSubject()),
                    escapeCsv(c.getDescription()),
                    escapeCsv(c.getStatus()),
                    escapeCsv(c.getAdminResponse()),
                    escapeCsv(c.getCreatedAt()),
                    escapeCsv(c.getUpdatedAt())));
        }
        writeCsvLines(new File(dataDir, "complaints.csv"),
                "ComplaintID,PassengerID,BaggageID,Category,Subject,Description,Status,AdminResponse,CreatedAt,UpdatedAt",
                lines);
    }

    // ==================== SEED INITIAL DATA ====================

    private void seedInitialData() {
        System.out.println("Initializing synthetic sample data for airport baggage routing system...");

        // 1. Users: Admin and Passengers
        String adminSalt = AuthService.generateSalt();
        String adminHash = AuthService.hashPassword("admin123", adminSalt);
        users.add(new User("USR-001", "admin", "admin@airport.local", adminHash, adminSalt,
                "ADMIN", "Chief Baggage Operations Officer", "+919876543210", "2026-11-01 06:00"));

        String pass1Salt = AuthService.generateSalt();
        String pass1Hash = AuthService.hashPassword("pass123", pass1Salt);
        users.add(new User("USR-101", "rahul_sharma", "rahul.sharma@example.com", pass1Hash, pass1Salt,
                "PASSENGER", "Rahul Sharma", "+919820011223", "2026-11-01 06:15"));

        String pass2Salt = AuthService.generateSalt();
        String pass2Hash = AuthService.hashPassword("pass123", pass2Salt);
        users.add(new User("USR-102", "ananya_patel", "ananya.patel@example.com", pass2Hash, pass2Salt,
                "PASSENGER", "Ananya Patel", "+919820033445", "2026-11-01 06:20"));

        String pass3Salt = AuthService.generateSalt();
        String pass3Hash = AuthService.hashPassword("pass123", pass3Salt);
        users.add(new User("USR-103", "david_miller", "david.miller@example.com", pass3Hash, pass3Salt,
                "PASSENGER", "David Miller", "+919820055667", "2026-11-01 06:30"));

        // 2. Passengers
        passengers.add(new Passenger("PAX-1001", "USR-101", "Rahul Sharma", "rahul.sharma@example.com", "+919820011223", "BK-7890"));
        passengers.add(new Passenger("PAX-1002", "USR-102", "Ananya Patel", "ananya.patel@example.com", "+919820033445", "BK-4512"));
        passengers.add(new Passenger("PAX-1003", "USR-103", "David Miller", "david.miller@example.com", "+919820055667", "BK-9931"));

        // 3. Flights (4 flights as required: AI202, AI305, AI410, AI520)
        flights.add(new Flight("AI202", "London Heathrow (LHR)", "10:30", "10:00", 35.0, "LB1", "BOARDING"));
        flights.add(new Flight("AI305", "Dubai International (DXB)", "11:15", "10:45", 50.0, "LB2", "SCHEDULED"));
        flights.add(new Flight("AI410", "Singapore Changi (SIN)", "09:50", "09:20", 12.0, "LB3", "BOARDING"));
        flights.add(new Flight("AI520", "New York JFK (JFK)", "12:00", "11:30", 95.0, "LB4", "SCHEDULED"));

        // 4. Loading Bays
        loadingBays.add(new LoadingBay("LB1", "Loading Bay 1 (Gate A1)", "AI202", 150, "ACTIVE"));
        loadingBays.add(new LoadingBay("LB2", "Loading Bay 2 (Gate A2)", "AI305", 150, "ACTIVE"));
        loadingBays.add(new LoadingBay("LB3", "Loading Bay 3 (Gate B1)", "AI410", 150, "ACTIVE"));
        loadingBays.add(new LoadingBay("LB4", "Loading Bay 4 (Gate B2)", "AI520", 180, "ACTIVE"));

        // 5. Conveyor Segments (11 nodes: N1..N11 + 4 destination bays: LB1..LB4)
        // Conveyor topology with redundancy, cross-trunk connections, and alternative bypasses
        conveyorSegments.add(new ConveyorSegment("SEG-01", "N1", "N4", 3.0, "ACTIVE"));  // Terminal A Check-in -> Primary Sorting
        conveyorSegments.add(new ConveyorSegment("SEG-02", "N2", "N4", 3.5, "ACTIVE"));  // Terminal B Check-in -> Primary Sorting
        conveyorSegments.add(new ConveyorSegment("SEG-03", "N3", "N4", 2.5, "ACTIVE"));  // Transfer Infeed -> Primary Sorting
        conveyorSegments.add(new ConveyorSegment("SEG-04", "N4", "N5", 2.0, "ACTIVE"));  // Primary Sorting -> Security X-Ray
        conveyorSegments.add(new ConveyorSegment("SEG-05", "N4", "N7", 4.0, "ACTIVE"));  // Primary Sorting -> Trunkline North (Pre-screened bypass)
        conveyorSegments.add(new ConveyorSegment("SEG-06", "N5", "N6", 2.5, "ACTIVE"));  // Security X-Ray -> Secondary Sorting
        conveyorSegments.add(new ConveyorSegment("SEG-07", "N6", "N7", 3.0, "ACTIVE"));  // Secondary Sorting -> Trunkline North
        conveyorSegments.add(new ConveyorSegment("SEG-08", "N6", "N8", 3.0, "ACTIVE"));  // Secondary Sorting -> Trunkline South
        conveyorSegments.add(new ConveyorSegment("SEG-09", "N7", "N9", 3.5, "ACTIVE"));  // Trunk North -> Central Junction Hub
        conveyorSegments.add(new ConveyorSegment("SEG-10", "N8", "N9", 4.0, "ACTIVE"));  // Trunk South -> Central Junction Hub
        conveyorSegments.add(new ConveyorSegment("SEG-11", "N9", "N10", 2.5, "ACTIVE")); // Central Junction -> Feeder Loop West
        conveyorSegments.add(new ConveyorSegment("SEG-12", "N9", "N11", 2.5, "ACTIVE")); // Central Junction -> Feeder Loop East
        conveyorSegments.add(new ConveyorSegment("SEG-13", "N10", "LB1", 2.0, "ACTIVE"));// Feeder West -> Bay 1 (AI202)
        conveyorSegments.add(new ConveyorSegment("SEG-14", "N10", "LB2", 2.5, "ACTIVE"));// Feeder West -> Bay 2 (AI305)
        conveyorSegments.add(new ConveyorSegment("SEG-15", "N11", "LB3", 2.0, "ACTIVE"));// Feeder East -> Bay 3 (AI410)
        conveyorSegments.add(new ConveyorSegment("SEG-16", "N11", "LB4", 2.5, "ACTIVE"));// Feeder East -> Bay 4 (AI520)
        // Redundancy cross-links:
        conveyorSegments.add(new ConveyorSegment("SEG-17", "N10", "N11", 2.0, "ACTIVE"));// Cross-bay interconnect West -> East
        conveyorSegments.add(new ConveyorSegment("SEG-18", "N11", "N10", 2.0, "ACTIVE"));// Cross-bay interconnect East -> West
        conveyorSegments.add(new ConveyorSegment("SEG-19", "N8", "N11", 5.0, "ACTIVE")); // Direct South -> East Feeder bypass
        // One segment initialized as FAILED for realistic testing: SEG-20 test feeder spur or alternate link
        conveyorSegments.add(new ConveyorSegment("SEG-20", "N7", "N10", 3.0, "FAILED")); // Express North-West bypass (Currently Failed)

        // 6. Baggage Records (At least 30 synthetic records as required)
        seedBaggageRecords();

        // Save everything to CSV files
        saveUsers();
        savePassengers();
        saveFlights();
        saveLoadingBays();
        saveConveyors();
        saveBaggage();
        saveTrackingHistory();
        saveRoutingHistory();
        saveComplaints();
        System.out.println("Default CSV data successfully initialized with 30+ baggage records, 4 flights, and 11 conveyor nodes.");
    }

    private void seedBaggageRecords() {
        String[] paxIds = {"PAX-1001", "PAX-1002", "PAX-1003"};
        String[] bookingRefs = {"BK-7890", "BK-4512", "BK-9931"};
        String[] flightsArr = {"AI202", "AI305", "AI410", "AI520"};
        String[] bayArr = {"LB1", "LB2", "LB3", "LB4"};
        double[] flightCutoffs = {35.0, 50.0, 12.0, 95.0};

        // We will seed 32 detailed baggage records with diverse states:
        // - Cleared vs Awaiting Clearance
        // - Origin vs Transfer
        // - In Transit, Loaded, Cutoff at Risk, Missed Cutoff, Route Failure
        for (int i = 1; i <= 32; i++) {
            String bagId = String.format("BAG-%04d", 1000 + i);
            int flightIdx = (i - 1) % 4;
            String flt = flightsArr[flightIdx];
            String bay = bayArr[flightIdx];
            double flightCutoffRemaining = flightCutoffs[flightIdx];
            int paxIdx = (i - 1) % 3;
            String paxId = paxIds[paxIdx];
            String bk = bookingRefs[paxIdx];
            double weight = 16.0 + (i * 0.7) % 15.0;
            weight = Math.round(weight * 10.0) / 10.0;

            boolean isTransfer = (i % 3 == 0);
            boolean operatorCleared = (i % 5 != 0); // some bags not cleared
            String infeedTime = String.format("08:%02d", 10 + (i * 2) % 45);
            String lastUpdated = "2026-11-01 " + infeedTime;

            String status;
            String location;
            String path;
            double estTime;
            String feasibility;

            if (!operatorCleared) {
                // Uncleared bags cannot proceed to loading
                status = "AWAITING_CLEARANCE";
                location = isTransfer ? "N3" : "N1";
                path = "Pending Operator Security Clearance";
                estTime = 0.0;
                feasibility = "UNROUTED";
            } else if (i == 3 || i == 7) {
                // Successfully loaded bags
                status = "LOADED";
                location = bay;
                path = "N1 -> N4 -> N5 -> N6 -> N7 -> N9 -> N10 -> " + bay;
                estTime = 16.0;
                feasibility = "FEASIBLE";
            } else if (flt.equals("AI410") && (i == 11 || i == 19)) {
                // Flight AI410 has only 12m cutoff left, conveyor travel is 15.5m -> Missed Cutoff
                status = "MISSED_CUTOFF";
                location = "N4";
                path = "N4 -> N5 -> N6 -> N8 -> N9 -> N11 -> LB3";
                estTime = 16.0;
                feasibility = "MISSED_CUTOFF";
            } else if (flt.equals("AI410") && i == 15) {
                // Cutoff at Risk (travel time ~10m close to 12m cutoff)
                status = "CUTOFF_AT_RISK";
                location = "N6";
                path = "N6 -> N7 -> N9 -> N11 -> LB3";
                estTime = 11.0;
                feasibility = "AT_RISK";
            } else if (i == 22) {
                // Route Failure demonstration (e.g. stranded or isolated)
                status = "ROUTE_FAILURE";
                location = "N7";
                path = "Route recalculation failed: Express bypass SEG-20 failed and trunk congested";
                estTime = 0.0;
                feasibility = "UNROUTED";
            } else {
                // In transit
                status = "IN_TRANSIT";
                location = (i % 2 == 0) ? "N5" : "N9";
                path = "N1 -> N4 -> N5 -> N6 -> N9 -> " + (flt.equals("AI410") || flt.equals("AI520") ? "N11" : "N10") + " -> " + bay;
                estTime = 14.5;
                feasibility = (estTime <= flightCutoffRemaining - 5.0) ? "FEASIBLE" : "AT_RISK";
            }

            Baggage bag;
            if (isTransfer) {
                String inboundFlt = "AI" + (100 + (i * 7) % 50);
                double connWindow = 30.0 + (i * 3) % 40;
                bag = new TransferBag(bagId, paxId, bk, flt, weight, operatorCleared, location,
                        status, bay, path, estTime, feasibility, flightCutoffRemaining, infeedTime, lastUpdated,
                        inboundFlt, connWindow);
            } else {
                String desk = "DESK-A" + (1 + (i % 8));
                String cat = (i % 7 == 0) ? "FIRST_CLASS" : (i % 4 == 0 ? "BUSINESS" : "ECONOMY");
                bag = new OriginBag(bagId, paxId, bk, flt, weight, operatorCleared, location,
                        status, bay, path, estTime, feasibility, flightCutoffRemaining, infeedTime, lastUpdated,
                        desk, cat);
            }
            bag.setArrivalOrder(i);
            baggageList.add(bag);

            // Seed tracking history events for each bag
            trackingHistory.add(new TrackingEvent("EVT-" + (1000 + i * 2), bagId, "2026-11-01 " + infeedTime,
                    isTransfer ? "N3" : "N1", "CHECKED_IN", "Baggage injected at conveyor infeed", "System"));
            if (operatorCleared) {
                trackingHistory.add(new TrackingEvent("EVT-" + (1000 + i * 2 + 1), bagId, "2026-11-01 " + infeedTime,
                        location, status, "Baggage verified at conveyor node " + location, "Operator OPS-01"));
            }
        }
    }

    // ==================== GETTERS & ACCESSORS ====================

    public List<User> getAllUsers() {
        lock.readLock().lock();
        try { return new ArrayList<>(users); } finally { lock.readLock().unlock(); }
    }

    public List<Passenger> getAllPassengers() {
        lock.readLock().lock();
        try { return new ArrayList<>(passengers); } finally { lock.readLock().unlock(); }
    }

    public List<Flight> getAllFlights() {
        lock.readLock().lock();
        try { return new ArrayList<>(flights); } finally { lock.readLock().unlock(); }
    }

    public List<LoadingBay> getAllLoadingBays() {
        lock.readLock().lock();
        try { return new ArrayList<>(loadingBays); } finally { lock.readLock().unlock(); }
    }

    public List<ConveyorSegment> getAllConveyors() {
        lock.readLock().lock();
        try { return new ArrayList<>(conveyorSegments); } finally { lock.readLock().unlock(); }
    }

    public List<Baggage> getAllBaggage() {
        lock.readLock().lock();
        try { return new ArrayList<>(baggageList); } finally { lock.readLock().unlock(); }
    }

    public List<TrackingEvent> getAllTrackingHistory() {
        lock.readLock().lock();
        try { return new ArrayList<>(trackingHistory); } finally { lock.readLock().unlock(); }
    }

    public List<RoutingRecord> getAllRoutingHistory() {
        lock.readLock().lock();
        try { return new ArrayList<>(routingHistory); } finally { lock.readLock().unlock(); }
    }

    // Mutation methods
    public void addUser(User u) {
        lock.writeLock().lock();
        try {
            users.add(u);
            saveUsers();
        } finally { lock.writeLock().unlock(); }
    }

    public void updateUser(User user) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getUserId().equalsIgnoreCase(user.getUserId())) {
                    users.set(i, user);
                    break;
                }
            }
            saveUsers();
        } finally { lock.writeLock().unlock(); }
    }

    public void addPassenger(Passenger p) {
        lock.writeLock().lock();
        try {
            passengers.add(p);
            savePassengers();
        } finally { lock.writeLock().unlock(); }
    }

    public void updateFlight(Flight flight) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < flights.size(); i++) {
                if (flights.get(i).getFlightNumber().equalsIgnoreCase(flight.getFlightNumber())) {
                    flights.set(i, flight);
                    break;
                }
            }
            saveFlights();
        } finally { lock.writeLock().unlock(); }
    }

    public void addFlight(Flight flight) {
        lock.writeLock().lock();
        try {
            flights.add(flight);
            saveFlights();
        } finally { lock.writeLock().unlock(); }
    }

    public void updateConveyor(ConveyorSegment seg) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < conveyorSegments.size(); i++) {
                if (conveyorSegments.get(i).getSegmentId().equalsIgnoreCase(seg.getSegmentId())) {
                    conveyorSegments.set(i, seg);
                    break;
                }
            }
            saveConveyors();
        } finally { lock.writeLock().unlock(); }
    }

    public void addConveyor(ConveyorSegment seg) {
        lock.writeLock().lock();
        try {
            conveyorSegments.add(seg);
            saveConveyors();
        } finally { lock.writeLock().unlock(); }
    }

    public void updateLoadingBay(LoadingBay bay) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < loadingBays.size(); i++) {
                if (loadingBays.get(i).getBayId().equalsIgnoreCase(bay.getBayId())) {
                    loadingBays.set(i, bay);
                    break;
                }
            }
            saveLoadingBays();
        } finally { lock.writeLock().unlock(); }
    }

    public void updateBaggage(Baggage b) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < baggageList.size(); i++) {
                if (baggageList.get(i).getBaggageId().equalsIgnoreCase(b.getBaggageId())) {
                    baggageList.set(i, b);
                    break;
                }
            }
            saveBaggage();
        } finally { lock.writeLock().unlock(); }
    }

    public void addBaggage(Baggage b) {
        lock.writeLock().lock();
        try {
            long nextArrivalOrder = baggageList.stream().mapToLong(Baggage::getArrivalOrder).max().orElse(0) + 1;
            if (b.getArrivalOrder() == 0) b.setArrivalOrder(nextArrivalOrder);
            baggageList.add(b);
            saveBaggage();
        } finally { lock.writeLock().unlock(); }
    }

    public void addTrackingEvent(TrackingEvent evt) {
        lock.writeLock().lock();
        try {
            trackingHistory.add(evt);
            saveTrackingHistory();
        } finally { lock.writeLock().unlock(); }
    }

    public void addRoutingRecord(RoutingRecord rec) {
        lock.writeLock().lock();
        try {
            routingHistory.add(rec);
            saveRoutingHistory();
        } finally { lock.writeLock().unlock(); }
    }

    // ==================== COMPLAINT METHODS ====================

    public List<com.airport.baggage.model.Complaint> getAllComplaints() {
        lock.readLock().lock();
        try { return new ArrayList<>(complaints); } finally { lock.readLock().unlock(); }
    }

    public void addComplaint(com.airport.baggage.model.Complaint c) {
        lock.writeLock().lock();
        try {
            complaints.add(c);
            saveComplaints();
        } finally { lock.writeLock().unlock(); }
    }

    public void updateComplaint(com.airport.baggage.model.Complaint updated) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < complaints.size(); i++) {
                if (complaints.get(i).getComplaintId().equalsIgnoreCase(updated.getComplaintId())) {
                    complaints.set(i, updated);
                    break;
                }
            }
            saveComplaints();
        } finally { lock.writeLock().unlock(); }
    }
}
