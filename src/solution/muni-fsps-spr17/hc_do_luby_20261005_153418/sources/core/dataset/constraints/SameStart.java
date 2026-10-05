package dataset.constraints;

import dataset.Class;
import dataset.Event;

/**
 * <p>All classes in this constraint must start at the same time of day.</p>
 * <p>Given classes must start at the same time slot, regardless of their days of week or weeks. This means that
 * Ci.start = Cj.start for any two classes Ci and Cj from the constraint; Ci.start is the assigned start time slot of a
 * class Ci.</p>
 */
public class SameStart extends PairDistributionConstraint {
    /**
     * Constructs a paired same start distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public SameStart(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // Ci.start = Cj.start
        return e1.getTimeAssignment().time().start() == e2.getTimeAssignment().time().start();
    }
}