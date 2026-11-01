// UserAccount.java
// Represents a successfully authenticated user stored inside a server-side session.
// After login, one UserAccount object lives in SessionManager for the life of the session.
// The role field controls which pages and actions the user is allowed to access.

public class UserAccount {

    // ---- Role constants ----
    public static final String ROLE_WARD        = "WARD_OFFICER";
    public static final String ROLE_ENGINEERING = "ENGINEERING";
    public static final String ROLE_MAINTENANCE = "MAINTENANCE";

    // ---- Fields ----
    private final String userId;       // Login username, e.g. "ward1", "eng1", "T1"
    private final String displayName;  // Human-readable name shown in the navigation bar
    private final String role;         // One of the ROLE_* constants above
    private final String teamId;       // Only for MAINTENANCE — equals the team ID, e.g. "T1"
    private final String wardId;       // Only for WARD_OFFICER — e.g. "W03"

    // Constructor — called by SessionManager after a successful login
    public UserAccount(String userId, String displayName, String role,
                       String teamId, String wardId) {
        this.userId      = userId;
        this.displayName = displayName;
        this.role        = role;
        this.teamId      = teamId == null ? "" : teamId;
        this.wardId      = wardId == null ? "" : wardId;
    }

    // ---- Getters ----
    public String getUserId()      { return userId; }
    public String getDisplayName() { return displayName; }
    public String getRole()        { return role; }
    public String getTeamId()      { return teamId; }
    public String getWardId()      { return wardId; }

    // ---- Convenience role checks ----
    public boolean isWardOfficer() { return ROLE_WARD.equals(role); }
    public boolean isEngineering() { return ROLE_ENGINEERING.equals(role); }
    public boolean isMaintenance() { return ROLE_MAINTENANCE.equals(role); }
}
