// RoadDamage.java
// Represents a single road-damage report in the new three-role workflow.
//
// STATUS LIFECYCLE:
//
//   Ward Officer submits
//       → SUBMITTED
//           → Engineering reviews → VERIFIED   (enters priority queue)
//           → Engineering reviews → RETURNED   (ward officer must correct)
//           → Engineering reviews → REJECTED   (invalid / duplicate)
//   RETURNED → Ward Officer corrects → SUBMITTED again (for re-review)
//   VERIFIED → Engineering assigns team → ASSIGNED
//   ASSIGNED → Maintenance team starts → IN_PROGRESS
//   IN_PROGRESS → Maintenance team completes → COMPLETED

public class RoadDamage {

    // ---- Status constants ----
    public static final String STATUS_SUBMITTED   = "SUBMITTED";    // Waiting for Engineering review
    public static final String STATUS_RETURNED    = "RETURNED";     // Returned to ward officer for correction
    public static final String STATUS_VERIFIED    = "VERIFIED";     // In priority queue (Engineering approved)
    public static final String STATUS_ASSIGNED    = "ASSIGNED";     // Team assigned, travelling to site
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";  // Team has started the repair
    public static final String STATUS_COMPLETED   = "COMPLETED";    // Repair finished
    public static final String STATUS_REJECTED    = "REJECTED";     // Engineering rejected the report

    // Alias kept for backward compatibility with TestRunner
    public static final String STATUS_PENDING     = "VERIFIED";

    // ---- Core fields (set at submission time) ----
    private String reportId;            // Unique identifier, e.g. "R001"
    private String locationId;          // Location ID in the road network, e.g. "L06"
    private String damageType;          // e.g. "Pothole", "Resurfacing", "Crack Seal"

    // Priority ranks — each must be 1 (low) to 3 (high)
    private int severityRank;           // Severity of physical damage
    private int accidentRiskRank;       // Risk of an accident if unrepaired
    private int roadImportanceRank;     // How important this road is to the municipality
    private int trafficRank;            // Traffic volume on this road

    private int reportOrder;            // Auto-assigned submission sequence number
    private String requiredEquipment;   // e.g. "Pothole Filler"
    private String status;              // Current status (one of the constants above)
    private String assignedTeamId;      // Team assigned to this repair (null if none)

    // ---- Ward Officer fields ----
    private String submittedBy;         // User ID of the ward officer who submitted
    private String wardNumber;          // e.g. "W03"
    private String roadName;            // Free-text road description, e.g. "Anna Nagar Main Road"
    private String landmark;            // e.g. "Near HDFC Bank"
    private String fieldObservations;   // Additional notes from the ward officer on site

    // ---- Engineering fields ----
    private String returnReason;        // Reason given when Engineering returns the report
    private String rejectionReason;     // Reason given when Engineering rejects the report

    // ---- Completion/tracking fields ----
    private String completionNote;      // Notes entered by the maintenance team on completion
    private String submissionDate;      // When the report was first submitted
    private String startDate;           // When the team started the repair (IN_PROGRESS)
    private String completionDate;      // When the repair was finished (COMPLETED)

    // ---- Full constructor (used by ward officer submissions) ----
    public RoadDamage(String reportId, String locationId, String damageType,
                      int severityRank, int accidentRiskRank, int roadImportanceRank,
                      int trafficRank, int reportOrder, String requiredEquipment,
                      String submittedBy, String wardNumber, String roadName,
                      String landmark, String fieldObservations) {
        this.reportId           = reportId;
        this.locationId         = locationId;
        this.damageType         = damageType;
        this.severityRank       = severityRank;
        this.accidentRiskRank   = accidentRiskRank;
        this.roadImportanceRank = roadImportanceRank;
        this.trafficRank        = trafficRank;
        this.reportOrder        = reportOrder;
        this.requiredEquipment  = requiredEquipment;
        this.submittedBy        = submittedBy != null ? submittedBy : "system";
        this.wardNumber         = wardNumber  != null ? wardNumber  : "";
        this.roadName           = roadName    != null ? roadName    : "";
        this.landmark           = landmark    != null ? landmark    : "";
        this.fieldObservations  = fieldObservations != null ? fieldObservations : "";
        this.status             = STATUS_SUBMITTED;
        this.assignedTeamId     = null;
        this.submissionDate     = nowString();
    }

    // ---- Legacy constructor for sample data (creates a SUBMITTED report with defaults) ----
    public RoadDamage(String reportId, String locationId, String damageType,
                      int severityRank, int accidentRiskRank, int roadImportanceRank,
                      int trafficRank, int reportOrder, String requiredEquipment) {
        this(reportId, locationId, damageType, severityRank, accidentRiskRank,
             roadImportanceRank, trafficRank, reportOrder, requiredEquipment,
             "system", "SAMPLE", locationId + " Road", "—", "Pre-loaded sample data");
    }

    // Returns a formatted date-time string for the current moment
    private static String nowString() {
        return java.time.LocalDateTime.now()
               .format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"));
    }

