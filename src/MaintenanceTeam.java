// MaintenanceTeam.java
// Represents a road-maintenance team with its equipment, location and availability.
// Composition: a MaintenanceTeam CONTAINS a HashSet of Equipment objects.

import java.util.HashSet;

public class MaintenanceTeam {

    private String teamId;          // Unique ID, e.g. "T1"
    private String teamName;        // Human-readable name, e.g. "Alpha Squad"
    private String currentLocationId; // The location where this team currently is
    private boolean isAvailable;    // true = ready to take a new job

    // A HashSet is used for equipment because:
    //  • We only care whether a piece of equipment is present (membership check).
    //  • HashSet.contains() runs in O(1) average time.
    //  • Duplicates are automatically ignored.
    private HashSet<Equipment> equipmentSet;

    // The report ID of the job this team is currently working on (null if free)
    private String currentAssignmentId;

    // ---- Constructor ----
    public MaintenanceTeam(String teamId, String teamName, String currentLocationId) {
        this.teamId              = teamId;
        this.teamName            = teamName;
        this.currentLocationId   = currentLocationId;
        this.isAvailable         = true;       // Teams start available
        this.equipmentSet        = new HashSet<>();
        this.currentAssignmentId = null;
    }

    // ---- Equipment management ----

    // Adds a piece of equipment to this team's set
    public void addEquipment(Equipment equipment) {
        equipmentSet.add(equipment);
    }

    // Returns true if this team owns ALL the equipment in the requiredSet.
    // We use HashSet.containsAll() for a clean O(n) check.
    public boolean hasRequiredEquipment(HashSet<Equipment> requiredSet) {
        return equipmentSet.containsAll(requiredSet);
    }

    // Convenience: check by equipment name alone
    public boolean hasEquipmentByName(String equipmentName) {
        Equipment needed = new Equipment(equipmentName);
        return equipmentSet.contains(needed);
    }

    // ---- Assignment management ----

    // Called when this team is assigned a repair job
    public void assignJob(String reportId) {
        this.currentAssignmentId = reportId;
        this.isAvailable         = false;
    }

    // Called when the repair is finished — releases the team
    public void completeRepair(String newLocationId) {
        this.currentLocationId   = newLocationId; // Team moves to the repair site
        this.currentAssignmentId = null;
        this.isAvailable         = true;
    }

    // ---- Getters ----
    public String           getTeamId()              { return teamId; }
    public String           getTeamName()            { return teamName; }
    public String           getCurrentLocationId()   { return currentLocationId; }
    public boolean          isAvailable()            { return isAvailable; }
    public HashSet<Equipment> getEquipmentSet()      { return equipmentSet; }
    public String           getCurrentAssignmentId() { return currentAssignmentId; }

    // Returns a comma-separated list of equipment names for display in HTML
    public String getEquipmentNames() {
        if (equipmentSet.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (Equipment eq : equipmentSet) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(eq.getName());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Team[" + teamId + " – " + teamName + ", loc=" + currentLocationId
                + ", available=" + isAvailable + "]";
    }
}
