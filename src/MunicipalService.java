// MunicipalService.java — UPDATED FOR THREE-ROLE WORKFLOW
// Central service class. Holds all in-memory data and implements business rules
// for the Ward Officer, Engineering Section and Maintenance Team roles.
//
// STATUS WORKFLOW:
//   SUBMITTED → (Engineering) → VERIFIED / RETURNED / REJECTED
//   RETURNED  → (Ward Officer corrects) → SUBMITTED
//   VERIFIED  → (Engineering assigns) → ASSIGNED
//   ASSIGNED  → (Maintenance starts) → IN_PROGRESS
//   IN_PROGRESS → (Maintenance completes) → COMPLETED

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.ArrayList;
import java.util.List;

public class MunicipalService {

    // ---- In-memory data stores ----
    private HashMap<String, RoadDamage>    reportsMap;   // All reports, keyed by report ID
    private HashMap<String, MaintenanceTeam> teamsMap;   // 3 teams, keyed by team ID
    private PriorityQueue<RoadDamage>      verifiedQueue; // VERIFIED reports in priority order
    private RoadNetwork                    network;       // Road graph + Dijkstra's
    private int                            nextReportOrder; // Auto-increments for each new report

    // ---- Constructor ----
    public MunicipalService() {
        reportsMap    = new HashMap<>();
        teamsMap      = new HashMap<>();
        verifiedQueue = new PriorityQueue<>(new RepairPriorityPolicy());
        network       = new RoadNetwork();
        nextReportOrder = 1;
        DatabaseConnection.initializeSchemaIfConnected();
        loadSampleData();
    }

