// WardHandler.java
// Handles all /ward/* routes for the Ward Officer role.
// Routes: /ward/dashboard, /ward/submit, /ward/reports, /ward/resubmit

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class WardHandler implements HttpHandler {

    private final MunicipalService service;
    private final SessionManager   sessionManager;

    public WardHandler(MunicipalService service, SessionManager sessionManager) {
        this.service        = service;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // --- Authentication check ---
        UserAccount user = HttpUtils.getSessionUser(exchange, sessionManager);
        if (user == null) { HttpUtils.redirect(exchange, "/login"); return; }
        if (!user.isWardOfficer()) { HttpUtils.redirect(exchange, "/login"); return; }

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String query  = exchange.getRequestURI().getQuery();

        if (path.startsWith("/ward/submit")) {
            if ("POST".equals(method)) handleSubmit(exchange, user);
            else HttpUtils.sendHtml(exchange, 200, buildSubmitPage(user, null, null));
            return;
        }

        if (path.startsWith("/ward/resubmit")) {
            String rid = (query != null && query.startsWith("id=")) ? query.substring(3) : "";
            if ("POST".equals(method)) handleResubmit(exchange, user);
            else HttpUtils.sendHtml(exchange, 200, buildResubmitPage(user, rid, null, null));
            return;
        }

        if (path.startsWith("/ward/reports")) {
            HttpUtils.sendHtml(exchange, 200, buildMyReportsPage(user));
            return;
        }

        // Default: dashboard
        HttpUtils.sendHtml(exchange, 200, buildDashboard(user));
    }

    // ================================================================
    // POST HANDLERS
    // ================================================================

    private void handleSubmit(HttpExchange exchange, UserAccount user) throws IOException {
        String body   = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);

        // Auto-generate report ID from timestamp to ensure uniqueness
        String reportId = "R-" + user.getUserId().toUpperCase()
                        + "-" + System.currentTimeMillis() % 100000;
        String locationId        = p.getOrDefault("locationId", "");
        String roadName          = p.getOrDefault("roadName", "");
        String landmark          = p.getOrDefault("landmark", "");
        String damageType        = p.getOrDefault("damageType", "");
        int    sev               = HttpUtils.parseInt(p.get("severityRank"), 1);
        int    acc               = HttpUtils.parseInt(p.get("accidentRiskRank"), 1);
        int    imp               = HttpUtils.parseInt(p.get("roadImportanceRank"), 1);
        int    trf               = HttpUtils.parseInt(p.get("trafficRank"), 1);
        String fieldObservations = p.getOrDefault("fieldObservations", "");
        String requiredEquipment = p.getOrDefault("requiredEquipment", "");

        String error = service.submitReport(
                user.getUserId(), user.getWardId(),
                reportId, locationId, roadName, landmark, damageType,
                sev, acc, imp, trf, fieldObservations, requiredEquipment);

        if (error != null) {
            HttpUtils.sendHtml(exchange, 200, buildSubmitPage(user, error, null));
        } else {
            HttpUtils.sendHtml(exchange, 200, buildSubmitPage(user, null,
                    "Report " + reportId + " submitted successfully! "
                    + "It is now awaiting Engineering review."));
        }
    }

    private void handleResubmit(HttpExchange exchange, UserAccount user) throws IOException {
        String body   = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);

        String reportId          = p.getOrDefault("reportId", "");
        int    sev               = HttpUtils.parseInt(p.get("severityRank"), 1);
        int    acc               = HttpUtils.parseInt(p.get("accidentRiskRank"), 1);
        int    imp               = HttpUtils.parseInt(p.get("roadImportanceRank"), 1);
        int    trf               = HttpUtils.parseInt(p.get("trafficRank"), 1);
        String fieldObservations = p.getOrDefault("fieldObservations", "");
        String landmark          = p.getOrDefault("landmark", "");

        String error = service.resubmitReport(
                reportId, user.getUserId(), sev, acc, imp, trf, fieldObservations, landmark);

        if (error != null) {
            HttpUtils.sendHtml(exchange, 200, buildResubmitPage(user, reportId, error, null));
        } else {
            HttpUtils.sendHtml(exchange, 200, buildMyReportsPage(user,
                    "Report " + reportId + " resubmitted successfully! Engineering will review it again."));
        }
    }

    // ================================================================
    // PAGE BUILDERS
    // ================================================================

    private String buildDashboard(UserAccount user) {
        List<RoadDamage> myReports = service.getReportsByWardOfficer(user.getUserId());
        long submitted  = myReports.stream().filter(r -> RoadDamage.STATUS_SUBMITTED.equals(r.getStatus())).count();
        long returned   = myReports.stream().filter(r -> RoadDamage.STATUS_RETURNED.equals(r.getStatus())).count();
        long verified   = myReports.stream().filter(r -> RoadDamage.STATUS_VERIFIED.equals(r.getStatus())).count();
        long assigned   = myReports.stream().filter(r -> RoadDamage.STATUS_ASSIGNED.equals(r.getStatus())).count();
        long inProgress = myReports.stream().filter(r -> RoadDamage.STATUS_IN_PROGRESS.equals(r.getStatus())).count();
        long completed  = myReports.stream().filter(r -> RoadDamage.STATUS_COMPLETED.equals(r.getStatus())).count();

        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Ward Officer Dashboard", user));
        sb.append(HtmlHelper.pageHeading("Ward Officer Dashboard",
                "Welcome, " + user.getDisplayName() + " — " + user.getWardId()));

        // Attention required banner
        if (returned > 0) {
            sb.append(HtmlHelper.alertWarning(returned + " report(s) returned for correction. "
                    + "<a href='/ward/reports'>View &amp; Resubmit →</a>"));
        }

        // Stats
        sb.append("<div class=\"stat-grid\">\n");
        sb.append(statCard("📋", String.valueOf(myReports.size()), "My Total Reports", "stat-blue"));
        sb.append(statCard("🕐", String.valueOf(submitted), "Awaiting Review", "stat-orange"));
        sb.append(statCard("⚠️", String.valueOf(returned), "Returned", "stat-red"));
        sb.append(statCard("✅", String.valueOf(verified + assigned + inProgress), "In Progress", "stat-teal"));
        sb.append(statCard("🏁", String.valueOf(completed), "Completed", "stat-green"));
        sb.append("</div>\n");

        // Quick actions
        sb.append("<div class=\"card-grid\">\n");
        sb.append("<div class=\"card\"><div class=\"card-body\" style=\"text-align:center;padding:24px;\">");
        sb.append("<h3 style=\"margin:0 0 8px 0;\">➕ Submit New Report</h3>");
        sb.append("<p style=\"color:#64748B;margin:0 0 16px 0;font-size:0.875rem;\">Report road damage in your ward</p>");
        sb.append("<a href=\"/ward/submit\" class=\"btn btn-primary\" id=\"submitNewBtn\">Submit Report</a>");
        sb.append("</div></div>\n");

        sb.append("<div class=\"card\"><div class=\"card-body\" style=\"text-align:center;padding:24px;\">");
        sb.append("<h3 style=\"margin:0 0 8px 0;\">📋 My Reports</h3>");
        sb.append("<p style=\"color:#64748B;margin:0 0 16px 0;font-size:0.875rem;\">View all reports you have submitted</p>");
        sb.append("<a href=\"/ward/reports\" class=\"btn btn-teal\" id=\"viewReportsBtn\">View Reports</a>");
        sb.append("</div></div>\n");
        sb.append("</div>\n");

        // Recent reports table (last 5)
        if (!myReports.isEmpty()) {
            sb.append(HtmlHelper.cardOpen("Recent Reports (last 5)"));
            sb.append(buildReportTable(myReports.subList(0, Math.min(5, myReports.size())), user, false));
            sb.append(HtmlHelper.cardClose());
        }

        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildSubmitPage(UserAccount user, String error, String success) {
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Submit Damage Report", user));
        sb.append(HtmlHelper.pageHeading("Submit Damage Report",
                "Report road damage in your ward — " + user.getWardId()));

        if (error   != null) sb.append(HtmlHelper.alertError(error));
        if (success != null) sb.append(HtmlHelper.alertSuccess(success));

        sb.append(HtmlHelper.cardOpen("New Damage Report"));
        sb.append("<form method=\"POST\" action=\"/ward/submit\" id=\"submitReportForm\">\n");

        // Location
        sb.append(HtmlHelper.locationSelect("Location *",
                "locationId", service.getNetwork().getLocationNames()));

        sb.append(HtmlHelper.formField("Road Name *", "roadName", "text",
                "e.g. Anna Nagar Main Road", true));
        sb.append(HtmlHelper.formField("Landmark", "landmark", "text",
                "e.g. Near HDFC Bank, 200m from junction", false));

        // Damage type and equipment
        sb.append(HtmlHelper.formSelectArray("Damage Type *", "damageType",
                new String[]{"Pothole","Resurfacing","Crack Seal","Waterlogging","Other"}, true));
        sb.append(HtmlHelper.formSelectArray("Required Equipment *", "requiredEquipment",
                new String[]{"Pothole Filler","Road Roller","Crack Sealer","Asphalt Spreader",
                             "Safety Cones","Excavator","Other"}, true));

        // Priority ranks
        sb.append("<div class=\"rank-grid\">\n");
        sb.append(HtmlHelper.rankSelect("Severity Rank *",         "severityRank"));
        sb.append(HtmlHelper.rankSelect("Accident Risk Rank *",    "accidentRiskRank"));
        sb.append(HtmlHelper.rankSelect("Road Importance Rank *",  "roadImportanceRank"));
        sb.append(HtmlHelper.rankSelect("Traffic Rank *",          "trafficRank"));
        sb.append("</div>\n");

        sb.append(HtmlHelper.formTextarea("Field Observations",
                "fieldObservations", "Describe what you observed at the site...", false));

        // Ward number and submitter are assigned from session — show as read-only info
        sb.append("<div class=\"alert alert-info\" style=\"margin-bottom:16px;\">"
                + "ℹ️ Report will be submitted as <strong>" + HtmlHelper.escape(user.getDisplayName())
                + "</strong> (Ward " + HtmlHelper.escape(user.getWardId()) + ")"
                + "</div>\n");

        sb.append("<div style=\"display:flex;gap:12px;\">");
        sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"confirmSubmitBtn\">"
                + "Submit Report</button>");
        sb.append("<a href=\"/ward/dashboard\" class=\"btn btn-teal\">Cancel</a>");
        sb.append("</div>");
        sb.append("</form>\n");
        sb.append(HtmlHelper.cardClose());
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildMyReportsPage(UserAccount user) {
        return buildMyReportsPage(user, null);
    }

    private String buildMyReportsPage(UserAccount user, String successMsg) {
        List<RoadDamage> myReports = service.getReportsByWardOfficer(user.getUserId());
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("My Reports", user));
        sb.append(HtmlHelper.pageHeading("My Damage Reports",
                "All reports submitted by " + user.getDisplayName()));

        if (successMsg != null) sb.append(HtmlHelper.alertSuccess(successMsg));

        // Returned reports — needs attention
        List<RoadDamage> returned = new java.util.ArrayList<>();
        for (RoadDamage rd : myReports) {
            if (RoadDamage.STATUS_RETURNED.equals(rd.getStatus())) returned.add(rd);
        }

        if (!returned.isEmpty()) {
            sb.append(HtmlHelper.cardOpen("⚠️ Action Required — Reports Returned for Correction"));
            sb.append(buildReportTable(returned, user, true));
            sb.append(HtmlHelper.cardClose());
        }

        sb.append(HtmlHelper.cardOpen("All My Reports (" + myReports.size() + " total)"));
        if (myReports.isEmpty()) {
            sb.append("<p>No reports submitted yet. <a href=\"/ward/submit\">Submit your first report</a>.</p>\n");
        } else {
            sb.append(buildReportTable(myReports, user, true));
        }
        sb.append(HtmlHelper.cardClose());
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildResubmitPage(UserAccount user, String reportId,
                                      String error, String success) {
        RoadDamage rd = service.getReport(reportId);
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Resubmit Report", user));
        sb.append(HtmlHelper.pageHeading("Resubmit Corrected Report",
                "Address Engineering feedback and resubmit"));

        if (error   != null) sb.append(HtmlHelper.alertError(error));
        if (success != null) sb.append(HtmlHelper.alertSuccess(success));

        if (rd == null) {
            sb.append(HtmlHelper.alertError("Report not found: " + HtmlHelper.escape(reportId)));
            sb.append(HtmlHelper.footer());
            return sb.toString();
        }
        if (!user.getUserId().equals(rd.getSubmittedBy())) {
            sb.append(HtmlHelper.alertError("You can only resubmit your own reports."));
            sb.append(HtmlHelper.footer());
            return sb.toString();
        }
        if (!RoadDamage.STATUS_RETURNED.equals(rd.getStatus())) {
            sb.append(HtmlHelper.alertError("Report " + reportId + " is not in RETURNED status."));
            sb.append(HtmlHelper.footer());
            return sb.toString();
        }

        // Show the return reason
        sb.append(HtmlHelper.alertWarning("Engineering Feedback: " + rd.getReturnReason()));

        sb.append(HtmlHelper.cardOpen("Correct and Resubmit — " + HtmlHelper.escape(reportId)));
        sb.append("<form method=\"POST\" action=\"/ward/resubmit\" id=\"resubmitForm\">\n");
        sb.append("<input type=\"hidden\" name=\"reportId\" value=\""
                + HtmlHelper.escape(rd.getReportId()) + "\">\n");

        sb.append("<p><strong>Location:</strong> "
                + HtmlHelper.escape(rd.getLocationId()) + " — "
                + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId()))
                + " (cannot be changed)</p>\n");
        sb.append("<p><strong>Road:</strong> " + HtmlHelper.escape(rd.getRoadName()) + "</p>\n");

        sb.append(HtmlHelper.formField("Landmark", "landmark", "text",
                rd.getLandmark(), false));

        sb.append("<div class=\"rank-grid\">\n");
        sb.append(HtmlHelper.rankSelectWithValue("Severity Rank *",        "severityRank",       rd.getSeverityRank()));
        sb.append(HtmlHelper.rankSelectWithValue("Accident Risk Rank *",   "accidentRiskRank",   rd.getAccidentRiskRank()));
        sb.append(HtmlHelper.rankSelectWithValue("Road Importance Rank *", "roadImportanceRank", rd.getRoadImportanceRank()));
        sb.append(HtmlHelper.rankSelectWithValue("Traffic Rank *",         "trafficRank",        rd.getTrafficRank()));
        sb.append("</div>\n");

        sb.append("<div class=\"form-group\">\n");
        sb.append("  <label for=\"fieldObservations\">Updated Field Observations *</label>\n");
        sb.append("  <textarea id=\"fieldObservations\" name=\"fieldObservations\" rows=\"4\" required>"
                + HtmlHelper.escape(rd.getFieldObservations()) + "</textarea>\n");
        sb.append("</div>\n");

        sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"resubmitBtn\">Resubmit for Review</button>\n");
        sb.append("</form>\n");
        sb.append(HtmlHelper.cardClose());
        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    // ================================================================
    // TABLE BUILDER
    // ================================================================
    private String buildReportTable(List<RoadDamage> reports, UserAccount user, boolean showActions) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"overflow-x:auto;\">\n");
        sb.append("<table class=\"data-table\">\n<thead><tr>\n");
        sb.append("<th>ID</th><th>Road Name</th><th>Location</th><th>Damage Type</th><th>Severity</th>");
        sb.append("<th>Status</th><th>Submitted</th>");
        if (showActions) sb.append("<th>Actions</th>");
        sb.append("</tr></thead>\n<tbody>\n");

        for (RoadDamage rd : reports) {
            sb.append("<tr>\n");
            sb.append("<td><strong>" + HtmlHelper.escape(rd.getReportId()) + "</strong></td>\n");
            sb.append("<td>" + HtmlHelper.escape(rd.getRoadName()) + "</td>\n");
            sb.append("<td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                    + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>\n");
            sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>\n");
            sb.append("<td>" + HtmlHelper.priorityBadge(rd.getSeverityRank()) + "</td>\n");
            sb.append("<td>" + HtmlHelper.statusBadge(rd.getStatus()) + "</td>\n");
            sb.append("<td>" + HtmlHelper.escape(rd.getSubmissionDate()) + "</td>\n");
            if (showActions) {
                sb.append("<td>");
                if (RoadDamage.STATUS_RETURNED.equals(rd.getStatus())) {
                    sb.append("<a href=\"/ward/resubmit?id="
                            + rd.getReportId() + "\" class=\"btn btn-primary\""
                            + " style=\"padding:4px 10px;font-size:0.8rem;\""
                            + " id=\"resubmit-" + rd.getReportId() + "\">Correct &amp; Resubmit</a>");
                }
                if (rd.getReturnReason() != null && !rd.getReturnReason().isEmpty()) {
                    sb.append("<div style=\"color:#B45309;font-size:0.8rem;margin-top:4px;\">Reason: "
                            + HtmlHelper.escape(rd.getReturnReason()) + "</div>");
                }
                sb.append("</td>\n");
            }
            sb.append("</tr>\n");
        }

        sb.append("</tbody>\n</table>\n</div>\n");
        return sb.toString();
    }

    private String statCard(String icon, String value, String label, String cls) {
        return "<div class=\"stat-card " + cls + "\">"
             + "<div class=\"stat-icon\">" + icon + "</div>"
             + "<div class=\"stat-value\">" + value + "</div>"
             + "<div class=\"stat-label\">" + label + "</div>"
             + "</div>\n";
    }
}