    // ---- Core getters ----
    public String getReportId()           { return reportId; }
    public String getLocationId()         { return locationId; }
    public String getDamageType()         { return damageType; }
    public int    getSeverityRank()       { return severityRank; }
    public int    getAccidentRiskRank()   { return accidentRiskRank; }
    public int    getRoadImportanceRank() { return roadImportanceRank; }
    public int    getTrafficRank()        { return trafficRank; }
    public int    getReportOrder()        { return reportOrder; }
    public String getRequiredEquipment()  { return requiredEquipment; }
    public String getStatus()             { return status; }
    public String getAssignedTeamId()     { return assignedTeamId; }

    // ---- Ward officer getters ----
    public String getSubmittedBy()       { return submittedBy != null ? submittedBy : ""; }
    public String getWardNumber()        { return wardNumber  != null ? wardNumber  : ""; }
    public String getRoadName()          { return roadName    != null ? roadName    : ""; }
    public String getLandmark()          { return landmark    != null ? landmark    : ""; }
    public String getFieldObservations() { return fieldObservations != null ? fieldObservations : ""; }

    // ---- Engineering / tracking getters ----
    public String getReturnReason()    { return returnReason    != null ? returnReason    : ""; }
    public String getRejectionReason() { return rejectionReason != null ? rejectionReason : ""; }
    public String getCompletionNote()  { return completionNote  != null ? completionNote  : ""; }
    public String getSubmissionDate()  { return submissionDate  != null ? submissionDate  : ""; }
    public String getStartDate()       { return startDate       != null ? startDate       : ""; }
    public String getCompletionDate()  { return completionDate  != null ? completionDate  : ""; }

    // ================================================================
    // STATUS TRANSITION METHODS (called by MunicipalService)
    // ================================================================

    // Engineering verifies the report and optionally adjusts priority ranks.
    // The report must be SUBMITTED or RETURNED before it can be verified.
    public boolean verify(int sev, int acc, int imp, int trf) {
        if (!STATUS_SUBMITTED.equals(status) && !STATUS_RETURNED.equals(status)) return false;
        this.severityRank       = sev;
        this.accidentRiskRank   = acc;
        this.roadImportanceRank = imp;
        this.trafficRank        = trf;
        this.returnReason       = null; // Clear any previous return reason
        this.status             = STATUS_VERIFIED;
        return true;
    }

    // Direct-verify without status check (used for sample data setup only)
    public void directVerify() {
        this.status = STATUS_VERIFIED;
    }

    // Engineering returns the report to the ward officer with a reason.
    public boolean returnForCorrection(String reason) {
        if (!STATUS_SUBMITTED.equals(status)) return false;
        this.returnReason = reason;
        this.status       = STATUS_RETURNED;
        return true;
    }

    // Engineering rejects the report with an explanation.
    public boolean reject(String reason) {
        if (!STATUS_SUBMITTED.equals(status) && !STATUS_RETURNED.equals(status)) return false;
        this.rejectionReason = reason;
        this.status          = STATUS_REJECTED;
        return true;
    }

    // Ward officer corrects and resubmits a RETURNED report.
    public boolean resubmit(int sev, int acc, int imp, int trf,
                            String newObservations, String newLandmark) {
        if (!STATUS_RETURNED.equals(status)) return false;
        this.severityRank       = sev;
        this.accidentRiskRank   = acc;
        this.roadImportanceRank = imp;
        this.trafficRank        = trf;
        this.fieldObservations  = newObservations;
        this.landmark           = newLandmark;
        this.returnReason       = null;
        this.status             = STATUS_SUBMITTED; // Goes back for re-review
        this.submissionDate     = nowString();
        return true;
    }

    // Engineering assigns a team to this VERIFIED report.
    public void assignTeam(String teamId) {
        this.assignedTeamId = teamId;
        this.status         = STATUS_ASSIGNED;
    }

    // Maintenance team starts the repair (ASSIGNED → IN_PROGRESS).
    public boolean startWork() {
        if (!STATUS_ASSIGNED.equals(status)) return false;
        this.status    = STATUS_IN_PROGRESS;
        this.startDate = nowString();
        return true;
    }

    // Maintenance team or Engineering completes the repair.
    // Accepts IN_PROGRESS or ASSIGNED (Engineering admin can complete from ASSIGNED directly).
    public boolean markCompleted() {
        if (!STATUS_IN_PROGRESS.equals(status) && !STATUS_ASSIGNED.equals(status)) return false;
        this.status         = STATUS_COMPLETED;
        this.completionDate = nowString();
        this.assignedTeamId = null;
        return true;
    }

    // Completes with a team-provided note (maintenance team flow).
    public boolean completeWithNote(String note) {
        if (!markCompleted()) return false;
        this.completionNote = note;
        return true;
    }

    // ---- Used by MunicipalService.updateReport() on VERIFIED reports ----
    public boolean updateRanks(int sev, int acc, int imp, int trf) {
        // Only VERIFIED reports can have their ranks updated (before assignment)
        if (!STATUS_VERIFIED.equals(status)) return false;
        this.severityRank       = sev;
        this.accidentRiskRank   = acc;
        this.roadImportanceRank = imp;
        this.trafficRank        = trf;
        return true;
    }

    // ---- Utility: validates that a rank value is between 1 and 3 inclusive ----
    public static boolean isValidRank(int rank) {
        return rank >= 1 && rank <= 3;
    }

    @Override
    public String toString() {
        return "RoadDamage[" + reportId + ", " + locationId + ", " + damageType
                + ", sev=" + severityRank + ", status=" + status + "]";
    }
}
