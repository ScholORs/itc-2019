package dataset.constraints;

import dataset.Class;
import dataset.Event;

/**
 * <p>The times of day during which all classes in this constraint meet can not overlap.</p>
 * <p>Given classes must be taught during different times of day, regardless of their days of week or weeks. This means
 * that no two classes of this constraint can overlap at a time of the day.
 * This means that (Ci.end ≤ Cj.start) ∨ (Cj.end ≤ Ci.start) for any two classes Ci and Cj from the constraint.</p>
 */
public class DifferentTime extends PairDistributionConstraint {
    /**
     * Constructs a paired different time distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public DifferentTime(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.end ≤ Cj.start) ∨ (Cj.end ≤ Ci.start)
        return (e1.getTimeAssignment().time().end() <= e2.getTimeAssignment().time().start())
                || (e2.getTimeAssignment().time().end() <= e1.getTimeAssignment().time().start());
    }
}