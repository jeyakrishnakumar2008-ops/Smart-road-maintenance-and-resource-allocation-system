// RepairJob.java
// Abstract base class for all types of repair jobs.
// ABSTRACTION: We define a template — every repair type must implement requiredEquipment().
// INHERITANCE: PotholeRepair and ResurfacingRepair extend this class.

import java.util.HashSet;

public abstract class RepairJob {

    private String jobId;       // Unique job identifier, e.g. "JOB-R001"
    private String reportId;    // The damage report this job is for
    private String locationId;  // Where the repair will happen

    // Constructor — called from subclass constructors via super()
    public RepairJob(String jobId, String reportId, String locationId) {
        this.jobId      = jobId;
        this.reportId   = reportId;
        this.locationId = locationId;
    }

    // ABSTRACTION / POLYMORPHISM:
    // Each subclass MUST override this method and return the set of
    // equipment it needs. The caller does not need to know which
    // subclass it is dealing with — it just calls requiredEquipment().
    public abstract HashSet<Equipment> requiredEquipment();

    // Returns a human-readable description of the repair type
    // Subclasses may override this to provide a more specific description.
    public String getRepairDescription() {
        return "General road repair at " + locationId;
    }

    // ---- Getters ----
    public String getJobId()      { return jobId; }
    public String getReportId()   { return reportId; }
    public String getLocationId() { return locationId; }

    @Override
    public String toString() {
        return "RepairJob[" + jobId + ", report=" + reportId + ", loc=" + locationId + "]";
    }
}
