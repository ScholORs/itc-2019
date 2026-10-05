package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>All classes in this constraint must be offered during the same weeks of the term or, if a class meets fewer weeks,
 * it must meet during a subset of the weeks used by the class meeting for the largest number of weeks.</p>
 * <p>Given classes must be taught in the same weeks, regardless of their time slots or days of the week. In case of
 * classes of different weeks, a class with fewer weeks must meet on a subset of the weeks used by the class with more
 * weeks. This means that (Ci.weeks or Cj.weeks) = Ci.weeks) ∨ (Ci.weeks or Cj.weeks) = Cj.weeks) for any two classes
 * Ci and Cj from the constraint; doing binary "or" between the bit strings representing the assigned weeks.</p>
 */
public class SameWeeks extends PairDistributionConstraint {
    /**
     * Constructs a paired same weeks distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public SameWeeks(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.weeks or Cj.weeks) = Ci.weeks) ∨ (Ci.weeks or Cj.weeks) = Cj.weeks)
        return LogicalOperators.areSubsets(e1.getTimeAssignment().time().weeks(), e2.getTimeAssignment().time().weeks());
    }
}