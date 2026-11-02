# Intelligent Airport Baggage Handling and Routing System

A modern, responsive, and robust website and simulation platform for an **Intelligent Airport Baggage Handling and Routing System**. The platform is built with **HTML5, CSS3, Modern JavaScript**, and a high-performance **Java 17** backend utilizing object-oriented programming (OOP) principles and thread-safe **CSV file storage** instead of an external database.

---

## 1. Project Overview & Architecture

* **Title**: Intelligent Airport Baggage Handling and Routing System
* **Architecture**: Client-Server Architecture
  * **Frontend**: Responsive Single-Page Application (SPA) with pure Vanilla HTML5, CSS3, and ES6+ JavaScript.
    * **Initial Entry Screen**: Dedicated Welcome Gateway Page with passenger sign-in, account registration, and admin access cards before advancing into the main Home Page and operational dashboards.
    * **Interactive Visualizations**: Custom interactive SVG conveyor network visualizer with real-time route path animation and failover indicators.
  * **Backend**: Java 17 using native `com.sun.net.httpserver.HttpServer` with RESTful API endpoints, multi-threaded request processing, and role-based access control.
  * **Persistence**: Pure CSV files (`backend/data/`) managed with thread-safe file locking (`ReentrantReadWriteLock`).

---

## 2. Design System & UI Specifications

The user interface follows a professional, clean airport operations dashboard design:

* **Primary Color**: Deep Navy Blue (`#142B4A`)
* **Secondary Color**: Muted Blue (`#315B83`)
* **Background**: Soft Off-White (`#F7F9FC`)
* **Card Surface**: Pure White (`#FFFFFF`) with subtle borders and shadows
* **Typography**: Inter / clean sans-serif
* **Status Badges**:
  * 🟢 **Green** (`#10B981`): Loaded / Active / Feasible
  * 🔵 **Blue** (`#2563EB`): In Transit
  * 🟡 **Amber** (`#F59E0B`): Cutoff at Risk / Warning
  * 🔴 **Red** (`#EF4444`): Missed Cutoff / Route Failure / Conveyor Segment Failed
  * ⚪ **Gray** (`#64748B`): Awaiting Operator Clearance

---

## 3. Java OOP Concepts Applied

The backend exemplifies core Object-Oriented Programming (OOP) concepts:

1. **Abstraction**:
   * `Baggage.java`: Abstract base class defining common properties (ID, flight number, weight, status, clearance, location) and abstract methods `getSpecificTypeDetails()` and `getHandlingPriorityScore()`.
2. **Inheritance**:
   * `OriginBag.java` extends `Baggage` with check-in desk identifiers and passenger travel class category (`FIRST_CLASS`, `BUSINESS`, `ECONOMY`).
   * `TransferBag.java` extends `Baggage` with inbound feeder flight numbers and connection time windows.
3. **Polymorphism**:
   * Subclasses override `getSpecificTypeDetails()` and `getHandlingPriorityScore()`, allowing dynamic handling behavior and priority scoring during conveyor scheduling.
4. **Encapsulation**:
   * All entity fields are `private` and accessed exclusively via validated getters and setters.
5. **Composition**:
   * `Passenger` objects compose `User` credentials; `Graph` composes `Edge` elements representing conveyor links.

---

## 4. Data Structures and Algorithms

1. **Dijkstra's Algorithm (`ConveyorRoutePlanner.java`)**:
   * Calculates minimum-travel-time paths across directed, weighted conveyor segments with positive travel times.
   * Uses a min-heap `PriorityQueue<NodeRecord>` for optimal $O((V + E) \log V)$ computation.
   * Dynamically skips failed conveyor segments.
   * Automatically calculates total transit time and checks against flight loading cutoff windows.
2. **Earliest Loading Cutoff Priority Queue (`PriorityQueue<Baggage>`)**:
   * Implements strict scheduling comparison:
     1. Earliest flight loading cutoff (strictly first).
     2. Transfer baggage first only when cutoff times are equal.
     3. Configured passenger-category priority, then persisted baggage arrival order.
   * *Rule guarantee: Transfer status never overrides an earlier flight cutoff.*
   * Estimated arrival includes both remaining processing time and Dijkstra travel time.
