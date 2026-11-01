// LoginHandler.java
// Handles all authentication routes: /login, /login/ward, /login/engineering,
// /login/maintenance, and /logout.
// Verifies roles strictly and checks salted PBKDF2 hashes via SessionManager.
// No JavaScript — uses standard HTML form POST.

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.HashMap;

public class LoginHandler implements HttpHandler {

    private final SessionManager sessionManager;

    public LoginHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String query  = exchange.getRequestURI().getQuery();
        boolean justRegistered = query != null && query.contains("registered=true");

        // Logout
        if (path.equals("/logout")) {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            String sessionId    = sessionManager.extractSessionId(cookieHeader);
            sessionManager.invalidate(sessionId);
            HttpUtils.clearCookie(exchange);
            HttpUtils.redirect(exchange, "/login");
            return;
        }

        // Role selection page
        if (path.equals("/login") || path.equals("/login/")) {
            UserAccount user = HttpUtils.getSessionUser(exchange, sessionManager);
            if (user != null) {
                HttpUtils.redirect(exchange, getDashboard(user));
                return;
            }
            HttpUtils.sendHtml(exchange, 200, buildRoleSelectionPage());
            return;
        }

        // Individual role login pages
        if (path.equals("/login/ward")) {
            if ("POST".equals(method)) {
                handleLoginPost(exchange, UserAccount.ROLE_WARD);
            } else {
                HttpUtils.sendHtml(exchange, 200,
                        buildLoginForm("Ward Officer", "ward1", "ward123", "/login/ward",
                                "/signup/ward", "#2563EB", "👷", justRegistered ? "Account created successfully! Please sign in with your credentials below." : null, null));
            }
            return;
        }

        if (path.equals("/login/engineering")) {
            if ("POST".equals(method)) {
                handleLoginPost(exchange, UserAccount.ROLE_ENGINEERING);
            } else {
                HttpUtils.sendHtml(exchange, 200,
                        buildLoginForm("Engineering Section", "eng1", "eng123", "/login/engineering",
                                "/signup/engineering", "#4F46E5", "🏛️", justRegistered ? "Account created successfully! Please sign in with your credentials below." : null, null));
            }
            return;
        }

        if (path.equals("/login/maintenance")) {
            if ("POST".equals(method)) {
                handleLoginPost(exchange, UserAccount.ROLE_MAINTENANCE);
            } else {
                HttpUtils.sendHtml(exchange, 200,
                        buildLoginForm("Maintenance Team", "T1", "team123", "/login/maintenance",
                                "/signup/maintenance", "#0D9488", "🛠️", justRegistered ? "Account created successfully! Please sign in with your credentials below." : null, null));
            }
            return;
        }

