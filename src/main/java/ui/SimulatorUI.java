package src.main.java.ui;

import src.main.java.core.MachineController;
import src.main.java.core.MachineState;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

/**
 * The SimulatorUI class represents the graphical user interface (GUI), acting as the
 * front panel for the C6461 machine simulator.
 * * It is built using Java Swing and is responsible for:
 * 1. **Visualizing Machine State:** Displaying the current values of all registers (GPRs,
 * PC, MAR, MBR, etc.) and memory in the required ISA octal format.
 * 2. **Handling User Input:** Processing all operator actions, including button clicks
 * (IPL, Run, Step), manual data deposit into registers, and console controls.
 * 3. **Interface to CPU:** Communicating user events to the MachineController for execution
 * and updating its own displays based on the results from the MachineState.
 * * The UI ensures architectural compliance by displaying PC/MAR in 4-digit octal and
 * 16-bit registers in 6-digit octal format.
 */
public class SimulatorUI extends JFrame {

    // Headless mode flag: when true, skip building/showing the Swing frame and no-op UI updates
    private final boolean headless;

    // --- Custom Colors and Constants ---
    private static final Color LIGHT_BLUE = new Color(220, 230, 255); // Pale, light blue for background
    private static final Color DARK_CONTROL_BLUE = new Color(50, 100, 150); // Darker blue for buttons
    private static final Color BUTTON_TEXT_COLOR = Color.BLACK; // Changed to BLACK as requested
    private static final int SQUARE_BUTTON_SIZE = 24;

    // R/IXR/MBR/IR are 16-bit (6 octal digits)
    private static final String REG_INIT_VALUE = "000000";

    // PC/MAR are 12-bit (4 octal digits)
    private static final String REG_12BIT_INIT_VALUE = "0000";

    // CC/MFR are 4-bit (4 octal digits, padded as needed for display)
    private static final String STATUS_INIT_VALUE = "0000";

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

    private MachineController controller;

    // Constructor (normal GUI)
    public SimulatorUI() {
        this(false);
    }

