package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>All classes in this constraint can not meet on any of the same days of the week.</p>
 * <p>Given classes must be taught on different days of the week, regardless of their start time slots and weeks. This
 * means that (Ci.days and Cj.days) = 0 for any two classes Ci and Cj from the constraint; doing binary "and" between
 * the bit strings representing the assigned days.</p>
 */
public class DifferentDays extends PairDistributionConstraint {
    /**
     * Constructs a paired different days distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public DifferentDays(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.days and Cj.days) = 0
        return LogicalOperators.areExclusive(e1.getTimeAssignment().time().days(), e2.getTimeAssignment().time().days());
    }
}