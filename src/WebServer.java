// WebServer.java
// Uses Java's built-in com.sun.net.httpserver package to serve the web application.
// All HTTP GET requests return HTML pages built by Java.
// All HTTP POST requests process form submissions and redirect to a result page.
//
// NO JavaScript is used anywhere. All logic runs in Java.
// A full page reload occurs after every form submission.

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Paths;

public class WebServer {

    private MunicipalService service;
    private SessionManager   sessionManager;
    private int port;

    public WebServer(MunicipalService service, int port) {
        this.service        = service;
        this.port           = port;
        this.sessionManager = new SessionManager();
    }

    // Starts the HTTP server and registers URL handlers
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Role-based modular handlers
        LoginHandler loginHandler             = new LoginHandler(sessionManager);
        SignupHandler signupHandler           = new SignupHandler(sessionManager);
        WardHandler wardHandler               = new WardHandler(service, sessionManager);
        EngineeringHandler engineeringHandler = new EngineeringHandler(service, sessionManager);
        MaintenanceHandler maintenanceHandler = new MaintenanceHandler(service, sessionManager);

        server.createContext("/login",       loginHandler);
        server.createContext("/logout",      loginHandler);
        server.createContext("/signup",      signupHandler);
        server.createContext("/ward",        wardHandler);
        server.createContext("/engineering", engineeringHandler);
        server.createContext("/maintenance", maintenanceHandler);

        // Core / backwards-compatible handlers
        server.createContext("/",          new DashboardHandler());
        server.createContext("/reports",   new ReportsHandler());
        server.createContext("/priority",  new PriorityHandler());
        server.createContext("/teams",     new TeamsHandler());
        server.createContext("/network",   new NetworkHandler());
        server.createContext("/assign",    new AssignHandler());
        server.createContext("/complete",  new CompleteHandler());
        server.createContext("/style.css", new CssHandler());

