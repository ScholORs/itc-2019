package dataset;

import utils.LogicalOperators;

/**
 * <p>This class represents rooms as defined in the ITC 2019. Each room is specified by its id and capacity. A room may
 * not be available at certain times, which are defined by unavailable elements using the days of the week, the start
 * time slot, and a length in slots for a set of weeks during the semester. Non-zero travel times from other rooms are
 * specified by the travel elements, which express the number of time slots it takes to get from one room to another.
 * Travel times are expected to be symmetric and are listed only on one of the rooms.</p>
 * <p>A class cannot be placed in a room when its assigned time overlaps with an unavailability of the room or when
 * there is some other class placed in the room at an overlapping time. Besides available times, each class also has a
 * list of rooms where it can be placed. Each class needs one time and one room assignment from its list of possible
 * assignments. It is possible for a class to only need a time assignment and no room. In this case, there are no rooms
 * listed and the room attribute of the class is set to false.</p>
 * <p><strong>IMPORTANT: For efficiency and optimization, travel times are implemented and stored in the Instance class
 * rather than here.</strong></p>
 */
public record Room(int id, int capacity, Time[] unavailable) {
    /**
     * Constructs a Room object.
     *
     * @param id          The id of the room.
     * @param capacity    The capacity of the room.
     * @param unavailable The unavailable time periods of the room.
     * @throws IllegalArgumentException If the passed id and capacity parameters are invalid.
     */
    public Room {
        if (id < 1) throw new IllegalArgumentException("Room id cannot be less than 1!");
        if (capacity < 0) throw new IllegalArgumentException("Room capacity cannot be negative!");
    }

    public boolean isAvailable(Time time) {
        for (Time t : unavailable) {
            if ((time.start() < t.end()) && (t.start() < time.end()) && (!LogicalOperators.areExclusive(t.days(), time.days())) && (!LogicalOperators.areExclusive(t.weeks(), time.weeks())))
                return false;
        }
        return true;
    }
}