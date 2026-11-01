// Main.java
// Entry point of the Smart Municipal Road Maintenance application.
// This class creates the MunicipalService (which loads sample data)
// and starts the WebServer on port 8080.

public class Main {

    public static void main(String[] args) throws Exception {
        int port = 8080;

        // Allow an optional command-line port override: java Main 9090
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port argument. Using default port " + port);
            }
        }

        System.out.println("==============================================");
        System.out.println("  Smart Municipal Road Maintenance System");
        System.out.println("  Municipality: Senthamizh Nagar");
        System.out.println("==============================================");

        // Create the service — this also loads all sample data
        System.out.println("Loading sample data...");
        MunicipalService service = new MunicipalService();

        System.out.println("Reports loaded : " + service.getTotalReports());
        System.out.println("Teams loaded   : " + service.getAllTeams().size());
        System.out.println("Pending reports: " + service.getPendingCount());

        // Start the HTTP server
        WebServer server = new WebServer(service, port);
        server.start();
    }
}
