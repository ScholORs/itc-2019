package dataset;

import dataset.constraints.HardConstraint;
import dataset.constraints.SoftConstraint;

import java.util.Arrays;
import java.util.Comparator;

/**
 * This class represents a problem instance of the International Timetabling Competition (ITC) 2019 dataset.
 */
public record ProblemInstance(String instanceName, int nrDays, int nrWeeks, int slotsPerDay, int timePenaltyWeight,
                              int roomPenaltyWeight, int distributionPenaltyWeight, int studentPenaltyWeight,
                              Room[] rooms, Course[] courses, HardConstraint[] hardConstraints,
                              SoftConstraint[] softConstraints, Student[] students, TravelTime travelTimes,
                              Class[] classes) {
    /**
     * Constructs a new problem instance object with the provided parameters and performs validation checks.
     *
     * @param instanceName              The name of the problem instance.
     * @param nrDays                    The number of days in a week.
     * @param nrWeeks                   The number of weeks in a semester.
     * @param slotsPerDay               The number of time slots per day.
     * @param timePenaltyWeight         The weight for time-related penalties.
     * @param roomPenaltyWeight         The weight for room-related penalties.
     * @param distributionPenaltyWeight The weight for distribution-related penalties.
     * @param studentPenaltyWeight      The weight for student-related penalties.
     * @param rooms                     An array of rooms available for scheduling.
     * @param courses                   An array of courses to be scheduled.
     * @param hardConstraints           An array of hard constraints to be satisfied.
     * @param softConstraints           An array of soft constraints to be optimized.
     * @param students                  An array of students and their preferences.
     * @param travelTimes               The travel time matrix between rooms.
     * @param classes                   All the classes in the problem instance.
     * @throws IllegalArgumentException If the provided parameters violate the specified constraints.
     **/
    public ProblemInstance {
        // TODO: Do I need to add a check for ids being sequential and unique?
        if (nrDays < 1 || nrDays > 7)
            throw new IllegalArgumentException("The number of days in a week should be between 1 and 7!");
        if (nrWeeks < 1) throw new IllegalArgumentException("The number of weeks in a semester cannot be less than 1!");
        if (slotsPerDay < 1 || slotsPerDay > 288)
            throw new IllegalArgumentException("The number of slots (each slot is 5 mins) per day should be between 1 and 288!");
        if (timePenaltyWeight < 0) throw new IllegalArgumentException("The time penalty weight cannot be negative!");
        if (roomPenaltyWeight < 0) throw new IllegalArgumentException("The room penalty weight cannot be negative!");
        if (distributionPenaltyWeight < 0)
            throw new IllegalArgumentException("The distribution penalty weight cannot be negative!");
        if (studentPenaltyWeight < 0)
            throw new IllegalArgumentException("The student penalty weight cannot be negative!");
        Arrays.sort(classes, Comparator.comparingInt(Class::id));
    }

    /**
     * Prints a summary of the problem instance for debugging and verification purposes.
     * It includes structural parameters, penalty weights, and counts of entities and constraints.
     * Null-safe: handles cases where any array or object might not have been initialized.
     */
    public void printStats() {
        System.out.println("========== Problem Instance Summary ==========");
        System.out.println("Instance Name         : " + instanceName);
        System.out.println("Days per Week         : " + nrDays);
        System.out.println("Weeks                 : " + nrWeeks);
        System.out.println("Slots per Day         : " + slotsPerDay);
        System.out.println();

        System.out.println("Penalty Weights:");
        System.out.println("  Time Penalty        : " + timePenaltyWeight);
        System.out.println("  Room Penalty        : " + roomPenaltyWeight);
        System.out.println("  Distribution Penalty: " + distributionPenaltyWeight);
        System.out.println("  Student Penalty     : " + studentPenaltyWeight);
        System.out.println();

        System.out.println("Entity Counts:");
        System.out.println("  Rooms               : " + (rooms != null ? rooms.length : "Not Loaded"));
        System.out.println("  Courses             : " + (courses != null ? courses.length : "Not Loaded"));
        System.out.println("  Students            : " + (students != null ? students.length : "Not Loaded"));
        System.out.println("  Hard Constraints    : " + (hardConstraints != null ? hardConstraints.length : "Not Loaded"));
        System.out.println("  Soft Constraints    : " + (softConstraints != null ? softConstraints.length : "Not Loaded"));
        System.out.println("  Classes             : " + (classes != null ? classes.length : "Not Loaded"));

        int totalConstraints = (hardConstraints != null ? hardConstraints.length : 0) + (softConstraints != null ? softConstraints.length : 0);
        boolean constraintsLoaded = hardConstraints != null || softConstraints != null;
        System.out.println("  Total Constraints   : " + (constraintsLoaded ? totalConstraints : "Not Loaded"));
        System.out.println();

        System.out.println("Travel Time Matrix     : " + (travelTimes != null ? travelTimes.getRowCount() + " rows" : "Not Loaded"));
        System.out.println("==============================================");
    }
}