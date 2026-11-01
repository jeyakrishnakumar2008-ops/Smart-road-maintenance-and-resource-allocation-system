// Equipment.java
// Represents a piece of equipment that a maintenance team owns or that a repair job requires.
// We use simple string-based equipment names so they are easy to compare.

public class Equipment {

    // The name of this piece of equipment, e.g. "Pothole Filler", "Road Roller"
    private String name;

    // Constructor — creates an Equipment object with the given name
    public Equipment(String name) {
        this.name = name;
    }

    // Returns the name of this equipment
    public String getName() {
        return name;
    }

    // Two Equipment objects are "equal" if they have the same name (case-insensitive).
    // This is important so that HashSet.contains() works correctly when checking
    // whether a team owns a piece of equipment that a job requires.
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Equipment eq = (Equipment) other;
        return name.equalsIgnoreCase(eq.name);
    }

    // hashCode must be consistent with equals.
    // We convert the name to lower-case before hashing so that
    // "Pothole Filler" and "pothole filler" produce the same hash.
    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    // Returns a readable string, useful for printing and HTML generation
    @Override
    public String toString() {
        return name;
    }
}
