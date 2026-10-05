package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>All classes in this constraint must be offered on the same days of the week or, if a class meets fewer days, it
 * must meet on a subset of the days used by the class with the largest number of days.</p>
 * <p>Given classes must be taught on the same days, regardless of their start time slots and weeks. In case of classes
 * of different days of the week, a class with fewer meetings must meet on a subset of the days used by the class with
 * more meetings. For example, if the class with the most meetings meets on Monday–Tuesday–Wednesday, all others classes
 * in the constraint can only be taught on Monday, Wednesday, and/or Friday. This means that
 * ((Ci.days or Cj.days) = Ci.days) ∨ ((Ci.days or Cj.days) = Cj.days) for any two classes Ci and Cj from the
 * constraint; Ci.days are the assigned days of the week of a class Ci, doing binary "or" between the bit strings.</p>
 */
public class SameDays extends PairDistributionConstraint {
    /**
     * Constructs a paired same days distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public SameDays(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // ((Ci.days or Cj.days) = Ci.days) ∨ ((Ci.days or Cj.days) = Cj.days)
        return LogicalOperators.areSubsets(e1.getTimeAssignment().time().days(), e2.getTimeAssignment().time().days());
    }
}