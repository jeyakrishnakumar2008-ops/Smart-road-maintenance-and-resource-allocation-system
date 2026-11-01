# PROJECT REPORT — Smart Municipal Road Maintenance and Resource Allocation

**Title:** Smart Municipal Road Maintenance and Resource Allocation  
**Municipality:** Senthamizh Nagar (Fictional)  
**Developer:** Mini Project — Computer Science  
**Date:** October 2026  

---

## 1. Problem Analysis

### 1.1 Current Problem

The engineering section of Senthamizh Nagar manually manages road-damage reports submitted by ward officers. The manual process leads to several operational problems:

| Problem | Impact |
|---------|--------|
| High-risk damage may remain unattended | Accidents, public injury |
| Two teams may be assigned to the same repair | Wasted resources |
| Teams may lack required equipment | Repairs cannot be completed |
| Closed roads may make a destination unreachable | Delayed response |
| Travel and resources used inefficiently | Increased cost |
| No systematic priority ranking | Low-risk repairs may be addressed before critical ones |

### 1.2 Proposed Solution

Build a Java web application that:
- Ranks damage reports automatically using a multi-factor priority system.
- Allocates maintenance teams based on equipment compatibility and shortest travel distance.
- Calculates optimal routes using Dijkstra's shortest-path algorithm.
- Manages road closures and prevents teams from being routed through impassable roads.
- Tracks repair status from PENDING → ASSIGNED → COMPLETED.

---

## 2. Assumptions

- A damage report is submitted only once per location event.
- A maintenance team can handle only one repair at a time.
- All road travel times are estimated in minutes.
- Equipment is not shared between teams during a repair.
- Road closures are managed manually by administrators.
- A damaged road is NOT automatically closed — these are separate events.
- Data persistence is out of scope; data resets when the server restarts.

---

## 3. Design

### 3.1 System Architecture

```
Browser (HTML + CSS)
       ↕  HTTP GET / POST
Java HTTP Server (WebServer.java)
       ↓
MunicipalService.java  ←→  HashMap (reports, teams)
       ↓                     PriorityQueue (pending)
RoadNetwork.java        ←→  Dijkstra's algorithm
```

### 3.2 OOP Design Decisions

| Concept | Application |
|---------|-------------|
| Encapsulation | All fields are private; state changes go through controlled methods |
| Inheritance | `PotholeRepair` and `ResurfacingRepair` extend `RepairJob` |
| Polymorphism | `requiredEquipment()` behaves differently per subclass |
| Abstraction | `RepairJob` forces subclasses to declare their equipment needs |
| Composition | `MaintenanceTeam` contains a `HashSet<Equipment>` |

---

## 4. Algorithms

### 4.1 Priority Queue — 5-Step Comparator

```
Given two reports A and B, the comparator applies:

1. If severity(B) ≠ severity(A) → higher severity wins.
2. Else if accidentRisk(B) ≠ accidentRisk(A) → higher accident risk wins.
3. Else if roadImportance(B) ≠ roadImportance(A) → higher importance wins.
4. Else if traffic(B) ≠ traffic(A) → higher traffic wins.
5. Else → lower reportOrder (earlier submission) wins.
```

**Data structure:** `java.util.PriorityQueue` (binary min-heap, O(log n) per operation).

### 4.2 Dijkstra's Shortest Path

The road network is a weighted directed graph stored as an adjacency list.

**Algorithm:**
1. Set distance to source = 0; all others = infinity.
2. Use a min-heap (PriorityQueue) ordered by accumulated distance.
3. Repeatedly extract the minimum-distance location and relax its open neighbours.
4. If the destination is extracted, reconstruct path from `prev` map and return.
5. If heap empties without reaching destination, return "unreachable".

**Closed-road handling:** A single `if (!road.isOpen()) continue;` skips closed roads.

**Time complexity:** O((V + E) log V) — efficient for small municipal road networks.

### 4.3 Team Allocation

```
1. Get highest-priority PENDING report from queue.
2. For each team:
   a. Skip if unavailable.
   b. Skip if missing required equipment.
   c. Run Dijkstra's from team's location to report's location.
   d. Skip if unreachable.
   e. Track team with minimum travel time.
3. Assign best team, mark unavailable, remove report from pending queue.
4. If no eligible team, report remains PENDING with explanation.
```

---

