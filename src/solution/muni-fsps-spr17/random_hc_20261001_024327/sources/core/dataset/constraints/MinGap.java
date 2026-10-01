package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>The times assigned to any two classes in this constraint that are placed on the same day must allow for at least G
 * slots between the end of the earlier class and the start of the later class.</p>
 * <p>Any two classes that are taught on the same day (they are placed on overlapping days and weeks) must be at least G
 * slots apart. This means that there must be at least G slots between the end of the earlier class and the start of the
 * later class. That is
 * ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0) ∨ (Ci.end + G ≤ Cj.start) ∨ (Cj.end + G ≤ Ci.start)
 * for any two classes Ci and Cj from the constraint.</p>
 */
public class MinGap extends PairDistributionConstraint {
    private final int minGap; //G

    /**
     * Constructs a paired min gap distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     * @param minGap  The minimum number of time slots necessary between two classes in a day.
     * @throws IllegalArgumentException If the passed min gap is negative.
     */
    public MinGap(Class[] classes, int minGap) throws IllegalArgumentException {
        super(classes);
        if (minGap < 0)
            throw new IllegalArgumentException("The minimum allowed gap between two classes cannot ne negative!");
        this.minGap = minGap;
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0) ∨ (Ci.end + G ≤ Cj.start) ∨ (Cj.end + G ≤ Ci.start)
        return LogicalOperators.areExclusive(e1.getTimeAssignment().time().days(), e2.getTimeAssignment().time().days())
                || LogicalOperators.areExclusive(e1.getTimeAssignment().time().weeks(), e2.getTimeAssignment().time().weeks())
                || (e1.getTimeAssignment().time().end() + minGap <= e2.getTimeAssignment().time().start())
                || (e2.getTimeAssignment().time().end() + minGap <= e1.getTimeAssignment().time().start());
    }
}