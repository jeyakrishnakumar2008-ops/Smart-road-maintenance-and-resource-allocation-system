# Smart Municipal Road Maintenance and Resource Allocation System
**Municipality:** Senthamizh Nagar  
**Technology Stack:** Core Java, Built-in HTTP Server, MySQL, JDBC (MySQL Connector/J), HTML5 & CSS3 (**Zero JavaScript**)

---

## 1. Project Overview

A full-stack municipal infrastructure management application built for **Senthamizh Nagar Municipal Corporation**. The system automates the complete lifecycle of road damage inspection, verification, multi-criteria priority ranking, route navigation, and team resource allocation across three dedicated municipal departments:

1. **Ward Officer:** Inspects field damage, submits standardized reports with severity/risk assessments, and resubmits returned reports.
2. **Engineering Section:** Verifies submitted damage, manages priority queues, oversees road closures, and assigns compatible squads using shortest-path calculations.
3. **Maintenance Team:** Dispatched squads (Alpha T1, Beta T2, Gamma T3) view turn-by-turn routes via Dijkstra's algorithm, start repairs, and submit work completion reports.

---

## 2. Key Architecture & Features

- **Three Separate Role-Based Signup & Login Portals:**
  - Dedicated signup forms: `/signup/ward`, `/signup/engineering`, `/signup/maintenance`
  - Dedicated login forms: `/login/ward`, `/login/engineering`, `/login/maintenance`
  - Dedicated role dashboards: `/ward/dashboard`, `/engineering/dashboard`, `/maintenance/dashboard`
- **Security & Account Provisioning:**
  - Passwords hashed using **PBKDF2WithHmacSHA256** with unique 16-byte cryptographic salts. Plaintext passwords are never stored.
  - Engineering Section registration requires an authorization code (`MUNI-ENG-2026`) configured in `db.properties`.
  - Role isolation on the server prevents unauthorized access or URL tampering.
- **MySQL Database & JDBC Integration:**
  - Persistent storage using normalized InnoDB tables with foreign key constraints.
  - ACID transactions with automatic rollback protect multi-record assignments and repair completions.
  - Audit history logging (`report_history`) tracks all state transitions.
  - High availability: operates with in-memory synchronization fallback if MySQL is offline.
- **Algorithms Preserved in Java:**
  - **PriorityQueue Multi-Tier Comparator:** 1. Severity Rank (1–3) → 2. Accident Risk (1–3) → 3. Road Importance (1–3) → 4. Traffic Rank (1–3) → 5. Submission Order.
  - **Dijkstra's Shortest Path Algorithm:** Min-heap algorithm finding the fastest route while avoiding closed roads.
  - **Equipment Compatibility Matching:** HashSet containment check ensuring squads possess required machinery before assignment.
- **Zero JavaScript Rule:** 100% server-rendered HTML and native GET/POST forms.

---

## 3. Project Structure

```
SmartMunicipalRoadMaintenance/
├── database/
│   ├── schema.sql              — Normalized MySQL table definitions & constraints
│   └── sample_data.sql         — Safe seed data (locations, roads, demo users, equipment)
├── lib/
│   └── mysql-connector-j-8.4.0.jar — Official MySQL Connector/J JDBC Driver
├── src/
│   ├── Main.java               — Entry point; boots service and starts HTTP server
│   ├── DatabaseConnection.java — JDBC connection manager (reads db.properties)
│   ├── DatabaseRepository.java — Data Access Object (PreparedStatements & transactions)
│   ├── PasswordHasher.java     — PBKDF2WithHmacSHA256 salted password hashing
│   ├── UserAccount.java        — Authenticated user entity model
│   ├── SessionManager.java     — Server-side session manager & registration cache
│   ├── HttpUtils.java          — HTTP request/response and cookie utilities
│   ├── HtmlHelper.java         — Reusable layout, navigation & alert builders
│   ├── WebServer.java          — HTTP server & homepage with role sign-in cards
│   ├── SignupHandler.java      — Role-specific signup forms & validation
│   ├── LoginHandler.java       — Role-specific login verification & authentication
│   ├── WardHandler.java        — Ward Officer dashboard, submission & correction
│   ├── EngineeringHandler.java — Engineering dashboard, inbox, priority & routing
│   ├── MaintenanceHandler.java — Squad dashboard, turn-by-turn routing & completion
│   ├── MunicipalService.java   — Central business logic & workflow manager
│   ├── RoadDamage.java         — Damage report model with 7-stage status workflow
│   ├── MaintenanceTeam.java    — Squad model with location, equipment & availability
│   ├── Equipment.java          — Equipment catalog model
│   ├── RoadNetwork.java        — Weighted directed road graph & Dijkstra min-heap
│   ├── RoadConnection.java     — Road edge model with open/closed status
│   ├── DijkstraResult.java     — Shortest path container with travel time
│   ├── AssignmentResult.java   — Team allocation outcome container
│   ├── RepairPriorityPolicy.java — 5-tier PriorityQueue comparator
│   ├── TestRunner.java         — 22-test core algorithmic test suite
│   └── DatabaseAndRoleAuthTest.java — 15-test security & database test suite
├── web/
│   └── style.css               — Responsive light-theme stylesheet (blue/teal accents)
├── db.properties               — Local MySQL database configuration
├── db.properties.example       — Template configuration file for version control
├── README.md
├── CODE_EXPLANATION.md
├── TEST_REPORT.md
└── MENTOR_VIVA.md
```

