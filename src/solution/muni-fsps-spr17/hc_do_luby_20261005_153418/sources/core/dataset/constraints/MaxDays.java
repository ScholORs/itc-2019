package dataset.constraints;

import dataset.Class;
import dataset.Timetable;

/**
 * <p>The classes in this constraint should not be assigned to more than D different weekdays, regardless of whether
 * this occurs during the same week of a term.</p>
 * <p>Given classes cannot spread over more than D days of the week, regardless whether they are in the same week of
 * semester or not. This means that the total number of days of the week that have at least one class of this
 * distribution constraint C1, …, Cn is not greater than D, countNonzeroBits(C1.days or C2.days or ⋅ ⋅ ⋅ Cn.days) ≤ D
 * where countNonzeroBits(x) returns the number of non-zero bits in the bit string x. When the constraint is soft, the
 * penalty is multiplied by the number of days that exceed the given constant D.</p>
 */
public class MaxDays extends DistributionConstraint {
    private final int maxDays; // D

    /**
     * Constructs a max days distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     * @param maxDays The maximum allowed number of days of the week for classes to spread over.
     * @throws IllegalArgumentException If the passed maxDays is negative.
     */
    public MaxDays(Class[] classes, int maxDays) throws IllegalArgumentException {
        super(classes);
        if (maxDays < 0 || maxDays > 7)
            throw new IllegalArgumentException("The maximum allowed number of days for classes to spread over cannot be negative!");
        this.maxDays = maxDays;
    }

    // TODO: Is there a better way to get the days and weeks lengths in isSatisfied and violationCount methods for this, maxDayLoad, maxBreaks, maxBlock and soft constraint classes?
    // TODO: Verify and optimize this, maxDayLoad, maxBreaks, and maxBlock constraint classes.
    @Override
    public boolean isSatisfied(Timetable timetable) throws NullPointerException {
        // countNonzeroBits(C1.days or C2.days or ⋅ ⋅ ⋅ Cn.days) ≤ D
        int counter = 0;
        final int daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        for (int i = 0; i < daysLength; i++)
            for (Class aClass : getClasses()) {
                if (counter > maxDays) return false;
                if (timetable.getEvent(aClass).getTimeAssignment().time().days()[i]) {
                    counter++;
                    break;
                }
            }
        return true;
    }

    @Override
    public int violationCount(Timetable timetable) throws NullPointerException {
        int counter = 0;
        final int daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        for (int i = 0; i < daysLength; i++)
            for (Class aClass : getClasses())
                if (timetable.getEvent(aClass).getTimeAssignment().time().days()[i]) {
                    counter++;
                    break;
                }
        return Math.max(0, counter - maxDays);
    }
}