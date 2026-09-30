package solver.DO;

import dataset.*;
import dataset.Class;
import io.Encoder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Persistent DF evaluator and HC runner for terminal-solution collection. */
public class SearchBridge {
    record Block(Class clazz, boolean isTime, Object[] choices) {}
    private final ProblemInstance instance;
    private final List<Block> blocks = new ArrayList<>();
    private Timetable current;
    private int df;
    private long nfe;
    private long lastImprovementNfe;
    private long searchStarted;
    private Random random;
    private final PrintWriter trajectory;
    private final String technique;
    private final boolean improvementsOnly;

    private SearchBridge(String problem, String log, boolean improvementsOnly) throws Exception {
        this.improvementsOnly = improvementsOnly;
        technique = "Ordinary HC data collection (DF only)";
        PrintStream protocol = System.out;
        System.setOut(System.err);
        try { instance = new Encoder(problem).getProblemInstance(); }
        finally { System.setOut(protocol); }
        Class[] classes = instance.classes().clone();
        Arrays.sort(classes, Comparator.comparingInt(Class::id));
        for (Class c : classes) {
            TimeAssignment[] times = c.possibleTimes().clone();
            Arrays.sort(times, Comparator.comparing((TimeAssignment t) -> bits(t.time().days()))
                .thenComparing(t -> bits(t.time().weeks())).thenComparingInt(t -> t.time().start()));
            blocks.add(new Block(c, true, times));
            if (c.possibleRooms() != null) {
                RoomAssignment[] rooms = c.possibleRooms().clone();
                Arrays.sort(rooms, Comparator.comparingInt(r -> r.room().id()));
                blocks.add(new Block(c, false, rooms));
            }
        }
        trajectory = new PrintWriter(Files.newBufferedWriter(Path.of(log)));
        trajectory.println("nfe,phase,cycle,step,before_df,candidate_df,after_df,accepted,changed_classes,elapsed_ns");
    }
    private static String bits(boolean[] values) {
        StringBuilder s = new StringBuilder();
        for (boolean v : values) s.append(v ? '1' : '0');
        return s.toString();
    }
    private static String timeKey(TimeAssignment t) {
        return bits(t.time().days()) + ":" + bits(t.time().weeks()) + ":" + t.time().start();
    }
    private String key(Event e) {
        return timeKey(e.getTimeAssignment()) + ":" +
            (e.getRoomAssignment() == null ? 0 : e.getRoomAssignment().room().id());
    }
    private int changed(Timetable candidate) {
        int count = 0;
        for (Class c : instance.classes())
            if (!key(current.getEvent(c)).equals(key(candidate.getEvent(c)))) count++;
        return count;
    }
    private String state() {
        StringJoiner values = new StringJoiner(",");
        for (Block b : blocks) {
            Event e = current.getEvent(b.clazz());
            int index = -1;
            for (int i = 0; i < b.choices().length; i++) {
                if (b.isTime() ? timeKey((TimeAssignment)b.choices()[i]).equals(timeKey(e.getTimeAssignment())) :
                    ((RoomAssignment)b.choices()[i]).room().id() == e.getRoomAssignment().room().id()) { index = i; break; }
            }
            if (index < 0) throw new IllegalStateException("assignment outside schema");
            values.add(Integer.toString(index));
        }
        return df + "\t" + nfe + "\t" + values;
    }
    private String evaluate(Timetable candidate, String phase, int cycle, int step) {
        int before = df, changes = changed(candidate);
        int candidateDf = candidate.isFeasible(instance);
        nfe++;
        boolean accepted = candidateDf <= df;
        if (candidateDf < df) lastImprovementNfe = nfe;
        if (accepted) { current = candidate; df = candidateDf; }
        if (!improvementsOnly || candidateDf < before) trajectory.println(nfe + "," + phase + "," + cycle + "," + step + "," + before + "," +
            candidateDf + "," + df + "," + accepted + "," + changes + "," + (System.nanoTime() - searchStarted));
        // HC does not need to serialize all categorical choices for each candidate.
        return phase.equals("miv") ? before + "\t" + candidateDf + "\t" + accepted + "\t" + changes + "\t" + state() : "";
    }
    private void save(String path) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = document.createElement("solution");
        root.setAttribute("name", "muni-fsps-spr17");
        root.setAttribute("technique", technique);
        document.appendChild(root);
        for (Class c : instance.classes()) {
            Event e = current.getEvent(c);
            Element node = document.createElement("class");
            node.setAttribute("id", Integer.toString(c.id()));
            node.setAttribute("days", bits(e.getTimeAssignment().time().days()));
            node.setAttribute("weeks", bits(e.getTimeAssignment().time().weeks()));
            node.setAttribute("start", Integer.toString(e.getTimeAssignment().time().start()));
            if (e.getRoomAssignment() != null)
                node.setAttribute("room", Integer.toString(e.getRoomAssignment().room().id()));
            root.appendChild(node);
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        try (OutputStream output = Files.newOutputStream(Path.of(path))) {
            transformer.transform(new DOMSource(document), new StreamResult(output));
        }
    }
    private String command(String line) throws Exception {
        String[] fields = line.split("\t", -1);
        switch (fields[0]) {
            case "LOAD":
                searchStarted = System.nanoTime();
                current = EvaluateSolutions.load(instance, fields[1]);
                random = new Random(Long.parseLong(fields[2]));
                df = current.isFeasible(instance); nfe = 1;
                lastImprovementNfe = 1;
                trajectory.println("1,initial,0,0," + df + "," + df + "," + df + ",true,0," + (System.nanoTime() - searchStarted));
                return state();
            case "RANDOM": {
                searchStarted = System.nanoTime();
                random = new Random(Long.parseLong(fields[1]));
                current = new Timetable(instance.classes());
                for (Class c : instance.classes()) {
                    Event e = current.getEvent(c);
                    e.setTimeAssignment(c.possibleTimes()[random.nextInt(c.possibleTimes().length)]);
                    if (c.possibleRooms() != null) {
                        RoomAssignment[] choices = e.getAvailableRooms();
                        if (choices == null) choices = c.possibleRooms();
                        e.setRoomAssignment(choices[random.nextInt(choices.length)]);
                    }
                }
                df = current.isFeasible(instance); nfe = 1; lastImprovementNfe = 1;
                trajectory.println("1,initial,0,0," + df + "," + df + "," + df + ",true,0," + (System.nanoTime() - searchStarted));
                return state();
            }
            case "PATCH": {
                Timetable candidate = current.deepCopy(instance);
                if (!fields[3].isEmpty()) for (String change : fields[3].split(",")) {
                    String[] parts = change.split(":");
                    Block b = blocks.get(Integer.parseInt(parts[0]));
                    Event e = candidate.getEvent(b.clazz());
                    Object choice = b.choices()[Integer.parseInt(parts[1])];
                    if (b.isTime()) e.setTimeAssignment((TimeAssignment)choice);
                    else e.setRoomAssignment((RoomAssignment)choice);
                }
                return evaluate(candidate, "miv", Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
            }
            case "HC":
            case "HC_TIME":
            case "HC_UNTIL": {
                int budget = Integer.parseInt(fields[1]), cycle = Integer.parseInt(fields[2]);
                int target = fields[0].equals("HC_UNTIL") ? Integer.parseInt(fields[3]) : 0;
                long stall = fields[0].equals("HC_UNTIL") ? Long.parseLong(fields[4]) : Long.MAX_VALUE;
                long deadline = fields[0].equals("HC_TIME") ? Long.parseLong(fields[3]) : Long.MAX_VALUE;
                for (int step = 1; step <= budget && df > target &&
                        (stall < 0 || nfe - lastImprovementNfe < stall) &&
                        System.nanoTime() - searchStarted < deadline; step++) {
                    Timetable candidate = current.deepCopy(instance);
                    Class c = instance.classes()[random.nextInt(instance.classes().length)];
                    Event e = candidate.getEvent(c);
                    if (c.possibleRooms() == null || random.nextInt(2) == 1)
                        e.setTimeAssignment(c.possibleTimes()[random.nextInt(c.possibleTimes().length)]);
                    else {
                        RoomAssignment[] choices = e.getAvailableRooms();
                        if (choices == null) choices = c.possibleRooms();
                        e.setRoomAssignment(choices[random.nextInt(choices.length)]);
                    }
                    evaluate(candidate, "hc", cycle, step);
                }
                trajectory.flush();
                return state();
            }
            case "STATS": return nfe + "\t" + lastImprovementNfe + "\t" + (System.nanoTime() - searchStarted);
            case "SAVE": save(fields[1]); trajectory.flush(); return "OK";
            case "VERIFY": {
                Timetable saved = EvaluateSolutions.load(instance, fields[1]);
                return Integer.toString(saved.isFeasible(instance));
            }
            case "QUIT": trajectory.close(); return "BYE";
            default: throw new IllegalArgumentException("unknown command");
        }
    }
    public static void main(String[] args) throws Exception {
        SearchBridge bridge = new SearchBridge(args[0], args[1], args.length > 2 && args[2].equals("improvements"));
        System.out.println("READY\t" + bridge.blocks.size());
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        try {
            while ((line = input.readLine()) != null) {
                System.out.println(bridge.command(line));
                if (line.equals("QUIT")) break;
            }
        } finally { bridge.trajectory.close(); }
    }
}
