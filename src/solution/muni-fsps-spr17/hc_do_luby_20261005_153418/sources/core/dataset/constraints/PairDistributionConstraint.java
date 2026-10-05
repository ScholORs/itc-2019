package dataset.constraints;

import dataset.Class;
import dataset.Event;
import dataset.Timetable;

/**
 * This abstract class represents a paired distribution constraint as defined in the ITC 2019 dataset. A distribution
 * constraint can either be paired or can be over a list of classes. While a paired distribution constraint is applied
 * and checked over a pair of events (scheduled classes), others evaluate and perform the check method over features of
 * a list of events (scheduled classes). The necessary check methods for violations are also added here.
 */
abstract class PairDistributionConstraint extends DistributionConstraint {
    /**
     * Constructs a paired distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    PairDistributionConstraint(Class[] classes) {
        super(classes);
    }

    /**
     * Checks to see if the given pair of events (scheduled classes) satisfy this pair distribution constraint.
     *
     * @param e1 The first event (scheduled class).
     * @param e2 The second event (scheduled class).
     * @return true if the given pair of events satisfy this pair distribution constraint; false otherwise.
     * @throws NullPointerException If the given timetable is half or not scheduled.
     */
    abstract boolean check(Event e1, Event e2) throws NullPointerException;

    @Override
    public boolean isSatisfied(Timetable timetable) throws NullPointerException {
        Event event1, event2;
        for (int i = 0; i < getClasses().length - 1; i++) {
            event1 = timetable.getEvent(getClasses()[i]);
            for (int j = i + 1; j < getClasses().length; j++) {
                event2 = timetable.getEvent(getClasses()[j]);
                if (!check(event1, event2)) return false;
            }
        }
        return true;
    }

    @Override
    public int violationCount(Timetable timetable) throws NullPointerException {
        int count = 0;
        Event event1, event2;
        for (int i = 0; i < getClasses().length - 1; i++) {
            event1 = timetable.getEvent(getClasses()[i]);
            for (int j = i + 1; j < getClasses().length; j++) {
                event2 = timetable.getEvent(getClasses()[j]);
                if (!check(event1, event2)) count++;
            }
        }
        return count;
    }
}