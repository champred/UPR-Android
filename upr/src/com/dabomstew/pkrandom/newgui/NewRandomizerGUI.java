package com.dabomstew.pkrandom.newgui;

/*----------------------------------------------------------------------------*/
/*--  NewRandomizerGUI.java - the main GUI for the randomizer, containing   --*/
/*--                          the various options available and such        --*/
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

import com.dabomstew.pkrandom.*;
import com.dabomstew.pkrandom.cli.CliRandomizer;
import com.dabomstew.pkrandom.constants.GlobalConstants;
import com.dabomstew.pkrandom.exceptions.EncryptedROMException;
import com.dabomstew.pkrandom.exceptions.InvalidSupplementFilesException;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.pokemon.ExpCurve;
import com.dabomstew.pkrandom.pokemon.GenRestrictions;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NewRandomizerGUI {
	private JCheckBox mdLegacyCheckBox;

	private static JFrame frame;

    private static String launcherInput = "";
    public static boolean usedLauncher = false;

    private GenRestrictions currentRestrictions;
    private OperationDialog opDialog;

    private ResourceBundle bundle;
    protected RomHandler.Factory[] checkHandlers;
    private RomHandler romHandler;

    private boolean presetMode = false;
    private boolean initialPopup = true;
    private boolean showInvalidRomPopup = true;

    private List<JCheckBox> tweakCheckBoxes;
    private JPanel liveTweaksPanel = new JPanel();

    private JFileChooser romOpenChooser = new JFileChooser();
    private JFileChooser romSaveChooser = new JFileChooser();
    private JFileChooser qsOpenChooser = new JFileChooser();
    private JFileChooser qsSaveChooser = new JFileChooser();
    private JFileChooser qsUpdateChooser = new JFileChooser();
    private JFileChooser gameUpdateChooser = new JFileChooser();

    private JPopupMenu settingsMenu;
    private JMenuItem customNamesEditorMenuItem;
    private JMenuItem applyGameUpdateMenuItem;
    private JMenuItem removeGameUpdateMenuItem;
    private JMenuItem loadGetSettingsMenuItem;
    private JMenuItem keepOrUnloadGameAfterRandomizingMenuItem;

    private ImageIcon emptyIcon = new ImageIcon(getClass().getResource("/com/dabomstew/pkrandom/newgui/emptyIcon.png"));
    private boolean haveCheckedCustomNames, unloadGameOnSuccess;
    private Map<String, String> gameUpdates = new TreeMap<>();

    private List<String> trainerSettings = new ArrayList<>();
    private List<String> trainerSettingToolTips = new ArrayList<>();
    private final int TRAINER_UNCHANGED = 0, TRAINER_RANDOM = 1, TRAINER_RANDOM_EVEN = 2, TRAINER_RANDOM_EVEN_MAIN = 3,
                        TRAINER_TYPE_THEMED = 4, TRAINER_TYPE_THEMED_ELITE4_GYMS = 5;

    public NewRandomizerGUI() {
		initComponents();
		ToolTipManager.sharedInstance().setInitialDelay(400);
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
        bundle = ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle");
        testForRequiredConfigs();
        checkHandlers = new RomHandler.Factory[] { new Gen1RomHandler.Factory(), new Gen2RomHandler.Factory(),
                new Gen3RomHandler.Factory(), new Gen4RomHandler.Factory(), new Gen5RomHandler.Factory(),
                new Gen6RomHandler.Factory(), new Gen7RomHandler.Factory() };

        haveCheckedCustomNames = false;
        attemptReadConfig();
        initExplicit();
        initTweaksPanel();
        initFileChooserDirectories();

        boolean canWrite = attemptWriteConfig();
        if (!canWrite) {
            JOptionPane.showMessageDialog(null, bundle.getString("GUI.cantWriteConfigFile"));
        }

        if (!haveCheckedCustomNames) {
            checkCustomNames();
        }

        new Thread(() -> {
            String latestVersionString = "???";

            try {

                URL url = new URL(SysConstants.API_URL_ZX);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);

                BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

                String output;
                while ((output = br.readLine()) != null) {
                    String[] a = output.split("tag_name\":\"");
                    if (a.length > 1) {
                        latestVersionString = a[1].split("\",")[0];
                    }
                }

                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }

            // If the release version is newer than this version, bold it to make it more obvious.
            if (Version.isReleaseVersionNewer(latestVersionString)) {
                latestVersionString = String.format("<b>%s</b>", latestVersionString);
            }
            String finalLatestVersionString = latestVersionString;
            SwingUtilities.invokeLater(() -> {
                websiteLinkLabel.setText(String.format(bundle.getString("GUI.websiteLinkLabel.text"), finalLatestVersionString));
            });
        }).run();

        frame.setTitle(String.format(bundle.getString("GUI.windowTitle"),Version.VERSION_STRING));

        openROMButton.addActionListener(e -> loadROM());
        pbsUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pbsShuffleRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pbsRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pbsFollowMegaEvosCheckBox.addActionListener(e -> enableOrDisableSubControls());
        pbsFollowEvolutionsCheckBox.addActionListener(e -> enableOrDisableSubControls());
        pbsStandardizeEXPCurvesCheckBox.addActionListener(e -> enableOrDisableSubControls());
        paUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        paRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        peUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        peRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        peRandomEveryLevelRadioButton.addActionListener(e -> enableOrDisableSubControls());
        peAllowAltFormesCheckBox.addActionListener(e -> enableOrDisableSubControls());
        spUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        spCustomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        spRandomCompletelyRadioButton.addActionListener(e -> enableOrDisableSubControls());
        spRandomTwoEvosRadioButton.addActionListener(e -> enableOrDisableSubControls());
        stpUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        stpSwapLegendariesSwapStandardsRadioButton.addActionListener(e -> enableOrDisableSubControls());
        stpRandomCompletelyRadioButton.addActionListener(e -> enableOrDisableSubControls());
        stpRandomSimilarStrengthRadioButton.addActionListener(e -> enableOrDisableSubControls());
        stpPercentageLevelModifierCheckBox.addActionListener(e -> enableOrDisableSubControls());
        igtUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        igtRandomizeGivenPokemonOnlyRadioButton.addActionListener(e -> enableOrDisableSubControls());
        igtRandomizeBothRequestedGivenRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pmsUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pmsRandomPreferringSameTypeRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pmsRandomCompletelyRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pmsMetronomeOnlyModeRadioButton.addActionListener(e -> enableOrDisableSubControls());
        pmsGuaranteedLevel1MovesCheckBox.addActionListener(e -> enableOrDisableSubControls());
        pmsForceGoodDamagingCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpForceFullyEvolvedAtCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpPercentageLevelModifierCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpEliteFourUniquePokemonCheckBox.addActionListener(e -> enableOrDisableSubControls());
        wpUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        wpRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        wpArea1To1RadioButton.addActionListener(e -> enableOrDisableSubControls());
        wpGlobal1To1RadioButton.addActionListener(e -> enableOrDisableSubControls());
        wpSetMinimumCatchRateCheckBox.addActionListener(e -> enableOrDisableSubControls());
        wpRandomizeHeldItemsCheckBox.addActionListener(e -> enableOrDisableSubControls());
        wpPercentageLevelModifierCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tmUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        tmRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        tmForceGoodDamagingCheckBox.addActionListener(e -> enableOrDisableSubControls());
        thcUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        thcRandomPreferSameTypeRadioButton.addActionListener(e -> enableOrDisableSubControls());
        thcRandomCompletelyRadioButton.addActionListener(e -> enableOrDisableSubControls());
        thcFullCompatibilityRadioButton.addActionListener(e -> enableOrDisableSubControls());
        mtUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        mtRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        mtForceGoodDamagingCheckBox.addActionListener(e -> enableOrDisableSubControls());
        mtcUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        mtcRandomPreferSameTypeRadioButton.addActionListener(e -> enableOrDisableSubControls());
        mtcRandomCompletelyRadioButton.addActionListener(e -> enableOrDisableSubControls());
        mtcFullCompatibilityRadioButton.addActionListener(e -> enableOrDisableSubControls());
        fiUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        fiShuffleRadioButton.addActionListener(e -> enableOrDisableSubControls());
        fiRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        fiRandomEvenDistributionRadioButton.addActionListener(e -> enableOrDisableSubControls());
        shUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        shShuffleRadioButton.addActionListener(e -> enableOrDisableSubControls());
        shRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        puUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        puRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        websiteLinkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Desktop desktop = java.awt.Desktop.getDesktop();
                try {
                    desktop.browse(new URI(SysConstants.WEBSITE_URL_ZX));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        wikiLinkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Desktop desktop = java.awt.Desktop.getDesktop();
                try {
                    desktop.browse(new URI(SysConstants.WIKI_URL_ZX));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        randomizeSaveButton.addActionListener(e -> saveROM());
        premadeSeedButton.addActionListener(e -> presetLoader());
        loadSettingsButton.addActionListener(e -> loadQS());
        saveSettingsButton.addActionListener(e -> saveQS());
        settingsButton.addActionListener(e -> settingsMenu.show(settingsButton,0,settingsButton.getHeight()));
        customNamesEditorMenuItem.addActionListener(e -> new CustomNamesEditorDialog(frame));
        applyGameUpdateMenuItem.addActionListener(e -> applyGameUpdateMenuItemActionPerformed());
        removeGameUpdateMenuItem.addActionListener(e -> removeGameUpdateMenuItemActionPerformed());
        loadGetSettingsMenuItem.addActionListener(e -> loadGetSettingsMenuItemActionPerformed());
        keepOrUnloadGameAfterRandomizingMenuItem.addActionListener(e -> keepOrUnloadGameAfterRandomizingMenuItemActionPerformed());
        limitPokemonButton.addActionListener(e -> {
            NewGenerationLimitDialog gld = new NewGenerationLimitDialog(frame, currentRestrictions,
                    romHandler.generationOfPokemon(), romHandler.forceSwapStaticMegaEvos());
            if (gld.pressedOK()) {
                currentRestrictions = gld.getChoice();
                if (currentRestrictions != null && !currentRestrictions.allowTrainerSwapMegaEvolvables(
                        romHandler.forceSwapStaticMegaEvos(), isTrainerSetting(TRAINER_TYPE_THEMED))) {
                    tpSwapMegaEvosCheckBox.setEnabled(false);
                    tpSwapMegaEvosCheckBox.setSelected(false);
                }
            }
        });
        limitPokemonCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpAllowAlternateFormesCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpBossTrainersCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpImportantTrainersCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpRegularTrainersCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpBossTrainersItemsCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpImportantTrainersItemsCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tpRegularTrainersItemsCheckBox.addActionListener(e -> enableOrDisableSubControls());
        totpUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        totpRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        totpRandomSimilarStrengthRadioButton.addActionListener(e -> enableOrDisableSubControls());
        totpAllyUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        totpAllyRandomRadioButton.addActionListener(e -> enableOrDisableSubControls());
        totpAllyRandomSimilarStrengthRadioButton.addActionListener(e -> enableOrDisableSubControls());
        totpPercentageLevelModifierCheckBox.addActionListener(e -> enableOrDisableSubControls());
        pbsUpdateBaseStatsCheckBox.addActionListener(e -> enableOrDisableSubControls());
        mdUpdateMovesCheckBox.addActionListener(e -> enableOrDisableSubControls());
        frame.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {

            }

            @Override
            public void componentMoved(ComponentEvent e) {

            }

            @Override
            public void componentShown(ComponentEvent e) {
                showInitialPopup();
            }

            @Override
            public void componentHidden(ComponentEvent e) {

            }
        });
        ptUnchangedRadioButton.addActionListener(e -> enableOrDisableSubControls());
        ptRandomFollowEvolutionsRadioButton.addActionListener(e -> enableOrDisableSubControls());
        ptRandomCompletelyRadioButton.addActionListener(e -> enableOrDisableSubControls());
        spRandomizeStarterHeldItemsCheckBox.addActionListener(e -> enableOrDisableSubControls());
        tmLevelupMoveSanityCheckBox.addActionListener(e -> enableOrDisableSubControls());
        mtLevelupMoveSanityCheckBox.addActionListener(e -> enableOrDisableSubControls());
        noIrregularAltFormesCheckBox.addActionListener(e -> enableOrDisableSubControls());
        ptIsDualTypeCheckBox.addActionListener(e->enableOrDisableSubControls());
        tpComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                enableOrDisableSubControls();
            }
        });
    }

    private void showInitialPopup() {
        if (!usedLauncher) {
            String message = bundle.getString("GUI.pleaseUseTheLauncher");
            Object[] messages = {message};
            JOptionPane.showMessageDialog(frame, messages);
        }
        if (initialPopup) {
            String message = String.format(bundle.getString("GUI.firstStart"),Version.VERSION_STRING);
            JLabel label = new JLabel("<html><a href=\"https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Important-Information\">Checking out the \"Important Information\" page on the Wiki is highly recommended.</a>");
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Desktop desktop = java.awt.Desktop.getDesktop();
                    try {
                        desktop.browse(new URI("https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Important-Information"));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            label.setCursor(new java.awt.Cursor(Cursor.HAND_CURSOR));
            Object[] messages = {message,label};
            JOptionPane.showMessageDialog(frame, messages);
            initialPopup = false;
            attemptWriteConfig();
        }
    }

    private void showInvalidRomPopup() {
        if (showInvalidRomPopup) {
            String message = String.format(bundle.getString("GUI.invalidRomMessage"));
            JLabel label = new JLabel("<html><b>Randomizing ROM hacks or bad ROM dumps is not supported and may cause issues.</b>");
            JCheckBox checkbox = new JCheckBox("Don't show this again");
            Object[] messages = {message, label, checkbox};
            Object[] options = {"OK"};
            JOptionPane.showOptionDialog(frame,
                    messages,
                    "Invalid ROM detected",
                    JOptionPane.OK_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    null);
            showInvalidRomPopup = !checkbox.isSelected();
            attemptWriteConfig();
        }
    }

    private void initFileChooserDirectories() {
        romOpenChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH));
        romSaveChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH));
        if (new File(SysConstants.ROOT_PATH + "settings/").exists()) {
            qsOpenChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH + "settings/"));
            qsSaveChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH + "settings/"));
            qsUpdateChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH + "settings/"));
        } else {
            qsOpenChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH));
            qsSaveChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH));
            qsUpdateChooser.setCurrentDirectory(new File(SysConstants.ROOT_PATH));
        }
    }

    private void initExplicit() {

        versionLabel.setText(String.format(bundle.getString("GUI.versionLabel.text"), Version.VERSION_STRING));
        mtNoExistLabel.setVisible(false);
        mtNoneAvailableLabel.setVisible(false);
        baseTweaksPanel.add(liveTweaksPanel);
        liveTweaksPanel.setVisible(false);
        websiteLinkLabel.setCursor(new java.awt.Cursor(Cursor.HAND_CURSOR));
        wikiLinkLabel.setCursor(new java.awt.Cursor(Cursor.HAND_CURSOR));

        romOpenChooser.setFileFilter(new ROMFilter());

        romSaveChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        romSaveChooser.setFileFilter(new ROMFilter());

        qsOpenChooser.setFileFilter(new QSFileFilter());

        qsSaveChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        qsSaveChooser.setFileFilter(new QSFileFilter());

        qsUpdateChooser.setFileFilter(new QSFileFilter());

        settingsMenu = new JPopupMenu();

        SpinnerModel bossTrainerModel = new SpinnerNumberModel(
                1,
                1,
                5,
                1
        );
        SpinnerModel importantTrainerModel = new SpinnerNumberModel(
                1,
                1,
                5,
                1
        );
        SpinnerModel regularTrainerModel = new SpinnerNumberModel(
                1,
                1,
                5,
                1
        );

        SpinnerModel eliteFourUniquePokemonModel = new SpinnerNumberModel(
                1,
                1,
                2,
                1
        );

        List<String> keys = new ArrayList<>(bundle.keySet());
        Collections.sort(keys);
        for (String k: keys) {
            if (k.matches("^GUI\\.tpMain.*\\.text$")) {
                trainerSettings.add(bundle.getString(k));
                trainerSettingToolTips.add(k.replace("text","toolTipText"));
            }
        }

        tpBossTrainersSpinner.setModel(bossTrainerModel);
        tpImportantTrainersSpinner.setModel(importantTrainerModel);
        tpRegularTrainersSpinner.setModel(regularTrainerModel);
        tpEliteFourUniquePokemonSpinner.setModel(eliteFourUniquePokemonModel);

        customNamesEditorMenuItem = new JMenuItem();
        customNamesEditorMenuItem.setText(bundle.getString("GUI.customNamesEditorMenuItem.text"));
        settingsMenu.add(customNamesEditorMenuItem);

        loadGetSettingsMenuItem = new JMenuItem();
        loadGetSettingsMenuItem.setText(bundle.getString("GUI.loadGetSettingsMenuItem.text"));
        settingsMenu.add(loadGetSettingsMenuItem);

        applyGameUpdateMenuItem = new JMenuItem();
        applyGameUpdateMenuItem.setText(bundle.getString("GUI.applyGameUpdateMenuItem.text"));
        settingsMenu.add(applyGameUpdateMenuItem);

        removeGameUpdateMenuItem = new JMenuItem();
        removeGameUpdateMenuItem.setText(bundle.getString("GUI.removeGameUpdateMenuItem.text"));
        settingsMenu.add(removeGameUpdateMenuItem);

        keepOrUnloadGameAfterRandomizingMenuItem = new JMenuItem();
        if (this.unloadGameOnSuccess) {
            keepOrUnloadGameAfterRandomizingMenuItem.setText(bundle.getString("GUI.keepGameLoadedAfterRandomizingMenuItem.text"));
        } else {
            keepOrUnloadGameAfterRandomizingMenuItem.setText(bundle.getString("GUI.unloadGameAfterRandomizingMenuItem.text"));
        }
        settingsMenu.add(keepOrUnloadGameAfterRandomizingMenuItem);
    }

    private void loadROM() {
        romOpenChooser.setSelectedFile(null);
        int returnVal = romOpenChooser.showOpenDialog(mainPanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            final File fh = romOpenChooser.getSelectedFile();
            try {
                Utils.validateRomFile(fh);
            } catch (Utils.InvalidROMException e) {
                switch (e.getType()) {
                    case LENGTH:
                        JOptionPane.showMessageDialog(mainPanel,
                                String.format(bundle.getString("GUI.tooShortToBeARom"), fh.getName()));
                        return;
                    case ZIP_FILE:
                        JOptionPane.showMessageDialog(mainPanel,
                                String.format(bundle.getString("GUI.openedZIPfile"), fh.getName()));
                        return;
                    case RAR_FILE:
                        JOptionPane.showMessageDialog(mainPanel,
                                String.format(bundle.getString("GUI.openedRARfile"), fh.getName()));
                        return;
                    case IPS_FILE:
                        JOptionPane.showMessageDialog(mainPanel,
                                String.format(bundle.getString("GUI.openedIPSfile"), fh.getName()));
                        return;
                    case UNREADABLE:
                        JOptionPane.showMessageDialog(mainPanel,
                                String.format(bundle.getString("GUI.unreadableRom"), fh.getName()));
                        return;
                }
            }

            for (RomHandler.Factory rhf : checkHandlers) {
                if (rhf.isLoadable(fh.getAbsolutePath())) {
                    this.romHandler = rhf.create(RandomSource.instance());
                    if (!usedLauncher && this.romHandler instanceof Abstract3DSRomHandler) {
                        String message = bundle.getString("GUI.pleaseUseTheLauncher");
                        Object[] messages = {message};
                        JOptionPane.showMessageDialog(frame, messages);
                        this.romHandler = null;
                        return;
                    }
                    opDialog = new OperationDialog(bundle.getString("GUI.loadingText"), frame, true);
                    Thread t = new Thread(() -> {
                        boolean romLoaded = false;
                        SwingUtilities.invokeLater(() -> opDialog.setVisible(true));
                        try {
                            this.romHandler.loadRom(fh.getAbsolutePath());
                            if (gameUpdates.containsKey(this.romHandler.getROMCode())) {
                                this.romHandler.loadGameUpdate(gameUpdates.get(this.romHandler.getROMCode()));
                            }
                            romLoaded = true;
                        } catch (EncryptedROMException ex) {
                            JOptionPane.showMessageDialog(mainPanel,
                                    String.format(bundle.getString("GUI.encryptedRom"), fh.getAbsolutePath()));
                        } catch (Exception ex) {
                            attemptToLogException(ex, "GUI.loadFailed", "GUI.loadFailedNoLog", null, null);
                        }
                        final boolean loadSuccess = romLoaded;
                        SwingUtilities.invokeLater(() -> {
                            this.opDialog.setVisible(false);
                            this.initialState();
                            if (loadSuccess) {
                                this.romLoaded();
                            }
                        });
                    });
                    t.start();

                    return;
                }
            }
            JOptionPane.showMessageDialog(mainPanel,
                    String.format(bundle.getString("GUI.unsupportedRom"), fh.getName()));
        }
    }

    private void saveROM() {
        if (romHandler == null) {
            return; // none loaded
        }
        if (raceModeCheckBox.isSelected() && isTrainerSetting(TRAINER_UNCHANGED) && wpUnchangedRadioButton.isSelected()) {
            JOptionPane.showMessageDialog(frame, bundle.getString("GUI.raceModeRequirements"));
            return;
        }
        if (limitPokemonCheckBox.isSelected()
                && (this.currentRestrictions == null || this.currentRestrictions.nothingSelected())) {
            JOptionPane.showMessageDialog(frame, bundle.getString("GUI.pokeLimitNotChosen"));
            return;
        }
        SaveType outputType = askForSaveType();
        romSaveChooser.setSelectedFile(null);
        boolean allowed = false;
        File fh = null;
        if (outputType == SaveType.FILE) {
            romSaveChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            int returnVal = romSaveChooser.showSaveDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                fh = romSaveChooser.getSelectedFile();
                // Fix or add extension
                List<String> extensions = new ArrayList<>(Arrays.asList("sgb", "gbc", "gba", "nds", "cxi"));
                extensions.remove(this.romHandler.getDefaultExtension());
                fh = FileFunctions.fixFilename(fh, this.romHandler.getDefaultExtension(), extensions);
                allowed = true;
                if (this.romHandler instanceof AbstractDSRomHandler || this.romHandler instanceof Abstract3DSRomHandler) {
                    String currentFN = this.romHandler.loadedFilename();
                    if (currentFN.equals(fh.getAbsolutePath())) {
                        JOptionPane.showMessageDialog(frame, bundle.getString("GUI.cantOverwriteDS"));
                        allowed = false;
                    }
                }
            }
        } else if (outputType == SaveType.DIRECTORY) {
            romSaveChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnVal = romSaveChooser.showSaveDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                fh = romSaveChooser.getSelectedFile();
                allowed = true;
            }
        }

        if (allowed && fh != null) {
            // Get a seed
            long seed = RandomSource.pickSeed();
            // Apply it
            RandomSource.seed(seed);
            presetMode = false;

            try {
                CustomNamesSet cns = FileFunctions.getCustomNames();
                performRandomization(fh.getAbsolutePath(), seed, cns, outputType == SaveType.DIRECTORY);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, bundle.getString("GUI.cantLoadCustomNames"));
            }

        }
    }

    private void loadQS() {
        if (this.romHandler == null) {
            return;
        }
        qsOpenChooser.setSelectedFile(null);
        int returnVal = qsOpenChooser.showOpenDialog(frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File fh = qsOpenChooser.getSelectedFile();
            try {
                FileInputStream fis = new FileInputStream(fh);
                Settings settings = Settings.read(fis);
                fis.close();

                SwingUtilities.invokeLater(() -> {
                    // load settings
                    initialState();
                    romLoaded();
                    Settings.TweakForROMFeedback feedback = settings.tweakForRom(this.romHandler);
                    if (feedback.isChangedStarter() && settings.getStartersMod() == Settings.StartersMod.CUSTOM) {
                        JOptionPane.showMessageDialog(frame, bundle.getString("GUI.starterUnavailable"));
                    }
                    this.restoreStateFromSettings(settings);

                    if (settings.isUpdatedFromOldVersion()) {
                        // show a warning dialog, but load it
                        JOptionPane.showMessageDialog(frame, bundle.getString("GUI.settingsFileOlder"));
                    }

                    JOptionPane.showMessageDialog(frame,
                            String.format(bundle.getString("GUI.settingsLoaded"), fh.getName()));
                });
            } catch (UnsupportedOperationException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, ex.getMessage());
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, bundle.getString("GUI.invalidSettingsFile"));
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, bundle.getString("GUI.settingsLoadFailed"));
            }
        }
    }

    private void saveQS() {
        if (this.romHandler == null) {
            return;
        }
        qsSaveChooser.setSelectedFile(null);
        int returnVal = qsSaveChooser.showSaveDialog(frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File fh = qsSaveChooser.getSelectedFile();
            // Fix or add extension
            fh = FileFunctions.fixFilename(fh, "rnqs");
            // Save now?
            try {
                FileOutputStream fos = new FileOutputStream(fh);
                getCurrentSettings().write(fos);
                fos.close();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, bundle.getString("GUI.settingsSaveFailed"));
            }
        }
    }

    private void performRandomization(final String filename, final long seed, CustomNamesSet customNames, boolean saveAsDirectory) {
        final Settings settings = createSettingsFromState(customNames);
        final boolean raceMode = settings.isRaceMode();
        // Setup verbose log
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream log;
        try {
            log = new PrintStream(baos, false, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log = new PrintStream(baos);
        }

        final PrintStream verboseLog = log;

        try {
            final AtomicInteger finishedCV = new AtomicInteger(0);
            opDialog = new OperationDialog(bundle.getString("GUI.savingText"), frame, true);
            Thread t = new Thread(() -> {
                SwingUtilities.invokeLater(() -> opDialog.setVisible(true));
                boolean succeededSave = false;
                try {
                    romHandler.setLog(verboseLog);
                    finishedCV.set(new Randomizer(settings, romHandler, bundle, saveAsDirectory).randomize(filename,
                            verboseLog, seed));
                    succeededSave = true;
                } catch (RandomizationException ex) {
                    attemptToLogException(ex, "GUI.saveFailedMessage",
                            "GUI.saveFailedMessageNoLog", true, settings.toString(), Long.toString(seed));
                    if (verboseLog != null) {
                        verboseLog.close();
                    }
                } catch (Exception ex) {
                    attemptToLogException(ex, "GUI.saveFailedIO", "GUI.saveFailedIONoLog", settings.toString(), Long.toString(seed));
                    if (verboseLog != null) {
                        verboseLog.close();
                    }
                }
                if (succeededSave) {
                    SwingUtilities.invokeLater(() -> {
                        opDialog.setVisible(false);
                        // Log?
                        verboseLog.close();
                        byte[] out = baos.toByteArray();

                        if (raceMode) {
                            JOptionPane.showMessageDialog(frame,
                                    String.format(bundle.getString("GUI.raceModeCheckValuePopup"),
                                            finishedCV.get()));
                        } else {
                            int response = JOptionPane.showConfirmDialog(frame,
                                    bundle.getString("GUI.saveLogDialog.text"),
                                    bundle.getString("GUI.saveLogDialog.title"),
                                    JOptionPane.YES_NO_OPTION);
                            if (response == JOptionPane.YES_OPTION) {
                                try {
                                    FileOutputStream fos = new FileOutputStream(filename + ".log");
                                    fos.write(0xEF);
                                    fos.write(0xBB);
                                    fos.write(0xBF);
                                    fos.write(out);
                                    fos.close();
                                } catch (IOException e) {
                                    JOptionPane.showMessageDialog(frame,
                                            bundle.getString("GUI.logSaveFailed"));
                                    return;
                                }
                                JOptionPane.showMessageDialog(frame,
                                        String.format(bundle.getString("GUI.logSaved"), filename));
                            }
                        }
                        if (presetMode) {
                            JOptionPane.showMessageDialog(frame,
                                    bundle.getString("GUI.randomizationDone"));
                            // Done
                            if (this.unloadGameOnSuccess) {
                                romHandler = null;
                                initialState();
                            } else {
                                reinitializeRomHandler();
                            }
                        } else {
                            // Compile a config string
                            try {
                                String configString = getCurrentSettings().toString();
                                // Show the preset maker
                                new PresetMakeDialog(frame, seed, configString);
                            } catch (IOException ex) {
                                JOptionPane.showMessageDialog(frame,
                                        bundle.getString("GUI.cantLoadCustomNames"));
                            }

                            // Done
                            if (this.unloadGameOnSuccess) {
                                romHandler = null;
                                initialState();
                            } else {
                                reinitializeRomHandler();
                            }
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        opDialog.setVisible(false);
                        romHandler = null;
                        initialState();
                    });
                }
            });
            t.start();
        } catch (Exception ex) {
            attemptToLogException(ex, "GUI.saveFailed", "GUI.saveFailedNoLog", settings.toString(), Long.toString(seed));
            if (verboseLog != null) {
                verboseLog.close();
            }
        }
    }

    private void presetLoader() {
        PresetLoadDialog pld = new PresetLoadDialog(this,frame);
        if (pld.isCompleted()) {
            // Apply it
            long seed = pld.getSeed();
            String config = pld.getConfigString();
            this.romHandler = pld.getROM();
            if (gameUpdates.containsKey(this.romHandler.getROMCode())) {
                this.romHandler.loadGameUpdate(gameUpdates.get(this.romHandler.getROMCode()));
            }
            this.romLoaded();
            Settings settings;
            try {
                settings = Settings.fromString(config);
                settings.tweakForRom(this.romHandler);
                this.restoreStateFromSettings(settings);
            } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                // settings load failed
                e.printStackTrace();
                this.romHandler = null;
                initialState();
            }
            SaveType outputType = askForSaveType();
            romSaveChooser.setSelectedFile(null);
            boolean allowed = false;
            File fh = null;
            if (outputType == SaveType.FILE) {
                romSaveChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                int returnVal = romSaveChooser.showSaveDialog(frame);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    fh = romSaveChooser.getSelectedFile();
                    // Fix or add extension
                    List<String> extensions = new ArrayList<>(Arrays.asList("sgb", "gbc", "gba", "nds", "cxi"));
                    extensions.remove(this.romHandler.getDefaultExtension());
                    fh = FileFunctions.fixFilename(fh, this.romHandler.getDefaultExtension(), extensions);
                    allowed = true;
                    if (this.romHandler instanceof AbstractDSRomHandler || this.romHandler instanceof Abstract3DSRomHandler) {
                        String currentFN = this.romHandler.loadedFilename();
                        if (currentFN.equals(fh.getAbsolutePath())) {
                            JOptionPane.showMessageDialog(frame, bundle.getString("GUI.cantOverwriteDS"));
                            allowed = false;
                        }
                    }
                } else {
                    this.romHandler = null;
                    initialState();
                }
            } else if (outputType == SaveType.DIRECTORY) {
                romSaveChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnVal = romSaveChooser.showSaveDialog(frame);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    fh = romSaveChooser.getSelectedFile();
                    allowed = true;
                } else {
                    this.romHandler = null;
                    initialState();
                }
            }

            if (allowed && fh != null) {
                // Apply the seed we were given
                RandomSource.seed(seed);
                presetMode = true;
                performRandomization(fh.getAbsolutePath(), seed, pld.getCustomNames(), outputType == SaveType.DIRECTORY);
            }
        }

    }


    private enum SaveType {
        FILE, DIRECTORY, INVALID
    }

    private SaveType askForSaveType() {
        SaveType saveType = SaveType.FILE;
        if (romHandler.hasGameUpdateLoaded()) {
            String text = bundle.getString("GUI.savingWithGameUpdate");
            String url = "https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Randomizing-the-3DS-games#managing-game-updates";
            showMessageDialogWithLink(text, url);
            saveType = SaveType.DIRECTORY;
        } else if (romHandler.generationOfPokemon() == 6 || romHandler.generationOfPokemon() == 7) {
            Object[] options3DS = {"CXI", "LayeredFS"};
            String question = "Would you like to output your 3DS game as a CXI file or as a LayeredFS directory?";
            JLabel label = new JLabel("<html><a href=\"https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Randomizing-the-3DS-games#changes-to-saving-a-rom-when-working-with-3ds-games\">For more information, click here.</a>");
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Desktop desktop = java.awt.Desktop.getDesktop();
                    try {
                        desktop.browse(new URI("https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Randomizing-the-3DS-games#changes-to-saving-a-rom-when-working-with-3ds-games"));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            label.setCursor(new java.awt.Cursor(Cursor.HAND_CURSOR));
            Object[] messages = {question,label};
            int returnVal3DS = JOptionPane.showOptionDialog(frame,
                    messages,
                    "3DS Output Choice",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options3DS,
                    null);
            if (returnVal3DS < 0) {
                saveType = SaveType.INVALID;
            } else {
                saveType = SaveType.values()[returnVal3DS];
            }
        }
        return saveType;
    }

    private void applyGameUpdateMenuItemActionPerformed() {

        if (romHandler == null) return;

        gameUpdateChooser.setSelectedFile(null);
        gameUpdateChooser.setFileFilter(new GameUpdateFilter());
        int returnVal = gameUpdateChooser.showOpenDialog(frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File fh = gameUpdateChooser.getSelectedFile();

            // On the 3DS, the update has the same title ID as the base game, save for the 8th character,
            // which is 'E' instead of '0'. We can use this to detect if the update matches the game.
            String actualUpdateTitleId = Abstract3DSRomHandler.getTitleIdFromFile(fh.getAbsolutePath());
            if (actualUpdateTitleId == null) {
                // Error: couldn't find a title ID in the update
                JOptionPane.showMessageDialog(frame, String.format(bundle.getString("GUI.invalidGameUpdate"), fh.getName()));
                return;
            }
            Abstract3DSRomHandler ctrRomHandler = (Abstract3DSRomHandler) romHandler;
            String baseGameTitleId = ctrRomHandler.getTitleIdFromLoadedROM();
            char[] baseGameTitleIdChars = baseGameTitleId.toCharArray();
            baseGameTitleIdChars[7] = 'E';
            String expectedUpdateTitleId = String.valueOf(baseGameTitleIdChars);
            if (actualUpdateTitleId.equals(expectedUpdateTitleId)) {
                try {
                    romHandler.loadGameUpdate(fh.getAbsolutePath());
                } catch (EncryptedROMException ex) {
                    JOptionPane.showMessageDialog(mainPanel,
                            String.format(bundle.getString("GUI.encryptedRom"), fh.getAbsolutePath()));
                    return;
                }
                gameUpdates.put(romHandler.getROMCode(), fh.getAbsolutePath());
                attemptWriteConfig();
                removeGameUpdateMenuItem.setVisible(true);
                setRomNameLabel();
                String text = String.format(bundle.getString("GUI.gameUpdateApplied"), romHandler.getROMName());
                String url = "https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Randomizing-the-3DS-games#3ds-game-updates";
                showMessageDialogWithLink(text, url);
            } else {
                // Error: update is not for the correct game
                JOptionPane.showMessageDialog(frame, String.format(bundle.getString("GUI.nonMatchingGameUpdate"), fh.getName(), romHandler.getROMName()));
            }
        }
    }

    private void removeGameUpdateMenuItemActionPerformed() {

        if (romHandler == null) return;

        gameUpdates.remove(romHandler.getROMCode());
        attemptWriteConfig();
        romHandler.removeGameUpdate();
        removeGameUpdateMenuItem.setVisible(false);
        setRomNameLabel();
    }

    private void loadGetSettingsMenuItemActionPerformed() {

        if (romHandler == null) return;

        String currentSettingsString = "Current Settings String:";
        JTextField currentSettingsStringField = new JTextField();
        currentSettingsStringField.setEditable(false);
        try {
            String theSettingsString = Version.VERSION + getCurrentSettings().toString();
            currentSettingsStringField.setColumns(Settings.LENGTH_OF_SETTINGS_DATA * 2);
            currentSettingsStringField.setText(theSettingsString);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String loadSettingsString = "Load Settings String:";
        JTextField loadSettingsStringField = new JTextField();
        Object[] messages = {currentSettingsString,currentSettingsStringField,loadSettingsString,loadSettingsStringField};
        Object[] options = {"Load","Cancel"};
        int choice = JOptionPane.showOptionDialog(
                frame,
                messages,
                "Get/Load Settings String",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                null
        );
        if (choice == 0) {
            String configString = loadSettingsStringField.getText().trim();
            if (configString.length() > 0) {
                if (configString.length() < 3) {
                    JOptionPane.showMessageDialog(frame,bundle.getString("GUI.invalidSettingsString"));
                } else {
                    try {
                        int settingsStringVersionNumber = Integer.parseInt(configString.substring(0, 3));
                        if (settingsStringVersionNumber < Version.VERSION) {
                            JOptionPane.showMessageDialog(frame,bundle.getString("GUI.settingsStringOlder"));
                            String updatedSettingsString = new SettingsUpdater().update(settingsStringVersionNumber, configString.substring(3));
                            Settings settings = Settings.fromString(updatedSettingsString);
                            settings.tweakForRom(this.romHandler);
                            restoreStateFromSettings(settings);
                            JOptionPane.showMessageDialog(frame,bundle.getString("GUI.settingsStringLoaded"));
                        } else if (settingsStringVersionNumber > Version.VERSION) {
                            JOptionPane.showMessageDialog(frame,bundle.getString("GUI.settingsStringTooNew"));
                        } else {
                            Settings settings = Settings.fromString(configString.substring(3));
                            settings.tweakForRom(this.romHandler);
                            restoreStateFromSettings(settings);
                            JOptionPane.showMessageDialog(frame,bundle.getString("GUI.settingsStringLoaded"));
                        }
                    } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
                        JOptionPane.showMessageDialog(frame,bundle.getString("GUI.invalidSettingsString"));
                    }
                }

            }
        }
    }

    private void keepOrUnloadGameAfterRandomizingMenuItemActionPerformed() {
        this.unloadGameOnSuccess = !this.unloadGameOnSuccess;
        if (this.unloadGameOnSuccess) {
            JOptionPane.showMessageDialog(frame, bundle.getString("GUI.unloadGameAfterRandomizing"));
            keepOrUnloadGameAfterRandomizingMenuItem.setText(bundle.getString("GUI.keepGameLoadedAfterRandomizingMenuItem.text"));
        } else {
            JOptionPane.showMessageDialog(frame, bundle.getString("GUI.keepGameLoadedAfterRandomizing"));
            keepOrUnloadGameAfterRandomizingMenuItem.setText(bundle.getString("GUI.unloadGameAfterRandomizingMenuItem.text"));
        }
        attemptWriteConfig();
    }

    private void showMessageDialogWithLink(String text, String url) {
        JLabel label = new JLabel("<html><a href=\"" + url + "\">For more information, click here.</a>");
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Desktop desktop = java.awt.Desktop.getDesktop();
                try {
                    desktop.browse(new URI(url));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        label.setCursor(new java.awt.Cursor(Cursor.HAND_CURSOR));
        Object[] messages = {text,label};
        JOptionPane.showMessageDialog(frame, messages);
    }

    // This is only intended to be used with the "Keep Game Loaded After Randomizing" setting; it assumes that
    // the game has already been loaded once, and we just need to reload the same game to reinitialize the
    // RomHandler. Don't use this for other purposes unless you know what you're doing.
    private void reinitializeRomHandler() {
        String currentFN = this.romHandler.loadedFilename();
        for (RomHandler.Factory rhf : checkHandlers) {
            if (rhf.isLoadable(currentFN)) {
                this.romHandler = rhf.create(RandomSource.instance());
                opDialog = new OperationDialog(bundle.getString("GUI.loadingText"), frame, true);
                Thread t = new Thread(() -> {
                    SwingUtilities.invokeLater(() -> opDialog.setVisible(true));
                    try {
                        this.romHandler.loadRom(currentFN);
                        if (gameUpdates.containsKey(this.romHandler.getROMCode())) {
                            this.romHandler.loadGameUpdate(gameUpdates.get(this.romHandler.getROMCode()));
                        }
                    } catch (Exception ex) {
                        attemptToLogException(ex, "GUI.loadFailed", "GUI.loadFailedNoLog", null, null);
                    }
                    SwingUtilities.invokeLater(() -> {
                        this.opDialog.setVisible(false);
                    });
                });
                t.start();

                return;
            }
        }
    }

    private void restoreStateFromSettings(Settings settings) {

        limitPokemonCheckBox.setSelected(settings.isLimitPokemon());
        currentRestrictions = settings.getCurrentRestrictions();
        if (currentRestrictions != null) {
            currentRestrictions.limitToGen(romHandler.generationOfPokemon());
        }
        noIrregularAltFormesCheckBox.setSelected(settings.isBanIrregularAltFormes());
        raceModeCheckBox.setSelected(settings.isRaceMode());

        peChangeImpossibleEvosCheckBox.setSelected(settings.isChangeImpossibleEvolutions());
        mdUpdateMovesCheckBox.setSelected(settings.isUpdateMoves());
        mdUpdateComboBox.setSelectedIndex(Math.max(0,settings.getUpdateMovesToGeneration() - (romHandler.generationOfPokemon()+1)));
        tpRandomizeTrainerNamesCheckBox.setSelected(settings.isRandomizeTrainerNames());
        tpRandomizeTrainerClassNamesCheckBox.setSelected(settings.isRandomizeTrainerClassNames());
        ptIsDualTypeCheckBox.setSelected(settings.isDualTypeOnly());

        pbsRandomRadioButton.setSelected(settings.getBaseStatisticsMod() == Settings.BaseStatisticsMod.RANDOM);
        pbsShuffleRadioButton.setSelected(settings.getBaseStatisticsMod() == Settings.BaseStatisticsMod.SHUFFLE);
        pbsUnchangedRadioButton.setSelected(settings.getBaseStatisticsMod() == Settings.BaseStatisticsMod.UNCHANGED);
        pbsFollowEvolutionsCheckBox.setSelected(settings.isBaseStatsFollowEvolutions());
        pbsUpdateBaseStatsCheckBox.setSelected(settings.isUpdateBaseStats());
        pbsUpdateComboBox.setSelectedIndex(Math.max(0,settings.getUpdateBaseStatsToGeneration() - (Math.max(6,romHandler.generationOfPokemon()+1))));
        pbsStandardizeEXPCurvesCheckBox.setSelected(settings.isStandardizeEXPCurves());
        pbsLegendariesSlowRadioButton.setSelected(settings.getExpCurveMod() == Settings.ExpCurveMod.LEGENDARIES);
        pbsStrongLegendariesSlowRadioButton.setSelected(settings.getExpCurveMod() == Settings.ExpCurveMod.STRONG_LEGENDARIES);
        pbsAllMediumFastRadioButton.setSelected(settings.getExpCurveMod() == Settings.ExpCurveMod.ALL);
        ExpCurve[] expCurves = getEXPCurvesForGeneration(romHandler.generationOfPokemon());
        int index = 0;
        for (int i = 0; i < expCurves.length; i++) {
            if (expCurves[i] == settings.getSelectedEXPCurve()) {
                index = i;
            }
        }
        pbsEXPCurveComboBox.setSelectedIndex(index);
        pbsFollowMegaEvosCheckBox.setSelected(settings.isBaseStatsFollowMegaEvolutions());
        pbsAssignEvoStatsRandomlyCheckBox.setSelected(settings.isAssignEvoStatsRandomly());

        paUnchangedRadioButton.setSelected(settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED);
        paRandomRadioButton.setSelected(settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE);
        paAllowWonderGuardCheckBox.setSelected(settings.isAllowWonderGuard());
        paFollowEvolutionsCheckBox.setSelected(settings.isAbilitiesFollowEvolutions());
        paTrappingAbilitiesCheckBox.setSelected(settings.isBanTrappingAbilities());
        paNegativeAbilitiesCheckBox.setSelected(settings.isBanNegativeAbilities());
        paBadAbilitiesCheckBox.setSelected(settings.isBanBadAbilities());
        paFollowMegaEvosCheckBox.setSelected(settings.isAbilitiesFollowMegaEvolutions());
        paWeighDuplicatesTogetherCheckBox.setSelected(settings.isWeighDuplicateAbilitiesTogether());
        paEnsureTwoAbilitiesCheckbox.setSelected(settings.isEnsureTwoAbilities());

        ptRandomFollowEvolutionsRadioButton.setSelected(settings.getTypesMod() == Settings.TypesMod.RANDOM_FOLLOW_EVOLUTIONS);
        ptRandomCompletelyRadioButton.setSelected(settings.getTypesMod() == Settings.TypesMod.COMPLETELY_RANDOM);
        ptUnchangedRadioButton.setSelected(settings.getTypesMod() == Settings.TypesMod.UNCHANGED);
        ptFollowMegaEvosCheckBox.setSelected(settings.isTypesFollowMegaEvolutions());
        pmsNoGameBreakingMovesCheckBox.setSelected(settings.doBlockBrokenMoves());

        peMakeEvolutionsEasierCheckBox.setSelected(settings.isMakeEvolutionsEasier());
        peRemoveTimeBasedEvolutionsCheckBox.setSelected(settings.isRemoveTimeBasedEvolutions());

        spCustomRadioButton.setSelected(settings.getStartersMod() == Settings.StartersMod.CUSTOM);
        spRandomCompletelyRadioButton.setSelected(settings.getStartersMod() == Settings.StartersMod.COMPLETELY_RANDOM);
        spUnchangedRadioButton.setSelected(settings.getStartersMod() == Settings.StartersMod.UNCHANGED);
        spRandomTwoEvosRadioButton.setSelected(settings.getStartersMod() == Settings.StartersMod.RANDOM_WITH_TWO_EVOLUTIONS);
        spRandomizeStarterHeldItemsCheckBox.setSelected(settings.isRandomizeStartersHeldItems());
        spBanBadItemsCheckBox.setSelected(settings.isBanBadRandomStarterHeldItems());
        spAllowAltFormesCheckBox.setSelected(settings.isAllowStarterAltFormes());

        int[] customStarters = settings.getCustomStarters();
        spComboBox1.setSelectedIndex(customStarters[0] - 1);
        spComboBox2.setSelectedIndex(customStarters[1] - 1);
        spComboBox3.setSelectedIndex(customStarters[2] - 1);

        peUnchangedRadioButton.setSelected(settings.getEvolutionsMod() == Settings.EvolutionsMod.UNCHANGED);
        peRandomRadioButton.setSelected(settings.getEvolutionsMod() == Settings.EvolutionsMod.RANDOM);
        peRandomEveryLevelRadioButton.setSelected(settings.getEvolutionsMod() == Settings.EvolutionsMod.RANDOM_EVERY_LEVEL);
        peSimilarStrengthCheckBox.setSelected(settings.isEvosSimilarStrength());
        peSameTypingCheckBox.setSelected(settings.isEvosSameTyping());
        peLimitEvolutionsToThreeCheckBox.setSelected(settings.isEvosMaxThreeStages());
        peForceChangeCheckBox.setSelected(settings.isEvosForceChange());
        peAllowAltFormesCheckBox.setSelected(settings.isEvosAllowAltFormes());

        mdRandomizeMoveAccuracyCheckBox.setSelected(settings.isRandomizeMoveAccuracies());
        mdRandomizeMoveCategoryCheckBox.setSelected(settings.isRandomizeMoveCategory());
        mdRandomizeMovePowerCheckBox.setSelected(settings.isRandomizeMovePowers());
        mdRandomizeMovePPCheckBox.setSelected(settings.isRandomizeMovePPs());
        mdRandomizeMoveTypesCheckBox.setSelected(settings.isRandomizeMoveTypes());

        pmsRandomCompletelyRadioButton.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.COMPLETELY_RANDOM);
        pmsRandomPreferringSameTypeRadioButton.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE);
        pmsUnchangedRadioButton.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.UNCHANGED);
        pmsMetronomeOnlyModeRadioButton.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.METRONOME_ONLY);
        pmsGuaranteedLevel1MovesCheckBox.setSelected(settings.isStartWithGuaranteedMoves());
        pmsGuaranteedLevel1MovesSlider.setValue(settings.getGuaranteedMoveCount());
        pmsReorderDamagingMovesCheckBox.setSelected(settings.isReorderDamagingMoves());
        pmsForceGoodDamagingCheckBox.setSelected(settings.isMovesetsForceGoodDamaging());
        pmsForceGoodDamagingSlider.setValue(settings.getMovesetsGoodDamagingPercent());
        pmsNoGameBreakingMovesCheckBox.setSelected(settings.isBlockBrokenMovesetMoves());
        pmsEvolutionMovesCheckBox.setSelected(settings.isEvolutionMovesForAll());

        tpSimilarStrengthCheckBox.setSelected(settings.isTrainersUsePokemonOfSimilarStrength());
        tpComboBox.setSelectedItem(trainerSettings.get(settings.getTrainersMod().ordinal()));
        tpRivalCarriesStarterCheckBox.setSelected(settings.isRivalCarriesStarterThroughout());
        tpWeightTypesCheckBox.setSelected(settings.isTrainersMatchTypingDistribution());
        tpDontUseLegendariesCheckBox.setSelected(settings.isTrainersBlockLegendaries());
        tpNoEarlyWonderGuardCheckBox.setSelected(settings.isTrainersBlockEarlyWonderGuard());
        tpForceFullyEvolvedAtCheckBox.setSelected(settings.isTrainersForceFullyEvolved());
        tpForceFullyEvolvedAtSlider.setValue(settings.getTrainersForceFullyEvolvedLevel());
        tpPercentageLevelModifierCheckBox.setSelected(settings.isTrainersLevelModified());
        tpPercentageLevelModifierSlider.setValue(settings.getTrainersLevelModifier());
        tpEliteFourUniquePokemonCheckBox.setSelected(settings.getEliteFourUniquePokemonNumber() > 0);
        tpEliteFourUniquePokemonSpinner.setValue(settings.getEliteFourUniquePokemonNumber() > 0 ? settings.getEliteFourUniquePokemonNumber() : 1);
        tpAllowAlternateFormesCheckBox.setSelected(settings.isAllowTrainerAlternateFormes());
        tpSwapMegaEvosCheckBox.setSelected(settings.isSwapTrainerMegaEvos());
        tpDoubleBattleModeCheckBox.setSelected(settings.isDoubleBattleMode());
        tpBossTrainersCheckBox.setSelected(settings.getAdditionalBossTrainerPokemon() > 0);
        tpBossTrainersSpinner.setValue(settings.getAdditionalBossTrainerPokemon() > 0 ? settings.getAdditionalBossTrainerPokemon() : 1);
        tpImportantTrainersCheckBox.setSelected(settings.getAdditionalImportantTrainerPokemon() > 0);
        tpImportantTrainersSpinner.setValue(settings.getAdditionalImportantTrainerPokemon() > 0 ? settings.getAdditionalImportantTrainerPokemon() : 1);
        tpRegularTrainersCheckBox.setSelected(settings.getAdditionalRegularTrainerPokemon() > 0);
        tpRegularTrainersSpinner.setValue(settings.getAdditionalRegularTrainerPokemon() > 0 ? settings.getAdditionalRegularTrainerPokemon() : 1);
        tpBossTrainersItemsCheckBox.setSelected(settings.isRandomizeHeldItemsForBossTrainerPokemon());
        tpImportantTrainersItemsCheckBox.setSelected(settings.isRandomizeHeldItemsForImportantTrainerPokemon());
        tpRegularTrainersItemsCheckBox.setSelected(settings.isRandomizeHeldItemsForRegularTrainerPokemon());
        tpConsumableItemsOnlyCheckBox.setSelected(settings.isConsumableItemsOnlyForTrainers());
        tpSensibleItemsCheckBox.setSelected(settings.isSensibleItemsOnlyForTrainers());
        tpHighestLevelGetsItemCheckBox.setSelected(settings.isHighestLevelGetsItemsForTrainers());

        tpRandomShinyTrainerPokemonCheckBox.setSelected(settings.isShinyChance());
        tpBetterMovesetsCheckBox.setSelected(settings.isBetterTrainerMovesets());

        totpUnchangedRadioButton.setSelected(settings.getTotemPokemonMod() == Settings.TotemPokemonMod.UNCHANGED);
        totpRandomRadioButton.setSelected(settings.getTotemPokemonMod() == Settings.TotemPokemonMod.RANDOM);
        totpRandomSimilarStrengthRadioButton.setSelected(settings.getTotemPokemonMod() == Settings.TotemPokemonMod.SIMILAR_STRENGTH);
        totpAllyUnchangedRadioButton.setSelected(settings.getAllyPokemonMod() == Settings.AllyPokemonMod.UNCHANGED);
        totpAllyRandomRadioButton.setSelected(settings.getAllyPokemonMod() == Settings.AllyPokemonMod.RANDOM);
        totpAllyRandomSimilarStrengthRadioButton.setSelected(settings.getAllyPokemonMod() == Settings.AllyPokemonMod.SIMILAR_STRENGTH);
        totpAuraUnchangedRadioButton.setSelected(settings.getAuraMod() == Settings.AuraMod.UNCHANGED);
        totpAuraRandomRadioButton.setSelected(settings.getAuraMod() == Settings.AuraMod.RANDOM);
        totpAuraRandomSameStrengthRadioButton.setSelected(settings.getAuraMod() == Settings.AuraMod.SAME_STRENGTH);
        totpRandomizeHeldItemsCheckBox.setSelected(settings.isRandomizeTotemHeldItems());
        totpAllowAltFormesCheckBox.setSelected(settings.isAllowTotemAltFormes());
        totpPercentageLevelModifierCheckBox.setSelected(settings.isTotemLevelsModified());
        totpPercentageLevelModifierSlider.setValue(settings.getTotemLevelModifier());

        wpARCatchEmAllModeRadioButton
                .setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL);
        wpArea1To1RadioButton.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.AREA_MAPPING);
        wpARNoneRadioButton.setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.NONE);
        wpARTypeThemeAreasRadioButton
                .setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS);
        wpGlobal1To1RadioButton.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.GLOBAL_MAPPING);
        wpRandomRadioButton.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.RANDOM);
        wpUnchangedRadioButton.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.UNCHANGED);
        wpUseTimeBasedEncountersCheckBox.setSelected(settings.isUseTimeBasedEncounters());

        wpSetMinimumCatchRateCheckBox.setSelected(settings.isUseMinimumCatchRate());
        wpSetMinimumCatchRateSlider.setValue(settings.getMinimumCatchRateLevel());
        wpDontUseLegendariesCheckBox.setSelected(settings.isBlockWildLegendaries());
        wpARSimilarStrengthRadioButton
                .setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH);
        wpRandomizeHeldItemsCheckBox.setSelected(settings.isRandomizeWildPokemonHeldItems());
        wpBanBadItemsCheckBox.setSelected(settings.isBanBadRandomWildPokemonHeldItems());
        wpBalanceShakingGrassPokemonCheckBox.setSelected(settings.isBalanceShakingGrass());
        wpPercentageLevelModifierCheckBox.setSelected(settings.isWildLevelsModified());
        wpPercentageLevelModifierSlider.setValue(settings.getWildLevelModifier());
        wpAllowAltFormesCheckBox.setSelected(settings.isAllowWildAltFormes());

        stpUnchangedRadioButton.setSelected(settings.getStaticPokemonMod() == Settings.StaticPokemonMod.UNCHANGED);
        stpSwapLegendariesSwapStandardsRadioButton.setSelected(settings.getStaticPokemonMod() == Settings.StaticPokemonMod.RANDOM_MATCHING);
        stpRandomCompletelyRadioButton
                .setSelected(settings.getStaticPokemonMod() == Settings.StaticPokemonMod.COMPLETELY_RANDOM);
        stpRandomSimilarStrengthRadioButton
                .setSelected(settings.getStaticPokemonMod() == Settings.StaticPokemonMod.SIMILAR_STRENGTH);
        stpLimitMainGameLegendariesCheckBox.setSelected(settings.isLimitMainGameLegendaries());
        stpRandomize600BSTCheckBox.setSelected(settings.isLimit600());
        stpAllowAltFormesCheckBox.setSelected(settings.isAllowStaticAltFormes());
        stpSwapMegaEvosCheckBox.setSelected(settings.isSwapStaticMegaEvos());
        stpPercentageLevelModifierCheckBox.setSelected(settings.isStaticLevelModified());
        stpPercentageLevelModifierSlider.setValue(settings.getStaticLevelModifier());
        stpFixMusicCheckBox.setSelected(settings.isCorrectStaticMusic());

        thcRandomCompletelyRadioButton
                .setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.COMPLETELY_RANDOM);
        thcRandomPreferSameTypeRadioButton
                .setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE);
        thcUnchangedRadioButton
                .setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.UNCHANGED);
        tmRandomRadioButton.setSelected(settings.getTmsMod() == Settings.TMsMod.RANDOM);
        tmUnchangedRadioButton.setSelected(settings.getTmsMod() == Settings.TMsMod.UNCHANGED);
        tmLevelupMoveSanityCheckBox.setSelected(settings.isTmLevelUpMoveSanity());
        tmKeepFieldMoveTMsCheckBox.setSelected(settings.isKeepFieldMoveTMs());
        thcFullCompatibilityRadioButton.setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.FULL);
        tmFullHMCompatibilityCheckBox.setSelected(settings.isFullHMCompat());
        tmForceGoodDamagingCheckBox.setSelected(settings.isTmsForceGoodDamaging());
        tmForceGoodDamagingSlider.setValue(settings.getTmsGoodDamagingPercent());
        tmNoGameBreakingMovesCheckBox.setSelected(settings.isBlockBrokenTMMoves());
        tmFollowEvolutionsCheckBox.setSelected(settings.isTmsFollowEvolutions());

        mtcRandomCompletelyRadioButton
                .setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.COMPLETELY_RANDOM);
        mtcRandomPreferSameTypeRadioButton
                .setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.RANDOM_PREFER_TYPE);
        mtcUnchangedRadioButton
                .setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.UNCHANGED);
        mtRandomRadioButton.setSelected(settings.getMoveTutorMovesMod() == Settings.MoveTutorMovesMod.RANDOM);
        mtUnchangedRadioButton.setSelected(settings.getMoveTutorMovesMod() == Settings.MoveTutorMovesMod.UNCHANGED);
        mtLevelupMoveSanityCheckBox.setSelected(settings.isTutorLevelUpMoveSanity());
        mtKeepFieldMoveTutorsCheckBox.setSelected(settings.isKeepFieldMoveTutors());
        mtcFullCompatibilityRadioButton
                .setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.FULL);
        mtForceGoodDamagingCheckBox.setSelected(settings.isTutorsForceGoodDamaging());
        mtForceGoodDamagingSlider.setValue(settings.getTutorsGoodDamagingPercent());
        mtNoGameBreakingMovesCheckBox.setSelected(settings.isBlockBrokenTutorMoves());
        mtFollowEvolutionsCheckBox.setSelected(settings.isTutorFollowEvolutions());

        igtRandomizeBothRequestedGivenRadioButton
                .setSelected(settings.getInGameTradesMod() == Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        igtRandomizeGivenPokemonOnlyRadioButton.setSelected(settings.getInGameTradesMod() == Settings.InGameTradesMod.RANDOMIZE_GIVEN);
        igtRandomizeItemsCheckBox.setSelected(settings.isRandomizeInGameTradesItems());
        igtRandomizeIVsCheckBox.setSelected(settings.isRandomizeInGameTradesIVs());
        igtRandomizeNicknamesCheckBox.setSelected(settings.isRandomizeInGameTradesNicknames());
        igtRandomizeOTsCheckBox.setSelected(settings.isRandomizeInGameTradesOTs());
        igtUnchangedRadioButton.setSelected(settings.getInGameTradesMod() == Settings.InGameTradesMod.UNCHANGED);

        fiRandomRadioButton.setSelected(settings.getFieldItemsMod() == Settings.FieldItemsMod.RANDOM);
        fiRandomEvenDistributionRadioButton.setSelected(settings.getFieldItemsMod() == Settings.FieldItemsMod.RANDOM_EVEN);
        fiShuffleRadioButton.setSelected(settings.getFieldItemsMod() == Settings.FieldItemsMod.SHUFFLE);
        fiUnchangedRadioButton.setSelected(settings.getFieldItemsMod() == Settings.FieldItemsMod.UNCHANGED);
        fiBanBadItemsCheckBox.setSelected(settings.isBanBadRandomFieldItems());

        shRandomRadioButton.setSelected(settings.getShopItemsMod() == Settings.ShopItemsMod.RANDOM);
        shShuffleRadioButton.setSelected(settings.getShopItemsMod() == Settings.ShopItemsMod.SHUFFLE);
        shUnchangedRadioButton.setSelected(settings.getShopItemsMod() == Settings.ShopItemsMod.UNCHANGED);
        shBanBadItemsCheckBox.setSelected(settings.isBanBadRandomShopItems());
        shBanRegularShopItemsCheckBox.setSelected(settings.isBanRegularShopItems());
        shBanOverpoweredShopItemsCheckBox.setSelected(settings.isBanOPShopItems());
        shBalanceShopItemPricesCheckBox.setSelected(settings.isBalanceShopPrices());
        shGuaranteeEvolutionItemsCheckBox.setSelected(settings.isGuaranteeEvolutionItems());
        shGuaranteeXItemsCheckBox.setSelected(settings.isGuaranteeXItems());

        puUnchangedRadioButton.setSelected(settings.getPickupItemsMod() == Settings.PickupItemsMod.UNCHANGED);
        puRandomRadioButton.setSelected(settings.getPickupItemsMod() == Settings.PickupItemsMod.RANDOM);
        puBanBadItemsCheckBox.setSelected(settings.isBanBadRandomPickupItems());

        int mtsSelected = settings.getCurrentMiscTweaks();
        int mtCount = MiscTweak.allTweaks.size();

        for (int mti = 0; mti < mtCount; mti++) {
            MiscTweak mt = MiscTweak.allTweaks.get(mti);
            JCheckBox mtCB = tweakCheckBoxes.get(mti);
            mtCB.setSelected((mtsSelected & mt.getValue()) != 0);
        }

        this.enableOrDisableSubControls();
    }

    private Settings createSettingsFromState(CustomNamesSet customNames) {
        Settings settings = new Settings();
        settings.setRomName(this.romHandler.getROMName());

        settings.setLimitPokemon(limitPokemonCheckBox.isSelected() && limitPokemonCheckBox.isVisible());
        settings.setCurrentRestrictions(currentRestrictions);
        settings.setBanIrregularAltFormes(noIrregularAltFormesCheckBox.isSelected() && noIrregularAltFormesCheckBox.isVisible());
        settings.setRaceMode(raceModeCheckBox.isSelected());

        settings.setChangeImpossibleEvolutions(peChangeImpossibleEvosCheckBox.isSelected() && peChangeImpossibleEvosCheckBox.isVisible());
        settings.setUpdateMoves(mdUpdateMovesCheckBox.isSelected() && mdUpdateMovesCheckBox.isVisible());
        settings.setUpdateMovesToGeneration(mdUpdateComboBox.getSelectedIndex() + (romHandler.generationOfPokemon()+1));
        settings.setRandomizeTrainerNames(tpRandomizeTrainerNamesCheckBox.isSelected());
        settings.setRandomizeTrainerClassNames(tpRandomizeTrainerClassNamesCheckBox.isSelected());

        settings.setBaseStatisticsMod(pbsUnchangedRadioButton.isSelected(), pbsShuffleRadioButton.isSelected(),
                pbsRandomRadioButton.isSelected());
        settings.setBaseStatsFollowEvolutions(pbsFollowEvolutionsCheckBox.isSelected());
        settings.setUpdateBaseStats(pbsUpdateBaseStatsCheckBox.isSelected() && pbsUpdateBaseStatsCheckBox.isVisible());
        settings.setUpdateBaseStatsToGeneration(pbsUpdateComboBox.getSelectedIndex() + (Math.max(6,romHandler.generationOfPokemon()+1)));
        settings.setStandardizeEXPCurves(pbsStandardizeEXPCurvesCheckBox.isSelected());
        settings.setExpCurveMod(pbsLegendariesSlowRadioButton.isSelected(), pbsStrongLegendariesSlowRadioButton.isSelected(),
                pbsAllMediumFastRadioButton.isSelected());
        ExpCurve[] expCurves = getEXPCurvesForGeneration(romHandler.generationOfPokemon());
        settings.setSelectedEXPCurve(expCurves[pbsEXPCurveComboBox.getSelectedIndex()]);
        settings.setBaseStatsFollowMegaEvolutions(pbsFollowMegaEvosCheckBox.isSelected() && pbsFollowMegaEvosCheckBox.isVisible());
        settings.setAssignEvoStatsRandomly(pbsAssignEvoStatsRandomlyCheckBox.isSelected() && pbsAssignEvoStatsRandomlyCheckBox.isVisible());

        settings.setAbilitiesMod(paUnchangedRadioButton.isSelected(), paRandomRadioButton.isSelected());
        settings.setAllowWonderGuard(paAllowWonderGuardCheckBox.isSelected());
        settings.setAbilitiesFollowEvolutions(paFollowEvolutionsCheckBox.isSelected());
        settings.setBanTrappingAbilities(paTrappingAbilitiesCheckBox.isSelected());
        settings.setBanNegativeAbilities(paNegativeAbilitiesCheckBox.isSelected());
        settings.setBanBadAbilities(paBadAbilitiesCheckBox.isSelected());
        settings.setAbilitiesFollowMegaEvolutions(paFollowMegaEvosCheckBox.isSelected());
        settings.setWeighDuplicateAbilitiesTogether(paWeighDuplicatesTogetherCheckBox.isSelected());
        settings.setEnsureTwoAbilities(paEnsureTwoAbilitiesCheckbox.isSelected());

        settings.setTypesMod(ptUnchangedRadioButton.isSelected(), ptRandomFollowEvolutionsRadioButton.isSelected(),
                ptRandomCompletelyRadioButton.isSelected());
        settings.setTypesFollowMegaEvolutions(ptFollowMegaEvosCheckBox.isSelected() && ptFollowMegaEvosCheckBox.isVisible());
        settings.setBlockBrokenMovesetMoves(pmsNoGameBreakingMovesCheckBox.isSelected());
        settings.setDualTypeOnly(ptIsDualTypeCheckBox.isSelected());

        settings.setMakeEvolutionsEasier(peMakeEvolutionsEasierCheckBox.isSelected());
        settings.setRemoveTimeBasedEvolutions(peRemoveTimeBasedEvolutionsCheckBox.isSelected());

        settings.setStartersMod(spUnchangedRadioButton.isSelected(), spCustomRadioButton.isSelected(), spRandomCompletelyRadioButton.isSelected(),
                spRandomTwoEvosRadioButton.isSelected());
        settings.setRandomizeStartersHeldItems(spRandomizeStarterHeldItemsCheckBox.isSelected() && spRandomizeStarterHeldItemsCheckBox.isVisible());
        settings.setBanBadRandomStarterHeldItems(spBanBadItemsCheckBox.isSelected() && spBanBadItemsCheckBox.isVisible());
        settings.setAllowStarterAltFormes(spAllowAltFormesCheckBox.isSelected() && spAllowAltFormesCheckBox.isVisible());

        int[] customStarters = new int[] { spComboBox1.getSelectedIndex() + 1,
                spComboBox2.getSelectedIndex() + 1, spComboBox3.getSelectedIndex() + 1 };
        settings.setCustomStarters(customStarters);

        settings.setEvolutionsMod(peUnchangedRadioButton.isSelected(), peRandomRadioButton.isSelected(), peRandomEveryLevelRadioButton.isSelected());
        settings.setEvosSimilarStrength(peSimilarStrengthCheckBox.isSelected());
        settings.setEvosSameTyping(peSameTypingCheckBox.isSelected());
        settings.setEvosMaxThreeStages(peLimitEvolutionsToThreeCheckBox.isSelected());
        settings.setEvosForceChange(peForceChangeCheckBox.isSelected());
        settings.setEvosAllowAltFormes(peAllowAltFormesCheckBox.isSelected() && peAllowAltFormesCheckBox.isVisible());

        settings.setRandomizeMoveAccuracies(mdRandomizeMoveAccuracyCheckBox.isSelected());
        settings.setRandomizeMoveCategory(mdRandomizeMoveCategoryCheckBox.isSelected());
        settings.setRandomizeMovePowers(mdRandomizeMovePowerCheckBox.isSelected());
        settings.setRandomizeMovePPs(mdRandomizeMovePPCheckBox.isSelected());
        settings.setRandomizeMoveTypes(mdRandomizeMoveTypesCheckBox.isSelected());

        settings.setMovesetsMod(pmsUnchangedRadioButton.isSelected(), pmsRandomPreferringSameTypeRadioButton.isSelected(),
                pmsRandomCompletelyRadioButton.isSelected(), pmsMetronomeOnlyModeRadioButton.isSelected());
        settings.setStartWithGuaranteedMoves(pmsGuaranteedLevel1MovesCheckBox.isSelected() && pmsGuaranteedLevel1MovesCheckBox.isVisible());
        settings.setGuaranteedMoveCount(pmsGuaranteedLevel1MovesSlider.getValue());
        settings.setReorderDamagingMoves(pmsReorderDamagingMovesCheckBox.isSelected());

        settings.setMovesetsForceGoodDamaging(pmsForceGoodDamagingCheckBox.isSelected());
        settings.setMovesetsGoodDamagingPercent(pmsForceGoodDamagingSlider.getValue());
        settings.setBlockBrokenMovesetMoves(pmsNoGameBreakingMovesCheckBox.isSelected());
        settings.setEvolutionMovesForAll(pmsEvolutionMovesCheckBox.isVisible() &&
                pmsEvolutionMovesCheckBox.isSelected());

        settings.setTrainersMod(isTrainerSetting(TRAINER_UNCHANGED), isTrainerSetting(TRAINER_RANDOM),
                isTrainerSetting(TRAINER_RANDOM_EVEN), isTrainerSetting(TRAINER_RANDOM_EVEN_MAIN),
                isTrainerSetting(TRAINER_TYPE_THEMED), isTrainerSetting(TRAINER_TYPE_THEMED_ELITE4_GYMS));
        settings.setTrainersUsePokemonOfSimilarStrength(tpSimilarStrengthCheckBox.isSelected());
        settings.setRivalCarriesStarterThroughout(tpRivalCarriesStarterCheckBox.isSelected());
        settings.setTrainersMatchTypingDistribution(tpWeightTypesCheckBox.isSelected());
        settings.setTrainersBlockLegendaries(tpDontUseLegendariesCheckBox.isSelected());
        settings.setTrainersBlockEarlyWonderGuard(tpNoEarlyWonderGuardCheckBox.isSelected());
        settings.setTrainersForceFullyEvolved(tpForceFullyEvolvedAtCheckBox.isSelected());
        settings.setTrainersForceFullyEvolvedLevel(tpForceFullyEvolvedAtSlider.getValue());
        settings.setTrainersLevelModified(tpPercentageLevelModifierCheckBox.isSelected());
        settings.setTrainersLevelModifier(tpPercentageLevelModifierSlider.getValue());
        settings.setEliteFourUniquePokemonNumber(tpEliteFourUniquePokemonCheckBox.isVisible() && tpEliteFourUniquePokemonCheckBox.isSelected() ? (int)tpEliteFourUniquePokemonSpinner.getValue() : 0);
        settings.setAllowTrainerAlternateFormes(tpAllowAlternateFormesCheckBox.isSelected() && tpAllowAlternateFormesCheckBox.isVisible());
        settings.setSwapTrainerMegaEvos(tpSwapMegaEvosCheckBox.isSelected() && tpSwapMegaEvosCheckBox.isVisible());
        settings.setDoubleBattleMode(tpDoubleBattleModeCheckBox.isVisible() && tpDoubleBattleModeCheckBox.isSelected());
        settings.setAdditionalBossTrainerPokemon(tpBossTrainersCheckBox.isVisible() && tpBossTrainersCheckBox.isSelected() ? (int)tpBossTrainersSpinner.getValue() : 0);
        settings.setAdditionalImportantTrainerPokemon(tpImportantTrainersCheckBox.isVisible() && tpImportantTrainersCheckBox.isSelected() ? (int)tpImportantTrainersSpinner.getValue() : 0);
        settings.setAdditionalRegularTrainerPokemon(tpRegularTrainersCheckBox.isVisible() && tpRegularTrainersCheckBox.isSelected() ? (int)tpRegularTrainersSpinner.getValue() : 0);
        settings.setShinyChance(tpRandomShinyTrainerPokemonCheckBox.isVisible() && tpRandomShinyTrainerPokemonCheckBox.isSelected());
        settings.setBetterTrainerMovesets(tpBetterMovesetsCheckBox.isVisible() && tpBetterMovesetsCheckBox.isSelected());
        settings.setRandomizeHeldItemsForBossTrainerPokemon(tpBossTrainersItemsCheckBox.isVisible() && tpBossTrainersItemsCheckBox.isSelected());
        settings.setRandomizeHeldItemsForImportantTrainerPokemon(tpImportantTrainersItemsCheckBox.isVisible() && tpImportantTrainersItemsCheckBox.isSelected());
        settings.setRandomizeHeldItemsForRegularTrainerPokemon(tpRegularTrainersItemsCheckBox.isVisible() && tpRegularTrainersItemsCheckBox.isSelected());
        settings.setConsumableItemsOnlyForTrainers(tpConsumableItemsOnlyCheckBox.isVisible() && tpConsumableItemsOnlyCheckBox.isSelected());
        settings.setSensibleItemsOnlyForTrainers(tpSensibleItemsCheckBox.isVisible() && tpSensibleItemsCheckBox.isSelected());
        settings.setHighestLevelGetsItemsForTrainers(tpHighestLevelGetsItemCheckBox.isVisible() && tpHighestLevelGetsItemCheckBox.isSelected());

        settings.setTotemPokemonMod(totpUnchangedRadioButton.isSelected(), totpRandomRadioButton.isSelected(), totpRandomSimilarStrengthRadioButton.isSelected());
        settings.setAllyPokemonMod(totpAllyUnchangedRadioButton.isSelected(), totpAllyRandomRadioButton.isSelected(), totpAllyRandomSimilarStrengthRadioButton.isSelected());
        settings.setAuraMod(totpAuraUnchangedRadioButton.isSelected(), totpAuraRandomRadioButton.isSelected(), totpAuraRandomSameStrengthRadioButton.isSelected());
        settings.setRandomizeTotemHeldItems(totpRandomizeHeldItemsCheckBox.isSelected());
        settings.setAllowTotemAltFormes(totpAllowAltFormesCheckBox.isSelected());
        settings.setTotemLevelsModified(totpPercentageLevelModifierCheckBox.isSelected());
        settings.setTotemLevelModifier(totpPercentageLevelModifierSlider.getValue());

        settings.setWildPokemonMod(wpUnchangedRadioButton.isSelected(), wpRandomRadioButton.isSelected(), wpArea1To1RadioButton.isSelected(),
                wpGlobal1To1RadioButton.isSelected());
        settings.setWildPokemonRestrictionMod(wpARNoneRadioButton.isSelected(), wpARSimilarStrengthRadioButton.isSelected(),
                wpARCatchEmAllModeRadioButton.isSelected(), wpARTypeThemeAreasRadioButton.isSelected());
        settings.setUseTimeBasedEncounters(wpUseTimeBasedEncountersCheckBox.isSelected());
        settings.setUseMinimumCatchRate(wpSetMinimumCatchRateCheckBox.isSelected());
        settings.setMinimumCatchRateLevel(wpSetMinimumCatchRateSlider.getValue());
        settings.setBlockWildLegendaries(wpDontUseLegendariesCheckBox.isSelected());
        settings.setRandomizeWildPokemonHeldItems(wpRandomizeHeldItemsCheckBox.isSelected() && wpRandomizeHeldItemsCheckBox.isVisible());
        settings.setBanBadRandomWildPokemonHeldItems(wpBanBadItemsCheckBox.isSelected() && wpBanBadItemsCheckBox.isVisible());
        settings.setBalanceShakingGrass(wpBalanceShakingGrassPokemonCheckBox.isSelected() && wpBalanceShakingGrassPokemonCheckBox.isVisible());
        settings.setWildLevelsModified(wpPercentageLevelModifierCheckBox.isSelected());
        settings.setWildLevelModifier(wpPercentageLevelModifierSlider.getValue());
        settings.setAllowWildAltFormes(wpAllowAltFormesCheckBox.isSelected() && wpAllowAltFormesCheckBox.isVisible());

        settings.setStaticPokemonMod(stpUnchangedRadioButton.isSelected(), stpSwapLegendariesSwapStandardsRadioButton.isSelected(),
                stpRandomCompletelyRadioButton.isSelected(), stpRandomSimilarStrengthRadioButton.isSelected());
        settings.setLimitMainGameLegendaries(stpLimitMainGameLegendariesCheckBox.isSelected() && stpLimitMainGameLegendariesCheckBox.isVisible());
        settings.setLimit600(stpRandomize600BSTCheckBox.isSelected());
        settings.setAllowStaticAltFormes(stpAllowAltFormesCheckBox.isSelected() && stpAllowAltFormesCheckBox.isVisible());
        settings.setSwapStaticMegaEvos(stpSwapMegaEvosCheckBox.isSelected() && stpSwapMegaEvosCheckBox.isVisible());
        settings.setStaticLevelModified(stpPercentageLevelModifierCheckBox.isSelected());
        settings.setStaticLevelModifier(stpPercentageLevelModifierSlider.getValue());
        settings.setCorrectStaticMusic(stpFixMusicCheckBox.isSelected() && stpFixMusicCheckBox.isVisible());

        settings.setTmsMod(tmUnchangedRadioButton.isSelected(), tmRandomRadioButton.isSelected());

        settings.setTmsHmsCompatibilityMod(thcUnchangedRadioButton.isSelected(), thcRandomPreferSameTypeRadioButton.isSelected(),
                thcRandomCompletelyRadioButton.isSelected(), thcFullCompatibilityRadioButton.isSelected());
        settings.setTmLevelUpMoveSanity(tmLevelupMoveSanityCheckBox.isSelected());
        settings.setKeepFieldMoveTMs(tmKeepFieldMoveTMsCheckBox.isSelected());
        settings.setFullHMCompat(tmFullHMCompatibilityCheckBox.isSelected() && tmFullHMCompatibilityCheckBox.isVisible());
        settings.setTmsForceGoodDamaging(tmForceGoodDamagingCheckBox.isSelected());
        settings.setTmsGoodDamagingPercent(tmForceGoodDamagingSlider.getValue());
        settings.setBlockBrokenTMMoves(tmNoGameBreakingMovesCheckBox.isSelected());
        settings.setTmsFollowEvolutions(tmFollowEvolutionsCheckBox.isSelected());

        settings.setMoveTutorMovesMod(mtUnchangedRadioButton.isSelected(), mtRandomRadioButton.isSelected());
        settings.setMoveTutorsCompatibilityMod(mtcUnchangedRadioButton.isSelected(), mtcRandomPreferSameTypeRadioButton.isSelected(),
                mtcRandomCompletelyRadioButton.isSelected(), mtcFullCompatibilityRadioButton.isSelected());
        settings.setTutorLevelUpMoveSanity(mtLevelupMoveSanityCheckBox.isSelected());
        settings.setKeepFieldMoveTutors(mtKeepFieldMoveTutorsCheckBox.isSelected());
        settings.setTutorsForceGoodDamaging(mtForceGoodDamagingCheckBox.isSelected());
        settings.setTutorsGoodDamagingPercent(mtForceGoodDamagingSlider.getValue());
        settings.setBlockBrokenTutorMoves(mtNoGameBreakingMovesCheckBox.isSelected());
        settings.setTutorFollowEvolutions(mtFollowEvolutionsCheckBox.isSelected());

        settings.setInGameTradesMod(igtUnchangedRadioButton.isSelected(), igtRandomizeGivenPokemonOnlyRadioButton.isSelected(), igtRandomizeBothRequestedGivenRadioButton.isSelected());
        settings.setRandomizeInGameTradesItems(igtRandomizeItemsCheckBox.isSelected());
        settings.setRandomizeInGameTradesIVs(igtRandomizeIVsCheckBox.isSelected());
        settings.setRandomizeInGameTradesNicknames(igtRandomizeNicknamesCheckBox.isSelected());
        settings.setRandomizeInGameTradesOTs(igtRandomizeOTsCheckBox.isSelected());

        settings.setFieldItemsMod(fiUnchangedRadioButton.isSelected(), fiShuffleRadioButton.isSelected(), fiRandomRadioButton.isSelected(), fiRandomEvenDistributionRadioButton.isSelected());
        settings.setBanBadRandomFieldItems(fiBanBadItemsCheckBox.isSelected());

        settings.setShopItemsMod(shUnchangedRadioButton.isSelected(), shShuffleRadioButton.isSelected(), shRandomRadioButton.isSelected());
        settings.setBanBadRandomShopItems(shBanBadItemsCheckBox.isSelected());
        settings.setBanRegularShopItems(shBanRegularShopItemsCheckBox.isSelected());
        settings.setBanOPShopItems(shBanOverpoweredShopItemsCheckBox.isSelected());
        settings.setBalanceShopPrices(shBalanceShopItemPricesCheckBox.isSelected());
        settings.setGuaranteeEvolutionItems(shGuaranteeEvolutionItemsCheckBox.isSelected());
        settings.setGuaranteeXItems(shGuaranteeXItemsCheckBox.isSelected());

        settings.setPickupItemsMod(puUnchangedRadioButton.isSelected(), puRandomRadioButton.isSelected());
        settings.setBanBadRandomPickupItems(puBanBadItemsCheckBox.isSelected());

        int currentMiscTweaks = 0;
        int mtCount = MiscTweak.allTweaks.size();

        for (int mti = 0; mti < mtCount; mti++) {
            MiscTweak mt = MiscTweak.allTweaks.get(mti);
            JCheckBox mtCB = tweakCheckBoxes.get(mti);
            if (mtCB.isSelected()) {
                currentMiscTweaks |= mt.getValue();
            }
        }

        settings.setCurrentMiscTweaks(currentMiscTweaks);

        settings.setCustomNames(customNames);

        return settings;
    }

    private Settings getCurrentSettings() throws IOException {
        return createSettingsFromState(FileFunctions.getCustomNames());
    }

    private void attemptToLogException(Exception ex, String baseMessageKey, String noLogMessageKey,
                                       String settingsString, String seedString) {
        attemptToLogException(ex, baseMessageKey, noLogMessageKey, false, settingsString, seedString);
    }

    private void attemptToLogException(Exception ex, String baseMessageKey, String noLogMessageKey, boolean showMessage,
                                       String settingsString, String seedString) {

        // Make sure the operation dialog doesn't show up over the error
        // dialog
        SwingUtilities.invokeLater(() -> NewRandomizerGUI.this.opDialog.setVisible(false));

        Date now = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        try {
            String errlog = "error_" + ft.format(now) + ".txt";
            PrintStream ps = new PrintStream(new FileOutputStream(errlog));
            ps.println("Randomizer Version: " + Version.VERSION_STRING);
            if (seedString != null) {
                ps.println("Seed: " + seedString);
            }
            if (settingsString != null) {
                ps.println("Settings String: " + Version.VERSION + settingsString);
            }
            ps.println("Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vm.name"));
            PrintStream e1 = System.err;
            System.setErr(ps);
            if (this.romHandler != null) {
                try {
                    ps.println("ROM: " + romHandler.getROMName());
                    ps.println("Code: " + romHandler.getROMCode());
                    ps.println("Reported Support Level: " + romHandler.getSupportLevel());
                    ps.println();
                } catch (Exception ex2) {
                    // Do nothing, just don't fail
                }
            }
            ex.printStackTrace();
            ps.println();
            ps.println("--ROM Diagnostics--");
            if (!romHandler.isRomValid()) {
                ps.println(bundle.getString("Log.InvalidRomLoaded"));
            }
            romHandler.printRomDiagnostics(ps);
            System.setErr(e1);
            ps.close();
            if (showMessage) {
                JOptionPane.showMessageDialog(mainPanel,
                        String.format(bundle.getString(baseMessageKey), ex.getMessage(), errlog));
            } else {
                JOptionPane.showMessageDialog(mainPanel, String.format(bundle.getString(baseMessageKey), errlog));
            }
        } catch (Exception logex) {
            if (showMessage) {
                JOptionPane.showMessageDialog(mainPanel, String.format(bundle.getString(noLogMessageKey), ex.getMessage()));
            } else {
                JOptionPane.showMessageDialog(mainPanel, bundle.getString(noLogMessageKey));
            }
        }
    }

    public String getValidRequiredROMName(String config, CustomNamesSet customNames)
            throws UnsupportedEncodingException, InvalidSupplementFilesException {
        try {
            Utils.validatePresetSupplementFiles(config, customNames);
        } catch (InvalidSupplementFilesException e) {
            switch (e.getType()) {
                case CUSTOM_NAMES:
                    JOptionPane.showMessageDialog(null, bundle.getString("GUI.presetDifferentCustomNames"));
                    break;
                default:
                    throw e;
            }
        }
        byte[] data = Base64.getDecoder().decode(config);

        int nameLength = data[Settings.LENGTH_OF_SETTINGS_DATA] & 0xFF;
        if (data.length != Settings.LENGTH_OF_SETTINGS_DATA + 9 + nameLength) {
            return null; // not valid length
        }
        return new String(data, Settings.LENGTH_OF_SETTINGS_DATA + 1, nameLength, "US-ASCII");
    }

    private void initialState() {

        romNameLabel.setText(bundle.getString("GUI.noRomLoaded"));
        romCodeLabel.setText("");
        romSupportLabel.setText("");

        gameMascotLabel.setIcon(emptyIcon);

        limitPokemonCheckBox.setVisible(true);
        limitPokemonCheckBox.setEnabled(false);
        limitPokemonCheckBox.setSelected(false);
        limitPokemonButton.setVisible(true);
        limitPokemonButton.setEnabled(false);
        noIrregularAltFormesCheckBox.setVisible(true);
        noIrregularAltFormesCheckBox.setEnabled(false);
        noIrregularAltFormesCheckBox.setSelected(false);
        raceModeCheckBox.setVisible(true);
        raceModeCheckBox.setEnabled(false);
        raceModeCheckBox.setSelected(false);

        currentRestrictions = null;

        openROMButton.setVisible(true);
        openROMButton.setEnabled(true);
        openROMButton.setSelected(false);
        randomizeSaveButton.setVisible(true);
        randomizeSaveButton.setEnabled(true);
        randomizeSaveButton.setSelected(false);
        premadeSeedButton.setVisible(true);
        premadeSeedButton.setEnabled(true);
        premadeSeedButton.setSelected(false);
        settingsButton.setVisible(true);
        settingsButton.setEnabled(true);
        settingsButton.setSelected(false);

        loadSettingsButton.setVisible(true);
        loadSettingsButton.setEnabled(false);
        loadSettingsButton.setSelected(false);
        saveSettingsButton.setVisible(true);
        saveSettingsButton.setEnabled(false);
        saveSettingsButton.setSelected(false);
        pbsUnchangedRadioButton.setVisible(true);
        pbsUnchangedRadioButton.setEnabled(false);
        pbsUnchangedRadioButton.setSelected(false);
        pbsShuffleRadioButton.setVisible(true);
        pbsShuffleRadioButton.setEnabled(false);
        pbsShuffleRadioButton.setSelected(false);
        pbsRandomRadioButton.setVisible(true);
        pbsRandomRadioButton.setEnabled(false);
        pbsRandomRadioButton.setSelected(false);
        pbsLegendariesSlowRadioButton.setVisible(true);
        pbsLegendariesSlowRadioButton.setEnabled(false);
        pbsLegendariesSlowRadioButton.setSelected(false);
        pbsStrongLegendariesSlowRadioButton.setVisible(true);
        pbsStrongLegendariesSlowRadioButton.setEnabled(false);
        pbsStrongLegendariesSlowRadioButton.setSelected(false);
        pbsAllMediumFastRadioButton.setVisible(true);
        pbsAllMediumFastRadioButton.setEnabled(false);
        pbsAllMediumFastRadioButton.setSelected(false);
        pbsStandardizeEXPCurvesCheckBox.setVisible(true);
        pbsStandardizeEXPCurvesCheckBox.setEnabled(false);
        pbsStandardizeEXPCurvesCheckBox.setSelected(false);
        pbsEXPCurveComboBox.setVisible(true);
        pbsEXPCurveComboBox.setEnabled(false);
        pbsEXPCurveComboBox.setSelectedIndex(0);
        pbsEXPCurveComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "Medium Fast" }));
        pbsFollowEvolutionsCheckBox.setVisible(true);
        pbsFollowEvolutionsCheckBox.setEnabled(false);
        pbsFollowEvolutionsCheckBox.setSelected(false);
        pbsUpdateBaseStatsCheckBox.setVisible(true);
        pbsUpdateBaseStatsCheckBox.setEnabled(false);
        pbsUpdateBaseStatsCheckBox.setSelected(false);
        pbsFollowMegaEvosCheckBox.setVisible(true);
        pbsFollowMegaEvosCheckBox.setEnabled(false);
        pbsFollowMegaEvosCheckBox.setSelected(false);
        pbsUpdateComboBox.setVisible(true);
        pbsUpdateComboBox.setEnabled(false);
        pbsUpdateComboBox.setSelectedIndex(0);
        pbsUpdateComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "--" }));
        pbsAssignEvoStatsRandomlyCheckBox.setVisible(true);
        pbsAssignEvoStatsRandomlyCheckBox.setEnabled(false);
        pbsAssignEvoStatsRandomlyCheckBox.setSelected(false);
        ptUnchangedRadioButton.setVisible(true);
        ptUnchangedRadioButton.setEnabled(false);
        ptUnchangedRadioButton.setSelected(false);
        ptRandomFollowEvolutionsRadioButton.setVisible(true);
        ptRandomFollowEvolutionsRadioButton.setEnabled(false);
        ptRandomFollowEvolutionsRadioButton.setSelected(false);
        ptRandomCompletelyRadioButton.setVisible(true);
        ptRandomCompletelyRadioButton.setEnabled(false);
        ptRandomCompletelyRadioButton.setSelected(false);
        ptFollowMegaEvosCheckBox.setVisible(true);
        ptFollowMegaEvosCheckBox.setEnabled(false);
        ptFollowMegaEvosCheckBox.setSelected(false);
        ptIsDualTypeCheckBox.setVisible(true);
        ptIsDualTypeCheckBox.setEnabled(false);
        ptIsDualTypeCheckBox.setSelected(false);
        pokemonAbilitiesPanel.setVisible(true);
        paUnchangedRadioButton.setVisible(true);
        paUnchangedRadioButton.setEnabled(false);
        paUnchangedRadioButton.setSelected(false);
        paRandomRadioButton.setVisible(true);
        paRandomRadioButton.setEnabled(false);
        paRandomRadioButton.setSelected(false);
        paAllowWonderGuardCheckBox.setVisible(true);
        paAllowWonderGuardCheckBox.setEnabled(false);
        paAllowWonderGuardCheckBox.setSelected(false);
        paFollowEvolutionsCheckBox.setVisible(true);
        paFollowEvolutionsCheckBox.setEnabled(false);
        paFollowEvolutionsCheckBox.setSelected(false);
        paTrappingAbilitiesCheckBox.setVisible(true);
        paTrappingAbilitiesCheckBox.setEnabled(false);
        paTrappingAbilitiesCheckBox.setSelected(false);
        paNegativeAbilitiesCheckBox.setVisible(true);
        paNegativeAbilitiesCheckBox.setEnabled(false);
        paNegativeAbilitiesCheckBox.setSelected(false);
        paBadAbilitiesCheckBox.setVisible(true);
        paBadAbilitiesCheckBox.setEnabled(false);
        paBadAbilitiesCheckBox.setSelected(false);
        paFollowMegaEvosCheckBox.setVisible(true);
        paFollowMegaEvosCheckBox.setEnabled(false);
        paFollowMegaEvosCheckBox.setSelected(false);
        paWeighDuplicatesTogetherCheckBox.setVisible(true);
        paWeighDuplicatesTogetherCheckBox.setEnabled(false);
        paWeighDuplicatesTogetherCheckBox.setSelected(false);
        paEnsureTwoAbilitiesCheckbox.setVisible(true);
        paEnsureTwoAbilitiesCheckbox.setEnabled(false);
        paEnsureTwoAbilitiesCheckbox.setSelected(false);
        peUnchangedRadioButton.setVisible(true);
        peUnchangedRadioButton.setEnabled(false);
        peUnchangedRadioButton.setSelected(false);
        peRandomRadioButton.setVisible(true);
        peRandomRadioButton.setEnabled(false);
        peRandomRadioButton.setSelected(false);
        peRandomEveryLevelRadioButton.setVisible(true);
        peRandomEveryLevelRadioButton.setEnabled(false);
        peRandomEveryLevelRadioButton.setSelected(false);
        peSimilarStrengthCheckBox.setVisible(true);
        peSimilarStrengthCheckBox.setEnabled(false);
        peSimilarStrengthCheckBox.setSelected(false);
        peSameTypingCheckBox.setVisible(true);
        peSameTypingCheckBox.setEnabled(false);
        peSameTypingCheckBox.setSelected(false);
        peLimitEvolutionsToThreeCheckBox.setVisible(true);
        peLimitEvolutionsToThreeCheckBox.setEnabled(false);
        peLimitEvolutionsToThreeCheckBox.setSelected(false);
        peForceChangeCheckBox.setVisible(true);
        peForceChangeCheckBox.setEnabled(false);
        peForceChangeCheckBox.setSelected(false);
        peChangeImpossibleEvosCheckBox.setVisible(true);
        peChangeImpossibleEvosCheckBox.setEnabled(false);
        peChangeImpossibleEvosCheckBox.setSelected(false);
        peMakeEvolutionsEasierCheckBox.setVisible(true);
        peMakeEvolutionsEasierCheckBox.setEnabled(false);
        peMakeEvolutionsEasierCheckBox.setSelected(false);
        peRemoveTimeBasedEvolutionsCheckBox.setVisible(true);
        peRemoveTimeBasedEvolutionsCheckBox.setEnabled(false);
        peRemoveTimeBasedEvolutionsCheckBox.setSelected(false);
        peAllowAltFormesCheckBox.setVisible(true);
        peAllowAltFormesCheckBox.setEnabled(false);
        peAllowAltFormesCheckBox.setSelected(false);
        spUnchangedRadioButton.setVisible(true);
        spUnchangedRadioButton.setEnabled(false);
        spUnchangedRadioButton.setSelected(false);
        spCustomRadioButton.setVisible(true);
        spCustomRadioButton.setEnabled(false);
        spCustomRadioButton.setSelected(false);
        spRandomCompletelyRadioButton.setVisible(true);
        spRandomCompletelyRadioButton.setEnabled(false);
        spRandomCompletelyRadioButton.setSelected(false);
        spRandomTwoEvosRadioButton.setVisible(true);
        spRandomTwoEvosRadioButton.setEnabled(false);
        spRandomTwoEvosRadioButton.setSelected(false);
        spComboBox1.setVisible(true);
        spComboBox1.setEnabled(false);
        spComboBox1.setSelectedIndex(0);
        spComboBox1.setModel(new DefaultComboBoxModel<>(new String[] { "--" }));
        spComboBox2.setVisible(true);
        spComboBox2.setEnabled(false);
        spComboBox2.setSelectedIndex(0);
        spComboBox2.setModel(new DefaultComboBoxModel<>(new String[] { "--" }));
        spComboBox3.setVisible(true);
        spComboBox3.setEnabled(false);
        spComboBox3.setSelectedIndex(0);
        spComboBox3.setModel(new DefaultComboBoxModel<>(new String[] { "--" }));
        spRandomizeStarterHeldItemsCheckBox.setVisible(true);
        spRandomizeStarterHeldItemsCheckBox.setEnabled(false);
        spRandomizeStarterHeldItemsCheckBox.setSelected(false);
        spBanBadItemsCheckBox.setVisible(true);
        spBanBadItemsCheckBox.setEnabled(false);
        spBanBadItemsCheckBox.setSelected(false);
        spAllowAltFormesCheckBox.setVisible(true);
        spAllowAltFormesCheckBox.setEnabled(false);
        spAllowAltFormesCheckBox.setSelected(false);
        stpUnchangedRadioButton.setVisible(true);
        stpUnchangedRadioButton.setEnabled(false);
        stpUnchangedRadioButton.setSelected(false);
        stpSwapLegendariesSwapStandardsRadioButton.setVisible(true);
        stpSwapLegendariesSwapStandardsRadioButton.setEnabled(false);
        stpSwapLegendariesSwapStandardsRadioButton.setSelected(false);
        stpRandomCompletelyRadioButton.setVisible(true);
        stpRandomCompletelyRadioButton.setEnabled(false);
        stpRandomCompletelyRadioButton.setSelected(false);
        stpRandomSimilarStrengthRadioButton.setVisible(true);
        stpRandomSimilarStrengthRadioButton.setEnabled(false);
        stpRandomSimilarStrengthRadioButton.setSelected(false);
        stpPercentageLevelModifierCheckBox.setVisible(true);
        stpPercentageLevelModifierCheckBox.setEnabled(false);
        stpPercentageLevelModifierCheckBox.setSelected(false);
        stpPercentageLevelModifierSlider.setVisible(true);
        stpPercentageLevelModifierSlider.setEnabled(false);
        stpPercentageLevelModifierSlider.setValue(0);
        stpLimitMainGameLegendariesCheckBox.setVisible(true);
        stpLimitMainGameLegendariesCheckBox.setEnabled(false);
        stpLimitMainGameLegendariesCheckBox.setSelected(false);
        stpRandomize600BSTCheckBox.setVisible(true);
        stpRandomize600BSTCheckBox.setEnabled(false);
        stpRandomize600BSTCheckBox.setSelected(false);
        stpAllowAltFormesCheckBox.setVisible(true);
        stpAllowAltFormesCheckBox.setEnabled(false);
        stpAllowAltFormesCheckBox.setSelected(false);
        stpSwapMegaEvosCheckBox.setVisible(true);
        stpSwapMegaEvosCheckBox.setEnabled(false);
        stpSwapMegaEvosCheckBox.setSelected(false);
        stpFixMusicCheckBox.setVisible(true);
        stpFixMusicCheckBox.setEnabled(false);
        stpFixMusicCheckBox.setSelected(false);
        igtUnchangedRadioButton.setVisible(true);
        igtUnchangedRadioButton.setEnabled(false);
        igtUnchangedRadioButton.setSelected(false);
        igtRandomizeGivenPokemonOnlyRadioButton.setVisible(true);
        igtRandomizeGivenPokemonOnlyRadioButton.setEnabled(false);
        igtRandomizeGivenPokemonOnlyRadioButton.setSelected(false);
        igtRandomizeBothRequestedGivenRadioButton.setVisible(true);
        igtRandomizeBothRequestedGivenRadioButton.setEnabled(false);
        igtRandomizeBothRequestedGivenRadioButton.setSelected(false);
        igtRandomizeNicknamesCheckBox.setVisible(true);
        igtRandomizeNicknamesCheckBox.setEnabled(false);
        igtRandomizeNicknamesCheckBox.setSelected(false);
        igtRandomizeOTsCheckBox.setVisible(true);
        igtRandomizeOTsCheckBox.setEnabled(false);
        igtRandomizeOTsCheckBox.setSelected(false);
        igtRandomizeIVsCheckBox.setVisible(true);
        igtRandomizeIVsCheckBox.setEnabled(false);
        igtRandomizeIVsCheckBox.setSelected(false);
        igtRandomizeItemsCheckBox.setVisible(true);
        igtRandomizeItemsCheckBox.setEnabled(false);
        igtRandomizeItemsCheckBox.setSelected(false);
        mdRandomizeMovePowerCheckBox.setVisible(true);
        mdRandomizeMovePowerCheckBox.setEnabled(false);
        mdRandomizeMovePowerCheckBox.setSelected(false);
        mdRandomizeMoveAccuracyCheckBox.setVisible(true);
        mdRandomizeMoveAccuracyCheckBox.setEnabled(false);
        mdRandomizeMoveAccuracyCheckBox.setSelected(false);
        mdRandomizeMovePPCheckBox.setVisible(true);
        mdRandomizeMovePPCheckBox.setEnabled(false);
        mdRandomizeMovePPCheckBox.setSelected(false);
        mdRandomizeMoveTypesCheckBox.setVisible(true);
        mdRandomizeMoveTypesCheckBox.setEnabled(false);
        mdRandomizeMoveTypesCheckBox.setSelected(false);
        mdRandomizeMoveCategoryCheckBox.setVisible(true);
        mdRandomizeMoveCategoryCheckBox.setEnabled(false);
        mdRandomizeMoveCategoryCheckBox.setSelected(false);
        mdUpdateMovesCheckBox.setVisible(true);
        mdUpdateMovesCheckBox.setEnabled(false);
        mdUpdateMovesCheckBox.setSelected(false);
        mdUpdateComboBox.setVisible(true);
        mdUpdateComboBox.setEnabled(false);
        mdUpdateComboBox.setSelectedIndex(0);
        mdUpdateComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "--" }));
        pmsUnchangedRadioButton.setVisible(true);
        pmsUnchangedRadioButton.setEnabled(false);
        pmsUnchangedRadioButton.setSelected(false);
        pmsRandomPreferringSameTypeRadioButton.setVisible(true);
        pmsRandomPreferringSameTypeRadioButton.setEnabled(false);
        pmsRandomPreferringSameTypeRadioButton.setSelected(false);
        pmsRandomCompletelyRadioButton.setVisible(true);
        pmsRandomCompletelyRadioButton.setEnabled(false);
        pmsRandomCompletelyRadioButton.setSelected(false);
        pmsMetronomeOnlyModeRadioButton.setVisible(true);
        pmsMetronomeOnlyModeRadioButton.setEnabled(false);
        pmsMetronomeOnlyModeRadioButton.setSelected(false);
        pmsGuaranteedLevel1MovesCheckBox.setVisible(true);
        pmsGuaranteedLevel1MovesCheckBox.setEnabled(false);
        pmsGuaranteedLevel1MovesCheckBox.setSelected(false);
        pmsReorderDamagingMovesCheckBox.setVisible(true);
        pmsReorderDamagingMovesCheckBox.setEnabled(false);
        pmsReorderDamagingMovesCheckBox.setSelected(false);
        pmsNoGameBreakingMovesCheckBox.setVisible(true);
        pmsNoGameBreakingMovesCheckBox.setEnabled(false);
        pmsNoGameBreakingMovesCheckBox.setSelected(false);
        pmsForceGoodDamagingCheckBox.setVisible(true);
        pmsForceGoodDamagingCheckBox.setEnabled(false);
        pmsForceGoodDamagingCheckBox.setSelected(false);
        pmsGuaranteedLevel1MovesSlider.setVisible(true);
        pmsGuaranteedLevel1MovesSlider.setEnabled(false);
        pmsGuaranteedLevel1MovesSlider.setValue(pmsGuaranteedLevel1MovesSlider.getMinimum());
        pmsForceGoodDamagingSlider.setVisible(true);
        pmsForceGoodDamagingSlider.setEnabled(false);
        pmsForceGoodDamagingSlider.setValue(pmsForceGoodDamagingSlider.getMinimum());
        pmsEvolutionMovesCheckBox.setVisible(true);
        pmsEvolutionMovesCheckBox.setEnabled(false);
        pmsEvolutionMovesCheckBox.setSelected(false);
        tpComboBox.setVisible(true);
        tpComboBox.setEnabled(false);
        tpComboBox.setModel(new DefaultComboBoxModel<>(new String[] { "Unchanged" }));
        tpRivalCarriesStarterCheckBox.setVisible(true);
        tpRivalCarriesStarterCheckBox.setEnabled(false);
        tpRivalCarriesStarterCheckBox.setSelected(false);
        tpSimilarStrengthCheckBox.setVisible(true);
        tpSimilarStrengthCheckBox.setEnabled(false);
        tpSimilarStrengthCheckBox.setSelected(false);
        tpWeightTypesCheckBox.setVisible(true);
        tpWeightTypesCheckBox.setEnabled(false);
        tpWeightTypesCheckBox.setSelected(false);
        tpDontUseLegendariesCheckBox.setVisible(true);
        tpDontUseLegendariesCheckBox.setEnabled(false);
        tpDontUseLegendariesCheckBox.setSelected(false);
        tpNoEarlyWonderGuardCheckBox.setVisible(true);
        tpNoEarlyWonderGuardCheckBox.setEnabled(false);
        tpNoEarlyWonderGuardCheckBox.setSelected(false);
        tpRandomizeTrainerNamesCheckBox.setVisible(true);
        tpRandomizeTrainerNamesCheckBox.setEnabled(false);
        tpRandomizeTrainerNamesCheckBox.setSelected(false);
        tpRandomizeTrainerClassNamesCheckBox.setVisible(true);
        tpRandomizeTrainerClassNamesCheckBox.setEnabled(false);
        tpRandomizeTrainerClassNamesCheckBox.setSelected(false);
        tpForceFullyEvolvedAtCheckBox.setVisible(true);
        tpForceFullyEvolvedAtCheckBox.setEnabled(false);
        tpForceFullyEvolvedAtCheckBox.setSelected(false);
        tpForceFullyEvolvedAtSlider.setVisible(true);
        tpForceFullyEvolvedAtSlider.setEnabled(false);
        tpForceFullyEvolvedAtSlider.setValue(tpForceFullyEvolvedAtSlider.getMinimum());
        tpPercentageLevelModifierSlider.setVisible(true);
        tpPercentageLevelModifierSlider.setEnabled(false);
        tpPercentageLevelModifierSlider.setValue(0);
        tpPercentageLevelModifierCheckBox.setVisible(true);
        tpPercentageLevelModifierCheckBox.setEnabled(false);
        tpPercentageLevelModifierCheckBox.setSelected(false);

        tpEliteFourUniquePokemonCheckBox.setVisible(true);
        tpEliteFourUniquePokemonCheckBox.setEnabled(false);
        tpEliteFourUniquePokemonCheckBox.setSelected(false);
        tpEliteFourUniquePokemonSpinner.setVisible(true);
        tpEliteFourUniquePokemonSpinner.setEnabled(false);
        tpEliteFourUniquePokemonSpinner.setValue(1);

        tpAllowAlternateFormesCheckBox.setVisible(true);
        tpAllowAlternateFormesCheckBox.setEnabled(false);
        tpAllowAlternateFormesCheckBox.setSelected(false);
        tpSwapMegaEvosCheckBox.setVisible(true);
        tpSwapMegaEvosCheckBox.setEnabled(false);
        tpSwapMegaEvosCheckBox.setSelected(false);
        tpDoubleBattleModeCheckBox.setVisible(true);
        tpDoubleBattleModeCheckBox.setEnabled(false);
        tpDoubleBattleModeCheckBox.setSelected(false);
        tpBossTrainersCheckBox.setVisible(true);
        tpBossTrainersCheckBox.setEnabled(false);
        tpBossTrainersCheckBox.setSelected(false);
        tpImportantTrainersCheckBox.setVisible(true);
        tpImportantTrainersCheckBox.setEnabled(false);
        tpImportantTrainersCheckBox.setSelected(false);
        tpRegularTrainersCheckBox.setVisible(true);
        tpRegularTrainersCheckBox.setEnabled(false);
        tpRegularTrainersCheckBox.setSelected(false);
        tpBossTrainersSpinner.setVisible(true);
        tpBossTrainersSpinner.setEnabled(false);
        tpBossTrainersSpinner.setValue(1);
        tpImportantTrainersSpinner.setVisible(true);
        tpImportantTrainersSpinner.setEnabled(false);
        tpImportantTrainersSpinner.setValue(1);
        tpRegularTrainersSpinner.setVisible(true);
        tpRegularTrainersSpinner.setEnabled(false);
        tpRegularTrainersSpinner.setValue(1);
        tpAdditionalPokemonForLabel.setVisible(true);
        tpHeldItemsLabel.setVisible(true);
        tpBossTrainersItemsCheckBox.setVisible(true);
        tpBossTrainersItemsCheckBox.setEnabled(false);
        tpBossTrainersItemsCheckBox.setSelected(false);
        tpImportantTrainersItemsCheckBox.setVisible(true);
        tpImportantTrainersItemsCheckBox.setEnabled(false);
        tpImportantTrainersItemsCheckBox.setSelected(false);
        tpRegularTrainersItemsCheckBox.setVisible(true);
        tpRegularTrainersItemsCheckBox.setEnabled(false);
        tpRegularTrainersItemsCheckBox.setSelected(false);
        tpConsumableItemsOnlyCheckBox.setVisible(true);
        tpConsumableItemsOnlyCheckBox.setEnabled(false);
        tpConsumableItemsOnlyCheckBox.setSelected(false);
        tpSensibleItemsCheckBox.setVisible(true);
        tpSensibleItemsCheckBox.setEnabled(false);
        tpSensibleItemsCheckBox.setSelected(false);
        tpHighestLevelGetsItemCheckBox.setVisible(true);
        tpHighestLevelGetsItemCheckBox.setEnabled(false);
        tpHighestLevelGetsItemCheckBox.setSelected(false);
        tpRandomShinyTrainerPokemonCheckBox.setVisible(true);
        tpRandomShinyTrainerPokemonCheckBox.setEnabled(false);
        tpBetterMovesetsCheckBox.setVisible(true);
        tpBetterMovesetsCheckBox.setEnabled(false);
        tpBetterMovesetsCheckBox.setSelected(false);
        totpPanel.setVisible(true);
        totpAllyPanel.setVisible(true);
        totpAuraPanel.setVisible(true);
        totpUnchangedRadioButton.setVisible(true);
        totpUnchangedRadioButton.setEnabled(false);
        totpUnchangedRadioButton.setSelected(true);
        totpRandomRadioButton.setVisible(true);
        totpRandomRadioButton.setEnabled(false);
        totpRandomRadioButton.setSelected(false);
        totpRandomSimilarStrengthRadioButton.setVisible(true);
        totpRandomSimilarStrengthRadioButton.setEnabled(false);
        totpRandomSimilarStrengthRadioButton.setSelected(false);
        totpAllyUnchangedRadioButton.setVisible(true);
        totpAllyUnchangedRadioButton.setEnabled(false);
        totpAllyUnchangedRadioButton.setSelected(true);
        totpAllyRandomRadioButton.setVisible(true);
        totpAllyRandomRadioButton.setEnabled(false);
        totpAllyRandomRadioButton.setSelected(false);
        totpAllyRandomSimilarStrengthRadioButton.setVisible(true);
        totpAllyRandomSimilarStrengthRadioButton.setEnabled(false);
        totpAllyRandomSimilarStrengthRadioButton.setSelected(false);
        totpAuraUnchangedRadioButton.setVisible(true);
        totpAuraUnchangedRadioButton.setEnabled(false);
        totpAuraUnchangedRadioButton.setSelected(true);
        totpAuraRandomRadioButton.setVisible(true);
        totpAuraRandomRadioButton.setEnabled(false);
        totpAuraRandomRadioButton.setSelected(false);
        totpAuraRandomSameStrengthRadioButton.setVisible(true);
        totpAuraRandomSameStrengthRadioButton.setEnabled(false);
        totpAuraRandomSameStrengthRadioButton.setSelected(false);
        totpPercentageLevelModifierCheckBox.setVisible(true);
        totpPercentageLevelModifierCheckBox.setEnabled(false);
        totpPercentageLevelModifierCheckBox.setSelected(false);
        totpPercentageLevelModifierSlider.setVisible(true);
        totpPercentageLevelModifierSlider.setEnabled(false);
        totpPercentageLevelModifierSlider.setValue(0);
        totpRandomizeHeldItemsCheckBox.setVisible(true);
        totpRandomizeHeldItemsCheckBox.setEnabled(false);
        totpRandomizeHeldItemsCheckBox.setSelected(false);
        totpAllowAltFormesCheckBox.setVisible(true);
        totpAllowAltFormesCheckBox.setEnabled(false);
        totpAllowAltFormesCheckBox.setSelected(false);
        wpUnchangedRadioButton.setVisible(true);
        wpUnchangedRadioButton.setEnabled(false);
        wpUnchangedRadioButton.setSelected(false);
        wpRandomRadioButton.setVisible(true);
        wpRandomRadioButton.setEnabled(false);
        wpRandomRadioButton.setSelected(false);
        wpArea1To1RadioButton.setVisible(true);
        wpArea1To1RadioButton.setEnabled(false);
        wpArea1To1RadioButton.setSelected(false);
        wpGlobal1To1RadioButton.setVisible(true);
        wpGlobal1To1RadioButton.setEnabled(false);
        wpGlobal1To1RadioButton.setSelected(false);
        wpARNoneRadioButton.setVisible(true);
        wpARNoneRadioButton.setEnabled(false);
        wpARNoneRadioButton.setSelected(false);
        wpARSimilarStrengthRadioButton.setVisible(true);
        wpARSimilarStrengthRadioButton.setEnabled(false);
        wpARSimilarStrengthRadioButton.setSelected(false);
        wpARCatchEmAllModeRadioButton.setVisible(true);
        wpARCatchEmAllModeRadioButton.setEnabled(false);
        wpARCatchEmAllModeRadioButton.setSelected(false);
        wpARTypeThemeAreasRadioButton.setVisible(true);
        wpARTypeThemeAreasRadioButton.setEnabled(false);
        wpARTypeThemeAreasRadioButton.setSelected(false);
        wpUseTimeBasedEncountersCheckBox.setVisible(true);
        wpUseTimeBasedEncountersCheckBox.setEnabled(false);
        wpUseTimeBasedEncountersCheckBox.setSelected(false);
        wpDontUseLegendariesCheckBox.setVisible(true);
        wpDontUseLegendariesCheckBox.setEnabled(false);
        wpDontUseLegendariesCheckBox.setSelected(false);
        wpSetMinimumCatchRateCheckBox.setVisible(true);
        wpSetMinimumCatchRateCheckBox.setEnabled(false);
        wpSetMinimumCatchRateCheckBox.setSelected(false);
        wpRandomizeHeldItemsCheckBox.setVisible(true);
        wpRandomizeHeldItemsCheckBox.setEnabled(false);
        wpRandomizeHeldItemsCheckBox.setSelected(false);
        wpBanBadItemsCheckBox.setVisible(true);
        wpBanBadItemsCheckBox.setEnabled(false);
        wpBanBadItemsCheckBox.setSelected(false);
        wpBalanceShakingGrassPokemonCheckBox.setVisible(true);
        wpBalanceShakingGrassPokemonCheckBox.setEnabled(false);
        wpBalanceShakingGrassPokemonCheckBox.setSelected(false);
        wpPercentageLevelModifierCheckBox.setVisible(true);
        wpPercentageLevelModifierCheckBox.setEnabled(false);
        wpPercentageLevelModifierCheckBox.setSelected(false);
        wpPercentageLevelModifierSlider.setVisible(true);
        wpPercentageLevelModifierSlider.setEnabled(false);
        wpPercentageLevelModifierSlider.setValue(0);
        wpSetMinimumCatchRateSlider.setVisible(true);
        wpSetMinimumCatchRateSlider.setEnabled(false);
        wpSetMinimumCatchRateSlider.setValue(wpSetMinimumCatchRateSlider.getMinimum());
        wpAllowAltFormesCheckBox.setVisible(true);
        wpAllowAltFormesCheckBox.setEnabled(false);
        wpAllowAltFormesCheckBox.setSelected(false);
        tmUnchangedRadioButton.setVisible(true);
        tmUnchangedRadioButton.setEnabled(false);
        tmUnchangedRadioButton.setSelected(false);
        tmRandomRadioButton.setVisible(true);
        tmRandomRadioButton.setEnabled(false);
        tmRandomRadioButton.setSelected(false);
        tmNoGameBreakingMovesCheckBox.setVisible(true);
        tmNoGameBreakingMovesCheckBox.setEnabled(false);
        tmNoGameBreakingMovesCheckBox.setSelected(false);
        tmFullHMCompatibilityCheckBox.setVisible(true);
        tmFullHMCompatibilityCheckBox.setEnabled(false);
        tmFullHMCompatibilityCheckBox.setSelected(false);
        tmLevelupMoveSanityCheckBox.setVisible(true);
        tmLevelupMoveSanityCheckBox.setEnabled(false);
        tmLevelupMoveSanityCheckBox.setSelected(false);
        tmKeepFieldMoveTMsCheckBox.setVisible(true);
        tmKeepFieldMoveTMsCheckBox.setEnabled(false);
        tmKeepFieldMoveTMsCheckBox.setSelected(false);
        tmForceGoodDamagingCheckBox.setVisible(true);
        tmForceGoodDamagingCheckBox.setEnabled(false);
        tmForceGoodDamagingCheckBox.setSelected(false);
        tmForceGoodDamagingSlider.setVisible(true);
        tmForceGoodDamagingSlider.setEnabled(false);
        tmForceGoodDamagingSlider.setValue(tmForceGoodDamagingSlider.getMinimum());
        tmFollowEvolutionsCheckBox.setVisible(true);
        tmFollowEvolutionsCheckBox.setEnabled(false);
        tmFollowEvolutionsCheckBox.setSelected(false);
        thcUnchangedRadioButton.setVisible(true);
        thcUnchangedRadioButton.setEnabled(false);
        thcUnchangedRadioButton.setSelected(false);
        thcRandomPreferSameTypeRadioButton.setVisible(true);
        thcRandomPreferSameTypeRadioButton.setEnabled(false);
        thcRandomPreferSameTypeRadioButton.setSelected(false);
        thcRandomCompletelyRadioButton.setVisible(true);
        thcRandomCompletelyRadioButton.setEnabled(false);
        thcRandomCompletelyRadioButton.setSelected(false);
        thcFullCompatibilityRadioButton.setVisible(true);
        thcFullCompatibilityRadioButton.setEnabled(false);
        thcFullCompatibilityRadioButton.setSelected(false);
        mtUnchangedRadioButton.setVisible(true);
        mtUnchangedRadioButton.setEnabled(false);
        mtUnchangedRadioButton.setSelected(false);
        mtRandomRadioButton.setVisible(true);
        mtRandomRadioButton.setEnabled(false);
        mtRandomRadioButton.setSelected(false);
        mtNoGameBreakingMovesCheckBox.setVisible(true);
        mtNoGameBreakingMovesCheckBox.setEnabled(false);
        mtNoGameBreakingMovesCheckBox.setSelected(false);
        mtLevelupMoveSanityCheckBox.setVisible(true);
        mtLevelupMoveSanityCheckBox.setEnabled(false);
        mtLevelupMoveSanityCheckBox.setSelected(false);
        mtKeepFieldMoveTutorsCheckBox.setVisible(true);
        mtKeepFieldMoveTutorsCheckBox.setEnabled(false);
        mtKeepFieldMoveTutorsCheckBox.setSelected(false);
        mtForceGoodDamagingCheckBox.setVisible(true);
        mtForceGoodDamagingCheckBox.setEnabled(false);
        mtForceGoodDamagingCheckBox.setSelected(false);
        mtForceGoodDamagingSlider.setVisible(true);
        mtForceGoodDamagingSlider.setEnabled(false);
        mtForceGoodDamagingSlider.setValue(mtForceGoodDamagingSlider.getMinimum());
        mtFollowEvolutionsCheckBox.setVisible(true);
        mtFollowEvolutionsCheckBox.setEnabled(false);
        mtFollowEvolutionsCheckBox.setSelected(false);
        mtcUnchangedRadioButton.setVisible(true);
        mtcUnchangedRadioButton.setEnabled(false);
        mtcUnchangedRadioButton.setSelected(false);
        mtcRandomPreferSameTypeRadioButton.setVisible(true);
        mtcRandomPreferSameTypeRadioButton.setEnabled(false);
        mtcRandomPreferSameTypeRadioButton.setSelected(false);
        mtcRandomCompletelyRadioButton.setVisible(true);
        mtcRandomCompletelyRadioButton.setEnabled(false);
        mtcRandomCompletelyRadioButton.setSelected(false);
        mtcFullCompatibilityRadioButton.setVisible(true);
        mtcFullCompatibilityRadioButton.setEnabled(false);
        mtcFullCompatibilityRadioButton.setSelected(false);
        fiUnchangedRadioButton.setVisible(true);
        fiUnchangedRadioButton.setEnabled(false);
        fiUnchangedRadioButton.setSelected(false);
        fiShuffleRadioButton.setVisible(true);
        fiShuffleRadioButton.setEnabled(false);
        fiShuffleRadioButton.setSelected(false);
        fiRandomRadioButton.setVisible(true);
        fiRandomRadioButton.setEnabled(false);
        fiRandomRadioButton.setSelected(false);
        fiRandomEvenDistributionRadioButton.setVisible(true);
        fiRandomEvenDistributionRadioButton.setEnabled(false);
        fiRandomEvenDistributionRadioButton.setSelected(false);
        fiBanBadItemsCheckBox.setVisible(true);
        fiBanBadItemsCheckBox.setEnabled(false);
        fiBanBadItemsCheckBox.setSelected(false);
        shUnchangedRadioButton.setVisible(true);
        shUnchangedRadioButton.setEnabled(false);
        shUnchangedRadioButton.setSelected(false);
        shShuffleRadioButton.setVisible(true);
        shShuffleRadioButton.setEnabled(false);
        shShuffleRadioButton.setSelected(false);
        shRandomRadioButton.setVisible(true);
        shRandomRadioButton.setEnabled(false);
        shRandomRadioButton.setSelected(false);
        shBanOverpoweredShopItemsCheckBox.setVisible(true);
        shBanOverpoweredShopItemsCheckBox.setEnabled(false);
        shBanOverpoweredShopItemsCheckBox.setSelected(false);
        shBanBadItemsCheckBox.setVisible(true);
        shBanBadItemsCheckBox.setEnabled(false);
        shBanBadItemsCheckBox.setSelected(false);
        shBanRegularShopItemsCheckBox.setVisible(true);
        shBanRegularShopItemsCheckBox.setEnabled(false);
        shBanRegularShopItemsCheckBox.setSelected(false);
        shBalanceShopItemPricesCheckBox.setVisible(true);
        shBalanceShopItemPricesCheckBox.setEnabled(false);
        shBalanceShopItemPricesCheckBox.setSelected(false);
        shGuaranteeEvolutionItemsCheckBox.setVisible(true);
        shGuaranteeEvolutionItemsCheckBox.setEnabled(false);
        shGuaranteeEvolutionItemsCheckBox.setSelected(false);
        shGuaranteeXItemsCheckBox.setVisible(true);
        shGuaranteeXItemsCheckBox.setEnabled(false);
        shGuaranteeXItemsCheckBox.setSelected(false);
        puUnchangedRadioButton.setVisible(true);
        puUnchangedRadioButton.setEnabled(false);
        puUnchangedRadioButton.setSelected(false);
        puRandomRadioButton.setVisible(true);
        puRandomRadioButton.setEnabled(false);
        puRandomRadioButton.setSelected(false);
        puBanBadItemsCheckBox.setVisible(true);
        puBanBadItemsCheckBox.setEnabled(false);
        puBanBadItemsCheckBox.setSelected(false);
        miscBWExpPatchCheckBox.setVisible(true);
        miscBWExpPatchCheckBox.setEnabled(false);
        miscBWExpPatchCheckBox.setSelected(false);
        miscNerfXAccuracyCheckBox.setVisible(true);
        miscNerfXAccuracyCheckBox.setEnabled(false);
        miscNerfXAccuracyCheckBox.setSelected(false);
        miscFixCritRateCheckBox.setVisible(true);
        miscFixCritRateCheckBox.setEnabled(false);
        miscFixCritRateCheckBox.setSelected(false);
        miscFastestTextCheckBox.setVisible(true);
        miscFastestTextCheckBox.setEnabled(false);
        miscFastestTextCheckBox.setSelected(false);
        miscRunningShoesIndoorsCheckBox.setVisible(true);
        miscRunningShoesIndoorsCheckBox.setEnabled(false);
        miscRunningShoesIndoorsCheckBox.setSelected(false);
        miscRandomizePCPotionCheckBox.setVisible(true);
        miscRandomizePCPotionCheckBox.setEnabled(false);
        miscRandomizePCPotionCheckBox.setSelected(false);
        miscAllowPikachuEvolutionCheckBox.setVisible(true);
        miscAllowPikachuEvolutionCheckBox.setEnabled(false);
        miscAllowPikachuEvolutionCheckBox.setSelected(false);
        miscGiveNationalDexAtCheckBox.setVisible(true);
        miscGiveNationalDexAtCheckBox.setEnabled(false);
        miscGiveNationalDexAtCheckBox.setSelected(false);
        miscUpdateTypeEffectivenessCheckBox.setVisible(true);
        miscUpdateTypeEffectivenessCheckBox.setEnabled(false);
        miscUpdateTypeEffectivenessCheckBox.setSelected(false);
        miscLowerCasePokemonNamesCheckBox.setVisible(true);
        miscLowerCasePokemonNamesCheckBox.setEnabled(false);
        miscLowerCasePokemonNamesCheckBox.setSelected(false);
        miscRandomizeCatchingTutorialCheckBox.setVisible(true);
        miscRandomizeCatchingTutorialCheckBox.setEnabled(false);
        miscRandomizeCatchingTutorialCheckBox.setSelected(false);
        miscBanLuckyEggCheckBox.setVisible(true);
        miscBanLuckyEggCheckBox.setEnabled(false);
        miscBanLuckyEggCheckBox.setSelected(false);
        miscNoFreeLuckyEggCheckBox.setVisible(true);
        miscNoFreeLuckyEggCheckBox.setEnabled(false);
        miscNoFreeLuckyEggCheckBox.setSelected(false);
        miscBanBigMoneyManiacCheckBox.setVisible(true);
        miscBanBigMoneyManiacCheckBox.setEnabled(false);
        miscBanBigMoneyManiacCheckBox.setSelected(false);
        mtNoExistLabel.setVisible(false);
        mtNoneAvailableLabel.setVisible(false);

        liveTweaksPanel.setVisible(false);
        miscTweaksPanel.setVisible(true);
    }

    private void romLoaded() {

        try {
            int pokemonGeneration = romHandler.generationOfPokemon();

            setRomNameLabel();
            romCodeLabel.setText(romHandler.getROMCode());
            romSupportLabel.setText(bundle.getString("GUI.romSupportPrefix") + " "
                    + this.romHandler.getSupportLevel());

            if (!romHandler.isRomValid()) {
                romNameLabel.setForeground(Color.RED);
                romCodeLabel.setForeground(Color.RED);
                romSupportLabel.setForeground(Color.RED);
                romSupportLabel.setText("<html>" + bundle.getString("GUI.romSupportPrefix") + " <b>Unofficial ROM</b>");
                showInvalidRomPopup();
            } else {
                romNameLabel.setForeground(Color.BLACK);
                romCodeLabel.setForeground(Color.BLACK);
                romSupportLabel.setForeground(Color.BLACK);
            }

            limitPokemonCheckBox.setVisible(true);
            limitPokemonCheckBox.setEnabled(true);
            limitPokemonButton.setVisible(true);

            noIrregularAltFormesCheckBox.setVisible(pokemonGeneration >= 4);
            noIrregularAltFormesCheckBox.setEnabled(pokemonGeneration >= 4);

            raceModeCheckBox.setEnabled(true);

            loadSettingsButton.setEnabled(true);
            saveSettingsButton.setEnabled(true);

            // Pokemon Traits

            // Pokemon Base Statistics
            pbsUnchangedRadioButton.setEnabled(true);
            pbsUnchangedRadioButton.setSelected(true);
            pbsShuffleRadioButton.setEnabled(true);
            pbsRandomRadioButton.setEnabled(true);

            pbsStandardizeEXPCurvesCheckBox.setEnabled(true);
            pbsLegendariesSlowRadioButton.setSelected(true);
            pbsUpdateBaseStatsCheckBox.setEnabled(pokemonGeneration < 8);
            pbsFollowMegaEvosCheckBox.setVisible(romHandler.hasMegaEvolutions());
            pbsUpdateComboBox.setVisible(pokemonGeneration < 8);
            ExpCurve[] expCurves = getEXPCurvesForGeneration(pokemonGeneration);
            String[] expCurveNames = new String[expCurves.length];
            for (int i = 0; i < expCurves.length; i++) {
                expCurveNames[i] = expCurves[i].toString();
            }
            pbsEXPCurveComboBox.setModel(new DefaultComboBoxModel<>(expCurveNames));
            pbsEXPCurveComboBox.setSelectedIndex(0);

            // Pokemon Types
            ptUnchangedRadioButton.setEnabled(true);
            ptUnchangedRadioButton.setSelected(true);
            ptRandomFollowEvolutionsRadioButton.setEnabled(true);
            ptRandomCompletelyRadioButton.setEnabled(true);
            ptFollowMegaEvosCheckBox.setVisible(romHandler.hasMegaEvolutions());
            ptIsDualTypeCheckBox.setEnabled(false);

            // Pokemon Abilities
            if (pokemonGeneration >= 3) {
                paUnchangedRadioButton.setEnabled(true);
                paUnchangedRadioButton.setSelected(true);
                paRandomRadioButton.setEnabled(true);

                paAllowWonderGuardCheckBox.setEnabled(false);
                paFollowEvolutionsCheckBox.setEnabled(false);
                paTrappingAbilitiesCheckBox.setEnabled(false);
                paNegativeAbilitiesCheckBox.setEnabled(false);
                paBadAbilitiesCheckBox.setEnabled(false);
                paFollowMegaEvosCheckBox.setVisible(romHandler.hasMegaEvolutions());
                paWeighDuplicatesTogetherCheckBox.setEnabled(false);
                paEnsureTwoAbilitiesCheckbox.setEnabled(false);
            } else {
                pokemonAbilitiesPanel.setVisible(false);
            }

            // Pokemon Evolutions
            peUnchangedRadioButton.setEnabled(true);
            peUnchangedRadioButton.setSelected(true);
            peRandomRadioButton.setEnabled(true);
            peRandomEveryLevelRadioButton.setVisible(pokemonGeneration >= 3);
            peRandomEveryLevelRadioButton.setEnabled(pokemonGeneration >= 3);
            peChangeImpossibleEvosCheckBox.setEnabled(true);
            peMakeEvolutionsEasierCheckBox.setEnabled(true);
            peRemoveTimeBasedEvolutionsCheckBox.setEnabled(true);
            peAllowAltFormesCheckBox.setVisible(pokemonGeneration >= 7);

            // Starters, Statics & Trades

            // Starter Pokemon
            spUnchangedRadioButton.setEnabled(true);
            spUnchangedRadioButton.setSelected(true);

            spCustomRadioButton.setEnabled(true);
            spRandomCompletelyRadioButton.setEnabled(true);
            spRandomTwoEvosRadioButton.setEnabled(true);
            spAllowAltFormesCheckBox.setVisible(romHandler.hasStarterAltFormes());
            if (romHandler.isYellow()) {
                spComboBox3.setVisible(false);
            }
            populateDropdowns();

            boolean hasStarterHeldItems = (pokemonGeneration == 2 || pokemonGeneration == 3);
            spRandomizeStarterHeldItemsCheckBox.setEnabled(hasStarterHeldItems);
            spRandomizeStarterHeldItemsCheckBox.setVisible(hasStarterHeldItems);
            spBanBadItemsCheckBox.setEnabled(false);
            spBanBadItemsCheckBox.setVisible(hasStarterHeldItems);

            stpUnchangedRadioButton.setEnabled(true);
            stpUnchangedRadioButton.setSelected(true);
            if (romHandler.canChangeStaticPokemon()) {
                stpSwapLegendariesSwapStandardsRadioButton.setEnabled(true);
                stpRandomCompletelyRadioButton.setEnabled(true);
                stpRandomSimilarStrengthRadioButton.setEnabled(true);
                stpLimitMainGameLegendariesCheckBox.setVisible(romHandler.hasMainGameLegendaries());
                stpLimitMainGameLegendariesCheckBox.setEnabled(false);
                stpAllowAltFormesCheckBox.setVisible(romHandler.hasStaticAltFormes());
                stpSwapMegaEvosCheckBox.setVisible(pokemonGeneration == 6 && !romHandler.forceSwapStaticMegaEvos());
                stpPercentageLevelModifierCheckBox.setVisible(true);
                stpPercentageLevelModifierCheckBox.setEnabled(true);
                stpPercentageLevelModifierSlider.setVisible(true);
                stpPercentageLevelModifierSlider.setEnabled(false);
                stpFixMusicCheckBox.setVisible(romHandler.hasStaticMusicFix());
                stpFixMusicCheckBox.setEnabled(false);
            } else {
                stpSwapLegendariesSwapStandardsRadioButton.setVisible(false);
                stpRandomCompletelyRadioButton.setVisible(false);
                stpRandomSimilarStrengthRadioButton.setVisible(false);
                stpRandomize600BSTCheckBox.setVisible(false);
                stpLimitMainGameLegendariesCheckBox.setVisible(false);
                stpPercentageLevelModifierCheckBox.setVisible(false);
                stpPercentageLevelModifierSlider.setVisible(false);
                stpFixMusicCheckBox.setVisible(false);
            }

            igtUnchangedRadioButton.setEnabled(true);
            igtUnchangedRadioButton.setSelected(true);
            igtRandomizeGivenPokemonOnlyRadioButton.setEnabled(true);
            igtRandomizeBothRequestedGivenRadioButton.setEnabled(true);

            igtRandomizeNicknamesCheckBox.setEnabled(false);
            igtRandomizeOTsCheckBox.setEnabled(false);
            igtRandomizeIVsCheckBox.setEnabled(false);
            igtRandomizeItemsCheckBox.setEnabled(false);

            if (pokemonGeneration == 1) {
                igtRandomizeOTsCheckBox.setVisible(false);
                igtRandomizeIVsCheckBox.setVisible(false);
                igtRandomizeItemsCheckBox.setVisible(false);
            }

            // Move Data
            mdRandomizeMovePowerCheckBox.setEnabled(true);
            mdRandomizeMoveAccuracyCheckBox.setEnabled(true);
            mdRandomizeMovePPCheckBox.setEnabled(true);
            mdRandomizeMoveTypesCheckBox.setEnabled(true);
            mdRandomizeMoveCategoryCheckBox.setEnabled(romHandler.hasPhysicalSpecialSplit());
            mdRandomizeMoveCategoryCheckBox.setVisible(romHandler.hasPhysicalSpecialSplit());
            mdUpdateMovesCheckBox.setEnabled(pokemonGeneration < 8);
            mdUpdateMovesCheckBox.setVisible(pokemonGeneration < 8);

            // Pokemon Movesets
            pmsUnchangedRadioButton.setEnabled(true);
            pmsUnchangedRadioButton.setSelected(true);
            pmsRandomPreferringSameTypeRadioButton.setEnabled(true);
            pmsRandomCompletelyRadioButton.setEnabled(true);
            pmsMetronomeOnlyModeRadioButton.setEnabled(true);

            pmsGuaranteedLevel1MovesCheckBox.setVisible(romHandler.supportsFourStartingMoves());
            pmsGuaranteedLevel1MovesSlider.setVisible(romHandler.supportsFourStartingMoves());
            pmsEvolutionMovesCheckBox.setVisible(pokemonGeneration >= 7);

            tpComboBox.setEnabled(true);
            tpAllowAlternateFormesCheckBox.setVisible(romHandler.hasFunctionalFormes());
            tpForceFullyEvolvedAtCheckBox.setEnabled(true);
            tpPercentageLevelModifierCheckBox.setEnabled(true);
            tpSwapMegaEvosCheckBox.setVisible(romHandler.hasMegaEvolutions());
            tpDoubleBattleModeCheckBox.setVisible(pokemonGeneration >= 3);

            boolean additionalPokemonAvailable = pokemonGeneration >= 3;

            tpAdditionalPokemonForLabel.setVisible(additionalPokemonAvailable);
            tpBossTrainersCheckBox.setVisible(additionalPokemonAvailable);
            tpBossTrainersCheckBox.setEnabled(false);
            tpBossTrainersSpinner.setVisible(additionalPokemonAvailable);
            tpImportantTrainersCheckBox.setVisible(additionalPokemonAvailable);
            tpImportantTrainersCheckBox.setEnabled(false);
            tpImportantTrainersSpinner.setVisible(additionalPokemonAvailable);
            tpRegularTrainersCheckBox.setVisible(additionalPokemonAvailable);
            tpRegularTrainersCheckBox.setEnabled(false);
            tpRegularTrainersSpinner.setVisible(additionalPokemonAvailable);

            boolean trainersHeldItemSupport = pokemonGeneration >= 3;
            tpHeldItemsLabel.setVisible(trainersHeldItemSupport);
            tpBossTrainersItemsCheckBox.setVisible(trainersHeldItemSupport);
            tpBossTrainersItemsCheckBox.setEnabled(false);
            tpImportantTrainersItemsCheckBox.setVisible(trainersHeldItemSupport);
            tpImportantTrainersItemsCheckBox.setEnabled(false);
            tpRegularTrainersItemsCheckBox.setVisible(trainersHeldItemSupport);
            tpRegularTrainersItemsCheckBox.setEnabled(false);
            tpConsumableItemsOnlyCheckBox.setVisible(trainersHeldItemSupport);
            tpConsumableItemsOnlyCheckBox.setEnabled(false);
            tpSensibleItemsCheckBox.setVisible(trainersHeldItemSupport);
            tpSensibleItemsCheckBox.setEnabled(false);
            tpHighestLevelGetsItemCheckBox.setVisible(trainersHeldItemSupport);
            tpHighestLevelGetsItemCheckBox.setEnabled(false);

            tpEliteFourUniquePokemonCheckBox.setVisible(pokemonGeneration >= 3);
            tpEliteFourUniquePokemonSpinner.setVisible(pokemonGeneration >= 3);

            tpRandomizeTrainerNamesCheckBox.setEnabled(true);
            tpRandomizeTrainerClassNamesCheckBox.setEnabled(true);
            tpNoEarlyWonderGuardCheckBox.setVisible(pokemonGeneration >= 3);
            tpRandomShinyTrainerPokemonCheckBox.setVisible(pokemonGeneration >= 7);
            tpBetterMovesetsCheckBox.setVisible(pokemonGeneration >= 3);

            totpPanel.setVisible(pokemonGeneration == 7);
            if (totpPanel.isVisible()) {
                totpUnchangedRadioButton.setEnabled(true);
                totpRandomRadioButton.setEnabled(true);
                totpRandomSimilarStrengthRadioButton.setEnabled(true);

                totpAllyPanel.setVisible(pokemonGeneration == 7);
                totpAllyUnchangedRadioButton.setEnabled(true);
                totpAllyRandomRadioButton.setEnabled(true);
                totpAllyRandomSimilarStrengthRadioButton.setEnabled(true);

                totpAuraPanel.setVisible(pokemonGeneration == 7);
                totpAuraUnchangedRadioButton.setEnabled(true);
                totpAuraRandomRadioButton.setEnabled(true);
                totpAuraRandomSameStrengthRadioButton.setEnabled(true);

                totpRandomizeHeldItemsCheckBox.setEnabled(true);
                totpAllowAltFormesCheckBox.setEnabled(false);
                totpPercentageLevelModifierCheckBox.setEnabled(true);
                totpPercentageLevelModifierSlider.setEnabled(false);
            }

            // Wild Pokemon
            wpUnchangedRadioButton.setEnabled(true);
            wpUnchangedRadioButton.setSelected(true);
            wpRandomRadioButton.setEnabled(true);
            wpArea1To1RadioButton.setEnabled(true);
            wpGlobal1To1RadioButton.setEnabled(true);

            wpARNoneRadioButton.setSelected(true);

            wpUseTimeBasedEncountersCheckBox.setVisible(romHandler.hasTimeBasedEncounters());
            wpSetMinimumCatchRateCheckBox.setEnabled(true);
            wpRandomizeHeldItemsCheckBox.setEnabled(true);
            wpRandomizeHeldItemsCheckBox.setVisible(pokemonGeneration != 1);
            wpBanBadItemsCheckBox.setVisible(pokemonGeneration != 1);
            wpBalanceShakingGrassPokemonCheckBox.setVisible(pokemonGeneration == 5);
            wpPercentageLevelModifierCheckBox.setEnabled(true);
            wpAllowAltFormesCheckBox.setVisible(romHandler.hasWildAltFormes());

            tmUnchangedRadioButton.setEnabled(true);
            tmUnchangedRadioButton.setSelected(true);
            tmRandomRadioButton.setEnabled(true);
            tmFullHMCompatibilityCheckBox.setVisible(pokemonGeneration < 7);
            if (tmFullHMCompatibilityCheckBox.isVisible()) {
                tmFullHMCompatibilityCheckBox.setEnabled(true);
            }

            thcUnchangedRadioButton.setEnabled(true);
            thcUnchangedRadioButton.setSelected(true);
            thcRandomPreferSameTypeRadioButton.setEnabled(true);
            thcRandomCompletelyRadioButton.setEnabled(true);
            thcFullCompatibilityRadioButton.setEnabled(true);

            if (romHandler.hasMoveTutors()) {
                mtMovesPanel.setVisible(true);
                mtCompatPanel.setVisible(true);
                mtNoExistLabel.setVisible(false);

                mtUnchangedRadioButton.setEnabled(true);
                mtUnchangedRadioButton.setSelected(true);
                mtRandomRadioButton.setEnabled(true);

                mtcUnchangedRadioButton.setEnabled(true);
                mtcUnchangedRadioButton.setSelected(true);
                mtcRandomPreferSameTypeRadioButton.setEnabled(true);
                mtcRandomCompletelyRadioButton.setEnabled(true);
                mtcFullCompatibilityRadioButton.setEnabled(true);
            } else {
                mtMovesPanel.setVisible(false);
                mtCompatPanel.setVisible(false);
                mtNoExistLabel.setVisible(true);
            }

            fiUnchangedRadioButton.setEnabled(true);
            fiUnchangedRadioButton.setSelected(true);
            fiShuffleRadioButton.setEnabled(true);
            fiRandomRadioButton.setEnabled(true);
            fiRandomEvenDistributionRadioButton.setEnabled(true);

            shopItemsPanel.setVisible(romHandler.hasShopRandomization());
            shUnchangedRadioButton.setEnabled(true);
            shUnchangedRadioButton.setSelected(true);
            shShuffleRadioButton.setEnabled(true);
            shRandomRadioButton.setEnabled(true);

            pickupItemsPanel.setVisible(romHandler.abilitiesPerPokemon() > 0);
            puUnchangedRadioButton.setEnabled(true);
            puUnchangedRadioButton.setSelected(true);
            puRandomRadioButton.setEnabled(true);

            int mtsAvailable = romHandler.miscTweaksAvailable();
            int mtCount = MiscTweak.allTweaks.size();
            List<JCheckBox> usableCheckBoxes = new ArrayList<>();

            for (int mti = 0; mti < mtCount; mti++) {
                MiscTweak mt = MiscTweak.allTweaks.get(mti);
                JCheckBox mtCB = tweakCheckBoxes.get(mti);
                mtCB.setSelected(false);
                if ((mtsAvailable & mt.getValue()) != 0) {
                    mtCB.setVisible(true);
                    mtCB.setEnabled(true);
                    usableCheckBoxes.add(mtCB);
                } else {
                    mtCB.setVisible(false);
                    mtCB.setEnabled(false);
                }
            }

            if (usableCheckBoxes.size() > 0) {
                setTweaksPanel(usableCheckBoxes);
                //tabbedPane1.setComponentAt(7,makeTweaksLayout(usableCheckBoxes));
                //miscTweaksPanel.setLayout(makeTweaksLayout(usableCheckBoxes));
            } else {
                mtNoneAvailableLabel.setVisible(true);
                liveTweaksPanel.setVisible(false);
                miscTweaksPanel.setVisible(true);
                //miscTweaksPanel.setLayout(noTweaksLayout);
            }

            if (romHandler.generationOfPokemon() < 6) {
                applyGameUpdateMenuItem.setVisible(false);
            } else {
                applyGameUpdateMenuItem.setVisible(true);
            }

            if (romHandler.hasGameUpdateLoaded()) {
                removeGameUpdateMenuItem.setVisible(true);
            } else {
                removeGameUpdateMenuItem.setVisible(false);
            }

            gameMascotLabel.setIcon(makeMascotIcon());

            if (romHandler instanceof AbstractDSRomHandler) {
                ((AbstractDSRomHandler) romHandler).closeInnerRom();
            } else if (romHandler instanceof Abstract3DSRomHandler) {
                ((Abstract3DSRomHandler) romHandler).closeInnerRom();
            }
        } catch (Exception e) {
            attemptToLogException(e, "GUI.processFailed","GUI.processFailedNoLog", null, null);
            romHandler = null;
            initialState();
        }
    }

    private void setRomNameLabel() {
        if (romHandler.hasGameUpdateLoaded()) {
            romNameLabel.setText(romHandler.getROMName() + " (" + romHandler.getGameUpdateVersion() + ")");
        } else {
            romNameLabel.setText(romHandler.getROMName());
        }
    }

    private void setTweaksPanel(List<JCheckBox> usableCheckBoxes) {
        mtNoneAvailableLabel.setVisible(false);
        miscTweaksPanel.setVisible(false);
        baseTweaksPanel.remove(liveTweaksPanel);
        makeTweaksLayout(usableCheckBoxes);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0.1;
        c.weighty = 0.1;
        c.gridx = 1;
        c.gridy = 1;
        baseTweaksPanel.add(liveTweaksPanel,c);
        liveTweaksPanel.setVisible(true);
    }

    private void enableOrDisableSubControls() {

        if (limitPokemonCheckBox.isSelected()) {
            limitPokemonButton.setEnabled(true);
        } else {
            limitPokemonButton.setEnabled(false);
        }

        boolean followEvolutionControlsEnabled = !peRandomEveryLevelRadioButton.isSelected();
        boolean followMegaEvolutionControlsEnabled = !(peRandomEveryLevelRadioButton.isSelected() && !noIrregularAltFormesCheckBox.isSelected() && peAllowAltFormesCheckBox.isSelected());

        if (peRandomEveryLevelRadioButton.isSelected()) {
            // If Evolve Every Level is enabled, unselect all "Follow Evolutions" controls
            pbsFollowEvolutionsCheckBox.setSelected(false);
            ptRandomFollowEvolutionsRadioButton.setEnabled(false);
            if (ptRandomFollowEvolutionsRadioButton.isSelected()) {
                ptRandomFollowEvolutionsRadioButton.setSelected(false);
                ptRandomCompletelyRadioButton.setSelected(true);
            }
            spRandomTwoEvosRadioButton.setEnabled(false);
            if (spRandomTwoEvosRadioButton.isSelected()) {
                spRandomTwoEvosRadioButton.setSelected(false);
                spRandomCompletelyRadioButton.setSelected(true);
            }
            paFollowEvolutionsCheckBox.setSelected(false);
            tmFollowEvolutionsCheckBox.setSelected(false);
            mtFollowEvolutionsCheckBox.setSelected(false);

            // If the Follow Mega Evolution controls should be disabled, deselect them here too
            if (!followMegaEvolutionControlsEnabled) {
                pbsFollowMegaEvosCheckBox.setSelected(false);
                ptFollowMegaEvosCheckBox.setSelected(false);
                paFollowMegaEvosCheckBox.setSelected(false);
            }

            // Also disable/unselect all the settings that make evolutions easier/possible,
            // since they aren't relevant in this scenario at all.
            peChangeImpossibleEvosCheckBox.setEnabled(false);
            peChangeImpossibleEvosCheckBox.setSelected(false);
            peMakeEvolutionsEasierCheckBox.setEnabled(false);
            peMakeEvolutionsEasierCheckBox.setSelected(false);
            peRemoveTimeBasedEvolutionsCheckBox.setEnabled(false);
            peRemoveTimeBasedEvolutionsCheckBox.setSelected(false);

            // Disable "Force Fully Evolved" Trainer Pokemon
            tpForceFullyEvolvedAtCheckBox.setSelected(false);
            tpForceFullyEvolvedAtCheckBox.setEnabled(false);
            tpForceFullyEvolvedAtSlider.setEnabled(false);
            tpForceFullyEvolvedAtSlider.setValue(tpForceFullyEvolvedAtSlider.getMinimum());
        } else {
            // All other "Follow Evolutions" controls get properly set/unset below
            // except this one, so manually enable it again.
            ptRandomFollowEvolutionsRadioButton.setEnabled(true);
            spRandomTwoEvosRadioButton.setEnabled(true);

            // The controls that make evolutions easier/possible, however,
            // need to all be manually re-enabled.
            peChangeImpossibleEvosCheckBox.setEnabled(true);
            peMakeEvolutionsEasierCheckBox.setEnabled(true);
            peRemoveTimeBasedEvolutionsCheckBox.setEnabled(true);

            // Re-enable "Force Fully Evolved" Trainer Pokemon
            tpForceFullyEvolvedAtCheckBox.setEnabled(true);
        }

        if (pbsUnchangedRadioButton.isSelected()) {
            pbsFollowEvolutionsCheckBox.setEnabled(false);
            pbsFollowEvolutionsCheckBox.setSelected(false);
            pbsFollowMegaEvosCheckBox.setEnabled(false);
            pbsFollowMegaEvosCheckBox.setSelected(false);
        } else {
            pbsFollowEvolutionsCheckBox.setEnabled(followEvolutionControlsEnabled);
            pbsFollowMegaEvosCheckBox.setEnabled(followMegaEvolutionControlsEnabled);
        }

        if (pbsRandomRadioButton.isSelected()) {
            if (pbsFollowEvolutionsCheckBox.isSelected() || pbsFollowMegaEvosCheckBox.isSelected()) {
                pbsAssignEvoStatsRandomlyCheckBox.setEnabled(true);
            } else {
                pbsAssignEvoStatsRandomlyCheckBox.setEnabled(false);
                pbsAssignEvoStatsRandomlyCheckBox.setSelected(false);
            }
        } else {
            pbsAssignEvoStatsRandomlyCheckBox.setEnabled(false);
            pbsAssignEvoStatsRandomlyCheckBox.setSelected(false);
        }

        if (pbsStandardizeEXPCurvesCheckBox.isSelected()) {
            pbsLegendariesSlowRadioButton.setEnabled(true);
            pbsStrongLegendariesSlowRadioButton.setEnabled(true);
            pbsAllMediumFastRadioButton.setEnabled(true);
            pbsEXPCurveComboBox.setEnabled(true);
        } else {
            pbsLegendariesSlowRadioButton.setEnabled(false);
            pbsLegendariesSlowRadioButton.setSelected(true);
            pbsStrongLegendariesSlowRadioButton.setEnabled(false);
            pbsAllMediumFastRadioButton.setEnabled(false);
            pbsEXPCurveComboBox.setEnabled(false);
        }

        if (pbsUpdateBaseStatsCheckBox.isSelected()) {
            pbsUpdateComboBox.setEnabled(true);
        } else {
            pbsUpdateComboBox.setEnabled(false);
        }

        if (ptUnchangedRadioButton.isSelected()) {
            ptFollowMegaEvosCheckBox.setEnabled(false);
            ptFollowMegaEvosCheckBox.setSelected(false);
            ptIsDualTypeCheckBox.setEnabled(false);
            ptIsDualTypeCheckBox.setSelected(false);
        } else {
            ptFollowMegaEvosCheckBox.setEnabled(followMegaEvolutionControlsEnabled);
            ptIsDualTypeCheckBox.setEnabled(true);
        }

        if (paRandomRadioButton.isSelected()) {
            paAllowWonderGuardCheckBox.setEnabled(true);
            paFollowEvolutionsCheckBox.setEnabled(followEvolutionControlsEnabled);
            paFollowMegaEvosCheckBox.setEnabled(followMegaEvolutionControlsEnabled);
            paTrappingAbilitiesCheckBox.setEnabled(true);
            paNegativeAbilitiesCheckBox.setEnabled(true);
            paBadAbilitiesCheckBox.setEnabled(true);
            paWeighDuplicatesTogetherCheckBox.setEnabled(true);
            paEnsureTwoAbilitiesCheckbox.setEnabled(true);
        } else {
            paAllowWonderGuardCheckBox.setEnabled(false);
            paAllowWonderGuardCheckBox.setSelected(false);
            paFollowEvolutionsCheckBox.setEnabled(false);
            paFollowEvolutionsCheckBox.setSelected(false);
            paTrappingAbilitiesCheckBox.setEnabled(false);
            paTrappingAbilitiesCheckBox.setSelected(false);
            paNegativeAbilitiesCheckBox.setEnabled(false);
            paNegativeAbilitiesCheckBox.setSelected(false);
            paBadAbilitiesCheckBox.setEnabled(false);
            paBadAbilitiesCheckBox.setSelected(false);
            paFollowMegaEvosCheckBox.setEnabled(false);
            paFollowMegaEvosCheckBox.setSelected(false);
            paWeighDuplicatesTogetherCheckBox.setEnabled(false);
            paWeighDuplicatesTogetherCheckBox.setSelected(false);
            paEnsureTwoAbilitiesCheckbox.setEnabled(false);
            paEnsureTwoAbilitiesCheckbox.setSelected(false);
        }

        if (peRandomRadioButton.isSelected()) {
            peSimilarStrengthCheckBox.setEnabled(true);
            peSameTypingCheckBox.setEnabled(true);
            peLimitEvolutionsToThreeCheckBox.setEnabled(true);
            peForceChangeCheckBox.setEnabled(true);
            peAllowAltFormesCheckBox.setEnabled(true);
        } else if (peRandomEveryLevelRadioButton.isSelected()) {
            peSimilarStrengthCheckBox.setEnabled(false);
            peSimilarStrengthCheckBox.setSelected(false);
            peSameTypingCheckBox.setEnabled(true);
            peLimitEvolutionsToThreeCheckBox.setEnabled(false);
            peLimitEvolutionsToThreeCheckBox.setSelected(false);
            peForceChangeCheckBox.setEnabled(true);
            peAllowAltFormesCheckBox.setEnabled(true);
        } else {
            peSimilarStrengthCheckBox.setEnabled(false);
            peSimilarStrengthCheckBox.setSelected(false);
            peSameTypingCheckBox.setEnabled(false);
            peSameTypingCheckBox.setSelected(false);
            peLimitEvolutionsToThreeCheckBox.setEnabled(false);
            peLimitEvolutionsToThreeCheckBox.setSelected(false);
            peForceChangeCheckBox.setEnabled(false);
            peForceChangeCheckBox.setSelected(false);
            peAllowAltFormesCheckBox.setEnabled(false);
            peAllowAltFormesCheckBox.setSelected(false);
        }

        boolean spCustomStatus = spCustomRadioButton.isSelected();
        spComboBox1.setEnabled(spCustomStatus);
        spComboBox2.setEnabled(spCustomStatus);
        spComboBox3.setEnabled(spCustomStatus);

        if (spUnchangedRadioButton.isSelected()) {
            spAllowAltFormesCheckBox.setEnabled(false);
            spAllowAltFormesCheckBox.setSelected(false);
        } else {
            spAllowAltFormesCheckBox.setEnabled(true);
        }

        if (spRandomizeStarterHeldItemsCheckBox.isSelected()) {
            spBanBadItemsCheckBox.setEnabled(true);
        } else {
            spBanBadItemsCheckBox.setEnabled(false);
            spBanBadItemsCheckBox.setSelected(false);
        }

        if (stpUnchangedRadioButton.isSelected()) {
            stpRandomize600BSTCheckBox.setEnabled(false);
            stpRandomize600BSTCheckBox.setSelected(false);
            stpAllowAltFormesCheckBox.setEnabled(false);
            stpAllowAltFormesCheckBox.setSelected(false);
            stpSwapMegaEvosCheckBox.setEnabled(false);
            stpSwapMegaEvosCheckBox.setSelected(false);
            stpFixMusicCheckBox.setEnabled(false);
            stpFixMusicCheckBox.setSelected(false);
        } else {
            stpRandomize600BSTCheckBox.setEnabled(true);
            stpAllowAltFormesCheckBox.setEnabled(true);
            stpSwapMegaEvosCheckBox.setEnabled(true);
            stpFixMusicCheckBox.setEnabled(true);
        }

        if (stpRandomSimilarStrengthRadioButton.isSelected()) {
            stpLimitMainGameLegendariesCheckBox.setEnabled(stpLimitMainGameLegendariesCheckBox.isVisible());
        } else {
            stpLimitMainGameLegendariesCheckBox.setEnabled(false);
            stpLimitMainGameLegendariesCheckBox.setSelected(false);
        }

        if (stpPercentageLevelModifierCheckBox.isSelected()) {
            stpPercentageLevelModifierSlider.setEnabled(true);
        } else {
            stpPercentageLevelModifierSlider.setEnabled(false);
            stpPercentageLevelModifierSlider.setValue(0);
        }

        if (igtUnchangedRadioButton.isSelected()) {
            igtRandomizeItemsCheckBox.setEnabled(false);
            igtRandomizeItemsCheckBox.setSelected(false);
            igtRandomizeIVsCheckBox.setEnabled(false);
            igtRandomizeIVsCheckBox.setSelected(false);
            igtRandomizeNicknamesCheckBox.setEnabled(false);
            igtRandomizeNicknamesCheckBox.setSelected(false);
            igtRandomizeOTsCheckBox.setEnabled(false);
            igtRandomizeOTsCheckBox.setSelected(false);
        } else {
            igtRandomizeItemsCheckBox.setEnabled(true);
            igtRandomizeIVsCheckBox.setEnabled(true);
            igtRandomizeNicknamesCheckBox.setEnabled(true);
            igtRandomizeOTsCheckBox.setEnabled(true);
        }

        if (mdUpdateMovesCheckBox.isSelected()) {
            mdUpdateComboBox.setEnabled(true);
        } else {
            mdUpdateComboBox.setEnabled(false);
        }

        if (pmsMetronomeOnlyModeRadioButton.isSelected() || pmsUnchangedRadioButton.isSelected()) {
            pmsGuaranteedLevel1MovesCheckBox.setEnabled(false);
            pmsGuaranteedLevel1MovesCheckBox.setSelected(false);
            pmsForceGoodDamagingCheckBox.setEnabled(false);
            pmsForceGoodDamagingCheckBox.setSelected(false);
            pmsReorderDamagingMovesCheckBox.setEnabled(false);
            pmsReorderDamagingMovesCheckBox.setSelected(false);
            pmsNoGameBreakingMovesCheckBox.setEnabled(false);
            pmsNoGameBreakingMovesCheckBox.setSelected(false);
            pmsEvolutionMovesCheckBox.setEnabled(false);
            pmsEvolutionMovesCheckBox.setSelected(false);
        } else {
            pmsGuaranteedLevel1MovesCheckBox.setEnabled(true);
            pmsForceGoodDamagingCheckBox.setEnabled(true);
            pmsReorderDamagingMovesCheckBox.setEnabled(true);
            pmsNoGameBreakingMovesCheckBox.setEnabled(true);
            pmsEvolutionMovesCheckBox.setEnabled(true);
        }

        if (pmsGuaranteedLevel1MovesCheckBox.isSelected()) {
            pmsGuaranteedLevel1MovesSlider.setEnabled(true);
        } else {
            pmsGuaranteedLevel1MovesSlider.setEnabled(false);
            pmsGuaranteedLevel1MovesSlider.setValue(pmsGuaranteedLevel1MovesSlider.getMinimum());
        }

        if (pmsForceGoodDamagingCheckBox.isSelected()) {
            pmsForceGoodDamagingSlider.setEnabled(true);
        } else {
            pmsForceGoodDamagingSlider.setEnabled(false);
            pmsForceGoodDamagingSlider.setValue(pmsForceGoodDamagingSlider.getMinimum());
        }

        if (isTrainerSetting(TRAINER_UNCHANGED)) {
            tpSimilarStrengthCheckBox.setEnabled(false);
            tpSimilarStrengthCheckBox.setSelected(false);
            tpDontUseLegendariesCheckBox.setEnabled(false);
            tpDontUseLegendariesCheckBox.setSelected(false);
            tpNoEarlyWonderGuardCheckBox.setEnabled(false);
            tpNoEarlyWonderGuardCheckBox.setSelected(false);
            tpAllowAlternateFormesCheckBox.setEnabled(false);
            tpAllowAlternateFormesCheckBox.setSelected(false);
            tpSwapMegaEvosCheckBox.setEnabled(false);
            tpSwapMegaEvosCheckBox.setSelected(false);
            tpRandomShinyTrainerPokemonCheckBox.setEnabled(false);
            tpRandomShinyTrainerPokemonCheckBox.setSelected(false);
            tpBetterMovesetsCheckBox.setEnabled(false);
            tpBetterMovesetsCheckBox.setSelected(false);
            tpDoubleBattleModeCheckBox.setEnabled(false);
            tpDoubleBattleModeCheckBox.setSelected(false);
            tpBossTrainersCheckBox.setEnabled(false);
            tpBossTrainersCheckBox.setSelected(false);
            tpImportantTrainersCheckBox.setEnabled(false);
            tpImportantTrainersCheckBox.setSelected(false);
            tpRegularTrainersCheckBox.setEnabled(false);
            tpRegularTrainersCheckBox.setSelected(false);
            tpBossTrainersItemsCheckBox.setEnabled(false);
            tpBossTrainersItemsCheckBox.setSelected(false);
            tpImportantTrainersItemsCheckBox.setEnabled(false);
            tpImportantTrainersItemsCheckBox.setSelected(false);
            tpRegularTrainersItemsCheckBox.setEnabled(false);
            tpRegularTrainersItemsCheckBox.setSelected(false);
            tpConsumableItemsOnlyCheckBox.setEnabled(false);
            tpConsumableItemsOnlyCheckBox.setSelected(false);
            tpSensibleItemsCheckBox.setEnabled(false);
            tpSensibleItemsCheckBox.setSelected(false);
            tpHighestLevelGetsItemCheckBox.setEnabled(false);
            tpHighestLevelGetsItemCheckBox.setSelected(false);
            tpEliteFourUniquePokemonCheckBox.setEnabled(false);
            tpEliteFourUniquePokemonCheckBox.setSelected(false);
        } else {
            tpSimilarStrengthCheckBox.setEnabled(true);
            tpDontUseLegendariesCheckBox.setEnabled(true);
            tpNoEarlyWonderGuardCheckBox.setEnabled(true);
            tpAllowAlternateFormesCheckBox.setEnabled(true);
            if (currentRestrictions == null || currentRestrictions.allowTrainerSwapMegaEvolvables(
                    romHandler.forceSwapStaticMegaEvos(), isTrainerSetting(TRAINER_TYPE_THEMED))) {
                tpSwapMegaEvosCheckBox.setEnabled(true);
            } else {
                tpSwapMegaEvosCheckBox.setEnabled(false);
                tpSwapMegaEvosCheckBox.setSelected(false);
            }
            tpRandomShinyTrainerPokemonCheckBox.setEnabled(true);
            tpBetterMovesetsCheckBox.setEnabled(true);
            tpDoubleBattleModeCheckBox.setEnabled(tpDoubleBattleModeCheckBox.isVisible());
            tpBossTrainersCheckBox.setEnabled(tpBossTrainersCheckBox.isVisible());
            tpImportantTrainersCheckBox.setEnabled(tpImportantTrainersCheckBox.isVisible());
            tpRegularTrainersCheckBox.setEnabled(tpRegularTrainersCheckBox.isVisible());
            tpBossTrainersItemsCheckBox.setEnabled(tpBossTrainersItemsCheckBox.isVisible());
            tpImportantTrainersItemsCheckBox.setEnabled(tpImportantTrainersItemsCheckBox.isVisible());
            tpRegularTrainersItemsCheckBox.setEnabled(tpRegularTrainersItemsCheckBox.isVisible());
            tpEliteFourUniquePokemonCheckBox.setEnabled(tpEliteFourUniquePokemonCheckBox.isVisible());
        }

        if (tpForceFullyEvolvedAtCheckBox.isSelected()) {
            tpForceFullyEvolvedAtSlider.setEnabled(true);
        } else {
            tpForceFullyEvolvedAtSlider.setEnabled(false);
            tpForceFullyEvolvedAtSlider.setValue(tpForceFullyEvolvedAtSlider.getMinimum());
        }

        if (tpPercentageLevelModifierCheckBox.isSelected()) {
            tpPercentageLevelModifierSlider.setEnabled(true);
        } else {
            tpPercentageLevelModifierSlider.setEnabled(false);
            tpPercentageLevelModifierSlider.setValue(0);
        }

        if (tpBossTrainersCheckBox.isSelected()) {
            tpBossTrainersSpinner.setEnabled(true);
        } else {
            tpBossTrainersSpinner.setEnabled(false);
            tpBossTrainersSpinner.setValue(1);
        }

        if (tpImportantTrainersCheckBox.isSelected()) {
            tpImportantTrainersSpinner.setEnabled(true);
        } else {
            tpImportantTrainersSpinner.setEnabled(false);
            tpImportantTrainersSpinner.setValue(1);
        }

        if (tpRegularTrainersCheckBox.isSelected()) {
            tpRegularTrainersSpinner.setEnabled(true);
        } else {
            tpRegularTrainersSpinner.setEnabled(false);
            tpRegularTrainersSpinner.setValue(1);
        }

        if (tpBossTrainersItemsCheckBox.isSelected() || tpImportantTrainersItemsCheckBox.isSelected() ||
                tpRegularTrainersItemsCheckBox.isSelected()) {
            tpConsumableItemsOnlyCheckBox.setEnabled(true);
            tpSensibleItemsCheckBox.setEnabled(true);
            tpHighestLevelGetsItemCheckBox.setEnabled(true);
        } else {
            tpConsumableItemsOnlyCheckBox.setEnabled(false);
            tpSensibleItemsCheckBox.setEnabled(false);
            tpHighestLevelGetsItemCheckBox.setEnabled(false);
        }

        if (!spUnchangedRadioButton.isSelected() || !isTrainerSetting(TRAINER_UNCHANGED)) {
            tpRivalCarriesStarterCheckBox.setEnabled(true);
        } else {
            tpRivalCarriesStarterCheckBox.setEnabled(false);
            tpRivalCarriesStarterCheckBox.setSelected(false);
        }

        if (isTrainerSetting(TRAINER_TYPE_THEMED)) {
            tpWeightTypesCheckBox.setEnabled(true);
        } else {
            tpWeightTypesCheckBox.setEnabled(false);
            tpWeightTypesCheckBox.setSelected(false);
        }

        if (tpEliteFourUniquePokemonCheckBox.isSelected()) {
            tpEliteFourUniquePokemonSpinner.setEnabled(true);
        } else {
            tpEliteFourUniquePokemonSpinner.setEnabled(false);
            tpEliteFourUniquePokemonSpinner.setValue(1);
        }

        if (!totpUnchangedRadioButton.isSelected() || !totpAllyUnchangedRadioButton.isSelected()) {
            totpAllowAltFormesCheckBox.setEnabled(true);
        } else {
            totpAllowAltFormesCheckBox.setEnabled(false);
            totpAllowAltFormesCheckBox.setSelected(false);
        }

        if (totpPercentageLevelModifierCheckBox.isSelected()) {
            totpPercentageLevelModifierSlider.setEnabled(true);
        } else {
            totpPercentageLevelModifierSlider.setEnabled(false);
            totpPercentageLevelModifierSlider.setValue(0);
        }

        if (wpRandomRadioButton.isSelected()) {
            wpARNoneRadioButton.setEnabled(true);
            wpARSimilarStrengthRadioButton.setEnabled(true);
            wpARCatchEmAllModeRadioButton.setEnabled(true);
            wpARTypeThemeAreasRadioButton.setEnabled(true);
            wpBalanceShakingGrassPokemonCheckBox.setEnabled(true);
        } else if (wpArea1To1RadioButton.isSelected()) {
            wpARNoneRadioButton.setEnabled(true);
            wpARSimilarStrengthRadioButton.setEnabled(true);
            wpARCatchEmAllModeRadioButton.setEnabled(true);
            wpARTypeThemeAreasRadioButton.setEnabled(true);
            wpBalanceShakingGrassPokemonCheckBox.setEnabled(false);
        } else if (wpGlobal1To1RadioButton.isSelected()) {
            if (wpARCatchEmAllModeRadioButton.isSelected() || wpARTypeThemeAreasRadioButton.isSelected()) {
                wpARNoneRadioButton.setSelected(true);
            }
            wpARNoneRadioButton.setEnabled(true);
            wpARSimilarStrengthRadioButton.setEnabled(true);
            wpARCatchEmAllModeRadioButton.setEnabled(false);
            wpARTypeThemeAreasRadioButton.setEnabled(false);
            wpBalanceShakingGrassPokemonCheckBox.setEnabled(false);
        } else {
            wpARNoneRadioButton.setEnabled(false);
            wpARNoneRadioButton.setSelected(true);
            wpARSimilarStrengthRadioButton.setEnabled(false);
            wpARCatchEmAllModeRadioButton.setEnabled(false);
            wpARTypeThemeAreasRadioButton.setEnabled(false);
            wpBalanceShakingGrassPokemonCheckBox.setEnabled(false);
        }

        if (wpUnchangedRadioButton.isSelected()) {
            wpUseTimeBasedEncountersCheckBox.setEnabled(false);
            wpUseTimeBasedEncountersCheckBox.setSelected(false);
            wpDontUseLegendariesCheckBox.setEnabled(false);
            wpDontUseLegendariesCheckBox.setSelected(false);
            wpAllowAltFormesCheckBox.setEnabled(false);
            wpAllowAltFormesCheckBox.setSelected(false);
        } else {
            wpUseTimeBasedEncountersCheckBox.setEnabled(true);
            wpDontUseLegendariesCheckBox.setEnabled(true);
            wpAllowAltFormesCheckBox.setEnabled(true);
        }

        if (wpRandomizeHeldItemsCheckBox.isSelected()
                && wpRandomizeHeldItemsCheckBox.isVisible()
                && wpRandomizeHeldItemsCheckBox.isEnabled()) { // ??? why all three
            wpBanBadItemsCheckBox.setEnabled(true);
        } else {
            wpBanBadItemsCheckBox.setEnabled(false);
            wpBanBadItemsCheckBox.setSelected(false);
        }

        if (wpSetMinimumCatchRateCheckBox.isSelected()) {
            wpSetMinimumCatchRateSlider.setEnabled(true);
        } else {
            wpSetMinimumCatchRateSlider.setEnabled(false);
            wpSetMinimumCatchRateSlider.setValue(0);
        }

        if (wpPercentageLevelModifierCheckBox.isSelected()) {
            wpPercentageLevelModifierSlider.setEnabled(true);
        } else {
            wpPercentageLevelModifierSlider.setEnabled(false);
            wpPercentageLevelModifierSlider.setValue(0);
        }

        if (pmsMetronomeOnlyModeRadioButton.isSelected()) {
            tmUnchangedRadioButton.setEnabled(false);
            tmRandomRadioButton.setEnabled(false);
            tmUnchangedRadioButton.setSelected(true);

            mtUnchangedRadioButton.setEnabled(false);
            mtRandomRadioButton.setEnabled(false);
            mtUnchangedRadioButton.setSelected(true);

            tmLevelupMoveSanityCheckBox.setEnabled(false);
            tmLevelupMoveSanityCheckBox.setSelected(false);
            tmKeepFieldMoveTMsCheckBox.setEnabled(false);
            tmKeepFieldMoveTMsCheckBox.setSelected(false);
            tmForceGoodDamagingCheckBox.setEnabled(false);
            tmForceGoodDamagingCheckBox.setSelected(false);
            tmNoGameBreakingMovesCheckBox.setEnabled(false);
            tmNoGameBreakingMovesCheckBox.setSelected(false);
            tmFollowEvolutionsCheckBox.setEnabled(false);
            tmFollowEvolutionsCheckBox.setSelected(false);

            mtLevelupMoveSanityCheckBox.setEnabled(false);
            mtLevelupMoveSanityCheckBox.setSelected(false);
            mtKeepFieldMoveTutorsCheckBox.setEnabled(false);
            mtKeepFieldMoveTutorsCheckBox.setSelected(false);
            mtForceGoodDamagingCheckBox.setEnabled(false);
            mtForceGoodDamagingCheckBox.setSelected(false);
            mtNoGameBreakingMovesCheckBox.setEnabled(false);
            mtNoGameBreakingMovesCheckBox.setSelected(false);
            mtFollowEvolutionsCheckBox.setEnabled(false);
            mtFollowEvolutionsCheckBox.setSelected(false);
        } else {
            tmUnchangedRadioButton.setEnabled(true);
            tmRandomRadioButton.setEnabled(true);

            mtUnchangedRadioButton.setEnabled(true);
            mtRandomRadioButton.setEnabled(true);

            if (!(pmsUnchangedRadioButton.isSelected()) || !(tmUnchangedRadioButton.isSelected())
                    || !(thcUnchangedRadioButton.isSelected())) {
                tmLevelupMoveSanityCheckBox.setEnabled(true);
            } else {
                tmLevelupMoveSanityCheckBox.setEnabled(false);
                tmLevelupMoveSanityCheckBox.setSelected(false);
            }

            if ((!thcUnchangedRadioButton.isSelected()) || (tmLevelupMoveSanityCheckBox.isSelected())) {
                tmFollowEvolutionsCheckBox.setEnabled(followEvolutionControlsEnabled);
            }
            else {
                tmFollowEvolutionsCheckBox.setEnabled(false);
                tmFollowEvolutionsCheckBox.setSelected(false);
            }

            if (!(tmUnchangedRadioButton.isSelected())) {
                tmKeepFieldMoveTMsCheckBox.setEnabled(true);
                tmForceGoodDamagingCheckBox.setEnabled(true);
                tmNoGameBreakingMovesCheckBox.setEnabled(true);
            } else {
                tmKeepFieldMoveTMsCheckBox.setEnabled(false);
                tmKeepFieldMoveTMsCheckBox.setSelected(false);
                tmForceGoodDamagingCheckBox.setEnabled(false);
                tmForceGoodDamagingCheckBox.setSelected(false);
                tmNoGameBreakingMovesCheckBox.setEnabled(false);
                tmNoGameBreakingMovesCheckBox.setSelected(false);
            }

            if (romHandler.hasMoveTutors()
                    && (!(pmsUnchangedRadioButton.isSelected()) || !(mtUnchangedRadioButton.isSelected())
                    || !(mtcUnchangedRadioButton.isSelected()))) {
                mtLevelupMoveSanityCheckBox.setEnabled(true);
            } else {
                mtLevelupMoveSanityCheckBox.setEnabled(false);
                mtLevelupMoveSanityCheckBox.setSelected(false);
            }

            if (!(mtcUnchangedRadioButton.isSelected()) || (mtLevelupMoveSanityCheckBox.isSelected())) {
                mtFollowEvolutionsCheckBox.setEnabled(followEvolutionControlsEnabled);
            }
            else {
                mtFollowEvolutionsCheckBox.setEnabled(false);
                mtFollowEvolutionsCheckBox.setSelected(false);
            }

            if (romHandler.hasMoveTutors() && !(mtUnchangedRadioButton.isSelected())) {
                mtKeepFieldMoveTutorsCheckBox.setEnabled(true);
                mtForceGoodDamagingCheckBox.setEnabled(true);
                mtNoGameBreakingMovesCheckBox.setEnabled(true);
            } else {
                mtKeepFieldMoveTutorsCheckBox.setEnabled(false);
                mtKeepFieldMoveTutorsCheckBox.setSelected(false);
                mtForceGoodDamagingCheckBox.setEnabled(false);
                mtForceGoodDamagingCheckBox.setSelected(false);
                mtNoGameBreakingMovesCheckBox.setEnabled(false);
                mtNoGameBreakingMovesCheckBox.setSelected(false);
            }
        }

        if (tmForceGoodDamagingCheckBox.isSelected()) {
            tmForceGoodDamagingSlider.setEnabled(true);
        } else {
            tmForceGoodDamagingSlider.setEnabled(false);
            tmForceGoodDamagingSlider.setValue(tmForceGoodDamagingSlider.getMinimum());
        }

        if (mtForceGoodDamagingCheckBox.isSelected()) {
            mtForceGoodDamagingSlider.setEnabled(true);
        } else {
            mtForceGoodDamagingSlider.setEnabled(false);
            mtForceGoodDamagingSlider.setValue(mtForceGoodDamagingSlider.getMinimum());
        }

        tmFullHMCompatibilityCheckBox.setEnabled(!thcFullCompatibilityRadioButton.isSelected());

        if (fiRandomRadioButton.isSelected() && fiRandomRadioButton.isVisible() && fiRandomRadioButton.isEnabled()) {
            fiBanBadItemsCheckBox.setEnabled(true);
        } else if (fiRandomEvenDistributionRadioButton.isSelected() && fiRandomEvenDistributionRadioButton.isVisible()
                && fiRandomEvenDistributionRadioButton.isEnabled()) {
            fiBanBadItemsCheckBox.setEnabled(true);
        } else {
            fiBanBadItemsCheckBox.setEnabled(false);
            fiBanBadItemsCheckBox.setSelected(false);
        }

        if (shRandomRadioButton.isSelected() && shRandomRadioButton.isVisible() && shRandomRadioButton.isEnabled()) {
            shBanBadItemsCheckBox.setEnabled(true);
            shBanRegularShopItemsCheckBox.setEnabled(true);
            shBanOverpoweredShopItemsCheckBox.setEnabled(true);
            shBalanceShopItemPricesCheckBox.setEnabled(true);
            shGuaranteeEvolutionItemsCheckBox.setEnabled(true);
            shGuaranteeXItemsCheckBox.setEnabled(true);
        } else {
            shBanBadItemsCheckBox.setEnabled(false);
            shBanBadItemsCheckBox.setSelected(false);
            shBanRegularShopItemsCheckBox.setEnabled(false);
            shBanRegularShopItemsCheckBox.setSelected(false);
            shBanOverpoweredShopItemsCheckBox.setEnabled(false);
            shBanOverpoweredShopItemsCheckBox.setSelected(false);
            shBalanceShopItemPricesCheckBox.setEnabled(false);
            shBalanceShopItemPricesCheckBox.setSelected(false);
            shGuaranteeEvolutionItemsCheckBox.setEnabled(false);
            shGuaranteeEvolutionItemsCheckBox.setSelected(false);
            shGuaranteeXItemsCheckBox.setEnabled(false);
            shGuaranteeXItemsCheckBox.setSelected(false);
        }

        if (puRandomRadioButton.isSelected() && puRandomRadioButton.isVisible() && puRandomRadioButton.isEnabled()) {
            puBanBadItemsCheckBox.setEnabled(true);
        } else {
            puBanBadItemsCheckBox.setEnabled(false);
            puBanBadItemsCheckBox.setSelected(false);
        }
    }

    private void initTweaksPanel() {
        tweakCheckBoxes = new ArrayList<>();
        int numTweaks = MiscTweak.allTweaks.size();
        for (int i = 0; i < numTweaks; i++) {
            MiscTweak ct = MiscTweak.allTweaks.get(i);
            JCheckBox tweakBox = new JCheckBox();
            tweakBox.setText(ct.getTweakName());
            tweakBox.setToolTipText(ct.getTooltipText());
            tweakCheckBoxes.add(tweakBox);
        }
    }

    private void makeTweaksLayout(List<JCheckBox> tweaks) {
        liveTweaksPanel = new JPanel(new GridBagLayout());
        TitledBorder border = BorderFactory.createTitledBorder("Misc. Tweaks");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD));
        liveTweaksPanel.setBorder(border);

        int numTweaks = tweaks.size();
        Iterator<JCheckBox> tweaksIterator = tweaks.iterator();

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(5,5,0,5);

        int TWEAK_COLS = 4;
        int numCols = Math.min(TWEAK_COLS, numTweaks);

        for (int row = 0; row <= numTweaks / numCols; row++) {
            for (int col = 0; col < numCols; col++) {
                if (!tweaksIterator.hasNext()) break;
                c.gridx = col;
                c.gridy = row;
                liveTweaksPanel.add(tweaksIterator.next(),c);
            }
        }

        // Pack the checkboxes together

        GridBagConstraints horizontalC = new GridBagConstraints();
        horizontalC.gridx = numCols;
        horizontalC.gridy = 0;
        horizontalC.weightx = 0.1;

        GridBagConstraints verticalC = new GridBagConstraints();
        verticalC.gridx = 0;
        verticalC.gridy = (numTweaks / numCols) + 1;
        verticalC.weighty = 0.1;

        liveTweaksPanel.add(new JSeparator(SwingConstants.HORIZONTAL),horizontalC);
        liveTweaksPanel.add(new JSeparator(SwingConstants.VERTICAL),verticalC);
    }

    private void populateDropdowns() {
        List<Pokemon> currentStarters = romHandler.getStarters();
        List<Pokemon> allPokes =
                romHandler.generationOfPokemon() >= 6 ?
                        romHandler.getPokemonInclFormes()
                                .stream()
                                .filter(pk -> pk == null || !pk.actuallyCosmetic)
                                .collect(Collectors.toList()) :
                        romHandler.getPokemon();
        String[] pokeNames = new String[allPokes.size()];
        pokeNames[0] = "Random";
        for (int i = 1; i < allPokes.size(); i++) {
            pokeNames[i] = allPokes.get(i).fullName();

        }

        spComboBox1.setModel(new DefaultComboBoxModel<>(pokeNames));
        spComboBox1.setSelectedIndex(allPokes.indexOf(currentStarters.get(0)));
        spComboBox2.setModel(new DefaultComboBoxModel<>(pokeNames));
        spComboBox2.setSelectedIndex(allPokes.indexOf(currentStarters.get(1)));
        if (!romHandler.isYellow()) {
            spComboBox3.setModel(new DefaultComboBoxModel<>(pokeNames));
            spComboBox3.setSelectedIndex(allPokes.indexOf(currentStarters.get(2)));
        }

        String[] baseStatGenerationNumbers = new String[Math.min(3, GlobalConstants.HIGHEST_POKEMON_GEN - romHandler.generationOfPokemon())];
        int j = Math.max(6,romHandler.generationOfPokemon() + 1);
        for (int i = 0; i < baseStatGenerationNumbers.length; i++) {
            baseStatGenerationNumbers[i] = String.valueOf(j);
            j++;
        }
        pbsUpdateComboBox.setModel(new DefaultComboBoxModel<>(baseStatGenerationNumbers));

        String[] moveGenerationNumbers = new String[GlobalConstants.HIGHEST_POKEMON_GEN - romHandler.generationOfPokemon()];
        j = romHandler.generationOfPokemon() + 1;
        for (int i = 0; i < moveGenerationNumbers.length; i++) {
            moveGenerationNumbers[i] = String.valueOf(j);
            j++;
        }
        mdUpdateComboBox.setModel(new DefaultComboBoxModel<>(moveGenerationNumbers));


        tpComboBox.setModel(new DefaultComboBoxModel<>(getTrainerSettingsForGeneration(romHandler.generationOfPokemon())));
        tpComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JComponent comp = (JComponent) super.getListCellRendererComponent(list,
                        value, index, isSelected, cellHasFocus);

                if (index >= 0 && value != null) {
                    list.setToolTipText(bundle.getString(trainerSettingToolTips.get(trainerSettings.indexOf(value))));
                }
                return comp;
            }
        });
    }

    private ImageIcon makeMascotIcon() {
        try {
            BufferedImage handlerImg = romHandler.getMascotImage();

            if (handlerImg == null) {
                return emptyIcon;
            }

            BufferedImage nImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            int hW = handlerImg.getWidth();
            int hH = handlerImg.getHeight();
            nImg.getGraphics().drawImage(handlerImg, 64 - hW / 2, 64 - hH / 2, frame);
            return new ImageIcon(nImg);
        } catch (Exception ex) {
            return emptyIcon;
        }
    }

    private void checkCustomNames() {
        String[] cnamefiles = new String[] { SysConstants.tnamesFile, SysConstants.tclassesFile,
                SysConstants.nnamesFile };

        boolean foundFile = false;
        for (int file = 0; file < 3; file++) {
            File currentFile = new File(SysConstants.ROOT_PATH + cnamefiles[file]);
            if (currentFile.exists()) {
                foundFile = true;
                break;
            }
        }

        if (foundFile) {
            int response = JOptionPane.showConfirmDialog(frame,
                    bundle.getString("GUI.convertNameFilesDialog.text"),
                    bundle.getString("GUI.convertNameFilesDialog.title"), JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.YES_OPTION) {
                try {
                    CustomNamesSet newNamesData = CustomNamesSet.importOldNames();
                    byte[] data = newNamesData.getBytes();
                    FileFunctions.writeBytesToFile(SysConstants.customNamesFile, data);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, bundle.getString("GUI.convertNameFilesFailed"));
                }
            }

            haveCheckedCustomNames = true;
            attemptWriteConfig();
        }

    }

    private void attemptReadConfig() {
        // Things that should be true by default should be manually set here
        unloadGameOnSuccess = true;
        File fh = new File(SysConstants.ROOT_PATH + "config.ini");
        if (!fh.exists() || !fh.canRead()) {
            return;
        }

        try {
            Scanner sc = new Scanner(fh, "UTF-8");
            boolean isReadingUpdates = false;
            while (sc.hasNextLine()) {
                String q = sc.nextLine().trim();
                if (q.contains("//")) {
                    q = q.substring(0, q.indexOf("//")).trim();
                }
                if (q.equals("[Game Updates]")) {
                    isReadingUpdates = true;
                    continue;
                }
                if (!q.isEmpty()) {
                    String[] tokens = q.split("=", 2);
                    if (tokens.length == 2) {
                        String key = tokens[0].trim();
                        if (isReadingUpdates) {
                            gameUpdates.put(key, tokens[1]);
                        }
                        if (key.equalsIgnoreCase("checkedcustomnames172")) {
                            haveCheckedCustomNames = Boolean.parseBoolean(tokens[1].trim());
                        }
                        if (key.equals("firststart")) {
                            String val = tokens[1];
                            if (val.equals(Version.VERSION_STRING)) {
                                initialPopup = false;
                            }
                        }
                        if (key.equals("unloadgameonsuccess")) {
                            unloadGameOnSuccess = Boolean.parseBoolean(tokens[1].trim());
                        }
                        if (key.equals("showinvalidrompopup")) {
                            showInvalidRomPopup = Boolean.parseBoolean(tokens[1].trim());
                        }
                    }
                } else if (isReadingUpdates) {
                    isReadingUpdates = false;
                }
            }
            sc.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private boolean attemptWriteConfig() {
        File fh = new File(SysConstants.ROOT_PATH + "config.ini");
        if (fh.exists() && !fh.canWrite()) {
            return false;
        }

        try {
            PrintStream ps = new PrintStream(new FileOutputStream(fh), true, "UTF-8");
            ps.println("checkedcustomnames=true");
            ps.println("checkedcustomnames172=" + haveCheckedCustomNames);
            ps.println("unloadgameonsuccess=" + unloadGameOnSuccess);
            ps.println("showinvalidrompopup=" + showInvalidRomPopup);
            if (!initialPopup) {
                ps.println("firststart=" + Version.VERSION_STRING);
            }
            if (gameUpdates.size() > 0) {
                ps.println();
                ps.println("[Game Updates]");
                for (Map.Entry<String, String> update : gameUpdates.entrySet()) {
                    ps.format("%s=%s", update.getKey(), update.getValue());
                    ps.println();
                }
            }
            ps.close();
            return true;
        } catch (IOException e) {
            return false;
        }

    }

    private void testForRequiredConfigs() {
        try {
            Utils.testForRequiredConfigs();
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(null,
                    String.format(bundle.getString("GUI.configFileMissing"), e.getMessage()));
            System.exit(1);
        }
    }

    private ExpCurve[] getEXPCurvesForGeneration(int generation) {
        ExpCurve[] result;
        if (generation < 3) {
            result = new ExpCurve[]{ ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW };
        } else {
            result = new ExpCurve[]{ ExpCurve.MEDIUM_FAST, ExpCurve.MEDIUM_SLOW, ExpCurve.FAST, ExpCurve.SLOW, ExpCurve.ERRATIC, ExpCurve.FLUCTUATING };
        }
        return result;
    }

    private String[] getTrainerSettingsForGeneration(int generation) {
        List<String> result = new ArrayList<>(trainerSettings);
        if (generation != 5) {
            result.remove(bundle.getString("GUI.tpMain3RandomEvenDistributionMainGame.text"));
        }
        return result.toArray(new String[0]);
    }

    private boolean isTrainerSetting(int setting) {
        return trainerSettings.indexOf(tpComboBox.getSelectedItem()) == setting;
    }

    public static void main(String[] args) {
        String firstCliArg = args.length > 0 ? args[0] : "";
        // invoke as CLI program
        if (firstCliArg.equals("cli")) {
            // snip the "cli" flag arg off the args array and invoke command
            String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
            int exitCode = CliRandomizer.invoke(commandArgs);
            System.exit(exitCode);
        } else {
            launcherInput = firstCliArg;
            if (launcherInput.equals("please-use-the-launcher")) usedLauncher = true;
            SwingUtilities.invokeLater(() -> {
                frame = new JFrame("NewRandomizerGUI");
                try {
                    String lafName = javax.swing.UIManager.getSystemLookAndFeelClassName();
                    // NEW: Only set Native LaF on windows.
                    if (lafName.equalsIgnoreCase("com.sun.java.swing.plaf.windows.WindowsLookAndFeel")) {
                        javax.swing.UIManager.setLookAndFeel(lafName);
                    }
                } catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException ex) {
                    java.util.logging.Logger.getLogger(NewRandomizerGUI.class.getName()).log(java.util.logging.Level.SEVERE, null,
                            ex);
                }
                frame.setContentPane(new NewRandomizerGUI().mainPanel);
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);
            });
        }
    }

	private void initComponents() {
		ResourceBundle bundle = ResourceBundle.getBundle("com.dabomstew.pkrandom.newgui.Bundle");
		mainPanel = new JPanel();
		JPanel panel1 = new JPanel();
		raceModeCheckBox = new JCheckBox();
		limitPokemonCheckBox = new JCheckBox();
		limitPokemonButton = new JButton();
		noIrregularAltFormesCheckBox = new JCheckBox();
		JPanel vSpacer1 = new JPanel(null);
		JPanel panel2 = new JPanel();
		JPanel hSpacer1 = new JPanel(null);
		romNameLabel = new JLabel();
		romCodeLabel = new JLabel();
		romSupportLabel = new JLabel();
		tabbedPane1 = new JTabbedPane();
		JPanel panel3 = new JPanel();
		JPanel panel4 = new JPanel();
		pbsUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer2 = new JPanel(null);
		pbsShuffleRadioButton = new JRadioButton();
		pbsLegendariesSlowRadioButton = new JRadioButton();
		pbsStrongLegendariesSlowRadioButton = new JRadioButton();
		pbsStandardizeEXPCurvesCheckBox = new JCheckBox();
		pbsFollowEvolutionsCheckBox = new JCheckBox();
		JPanel hSpacer3 = new JPanel(null);
		JPanel vSpacer2 = new JPanel(null);
		JPanel vSpacer3 = new JPanel(null);
		pbsUpdateBaseStatsCheckBox = new JCheckBox();
		pbsFollowMegaEvosCheckBox = new JCheckBox();
		pbsUpdateComboBox = new JComboBox<>();
		pbsEXPCurveComboBox = new JComboBox<>();
		pbsAssignEvoStatsRandomlyCheckBox = new JCheckBox();
		pbsRandomRadioButton = new JRadioButton();
		pbsAllMediumFastRadioButton = new JRadioButton();
		JPanel hSpacer4 = new JPanel(null);
		JPanel vSpacer4 = new JPanel(null);
		JPanel hSpacer5 = new JPanel(null);
		JPanel panel5 = new JPanel();
		ptUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer6 = new JPanel(null);
		JPanel vSpacer5 = new JPanel(null);
		JPanel hSpacer7 = new JPanel(null);
		JPanel vSpacer6 = new JPanel(null);
		ptRandomFollowEvolutionsRadioButton = new JRadioButton();
		ptRandomCompletelyRadioButton = new JRadioButton();
		ptFollowMegaEvosCheckBox = new JCheckBox();
		ptIsDualTypeCheckBox = new JCheckBox();
		pokemonAbilitiesPanel = new JPanel();
		paUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer8 = new JPanel(null);
		JPanel vSpacer7 = new JPanel(null);
		JPanel hSpacer9 = new JPanel(null);
		JPanel vSpacer8 = new JPanel(null);
		paRandomRadioButton = new JRadioButton();
		paAllowWonderGuardCheckBox = new JCheckBox();
		paFollowEvolutionsCheckBox = new JCheckBox();
		JLabel label1 = new JLabel();
		paTrappingAbilitiesCheckBox = new JCheckBox();
		paNegativeAbilitiesCheckBox = new JCheckBox();
		paBadAbilitiesCheckBox = new JCheckBox();
		paWeighDuplicatesTogetherCheckBox = new JCheckBox();
		paEnsureTwoAbilitiesCheckbox = new JCheckBox();
		paFollowMegaEvosCheckBox = new JCheckBox();
		JPanel panel6 = new JPanel();
		peUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer10 = new JPanel(null);
		JPanel vSpacer9 = new JPanel(null);
		JPanel hSpacer11 = new JPanel(null);
		JPanel vSpacer10 = new JPanel(null);
		peRandomRadioButton = new JRadioButton();
		peSimilarStrengthCheckBox = new JCheckBox();
		peSameTypingCheckBox = new JCheckBox();
		peLimitEvolutionsToThreeCheckBox = new JCheckBox();
		peForceChangeCheckBox = new JCheckBox();
		peChangeImpossibleEvosCheckBox = new JCheckBox();
		peMakeEvolutionsEasierCheckBox = new JCheckBox();
		peAllowAltFormesCheckBox = new JCheckBox();
		peRemoveTimeBasedEvolutionsCheckBox = new JCheckBox();
		peRandomEveryLevelRadioButton = new JRadioButton();
		JPanel vSpacer11 = new JPanel(null);
		JPanel panel7 = new JPanel();
		JPanel panel8 = new JPanel();
		spUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer12 = new JPanel(null);
		JPanel vSpacer12 = new JPanel(null);
		JPanel hSpacer13 = new JPanel(null);
		JPanel vSpacer13 = new JPanel(null);
		spCustomRadioButton = new JRadioButton();
		spRandomCompletelyRadioButton = new JRadioButton();
		spRandomTwoEvosRadioButton = new JRadioButton();
		spComboBox1 = new JComboBox<>();
		spComboBox2 = new JComboBox<>();
		spComboBox3 = new JComboBox<>();
		spRandomizeStarterHeldItemsCheckBox = new JCheckBox();
		spBanBadItemsCheckBox = new JCheckBox();
		spAllowAltFormesCheckBox = new JCheckBox();
		JPanel hSpacer14 = new JPanel(null);
		JPanel vSpacer14 = new JPanel(null);
		JPanel hSpacer15 = new JPanel(null);
		JPanel vSpacer15 = new JPanel(null);
		JPanel panel9 = new JPanel();
		stpUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer16 = new JPanel(null);
		JPanel vSpacer16 = new JPanel(null);
		JPanel hSpacer17 = new JPanel(null);
		JPanel vSpacer17 = new JPanel(null);
		stpSwapLegendariesSwapStandardsRadioButton = new JRadioButton();
		stpRandomCompletelyRadioButton = new JRadioButton();
		stpRandomSimilarStrengthRadioButton = new JRadioButton();
		stpSwapMegaEvosCheckBox = new JCheckBox();
		stpPercentageLevelModifierCheckBox = new JCheckBox();
		stpPercentageLevelModifierSlider = new JSlider();
		stpRandomize600BSTCheckBox = new JCheckBox();
		stpAllowAltFormesCheckBox = new JCheckBox();
		stpLimitMainGameLegendariesCheckBox = new JCheckBox();
		stpFixMusicCheckBox = new JCheckBox();
		JPanel panel10 = new JPanel();
		igtUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer18 = new JPanel(null);
		JPanel vSpacer18 = new JPanel(null);
		igtRandomizeGivenPokemonOnlyRadioButton = new JRadioButton();
		igtRandomizeBothRequestedGivenRadioButton = new JRadioButton();
		JPanel hSpacer19 = new JPanel(null);
		JPanel vSpacer19 = new JPanel(null);
		igtRandomizeNicknamesCheckBox = new JCheckBox();
		igtRandomizeOTsCheckBox = new JCheckBox();
		igtRandomizeIVsCheckBox = new JCheckBox();
		igtRandomizeItemsCheckBox = new JCheckBox();
		JPanel panel11 = new JPanel();
		JPanel panel12 = new JPanel();
		mdRandomizeMovePowerCheckBox = new JCheckBox();
		JPanel hSpacer20 = new JPanel(null);
		JPanel vSpacer20 = new JPanel(null);
		JPanel hSpacer21 = new JPanel(null);
		JPanel vSpacer21 = new JPanel(null);
		mdRandomizeMoveAccuracyCheckBox = new JCheckBox();
		mdRandomizeMovePPCheckBox = new JCheckBox();
		mdRandomizeMoveTypesCheckBox = new JCheckBox();
		mdRandomizeMoveCategoryCheckBox = new JCheckBox();
		mdUpdateMovesCheckBox = new JCheckBox();
		mdUpdateComboBox = new JComboBox<>();
		JPanel hSpacer22 = new JPanel(null);
		JPanel vSpacer22 = new JPanel(null);
		JPanel hSpacer23 = new JPanel(null);
		JPanel vSpacer23 = new JPanel(null);
		JPanel panel13 = new JPanel();
		pmsUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer24 = new JPanel(null);
		JPanel vSpacer24 = new JPanel(null);
		JPanel hSpacer25 = new JPanel(null);
		JPanel vSpacer25 = new JPanel(null);
		pmsRandomPreferringSameTypeRadioButton = new JRadioButton();
		pmsRandomCompletelyRadioButton = new JRadioButton();
		pmsMetronomeOnlyModeRadioButton = new JRadioButton();
		pmsGuaranteedLevel1MovesCheckBox = new JCheckBox();
		pmsReorderDamagingMovesCheckBox = new JCheckBox();
		pmsNoGameBreakingMovesCheckBox = new JCheckBox();
		pmsForceGoodDamagingCheckBox = new JCheckBox();
		pmsGuaranteedLevel1MovesSlider = new JSlider();
		pmsForceGoodDamagingSlider = new JSlider();
		pmsEvolutionMovesCheckBox = new JCheckBox();
		JPanel panel14 = new JPanel();
		JPanel panel15 = new JPanel();
		JPanel hSpacer26 = new JPanel(null);
		JPanel vSpacer26 = new JPanel(null);
		tpRivalCarriesStarterCheckBox = new JCheckBox();
		tpSimilarStrengthCheckBox = new JCheckBox();
		tpRandomizeTrainerNamesCheckBox = new JCheckBox();
		tpRandomizeTrainerClassNamesCheckBox = new JCheckBox();
		tpForceFullyEvolvedAtCheckBox = new JCheckBox();
		tpForceFullyEvolvedAtSlider = new JSlider();
		tpPercentageLevelModifierSlider = new JSlider();
		tpPercentageLevelModifierCheckBox = new JCheckBox();
		JPanel hSpacer27 = new JPanel(null);
		tpWeightTypesCheckBox = new JCheckBox();
		tpDontUseLegendariesCheckBox = new JCheckBox();
		tpNoEarlyWonderGuardCheckBox = new JCheckBox();
		tpAllowAlternateFormesCheckBox = new JCheckBox();
		tpSwapMegaEvosCheckBox = new JCheckBox();
		tpRandomShinyTrainerPokemonCheckBox = new JCheckBox();
		JPanel vSpacer27 = new JPanel(null);
		tpEliteFourUniquePokemonCheckBox = new JCheckBox();
		tpEliteFourUniquePokemonSpinner = new JSpinner();
		tpComboBox = new JComboBox<>();
		tpDoubleBattleModeCheckBox = new JCheckBox();
		tpAdditionalPokemonForLabel = new JLabel();
		tpBossTrainersCheckBox = new JCheckBox();
		tpImportantTrainersCheckBox = new JCheckBox();
		tpRegularTrainersCheckBox = new JCheckBox();
		tpBossTrainersSpinner = new JSpinner();
		tpImportantTrainersSpinner = new JSpinner();
		tpRegularTrainersSpinner = new JSpinner();
		tpHeldItemsLabel = new JLabel();
		tpBossTrainersItemsCheckBox = new JCheckBox();
		tpImportantTrainersItemsCheckBox = new JCheckBox();
		tpRegularTrainersItemsCheckBox = new JCheckBox();
		tpConsumableItemsOnlyCheckBox = new JCheckBox();
		tpSensibleItemsCheckBox = new JCheckBox();
		tpHighestLevelGetsItemCheckBox = new JCheckBox();
		tpBetterMovesetsCheckBox = new JCheckBox();
		JPanel hSpacer28 = new JPanel(null);
		JPanel vSpacer28 = new JPanel(null);
		JPanel hSpacer29 = new JPanel(null);
		JPanel vSpacer29 = new JPanel(null);
		totpPanel = new JPanel();
		totpUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer30 = new JPanel(null);
		JPanel vSpacer30 = new JPanel(null);
		JPanel hSpacer31 = new JPanel(null);
		JPanel vSpacer31 = new JPanel(null);
		totpAllyPanel = new JPanel();
		totpAllyUnchangedRadioButton = new JRadioButton();
		totpAllyRandomRadioButton = new JRadioButton();
		totpAllyRandomSimilarStrengthRadioButton = new JRadioButton();
		totpAuraPanel = new JPanel();
		totpAuraUnchangedRadioButton = new JRadioButton();
		totpAuraRandomRadioButton = new JRadioButton();
		totpAuraRandomSameStrengthRadioButton = new JRadioButton();
		totpPercentageLevelModifierSlider = new JSlider();
		totpPercentageLevelModifierCheckBox = new JCheckBox();
		totpRandomizeHeldItemsCheckBox = new JCheckBox();
		totpAllowAltFormesCheckBox = new JCheckBox();
		totpRandomRadioButton = new JRadioButton();
		totpRandomSimilarStrengthRadioButton = new JRadioButton();
		JPanel panel16 = new JPanel();
		JPanel panel17 = new JPanel();
		wpUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer32 = new JPanel(null);
		JPanel vSpacer32 = new JPanel(null);
		JPanel hSpacer33 = new JPanel(null);
		JPanel vSpacer33 = new JPanel(null);
		wpRandomRadioButton = new JRadioButton();
		wpArea1To1RadioButton = new JRadioButton();
		wpGlobal1To1RadioButton = new JRadioButton();
		JPanel panel18 = new JPanel();
		wpARNoneRadioButton = new JRadioButton();
		JPanel hSpacer34 = new JPanel(null);
		JPanel vSpacer34 = new JPanel(null);
		JPanel hSpacer35 = new JPanel(null);
		wpARSimilarStrengthRadioButton = new JRadioButton();
		wpARCatchEmAllModeRadioButton = new JRadioButton();
		wpARTypeThemeAreasRadioButton = new JRadioButton();
		wpUseTimeBasedEncountersCheckBox = new JCheckBox();
		wpDontUseLegendariesCheckBox = new JCheckBox();
		wpSetMinimumCatchRateCheckBox = new JCheckBox();
		wpRandomizeHeldItemsCheckBox = new JCheckBox();
		wpBanBadItemsCheckBox = new JCheckBox();
		wpBalanceShakingGrassPokemonCheckBox = new JCheckBox();
		wpPercentageLevelModifierCheckBox = new JCheckBox();
		wpPercentageLevelModifierSlider = new JSlider();
		wpSetMinimumCatchRateSlider = new JSlider();
		wpAllowAltFormesCheckBox = new JCheckBox();
		JPanel hSpacer36 = new JPanel(null);
		JPanel vSpacer35 = new JPanel(null);
		JPanel hSpacer37 = new JPanel(null);
		JPanel vSpacer36 = new JPanel(null);
		JPanel panel19 = new JPanel();
		JPanel panel20 = new JPanel();
		JPanel panel21 = new JPanel();
		tmUnchangedRadioButton = new JRadioButton();
		JPanel vSpacer37 = new JPanel(null);
		JPanel hSpacer38 = new JPanel(null);
		JPanel vSpacer38 = new JPanel(null);
		tmRandomRadioButton = new JRadioButton();
		tmNoGameBreakingMovesCheckBox = new JCheckBox();
		tmKeepFieldMoveTMsCheckBox = new JCheckBox();
		tmForceGoodDamagingCheckBox = new JCheckBox();
		tmForceGoodDamagingSlider = new JSlider();
		JPanel hSpacer39 = new JPanel(null);
		JPanel vSpacer39 = new JPanel(null);
		JPanel hSpacer40 = new JPanel(null);
		JPanel vSpacer40 = new JPanel(null);
		JPanel panel22 = new JPanel();
		thcUnchangedRadioButton = new JRadioButton();
		JPanel vSpacer41 = new JPanel(null);
		JPanel hSpacer41 = new JPanel(null);
		JPanel vSpacer42 = new JPanel(null);
		thcRandomPreferSameTypeRadioButton = new JRadioButton();
		thcRandomCompletelyRadioButton = new JRadioButton();
		thcFullCompatibilityRadioButton = new JRadioButton();
		tmFollowEvolutionsCheckBox = new JCheckBox();
		tmLevelupMoveSanityCheckBox = new JCheckBox();
		tmFullHMCompatibilityCheckBox = new JCheckBox();
		JPanel hSpacer42 = new JPanel(null);
		JPanel vSpacer43 = new JPanel(null);
		JPanel hSpacer43 = new JPanel(null);
		JPanel vSpacer44 = new JPanel(null);
		moveTutorPanel = new JPanel();
		mtMovesPanel = new JPanel();
		mtUnchangedRadioButton = new JRadioButton();
		JPanel vSpacer45 = new JPanel(null);
		JPanel hSpacer44 = new JPanel(null);
		JPanel vSpacer46 = new JPanel(null);
		mtRandomRadioButton = new JRadioButton();
		mtForceGoodDamagingSlider = new JSlider();
		mtNoGameBreakingMovesCheckBox = new JCheckBox();
		mtKeepFieldMoveTutorsCheckBox = new JCheckBox();
		mtForceGoodDamagingCheckBox = new JCheckBox();
		JPanel hSpacer45 = new JPanel(null);
		JPanel vSpacer47 = new JPanel(null);
		JPanel hSpacer46 = new JPanel(null);
		JPanel vSpacer48 = new JPanel(null);
		mtCompatPanel = new JPanel();
		mtcUnchangedRadioButton = new JRadioButton();
		JPanel vSpacer49 = new JPanel(null);
		JPanel hSpacer47 = new JPanel(null);
		JPanel vSpacer50 = new JPanel(null);
		mtcRandomPreferSameTypeRadioButton = new JRadioButton();
		mtcRandomCompletelyRadioButton = new JRadioButton();
		mtcFullCompatibilityRadioButton = new JRadioButton();
		mtFollowEvolutionsCheckBox = new JCheckBox();
		mtLevelupMoveSanityCheckBox = new JCheckBox();
		mtNoExistLabel = new JLabel();
		JPanel panel23 = new JPanel();
		JPanel panel24 = new JPanel();
		fiUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer48 = new JPanel(null);
		JPanel vSpacer51 = new JPanel(null);
		JPanel hSpacer49 = new JPanel(null);
		JPanel vSpacer52 = new JPanel(null);
		fiShuffleRadioButton = new JRadioButton();
		fiRandomRadioButton = new JRadioButton();
		fiRandomEvenDistributionRadioButton = new JRadioButton();
		fiBanBadItemsCheckBox = new JCheckBox();
		JPanel hSpacer50 = new JPanel(null);
		JPanel vSpacer53 = new JPanel(null);
		JPanel hSpacer51 = new JPanel(null);
		JPanel vSpacer54 = new JPanel(null);
		shopItemsPanel = new JPanel();
		shUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer52 = new JPanel(null);
		JPanel vSpacer55 = new JPanel(null);
		JPanel hSpacer53 = new JPanel(null);
		JPanel vSpacer56 = new JPanel(null);
		shShuffleRadioButton = new JRadioButton();
		shRandomRadioButton = new JRadioButton();
		shBanOverpoweredShopItemsCheckBox = new JCheckBox();
		shBanBadItemsCheckBox = new JCheckBox();
		shBanRegularShopItemsCheckBox = new JCheckBox();
		shBalanceShopItemPricesCheckBox = new JCheckBox();
		shGuaranteeEvolutionItemsCheckBox = new JCheckBox();
		shGuaranteeXItemsCheckBox = new JCheckBox();
		pickupItemsPanel = new JPanel();
		puUnchangedRadioButton = new JRadioButton();
		JPanel hSpacer54 = new JPanel(null);
		JPanel vSpacer57 = new JPanel(null);
		JPanel hSpacer55 = new JPanel(null);
		JPanel vSpacer58 = new JPanel(null);
		puRandomRadioButton = new JRadioButton();
		puBanBadItemsCheckBox = new JCheckBox();
		baseTweaksPanel = new JPanel();
		miscTweaksPanel = new JPanel();
		miscBWExpPatchCheckBox = new JCheckBox();
		JPanel hSpacer56 = new JPanel(null);
		JPanel vSpacer59 = new JPanel(null);
		JPanel hSpacer57 = new JPanel(null);
		miscNerfXAccuracyCheckBox = new JCheckBox();
		miscFixCritRateCheckBox = new JCheckBox();
		miscFastestTextCheckBox = new JCheckBox();
		miscRunningShoesIndoorsCheckBox = new JCheckBox();
		miscRandomizePCPotionCheckBox = new JCheckBox();
		miscAllowPikachuEvolutionCheckBox = new JCheckBox();
		miscGiveNationalDexAtCheckBox = new JCheckBox();
		miscUpdateTypeEffectivenessCheckBox = new JCheckBox();
		miscLowerCasePokemonNamesCheckBox = new JCheckBox();
		miscRandomizeCatchingTutorialCheckBox = new JCheckBox();
		miscBanLuckyEggCheckBox = new JCheckBox();
		miscNoFreeLuckyEggCheckBox = new JCheckBox();
		miscBanBigMoneyManiacCheckBox = new JCheckBox();
		mtNoneAvailableLabel = new JLabel();
		miscSOSBattlesCheckBox = new JCheckBox();
		miscBalanceStaticLevelsCheckBox = new JCheckBox();
		miscRetainAltFormesCheckBox = new JCheckBox();
		miscRunWithoutRunningShoesCheckBox = new JCheckBox();
		miscFasterHPAndEXPBarsCheckBox = new JCheckBox();
		miscForceChallengeModeCheckBox = new JCheckBox();
		miscFastDistortionWorldCheckBox = new JCheckBox();
		JPanel hSpacer58 = new JPanel(null);
		JPanel vSpacer60 = new JPanel(null);
		JPanel hSpacer59 = new JPanel(null);
		JPanel vSpacer61 = new JPanel(null);
		openROMButton = new JButton();
		randomizeSaveButton = new JButton();
		premadeSeedButton = new JButton();
		settingsButton = new JButton();
		websiteLinkLabel = new JLabel();
		versionLabel = new JLabel();
		JPanel hSpacer60 = new JPanel(null);
		JPanel hSpacer61 = new JPanel(null);
		JPanel vSpacer62 = new JPanel(null);
		JPanel vSpacer63 = new JPanel(null);
		gameMascotLabel = new JLabel();
		saveSettingsButton = new JButton();
		loadSettingsButton = new JButton();
		JPanel hSpacer62 = new JPanel(null);
		JPanel hSpacer63 = new JPanel(null);
		JPanel hSpacer64 = new JPanel(null);
		JPanel vSpacer64 = new JPanel(null);
		wikiLinkLabel = new JLabel();

		//======== mainPanel ========
		{
			mainPanel.setLayout(new GridBagLayout());

			//======== panel1 ========
			{
				panel1.setEnabled(true);
				panel1.setBorder(new TitledBorder(null, bundle.getString("GUI.generalOptionsPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
					UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
				panel1.setLayout(new GridBagLayout());

				//---- raceModeCheckBox ----
				raceModeCheckBox.setEnabled(false);
				raceModeCheckBox.setText(bundle.getString("GUI.raceModeCheckBox.text"));
				raceModeCheckBox.setToolTipText(bundle.getString("GUI.raceModeCheckBox.toolTipText"));
				panel1.add(raceModeCheckBox, new GridBagConstraints(0, 2, 2, 1, 0.0, 0.0,
					GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(0, 0, 0, 30), 0, 0));

				//---- limitPokemonCheckBox ----
				limitPokemonCheckBox.setEnabled(false);
				limitPokemonCheckBox.setText("");
				limitPokemonCheckBox.setToolTipText(bundle.getString("GUI.limitPokemonCheckBox.toolTipText"));
				panel1.add(limitPokemonCheckBox, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
					GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(5, 0, 0, 0), 0, 0));

				//---- limitPokemonButton ----
				limitPokemonButton.setEnabled(false);
				limitPokemonButton.setText(bundle.getString("GUI.limitPokemonCheckBox.text"));
				panel1.add(limitPokemonButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
					GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
					new Insets(5, 0, 0, 0), 0, 0));

				//---- noIrregularAltFormesCheckBox ----
				noIrregularAltFormesCheckBox.setEnabled(false);
				noIrregularAltFormesCheckBox.setText(bundle.getString("GUI.noIrregularAltFormesCheckBox.text"));
				noIrregularAltFormesCheckBox.setToolTipText(bundle.getString("GUI.noIrregularAltFormesCheckBox.toolTipText"));
				panel1.add(noIrregularAltFormesCheckBox, new GridBagConstraints(0, 1, 2, 1, 0.0, 0.0,
					GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(0, 0, 0, 0), 0, 0));
			}
			mainPanel.add(panel1, new GridBagConstraints(2, 1, 1, 3, 0.0, 0.0,
				GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(vSpacer1, new GridBagConstraints(2, 7, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.VERTICAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//======== panel2 ========
			{
				panel2.setBorder(new TitledBorder(null, bundle.getString("GUI.romInformationPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
					UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
				panel2.setLayout(new GridBagLayout());
				panel2.add(hSpacer1, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
					GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
					new Insets(0, 0, 0, 0), 0, 0));

				//---- romNameLabel ----
				romNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
				romNameLabel.setText(bundle.getString("GUI.noRomLoaded"));
				panel2.add(romNameLabel, new GridBagConstraints(0, 0, 1, 1, 0.1, 0.1,
					GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
					new Insets(5, 5, 5, 0), 0, 0));

				//---- romCodeLabel ----
				romCodeLabel.setText("");
				panel2.add(romCodeLabel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.1,
					GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(5, 5, 5, 0), 0, 0));

				//---- romSupportLabel ----
				romSupportLabel.setText("");
				panel2.add(romSupportLabel, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.1,
					GridBagConstraints.WEST, GridBagConstraints.NONE,
					new Insets(5, 5, 5, 0), 0, 0));
			}
			mainPanel.add(panel2, new GridBagConstraints(4, 1, 2, 3, 0.1, 0.0,
				GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//======== tabbedPane1 ========
			{
				tabbedPane1.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
				tabbedPane1.setTabPlacement(JTabbedPane.TOP);

				//======== panel3 ========
				{
					panel3.setLayout(new GridBagLayout());

					//======== panel4 ========
					{
						panel4.setBorder(new TitledBorder(null, bundle.getString("GUI.pbsPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel4.setLayout(new GridBagLayout());

						//---- pbsUnchangedRadioButton ----
						pbsUnchangedRadioButton.setEnabled(false);
						pbsUnchangedRadioButton.setText(bundle.getString("GUI.pbsUnchangedRadioButton.text"));
						pbsUnchangedRadioButton.setToolTipText(bundle.getString("GUI.pbsUnchangedRadioButton.toolTipText"));
						panel4.add(pbsUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel4.add(hSpacer2, new GridBagConstraints(6, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsShuffleRadioButton ----
						pbsShuffleRadioButton.setEnabled(false);
						pbsShuffleRadioButton.setText(bundle.getString("GUI.pbsShuffleRadioButton.text"));
						pbsShuffleRadioButton.setToolTipText(bundle.getString("GUI.pbsShuffleRadioButton.toolTipText"));
						panel4.add(pbsShuffleRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsLegendariesSlowRadioButton ----
						pbsLegendariesSlowRadioButton.setEnabled(false);
						pbsLegendariesSlowRadioButton.setText(bundle.getString("GUI.pbsLegendariesSlowRadioButton.text"));
						pbsLegendariesSlowRadioButton.setToolTipText(bundle.getString("GUI.pbsLegendariesSlowRadioButton.toolTipText"));
						panel4.add(pbsLegendariesSlowRadioButton, new GridBagConstraints(5, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsStrongLegendariesSlowRadioButton ----
						pbsStrongLegendariesSlowRadioButton.setEnabled(false);
						pbsStrongLegendariesSlowRadioButton.setText(bundle.getString("GUI.pbsStrongLegendariesSlowRadioButton.text"));
						pbsStrongLegendariesSlowRadioButton.setToolTipText(bundle.getString("GUI.pbsStrongLegendariesSlowRadioButton.toolTipText"));
						panel4.add(pbsStrongLegendariesSlowRadioButton, new GridBagConstraints(5, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsStandardizeEXPCurvesCheckBox ----
						pbsStandardizeEXPCurvesCheckBox.setEnabled(false);
						pbsStandardizeEXPCurvesCheckBox.setText(bundle.getString("GUI.pbsStandardizeEXPCurvesCheckBox.text"));
						pbsStandardizeEXPCurvesCheckBox.setToolTipText(bundle.getString("GUI.pbsStandardizeEXPCurvesCheckBox.toolTipText"));
						panel4.add(pbsStandardizeEXPCurvesCheckBox, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsFollowEvolutionsCheckBox ----
						pbsFollowEvolutionsCheckBox.setEnabled(false);
						pbsFollowEvolutionsCheckBox.setText(bundle.getString("GUI.pbsFollowEvolutionsCheckBox.text"));
						pbsFollowEvolutionsCheckBox.setToolTipText(bundle.getString("GUI.pbsFollowEvolutionsCheckBox.toolTipText"));
						panel4.add(pbsFollowEvolutionsCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel4.add(hSpacer3, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel4.add(vSpacer2, new GridBagConstraints(0, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel4.add(vSpacer3, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsUpdateBaseStatsCheckBox ----
						pbsUpdateBaseStatsCheckBox.setEnabled(false);
						pbsUpdateBaseStatsCheckBox.setText(bundle.getString("GUI.pbsUpdateBaseStatsCheckBox.text"));
						pbsUpdateBaseStatsCheckBox.setToolTipText(bundle.getString("GUI.pbsUpdateBaseStatsCheckBox.toolTipText"));
						panel4.add(pbsUpdateBaseStatsCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsFollowMegaEvosCheckBox ----
						pbsFollowMegaEvosCheckBox.setEnabled(false);
						pbsFollowMegaEvosCheckBox.setText(bundle.getString("GUI.pbsFollowMegaEvosCheckBox.text"));
						pbsFollowMegaEvosCheckBox.setToolTipText(bundle.getString("GUI.pbsFollowMegaEvosCheckBox.toolTipText"));
						panel4.add(pbsFollowMegaEvosCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsUpdateComboBox ----
						pbsUpdateComboBox.setEnabled(false);
						pbsUpdateComboBox.setModel(new DefaultComboBoxModel<>(new String[] {
							"--"
						}));
						panel4.add(pbsUpdateComboBox, new GridBagConstraints(3, 4, 1, 1, 0.6, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 5, 0, 0), 0, 0));

						//---- pbsEXPCurveComboBox ----
						pbsEXPCurveComboBox.setEnabled(false);
						pbsEXPCurveComboBox.setModel(new DefaultComboBoxModel<>(new String[] {
							"Medium Fast"
						}));
						panel4.add(pbsEXPCurveComboBox, new GridBagConstraints(4, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 20, 0, 5), 0, 0));

						//---- pbsAssignEvoStatsRandomlyCheckBox ----
						pbsAssignEvoStatsRandomlyCheckBox.setEnabled(false);
						pbsAssignEvoStatsRandomlyCheckBox.setText(bundle.getString("GUI.pbsAssignEvoStatsRandomlyCheckBox.text"));
						pbsAssignEvoStatsRandomlyCheckBox.setToolTipText(bundle.getString("GUI.pbsAssignEvoStatsRandomlyCheckBox.tooltipText"));
						panel4.add(pbsAssignEvoStatsRandomlyCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsRandomRadioButton ----
						pbsRandomRadioButton.setEnabled(false);
						pbsRandomRadioButton.setText(bundle.getString("GUI.pbsRandomRadioButton.text"));
						pbsRandomRadioButton.setToolTipText(bundle.getString("GUI.pbsRandomRadioButton.toolTipText"));
						panel4.add(pbsRandomRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pbsAllMediumFastRadioButton ----
						pbsAllMediumFastRadioButton.setEnabled(false);
						pbsAllMediumFastRadioButton.setText(bundle.getString("GUI.pbsAllMediumFastRadioButton.text"));
						pbsAllMediumFastRadioButton.setToolTipText(bundle.getString("GUI.pbsAllMediumFastRadioButton.toolTipText"));
						panel4.add(pbsAllMediumFastRadioButton, new GridBagConstraints(5, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel3.add(panel4, new GridBagConstraints(1, 1, 2, 1, 0.1, 0.0,
						GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
						new Insets(5, 0, 0, 0), 0, 0));
					panel3.add(hSpacer4, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel3.add(vSpacer4, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel3.add(hSpacer5, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== panel5 ========
					{
						panel5.setBorder(new TitledBorder(null, bundle.getString("GUI.ptPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel5.setLayout(new GridBagLayout());

						//---- ptUnchangedRadioButton ----
						ptUnchangedRadioButton.setEnabled(false);
						ptUnchangedRadioButton.setHideActionText(false);
						ptUnchangedRadioButton.setText(bundle.getString("GUI.ptUnchangedRadioButton.text"));
						ptUnchangedRadioButton.setToolTipText(bundle.getString("GUI.ptUnchangedRadioButton.toolTipText"));
						panel5.add(ptUnchangedRadioButton, new GridBagConstraints(1, 1, 2, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel5.add(hSpacer6, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel5.add(vSpacer5, new GridBagConstraints(2, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel5.add(hSpacer7, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel5.add(vSpacer6, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- ptRandomFollowEvolutionsRadioButton ----
						ptRandomFollowEvolutionsRadioButton.setEnabled(false);
						ptRandomFollowEvolutionsRadioButton.setText(bundle.getString("GUI.ptRandomFollowEvolutionsRadioButton.text"));
						ptRandomFollowEvolutionsRadioButton.setToolTipText(bundle.getString("GUI.ptRandomFollowEvolutionsRadioButton.toolTipText"));
						panel5.add(ptRandomFollowEvolutionsRadioButton, new GridBagConstraints(1, 2, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- ptRandomCompletelyRadioButton ----
						ptRandomCompletelyRadioButton.setEnabled(false);
						ptRandomCompletelyRadioButton.setText(bundle.getString("GUI.ptRandomCompletelyRadioButton.text"));
						ptRandomCompletelyRadioButton.setToolTipText(bundle.getString("GUI.ptRandomCompletelyRadioButton.toolTipText"));
						panel5.add(ptRandomCompletelyRadioButton, new GridBagConstraints(1, 3, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- ptFollowMegaEvosCheckBox ----
						ptFollowMegaEvosCheckBox.setEnabled(false);
						ptFollowMegaEvosCheckBox.setText(bundle.getString("GUI.ptFollowMegaEvosCheckBox.text"));
						ptFollowMegaEvosCheckBox.setToolTipText(bundle.getString("GUI.ptFollowMegaEvosCheckBox.toolTipText"));
						panel5.add(ptFollowMegaEvosCheckBox, new GridBagConstraints(1, 4, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- ptIsDualTypeCheckBox ----
						ptIsDualTypeCheckBox.setEnabled(false);
						ptIsDualTypeCheckBox.setSelected(false);
						ptIsDualTypeCheckBox.setText(bundle.getString("GUI.ptForceDualTypeCheckBox.text"));
						ptIsDualTypeCheckBox.setToolTipText(bundle.getString("GUI.ptForceDualTypeCheckBox.toolTipText"));
						panel5.add(ptIsDualTypeCheckBox, new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel3.add(panel5, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== pokemonAbilitiesPanel ========
					{
						pokemonAbilitiesPanel.setBorder(new TitledBorder(null, bundle.getString("GUI.paPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						pokemonAbilitiesPanel.setLayout(new GridBagLayout());

						//---- paUnchangedRadioButton ----
						paUnchangedRadioButton.setEnabled(false);
						paUnchangedRadioButton.setText(bundle.getString("GUI.paUnchangedRadioButton.text"));
						paUnchangedRadioButton.setToolTipText(bundle.getString("GUI.paUnchangedRadioButton.toolTipText"));
						pokemonAbilitiesPanel.add(paUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						pokemonAbilitiesPanel.add(hSpacer8, new GridBagConstraints(6, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						pokemonAbilitiesPanel.add(vSpacer7, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.1,
							GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						pokemonAbilitiesPanel.add(hSpacer9, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						pokemonAbilitiesPanel.add(vSpacer8, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- paRandomRadioButton ----
						paRandomRadioButton.setEnabled(false);
						paRandomRadioButton.setText(bundle.getString("GUI.paRandomRadioButton.text"));
						paRandomRadioButton.setToolTipText(bundle.getString("GUI.paRandomRadioButton.toolTipText"));
						pokemonAbilitiesPanel.add(paRandomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- paAllowWonderGuardCheckBox ----
						paAllowWonderGuardCheckBox.setEnabled(false);
						paAllowWonderGuardCheckBox.setText(bundle.getString("GUI.paAllowWonderGuardCheckBox.text"));
						paAllowWonderGuardCheckBox.setToolTipText(bundle.getString("GUI.paAllowWonderGuardCheckBox.toolTipText"));
						pokemonAbilitiesPanel.add(paAllowWonderGuardCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 5, 0, 0), 0, 0));

						//---- paFollowEvolutionsCheckBox ----
						paFollowEvolutionsCheckBox.setEnabled(false);
						paFollowEvolutionsCheckBox.setText(bundle.getString("GUI.paFollowEvolutionsCheckBox.text"));
						paFollowEvolutionsCheckBox.setToolTipText(bundle.getString("GUI.paFollowEvolutionsCheckBox.toolTipText"));
						pokemonAbilitiesPanel.add(paFollowEvolutionsCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 5, 0, 0), 0, 0));

						//---- label1 ----
						label1.setText(bundle.getString("GUI.paBanLabel.text"));
						pokemonAbilitiesPanel.add(label1, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.EAST, GridBagConstraints.NONE,
							new Insets(5, 0, 0, 0), 0, 0));

						//---- paTrappingAbilitiesCheckBox ----
						paTrappingAbilitiesCheckBox.setEnabled(false);
						paTrappingAbilitiesCheckBox.setText(bundle.getString("GUI.paTrappingAbilitiesCheckBox.text"));
						paTrappingAbilitiesCheckBox.setToolTipText(bundle.getString("GUI.paTrappingAbilitiesCheckBox.toolTipText"));
						pokemonAbilitiesPanel.add(paTrappingAbilitiesCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 5, 0, 0), 0, 0));

						//---- paNegativeAbilitiesCheckBox ----
						paNegativeAbilitiesCheckBox.setEnabled(false);
						paNegativeAbilitiesCheckBox.setSelected(false);
						paNegativeAbilitiesCheckBox.setText(bundle.getString("GUI.paNegativeAbilitiesCheckBox.text"));
						paNegativeAbilitiesCheckBox.setToolTipText(bundle.getString("GUI.paNegativeAbilitiesCheckBox.toolTipText"));
						pokemonAbilitiesPanel.add(paNegativeAbilitiesCheckBox, new GridBagConstraints(3, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- paBadAbilitiesCheckBox ----
						paBadAbilitiesCheckBox.setEnabled(false);
						paBadAbilitiesCheckBox.setText(bundle.getString("GUI.paBadAbilitiesCheckBox.text"));
						paBadAbilitiesCheckBox.setToolTipText(bundle.getString("GUI.paBadAbilitiesCheckBox.toolTipText"));
						pokemonAbilitiesPanel.add(paBadAbilitiesCheckBox, new GridBagConstraints(4, 3, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- paWeighDuplicatesTogetherCheckBox ----
						paWeighDuplicatesTogetherCheckBox.setEnabled(false);
						paWeighDuplicatesTogetherCheckBox.setText(bundle.getString("GUI.paWeighDuplicatesTogetherCheckBox.text"));
						paWeighDuplicatesTogetherCheckBox.setToolTipText(bundle.getString("GUI.paWeighDuplicatesTogetherCheckBox.toolTipText"));
						pokemonAbilitiesPanel.add(paWeighDuplicatesTogetherCheckBox, new GridBagConstraints(3, 1, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- paEnsureTwoAbilitiesCheckbox ----
						paEnsureTwoAbilitiesCheckbox.setEnabled(false);
						paEnsureTwoAbilitiesCheckbox.setText(bundle.getString("GUI.paEnsureTwoAbilitiesCheckbox.text"));
						paEnsureTwoAbilitiesCheckbox.setToolTipText(bundle.getString("GUI.paEnsureTwoAbilitiesCheckbox.toolTipText"));
						pokemonAbilitiesPanel.add(paEnsureTwoAbilitiesCheckbox, new GridBagConstraints(5, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- paFollowMegaEvosCheckBox ----
						paFollowMegaEvosCheckBox.setEnabled(false);
						paFollowMegaEvosCheckBox.setText(bundle.getString("GUI.paFollowMegaEvosCheckBox.text"));
						paFollowMegaEvosCheckBox.setToolTipText(bundle.getString("GUI.paFollowMegaEvosCheckBox.toolTipText"));
						pokemonAbilitiesPanel.add(paFollowMegaEvosCheckBox, new GridBagConstraints(3, 2, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel3.add(pokemonAbilitiesPanel, new GridBagConstraints(2, 2, 1, 1, 0.9, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== panel6 ========
					{
						panel6.setBorder(new TitledBorder(null, bundle.getString("GUI.pePanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel6.setLayout(new GridBagLayout());

						//---- peUnchangedRadioButton ----
						peUnchangedRadioButton.setEnabled(false);
						peUnchangedRadioButton.setText(bundle.getString("GUI.peUnchangedRadioButton.text"));
						peUnchangedRadioButton.setToolTipText(bundle.getString("GUI.peUnchangedRadioButton.toolTipText"));
						panel6.add(peUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel6.add(hSpacer10, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel6.add(vSpacer9, new GridBagConstraints(1, 6, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel6.add(hSpacer11, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel6.add(vSpacer10, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peRandomRadioButton ----
						peRandomRadioButton.setEnabled(false);
						peRandomRadioButton.setText(bundle.getString("GUI.peRandomRadioButton.text"));
						peRandomRadioButton.setToolTipText(bundle.getString("GUI.peRandomRadioButton.toolTipText"));
						panel6.add(peRandomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peSimilarStrengthCheckBox ----
						peSimilarStrengthCheckBox.setEnabled(false);
						peSimilarStrengthCheckBox.setText(bundle.getString("GUI.peSimilarStrengthCheckBox.text"));
						peSimilarStrengthCheckBox.setToolTipText(bundle.getString("GUI.peSimilarStrengthCheckBox.toolTipText"));
						panel6.add(peSimilarStrengthCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.9, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peSameTypingCheckBox ----
						peSameTypingCheckBox.setEnabled(false);
						peSameTypingCheckBox.setText(bundle.getString("GUI.peSameTypingCheckBox.text"));
						peSameTypingCheckBox.setToolTipText(bundle.getString("GUI.peSameTypingCheckBox.toolTipText"));
						panel6.add(peSameTypingCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peLimitEvolutionsToThreeCheckBox ----
						peLimitEvolutionsToThreeCheckBox.setEnabled(false);
						peLimitEvolutionsToThreeCheckBox.setText(bundle.getString("GUI.peLimitEvolutionsToThreeCheckBox.text"));
						peLimitEvolutionsToThreeCheckBox.setToolTipText(bundle.getString("GUI.peLimitEvolutionsToThreeCheckBox.toolTipText"));
						panel6.add(peLimitEvolutionsToThreeCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peForceChangeCheckBox ----
						peForceChangeCheckBox.setEnabled(false);
						peForceChangeCheckBox.setText(bundle.getString("GUI.peForceChangeCheckBox.text"));
						peForceChangeCheckBox.setToolTipText(bundle.getString("GUI.peForceChangeCheckBox.toolTipText"));
						panel6.add(peForceChangeCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peChangeImpossibleEvosCheckBox ----
						peChangeImpossibleEvosCheckBox.setEnabled(false);
						peChangeImpossibleEvosCheckBox.setText(bundle.getString("GUI.peChangeImpossibleEvosCheckBox.text"));
						peChangeImpossibleEvosCheckBox.setToolTipText(bundle.getString("GUI.peChangeImpossibleEvosCheckBox.toolTipText"));
						panel6.add(peChangeImpossibleEvosCheckBox, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peMakeEvolutionsEasierCheckBox ----
						peMakeEvolutionsEasierCheckBox.setEnabled(false);
						peMakeEvolutionsEasierCheckBox.setText(bundle.getString("GUI.peMakeEvolutionsEasierCheckBox.text"));
						peMakeEvolutionsEasierCheckBox.setToolTipText(bundle.getString("GUI.peMakeEvolutionsEasierCheckBox.toolTipText"));
						panel6.add(peMakeEvolutionsEasierCheckBox, new GridBagConstraints(3, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peAllowAltFormesCheckBox ----
						peAllowAltFormesCheckBox.setEnabled(false);
						peAllowAltFormesCheckBox.setText(bundle.getString("GUI.peAllowAltFormesCheckBox.text"));
						peAllowAltFormesCheckBox.setToolTipText(bundle.getString("GUI.peAllowAltFormesCheckBox.toolTipText"));
						panel6.add(peAllowAltFormesCheckBox, new GridBagConstraints(2, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peRemoveTimeBasedEvolutionsCheckBox ----
						peRemoveTimeBasedEvolutionsCheckBox.setEnabled(false);
						peRemoveTimeBasedEvolutionsCheckBox.setText(bundle.getString("GUI.peRemoveTimeBasedEvolutions.text"));
						peRemoveTimeBasedEvolutionsCheckBox.setToolTipText(bundle.getString("GUI.peRemoveTimeBasedEvolutions.toolTipText"));
						panel6.add(peRemoveTimeBasedEvolutionsCheckBox, new GridBagConstraints(3, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- peRandomEveryLevelRadioButton ----
						peRandomEveryLevelRadioButton.setEnabled(false);
						peRandomEveryLevelRadioButton.setText(bundle.getString("GUI.peRandomEveryLevelRadioButton.text"));
						peRandomEveryLevelRadioButton.setToolTipText(bundle.getString("GUI.peRandomEveryLevelRadioButton.toolTipText"));
						panel6.add(peRandomEveryLevelRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel3.add(panel6, new GridBagConstraints(1, 3, 2, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					panel3.add(vSpacer11, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.pokemonTraitsPanel.title"), panel3);

				//======== panel7 ========
				{
					panel7.setLayout(new GridBagLayout());

					//======== panel8 ========
					{
						panel8.setBorder(new TitledBorder(null, bundle.getString("GUI.spPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel8.setLayout(new GridBagLayout());

						//---- spUnchangedRadioButton ----
						spUnchangedRadioButton.setEnabled(false);
						spUnchangedRadioButton.setText(bundle.getString("GUI.spUnchangedRadioButton.text"));
						spUnchangedRadioButton.setToolTipText(bundle.getString("GUI.spUnchangedRadioButton.toolTipText"));
						panel8.add(spUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel8.add(hSpacer12, new GridBagConstraints(6, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel8.add(vSpacer12, new GridBagConstraints(1, 5, 2, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel8.add(hSpacer13, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel8.add(vSpacer13, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- spCustomRadioButton ----
						spCustomRadioButton.setEnabled(false);
						spCustomRadioButton.setText(bundle.getString("GUI.spCustomRadioButton.text"));
						spCustomRadioButton.setToolTipText(bundle.getString("GUI.spCustomRadioButton.toolTipText"));
						panel8.add(spCustomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- spRandomCompletelyRadioButton ----
						spRandomCompletelyRadioButton.setEnabled(false);
						spRandomCompletelyRadioButton.setText(bundle.getString("GUI.spRandomCompletelyRadioButton.text"));
						spRandomCompletelyRadioButton.setToolTipText(bundle.getString("GUI.spRandomCompletelyRadioButton.toolTipText"));
						panel8.add(spRandomCompletelyRadioButton, new GridBagConstraints(1, 3, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- spRandomTwoEvosRadioButton ----
						spRandomTwoEvosRadioButton.setEnabled(false);
						spRandomTwoEvosRadioButton.setText(bundle.getString("GUI.spRandomTwoEvosRadioButton.text"));
						spRandomTwoEvosRadioButton.setToolTipText(bundle.getString("GUI.spRandomTwoEvosRadioButton.toolTipText"));
						panel8.add(spRandomTwoEvosRadioButton, new GridBagConstraints(1, 4, 4, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- spComboBox1 ----
						spComboBox1.setEnabled(false);
						spComboBox1.setModel(new DefaultComboBoxModel<>(new String[] {
							"--"
						}));
						panel8.add(spComboBox1, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- spComboBox2 ----
						spComboBox2.setEnabled(false);
						spComboBox2.setModel(new DefaultComboBoxModel<>(new String[] {
							"--"
						}));
						panel8.add(spComboBox2, new GridBagConstraints(3, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 5, 0, 0), 0, 0));

						//---- spComboBox3 ----
						spComboBox3.setEnabled(false);
						spComboBox3.setModel(new DefaultComboBoxModel<>(new String[] {
							"--"
						}));
						panel8.add(spComboBox3, new GridBagConstraints(4, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 5, 0, 5), 0, 0));

						//---- spRandomizeStarterHeldItemsCheckBox ----
						spRandomizeStarterHeldItemsCheckBox.setEnabled(false);
						spRandomizeStarterHeldItemsCheckBox.setText(bundle.getString("GUI.spRandomizeStarterHeldItemsCheckBox.text"));
						spRandomizeStarterHeldItemsCheckBox.setToolTipText(bundle.getString("GUI.spRandomizeStarterHeldItemsCheckBox.toolTipText"));
						panel8.add(spRandomizeStarterHeldItemsCheckBox, new GridBagConstraints(5, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- spBanBadItemsCheckBox ----
						spBanBadItemsCheckBox.setEnabled(false);
						spBanBadItemsCheckBox.setText(bundle.getString("GUI.spBanBadItemsCheckBox.text"));
						spBanBadItemsCheckBox.setToolTipText(bundle.getString("GUI.spBanBadItemsCheckBox.toolTipText"));
						panel8.add(spBanBadItemsCheckBox, new GridBagConstraints(5, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- spAllowAltFormesCheckBox ----
						spAllowAltFormesCheckBox.setEnabled(false);
						spAllowAltFormesCheckBox.setText(bundle.getString("GUI.spAllowAltFormesCheckBox.text"));
						spAllowAltFormesCheckBox.setToolTipText(bundle.getString("GUI.spAllowAltFormesCheckBox.toolTipText"));
						panel8.add(spAllowAltFormesCheckBox, new GridBagConstraints(5, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel7.add(panel8, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					panel7.add(hSpacer14, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel7.add(vSpacer14, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel7.add(hSpacer15, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel7.add(vSpacer15, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== panel9 ========
					{
						panel9.setBorder(new TitledBorder(null, bundle.getString("GUI.stpPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel9.setLayout(new GridBagLayout());

						//---- stpUnchangedRadioButton ----
						stpUnchangedRadioButton.setEnabled(false);
						stpUnchangedRadioButton.setText(bundle.getString("GUI.stpUnchangedRadioButton.text"));
						stpUnchangedRadioButton.setToolTipText(bundle.getString("GUI.stpUnchangedRadioButton.toolTipText"));
						panel9.add(stpUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel9.add(hSpacer16, new GridBagConstraints(5, 1, 1, 1, 6.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel9.add(vSpacer16, new GridBagConstraints(1, 6, 4, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel9.add(hSpacer17, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel9.add(vSpacer17, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpSwapLegendariesSwapStandardsRadioButton ----
						stpSwapLegendariesSwapStandardsRadioButton.setEnabled(false);
						stpSwapLegendariesSwapStandardsRadioButton.setText(bundle.getString("GUI.stpSwapLegendariesSwapStandardsRadioButton.text"));
						stpSwapLegendariesSwapStandardsRadioButton.setToolTipText(bundle.getString("GUI.stpSwapLegendariesSwapStandardsRadioButton.toolTipText"));
						panel9.add(stpSwapLegendariesSwapStandardsRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpRandomCompletelyRadioButton ----
						stpRandomCompletelyRadioButton.setEnabled(false);
						stpRandomCompletelyRadioButton.setText(bundle.getString("GUI.stpRandomCompletelyRadioButton.text"));
						stpRandomCompletelyRadioButton.setToolTipText(bundle.getString("GUI.stpRandomCompletelyRadioButton.toolTipText"));
						panel9.add(stpRandomCompletelyRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpRandomSimilarStrengthRadioButton ----
						stpRandomSimilarStrengthRadioButton.setEnabled(false);
						stpRandomSimilarStrengthRadioButton.setText(bundle.getString("GUI.stpRandomSimilarStrengthRadioButton.text"));
						stpRandomSimilarStrengthRadioButton.setToolTipText(bundle.getString("GUI.stpRandomSimilarStrengthRadioButton.toolTipText"));
						panel9.add(stpRandomSimilarStrengthRadioButton, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpSwapMegaEvosCheckBox ----
						stpSwapMegaEvosCheckBox.setEnabled(false);
						stpSwapMegaEvosCheckBox.setText(bundle.getString("GUI.stpSwapMegaEvosCheckBox.text"));
						stpSwapMegaEvosCheckBox.setToolTipText(bundle.getString("GUI.stpSwapMegaEvosCheckBox.toolTipText"));
						panel9.add(stpSwapMegaEvosCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpPercentageLevelModifierCheckBox ----
						stpPercentageLevelModifierCheckBox.setEnabled(false);
						stpPercentageLevelModifierCheckBox.setText(bundle.getString("GUI.stpPercentageLevelModifierCheckBox.text"));
						stpPercentageLevelModifierCheckBox.setToolTipText(bundle.getString("GUI.stpPercentageLevelModifierCheckBox.tooltipText"));
						panel9.add(stpPercentageLevelModifierCheckBox, new GridBagConstraints(4, 1, 1, 1, 6.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpPercentageLevelModifierSlider ----
						stpPercentageLevelModifierSlider.setEnabled(false);
						stpPercentageLevelModifierSlider.setMajorTickSpacing(10);
						stpPercentageLevelModifierSlider.setMaximum(50);
						stpPercentageLevelModifierSlider.setMinimum(-50);
						stpPercentageLevelModifierSlider.setMinorTickSpacing(2);
						stpPercentageLevelModifierSlider.setPaintLabels(true);
						stpPercentageLevelModifierSlider.setPaintTicks(true);
						stpPercentageLevelModifierSlider.setSnapToTicks(true);
						stpPercentageLevelModifierSlider.setValue(0);
						panel9.add(stpPercentageLevelModifierSlider, new GridBagConstraints(4, 2, 1, 3, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpRandomize600BSTCheckBox ----
						stpRandomize600BSTCheckBox.setEnabled(false);
						stpRandomize600BSTCheckBox.setText(bundle.getString("GUI.stpRandomize600BSTCheckBox.text"));
						stpRandomize600BSTCheckBox.setToolTipText(bundle.getString("GUI.stpRandomize600BSTCheckBox.toolTipText"));
						panel9.add(stpRandomize600BSTCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.8, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpAllowAltFormesCheckBox ----
						stpAllowAltFormesCheckBox.setEnabled(false);
						stpAllowAltFormesCheckBox.setText(bundle.getString("GUI.stpAllowAltFormesCheckBox.text"));
						stpAllowAltFormesCheckBox.setToolTipText(bundle.getString("GUI.stpAllowAltFormesCheckBox.toolTipText"));
						panel9.add(stpAllowAltFormesCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpLimitMainGameLegendariesCheckBox ----
						stpLimitMainGameLegendariesCheckBox.setEnabled(false);
						stpLimitMainGameLegendariesCheckBox.setText(bundle.getString("GUI.stpLimitMainGameLegendariesCheckBox.text"));
						stpLimitMainGameLegendariesCheckBox.setToolTipText(bundle.getString("GUI.stpLimitMainGameLegendariesCheckBox.toolTipText"));
						panel9.add(stpLimitMainGameLegendariesCheckBox, new GridBagConstraints(2, 2, 1, 1, 1.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- stpFixMusicCheckBox ----
						stpFixMusicCheckBox.setEnabled(false);
						stpFixMusicCheckBox.setText(bundle.getString("GUI.stpFixMusicAllCheckBox.text"));
						stpFixMusicCheckBox.setToolTipText(bundle.getString("GUI.stpFixMusicAllCheckBox.toolTipText"));
						panel9.add(stpFixMusicCheckBox, new GridBagConstraints(2, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel7.add(panel9, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== panel10 ========
					{
						panel10.setBorder(new TitledBorder(null, bundle.getString("GUI.igtPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel10.setLayout(new GridBagLayout());

						//---- igtUnchangedRadioButton ----
						igtUnchangedRadioButton.setEnabled(false);
						igtUnchangedRadioButton.setText(bundle.getString("GUI.igtUnchangedRadioButton.text"));
						igtUnchangedRadioButton.setToolTipText(bundle.getString("GUI.igtUnchangedRadioButton.toolTipText"));
						panel10.add(igtUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel10.add(hSpacer18, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel10.add(vSpacer18, new GridBagConstraints(1, 5, 2, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- igtRandomizeGivenPokemonOnlyRadioButton ----
						igtRandomizeGivenPokemonOnlyRadioButton.setEnabled(false);
						igtRandomizeGivenPokemonOnlyRadioButton.setText(bundle.getString("GUI.igtRandomizeGivenPokemonOnlyRadioButton.text"));
						igtRandomizeGivenPokemonOnlyRadioButton.setToolTipText(bundle.getString("GUI.igtRandomizeGivenPokemonOnlyRadioButton.toolTipText"));
						panel10.add(igtRandomizeGivenPokemonOnlyRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- igtRandomizeBothRequestedGivenRadioButton ----
						igtRandomizeBothRequestedGivenRadioButton.setEnabled(false);
						igtRandomizeBothRequestedGivenRadioButton.setText(bundle.getString("GUI.igtRandomizeBothRequestedGivenRadioButton.text"));
						igtRandomizeBothRequestedGivenRadioButton.setToolTipText(bundle.getString("GUI.igtRandomizeBothRequestedGivenRadioButton.toolTipText"));
						panel10.add(igtRandomizeBothRequestedGivenRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel10.add(hSpacer19, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel10.add(vSpacer19, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- igtRandomizeNicknamesCheckBox ----
						igtRandomizeNicknamesCheckBox.setEnabled(false);
						igtRandomizeNicknamesCheckBox.setText(bundle.getString("GUI.igtRandomizeNicknamesCheckBox.text"));
						igtRandomizeNicknamesCheckBox.setToolTipText(bundle.getString("GUI.igtRandomizeNicknamesCheckBox.toolTipText"));
						panel10.add(igtRandomizeNicknamesCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.8, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- igtRandomizeOTsCheckBox ----
						igtRandomizeOTsCheckBox.setEnabled(false);
						igtRandomizeOTsCheckBox.setText(bundle.getString("GUI.igtRandomizeOTsCheckBox.text"));
						igtRandomizeOTsCheckBox.setToolTipText(bundle.getString("GUI.igtRandomizeOTsCheckBox.toolTipText"));
						panel10.add(igtRandomizeOTsCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- igtRandomizeIVsCheckBox ----
						igtRandomizeIVsCheckBox.setEnabled(false);
						igtRandomizeIVsCheckBox.setText(bundle.getString("GUI.igtRandomizeIVsCheckBox.text"));
						igtRandomizeIVsCheckBox.setToolTipText(bundle.getString("GUI.igtRandomizeIVsCheckBox.toolTipText"));
						panel10.add(igtRandomizeIVsCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- igtRandomizeItemsCheckBox ----
						igtRandomizeItemsCheckBox.setEnabled(false);
						igtRandomizeItemsCheckBox.setText(bundle.getString("GUI.igtRandomizeItemsCheckBox.text"));
						igtRandomizeItemsCheckBox.setToolTipText(bundle.getString("GUI.igtRandomizeItemsCheckBox.toolTipText"));
						panel10.add(igtRandomizeItemsCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel7.add(panel10, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.startersStaticsTradesPanel"), panel7);

				//======== panel11 ========
				{
					panel11.setLayout(new GridBagLayout());

					//======== panel12 ========
					{
						panel12.setBorder(new TitledBorder(null, bundle.getString("GUI.mdPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel12.setLayout(new GridBagLayout());

						//---- mdRandomizeMovePowerCheckBox ----
						mdRandomizeMovePowerCheckBox.setEnabled(false);
						mdRandomizeMovePowerCheckBox.setText(bundle.getString("GUI.mdRandomizeMovePowerCheckBox.text"));
						mdRandomizeMovePowerCheckBox.setToolTipText(bundle.getString("GUI.mdRandomizeMovePowerCheckBox.toolTipText"));
						panel12.add(mdRandomizeMovePowerCheckBox, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel12.add(hSpacer20, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel12.add(vSpacer20, new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel12.add(hSpacer21, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel12.add(vSpacer21, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- mdRandomizeMoveAccuracyCheckBox ----
						mdRandomizeMoveAccuracyCheckBox.setEnabled(false);
						mdRandomizeMoveAccuracyCheckBox.setText(bundle.getString("GUI.mdRandomizeMoveAccuracyCheckBox.text"));
						mdRandomizeMoveAccuracyCheckBox.setToolTipText(bundle.getString("GUI.mdRandomizeMoveAccuracyCheckBox.toolTipText"));
						panel12.add(mdRandomizeMoveAccuracyCheckBox, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- mdRandomizeMovePPCheckBox ----
						mdRandomizeMovePPCheckBox.setEnabled(false);
						mdRandomizeMovePPCheckBox.setText(bundle.getString("GUI.mdRandomizeMovePPCheckBox.text"));
						mdRandomizeMovePPCheckBox.setToolTipText(bundle.getString("GUI.mdRandomizeMovePPCheckBox.toolTipText"));
						panel12.add(mdRandomizeMovePPCheckBox, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- mdRandomizeMoveTypesCheckBox ----
						mdRandomizeMoveTypesCheckBox.setEnabled(false);
						mdRandomizeMoveTypesCheckBox.setText(bundle.getString("GUI.mdRandomizeMoveTypesCheckBox.text"));
						mdRandomizeMoveTypesCheckBox.setToolTipText(bundle.getString("GUI.mdRandomizeMoveTypesCheckBox.toolTipText"));
						panel12.add(mdRandomizeMoveTypesCheckBox, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- mdRandomizeMoveCategoryCheckBox ----
						mdRandomizeMoveCategoryCheckBox.setEnabled(false);
						mdRandomizeMoveCategoryCheckBox.setText(bundle.getString("GUI.mdRandomizeMoveCategoryCheckBox.text"));
						mdRandomizeMoveCategoryCheckBox.setToolTipText(bundle.getString("GUI.mdRandomizeMoveCategoryCheckBox.toolTipText"));
						panel12.add(mdRandomizeMoveCategoryCheckBox, new GridBagConstraints(2, 1, 2, 1, 0.6, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- mdUpdateMovesCheckBox ----
						mdUpdateMovesCheckBox.setEnabled(false);
						mdUpdateMovesCheckBox.setText(bundle.getString("GUI.mdUpdateMovesCheckBox.text"));
						mdUpdateMovesCheckBox.setToolTipText(bundle.getString("GUI.mdUpdateMovesCheckBox.toolTipText"));
						panel12.add(mdUpdateMovesCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- mdUpdateComboBox ----
						mdUpdateComboBox.setEnabled(false);
						mdUpdateComboBox.setModel(new DefaultComboBoxModel<>(new String[] {
							"--"
						}));
						panel12.add(mdUpdateComboBox, new GridBagConstraints(3, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 5, 0, 0), 0, 0));
					}
					panel11.add(panel12, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					panel11.add(hSpacer22, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel11.add(vSpacer22, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel11.add(hSpacer23, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel11.add(vSpacer23, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== panel13 ========
					{
						panel13.setBorder(new TitledBorder(null, bundle.getString("GUI.pmsPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel13.setLayout(new GridBagLayout());

						//---- pmsUnchangedRadioButton ----
						pmsUnchangedRadioButton.setEnabled(false);
						pmsUnchangedRadioButton.setText(bundle.getString("GUI.pmsUnchangedRadioButton.text"));
						pmsUnchangedRadioButton.setToolTipText(bundle.getString("GUI.pmsUnchangedRadioButton.toolTipText"));
						panel13.add(pmsUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel13.add(hSpacer24, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel13.add(vSpacer24, new GridBagConstraints(1, 7, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel13.add(hSpacer25, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel13.add(vSpacer25, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsRandomPreferringSameTypeRadioButton ----
						pmsRandomPreferringSameTypeRadioButton.setEnabled(false);
						pmsRandomPreferringSameTypeRadioButton.setText(bundle.getString("GUI.pmsRandomPreferringSameTypeRadioButton.text"));
						pmsRandomPreferringSameTypeRadioButton.setToolTipText(bundle.getString("GUI.pmsRandomPreferringSameTypeRadioButton.toolTipText"));
						panel13.add(pmsRandomPreferringSameTypeRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsRandomCompletelyRadioButton ----
						pmsRandomCompletelyRadioButton.setEnabled(false);
						pmsRandomCompletelyRadioButton.setText(bundle.getString("GUI.pmsRandomCompletelyRadioButton.text"));
						pmsRandomCompletelyRadioButton.setToolTipText(bundle.getString("GUI.pmsRandomCompletelyRadioButton.toolTipText"));
						panel13.add(pmsRandomCompletelyRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsMetronomeOnlyModeRadioButton ----
						pmsMetronomeOnlyModeRadioButton.setEnabled(false);
						pmsMetronomeOnlyModeRadioButton.setText(bundle.getString("GUI.pmsMetronomeOnlyModeRadioButton.text"));
						pmsMetronomeOnlyModeRadioButton.setToolTipText(bundle.getString("GUI.pmsMetronomeOnlyModeRadioButton.toolTipText"));
						panel13.add(pmsMetronomeOnlyModeRadioButton, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsGuaranteedLevel1MovesCheckBox ----
						pmsGuaranteedLevel1MovesCheckBox.setEnabled(false);
						pmsGuaranteedLevel1MovesCheckBox.setText(bundle.getString("GUI.pmsGuaranteedLevel1MovesCheckBox.text"));
						pmsGuaranteedLevel1MovesCheckBox.setToolTipText(bundle.getString("GUI.pmsGuaranteedLevel1MovesCheckBox.toolTipText"));
						panel13.add(pmsGuaranteedLevel1MovesCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsReorderDamagingMovesCheckBox ----
						pmsReorderDamagingMovesCheckBox.setEnabled(false);
						pmsReorderDamagingMovesCheckBox.setText(bundle.getString("GUI.pmsReorderDamagingMovesCheckBox.text"));
						pmsReorderDamagingMovesCheckBox.setToolTipText(bundle.getString("GUI.pmsReorderDamagingMovesCheckBox.toolTipText"));
						panel13.add(pmsReorderDamagingMovesCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsNoGameBreakingMovesCheckBox ----
						pmsNoGameBreakingMovesCheckBox.setEnabled(false);
						pmsNoGameBreakingMovesCheckBox.setText(bundle.getString("GUI.pmsNoGameBreakingMovesCheckBox.text"));
						pmsNoGameBreakingMovesCheckBox.setToolTipText(bundle.getString("GUI.pmsNoGameBreakingMovesCheckBox.toolTipText"));
						panel13.add(pmsNoGameBreakingMovesCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsForceGoodDamagingCheckBox ----
						pmsForceGoodDamagingCheckBox.setEnabled(false);
						pmsForceGoodDamagingCheckBox.setText(bundle.getString("GUI.pmsForceGoodDamagingCheckBox.text"));
						pmsForceGoodDamagingCheckBox.setToolTipText(bundle.getString("GUI.pmsForceGoodDamagingCheckBox.toolTipText"));
						panel13.add(pmsForceGoodDamagingCheckBox, new GridBagConstraints(2, 4, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsGuaranteedLevel1MovesSlider ----
						pmsGuaranteedLevel1MovesSlider.setEnabled(false);
						pmsGuaranteedLevel1MovesSlider.setMajorTickSpacing(1);
						pmsGuaranteedLevel1MovesSlider.setMaximum(4);
						pmsGuaranteedLevel1MovesSlider.setMinimum(2);
						pmsGuaranteedLevel1MovesSlider.setPaintLabels(true);
						pmsGuaranteedLevel1MovesSlider.setPaintTicks(false);
						pmsGuaranteedLevel1MovesSlider.setSnapToTicks(true);
						pmsGuaranteedLevel1MovesSlider.setToolTipText(bundle.getString("GUI.pmsGuaranteedLevel1MovesSlider.toolTipText"));
						pmsGuaranteedLevel1MovesSlider.setValue(2);
						pmsGuaranteedLevel1MovesSlider.setValueIsAdjusting(false);
						panel13.add(pmsGuaranteedLevel1MovesSlider, new GridBagConstraints(3, 1, 1, 2, 0.0, 0.0,
							GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
							new Insets(0, 10, 0, 0), 0, 0));

						//---- pmsForceGoodDamagingSlider ----
						pmsForceGoodDamagingSlider.setEnabled(false);
						pmsForceGoodDamagingSlider.setMajorTickSpacing(20);
						pmsForceGoodDamagingSlider.setMinorTickSpacing(5);
						pmsForceGoodDamagingSlider.setPaintLabels(true);
						pmsForceGoodDamagingSlider.setPaintTicks(true);
						pmsForceGoodDamagingSlider.setSnapToTicks(true);
						pmsForceGoodDamagingSlider.setToolTipText(bundle.getString("GUI.pmsForceGoodDamagingSlider.toolTipText"));
						pmsForceGoodDamagingSlider.setValue(0);
						panel13.add(pmsForceGoodDamagingSlider, new GridBagConstraints(2, 5, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- pmsEvolutionMovesCheckBox ----
						pmsEvolutionMovesCheckBox.setEnabled(false);
						pmsEvolutionMovesCheckBox.setText(bundle.getString("GUI.pmsEvolutionMovesCheckBox.text"));
						pmsEvolutionMovesCheckBox.setToolTipText(bundle.getString("GUI.pmsEvolutionMovesCheckBox.toolTipText"));
						panel13.add(pmsEvolutionMovesCheckBox, new GridBagConstraints(2, 6, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel11.add(panel13, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.movesMovesetsPanel"), panel11);

				//======== panel14 ========
				{
					panel14.setLayout(new GridBagLayout());

					//======== panel15 ========
					{
						panel15.setBorder(new TitledBorder(null, bundle.getString("GUI.tpPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel15.setLayout(new GridBagLayout());
						panel15.add(hSpacer26, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel15.add(vSpacer26, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpRivalCarriesStarterCheckBox ----
						tpRivalCarriesStarterCheckBox.setEnabled(false);
						tpRivalCarriesStarterCheckBox.setText(bundle.getString("GUI.tpRivalCarriesStarterCheckBox.text"));
						tpRivalCarriesStarterCheckBox.setToolTipText(bundle.getString("GUI.tpRivalCarriesStarterCheckBox.toolTipText"));
						panel15.add(tpRivalCarriesStarterCheckBox, new GridBagConstraints(5, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpSimilarStrengthCheckBox ----
						tpSimilarStrengthCheckBox.setEnabled(false);
						tpSimilarStrengthCheckBox.setText(bundle.getString("GUI.tpSimilarStrengthCheckBox.text"));
						tpSimilarStrengthCheckBox.setToolTipText(bundle.getString("GUI.tpSimilarStrengthCheckBox.toolTipText"));
						panel15.add(tpSimilarStrengthCheckBox, new GridBagConstraints(5, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpRandomizeTrainerNamesCheckBox ----
						tpRandomizeTrainerNamesCheckBox.setEnabled(false);
						tpRandomizeTrainerNamesCheckBox.setText(bundle.getString("GUI.tpRandomizeTrainerNamesCheckBox.text"));
						tpRandomizeTrainerNamesCheckBox.setToolTipText(bundle.getString("GUI.tpRandomizeTrainerNamesCheckBox.toolTipText"));
						panel15.add(tpRandomizeTrainerNamesCheckBox, new GridBagConstraints(6, 1, 2, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpRandomizeTrainerClassNamesCheckBox ----
						tpRandomizeTrainerClassNamesCheckBox.setEnabled(false);
						tpRandomizeTrainerClassNamesCheckBox.setText(bundle.getString("GUI.tpRandomizeTrainerClassNamesCheckBox.text"));
						tpRandomizeTrainerClassNamesCheckBox.setToolTipText(bundle.getString("GUI.tpRandomizeTrainerClassNamesCheckBox.toolTipText"));
						panel15.add(tpRandomizeTrainerClassNamesCheckBox, new GridBagConstraints(6, 2, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpForceFullyEvolvedAtCheckBox ----
						tpForceFullyEvolvedAtCheckBox.setEnabled(false);
						tpForceFullyEvolvedAtCheckBox.setText(bundle.getString("GUI.tpForceFullyEvolvedAtCheckBox.text"));
						tpForceFullyEvolvedAtCheckBox.setToolTipText(bundle.getString("GUI.tpForceFullyEvolvedAtCheckBox.toolTipText"));
						panel15.add(tpForceFullyEvolvedAtCheckBox, new GridBagConstraints(6, 4, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpForceFullyEvolvedAtSlider ----
						tpForceFullyEvolvedAtSlider.setEnabled(false);
						tpForceFullyEvolvedAtSlider.setMajorTickSpacing(5);
						tpForceFullyEvolvedAtSlider.setMaximum(65);
						tpForceFullyEvolvedAtSlider.setMinimum(30);
						tpForceFullyEvolvedAtSlider.setMinorTickSpacing(1);
						tpForceFullyEvolvedAtSlider.setPaintLabels(true);
						tpForceFullyEvolvedAtSlider.setPaintTicks(true);
						tpForceFullyEvolvedAtSlider.setSnapToTicks(true);
						tpForceFullyEvolvedAtSlider.setToolTipText(bundle.getString("GUI.tpForceFullyEvolvedAtSlider.toolTipText"));
						tpForceFullyEvolvedAtSlider.setValue(30);
						panel15.add(tpForceFullyEvolvedAtSlider, new GridBagConstraints(6, 5, 2, 2, 0.0, 0.0,
							GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpPercentageLevelModifierSlider ----
						tpPercentageLevelModifierSlider.setEnabled(false);
						tpPercentageLevelModifierSlider.setMajorTickSpacing(10);
						tpPercentageLevelModifierSlider.setMaximum(50);
						tpPercentageLevelModifierSlider.setMinimum(-50);
						tpPercentageLevelModifierSlider.setMinorTickSpacing(2);
						tpPercentageLevelModifierSlider.setPaintLabels(true);
						tpPercentageLevelModifierSlider.setPaintTicks(true);
						tpPercentageLevelModifierSlider.setSnapToTicks(true);
						tpPercentageLevelModifierSlider.setToolTipText(bundle.getString("GUI.tpPercentageLevelModifierSlider.toolTipText"));
						tpPercentageLevelModifierSlider.setValue(0);
						panel15.add(tpPercentageLevelModifierSlider, new GridBagConstraints(6, 8, 2, 2, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpPercentageLevelModifierCheckBox ----
						tpPercentageLevelModifierCheckBox.setEnabled(false);
						tpPercentageLevelModifierCheckBox.setText(bundle.getString("GUI.tpPercentageLevelModifierCheckBox.text"));
						tpPercentageLevelModifierCheckBox.setToolTipText(bundle.getString("GUI.tpPercentageLevelModifierCheckBox.toolTipText"));
						panel15.add(tpPercentageLevelModifierCheckBox, new GridBagConstraints(6, 7, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel15.add(hSpacer27, new GridBagConstraints(8, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpWeightTypesCheckBox ----
						tpWeightTypesCheckBox.setEnabled(false);
						tpWeightTypesCheckBox.setText(bundle.getString("GUI.tpWeightTypesCheckBox.text"));
						tpWeightTypesCheckBox.setToolTipText(bundle.getString("GUI.tpWeightTypesCheckBox.toolTipText"));
						panel15.add(tpWeightTypesCheckBox, new GridBagConstraints(5, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpDontUseLegendariesCheckBox ----
						tpDontUseLegendariesCheckBox.setEnabled(false);
						tpDontUseLegendariesCheckBox.setText(bundle.getString("GUI.tpDontUseLegendariesCheckBox.text"));
						panel15.add(tpDontUseLegendariesCheckBox, new GridBagConstraints(5, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpNoEarlyWonderGuardCheckBox ----
						tpNoEarlyWonderGuardCheckBox.setEnabled(false);
						tpNoEarlyWonderGuardCheckBox.setText(bundle.getString("GUI.tpNoEarlyWonderGuardCheckBox.text"));
						tpNoEarlyWonderGuardCheckBox.setToolTipText(bundle.getString("GUI.tpNoEarlyWonderGuardCheckBox.toolTipText"));
						panel15.add(tpNoEarlyWonderGuardCheckBox, new GridBagConstraints(5, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpAllowAlternateFormesCheckBox ----
						tpAllowAlternateFormesCheckBox.setEnabled(false);
						tpAllowAlternateFormesCheckBox.setText(bundle.getString("GUI.tpAllowAlternateFormesCheckBox.text"));
						tpAllowAlternateFormesCheckBox.setToolTipText(bundle.getString("GUI.tpAllowAlternateFormesCheckBox.toolTipText"));
						panel15.add(tpAllowAlternateFormesCheckBox, new GridBagConstraints(5, 6, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpSwapMegaEvosCheckBox ----
						tpSwapMegaEvosCheckBox.setEnabled(false);
						tpSwapMegaEvosCheckBox.setText(bundle.getString("GUI.tpSwapMegaEvosCheckBox.text"));
						tpSwapMegaEvosCheckBox.setToolTipText(bundle.getString("GUI.tpSwapMegaEvosCheckBox.toolTipText"));
						panel15.add(tpSwapMegaEvosCheckBox, new GridBagConstraints(5, 7, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpRandomShinyTrainerPokemonCheckBox ----
						tpRandomShinyTrainerPokemonCheckBox.setEnabled(false);
						tpRandomShinyTrainerPokemonCheckBox.setText(bundle.getString("GUI.tpRandomShinyTrainerPokemonCheckBox.text"));
						tpRandomShinyTrainerPokemonCheckBox.setToolTipText(bundle.getString("GUI.tpRandomShinyTrainerPokemonCheckBox.toolTipText"));
						panel15.add(tpRandomShinyTrainerPokemonCheckBox, new GridBagConstraints(5, 8, 1, 1, 0.0, 0.0,
							GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel15.add(vSpacer27, new GridBagConstraints(0, 11, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpEliteFourUniquePokemonCheckBox ----
						tpEliteFourUniquePokemonCheckBox.setEnabled(false);
						tpEliteFourUniquePokemonCheckBox.setText(bundle.getString("GUI.tpEliteFourUniquePokemonCheckBox.text"));
						tpEliteFourUniquePokemonCheckBox.setToolTipText(bundle.getString("GUI.tpEliteFourUniquePokemonCheckBox.toolTipText"));
						panel15.add(tpEliteFourUniquePokemonCheckBox, new GridBagConstraints(6, 10, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpEliteFourUniquePokemonSpinner ----
						tpEliteFourUniquePokemonSpinner.setEnabled(false);
						panel15.add(tpEliteFourUniquePokemonSpinner, new GridBagConstraints(7, 10, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpComboBox ----
						tpComboBox.setEnabled(false);
						tpComboBox.setModel(new DefaultComboBoxModel<>(new String[] {
							"Unchanged"
						}));
						panel15.add(tpComboBox, new GridBagConstraints(1, 1, 3, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpDoubleBattleModeCheckBox ----
						tpDoubleBattleModeCheckBox.setEnabled(false);
						tpDoubleBattleModeCheckBox.setText(bundle.getString("GUI.tpDoubleBattleModeCheckBox.text"));
						tpDoubleBattleModeCheckBox.setToolTipText(bundle.getString("GUI.tpDoubleBattleModeCheckBox.toolTipText"));
						panel15.add(tpDoubleBattleModeCheckBox, new GridBagConstraints(1, 2, 4, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpAdditionalPokemonForLabel ----
						tpAdditionalPokemonForLabel.setText(bundle.getString("GUI.tpAddMorePokemonForLabel.text"));
						panel15.add(tpAdditionalPokemonForLabel, new GridBagConstraints(1, 4, 4, 1, 0.0, 0.0,
							GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpBossTrainersCheckBox ----
						tpBossTrainersCheckBox.setEnabled(false);
						tpBossTrainersCheckBox.setText(bundle.getString("GUI.tpBossTrainersCheckBox.text"));
						tpBossTrainersCheckBox.setToolTipText(bundle.getString("GUI.tpBossTrainersCheckBox.toolTipText"));
						panel15.add(tpBossTrainersCheckBox, new GridBagConstraints(1, 5, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpImportantTrainersCheckBox ----
						tpImportantTrainersCheckBox.setEnabled(false);
						tpImportantTrainersCheckBox.setText(bundle.getString("GUI.tpImportantTrainersCheckBox.text"));
						tpImportantTrainersCheckBox.setToolTipText(bundle.getString("GUI.tpImportantTrainersCheckBox.toolTipText"));
						panel15.add(tpImportantTrainersCheckBox, new GridBagConstraints(3, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpRegularTrainersCheckBox ----
						tpRegularTrainersCheckBox.setEnabled(false);
						tpRegularTrainersCheckBox.setText(bundle.getString("GUI.tpRegularTrainersCheckBox.text"));
						tpRegularTrainersCheckBox.setToolTipText(bundle.getString("GUI.tpRegularTrainersCheckBox.toolTipText"));
						panel15.add(tpRegularTrainersCheckBox, new GridBagConstraints(4, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpBossTrainersSpinner ----
						tpBossTrainersSpinner.setEnabled(false);
						panel15.add(tpBossTrainersSpinner, new GridBagConstraints(1, 6, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 4, 0, 0), 10, 0));

						//---- tpImportantTrainersSpinner ----
						tpImportantTrainersSpinner.setEnabled(false);
						panel15.add(tpImportantTrainersSpinner, new GridBagConstraints(3, 6, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 4, 0, 0), 10, 0));

						//---- tpRegularTrainersSpinner ----
						tpRegularTrainersSpinner.setEnabled(false);
						panel15.add(tpRegularTrainersSpinner, new GridBagConstraints(4, 6, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 4, 0, 0), 10, 0));

						//---- tpHeldItemsLabel ----
						tpHeldItemsLabel.setText(bundle.getString("GUI.tpHeldItemsLabel.text"));
						panel15.add(tpHeldItemsLabel, new GridBagConstraints(1, 8, 4, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpBossTrainersItemsCheckBox ----
						tpBossTrainersItemsCheckBox.setEnabled(false);
						tpBossTrainersItemsCheckBox.setText(bundle.getString("GUI.tpBossTrainersItemsCheckBox.text"));
						tpBossTrainersItemsCheckBox.setToolTipText(bundle.getString("GUI.tpBossTrainersItemsCheckBox.toolTipText"));
						panel15.add(tpBossTrainersItemsCheckBox, new GridBagConstraints(1, 9, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpImportantTrainersItemsCheckBox ----
						tpImportantTrainersItemsCheckBox.setEnabled(false);
						tpImportantTrainersItemsCheckBox.setText(bundle.getString("GUI.tpImportantTrainersItemsCheckBox.text"));
						tpImportantTrainersItemsCheckBox.setToolTipText(bundle.getString("GUI.tpImportantTrainersItemsCheckBox.toolTipText"));
						panel15.add(tpImportantTrainersItemsCheckBox, new GridBagConstraints(3, 9, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpRegularTrainersItemsCheckBox ----
						tpRegularTrainersItemsCheckBox.setEnabled(false);
						tpRegularTrainersItemsCheckBox.setText(bundle.getString("GUI.tpRegularTrainersItemsCheckBox.text"));
						tpRegularTrainersItemsCheckBox.setToolTipText(bundle.getString("GUI.tpRegularTrainersItemsCheckBox.toolTipText"));
						panel15.add(tpRegularTrainersItemsCheckBox, new GridBagConstraints(4, 9, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpConsumableItemsOnlyCheckBox ----
						tpConsumableItemsOnlyCheckBox.setEnabled(false);
						tpConsumableItemsOnlyCheckBox.setText(bundle.getString("GUI.tpConsumableItemsOnlyCheckBox.text"));
						tpConsumableItemsOnlyCheckBox.setToolTipText(bundle.getString("GUI.tpConsumableItemsOnlyCheckBox.tooltip"));
						panel15.add(tpConsumableItemsOnlyCheckBox, new GridBagConstraints(1, 10, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpSensibleItemsCheckBox ----
						tpSensibleItemsCheckBox.setEnabled(false);
						tpSensibleItemsCheckBox.setText(bundle.getString("GUI.tpSensibleItemsCheckBox.text"));
						tpSensibleItemsCheckBox.setToolTipText(bundle.getString("GUI.tpSensibleItemsCheckBox.tooltip"));
						panel15.add(tpSensibleItemsCheckBox, new GridBagConstraints(3, 10, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpHighestLevelGetsItemCheckBox ----
						tpHighestLevelGetsItemCheckBox.setEnabled(false);
						tpHighestLevelGetsItemCheckBox.setText(bundle.getString("GUI.tpHighestLevelGetsItemCheckBox.text"));
						tpHighestLevelGetsItemCheckBox.setToolTipText(bundle.getString("GUI.tpHighestLevelGetsItemCheckBox.tooltip"));
						panel15.add(tpHighestLevelGetsItemCheckBox, new GridBagConstraints(4, 10, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- tpBetterMovesetsCheckBox ----
						tpBetterMovesetsCheckBox.setEnabled(false);
						tpBetterMovesetsCheckBox.setText(bundle.getString("GUI.tpBetterMovesetsCheckBox.text"));
						tpBetterMovesetsCheckBox.setToolTipText(bundle.getString("GUI.tpBetterMovesetsCheckBox.toolTipText"));
						panel15.add(tpBetterMovesetsCheckBox, new GridBagConstraints(1, 3, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel14.add(panel15, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					panel14.add(hSpacer28, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel14.add(vSpacer28, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel14.add(hSpacer29, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel14.add(vSpacer29, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== totpPanel ========
					{
						totpPanel.setBorder(new TitledBorder(null, bundle.getString("GUI.totpPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						totpPanel.setLayout(new GridBagLayout());

						//---- totpUnchangedRadioButton ----
						totpUnchangedRadioButton.setEnabled(false);
						totpUnchangedRadioButton.setText(bundle.getString("GUI.totpUnchangedRadioButton.text"));
						totpUnchangedRadioButton.setToolTipText(bundle.getString("GUI.totpUnchangedRadioButton.toolTipText"));
						totpPanel.add(totpUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.01, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						totpPanel.add(hSpacer30, new GridBagConstraints(5, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						totpPanel.add(vSpacer30, new GridBagConstraints(1, 5, 2, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						totpPanel.add(hSpacer31, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						totpPanel.add(vSpacer31, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//======== totpAllyPanel ========
						{
							totpAllyPanel.setBorder(new TitledBorder(bundle.getString("GUI.totpAllyPanel.title")));
							totpAllyPanel.setLayout(new GridBagLayout());

							//---- totpAllyUnchangedRadioButton ----
							totpAllyUnchangedRadioButton.setEnabled(false);
							totpAllyUnchangedRadioButton.setText(bundle.getString("GUI.totpAllyUnchangedRadioButton.text"));
							totpAllyUnchangedRadioButton.setToolTipText(bundle.getString("GUI.totpAllyUnchangedRadioButton.toolTipText"));
							totpAllyPanel.add(totpAllyUnchangedRadioButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- totpAllyRandomRadioButton ----
							totpAllyRandomRadioButton.setEnabled(false);
							totpAllyRandomRadioButton.setText(bundle.getString("GUI.totpAllyRandomRadioButton.text"));
							totpAllyRandomRadioButton.setToolTipText(bundle.getString("GUI.totpAllyRandomRadioButton.toolTipText"));
							totpAllyPanel.add(totpAllyRandomRadioButton, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- totpAllyRandomSimilarStrengthRadioButton ----
							totpAllyRandomSimilarStrengthRadioButton.setEnabled(false);
							totpAllyRandomSimilarStrengthRadioButton.setText(bundle.getString("GUI.totpAllyRandomSimilarStrengthRadioButton.text"));
							totpAllyRandomSimilarStrengthRadioButton.setToolTipText(bundle.getString("GUI.totpAllyRandomSimilarStrengthRadioButton.toolTipText"));
							totpAllyPanel.add(totpAllyRandomSimilarStrengthRadioButton, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
						}
						totpPanel.add(totpAllyPanel, new GridBagConstraints(2, 1, 1, 4, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.BOTH,
							new Insets(0, 0, 0, 0), 0, 0));

						//======== totpAuraPanel ========
						{
							totpAuraPanel.setBorder(new TitledBorder(bundle.getString("GUI.totpAuraPanel.title")));
							totpAuraPanel.setLayout(new GridBagLayout());

							//---- totpAuraUnchangedRadioButton ----
							totpAuraUnchangedRadioButton.setEnabled(false);
							totpAuraUnchangedRadioButton.setText(bundle.getString("GUI.totpAuraUnchangedRadioButton.text"));
							totpAuraUnchangedRadioButton.setToolTipText(bundle.getString("GUI.totpAuraUnchangedRadioButton.toolTipText"));
							totpAuraPanel.add(totpAuraUnchangedRadioButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- totpAuraRandomRadioButton ----
							totpAuraRandomRadioButton.setEnabled(false);
							totpAuraRandomRadioButton.setText(bundle.getString("GUI.totpAuraRandomRadioButton.text"));
							totpAuraRandomRadioButton.setToolTipText(bundle.getString("GUI.totpAuraRandomRadioButton.toolTipText."));
							totpAuraPanel.add(totpAuraRandomRadioButton, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- totpAuraRandomSameStrengthRadioButton ----
							totpAuraRandomSameStrengthRadioButton.setEnabled(false);
							totpAuraRandomSameStrengthRadioButton.setText(bundle.getString("GUI.totpAuraRandomSameStrengthRadioButton.text"));
							totpAuraRandomSameStrengthRadioButton.setToolTipText(bundle.getString("GUI.totpAuraRandomSameStrengthRadioButton.toolTipText"));
							totpAuraPanel.add(totpAuraRandomSameStrengthRadioButton, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
						}
						totpPanel.add(totpAuraPanel, new GridBagConstraints(3, 1, 1, 4, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.BOTH,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- totpPercentageLevelModifierSlider ----
						totpPercentageLevelModifierSlider.setEnabled(false);
						totpPercentageLevelModifierSlider.setMajorTickSpacing(10);
						totpPercentageLevelModifierSlider.setMaximum(50);
						totpPercentageLevelModifierSlider.setMinimum(-50);
						totpPercentageLevelModifierSlider.setMinorTickSpacing(2);
						totpPercentageLevelModifierSlider.setPaintLabels(true);
						totpPercentageLevelModifierSlider.setPaintTicks(true);
						totpPercentageLevelModifierSlider.setSnapToTicks(true);
						totpPercentageLevelModifierSlider.setValue(0);
						totpPanel.add(totpPercentageLevelModifierSlider, new GridBagConstraints(4, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- totpPercentageLevelModifierCheckBox ----
						totpPercentageLevelModifierCheckBox.setEnabled(false);
						totpPercentageLevelModifierCheckBox.setText(bundle.getString("GUI.totpPercentageLevelModifierCheckBox.text"));
						totpPercentageLevelModifierCheckBox.setToolTipText(bundle.getString("GUI.totpPercentageLevelModifierCheckBox.toolTipText"));
						totpPanel.add(totpPercentageLevelModifierCheckBox, new GridBagConstraints(4, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- totpRandomizeHeldItemsCheckBox ----
						totpRandomizeHeldItemsCheckBox.setEnabled(false);
						totpRandomizeHeldItemsCheckBox.setText(bundle.getString("GUI.totpRandomizeHeldItemsCheckBox.text"));
						totpRandomizeHeldItemsCheckBox.setToolTipText(bundle.getString("GUI.totpRandomizeHeldItemsCheckBox.toolTipText"));
						totpPanel.add(totpRandomizeHeldItemsCheckBox, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- totpAllowAltFormesCheckBox ----
						totpAllowAltFormesCheckBox.setEnabled(false);
						totpAllowAltFormesCheckBox.setText(bundle.getString("GUI.totpAllowAltFormesCheckBox.text"));
						totpAllowAltFormesCheckBox.setToolTipText(bundle.getString("GUI.totpAllowAltFormesCheckBox.toolTipText"));
						totpPanel.add(totpAllowAltFormesCheckBox, new GridBagConstraints(4, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- totpRandomRadioButton ----
						totpRandomRadioButton.setEnabled(false);
						totpRandomRadioButton.setText(bundle.getString("GUI.totpRandomRadioButton.text"));
						totpRandomRadioButton.setToolTipText(bundle.getString("GUI.totpRandomRadioButton.toolTipText"));
						totpPanel.add(totpRandomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- totpRandomSimilarStrengthRadioButton ----
						totpRandomSimilarStrengthRadioButton.setEnabled(false);
						totpRandomSimilarStrengthRadioButton.setText(bundle.getString("GUI.totpRandomSimilarStrengthRadioButton.text"));
						totpRandomSimilarStrengthRadioButton.setToolTipText(bundle.getString("GUI.totpRandomSimilarStrengthRadioButton.toolTipText"));
						totpPanel.add(totpRandomSimilarStrengthRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel14.add(totpPanel, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.foePokemonPanel.title"), panel14);

				//======== panel16 ========
				{
					panel16.setEnabled(false);
					panel16.setLayout(new GridBagLayout());

					//======== panel17 ========
					{
						panel17.setBorder(new TitledBorder(null, bundle.getString("GUI.wpPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel17.setLayout(new GridBagLayout());

						//---- wpUnchangedRadioButton ----
						wpUnchangedRadioButton.setEnabled(false);
						wpUnchangedRadioButton.setText(bundle.getString("GUI.wpUnchangedRadioButton.text"));
						wpUnchangedRadioButton.setToolTipText(bundle.getString("GUI.wpUnchangedRadioButton.toolTipText"));
						panel17.add(wpUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						panel17.add(hSpacer32, new GridBagConstraints(5, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel17.add(vSpacer32, new GridBagConstraints(1, 8, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel17.add(hSpacer33, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel17.add(vSpacer33, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpRandomRadioButton ----
						wpRandomRadioButton.setEnabled(false);
						wpRandomRadioButton.setText(bundle.getString("GUI.wpRandomRadioButton.text"));
						wpRandomRadioButton.setToolTipText(bundle.getString("GUI.wpRandomRadioButton.toolTipText"));
						panel17.add(wpRandomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpArea1To1RadioButton ----
						wpArea1To1RadioButton.setEnabled(false);
						wpArea1To1RadioButton.setText(bundle.getString("GUI.wpArea1To1RadioButton.text"));
						wpArea1To1RadioButton.setToolTipText(bundle.getString("GUI.wpArea1To1RadioButton.toolTipText"));
						panel17.add(wpArea1To1RadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpGlobal1To1RadioButton ----
						wpGlobal1To1RadioButton.setEnabled(false);
						wpGlobal1To1RadioButton.setText(bundle.getString("GUI.wpGlobal1To1RadioButton.text"));
						wpGlobal1To1RadioButton.setToolTipText(bundle.getString("GUI.wpGlobal1To1RadioButton.toolTipText"));
						panel17.add(wpGlobal1To1RadioButton, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//======== panel18 ========
						{
							panel18.setBorder(new TitledBorder(bundle.getString("GUI.wpARPanel.title")));
							panel18.setLayout(new GridBagLayout());

							//---- wpARNoneRadioButton ----
							wpARNoneRadioButton.setEnabled(false);
							wpARNoneRadioButton.setText(bundle.getString("GUI.wpARNoneRadioButton.text"));
							wpARNoneRadioButton.setToolTipText(bundle.getString("GUI.wpARNoneRadioButton.toolTipText"));
							panel18.add(wpARNoneRadioButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
							panel18.add(hSpacer34, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));
							panel18.add(vSpacer34, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.1,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));
							panel18.add(hSpacer35, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- wpARSimilarStrengthRadioButton ----
							wpARSimilarStrengthRadioButton.setEnabled(false);
							wpARSimilarStrengthRadioButton.setText(bundle.getString("GUI.wpARSimilarStrengthRadioButton.text"));
							wpARSimilarStrengthRadioButton.setToolTipText(bundle.getString("GUI.wpARSimilarStrengthRadioButton.toolTipText"));
							panel18.add(wpARSimilarStrengthRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- wpARCatchEmAllModeRadioButton ----
							wpARCatchEmAllModeRadioButton.setEnabled(false);
							wpARCatchEmAllModeRadioButton.setText(bundle.getString("GUI.wpARCatchEmAllModeRadioButton.text"));
							wpARCatchEmAllModeRadioButton.setToolTipText(bundle.getString("GUI.wpARCatchEmAllModeRadioButton.toolTipText"));
							panel18.add(wpARCatchEmAllModeRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- wpARTypeThemeAreasRadioButton ----
							wpARTypeThemeAreasRadioButton.setEnabled(false);
							wpARTypeThemeAreasRadioButton.setText(bundle.getString("GUI.wpARTypeThemeAreasRadioButton.text"));
							wpARTypeThemeAreasRadioButton.setToolTipText(bundle.getString("GUI.wpARTypeThemeAreasRadioButton.toolTipText"));
							panel18.add(wpARTypeThemeAreasRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
						}
						panel17.add(panel18, new GridBagConstraints(2, 1, 1, 6, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.BOTH,
							new Insets(0, 0, 0, 80), 0, 0));

						//---- wpUseTimeBasedEncountersCheckBox ----
						wpUseTimeBasedEncountersCheckBox.setEnabled(false);
						wpUseTimeBasedEncountersCheckBox.setText(bundle.getString("GUI.wpUseTimeBasedEncountersCheckBox.text"));
						wpUseTimeBasedEncountersCheckBox.setToolTipText(bundle.getString("GUI.wpUseTimeBasedEncountersCheckBox.toolTipText"));
						panel17.add(wpUseTimeBasedEncountersCheckBox, new GridBagConstraints(3, 1, 2, 1, 0.1, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpDontUseLegendariesCheckBox ----
						wpDontUseLegendariesCheckBox.setEnabled(false);
						wpDontUseLegendariesCheckBox.setText(bundle.getString("GUI.wpDontUseLegendariesCheckBox.text"));
						panel17.add(wpDontUseLegendariesCheckBox, new GridBagConstraints(3, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpSetMinimumCatchRateCheckBox ----
						wpSetMinimumCatchRateCheckBox.setEnabled(false);
						wpSetMinimumCatchRateCheckBox.setText(bundle.getString("GUI.wpSetMinimumCatchRateCheckBox.text"));
						wpSetMinimumCatchRateCheckBox.setToolTipText(bundle.getString("GUI.wpSetMinimumCatchRateCheckBox.toolTipText"));
						panel17.add(wpSetMinimumCatchRateCheckBox, new GridBagConstraints(3, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpRandomizeHeldItemsCheckBox ----
						wpRandomizeHeldItemsCheckBox.setEnabled(false);
						wpRandomizeHeldItemsCheckBox.setText(bundle.getString("GUI.wpRandomizeHeldItemsCheckBox.text"));
						wpRandomizeHeldItemsCheckBox.setToolTipText(bundle.getString("GUI.wpRandomizeHeldItemsCheckBox.toolTipText"));
						panel17.add(wpRandomizeHeldItemsCheckBox, new GridBagConstraints(3, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpBanBadItemsCheckBox ----
						wpBanBadItemsCheckBox.setEnabled(false);
						wpBanBadItemsCheckBox.setText(bundle.getString("GUI.wpBanBadItemsCheckBox.text"));
						wpBanBadItemsCheckBox.setToolTipText(bundle.getString("GUI.wpBanBadItemsCheckBox.toolTipText"));
						panel17.add(wpBanBadItemsCheckBox, new GridBagConstraints(3, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpBalanceShakingGrassPokemonCheckBox ----
						wpBalanceShakingGrassPokemonCheckBox.setEnabled(false);
						wpBalanceShakingGrassPokemonCheckBox.setText(bundle.getString("GUI.wpBalanceShakingGrassPokemonCheckBox.text"));
						wpBalanceShakingGrassPokemonCheckBox.setToolTipText(bundle.getString("GUI.wpBalanceShakingGrassPokemonCheckBox.toolTipText"));
						panel17.add(wpBalanceShakingGrassPokemonCheckBox, new GridBagConstraints(3, 6, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpPercentageLevelModifierCheckBox ----
						wpPercentageLevelModifierCheckBox.setEnabled(false);
						wpPercentageLevelModifierCheckBox.setText(bundle.getString("GUI.wpPercentageLevelModifierCheckBox.text"));
						wpPercentageLevelModifierCheckBox.setToolTipText(bundle.getString("GUI.wpPercentageLevelModifierCheckBox.toolTipText"));
						panel17.add(wpPercentageLevelModifierCheckBox, new GridBagConstraints(3, 7, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- wpPercentageLevelModifierSlider ----
						wpPercentageLevelModifierSlider.setEnabled(false);
						wpPercentageLevelModifierSlider.setMajorTickSpacing(10);
						wpPercentageLevelModifierSlider.setMaximum(50);
						wpPercentageLevelModifierSlider.setMinimum(-50);
						wpPercentageLevelModifierSlider.setMinorTickSpacing(2);
						wpPercentageLevelModifierSlider.setPaintLabels(true);
						wpPercentageLevelModifierSlider.setPaintTicks(true);
						wpPercentageLevelModifierSlider.setSnapToTicks(true);
						wpPercentageLevelModifierSlider.setToolTipText(bundle.getString("GUI.wpPercentageLevelModifierSlider.toolTipText"));
						wpPercentageLevelModifierSlider.setValue(0);
						panel17.add(wpPercentageLevelModifierSlider, new GridBagConstraints(3, 8, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 30, 0));

						//---- wpSetMinimumCatchRateSlider ----
						wpSetMinimumCatchRateSlider.setEnabled(false);
						wpSetMinimumCatchRateSlider.setMajorTickSpacing(1);
						wpSetMinimumCatchRateSlider.setMaximum(4);
						wpSetMinimumCatchRateSlider.setMinimum(1);
						wpSetMinimumCatchRateSlider.setPaintLabels(true);
						wpSetMinimumCatchRateSlider.setSnapToTicks(true);
						wpSetMinimumCatchRateSlider.setToolTipText(bundle.getString("GUI.wpSetMinimumCatchRateSlider.toolTipText"));
						wpSetMinimumCatchRateSlider.setValue(1);
						panel17.add(wpSetMinimumCatchRateSlider, new GridBagConstraints(4, 3, 1, 2, 0.0, 0.0,
							GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
							new Insets(0, 10, 0, 0), 0, 0));

						//---- wpAllowAltFormesCheckBox ----
						wpAllowAltFormesCheckBox.setEnabled(false);
						wpAllowAltFormesCheckBox.setText(bundle.getString("GUI.wpAllowAltFormesCheckBox.text"));
						wpAllowAltFormesCheckBox.setToolTipText(bundle.getString("GUI.wpAllowAltFormesCheckBox.toolTipText"));
						panel17.add(wpAllowAltFormesCheckBox, new GridBagConstraints(3, 9, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel16.add(panel17, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					panel16.add(hSpacer36, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel16.add(vSpacer35, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel16.add(hSpacer37, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel16.add(vSpacer36, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.wildPokemonPanel.title"), panel16);

				//======== panel19 ========
				{
					panel19.setEnabled(false);
					panel19.setLayout(new GridBagLayout());

					//======== panel20 ========
					{
						panel20.setBorder(new TitledBorder(null, bundle.getString("GUI.tmPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel20.setLayout(new GridBagLayout());

						//======== panel21 ========
						{
							panel21.setBorder(new TitledBorder(bundle.getString("GUI.tmMovesPanel.title")));
							panel21.setLayout(new GridBagLayout());

							//---- tmUnchangedRadioButton ----
							tmUnchangedRadioButton.setEnabled(false);
							tmUnchangedRadioButton.setText(bundle.getString("GUI.tmUnchangedRadioButton.text"));
							tmUnchangedRadioButton.setToolTipText(bundle.getString("GUI.tmUnchangedRadioButton.toolTipText"));
							panel21.add(tmUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 70), 0, 0));
							panel21.add(vSpacer37, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));
							panel21.add(hSpacer38, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));
							panel21.add(vSpacer38, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmRandomRadioButton ----
							tmRandomRadioButton.setEnabled(false);
							tmRandomRadioButton.setText(bundle.getString("GUI.tmRandomRadioButton.text"));
							tmRandomRadioButton.setToolTipText(bundle.getString("GUI.tmRandomRadioButton.toolTipText"));
							panel21.add(tmRandomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmNoGameBreakingMovesCheckBox ----
							tmNoGameBreakingMovesCheckBox.setEnabled(false);
							tmNoGameBreakingMovesCheckBox.setText(bundle.getString("GUI.tmNoGameBreakingMovesCheckBox.text"));
							tmNoGameBreakingMovesCheckBox.setToolTipText(bundle.getString("GUI.tmNoGameBreakingMovesCheckBox.toolTipText"));
							panel21.add(tmNoGameBreakingMovesCheckBox, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmKeepFieldMoveTMsCheckBox ----
							tmKeepFieldMoveTMsCheckBox.setEnabled(false);
							tmKeepFieldMoveTMsCheckBox.setText(bundle.getString("GUI.tmKeepFieldMoveTMsCheckBox.text"));
							tmKeepFieldMoveTMsCheckBox.setToolTipText(bundle.getString("GUI.tmKeepFieldMoveTMsCheckBox.toolTipText"));
							panel21.add(tmKeepFieldMoveTMsCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmForceGoodDamagingCheckBox ----
							tmForceGoodDamagingCheckBox.setEnabled(false);
							tmForceGoodDamagingCheckBox.setText(bundle.getString("GUI.tmForceGoodDamagingCheckBox.text"));
							tmForceGoodDamagingCheckBox.setToolTipText(bundle.getString("GUI.tmForceGoodDamagingCheckBox.toolTipText"));
							panel21.add(tmForceGoodDamagingCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmForceGoodDamagingSlider ----
							tmForceGoodDamagingSlider.setEnabled(false);
							tmForceGoodDamagingSlider.setMajorTickSpacing(20);
							tmForceGoodDamagingSlider.setMinorTickSpacing(5);
							tmForceGoodDamagingSlider.setPaintLabels(true);
							tmForceGoodDamagingSlider.setPaintTicks(true);
							tmForceGoodDamagingSlider.setSnapToTicks(true);
							tmForceGoodDamagingSlider.setToolTipText(bundle.getString("GUI.tmForceGoodDamagingSlider.toolTipText"));
							tmForceGoodDamagingSlider.setValue(0);
							panel21.add(tmForceGoodDamagingSlider, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));
						}
						panel20.add(panel21, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.BOTH,
							new Insets(0, 0, 0, 0), 0, 0));
						panel20.add(hSpacer39, new GridBagConstraints(3, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel20.add(vSpacer39, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel20.add(hSpacer40, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel20.add(vSpacer40, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//======== panel22 ========
						{
							panel22.setBorder(new TitledBorder(bundle.getString("GUI.thcPanel.title")));
							panel22.setLayout(new GridBagLayout());

							//---- thcUnchangedRadioButton ----
							thcUnchangedRadioButton.setEnabled(false);
							thcUnchangedRadioButton.setText(bundle.getString("GUI.thcUnchangedRadioButton.text"));
							thcUnchangedRadioButton.setToolTipText(bundle.getString("GUI.thcUnchangedRadioButton.toolTipText"));
							panel22.add(thcUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
							panel22.add(vSpacer41, new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));
							panel22.add(hSpacer41, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));
							panel22.add(vSpacer42, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- thcRandomPreferSameTypeRadioButton ----
							thcRandomPreferSameTypeRadioButton.setEnabled(false);
							thcRandomPreferSameTypeRadioButton.setText(bundle.getString("GUI.thcRandomPreferSameTypeRadioButton.text"));
							thcRandomPreferSameTypeRadioButton.setToolTipText(bundle.getString("GUI.thcRandomPreferSameTypeRadioButton.toolTipText"));
							panel22.add(thcRandomPreferSameTypeRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- thcRandomCompletelyRadioButton ----
							thcRandomCompletelyRadioButton.setEnabled(false);
							thcRandomCompletelyRadioButton.setText(bundle.getString("GUI.thcRandomCompletelyRadioButton.text"));
							thcRandomCompletelyRadioButton.setToolTipText(bundle.getString("GUI.thcRandomCompletelyRadioButton.toolTipText"));
							panel22.add(thcRandomCompletelyRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- thcFullCompatibilityRadioButton ----
							thcFullCompatibilityRadioButton.setEnabled(false);
							thcFullCompatibilityRadioButton.setText(bundle.getString("GUI.thcFullCompatibilityRadioButton.text"));
							thcFullCompatibilityRadioButton.setToolTipText(bundle.getString("GUI.thcFullCompatibilityRadioButton.toolTipText"));
							panel22.add(thcFullCompatibilityRadioButton, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmFollowEvolutionsCheckBox ----
							tmFollowEvolutionsCheckBox.setEnabled(false);
							tmFollowEvolutionsCheckBox.setText(bundle.getString("GUI.tmFollowEvolutionsCheckBox.text"));
							tmFollowEvolutionsCheckBox.setToolTipText(bundle.getString("GUI.tmFollowEvolutionsCheckBox.toolTipText"));
							panel22.add(tmFollowEvolutionsCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmLevelupMoveSanityCheckBox ----
							tmLevelupMoveSanityCheckBox.setEnabled(false);
							tmLevelupMoveSanityCheckBox.setText(bundle.getString("GUI.tmLevelupMoveSanityCheckBox.text"));
							tmLevelupMoveSanityCheckBox.setToolTipText(bundle.getString("GUI.tmLevelupMoveSanityCheckBox.toolTipText"));
							panel22.add(tmLevelupMoveSanityCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- tmFullHMCompatibilityCheckBox ----
							tmFullHMCompatibilityCheckBox.setEnabled(false);
							tmFullHMCompatibilityCheckBox.setText(bundle.getString("GUI.tmFullHMCompatibilityCheckBox.text"));
							tmFullHMCompatibilityCheckBox.setToolTipText(bundle.getString("GUI.tmFullHMCompatibilityCheckBox.toolTipText"));
							panel22.add(tmFullHMCompatibilityCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
						}
						panel20.add(panel22, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.BOTH,
							new Insets(0, 20, 0, 0), 0, 0));
					}
					panel19.add(panel20, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					panel19.add(hSpacer42, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel19.add(vSpacer43, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel19.add(hSpacer43, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel19.add(vSpacer44, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== moveTutorPanel ========
					{
						moveTutorPanel.setBorder(new TitledBorder(null, bundle.getString("GUI.mtPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						moveTutorPanel.setLayout(new GridBagLayout());

						//======== mtMovesPanel ========
						{
							mtMovesPanel.setBorder(new TitledBorder(bundle.getString("GUI.mtMovesPanel.title")));
							mtMovesPanel.setLayout(new GridBagLayout());

							//---- mtUnchangedRadioButton ----
							mtUnchangedRadioButton.setEnabled(false);
							mtUnchangedRadioButton.setText(bundle.getString("GUI.mtUnchangedRadioButton.text"));
							mtUnchangedRadioButton.setToolTipText(bundle.getString("GUI.mtUnchangedRadioButton.toolTipText"));
							mtMovesPanel.add(mtUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 70), 0, 0));
							mtMovesPanel.add(vSpacer45, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));
							mtMovesPanel.add(hSpacer44, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));
							mtMovesPanel.add(vSpacer46, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtRandomRadioButton ----
							mtRandomRadioButton.setEnabled(false);
							mtRandomRadioButton.setText(bundle.getString("GUI.mtRandomRadioButton.text"));
							mtRandomRadioButton.setToolTipText(bundle.getString("GUI.mtRandomRadioButton.toolTipText"));
							mtMovesPanel.add(mtRandomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtForceGoodDamagingSlider ----
							mtForceGoodDamagingSlider.setEnabled(false);
							mtForceGoodDamagingSlider.setMajorTickSpacing(20);
							mtForceGoodDamagingSlider.setMinorTickSpacing(5);
							mtForceGoodDamagingSlider.setPaintLabels(true);
							mtForceGoodDamagingSlider.setPaintTicks(true);
							mtForceGoodDamagingSlider.setSnapToTicks(true);
							mtForceGoodDamagingSlider.setToolTipText(bundle.getString("GUI.mtForceGoodDamagingSlider.toolTipText"));
							mtForceGoodDamagingSlider.setValue(0);
							mtMovesPanel.add(mtForceGoodDamagingSlider, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtNoGameBreakingMovesCheckBox ----
							mtNoGameBreakingMovesCheckBox.setEnabled(false);
							mtNoGameBreakingMovesCheckBox.setText(bundle.getString("GUI.mtNoGameBreakingMovesCheckBox.text"));
							mtNoGameBreakingMovesCheckBox.setToolTipText(bundle.getString("GUI.mtNoGameBreakingMovesCheckBox.toolTipText"));
							mtMovesPanel.add(mtNoGameBreakingMovesCheckBox, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtKeepFieldMoveTutorsCheckBox ----
							mtKeepFieldMoveTutorsCheckBox.setEnabled(false);
							mtKeepFieldMoveTutorsCheckBox.setText(bundle.getString("GUI.mtKeepFieldMoveTutorsCheckBox.text"));
							mtKeepFieldMoveTutorsCheckBox.setToolTipText(bundle.getString("GUI.mtKeepFieldMoveTutorsCheckBox.toolTipText"));
							mtMovesPanel.add(mtKeepFieldMoveTutorsCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtForceGoodDamagingCheckBox ----
							mtForceGoodDamagingCheckBox.setEnabled(false);
							mtForceGoodDamagingCheckBox.setText(bundle.getString("GUI.mtForceGoodDamagingCheckBox.text"));
							mtForceGoodDamagingCheckBox.setToolTipText(bundle.getString("GUI.mtForceGoodDamagingCheckBox.toolTipText"));
							mtMovesPanel.add(mtForceGoodDamagingCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
						}
						moveTutorPanel.add(mtMovesPanel, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.BOTH,
							new Insets(0, 0, 0, 0), 0, 0));
						moveTutorPanel.add(hSpacer45, new GridBagConstraints(3, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						moveTutorPanel.add(vSpacer47, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						moveTutorPanel.add(hSpacer46, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						moveTutorPanel.add(vSpacer48, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//======== mtCompatPanel ========
						{
							mtCompatPanel.setBorder(new TitledBorder(bundle.getString("GUI.mtcPanel.title")));
							mtCompatPanel.setLayout(new GridBagLayout());

							//---- mtcUnchangedRadioButton ----
							mtcUnchangedRadioButton.setEnabled(false);
							mtcUnchangedRadioButton.setText(bundle.getString("GUI.mtcUnchangedRadioButton.text"));
							mtcUnchangedRadioButton.setToolTipText(bundle.getString("GUI.mtcUnchangedRadioButton.toolTipText"));
							mtCompatPanel.add(mtcUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
							mtCompatPanel.add(vSpacer49, new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));
							mtCompatPanel.add(hSpacer47, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
								new Insets(0, 0, 0, 0), 0, 0));
							mtCompatPanel.add(vSpacer50, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
								GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtcRandomPreferSameTypeRadioButton ----
							mtcRandomPreferSameTypeRadioButton.setEnabled(false);
							mtcRandomPreferSameTypeRadioButton.setText(bundle.getString("GUI.mtcRandomPreferSameTypeRadioButton.text"));
							mtcRandomPreferSameTypeRadioButton.setToolTipText(bundle.getString("GUI.mtcRandomPreferSameTypeRadioButton.toolTipText"));
							mtCompatPanel.add(mtcRandomPreferSameTypeRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtcRandomCompletelyRadioButton ----
							mtcRandomCompletelyRadioButton.setEnabled(false);
							mtcRandomCompletelyRadioButton.setText(bundle.getString("GUI.mtcRandomCompletelyRadioButton.text"));
							mtcRandomCompletelyRadioButton.setToolTipText(bundle.getString("GUI.mtcRandomCompletelyRadioButton.toolTipText"));
							mtCompatPanel.add(mtcRandomCompletelyRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtcFullCompatibilityRadioButton ----
							mtcFullCompatibilityRadioButton.setEnabled(false);
							mtcFullCompatibilityRadioButton.setText(bundle.getString("GUI.mtcFullCompatibilityRadioButton.text"));
							mtcFullCompatibilityRadioButton.setToolTipText(bundle.getString("GUI.mtcFullCompatibilityRadioButton.toolTipText"));
							mtCompatPanel.add(mtcFullCompatibilityRadioButton, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtFollowEvolutionsCheckBox ----
							mtFollowEvolutionsCheckBox.setEnabled(false);
							mtFollowEvolutionsCheckBox.setText(bundle.getString("GUI.mtFollowEvolutionsCheckBox.text"));
							mtFollowEvolutionsCheckBox.setToolTipText(bundle.getString("GUI.mtFollowEvolutionsCheckBox.toolTipText"));
							mtCompatPanel.add(mtFollowEvolutionsCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));

							//---- mtLevelupMoveSanityCheckBox ----
							mtLevelupMoveSanityCheckBox.setEnabled(false);
							mtLevelupMoveSanityCheckBox.setText(bundle.getString("GUI.mtLevelupMoveSanityCheckBox.text"));
							mtLevelupMoveSanityCheckBox.setToolTipText(bundle.getString("GUI.mtLevelupMoveSanityCheckBox.toolTipText"));
							mtCompatPanel.add(mtLevelupMoveSanityCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
								GridBagConstraints.WEST, GridBagConstraints.NONE,
								new Insets(0, 0, 0, 0), 0, 0));
						}
						moveTutorPanel.add(mtCompatPanel, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.BOTH,
							new Insets(0, 20, 0, 0), 0, 0));

						//---- mtNoExistLabel ----
						mtNoExistLabel.setEnabled(true);
						mtNoExistLabel.setText(bundle.getString("GUI.mtNoExistLabel.text"));
						moveTutorPanel.add(mtNoExistLabel, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel19.add(moveTutorPanel, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.tmsHMsTutorsPanel.title"), panel19);

				//======== panel23 ========
				{
					panel23.setEnabled(false);
					panel23.setLayout(new GridBagLayout());

					//======== panel24 ========
					{
						panel24.setBorder(new TitledBorder(null, bundle.getString("GUI.fiPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						panel24.setLayout(new GridBagLayout());

						//---- fiUnchangedRadioButton ----
						fiUnchangedRadioButton.setEnabled(false);
						fiUnchangedRadioButton.setText(bundle.getString("GUI.fiUnchangedRadioButton.text"));
						fiUnchangedRadioButton.setToolTipText(bundle.getString("GUI.fiUnchangedRadioButton.toolTipText"));
						panel24.add(fiUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 140), 0, 0));
						panel24.add(hSpacer48, new GridBagConstraints(3, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel24.add(vSpacer51, new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel24.add(hSpacer49, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						panel24.add(vSpacer52, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- fiShuffleRadioButton ----
						fiShuffleRadioButton.setEnabled(false);
						fiShuffleRadioButton.setText(bundle.getString("GUI.fiShuffleRadioButton.text"));
						fiShuffleRadioButton.setToolTipText(bundle.getString("GUI.fiShuffleRadioButton.toolTipText"));
						panel24.add(fiShuffleRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- fiRandomRadioButton ----
						fiRandomRadioButton.setEnabled(false);
						fiRandomRadioButton.setText(bundle.getString("GUI.fiRandomRadioButton.text"));
						fiRandomRadioButton.setToolTipText(bundle.getString("GUI.fiRandomRadioButton.toolTipText"));
						panel24.add(fiRandomRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- fiRandomEvenDistributionRadioButton ----
						fiRandomEvenDistributionRadioButton.setEnabled(false);
						fiRandomEvenDistributionRadioButton.setText(bundle.getString("GUI.fiRandomEvenDistributionRadioButton.text"));
						fiRandomEvenDistributionRadioButton.setToolTipText(bundle.getString("GUI.fiRandomEvenDistributionRadioButton.toolTipText"));
						panel24.add(fiRandomEvenDistributionRadioButton, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- fiBanBadItemsCheckBox ----
						fiBanBadItemsCheckBox.setEnabled(false);
						fiBanBadItemsCheckBox.setText(bundle.getString("GUI.fiBanBadItemsCheckBox.text"));
						fiBanBadItemsCheckBox.setToolTipText(bundle.getString("GUI.fiBanBadItemsCheckBox.toolTipText"));
						panel24.add(fiBanBadItemsCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel23.add(panel24, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					panel23.add(hSpacer50, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel23.add(vSpacer53, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel23.add(hSpacer51, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					panel23.add(vSpacer54, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== shopItemsPanel ========
					{
						shopItemsPanel.setBorder(new TitledBorder(null, bundle.getString("GUI.shPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						shopItemsPanel.setLayout(new GridBagLayout());

						//---- shUnchangedRadioButton ----
						shUnchangedRadioButton.setEnabled(false);
						shUnchangedRadioButton.setText(bundle.getString("GUI.shUnchangedRadioButton.text"));
						shUnchangedRadioButton.setToolTipText(bundle.getString("GUI.shUnchangedRadioButton.toolTipText"));
						shopItemsPanel.add(shUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 140), 0, 0));
						shopItemsPanel.add(hSpacer52, new GridBagConstraints(3, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						shopItemsPanel.add(vSpacer55, new GridBagConstraints(1, 7, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						shopItemsPanel.add(hSpacer53, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						shopItemsPanel.add(vSpacer56, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shShuffleRadioButton ----
						shShuffleRadioButton.setEnabled(false);
						shShuffleRadioButton.setText(bundle.getString("GUI.shShuffleRadioButton.text"));
						shShuffleRadioButton.setToolTipText(bundle.getString("GUI.shShuffleRadioButton.toolTipText"));
						shopItemsPanel.add(shShuffleRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shRandomRadioButton ----
						shRandomRadioButton.setEnabled(false);
						shRandomRadioButton.setText(bundle.getString("GUI.shRandomRadioButton.text"));
						shRandomRadioButton.setToolTipText(bundle.getString("GUI.shRandomRadioButton.toolTipText"));
						shopItemsPanel.add(shRandomRadioButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shBanOverpoweredShopItemsCheckBox ----
						shBanOverpoweredShopItemsCheckBox.setEnabled(false);
						shBanOverpoweredShopItemsCheckBox.setText(bundle.getString("GUI.shBanOverpoweredShopItemsCheckBox.text"));
						shBanOverpoweredShopItemsCheckBox.setToolTipText(bundle.getString("GUI.shBanOverpoweredShopItemsCheckBox.toolTipText"));
						shopItemsPanel.add(shBanOverpoweredShopItemsCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shBanBadItemsCheckBox ----
						shBanBadItemsCheckBox.setEnabled(false);
						shBanBadItemsCheckBox.setText(bundle.getString("GUI.shBanBadItemsCheckBox.text"));
						shBanBadItemsCheckBox.setToolTipText(bundle.getString("GUI.shBanBadItemsCheckBox.toolTipText"));
						shopItemsPanel.add(shBanBadItemsCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shBanRegularShopItemsCheckBox ----
						shBanRegularShopItemsCheckBox.setEnabled(false);
						shBanRegularShopItemsCheckBox.setText(bundle.getString("GUI.shBanRegularShopItemsCheckBox.text"));
						shBanRegularShopItemsCheckBox.setToolTipText(bundle.getString("GUI.shBanRegularShopItemsCheckBox.toolTipText"));
						shopItemsPanel.add(shBanRegularShopItemsCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shBalanceShopItemPricesCheckBox ----
						shBalanceShopItemPricesCheckBox.setEnabled(false);
						shBalanceShopItemPricesCheckBox.setText(bundle.getString("GUI.shBalanceShopItemPricesCheckBox.text"));
						shBalanceShopItemPricesCheckBox.setToolTipText(bundle.getString("GUI.shBalanceShopItemPricesCheckBox.toolTipText"));
						shopItemsPanel.add(shBalanceShopItemPricesCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shGuaranteeEvolutionItemsCheckBox ----
						shGuaranteeEvolutionItemsCheckBox.setEnabled(false);
						shGuaranteeEvolutionItemsCheckBox.setText(bundle.getString("GUI.shGuaranteeEvolutionItemsCheckBox.text"));
						shGuaranteeEvolutionItemsCheckBox.setToolTipText(bundle.getString("GUI.shGuaranteeEvolutionItemsCheckBox.toolTipText"));
						shopItemsPanel.add(shGuaranteeEvolutionItemsCheckBox, new GridBagConstraints(2, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- shGuaranteeXItemsCheckBox ----
						shGuaranteeXItemsCheckBox.setEnabled(false);
						shGuaranteeXItemsCheckBox.setSelected(false);
						shGuaranteeXItemsCheckBox.setText(bundle.getString("GUI.shGuaranteeXItemsCheckbox.text"));
						shGuaranteeXItemsCheckBox.setToolTipText(bundle.getString("GUI.shGuaranteeXItemsCheckbox.tooltipText"));
						shopItemsPanel.add(shGuaranteeXItemsCheckBox, new GridBagConstraints(2, 6, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel23.add(shopItemsPanel, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));

					//======== pickupItemsPanel ========
					{
						pickupItemsPanel.setBorder(new TitledBorder(null, bundle.getString("GUI.puPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font").deriveFont(Font.BOLD)));
						pickupItemsPanel.setLayout(new GridBagLayout());

						//---- puUnchangedRadioButton ----
						puUnchangedRadioButton.setEnabled(false);
						puUnchangedRadioButton.setText(bundle.getString("GUI.puUnchangedRadioButton.text"));
						puUnchangedRadioButton.setToolTipText(bundle.getString("GUI.puUnchangedRadioButton.toolTipText"));
						pickupItemsPanel.add(puUnchangedRadioButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 140), 0, 0));
						pickupItemsPanel.add(hSpacer54, new GridBagConstraints(3, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						pickupItemsPanel.add(vSpacer57, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						pickupItemsPanel.add(hSpacer55, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						pickupItemsPanel.add(vSpacer58, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- puRandomRadioButton ----
						puRandomRadioButton.setEnabled(false);
						puRandomRadioButton.setText(bundle.getString("GUI.puRandomRadioButton.text"));
						puRandomRadioButton.setToolTipText(bundle.getString("GUI.puRandomRadioButton.toolTipText"));
						pickupItemsPanel.add(puRandomRadioButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- puBanBadItemsCheckBox ----
						puBanBadItemsCheckBox.setEnabled(false);
						puBanBadItemsCheckBox.setText(bundle.getString("GUI.puBanBadItemsCheckBox.text"));
						puBanBadItemsCheckBox.setToolTipText(bundle.getString("GUI.puBanBadItemsCheckBox.toolTipText"));
						pickupItemsPanel.add(puBanBadItemsCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					panel23.add(pickupItemsPanel, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.itemsPanel.title"), panel23);

				//======== baseTweaksPanel ========
				{
					baseTweaksPanel.setEnabled(false);
					baseTweaksPanel.setLayout(new GridBagLayout());

					//======== miscTweaksPanel ========
					{
						miscTweaksPanel.setBorder(new TitledBorder(null, bundle.getString("GUI.miscPanel.title"), TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION,
							UIManager.getFont("TitledBorder.font")));
						miscTweaksPanel.setLayout(new GridBagLayout());

						//---- miscBWExpPatchCheckBox ----
						miscBWExpPatchCheckBox.setEnabled(false);
						miscBWExpPatchCheckBox.setText(bundle.getString("GUI.miscBWExpPatchCheckBox.text"));
						miscBWExpPatchCheckBox.setToolTipText(bundle.getString("GUI.miscBWExpPatchCheckBox.toolTipText"));
						miscTweaksPanel.add(miscBWExpPatchCheckBox, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
						miscTweaksPanel.add(hSpacer56, new GridBagConstraints(5, 1, 1, 1, 0.1, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));
						miscTweaksPanel.add(vSpacer59, new GridBagConstraints(1, 7, 1, 1, 0.0, 0.1,
							GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
							new Insets(0, 0, 0, 0), 0, 0));
						miscTweaksPanel.add(hSpacer57, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscNerfXAccuracyCheckBox ----
						miscNerfXAccuracyCheckBox.setEnabled(false);
						miscNerfXAccuracyCheckBox.setText(bundle.getString("GUI.miscNerfXAccuracyCheckBox.text"));
						miscNerfXAccuracyCheckBox.setToolTipText(bundle.getString("GUI.miscNerfXAccuracyCheckBox.toolTipText"));
						miscTweaksPanel.add(miscNerfXAccuracyCheckBox, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscFixCritRateCheckBox ----
						miscFixCritRateCheckBox.setEnabled(false);
						miscFixCritRateCheckBox.setText(bundle.getString("GUI.miscFixCritRateCheckBox.text"));
						miscFixCritRateCheckBox.setToolTipText(bundle.getString("GUI.miscFixCritRateCheckBox.toolTipText"));
						miscTweaksPanel.add(miscFixCritRateCheckBox, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscFastestTextCheckBox ----
						miscFastestTextCheckBox.setEnabled(false);
						miscFastestTextCheckBox.setText(bundle.getString("GUI.miscFastestTextCheckBox.text"));
						miscFastestTextCheckBox.setToolTipText(bundle.getString("GUI.miscFastestTextCheckBox.toolTipText"));
						miscTweaksPanel.add(miscFastestTextCheckBox, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscRunningShoesIndoorsCheckBox ----
						miscRunningShoesIndoorsCheckBox.setEnabled(false);
						miscRunningShoesIndoorsCheckBox.setText(bundle.getString("GUI.miscRunningShoesIndoorsCheckBox.text"));
						miscRunningShoesIndoorsCheckBox.setToolTipText(bundle.getString("GUI.miscRunningShoesIndoorsCheckBox.toolTipText"));
						miscTweaksPanel.add(miscRunningShoesIndoorsCheckBox, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscRandomizePCPotionCheckBox ----
						miscRandomizePCPotionCheckBox.setEnabled(false);
						miscRandomizePCPotionCheckBox.setText(bundle.getString("GUI.miscRandomizePCPotionCheckBox.text"));
						miscRandomizePCPotionCheckBox.setToolTipText(bundle.getString("GUI.miscRandomizePCPotionCheckBox.toolTipText"));
						miscTweaksPanel.add(miscRandomizePCPotionCheckBox, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscAllowPikachuEvolutionCheckBox ----
						miscAllowPikachuEvolutionCheckBox.setEnabled(false);
						miscAllowPikachuEvolutionCheckBox.setText(bundle.getString("GUI.miscAllowPikachuEvolutionCheckBox.text"));
						miscAllowPikachuEvolutionCheckBox.setToolTipText(bundle.getString("GUI.miscAllowPikachuEvolutionCheckBox.toolTipText"));
						miscTweaksPanel.add(miscAllowPikachuEvolutionCheckBox, new GridBagConstraints(3, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscGiveNationalDexAtCheckBox ----
						miscGiveNationalDexAtCheckBox.setEnabled(false);
						miscGiveNationalDexAtCheckBox.setText(bundle.getString("GUI.miscGiveNationalDexAtCheckBox.text"));
						miscGiveNationalDexAtCheckBox.setToolTipText(bundle.getString("GUI.miscGiveNationalDexAtCheckBox.toolTipText"));
						miscTweaksPanel.add(miscGiveNationalDexAtCheckBox, new GridBagConstraints(4, 2, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscUpdateTypeEffectivenessCheckBox ----
						miscUpdateTypeEffectivenessCheckBox.setEnabled(false);
						miscUpdateTypeEffectivenessCheckBox.setText(bundle.getString("GUI.miscUpdateTypeEffectivenessCheckBox.text"));
						miscUpdateTypeEffectivenessCheckBox.setToolTipText(bundle.getString("GUI.miscUpdateTypeEffectivenessCheckBox.toolTipText"));
						miscTweaksPanel.add(miscUpdateTypeEffectivenessCheckBox, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscLowerCasePokemonNamesCheckBox ----
						miscLowerCasePokemonNamesCheckBox.setEnabled(false);
						miscLowerCasePokemonNamesCheckBox.setText(bundle.getString("GUI.miscLowerCasePokemonNamesCheckBox.text"));
						miscLowerCasePokemonNamesCheckBox.setToolTipText(bundle.getString("GUI.miscLowerCasePokemonNamesCheckBox.toolTipText"));
						miscTweaksPanel.add(miscLowerCasePokemonNamesCheckBox, new GridBagConstraints(3, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscRandomizeCatchingTutorialCheckBox ----
						miscRandomizeCatchingTutorialCheckBox.setEnabled(false);
						miscRandomizeCatchingTutorialCheckBox.setText(bundle.getString("GUI.miscRandomizeCatchingTutorialCheckBox.text"));
						miscRandomizeCatchingTutorialCheckBox.setToolTipText(bundle.getString("GUI.miscRandomizeCatchingTutorialCheckBox.toolTipText"));
						miscTweaksPanel.add(miscRandomizeCatchingTutorialCheckBox, new GridBagConstraints(4, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscBanLuckyEggCheckBox ----
						miscBanLuckyEggCheckBox.setEnabled(false);
						miscBanLuckyEggCheckBox.setText(bundle.getString("GUI.miscBanLuckyEggCheckBox.text"));
						miscBanLuckyEggCheckBox.setToolTipText(bundle.getString("GUI.miscBanLuckyEggCheckBox.toolTipText"));
						miscTweaksPanel.add(miscBanLuckyEggCheckBox, new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscNoFreeLuckyEggCheckBox ----
						miscNoFreeLuckyEggCheckBox.setEnabled(false);
						miscNoFreeLuckyEggCheckBox.setText(bundle.getString("GUI.miscNoFreeLuckyEggCheckBox.text"));
						miscNoFreeLuckyEggCheckBox.setToolTipText(bundle.getString("GUI.miscNoFreeLuckyEggCheckBox.toolTipText"));
						miscTweaksPanel.add(miscNoFreeLuckyEggCheckBox, new GridBagConstraints(2, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscBanBigMoneyManiacCheckBox ----
						miscBanBigMoneyManiacCheckBox.setEnabled(false);
						miscBanBigMoneyManiacCheckBox.setText(bundle.getString("GUI.miscBanBigMoneyManiacCheckBox.text"));
						miscBanBigMoneyManiacCheckBox.setToolTipText(bundle.getString("GUI.miscBanBigMoneyManiacCheckBox.toolTipText"));
						miscTweaksPanel.add(miscBanBigMoneyManiacCheckBox, new GridBagConstraints(3, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- mtNoneAvailableLabel ----
						mtNoneAvailableLabel.setText(bundle.getString("GUI.miscNoneAvailableLabel.text"));
						miscTweaksPanel.add(mtNoneAvailableLabel, new GridBagConstraints(1, 0, 2, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscSOSBattlesCheckBox ----
						miscSOSBattlesCheckBox.setEnabled(false);
						miscSOSBattlesCheckBox.setText(bundle.getString("CodeTweaks.sosBattles.name"));
						miscSOSBattlesCheckBox.setToolTipText(bundle.getString("CodeTweaks.sosBattles.toolTipText"));
						miscTweaksPanel.add(miscSOSBattlesCheckBox, new GridBagConstraints(4, 4, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscBalanceStaticLevelsCheckBox ----
						miscBalanceStaticLevelsCheckBox.setEnabled(false);
						miscBalanceStaticLevelsCheckBox.setText(bundle.getString("CodeTweaks.balanceStaticLevels.name"));
						miscBalanceStaticLevelsCheckBox.setToolTipText(bundle.getString("CodeTweaks.balanceStaticLevels.toolTipText"));
						miscTweaksPanel.add(miscBalanceStaticLevelsCheckBox, new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscRetainAltFormesCheckBox ----
						miscRetainAltFormesCheckBox.setEnabled(false);
						miscRetainAltFormesCheckBox.setText(bundle.getString("CodeTweaks.retainAltFormes.name"));
						miscRetainAltFormesCheckBox.setToolTipText(bundle.getString("CodeTweaks.retainAltFormes.toolTipText"));
						miscTweaksPanel.add(miscRetainAltFormesCheckBox, new GridBagConstraints(2, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscRunWithoutRunningShoesCheckBox ----
						miscRunWithoutRunningShoesCheckBox.setEnabled(false);
						miscRunWithoutRunningShoesCheckBox.setText(bundle.getString("CodeTweaks.runWithoutRunningShoes.name"));
						miscRunWithoutRunningShoesCheckBox.setToolTipText(bundle.getString("CodeTweaks.runWithoutRunningShoes.toolTipText"));
						miscTweaksPanel.add(miscRunWithoutRunningShoesCheckBox, new GridBagConstraints(3, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscFasterHPAndEXPBarsCheckBox ----
						miscFasterHPAndEXPBarsCheckBox.setEnabled(false);
						miscFasterHPAndEXPBarsCheckBox.setText(bundle.getString("CodeTweaks.fasterHpAndExpBars.name"));
						miscFasterHPAndEXPBarsCheckBox.setToolTipText(bundle.getString("CodeTweaks.fasterHpAndExpBars.toolTipText"));
						miscTweaksPanel.add(miscFasterHPAndEXPBarsCheckBox, new GridBagConstraints(4, 5, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscForceChallengeModeCheckBox ----
						miscForceChallengeModeCheckBox.setEnabled(false);
						miscForceChallengeModeCheckBox.setText(bundle.getString("CodeTweaks.forceChallengeMode.name"));
						miscForceChallengeModeCheckBox.setToolTipText(bundle.getString("CodeTweaks.forceChallengeMode.toolTipText"));
						miscTweaksPanel.add(miscForceChallengeModeCheckBox, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));

						//---- miscFastDistortionWorldCheckBox ----
						miscFastDistortionWorldCheckBox.setEnabled(false);
						miscFastDistortionWorldCheckBox.setText(bundle.getString("CodeTweaks.fastDistortionWorld.name"));
						miscFastDistortionWorldCheckBox.setToolTipText(bundle.getString("CodeTweaks.fastDistortionWorld.toolTipText"));
						miscTweaksPanel.add(miscFastDistortionWorldCheckBox, new GridBagConstraints(1, 6, 1, 1, 0.0, 0.0,
							GridBagConstraints.WEST, GridBagConstraints.NONE,
							new Insets(0, 0, 0, 0), 0, 0));
					}
					baseTweaksPanel.add(miscTweaksPanel, new GridBagConstraints(1, 1, 1, 1, 0.1, 0.1,
						GridBagConstraints.CENTER, GridBagConstraints.BOTH,
						new Insets(0, 0, 0, 0), 0, 0));
					baseTweaksPanel.add(hSpacer58, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					baseTweaksPanel.add(vSpacer60, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
					baseTweaksPanel.add(hSpacer59, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
						new Insets(0, 0, 0, 0), 0, 0));
					baseTweaksPanel.add(vSpacer61, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
						GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
						new Insets(0, 0, 0, 0), 0, 0));
				}
				tabbedPane1.addTab(bundle.getString("GUI.miscTweaksPanel.title"), baseTweaksPanel);
			}
			mainPanel.add(tabbedPane1, new GridBagConstraints(1, 8, 11, 1, 0.5, 0.1,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- openROMButton ----
			openROMButton.setRequestFocusEnabled(false);
			openROMButton.setText(bundle.getString("GUI.openROMButton.text"));
			mainPanel.add(openROMButton, new GridBagConstraints(9, 1, 3, 1, 0.3, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 10, 0), 0, 0));

			//---- randomizeSaveButton ----
			randomizeSaveButton.setText(bundle.getString("GUI.randomizeSaveButton.text"));
			mainPanel.add(randomizeSaveButton, new GridBagConstraints(9, 2, 3, 1, 0.3, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 10, 0), 0, 0));

			//---- premadeSeedButton ----
			premadeSeedButton.setText(bundle.getString("GUI.premadeSeedButton.text"));
			mainPanel.add(premadeSeedButton, new GridBagConstraints(9, 3, 3, 1, 0.3, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 10, 0), 0, 0));

			//---- settingsButton ----
			settingsButton.setText(bundle.getString("GUI.settingsButton.text"));
			mainPanel.add(settingsButton, new GridBagConstraints(9, 4, 3, 1, 0.3, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- websiteLinkLabel ----
			websiteLinkLabel.setText(bundle.getString("GUI.websiteLinkLabel.text"));
			mainPanel.add(websiteLinkLabel, new GridBagConstraints(11, 6, 1, 1, 0.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- versionLabel ----
			versionLabel.setText(bundle.getString("GUI.versionLabel.text"));
			mainPanel.add(versionLabel, new GridBagConstraints(2, 6, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer60, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer61, new GridBagConstraints(12, 3, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(vSpacer62, new GridBagConstraints(10, 0, 2, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(vSpacer63, new GridBagConstraints(7, 9, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- gameMascotLabel ----
			gameMascotLabel.setIcon(new ImageIcon(getClass().getResource("/com/dabomstew/pkrandom/newgui/emptyIcon.png")));
			gameMascotLabel.setText("");
			mainPanel.add(gameMascotLabel, new GridBagConstraints(7, 1, 1, 4, 0.1, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- saveSettingsButton ----
			saveSettingsButton.setEnabled(false);
			saveSettingsButton.setText(bundle.getString("GUI.saveSettingsButton.text"));
			saveSettingsButton.setToolTipText(bundle.getString("GUI.saveSettingsButton.toolTipText"));
			mainPanel.add(saveSettingsButton, new GridBagConstraints(5, 4, 1, 1, 0.1, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- loadSettingsButton ----
			loadSettingsButton.setEnabled(false);
			loadSettingsButton.setText(bundle.getString("GUI.loadSettingsButton.text"));
			loadSettingsButton.setToolTipText(bundle.getString("GUI.loadSettingsButton.toolTipText"));
			mainPanel.add(loadSettingsButton, new GridBagConstraints(4, 4, 1, 1, 0.1, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer62, new GridBagConstraints(3, 2, 1, 1, 0.1, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer63, new GridBagConstraints(6, 2, 1, 1, 0.2, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(hSpacer64, new GridBagConstraints(8, 2, 1, 1, 0.1, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
			mainPanel.add(vSpacer64, new GridBagConstraints(2, 5, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
				new Insets(0, 0, 0, 0), 0, 0));

			//---- wikiLinkLabel ----
			wikiLinkLabel.setHorizontalAlignment(SwingConstants.LEADING);
			wikiLinkLabel.setText(bundle.getString("GUI.wikiLinkLabel.text"));
			mainPanel.add(wikiLinkLabel, new GridBagConstraints(10, 6, 1, 1, 0.0, 0.0,
				GridBagConstraints.EAST, GridBagConstraints.NONE,
				new Insets(0, 0, 0, 10), 0, 0));
		}

		//---- baseStatButtonGroup ----
		ButtonGroup baseStatButtonGroup = new ButtonGroup();
		baseStatButtonGroup.add(pbsUnchangedRadioButton);
		baseStatButtonGroup.add(pbsShuffleRadioButton);
		baseStatButtonGroup.add(pbsRandomRadioButton);

		//---- expCurveButtonGroup ----
		ButtonGroup expCurveButtonGroup = new ButtonGroup();
		expCurveButtonGroup.add(pbsLegendariesSlowRadioButton);
		expCurveButtonGroup.add(pbsStrongLegendariesSlowRadioButton);
		expCurveButtonGroup.add(pbsAllMediumFastRadioButton);

		//---- pokemonTypeButtonGroup ----
		ButtonGroup pokemonTypeButtonGroup = new ButtonGroup();
		pokemonTypeButtonGroup.add(ptUnchangedRadioButton);
		pokemonTypeButtonGroup.add(ptRandomFollowEvolutionsRadioButton);
		pokemonTypeButtonGroup.add(ptRandomCompletelyRadioButton);

		//---- pokemonAbilityButtonGroup ----
		ButtonGroup pokemonAbilityButtonGroup = new ButtonGroup();
		pokemonAbilityButtonGroup.add(paUnchangedRadioButton);
		pokemonAbilityButtonGroup.add(paRandomRadioButton);

		//---- pokemonEvoButtonGroup ----
		ButtonGroup pokemonEvoButtonGroup = new ButtonGroup();
		pokemonEvoButtonGroup.add(peUnchangedRadioButton);
		pokemonEvoButtonGroup.add(peRandomRadioButton);
		pokemonEvoButtonGroup.add(peRandomEveryLevelRadioButton);

		//---- starterButtonGroup ----
		ButtonGroup starterButtonGroup = new ButtonGroup();
		starterButtonGroup.add(spUnchangedRadioButton);
		starterButtonGroup.add(spCustomRadioButton);
		starterButtonGroup.add(spRandomCompletelyRadioButton);
		starterButtonGroup.add(spRandomTwoEvosRadioButton);

		//---- staticPokemonButtonGroup ----
		ButtonGroup staticPokemonButtonGroup = new ButtonGroup();
		staticPokemonButtonGroup.add(stpUnchangedRadioButton);
		staticPokemonButtonGroup.add(stpSwapLegendariesSwapStandardsRadioButton);
		staticPokemonButtonGroup.add(stpRandomCompletelyRadioButton);
		staticPokemonButtonGroup.add(stpRandomSimilarStrengthRadioButton);

		//---- tradeButtonGroup ----
		ButtonGroup tradeButtonGroup = new ButtonGroup();
		tradeButtonGroup.add(igtUnchangedRadioButton);
		tradeButtonGroup.add(igtRandomizeGivenPokemonOnlyRadioButton);
		tradeButtonGroup.add(igtRandomizeBothRequestedGivenRadioButton);

		//---- pokemonMovesetButtonGroup ----
		ButtonGroup pokemonMovesetButtonGroup = new ButtonGroup();
		pokemonMovesetButtonGroup.add(pmsUnchangedRadioButton);
		pokemonMovesetButtonGroup.add(pmsRandomPreferringSameTypeRadioButton);
		pokemonMovesetButtonGroup.add(pmsRandomCompletelyRadioButton);
		pokemonMovesetButtonGroup.add(pmsMetronomeOnlyModeRadioButton);

		//---- totemPokemonButtonGroup ----
		ButtonGroup totemPokemonButtonGroup = new ButtonGroup();
		totemPokemonButtonGroup.add(totpUnchangedRadioButton);
		totemPokemonButtonGroup.add(totpRandomRadioButton);
		totemPokemonButtonGroup.add(totpRandomSimilarStrengthRadioButton);

		//---- allyPokemonButtonGroup ----
		ButtonGroup allyPokemonButtonGroup = new ButtonGroup();
		allyPokemonButtonGroup.add(totpAllyUnchangedRadioButton);
		allyPokemonButtonGroup.add(totpAllyRandomRadioButton);
		allyPokemonButtonGroup.add(totpAllyRandomSimilarStrengthRadioButton);

		//---- auraButtonGroup ----
		ButtonGroup auraButtonGroup = new ButtonGroup();
		auraButtonGroup.add(totpAuraUnchangedRadioButton);
		auraButtonGroup.add(totpAuraRandomRadioButton);
		auraButtonGroup.add(totpAuraRandomSameStrengthRadioButton);

		//---- wildPokemonButtonGroup ----
		ButtonGroup wildPokemonButtonGroup = new ButtonGroup();
		wildPokemonButtonGroup.add(wpUnchangedRadioButton);
		wildPokemonButtonGroup.add(wpRandomRadioButton);
		wildPokemonButtonGroup.add(wpArea1To1RadioButton);
		wildPokemonButtonGroup.add(wpGlobal1To1RadioButton);

		//---- wildPokemonAdditionalButtonGroup ----
		ButtonGroup wildPokemonAdditionalButtonGroup = new ButtonGroup();
		wildPokemonAdditionalButtonGroup.add(wpARNoneRadioButton);
		wildPokemonAdditionalButtonGroup.add(wpARSimilarStrengthRadioButton);
		wildPokemonAdditionalButtonGroup.add(wpARCatchEmAllModeRadioButton);
		wildPokemonAdditionalButtonGroup.add(wpARTypeThemeAreasRadioButton);

		//---- tmsButtonGroup ----
		ButtonGroup tmsButtonGroup = new ButtonGroup();
		tmsButtonGroup.add(tmUnchangedRadioButton);
		tmsButtonGroup.add(tmRandomRadioButton);

		//---- tmCompatButtonGroup ----
		ButtonGroup tmCompatButtonGroup = new ButtonGroup();
		tmCompatButtonGroup.add(thcUnchangedRadioButton);
		tmCompatButtonGroup.add(thcRandomPreferSameTypeRadioButton);
		tmCompatButtonGroup.add(thcRandomCompletelyRadioButton);
		tmCompatButtonGroup.add(thcFullCompatibilityRadioButton);

		//---- tutorButtonGroup ----
		ButtonGroup tutorButtonGroup = new ButtonGroup();
		tutorButtonGroup.add(mtUnchangedRadioButton);
		tutorButtonGroup.add(mtRandomRadioButton);

		//---- tutorCompatButtonGroup ----
		ButtonGroup tutorCompatButtonGroup = new ButtonGroup();
		tutorCompatButtonGroup.add(mtcUnchangedRadioButton);
		tutorCompatButtonGroup.add(mtcRandomPreferSameTypeRadioButton);
		tutorCompatButtonGroup.add(mtcRandomCompletelyRadioButton);
		tutorCompatButtonGroup.add(mtcFullCompatibilityRadioButton);

		//---- fieldItemButtonGroup ----
		ButtonGroup fieldItemButtonGroup = new ButtonGroup();
		fieldItemButtonGroup.add(fiUnchangedRadioButton);
		fieldItemButtonGroup.add(fiShuffleRadioButton);
		fieldItemButtonGroup.add(fiRandomRadioButton);
		fieldItemButtonGroup.add(fiRandomEvenDistributionRadioButton);

		//---- shopItemButtonGroup ----
		ButtonGroup shopItemButtonGroup = new ButtonGroup();
		shopItemButtonGroup.add(shUnchangedRadioButton);
		shopItemButtonGroup.add(shShuffleRadioButton);
		shopItemButtonGroup.add(shRandomRadioButton);

		//---- pickupItemButtonGroup ----
		ButtonGroup pickupItemButtonGroup = new ButtonGroup();
		pickupItemButtonGroup.add(puUnchangedRadioButton);
		pickupItemButtonGroup.add(puRandomRadioButton);
	}

	private JPanel mainPanel;
	private JCheckBox raceModeCheckBox;
	private JCheckBox limitPokemonCheckBox;
	private JButton limitPokemonButton;
	private JCheckBox noIrregularAltFormesCheckBox;
	private JLabel romNameLabel;
	private JLabel romCodeLabel;
	private JLabel romSupportLabel;
	private JTabbedPane tabbedPane1;
	private JRadioButton pbsUnchangedRadioButton;
	private JRadioButton pbsShuffleRadioButton;
	private JRadioButton pbsLegendariesSlowRadioButton;
	private JRadioButton pbsStrongLegendariesSlowRadioButton;
	private JCheckBox pbsStandardizeEXPCurvesCheckBox;
	private JCheckBox pbsFollowEvolutionsCheckBox;
	private JCheckBox pbsUpdateBaseStatsCheckBox;
	private JCheckBox pbsFollowMegaEvosCheckBox;
	private JComboBox<String> pbsUpdateComboBox;
	private JComboBox<String> pbsEXPCurveComboBox;
	private JCheckBox pbsAssignEvoStatsRandomlyCheckBox;
	private JRadioButton pbsRandomRadioButton;
	private JRadioButton pbsAllMediumFastRadioButton;
	private JRadioButton ptUnchangedRadioButton;
	private JRadioButton ptRandomFollowEvolutionsRadioButton;
	private JRadioButton ptRandomCompletelyRadioButton;
	private JCheckBox ptFollowMegaEvosCheckBox;
	private JCheckBox ptIsDualTypeCheckBox;
	private JPanel pokemonAbilitiesPanel;
	private JRadioButton paUnchangedRadioButton;
	private JRadioButton paRandomRadioButton;
	private JCheckBox paAllowWonderGuardCheckBox;
	private JCheckBox paFollowEvolutionsCheckBox;
	private JCheckBox paTrappingAbilitiesCheckBox;
	private JCheckBox paNegativeAbilitiesCheckBox;
	private JCheckBox paBadAbilitiesCheckBox;
	private JCheckBox paWeighDuplicatesTogetherCheckBox;
	private JCheckBox paEnsureTwoAbilitiesCheckbox;
	private JCheckBox paFollowMegaEvosCheckBox;
	private JRadioButton peUnchangedRadioButton;
	private JRadioButton peRandomRadioButton;
	private JCheckBox peSimilarStrengthCheckBox;
	private JCheckBox peSameTypingCheckBox;
	private JCheckBox peLimitEvolutionsToThreeCheckBox;
	private JCheckBox peForceChangeCheckBox;
	private JCheckBox peChangeImpossibleEvosCheckBox;
	private JCheckBox peMakeEvolutionsEasierCheckBox;
	private JCheckBox peAllowAltFormesCheckBox;
	private JCheckBox peRemoveTimeBasedEvolutionsCheckBox;
	private JRadioButton peRandomEveryLevelRadioButton;
	private JRadioButton spUnchangedRadioButton;
	private JRadioButton spCustomRadioButton;
	private JRadioButton spRandomCompletelyRadioButton;
	private JRadioButton spRandomTwoEvosRadioButton;
	private JComboBox<String> spComboBox1;
	private JComboBox<String> spComboBox2;
	private JComboBox<String> spComboBox3;
	private JCheckBox spRandomizeStarterHeldItemsCheckBox;
	private JCheckBox spBanBadItemsCheckBox;
	private JCheckBox spAllowAltFormesCheckBox;
	private JRadioButton stpUnchangedRadioButton;
	private JRadioButton stpSwapLegendariesSwapStandardsRadioButton;
	private JRadioButton stpRandomCompletelyRadioButton;
	private JRadioButton stpRandomSimilarStrengthRadioButton;
	private JCheckBox stpSwapMegaEvosCheckBox;
	private JCheckBox stpPercentageLevelModifierCheckBox;
	private JSlider stpPercentageLevelModifierSlider;
	private JCheckBox stpRandomize600BSTCheckBox;
	private JCheckBox stpAllowAltFormesCheckBox;
	private JCheckBox stpLimitMainGameLegendariesCheckBox;
	private JCheckBox stpFixMusicCheckBox;
	private JRadioButton igtUnchangedRadioButton;
	private JRadioButton igtRandomizeGivenPokemonOnlyRadioButton;
	private JRadioButton igtRandomizeBothRequestedGivenRadioButton;
	private JCheckBox igtRandomizeNicknamesCheckBox;
	private JCheckBox igtRandomizeOTsCheckBox;
	private JCheckBox igtRandomizeIVsCheckBox;
	private JCheckBox igtRandomizeItemsCheckBox;
	private JCheckBox mdRandomizeMovePowerCheckBox;
	private JCheckBox mdRandomizeMoveAccuracyCheckBox;
	private JCheckBox mdRandomizeMovePPCheckBox;
	private JCheckBox mdRandomizeMoveTypesCheckBox;
	private JCheckBox mdRandomizeMoveCategoryCheckBox;
	private JCheckBox mdUpdateMovesCheckBox;
	private JComboBox<String> mdUpdateComboBox;
	private JRadioButton pmsUnchangedRadioButton;
	private JRadioButton pmsRandomPreferringSameTypeRadioButton;
	private JRadioButton pmsRandomCompletelyRadioButton;
	private JRadioButton pmsMetronomeOnlyModeRadioButton;
	private JCheckBox pmsGuaranteedLevel1MovesCheckBox;
	private JCheckBox pmsReorderDamagingMovesCheckBox;
	private JCheckBox pmsNoGameBreakingMovesCheckBox;
	private JCheckBox pmsForceGoodDamagingCheckBox;
	private JSlider pmsGuaranteedLevel1MovesSlider;
	private JSlider pmsForceGoodDamagingSlider;
	private JCheckBox pmsEvolutionMovesCheckBox;
	private JCheckBox tpRivalCarriesStarterCheckBox;
	private JCheckBox tpSimilarStrengthCheckBox;
	private JCheckBox tpRandomizeTrainerNamesCheckBox;
	private JCheckBox tpRandomizeTrainerClassNamesCheckBox;
	private JCheckBox tpForceFullyEvolvedAtCheckBox;
	private JSlider tpForceFullyEvolvedAtSlider;
	private JSlider tpPercentageLevelModifierSlider;
	private JCheckBox tpPercentageLevelModifierCheckBox;
	private JCheckBox tpWeightTypesCheckBox;
	private JCheckBox tpDontUseLegendariesCheckBox;
	private JCheckBox tpNoEarlyWonderGuardCheckBox;
	private JCheckBox tpAllowAlternateFormesCheckBox;
	private JCheckBox tpSwapMegaEvosCheckBox;
	private JCheckBox tpRandomShinyTrainerPokemonCheckBox;
	private JCheckBox tpEliteFourUniquePokemonCheckBox;
	private JSpinner tpEliteFourUniquePokemonSpinner;
	private JComboBox<String> tpComboBox;
	private JCheckBox tpDoubleBattleModeCheckBox;
	private JLabel tpAdditionalPokemonForLabel;
	private JCheckBox tpBossTrainersCheckBox;
	private JCheckBox tpImportantTrainersCheckBox;
	private JCheckBox tpRegularTrainersCheckBox;
	private JSpinner tpBossTrainersSpinner;
	private JSpinner tpImportantTrainersSpinner;
	private JSpinner tpRegularTrainersSpinner;
	private JLabel tpHeldItemsLabel;
	private JCheckBox tpBossTrainersItemsCheckBox;
	private JCheckBox tpImportantTrainersItemsCheckBox;
	private JCheckBox tpRegularTrainersItemsCheckBox;
	private JCheckBox tpConsumableItemsOnlyCheckBox;
	private JCheckBox tpSensibleItemsCheckBox;
	private JCheckBox tpHighestLevelGetsItemCheckBox;
	private JCheckBox tpBetterMovesetsCheckBox;
	private JPanel totpPanel;
	private JRadioButton totpUnchangedRadioButton;
	private JPanel totpAllyPanel;
	private JRadioButton totpAllyUnchangedRadioButton;
	private JRadioButton totpAllyRandomRadioButton;
	private JRadioButton totpAllyRandomSimilarStrengthRadioButton;
	private JPanel totpAuraPanel;
	private JRadioButton totpAuraUnchangedRadioButton;
	private JRadioButton totpAuraRandomRadioButton;
	private JRadioButton totpAuraRandomSameStrengthRadioButton;
	private JSlider totpPercentageLevelModifierSlider;
	private JCheckBox totpPercentageLevelModifierCheckBox;
	private JCheckBox totpRandomizeHeldItemsCheckBox;
	private JCheckBox totpAllowAltFormesCheckBox;
	private JRadioButton totpRandomRadioButton;
	private JRadioButton totpRandomSimilarStrengthRadioButton;
	private JRadioButton wpUnchangedRadioButton;
	private JRadioButton wpRandomRadioButton;
	private JRadioButton wpArea1To1RadioButton;
	private JRadioButton wpGlobal1To1RadioButton;
	private JRadioButton wpARNoneRadioButton;
	private JRadioButton wpARSimilarStrengthRadioButton;
	private JRadioButton wpARCatchEmAllModeRadioButton;
	private JRadioButton wpARTypeThemeAreasRadioButton;
	private JCheckBox wpUseTimeBasedEncountersCheckBox;
	private JCheckBox wpDontUseLegendariesCheckBox;
	private JCheckBox wpSetMinimumCatchRateCheckBox;
	private JCheckBox wpRandomizeHeldItemsCheckBox;
	private JCheckBox wpBanBadItemsCheckBox;
	private JCheckBox wpBalanceShakingGrassPokemonCheckBox;
	private JCheckBox wpPercentageLevelModifierCheckBox;
	private JSlider wpPercentageLevelModifierSlider;
	private JSlider wpSetMinimumCatchRateSlider;
	private JCheckBox wpAllowAltFormesCheckBox;
	private JRadioButton tmUnchangedRadioButton;
	private JRadioButton tmRandomRadioButton;
	private JCheckBox tmNoGameBreakingMovesCheckBox;
	private JCheckBox tmKeepFieldMoveTMsCheckBox;
	private JCheckBox tmForceGoodDamagingCheckBox;
	private JSlider tmForceGoodDamagingSlider;
	private JRadioButton thcUnchangedRadioButton;
	private JRadioButton thcRandomPreferSameTypeRadioButton;
	private JRadioButton thcRandomCompletelyRadioButton;
	private JRadioButton thcFullCompatibilityRadioButton;
	private JCheckBox tmFollowEvolutionsCheckBox;
	private JCheckBox tmLevelupMoveSanityCheckBox;
	private JCheckBox tmFullHMCompatibilityCheckBox;
	private JPanel moveTutorPanel;
	private JPanel mtMovesPanel;
	private JRadioButton mtUnchangedRadioButton;
	private JRadioButton mtRandomRadioButton;
	private JSlider mtForceGoodDamagingSlider;
	private JCheckBox mtNoGameBreakingMovesCheckBox;
	private JCheckBox mtKeepFieldMoveTutorsCheckBox;
	private JCheckBox mtForceGoodDamagingCheckBox;
	private JPanel mtCompatPanel;
	private JRadioButton mtcUnchangedRadioButton;
	private JRadioButton mtcRandomPreferSameTypeRadioButton;
	private JRadioButton mtcRandomCompletelyRadioButton;
	private JRadioButton mtcFullCompatibilityRadioButton;
	private JCheckBox mtFollowEvolutionsCheckBox;
	private JCheckBox mtLevelupMoveSanityCheckBox;
	private JLabel mtNoExistLabel;
	private JRadioButton fiUnchangedRadioButton;
	private JRadioButton fiShuffleRadioButton;
	private JRadioButton fiRandomRadioButton;
	private JRadioButton fiRandomEvenDistributionRadioButton;
	private JCheckBox fiBanBadItemsCheckBox;
	private JPanel shopItemsPanel;
	private JRadioButton shUnchangedRadioButton;
	private JRadioButton shShuffleRadioButton;
	private JRadioButton shRandomRadioButton;
	private JCheckBox shBanOverpoweredShopItemsCheckBox;
	private JCheckBox shBanBadItemsCheckBox;
	private JCheckBox shBanRegularShopItemsCheckBox;
	private JCheckBox shBalanceShopItemPricesCheckBox;
	private JCheckBox shGuaranteeEvolutionItemsCheckBox;
	private JCheckBox shGuaranteeXItemsCheckBox;
	private JPanel pickupItemsPanel;
	private JRadioButton puUnchangedRadioButton;
	private JRadioButton puRandomRadioButton;
	private JCheckBox puBanBadItemsCheckBox;
	private JPanel baseTweaksPanel;
	private JPanel miscTweaksPanel;
	private JCheckBox miscBWExpPatchCheckBox;
	private JCheckBox miscNerfXAccuracyCheckBox;
	private JCheckBox miscFixCritRateCheckBox;
	private JCheckBox miscFastestTextCheckBox;
	private JCheckBox miscRunningShoesIndoorsCheckBox;
	private JCheckBox miscRandomizePCPotionCheckBox;
	private JCheckBox miscAllowPikachuEvolutionCheckBox;
	private JCheckBox miscGiveNationalDexAtCheckBox;
	private JCheckBox miscUpdateTypeEffectivenessCheckBox;
	private JCheckBox miscLowerCasePokemonNamesCheckBox;
	private JCheckBox miscRandomizeCatchingTutorialCheckBox;
	private JCheckBox miscBanLuckyEggCheckBox;
	private JCheckBox miscNoFreeLuckyEggCheckBox;
	private JCheckBox miscBanBigMoneyManiacCheckBox;
	private JLabel mtNoneAvailableLabel;
	private JCheckBox miscSOSBattlesCheckBox;
	private JCheckBox miscBalanceStaticLevelsCheckBox;
	private JCheckBox miscRetainAltFormesCheckBox;
	private JCheckBox miscRunWithoutRunningShoesCheckBox;
	private JCheckBox miscFasterHPAndEXPBarsCheckBox;
	private JCheckBox miscForceChallengeModeCheckBox;
	private JCheckBox miscFastDistortionWorldCheckBox;
	private JButton openROMButton;
	private JButton randomizeSaveButton;
	private JButton premadeSeedButton;
	private JButton settingsButton;
	private JLabel websiteLinkLabel;
	private JLabel versionLabel;
	private JLabel gameMascotLabel;
	private JButton saveSettingsButton;
	private JButton loadSettingsButton;
	private JLabel wikiLinkLabel;
}
