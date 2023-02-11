package com.dabomstew.pkrandom.newgui;

/*----------------------------------------------------------------------------*/
/*--  BatchRandomizationSettingsDialog.java - a dialog for configuring      --*/
/*--                                          batch randomization settings  --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.awt.*;
import java.util.*;
import com.dabomstew.pkrandom.BatchRandomizationSettings;
import com.dabomstew.pkrandom.SysConstants;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;

public class BatchRandomizationSettingsDialog extends JDialog {

    private JFileChooser outputDirectoryFileChooser, saveFileChooser;

    private final BatchRandomizationSettings currentSettings;

    public BatchRandomizationSettings getCurrentSettings() {
        return this.currentSettings;
    }

    public BatchRandomizationSettingsDialog(JFrame parent, BatchRandomizationSettings currentSettings) {
		super(parent, true);
		initComponents();
        add(mainPanel);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle");
        setTitle(bundle.getString("BatchRandomizationSettingsDialog.title"));
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        getRootPane().setDefaultButton(okButton);

        this.currentSettings = currentSettings.clone();

        initializeControls();
        setLocationRelativeTo(parent);
        pack();
        setVisible(true);
    }

    private void onOK() {
        updateSettings();
        setVisible(false);
    }

    private void onCancel() {
        // add your code here if necessary
        setVisible(false);
    }

    private void initializeControls() {
        outputDirectoryFileChooser = new JFileChooser();
		saveFileChooser = new JFileChooser();
        okButton.addActionListener(e -> onOK());
        cancelButton.addActionListener(e -> onCancel());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        mainPanel.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        SpinnerNumberModel numberOfRandomizedROMsModel = new SpinnerNumberModel(1,1, Integer.MAX_VALUE, 1);
        numberOfRandomizedROMsSpinner.setModel(numberOfRandomizedROMsModel);

        SpinnerNumberModel startingIndexModel = new SpinnerNumberModel(1,0, Integer.MAX_VALUE, 1);
        startingIndexSpinner.setModel(startingIndexModel);

        chooseDirectoryButton.addActionListener(e -> {
            int selectionResult = outputDirectoryFileChooser.showDialog(this, "Select");
            if (selectionResult == JFileChooser.APPROVE_OPTION) {
                outputDirectoryFileChooser.setCurrentDirectory(outputDirectoryFileChooser.getSelectedFile());
                outputDirectoryLabel.setText(outputDirectoryFileChooser.getSelectedFile().getAbsolutePath());
            }
        });

		chooseSaveFileButton.addActionListener(e -> {
			int selectionResult = saveFileChooser.showDialog(this, "Select");
			if (selectionResult == JFileChooser.APPROVE_OPTION) {
				saveFileChooser.setCurrentDirectory(saveFileChooser.getSelectedFile());
				saveFileLabel.setText(saveFileChooser.getSelectedFile().getName());
			}
		});

        setInitialControlValues();
        setControlsEnabled(currentSettings.isBatchRandomizationEnabled());
    }

    private void setInitialControlValues() {
        enableBatchRandomizationCheckBox.setSelected(currentSettings.isBatchRandomizationEnabled());
        generateLogFilesCheckBox.setSelected(currentSettings.shouldGenerateLogFile());
        autoAdvanceIndexCheckBox.setSelected(currentSettings.shouldAutoAdvanceStartingIndex());
        numberOfRandomizedROMsSpinner.setValue(currentSettings.getNumberOfRandomizedROMs());
        startingIndexSpinner.setValue(currentSettings.getStartingIndex());
        fileNamePrefixTextField.setText(currentSettings.getFileNamePrefix());
        outputDirectoryLabel.setText(currentSettings.getOutputDirectory());
        outputDirectoryFileChooser.setCurrentDirectory(new File(currentSettings.getOutputDirectory()));
        outputDirectoryFileChooser.setSelectedFile(new File(currentSettings.getOutputDirectory()));
        outputDirectoryFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		saveFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		String saveFilePath = currentSettings.getSaveFilePath();
		if (saveFilePath.isEmpty()) {
			saveFileChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH));
		} else {
			File saveFile = new File(saveFilePath);
			saveFileChooser.setCurrentDirectory(saveFile.getParentFile());
			saveFileChooser.setSelectedFile(saveFile);
			saveFileLabel.setText(saveFile.getName());
		}
        enableBatchRandomizationCheckBox.addActionListener(a -> setControlsEnabled(enableBatchRandomizationCheckBox.isSelected()));
    }

    private void setControlsEnabled(boolean enabled) {
        numberOfRandomizedROMsSpinner.setEnabled(enabled);
        startingIndexSpinner.setEnabled(enabled);
        fileNamePrefixTextField.setEnabled(enabled);
        generateLogFilesCheckBox.setEnabled(enabled);
        autoAdvanceIndexCheckBox.setEnabled(enabled);
        chooseDirectoryButton.setEnabled(enabled);
		chooseSaveFileButton.setEnabled(enabled);
    }

    private void updateSettings() {
        currentSettings.setBatchRandomizationEnabled(enableBatchRandomizationCheckBox.isSelected());
        currentSettings.setGenerateLogFile(generateLogFilesCheckBox.isSelected());
        currentSettings.setAutoAdvanceStartingIndex(autoAdvanceIndexCheckBox.isSelected());
        currentSettings.setNumberOfRandomizedROMs((Integer) numberOfRandomizedROMsSpinner.getValue());
        currentSettings.setStartingIndex((Integer) startingIndexSpinner.getValue());
        currentSettings.setFileNamePrefix(fileNamePrefixTextField.getText());
        currentSettings.setOutputDirectory(outputDirectoryFileChooser.getSelectedFile().getAbsolutePath());
		currentSettings.setSaveFilePath(saveFileChooser.getSelectedFile().getAbsolutePath());
    }

	private void initComponents() {
		ResourceBundle bundle = ResourceBundle.getBundle("com.dabomstew.pkrandom.newgui.Bundle");
		mainPanel = new JPanel();
		JPanel panel1 = new JPanel();
		okButton = new JButton();
		cancelButton = new JButton();
		JPanel hSpacer1 = new JPanel(null);
		JPanel hSpacer2 = new JPanel(null);
		JLabel label1 = new JLabel();
		fileNamePrefixTextField = new JTextField(20);
		JLabel label2 = new JLabel();
		JLabel label3 = new JLabel();
		startingIndexSpinner = new JSpinner();
		numberOfRandomizedROMsSpinner = new JSpinner();
		enableBatchRandomizationCheckBox = new JCheckBox();
		chooseDirectoryButton = new JButton();
		outputDirectoryLabel = new JLabel();
		chooseSaveFileButton = new JButton();
		saveFileLabel = new JLabel();
		generateLogFilesCheckBox = new JCheckBox();
		autoAdvanceIndexCheckBox = new JCheckBox();
		JPanel hSpacer3 = new JPanel(null);

		//======== mainPanel ========
		{
			mainPanel.setLayout(new GridBagLayout());

			//======== panel1 ========
			{
				panel1.setLayout(new GridBagLayout());

				//---- okButton ----
				okButton.setText(bundle.getString("BatchRandomizationSettingsDialog.okButton.text"));
				panel1.add(okButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
					GridBagConstraints.CENTER, GridBagConstraints.NONE,
					new Insets(0, 0, 0, 0), 0, 0));

				//---- cancelButton ----
				cancelButton.setOpaque(false);
				cancelButton.setText(bundle.getString("BatchRandomizationSettingsDialog.cancelButton.text"));
				panel1.add(cancelButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 1.0,
					GridBagConstraints.CENTER, GridBagConstraints.NONE,
					new Insets(0, 0, 0, 0), 0, 0));
			}
			mainPanel.add(panel1, new GridBagConstraints(1, 8, 3, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer1, new GridBagConstraints(0, 0, 1, 8, 1.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer2, new GridBagConstraints(4, 0, 1, 8, 1.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- label1 ----
			label1.setText(bundle.getString("BatchRandomizationSettingsDialog.startingIndexLabel.text"));
			mainPanel.add(label1, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- fileNamePrefixTextField ----
			fileNamePrefixTextField.setMargin(new Insets(2, 6, 2, 6));
			fileNamePrefixTextField.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.fileNamePrefixTextBox.toolTipText"));
			mainPanel.add(fileNamePrefixTextField, new GridBagConstraints(3, 4, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- label2 ----
			label2.setText(bundle.getString("BatchRandomizationSettingsDialog.fileNamePrefixLabel.text"));
			mainPanel.add(label2, new GridBagConstraints(1, 4, 1, 1, 1.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- label3 ----
			label3.setText(bundle.getString("BatchRandomizationSettingsDialog.numberOfRandomizedROMsLabel.text"));
			mainPanel.add(label3, new GridBagConstraints(1, 2, 1, 1, 1.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- startingIndexSpinner ----
			startingIndexSpinner.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.startingIndexSpinner.toolTipText"));
			mainPanel.add(startingIndexSpinner, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- numberOfRandomizedROMsSpinner ----
			numberOfRandomizedROMsSpinner.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.numberOfRandomizedROMsSpinner.toolTipText"));
			mainPanel.add(numberOfRandomizedROMsSpinner, new GridBagConstraints(3, 2, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- enableBatchRandomizationCheckBox ----
			enableBatchRandomizationCheckBox.setAlignmentY(0.5F);
			enableBatchRandomizationCheckBox.setText(bundle.getString("BatchRandomizationSettingsDialog.enableCheckBox.text"));
			enableBatchRandomizationCheckBox.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.enableCheckBox.toolTipText"));
			mainPanel.add(enableBatchRandomizationCheckBox, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0,
				GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- chooseDirectoryButton ----
			chooseDirectoryButton.setAlignmentY(0.0F);
			chooseDirectoryButton.setInheritsPopupMenu(true);
			chooseDirectoryButton.setText(bundle.getString("BatchRandomizationSettingsDialog.outputDirectoryButton.text"));
			chooseDirectoryButton.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.outputDirectoryButton.toolTipText"));
			mainPanel.add(chooseDirectoryButton, new GridBagConstraints(1, 3, 1, 1, 0.5, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- outputDirectoryLabel ----
			outputDirectoryLabel.setText("");
			mainPanel.add(outputDirectoryLabel, new GridBagConstraints(3, 3, 1, 1, 1.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- generateLogFilesCheckBox ----
			generateLogFilesCheckBox.setText(bundle.getString("BatchRandomizationSettingsDialog.generateLogFilesCheckBox.text"));
			generateLogFilesCheckBox.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.generateLogFilesCheckBox.toolTipText"));
			mainPanel.add(generateLogFilesCheckBox, new GridBagConstraints(3, 6, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- autoAdvanceIndexCheckBox ----
			autoAdvanceIndexCheckBox.setText(bundle.getString("BatchRandomizationSettingsDialog.autoAdvanceIndexCheckBox.text"));
			autoAdvanceIndexCheckBox.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.autoAdvanceIndexCheckBox.toolTipText"));
			mainPanel.add(autoAdvanceIndexCheckBox, new GridBagConstraints(3, 5, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer3, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));

			chooseSaveFileButton.setAlignmentY(0.0F);
			chooseSaveFileButton.setInheritsPopupMenu(true);
			chooseSaveFileButton.setText(bundle.getString("BatchRandomizationSettingsDialog.saveFileButton.text"));
			chooseSaveFileButton.setToolTipText(bundle.getString("BatchRandomizationSettingsDialog.saveFileButton.toolTipText"));
			mainPanel.add(chooseSaveFileButton, new GridBagConstraints(1, 7, 1, 1, 0.5, 0.0,
					GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
					new Insets(0, 0, 0, 0), 0, 0));

			saveFileLabel.setText("");
			mainPanel.add(saveFileLabel, new GridBagConstraints(3, 7, 1, 1, 1.0, 0.0,
					GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(0, 0, 0, 0), 0, 0));
		}
	}

	private JPanel mainPanel;
	private JButton okButton;
	private JButton cancelButton;
	private JTextField fileNamePrefixTextField;
	private JSpinner startingIndexSpinner;
	private JSpinner numberOfRandomizedROMsSpinner;
	private JCheckBox enableBatchRandomizationCheckBox;
	private JButton chooseDirectoryButton;
	private JLabel outputDirectoryLabel;
	private JButton chooseSaveFileButton;
	private JLabel saveFileLabel;
	private JCheckBox generateLogFilesCheckBox;
	private JCheckBox autoAdvanceIndexCheckBox;
}
