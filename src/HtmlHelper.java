// HtmlHelper.java — UPDATED for three-role navigation
// Builds reusable HTML strings used by all handlers.
// User-supplied text is always passed through escape() before insertion.

import java.util.HashMap;
import java.util.List;

public class HtmlHelper {

    // ---- Escape HTML special characters to prevent injection ----
    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    // ================================================================
    // PAGE STRUCTURE HELPERS
    // ================================================================

    // Full page header for role-aware dashboards
    public static String header(String pageTitle, UserAccount user) {
        String roleLabel = "";
        String roleClass = "";
        if (user != null) {
            if (user.isWardOfficer())  { roleLabel = "Ward Officer";       roleClass = "role-ward"; }
            if (user.isEngineering())  { roleLabel = "Engineering Section"; roleClass = "role-eng"; }
            if (user.isMaintenance())  { roleLabel = "Maintenance Team";   roleClass = "role-maint"; }
        }
        return "<!DOCTYPE html>\n"
             + "<html lang=\"en\">\n"
             + "<head>\n"
             + "  <meta charset=\"UTF-8\">\n"
             + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
             + "  <title>" + escape(pageTitle) + " — Senthamizh Nagar</title>\n"
             + "  <meta name=\"description\" content=\"Smart Municipal Road Maintenance System — Senthamizh Nagar\">\n"
             + "  <link rel=\"stylesheet\" href=\"/style.css\">\n"
             + "</head>\n"
             + "<body>\n"
             + topNav(user, roleLabel, roleClass)
             + "<div class=\"layout\">\n"
             + sidebar(user)
             + "<main class=\"content\">\n";
    }

    // Overload for backward compat (no user context — shows engineering nav)
    public static String header(String pageTitle) {
        return header(pageTitle, null);
    }

    public static String footer() {
        return "</main>\n</div>\n</body>\n</html>\n";
    }

    // ---- Top navigation bar ----
    private static String topNav(UserAccount user, String roleLabel, String roleClass) {
        String userInfo = "";
        if (user != null) {
            userInfo = "<div class=\"topnav-user\">"
                     + "<span class=\"role-badge " + roleClass + "\">" + escape(roleLabel) + "</span>"
                     + "<span class=\"user-name\">" + escape(user.getDisplayName()) + "</span>"
                     + "<a href=\"/logout\" class=\"logout-link\">Logout</a>"
                     + "</div>";
        }
        return "<header class=\"topnav\">\n"
             + "  <div class=\"topnav-brand\">\n"
             + "    <span class=\"brand-icon\">🏛️</span>\n"
             + "    <span class=\"brand-name\">Senthamizh Nagar Municipality</span>\n"
             + "  </div>\n"
             + "  <div class=\"topnav-subtitle\">Smart Road Maintenance &amp; Resource Allocation</div>\n"
             + "  " + userInfo + "\n"
             + "</header>\n";
    }

    // ---- Role-specific sidebar navigation ----
    private static String sidebar(UserAccount user) {
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"sidebar\">\n<ul class=\"nav-list\">\n");

        if (user == null || user.isEngineering()) {
            // Engineering: full navigation
            sb.append(navItem("/engineering/dashboard", "📊", "Dashboard"));
            sb.append(navItem("/engineering/inbox",     "📥", "Inbox (Review)"));
            sb.append(navItem("/engineering/priority",  "⚡", "Priority Queue"));
            sb.append(navItem("/engineering/teams",     "👷", "Teams"));
            sb.append(navItem("/engineering/network",   "🗺️", "Road Network"));
            sb.append(navItem("/engineering/assign",    "🔧", "Assign &amp; Route"));
            sb.append(navItem("/engineering/history",   "📋", "All Reports"));
        } else if (user.isWardOfficer()) {
            // Ward Officer: limited navigation
            sb.append(navItem("/ward/dashboard", "📊", "My Dashboard"));
            sb.append(navItem("/ward/submit",    "➕", "Submit Report"));
            sb.append(navItem("/ward/reports",   "📋", "My Reports"));
        } else if (user.isMaintenance()) {
            // Maintenance: team-specific navigation
            sb.append(navItem("/maintenance/dashboard", "📊", "My Dashboard"));
            sb.append(navItem("/maintenance/job",       "🔧", "My Assignment"));
            sb.append(navItem("/maintenance/history",   "📋", "History"));
        }

