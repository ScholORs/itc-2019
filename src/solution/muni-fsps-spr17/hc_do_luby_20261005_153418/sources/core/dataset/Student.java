package dataset;

/**
 * <p>This class represents students as defined in the ITC 2019. Each student has a unique id and a list of courses that
 * he or she needs to attend. Each course is specified by its course id.</p>
 * <p>A student needs to be sectioned into one class of every subpart of a single configuration for each course from his
 * or her list of courses. If a parent-child relation between classes of a course is specified, this relation must be
 * respected as well (see also Section Courses). Also, the number of students that can attend each class is constrained
 * by the limit that is part of the class definition.</p>
 * <p>A student conflict occurs when a student is enrolled in two classes that overlap in time (they share at least one
 * week and one day of the week and they overlap in time of a day) or they are one after the other in rooms that are too
 * far apart. This means that the number of time slots between the two classes is smaller than the travel time value
 * between the two rooms. Student conflicts are allowed and penalized. The same penalty of one student conflict occurs
 * for any pair of classes that a student cannot attend, regardless of the number of actual meetings that are in
 * conflict or the length of the overlapping time.</p>
 */
public record Student(int id, Course[] courses) {
    /**
     * Constructs a Student object.
     *
     * @param id      The id of the student.
     * @param courses The list of courses that this student needs to take.
     * @throws IllegalArgumentException If the student id is invalid.
     */
    public Student {
        if (id < 1) throw new IllegalArgumentException("Room id cannot be less than 1!");
    }

    /**
     * Checks if this student needs the passed class.
     * <p><strong>This method has very high time complexity (O(n^4)). Avoid calling this method unless you have to.
     * Instead, while assigning a student to a class, search the demanded courses of the student.</strong></p>
     *
     * @param aClass The class to search for.
     * @return true if the passed class is in the demanded courses of this student; false otherwise.
     */
    boolean needsClass(Class aClass) {
        for (Course course : courses)
            for (Config config : course.configs())
                for (Subpart subpart : config.subparts())
                    for (Class theClass : subpart.classes())
                        if (theClass.equals(aClass)) return true;
        return false;
    }
}