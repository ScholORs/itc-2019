package dataset.constraints;

import dataset.Class;
import dataset.Event;
import utils.LogicalOperators;

/**
 * <p>This constraint establishes an ordered listing which requires that the first meeting of each of the listed classes
 * occurs in its entirety prior to the first meeting of all subsequently listed classes.</p>
 * <p>Given classes must be one after the other in the order provided in the constraint definition. For classes that
 * have multiple meetings in a week or that are on different weeks, the constraint only cares about the first meeting of
 * the class. That is,
 * -the first class starts on an earlier week or
 * -they start on the same week and the first class starts on an earlier day of the week or
 * -they start on the same week and day of the week and the first class is earlier in the day.
 * This means that
 * (first(Ci.weeks) < first(Cj.weeks)) ∨
 * [ (first(Ci.weeks) = first(Cj.weeks)) ∧
 * [ (first(Ci .days) < first(Cj .days)) ∨
 * ((first(Ci.days) = first(Cj.days)) ∧ (Ci.end ≤ Cj.start))
 * ]
 * ]
 * for any two classes Ci and Cj from the constraint where i < j and first(x) is the index of the first non-zero bit in
 * the binary string x.</p>
 */
public class Precedence extends PairDistributionConstraint {
    /**
     * Constructs a paired precedence distribution constraint object with the given classes.
     *
     * @param classes Classes that this distribution constraint applies to.
     */
    public Precedence(Class[] classes) {
        super(classes);
    }

    @Override
    boolean check(Event e1, Event e2) throws NullPointerException {
        // (first(Ci.weeks) < first(Cj.weeks)) ∨
        //   [ (first(Ci.weeks) = first(Cj.weeks)) ∧
        //     [ (first(Ci .days) < first(Cj .days)) ∨
        //       ((first(Ci.days) = first(Cj.days)) ∧ (Ci.end ≤ Cj.start))
        //     ]
        //  ]
        int e1WeeksFirst = LogicalOperators.firstTrueIndex(e1.getTimeAssignment().time().weeks());
        int e2WeeksFirst = LogicalOperators.firstTrueIndex(e2.getTimeAssignment().time().weeks());
        int e1DaysFirst = LogicalOperators.firstTrueIndex(e1.getTimeAssignment().time().days());
        int e2DaysFirst = LogicalOperators.firstTrueIndex(e2.getTimeAssignment().time().days());
        return e1WeeksFirst < e2WeeksFirst
                || (e1WeeksFirst == e2WeeksFirst
                && (e1DaysFirst < e2DaysFirst
                || (e1DaysFirst == e2DaysFirst && e1.getTimeAssignment().time().end() <= e2.getTimeAssignment().time().start())));
    }
}