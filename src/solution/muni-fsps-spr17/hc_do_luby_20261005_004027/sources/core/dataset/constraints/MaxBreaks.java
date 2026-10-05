package dataset.constraints;

import dataset.Class;
import dataset.Time;
import dataset.Timetable;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * <p>This constraint limits the number of breaks between classes during a day which exceed S time slots to no more than
 * R per day.</p>
 * <p>This constraint limits the number of breaks during a day between a given set of classes (not more than R breaks
 * during a day). For each day of week and week, there is a break between classes if there is more than S empty time
 * slots in between.
 * Two consecutive classes are considered to be in the same block if the gap between them is not more than S time slots.
 * This means that for each week w ∈ {0, 1, …, nrWeeks − 1} of the semester and each day of the week
 * d ∈ {0, 1, …, nrDays − 1}, the number of blocks is not greater than R + 1,
 * |MergeBlocks{(C.start, C.end) | (C.days and 2d) ≠ 0 ∧ (C.weeks and 2w) ≠ 0})| ≤ R + 1
 * where 2d is a bit string with the only non-zero bit on position d and 2w is a bit string with the only non-zero bit
 * on position w. The MergeBlocks function recursively merges to the block B any two blocks Ba and Bb that are
 * identified by their start and end slots that overlap or are not more than S slots apart, until there are no more
 * blocks that could be merged.
 * (Ba.end + S ≥ Bb.start) ∧ (Bb.end + S ≥ Ba.start) ⇒ (B.start = min(Ba.start, Bb.start)) ∧ (B.end = max(Ba.end, Bb.end))
 * When the constraint is soft, the penalty is multiplied by the total number of additional breaks computed over each
 * day of the week and week of the semester and divided by the number of weeks of the semester at the end (using integer
 * division, just like for {@link MaxDayLoad}.</p>
 */
public class MaxBreaks extends DistributionConstraint {
    private final int maxBreakCount, extendedBreakLength; // R & S

    private record Block(int start, int end) {
    }

    /**
     * Constructs a max breaks distribution constraint object with the given classes.
     *
     * @param classes             Classes that this distribution constraint applies to.
     * @param maxBreakCount       The maximum allowed number of extended breaks per day.
     * @param extendedBreakLength The minimum length (number of time slots) required to consider a break, an extended
     *                            break.
     * @throws IllegalArgumentException If the passed maxBreakCount or extendedBreakLength is negative.
     */
    public MaxBreaks(Class[] classes, int maxBreakCount, int extendedBreakLength) throws IllegalArgumentException {
        super(classes);
        if (maxBreakCount < 0)
            throw new IllegalArgumentException("The maximum allowed number of extended breaks in a day cannot be negative!");
        this.maxBreakCount = maxBreakCount;
        if (extendedBreakLength < 0)
            throw new IllegalArgumentException("The minimum length of an extended break cannot be negative!");
        this.extendedBreakLength = extendedBreakLength;
    }

    @Override
    public boolean isSatisfied(Timetable timetable) throws NullPointerException {
        // |MergeBlocks{(C.start, C.end) | (C.days and 2d) ≠ 0 ∧ (C.weeks and 2w) ≠ 0})| ≤ R + 1
        final int weeksLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().weeks().length, daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        Time eventTime;
        ArrayList<Block> dayEvents;
        int mergedBlockEnd, blocksCounter;
        // STEP 1: Finding all the events that take place in a day
        for (int w = 0; w < weeksLength; w++)
            for (int d = 0; d < daysLength; d++) {
                dayEvents = new ArrayList<>();
                blocksCounter = 0;
                for (Class aClass : getClasses()) {
                    eventTime = timetable.getEvent(aClass).getTimeAssignment().time();
                    if (eventTime.weeks()[w] && eventTime.days()[d])
                        dayEvents.add(new Block(eventTime.start(), eventTime.end()));
                }
                if (dayEvents.isEmpty()) break; // If no event today, go to the next day
                // STEP 2: Sorting all the events in a day based on their starting time and adding them to blocks
                dayEvents.sort(Comparator.comparingInt(block -> block.start));
                mergedBlockEnd = dayEvents.get(0).end;
                // STEP 3: Merging blocks and checking the number of merged blocks
                for (int i = 1; i < dayEvents.size(); i++) {
                    Block current = dayEvents.get(i);
                    if (mergedBlockEnd + extendedBreakLength < current.start) { // If no overlap, add to the number of merged blocks and start a new merged block
                        if (++blocksCounter > (maxBreakCount + 1)) return false;
                        mergedBlockEnd = current.end;
                    } else mergedBlockEnd = Math.max(mergedBlockEnd, current.end); // Else, merge the blocks
                }
            }
        return true;
    }

    @Override
    public int violationCount(Timetable timetable) throws NullPointerException {
        final int weeksLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().weeks().length, daysLength = timetable.getEvent(getClasses()[0]).getTimeAssignment().time().days().length;
        Time eventTime;
        ArrayList<Block> dayEvents;
        int mergedBlockEnd, blocksCounter, totalOverflow = 0;
        // STEP 1: Finding all the events that take place in a day
        for (int w = 0; w < weeksLength; w++)
            for (int d = 0; d < daysLength; d++) {
                dayEvents = new ArrayList<>();
                blocksCounter = 0;
                for (Class aClass : getClasses()) {
                    eventTime = timetable.getEvent(aClass).getTimeAssignment().time();
                    if (eventTime.weeks()[w] && eventTime.days()[d])
                        dayEvents.add(new Block(eventTime.start(), eventTime.end()));
                }
                if (dayEvents.isEmpty()) break; // If no event today, go to the next day
                // STEP 2: Sorting all the events in a day based on their starting time and adding them to blocks
                dayEvents.sort(Comparator.comparingInt(block -> block.start));
                mergedBlockEnd = dayEvents.get(0).end;
                // STEP 3: Merging blocks and checking the number of merged blocks
                for (int i = 1; i < dayEvents.size(); i++) {
                    Block current = dayEvents.get(i);
                    if (mergedBlockEnd + extendedBreakLength < current.start) { // If no overlap, add to the number of merged blocks and start a new merged block
                        if (++blocksCounter > (maxBreakCount + 1)) totalOverflow += (blocksCounter - maxBreakCount - 1);
                        mergedBlockEnd = current.end;
                    } else mergedBlockEnd = Math.max(mergedBlockEnd, current.end); // Else, merge the blocks
                }
            }
        return totalOverflow;
    }
}