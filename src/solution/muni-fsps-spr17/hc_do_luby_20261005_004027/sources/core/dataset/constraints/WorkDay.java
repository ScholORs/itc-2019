package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>This constraint prevents or penalizes the occurrence of class pairs among the listed classes where the time
 * between the start of one class and the end of another which occur on the same day is greater than S time slots.</p>
 * <p>There should not be more than S time slots between the start of the first class and the end of the last class on
 * any given day. This means that classes that are placed on the overlapping days and weeks that have more than S time
 * slots between the start of the earlier class and the end of the later class are violating the constraint. That is
 * ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0) ∨ (max(Ci.end,Cj.end)−min(Ci.start,Cj.start) ≤ S)
 * for any two classes Ci and Cj from the constraint.</p>
 */
public class WorkDay extends PairDistributionConstraint {
    private final int maxDayLength; //S

    /**
     * Constructs a paired work days distribution constraint object with the given classes.
     *
     * @param classes      Classes that this distribution constraint applies to.
     * @param maxDayLength The max number of time slots allowed between the start of the first class and the end of the
     *                     last class in a day.
     * @throws IllegalArgumentException If the passed max day length is negative.
     */
    public WorkDay(Class[] classes, int maxDayLength) throws IllegalArgumentException {
        super(classes);
        if (maxDayLength < 0) throw new IllegalArgumentException("The max day length cannot be negative!");
        this.maxDayLength = maxDayLength;
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0) ∨ (max(Ci.end,Cj.end)−min(Ci.start,Cj.start) ≤ S)
        return LogicalOperators.areExclusive(e1.getTimeAssignment().time().days(), e2.getTimeAssignment().time().days())
                || LogicalOperators.areExclusive(e1.getTimeAssignment().time().weeks(), e2.getTimeAssignment().time().weeks())
                || (Math.max(e1.getTimeAssignment().time().end(), e2.getTimeAssignment().time().end()) - Math.min(e1.getTimeAssignment().time().start(), e2.getTimeAssignment().time().start()) <= maxDayLength);
    }
}