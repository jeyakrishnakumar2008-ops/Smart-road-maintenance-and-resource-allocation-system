// MaintenanceHandler.java
// Handles all /maintenance/* routes for the Maintenance Team role.
// Routes:
//   /maintenance/dashboard (and /maintenance)
//   /maintenance/job
//   /maintenance/history
//   /maintenance/start    (POST)
//   /maintenance/complete (POST)
//
// Follows strict project guidelines:
// - Server-side authentication & session validation (only ROLE_MAINTENANCE can access)
// - Can only see and manipulate jobs assigned to its specific teamId
// - Pure HTML/CSS, NO JavaScript
// - Integrates Dijkstra shortest path from team location to repair site

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class MaintenanceHandler implements HttpHandler {

    private final MunicipalService service;
    private final SessionManager   sessionManager;

    public MaintenanceHandler(MunicipalService service, SessionManager sessionManager) {
        this.service        = service;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // --- 1. Authentication Check ---
        UserAccount user = HttpUtils.getSessionUser(exchange, sessionManager);
        if (user == null) {
            HttpUtils.redirect(exchange, "/login");
            return;
        }
        if (!user.isMaintenance()) {
            HttpUtils.redirect(exchange, "/login");
            return;
        }

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // --- 2. Action POST routes ---
        if (path.startsWith("/maintenance/start")) {
            if ("POST".equals(method)) {
                handleStart(exchange, user);
            } else {
                HttpUtils.redirect(exchange, "/maintenance/dashboard");
            }
            return;
        }

        if (path.startsWith("/maintenance/complete")) {
            if ("POST".equals(method)) {
                handleComplete(exchange, user);
            } else {
                HttpUtils.redirect(exchange, "/maintenance/dashboard");
            }
            return;
        }

        // --- 3. View routes ---
        if (path.startsWith("/maintenance/job")) {
            HttpUtils.sendHtml(exchange, 200, buildJobPage(user, null, null));
            return;
        }

        if (path.startsWith("/maintenance/history")) {
            HttpUtils.sendHtml(exchange, 200, buildHistoryPage(user));
            return;
        }

        // Default: Team Dashboard
        HttpUtils.sendHtml(exchange, 200, buildDashboardPage(user, null, null));
    }

    // ================================================================
    // POST HANDLERS
    // ================================================================

    private void handleStart(HttpExchange exchange, UserAccount user) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);
        String reportId = p.getOrDefault("reportId", "").trim();

        String error = service.startRepair(reportId, user.getTeamId());
        if (error != null) {
            HttpUtils.sendHtml(exchange, 200, buildDashboardPage(user, null, error));
        } else {
            HttpUtils.sendHtml(exchange, 200,
                    buildDashboardPage(user, "Repair " + reportId + " started. Status is now IN PROGRESS.", null));
        }
    }

    private void handleComplete(HttpExchange exchange, UserAccount user) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);
        String reportId = p.getOrDefault("reportId", "").trim();
        String note     = p.getOrDefault("note", "").trim();

        if (note.isEmpty()) {
            note = "Repair completed successfully as per municipal specifications.";
        }

        String error = service.completeRepairByTeam(reportId, user.getTeamId(), note);
        if (error != null) {
            HttpUtils.sendHtml(exchange, 200, buildDashboardPage(user, null, error));
        } else {
            HttpUtils.sendHtml(exchange, 200,
                    buildDashboardPage(user, "Repair " + reportId + " marked as COMPLETED! Squad is now released and available.", null));
        }
    }

    // ================================================================
    // PAGE BUILDERS
    // ================================================================

    private String buildDashboardPage(UserAccount user, String success, String error) {
        MaintenanceTeam team = service.getTeam(user.getTeamId());
        List<RoadDamage> activeReports = service.getActiveReportsByTeam(user.getTeamId());
        List<RoadDamage> historyReports = service.getCompletedReportsByTeam(user.getTeamId());

        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Team Dashboard", user));
        sb.append(HtmlHelper.pageHeading("Maintenance Dashboard — " + user.getDisplayName(),
                "Real-time dispatch, route navigation, and field repair status"));

        if (success != null) sb.append(HtmlHelper.alertSuccess(success));
        if (error   != null) sb.append(HtmlHelper.alertError(error));

        // Quick Stats row
        sb.append("<div style=\"display:grid;grid-template-columns:repeat(auto-fit, minmax(200px, 1fr));gap:16px;margin-bottom:24px;\">\n");
        sb.append("<div class=\"stat-card\">\n");
        sb.append("<div class=\"stat-label\">Squad ID</div>\n");
        sb.append("<div class=\"stat-val\">").append(HtmlHelper.escape(user.getTeamId())).append("</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"stat-card\">\n");
        sb.append("<div class=\"stat-label\">Current Location</div>\n");
        String locName = team != null ? service.getNetwork().getLocationName(team.getCurrentLocationId()) : "Unknown";
        sb.append("<div class=\"stat-val\" style=\"font-size:18px;\">")
          .append(team != null ? HtmlHelper.escape(team.getCurrentLocationId()) + " — " + HtmlHelper.escape(locName) : "N/A")
          .append("</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"stat-card\">\n");
        sb.append("<div class=\"stat-label\">Squad Status</div>\n");
        String availBadge = (team != null && team.isAvailable())
                ? "<span class=\"badge badge-verified\">AVAILABLE / STANDBY</span>"
                : "<span class=\"badge badge-assigned\">DISPATCHED / ON JOB</span>";
        sb.append("<div class=\"stat-val\" style=\"font-size:16px;margin-top:8px;\">").append(availBadge).append("</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"stat-card\">\n");
        sb.append("<div class=\"stat-label\">Repairs Completed</div>\n");
        sb.append("<div class=\"stat-val\">").append(historyReports.size()).append("</div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");

        // Squad Profile & Equipment Card
        sb.append(HtmlHelper.cardOpen("🛠️ Squad Profile & Equipment Inventory"));
        sb.append("<table class=\"data-table\">\n<tbody>\n");
        sb.append("<tr><td style=\"font-weight:600;width:220px;\">Squad Name</td><td>")
          .append(team != null ? HtmlHelper.escape(team.getTeamName()) : "N/A").append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Base / Stationed At</td><td>")
          .append(team != null ? HtmlHelper.escape(team.getCurrentLocationId()) + " — " + HtmlHelper.escape(locName) : "N/A").append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Equipped With</td><td>")
          .append(team != null ? "<span class=\"badge badge-low\">" + HtmlHelper.escape(team.getEquipmentNames()) + "</span>" : "None").append("</td></tr>\n");
        sb.append("</tbody></table>\n");
        sb.append(HtmlHelper.cardClose());

        // Active Repair Assignment Card
        sb.append(HtmlHelper.cardOpen("🚨 Current Assigned Repair Task"));
        if (activeReports.isEmpty()) {
            sb.append("<div style=\"padding:24px;text-align:center;color:#64748B;\">\n");
            sb.append("<div style=\"font-size:36px;margin-bottom:8px;\">☕</div>\n");
            sb.append("<h3 style=\"margin:0 0 6px 0;color:#0F172A;\">No Active Assignments</h3>\n");
            sb.append("<p style=\"margin:0;font-size:14px;\">Your squad is currently in reserve. When Engineering Section assigns a verified road damage report matching your location and equipment, details will appear here immediately.</p>\n");
            sb.append("</div>\n");
        } else {
            RoadDamage rd = activeReports.get(0);
            renderActiveJobDetail(sb, team, rd);
        }
        sb.append(HtmlHelper.cardClose());

        // Recent Completed Repairs
        sb.append(HtmlHelper.cardOpen("📋 Completed Work History"));
        renderHistoryTable(sb, historyReports);
        sb.append(HtmlHelper.cardClose());

        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private String buildJobPage(UserAccount user, String success, String error) {
        return buildDashboardPage(user, success, error);
    }

    private String buildHistoryPage(UserAccount user) {
        List<RoadDamage> historyReports = service.getCompletedReportsByTeam(user.getTeamId());
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlHelper.header("Work History", user));
        sb.append(HtmlHelper.pageHeading("Maintenance Work History — Squad " + user.getTeamId(),
                "Complete record of road repairs resolved and verified"));

        sb.append(HtmlHelper.cardOpen("Archived Repair Records"));
        renderHistoryTable(sb, historyReports);
        sb.append(HtmlHelper.cardClose());

        sb.append(HtmlHelper.footer());
        return sb.toString();
    }

    private void renderActiveJobDetail(StringBuilder sb, MaintenanceTeam team, RoadDamage rd) {
        // Compute Dijkstra route from team's current location to target site
        String teamLoc = team != null ? team.getCurrentLocationId() : "";
        DijkstraResult route = service.getNetwork().findShortestPath(teamLoc, rd.getLocationId());

        sb.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:20px;\">\n");

        // Column 1: Damage info
        sb.append("<div>\n");
        sb.append("<table class=\"data-table\">\n<tbody>\n");
        sb.append("<tr><td style=\"font-weight:600;width:140px;\">Report ID</td><td><strong>")
          .append(HtmlHelper.escape(rd.getReportId())).append("</strong> ")
          .append(HtmlHelper.statusBadge(rd.getStatus())).append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Road Name</td><td>")
          .append(HtmlHelper.escape(rd.getRoadName())).append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Location</td><td>")
          .append(HtmlHelper.escape(rd.getLocationId())).append(" — ")
          .append(HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId()))).append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Ward Number</td><td>")
          .append(HtmlHelper.escape(rd.getWardNumber())).append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Damage Type</td><td>")
          .append(HtmlHelper.escape(rd.getDamageType())).append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Required Equipment</td><td>")
          .append(HtmlHelper.escape(rd.getRequiredEquipment())).append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Severity / Priority</td><td>")
          .append(HtmlHelper.priorityBadge(rd.getSeverityRank()))
          .append(" (Sev: ").append(rd.getSeverityRank())
          .append(", Risk: ").append(rd.getAccidentRiskRank()).append(")</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Landmark</td><td>")
          .append(HtmlHelper.escape(rd.getLandmark())).append("</td></tr>\n");
        sb.append("<tr><td style=\"font-weight:600;\">Field Notes</td><td>")
          .append(HtmlHelper.escape(rd.getFieldObservations())).append("</td></tr>\n");
        sb.append("</tbody></table>\n");
        sb.append("</div>\n");

        // Column 2: Navigation & Route Card
        sb.append("<div>\n");
        sb.append("<div style=\"background:#EFF6FF;border:1px solid #BFDBFE;border-radius:8px;padding:16px;\">\n");
        sb.append("<h4 style=\"margin:0 0 10px 0;color:#1E40AF;\">🗺️ Dijkstra Recommended Route</h4>\n");
        if (route.isReachable()) {
            sb.append("<p style=\"margin:0 0 8px 0;font-size:14px;color:#1E3A8A;\"><strong>Route:</strong> ")
              .append(HtmlHelper.escape(route.getPathString())).append("</p>\n");
            sb.append("<p style=\"margin:0 0 12px 0;font-size:14px;color:#1E3A8A;\"><strong>Est. Travel Time:</strong> ")
              .append("<span style=\"font-size:18px;font-weight:700;color:#2563EB;\">").append(route.getTotalTime())
              .append(" minutes</span></p>\n");
        } else {
            sb.append("<p style=\"margin:0;color:#DC2626;\">⚠️ Destination is currently unreachable due to active road closures. Contact Engineering Section.</p>\n");
        }
        sb.append("</div>\n");

        // Actions box
        sb.append("<div style=\"margin-top:16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:8px;padding:16px;\">\n");
        sb.append("<h4 style=\"margin:0 0 12px 0;color:#0F172A;\">⚙️ Squad Action Controls</h4>\n");

        if (RoadDamage.STATUS_ASSIGNED.equals(rd.getStatus())) {
            sb.append("<form method=\"POST\" action=\"/maintenance/start\">\n");
            sb.append("<input type=\"hidden\" name=\"reportId\" value=\"").append(HtmlHelper.escape(rd.getReportId())).append("\">\n");
            sb.append("<p style=\"font-size:13px;color:#64748B;margin:0 0 10px 0;\">Click below when squad reaches the destination site and commences maintenance.</p>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-primary\" style=\"width:100%;padding:10px;font-size:15px;\">▶️ Start Repair Work (Mark IN PROGRESS)</button>\n");
            sb.append("</form>\n");
        } else if (RoadDamage.STATUS_IN_PROGRESS.equals(rd.getStatus())) {
            sb.append("<form method=\"POST\" action=\"/maintenance/complete\">\n");
            sb.append("<input type=\"hidden\" name=\"reportId\" value=\"").append(HtmlHelper.escape(rd.getReportId())).append("\">\n");
            sb.append("<div class=\"form-group\" style=\"margin-bottom:10px;\">\n");
            sb.append("<label for=\"note\" style=\"font-size:13px;font-weight:600;\">Completion Note / Work Outcome:</label>\n");
            sb.append("<textarea id=\"note\" name=\"note\" rows=\"2\" class=\"form-input\" style=\"width:100%;margin-top:4px;\" placeholder=\"e.g., Pothole filled with cold mix asphalt and rolled smooth.\" required></textarea>\n");
            sb.append("</div>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-success\" style=\"width:100%;padding:10px;font-size:15px;background:#16A34A;color:white;border:none;border-radius:6px;cursor:pointer;\">✅ Complete Repair &amp; Release Squad</button>\n");
            sb.append("</form>\n");
        }
        sb.append("</div>\n");

        sb.append("</div>\n"); // end column 2
        sb.append("</div>\n"); // end grid
    }

    private void renderHistoryTable(StringBuilder sb, List<RoadDamage> historyReports) {
        if (historyReports.isEmpty()) {
            sb.append("<p style=\"color:#64748B;padding:16px 0;text-align:center;\">No repairs logged in completion history yet.</p>\n");
            return;
        }
        sb.append("<div style=\"overflow-x:auto;\">\n");
        sb.append("<table class=\"data-table\">\n");
        sb.append("<thead><tr>\n");
        sb.append("<th>ID</th><th>Road Name</th><th>Location</th><th>Damage Type</th><th>Completed At</th><th>Work Outcome Note</th><th>Status</th>\n");
        sb.append("</tr></thead>\n<tbody>\n");

        for (RoadDamage rd : historyReports) {
            sb.append("<tr>\n");
            sb.append("<td><strong>").append(HtmlHelper.escape(rd.getReportId())).append("</strong></td>\n");
            sb.append("<td>").append(HtmlHelper.escape(rd.getRoadName())).append("</td>\n");
            sb.append("<td>").append(HtmlHelper.escape(rd.getLocationId())).append(" — ")
              .append(HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId()))).append("</td>\n");
            sb.append("<td>").append(HtmlHelper.escape(rd.getDamageType())).append("</td>\n");
            sb.append("<td>").append(HtmlHelper.escape(rd.getCompletionDate())).append("</td>\n");
            sb.append("<td><em>").append(HtmlHelper.escape(rd.getCompletionNote())).append("</em></td>\n");
            sb.append("<td>").append(HtmlHelper.statusBadge(rd.getStatus())).append("</td>\n");
            sb.append("</tr>\n");
        }

        sb.append("</tbody></table>\n</div>\n");
    }
}
