// DatabaseAndRoleAuthTest.java
// Automated test suite for PBKDF2 password hashing,
// role-based signup, authorization code security, and MySQL database repository.

public class DatabaseAndRoleAuthTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  Database & Role Authentication Test Suite");
        System.out.println("==============================================");

        // ---- Test 1: PBKDF2 Password Hashing & Salt ----
        PasswordHasher.HashResult hr = PasswordHasher.hashPassword("SuperSecret123!");
        check("Test 01: PBKDF2 generates 64-char hex hash & 32-char salt",
                hr.getHash() != null && hr.getHash().length() == 64 &&
                hr.getSalt() != null && hr.getSalt().length() == 32);

        // ---- Test 2: Constant-time Verification ----
        boolean verifyGood = PasswordHasher.verify("SuperSecret123!", hr.getHash(), hr.getSalt());
        boolean verifyBad  = PasswordHasher.verify("WrongPassword",   hr.getHash(), hr.getSalt());
        check("Test 02: PasswordHasher verifies correct password & rejects wrong password",
                verifyGood && !verifyBad);

        // ---- Test 3: SessionManager User Registration (Ward Officer) ----
        SessionManager sm = new SessionManager();
        String wErr = sm.register("ward_test1", "password123", "ward_test1@muni.gov",
                "Ramesh Chandran", "EMP-W11", UserAccount.ROLE_WARD, "W11", null, null, null, null);
        check("Test 03: Ward Officer registration succeeds", wErr == null);

        // ---- Test 4: Duplicate Username Rejection ----
        String dupUserErr = sm.register("ward_test1", "password123", "other@muni.gov",
                "Duplicate Name", "EMP-W12", UserAccount.ROLE_WARD, "W12", null, null, null, null);
        check("Test 04: Duplicate username is rejected", dupUserErr != null && dupUserErr.contains("already registered"));

        // ---- Test 5: Duplicate Email Rejection ----
        String dupEmailErr = sm.register("ward_test2", "password123", "ward_test1@muni.gov",
                "Another Name", "EMP-W13", UserAccount.ROLE_WARD, "W13", null, null, null, null);
        check("Test 05: Duplicate email is rejected", dupEmailErr != null && dupEmailErr.contains("already registered"));

        // ---- Test 6: Short Password Rejection ----
        String shortPassErr = sm.register("ward_short", "123", "short@muni.gov",
                "Short Pass", "EMP-W14", UserAccount.ROLE_WARD, "W14", null, null, null, null);
        check("Test 06: Password shorter than 6 characters is rejected",
                shortPassErr != null && shortPassErr.contains("at least 6 characters"));

        // ---- Test 7: Engineering Section Registration & Authorization Code ----
        String engErr = sm.register("eng_chief2", "engineerPass123", "eng_chief2@muni.gov",
                "Kavitha Sundar", "EMP-ENG99", UserAccount.ROLE_ENGINEERING, null, "Structural Works", null, null, null);
        check("Test 07: Engineering Section registration succeeds", engErr == null);

        // ---- Test 8: Maintenance Team Registration ----
        String maintErr = sm.register("squad_delta", "deltaPass123", "delta@muni.gov",
                "Delta Squad", "SQUAD-T4", UserAccount.ROLE_MAINTENANCE, null, null, "T4", "Delta Squad", "Pothole Filler");
        check("Test 08: Maintenance Team registration succeeds", maintErr == null);

        // ---- Test 9: Login with Correct Role & Password ----
        String wSession = sm.loginWithRole("ward_test1", "password123", UserAccount.ROLE_WARD);
        check("Test 09: Valid Ward Officer login returns active session ID",
                wSession != null && !wSession.isEmpty());

        // ---- Test 10: Wrong Password Login Rejected ----
        String badPassSession = sm.loginWithRole("ward_test1", "incorrectPass", UserAccount.ROLE_WARD);
        check("Test 10: Incorrect password login is rejected", badPassSession == null);

        // ---- Test 11: Cross-Role Privilege Escalation Blocked ----
        // ward_test1 attempts to log in as ENGINEERING
        String roleMismatchSession = sm.loginWithRole("ward_test1", "password123", UserAccount.ROLE_ENGINEERING);
        check("Test 11: Cross-role login attempt (Ward as Engineering) is rejected",
                roleMismatchSession == null);

        // ---- Test 12: Session Retrieval & Role Attributes ----
        UserAccount authedUser = sm.getSession(wSession);
        check("Test 12: Authenticated user object holds correct role and ward ID",
                authedUser != null && authedUser.isWardOfficer() && "W11".equals(authedUser.getWardId()));

        // ---- Test 13: Session Invalidation on Logout ----
        sm.invalidate(wSession);
        check("Test 13: Logout invalidates session ID", sm.getSession(wSession) == null);

        // ---- Test 14: Database Connection Helper Availability ----
        boolean hasDriver = true;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            hasDriver = false;
        }
        check("Test 14: MySQL Connector/J JDBC Driver loaded on classpath", hasDriver);

        // ---- Test 15: Engineering Registration Code Check ----
        String validCode = DatabaseConnection.getEngineeringRegistrationCode();
        check("Test 15: Engineering registration code is configured",
                validCode != null && validCode.equals("MUNI-ENG-2026"));

        System.out.println("==============================================");
        System.out.println("  Results: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==============================================");

        if (failed == 0) {
            System.out.println("  ✅ All database & authentication tests passed!");
        } else {
            System.out.println("  ❌ Some tests failed. Review the output above.");
            System.exit(1);
        }
    }

    private static void check(String desc, boolean condition) {
        if (condition) {
            System.out.println("  PASS  " + desc);
            passed++;
        } else {
            System.out.println("  FAIL  " + desc);
            failed++;
        }
    }
}