    // ================================================================
    // SAMPLE DATA — loaded automatically on startup
    // ================================================================
    private void loadSampleData() {

        // --- 15 Locations in Senthamizh Nagar ---
        network.addLocation("L01", "Town Hall");
        network.addLocation("L02", "Market Street");
        network.addLocation("L03", "Anna Nagar");
        network.addLocation("L04", "Railway Station");
        network.addLocation("L05", "Bus Terminus");
        network.addLocation("L06", "Hospital Road");
        network.addLocation("L07", "School Zone");
        network.addLocation("L08", "Industrial Area");
        network.addLocation("L09", "Residential Block A");
        network.addLocation("L10", "Residential Block B");
        network.addLocation("L11", "Water Tank Junction");
        network.addLocation("L12", "Collector Office");
        network.addLocation("L13", "Sports Complex");
        network.addLocation("L14", "North Gate");
        network.addLocation("L15", "Isolated Lane");   // Not connected — unreachable

        // --- Road connections ---
        network.addBidirectionalRoad("L01", "L02",  5);
        network.addBidirectionalRoad("L01", "L03",  8);
        network.addBidirectionalRoad("L02", "L04",  6);
        network.addBidirectionalRoad("L02", "L05",  4);
        network.addBidirectionalRoad("L03", "L06",  7);
        network.addBidirectionalRoad("L03", "L07",  5);
        network.addBidirectionalRoad("L04", "L08", 10);
        network.addBidirectionalRoad("L05", "L09",  9);
        network.addBidirectionalRoad("L06", "L10",  6);
        network.addBidirectionalRoad("L07", "L11",  4);
        network.addBidirectionalRoad("L08", "L12", 11);
        network.addBidirectionalRoad("L09", "L13",  7);
        network.addBidirectionalRoad("L10", "L12",  5);
        network.addBidirectionalRoad("L11", "L13",  8);
        network.addBidirectionalRoad("L12", "L14",  6);
        network.addBidirectionalRoad("L13", "L14",  9);
        network.addBidirectionalRoad("L04", "L06",  7);
        network.addBidirectionalRoad("L05", "L07",  6);
        network.addBidirectionalRoad("L08", "L10",  8);
        network.addBidirectionalRoad("L01", "L12", 15);

        // Close the L08 ↔ L12 road to demonstrate closure routing
        network.closeRoad("L08", "L12");
        network.closeRoad("L12", "L08");

        // --- 3 Maintenance Teams ---
        MaintenanceTeam t1 = new MaintenanceTeam("T1", "Alpha Squad", "L01");
        t1.addEquipment(new Equipment("Pothole Filler"));
        t1.addEquipment(new Equipment("Safety Cones"));
        t1.addEquipment(new Equipment("Excavator"));

        MaintenanceTeam t2 = new MaintenanceTeam("T2", "Beta Squad", "L04");
        t2.addEquipment(new Equipment("Road Roller"));
        t2.addEquipment(new Equipment("Asphalt Spreader"));
        t2.addEquipment(new Equipment("Safety Cones"));

        MaintenanceTeam t3 = new MaintenanceTeam("T3", "Gamma Squad", "L05");
        t3.addEquipment(new Equipment("Pothole Filler"));
        t3.addEquipment(new Equipment("Safety Cones"));
        t3.addEquipment(new Equipment("Road Roller"));
        t3.addEquipment(new Equipment("Asphalt Spreader"));
        t3.addEquipment(new Equipment("Crack Sealer"));

        teamsMap.put("T1", t1);
        teamsMap.put("T2", t2);
        teamsMap.put("T3", t3);

        // --- Sample Reports ---
        // Format: addSampleReport(id, loc, type, sev, acc, imp, trf, equip, submittedBy)

        // R001–R010: VERIFIED by Engineering (already in priority queue)
        addSampleReport("R001","L06","Pothole",     3,3,3,3,"Pothole Filler","ward1");
        addSampleReport("R002","L02","Resurfacing", 3,3,2,3,"Road Roller","ward1");
        addSampleReport("R003","L07","Pothole",     3,2,3,2,"Pothole Filler","ward2");
        addSampleReport("R004","L03","Crack Seal",  3,2,2,3,"Crack Sealer","ward1");
        addSampleReport("R005","L04","Resurfacing", 3,1,3,3,"Road Roller","ward2");
        addSampleReport("R006","L08","Pothole",     2,3,3,3,"Pothole Filler","ward1");
        addSampleReport("R007","L10","Resurfacing", 2,3,2,3,"Road Roller","ward2");
        addSampleReport("R008","L11","Pothole",     2,2,3,2,"Pothole Filler","ward1");
        addSampleReport("R009","L12","Crack Seal",  2,2,2,3,"Crack Sealer","ward2");
        addSampleReport("R010","L01","Resurfacing", 2,1,3,3,"Road Roller","ward1");

        // Mark R001–R010 as VERIFIED (Engineering pre-approved for demo)
        for (String id : new String[]{"R001","R002","R003","R004","R005",
                                       "R006","R007","R008","R009","R010"}) {
            RoadDamage rd = reportsMap.get(id);
            rd.directVerify();
            verifiedQueue.offer(rd);
        }

        // R011: Pre-assigned to T1 (Pothole at L06 — Alpha Squad is on it)
        addSampleReport("R011","L06","Pothole",     3,3,3,3,"Pothole Filler","ward2");
        RoadDamage r011 = reportsMap.get("R011");
        r011.directVerify();
        r011.assignTeam("T1");
        t1.assignJob("R011");

        // R012: Pre-assigned to T2 (Resurfacing at L02 — Beta Squad, IN_PROGRESS)
        addSampleReport("R012","L02","Resurfacing", 3,3,2,3,"Road Roller","ward1");
        RoadDamage r012 = reportsMap.get("R012");
        r012.directVerify();
        r012.assignTeam("T2");
        t2.assignJob("R012");
        r012.startWork();   // IN_PROGRESS

        // R013: COMPLETED (historical record)
        addSampleReport("R013","L09","Crack Seal",  1,2,3,2,"Crack Sealer","ward2");
        RoadDamage r013 = reportsMap.get("R013");
        r013.directVerify();
        r013.assignTeam("T3");
        r013.startWork();
        r013.completeWithNote("Road surface repaired and tested. Area cleaned.");
        // Note: T3 location NOT updated here — we want T3 available at L05 for live demo

        // R014: SUBMITTED by ward1, awaiting Engineering review
        addSampleReport("R014","L13","Pothole",     1,3,3,3,"Pothole Filler","ward1");
        // R015 is at L15 (Isolated Lane) — VERIFIED in queue to test unreachable routing
        addSampleReport("R015","L15","Pothole",     3,3,3,3,"Pothole Filler","ward1");
        RoadDamage r015 = reportsMap.get("R015");
        r015.directVerify();
        verifiedQueue.offer(r015);

        // R016–R017: SUBMITTED by ward2
        addSampleReport("R016","L03","Pothole",     2,3,1,2,"Pothole Filler","ward2");
        addSampleReport("R017","L07","Resurfacing", 3,1,1,2,"Road Roller","ward2");

        // R016: RETURNED by Engineering (needs more details)
        reportsMap.get("R016").returnForCorrection(
            "Please provide a more accurate road-importance assessment for this lane.");

        // R018-R022: VERIFIED (lower-priority, in the queue)
        addSampleReport("R018","L06","Crack Seal",   1,1,1,1,"Crack Sealer","ward1");
        addSampleReport("R019","L07","Resurfacing",  3,1,1,2,"Road Roller","ward2");
        addSampleReport("R020","L08","Pothole",      2,2,2,2,"Pothole Filler","ward1");
        addSampleReport("R021","L10","Crack Seal",   1,3,2,1,"Crack Sealer","ward2");
        addSampleReport("R022","L11","Resurfacing",  3,2,2,3,"Road Roller","ward1");

        for (String id : new String[]{"R018","R019","R020","R021","R022"}) {
            RoadDamage rd = reportsMap.get(id);
            rd.directVerify();
            verifiedQueue.offer(rd);
        }
    }

