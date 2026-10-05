package dataset.constraints;

import dataset.Class;
import dataset.Time;
import dataset.Timetable;

/**
 * <p>This constraint limits the total amount of time assigned to the set of classes listed in the constraint to no more
 * than S time slots per day over the entire term.</p>
 * <p>Given classes must be spread over the days of the week (and weeks) in a way that there is no more than a given
 * number of S time slots on every day. This means that for each week w ∈ {0, 1, …, nrWeeks − 1} of the semester and
 * each day of the week d ∈ {0, 1, …, nrDays − 1}, the total number of slots assigned to classes C that overlap with the
 * selected day d and week w is not more than S,
 * DayLoad(d,w) ≤ S
 * DayLoad(d,w) = ∑i {Ci.length | (Ci.days and 2d) ≠ 0 ∧ (Ci.weeks and 2w) ≠ 0)}
 * where 2d is a bit string with the only non-zero bit on position d and 2w is a bit string with the only non-zero bit
 * on position w. When the constraint is soft (it is not required and there is a penalty), its penalty is multiplied by
 * the number of slots that exceed the given constant S over all days of the semester and divided by the number of weeks
 * of the semester (using integer division). Importantly the integer division is computed at the very end. That is
 * (penalty × ∑w,dmax(DayLoad(d,w) − S, 0)) / nrWeeks</p>
 */
public class MaxDayLoad extends DistributionConstraint {
    private final int maxDayLoad; // S

    /**
     * Constructs a max day load distribution constraint object with the given classes.
     *
     * @param classes    Classes that this distribution constraint applies to.
     * @param maxDayLoad The maximum allowed number of time slots to be assigned to a class on every day.
     * @throws IllegalArgumentException If the passed max day load is negative.
     */
    public MaxDayLoad(Class[] classes, int maxDayLoad) throws IllegalArgumentException {
        super(classes);
        if (maxDayLoad < 0) throw new IllegalArgumentException("The maximum allowed day load cannot be negative!");
        this.maxDayLoad = maxDayLoad;
    }

    @Override
    public boolean isSatisfied(Timetable timetable) throws NullPointerException {
        // DayLoad(d,w) ≤ S
        int dayLoad;
        final int weeksLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().weeks().length, daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        Time eventTime;
        for (int w = 0; w < weeksLength; w++)
            for (int d = 0; d < daysLength; d++) {
                dayLoad = 0;
                for (Class aClass : getClasses()) {
                    eventTime = timetable.getEvent(aClass).getTimeAssignment().time();
                    if (eventTime.weeks()[w] && eventTime.days()[d]) dayLoad += eventTime.duration();
                    if (dayLoad > maxDayLoad) return false;
                }
            }
        return true;
    }

    @Override
    public int violationCount(Timetable timetable) throws NullPointerException {
        int dayLoad, totalOverflow = 0;
        final int weeksLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().weeks().length, daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        Time eventTime;
        for (int w = 0; w < weeksLength; w++)
            for (int d = 0; d < daysLength; d++) {
                dayLoad = 0;
                for (Class aClass : getClasses()) {
                    eventTime = timetable.getEvent(aClass).getTimeAssignment().time();
                    if (eventTime.weeks()[w] && eventTime.days()[d]) dayLoad += eventTime.duration();
                    if (dayLoad > maxDayLoad) totalOverflow += (dayLoad - maxDayLoad);
                }
            }
        return totalOverflow;
    }
}