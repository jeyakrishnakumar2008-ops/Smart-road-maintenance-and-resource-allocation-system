// RoadConnection.java
// Represents a single directed edge in the road network.
// Each connection has a destination location, a travel time (weight) and an open/closed flag.
// The travel time is ALWAYS preserved even when the road is closed,
// so it can be used again if the road is reopened.

public class RoadConnection {

    private String destinationId; // The location this road leads to
    private int    travelTime;    // Travel time in minutes (the edge weight)
    private boolean isOpen;       // true = road is passable; false = road is closed

    // Constructor — all roads start OPEN
    public RoadConnection(String destinationId, int travelTime) {
        this.destinationId = destinationId;
        this.travelTime    = travelTime;
        this.isOpen        = true;
    }

    // ---- Getters ----
    public String  getDestinationId() { return destinationId; }
    public int     getTravelTime()    { return travelTime; }
    public boolean isOpen()           { return isOpen; }

    // ---- Road status changes ----
    public void close()  { isOpen = false; }
    public void reopen() { isOpen = true; }

    @Override
    public String toString() {
        return "→ " + destinationId + " (" + travelTime + " min, "
                + (isOpen ? "OPEN" : "CLOSED") + ")";
    }
}
