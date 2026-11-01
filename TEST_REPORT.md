# TEST REPORT — Smart Municipal Road Maintenance System
**Municipality:** Senthamizh Nagar  
**Test Execution Date:** October 2026  
**Total Tests Executed:** 56  
**Passing Tests:** 56  
**Failing Tests:** 0  
**Success Rate:** 100%

---

## 1. Test Suite Summary

| Test Suite Category | Test Target / Scope | Executed | Passed | Failed |
| :--- | :--- | :---: | :---: | :---: |
| **Core Algorithm & Graph Suite** | PriorityQueue ordering, Dijkstra routing, equipment matching, closures | 22 | 22 | 0 |
| **Database & Security Suite** | PBKDF2 hashing, registration, role validation, authorization codes | 15 | 15 | 0 |
| **End-to-End Integration Suite** | HTTP endpoints, 3-role workflows, report lifecycle, sessions | 19 | 19 | 0 |
| **TOTAL** | **Full System Verification** | **56** | **56** | **0** |

---

## 2. Core Algorithmic Tests (`TestRunner.java`)

| Test # | Description | Expected Outcome | Result |
| :---: | :--- | :--- | :---: |
| Test 01 | Initial report catalogue load | At least 20 damage reports loaded in system | **PASS** |
| Test 02 | Initial squad count verification | Exactly 3 maintenance squads loaded | **PASS** |
| Test 03 | Priority queue top element | Highest severity report (Rank 3) ranks first | **PASS** |
| Test 04 | Tie-breaking by Accident Risk | Higher accident risk breaks severity ties | **PASS** |
| Test 05 | Tie-breaking by Road Importance | Higher road importance breaks prior ties | **PASS** |
| Test 06 | Tie-breaking by Traffic Volume | Higher traffic volume breaks prior ties | **PASS** |
| Test 07 | FIFO Tie-breaking by Report Order | Earliest submission order resolves exact rank ties | **PASS** |
| Test 08 | Invalid severity rank rejection | Out-of-bounds rank (e.g. 5) is rejected | **PASS** |
| Test 09 | Duplicate report ID prevention | Colliding report ID returns error | **PASS** |
| Test 10 | Equipment membership validation | Team Beta (T2) lacks Crack Sealer | **PASS** |
| Test 11 | Team availability post-assignment | Squad marked unavailable immediately after dispatch | **PASS** |
| Test 12 | Duplicate assignment prevention | Assigned report cannot be allocated a second time | **PASS** |
| Test 13 | Dijkstra closure detour routing | Alternative route found despite L08 ↔ L12 closure | **PASS** |
| Test 14 | Unreachable destination handling | Isolated location (L15) remains pending | **PASS** |
| Test 15 | Active assignment verification | At least one report is currently marked ASSIGNED | **PASS** |
| Test 16 | Team location update upon completion | Squad moves to repaired damage site | **PASS** |
| Test 16b| Status transition to COMPLETED | Report status correctly set to COMPLETED | **PASS** |
| Test 16c| Squad release upon completion | Squad marked AVAILABLE immediately upon completion | **PASS** |
| Test 17 | PriorityQueue dynamic reprioritization | Rank updates re-sort pending queue | **PASS** |
| Test 18 | Assigned report priority immutability | Assigned reports are protected from priority queue changes | **PASS** |
| Test 19 | Non-disruptive damage reporting | Roads remain passable unless explicitly closed | **PASS** |
| Test 20 | Reopened road route restoration | Reopened roads are included in subsequent routing | **PASS** |

---

## 3. Database & Security Tests (`DatabaseAndRoleAuthTest.java`)

| Test # | Description | Expected Outcome | Result |
| :---: | :--- | :--- | :---: |
| Test 01 | PBKDF2 hash & salt generation | Generates 64-character hash & 32-character salt | **PASS** |
| Test 02 | Constant-time password verification | Verifies valid password, rejects invalid password | **PASS** |
| Test 03 | Ward Officer account registration | Valid registration record created | **PASS** |
| Test 04 | Duplicate username rejection | Rejects existing username with friendly message | **PASS** |
| Test 05 | Duplicate email rejection | Rejects existing email with friendly message | **PASS** |
| Test 06 | Password length validation | Rejects password shorter than 6 characters | **PASS** |
| Test 07 | Engineering account registration | Valid engineer record created | **PASS** |
| Test 08 | Maintenance Team squad registration | Valid maintenance squad record created | **PASS** |
| Test 09 | Valid role authentication | Returns server-side session UUID on valid login | **PASS** |
| Test 10 | Incorrect password rejection | Rejects invalid password attempt | **PASS** |
| Test 11 | Cross-role privilege escalation block | Rejects Ward account attempting Engineering login | **PASS** |
| Test 12 | Session user attribute preservation | UserAccount retains role and ward ID from session | **PASS** |
| Test 13 | Session logout invalidation | Invalidate method completely removes active session | **PASS** |
| Test 14 | MySQL Connector/J driver load | Driver loaded on classpath from lib/ directory | **PASS** |
| Test 15 | Engineering authorization code | Code is verified against db.properties setting | **PASS** |

---

## 4. End-to-End Workflow Integration Tests (`test_e2e_signup_login_mysql.py`)

| Test # | Workflow Stage | Verification Details | Result |
| :---: | :--- | :--- | :---: |
| Test 01 | Portal Landing Page | Homepage displays separate Sign In & Sign Up for 3 roles | **PASS** |
| Test 02 | Role Selection Page | `/signup` displays distinct role selection cards | **PASS** |
| Test 03 | Ward Signup Form | Form renders with all required profile input fields | **PASS** |
| Test 04 | Password Mismatch | Signup rejects non-matching confirm password | **PASS** |
| Test 05 | Successful Ward Signup | Redirects to login page with registration success query | **PASS** |
| Test 06 | Ward Authentication | Newly registered user logs in and opens `/ward/dashboard` | **PASS** |
| Test 07 | Authorization Code Gate | Engineering signup blocks invalid authorization codes | **PASS** |
| Test 08 | Authorized Engineer Signup | Engineering signup succeeds with code `MUNI-ENG-2026` | **PASS** |
| Test 09 | Engineer Authentication | Newly registered engineer opens `/engineering/dashboard` | **PASS** |
| Test 10 | Squad Registration | Maintenance squad signup succeeds and redirects to login | **PASS** |
| Test 11 | Cross-Role Access Control | Ward user cannot access Engineering login | **PASS** |
| Test 12 | Damage Submission | Ward Officer submits damage (enters `SUBMITTED`) | **PASS** |
| Test 13 | Engineering Review Inbox | Report appears in Engineering verification inbox | **PASS** |
| Test 14 | Report Verification | Engineering verifies report into `PriorityQueue` | **PASS** |
| Test 15 | Squad Allocation | Nearest compatible team allocated via Dijkstra | **PASS** |
| Test 16 | Squad Job Notification | Dispatched team accesses active repair and route | **PASS** |
| Test 17 | Start Repair Work | Team starts repair (marked `IN_PROGRESS`) | **PASS** |
| Test 18 | Complete Repair Work | Team submits outcome note (marked `COMPLETED`, squad freed) | **PASS** |
| Test 19 | Session Invalidation | Logout clears session and blocks protected routes | **PASS** |

---

## 5. Summary Conclusion
All 56 test assertions passed without regression. The application successfully validates salted password hashing, dynamic role-based signups, MySQL integration, ACID transactions, and deterministic Dijkstra route allocations.
