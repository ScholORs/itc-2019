package dataset.constraints;

import dataset.Class;
import dataset.Time;
import dataset.Timetable;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * <p>This constraint limits the amount of time (measured as M slots) that a set of classes may be consecutively
 * scheduled to which are each separated by no more than S slots.</p>
 * <p>This constraint limits the length of a block of two or more consecutive classes during a day (not more than M
 * slots in a block). For each day of week and week, two consecutive classes are considered to be in the same block if
 * the gap between them is not more than S time slots. For each block, the number of time slots from the start of the
 * first class in a block till the end of the last class in a block must not be more than M time slots. This means that
 * for each week w ∈ {0, 1, …, nrWeeks − 1} of the semester and each day of the week d ∈ {0, 1, …, nrDays − 1}, the
 * maximal length of a block does not exceed M slots
 * max { B.end − B.start | B ∈ MergeBlocks{(C.start, C.end)
 * | (C.days and 2d) ≠ 0 ∧ (C.weeks and 2w) ≠ 0})
 * }) ≤ M
 * Please note that only blocks of two or more classes are considered. In other words, if there is a class longer than M
 * slots, it does not break the constraint by itself, but it cannot form a larger block with another class.
 * When the constraint is soft, the penalty is multiplied by the total number of blocks that are over the M time slots,
 * computed over each day of the week and week of the semester and divided by the number of weeks of the semester at the
 * end (using integer division, just like for {@link MaxDayLoad}.).</p>
 */
public class MaxBlock extends DistributionConstraint {
    private final int maxBlockLength, extendedBreakLength; // M & S

    private record Block(int start, int end) {
    }

    /**
     * Constructs a max blocks distribution constraint object with the given classes.
     *
     * @param classes             Classes that this distribution constraint applies to.
     * @param maxBlockLength      The maximum allowed length of a block of at least 2 classes with less than
     *                            extendedBreakLength time between them per day.
     * @param extendedBreakLength The minimum length (number of time slots) required to consider a break, an extended
     *                            break.
     * @throws IllegalArgumentException If the passed maxBlockLength or extendedBreakLength is negative.
     */
    public MaxBlock(Class[] classes, int maxBlockLength, int extendedBreakLength) throws IllegalArgumentException {
        super(classes);
        if (maxBlockLength < 0)
            throw new IllegalArgumentException("The maximum allowed length of a block in a day cannot be negative!");
        this.maxBlockLength = maxBlockLength;
        if (extendedBreakLength < 0)
            throw new IllegalArgumentException("The minimum length of an extended break cannot be negative!");
        this.extendedBreakLength = extendedBreakLength;
    }

    @Override
    public boolean isSatisfied(Timetable timetable) throws NullPointerException {
        // max { B.end − B.start | B ∈ MergeBlocks{(C.start, C.end) | (C.days and 2d) ≠ 0 ∧ (C.weeks and 2w) ≠ 0})}) ≤ M
        final int weeksLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().weeks().length, daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        Time eventTime;
        ArrayList<Block> dayEvents;
        int mergedBlockStart, mergedBlockEnd;
        // STEP 1: Finding all the events that take place in a day
        for (int w = 0; w < weeksLength; w++)
            for (int d = 0; d < daysLength; d++) {
                dayEvents = new ArrayList<>();
                for (Class aClass : getClasses()) {
                    eventTime = timetable.getEvent(aClass).getTimeAssignment().time();
                    if (eventTime.weeks()[w] && eventTime.days()[d])
                        dayEvents.add(new Block(eventTime.start(), eventTime.end()));
                }
                if (dayEvents.size() < 2) break; // If there are less than 2 events today, go to the next day
                // STEP 2: Sorting all the events in a day based on their starting time and adding them to blocks
                dayEvents.sort(Comparator.comparingInt(block -> block.start));
                mergedBlockStart = dayEvents.get(0).start;
                mergedBlockEnd = dayEvents.get(0).end;
                // STEP 3: Merging blocks and checking the length of merged blocks
                for (int i = 1; i < dayEvents.size(); i++) {
                    Block current = dayEvents.get(i);
                    if (mergedBlockEnd + extendedBreakLength < current.start) { // If no overlap, start a new merged block
                        mergedBlockStart = current.start;
                        mergedBlockEnd = current.end;
                    } else { // Else, merge the blocks and check the length of the block
                        mergedBlockEnd = Math.max(mergedBlockEnd, current.end);
                        if (mergedBlockEnd - mergedBlockStart > maxBlockLength) return false;
                    }
                }
            }
        return false;
    }

    @Override
    public int violationCount(Timetable timetable) throws NullPointerException {
        final int weeksLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().weeks().length, daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        Time eventTime;
        ArrayList<Block> dayEvents;
        int mergedBlockStart, mergedBlockEnd, totalOverflow = 0;
        // STEP 1: Finding all the events that take place in a day
        for (int w = 0; w < weeksLength; w++)
            for (int d = 0; d < daysLength; d++) {
                dayEvents = new ArrayList<>();
                for (Class aClass : getClasses()) {
                    eventTime = timetable.getEvent(aClass).getTimeAssignment().time();
                    if (eventTime.weeks()[w] && eventTime.days()[d])
                        dayEvents.add(new Block(eventTime.start(), eventTime.end()));
                }
                if (dayEvents.size() < 2) break; // If there are less than 2 events today, go to the next day
                // STEP 2: Sorting all the events in a day based on their starting time and adding them to blocks
                dayEvents.sort(Comparator.comparingInt(block -> block.start));
                mergedBlockStart = dayEvents.get(0).start;
                mergedBlockEnd = dayEvents.get(0).end;
                // STEP 3: Merging blocks and checking the length of merged blocks
                for (int i = 1; i < dayEvents.size(); i++) {
                    Block current = dayEvents.get(i);
                    if (mergedBlockEnd + extendedBreakLength < current.start) { // If no overlap, start a new merged block
                        mergedBlockStart = current.start;
                        mergedBlockEnd = current.end;
                    } else { // Else, merge the blocks and check the length of the block
                        mergedBlockEnd = Math.max(mergedBlockEnd, current.end);
                        if (mergedBlockEnd - mergedBlockStart > maxBlockLength) totalOverflow++;
                    }
                }
            }
        return totalOverflow;
    }
}