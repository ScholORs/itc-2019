package dataset;

/**
 * <p>This class represents time as defined in the ITC 2019. Each problem has a certain number of weeks nrWeeks, a
 * number of days nrDays in each week, and a number of time slots per day slotsPerDay. These parameters, together with
 * the instance name, are defined in the root element of the XML file.</p>
 * <p>While the data format allows for variations, in all the competition instances one time slot takes 5 minutes, which
 * allows us to model travel times as well as classes that meet at irregular times. There are 288 time slots covering
 * the whole day, from midnight to midnight.</p>
 * <p>A class can meet several times a week during some or all weeks of the semester. All meetings of a class start at
 * the same time, run for the same number of slots, and are placed in the same room. Each time is specified by its
 * starting time slot start within a day and have a duration length given as a number of time slots. Days and weeks with
 * a meeting are specified using the days and weeks binary strings. For example, days 1010100 means that the class meets
 * three times a week (on Monday, Wednesday, and Friday) each week when it is meeting. Similarly, weeks 0101010101010
 * specifies that the class would only meet during even weeks of the semester (during the 2nd, 4th, . . . , and 12th
 * weeks of the semester).</p>
 */
public record Time(boolean[] weeks, boolean[] days, int start, int duration, int end) {
    /**
     * Constructs a Time object. <strong>To ensure the weeks and days arrays are valid, make sure you pass the nrWeeks
     * and nrDays parameters when creating instances.</strong>
     *
     * @param nrWeeks  The number of weeks in a semester as defined in the problem instance.
     * @param nrDays   The number of days in a week as defined in the problem instance.
     * @param weeks    The weeks that this time period takes place.
     * @param days     The days that this time period takes place.
     * @param start    The starting time slot of this time period.
     * @param duration The duration of this time period.
     * @throws IllegalArgumentException If the passed parameters are invalid.
     */
    public Time(int nrWeeks, int nrDays, boolean[] weeks, boolean[] days, int start, int duration) throws IllegalArgumentException {
        this(weeks, days, start, duration, start + duration);
        if (weeks.length != nrWeeks)
            throw new IllegalArgumentException("The passed weeks array is not of the same as the number of weeks in a semester defined in the problem instance!");
        if (days.length != nrDays)
            throw new IllegalArgumentException("The passed days array is not of the same as the number of days in a week defined in the problem instance");
        if (start < 0 || duration <= 0 || (start + duration > 288))
            throw new IllegalArgumentException("This start and/or length parameter(s) are illegal!");
    }
}