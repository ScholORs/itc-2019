package dataset;

/**
 * This class represents courses as defined in the ITC 2019.
 * Each course (ME 263 in Figure 5) has a unique id and consists of one or more configurations named config in the XML
 * (Lec-Rec and Lec-Rec-Lab) and identified by their unique ids such that each student attends (some) classes in one
 * configuration only.
 */
public record Course(int id, Config[] configs) {
    /**
     * Constructs a Course object.
     *
     * @param id      The id of the course.
     * @param configs The list of configs of the course.
     * @throws IllegalArgumentException If the course id is invalid.
     */
    public Course {
        if (id < 1) throw new IllegalArgumentException("Course id cannot be less than 1!");
    }
}