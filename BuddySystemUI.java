import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;

/**
 * Buddy System Memory Management Simulation - Swing UI (pure Java, no libraries).
 *
 * Compile:  javac BuddySystemUI.java
 * Run:      java BuddySystemUI
 */
public class BuddySystemUI extends JFrame {

    // ============================================================
    // DATA CLASSES
    // ============================================================

    /** One block of memory (free or allocated). */
    static class Block {
        final int addr, size, requested;
        final boolean free;
        final String owner;

        Block(int addr, int size, boolean free, String owner, int requested) {
            this.addr = addr;
            this.size = size;
            this.free = free;
            this.owner = owner;
            this.requested = requested;
        }

        String label() {
            return free ? size + "k" : owner + " " + size + "k";
        }
    }

    /** One row of the history table. */
    static class Event {
        final int no;
        final String process, status;
        final int capacity;
        final List<Block> blocks;

        Event(int no, String process, String status, int capacity, List<Block> blocks) {
            this.no = no;
            this.process = process;
            this.status = status;
            this.capacity = capacity;
            this.blocks = blocks;
        }
    }

    // ============================================================
    // BUDDY SYSTEM LOGIC (same algorithm as the console version)
    // ============================================================

    static class Buddy {
        final int capacity;
        private final TreeMap<Integer, TreeSet<Integer>> freeLists = new TreeMap<>();
        private final Map<String, int[]> allocated = new LinkedHashMap<>(); // {addr, blockSize, requested}

        Buddy(int capacity) {
            this.capacity = capacity;
            addFree(0, capacity);
        }

        private void addFree(int addr, int size) {
            freeLists.computeIfAbsent(size, k -> new TreeSet<>()).add(addr);
        }

        private boolean removeFree(int addr, int size) {
            TreeSet<Integer> set = freeLists.get(size);
            if (set == null || !set.remove(addr)) return false;
            if (set.isEmpty()) freeLists.remove(size);
            return true;
        }

        private static int nextPowerOfTwo(int n) {
            int p = 1;
            while (p < n) p <<= 1;
            return p;
        }

        boolean isAllocated(String name) {
            return allocated.containsKey(name);
        }

        boolean request(String name, int sizeK) {
            if (allocated.containsKey(name) || sizeK <= 0) return false;

            int blockSize = nextPowerOfTwo(sizeK);
            if (blockSize > capacity) return false;

            Integer foundSize = null;
            for (Integer s : freeLists.tailMap(blockSize, true).keySet()) {
                foundSize = s;
                break;
            }
            if (foundSize == null) return false;

            int addr = freeLists.get(foundSize).first();
            removeFree(addr, foundSize);

            int cur = foundSize;
            while (cur > blockSize) {
                cur /= 2;
                addFree(addr + cur, cur); // upper half becomes free
            }

            allocated.put(name, new int[]{addr, blockSize, sizeK});
            return true;
        }

        boolean release(String name) {
            int[] info = allocated.remove(name);
            if (info == null) return false;

            int addr = info[0], size = info[1];

            while (size < capacity) {
                int buddy = addr ^ size;
                if (!removeFree(buddy, size)) break;
                addr = Math.min(addr, buddy);
                size *= 2;
            }
            addFree(addr, size);
            return true;
        }

        List<Block> getBlocks() {
            List<Block> out = new ArrayList<>();
            for (Map.Entry<Integer, TreeSet<Integer>> e : freeLists.entrySet())
                for (int addr : e.getValue())
                    out.add(new Block(addr, e.getKey(), true, null, 0));
            for (Map.Entry<String, int[]> e : allocated.entrySet()) {
                int[] i = e.getValue();
                out.add(new Block(i[0], i[1], false, e.getKey(), i[2]));
            }
            out.sort(Comparator.comparingInt(b -> b.addr));
            return out;
        }

        Set<String> processNames() {
            return allocated.keySet();
        }
    }

    // ============================================================
    // COLORS
    // ============================================================

    private static final Color FREE_COLOR = new Color(0xE8ECEF);
    private static final Color[] PALETTE = {
            new Color(0x4F8EF7), new Color(0xF2994A), new Color(0x27AE60), new Color(0x9B51E0),
            new Color(0xEB5757), new Color(0x2D9CDB), new Color(0xE0B000), new Color(0x1ABC9C),
            new Color(0xD6336C), new Color(0x7F8C8D)
    };
    private static final Map<String, Color> processColors = new HashMap<>();

    static Color colorFor(String process) {
        return processColors.computeIfAbsent(process, p -> PALETTE[processColors.size() % PALETTE.length]);
    }

    // ============================================================
    // MEMORY MAP PANEL (also used as table cell renderer)
    // ============================================================

    static class MemoryPanel extends JPanel implements TableCellRenderer {
        private List<Block> blocks = new ArrayList<>();
        private int capacity = 1;

        MemoryPanel() {
            setOpaque(true);
            setToolTipText("");
        }

