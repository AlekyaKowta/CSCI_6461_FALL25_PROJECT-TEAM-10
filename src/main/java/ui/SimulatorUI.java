



package ui;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Interface to simulate the essential methods needed by the GUI before the
 * MachineController is fully implemented.
 */
interface DummyController {
    void handleIPL(String loadFilePath);
    void handlePlaceholderAction(ActionEvent e);
    void handleRegisterLoad(String registerName);
}

public class SimulatorUI extends JFrame {

    // --- Custom Colors and Constants ---
    private static final Color LIGHT_BLUE = new Color(220, 230, 255); // Pale, light blue for background
    private static final Color DARK_CONTROL_BLUE = new Color(50, 100, 150); // Darker blue for buttons
    private static final Color BUTTON_TEXT_COLOR = Color.BLACK; // Changed to BLACK as requested
    private static final int SQUARE_BUTTON_SIZE = 24;
    private static final String REG_INIT_VALUE = "000000"; // 6 octal digits for 16 bits (GPR, IXR, PC, MAR, MBR, IR)
    private static final String STATUS_INIT_VALUE = "0000"; // 4 octal digits for CC, MFR

    // --- Register Fields ---
    private JTextField[] gprFields = new JTextField[4];
    private JTextField[] ixrFields = new JTextField[4];

    // Internal Registers
    private JTextField pcField, marField, mbrField, irField;
    private JTextField ccField, mfrField;

    // --- Input/Display Fields ---
    private JTextField binaryInputField, octalInputField;
    private JTextField programFileField;

    // --- Control Buttons ---
    private JButton iplButton, runButton, haltButton, singleStepButton;
    private JButton loadButton, loadPlusButton, storeButton, storePlusButton;
    private JButton trapButton;

    // --- Output Areas ---
    private JTextArea cacheContentArea, printerArea;
    private JTextField consoleInputField;

    private DummyController controller;

    // Constructor
    public SimulatorUI(DummyController controller) {
        this.controller = controller;
        initializeUI();
    }

    private void initializeUI() {
        setTitle("CSCI 6461 Machine Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        getContentPane().setBackground(LIGHT_BLUE);
        setLayout(new BorderLayout(15, 15));

        // 1. Center Panel: Registers, Inputs, and Buttons
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(LIGHT_BLUE);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;

        // ROW 0: Register Columns

        // A. GPR Column
        JPanel gprPanel = createRegisterColumn("GPR", gprFields, 0, 4, true, REG_INIT_VALUE);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3; centerPanel.add(gprPanel, gbc);

        // B. IXR Column
        JPanel ixrPanel = createRegisterColumn("IXR", ixrFields, 1, 4, true, REG_INIT_VALUE);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.3; centerPanel.add(ixrPanel, gbc);

        // C. Internal Register Column (PC, MAR, MBR, IR, CC, MFR)
        JPanel internalRegPanel = createInternalRegisterColumn();
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.4; centerPanel.add(internalRegPanel, gbc);

        // ROW 1: Inputs and Controls
        JPanel inputPanel = createInputPanel();
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        centerPanel.add(inputPanel, gbc);

        JPanel buttonPanel = createButtonPanel();
        gbc.gridx = 2; gbc.gridy = 1;
        gbc.gridwidth = 1;
        centerPanel.add(buttonPanel, gbc);

        // ROW 2: Program File Path
        JPanel filePanel = createProgramFilePanel();
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.SOUTH;
        centerPanel.add(filePanel, gbc);

        add(centerPanel, BorderLayout.CENTER);

        // 2. East Panel (Output/Console)
        add(createOutputPanel(), BorderLayout.EAST);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // --- Helper Methods for Styling and Components ---

    private JButton createStyledButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.setBackground(DARK_CONTROL_BLUE);
        button.setForeground(BUTTON_TEXT_COLOR); // Changed to BLACK
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createRaisedBevelBorder());
        button.addActionListener(listener);
        return button;
    }

