import urllib.request
import urllib.parse
import http.cookiejar
import sys
import re

BASE_URL = "http://localhost:8080"
passed = 0
failed = 0

def log_test(name, success, details=""):
    global passed, failed
    if success:
        passed += 1
        print(f"  [PASS] {name} {details}")
    else:
        failed += 1
        print(f"  [FAIL] {name} {details}")

class Client:
    def __init__(self):
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cj))

    def get(self, path):
        req = urllib.request.Request(BASE_URL + path)
        try:
            with self.opener.open(req) as resp:
                return resp.getcode(), resp.read().decode('utf-8', errors='ignore'), resp.geturl()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode('utf-8', errors='ignore'), e.geturl()

    def post(self, path, data):
        encoded = urllib.parse.urlencode(data).encode('utf-8')
        req = urllib.request.Request(BASE_URL + path, data=encoded, method='POST')
        req.add_header('Content-Type', 'application/x-www-form-urlencoded')
        try:
            with self.opener.open(req) as resp:
                return resp.getcode(), resp.read().decode('utf-8', errors='ignore'), resp.geturl()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode('utf-8', errors='ignore'), e.geturl()

print("=" * 65)
print("  Full 3-Role Signup, Login & Workflow Integration Test Suite")
print("=" * 65)

anon = Client()

# Test 1: Homepage contains 3 role cards with separate Sign Up and Sign In buttons
code, body, url = anon.get("/")
has_ward = "/login/ward" in body and "/signup/ward" in body
has_eng  = "/login/engineering" in body and "/signup/engineering" in body
has_maint = "/login/maintenance" in body and "/signup/maintenance" in body
log_test("1. Homepage displays separate Sign In & Sign Up for all 3 roles",
         code == 200 and has_ward and has_eng and has_maint)

# Test 2: /signup landing page
code, body, url = anon.get("/signup")
log_test("2. /signup route renders role selection cards",
         code == 200 and "New Account Registration" in body and "/signup/ward" in body)

# Test 3: Ward Officer Signup Form rendered
code, body, url = anon.get("/signup/ward")
log_test("3. Ward Officer signup form renders with required fields",
         code == 200 and "wardNumber" in body and "employeeId" in body)

# Test 4: Mismatched password rejected on signup
code, body, url = anon.post("/signup/ward", {
    "fullName": "Test Inspector",
    "employeeId": "EMP-T99",
    "wardNumber": "W05",
    "email": "test.mismatch@muni.gov",
    "username": "ward_mismatch",
    "password": "Password123",
    "confirmPassword": "DifferentPassword123"
})
log_test("4. Password mismatch rejection in Ward Officer signup",
         "do not match" in body.lower())

# Test 5: Successful Ward Officer Signup
code, body, url = anon.post("/signup/ward", {
    "fullName": "Meenakshi Sundaram",
    "employeeId": "EMP-W08",
    "wardNumber": "W08",
    "email": "meenakshi@senthamizh.gov.in",
    "username": "ward_meena",
    "password": "securePass123",
    "confirmPassword": "securePass123"
})
log_test("5. Valid Ward Officer signup redirects to login page",
         "/login/ward" in url and ("registered=true" in url or "created successfully" in body.lower()))

# Test 6: Registered Ward Officer can log in with newly created credentials
ward_client = Client()
code, body, url = ward_client.post("/login/ward", {
    "userId": "ward_meena",
    "password": "securePass123"
})
log_test("6. Newly registered Ward Officer logs in and opens dashboard",
         "/ward/dashboard" in url and "Meenakshi Sundaram" in body)

# Test 7: Engineering Section Signup blocks invalid authorization code
code, body, url = anon.post("/signup/engineering", {
    "fullName": "Fake Engineer",
    "employeeId": "EMP-ENG09",
    "departmentName": "Roads",
    "email": "fake.eng@muni.gov",
    "registrationCode": "WRONG_SECRET_CODE",
    "username": "eng_fake",
    "password": "password123",
    "confirmPassword": "password123"
})
log_test("7. Engineering signup rejects invalid authorization code",
         "Invalid Engineering Authorization Code" in body or "restricted" in body.lower())

# Test 8: Engineering Section Signup with valid authorization code
code, body, url = anon.post("/signup/engineering", {
    "fullName": "Er. K. Murugesan",
    "employeeId": "EMP-ENG03",
    "departmentName": "Bridges & Highways",
    "email": "murugesan.eng@senthamizh.gov.in",
    "registrationCode": "MUNI-ENG-2026",
    "username": "eng_murugan",
    "password": "engineerPass2026",
    "confirmPassword": "engineerPass2026"
})
log_test("8. Engineering signup succeeds with valid MUNI-ENG-2026 code",
         "/login/engineering" in url and ("registered=true" in url or "created successfully" in body.lower()))