        void setData(List<Block> blocks, int capacity) {
            this.blocks = blocks;
            this.capacity = Math.max(1, capacity);
            repaint();
        }

        private Block blockAt(int x) {
            int w = Math.max(1, getWidth());
            for (Block b : blocks) {
                int x1 = (int) ((long) b.addr * w / capacity);
                int x2 = (int) ((long) (b.addr + b.size) * w / capacity);
                if (x >= x1 && x < x2) return b;
            }
            return null;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            Block b = blockAt(e.getX());
            if (b == null) return null;
            if (b.free)
                return "Free block | address " + b.addr + "k | size " + b.size + "k";
            return b.owner + " | address " + b.addr + "k | block " + b.size + "k | requested "
                    + b.requested + "k | wasted " + (b.size - b.requested) + "k";
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int W = getWidth(), H = getHeight();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);

            FontMetrics fm = g.getFontMetrics();

            for (Block b : blocks) {
                int x1 = (int) ((long) b.addr * W / capacity);
                int x2 = (int) ((long) (b.addr + b.size) * W / capacity);
                int bw = x2 - x1;

                g.setColor(b.free ? FREE_COLOR : colorFor(b.owner));
                g.fillRect(x1, 0, bw, H);
                g.setColor(new Color(0x55, 0x55, 0x55));
                g.drawRect(x1, 0, bw - 1, H - 1);

                // Show the full label if it fits, else shorter forms, else nothing.
                String[] options = b.free
                        ? new String[]{b.size + "k"}
                        : new String[]{b.owner + " " + b.size + "k", b.owner, ""};
                for (String t : options) {
                    if (t.isEmpty()) break;
                    if (fm.stringWidth(t) + 6 <= bw) {
                        g.setColor(b.free ? new Color(0x555555) : Color.WHITE);
                        g.drawString(t, x1 + (bw - fm.stringWidth(t)) / 2,
                                (H + fm.getAscent() - fm.getDescent()) / 2);
                        break;
                    }
                }
            }
            g.dispose();
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            if (value instanceof Event) {
                Event ev = (Event) value;
                setData(ev.blocks, ev.capacity);
            }
            return this;
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private Buddy buddy;
    private int eventNo = 0;

