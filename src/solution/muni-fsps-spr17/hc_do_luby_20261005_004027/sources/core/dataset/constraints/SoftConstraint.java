package dataset.constraints;

import dataset.ProblemInstance;
import dataset.Timetable;

/**
 * <p>This class represents a distribution constraint that is hard (required="true"). Soft constraints may not be
 * satisfied and there is a penalty for each violation. When any of the constraints that can be validated on pairs of
 * classes is soft, the provided penalty is incurred for every pair of classes of the constraint that are in a
 * violation. In other words, if M pairs of classes do not satisfy the distribution constraint, the total penalty for
 * violation of this constraint is M × penalty.</p>
 * <p>This class is basically the same as the {@link DistributionConstraint}. It only acts as a wrapper to create
 * separation between hard and soft constraints when instantiating in the {@link ProblemInstance} for
 * further simplicity.</p>
 */
public record SoftConstraint(DistributionConstraint constraint, int penalty) {
    /**
     * Constructs a soft constraint object.
     *
     * @param constraint A distribution constraint.
     * @param penalty    The penalty for each violation of this soft constraint.
     * @throws IllegalArgumentException If the passed penalty is negative.
     */
    public SoftConstraint {
        if (penalty < 0) throw new IllegalArgumentException("Constraint penalty cannot be negative!");
    }

    /**
     * Checks the violation count of the constraint based on the passed timetable and calculates the penalty of this
     * soft constraint.
     *
     * @param timetable The timetable (candidate solution) to calculate the penalty of this soft constraint for.
     * @return The penalty of this soft constraint on the given timetable.
     * @throws NullPointerException If the passed timetable is half or not scheduled.
     */
    public int calcPenalty(Timetable timetable) throws NullPointerException {
        if (constraint instanceof MaxDayLoad || constraint instanceof MaxBreaks || constraint instanceof MaxBlock)
            return (penalty * constraint.violationCount(timetable) / timetable.getEvents()[0].getTimeAssignment().time().weeks().length);
        return penalty * constraint.violationCount(timetable);
    }
}