    // Private helper: creates a RoadDamage in SUBMITTED status and stores it
    private void addSampleReport(String id, String locId, String type,
                                  int sev, int acc, int imp, int trf,
                                  String equipment, String submittedBy) {
        RoadDamage rd = new RoadDamage(id, locId, type, sev, acc, imp, trf,
                nextReportOrder++, equipment,
                submittedBy, "W0X", locId + " Road", "Near landmark", "Sample observation");
        reportsMap.put(id, rd);
    }

    // ================================================================
    // WARD OFFICER OPERATIONS
    // ================================================================

    // Ward officer submits a new report (status = SUBMITTED, NOT added to priority queue yet)
    public String submitReport(String wardOfficerId, String wardNumber,
                               String reportId, String locationId, String roadName,
                               String landmark, String damageType,
                               int sev, int acc, int imp, int trf,
                               String fieldObservations, String requiredEquipment) {
        if (reportsMap.containsKey(reportId)) {
            return "Error: Report ID '" + reportId + "' already exists.";
        }
        if (!network.getLocations().contains(locationId)) {
            return "Error: Location ID '" + locationId + "' does not exist.";
        }
        if (!RoadDamage.isValidRank(sev)) return "Error: Severity rank must be 1–3.";
        if (!RoadDamage.isValidRank(acc)) return "Error: Accident risk rank must be 1–3.";
        if (!RoadDamage.isValidRank(imp)) return "Error: Road importance rank must be 1–3.";
        if (!RoadDamage.isValidRank(trf)) return "Error: Traffic rank must be 1–3.";
        if (damageType == null || damageType.trim().isEmpty())
            return "Error: Damage type cannot be empty.";
        if (requiredEquipment == null || requiredEquipment.trim().isEmpty())
            return "Error: Required equipment cannot be empty.";

        RoadDamage rd = new RoadDamage(
                reportId.trim(), locationId, damageType.trim(),
                sev, acc, imp, trf, nextReportOrder++, requiredEquipment.trim(),
                wardOfficerId, wardNumber, roadName.trim(),
                landmark.trim(), fieldObservations.trim());
        reportsMap.put(rd.getReportId(), rd);
        DatabaseRepository.saveReport(rd);
        return null; // null = success
    }

    // Ward officer corrects and resubmits a RETURNED report
    public String resubmitReport(String reportId, String wardOfficerId,
                                  int sev, int acc, int imp, int trf,
                                  String observations, String landmark) {
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        if (!wardOfficerId.equals(rd.getSubmittedBy()))
            return "Error: You can only resubmit your own reports.";
        if (!RoadDamage.STATUS_RETURNED.equals(rd.getStatus()))
            return "Error: Only RETURNED reports can be resubmitted.";
        if (!RoadDamage.isValidRank(sev)) return "Error: Severity rank must be 1–3.";
        if (!RoadDamage.isValidRank(acc)) return "Error: Accident risk rank must be 1–3.";
        if (!RoadDamage.isValidRank(imp)) return "Error: Road importance rank must be 1–3.";
        if (!RoadDamage.isValidRank(trf)) return "Error: Traffic rank must be 1–3.";
        rd.resubmit(sev, acc, imp, trf, observations, landmark);
        DatabaseRepository.updateReportStatus(reportId, "RETURNED", "SUBMITTED", wardOfficerId, "Resubmitted with corrections");
        return null;
    }

