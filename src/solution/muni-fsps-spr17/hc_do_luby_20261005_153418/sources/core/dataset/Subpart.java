package dataset;

/**
 * This class represents subparts as defined in the ITC 2019.
 */
public record Subpart(int id, Class[] classes) {
    /**
     * Constructs a Subpart object.
     *
     * @param id      The id of the subpart.
     * @param classes The list of classes in this subpart.
     * @throws IllegalArgumentException If the passed id is invalid.
     */
    public Subpart {
        if (id < 1) throw new IllegalArgumentException("Subpart id cannot be less than 1!");
    }
}