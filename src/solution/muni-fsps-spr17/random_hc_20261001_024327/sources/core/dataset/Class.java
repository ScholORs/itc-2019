package dataset;

import java.util.Arrays;
import java.util.Comparator;

/**
 * <p>This class represents classes as defined in the ITC 2019. Each student must attend one class from each subpart of
 * a single configuration. All students in the course configuration must be sectioned into classes of each subpart such
 * that their limit is not exceeded (one student attending configuration Lec-Rec must take one class from each of its
 * subparts 1 Lecture and 2 Recitation, e.g., Lec1 and Rec3). Each class has a unique id and belongs to one subpart
 * (classes Rec5, Rec6, Rec7, and Rec8 belong to subpart 4 Recitation).</p>
 * <p>A class may have a parent class defined which means that a student who attends the class must also attend its
 * parent class. For example, Lab3 has the parent Rec7 which has the parent Lec4. This means that a student attending
 * Lab3 must also attend Rec7 and Lec4 and no other combination including Lab3 is allowed. On the other hand, there is
 * no parent-child relationship between classes of subparts 1 Lecture and 2 Recitation, so a student may take any
 * lecture Lec1 or Lec2 and any recitation Rec1, Rec2, Rec3 or Rec4.</p>
 * <p>Each class has a defined set of possible times when it can meet. Each eligible time has a specified penalty which
 * must be included in the overall time penalization when the time is selected for the class. Valid time specifications
 * were described in Section Times. Each class also has a defined a set of possible rooms where it can meet (if a room
 * is needed). Each eligible room has a specified penalty to be included in the overall time penalization when selected.
 * Valid room specifications were given in Section Rooms.</p>
 */
public record Class(int id, int limit, TimeAssignment[] possibleTimes, RoomAssignment[] possibleRooms, Class parent) {
    /**
     * Constructs a Class object and sorts the possible time and room assignments based on their respective penalties.
     *
     * @param id            The id of this class.
     * @param limit         The limit (capacity) of this class.
     * @param possibleTimes The possible time periods for this class.
     * @param possibleRooms The possible room assignments for this class. <strong>If this class does not require a room,
     *                      pass null.</strong>
     * @param parent        The parent class of this class. <strong>If this class is not in a parent-child relationship,
     *                      pass null.</strong>
     * @throws IllegalArgumentException If the passed id or limit parameters are invalid.
     */
    public Class {
        if (id < 1) throw new IllegalArgumentException("Class id cannot be less than 1!");
        if (limit < 0) throw new IllegalArgumentException("Class limit cannot be negative!");
        Arrays.sort(possibleTimes, Comparator.comparingInt(TimeAssignment::penalty));
        if (possibleRooms != null) Arrays.sort(possibleRooms, Comparator.comparingInt(RoomAssignment::penalty));
    }
}