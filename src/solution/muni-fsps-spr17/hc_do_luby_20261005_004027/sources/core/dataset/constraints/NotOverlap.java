package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>No two classes in this constraint can overlap in time.</p>
 * <p>Given classes do not overlap in time. Two classes do not overlap in time when they do not overlap in time of day,
 * or in days of the week, or in weeks. This means that
 * (Ci.end ≤ Cj.start) ∨ (Cj.end ≤ Ci.start) ∨ ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0) for any two
 * classes Ci and Cj from the constraint, doing binary "and" between days and weeks of Ci and Cj.</p>
 */
public class NotOverlap extends PairDistributionConstraint {
    /**
     * Constructs a paired not overlap distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public NotOverlap(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.end ≤ Cj.start) ∨ (Cj.end ≤ Ci.start) ∨ ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0)
        return (e1.getTimeAssignment().time().end() <= e2.getTimeAssignment().time().start())
                || (e2.getTimeAssignment().time().end() <= e1.getTimeAssignment().time().start())
                || (LogicalOperators.areExclusive(e1.getTimeAssignment().time().days(), e2.getTimeAssignment().time().days()))
                || (LogicalOperators.areExclusive(e1.getTimeAssignment().time().weeks(), e2.getTimeAssignment().time().weeks()));
    }
}