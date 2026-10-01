package dataset;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <p>This class represents an event. Event is simply defined as a scheduled class, i.e. a class that has been assigned
 * a time slot, a room (if required), and a list of students.</p>
 * <p><strong>Note that at any given moment, an event can be half or not scheduled and its time, room, and student
 * parameters can be null.</strong></p>
 */
public class Event {
    private final Class theClass;
    private TimeAssignment timeAssignment;
    private RoomAssignment roomAssignment;
    private final ArrayList<Student> students;

    /**
     * Constructs an empty (not scheduled) event object for the given class.
     *
     * @param theClass The class to be scheduled.
     */
    public Event(Class theClass) {
        this.theClass = theClass;
        students = new ArrayList<>();
    }

    /**
     * Gets the class of this event.
     *
     * @return This event's class to be scheduled.
     */
    public Class getTheClass() {
        return theClass;
    }

    /**
     * Gets the current time assigned to this event.
     *
     * @return Time assignment of this event. Returns null if this event is not assigned a time.
     */
    public TimeAssignment getTimeAssignment() {
        return timeAssignment;
    }

    /**
     * Sets the time of this event.
     *
     * @param timeAssignment The time to be assigned to this event.
     * @throws IllegalArgumentException If the passed time is invalid for this event.
     */
    public void setTimeAssignment(TimeAssignment timeAssignment) throws IllegalArgumentException {
        //TODO OPTIMIZATION: For faster running time, comment the following if. However, doing so will allow invalid (not included in the possible time assignments of this event's class) time assignments.
        if (!Arrays.asList(theClass.possibleTimes()).contains(timeAssignment))
            throw new IllegalArgumentException("The passed time does not exist in the possible time assignments of this class!");
        // COMMENT UNTIL HERE!
        this.timeAssignment = timeAssignment;
    }

    /**
     * Gets the current room assigned to this event. <strong>Note that some classes (as defined) do not require a room.
     * </strong>
     *
     * @return Room assignment of this event. Returns null if this event is not assigned a room or does not require a
     * room.
     */
    public RoomAssignment getRoomAssignment() {
        return roomAssignment;
    }

    /**
     * Sets the room of this event.
     *
     * @param roomAssignment The room to be assigned to this event.
     * @throws IllegalArgumentException If the passed room is invalid for this event or this event's class does not need
     *                                  a room.
     */
    public void setRoomAssignment(RoomAssignment roomAssignment) throws IllegalArgumentException {
        //TODO OPTIMIZATION: For faster running time, comment the following if statements. However, doing so will allow invalid (not included in the possible room assignments of this event's class) room assignments.
        if (theClass.possibleRooms() == null) throw new IllegalArgumentException("This event does not require a room!");
        if (students.size() > roomAssignment.room().capacity())
            throw new IllegalArgumentException("The passed room does not have enough capacity for the current participant size of this event!");
        if (!Arrays.asList(theClass.possibleRooms()).contains(roomAssignment))
            throw new IllegalArgumentException("The passed room does not exist in the possible room assignments of this class!");
        // COMMENT UNTIL HERE!
        this.roomAssignment = roomAssignment;
    }

    @Override
    public String toString() {
        return "Event{" + "theClass=" + theClass + ", timeAssignment=" + timeAssignment + ", roomAssignment=" + roomAssignment + ", students=" + students + '}';
    }

    /**
     * Adds (enrolls) the passed student to this event. <strong> This method does not check that the student has taken
     * one class from each subpart of a single configuration. This should be checked when adding the students based on
     * their requested courses.</strong>
     *
     * @param student   The student to be added to this event.
     * @param timetable The timetable of this event to check if the passed student is enrolled in the corresponding
     *                  event of the parent class of this event's class.
     * @throws IllegalStateException    If the class or the current room of this event is at max size.
     * @throws IllegalArgumentException If the student is already enrolled in this event, did not request this class in
     *                                  the first place, or has not taken the parent class first. <strong>To
     *                                  significantly reduce the running time of this method from O(n^4) to O(n), the
     *                                  student needing this event check has been commented in this method. MAKE SURE
     *                                  THE PASSED STUDENT HAS THE CLASS OF THIS EVENT IN HIS/HER REQUESTED COURSES.
     *                                  OTHERWISE, UNCOMMENT THE RESPONSIBLE IF STATEMENT IN THIS METHOD</strong>
     */
    public void addStudent(Student student, Timetable timetable) throws IllegalStateException, IllegalArgumentException {
        //TODO OPTIMIZATION: For faster running time, comment the following if statements. However, doing so will allow invalid student enrollments.
        if (students.contains(student))
            throw new IllegalArgumentException("The passed student is already in this event!");
        if (students.size() == theClass.limit())
            throw new IllegalStateException("The corresponding class of this event has reached its limit size!");
        if (roomAssignment != null && students.size() == roomAssignment.room().capacity())
            throw new IllegalStateException("The current assigned room to this event has reached its maximum capacity!");
        if (theClass.parent() != null && !timetable.getEvent(theClass.parent()).students.contains(student))
            throw new IllegalArgumentException("The passed student needs to take the parent class first!");
//        if (!student.needsClass(theClass))
//            throw new IllegalArgumentException("The student did not request this class!");
        // COMMENT UNTIL HERE!
        students.add(student);
    }

    public RoomAssignment[] getAvailableRooms() {
        ArrayList<RoomAssignment> availableRooms = new ArrayList<>();
        boolean haveAvailable = false;
        for (RoomAssignment r : theClass.possibleRooms()) {
            if (r.room().isAvailable(timeAssignment.time())) {
                availableRooms.add(r);
                haveAvailable = true;
            }
        }
        if (!haveAvailable)
            return null;
        else
            return availableRooms.toArray(new RoomAssignment[0]);
    }

    public Event deepCopy() {
        Event copy = new Event(theClass);
        if (this.timeAssignment != null)
            copy.timeAssignment = this.timeAssignment.deepCopy();
        else copy.roomAssignment = null;
        if (this.roomAssignment != null)
            copy.roomAssignment = this.roomAssignment.deepCopy();
        else copy.roomAssignment = null;
        return copy;
    }
}