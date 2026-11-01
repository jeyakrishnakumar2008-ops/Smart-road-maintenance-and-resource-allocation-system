// ResurfacingRepair.java
// INHERITANCE: Extends RepairJob.
// POLYMORPHISM: Overrides requiredEquipment() to return resurfacing-specific tools.
// A resurfacing repair needs a Road Roller and Asphalt Spreader.

import java.util.HashSet;

public class ResurfacingRepair extends RepairJob {

    // Constructor — passes job information to the parent class
    public ResurfacingRepair(String jobId, String reportId, String locationId) {
        super(jobId, reportId, locationId);
    }

    // POLYMORPHISM: Returns the equipment needed specifically for resurfacing work.
    @Override
    public HashSet<Equipment> requiredEquipment() {
        HashSet<Equipment> needed = new HashSet<>();
        needed.add(new Equipment("Road Roller"));
        needed.add(new Equipment("Asphalt Spreader"));
        return needed;
    }

    // Provides a specific description for this repair type
    @Override
    public String getRepairDescription() {
        return "Road resurfacing at " + getLocationId()
                + " — lay and roll new asphalt surface";
    }
}