    private final JTextField capacityField = new JTextField("1M", 6);
    private final JTextField processField = new JTextField(6);
    private final JTextField sizeField = new JTextField(6);
    private final JComboBox<String> releaseBox = new JComboBox<>();
    private final MemoryPanel currentMap = new MemoryPanel();
    private final JLabel statsLabel = new JLabel(" ");
    private final JLabel messageLabel = new JLabel(" ");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Event", "Process", "Status", "Memory Map"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    public BuddySystemUI() {
        super("Buddy System Memory Management Simulation");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        // ---- Controls ----
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(new TitledBorder("Controls"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        // Row 0: capacity
        c.gridy = 0;
        c.gridx = 0;
        controls.add(new JLabel("Total memory:"), c);
        c.gridx = 1;
        controls.add(capacityField, c);
        c.gridx = 2;
        JButton resetBtn = new JButton("Set / Reset");
        controls.add(resetBtn, c);
        c.gridx = 3;
        controls.add(new JLabel("(e.g. 1M, 512K - rounded down to a power of two)"), c);

        // Row 1: allocate
        c.gridy = 1;
        c.gridx = 0;
        controls.add(new JLabel("Process:"), c);
        c.gridx = 1;
        controls.add(processField, c);
        c.gridx = 2;
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sizePanel.add(new JLabel("Size:"));
        sizePanel.add(sizeField);
        controls.add(sizePanel, c);
        c.gridx = 3;
        JButton allocBtn = new JButton("Allocate");
        controls.add(allocBtn, c);

        // Row 2: release
        c.gridy = 2;
        c.gridx = 0;
        controls.add(new JLabel("Release:"), c);
        c.gridx = 1;
        releaseBox.setEditable(true);
        releaseBox.setPreferredSize(new Dimension(90, releaseBox.getPreferredSize().height));
        controls.add(releaseBox, c);
        c.gridx = 2;
        JButton releaseBtn = new JButton("Release");
        controls.add(releaseBtn, c);

        // ---- Current memory map ----
        JPanel mapPanel = new JPanel(new BorderLayout(4, 4));
        mapPanel.setBorder(new TitledBorder("Current memory map (hover a block for details)"));
        currentMap.setPreferredSize(new Dimension(100, 60));
        mapPanel.add(currentMap, BorderLayout.CENTER);
        statsLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        mapPanel.add(statsLabel, BorderLayout.SOUTH);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(controls);
        top.add(Box.createVerticalStrut(8));
        top.add(mapPanel);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        mapPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(top, BorderLayout.NORTH);

        // ---- History table ----
        table.setRowHeight(46);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setMaxWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(700);
        table.getColumnModel().getColumn(3).setCellRenderer(new MemoryPanel());
        table.getColumnModel().getColumn(2).setCellRenderer(new StatusRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new TitledBorder("Memory allocation / release table"));
        root.add(scroll, BorderLayout.CENTER);

        // ---- Message bar ----
        messageLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        root.add(messageLabel, BorderLayout.SOUTH);

        // ---- Actions ----
        resetBtn.addActionListener(e -> resetSimulation());
        capacityField.addActionListener(e -> resetSimulation());
        allocBtn.addActionListener(e -> doAllocate());
        sizeField.addActionListener(e -> doAllocate());
        processField.addActionListener(e -> sizeField.requestFocusInWindow());
        releaseBtn.addActionListener(e -> doRelease());

        resetSimulation();

        setSize(1000, 640);
        setMinimumSize(new Dimension(760, 480));
        setLocationRelativeTo(null);
    }

    // ---- Status column coloring ----
    static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc,
                                                       int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            String s = String.valueOf(v);
            Color col = Color.DARK_GRAY;
            if (s.equals("allocated")) col = new Color(0x1E8E3E);
            else if (s.equals("released")) col = new Color(0x1A73E8);
            else if (s.equals("denied") || s.equals("not allocated")) col = new Color(0xD93025);
            else if (s.equals("already alloc")) col = new Color(0xE37400);
            setForeground(col);
            setFont(getFont().deriveFont(Font.BOLD));
            return this;
        }
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    private static int parseSize(String s) {
        s = s.trim().toUpperCase();
        try {
            if (s.endsWith("M")) return Integer.parseInt(s.substring(0, s.length() - 1).trim()) * 1024;
            if (s.endsWith("K")) return Integer.parseInt(s.substring(0, s.length() - 1).trim());
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void message(String text, boolean error) {
        messageLabel.setForeground(error ? new Color(0xD93025) : new Color(0x1E8E3E));
        messageLabel.setText(text);
    }

    private void resetSimulation() {
        String txt = capacityField.getText().trim();
        int cap = txt.isEmpty() ? 1024 : parseSize(txt);
        String note = "";

        if (cap <= 0) {
            cap = 1024;
            note = "Invalid capacity, using 1M. ";
        }
        if (Integer.bitCount(cap) != 1) {
            int rounded = Integer.highestOneBit(cap);
            note += "Capacity must be a power of two; using " + rounded + "K. ";
            cap = rounded;
        }

        buddy = new Buddy(cap);
        eventNo = 0;
        processColors.clear();
        tableModel.setRowCount(0);
        capacityField.setText(cap % 1024 == 0 ? (cap / 1024) + "M" : cap + "K");
        refreshCurrent();
        message(note + "Simulation ready with " + cap + "K of memory.", !note.isEmpty());
        processField.requestFocusInWindow();
    }

    private void doAllocate() {
        String p = processField.getText().trim().toUpperCase();
        int size = parseSize(sizeField.getText());

        if (p.isEmpty() || size <= 0) {
            message("Invalid process or size (example: P1 and 200K), event cancelled.", true);
            return;
        }

        eventNo++;
        String status;

        if (buddy.isAllocated(p)) {
            status = "already alloc";
            message(p + " is already allocated.", true);
        } else if (buddy.request(p, size)) {
            status = "allocated";
            message(p + " allocated (" + size + "K requested).", false);
        } else {
            status = "denied";
            message(p + " denied (not enough memory).", true);
        }

        saveEvent(p, status);
        processField.setText("");
        sizeField.setText("");
        processField.requestFocusInWindow();
    }

    private void doRelease() {
        Object sel = releaseBox.getEditor().getItem();
        String p = sel == null ? "" : sel.toString().trim().toUpperCase();

        if (p.isEmpty()) {
            message("Enter or pick a process to release.", true);
            return;
        }

        eventNo++;
        boolean ok = buddy.release(p);
        saveEvent(p, ok ? "released" : "not allocated");
        message(ok ? p + " released." : p + " is not allocated.", !ok);
    }

    private void saveEvent(String process, String status) {
        Event ev = new Event(eventNo, process, status, buddy.capacity, buddy.getBlocks());
        tableModel.addRow(new Object[]{ev.no, ev.process, ev.status, ev});
        int last = tableModel.getRowCount() - 1;
        table.scrollRectToVisible(table.getCellRect(last, 0, true));
        refreshCurrent();
    }

    private void refreshCurrent() {
        List<Block> blocks = buddy.getBlocks();
        currentMap.setData(blocks, buddy.capacity);

        int used = 0, requested = 0;
        for (Block b : blocks) {
            if (!b.free) {
                used += b.size;
                requested += b.requested;
            }
        }
        statsLabel.setText(String.format(
                "Total: %dK   |   Allocated blocks: %dK   |   Free: %dK   |   Internal fragmentation: %dK",
                buddy.capacity, used, buddy.capacity - used, used - requested));

        // Refresh release dropdown
        releaseBox.removeAllItems();
        for (String name : buddy.processNames()) releaseBox.addItem(name);
        releaseBox.setSelectedItem(null);
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new BuddySystemUI().setVisible(true);
        });
    }
}