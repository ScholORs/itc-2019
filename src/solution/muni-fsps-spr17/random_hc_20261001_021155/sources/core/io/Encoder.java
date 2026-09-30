package io;

import dataset.*;
import dataset.Class;
import dataset.constraints.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Encoder extends DefaultHandler {
    private String instanceName;
    private int nrDays;
    private int nrWeeks;
    private int slotsPerDay;
    private int timePenaltyWeight;
    private int roomPenaltyWeight;
    private int distributionPenaltyWeight;
    private int studentPenaltyWeight;

    private List<Room> roomList;
    private List<Time> unavailableTimes;
    private Room[] rooms;
    private Room room;
    private Time time;
    private int roomId;
    private int roomCapacity;
    private boolean inRooms;

    private List<TravelEntry> travelEntries = new ArrayList<>();
    private static TravelTime travelTime;
    private Time[] unavailable;

    private List<Course> courseList;
    private List<Config> configList;
    private List<Subpart> subpartList;
    private List<Class> classList;
    private List<Class> allClassList;
    private Course course;
    private Course[] courses;
    private Config config;
    private Config[] configs;
    private Subpart subpart;
    private Subpart[] subparts;
    private Class aClass;
    private Class[] classes;
    private Class[] allClasses;
    private boolean inCourses;
    private int classId;
    private int limit;
    private int subpartId;
    private int configId;
    private int courseId;
    private boolean needRoom;
    private int maxRoomId;

    private List<RoomAssignment> roomAssignmentList;
    private List<TimeAssignment> timeAssignmentList;
    private RoomAssignment roomAssignment;
    private RoomAssignment[] roomAssignments;
    private TimeAssignment timeAssignment;
    private TimeAssignment[] timeAssignments;

    private List<NoCourse> noCourseList;
    private List<NoConfig> noConfigList;
    private List<NoSubpart> noSubpartList;
    private List<NoClass> noClassList;
    private List<NoClass> parentClassList;
    private NoCourse noCourse;
    private NoCourse[] noCourses;
    private NoConfig noConfig;
    private NoConfig[] noConfigs;
    private NoSubpart noSubpart;
    private NoSubpart[] noSubparts;
    private NoClass noClass;
    private NoClass[] noClasses;
    private boolean isParent;
    private int parent;
    private NoClass parentClass;
    private NoClass[] parentClasses;
    private ArrayList<NoClass> noAllArray = new ArrayList<>();
    private ArrayList<Class> realParentList = new ArrayList<>();

    private List<Student> studentList;
    private List<Course> stuCourseList;
    private Student student;
    private Student[] students;
    private Course stuCourse;
    private Course[] stuCourses;
    private boolean inStudents;
    private boolean haveStudents;
    private int studentId;
    private int stuCourseId;

    private List<HardConstraint> hardConstraintList;
    private List<SoftConstraint> softConstraintList;
    private HardConstraint hardConstraint;
    private SoftConstraint softConstraint;
    private HardConstraint[] hardConstraints;
    private SoftConstraint[] softConstraints;
    private Class[] disClasses;
    private boolean inDistributions;
    private boolean isHard;
    private int disPenalty;
    private boolean haveHard;
    private boolean haveSoft;
    private String type;
    private DifferentDays differentDays;
    private DifferentRoom differentRoom;
    private DifferentTime differentTime;
    private DifferentWeeks differentWeeks;
    private MaxBlock maxBlock;
    private MaxBreaks maxBreaks;
    private MaxDayLoad maxDayLoad;
    private MaxDays maxDays;
    private MinGap minGap;
    private NotOverlap notOverlap;
    private Overlap overlap;
    private Precedence precedence;
    private SameAttendees sameAttendees;
    private SameDays sameDays;
    private SameRoom sameRoom;
    private SameStart sameStart;
    private SameTime sameTime;
    private SameWeeks sameWeeks;
    private WorkDay workDay;

    private ProblemInstance problemInstance;

    /**
     * Creates a new Encoder instance and parses the XML file at the given file path.
     * 
     * @param filePath Path to the XML file to be parsed
     * @throws ParserConfigurationException If there is a configuration error with the SAX parser
     * @throws SAXException If there is an error during XML parsing
     * @throws IOException If there is an error reading the file
     */
    public Encoder(String filePath) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        saxParser.parse(new File(filePath), this);

        createProblemInstance();
    }

    private void createProblemInstance() {
        problemInstance = new ProblemInstance(instanceName, nrDays, nrWeeks, slotsPerDay, timePenaltyWeight, roomPenaltyWeight, distributionPenaltyWeight, studentPenaltyWeight, rooms, courses, hardConstraints, softConstraints, students, travelTime, allClasses);
    }

    /**
     * Returns the parsed problem instance.
     * 
     * @return The ProblemInstance object created from the parsed XML file
     */
    public ProblemInstance getProblemInstance() {
        return problemInstance;
    }
    /**
     * Called when the SAX parser encounters an opening tag in the XML document.
     * This method processes the start element and its attributes.
     * 
     * @param uri The namespace URI, or an empty string if the element has no namespace URI
     * @param localName The local name (without prefix), or an empty string if namespace processing is not being performed
     * @param qName The qualified name (with prefix)
     * @param attributes The attributes attached to the element
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        // If we find the <problem...
        if ("problem".equalsIgnoreCase(qName)) {
            handleProblem(attributes);
        }
        else if ("optimization".equalsIgnoreCase(qName)) {
            handleOptimization(attributes);
        }
        else if ("rooms".equalsIgnoreCase(qName)) {
            handleRoomsStart();
        }
        else if (inRooms) {
            handleRoomElements(qName, attributes);
        }
        else if ("courses".equalsIgnoreCase(qName)) {
            inCourses = true;
            noCourseList = new ArrayList<>();
        }
        else if (inCourses) {
            if ("course".equalsIgnoreCase(qName)) {
                courseId = Integer.parseInt(attributes.getValue("id"));
                noConfigList = new ArrayList<>();
            }
            else if ("config".equalsIgnoreCase(qName)) {
                configId = Integer.parseInt(attributes.getValue("id"));
                noSubpartList = new ArrayList<>();
            }
            else if ("subpart".equalsIgnoreCase(qName)) {
                subpartId = Integer.parseInt(attributes.getValue("id"));
                noClassList = new ArrayList<>();
            }
            else if ("class".equalsIgnoreCase(qName)) {
                roomAssignmentList = new ArrayList<>();
                timeAssignmentList = new ArrayList<>();
                classId = Integer.parseInt(attributes.getValue("id"));
                limit = Integer.parseInt(attributes.getValue("limit"));
                String roomValue = attributes.getValue("room");
                if (roomValue == null) needRoom = true;
                else needRoom = false;
                String parentValue = attributes.getValue("parent");
                if (parentValue != null) {
                    parent = Integer.parseInt(parentValue);
                    isParent = true;
                }
                else {
                    parent = -1;
                    isParent = false;
                }
            }
            else if ("room".equalsIgnoreCase(qName)) {
                int rId = Integer.parseInt(attributes.getValue("id"));
                int penalty = Integer.parseInt(attributes.getValue("penalty"));
                Room courseRoom = null;
                for (Room r : rooms) {
                    if (r.id() == rId) {
                        courseRoom = r;
                        break;
                    }
                }
                roomAssignment = new RoomAssignment(courseRoom, penalty);
                roomAssignmentList.add(roomAssignment);
            }
            else if ("time".equalsIgnoreCase(qName)) {
                String daysStr = attributes.getValue("days");
                boolean[] days = parseBooleanArray(daysStr);
                int start = Integer.parseInt(attributes.getValue("start"));
                int length = Integer.parseInt(attributes.getValue("length"));
                String weeksStr = attributes.getValue("weeks");
                boolean[] weeks = parseBooleanArray(weeksStr);
                int penalty = Integer.parseInt(attributes.getValue("penalty"));
                time = new Time(nrWeeks, nrDays, weeks, days, start, length);
                timeAssignment = new TimeAssignment(time, penalty);
                timeAssignmentList.add(timeAssignment);
            }
        }
        else if ("distributions".equalsIgnoreCase(qName)) {
            haveHard = false;
            haveSoft = false;
            inDistributions = true;
            hardConstraintList = new ArrayList<>();
            softConstraintList = new ArrayList<>();
        }
        else if (inDistributions) {
            if ("distribution".equalsIgnoreCase(qName)) {
                classList = new ArrayList<>();
                type = attributes.getValue("type");
                String requiredValue = attributes.getValue("required");
                String penaltyValue = attributes.getValue("penalty");
                if (requiredValue != null) {
                    isHard = true;
                }
                else if (penaltyValue != null) {
                    isHard = false;
                    disPenalty = Integer.parseInt(attributes.getValue("penalty"));
                }
            }
            else if ("class".equalsIgnoreCase(qName)) {
                int classId = Integer.parseInt(attributes.getValue("id"));
                Class disClass = null;
                for (Class c : allClasses) {
                    if (c.id() == classId) {
                        disClass = c;
                        break;
                    }
                }
                classList.add(disClass);
            }
        }
        else if ("students".equalsIgnoreCase(qName)) {
            inStudents = true;
            studentList = new ArrayList<>();
        }
        else if (inStudents) {
            if ("student".equalsIgnoreCase(qName)) {
                haveStudents = true;
                studentId = Integer.parseInt(attributes.getValue("id"));
                stuCourseList = new ArrayList<>();
            }
            else if ("course".equalsIgnoreCase(qName)) {
                haveStudents = true;
                stuCourseId = Integer.parseInt(attributes.getValue("id"));
                for (Course c : courses) {
                    if (c.id() == stuCourseId) {
                        stuCourse = c;
                        break;
                    }
                }
                stuCourseList.add(stuCourse);
            }
        }
    }

    /**
     * Handle the problem tag
     */
    private void handleProblem(Attributes attributes) {
        instanceName = attributes.getValue("name");
        nrDays = Integer.parseInt(attributes.getValue("nrDays"));
        nrWeeks = Integer.parseInt(attributes.getValue("nrWeeks"));
        slotsPerDay = Integer.parseInt(attributes.getValue("slotsPerDay"));
    }

    /**
     * Handle the optimization tag
     */
    private void handleOptimization(Attributes attributes) {
        timePenaltyWeight = Integer.parseInt(attributes.getValue("time"));
        roomPenaltyWeight = Integer.parseInt(attributes.getValue("room"));
        distributionPenaltyWeight = Integer.parseInt(attributes.getValue("distribution"));
        studentPenaltyWeight = Integer.parseInt(attributes.getValue("student"));
    }

    /**
     * Initialize room related lists
     */
    private void handleRoomsStart() {
        inRooms = true;
        roomList = new ArrayList<>();
        travelEntries = new ArrayList<>();
    }

    /**
     * Handle elements inside rooms tag
     */
    private void handleRoomElements(String qName, Attributes attributes) {
        if ("room".equalsIgnoreCase(qName)) {
            roomId = Integer.parseInt(attributes.getValue("id"));
            roomCapacity = Integer.parseInt(attributes.getValue("capacity"));
            unavailableTimes = new ArrayList<>();
        }
        else if ("travel".equalsIgnoreCase(qName)) {
            int travelRoom = Integer.parseInt(attributes.getValue("room"));
            int travelValue = Integer.parseInt(attributes.getValue("value"));
            // Add the travel value to the ArrayList first
            travelEntries.add(new TravelEntry(roomId, travelRoom, travelValue));
        }
        else if ("unavailable".equalsIgnoreCase(qName)) {
            String daysStr = attributes.getValue("days");
            boolean[] days = parseBooleanArray(daysStr);
            int start = Integer.parseInt(attributes.getValue("start"));
            int length = Integer.parseInt(attributes.getValue("length"));
            String weeksStr = attributes.getValue("weeks");
            boolean[] weeks = parseBooleanArray(weeksStr);
            time = new Time(nrWeeks, nrDays, weeks, days, start, length);
            unavailableTimes.add(time);
        }
    }

    /**
     * Called when the SAX parser encounters a closing tag in the XML document.
     * This method processes the end element based on the current parsing context.
     *
     * @param uri The namespace URI, or an empty string if the element has no namespace URI
     * @param localName The local name (without prefix), or an empty string if namespace processing is not being performed
     * @param qName The qualified name (with prefix)
     */
    @Override
    public void endElement(String uri, String localName, String qName) {
        if (inRooms) {
            handleRoomsEnd(qName);
        }
        else if (inCourses) {
            handleCoursesEnd(qName);
        }
        else if (inDistributions) {
            handleDistributionsEnd(qName);
        }
        else if (inStudents) {
            handleStudentsEnd(qName);
        }
        else if ("problem".equalsIgnoreCase(qName)) {
        }
    }

    /**
     * Handle end tags for rooms
     */
    private void handleRoomsEnd(String qName) {
        if ("room".equalsIgnoreCase(qName)) {
            unavailable = unavailableTimes.toArray(new Time[0]);
            room = new Room(roomId, roomCapacity, unavailable);
            roomList.add(room);
        }
        else if ("rooms".equalsIgnoreCase(qName)) {
            inRooms = false;
            rooms = roomList.toArray(new Room[0]);
            for (Room r : rooms) {
                maxRoomId = r.id();
            }
            travelTime = TravelTime.createInstance(maxRoomId);
            fillTravelMatrix();
        }
    }

    /**
     * Handle end tags for courses
     */
    private void handleCoursesEnd(String qName) {
        if ("class".equalsIgnoreCase(qName)) {
            if (needRoom) roomAssignments = roomAssignmentList.toArray(new RoomAssignment[0]);
            else roomAssignments = null;
            timeAssignments = timeAssignmentList.toArray(new TimeAssignment[0]);
            noClass = new NoClass(classId, limit, timeAssignments, roomAssignments, isParent, parent, false);
            noClassList.add(noClass);
            noAllArray.add(noClass);
        }
        else if ("subpart".equalsIgnoreCase(qName)) {
            noClasses = noClassList.toArray(new NoClass[0]);
            noSubpart = new NoSubpart(subpartId, noClasses);
            noSubpartList.add(noSubpart);
        }
        else if ("config".equalsIgnoreCase(qName)) {
            noSubparts = noSubpartList.toArray(new NoSubpart[0]);
            noConfig = new NoConfig(configId, noSubparts);
            noConfigList.add(noConfig);
        }
        else if ("course".equalsIgnoreCase(qName)) {
            noConfigs = noConfigList.toArray(new NoConfig[0]);
            noCourse = new NoCourse(courseId, noConfigs);
            noCourseList.add(noCourse);
        }
        else if ("courses".equalsIgnoreCase(qName)) {
            inCourses = false;
            noCourses = noCourseList.toArray(new NoCourse[0]);
            createParentClass();
            courseList = new ArrayList<>();
            allClassList = new ArrayList<>();
            fillCourses();
            courses = courseList.toArray(new Course[0]);
            allClasses = allClassList.toArray(new Class[0]);
        }
    }

    /**
     * Returns a string representation of the problem instance.
     * This method prints all components of the problem instance and returns a null character.
     * 
     * @return A null character after printing the problem details
     */
    @Override
    public String toString() {
        printRooms();
        printTravelMatrix();
        printCourses();
        printDistribution();
        printStudents();
        return "\0";
    }

    /**
     * Print rooms information
     */
    public void printRooms() {
        if (rooms == null || rooms.length == 0) System.out.println("No rooms available!");
        else {
            System.out.println(" Rooms:(" + rooms.length + ") ");
            for (Room tRoom : rooms) {
                System.out.println(" Room ID: " + tRoom.id() + " Capacity: " + tRoom.capacity());
                for (Time tTime : tRoom.unavailable()) {
                    for (Boolean d : tTime.days()) {
                        if (d)
                            System.out.print(1);
                        else
                            System.out.print(0);
                    }
                    System.out.print(", " + tTime.start() + ", " + tTime.duration()  + ", ");
                    for (Boolean w : tTime.weeks()) {
                        if (w)
                            System.out.print(1);
                        else
                            System.out.print(0);
                    }
                    System.out.println();
                }
            }
        }
    }

    /**
     * Print travel time matrix
     */
    public void printTravelMatrix() {
        System.out.println(travelTime);
    }

    /**
     * Print courses information
     */
    public void printCourses() {
        for (Course i : courses) {
            System.out.println("Course: " + i.id());
            for (Config j : i.configs()) {
                System.out.println("Config: " + j.id());
                for (Subpart k : j.subparts()) {
                    System.out.println("Subpart: " + k.id());
                    for (Class m : k.classes()) {
                        System.out.print("Class: " + m.id() + ", " + "limit: " + m.limit());
                        if (m.parent() != null) System.out.print(", Parent: " + m.parent().id());
                        System.out.println();
                        if (m.possibleRooms() != null) {
                            for (RoomAssignment r : m.possibleRooms()) {
                                System.out.println("room: " + r.room().id() + ", penalty: " + r.penalty());
                            }
                        }
                        else System.out.println("Don't need Rooms!");
                        for (TimeAssignment t : m.possibleTimes()) {
                            System.out.print("time days: ");
                            for (Boolean d : t.time().days()) {
                                if (d)
                                    System.out.print(1);
                                else
                                    System.out.print(0);
                            }
                            System.out.print(", " + "start: " + t.time().start() + ", " + "Duration: " + t.time().duration() + ", ");
                            for (Boolean w : t.time().weeks()) {
                                if (w)
                                    System.out.print(1);
                                else
                                    System.out.print(0);
                            }
                            System.out.println(", penalty: " + t.penalty());
                        }
                    }
                }
            }
        }
    }

    /**
     * Print students information
     */
    public void printStudents() {
        if (students != null) {
            for (Student s : students) {
                System.out.println("Student: " + s.id());
                for (Course c : s.courses()) {
                    System.out.println("Course: " + c.id());
                }
            }
        }
        else System.out.println("No Students!");
    }

    /**
     * Print distribution constraints information
     */
    public void printDistribution() {
        if (haveHard) {
            System.out.println("HardConstraints:");
            for (HardConstraint h : hardConstraints) {
                System.out.println(h);
            }
        }
        else System.out.println("No HardConstraint!");
        if (haveSoft) {
            System.out.println("SoftConstraints:");
            for (SoftConstraint s : softConstraints) {
                System.out.println(s);
            }
        }
        else System.out.println("No SoftConstraint!");
    }
    // Create a class to store travel elements
    private static class TravelEntry {
        int fromRoomId;
        int toRoomId;
        int time;

        TravelEntry(int fromRoomId, int toRoomId, int time) {
            this.fromRoomId = fromRoomId;
            this.toRoomId = toRoomId;
            this.time = time;
        }
    }

    private void fillTravelMatrix() {
        for (TravelEntry entry : travelEntries) {
            int fromIndex = entry.fromRoomId;
            int toIndex = entry.toRoomId;
            Room fIndexRoom = null;
            Room toIndexRoom = null;
            for (Room r : rooms) {
                if (r.id() == fromIndex) fIndexRoom = r;
                if (r.id() == toIndex) toIndexRoom = r;
            }
            travelTime.setTravelTime(fIndexRoom, toIndexRoom, entry.time);
        }
    }
    
    /**
     * Parse a string into a boolean array, '1' means true, other characters mean false
     * @param str String containing 0s and 1s
     * @return Corresponding boolean array
     */
    private boolean[] parseBooleanArray(String str) {
        boolean[] result = new boolean[str.length()];
        for (int i = 0; i < str.length(); i++) {
            result[i] = str.charAt(i) == '1';
        }
        return result;
    }

    private static class NoCourse {
        int id;
        NoConfig[] noConfigs;

        NoCourse(int id, NoConfig[] noConfigs) {
            this.id = id;
            this.noConfigs = noConfigs;
        }
    }

    private static class NoConfig {
        int id;
        NoSubpart[] noSubparts;

        public NoConfig(int id, NoSubpart[] noSubparts) {
            this.id = id;
            this.noSubparts = noSubparts;
        }
    }

    private static class NoSubpart {
        int id;
        NoClass[] noClasses;

        public NoSubpart(int id, NoClass[] noClasses) {
            this.id = id;
            this.noClasses = noClasses;
        }
    }

    private static class NoClass{
        int id;
        int limit;
        TimeAssignment[] possibleTimes;
        RoomAssignment[] possibleRooms;
        boolean isParent = false;
        int parent;
        boolean turnReal;

        public NoClass(int id, int limit, TimeAssignment[] possibleTimes, RoomAssignment[] possibleRooms, boolean isParent, int parent, boolean turnReal) {
            this.id = id;
            this.limit = limit;
            this.possibleTimes = possibleTimes;
            this.possibleRooms = possibleRooms;
            this.isParent = isParent;
            this.parent = parent;
            this.turnReal = turnReal;
        }
    }

    private Class parentLoop (NoClass noParentClass) {
        for (int i=0; i<noAllArray.toArray().length; i++) {
            NoClass fakeParent = noAllArray.get(i);
            if (fakeParent.id == noParentClass.parent) {
                if (!fakeParent.turnReal) {
                    Class toParent = null;
                    if (fakeParent.isParent) {
                        toParent = parentLoop(fakeParent);
                    }
                    fakeParent.turnReal = true;
//                    if (fakeParent.limit < 1) System.out.println(fakeParent.id);
                    Class realParent = new Class(fakeParent.id, fakeParent.limit, fakeParent.possibleTimes, fakeParent.possibleRooms, toParent);
                    realParentList.add(realParent);
                    return realParent;
                }
                else {
                    for (Class c : realParentList) {
                        if (c.id() == fakeParent.id) return c;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    private void createParentClass() {
        for (int i=0; i<noAllArray.toArray().length; i++) {
            if (noAllArray.get(i).isParent) {
                Class realParent = parentLoop(noAllArray.get(i));
            }
        }
    }

    private void fillCourses() {
        for (NoCourse i : noCourses) {
            int noCourseId = i.id;
            NoConfig[] k = i.noConfigs;
            configList = new ArrayList<>();
            for (NoConfig j : k) {
                int noConfigId = j.id;
                NoSubpart[] m  = j.noSubparts;
                subpartList = new ArrayList<>();
                for (NoSubpart n : m) {
                    int noSubpartId = n.id;
                    NoClass[] f = n.noClasses;
                    classList = new ArrayList<>();
                    for (NoClass p : f) {
                        int noClassId = p.id;
                        int noLimit = p.limit;
                        RoomAssignment[] noRoom = p.possibleRooms;
                        TimeAssignment[] noTime = p.possibleTimes;
                        Class findParent = null;
                        if (p.isParent) {
                            for (Class c : realParentList) {
                                if (c.id() == p.parent) {
                                    findParent = c;
                                }
                            }
                        }
                        aClass = new Class(noClassId, noLimit, noTime, noRoom, findParent);
                        classList.add(aClass);
                        allClassList.add(aClass);
                    }
                    classes = classList.toArray(new Class[0]);
                    subpart = new Subpart(noSubpartId, classes);
                    subpartList.add(subpart);
                }
                subparts = subpartList.toArray(new Subpart[0]);
                config = new Config(noConfigId, subparts);
                configList.add(config);
            }
            configs = configList.toArray(new Config[0]);
            course = new Course(noCourseId, configs);
            courseList.add(course);
        }
    }

    /**
     * Handle end tags for distributions
     */
    private void handleDistributionsEnd(String qName) {
        if ("distribution".equalsIgnoreCase(qName)) {
            DistributionConstraint currentConstraint = null;
            disClasses = classList.toArray(new Class[0]);
            if (type.equals("DifferentDays")) {
                currentConstraint = differentDays = new DifferentDays(disClasses);
            }
            else if (type.equals("DifferentRoom")) {
                currentConstraint = differentRoom = new DifferentRoom(disClasses);
            }
            else if (type.equals("DifferentTime")) {
                currentConstraint = differentTime = new DifferentTime(disClasses);
            }
            else if (type.equals("DifferentWeeks")) {
                currentConstraint = differentWeeks = new DifferentWeeks(disClasses);
            }
            else if (type.equals("SameStart")) {
                currentConstraint = sameStart = new SameStart(disClasses);
            }
            else if (type.equals("SameTime")) {
                currentConstraint = sameTime = new SameTime(disClasses);
            }
            else if (type.equals("SameDays")) {
                currentConstraint = sameDays = new SameDays(disClasses);
            }
            else if (type.equals("SameWeeks")) {
                currentConstraint = sameWeeks = new SameWeeks(disClasses);
            }
            else if (type.equals("Overlap")) {
                currentConstraint = overlap = new Overlap(disClasses);
            }
            else if (type.equals("NotOverlap")) {
                currentConstraint = notOverlap = new NotOverlap(disClasses);
            }
            else if (type.equals("SameRoom")) {
                currentConstraint = sameRoom = new SameRoom(disClasses);
            }
            else if (type.equals("SameAttendees")) {
                currentConstraint = sameAttendees = new SameAttendees(disClasses);
            }
            else if (type.equals("Precedence")) {
                currentConstraint = precedence = new Precedence(disClasses);
            }
            else if (type.contains("(")&&type.contains(")")) {
                String baseType = type.substring(0, type.indexOf("("));
                String paramsStr = type.substring(type.indexOf("(") + 1, type.indexOf(")"));

                String[] params = paramsStr.split(",");
                int[] intParams = new int[params.length];
                for (int i = 0; i < params.length; i++) {
                    intParams[i] = Integer.parseInt(params[i].trim());
                }

                switch (baseType) {
                    case "WorkDay" -> currentConstraint = workDay = new WorkDay(disClasses, intParams[0]);
                    case "MinGap" -> currentConstraint = minGap = new MinGap(disClasses, intParams[0]);
                    case "MaxDays" -> currentConstraint = maxDays = new MaxDays(disClasses, intParams[0]);
                    case "MaxDayLoad" -> currentConstraint = maxDayLoad = new MaxDayLoad(disClasses, intParams[0]);
                    case "MaxBreaks" -> currentConstraint = maxBreaks = new MaxBreaks(disClasses, intParams[0], intParams[1]);
                    case "MaxBlock" -> currentConstraint = maxBlock = new MaxBlock(disClasses, intParams[0], intParams[1]);
                }
            }

            if (isHard) {
                haveHard = true;
                hardConstraint = new HardConstraint(currentConstraint);
                hardConstraintList.add(hardConstraint);
            }
            else {
                haveSoft = true;
                softConstraint = new SoftConstraint(currentConstraint, disPenalty);
                softConstraintList.add(softConstraint);
            }
        }
        else if ("distributions".equalsIgnoreCase(qName)) {
            if (haveHard) hardConstraints = hardConstraintList.toArray(new HardConstraint[0]);
            if (haveSoft) softConstraints = softConstraintList.toArray(new SoftConstraint[0]);
            inDistributions = false;
        }
    }

    /**
     * Handle end tags for students
     */
    private void handleStudentsEnd(String qName) {
        if ("student".equalsIgnoreCase(qName)) {
            stuCourses = stuCourseList.toArray(new Course[0]);
            student = new Student(studentId, stuCourses);
            studentList.add(student);
        }
        else if ("students".equalsIgnoreCase(qName)) {
            if (haveStudents) {
                students = studentList.toArray(new Student[0]);
            }
            else students = null;
            inStudents = false;
        }
    }
}
