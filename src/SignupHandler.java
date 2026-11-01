// SignupHandler.java
// Handles all user registration routes:
//   /signup
//   /signup/ward
//   /signup/engineering
//   /signup/maintenance
// Enforces server-side validation, password matching, authorization codes,
// and saves new users into MySQL with PBKDF2 salted hashes.
// Pure HTML/CSS, ZERO JavaScript.

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.HashMap;

public class SignupHandler implements HttpHandler {

    private final SessionManager sessionManager;

    public SignupHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // Landing selection page
        if (path.equals("/signup") || path.equals("/signup/")) {
            HttpUtils.sendHtml(exchange, 200, buildRoleSelectionPage());
            return;
        }

        // 1. Ward Officer Signup
        if (path.equals("/signup/ward")) {
            if ("POST".equals(method)) {
                handleWardSignup(exchange);
            } else {
                HttpUtils.sendHtml(exchange, 200, buildWardSignupForm(null, null));
            }
            return;
        }

        // 2. Engineering Section Signup
        if (path.equals("/signup/engineering")) {
            if ("POST".equals(method)) {
                handleEngineeringSignup(exchange);
            } else {
                HttpUtils.sendHtml(exchange, 200, buildEngineeringSignupForm(null, null));
            }
            return;
        }

        // 3. Maintenance Team Signup
        if (path.equals("/signup/maintenance")) {
            if ("POST".equals(method)) {
                handleMaintenanceSignup(exchange);
            } else {
                HttpUtils.sendHtml(exchange, 200, buildMaintenanceSignupForm(null, null));
            }
            return;
        }

