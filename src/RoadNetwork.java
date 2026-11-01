// RoadNetwork.java
// Represents the road network as a weighted directed graph.
// An adjacency list (HashMap of Lists) stores the connections.
//
// Dijkstra's algorithm (min-heap version) is implemented here to find
// the shortest travel time between two locations while ignoring closed roads.
//
// HOW THE GRAPH WORKS:
//   adjacencyList.get("L01") returns a List of RoadConnection objects.
//   Each RoadConnection says "you can travel to locationX in Y minutes".
//
// DIJKSTRA EXAMPLE (small graph):
//   Locations: L01, L02, L03
//   Roads: L01→L02 (5 min), L01→L03 (10 min), L02→L03 (3 min)
//   Shortest L01→L03: L01→L02→L03 = 5+3 = 8 min  (better than direct 10 min)

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.HashSet;
import java.util.Map;

public class RoadNetwork {

    // The adjacency list: maps each locationId to its list of outgoing connections
    private HashMap<String, List<RoadConnection>> adjacencyList;

    // Set of all known location IDs
    private HashSet<String> locations;

    // Set of human-readable location names keyed by location ID
    private HashMap<String, String> locationNames;

    // Constructor
    public RoadNetwork() {
        adjacencyList = new HashMap<>();
        locations     = new HashSet<>();
        locationNames = new HashMap<>();
    }

    // Adds a location to the network
    public void addLocation(String locationId, String locationName) {
        locations.add(locationId);
        locationNames.put(locationId, locationName);
        // Make sure the adjacency list has an entry for this location
        if (!adjacencyList.containsKey(locationId)) {
            adjacencyList.put(locationId, new ArrayList<>());
        }
    }

    // Adds a directed road from sourceId to destinationId with the given travel time.
    // "Directed" means travel is only from source to destination, not the other way.
    // To make a two-way road, call addRoad twice (swapping source and destination).
    public void addRoad(String sourceId, String destinationId, int travelTime) {
        // Make sure the source exists in the adjacency list
        if (!adjacencyList.containsKey(sourceId)) {
            adjacencyList.put(sourceId, new ArrayList<>());
        }
        adjacencyList.get(sourceId).add(new RoadConnection(destinationId, travelTime));
    }

    // Adds an undirected (two-way) road between two locations
    public void addBidirectionalRoad(String loc1, String loc2, int travelTime) {
        addRoad(loc1, loc2, travelTime);
        addRoad(loc2, loc1, travelTime);
    }

    // ---- Road closure management ----

    // Closes a specific road from sourceId to destinationId.
    // Returns true if the road was found and closed.
    public boolean closeRoad(String sourceId, String destinationId) {
        List<RoadConnection> connections = adjacencyList.get(sourceId);
        if (connections == null) return false;
        for (RoadConnection rc : connections) {
            if (rc.getDestinationId().equals(destinationId)) {
                rc.close();
                return true;
            }
        }
        return false;
    }

    // Reopens a specific road from sourceId to destinationId.
    // Returns true if the road was found and reopened.
    public boolean reopenRoad(String sourceId, String destinationId) {
        List<RoadConnection> connections = adjacencyList.get(sourceId);
        if (connections == null) return false;
        for (RoadConnection rc : connections) {
            if (rc.getDestinationId().equals(destinationId)) {
                rc.reopen();
                return true;
            }
        }
        return false;
    }