        sb.append("</ul>\n</nav>\n");
        return sb.toString();
    }

    private static String navItem(String href, String icon, String label) {
        return "<li><a href=\"" + href + "\" class=\"nav-link\">"
             + "<span class=\"nav-icon\">" + icon + "</span> " + label
             + "</a></li>\n";
    }

    // ================================================================
    // PAGE HEADING
    // ================================================================
    public static String pageHeading(String title, String subtitle) {
        return "<div class=\"page-heading\">\n"
             + "  <h1 class=\"page-title\">" + escape(title) + "</h1>\n"
             + "  <p class=\"page-subtitle\">" + escape(subtitle) + "</p>\n"
             + "</div>\n";
    }

    // ================================================================
    // ALERT BANNERS
    // ================================================================
    public static String alertSuccess(String msg) {
        return "<div class=\"alert alert-success\">✅ " + escape(msg) + "</div>\n";
    }
    public static String alertError(String msg) {
        return "<div class=\"alert alert-error\">❌ " + escape(msg) + "</div>\n";
    }
    public static String alertInfo(String msg) {
        return "<div class=\"alert alert-info\">ℹ️ " + escape(msg) + "</div>\n";
    }
    public static String alertWarning(String msg) {
        return "<div class=\"alert alert-warning\">⚠️ " + escape(msg) + "</div>\n";
    }

    // ================================================================
    // BADGES
    // ================================================================
    public static String statusBadge(String status) {
        String cls;
        switch (status) {
            case "SUBMITTED":   cls = "badge badge-submitted";   break;
            case "RETURNED":    cls = "badge badge-returned";    break;
            case "VERIFIED":    cls = "badge badge-verified";    break;
            case "ASSIGNED":    cls = "badge badge-assigned";    break;
            case "IN_PROGRESS": cls = "badge badge-inprogress";  break;
            case "COMPLETED":   cls = "badge badge-completed";   break;
            case "REJECTED":    cls = "badge badge-rejected";    break;
            default:            cls = "badge";
        }
        return "<span class=\"" + cls + "\">" + escape(status.replace("_", " ")) + "</span>";
    }

    public static String priorityBadge(int severity) {
        if (severity == 3) return "<span class=\"badge badge-high\">HIGH</span>";
        if (severity == 2) return "<span class=\"badge badge-medium\">MEDIUM</span>";
        return "<span class=\"badge badge-low\">LOW</span>";
    }

    // ================================================================
    // CARD HELPERS
    // ================================================================
    public static String cardOpen(String title) {
        return "<div class=\"card\">\n"
             + "  <div class=\"card-header\">" + escape(title) + "</div>\n"
             + "  <div class=\"card-body\">\n";
    }
    public static String cardClose() {
        return "  </div>\n</div>\n";
    }

    // ================================================================
    // FORM HELPERS (reused across handlers)
    // ================================================================
    public static String formField(String label, String name, String type,
                                   String placeholder, boolean required) {
        return "<div class=\"form-group\">\n"
             + "  <label for=\"" + name + "\">" + escape(label) + "</label>\n"
             + "  <input type=\"" + type + "\" id=\"" + name + "\" name=\"" + name + "\""
             + " placeholder=\"" + escape(placeholder) + "\""
             + (required ? " required" : "") + ">\n"
             + "</div>\n";
    }

    public static String formTextarea(String label, String name,
                                      String placeholder, boolean required) {
        return "<div class=\"form-group\">\n"
             + "  <label for=\"" + name + "\">" + escape(label) + "</label>\n"
             + "  <textarea id=\"" + name + "\" name=\"" + name
             + "\" rows=\"3\" placeholder=\"" + escape(placeholder) + "\""
             + (required ? " required" : "") + "></textarea>\n"
             + "</div>\n";
    }

    public static String formSelect(String label, String name,
                                    HashMap<String, String> options, boolean required) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"form-group\">\n");
        sb.append("  <label for=\"" + name + "\">" + escape(label) + "</label>\n");
        sb.append("  <select id=\"" + name + "\" name=\"" + name + "\""
                + (required ? " required" : "") + ">\n");
        List<String> ids = new java.util.ArrayList<>(options.keySet());
        ids.sort(null);
        for (String id : ids) {
            sb.append("    <option value=\"" + escape(id) + "\">"
                    + escape(id) + " — " + escape(options.get(id)) + "</option>\n");
        }
        sb.append("  </select>\n</div>\n");
        return sb.toString();
    }

    public static String formSelectArray(String label, String name,
                                         String[] options, boolean required) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"form-group\">\n");
        sb.append("  <label for=\"" + name + "\">" + escape(label) + "</label>\n");
        sb.append("  <select id=\"" + name + "\" name=\"" + name + "\""
                + (required ? " required" : "") + ">\n");
        for (String opt : options) {
            sb.append("    <option value=\"" + escape(opt) + "\">"
                    + escape(opt) + "</option>\n");
        }
        sb.append("  </select>\n</div>\n");
        return sb.toString();
    }

    public static String rankSelect(String label, String name) {
        return "<div class=\"form-group\">\n"
             + "  <label for=\"" + name + "\">" + escape(label) + "</label>\n"
             + "  <select id=\"" + name + "\" name=\"" + name + "\" required>\n"
             + "    <option value=\"3\">3 — High</option>\n"
             + "    <option value=\"2\">2 — Medium</option>\n"
             + "    <option value=\"1\">1 — Low</option>\n"
             + "  </select>\n</div>\n";
    }

    public static String rankSelectWithValue(String label, String name, int value) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"form-group\">\n");
        sb.append("  <label for=\"" + name + "\">" + escape(label) + "</label>\n");
        sb.append("  <select id=\"" + name + "\" name=\"" + name + "\" required>\n");
        for (int v = 3; v >= 1; v--) {
            String selected = (v == value) ? " selected" : "";
            String lbl = v == 3 ? "High" : (v == 2 ? "Medium" : "Low");
            sb.append("    <option value=\"" + v + "\"" + selected + ">" + v + " — " + lbl + "</option>\n");
        }
        sb.append("  </select>\n</div>\n");
        return sb.toString();
    }

    // Location dropdown helper
    public static String locationSelect(String label, String name,
                                        HashMap<String, String> locationNames) {
        return formSelect(label, name, locationNames, true);
    }
}
