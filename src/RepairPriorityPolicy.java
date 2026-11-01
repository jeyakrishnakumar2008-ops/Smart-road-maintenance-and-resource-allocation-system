// RepairPriorityPolicy.java
// Implements the exact five-step comparator used by the PriorityQueue.
// Java's PriorityQueue is a MIN-heap by default.
// To get the HIGHEST priority first, we INVERT the comparison:
//   if A should come BEFORE B, the comparator returns a NEGATIVE number.

import java.util.Comparator;

public class RepairPriorityPolicy implements Comparator<RoadDamage> {

    // compare(a, b) returns:
    //   negative  → a has higher priority than b (a comes first in the queue)
    //   zero      → a and b have equal priority
    //   positive  → b has higher priority than a (b comes first)
    @Override
    public int compare(RoadDamage a, RoadDamage b) {

        // STEP 1: Higher severity comes first.
        // Example: severity 3 > severity 2, so 3 should come first.
        // We compare b-a so that higher values give a negative result (higher priority).
        if (b.getSeverityRank() != a.getSeverityRank()) {
            return b.getSeverityRank() - a.getSeverityRank();
        }

        // STEP 2: Tie-break by accident risk rank (higher = more urgent).
        if (b.getAccidentRiskRank() != a.getAccidentRiskRank()) {
            return b.getAccidentRiskRank() - a.getAccidentRiskRank();
        }

        // STEP 3: Tie-break by road importance rank (higher = more important road).
        if (b.getRoadImportanceRank() != a.getRoadImportanceRank()) {
            return b.getRoadImportanceRank() - a.getRoadImportanceRank();
        }

        // STEP 4: Tie-break by traffic rank (higher traffic = more people affected).
        if (b.getTrafficRank() != a.getTrafficRank()) {
            return b.getTrafficRank() - a.getTrafficRank();
        }

        // STEP 5: Final tie-break — earlier report order wins (lower order = earlier).
        // Here we use a-b so that a SMALLER order number gives a negative result (comes first).
        return a.getReportOrder() - b.getReportOrder();
    }
}