## 5. Implementation

### 5.1 Sample Data

| Item | Count |
|------|-------|
| Locations | 15 |
| Maintenance teams | 3 |
| Damage reports | 22 |
| Road connections (bidirectional) | 20 pairs = 40 directed edges |
| Initially closed roads | 1 pair (L08 ↔ L12) |
| Isolated/unreachable location | 1 (L15 — Isolated Lane) |

### 5.2 Website Pages

| Page | URL | Function |
|------|-----|----------|
| Dashboard | `/` | Shows live statistics |
| Damage Reports | `/reports` | View, add, update reports |
| Priority Queue | `/priority` | Ordered pending reports with explanation |
| Maintenance Teams | `/teams` | Team status and equipment |
| Road Network | `/network` | Connections, close/reopen roads |
| Assign & Route | `/assign` | Run team assignment with Dijkstra's |
| Complete Repair | `/complete` | Mark repair done, release team |

### 5.3 Technology Stack

| Layer | Technology |
|-------|-----------|
| Logic | Java (default package, no external libraries) |
| HTTP server | `com.sun.net.httpserver` (built into JDK) |
| Presentation | HTML generated by Java string building |
| Styling | Vanilla CSS (white/light theme) |
| JavaScript | **None — strictly prohibited** |

---

## 6. Results

### 6.1 System Verification

- All 22 automated tests pass.
- Server starts and serves all 7 pages correctly.
- Forms process inputs and update Java state.
- Priority queue maintains correct order across insertions and updates.
- Dijkstra's routes correctly avoid the closed L08↔L12 road.
- Unreachable reports (R015 at L15) correctly remain PENDING.

### 6.2 Sample Assignment Walk-Through

**Starting state:**
- R001 is top of the priority queue (sev=3, acc=3, imp=3, trf=3, order=1).
- Location: L06 (Hospital Road). Required equipment: Pothole Filler.
- T1 (Alpha Squad) at L01, has Pothole Filler.
- T2 (Beta Squad) at L04, does NOT have Pothole Filler → rejected.
- T3 (Gamma Squad) at L05, has Pothole Filler.

**Dijkstra's routes calculated:**
- T1 at L01 → L06: L01→L03→L06, total = 8+7 = 15 min.
- T3 at L05 → L06: L05→L02→L01→L03→L06 or similar. Let Java calculate.

**Result:** Java selects the team with the shorter total travel time.

---

## 7. SDG Connections

### SDG 3 — Good Health and Well-Being

Unrepaired potholes and damaged road surfaces are associated with road accidents, injuries and vehicle damage. By prioritising repairs based on accident-risk rank, the system helps ensure that high-risk locations receive attention first. This may reduce the probability of accidents at those sites.

*Note: This is a simulation. No claim is made about measured accident-rate reductions.*

### SDG 9 — Industry, Innovation and Infrastructure

The system applies computer science (data structures, graph algorithms) to improve the efficiency of public infrastructure maintenance. Dijkstra's algorithm ensures teams travel the shortest available route, reducing fuel consumption and vehicle wear. Equipment-compatibility checks prevent teams from being dispatched to repairs they cannot complete.

### SDG 11 — Sustainable Cities and Communities

Senthamizh Nagar is a fictional model of a municipality. The system demonstrates how a low-cost, software-only solution (no database, no external APIs) can help a small or medium municipality manage road maintenance more systematically. Good roads improve mobility, support local commerce and contribute to a liveable city.

---

## 8. Limitations

1. **No persistence:** Data resets when the server is restarted.
2. **One assignment per click:** The system processes one pending report per button press.
3. **No authentication:** Any user can access all functions.
4. **Estimated travel times:** Real-world times depend on live traffic, which is not available.
5. **Manual closures only:** Road closures must be entered by hand; there is no sensor integration.
6. **No map visualization:** Routes are shown as location-ID sequences, not on a geographic map.

---

## 9. Future Enhancements

1. Add SQLite or PostgreSQL for persistent data storage.
2. Assign all pending reports automatically in one operation.
3. Integrate with OpenStreetMap for real geographic routing.
4. Add PDF or CSV export of reports.
5. Send email or SMS alerts to team leaders when a repair is assigned.
6. Add an analytics dashboard with charts (using SVG, generated by Java).
7. Implement role-based login (ward officer, supervisor, administrator).
