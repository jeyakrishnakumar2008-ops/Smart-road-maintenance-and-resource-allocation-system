// AssignmentResult.java
// A simple container that holds the result of one team-assignment attempt.
// It is returned by MunicipalService.assignNextPendingRepair()
// and used by WebServer to build the HTML response page.

import java.util.List;
import java.util.ArrayList;

public class AssignmentResult {

    // ---- Possible outcomes ----
    public static final String SUCCESS           = "SUCCESS";
    public static final String NO_PENDING        = "NO_PENDING";
    public static final String NO_ELIGIBLE_TEAM  = "NO_ELIGIBLE_TEAM";
    public static final String UNREACHABLE        = "UNREACHABLE";

    private String        outcome;          // One of the constants above
    private String        reportId;         // The report that was processed
    private String        teamId;           // The team that was assigned (null on failure)
    private DijkstraResult routeResult;     // Route details (null on failure)
    private String        failureReason;    // Human-readable explanation when failed
    private List<String>  skippedTeams;     // Teams that were considered but rejected

    // Constructor
    public AssignmentResult(String outcome, String reportId, String teamId,
                            DijkstraResult routeResult, String failureReason,
                            List<String> skippedTeams) {
        this.outcome       = outcome;
        this.reportId      = reportId;
        this.teamId        = teamId;
        this.routeResult   = routeResult;
        this.failureReason = failureReason;
        this.skippedTeams  = skippedTeams != null ? skippedTeams : new ArrayList<>();
    }

    // ---- Convenience factory methods ----

    public static AssignmentResult success(String reportId, String teamId, DijkstraResult route) {
        return new AssignmentResult(SUCCESS, reportId, teamId, route, null, null);
    }

    public static AssignmentResult noPending() {
        return new AssignmentResult(NO_PENDING, null, null, null,
                "No pending reports are waiting for assignment.", null);
    }

    public static AssignmentResult noEligibleTeam(String reportId, String reason, List<String> skipped) {
        return new AssignmentResult(NO_ELIGIBLE_TEAM, reportId, null, null, reason, skipped);
    }

    public static AssignmentResult unreachable(String reportId, String reason, List<String> skipped) {
        return new AssignmentResult(UNREACHABLE, reportId, null, null, reason, skipped);
    }

    // ---- Getters ----
    public String         getOutcome()       { return outcome; }
    public String         getReportId()      { return reportId; }
    public String         getTeamId()        { return teamId; }
    public DijkstraResult getRouteResult()   { return routeResult; }
    public String         getFailureReason() { return failureReason; }
    public List<String>   getSkippedTeams()  { return skippedTeams; }

    public boolean isSuccess() { return SUCCESS.equals(outcome); }
}