        // Default: redirect to selection
        HttpUtils.redirect(exchange, "/signup");
    }

    // =====================================================================
    // POST HANDLERS
    // =====================================================================

    private void handleWardSignup(HttpExchange exchange) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);

        String fullName        = p.getOrDefault("fullName", "").trim();
        String employeeId      = p.getOrDefault("employeeId", "").trim();
        String wardNumber      = p.getOrDefault("wardNumber", "").trim();
        String email           = p.getOrDefault("email", "").trim();
        String username        = p.getOrDefault("username", "").trim();
        String password        = p.getOrDefault("password", "");
        String confirmPassword = p.getOrDefault("confirmPassword", "");

        // Validation
        if (fullName.isEmpty() || employeeId.isEmpty() || wardNumber.isEmpty()
                || email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            HttpUtils.sendHtml(exchange, 200, buildWardSignupForm("All required fields must be filled out.", p));
            return;
        }

        if (!password.equals(confirmPassword)) {
            HttpUtils.sendHtml(exchange, 200, buildWardSignupForm("Password and confirm password do not match.", p));
            return;
        }

        if (password.length() < 6) {
            HttpUtils.sendHtml(exchange, 200, buildWardSignupForm("Password must be at least 6 characters long.", p));
            return;
        }

        String regError = sessionManager.register(username, password, email, fullName, employeeId,
                UserAccount.ROLE_WARD, wardNumber, null, null, null, null);

        if (regError != null) {
            HttpUtils.sendHtml(exchange, 200, buildWardSignupForm(regError, p));
            return;
        }

        // Success: Redirect to login page
        HttpUtils.redirect(exchange, "/login/ward?registered=true");
    }

    private void handleEngineeringSignup(HttpExchange exchange) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);

        String fullName        = p.getOrDefault("fullName", "").trim();
        String employeeId      = p.getOrDefault("employeeId", "").trim();
        String departmentName  = p.getOrDefault("departmentName", "").trim();
        String email           = p.getOrDefault("email", "").trim();
        String regCode         = p.getOrDefault("registrationCode", "").trim();
        String username        = p.getOrDefault("username", "").trim();
        String password        = p.getOrDefault("password", "");
        String confirmPassword = p.getOrDefault("confirmPassword", "");

        if (fullName.isEmpty() || employeeId.isEmpty() || departmentName.isEmpty()
                || email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            HttpUtils.sendHtml(exchange, 200, buildEngineeringSignupForm("All required fields must be filled out.", p));
            return;
        }

        // Verify secret Engineering registration code
        String expectedCode = DatabaseConnection.getEngineeringRegistrationCode();
        if (!expectedCode.equals(regCode)) {
            HttpUtils.sendHtml(exchange, 200, buildEngineeringSignupForm(
                    "Invalid Engineering Authorization Code. Registration restricted to authorized personnel.", p));
            return;
        }

        if (!password.equals(confirmPassword)) {
            HttpUtils.sendHtml(exchange, 200, buildEngineeringSignupForm("Password and confirm password do not match.", p));
            return;
        }

        if (password.length() < 6) {
            HttpUtils.sendHtml(exchange, 200, buildEngineeringSignupForm("Password must be at least 6 characters long.", p));
            return;
        }

        String regError = sessionManager.register(username, password, email, fullName, employeeId,
                UserAccount.ROLE_ENGINEERING, null, departmentName, null, null, null);

        if (regError != null) {
            HttpUtils.sendHtml(exchange, 200, buildEngineeringSignupForm(regError, p));
            return;
        }

        HttpUtils.redirect(exchange, "/login/engineering?registered=true");
    }

    private void handleMaintenanceSignup(HttpExchange exchange) throws IOException {
        String body = HttpUtils.readBody(exchange);
        HashMap<String, String> p = HttpUtils.parseParams(body);

        String teamName        = p.getOrDefault("teamName", "").trim();
        String teamId          = p.getOrDefault("teamId", "").trim().toUpperCase();
        String email           = p.getOrDefault("email", "").trim();
        String equipment       = p.getOrDefault("equipment", "").trim();
        String username        = p.getOrDefault("username", "").trim();
        String password        = p.getOrDefault("password", "");
        String confirmPassword = p.getOrDefault("confirmPassword", "");

        if (teamName.isEmpty() || teamId.isEmpty() || email.isEmpty()
                || username.isEmpty() || password.isEmpty()) {
            HttpUtils.sendHtml(exchange, 200, buildMaintenanceSignupForm("All required fields must be filled out.", p));
            return;
        }

        if (!password.equals(confirmPassword)) {
            HttpUtils.sendHtml(exchange, 200, buildMaintenanceSignupForm("Password and confirm password do not match.", p));
            return;
        }

        if (password.length() < 6) {
            HttpUtils.sendHtml(exchange, 200, buildMaintenanceSignupForm("Password must be at least 6 characters long.", p));
            return;
        }

        String regError = sessionManager.register(username, password, email, teamName, null,
                UserAccount.ROLE_MAINTENANCE, null, null, teamId, teamName, equipment);

        if (regError != null) {
            HttpUtils.sendHtml(exchange, 200, buildMaintenanceSignupForm(regError, p));
            return;
        }

        HttpUtils.redirect(exchange, "/login/maintenance?registered=true");
    }

    // =====================================================================
    // HTML FORM BUILDERS (White/Light Theme with Blue/Teal accents)
    // =====================================================================

    private String buildRoleSelectionPage() {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
             + "<title>Sign Up — Senthamizh Nagar Municipality</title>"
             + "<link rel=\"stylesheet\" href=\"/style.css\"></head><body>"
             + "<div style=\"min-height:100vh;display:flex;align-items:center;justify-content:center;padding:30px;background:#F1F5F9;\">"
             + "<div style=\"background:#fff;border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,0.08);max-width:850px;width:100%;padding:40px;\">"
             + "<div style=\"text-align:center;margin-bottom:32px;\">"
             + "<div style=\"font-size:44px;\">🏛️</div>"
             + "<h1 style=\"font-size:24px;color:#0F172A;margin:8px 0;\">New Account Registration</h1>"
             + "<p style=\"color:#64748B;font-size:15px;\">Select your municipal department to register for authorized system access.</p>"
             + "</div>"
             + "<div style=\"display:grid;grid-template-columns:repeat(auto-fit, minmax(240px, 1fr));gap:20px;\">"

             + "<div style=\"border:1px solid #E2E8F0;border-radius:10px;padding:24px;text-align:center;background:#F8FAFC;\">"
             + "<div style=\"font-size:36px;margin-bottom:12px;\">👷</div>"
             + "<h2 style=\"font-size:18px;color:#1E293B;margin-bottom:8px;\">Ward Officer</h2>"
             + "<p style=\"font-size:13px;color:#64748B;margin-bottom:20px;min-height:50px;\">Field inspection of road potholes, damage reporting, and observation logs.</p>"
             + "<a href=\"/signup/ward\" class=\"btn btn-primary\" style=\"display:block;text-decoration:none;padding:10px;\">Register as Ward Officer →</a>"
             + "</div>"

             + "<div style=\"border:1px solid #E2E8F0;border-radius:10px;padding:24px;text-align:center;background:#F8FAFC;\">"
             + "<div style=\"font-size:36px;margin-bottom:12px;\">🏛️</div>"
             + "<h2 style=\"font-size:18px;color:#1E293B;margin-bottom:8px;\">Engineering Section</h2>"
             + "<p style=\"font-size:13px;color:#64748B;margin-bottom:20px;min-height:50px;\">Damage verification, priority queue ordering, Dijkstra routing & squad dispatch.</p>"
             + "<a href=\"/signup/engineering\" class=\"btn btn-primary\" style=\"display:block;text-decoration:none;padding:10px;background:#4F46E5;\">Register as Engineer →</a>"
             + "</div>"

             + "<div style=\"border:1px solid #E2E8F0;border-radius:10px;padding:24px;text-align:center;background:#F8FAFC;\">"
             + "<div style=\"font-size:36px;margin-bottom:12px;\">🛠️</div>"
             + "<h2 style=\"font-size:18px;color:#1E293B;margin-bottom:8px;\">Maintenance Team</h2>"
             + "<p style=\"font-size:13px;color:#64748B;margin-bottom:20px;min-height:50px;\">Field squads, tool inventories, turn-by-turn navigation & repair execution.</p>"
             + "<a href=\"/signup/maintenance\" class=\"btn btn-primary\" style=\"display:block;text-decoration:none;padding:10px;background:#0D9488;\">Register Squad →</a>"
             + "</div>"

             + "</div>"
             + "<div style=\"text-align:center;margin-top:30px;color:#64748B;font-size:14px;\">"
             + "Already have an account? <a href=\"/login\" style=\"color:#2563EB;font-weight:600;\">Sign In Here</a> &nbsp;|&nbsp; <a href=\"/\" style=\"color:#64748B;\">Return to Portal Home</a>"
             + "</div>"
             + "</div></div></body></html>";
    }

    private String buildWardSignupForm(String error, HashMap<String, String> values) {
        return buildFormWrapper("Ward Officer Registration", "👷", "#2563EB", "/signup/ward", "/login/ward",
                error,
                HtmlHelper.formField("Full Name *", "fullName", "text", getVal(values, "fullName", "Suresh Kumar"), true)
                + "<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;\">"
                + HtmlHelper.formField("Employee ID *", "employeeId", "text", getVal(values, "employeeId", "EMP-W03"), true)
                + HtmlHelper.formField("Ward Number *", "wardNumber", "text", getVal(values, "wardNumber", "W03"), true)
                + "</div>"
                + HtmlHelper.formField("Official Email Address *", "email", "email", getVal(values, "email", "suresh@senthamizh.gov.in"), true)
                + HtmlHelper.formField("Desired Username *", "username", "text", getVal(values, "username", "ward_suresh"), true)
                + "<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;\">"
                + HtmlHelper.formField("Password (min 6 chars) *", "password", "password", "", true)
                + HtmlHelper.formField("Confirm Password *", "confirmPassword", "password", "", true)
                + "</div>"
        );
    }

    private String buildEngineeringSignupForm(String error, HashMap<String, String> values) {
        return buildFormWrapper("Engineering Section Registration", "🏛️", "#4F46E5", "/signup/engineering", "/login/engineering",
                error,
                HtmlHelper.formField("Full Name *", "fullName", "text", getVal(values, "fullName", "Rajan M"), true)
                + "<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;\">"
                + HtmlHelper.formField("Employee ID *", "employeeId", "text", getVal(values, "employeeId", "EMP-ENG02"), true)
                + HtmlHelper.formField("Department / Section *", "departmentName", "text", getVal(values, "departmentName", "Roads & Bridges"), true)
                + "</div>"
                + HtmlHelper.formField("Official Email Address *", "email", "email", getVal(values, "email", "rajan.m@senthamizh.gov.in"), true)
                + "<div style=\"background:#EFF6FF;border:1px solid #BFDBFE;padding:12px;border-radius:6px;margin-bottom:14px;\">"
                + "<div style=\"font-size:12px;color:#1E40AF;margin-bottom:6px;font-weight:600;\">🔒 Authorized Registration Security</div>"
                + HtmlHelper.formField("Engineering Authorization Code *", "registrationCode", "password", "Enter secure authorization key", true)
                + "<div style=\"font-size:11px;color:#64748B;\">Demo Code: <code>MUNI-ENG-2026</code> (configured in db.properties)</div>"
                + "</div>"
                + HtmlHelper.formField("Desired Username *", "username", "text", getVal(values, "username", "eng_rajan"), true)
                + "<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;\">"
                + HtmlHelper.formField("Password (min 6 chars) *", "password", "password", "", true)
                + HtmlHelper.formField("Confirm Password *", "confirmPassword", "password", "", true)
                + "</div>"
        );
    }

    private String buildMaintenanceSignupForm(String error, HashMap<String, String> values) {
        return buildFormWrapper("Maintenance Team Registration", "🛠️", "#0D9488", "/signup/maintenance", "/login/maintenance",
                error,
                HtmlHelper.formField("Squad / Team Name *", "teamName", "text", getVal(values, "teamName", "Delta Squad"), true)
                + "<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;\">"
                + HtmlHelper.formField("Squad ID (e.g. T4) *", "teamId", "text", getVal(values, "teamId", "T4"), true)
                + HtmlHelper.formField("Official Contact Email *", "email", "email", getVal(values, "email", "delta.squad@senthamizh.gov.in"), true)
                + "</div>"
                + HtmlHelper.formField("Equipped Machinery / Tools", "equipment", "text", getVal(values, "equipment", "Pothole Filler, Road Roller"), false)
                + HtmlHelper.formField("Login Username *", "username", "text", getVal(values, "username", "T4"), true)
                + "<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;\">"
                + HtmlHelper.formField("Password (min 6 chars) *", "password", "password", "", true)
                + HtmlHelper.formField("Confirm Password *", "confirmPassword", "password", "", true)
                + "</div>"
        );
    }

    private String buildFormWrapper(String title, String icon, String color,
                                     String formAction, String loginLink,
                                     String error, String fieldsHtml) {
        String errorBanner = error != null ? "<div class=\"alert alert-error\" style=\"margin-bottom:16px;\">❌ "
                + HtmlHelper.escape(error) + "</div>" : "";

        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
             + "<title>" + HtmlHelper.escape(title) + " — Senthamizh Nagar</title>"
             + "<link rel=\"stylesheet\" href=\"/style.css\"></head><body>"
             + "<div style=\"min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;background:#F8FAFC;\">"
             + "<div style=\"background:#fff;border-radius:10px;box-shadow:0 4px 18px rgba(0,0,0,0.06);max-width:540px;width:100%;padding:32px;border-top:5px solid " + color + ";\">"
             + "<div style=\"text-align:center;margin-bottom:24px;\">"
             + "<div style=\"font-size:38px;\">" + icon + "</div>"
             + "<h1 style=\"font-size:20px;color:#0F172A;margin:6px 0;\">" + HtmlHelper.escape(title) + "</h1>"
             + "<p style=\"color:#64748B;font-size:13px;\">Fill out the official registration form to create your account.</p>"
             + "</div>"
             + errorBanner
             + "<form method=\"POST\" action=\"" + formAction + "\">"
             + fieldsHtml
             + "<button type=\"submit\" class=\"btn btn-primary\" style=\"width:100%;padding:12px;margin-top:12px;background:" + color + ";font-size:15px;\">Create Account →</button>"
             + "</form>"
             + "<div style=\"text-align:center;margin-top:20px;font-size:13px;color:#64748B;\">"
             + "Already have an account? <a href=\"" + loginLink + "\" style=\"color:" + color + ";font-weight:600;\">Sign In Here</a>"
             + "<br><a href=\"/signup\" style=\"color:#94A3B8;font-size:12px;margin-top:8px;display:inline-block;\">← Choose another role</a>"
             + "</div>"
             + "</div></div></body></html>";
    }

    private String getVal(HashMap<String, String> values, String key, String defaultVal) {
        if (values == null || !values.containsKey(key)) return defaultVal;
        return HtmlHelper.escape(values.get(key));
    }
}
