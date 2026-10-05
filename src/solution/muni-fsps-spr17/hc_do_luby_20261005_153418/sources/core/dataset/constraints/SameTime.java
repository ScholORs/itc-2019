package dataset.constraints;

import dataset.Class;
import dataset.Event;

/**
 * <p>All classes in this constraint must be offered within the same times of day as those required by the longest class
 * in the set, i.e., the times of day they meet must be completely overlapped by the longest class in the
 * constraint.</p>
 * <p>Given classes must be taught at the same time of day, regardless of their days of week or weeks. For the classes
 * of the same length, this is the same constraint as SameStart (classes must start at the same time slot). For the
 * classes of different lengths, the shorter class can start after the longer class but must end before or at the same
 * time as the longer class.
 * This means that (Ci.start ≤ Cj.start ∧ Cj.end ≤ Ci.end) ∨ (Cj.start ≤ Ci.start ∧ Ci.end ≤ Cj.end) for any two classes
 * Ci and Cj from the constraint; Ci.end = Ci.start + Ci.length is the assigned end time slot of a class Ci.</p>
 */
public class SameTime extends PairDistributionConstraint {
    /**
     * Constructs a paired same time distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public SameTime(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.start ≤ Cj.start ∧ Cj.end ≤ Ci.end) ∨ (Cj.start ≤ Ci.start ∧ Ci.end ≤ Cj.end)
        return (e1.getTimeAssignment().time().start() <= e2.getTimeAssignment().time().start() && e2.getTimeAssignment().time().end() <= e1.getTimeAssignment().time().end())
                || (e2.getTimeAssignment().time().start() <= e1.getTimeAssignment().time().start() && e1.getTimeAssignment().time().end() <= e2.getTimeAssignment().time().end());
    }
}