    // Returns all reports submitted by a specific ward officer
    public List<RoadDamage> getReportsByWardOfficer(String wardOfficerId) {
        List<RoadDamage> result = new ArrayList<>();
        for (RoadDamage rd : reportsMap.values()) {
            if (wardOfficerId.equals(rd.getSubmittedBy())) result.add(rd);
        }
        result.sort((a, b) -> b.getReportOrder() - a.getReportOrder()); // newest first
        return result;
    }

    // ================================================================
    // ENGINEERING OPERATIONS
    // ================================================================

    // Engineering verifies a report and optionally adjusts its priority ranks
    public String verifyReport(String reportId, int sev, int acc, int imp, int trf) {
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        if (!RoadDamage.isValidRank(sev)) return "Error: Severity rank must be 1–3.";
        if (!RoadDamage.isValidRank(acc)) return "Error: Accident risk rank must be 1–3.";
        if (!RoadDamage.isValidRank(imp)) return "Error: Road importance rank must be 1–3.";
        if (!RoadDamage.isValidRank(trf)) return "Error: Traffic rank must be 1–3.";
        boolean ok = rd.verify(sev, acc, imp, trf);
        if (!ok) return "Error: Report '" + reportId + "' cannot be verified (status = " + rd.getStatus() + ").";
        verifiedQueue.offer(rd); // Now enters the priority queue
        DatabaseRepository.updateReportStatus(reportId, "SUBMITTED", "VERIFIED", "Engineering", "Report verified and entered PriorityQueue");
        return null;
    }

    // Engineering returns a report to the ward officer for correction
    public String returnReport(String reportId, String reason) {
        if (reason == null || reason.trim().isEmpty())
            return "Error: Please provide a reason for returning the report.";
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        boolean ok = rd.returnForCorrection(reason.trim());
        if (!ok) return "Error: Cannot return report with status " + rd.getStatus() + ".";
        DatabaseRepository.updateReportStatus(reportId, "SUBMITTED", "RETURNED", "Engineering", reason.trim());
        return null;
    }

    // Engineering rejects a report
    public String rejectReport(String reportId, String reason) {
        if (reason == null || reason.trim().isEmpty())
            return "Error: Please provide a reason for rejection.";
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        boolean ok = rd.reject(reason.trim());
        if (!ok) return "Error: Cannot reject report with status " + rd.getStatus() + ".";
        DatabaseRepository.updateReportStatus(reportId, "SUBMITTED", "REJECTED", "Engineering", reason.trim());
        return null;
    }

    // Engineering updates ranks of a VERIFIED report (before assignment)
    public String updateReport(String reportId, int sev, int acc, int imp, int trf) {
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        if (!RoadDamage.isValidRank(sev)) return "Error: Severity rank must be 1–3.";
        if (!RoadDamage.isValidRank(acc)) return "Error: Accident risk rank must be 1–3.";
        if (!RoadDamage.isValidRank(imp)) return "Error: Road importance rank must be 1–3.";
        if (!RoadDamage.isValidRank(trf)) return "Error: Traffic rank must be 1–3.";
        boolean ok = rd.updateRanks(sev, acc, imp, trf);
        if (!ok) return "Error: Only VERIFIED reports can have ranks updated (status = " + rd.getStatus() + ").";
        rebuildVerifiedQueue();
        return null;
    }

