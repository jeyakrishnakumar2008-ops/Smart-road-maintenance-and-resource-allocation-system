// EngineeringHandler.java
// Handles all /engineering/* routes for the Engineering Section role.
// Covers: dashboard, inbox (review reports), verify/return/reject,
// priority queue, team management, road network, and assignment.

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class EngineeringHandler implements HttpHandler {

    private final MunicipalService service;
    private final SessionManager   sessionManager;

    public EngineeringHandler(MunicipalService service, SessionManager sessionManager) {
        this.service        = service;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        UserAccount user = HttpUtils.getSessionUser(exchange, sessionManager);
        if (user == null) { HttpUtils.redirect(exchange, "/login"); return; }
        if (!user.isEngineering()) { HttpUtils.redirect(exchange, "/login"); return; }

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String query  = exchange.getRequestURI().getQuery();

        if (path.startsWith("/engineering/inbox")) {
            HttpUtils.sendHtml(exchange, 200, buildInboxPage(user, null, null));
            return;
        }

        if (path.startsWith("/engineering/review")) {
            String rid = parseParam(query, "id");
            if ("POST".equals(method)) handleReview(exchange, user, rid);
            else HttpUtils.sendHtml(exchange, 200, buildReviewPage(user, rid, null, null));
            return;
        }

        if (path.startsWith("/engineering/priority")) {
            HttpUtils.sendHtml(exchange, 200, buildPriorityPage(user));
            return;
        }

        if (path.startsWith("/engineering/teams")) {
            HttpUtils.sendHtml(exchange, 200, buildTeamsPage(user));
            return;
        }

        if (path.startsWith("/engineering/network")) {
            if ("POST".equals(method)) handleNetwork(exchange, user);
            else HttpUtils.sendHtml(exchange, 200, buildNetworkPage(user, null, null));
            return;
        }

        if (path.startsWith("/engineering/assign")) {
            if ("POST".equals(method)) handleAssign(exchange, user);
            else HttpUtils.sendHtml(exchange, 200, buildAssignPage(user, null, null));
            return;
        }

        if (path.startsWith("/engineering/complete")) {
            if ("POST".equals(method)) handleComplete(exchange, user);
            else HttpUtils.sendHtml(exchange, 200, buildCompletePage(user, null, null));
            return;
        }

        if (path.startsWith("/engineering/history")) {
            HttpUtils.sendHtml(exchange, 200, buildHistoryPage(user));
            return;
        }

        // Default: Engineering dashboard
        HttpUtils.sendHtml(exchange, 200, buildDashboard(user));
    }

    // ================================================================
    // POST HANDLERS
    // ================================================================

    private void handleReview(HttpExchange exchange, UserAccount user, String ridFromQuery) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);
        String action   = p.getOrDefault("action", "");
        String reportId = p.getOrDefault("reportId", ridFromQuery);
        String error = null, success = null;

        if ("verify".equals(action)) {
            int sev = HttpUtils.parseInt(p.get("severityRank"), 0);
            int acc = HttpUtils.parseInt(p.get("accidentRiskRank"), 0);
            int imp = HttpUtils.parseInt(p.get("roadImportanceRank"), 0);
            int trf = HttpUtils.parseInt(p.get("trafficRank"), 0);
            error = service.verifyReport(reportId, sev, acc, imp, trf);
            if (error == null) success = "Report " + reportId + " verified and added to the priority queue.";

        } else if ("return".equals(action)) {
            String reason = p.getOrDefault("returnReason", "").trim();
            error = service.returnReport(reportId, reason);
            if (error == null) success = "Report " + reportId + " returned to Ward Officer for correction.";

        } else if ("reject".equals(action)) {
            String reason = p.getOrDefault("rejectionReason", "").trim();
            error = service.rejectReport(reportId, reason);
            if (error == null) success = "Report " + reportId + " rejected.";
        }

        if (error != null) {
            HttpUtils.sendHtml(exchange, 200, buildReviewPage(user, reportId, error, null));
        } else {
            HttpUtils.sendHtml(exchange, 200, buildInboxPage(user, null, success));
        }
    }

    private void handleAssign(HttpExchange exchange, UserAccount user) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);
        String action = p.getOrDefault("action", "auto");

        if ("manual".equals(action)) {
            String reportId = p.getOrDefault("reportId", "");
            String teamId   = p.getOrDefault("teamId", "");
            AssignmentResult result = service.assignTeamManually(reportId, teamId);
            HttpUtils.sendHtml(exchange, 200, buildAssignPage(user, result, null));
        } else {
            AssignmentResult result = service.assignNextPendingRepair();
            HttpUtils.sendHtml(exchange, 200, buildAssignPage(user, result, null));
        }
    }

    private void handleComplete(HttpExchange exchange, UserAccount user) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);
        String reportId = p.getOrDefault("reportId", "");
        String error = service.completeRepair(reportId);
        String success = error == null ? "Report " + reportId + " marked as COMPLETED." : null;
        HttpUtils.sendHtml(exchange, 200, buildCompletePage(user, error, success));
    }

    private void handleNetwork(HttpExchange exchange, UserAccount user) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);
        String action  = p.getOrDefault("action", "");
        String srcId   = p.getOrDefault("sourceId", p.getOrDefault("src", ""));
        String dstId   = p.getOrDefault("destinationId", p.getOrDefault("dst", ""));
        String msg = null, errorMsg = null;

        if ("close".equals(action)) {
            boolean ok1 = service.closeRoad(srcId, dstId);
            boolean ok2 = service.closeRoad(dstId, srcId);
            if (ok1 || ok2) msg = "Road " + srcId + " ↔ " + dstId + " closed.";
            else errorMsg = "Could not close road " + srcId + " ↔ " + dstId + ". Check road IDs.";
        } else if ("reopen".equals(action)) {
            boolean ok1 = service.reopenRoad(srcId, dstId);
            boolean ok2 = service.reopenRoad(dstId, srcId);
            if (ok1 || ok2) msg = "Road " + srcId + " ↔ " + dstId + " reopened.";
            else errorMsg = "Could not reopen road. Check road IDs.";
        } else if ("route".equals(action)) {
            String fromId = p.getOrDefault("fromId", "");
            String toId   = p.getOrDefault("toId", "");
            DijkstraResult result = service.getNetwork().findShortestPath(fromId, toId);
            HttpUtils.sendHtml(exchange, 200, buildNetworkPage(user, null, result));
            return;
        }
        HttpUtils.sendHtml(exchange, 200, buildNetworkPage(user, errorMsg != null ? errorMsg : msg, null));
    }

    // ================================================================
    // PAGE BUILDERS
    // ================================================================

    private String buildDashboard(UserAccount user) {
        int inbox       = service.getInboxReports().size();
        int verified    = service.getCountByStatus(RoadDamage.STATUS_VERIFIED);
        int assigned    = service.getCountByStatus(RoadDamage.STATUS_ASSIGNED);
        int inProgress  = service.getCountByStatus(RoadDamage.STATUS_IN_PROGRESS);
        int completed   = service.getCountByStatus(RoadDamage.STATUS_COMPLETED);
        int available   = service.getAvailableTeamCount();

        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Engineering Dashboard", user));
        sb.append(HtmlHelper.pageHeading("Engineering Dashboard",
                "Welcome, " + user.getDisplayName() + " — Senthamizh Nagar Municipality"));

        if (inbox > 0) {
            sb.append(HtmlHelper.alertWarning(inbox + " report(s) awaiting your review. "
                    + "<a href='/engineering/inbox'>Review now →</a>"));
        }

        sb.append("<div class=\"stat-grid\">\n");
        sb.append(statCard("📥", String.valueOf(inbox),      "Inbox (Pending Review)", "stat-orange"));
        sb.append(statCard("✅", String.valueOf(verified),   "Verified (In Queue)",    "stat-blue"));
        sb.append(statCard("🔧", String.valueOf(assigned),   "Assigned to Teams",      "stat-teal"));
        sb.append(statCard("⚙️", String.valueOf(inProgress), "In Progress",            "stat-purple"));
        sb.append(statCard("🏁", String.valueOf(completed),  "Completed",              "stat-green"));
        sb.append(statCard("👷", String.valueOf(available),  "Available Teams",        "stat-blue"));
        sb.append("</div>\n");

        sb.append("<div class=\"card-grid\">\n");
        sb.append(quickActionCard("📥", "Review Inbox",     "Review submitted ward officer reports",   "/engineering/inbox",    "btn-primary"));
        sb.append(quickActionCard("⚡", "Priority Queue",   "View VERIFIED reports awaiting assignment", "/engineering/priority", "btn-teal"));
        sb.append(quickActionCard("🔧", "Assign &amp; Route","Auto or manually assign next repair",     "/engineering/assign",   "btn-primary"));
        sb.append(quickActionCard("🗺️", "Road Network",      "Manage road closures and plan routes",    "/engineering/network",  "btn-teal"));
        sb.append("</div>\n");

        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildInboxPage(UserAccount user, String error, String success) {
        List<RoadDamage> inbox = service.getInboxReports();
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Inbox — Review Reports", user));
        sb.append(HtmlHelper.pageHeading("Inbox", "Review SUBMITTED and RETURNED reports"));
        if (error   != null) sb.append(HtmlHelper.alertError(error));
        if (success != null) sb.append(HtmlHelper.alertSuccess(success));

        sb.append(HtmlHelper.cardOpen("Reports Awaiting Review (" + inbox.size() + ")"));
        if (inbox.isEmpty()) {
            sb.append("<p>No reports waiting for review. All caught up! 🎉</p>\n");
        } else {
            sb.append("<div style=\"overflow-x:auto;\"><table class=\"data-table\">\n<thead><tr>");
            sb.append("<th>ID</th><th>Road Name</th><th>Ward</th><th>Location</th><th>Damage</th>");
            sb.append("<th>Sev</th><th>Acc</th><th>Imp</th><th>Trf</th>");
            sb.append("<th>Status</th><th>Submitted By</th><th>Action</th>");
            sb.append("</tr></thead>\n<tbody>\n");

            for (RoadDamage rd : inbox) {
                sb.append("<tr>\n");
                sb.append("<td><strong>" + HtmlHelper.escape(rd.getReportId()) + "</strong></td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getRoadName()) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getWardNumber()) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                        + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>\n");
                sb.append("<td>" + rd.getSeverityRank() + "</td>\n");
                sb.append("<td>" + rd.getAccidentRiskRank() + "</td>\n");
                sb.append("<td>" + rd.getRoadImportanceRank() + "</td>\n");
                sb.append("<td>" + rd.getTrafficRank() + "</td>\n");
                sb.append("<td>" + HtmlHelper.statusBadge(rd.getStatus()) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getSubmittedBy()) + "</td>\n");
                sb.append("<td><a href=\"/engineering/review?id=" + rd.getReportId()
                        + "\" class=\"btn btn-primary\" style=\"padding:4px 10px;font-size:0.8rem;\""
                        + " id=\"review-" + rd.getReportId() + "\">Review</a></td>\n");
                sb.append("</tr>\n");
            }
            sb.append("</tbody></table></div>\n");
        }
        sb.append(HtmlHelper.cardClose());
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildReviewPage(UserAccount user, String reportId, String error, String success) {
        RoadDamage rd = service.getReport(reportId);
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Review Report — " + reportId, user));
        sb.append(HtmlHelper.pageHeading("Review Report — " + HtmlHelper.escape(reportId),
                "Verify, return or reject this damage report"));

        if (error   != null) sb.append(HtmlHelper.alertError(error));
        if (success != null) sb.append(HtmlHelper.alertSuccess(success));

        if (rd == null) {
            sb.append(HtmlHelper.alertError("Report not found: " + HtmlHelper.escape(reportId)));
            sb.append(HtmlHelper.footer());
            return sb.toString();
        }

        // Report details panel
        sb.append(HtmlHelper.cardOpen("Report Details — " + rd.getReportId()));
        sb.append("<table class=\"data-table\">\n<tbody>\n");
        sb.append(detailRow("Status", HtmlHelper.statusBadge(rd.getStatus())));
        sb.append(detailRow("Submitted By", HtmlHelper.escape(rd.getSubmittedBy()) + " (Ward " + HtmlHelper.escape(rd.getWardNumber()) + ")"));
        sb.append(detailRow("Location", rd.getLocationId() + " — " + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId()))));
        sb.append(detailRow("Road Name", HtmlHelper.escape(rd.getRoadName())));
        sb.append(detailRow("Landmark", HtmlHelper.escape(rd.getLandmark())));
        sb.append(detailRow("Damage Type", HtmlHelper.escape(rd.getDamageType())));
        sb.append(detailRow("Required Equipment", HtmlHelper.escape(rd.getRequiredEquipment())));
        sb.append(detailRow("Severity Rank", String.valueOf(rd.getSeverityRank()) + "/3 " + HtmlHelper.priorityBadge(rd.getSeverityRank())));
        sb.append(detailRow("Accident Risk Rank", String.valueOf(rd.getAccidentRiskRank()) + "/3"));
        sb.append(detailRow("Road Importance Rank", String.valueOf(rd.getRoadImportanceRank()) + "/3"));
        sb.append(detailRow("Traffic Rank", String.valueOf(rd.getTrafficRank()) + "/3"));
        sb.append(detailRow("Field Observations", HtmlHelper.escape(rd.getFieldObservations())));
        sb.append(detailRow("Submission Date", HtmlHelper.escape(rd.getSubmissionDate())));
        sb.append("</tbody></table>\n");
        sb.append(HtmlHelper.cardClose());

        if (!RoadDamage.STATUS_SUBMITTED.equals(rd.getStatus())
                && !RoadDamage.STATUS_RETURNED.equals(rd.getStatus())) {
            sb.append(HtmlHelper.alertInfo("This report is " + rd.getStatus()
                    + " and cannot be reviewed again."));
            sb.append("<p><a href=\"/engineering/inbox\" class=\"btn btn-teal\">← Back to Inbox</a></p>\n");
            sb.append(HtmlHelper.footer());
            return sb.toString();
        }

        // Action 1: Verify (and optionally adjust ranks)
        sb.append(HtmlHelper.cardOpen("Action 1 — Verify Report (adjust ranks if needed)"));
        sb.append("<form method=\"POST\" action=\"/engineering/review?id="
                + rd.getReportId() + "\" id=\"verifyForm\">\n");
        sb.append("<input type=\"hidden\" name=\"reportId\" value=\""
                + HtmlHelper.escape(rd.getReportId()) + "\">\n");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"verify\">\n");
        sb.append("<div class=\"rank-grid\">\n");
        sb.append(HtmlHelper.rankSelectWithValue("Severity Rank",        "severityRank",       rd.getSeverityRank()));
        sb.append(HtmlHelper.rankSelectWithValue("Accident Risk Rank",   "accidentRiskRank",   rd.getAccidentRiskRank()));
        sb.append(HtmlHelper.rankSelectWithValue("Road Importance Rank", "roadImportanceRank", rd.getRoadImportanceRank()));
        sb.append(HtmlHelper.rankSelectWithValue("Traffic Rank",         "trafficRank",        rd.getTrafficRank()));
        sb.append("</div>\n");
        sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"verifyBtn\">"
                + "✅ Verify Report</button>\n");
        sb.append("</form>\n");
        sb.append(HtmlHelper.cardClose());

        // Action 2: Return for correction
        sb.append(HtmlHelper.cardOpen("Action 2 — Return for Correction"));
        sb.append("<form method=\"POST\" action=\"/engineering/review?id="
                + rd.getReportId() + "\" id=\"returnForm\">\n");
        sb.append("<input type=\"hidden\" name=\"reportId\" value=\""
                + HtmlHelper.escape(rd.getReportId()) + "\">\n");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"return\">\n");
        sb.append(HtmlHelper.formTextarea("Return Reason (required)", "returnReason",
                "Explain what the Ward Officer needs to correct...", true));
        sb.append("<button type=\"submit\" class=\"btn btn-teal\" id=\"returnBtn\">"
                + "⚠️ Return for Correction</button>\n");
        sb.append("</form>\n");
        sb.append(HtmlHelper.cardClose());

        // Action 3: Reject
        sb.append(HtmlHelper.cardOpen("Action 3 — Reject Report"));
        sb.append("<form method=\"POST\" action=\"/engineering/review?id="
                + rd.getReportId() + "\" id=\"rejectForm\">\n");
        sb.append("<input type=\"hidden\" name=\"reportId\" value=\""
                + HtmlHelper.escape(rd.getReportId()) + "\">\n");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"reject\">\n");
        sb.append(HtmlHelper.formTextarea("Rejection Reason (required)", "rejectionReason",
                "Explain why this report is being rejected (e.g. duplicate, invalid)...", true));
        sb.append("<button type=\"submit\" class=\"btn\" style=\"background:#DC2626;color:#fff;\" id=\"rejectBtn\">"
                + "❌ Reject Report</button>\n");
        sb.append("</form>\n");
        sb.append(HtmlHelper.cardClose());

        sb.append("<p><a href=\"/engineering/inbox\" class=\"btn btn-teal\">← Back to Inbox</a></p>\n");
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildPriorityPage(UserAccount user) {
        List<RoadDamage> queue = service.getPendingReportsOrdered();
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Priority Queue", user));
        sb.append(HtmlHelper.pageHeading("Verified Reports — Priority Queue",
                "Ordered by Severity → Accident Risk → Road Importance → Traffic → Report Order"));

        sb.append(HtmlHelper.cardOpen("Priority Queue (" + queue.size() + " reports awaiting assignment)"));
        if (queue.isEmpty()) {
            sb.append("<p>No verified reports in the priority queue.</p>\n");
        } else {
            sb.append("<div style=\"overflow-x:auto;\"><table class=\"data-table\">\n<thead><tr>");
            sb.append("<th>Rank</th><th>ID</th><th>Location</th><th>Damage</th>");
            sb.append("<th>Sev</th><th>Acc</th><th>Imp</th><th>Trf</th><th>Equipment</th><th>Priority</th>");
            sb.append("</tr></thead>\n<tbody>\n");
            int rank = 1;
            for (RoadDamage rd : queue) {
                sb.append("<tr>\n<td><strong>#" + rank++ + "</strong></td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getReportId()) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                        + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>\n");
                sb.append("<td>" + rd.getSeverityRank() + "</td>\n");
                sb.append("<td>" + rd.getAccidentRiskRank() + "</td>\n");
                sb.append("<td>" + rd.getRoadImportanceRank() + "</td>\n");
                sb.append("<td>" + rd.getTrafficRank() + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getRequiredEquipment()) + "</td>\n");
                sb.append("<td>" + HtmlHelper.priorityBadge(rd.getSeverityRank()) + "</td>\n");
                sb.append("</tr>\n");
            }
            sb.append("</tbody></table></div>\n");
        }
        sb.append(HtmlHelper.cardClose());
        sb.append("<p><a href=\"/engineering/assign\" class=\"btn btn-primary\" id=\"goAssignBtn\">"
                + "→ Assign Next Report</a></p>\n");
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildTeamsPage(UserAccount user) {
        List<MaintenanceTeam> teams = service.getAllTeams();
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Maintenance Teams", user));
        sb.append(HtmlHelper.pageHeading("Maintenance Teams", "Team status, equipment and current assignments"));
        for (MaintenanceTeam team : teams) {
            String avail = team.isAvailable()
                    ? "<span class='badge badge-completed'>Available</span>"
                    : "<span class='badge badge-inprogress'>Busy</span>";
            sb.append(HtmlHelper.cardOpen(team.getTeamId() + " — " + team.getTeamName() + "  " + avail));
            sb.append("<table class=\"data-table\"><tbody>\n");
            sb.append(detailRow("Current Location",
                    team.getCurrentLocationId() + " — "
                    + HtmlHelper.escape(service.getNetwork().getLocationName(team.getCurrentLocationId()))));
            sb.append(detailRow("Equipment", HtmlHelper.escape(team.getEquipmentNames())));
            if (!team.isAvailable()) {
                sb.append(detailRow("Current Assignment", HtmlHelper.escape(team.getCurrentAssignmentId())));
            }
            sb.append("</tbody></table>\n");
            sb.append(HtmlHelper.cardClose());
        }
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildNetworkPage(UserAccount user, String message, DijkstraResult routeResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Road Network", user));
        sb.append(HtmlHelper.pageHeading("Road Network Management",
                "Manage road closures and calculate shortest routes"));

        if (message != null) {
            if (message.contains("Error") || message.contains("Could not"))
                sb.append(HtmlHelper.alertError(message));
            else
                sb.append(HtmlHelper.alertSuccess(message));
        }

        if (routeResult != null) {
            sb.append(HtmlHelper.cardOpen("Route Calculation Result"));
            if (routeResult.isReachable()) {
                sb.append("<p>✅ Route found!</p>\n");
                sb.append("<p><strong>Path:</strong> " + HtmlHelper.escape(routeResult.getPathString()) + "</p>\n");
                sb.append("<p><strong>Total Travel Time:</strong> " + routeResult.getTotalTime() + " minutes</p>\n");
            } else {
                sb.append("<p>❌ Location is unreachable — no open road connects the two locations.</p>\n");
            }
            sb.append(HtmlHelper.cardClose());
        }

        // Close/Reopen road form
        sb.append(HtmlHelper.cardOpen("Close or Reopen a Road (bidirectional)"));
        sb.append("<form method=\"POST\" action=\"/engineering/network\" id=\"networkForm\">\n");
        sb.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:16px;\">\n");
        sb.append(HtmlHelper.locationSelect("From Location", "sourceId", service.getNetwork().getLocationNames()));
        sb.append(HtmlHelper.locationSelect("To Location",   "destinationId", service.getNetwork().getLocationNames()));
        sb.append("</div>\n");
        sb.append("<div style=\"display:flex;gap:12px;margin-top:8px;\">\n");
        sb.append("<button type=\"submit\" name=\"action\" value=\"close\""
                + " class=\"btn\" style=\"background:#DC2626;color:#fff;\" id=\"closeRoadBtn\">"
                + "🚧 Close Road (both directions)</button>\n");
        sb.append("<button type=\"submit\" name=\"action\" value=\"reopen\""
                + " class=\"btn btn-primary\" id=\"reopenRoadBtn\">"
                + "🟢 Reopen Road (both directions)</button>\n");
        sb.append("</div>\n</form>\n");
        sb.append(HtmlHelper.cardClose());

        // Route finder
        sb.append(HtmlHelper.cardOpen("Find Shortest Route (Dijkstra's Algorithm)"));
        sb.append("<form method=\"POST\" action=\"/engineering/network\" id=\"routeForm\">\n");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"route\">\n");
        sb.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:16px;\">\n");
        sb.append(HtmlHelper.locationSelect("From", "fromId", service.getNetwork().getLocationNames()));
        sb.append(HtmlHelper.locationSelect("To",   "toId",   service.getNetwork().getLocationNames()));
        sb.append("</div>\n");
        sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"calcRouteBtn\">"
                + "🗺️ Calculate Route</button>\n");
        sb.append("</form>\n");
        sb.append(HtmlHelper.cardClose());

        // Road list
        sb.append(HtmlHelper.cardOpen("All Road Connections"));
        sb.append(service.getNetwork().buildRoadTableHtml());
        sb.append(HtmlHelper.cardClose());

        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildAssignPage(UserAccount user, AssignmentResult result, String error) {
        List<RoadDamage> queue = service.getPendingReportsOrdered();
        List<MaintenanceTeam> teams = service.getAllTeams();
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Assign & Route", user));
        sb.append(HtmlHelper.pageHeading("Team Assignment",
                "Auto-assign highest priority repair or manually choose a team"));

        if (error != null) sb.append(HtmlHelper.alertError(error));

        // Show assignment result
        if (result != null) {
            if (result.isSuccess()) {
                DijkstraResult route = result.getRouteResult();
                sb.append(HtmlHelper.alertSuccess("Assigned report " + result.getReportId()
                        + " to team " + result.getTeamId()
                        + ". Route: " + HtmlHelper.escape(route.getPathString())
                        + " (" + route.getTotalTime() + " min)"));
            } else {
                sb.append(HtmlHelper.alertError(result.getFailureReason()));
                if (!result.getSkippedTeams().isEmpty()) {
                    sb.append("<ul style=\"color:#DC2626;padding-left:20px;\">\n");
                    for (String s : result.getSkippedTeams()) {
                        sb.append("<li>" + HtmlHelper.escape(s) + "</li>\n");
                    }
                    sb.append("</ul>\n");
                }
            }
        }

        // Auto-assign
        sb.append(HtmlHelper.cardOpen("Auto-Assign (Highest Priority VERIFIED Report)"));
        if (queue.isEmpty()) {
            sb.append("<p>No VERIFIED reports in the priority queue.</p>\n");
        } else {
            RoadDamage top = queue.get(0);
            sb.append("<p>Next report: <strong>" + top.getReportId() + "</strong>"
                    + " — " + HtmlHelper.escape(top.getDamageType()) + " at "
                    + HtmlHelper.escape(service.getNetwork().getLocationName(top.getLocationId()))
                    + " " + HtmlHelper.priorityBadge(top.getSeverityRank()) + "</p>\n");
            sb.append("<form method=\"POST\" action=\"/engineering/assign\" id=\"autoAssignForm\">\n");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"auto\">\n");
            sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"autoAssignBtn\">"
                    + "⚡ Auto-Assign to Nearest Eligible Team</button>\n");
            sb.append("</form>\n");
        }
        sb.append(HtmlHelper.cardClose());

        // Manual assign
        sb.append(HtmlHelper.cardOpen("Manual Assignment"));
        if (queue.isEmpty()) {
            sb.append("<p>No VERIFIED reports available to assign.</p>\n");
        } else {
            sb.append("<form method=\"POST\" action=\"/engineering/assign\" id=\"manualAssignForm\">\n");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"manual\">\n");
            sb.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:16px;\">\n");
            // Report dropdown
            sb.append("<div class=\"form-group\">\n<label>Select Report</label>\n");
            sb.append("<select name=\"reportId\" required>\n");
            for (RoadDamage rd : queue) {
                sb.append("<option value=\"" + rd.getReportId() + "\">"
                        + rd.getReportId() + " — " + rd.getDamageType()
                        + " at " + service.getNetwork().getLocationName(rd.getLocationId())
                        + " (Sev=" + rd.getSeverityRank() + ")</option>\n");
            }
            sb.append("</select>\n</div>\n");
            // Team dropdown
            sb.append("<div class=\"form-group\">\n<label>Select Team</label>\n");
            sb.append("<select name=\"teamId\" required>\n");
            for (MaintenanceTeam t : teams) {
                String avail = t.isAvailable() ? "" : " (BUSY)";
                sb.append("<option value=\"" + t.getTeamId() + "\">"
                        + t.getTeamId() + " — " + t.getTeamName() + avail + "</option>\n");
            }
            sb.append("</select>\n</div>\n</div>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-teal\" id=\"manualAssignBtn\">"
                    + "🔧 Assign Selected Team</button>\n");
            sb.append("</form>\n");
        }
        sb.append(HtmlHelper.cardClose());

        // Currently assigned/in-progress
        sb.append(HtmlHelper.cardOpen("Currently Active Repairs"));
        sb.append("<div style=\"overflow-x:auto;\"><table class=\"data-table\">\n<thead><tr>");
        sb.append("<th>Report</th><th>Location</th><th>Damage</th><th>Team</th><th>Status</th><th>Action</th>");
        sb.append("</tr></thead>\n<tbody>\n");
        boolean hasActive = false;
        for (RoadDamage rd : service.getAllReports()) {
            if (RoadDamage.STATUS_ASSIGNED.equals(rd.getStatus())
                    || RoadDamage.STATUS_IN_PROGRESS.equals(rd.getStatus())) {
                hasActive = true;
                sb.append("<tr>\n<td>" + rd.getReportId() + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>\n");
                sb.append("<td>" + HtmlHelper.escape(rd.getAssignedTeamId()) + "</td>\n");
                sb.append("<td>" + HtmlHelper.statusBadge(rd.getStatus()) + "</td>\n");
                sb.append("<td><form method='POST' action='/engineering/complete' style='display:inline;'>"
                        + "<input type='hidden' name='reportId' value='" + rd.getReportId() + "'>"
                        + "<button type='submit' class='btn btn-teal'"
                        + " style='padding:4px 10px;font-size:0.8rem;'"
                        + " id='complete-" + rd.getReportId() + "'>Mark Completed</button>"
                        + "</form></td>\n");
                sb.append("</tr>\n");
            }
        }
        if (!hasActive) sb.append("<tr><td colspan='6'>No active repairs at this time.</td></tr>\n");
        sb.append("</tbody></table></div>\n");
        sb.append(HtmlHelper.cardClose());

        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildCompletePage(UserAccount user, String error, String success) {
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Complete Repair", user));
        sb.append(HtmlHelper.pageHeading("Mark Repair Completed", "Release team and update status"));
        if (error   != null) sb.append(HtmlHelper.alertError(error));
        if (success != null) sb.append(HtmlHelper.alertSuccess(success));

        sb.append(HtmlHelper.cardOpen("Select Assigned Report to Complete"));
        sb.append("<form method=\"POST\" action=\"/engineering/complete\" id=\"completeForm\">\n");
        sb.append("<div class=\"form-group\">\n<label for=\"reportId\">Report ID</label>\n");
        sb.append("<select name=\"reportId\" id=\"reportId\" required>\n");
        boolean hasAny = false;
        for (RoadDamage rd : service.getAllReports()) {
            if (RoadDamage.STATUS_ASSIGNED.equals(rd.getStatus())
                    || RoadDamage.STATUS_IN_PROGRESS.equals(rd.getStatus())) {
                sb.append("<option value=\"" + rd.getReportId() + "\">"
                        + rd.getReportId() + " — " + rd.getDamageType()
                        + " (" + rd.getStatus() + ")</option>\n");
                hasAny = true;
            }
        }
        sb.append("</select>\n</div>\n");
        if (!hasAny) {
            sb.append("<p>No ASSIGNED or IN_PROGRESS reports available.</p>\n");
        } else {
            sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"completeRepairBtn\">"
                    + "✅ Mark as Completed</button>\n");
        }
        sb.append("</form>\n");
        sb.append(HtmlHelper.cardClose());
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildHistoryPage(UserAccount user) {
        List<RoadDamage> all = service.getAllReports();
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("All Reports", user));
        sb.append(HtmlHelper.pageHeading("All Reports", "Complete overview of all " + all.size() + " reports"));
        sb.append(HtmlHelper.cardOpen("All Reports"));
        sb.append("<div style=\"overflow-x:auto;\"><table class=\"data-table\">\n<thead><tr>");
        sb.append("<th>ID</th><th>Ward</th><th>Location</th><th>Damage</th>");
        sb.append("<th>Sev</th><th>Equipment</th><th>Status</th><th>Team</th>");
        sb.append("</tr></thead>\n<tbody>\n");
        for (RoadDamage rd : all) {
            sb.append("<tr>\n");
            sb.append("<td><strong>" + rd.getReportId() + "</strong></td>\n");
            sb.append("<td>" + HtmlHelper.escape(rd.getWardNumber()) + "</td>\n");
            sb.append("<td>" + rd.getLocationId() + " — "
                    + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>\n");
            sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>\n");
            sb.append("<td>" + HtmlHelper.priorityBadge(rd.getSeverityRank()) + "</td>\n");
            sb.append("<td>" + HtmlHelper.escape(rd.getRequiredEquipment()) + "</td>\n");
            sb.append("<td>" + HtmlHelper.statusBadge(rd.getStatus()) + "</td>\n");
            sb.append("<td>" + (rd.getAssignedTeamId() != null ? HtmlHelper.escape(rd.getAssignedTeamId()) : "—") + "</td>\n");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table></div>\n");
        sb.append(HtmlHelper.cardClose());
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    // ================================================================
    // UTILITIES
    // ================================================================
    private String statCard(String icon, String value, String label, String cls) {
        return "<div class=\"stat-card " + cls + "\">"
             + "<div class=\"stat-icon\">" + icon + "</div>"
             + "<div class=\"stat-value\">" + value + "</div>"
             + "<div class=\"stat-label\">" + label + "</div>"
             + "</div>\n";
    }
    private String quickActionCard(String icon, String title, String desc, String href, String btnClass) {
        return "<div class=\"card\"><div class=\"card-body\" style=\"text-align:center;padding:24px;\">"
             + "<h3 style=\"margin:0 0 8px 0;\">" + icon + " " + HtmlHelper.escape(title) + "</h3>"
             + "<p style=\"color:#64748B;margin:0 0 16px 0;font-size:0.875rem;\">" + desc + "</p>"
             + "<a href=\"" + href + "\" class=\"btn " + btnClass + "\">" + title + "</a>"
             + "</div></div>\n";
    }
    private String detailRow(String label, String value) {
        return "<tr><td style=\"font-weight:600;width:40%;\">" + label + "</td>"
             + "<td>" + value + "</td></tr>\n";
    }
    private String parseParam(String query, String key) {
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(HttpUtils.decode(kv[0])))
                return HttpUtils.decode(kv[1]);
        }
        return "";
    }
}
