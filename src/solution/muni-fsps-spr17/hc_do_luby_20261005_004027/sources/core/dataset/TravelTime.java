package dataset;

import java.util.Arrays;

/**
 * This singleton class represents the travel time between the rooms of the ITC 2019 dataset, which expresses the number
 * of timeslots needed to get from one room to other rooms.
 */
public class TravelTime {
    private static TravelTime travelTimeInstance = null;
    private final int[][] travelTimes;

    /**
     * Private constructor to initialize the travel times matrix with 0.
     *
     * @param roomCount The total number of rooms in the problem instance.
     * @throws NegativeArraySizeException If the provided room count is negative.
     */
    private TravelTime(int roomCount) throws NegativeArraySizeException {
        travelTimes = new int[roomCount][roomCount];
    }

    /**
     * Initializes (creates an instance for) the static constructor of this travel time singleton class. This method has
     * to be performed first (only once) to have the object of this class.
     *
     * @param roomNo The total number of rooms in the problem instance.
     * @return The object or instance of this singleton class.
     * @throws ExceptionInInitializerError If an object of this singleton class has already been created.
     */
    public static synchronized TravelTime createInstance(int roomNo) throws ExceptionInInitializerError {
        if (travelTimeInstance != null)
            throw new ExceptionInInitializerError("An object of this singleton class has already been created!");
        travelTimeInstance = new TravelTime(roomNo);
        return travelTimeInstance;
    }

    /**
     * Returns the singleton instance of travel time.
     *
     * @return The travel time instance
     * @throws NullPointerException If this singleton class has not yet been initialized or created.
     */
    public static synchronized TravelTime getInstance() throws NullPointerException {
        if (travelTimeInstance == null)
            throw new NullPointerException("An object of this singleton class must be created first (using the createInstance method)!");
        return travelTimeInstance;
    }

    @Override
    public String toString() {
        return "TravelTime{" + "travelTimes=" + Arrays.toString(travelTimes) + '}';
    }

    /**
     * Gets the travel time between two rooms.
     *
     * @param room1 The first room.
     * @param room2 The second room.
     * @return The number of timeslots needed to go from room 1 to room 2.
     * @throws IndexOutOfBoundsException If room indices are out of bounds.
     */
    public int getTravelTime(Room room1, Room room2) throws IndexOutOfBoundsException {
        return travelTimes[room1.id() - 1][room2.id() - 1];
    }

    /**
     * Sets the travel time between two rooms.
     *
     * @param room1     The first room.
     * @param room2     The second room.
     * @param timeslots The number of timeslots needed to travel between the rooms.
     * @throws IllegalArgumentException  If the number of timeslots is negative.
     * @throws IndexOutOfBoundsException If room indices are out of bounds.
     */
    public void setTravelTime(Room room1, Room room2, int timeslots) throws IllegalArgumentException, IndexOutOfBoundsException {
        if (timeslots < 0)
            throw new IllegalArgumentException("The number of timeslots needed to travel between two rooms cannot be negative!");
        travelTimes[room1.id() - 1][room2.id() - 1] = timeslots;
        travelTimes[room2.id() - 1][room1.id() - 1] = timeslots;
    }

    /**
     * Gets the number of rows of the travel time matrix.
     *
     * @return Travel time row count.
     */
    int getRowCount() {
        return travelTimes.length;
    }
}