3. **FIFO Queue (`ArrayDeque<Baggage>`)**:
   * Manages incoming baggage queues at conveyor injection points (Terminal A, Terminal B, and Transfer infeed).
4. **Fast Lookups (`HashMap`)**:
   * $O(1)$ lookups for baggage records, flights, loading bays, and conveyor segments.

---

## 5. Conveyor Network Topology

The conveyor network models 11 operational nodes and 4 aircraft loading bays:

* **Infeed Points**:
  * `N1`: Terminal A Check-in Infeed
  * `N2`: Terminal B Check-in Infeed
  * `N3`: Transfer Infeed Feeder (Connecting arrivals)
* **Sorting & Screening**:
  * `N4`: Primary Sorting Hub
  * `N5`: Security & X-Ray Screening Checkpoint
  * `N6`: Secondary Sorting Hub
* **High-Speed Trunklines**:
  * `N7`: High-Speed Trunkline North (Pre-screened bypass)
  * `N8`: High-Speed Trunkline South
  * `N9`: Central Junction Hub
* **Feeder Loops**:
  * `N10`: Feeder Loop West
  * `N11`: Feeder Loop East
* **Aircraft Loading Bays (Destination Bays)**:
  * `LB1`: Loading Bay 1 (Gate A1 &mdash; Flight AI202)
  * `LB2`: Loading Bay 2 (Gate A2 &mdash; Flight AI305)
  * `LB3`: Loading Bay 3 (Gate B1 &mdash; Flight AI410)
  * `LB4`: Loading Bay 4 (Gate B2 &mdash; Flight AI520)

---

## 6. CSV Data Storage

Data is stored persistently in the `backend/data/` directory:

1. `users.csv`: User credentials, roles (`PASSENGER`, `ADMIN`), and salted PBKDF2-HMAC-SHA256 password hashes. Existing salted SHA-256 records are upgraded after a successful login.
2. `passengers.csv`: Passenger profiles linked to booking references.
3. `flights.csv`: Flight schedules, departure times, loading cutoffs, and assigned bays.
4. `loading_bays.csv`: Terminal bays, assigned flights, capacities, and statuses.
5. `conveyor_segments.csv`: Directed segments, start/end nodes, travel times (mins), and statuses (`ACTIVE`/`FAILED`).
6. `baggage.csv`: Complete baggage registry (origin and transfer bags, clearance, status, calculated routes, remaining processing time, and arrival order).
7. `tracking_history.csv`: Audit event trail of all movements and state updates.
8. `routing_history.csv`: Calculation logs with Dijkstra paths, feasibility flags, and diagnostic reasons.
9. `complaints.csv`: Passenger complaints and feedback with status and official admin response.
10. `audit_log.csv`: Flight schedule changes, with flight, field, previous/new values, administrator, and timestamp.

Passenger registration collects a count of 1–10 baggage units and each bag's name/description, type, and flight. The backend validates these details, saves the new baggage records to `baggage.csv`, and returns generated baggage IDs for the passenger to keep. New bags start at the appropriate infeed node and await operator clearance. The Passenger Home page shows only baggage status, confirmed location, flight number, estimated travel time, and loading-cutoff status for the signed-in passenger. A **Details** action opens that passenger's owned baggage record, route, cutoff, and tracking timeline; the backend enforces baggage ownership. The passenger Complaints & Feedback page accepts an optional owned baggage ID and displays the account's complaint history and admin responses. The Admin Flight Management module allows admins to add flights with a destination, departure and cutoff times, cutoff minutes remaining, active loading bay, and flight status; creation requires confirmation and is recorded in `audit_log.csv`. Admins can also search flights, edit schedules with confirmation, inspect associated baggage, change flight status, reevaluate cutoff risk, and trigger bay-change route recalculation. Schedule and cutoff data returned to passengers comes from the current `flights.csv` record. The Admin Dashboard includes priority and cutoff-risk views, conveyor ACTIVE/FAILED controls, explicit route calculation, and complaint review/status/response management. Changing a conveyor state does not calculate routes by itself; use **Calculate** after failures or restorations to recalculate paths.

---

## 7. Automated Test Verification (All 8 Scenarios)

The system includes an automated test runner (`ScenarioRunner.java`) verifying all required operational scenarios:

