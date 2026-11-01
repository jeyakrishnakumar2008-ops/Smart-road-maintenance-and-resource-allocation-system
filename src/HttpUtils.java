// HttpUtils.java
// Shared HTTP utility methods used by all handler classes.
// Avoids duplicating the same code across WebServer, LoginHandler,
// WardHandler, EngineeringHandler and MaintenanceHandler.

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class HttpUtils {

    // ---- Send an HTML page response ----
    public static void sendHtml(HttpExchange exchange, int statusCode, String html)
            throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    // ---- HTTP 302 redirect ----
    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }

    // ---- Set the session cookie on the browser ----
    // The cookie is HttpOnly (JavaScript cannot read it — good practice even without JS frontend).
    public static void setCookie(HttpExchange exchange, String sessionId) {
        exchange.getResponseHeaders().set("Set-Cookie",
                SessionManager.COOKIE_NAME + "=" + sessionId
                + "; Path=/; HttpOnly; SameSite=Strict");
    }

    // ---- Clear the session cookie (used on logout) ----
    public static void clearCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Set-Cookie",
                SessionManager.COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly");
    }

    // ---- Read the POST request body as a plain string ----
    public static String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    // ---- Parse URL-encoded parameters (GET query string or POST body) ----
    // Input:  "reportId=R023&severityRank=3"
    // Output: { "reportId" → "R023", "severityRank" → "3" }
    public static HashMap<String, String> parseParams(String query) {
        HashMap<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(decode(kv[0]), decode(kv[1]));
            } else if (kv.length == 1) {
                params.put(decode(kv[0]), "");
            }
        }
        return params;
    }

    // ---- URL-decode a percent-encoded string ----
    // e.g. "Road+Name%20Here" → "Road Name Here"
    public static String decode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    // ---- Parse an integer from a string with a default fallback ----
    public static int parseInt(String s, int defaultValue) {
        if (s == null || s.trim().isEmpty()) return defaultValue;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // ---- Retrieve the logged-in user from the session cookie ----
    // Returns null if the cookie is missing or the session has expired.
    public static UserAccount getSessionUser(HttpExchange exchange, SessionManager sm) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        String sessionId    = sm.extractSessionId(cookieHeader);
        return sm.getSession(sessionId);
    }

    // ---- Get the raw session ID from the cookie ----
    public static String getSessionId(HttpExchange exchange, SessionManager sm) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        return sm.extractSessionId(cookieHeader);
    }
}
