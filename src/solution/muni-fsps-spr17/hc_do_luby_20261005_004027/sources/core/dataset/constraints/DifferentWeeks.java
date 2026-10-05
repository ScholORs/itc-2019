package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>The weeks of the term during which any classes in this constraint meet can not overlap.</p>
 * <p>Given classes must be taught on different weeks, regardless of their time slots or days of the week. This means
 * that (Ci.weeks and Cj.weeks) = 0 for any two classes Ci and Cj from the constraint; doing binary "and" between the
 * bit strings representing the assigned weeks.</p>
 */
public class DifferentWeeks extends PairDistributionConstraint {
    /**
     * Constructs a paired different weeks distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public DifferentWeeks(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.weeks and Cj.weeks) = 0
        return LogicalOperators.areExclusive(e1.getTimeAssignment().time().weeks(), e2.getTimeAssignment().time().weeks());
    }
}