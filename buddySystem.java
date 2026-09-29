import java.util.*;

public class buddySystem {

    private final int capacity;

    // blockSize -> starting addresses
    private final TreeMap<Integer, TreeSet<Integer>> freeLists =
            new TreeMap<>();

    // process -> {address, blockSize, requestedSize}
    private final Map<String, int[]> allocated =
            new LinkedHashMap<>();

    // Stores the result of every allocation/release event
    private final List<String> history = new ArrayList<>();

    public buddySystem(int capacity) {
        this.capacity = capacity;
        addFree(0, capacity);
    }

    private void addFree(int addr, int size) {
        freeLists
                .computeIfAbsent(size, k -> new TreeSet<>())
                .add(addr);
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

        for (Integer size :
                freeLists.tailMap(blockSize, true).keySet()) {

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
            addFree(
                    addr + currentSize,
                    currentSize
            );
        }

        allocated.put(
                name,
                new int[] {
                        addr,
                        blockSize,
                        sizeK
                }
        );

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
    // CREATE MEMORY MAP
    // ============================================================

    private String getMemoryMap() {

        /*
         * {address, size, free/allocated}
         *
         * free = 1
         * allocated = 0
         */
        List<int[]> blocks = new ArrayList<>();

        // Free blocks
        for (Map.Entry<Integer, TreeSet<Integer>> entry :
                freeLists.entrySet()) {

            int size = entry.getKey();

            for (int addr : entry.getValue()) {

                blocks.add(new int[] {
                        addr,
                        size,
                        1
                });
            }
        }

        // Allocated blocks
        Map<Integer, String> owners = new HashMap<>();

        for (Map.Entry<String, int[]> entry :
                allocated.entrySet()) {

            String process = entry.getKey();
            int[] info = entry.getValue();

            int addr = info[0];
            int size = info[1];

            blocks.add(new int[] {
                    addr,
                    size,
                    0
            });

            owners.put(addr, process);
        }

        // Sort according to physical address.
        blocks.sort(
                Comparator.comparingInt(block -> block[0])
        );

        /*
         * Build:
         *
         * | A 256k | 256k | 512k |
         */
        StringBuilder map = new StringBuilder();

        map.append("|");

        for (int[] block : blocks) {

            map.append(" ");

            if (block[2] == 0) {
                map.append(owners.get(block[0]))
                    .append(" ");
            }

            map.append(block[1])
               .append("k |");
        }

        return map.toString();
    }

    // ============================================================
    // SAVE EVENT
    // ============================================================

    public void saveEvent(
            int eventNo,
            String process,
            String status) {

        String map = getMemoryMap();

        String line =
                String.format(
                        "%-5d | %-7s | %-10s | %s",
                        eventNo,
                        process,
                        status,
                        map
                );

        history.add(line);
    }

    // ============================================================
    // PRINT FINAL TABLE
    // ============================================================

    public void printHistory() {

        System.out.println();
        System.out.println(
                "==============================================================="
        );
        System.out.println(
                "              MEMORY ALLOCATION / RELEASE TABLE"
        );
        System.out.println(
                "==============================================================="
        );

        System.out.println(
                "Event | Process | Status     | Memory Map"
        );

        System.out.println(
                "---------------------------------------------------------------"
        );

        for (String line : history) {
            System.out.println(line);
        }

        System.out.println(
                "==============================================================="
        );
    }

    // ============================================================
    // INPUT
    // ============================================================

    private static int parseSize(String s) {

        s = s.trim().toUpperCase();

        try {

            if (s.endsWith("M")) {

                return Integer.parseInt(
                        s.substring(
                                0,
                                s.length() - 1
                        ).trim()
                ) * 1024;
            }

            if (s.endsWith("K")) {

                return Integer.parseInt(
                        s.substring(
                                0,
                                s.length() - 1
                        ).trim()
                );
            }

            return Integer.parseInt(s);

        } catch (NumberFormatException ex) {

            return -1;
        }
    }

    private static String ask(
            Scanner in,
            String prompt) {

        System.out.print(prompt);

        return in.hasNextLine()
                ? in.nextLine().trim()
                : "";
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {

        Scanner in = new Scanner(System.in);

        System.out.println(
                "=== Buddy System Memory Management Simulation ==="
        );

        // --------------------------------------------------------
        // CAPACITY
        // --------------------------------------------------------

        String capLine = ask(
                in,
                "Total memory capacity [default 1M]: "
        );

        int cap =
                capLine.isEmpty()
                        ? 1024
                        : parseSize(capLine);

        if (cap <= 0) {

            System.out.println(
                    "Invalid capacity, using 1M."
            );

            cap = 1024;
        }

        if (Integer.bitCount(cap) != 1) {

            int rounded =
                    Integer.highestOneBit(cap);

            System.out.println(
                    "Capacity must be a power of two; "
                            + "using "
                            + rounded
                            + "K."
            );

            cap = rounded;
        }

        // --------------------------------------------------------
        // NUMBER OF PROCESSES
        // --------------------------------------------------------

        int n = 0;

        while (n < 1 || n > 26) {

            String s = ask(
                    in,
                    "How many processes? (1-26): "
            );

            try {
                n = Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                n = 0;
            }

            if (n < 1 || n > 26) {

                System.out.println(
                        "Please enter a number from 1 to 26."
                );
            }
        }

        // --------------------------------------------------------
        // PROCESS SIZES
        // --------------------------------------------------------

        Map<String, Integer> sizes =
                new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {

            String name =
                    String.valueOf(
                            (char) ('A' + i)
                    );

            int size = -1;

            while (size <= 0) {

                size = parseSize(
                        ask(
                                in,
                                "Memory needed by Process "
                                        + name
                                        + " (e.g. 200K, 1M): "
                        )
                );

                if (size <= 0) {

                    System.out.println(
                            "Invalid size, try again."
                    );
                }
            }

            sizes.put(name, size);
        }

        buddySystem mm =
                new buddySystem(cap);

        // --------------------------------------------------------
        // EVENTS
        // --------------------------------------------------------

        int eventNo = 0;

        while (true) {

            System.out.println();
            System.out.println("Processes:");

            for (Map.Entry<String, Integer> entry :
                    sizes.entrySet()) {

                System.out.printf(
                        "  %s  %5dK  %s%n",
                        entry.getKey(),
                        entry.getValue(),
                        mm.isAllocated(entry.getKey())
                                ? "[allocated]"
                                : "[not allocated]"
                );
            }

            String p = ask(
                    in,
                    "\nEvent - which process? (A-"
                            + (char) ('A' + n - 1)
                            + ", or 'done'): "
            ).toUpperCase();

            /*
             * IMPORTANT:
             *
             * When DONE is entered, stop collecting
             * events and display the final table.
             */
            if (p.isEmpty() || p.equals("DONE")) {
                break;
            }

            if (!sizes.containsKey(p)) {

                System.out.println(
                        "Unknown process '" + p + "'."
                );

                continue;
            }

            String act = ask(
                    in,
                    "Allocate or Release? (A/R): "
            ).toUpperCase();

            // ----------------------------------------------------
            // ALLOCATE
            // ----------------------------------------------------

            if (act.startsWith("A")) {

                eventNo++;

                boolean success =
                        mm.request(
                                p,
                                sizes.get(p)
                        );

                mm.saveEvent(
                        eventNo,
                        p,
                        success
                                ? "allocated"
                                : "denied"
                );
            }

            // ----------------------------------------------------
            // RELEASE
            // ----------------------------------------------------

            else if (act.startsWith("R")) {

                eventNo++;

                boolean success =
                        mm.release(p);

                mm.saveEvent(
                        eventNo,
                        p,
                        success
                                ? "released"
                                : "not allocated"
                );
            }

            // ----------------------------------------------------
            // INVALID
            // ----------------------------------------------------

            else {

                System.out.println(
                        "Please enter A (allocate) or R (release)."
                );
            }
        }

        // ========================================================
        // AFTER DONE
        // ========================================================

        mm.printHistory();

        System.out.println();
        System.out.println("Simulation finished.");

        in.close();
    }
}