    // Legacy: adds a report directly as VERIFIED (used by TestRunner for backward compat)
    public String addNewReport(String reportId, String locationId, String damageType,
                               int sev, int acc, int imp, int trf, String equipment) {
        if (reportsMap.containsKey(reportId))
            return "Error: Report ID '" + reportId + "' already exists.";
        if (!network.getLocations().contains(locationId))
            return "Error: Location ID '" + locationId + "' does not exist.";
        if (!RoadDamage.isValidRank(sev)) return "Error: Severity rank must be 1–3.";
        if (!RoadDamage.isValidRank(acc)) return "Error: Accident risk rank must be 1–3.";
        if (!RoadDamage.isValidRank(imp)) return "Error: Road importance rank must be 1–3.";
        if (!RoadDamage.isValidRank(trf)) return "Error: Traffic rank must be 1–3.";
        if (damageType == null || damageType.trim().isEmpty()) return "Error: Damage type required.";
        if (equipment == null || equipment.trim().isEmpty()) return "Error: Equipment required.";
        RoadDamage rd = new RoadDamage(reportId.trim(), locationId, damageType.trim(),
                sev, acc, imp, trf, nextReportOrder++, equipment.trim());
        rd.directVerify(); // Goes straight to VERIFIED (legacy behaviour)
        reportsMap.put(rd.getReportId(), rd);
        verifiedQueue.offer(rd);
        return null;
    }

    // Engineering assigns the highest-priority VERIFIED report automatically
    public AssignmentResult assignNextPendingRepair() {
        List<RoadDamage> verifiedOrdered = getPendingReportsOrdered();
        if (verifiedOrdered.isEmpty()) return AssignmentResult.noPending();

        RoadDamage topReport = verifiedOrdered.get(0);
        if (!RoadDamage.STATUS_VERIFIED.equals(topReport.getStatus())) {
            rebuildVerifiedQueue();
            return AssignmentResult.noEligibleTeam(topReport.getReportId(),
                    "Report is no longer VERIFIED.", new ArrayList<>());
        }

        return doAssign(topReport);
    }

    // Engineering assigns a VERIFIED report to a specific team (manual assignment)
    public AssignmentResult assignTeamManually(String reportId, String teamId) {
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return AssignmentResult.noEligibleTeam(reportId,
                "Report not found.", new ArrayList<>());
        if (!RoadDamage.STATUS_VERIFIED.equals(rd.getStatus()))
            return AssignmentResult.noEligibleTeam(reportId,
                    "Only VERIFIED reports can be assigned (status = " + rd.getStatus() + ").",
                    new ArrayList<>());

        MaintenanceTeam team = teamsMap.get(teamId);
        if (team == null) return AssignmentResult.noEligibleTeam(reportId,
                "Team '" + teamId + "' not found.", new ArrayList<>());
        if (!team.isAvailable()) return AssignmentResult.noEligibleTeam(reportId,
                "Team " + teamId + " is not available.", new ArrayList<>());
        if (!team.hasEquipmentByName(rd.getRequiredEquipment()))
            return AssignmentResult.noEligibleTeam(reportId,
                    "Team " + teamId + " does not have required equipment: " + rd.getRequiredEquipment(),
                    new ArrayList<>());

        DijkstraResult route = network.findShortestPath(team.getCurrentLocationId(), rd.getLocationId());
        if (!route.isReachable()) return AssignmentResult.unreachable(reportId,
                "Location " + rd.getLocationId() + " is unreachable from team's position.", new ArrayList<>());

        team.assignJob(reportId);
        rd.assignTeam(teamId);
        verifiedQueue.remove(rd);
        DatabaseRepository.assignRepairTransaction(reportId, teamId, "Engineering");
        return AssignmentResult.success(reportId, teamId, route);
    }

