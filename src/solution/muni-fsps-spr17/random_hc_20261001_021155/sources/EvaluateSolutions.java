package solver.DO;

import dataset.*;
import dataset.Class;
import dataset.constraints.HardConstraint;
import io.Encoder;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Uses the existing Java DF implementation without running a search. */
public class EvaluateSolutions {
    public static Timetable load(ProblemInstance instance, String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(path);
        Map<Integer, Element> assignments = new HashMap<>();
        NodeList nodes = document.getElementsByTagName("class");
        for (int j = 0; j < nodes.getLength(); j++) {
            Element e = (Element) nodes.item(j);
            assignments.put(Integer.parseInt(e.getAttribute("id")), e);
        }
        Timetable timetable = new Timetable(instance.classes());
        for (Class c : instance.classes()) {
            Element e = assignments.get(c.id());
            if (e == null) throw new IllegalArgumentException("missing class " + c.id());
            Event event = timetable.getEvent(c);
            TimeAssignment selected = null;
            for (TimeAssignment t : c.possibleTimes()) {
                Time time = t.time();
                if (time.start() == Integer.parseInt(e.getAttribute("start")) &&
                    bits(time.days()).equals(e.getAttribute("days")) &&
                    bits(time.weeks()).equals(e.getAttribute("weeks"))) { selected = t; break; }
            }
            if (selected == null) throw new IllegalArgumentException("time outside domain: " + c.id());
            event.setTimeAssignment(selected);
            if (c.possibleRooms() != null) {
                RoomAssignment room = null;
                for (RoomAssignment r : c.possibleRooms())
                    if (r.room().id() == Integer.parseInt(e.getAttribute("room"))) room = r;
                if (room == null) throw new IllegalArgumentException("room outside domain: " + c.id());
                event.setRoomAssignment(room);
            }
        }
        return timetable;
    }
    public static void main(String[] args) throws Exception {
        ProblemInstance instance = new Encoder(args[0]).getProblemInstance();
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            String[] parts = line.split("\t", 2);
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Document document = factory.newDocumentBuilder().parse(parts[1]);
                Map<Integer, Element> assignments = new HashMap<>();
                NodeList nodes = document.getElementsByTagName("class");
                for (int j = 0; j < nodes.getLength(); j++) {
                    Element element = (Element) nodes.item(j);
                    assignments.put(Integer.parseInt(element.getAttribute("id")), element);
                }
                Timetable timetable = new Timetable(instance.classes());
                for (Class c : instance.classes()) {
                    Element e = assignments.get(c.id());
                    if (e == null) throw new IllegalArgumentException("missing class " + c.id());
                    Event event = timetable.getEvent(c);
                    TimeAssignment selected = null;
                    for (TimeAssignment t : c.possibleTimes()) {
                        Time time = t.time();
                        if (time.start() == Integer.parseInt(e.getAttribute("start")) &&
                            bits(time.days()).equals(e.getAttribute("days")) &&
                            bits(time.weeks()).equals(e.getAttribute("weeks"))) {
                            selected = t;
                            break;
                        }
                    }
                    if (selected == null) throw new IllegalArgumentException("time outside domain: " + c.id());
                    event.setTimeAssignment(selected);
                    if (c.possibleRooms() != null) {
                        RoomAssignment selectedRoom = null;
                        for (RoomAssignment r : c.possibleRooms())
                            if (r.room().id() == Integer.parseInt(e.getAttribute("room"))) selectedRoom = r;
                        if (selectedRoom == null) throw new IllegalArgumentException("room outside domain: " + c.id());
                        event.setRoomAssignment(selectedRoom);
                    }
                }
                List<String> violated = new ArrayList<>();
                for (HardConstraint h : instance.hardConstraints())
                    if (!h.constraint().isSatisfied(timetable)) violated.add(h.constraint().getClass().getSimpleName());
                System.out.println("RESULT\t" + parts[0] + "\t" + timetable.isFeasible(instance) +
                    "\t" + timetable.isValidAssignment() + "\t" + String.join(",", violated));
            } catch (Exception ex) {
                System.out.println("ERROR\t" + parts[0] + "\t" + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }
    private static String bits(boolean[] values) {
        StringBuilder result = new StringBuilder();
        for (boolean v : values) result.append(v ? '1' : '0');
        return result.toString();
    }
}
