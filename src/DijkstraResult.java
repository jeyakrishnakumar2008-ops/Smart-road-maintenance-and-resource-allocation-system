// DijkstraResult.java
// A simple container that holds the result of running Dijkstra's algorithm.
// It stores whether the destination was reachable, the minimum travel time
// and the list of locations visited on the shortest path.

import java.util.List;
import java.util.ArrayList;

public class DijkstraResult {

    private boolean    reachable;   // true if a path was found
    private int        totalTime;   // total travel time in minutes (Integer.MAX_VALUE if unreachable)
    private List<String> path;      // ordered list of location IDs from source to destination

    // Constructor
    public DijkstraResult(boolean reachable, int totalTime, List<String> path) {
        this.reachable  = reachable;
        this.totalTime  = totalTime;
        this.path       = path;
    }

    // Factory method: creates an "unreachable" result with an empty path
    public static DijkstraResult unreachable() {
        return new DijkstraResult(false, Integer.MAX_VALUE, new ArrayList<>());
    }

    // ---- Getters ----
    public boolean     isReachable() { return reachable; }
    public int         getTotalTime() { return totalTime; }
    public List<String> getPath()    { return path; }

    // Returns a readable route string, e.g. "L01 → L03 → L07"
    public String getPathString() {
        if (!reachable || path.isEmpty()) return "Unreachable";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(" → ");
            sb.append(path.get(i));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        if (!reachable) return "UNREACHABLE";
        return "Route: " + getPathString() + " | Time: " + totalTime + " min";
    }
}
