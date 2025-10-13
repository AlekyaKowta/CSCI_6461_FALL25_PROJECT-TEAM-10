package ui;

import javax.swing.*;

/**
 * Simulator UI (initial skeleton).
 * Add functionality in the next commit.
 */
public class SimulatorUI extends JFrame {
    public SimulatorUI() {
        super("CSCI 6461 Machine Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        // TODO: build UI components here in the next step
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimulatorUI().setVisible(true));
    }
}