| # | Test Scenario | Verified Outcome |
|---|---|---|
| **1** | Baggage reaches loading bay before cutoff | Dijkstra calculates optimal route; status marked `FEASIBLE`. |
| **2** | Conveyor fails and alternative route is calculated | SEG-04 fails; system recalculates alternate route via bypass N4 &rarr; N7. |
| **3** | Bag cannot reach loading bay before cutoff | Travel time exceeds cutoff window; status flagged `MISSED_CUTOFF`. |
| **4** | Uncleared baggage cannot proceed to loading | Routing calculation is strictly blocked until operator clearance is granted. |
| **5** | Flight's loading bay changes | Reassigning flight bay automatically recalculates routes of all assigned baggage. |
| **6** | Flight's loading cutoff changes | Tightening flight cutoff dynamically reevaluates cutoff feasibility across assigned bags. |
| **7** | No valid route exists | When all routes to a destination bay are severed, system reports `ROUTE_FAILURE`. |
| **8** | Passenger attempts to access another passenger's baggage | Role-based security returns `403 FORBIDDEN` (Strict ownership enforced). |

The scenario runner is available through the test API endpoint; route and conveyor state changes persist in CSV files, so back up `backend/data/` before running scenarios against important records.

---

## 8. Accounts and deployment safety

The application initializes demonstration data when its CSV files are missing or empty. Treat seeded demo accounts as development-only and replace their credentials and sample data before public deployment. Never commit real user data, passwords, API keys, or environment secrets.

---

## 9. Project structure

```text
.
├── frontend/                           # Static HTML, CSS, and JavaScript source
├── backend/
│   ├── src/main/java/com/airport/baggage/ # Java server, models, services, and tests
│   ├── data/                            # CSV storage and sample data
│   ├── build/classes/                   # Generated Java classes (ignored)
│   └── web/                             # Generated runtime copy of frontend (ignored)
├── Dockerfile
├── render.yaml
└── run.*                               # Windows and shell launch scripts
```

The project uses Java's standard library only; there is no Node package manifest or external Java dependency. `com.airport.baggage.Main` is the server entry point. Launch scripts and Docker stage `frontend/` into `backend/web/` because the existing server serves files relative to its working directory. The scenario runner is exposed at `/api/test/run-scenarios`; it changes CSV state, so use a disposable data directory when running it.

## 10. Running the Application Locally

### Prerequisites
* **Java Development Kit (JDK) 17**.
* No external libraries, databases, or build tools (like Maven or Gradle) are required.

### Quick Start (Windows)
Double-click `run.cmd` (or `run.bat`) or run in PowerShell:
```powershell
.\run.ps1
```
The script compiles all Java sources, starts the backend on port `8080`, and launches your default browser to `http://localhost:8080`.
To compile without starting the server, use `.\run.cmd --compile-only` or `.\run.ps1 -CompileOnly`.

### Manual Start (Linux / macOS)
1. Open a terminal in the project root directory and stage the frontend for the existing server:
   ```bash
   mkdir -p backend/web
   cp -R frontend/. backend/web/
   ```
2. Compile the source code:
   ```bash
   cd backend
   mkdir -p build/classes
   javac -encoding UTF-8 -d build/classes $(find src/main/java -name "*.java")
   ```
3. Run the application:
   ```bash
   java -cp build/classes com.airport.baggage.Main 8080
   ```
4. Open your web browser and navigate to:
   ```
   http://localhost:8080
   ```

## 11. Deployment (Render)

The repository includes a Dockerfile and Render Blueprint configuration. To deploy:

1. Push the repository to GitHub.
2. In Render, choose **New → Blueprint**, connect the GitHub repository, and apply `render.yaml`.
3. Wait for the Docker build and deployment to finish, then open the service URL shown by Render.

Render supplies the `PORT` environment variable; the server uses it automatically. The included free web service is suitable for a demo, but its local filesystem is ephemeral and may be cleared on restarts or redeploys. The app stores writable state in CSV files, so production use requires durable storage and a backup plan; do not use the free instance for important user data. Replace the demo administrator account and sample data before a public production deployment.

## 12. Push local changes to GitHub

Review `git status` before staging so local CSV data or other unrelated changes are not pushed accidentally. Stage the project changes, commit, and push the current branch:

```bash
git add -A
git commit -m "Organize project structure and deployment"
git push
```