    // ---- Dijkstra's algorithm ----
    // Finds the minimum travel time from startId to endId, skipping closed roads.
    // Uses a PriorityQueue as a min-heap so the location with the smallest
    // accumulated travel time is always processed first.
    //
    // ALGORITHM STEPS:
    //  1. Set distance to startId = 0, all others = infinity.
    //  2. Add startId to the min-heap.
    //  3. While the heap is not empty:
    //     a. Remove the location with the smallest current distance (this is the "settled" node).
    //     b. If it is the destination, we are done — reconstruct the path.
    //     c. For each open outgoing road from this location:
    //        - Calculate newDist = settled distance + road travel time.
    //        - If newDist < stored distance for the neighbour, update it and add to heap.
    //  4. If the heap empties without reaching the destination, the destination is unreachable.
    public DijkstraResult findShortestPath(String startId, String endId) {

        // Edge case: start and end are the same location
        if (startId.equals(endId)) {
            List<String> path = new ArrayList<>();
            path.add(startId);
            return new DijkstraResult(true, 0, path);
        }

        // dist stores the best known travel time from startId to each location.
        // We initialise every distance to a very large number (infinity substitute).
        HashMap<String, Integer> dist = new HashMap<>();
        for (String loc : locations) {
            dist.put(loc, Integer.MAX_VALUE);
        }
        dist.put(startId, 0);

        // prev stores the previous location on the best-known path to each location.
        // This is used to reconstruct the path once the destination is found.
        HashMap<String, String> prev = new HashMap<>();

        // The min-heap: each entry is an int[] array with [cumulativeTime, locationId].
        // We use a custom comparator so the entry with the smallest cumulativeTime is
        // always at the top of the heap.
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((x, y) -> x[0] - y[0]);

        // We also store the locationId in a parallel String array because int[] cannot hold Strings.
        // Instead we use a separate helper structure: a simple inner class via Object[].
        // For simplicity, we store entries as Object[]{Integer cost, String locId}.
        PriorityQueue<Object[]> heap = new PriorityQueue<>(
                (x, y) -> ((Integer) x[0]).compareTo((Integer) y[0])
        );
        heap.offer(new Object[]{0, startId});

        // visited set: once a location is settled, we skip it if it appears again in the heap
        HashSet<String> visited = new HashSet<>();

        while (!heap.isEmpty()) {

            // Step 3a: Remove the location with the smallest accumulated travel time
            Object[] current      = heap.poll();
            int      currentDist  = (Integer) current[0];
            String   currentLoc   = (String)  current[1];

            // Skip if we have already found a better path to this location
            if (visited.contains(currentLoc)) continue;
            visited.add(currentLoc);

            // Step 3b: Check if we reached the destination
            if (currentLoc.equals(endId)) {
                // Reconstruct the path by following the 'prev' map backwards
                List<String> path = new ArrayList<>();
                String step = endId;
                while (step != null) {
                    path.add(0, step); // add to the front so the list is in order
                    step = prev.get(step);
                }
                return new DijkstraResult(true, currentDist, path);
            }

            // Step 3c: Explore all open outgoing roads from currentLoc
            List<RoadConnection> neighbours = adjacencyList.get(currentLoc);
            if (neighbours == null) continue;

            for (RoadConnection road : neighbours) {
                // Skip closed roads — this is how road closures affect routing
                if (!road.isOpen()) continue;

                String neighbour   = road.getDestinationId();
                int    newDist     = currentDist + road.getTravelTime();

                // Only update if we found a shorter path to this neighbour
                if (newDist < dist.getOrDefault(neighbour, Integer.MAX_VALUE)) {
                    dist.put(neighbour, newDist);
                    prev.put(neighbour, currentLoc);   // record how we got here
                    heap.offer(new Object[]{newDist, neighbour});
                }
            }
        }

        // If we exit the loop without reaching endId, the destination is unreachable
        return DijkstraResult.unreachable();
    }

    // ---- Getters ----
    public HashSet<String>                              getLocations()       { return locations; }
    public HashMap<String, String>                      getLocationNames()   { return locationNames; }
    public HashMap<String, List<RoadConnection>>        getAdjacencyList()   { return adjacencyList; }

    // Returns the human-readable name for a location ID, or the ID itself if not found
    public String getLocationName(String locationId) {
        return locationNames.getOrDefault(locationId, locationId);
    }

    // Builds an HTML table of all road connections for display in web views
    public String buildRoadTableHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"overflow-x:auto;\">\n");
        sb.append("<table class=\"data-table\">\n");
        sb.append("<thead><tr><th>From</th><th>To</th><th style=\"text-align:center;\">Travel Time</th><th>Status</th></tr></thead>\n<tbody>\n");
        List<String> sortedLocs = new ArrayList<>(locations);
        sortedLocs.sort(null);
        for (String src : sortedLocs) {
            List<RoadConnection> conns = adjacencyList.get(src);
            if (conns != null) {
                for (RoadConnection rc : conns) {
                    String statusClass = rc.isOpen() ? "badge badge-verified" : "badge badge-rejected";
                    String statusText  = rc.isOpen() ? "OPEN" : "CLOSED";
                    sb.append("<tr>");
                    sb.append("<td>").append(HtmlHelper.escape(src)).append(" — ")
                      .append(HtmlHelper.escape(getLocationName(src))).append("</td>");
                    sb.append("<td>").append(HtmlHelper.escape(rc.getDestinationId())).append(" — ")
                      .append(HtmlHelper.escape(getLocationName(rc.getDestinationId()))).append("</td>");
                    sb.append("<td style=\"text-align:center;\">").append(rc.getTravelTime()).append(" min</td>");
                    sb.append("<td><span class=\"").append(statusClass).append("\">").append(statusText).append("</span></td>");
                    sb.append("</tr>\n");
                }
            }
        }
        sb.append("</tbody></table>\n</div>\n");
        return sb.toString();
    }
}

