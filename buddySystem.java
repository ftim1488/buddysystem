import java.util.*;

public class buddySystem {

    private final int capacity;

    // blockSize -> starting addresses
    private final TreeMap<Integer, TreeSet<Integer>> freeLists = new TreeMap<>();

    // process -> {address, blockSize, requestedSize}
    private final Map<String, int[]> allocated = new LinkedHashMap<>();

    // Stores the result of every allocation/release event
    private final List<Row> history = new ArrayList<>();

    private static class Cell {
        final String label;
        final int size;

        Cell(String label, int size) {
            this.label = label;
            this.size = size;
        }
    }

    private static class Row {
        final int no;
        final String process;
        final String status;
        final List<Cell> cells;

        Row(int no, String process, String status, List<Cell> cells) {
            this.no = no;
            this.process = process;
            this.status = status;
            this.cells = cells;
        }
    }

    public buddySystem(int capacity) {
        this.capacity = capacity;
        addFree(0, capacity);
    }

    private void addFree(int addr, int size) {
        freeLists.computeIfAbsent(size, k -> new TreeSet<>()).add(addr);
    }

    private boolean removeFree(int addr, int size) {
        TreeSet<Integer> set = freeLists.get(size);

        if (set == null || !set.remove(addr)) {
            return false;
        }

        if (set.isEmpty()) {
            freeLists.remove(size);
        }

        return true;
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;

        while (p < n) {
            p <<= 1;
        }

        return p;
    }

    // ============================================================
    // ALLOCATE
    // ============================================================

    public boolean request(String name, int sizeK) {

        if (allocated.containsKey(name)) {
            return false;
        }

        if (sizeK <= 0) {
            return false;
        }

        int blockSize = nextPowerOfTwo(sizeK);

        if (blockSize > capacity) {
            return false;
        }

        Integer foundSize = null;

        for (Integer size : freeLists.tailMap(blockSize, true).keySet()) {
            foundSize = size;
            break;
        }

        if (foundSize == null) {
            return false;
        }

        int addr = freeLists.get(foundSize).first();

        removeFree(addr, foundSize);

        int currentSize = foundSize;

        while (currentSize > blockSize) {
            currentSize /= 2;

            // Upper half becomes free.
            addFree(addr + currentSize, currentSize);
        }

        allocated.put(name, new int[] { addr, blockSize, sizeK });

        return true;
    }

    // ============================================================
    // RELEASE
    // ============================================================

    public boolean release(String name) {

        int[] info = allocated.remove(name);

        if (info == null) {
            return false;
        }

        int addr = info[0];
        int size = info[1];

        while (size < capacity) {

            int buddy = addr ^ size;

            if (!removeFree(buddy, size)) {
                break;
            }

            addr = Math.min(addr, buddy);
            size *= 2;
        }

        addFree(addr, size);

        return true;
    }

    public boolean isAllocated(String name) {
        return allocated.containsKey(name);
    }

    // ============================================================
    // CREATE MEMORY MAP (list of cells, one per block)
    // ============================================================

    private List<Cell> getMemoryCells() {

        // {address, size, free(1)/allocated(0)}
        List<int[]> blocks = new ArrayList<>();

        for (Map.Entry<Integer, TreeSet<Integer>> entry : freeLists.entrySet()) {
            int size = entry.getKey();

            for (int addr : entry.getValue()) {
                blocks.add(new int[] { addr, size, 1 });
            }
        }

        Map<Integer, String> owners = new HashMap<>();

        for (Map.Entry<String, int[]> entry : allocated.entrySet()) {
            int[] info = entry.getValue();

            blocks.add(new int[] { info[0], info[1], 0 });
            owners.put(info[0], entry.getKey());
        }

        // Sort according to physical address.
        blocks.sort(Comparator.comparingInt(block -> block[0]));

        List<Cell> cells = new ArrayList<>();

        for (int[] block : blocks) {
            String label = block[1] + "k";

            if (block[2] == 0) {
                label = owners.get(block[0]) + " " + label;
            }

            cells.add(new Cell(label, block[1]));
        }

        return cells;
    }

    // ============================================================
    // SAVE EVENT
    // ============================================================

    public void saveEvent(int eventNo, String process, String status) {
        history.add(new Row(eventNo, process, status, getMemoryCells()));
    }

    // ============================================================
    // PRINT FINAL TABLE (every cell padded so the | line up)
    // ============================================================

    private static String center(String text, int width) {

        int total = width - text.length();
        int left = total / 2;
        int right = total - left;

        return " ".repeat(left) + text + " ".repeat(right);
    }