# Test 9: Newly registered Engineer logs in
eng_client = Client()
code, body, url = eng_client.post("/login/engineering", {
    "userId": "eng_murugan",
    "password": "engineerPass2026"
})
log_test("9. Newly registered Engineer logs in and accesses Engineering Section",
         "/engineering/dashboard" in url and "Murugesan" in body)

# Test 10: Maintenance Team Squad Signup
code, body, url = anon.post("/signup/maintenance", {
    "teamName": "Omega Squad",
    "teamId": "T9",
    "email": "omega.squad@senthamizh.gov.in",
    "equipment": "Pothole Filler, Crack Sealer",
    "username": "squad_omega",
    "password": "omegaPassword123",
    "confirmPassword": "omegaPassword123"
})
log_test("10. Maintenance Team signup succeeds and redirects to login",
         "/login/maintenance" in url and ("registered=true" in url or "created successfully" in body.lower()))

# Test 11: Cross-Role Security - Ward Officer blocked from logging into Engineering
code, body, url = anon.post("/login/engineering", {
    "userId": "ward_meena",
    "password": "securePass123"
})
log_test("11. Cross-role login rejected (Ward Officer attempting Engineering login)",
         "/engineering/dashboard" not in url and ("Invalid" in body or "mismatch" in body.lower()))

# Test 12: Ward Officer submits a new road damage report
code, body, url = ward_client.post("/ward/submit", {
    "locationId": "L02",
    "roadName": "Gandhi Road Cross",
    "landmark": "Near Vegetable Market",
    "damageType": "Pothole",
    "severityRank": "3",
    "accidentRiskRank": "3",
    "roadImportanceRank": "3",
    "trafficRank": "3",
    "requiredEquipment": "Pothole Filler",
    "fieldObservations": "Deep cavity in center lane during rush hour."
})
log_test("12. Ward Officer submits new report (enters SUBMITTED status)",
         code == 200 and ("submitted successfully" in body.lower() or "R-" in body or "R0" in body))

# Test 13: Engineering views unverified reports in Inbox
code, body, url = eng_client.get("/engineering/inbox")
log_test("13. Engineering Section sees submitted reports in Review Inbox",
         code == 200 and "Gandhi Road" in body)

match = re.search(r'R-[A-Z0-9\-]+|R\d{3}', body)
rep_id = match.group(0) if match else "R001"

# Test 14: Engineering verifies the report into priority queue
code, body, url = eng_client.post("/engineering/review", {
    "action": "verify",
    "reportId": rep_id,
    "severityRank": "3",
    "accidentRiskRank": "3",
    "roadImportanceRank": "3",
    "trafficRank": "3"
})
log_test("14. Engineering verifies report -> enters PriorityQueue",
         code == 200 and ("verified" in body.lower() or "priority queue" in body.lower()))

# Test 15: Engineering allocates team
code, body, url = eng_client.post("/engineering/assign", {
    "action": "assign",
    "reportId": rep_id,
    "teamId": "T3"
})
log_test("15. Engineering assigns repair via Dijkstra & compatibility check",
         code == 200 and ("assigned" in body.lower() or "allocated" in body.lower() or "success" in body.lower()))

# Test 16: Maintenance Team T3 logs in and checks assigned job
t3_client = Client()
code, body, url = t3_client.post("/login/maintenance", {
    "userId": "T3",
    "password": "team123"
})
log_test("16. Maintenance Squad T3 signs in and accesses dashboard",
         "/maintenance/dashboard" in url and "Gamma Squad" in body)

# Test 17: T3 starts repair
code, body, url = t3_client.post("/maintenance/start", {
    "reportId": rep_id
})
log_test("17. Maintenance Squad starts repair (marked IN PROGRESS)",
         code == 200 and ("IN PROGRESS" in body or "started" in body.lower()))

# Test 18: T3 completes repair
code, body, url = t3_client.post("/maintenance/complete", {
    "reportId": rep_id,
    "note": "Cavity backfilled with hot-mix polymer asphalt and rolled flush."
})
log_test("18. Maintenance Squad completes repair (COMPLETED & squad released)",
         code == 200 and ("COMPLETED" in body or "released" in body.lower()))

# Test 19: Session Logout invalidates session
code, body, url = ward_client.get("/logout")
code2, body2, url2 = ward_client.get("/ward/dashboard")
log_test("19. Logout invalidates session and prevents protected dashboard access",
         "/login" in url2 or "Sign In" in body2)

print("=" * 65)
print(f"  Summary: {passed} PASSED, {failed} FAILED")
print("=" * 65)

if failed > 0:
    sys.exit(1)
