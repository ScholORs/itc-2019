package dataset.constraints;

import dataset.Class;
import dataset.Event;
import dataset.TravelTime;
import utils.LogicalOperators;

/**
 * <p>All classes in this constraint must meet at times and locations such that someone who must attend, e.g., an
 * instructor, is reasonably able to attend all classes. This means that no two classes can overlap in time or follow
 * one another without sufficient time to travel between the two class locations.</p>
 * <p>Given classes cannot overlap in time, and if they are placed on overlapping days of week and weeks, they must be
 * placed close enough so that the attendees can travel between the two classes. This means that
 * (Ci.end + Ci.room.travel[Cj.room] ≤ Cj.start) ∨
 * (Cj.end + Cj.room.travel[Ci.room] ≤ Ci.start) ∨
 * ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0)
 * for any two classes Ci and Cj from the constraint; Ci.room.travel[Cj.room] is the travel time between the assigned
 * rooms of Ci and Cj.</p>
 */
public class SameAttendees extends PairDistributionConstraint {
    /**
     * Constructs a paired same attendees distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public SameAttendees(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (Ci.end + Ci.room.travel[Cj.room] ≤ Cj.start)
        // ∨ (Cj.end + Cj.room.travel[Ci.room] ≤ Ci.start)
        // ∨ ((Ci.days and Cj.days) = 0) ∨ ((Ci.weeks and Cj.weeks) = 0)
        if (e1.getRoomAssignment() == null || e2.getRoomAssignment() == null)
            return (e1.getTimeAssignment().time().end() <= e2.getTimeAssignment().time().start())
                || (e2.getTimeAssignment().time().end() <= e1.getTimeAssignment().time().start())
                || LogicalOperators.areExclusive(e1.getTimeAssignment().time().days(), e2.getTimeAssignment().time().days())
                || LogicalOperators.areExclusive(e1.getTimeAssignment().time().weeks(), e2.getTimeAssignment().time().weeks());
        int travelTime = TravelTime.getInstance().getTravelTime(e1.getRoomAssignment().room(), e2.getRoomAssignment().room());
        return (e1.getTimeAssignment().time().end() + travelTime <= e2.getTimeAssignment().time().start())
                || (e2.getTimeAssignment().time().end() + travelTime <= e1.getTimeAssignment().time().start())
                || LogicalOperators.areExclusive(e1.getTimeAssignment().time().days(), e2.getTimeAssignment().time().days())
                || LogicalOperators.areExclusive(e1.getTimeAssignment().time().weeks(), e2.getTimeAssignment().time().weeks());
    }
}