    private JButton createSmallLoadButton(String registerName) {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(SQUARE_BUTTON_SIZE, SQUARE_BUTTON_SIZE));
        button.setMinimumSize(new Dimension(SQUARE_BUTTON_SIZE, SQUARE_BUTTON_SIZE));
        button.setBackground(DARK_CONTROL_BLUE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createRaisedBevelBorder());

        button.setActionCommand("LOAD_" + registerName.toUpperCase());
        button.addActionListener(e -> controller.handleRegisterLoad(registerName));
        return button;
    }

    /**
     * Creates a single column panel for GPR or IXR with register load buttons.
     */
    private JPanel createRegisterColumn(String title, JTextField[] fields, int start, int end, boolean includeLoadButtons, String initValue) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(LIGHT_BLUE);
        p.setBorder(BorderFactory.createTitledBorder(title));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Register fields and Load buttons
        for (int i = start; i < end; i++) {
            String regName = title + " " + i;
            JLabel label = new JLabel(regName);
            fields[i] = new JTextField(initValue, 6); // Set 6 octal digits
            fields[i].setEditable(false);

            // Label
            gbc.gridx = 0; gbc.gridy = i;
            p.add(label, gbc);

            // Field
            gbc.gridx = 1;
            p.add(fields[i], gbc);

            // Load Button
            if (includeLoadButtons) {
                gbc.gridx = 2;
                gbc.fill = GridBagConstraints.NONE;
                p.add(createSmallLoadButton(regName), gbc);
                gbc.fill = GridBagConstraints.HORIZONTAL; // Reset fill
            }
        }
        return p;
    }

    private JPanel createInternalRegisterColumn() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(LIGHT_BLUE);
        p.setBorder(BorderFactory.createTitledBorder("Internal Registers"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        String[] regNames = {"PC", "MAR", "MBR", "IR"};

        // Initialize 16-bit registers (PC, MAR, MBR, IR)
        pcField = new JTextField(REG_INIT_VALUE, 6); pcField.setEditable(false);
        marField = new JTextField(REG_INIT_VALUE, 6); marField.setEditable(false);
        mbrField = new JTextField(REG_INIT_VALUE, 6); mbrField.setEditable(false);
        irField = new JTextField(REG_INIT_VALUE, 6); irField.setEditable(false);

        JTextField[] fields = {pcField, marField, mbrField, irField};

        // PC, MAR, MBR fields with Load buttons
        for (int i = 0; i < 3; i++) {
            gbc.gridx = 0; gbc.gridy = i;
            p.add(new JLabel(regNames[i]), gbc);

            gbc.gridx = 1;
            p.add(fields[i], gbc);

            gbc.gridx = 2;
            gbc.fill = GridBagConstraints.NONE;
            p.add(createSmallLoadButton(regNames[i]), gbc);
            gbc.fill = GridBagConstraints.HORIZONTAL;
        }

        // IR field (no load button needed)
        int i = 3;
        gbc.gridx = 0; gbc.gridy = i;
        p.add(new JLabel(regNames[i]), gbc);
        gbc.gridx = 1;
        p.add(fields[i], gbc);

        // CC and MFR fields (Status Registers)
        ccField = new JTextField(STATUS_INIT_VALUE, 4); ccField.setEditable(false);
        mfrField = new JTextField(STATUS_INIT_VALUE, 4); mfrField.setEditable(false);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        p.add(new JLabel("CC"), gbc); gbc.gridx = 1; p.add(ccField, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        p.add(new JLabel("MFR"), gbc); gbc.gridx = 1; p.add(mfrField, gbc);

        return p;
    }

    private JPanel createInputPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(LIGHT_BLUE);
        p.setBorder(BorderFactory.createTitledBorder("Input/Status"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Binary Input/Display (16 bits)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        p.add(new JLabel("BINARY"), gbc);
        gbc.gridy = 1;
        binaryInputField = new JTextField("0000000000000000", 20);
        binaryInputField.setEditable(true);
        p.add(binaryInputField, gbc);

        // Octal Input (6 octal digits for 16 bits)
        gbc.gridy = 2; gbc.gridwidth = 1;
        p.add(new JLabel("OCTAL INPUT"), gbc);
        gbc.gridx = 1;
        octalInputField = new JTextField("000000", 6);
        octalInputField.setEditable(true);
        p.add(octalInputField, gbc);

        return p;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel(new GridLayout(4, 2, 10, 10));
        p.setBackground(LIGHT_BLUE);

        // Row 1: Load / Run
        loadButton = createStyledButton("Load", this::handlePlaceholderAction);
        p.add(loadButton);

        runButton = createStyledButton("Run", this::handlePlaceholderAction);
        p.add(runButton);

        // Row 2: Load+ / Step
        loadPlusButton = createStyledButton("Load+", this::handlePlaceholderAction);
        p.add(loadPlusButton);

        singleStepButton = createStyledButton("Step", this::handlePlaceholderAction);
        p.add(singleStepButton);

        // Row 3: Store / Halt
        storeButton = createStyledButton("Store", this::handlePlaceholderAction);
        p.add(storeButton);

        haltButton = createStyledButton("Halt", this::handlePlaceholderAction);
        p.add(haltButton);

        // Row 4: Store+ / IPL
        storePlusButton = createStyledButton("Store+", this::handlePlaceholderAction);
        p.add(storePlusButton);

        iplButton = createStyledButton("IPL", this::handleIPL);
        p.add(iplButton);

        return p;
    }

    private JPanel createProgramFilePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBackground(LIGHT_BLUE);

        programFileField = new JTextField("<Program Load File Path>", 40);
        programFileField.setEditable(false);

        p.add(new JLabel("Program File:"), BorderLayout.WEST);
        p.add(programFileField, BorderLayout.CENTER);

        return p;
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(LIGHT_BLUE);
        p.setBorder(BorderFactory.createTitledBorder("Console Output/Input"));

        // Cache Content Area
        cacheContentArea = new JTextArea(8, 30);
        cacheContentArea.setEditable(false);
        cacheContentArea.setBorder(BorderFactory.createTitledBorder("Cache Content"));
        p.add(new JScrollPane(cacheContentArea));

        // Console Printer Area
        printerArea = new JTextArea(10, 30);
        printerArea.setEditable(false);
        printerArea.setBorder(BorderFactory.createTitledBorder("Printer"));
        p.add(new JScrollPane(printerArea));

        // Console Input Field
        consoleInputField = new JTextField(30);
        consoleInputField.setBorder(BorderFactory.createTitledBorder("Console Input"));
        p.add(consoleInputField);

        return p;
    }

    // --- Placeholder Action Handlers ---

    private void handleIPL(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser(new File("."));
        fileChooser.setDialogTitle("Select Program Load File");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File loadFile = fileChooser.getSelectedFile();
            programFileField.setText(loadFile.getAbsolutePath());
            printerArea.append("IPL button pressed. Ready to load program.\n");
        }
    }

    private void handlePlaceholderAction(ActionEvent e) {
        printerArea.append("Button '" + e.getActionCommand() + "' pressed. Logic placeholder.\n");
    }

    // --- Main Method to run the UI ---
    public static void main(String[] args) {
        // Dummy implementation of the controller interface for compilation/display
        DummyController dummyController = new DummyController() {
            @Override
            public void handleIPL(String loadFilePath) {}
            @Override
            public void handlePlaceholderAction(ActionEvent e) {}
            @Override
            public void handleRegisterLoad(String registerName) {
                System.out.println("Load button pressed for register: " + registerName);
            }
        };

        SwingUtilities.invokeLater(() -> new SimulatorUI(dummyController));
    }
}

