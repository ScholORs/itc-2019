package dataset;

/**
 * This class represents a possible time assignment for a class as defined in the ITC 2019.
 */
public record TimeAssignment(Time time, int penalty) {
    /**
     * Constructs a time assignment object.
     *
     * @param time    The possible time period.
     * @param penalty The penalty associated with this assignment.
     * @throws IllegalArgumentException If the passed penalty is invalid.
     */
    public TimeAssignment {
        if (penalty < 0) throw new IllegalArgumentException("Penalty cannot be negative!");
    }

    public TimeAssignment deepCopy() {
        return new TimeAssignment(this.time, this.penalty);
    }
}