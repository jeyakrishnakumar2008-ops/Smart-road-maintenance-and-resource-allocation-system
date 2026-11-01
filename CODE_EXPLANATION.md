# CODE EXPLANATION — Smart Municipal Road Maintenance System
**Municipality:** Senthamizh Nagar  
**Project:** Three-Role Architecture, PBKDF2 Password Security & MySQL Database Integration

---

## 1. Architectural Overview

The application follows an enterprise Model-View-Controller (MVC) and Data Access Object (DAO) architecture implemented entirely in standard Java without external web frameworks:

- **Model Layer:** `UserAccount`, `RoadDamage`, `MaintenanceTeam`, `Equipment`, `RoadNetwork`, `RoadConnection`, `DijkstraResult`, `AssignmentResult`.
- **Database / DAO Layer:** `DatabaseConnection`, `DatabaseRepository`, `PasswordHasher`.
- **Service Layer:** `MunicipalService`, `SessionManager`, `RepairPriorityPolicy`.
- **Controller / Presentation Layer:** `WebServer`, `LoginHandler`, `SignupHandler`, `WardHandler`, `EngineeringHandler`, `MaintenanceHandler`, `HttpUtils`, `HtmlHelper`.

---

## 2. Security & Password Hashing (`PasswordHasher.java`)

### Why PBKDF2WithHmacSHA256?
Storing plaintext passwords or simple MD5/SHA-1 hashes in a database is vulnerable to rainbow table lookups and brute-force cracking.
`PasswordHasher.java` uses the standard Java Cryptography Architecture (`javax.crypto.SecretKeyFactory`):

1. **Cryptographic Salt Generation:**
   `SecureRandom` generates a cryptographically unpredictable 16-byte (128-bit) salt per user account, encoded as a 32-character hexadecimal string.
2. **Key Stretching:**
   Uses **65,536 iterations** of HMAC-SHA256 with a 256-bit derived key length. This intentionally introduces a computational cost that makes hardware-accelerated dictionary attacks infeasible.
3. **Constant-Time Verification:**
   `slowEquals()` compares hash bytes using bitwise XOR (`^`) rather than `String.equals()`. This guarantees constant execution time regardless of where mismatches occur, completely neutralizing timing side-channel attacks.

---

## 3. Database Connection & Schema (`DatabaseConnection.java` & `DatabaseRepository.java`)

### Connection Management
`DatabaseConnection.java` reads credentials dynamically from `db.properties` or environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`), ensuring credentials are never hardcoded in source code:
- Automatically loads `com.mysql.cj.jdbc.Driver` from `lib/mysql-connector-j-8.4.0.jar`.
- Uses standard JDBC `DriverManager.getConnection()`.
- Provides `isAvailable()` to verify database reachability.
- Automatically initializes table structures from `database/schema.sql` on startup.

### PreparedStatements & SQL Injection Prevention
All user inputs (usernames, passwords, report details, observations) are passed exclusively through parameterized SQL queries via `PreparedStatement`:
```java
String sql = "SELECT * FROM users WHERE (username = ? OR email = ?) AND role = ?";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, input);
    ps.setString(2, input);
    ps.setString(3, expectedRole);
    ...
}
```
User inputs are treated strictly as literal data values and can never be interpreted as executable SQL syntax.

### ACID Database Transactions
Multi-record operations (such as assigning a squad or completing a repair) require multiple database modifications across separate tables (`road_damage_reports`, `maintenance_teams`, `repair_assignments`, `report_history`).
`DatabaseRepository.java` wraps these operations in strict ACID transactions:
```java
conn.setAutoCommit(false); // Begin Transaction
try {
    // 1. Update report status to ASSIGNED
    // 2. Update team availability to ASSIGNED
    // 3. Insert assignment record
    // 4. Insert audit record in report_history
    conn.commit(); // Atomic commit
} catch (SQLException e) {
    conn.rollback(); // Rollback all changes if any query fails
}
```

---

## 4. Role-Based Authentication & Sessions (`SessionManager.java`)

1. **Server-Side Session Storage:**
   Active sessions are stored in an in-memory `HashMap<String, UserAccount>` keyed by cryptographically random UUID strings.
2. **HTTP Cookies:**
   On successful authentication, the server transmits the UUID in an HTTP `Set-Cookie: SESSIONID=<uuid>; Path=/; HttpOnly` header.
3. **Role Enforcement on Protected Routes:**
   Before executing any protected action, the handler extracts the session cookie via `HttpUtils.getSessionUser()`. If the user is unauthenticated or has an incorrect role, the request is immediately redirected to `/login`:
   - `WardHandler`: Requires `user.isWardOfficer()`
   - `EngineeringHandler`: Requires `user.isEngineering()`
   - `MaintenanceHandler`: Requires `user.isMaintenance()`

---

## 5. Core Algorithmic Implementations

### Multi-Tier PriorityQueue (`RepairPriorityPolicy.java`)
The municipal repair queue uses a Java `PriorityQueue<RoadDamage>` configured with a deterministic 5-tier comparator:
1. **Severity Rank (1–3):** Physical hazard extent (crater depth, structural damage).
2. **Accident Risk Rank (1–3):** Likelihood of vehicle collisions or pedestrian harm.
3. **Road Importance Rank (1–3):** Arterial corridors (collector roads, bus routes) over local alleys.
4. **Traffic Rank (1–3):** Daily vehicle volume.
5. **Report Order (FIFO):** Tie-breaker ensuring earlier reported damages are serviced first.

### Dijkstra's Shortest Path Algorithm (`RoadNetwork.java`)
Calculates the fastest route from a squad's current location to a damage site:
- **Graph Representation:** Adjacency list `HashMap<String, List<RoadConnection>>` where edges store travel time in minutes.
- **Min-Heap Optimization:** Uses `PriorityQueue<Object[]>` ordered by accumulated travel time.
- **Road Closure Handling:** The algorithm dynamically skips any `RoadConnection` where `isOpen() == false`. If a road is closed (e.g. `L08 ↔ L12`), the algorithm automatically computes an alternative detour route or marks the site as unreachable.

### Equipment Compatibility (`MaintenanceTeam.java`)
Before assigning a squad, the system performs a `HashSet.containsAll()` check against the report's required machinery:
- Pothole repairs require `Pothole Filler`.
- Resurfacing repairs require `Road Roller`.
- Crack sealing requires `Crack Sealer`.
Even if a squad is geographically closest, it will be skipped if it lacks the compatible machinery.