        // Unknown login sub-path → redirect to role selection
        HttpUtils.redirect(exchange, "/login");
    }

    // Processes a POST login form submission
    private void handleLoginPost(HttpExchange exchange, String expectedRole) throws IOException {
        String body   = HttpUtils.readBody(exchange);
        HashMap<String, String> params = HttpUtils.parseParams(body);
        String userId   = params.getOrDefault("userId",   "").trim();
        String password = params.getOrDefault("password", "").trim();

        // Enforce role matching and salted PBKDF2 hash verification
        String sessionId = sessionManager.loginWithRole(userId, password, expectedRole);
        if (sessionId == null) {
            // Wrong credentials or wrong role — show the login form again with an error
            String formPath = "/login/" +
                    (UserAccount.ROLE_WARD.equals(expectedRole) ? "ward"
                    : UserAccount.ROLE_ENGINEERING.equals(expectedRole) ? "engineering"
                    : "maintenance");
            String signupPath = "/signup/" +
                    (UserAccount.ROLE_WARD.equals(expectedRole) ? "ward"
                    : UserAccount.ROLE_ENGINEERING.equals(expectedRole) ? "engineering"
                    : "maintenance");
            String color = UserAccount.ROLE_WARD.equals(expectedRole) ? "#2563EB"
                    : UserAccount.ROLE_ENGINEERING.equals(expectedRole) ? "#4F46E5" : "#0D9488";
            String icon  = UserAccount.ROLE_WARD.equals(expectedRole) ? "👷"
                    : UserAccount.ROLE_ENGINEERING.equals(expectedRole) ? "🏛️" : "🛠️";
            String label = UserAccount.ROLE_WARD.equals(expectedRole) ? "Ward Officer"
                    : UserAccount.ROLE_ENGINEERING.equals(expectedRole) ? "Engineering Section" : "Maintenance Team";
            String demoUser = UserAccount.ROLE_WARD.equals(expectedRole) ? "ward1"
                    : UserAccount.ROLE_ENGINEERING.equals(expectedRole) ? "eng1" : "T1";
            String demoPass = UserAccount.ROLE_WARD.equals(expectedRole) ? "ward123"
                    : UserAccount.ROLE_ENGINEERING.equals(expectedRole) ? "eng123" : "team123";

            String errorHtml = buildLoginForm(label, demoUser, demoPass, formPath, signupPath, color, icon,
                    null, "Invalid username, password, or role mismatch. Please verify your credentials.");
            HttpUtils.sendHtml(exchange, 200, errorHtml);
            return;
        }

        // Login succeeded — set cookie and redirect to dashboard
        UserAccount user = sessionManager.getSession(sessionId);
        HttpUtils.setCookie(exchange, sessionId);
        HttpUtils.redirect(exchange, getDashboard(user));
    }

    private String getDashboard(UserAccount user) {
        if (user.isWardOfficer())  return "/ward/dashboard";
        if (user.isEngineering())  return "/engineering/dashboard";
        if (user.isMaintenance())  return "/maintenance/dashboard";
        return "/login";
    }

    // ================================================================
    // PAGE BUILDERS
    // ================================================================

    private String buildRoleSelectionPage() {
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
             + "  <meta charset=\"UTF-8\">\n"
             + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
             + "  <title>Sign In — Senthamizh Nagar Municipality</title>\n"
             + "  <link rel=\"stylesheet\" href=\"/style.css\">\n"
             + "</head>\n<body class=\"login-body\">\n"
             + "<div class=\"login-container\" style=\"max-width:850px;\">\n"
             + "  <div class=\"login-brand\">\n"
             + "    <div class=\"login-logo\">🏛️</div>\n"
             + "    <h1 class=\"login-title\">Senthamizh Nagar Municipality</h1>\n"
             + "    <p class=\"login-subtitle\">Smart Road Maintenance &amp; Resource Allocation System</p>\n"
             + "  </div>\n"
             + "  <p class=\"role-prompt\">Select your official municipal role to sign in</p>\n"
             + "  <div style=\"display:grid;grid-template-columns:repeat(auto-fit, minmax(240px, 1fr));gap:20px;margin-bottom:24px;\">\n"

             + "    <div style=\"background:#fff;border:1px solid #E2E8F0;border-top:4px solid #2563EB;border-radius:10px;padding:24px;text-align:center;\">\n"
             + "      <div style=\"font-size:36px;margin-bottom:8px;\">👷</div>\n"
             + "      <h2 style=\"font-size:18px;color:#1E293B;margin-bottom:6px;\">Ward Officer</h2>\n"
             + "      <p style=\"font-size:13px;color:#64748B;margin-bottom:16px;\">Field inspections, damage reporting &amp; review tracking.</p>\n"
             + "      <a href=\"/login/ward\" class=\"btn btn-primary\" style=\"display:block;margin-bottom:8px;text-decoration:none;\">Sign In →</a>\n"
             + "      <a href=\"/signup/ward\" style=\"font-size:13px;color:#2563EB;font-weight:600;\">Register New Account</a>\n"
             + "    </div>\n"

             + "    <div style=\"background:#fff;border:1px solid #E2E8F0;border-top:4px solid #4F46E5;border-radius:10px;padding:24px;text-align:center;\">\n"
             + "      <div style=\"font-size:36px;margin-bottom:8px;\">🏛️</div>\n"
             + "      <h2 style=\"font-size:18px;color:#1E293B;margin-bottom:6px;\">Engineering Section</h2>\n"
             + "      <p style=\"font-size:13px;color:#64748B;margin-bottom:16px;\">Verification, PriorityQueue ordering &amp; team allocation.</p>\n"
             + "      <a href=\"/login/engineering\" class=\"btn btn-primary\" style=\"display:block;margin-bottom:8px;background:#4F46E5;text-decoration:none;\">Sign In →</a>\n"
             + "      <a href=\"/signup/engineering\" style=\"font-size:13px;color:#4F46E5;font-weight:600;\">Register New Account</a>\n"
             + "    </div>\n"

             + "    <div style=\"background:#fff;border:1px solid #E2E8F0;border-top:4px solid #0D9488;border-radius:10px;padding:24px;text-align:center;\">\n"
             + "      <div style=\"font-size:36px;margin-bottom:8px;\">🛠️</div>\n"
             + "      <h2 style=\"font-size:18px;color:#1E293B;margin-bottom:6px;\">Maintenance Team</h2>\n"
             + "      <p style=\"font-size:13px;color:#64748B;margin-bottom:16px;\">Field squads, turn-by-turn routing &amp; repair completion.</p>\n"
             + "      <a href=\"/login/maintenance\" class=\"btn btn-primary\" style=\"display:block;margin-bottom:8px;background:#0D9488;text-decoration:none;\">Sign In →</a>\n"
             + "      <a href=\"/signup/maintenance\" style=\"font-size:13px;color:#0D9488;font-weight:600;\">Register New Squad</a>\n"
             + "    </div>\n"

             + "  </div>\n"
             + "  <div style=\"text-align:center;margin-top:20px;\"><a href=\"/\" style=\"color:#64748B;font-size:14px;\">← Return to Portal Homepage</a></div>\n"
             + "</div>\n</body>\n</html>\n";
    }

    private String buildLoginForm(String roleLabel, String demoUser, String demoPass,
                                   String actionUrl, String signupUrl, String color, String icon,
                                   String successMessage, String errorMessage) {
        String alertHtml = "";
        if (successMessage != null) {
            alertHtml = "<div class=\"alert alert-success\" style=\"margin-bottom:16px;\">✅ "
                    + HtmlHelper.escape(successMessage) + "</div>\n";
        }
        if (errorMessage != null) {
            alertHtml = "<div class=\"alert alert-error\" style=\"margin-bottom:16px;\">❌ "
                    + HtmlHelper.escape(errorMessage) + "</div>\n";
        }

        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
             + "  <meta charset=\"UTF-8\">\n"
             + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
             + "  <title>Sign In — " + HtmlHelper.escape(roleLabel) + "</title>\n"
             + "  <link rel=\"stylesheet\" href=\"/style.css\">\n"
             + "</head>\n<body class=\"login-body\">\n"
             + "<div class=\"login-container login-form-container\" style=\"border-top:4px solid " + color + ";\">\n"
             + "  <div class=\"login-brand\">\n"
             + "    <div class=\"login-logo\" style=\"background:" + color + ";\">" + icon + "</div>\n"
             + "    <h1 class=\"login-title\">" + HtmlHelper.escape(roleLabel) + " Sign In</h1>\n"
             + "    <p class=\"login-subtitle\">Senthamizh Nagar — Smart Road Maintenance</p>\n"
             + "  </div>\n"
             + alertHtml
             + "  <form method=\"POST\" action=\"" + actionUrl + "\" class=\"login-form\">\n"
             + "    <div class=\"form-group\">\n"
             + "      <label for=\"userId\">Username or Official Email</label>\n"
             + "      <input type=\"text\" id=\"userId\" name=\"userId\""
             + " placeholder=\"Enter username or email\" required autocomplete=\"username\">\n"
             + "    </div>\n"
             + "    <div class=\"form-group\">\n"
             + "      <label for=\"password\">Password</label>\n"
             + "      <input type=\"password\" id=\"password\" name=\"password\""
             + " placeholder=\"Enter password\" required autocomplete=\"current-password\">\n"
             + "    </div>\n"
             + "    <button type=\"submit\" class=\"btn btn-primary\" style=\"width:100%;background:" + color + ";\" id=\"loginBtn\">"
             + "Sign In as " + HtmlHelper.escape(roleLabel) + " →</button>\n"
             + "  </form>\n"
             + "  <div style=\"text-align:center;margin:18px 0 10px 0;font-size:13px;color:#64748B;\">"
             + "    Don't have an account yet? <a href=\"" + signupUrl + "\" style=\"color:" + color + ";font-weight:600;\">Sign Up Here</a>"
             + "  </div>\n"
             + "  <div class=\"demo-creds\">\n"
             + "    <p><strong>Demo evaluation credentials:</strong></p>\n"
             + "    <p>Username: <code>" + HtmlHelper.escape(demoUser) + "</code>"
             + " &nbsp; Password: <code>" + HtmlHelper.escape(demoPass) + "</code></p>\n"
             + "  </div>\n"
             + "  <p style=\"text-align:center; margin-top:14px;\">"
             + "<a href=\"/login\" style=\"color:#64748B; font-size:0.875rem;\">← Choose another role</a></p>\n"
             + "</div>\n</body>\n</html>\n";
    }
}