        server.setExecutor(null); // Use default Java executor
        server.start();
        System.out.println("✅ Server started at http://localhost:" + port);
        System.out.println("   Open your browser and go to: http://localhost:" + port);
        System.out.println("   Press Ctrl+C to stop the server.");
    }

    // ---- Utility: parse URL query parameters ----
    // Parses strings like "key1=value1&key2=value2" into a HashMap
    private static HashMap<String, String> parseParams(String query) {
        HashMap<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(decode(kv[0]), decode(kv[1]));
            } else if (kv.length == 1) {
                params.put(decode(kv[0]), "");
            }
        }
        return params;
    }

    // Decodes URL-encoded characters (e.g., %20 → space, + → space)
    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ---- Utility: read POST body as a string ----
    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    // ---- Utility: send an HTML response ----
    private static void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    // ---- Utility: redirect to another page ----
    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }

    // ---- Safe integer parsing ----
    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.trim().isEmpty()) return defaultValue;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // ================================================================
    // HANDLER: Dashboard (GET /)
    // ================================================================
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1); return;
            }

            // Check if user has an active session
            UserAccount user = HttpUtils.getSessionUser(exchange, sessionManager);
            if (user != null) {
                if (user.isWardOfficer()) {
                    HttpUtils.redirect(exchange, "/ward/dashboard");
                    return;
                } else if (user.isEngineering()) {
                    HttpUtils.redirect(exchange, "/engineering/dashboard");
                    return;
                } else if (user.isMaintenance()) {
                    HttpUtils.redirect(exchange, "/maintenance/dashboard");
                    return;
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(HtmlHelper.header("Municipal Operations Portal", null));
            sb.append(HtmlHelper.pageHeading("Senthamizh Nagar Municipal Corporation",
                    "Smart Municipal Road Maintenance & Resource Allocation System"));

            // 1. Role Sign-In Options (Three Separate Roles)
            sb.append("<div style=\"margin-bottom:28px;\">\n");
            sb.append("<h2 style=\"font-size:1.15rem;font-weight:700;color:#0F172A;margin-bottom:14px;display:flex;align-items:center;gap:8px;\">"
                    + "🔐 Select Your Role to Sign In</h2>\n");
            sb.append("<div style=\"display:grid;grid-template-columns:repeat(auto-fit, minmax(280px, 1fr));gap:20px;\">\n");

            // Ward Officer Card
            sb.append("<div class=\"card\" style=\"border-top:4px solid #2563EB;padding:22px;display:flex;flex-direction:column;\">\n");
            sb.append("<div style=\"font-size:36px;margin-bottom:10px;\">👷</div>\n");
            sb.append("<h3 style=\"margin:0 0 6px 0;color:#1E293B;font-size:1.1rem;\">Ward Officer</h3>\n");
            sb.append("<p style=\"color:#64748B;font-size:0.875rem;margin-bottom:14px;line-height:1.5;flex:1;\">"
                    + "Inspect road damages, log pothole reports, provide severity &amp; accident risks, and resubmit corrected issues.</p>\n");
            sb.append("<div style=\"font-size:0.75rem;color:#475569;margin-bottom:14px;background:#EFF6FF;border:1px solid #DBEAFE;padding:6px 10px;border-radius:6px;\">"
                    + "<strong>Demo:</strong> <code>ward1</code> / <code>ward123</code></div>\n");
            sb.append("<div style=\"display:flex;gap:10px;\">\n");
            sb.append("<a href=\"/login/ward\" class=\"btn btn-primary\" style=\"flex:1;text-align:center;text-decoration:none;\">Sign In →</a>\n");
            sb.append("<a href=\"/signup/ward\" class=\"btn\" style=\"flex:1;text-align:center;text-decoration:none;background:#EFF6FF;color:#2563EB;border:1px solid #BFDBFE;\">Sign Up</a>\n");
            sb.append("</div>\n");
            sb.append("</div>\n");

            // Engineering Section Card
            sb.append("<div class=\"card\" style=\"border-top:4px solid #4F46E5;padding:22px;display:flex;flex-direction:column;\">\n");
            sb.append("<div style=\"font-size:36px;margin-bottom:10px;\">🏛️</div>\n");
            sb.append("<h3 style=\"margin:0 0 6px 0;color:#1E293B;font-size:1.1rem;\">Engineering Section</h3>\n");
            sb.append("<p style=\"color:#64748B;font-size:0.875rem;margin-bottom:14px;line-height:1.5;flex:1;\">"
                    + "Verify damage reports, adjust priority values, compute Dijkstra routes, manage road closures, and allocate squads.</p>\n");
            sb.append("<div style=\"font-size:0.75rem;color:#475569;margin-bottom:14px;background:#EEF2FF;border:1px solid #E0E7FF;padding:6px 10px;border-radius:6px;\">"
                    + "<strong>Demo:</strong> <code>eng1</code> / <code>eng123</code></div>\n");
            sb.append("<div style=\"display:flex;gap:10px;\">\n");
            sb.append("<a href=\"/login/engineering\" class=\"btn btn-primary\" style=\"flex:1;text-align:center;text-decoration:none;background:#4F46E5;\">Sign In →</a>\n");
            sb.append("<a href=\"/signup/engineering\" class=\"btn\" style=\"flex:1;text-align:center;text-decoration:none;background:#EEF2FF;color:#4F46E5;border:1px solid #C7D2FE;\">Sign Up</a>\n");
            sb.append("</div>\n");
            sb.append("</div>\n");

            // Maintenance Team Card
            sb.append("<div class=\"card\" style=\"border-top:4px solid #0D9488;padding:22px;display:flex;flex-direction:column;\">\n");
            sb.append("<div style=\"font-size:36px;margin-bottom:10px;\">🛠️</div>\n");
            sb.append("<h3 style=\"margin:0 0 6px 0;color:#1E293B;font-size:1.1rem;\">Maintenance Team</h3>\n");
            sb.append("<p style=\"color:#64748B;font-size:0.875rem;margin-bottom:14px;line-height:1.5;flex:1;\">"
                    + "Dispatched squads (T1/T2/T3), turn-by-turn navigation, start field repair, and report work completion.</p>\n");
            sb.append("<div style=\"font-size:0.75rem;color:#475569;margin-bottom:14px;background:#F0FDFA;border:1px solid #CCFBF1;padding:6px 10px;border-radius:6px;\">"
                    + "<strong>Demo:</strong> <code>T1</code> / <code>team123</code></div>\n");
            sb.append("<div style=\"display:flex;gap:10px;\">\n");
            sb.append("<a href=\"/login/maintenance\" class=\"btn btn-primary\" style=\"flex:1;text-align:center;text-decoration:none;background:#0D9488;\">Sign In →</a>\n");
            sb.append("<a href=\"/signup/maintenance\" class=\"btn\" style=\"flex:1;text-align:center;text-decoration:none;background:#F0FDFA;color:#0D9488;border:1px solid #99F6E4;\">Sign Up</a>\n");
            sb.append("</div>\n");
            sb.append("</div>\n");

            sb.append("</div>\n</div>\n");

            // 2. Municipal Statistics Overview
            sb.append("<h2 style=\"font-size:1.15rem;font-weight:700;color:#0F172A;margin-bottom:14px;\">📊 City Maintenance Overview</h2>\n");
            sb.append("<div class=\"stat-grid\">\n");
            sb.append(statCard("📋", "Total Reports",    service.getTotalReports(),    "stat-blue"));
            sb.append(statCard("⏳", "Pending Repairs",  service.getPendingCount(),    "stat-orange"));
            sb.append(statCard("🔧", "Assigned Repairs", service.getAssignedCount(),   "stat-teal"));
            sb.append(statCard("✅", "Completed Repairs",service.getCompletedCount(),  "stat-green"));
            sb.append(statCard("👷", "Available Teams",  service.getAvailableTeamCount(), "stat-purple"));
            sb.append("</div>\n");

            // 3. Quick-action cards (Backwards Compatibility)
            sb.append("<div class=\"card-grid\">\n");
            sb.append(quickAction("⚡ Engineering Center",
                    "Manage priority queue, allocate teams, and monitor road closures",
                    "/login/engineering", "btn btn-primary"));
            sb.append(quickAction("📋 Field Inspection",
                    "Submit new road damage reports or check ward status",
                    "/login/ward", "btn btn-teal"));
            sb.append(quickAction("👷 Team Dispatch",
                    "View maintenance squads and live repair assignments",
                    "/login/maintenance", "btn btn-primary"));
            sb.append(quickAction("🗺️ Road Network",
                    "View weighted road graph connections and closures",
                    "/network", "btn btn-teal"));
            sb.append("</div>\n");

            // 4. System Info
            sb.append(HtmlHelper.cardOpen("ℹ️ Municipal System Information"));
            sb.append("<p>Senthamizh Nagar Municipal Corporation — Road Maintenance &amp; Resource Allocation System</p>");
            sb.append("<p>Teams: <strong>" + service.getAllTeams().size() + "</strong> &nbsp;|&nbsp; ");
            sb.append("Locations: <strong>" + service.getNetwork().getLocations().size() + "</strong> &nbsp;|&nbsp; ");
            sb.append("Technology: <strong>Java (Zero JavaScript)</strong> &nbsp;|&nbsp; In-memory data store</p>");
            sb.append(HtmlHelper.cardClose());

            sb.append(HtmlHelper.footer());
            sendHtml(exchange, 200, sb.toString());
        }

        private String statCard(String icon, String label, int value, String cssClass) {
            return "<div class=\"stat-card " + cssClass + "\">\n"
                 + "  <div class=\"stat-icon\">" + icon + "</div>\n"
                 + "  <div class=\"stat-value\">" + value + "</div>\n"
                 + "  <div class=\"stat-label\">" + HtmlHelper.escape(label) + "</div>\n"
                 + "</div>\n";
        }

        private String quickAction(String title, String description, String href, String btnClass) {
            return "<div class=\"card\">\n"
                 + "  <div class=\"card-body\" style=\"text-align:center; padding: 24px;\">\n"
                 + "    <h3 style=\"margin:0 0 8px 0; color:#1E293B;\">" + title + "</h3>\n"
                 + "    <p style=\"color:#64748B; margin:0 0 16px 0; font-size:0.875rem;\">" + description + "</p>\n"
                 + "    <a href=\"" + href + "\" class=\"" + btnClass + "\">Go</a>\n"
                 + "  </div>\n"
                 + "</div>\n";
        }
    }

    // ================================================================
    // HANDLER: Damage Reports (GET/POST /reports)
    // ================================================================
    private class ReportsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if ("POST".equals(method)) {
                handlePost(exchange);
            } else {
                handleGet(exchange, null, null);
            }
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            HashMap<String, String> params = parseParams(body);
            String action = params.getOrDefault("action", "add");

            String message = null;
            boolean success = false;

            if ("add".equals(action)) {
                String reportId   = params.getOrDefault("reportId", "").trim();
                String locationId = params.getOrDefault("locationId", "").trim();
                String damageType = params.getOrDefault("damageType", "").trim();
                int sev  = parseInt(params.get("severityRank"), 0);
                int acc  = parseInt(params.get("accidentRiskRank"), 0);
                int imp  = parseInt(params.get("roadImportanceRank"), 0);
                int trf  = parseInt(params.get("trafficRank"), 0);
                String eq = params.getOrDefault("requiredEquipment", "").trim();

                String error = service.addNewReport(reportId, locationId, damageType, sev, acc, imp, trf, eq);
                if (error != null) { message = error; success = false; }
                else               { message = "Report " + reportId + " added successfully."; success = true; }

            } else if ("update".equals(action)) {
                String reportId = params.getOrDefault("reportId", "").trim();
                int sev = parseInt(params.get("severityRank"), 0);
                int acc = parseInt(params.get("accidentRiskRank"), 0);
                int imp = parseInt(params.get("roadImportanceRank"), 0);
                int trf = parseInt(params.get("trafficRank"), 0);

                String error = service.updateReport(reportId, sev, acc, imp, trf);
                if (error != null) { message = error; success = false; }
                else               { message = "Report " + reportId + " updated and re-prioritized."; success = true; }
            }

            handleGet(exchange, message, success);
        }

        private void handleGet(HttpExchange exchange, String message, Boolean success) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append(HtmlHelper.header("Damage Reports"));
            sb.append(HtmlHelper.pageHeading("Damage Reports",
                    "All road-damage reports submitted to Senthamizh Nagar municipality"));

            // Show result message if present
            if (message != null) {
                sb.append(success ? HtmlHelper.alertSuccess(message) : HtmlHelper.alertError(message));
            }

            // ---- Add new report form ----
            sb.append(HtmlHelper.cardOpen("➕ Add New Damage Report"));
            sb.append("<form method=\"POST\" action=\"/reports\">\n");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"add\">\n");
            sb.append("<div class=\"form-grid\">\n");
            sb.append(formField("Report ID", "reportId", "text", "e.g. R023", true));
            sb.append(formFieldSelect("Location", "locationId", service.getNetwork().getLocationNames()));
            sb.append(formFieldSelect2("Damage Type", "damageType",
                    new String[]{"Pothole", "Resurfacing", "Crack Seal", "Drainage Issue", "Edge Damage"}));
            sb.append(formFieldSelect2("Required Equipment", "requiredEquipment",
                    new String[]{"Pothole Filler", "Road Roller", "Asphalt Spreader", "Crack Sealer", "Safety Cones", "Excavator"}));
            sb.append(rankField("Severity Rank (1–3)", "severityRank"));
            sb.append(rankField("Accident Risk Rank (1–3)", "accidentRiskRank"));
            sb.append(rankField("Road Importance Rank (1–3)", "roadImportanceRank"));
            sb.append(rankField("Traffic Rank (1–3)", "trafficRank"));
            sb.append("</div>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"addReportBtn\">Add Report</button>\n");
            sb.append("</form>\n");
            sb.append(HtmlHelper.cardClose());

            // ---- Update report form ----
            sb.append(HtmlHelper.cardOpen("✏️ Update a Pending Report"));
            sb.append("<form method=\"POST\" action=\"/reports\">\n");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"update\">\n");
            sb.append("<div class=\"form-grid\">\n");
            sb.append(formField("Report ID to Update", "reportId", "text", "e.g. R010", true));
            sb.append(rankField("New Severity Rank (1–3)", "severityRank"));
            sb.append(rankField("New Accident Risk Rank (1–3)", "accidentRiskRank"));
            sb.append(rankField("New Road Importance Rank (1–3)", "roadImportanceRank"));
            sb.append(rankField("New Traffic Rank (1–3)", "trafficRank"));
            sb.append("</div>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-teal\" id=\"updateReportBtn\">Update Report</button>\n");
            sb.append("</form>\n");
            sb.append(HtmlHelper.cardClose());

            // ---- Reports table ----
            sb.append(HtmlHelper.cardOpen("📋 All Reports (" + service.getTotalReports() + ")"));
            sb.append("<div class=\"table-wrapper\">\n");
            sb.append("<table class=\"data-table\">\n");
            sb.append("<thead><tr>");
            sb.append("<th>Report ID</th><th>Location</th><th>Damage Type</th>");
            sb.append("<th>Sev</th><th>Acc</th><th>Imp</th><th>Trf</th>");
            sb.append("<th>Required Equipment</th><th>Status</th><th>Team</th>");
            sb.append("</tr></thead>\n<tbody>\n");

            List<RoadDamage> reports = service.getAllReports();
            // Sort by report ID for consistent display
            reports.sort((a, b) -> a.getReportId().compareTo(b.getReportId()));

            for (RoadDamage rd : reports) {
                sb.append("<tr>");
                sb.append("<td><strong>" + HtmlHelper.escape(rd.getReportId()) + "</strong></td>");
                sb.append("<td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                        + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>");
                sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>");
                sb.append("<td>" + rd.getSeverityRank() + "</td>");
                sb.append("<td>" + rd.getAccidentRiskRank() + "</td>");
                sb.append("<td>" + rd.getRoadImportanceRank() + "</td>");
                sb.append("<td>" + rd.getTrafficRank() + "</td>");
                sb.append("<td>" + HtmlHelper.escape(rd.getRequiredEquipment()) + "</td>");
                sb.append("<td>" + HtmlHelper.statusBadge(rd.getStatus()) + "</td>");
                sb.append("<td>" + (rd.getAssignedTeamId() != null ? HtmlHelper.escape(rd.getAssignedTeamId()) : "—") + "</td>");
                sb.append("</tr>\n");
            }
            sb.append("</tbody></table>\n</div>\n");
            sb.append(HtmlHelper.cardClose());

            sb.append(HtmlHelper.footer());
            sendHtml(exchange, 200, sb.toString());
        }

        // Helper: builds a text form field
        private String formField(String label, String name, String type, String placeholder, boolean required) {
            return "<div class=\"form-group\">\n"
                 + "  <label for=\"" + name + "\">" + HtmlHelper.escape(label) + "</label>\n"
                 + "  <input type=\"" + type + "\" id=\"" + name + "\" name=\"" + name + "\""
                 + " placeholder=\"" + HtmlHelper.escape(placeholder) + "\""
                 + (required ? " required" : "") + ">\n"
                 + "</div>\n";
        }

        // Helper: builds a select field for location names
        private String formFieldSelect(String label, String name, java.util.HashMap<String, String> options) {
            StringBuilder sb = new StringBuilder();
            sb.append("<div class=\"form-group\">\n");
            sb.append("  <label for=\"" + name + "\">" + HtmlHelper.escape(label) + "</label>\n");
            sb.append("  <select id=\"" + name + "\" name=\"" + name + "\" required>\n");
            // Sort location IDs
            List<String> ids = new java.util.ArrayList<>(options.keySet());
            ids.sort(null);
            for (String id : ids) {
                sb.append("    <option value=\"" + HtmlHelper.escape(id) + "\">"
                        + HtmlHelper.escape(id) + " — " + HtmlHelper.escape(options.get(id))
                        + "</option>\n");
            }
            sb.append("  </select>\n</div>\n");
            return sb.toString();
        }

        // Helper: builds a select field for a plain string array
        private String formFieldSelect2(String label, String name, String[] options) {
            StringBuilder sb = new StringBuilder();
            sb.append("<div class=\"form-group\">\n");
            sb.append("  <label for=\"" + name + "\">" + HtmlHelper.escape(label) + "</label>\n");
            sb.append("  <select id=\"" + name + "\" name=\"" + name + "\" required>\n");
            for (String opt : options) {
                sb.append("    <option value=\"" + HtmlHelper.escape(opt) + "\">"
                        + HtmlHelper.escape(opt) + "</option>\n");
            }
            sb.append("  </select>\n</div>\n");
            return sb.toString();
        }

        // Helper: builds a 1–3 rank select field
        private String rankField(String label, String name) {
            return "<div class=\"form-group\">\n"
                 + "  <label for=\"" + name + "\">" + HtmlHelper.escape(label) + "</label>\n"
                 + "  <select id=\"" + name + "\" name=\"" + name + "\" required>\n"
                 + "    <option value=\"3\">3 — High</option>\n"
                 + "    <option value=\"2\">2 — Medium</option>\n"
                 + "    <option value=\"1\">1 — Low</option>\n"
                 + "  </select>\n"
                 + "</div>\n";
        }
    }

    // ================================================================
    // HANDLER: Priority Queue (GET /priority)
    // ================================================================
    private class PriorityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append(HtmlHelper.header("Priority Queue"));
            sb.append(HtmlHelper.pageHeading("Priority Queue",
                    "Waiting repairs ranked by severity → accident risk → road importance → traffic → report order"));

            sb.append(HtmlHelper.cardOpen("ℹ️ How Priority Works"));
            sb.append("<p>Reports are ordered using a <strong>5-step comparison</strong>:</p>");
            sb.append("<ol>");
            sb.append("<li><strong>Step 1:</strong> Higher <em>Severity Rank</em> comes first (3 &gt; 2 &gt; 1).</li>");
            sb.append("<li><strong>Step 2:</strong> Tie? Higher <em>Accident Risk Rank</em> wins.</li>");
            sb.append("<li><strong>Step 3:</strong> Still tied? Higher <em>Road Importance Rank</em> wins.</li>");
            sb.append("<li><strong>Step 4:</strong> Still tied? Higher <em>Traffic Rank</em> wins.</li>");
            sb.append("<li><strong>Step 5:</strong> Complete tie? Earlier <em>Report Order</em> wins (lower number submitted first).</li>");
            sb.append("</ol>");
            sb.append(HtmlHelper.cardClose());

            List<RoadDamage> pending = service.getPendingReportsOrdered();

            sb.append(HtmlHelper.cardOpen("⏳ Pending Queue (" + pending.size() + " reports)"));
            if (pending.isEmpty()) {
                sb.append(HtmlHelper.alertInfo("No reports are currently pending."));
            } else {
                sb.append("<div class=\"table-wrapper\">\n");
                sb.append("<table class=\"data-table\">\n");
                sb.append("<thead><tr>");
                sb.append("<th>Rank</th><th>Report ID</th><th>Location</th><th>Damage Type</th>");
                sb.append("<th>Severity</th><th>Acc.Risk</th><th>Rd.Imp</th><th>Traffic</th>");
                sb.append("<th>Order#</th><th>Equipment Needed</th><th>Priority Level</th>");
                sb.append("</tr></thead>\n<tbody>\n");

                int rank = 1;
                for (RoadDamage rd : pending) {
                    sb.append("<tr>");
                    sb.append("<td><strong>#" + rank++ + "</strong></td>");
                    sb.append("<td><strong>" + HtmlHelper.escape(rd.getReportId()) + "</strong></td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                            + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>");
                    sb.append("<td style=\"text-align:center\">" + rd.getSeverityRank() + "</td>");
                    sb.append("<td style=\"text-align:center\">" + rd.getAccidentRiskRank() + "</td>");
                    sb.append("<td style=\"text-align:center\">" + rd.getRoadImportanceRank() + "</td>");
                    sb.append("<td style=\"text-align:center\">" + rd.getTrafficRank() + "</td>");
                    sb.append("<td style=\"text-align:center\">" + rd.getReportOrder() + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getRequiredEquipment()) + "</td>");
                    sb.append("<td>" + HtmlHelper.priorityBadge(rd.getSeverityRank()) + "</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</tbody></table>\n</div>\n");
            }
            sb.append(HtmlHelper.cardClose());

            sb.append(HtmlHelper.footer());
            sendHtml(exchange, 200, sb.toString());
        }
    }

    // ================================================================
    // HANDLER: Maintenance Teams (GET /teams)
    // ================================================================
    private class TeamsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append(HtmlHelper.header("Maintenance Teams"));
            sb.append(HtmlHelper.pageHeading("Maintenance Teams",
                    "Current status, equipment and assignments of all maintenance crews"));

            List<MaintenanceTeam> teams = service.getAllTeams();
            teams.sort((a, b) -> a.getTeamId().compareTo(b.getTeamId()));

            for (MaintenanceTeam team : teams) {
                sb.append("<div class=\"card team-card\">\n");
                sb.append("<div class=\"team-header\">\n");
                sb.append("<div class=\"team-id\">" + HtmlHelper.escape(team.getTeamId()) + "</div>\n");
                sb.append("<div class=\"team-name\">" + HtmlHelper.escape(team.getTeamName()) + "</div>\n");
                sb.append("<div>" + (team.isAvailable()
                        ? "<span class=\"badge badge-completed\">✅ AVAILABLE</span>"
                        : "<span class=\"badge badge-assigned\">🔧 BUSY</span>") + "</div>\n");
                sb.append("</div>\n");
                sb.append("<div class=\"team-details\">\n");
                sb.append("<p><strong>📍 Current Location:</strong> "
                        + HtmlHelper.escape(team.getCurrentLocationId()) + " — "
                        + HtmlHelper.escape(service.getNetwork().getLocationName(team.getCurrentLocationId()))
                        + "</p>\n");
                sb.append("<p><strong>🛠️ Equipment:</strong> "
                        + HtmlHelper.escape(team.getEquipmentNames()) + "</p>\n");
                if (!team.isAvailable() && team.getCurrentAssignmentId() != null) {
                    RoadDamage rd = service.getReport(team.getCurrentAssignmentId());
                    sb.append("<p><strong>📋 Current Assignment:</strong> "
                            + HtmlHelper.escape(team.getCurrentAssignmentId()));
                    if (rd != null) {
                        sb.append(" — " + HtmlHelper.escape(rd.getDamageType())
                                + " at " + HtmlHelper.escape(rd.getLocationId()));
                    }
                    sb.append("</p>\n");
                }
                sb.append("</div>\n</div>\n");
            }

            sb.append(HtmlHelper.footer());
            sendHtml(exchange, 200, sb.toString());
        }
    }

    // ================================================================
    // HANDLER: Road Network (GET/POST /network)
    // ================================================================
    private class NetworkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String message = null;
            boolean success = false;

            if ("POST".equals(method)) {
                String body = readBody(exchange);
                HashMap<String, String> params = parseParams(body);
                String action = params.getOrDefault("action", "");
                String src    = params.getOrDefault("sourceId", "").trim();
                String dst    = params.getOrDefault("destinationId", "").trim();

                if ("close".equals(action)) {
                    boolean ok = service.closeRoad(src, dst);
                    if (ok) { message = "Road " + src + " → " + dst + " closed."; success = true; }
                    else    { message = "Road " + src + " → " + dst + " not found."; }
                } else if ("reopen".equals(action)) {
                    boolean ok = service.reopenRoad(src, dst);
                    if (ok) { message = "Road " + src + " → " + dst + " reopened."; success = true; }
                    else    { message = "Road " + src + " → " + dst + " not found."; }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(HtmlHelper.header("Road Network"));
            sb.append(HtmlHelper.pageHeading("Road Network",
                    "All road connections in Senthamizh Nagar with travel times and status"));

            if (message != null) {
                sb.append(success ? HtmlHelper.alertSuccess(message) : HtmlHelper.alertError(message));
            }

            // Close road form
            sb.append(HtmlHelper.cardOpen("🚧 Manage Road Status"));
            sb.append("<div class=\"form-row\">\n");

            sb.append("<form method=\"POST\" action=\"/network\" style=\"display:inline-block; margin-right:16px;\">\n");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"close\">\n");
            sb.append("<label>From: <select name=\"sourceId\">" + locationOptions() + "</select></label>\n");
            sb.append("<label>To: <select name=\"destinationId\">" + locationOptions() + "</select></label>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-danger\" id=\"closeRoadBtn\">Close Road</button>\n");
            sb.append("</form>\n");

            sb.append("<form method=\"POST\" action=\"/network\" style=\"display:inline-block;\">\n");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"reopen\">\n");
            sb.append("<label>From: <select name=\"sourceId\">" + locationOptions() + "</select></label>\n");
            sb.append("<label>To: <select name=\"destinationId\">" + locationOptions() + "</select></label>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-teal\" id=\"reopenRoadBtn\">Reopen Road</button>\n");
            sb.append("</form>\n");

            sb.append("</div>\n");
            sb.append(HtmlHelper.cardClose());

            // Road connections table
            sb.append(HtmlHelper.cardOpen("🗺️ All Road Connections"));
            sb.append("<div class=\"table-wrapper\">\n");
            sb.append("<table class=\"data-table\">\n");
            sb.append("<thead><tr><th>From</th><th>To</th><th>Travel Time (min)</th><th>Status</th></tr></thead>\n");
            sb.append("<tbody>\n");

            java.util.HashMap<String, java.util.List<RoadConnection>> adj =
                    service.getNetwork().getAdjacencyList();

            List<String> sources = new java.util.ArrayList<>(adj.keySet());
            sources.sort(null);

            for (String src : sources) {
                List<RoadConnection> conns = adj.get(src);
                for (RoadConnection rc : conns) {
                    String statusClass = rc.isOpen() ? "badge badge-completed" : "badge badge-high";
                    String statusText  = rc.isOpen() ? "OPEN" : "CLOSED";
                    sb.append("<tr>");
                    sb.append("<td>" + HtmlHelper.escape(src) + " — "
                            + HtmlHelper.escape(service.getNetwork().getLocationName(src)) + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rc.getDestinationId()) + " — "
                            + HtmlHelper.escape(service.getNetwork().getLocationName(rc.getDestinationId())) + "</td>");
                    sb.append("<td style=\"text-align:center\">" + rc.getTravelTime() + " min</td>");
                    sb.append("<td><span class=\"" + statusClass + "\">" + statusText + "</span></td>");
                    sb.append("</tr>\n");
                }
            }
            sb.append("</tbody></table>\n</div>\n");
            sb.append(HtmlHelper.cardClose());

            sb.append(HtmlHelper.footer());
            sendHtml(exchange, 200, sb.toString());
        }

        private String locationOptions() {
            java.util.HashMap<String, String> names = service.getNetwork().getLocationNames();
            List<String> ids = new java.util.ArrayList<>(names.keySet());
            ids.sort(null);
            StringBuilder sb = new StringBuilder();
            for (String id : ids) {
                sb.append("<option value=\"" + HtmlHelper.escape(id) + "\">"
                        + HtmlHelper.escape(id) + " — " + HtmlHelper.escape(names.get(id))
                        + "</option>\n");
            }
            return sb.toString();
        }
    }

    // ================================================================
    // HANDLER: Assign & Route (GET/POST /assign)
    // ================================================================
    private class AssignHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            AssignmentResult result = null;

            if ("POST".equals(exchange.getRequestMethod())) {
                result = service.assignNextPendingRepair();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(HtmlHelper.header("Assign & Route"));
            sb.append(HtmlHelper.pageHeading("Team Assignment & Route Planning",
                    "Assign the highest-priority pending repair to the nearest eligible team using Dijkstra's algorithm"));

            // Trigger form
            sb.append(HtmlHelper.cardOpen("🚀 Run Assignment"));
            sb.append("<p>Clicking the button below will:</p>");
            sb.append("<ol>");
            sb.append("<li>Pick the <strong>highest-priority PENDING</strong> report from the queue.</li>");
            sb.append("<li>Find all <strong>available teams</strong> that have the required equipment.</li>");
            sb.append("<li>Run <strong>Dijkstra's algorithm</strong> from each eligible team's location.</li>");
            sb.append("<li>Select the team with the <strong>shortest travel time</strong>.</li>");
            sb.append("<li>Assign the report and mark the team unavailable.</li>");
            sb.append("</ol>");
            sb.append("<form method=\"POST\" action=\"/assign\">\n");
            sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"runAssignBtn\">▶ Assign Next Repair</button>\n");
            sb.append("</form>\n");
            sb.append(HtmlHelper.cardClose());

            // Show result
            if (result != null) {
                sb.append(HtmlHelper.cardOpen("📊 Assignment Result"));
                if (result.isSuccess()) {
                    sb.append(HtmlHelper.alertSuccess("Report " + result.getReportId()
                            + " assigned to Team " + result.getTeamId() + "."));

                    RoadDamage rd = service.getReport(result.getReportId());
                    MaintenanceTeam team = service.getTeam(result.getTeamId());

                    sb.append("<table class=\"data-table\">\n");
                    sb.append("<tr><th>Report ID</th><td>" + HtmlHelper.escape(result.getReportId()) + "</td></tr>\n");
                    if (rd != null) {
                        sb.append("<tr><th>Damage Type</th><td>" + HtmlHelper.escape(rd.getDamageType()) + "</td></tr>\n");
                        sb.append("<tr><th>Location</th><td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                                + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td></tr>\n");
                        sb.append("<tr><th>Required Equipment</th><td>" + HtmlHelper.escape(rd.getRequiredEquipment()) + "</td></tr>\n");
                    }
                    sb.append("<tr><th>Assigned Team</th><td>" + HtmlHelper.escape(result.getTeamId()));
                    if (team != null) sb.append(" — " + HtmlHelper.escape(team.getTeamName()));
                    sb.append("</td></tr>\n");

                    DijkstraResult route = result.getRouteResult();
                    sb.append("<tr><th>Route</th><td>" + HtmlHelper.escape(route.getPathString()) + "</td></tr>\n");
                    sb.append("<tr><th>Total Travel Time</th><td>" + route.getTotalTime() + " minutes</td></tr>\n");
                    sb.append("</table>\n");

                } else {
                    String outcome = result.getOutcome();
                    if (AssignmentResult.NO_PENDING.equals(outcome)) {
                        sb.append(HtmlHelper.alertInfo("No pending reports — nothing to assign."));
                    } else {
                        sb.append(HtmlHelper.alertError("Could not assign Report "
                                + result.getReportId() + "."));
                        sb.append("<p><strong>Reason:</strong> " + HtmlHelper.escape(result.getFailureReason()) + "</p>\n");
                    }

                    if (!result.getSkippedTeams().isEmpty()) {
                        sb.append("<p><strong>Teams considered and rejected:</strong></p><ul>\n");
                        for (String reason : result.getSkippedTeams()) {
                            sb.append("<li>" + HtmlHelper.escape(reason) + "</li>\n");
                        }
                        sb.append("</ul>\n");
                    }
                }
                sb.append(HtmlHelper.cardClose());
            }

            // Current pending queue summary
            List<RoadDamage> pending = service.getPendingReportsOrdered();
            sb.append(HtmlHelper.cardOpen("⏳ Current Pending Queue (" + pending.size() + " reports)"));
            if (pending.isEmpty()) {
                sb.append(HtmlHelper.alertInfo("Queue is empty — all reports have been assigned or completed."));
            } else {
                sb.append("<div class=\"table-wrapper\">\n<table class=\"data-table\">\n");
                sb.append("<thead><tr><th>#</th><th>Report ID</th><th>Location</th>");
                sb.append("<th>Damage</th><th>Sev</th><th>Equipment Needed</th><th>Priority</th></tr></thead>\n<tbody>\n");
                int rank = 1;
                for (RoadDamage rd : pending) {
                    sb.append("<tr><td>" + rank++ + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getReportId()) + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                            + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>");
                    sb.append("<td>" + rd.getSeverityRank() + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getRequiredEquipment()) + "</td>");
                    sb.append("<td>" + HtmlHelper.priorityBadge(rd.getSeverityRank()) + "</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</tbody></table>\n</div>\n");
            }
            sb.append(HtmlHelper.cardClose());

            sb.append(HtmlHelper.footer());
            sendHtml(exchange, 200, sb.toString());
        }
    }

    // ================================================================
    // HANDLER: Complete Repair (GET/POST /complete)
    // ================================================================
    private class CompleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String message = null;
            boolean success = false;

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                HashMap<String, String> params = parseParams(body);
                String reportId = params.getOrDefault("reportId", "").trim();
                String error = service.completeRepair(reportId);
                if (error != null) { message = error; }
                else { message = "Report " + reportId + " marked COMPLETED. Team released."; success = true; }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(HtmlHelper.header("Complete Repair"));
            sb.append(HtmlHelper.pageHeading("Complete a Repair",
                    "Mark an assigned repair as completed and release the maintenance team"));

            if (message != null) {
                sb.append(success ? HtmlHelper.alertSuccess(message) : HtmlHelper.alertError(message));
            }

            // Completion form
            sb.append(HtmlHelper.cardOpen("✅ Mark Repair as Completed"));
            sb.append("<form method=\"POST\" action=\"/complete\">\n");
            sb.append("<div class=\"form-grid\">\n");

            // Build select of ASSIGNED reports
            List<RoadDamage> allReports = service.getAllReports();
            allReports.sort((a, b) -> a.getReportId().compareTo(b.getReportId()));

            sb.append("<div class=\"form-group\">\n");
            sb.append("<label for=\"reportId\">Select Assigned Report</label>\n");
            sb.append("<select id=\"reportId\" name=\"reportId\" required>\n");
            boolean anyAssigned = false;
            for (RoadDamage rd : allReports) {
                if (rd.getStatus().equals(RoadDamage.STATUS_ASSIGNED)) {
                    anyAssigned = true;
                    sb.append("<option value=\"" + HtmlHelper.escape(rd.getReportId()) + "\">");
                    sb.append(HtmlHelper.escape(rd.getReportId()) + " — "
                            + HtmlHelper.escape(rd.getDamageType()) + " at "
                            + HtmlHelper.escape(rd.getLocationId())
                            + " (Team: " + HtmlHelper.escape(rd.getAssignedTeamId()) + ")");
                    sb.append("</option>\n");
                }
            }
            if (!anyAssigned) {
                sb.append("<option value=\"\">— No assigned repairs —</option>\n");
            }
            sb.append("</select>\n</div>\n");

            sb.append("</div>\n");
            sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"completeRepairBtn\"");
            if (!anyAssigned) sb.append(" disabled");
            sb.append(">Mark as Completed</button>\n");
            sb.append("</form>\n");
            sb.append(HtmlHelper.cardClose());

            // Table of assigned repairs
            sb.append(HtmlHelper.cardOpen("🔧 Currently Assigned Repairs"));
            sb.append("<div class=\"table-wrapper\">\n<table class=\"data-table\">\n");
            sb.append("<thead><tr><th>Report ID</th><th>Location</th><th>Damage</th><th>Team</th><th>Status</th></tr></thead>\n<tbody>\n");
            boolean foundAny = false;
            for (RoadDamage rd : allReports) {
                if (rd.getStatus().equals(RoadDamage.STATUS_ASSIGNED)) {
                    foundAny = true;
                    sb.append("<tr>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getReportId()) + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getLocationId()) + " — "
                            + HtmlHelper.escape(service.getNetwork().getLocationName(rd.getLocationId())) + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getDamageType()) + "</td>");
                    sb.append("<td>" + HtmlHelper.escape(rd.getAssignedTeamId()) + "</td>");
                    sb.append("<td>" + HtmlHelper.statusBadge(rd.getStatus()) + "</td>");
                    sb.append("</tr>\n");
                }
            }
            if (!foundAny) {
                sb.append("<tr><td colspan=\"5\" style=\"text-align:center; color:#64748B;\">No reports are currently assigned.</td></tr>\n");
            }
            sb.append("</tbody></table>\n</div>\n");
            sb.append(HtmlHelper.cardClose());

            sb.append(HtmlHelper.footer());
            sendHtml(exchange, 200, sb.toString());
        }
    }

    // ================================================================
    // HANDLER: CSS file (GET /style.css)
    // ================================================================
    private class CssHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Try to load CSS from the web/ folder next to where Java is run
            String cssPath = "web/style.css";
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(Paths.get(cssPath));
            } catch (IOException e) {
                bytes = "body { font-family: sans-serif; }".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream out = exchange.getResponseBody();
            out.write(bytes);
            out.close();
        }
    }
}