    public void printHistory() {

        // Smallest block that ever appears = 1 unit of width.
        int unit = capacity;

        for (Row r : history) {
            for (Cell c : r.cells) {
                unit = Math.min(unit, c.size);
            }
        }

        // Keep the table readable for very large/small ratios.
        if (capacity / unit > 64) {
            unit = capacity / 64;
        }

        // Width of one unit so every label fits inside its block.
        int w = 1;

        for (Row r : history) {
            for (Cell c : r.cells) {
                int n = Math.max(1, c.size / unit);
                int need = (c.label.length() + 3 + n - 1) / n - 1;
                w = Math.max(w, need);
            }
        }

        String header = "Event | Process | Status         | Memory Map";
        List<String> lines = new ArrayList<>();
        int width = header.length();

        for (Row r : history) {

            StringBuilder sb = new StringBuilder();

            sb.append(String.format("%-5d | %-7s | %-14s ", r.no, r.process, r.status));
            sb.append("|");

            for (Cell c : r.cells) {
                int n = Math.max(1, c.size / unit);
                // A block n units wide spans n*(w+1) columns incl. its right "|"
                sb.append(center(c.label, n * (w + 1) - 1)).append("|");
            }

            lines.add(sb.toString());
            width = Math.max(width, sb.length());
        }

        String bar = "=".repeat(width);

        System.out.println();
        System.out.println(bar);
        System.out.println(center("MEMORY ALLOCATION / RELEASE TABLE", width));
        System.out.println(bar);
        System.out.println(header);

        for (String line : lines) {
            System.out.println("-".repeat(width));
            System.out.println(line);
        }

        System.out.println(bar);
    }

    // ============================================================
    // INPUT
    // ============================================================

    private static int parseSize(String s) {

        s = s.trim().toUpperCase();

        try {
            if (s.endsWith("M")) {
                return Integer.parseInt(s.substring(0, s.length() - 1).trim()) * 1024;
            }

            if (s.endsWith("K")) {
                return Integer.parseInt(s.substring(0, s.length() - 1).trim());
            }

            return Integer.parseInt(s);

        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String ask(Scanner in, String prompt) {

        System.out.print(prompt);

        return in.hasNextLine() ? in.nextLine().trim() : "";
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {

        Scanner in = new Scanner(System.in);

        System.out.println("=== Buddy System Memory Management Simulation ===");

        // --------------------------------------------------------
        // CAPACITY
        // --------------------------------------------------------

        String capLine = ask(in, "Total memory capacity [default 1M]: ");

        int cap = capLine.isEmpty() ? 1024 : parseSize(capLine);

        if (cap <= 0) {
            System.out.println("Invalid capacity, using 1M.");
            cap = 1024;
        }

        if (Integer.bitCount(cap) != 1) {
            int rounded = Integer.highestOneBit(cap);

            System.out.println("Capacity must be a power of two; using " + rounded + "K.");

            cap = rounded;
        }

        buddySystem mm = new buddySystem(cap);

        // --------------------------------------------------------
        // EVENTS (one line each)
        //   A P1/200K
        //   R P1
        // --------------------------------------------------------

        int eventNo = 0;

       System.out.println();
        System.out.println(
                "Enter events:"
        );

        System.out.println(
                "  A <process>/<size>  = Allocate"
        );

        System.out.println(
                "  R <process>         = Release"
        );

        System.out.println(
                "Example: A P1/200K"
        );

        System.out.println(
                "Type 'done' to finish."
        );


        while (true) {

            System.out.println();

            String line = ask(in, "Event " + (eventNo + 1) + ": ");

            if (line.equalsIgnoreCase("done")) {
                break;
            }

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0].toUpperCase();

            if (parts.length < 2 || parts[1].trim().isEmpty()) {
                System.out.println("Use: A P1/200K  or  R P1");
                continue;
            }

            String arg = parts[1].trim();

            // ----------------------------------------------------
            // ALLOCATE  ->  A P1/200K
            // ----------------------------------------------------

            if (cmd.equals("A")) {

                String[] pr = arg.split("/", 2);

                if (pr.length < 2) {
                    System.out.println("Use: A P1/200K");
                    continue;
                }

                String p = pr[0].trim().toUpperCase();
                int size = parseSize(pr[1]);

                if (p.isEmpty() || size <= 0) {
                    System.out.println("Invalid process or size, event cancelled.");
                    continue;
                }

                eventNo++;

                if (mm.isAllocated(p)) {
                    mm.saveEvent(eventNo, p, "already alloc");
                    System.out.println("-> " + p + " is already allocated");
                    continue;
                }

                boolean success = mm.request(p, size);

                mm.saveEvent(eventNo, p, success ? "allocated" : "denied");
                System.out.println(success ? "-> allocated" : "-> denied (not enough memory)");
            }

            // ----------------------------------------------------
            // RELEASE  ->  R P1
            // ----------------------------------------------------

            else if (cmd.equals("R")) {

                String p = arg.toUpperCase();

                eventNo++;

                boolean success = mm.release(p);

                mm.saveEvent(eventNo, p, success ? "released" : "not allocated");
                System.out.println(success ? "-> released" : "-> process is not allocated");
            }

            else {
                System.out.println("Use: A P1/200K  or  R P1");
            }
        }

        mm.printHistory();

        System.out.println();
        System.out.println("Simulation finished.");

        in.close();
    }
}