# MENTOR VIVA QUESTIONS & ANSWERS
**Project Title:** Smart Municipal Road Maintenance and Resource Allocation System  
**Municipality:** Senthamizh Nagar  
**Target Audience:** Beginner-friendly explanation guide for viva voce and project demonstration.

---

### Q1: Why did you choose MySQL for persistent storage in this project?
**Answer:**  
In municipal operations, road damage reports, repair histories, and team dispatches must persist permanently across server restarts. MySQL was chosen because:
1. It is a robust relational database management system (RDBMS) using the **InnoDB storage engine**, which fully supports ACID transactions and foreign key constraints.
2. It allows normalized data modeling (`users`, `ward_officer_profiles`, `road_damage_reports`, `repair_assignments`, `report_history`) to prevent data redundancy and maintain referential integrity.
3. It provides high query performance with indexing on primary keys and search attributes.

---

### Q2: What is JDBC, and how does your Java code communicate with MySQL?
**Answer:**  
**JDBC (Java Database Connectivity)** is Java's standard API for executing SQL statements and retrieving results from relational databases.  
In our project:
- We use the official `com.mysql.cj.jdbc.Driver` included in `lib/mysql-connector-j-8.4.0.jar`.
- `DatabaseConnection.java` creates database connections using `DriverManager.getConnection(url, user, password)`.
- It reads configuration dynamically from `db.properties` or environment variables, keeping sensitive credentials decoupled from application source code.
- We manage all database resources using Java's `try-with-resources` syntax, which automatically closes connections, statements, and result sets, preventing connection leaks.

---

### Q3: Why are passwords hashed with PBKDF2 and a salt instead of MD5 or plaintext?
**Answer:**  
Plaintext passwords should never be stored in a database because a database breach would immediately compromise every user's credentials.
- **Why not MD5 or SHA-1?** Simple hash algorithms can be computed billions of times per second on modern GPUs, making them vulnerable to precomputed rainbow tables and dictionary attacks.
- **What is a Salt?** A salt is a unique 16-byte random string generated per user via `SecureRandom`. It guarantees that even if two users choose the exact same password, their stored hashes will look completely different, neutralizing rainbow table attacks.
- **What is PBKDF2?** PBKDF2 (Password-Based Key Derivation Function 2) applies 65,536 iterations of HMAC-SHA256. This deliberate computational workload makes brute-force attacks computationally impractical.
- **Constant-Time Comparison:** When verifying passwords, our code uses bitwise XOR comparison (`slowEquals()`) to prevent timing side-channel attacks.

---

### Q4: Why are PreparedStatements important in JDBC?
**Answer:**  
`PreparedStatement` pre-compiles SQL query templates and treats user-provided inputs strictly as literal parameter data values rather than executable code.
- If an attacker submits a malicious input like `' OR '1'='1`, a regular string concatenation would alter the query logic and bypass authentication (SQL Injection).
- With `PreparedStatement`, the database treats the input strictly as literal characters, completely eliminating SQL Injection vulnerabilities.

---

### Q5: How does the signup and login workflow operate across the three roles?
**Answer:**  
The application enforces strict role-based separation:
1. **Signup Phase:** A user navigates to `/signup/ward`, `/signup/engineering`, or `/signup/maintenance`.
   - Each role requires specific fields (Ward number for Ward Officers; Department & secure authorization code `MUNI-ENG-2026` for Engineers; Squad ID & equipment for Maintenance squads).
   - The password is salted and hashed using PBKDF2.
   - The user record is inserted into `users` and their specific profile table within a database transaction.
2. **Login Phase:** The user logs in at their role's login portal (`/login/ward`, `/login/engineering`, `/login/maintenance`).
   - The server queries MySQL using `PreparedStatement`.
   - It verifies the PBKDF2 hash against the stored salt.
   - It checks that the user's role matches the portal role (a Ward Officer cannot log in through the Engineering portal).
   - On success, a server-side session UUID is generated, stored in `SessionManager`, and delivered to the browser as an HTTP cookie (`SESSIONID`).

---

### Q6: Why are there three separate user roles instead of a single admin dashboard?
**Answer:**  
In a municipal corporation, duties are segregated for security and workflow integrity (Separation of Concerns):
- **Ward Officers** work in the field. They inspect roads and report damages, but they should not allocate teams or modify equipment assignments.
- **Engineering Section** serves as the central administrative authority. They verify physical damage, evaluate severity, manage road closures, and assign resources.
- **Maintenance Teams** are mobile squads executing field repairs. Each squad should only view its own assigned task, turn-by-turn navigation, and start/complete buttons.

---

### Q7: How do database transactions maintain consistency in repair assignment and completion?
**Answer:**  
A database transaction groups multiple related SQL operations into an all-or-nothing unit (**Atomicity**).
- When Engineering assigns a squad:
  1. The report status is updated to `ASSIGNED`.
  2. The team availability status is updated to `ASSIGNED`.
  3. A new row is inserted into `repair_assignments`.
  4. An audit record is logged in `report_history`.
- In JDBC, we call `conn.setAutoCommit(false);`. If any query encounters an error or constraint violation, `conn.rollback();` is triggered, restoring the database to its clean original state. If all queries succeed, `conn.commit();` commits the entire batch atomically.

---

### Q8: How does the priority queue work, and what is the comparator hierarchy?
**Answer:**  
Verified reports enter a Java `PriorityQueue<RoadDamage>` configured with a custom 5-tier comparator (`RepairPriorityPolicy.java`):
1. **Severity Rank (1–3):** Higher physical damage is prioritized first.
2. **Accident Risk Rank (1–3):** Breaks severity ties based on collision or injury likelihood.
3. **Road Importance Rank (1–3):** Major arterial avenues rank higher than local lanes.
4. **Traffic Rank (1–3):** Higher daily traffic volume breaks ties.
5. **Report Order (FIFO):** Earliest submitted report serves as the final tie-breaker.

This ensures mathematically deterministic, fair, and transparent prioritization without arbitrary scoring formulas.

---

### Q9: How does Dijkstra's algorithm calculate the shortest travel time, and how are road closures handled?
**Answer:**  
`RoadNetwork.java` represents Senthamizh Nagar as a weighted directed graph using an adjacency list:
- Locations are vertices (`L01` through `L15`).
- Roads are directed edges with travel times in minutes as weights.
- Dijkstra's algorithm uses a min-heap (`PriorityQueue`) to greedily settle nodes with the shortest accumulated travel time from the squad's starting location.
- **Road Closures:** Before exploring any neighbor node, the algorithm checks `roadConnection.isOpen()`. If a road is closed (e.g. `L08 ↔ L12`), the algorithm skips that edge and automatically computes an alternative detour path or flags the destination as unreachable.

---

### Q10: How does equipment compatibility matching work during squad allocation?
**Answer:**  
Each repair type requires specific tools:
- Potholes require `Pothole Filler`.
- Resurfacing requires `Road Roller`.
- Crack sealing requires `Crack Sealer`.
Each `MaintenanceTeam` contains a `HashSet<Equipment>`. Before selecting a squad, the application calls `team.hasEquipmentByName(requiredEquipment)`. If a squad is stationed nearby but lacks the required equipment, it is bypassed in favor of a compatible squad.
