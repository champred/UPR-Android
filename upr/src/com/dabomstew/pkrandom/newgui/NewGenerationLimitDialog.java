package com.dabomstew.pkrandom.newgui;

/*----------------------------------------------------------------------------*/
/*--  NewGenerationLimitDialog.java - a GUI interface to allow users to     --*/
/*--                                  limit which Pokemon appear based on   --*/
/*--                                  their generation of origin.           --*/
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
import com.dabomstew.pkrandom.pokemon.GenRestrictions;

import javax.swing.*;

public class NewGenerationLimitDialog extends javax.swing.JDialog {

	private boolean pressedOk;
    private boolean isXY;

    public NewGenerationLimitDialog(JFrame parent, GenRestrictions current, int generation, boolean isXY) {
		super(parent, true);
		initComponents();
        add(mainPanel);
        this.isXY = isXY;
        initComponents2();
        initialState(generation);
        if (current != null) {
            current.limitToGen(generation);
            restoreFrom(current);
        }
        enableAndDisableBoxes();
        pressedOk = false;
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    public boolean pressedOK() {
        return pressedOk;
    }

    public GenRestrictions getChoice() {
        GenRestrictions gr = new GenRestrictions();
        gr.allow_gen1 = gen1CheckBox.isSelected();
        gr.allow_gen2 = gen2CheckBox.isSelected();
        gr.allow_gen3 = gen3CheckBox.isSelected();
        gr.allow_gen4 = gen4CheckBox.isSelected();
        gr.allow_gen5 = gen5CheckBox.isSelected();
        gr.allow_gen6 = gen6CheckBox.isSelected();
        gr.allow_gen7 = gen7CheckBox.isSelected();
        gr.allow_evolutionary_relatives = allowEvolutionaryRelativesCheckBox.isSelected();
        return gr;
    }

    private void initialState(int generation) {
        if (generation < 2) {
            gen2CheckBox.setVisible(false);
        }
        if (generation < 3) {
            gen3CheckBox.setVisible(false);
        }
        if (generation < 4) {
            gen4CheckBox.setVisible(false);
        }
        if (generation < 5) {
            gen5CheckBox.setVisible(false);
        }
        if (generation < 6) {
            gen6CheckBox.setVisible(false);
        }
        if (generation < 7) {
            gen7CheckBox.setVisible(false);
        }

        allowEvolutionaryRelativesCheckBox.setEnabled(false);
        allowEvolutionaryRelativesCheckBox.setSelected(false);
    }

    private void restoreFrom(GenRestrictions restrict) {
        gen1CheckBox.setSelected(restrict.allow_gen1);
        gen2CheckBox.setSelected(restrict.allow_gen2);
        gen3CheckBox.setSelected(restrict.allow_gen3);
        gen4CheckBox.setSelected(restrict.allow_gen4);
        gen5CheckBox.setSelected(restrict.allow_gen5);
        gen6CheckBox.setSelected(restrict.allow_gen6);
        gen7CheckBox.setSelected(restrict.allow_gen7);
        allowEvolutionaryRelativesCheckBox.setSelected(restrict.allow_evolutionary_relatives);
    }

    private void initComponents2() {
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle");
        setTitle(bundle.getString("GenerationLimitDialog.title"));
        gen1CheckBox.addActionListener(ev -> enableAndDisableBoxes());
        gen2CheckBox.addActionListener(ev -> enableAndDisableBoxes());
        gen3CheckBox.addActionListener(ev -> enableAndDisableBoxes());
        gen4CheckBox.addActionListener(ev -> enableAndDisableBoxes());
        gen5CheckBox.addActionListener(ev -> enableAndDisableBoxes());
        gen6CheckBox.addActionListener(ev -> enableAndDisableBoxes());
        gen7CheckBox.addActionListener(ev -> enableAndDisableBoxes());
        allowEvolutionaryRelativesCheckBox.addActionListener(ev -> enableAndDisableBoxes());
        okButton.addActionListener(evt -> okButtonActionPerformed());
        cancelButton.addActionListener(evt -> cancelButtonActionPerformed());
        xyWarningLabel.setVisible(isXY);
        if (isXY) {
            okButton.setEnabled(false);
        }
        pack();
    }

    private void enableAndDisableBoxes() {
        // To prevent softlocks on the Successor Korrina fight, only turn
        // on the OK button for XY if at least one of Gens 1-4 is selected.
        if (isXY) {
            if (gen1CheckBox.isSelected() || gen2CheckBox.isSelected() || gen3CheckBox.isSelected() || gen4CheckBox.isSelected()) {
                okButton.setEnabled(true);
            } else {
                okButton.setEnabled(false);
            }
        }

        if (gen1CheckBox.isSelected() || gen2CheckBox.isSelected() || gen3CheckBox.isSelected() ||
                gen4CheckBox.isSelected() || gen5CheckBox.isSelected() || gen6CheckBox.isSelected() ||
                gen7CheckBox.isSelected()) {
            allowEvolutionaryRelativesCheckBox.setEnabled(true);
        } else {
            allowEvolutionaryRelativesCheckBox.setEnabled(false);
            allowEvolutionaryRelativesCheckBox.setSelected(false);
        }
    }

    private void okButtonActionPerformed() {
        pressedOk = true;
        setVisible(false);
    }

    private void cancelButtonActionPerformed() {
        pressedOk = false;
        setVisible(false);
    }

	private void initComponents() {
		ResourceBundle bundle = ResourceBundle.getBundle("com.dabomstew.pkrandom.newgui.Bundle");
		mainPanel = new JPanel();
		JPanel panel1 = new JPanel();
		JPanel hSpacer1 = new JPanel(null);
		cancelButton = new JButton();
		okButton = new JButton();
		gen1CheckBox = new JCheckBox();
		gen2CheckBox = new JCheckBox();
		gen3CheckBox = new JCheckBox();
		gen4CheckBox = new JCheckBox();
		gen5CheckBox = new JCheckBox();
		gen6CheckBox = new JCheckBox();
		gen7CheckBox = new JCheckBox();
		xyWarningLabel = new JLabel();
		JPanel hSpacer2 = new JPanel(null);
		JPanel hSpacer3 = new JPanel(null);
		JPanel vSpacer1 = new JPanel(null);
		JLabel label1 = new JLabel();
		allowEvolutionaryRelativesCheckBox = new JCheckBox();

		//======== mainPanel ========
		{
			mainPanel.setLayout(new GridBagLayout());

			//======== panel1 ========
			{
				panel1.setLayout(new GridBagLayout());
				panel1.add(hSpacer1, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
					GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
					new Insets(0, 0, 0, 0), 0, 0));

				//---- cancelButton ----
				cancelButton.setText("Cancel");
				panel1.add(cancelButton, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
					GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
					new Insets(0, 0, 0, 0), 0, 0));

				//---- okButton ----
				okButton.setText("OK");
				panel1.add(okButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
					GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
					new Insets(0, 0, 0, 0), 0, 0));
			}
			mainPanel.add(panel1, new GridBagConstraints(3, 9, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gen1CheckBox ----
			gen1CheckBox.setText("Generation 1");
			mainPanel.add(gen1CheckBox, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gen2CheckBox ----
			gen2CheckBox.setText("Generation 2");
			mainPanel.add(gen2CheckBox, new GridBagConstraints(3, 2, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gen3CheckBox ----
			gen3CheckBox.setText("Generation 3");
			mainPanel.add(gen3CheckBox, new GridBagConstraints(3, 3, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gen4CheckBox ----
			gen4CheckBox.setText("Generation 4");
			mainPanel.add(gen4CheckBox, new GridBagConstraints(3, 4, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gen5CheckBox ----
			gen5CheckBox.setText("Generation 5");
			mainPanel.add(gen5CheckBox, new GridBagConstraints(3, 5, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gen6CheckBox ----
			gen6CheckBox.setText("Generation 6");
			mainPanel.add(gen6CheckBox, new GridBagConstraints(3, 6, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gen7CheckBox ----
			gen7CheckBox.setText("Generation 7");
			mainPanel.add(gen7CheckBox, new GridBagConstraints(3, 7, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- xyWarningLabel ----
			xyWarningLabel.setText(bundle.getString("GenerationLimitDialog.warningXYLabel.text"));
			mainPanel.add(xyWarningLabel, new GridBagConstraints(3, 11, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer2, new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer3, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(vSpacer1, new GridBagConstraints(3, 10, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- label1 ----
			label1.setText("Include Pokemon from:");
			mainPanel.add(label1, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- allowEvolutionaryRelativesCheckBox ----
			allowEvolutionaryRelativesCheckBox.setText("Allow Evolutionary Relatives");
			mainPanel.add(allowEvolutionaryRelativesCheckBox, new GridBagConstraints(3, 8, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));
		}
	}

	private JPanel mainPanel;
	private JButton cancelButton;
	private JButton okButton;
	private JCheckBox gen1CheckBox;
	private JCheckBox gen2CheckBox;
	private JCheckBox gen3CheckBox;
	private JCheckBox gen4CheckBox;
	private JCheckBox gen5CheckBox;
	private JCheckBox gen6CheckBox;
	private JCheckBox gen7CheckBox;
	private JLabel xyWarningLabel;
	private JCheckBox allowEvolutionaryRelativesCheckBox;
}