---

## 4. Setup & Running Instructions (Windows)

### Prerequisites
- **Java JDK 11 or higher** (`javac -version`, `java -version`)
- **MySQL Server 8.0+** (Optional: the app automatically connects if running, or runs in high-availability in-memory mode if offline)

### Step 1: MySQL Setup (Optional / Recommended)
Start your MySQL server (via MySQL Workbench, XAMPP, or Windows Service), then run:
```sql
SOURCE database/schema.sql;
SOURCE database/sample_data.sql;
```
Configure your credentials in `db.properties`:
```properties
db.url=jdbc:mysql://localhost:3306/smart_municipal_maintenance?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=your_password
security.engineering.registration_code=MUNI-ENG-2026
```

### Step 2: Compile the Application
```powershell
cd "c:\Users\Gopishankar\MINIPROJECT REV2\SmartMunicipalRoadMaintenance"
javac -cp "lib/*;." -d out src/*.java
```

### Step 3: Run Automated Test Suites
Run the test suites to verify algorithms, password hashing, and authentication:
```powershell
java -cp "out;lib/*" DatabaseAndRoleAuthTest
java -cp "out;lib/*" TestRunner
```

### Step 4: Start the Web Application
```powershell
java -cp "out;lib/*" Main
```
Open your browser and navigate to:  
👉 **http://localhost:8080**

---

## 5. Official Demo Accounts & Evaluation Credentials

| Role | Username | Password | Display Name | Purpose / Scope |
| :--- | :--- | :--- | :--- | :--- |
| **Ward Officer** | `ward1` | `ward123` | Suresh Kumar | Ward 3 Field Inspections & Reporting |
| **Ward Officer** | `ward2` | `ward123` | Priya Devi | Ward 7 Field Inspections & Reporting |
| **Engineering Section** | `eng1` | `eng123` | Chief Engineer Rajan | Damage Verification, Prioritization & Dispatch |
| **Maintenance Squad T1** | `T1` | `team123` | Alpha Squad (T1) | Assigned site repairs (Stationed at L01 Town Hall) |
| **Maintenance Squad T2** | `T2` | `team123` | Beta Squad (T2) | Assigned site repairs (Stationed at L04 Railway Station) |
| **Maintenance Squad T3** | `T3` | `team123` | Gamma Squad (T3) | Assigned site repairs (Stationed at L05 Bus Terminus) |

### Engineering Registration Authorization Code
When registering a new Engineering Section account at `/signup/engineering`, enter:  
`MUNI-ENG-2026`

---

## 6. Complete Three-Role Operational Workflow

```
[ Ward Officer ] ──(Submits Report)──> [ SUBMITTED ]
                                             │
                                             ▼
[ Engineering Section ] ────(Reviews)───> [ VERIFIED ] (Enters PriorityQueue)
          │                                  │
          ├─(Return for Correction)          ▼
          │  └──> [ RETURNED ]        [ ASSIGNED ] (Allocates nearest eligible team via Dijkstra)
          │                                  │
          └─(Reject Invalid)                 ▼
             └──> [ REJECTED ]        [ IN_PROGRESS ] (Maintenance Team clicks "Start Repair")
                                             │
                                             ▼
                                      [ COMPLETED ] (Maintenance Team logs notes & releases squad)
```

1. **Sign Up:** Register a new user at `/signup/ward`, `/signup/engineering` (using code `MUNI-ENG-2026`), or `/signup/maintenance`.
2. **Ward Inspection:** Log in as Ward Officer (`ward1`), fill out damage report with severity/risk ranks.
3. **Engineering Verification:** Log in as Engineering (`eng1`), view report in Inbox, verify into priority queue.
4. **Team Allocation:** From the Assign page, assign highest priority repair to nearest compatible team via Dijkstra.
5. **Field Maintenance:** Log in as assigned squad (`T1`), follow Dijkstra turn-by-turn route, click "Start Repair Work", and complete with notes. Squad is released and ready for new assignments.

---

## 7. Troubleshooting

- **Port 8080 already in use:**  
  Start with a custom port argument: `java -cp "out;lib/*" Main 8081`
- **MySQL Driver Not Found:**  
  Ensure the `-cp "out;lib/*"` flag is included in your run command so Java can load `lib/mysql-connector-j-8.4.0.jar`.
- **MySQL Access Denied:**  
  Verify the username and password in `db.properties` match your local MySQL configuration.
