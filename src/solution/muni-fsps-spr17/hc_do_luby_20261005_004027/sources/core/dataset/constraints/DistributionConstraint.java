package dataset.constraints;

import dataset.Class;
import dataset.Timetable;

import java.util.Arrays;

/**
 * <p>This abstract class represents distribution constraints as defined in the ITC 2019. Besides the already described
 * time and room constraints and student course demands, there are the following distribution constraints that can be
 * placed between any two or more classes. Any of these constraints can be either hard or soft. Hard constraints cannot
 * be violated and are marked as required. Soft constraints may not be satisfied and there is a penalty for each
 * violation.</p>
 * <p>Each constraint may affect the time of the day, the days of the week, the weeks of the semester, or the room
 * assigned. Constraints from the upper two sections of the table are evaluated between individual pairs of classes.
 * For example, if three classes need to be placed at the same starting time, such a constraint is violated if any two
 * of the three classes start at different times. Distribution constraints from the last section of the table need to
 * consider all classes for evaluation between which the constraint is created.</p>
 * <p>When any of the constraints that can be validated on pairs of classes is soft, the provided penalty is incurred
 * for every pair of classes of the constraint that are in a violation. In other words, if M pairs of classes do not
 * satisfy the distribution constraint, the total penalty for violation of this constraint is M × penalty. It means that
 * the maximal penalty for violation of one distribution constraint is penalty × N × (N − 1)/2, where N is the number of
 * classes in the constraint.</p>
 */
public abstract class DistributionConstraint {
    private final Class[] classes;

    /**
     * Constructs a distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public DistributionConstraint(Class[] classes) {
        this.classes = classes;
    }

    /**
     * Gets the classes of this distribution constraint.
     *
     * @return The classes this distribution constraint applies to.
     */
    Class[] getClasses() {
        return classes;
    }

    @Override
    public String toString() {
        return "DistributionConstraint{type=" + this.getClass().getSimpleName() + ", classes=" + Arrays.toString(classes) + '}';
    }

    /**
     * Checks if the given candidate solution (timetable) satisfies this distribution constraint over all the classes
     * defined in this constraint. As soon as there's a violation, it stops and returns false.
     *
     * @param timetable A candidate solution.
     * @return false as soon as there's a violation; true if this constraint is satisfied for the given timetable.
     * @throws NullPointerException If the given timetable is half or not scheduled.
     */
    public abstract boolean isSatisfied(Timetable timetable) throws NullPointerException;

    /**
     * Counts the number of times that the given timetable violates this constraint over its classes.
     *
     * @param timetable A candidate solution.
     * @return Violation count of the given timetable.
     * @throws NullPointerException If the given timetable is half or not scheduled.
     */
    public abstract int violationCount(Timetable timetable) throws NullPointerException;
}