    // Constructor (headless/test mode)
    public SimulatorUI(boolean headless) {
        this.headless = headless;

        // Initialize MachineState and MachineController, passing 'this' (the UI)
        MachineState state = new MachineState();
        this.controller = new MachineController(state, this);

        if (!headless) {
            initializeUI();
            addInputListeners();
            updateDisplays(); // Initial display update
        } else {
            // Minimal components to satisfy controller interactions in tests
            this.printerArea = new JTextArea();
            this.consoleInputField = new JTextField();
            this.octalInputField = new JTextField("000000", 6);
            this.binaryInputField = new JTextField("0000000000000000", 20);
        }
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
        JPanel gprPanel = createRegisterColumn("GPR", gprFields, 0, 4, true, REG_INIT_VALUE, 6);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3; centerPanel.add(gprPanel, gbc);

        // B. IXR Column
        JPanel ixrPanel = createRegisterColumn("IXR", ixrFields, 1, 4, true, REG_INIT_VALUE, 6);
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

    // Input Listener Method
    private void addInputListeners(){
        octalInputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                octalToBinaryConverter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                octalToBinaryConverter();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                // Not used for plain text fields
            }
        });

        consoleInputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = consoleInputField.getText();
                if (!input.isEmpty()) {
                    // Deposit the input into the controller's buffer
                    controller.depositInput(input);

                    // Clear the input field after deposit for a clean look
                    consoleInputField.setText("");

                    // Print the user's input to the printer area for confirmation
                    printerArea.append(">> " + input + "\n");

                    // If the machine is paused (waiting for input), resume it
                    if (!controller.isRunning()) {
                        controller.runProgram();
                    }
                }
            }
        });
    }

    /**
     * Converts the text in the octal input field to a 16-bit binary string
     * and displays it in the binary input field.
     */
    private void octalToBinaryConverter() {
        String octalStr = octalInputField.getText().trim();
        if (octalStr.isEmpty() || octalStr.length() > 6) {
            binaryInputField.setText("0000000000000000");
            return;
        }

        try {
            // 1. Convert octal string to integer (using base 8)
            int decimalValue = Integer.parseInt(octalStr, 8);

            // 2. Ensure the value fits within 16 bits (0 to 65535)
            // Mask the value and convert to binary string
            String binaryStr = Integer.toBinaryString(decimalValue & 0xFFFF);

            // 3. Pad with leading zeros to ensure a fixed 16-bit length
            String paddedBinary = String.format("%16s", binaryStr).replace(' ', '0');

            // 4. Update the Binary field
            binaryInputField.setText(paddedBinary);

        } catch (NumberFormatException e) {
            // Handle case where input contains non-octal characters or is too large
            binaryInputField.setText("Invalid OCTAL Input!");
        }
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
    private JPanel createRegisterColumn(
            String title,
            JTextField[] fields,
            int start, int end,
            boolean includeLoadButtons,
            String initValue,
            int fieldSize
    ) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(LIGHT_BLUE);
        p.setBorder(BorderFactory.createTitledBorder(title));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.anchor = GridBagConstraints.WEST;

        for (int i = start; i < end; i++) {
            int row = i - start;              // make rows start at 0 inside this panel

            String regName = title + " " + i;

            // Label: column 0 (no stretch)
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            p.add(new JLabel(regName), gbc);

            // Field: column 1 (stretch horizontally)
            fields[i] = new JTextField(initValue, fieldSize);
            fields[i].setEditable(false);

            gbc.gridx = 1;
            gbc.gridy = row;
            gbc.weightx = 1.0;                      // <-- give the text field the width
            gbc.fill = GridBagConstraints.HORIZONTAL;
            p.add(fields[i], gbc);

            // Optional Load button: column 2 (no stretch)
            if (includeLoadButtons) {
                gbc.gridx = 2;
                gbc.gridy = row;
                gbc.weightx = 0.0;
                gbc.fill = GridBagConstraints.NONE;
                p.add(createSmallLoadButton(regName), gbc);
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

        // Initialize 12-bit registers (PC, MAR)
        pcField = new JTextField(REG_12BIT_INIT_VALUE, 4); pcField.setEditable(false);
        marField = new JTextField(REG_12BIT_INIT_VALUE, 4); marField.setEditable(false);
        // Initialize 16-bit registers (MBR, IR)
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

    // Public getter for the printer area (used by the Controller)
    public JTextArea getPrinterArea() {
        return printerArea;
    }

    public JTextField getConsoleInputField() {
        return consoleInputField;
    }

    // Expose controller for headless/testing harnesses
    public MachineController getController() {
        return controller;
    }

    // Public getter for the Octal Input field (needed by MachineController for manual loads)
    public JTextField getOctalInputField() {
        return octalInputField;
    }

    // Public getter for the Binary Input field (if needed, though MBR sets the value)
    public JTextField getBinaryInputField() {
        return binaryInputField;
    }

    /**
     * Updates all register and memory displays from the MachineState.
     */
    public void updateDisplays() {
        if (headless) return; // No-op in headless mode
        MachineState state = controller.getMachineState();

        // PC and MAR are 12-bit, displayed as %04o (4 octal digits)
        pcField.setText(String.format("%04o", state.getPC()));
        marField.setText(String.format("%04o", state.getMAR()));

        // MBR and IR are 16-bit, displayed as %06o (6 octal digits)
        mbrField.setText(String.format("%06o", state.getMBR()));
        irField.setText(String.format("%06o", state.getIR()));

        // CC and MFR are 4-bit, displayed as %04o (4 octal digits)
        ccField.setText(String.format("%04o", state.getCC()));
        mfrField.setText(String.format("%04o", state.getMFR()));

        // Update GPRs and IXRs
        for (int i = 0; i < 4; i++) {
            gprFields[i].setText(String.format("%06o", state.getGPR(i)));
        }
        for (int i = 1; i < 4; i++) {
            ixrFields[i].setText(String.format("%06o", state.getIXR(i)));
        }

        // Update Binary/Octal input field with contents of MBR (or memory at MAR, as needed)
        int octalValue = state.getMBR();
        octalInputField.setText(String.format("%06o", octalValue));
        binaryInputField.setText(String.format("%16s", Integer.toBinaryString(octalValue)).replace(' ', '0'));

        cacheContentArea.setText(state.getCache().getCacheStateString());
    }

    // --- IPL Button Action Handlers ---

    private void handleIPL(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser(new File("."));
        fileChooser.setDialogTitle("Select Program Load File"); // Dialogue to ask for file location

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File loadFile = fileChooser.getSelectedFile();
            programFileField.setText(loadFile.getAbsolutePath());
            printerArea.append("Loading program from: " + loadFile.getName() + "...\n");

            try {
                // Call the controller's implementation of the ROM load simulation
                controller.performIPL(loadFile.getAbsolutePath());
                updateDisplays();
            } catch (IOException ex) {
                // If the program encounters an error, display an error message on the console printer and stop.
                printerArea.append("IPL ERROR: " + ex.getMessage() + "\n");
            } catch (NumberFormatException ex) {
                // Handle parsing errors if the file is malformed
                printerArea.append("IPL ERROR: Invalid octal data found in load file.\n");
            }
        }
    }

    public void setStepRunButtonsEnabled(boolean enabled) {
        if (headless) return; // No-op in headless mode
        singleStepButton.setEnabled(enabled);
        runButton.setEnabled(enabled);
    }

    // --- Placeholder Action Handlers (Now calling the Controller) ---

    private void handlePlaceholderAction(ActionEvent e) {
        controller.handlePlaceholderAction(e);
    }

    // --- Main Method to run the UI ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimulatorUI());
    }
}