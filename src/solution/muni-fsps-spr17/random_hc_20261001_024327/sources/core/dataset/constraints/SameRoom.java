package dataset.constraints;

import dataset.Class;
import dataset.Event;

/**
 * <p>All classes in this constraint must be assigned to the same room.</p>
 * <p>Given classes should be placed in the same room. This means that (Ci.room = Cj.room) for any two classes Ci and Cj
 * from the constraint; Ci.room is the assigned room of Ci.</p>
 */
public class SameRoom extends PairDistributionConstraint {
    /**
     * Constructs a paired same room distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public SameRoom(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.room = Cj.room)
        return e1.getRoomAssignment().room() == e2.getRoomAssignment().room();
    }
}