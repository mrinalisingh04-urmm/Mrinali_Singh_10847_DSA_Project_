import java.io.*;
import java.util.*;

/**
 * College Management System (PS 58)
 * Console application to manage students, faculty and courses.
 *   - Linked lists  : records of students, faculty and courses
 *   - Stack         : undo of the latest supported record change
 *   - Queue         : service requests (first come, first served)
 *   - Graph         : course relationships (prerequisites) using adjacency lists
 *   - File handling : data is loaded at start and saved on exit (college_data.txt)
 */

// ---------------------------------------------------------------- main class
public class CollegeManagementSystem {
    private static final String FILE_NAME = "college_data.txt";

    private static final LinkedList<Student> students = new LinkedList<>();
    private static final LinkedList<Faculty> facultyList = new LinkedList<>();
    private static final LinkedList<Course> courses = new LinkedList<>();

    // Graph: course ID -> list of its prerequisite course IDs (adjacency list)
    private static final HashMap<String, ArrayList<String>> prerequisites = new HashMap<>();

    private static final Stack<UndoAction> undoStack = new Stack<>();
    private static final Queue<ServiceRequest> requestQueue = new LinkedList<>();
    private static int requestCounter = 1;

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        loadFromFile();
        boolean running = true;

        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");
            switch (choice) {
                case 1: addStudent(); break;
                case 2: addFaculty(); break;
                case 3: addCourse(); break;
                case 4: displayAll("students", students); break;
                case 5: displayAll("faculty", facultyList); break;
                case 6: displayAll("courses", courses); break;
                case 7: searchRecord("Student", students); break;
                case 8: searchRecord("Faculty", facultyList); break;
                case 9: searchRecord("Course", courses); break;
                case 10: addRelationship(); break;
                case 11: displayRelationships(); break;
                case 12: submitRequest(); break;
                case 13: processRequest(); break;
                case 14: displayRequests(); break;
                case 15: undoLast(); break;
                case 16:
                    saveToFile();
                    System.out.println("Data saved to " + FILE_NAME + ". Goodbye!");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Please select 1-16.");
            }
        }
        sc.close();
    }

    // ---------------- Menu ----------------
    private static void printMenu() {
        System.out.println("\n===== College Management System =====");
        System.out.println(" 1. Add student");
        System.out.println(" 2. Add faculty");
        System.out.println(" 3. Add course");
        System.out.println(" 4. Display students");
        System.out.println(" 5. Display faculty");
        System.out.println(" 6. Display courses");
        System.out.println(" 7. Search student by ID");
        System.out.println(" 8. Search faculty by ID");
        System.out.println(" 9. Search course by ID");
        System.out.println("10. Add course relationship (prerequisite)");
        System.out.println("11. Display course relationships");
        System.out.println("12. Submit service request");
        System.out.println("13. Process next service request");
        System.out.println("14. Display pending service requests");
        System.out.println("15. Undo last change");
        System.out.println("16. Save and exit");
    }

    // ---------------- Add records ----------------
    private static void addStudent() {
        String name = readNonEmpty("Student name: ");
        String id = nextId("ST", 101, students);
        if (findById(students, id) != null) {
            System.out.println("Duplicate ID " + id + " not allowed.");
            return;
        }
        students.add(new Student(id, name));
        undoStack.push(new UndoAction(UndoAction.Type.ADD_STUDENT, id, null, "Add student " + name));
        System.out.println("Student added. ID: " + id + " | " + name);
    }

    private static void addFaculty() {
        String name = readNonEmpty("Faculty name: ");
        String id = nextId("F", 101, facultyList);
        if (findById(facultyList, id) != null) {
            System.out.println("Duplicate ID " + id + " not allowed.");
            return;
        }
        facultyList.add(new Faculty(id, name));
        undoStack.push(new UndoAction(UndoAction.Type.ADD_FACULTY, id, null, "Add faculty " + name));
        System.out.println("Faculty added. ID: " + id + " | " + name);
    }

    private static void addCourse() {
        String name = readNonEmpty("Course name: ");
        String id = nextId("C", 101, courses);
        if (findById(courses, id) != null) {
            System.out.println("Duplicate ID " + id + " not allowed.");
            return;
        }
        courses.add(new Course(id, name));
        undoStack.push(new UndoAction(UndoAction.Type.ADD_COURSE, id, null, "Add course " + name));
        System.out.println("Course added. ID: " + id + " | " + name);
    }

    // ---------------- Display and search ----------------
    private static void displayAll(String label, List<? extends Entity> list) {
        if (list.isEmpty()) {
            System.out.println("No " + label + " found.");
            return;
        }
        System.out.println("All " + label + " (" + list.size() + "):");
        for (Entity e : list) {
            System.out.println(e);
        }
    }

    private static void searchRecord(String label, List<? extends Entity> list) {
        String id = readId(label + " ID: ");
        Entity e = findById(list, id);
        if (e == null) {
            System.out.println(label + " " + id + " not found.");
        } else {
            System.out.println(e);
        }
    }

    // ---------------- Graph: course relationships ----------------
    private static void addRelationship() {
        String course = readId("Course ID: ");
        String prereq = readId("Prerequisite course ID: ");

        if (findById(courses, course) == null) {
            System.out.println("Course " + course + " not found.");
            return;
        }
        if (findById(courses, prereq) == null) {
            System.out.println("Course " + prereq + " not found.");
            return;
        }
        if (course.equals(prereq)) {
            System.out.println("A course cannot be its own prerequisite.");
            return;
        }
        ArrayList<String> existing = prerequisites.get(course);
        if (existing != null && existing.contains(prereq)) {
            System.out.println("Relationship already exists.");
            return;
        }
        if (hasPath(prereq, course)) {
            System.out.println("Cannot add: this would create a circular dependency.");
            return;
        }
        addEdge(course, prereq);
        undoStack.push(new UndoAction(UndoAction.Type.ADD_RELATION, course, prereq,
                "Add relationship " + course + " requires " + prereq));
        System.out.println("Relationship added: " + course + " requires " + prereq);
    }

    private static void displayRelationships() {
        System.out.print("Course ID (press Enter for all): ");
        String id = sc.nextLine().trim().toUpperCase();

        if (id.isEmpty()) {
            if (courses.isEmpty()) {
                System.out.println("No courses found.");
                return;
            }
            System.out.println("Course relationships:");
            for (Course c : courses) {
                System.out.println(c + " -> requires: " + joinOrNone(prerequisites.get(c.getId())));
            }
            return;
        }

        Course c = findById(courses, id);
        if (c == null) {
            System.out.println("Course " + id + " not found.");
            return;
        }
        ArrayList<String> requiredBy = new ArrayList<>();
        for (Course other : courses) {
            ArrayList<String> list = prerequisites.get(other.getId());
            if (list != null && list.contains(id)) {
                requiredBy.add(other.getId());
            }
        }
        System.out.println(c);
        System.out.println("  Requires    : " + joinOrNone(prerequisites.get(id)));
        System.out.println("  Required by : " + joinOrNone(requiredBy));
    }

    private static void addEdge(String course, String prereq) {
        ArrayList<String> list = prerequisites.get(course);
        if (list == null) {
            list = new ArrayList<>();
            prerequisites.put(course, list);
        }
        list.add(prereq);
    }

    private static void removeEdge(String course, String prereq) {
        ArrayList<String> list = prerequisites.get(course);
        if (list != null) {
            list.remove(prereq);
            if (list.isEmpty()) {
                prerequisites.remove(course);
            }
        }
    }

    // Depth-first search: is there a path from 'from' to 'to'?
    private static boolean hasPath(String from, String to) {
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(from);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (current.equals(to)) return true;
            if (!visited.add(current)) continue;
            ArrayList<String> next = prerequisites.get(current);
            if (next != null) {
                for (String n : next) {
                    stack.push(n);
                }
            }
        }
        return false;
    }

    // ---------------- Queue: service requests ----------------
    private static void submitRequest() {
        String text = readNonEmpty("Request details: ");
        ServiceRequest r = new ServiceRequest("R" + requestCounter, text);
        requestCounter++;
        requestQueue.add(r);                       // enqueue at the rear
        System.out.println("Service request submitted. ID: " + r);
    }

    private static void processRequest() {
        ServiceRequest r = requestQueue.poll();    // dequeue from the front (null if empty)
        if (r == null) {
            System.out.println("No pending service requests.");
            return;
        }
        System.out.println("Processed request: " + r);
    }

    private static void displayRequests() {
        if (requestQueue.isEmpty()) {
            System.out.println("No pending service requests.");
            return;
        }
        System.out.println("Pending service requests (" + requestQueue.size() + "):");
        int i = 1;
        for (ServiceRequest r : requestQueue) {
            System.out.println(i + ". " + r);
            i++;
        }
    }

    // ---------------- Stack: undo ----------------
    private static void undoLast() {
        if (undoStack.isEmpty()) {
            System.out.println("Nothing to undo.");
            return;
        }
        UndoAction a = undoStack.pop();
        switch (a.type) {
            case ADD_STUDENT:
                removeById(students, a.id);
                break;
            case ADD_FACULTY:
                removeById(facultyList, a.id);
                break;
            case ADD_COURSE:
                removeById(courses, a.id);
                prerequisites.remove(a.id);
                for (ArrayList<String> list : prerequisites.values()) {
                    list.remove(a.id);
                }
                break;
            case ADD_RELATION:
                removeEdge(a.id, a.other);
                break;
        }
        System.out.println("Last operation undone: " + a.description);
    }

    // ---------------- Helpers ----------------
    private static <T extends Entity> T findById(List<T> list, String id) {
        for (T item : list) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    private static void removeById(List<? extends Entity> list, String id) {
        Iterator<? extends Entity> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(id)) {
                it.remove();
                return;
            }
        }
    }

    // First ID is prefix + first (e.g. ST101); later IDs continue after the highest number
    private static String nextId(String prefix, int first, List<? extends Entity> list) {
        int max = first - 1;
        for (Entity e : list) {
            try {
                int n = Integer.parseInt(e.getId().substring(prefix.length()));
                if (n > max) max = n;
            } catch (NumberFormatException ex) {
                // ignore IDs that do not follow the pattern
            }
        }
        return prefix + (max + 1);
    }

    private static String joinOrNone(List<String> list) {
        if (list == null || list.isEmpty()) return "none";
        return String.join(", ", list);
    }

    private static String readId(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim().toUpperCase();
    }

    private static String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine().trim();
            if (!line.isEmpty() && !line.contains("|")) return line;
            System.out.println("Input cannot be empty or contain '|'. Try again.");
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    // ---------------- File handling ----------------
    private static void loadFromFile() {
        File f = new File(FILE_NAME);
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split("\\|");
                if (p.length != 3) continue;
                try {
                    switch (p[0]) {
                        case "STUDENT":
                            if (findById(students, p[1]) == null) students.add(new Student(p[1], p[2]));
                            break;
                        case "FACULTY":
                            if (findById(facultyList, p[1]) == null) facultyList.add(new Faculty(p[1], p[2]));
                            break;
                        case "COURSE":
                            if (findById(courses, p[1]) == null) courses.add(new Course(p[1], p[2]));
                            break;
                        case "PREREQ":
                            ArrayList<String> existing = prerequisites.get(p[1]);
                            boolean duplicate = existing != null && existing.contains(p[2]);
                            if (findById(courses, p[1]) != null && findById(courses, p[2]) != null && !duplicate) {
                                addEdge(p[1], p[2]);
                            }
                            break;
                        case "REQUEST":
                            requestQueue.add(new ServiceRequest(p[1], p[2]));
                            int n = Integer.parseInt(p[1].substring(1));
                            if (n >= requestCounter) requestCounter = n + 1;
                            break;
                        default:
                            break;
                    }
                } catch (RuntimeException e) {
                    // skip a damaged line and continue loading
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

    private static void saveToFile() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME))) {
            for (Student s : students) {
                pw.println("STUDENT|" + s.getId() + "|" + s.getName());
            }
            for (Faculty f : facultyList) {
                pw.println("FACULTY|" + f.getId() + "|" + f.getName());
            }
            for (Course c : courses) {
                pw.println("COURSE|" + c.getId() + "|" + c.getName());
            }
            for (Map.Entry<String, ArrayList<String>> e : prerequisites.entrySet()) {
                for (String p : e.getValue()) {
                    pw.println("PREREQ|" + e.getKey() + "|" + p);
                }
            }
            for (ServiceRequest r : requestQueue) {
                pw.println("REQUEST|" + r.getId() + "|" + r.getText());
            }
        } catch (IOException e) {
            System.out.println("Error saving file: " + e.getMessage());
        }
    }
}

// ---------------------------------------------------------------- record classes
interface Entity {
    String getId();
    String getName();
}

class Student implements Entity {
    private final String id;
    private final String name;

    public Student(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String toString() { return id + " | " + name; }
}

class Faculty implements Entity {
    private final String id;
    private final String name;

    public Faculty(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String toString() { return id + " | " + name; }
}

class Course implements Entity {
    private final String id;
    private final String name;

    public Course(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String toString() { return id + " | " + name; }
}

class ServiceRequest {
    private final String id;
    private final String text;

    public ServiceRequest(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    @Override public String toString() { return id + " | " + text; }
}

// One entry of the undo stack
class UndoAction {
    enum Type { ADD_STUDENT, ADD_FACULTY, ADD_COURSE, ADD_RELATION }

    final Type type;
    final String id;           // record ID (or the course in a relationship)
    final String other;        // prerequisite course ID (only for ADD_RELATION)
    final String description;  // text shown after undo

    UndoAction(Type type, String id, String other, String description) {
        this.type = type;
        this.id = id;
        this.other = other;
        this.description = description;
    }
}