    // Shared assignment logic used by both auto and manual assignment
    private AssignmentResult doAssign(RoadDamage topReport) {
        String targetLocation = topReport.getLocationId();
        String requiredEq     = topReport.getRequiredEquipment();
        List<String> skipped  = new ArrayList<>();
        String bestTeamId     = null;
        DijkstraResult bestRoute = null;

        for (MaintenanceTeam team : teamsMap.values()) {
            if (!team.isAvailable()) {
                skipped.add(team.getTeamId() + ": not available (assigned to "
                        + team.getCurrentAssignmentId() + ")");
                continue;
            }
            if (!team.hasEquipmentByName(requiredEq)) {
                skipped.add(team.getTeamId() + ": missing equipment '" + requiredEq + "'");
                continue;
            }
            DijkstraResult route = network.findShortestPath(
                    team.getCurrentLocationId(), targetLocation);
            if (!route.isReachable()) {
                skipped.add(team.getTeamId() + ": cannot reach " + targetLocation);
                continue;
            }
            if (bestTeamId == null || route.getTotalTime() < bestRoute.getTotalTime()) {
                bestTeamId = team.getTeamId();
                bestRoute  = route;
            }
        }

        if (bestTeamId == null) {
            String reason = skipped.isEmpty() ? "No teams in system."
                    : "All teams rejected: " + String.join("; ", skipped);
            return AssignmentResult.noEligibleTeam(topReport.getReportId(), reason, skipped);
        }

        teamsMap.get(bestTeamId).assignJob(topReport.getReportId());
        topReport.assignTeam(bestTeamId);
        verifiedQueue.remove(topReport);
        DatabaseRepository.assignRepairTransaction(topReport.getReportId(), bestTeamId, "Engineering");
        return AssignmentResult.success(topReport.getReportId(), bestTeamId, bestRoute);
    }

    // Engineering completes a repair (admin action — works on ASSIGNED or IN_PROGRESS)
    public String completeRepair(String reportId) {
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        if (!RoadDamage.STATUS_ASSIGNED.equals(rd.getStatus())
                && !RoadDamage.STATUS_IN_PROGRESS.equals(rd.getStatus())) {
            return "Error: Report '" + reportId + "' cannot be completed (status = " + rd.getStatus() + ").";
        }
        String teamId = rd.getAssignedTeamId();
        MaintenanceTeam team = teamsMap.get(teamId);
        rd.markCompleted();
        if (team != null) team.completeRepair(rd.getLocationId());
        DatabaseRepository.completeRepairTransaction(reportId, teamId, "Administrative verification completion", rd.getLocationId());
        return null;
    }

    // ================================================================
    // MAINTENANCE TEAM OPERATIONS
    // ================================================================

    // Maintenance team starts its assigned repair (ASSIGNED → IN_PROGRESS)
    public String startRepair(String reportId, String teamId) {
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        if (!teamId.equals(rd.getAssignedTeamId()))
            return "Error: Report '" + reportId + "' is not assigned to team " + teamId + ".";
        if (!RoadDamage.STATUS_ASSIGNED.equals(rd.getStatus()))
            return "Error: Repair must be ASSIGNED to start. Current status: " + rd.getStatus() + ".";
        boolean ok = rd.startWork();
        if (!ok) return "Error: Could not start repair.";
        DatabaseRepository.updateReportStatus(reportId, "ASSIGNED", "IN_PROGRESS", teamId, "Squad commenced work on site");
        return null;
    }

    // Maintenance team completes its repair (IN_PROGRESS → COMPLETED)
    public String completeRepairByTeam(String reportId, String teamId, String note) {
        RoadDamage rd = reportsMap.get(reportId);
        if (rd == null) return "Error: Report '" + reportId + "' not found.";
        if (!teamId.equals(rd.getAssignedTeamId()))
            return "Error: Report '" + reportId + "' is not assigned to your team.";
        if (!RoadDamage.STATUS_IN_PROGRESS.equals(rd.getStatus()))
            return "Error: Repair must be IN_PROGRESS before completing. Start it first.";
        boolean ok = rd.completeWithNote(note != null ? note.trim() : "Repair completed.");
        if (!ok) return "Error: Could not complete repair.";
        MaintenanceTeam team = teamsMap.get(teamId);
        if (team != null) team.completeRepair(rd.getLocationId());
        DatabaseRepository.completeRepairTransaction(reportId, teamId, note, rd.getLocationId());
        return null;
    }

    // Returns reports currently assigned to a specific team (ASSIGNED or IN_PROGRESS)
    public List<RoadDamage> getActiveReportsByTeam(String teamId) {
        List<RoadDamage> result = new ArrayList<>();
        for (RoadDamage rd : reportsMap.values()) {
            if (teamId.equals(rd.getAssignedTeamId())
                    && (RoadDamage.STATUS_ASSIGNED.equals(rd.getStatus())
                    || RoadDamage.STATUS_IN_PROGRESS.equals(rd.getStatus()))) {
                result.add(rd);
            }
        }
        return result;
    }

