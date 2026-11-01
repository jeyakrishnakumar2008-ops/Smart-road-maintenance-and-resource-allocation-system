// SessionManager.java
// Manages user sessions and authentication using MySQL + PBKDF2WithHmacSHA256 salted hashing.
// Never stores plaintext passwords.
// Supports role-specific signups, login validations, and session tracking.

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    // Active server-side sessions: sessionId (UUID string) → UserAccount
    private final HashMap<String, UserAccount> sessions;

    // In-memory cache of registered accounts: username → StoredAccount
    private final HashMap<String, StoredAccount> registeredAccounts;

    // Cookie name used to track HTTP sessions
    public static final String COOKIE_NAME = "SESSIONID";

    public static class StoredAccount {
        final String username;
        final String passwordHash;
        final String salt;
        final String role;
        final String displayName;
        final String teamId;
        final String wardId;
        final String email;
        final String employeeId;

        public StoredAccount(String username, String passwordHash, String salt, String role,
                             String displayName, String teamId, String wardId,
                             String email, String employeeId) {
            this.username     = username;
            this.passwordHash = passwordHash;
            this.salt         = salt;
            this.role         = role;
            this.displayName  = displayName;
            this.teamId       = teamId != null ? teamId : "";
            this.wardId       = wardId != null ? wardId : "";
            this.email        = email != null ? email : "";
            this.employeeId   = employeeId != null ? employeeId : "";
        }
    }

    public SessionManager() {
        sessions           = new HashMap<>();
        registeredAccounts = new HashMap<>();
        seedDemoAccountsWithHashes();
    }

    // Seeds demo accounts using salted PBKDF2 hashes (zero plaintext)
    private void seedDemoAccountsWithHashes() {
        registerInMemory("ward1", "ward123", "suresh.ward3@senthamizh.gov.in",
                "Suresh Kumar", "EMP-W03", UserAccount.ROLE_WARD, "W03", "", "", "");
        registerInMemory("ward2", "ward123", "priya.ward7@senthamizh.gov.in",
                "Priya Devi", "EMP-W07", UserAccount.ROLE_WARD, "W07", "", "", "");
        registerInMemory("eng1", "eng123", "rajan.eng@senthamizh.gov.in",
                "Chief Engineer Rajan", "EMP-ENG01", UserAccount.ROLE_ENGINEERING, "", "Roads & Infrastructure", "", "");
        registerInMemory("T1", "team123", "alpha.squad@senthamizh.gov.in",
                "Alpha Squad (T1)", "SQUAD-T1", UserAccount.ROLE_MAINTENANCE, "", "", "T1", "Alpha Squad");
        registerInMemory("T2", "team123", "beta.squad@senthamizh.gov.in",
                "Beta Squad (T2)", "SQUAD-T2", UserAccount.ROLE_MAINTENANCE, "", "", "T2", "Beta Squad");
        registerInMemory("T3", "team123", "gamma.squad@senthamizh.gov.in",
                "Gamma Squad (T3)", "SQUAD-T3", UserAccount.ROLE_MAINTENANCE, "", "", "T3", "Gamma Squad");
    }

    private void registerInMemory(String username, String plainPassword, String email,
                                  String displayName, String empId, String role,
                                  String wardId, String deptName, String teamId, String teamName) {
        PasswordHasher.HashResult hr = PasswordHasher.hashPassword(plainPassword);
        StoredAccount sa = new StoredAccount(username, hr.getHash(), hr.getSalt(), role,
                displayName, teamId, wardId, email, empId);
        registeredAccounts.put(username.toLowerCase(), sa);
    }

    // Registers a new user account across MySQL and in-memory cache
    public String register(String username, String password, String email,
                           String fullName, String employeeId, String role,
                           String wardNumber, String deptName,
                           String teamId, String teamName, String equipmentStr) {

        if (username == null || username.trim().isEmpty()) return "Username cannot be empty.";
        if (password == null || password.length() < 6) return "Password must be at least 6 characters long.";
        if (email == null || !email.contains("@")) return "A valid email address is required.";
        if (fullName == null || fullName.trim().isEmpty()) return "Full name cannot be empty.";

        String uLower = username.trim().toLowerCase();

        // Check uniqueness in local registry
        if (registeredAccounts.containsKey(uLower)) {
            return "Username '" + username + "' is already registered. Please choose another username.";
        }
        for (StoredAccount sa : registeredAccounts.values()) {
            if (sa.email.equalsIgnoreCase(email.trim())) {
                return "Email address '" + email + "' is already registered.";
            }
            if (employeeId != null && !employeeId.trim().isEmpty() && sa.employeeId.equalsIgnoreCase(employeeId.trim())) {
                return "Employee ID '" + employeeId + "' is already registered.";
            }
        }

        // Save to MySQL via DatabaseRepository (ACID transaction)
        String dbError = DatabaseRepository.registerUser(username.trim(), password, email.trim(),
                fullName.trim(), employeeId, role, wardNumber, deptName, teamId, teamName, equipmentStr);
        if (dbError != null) {
            return dbError; // Database constraint violation message
        }

        // Cache salted hash in memory
        registerInMemory(username.trim(), password, email.trim(), fullName.trim(),
                employeeId, role, wardNumber, deptName, teamId, teamName);

        return null; // Success!
    }

    // Role-specific login verification with PBKDF2 hash checking
    public String loginWithRole(String usernameOrEmail, String password, String expectedRole) {
        if (usernameOrEmail == null || password == null || expectedRole == null) return null;

        String input = usernameOrEmail.trim();

        // 1. Try MySQL Database authentication first
        UserAccount dbUser = DatabaseRepository.authenticateUser(input, password, expectedRole);
        if (dbUser != null) {
            String sessionId = UUID.randomUUID().toString();
            sessions.put(sessionId, dbUser);
            return sessionId;
        }

        // 2. Fallback to in-memory salted PBKDF2 hash verification
        StoredAccount account = null;
        for (StoredAccount sa : registeredAccounts.values()) {
            if (sa.username.equalsIgnoreCase(input) || sa.email.equalsIgnoreCase(input)) {
                account = sa;
                break;
            }
        }

        if (account == null) return null; // User not found
        if (!account.role.equals(expectedRole)) return null; // Role mismatch (e.g. Ward tried Engineering login)

        // Verify password hash against stored salt
        boolean matches = PasswordHasher.verify(password, account.passwordHash, account.salt);
        if (!matches) return null; // Incorrect password

        String sessionId = UUID.randomUUID().toString();
        UserAccount user = new UserAccount(account.username, account.displayName, account.role,
                account.teamId, account.wardId);
        sessions.put(sessionId, user);
        return sessionId;
    }

    // Backward-compatible login overload
    public String login(String userId, String password) {
        if (userId == null || password == null) return null;
        for (StoredAccount sa : registeredAccounts.values()) {
            if (sa.username.equalsIgnoreCase(userId.trim())) {
                return loginWithRole(userId, password, sa.role);
            }
        }
        return null;
    }

    public UserAccount getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        return sessions.get(sessionId);
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
    }

    public String extractSessionId(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(COOKIE_NAME + "=")) {
                return trimmed.substring(COOKIE_NAME.length() + 1);
            }
        }
        return null;
    }
}
