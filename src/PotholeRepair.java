// PotholeRepair.java
// INHERITANCE: Extends RepairJob.
// POLYMORPHISM: Overrides requiredEquipment() to return pothole-specific tools.
// A pothole repair needs a Pothole Filler and Safety Cones.

import java.util.HashSet;

public class PotholeRepair extends RepairJob {

    // Constructor — passes job information to the parent class
    public PotholeRepair(String jobId, String reportId, String locationId) {
        super(jobId, reportId, locationId);
    }

    // POLYMORPHISM: This override tells callers exactly what a pothole repair needs.
    // The caller only knows it has a RepairJob reference, but calling
    // requiredEquipment() will automatically run THIS version.
    @Override
    public HashSet<Equipment> requiredEquipment() {
        HashSet<Equipment> needed = new HashSet<>();
        needed.add(new Equipment("Pothole Filler"));
        needed.add(new Equipment("Safety Cones"));
        return needed;
    }

    // Provides a specific description for this repair type
    @Override
    public String getRepairDescription() {
        return "Pothole repair at " + getLocationId()
                + " — fill and compact the damaged section";
    }
}