    // Returns completed reports for a specific team (history)
    public List<RoadDamage> getCompletedReportsByTeam(String teamId) {
        List<RoadDamage> result = new ArrayList<>();
        for (RoadDamage rd : reportsMap.values()) {
            // We can't easily track which team completed it since assignedTeamId is cleared.
            // Instead, use completionNote presence AND look for completed reports
            // that were in the area where the team is now located.
            // Simpler: check all COMPLETED where completionDate is set (history for all teams).
            // For the team-specific view, we'll show all COMPLETED for now.
            if (RoadDamage.STATUS_COMPLETED.equals(rd.getStatus())) {
                result.add(rd);
            }
        }
        result.sort((a, b) -> b.getCompletionDate().compareTo(a.getCompletionDate()));
        return result;
    }

    // ================================================================
    // SHARED READ OPERATIONS
    // ================================================================

    public List<RoadDamage> getAllReports() {
        List<RoadDamage> list = new ArrayList<>(reportsMap.values());
        list.sort((a, b) -> a.getReportId().compareTo(b.getReportId()));
        return list;
    }

    public RoadDamage getReport(String reportId)       { return reportsMap.get(reportId); }
    public List<MaintenanceTeam> getAllTeams()          { return new ArrayList<>(teamsMap.values()); }
    public MaintenanceTeam getTeam(String teamId)       { return teamsMap.get(teamId); }
    public RoadNetwork getNetwork()                     { return network; }

    // Returns SUBMITTED + RETURNED reports for the Engineering inbox
    public List<RoadDamage> getInboxReports() {
        List<RoadDamage> result = new ArrayList<>();
        for (RoadDamage rd : reportsMap.values()) {
            if (RoadDamage.STATUS_SUBMITTED.equals(rd.getStatus())
                    || RoadDamage.STATUS_RETURNED.equals(rd.getStatus())) {
                result.add(rd);
            }
        }
        result.sort((a, b) -> a.getReportId().compareTo(b.getReportId()));
        return result;
    }

    // Returns VERIFIED reports in exact priority order (for display and assignment)
    public List<RoadDamage> getPendingReportsOrdered() {
        PriorityQueue<RoadDamage> copy = new PriorityQueue<>(new RepairPriorityPolicy());
        for (RoadDamage rd : verifiedQueue) {
            if (RoadDamage.STATUS_VERIFIED.equals(rd.getStatus())) copy.offer(rd);
        }
        List<RoadDamage> ordered = new ArrayList<>();
        while (!copy.isEmpty()) ordered.add(copy.poll());
        return ordered;
    }

    // Rebuilds the verified queue after a rank update
    private void rebuildVerifiedQueue() {
        verifiedQueue.clear();
        for (RoadDamage rd : reportsMap.values()) {
            if (RoadDamage.STATUS_VERIFIED.equals(rd.getStatus())) verifiedQueue.offer(rd);
        }
    }

    // Road network operations
    public boolean closeRoad(String src, String dst) {
        boolean ok = network.closeRoad(src, dst);
        if (ok) DatabaseRepository.setRoadOpen(src, dst, false);
        return ok;
    }

    public boolean reopenRoad(String src, String dst) {
        boolean ok = network.reopenRoad(src, dst);
        if (ok) DatabaseRepository.setRoadOpen(src, dst, true);
        return ok;
    }

    // ================================================================
    // STATISTICS (used by dashboards)
    // ================================================================
    public int getTotalReports()     { return reportsMap.size(); }
    public int getAvailableTeamCount() {
        int c = 0; for (MaintenanceTeam t : teamsMap.values()) if (t.isAvailable()) c++; return c;
    }

    public int getCountByStatus(String status) {
        int c = 0;
        for (RoadDamage rd : reportsMap.values())
            if (status.equals(rd.getStatus())) c++;
        return c;
    }

    // Backward-compat helpers used by TestRunner
    public int getPendingCount()   { return getCountByStatus(RoadDamage.STATUS_VERIFIED); }
    public int getAssignedCount()  { return getCountByStatus(RoadDamage.STATUS_ASSIGNED); }
    public int getCompletedCount() { return getCountByStatus(RoadDamage.STATUS_COMPLETED); }
}
