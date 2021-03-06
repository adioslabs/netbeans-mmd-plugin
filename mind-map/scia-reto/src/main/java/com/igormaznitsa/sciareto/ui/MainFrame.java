/*
 * Copyright 2016 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.sciareto.ui;

import com.igormaznitsa.sciareto.ui.tabs.EditorTabPane;
import com.igormaznitsa.sciareto.ui.tabs.TabTitle;
import com.igormaznitsa.sciareto.preferences.PreferencesPanel;
import com.igormaznitsa.sciareto.ui.tree.ExplorerTree;
import com.igormaznitsa.sciareto.ui.misc.AboutPanel;
import com.igormaznitsa.sciareto.ui.editors.PictureViewer;
import com.igormaznitsa.sciareto.ui.editors.TextEditor;
import com.igormaznitsa.sciareto.ui.editors.MMDEditor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileView;

import org.apache.commons.io.FilenameUtils;

import com.igormaznitsa.meta.annotation.MayContainNull;
import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mindmap.model.MindMap;
import com.igormaznitsa.mindmap.model.logger.Logger;
import com.igormaznitsa.mindmap.model.logger.LoggerFactory;
import com.igormaznitsa.mindmap.swing.panel.utils.Utils;
import com.igormaznitsa.sciareto.Context;
import com.igormaznitsa.sciareto.Main;
import com.igormaznitsa.sciareto.preferences.FileHistoryManager;
import com.igormaznitsa.sciareto.preferences.PreferencesManager;
import com.igormaznitsa.sciareto.ui.editors.EditorType;
import com.igormaznitsa.sciareto.ui.misc.DonateButton;
import com.igormaznitsa.sciareto.ui.misc.GoToFilePanel;
import com.igormaznitsa.sciareto.ui.platform.PlatformMenuAction;
import com.igormaznitsa.sciareto.ui.platform.PlatformMenuEvent;
import com.igormaznitsa.sciareto.ui.platform.PlatformProvider;
import com.igormaznitsa.sciareto.ui.tree.NodeFileOrFolder;
import com.igormaznitsa.sciareto.ui.tree.NodeProject;
import com.igormaznitsa.sciareto.ui.tree.NodeProjectGroup;

public final class MainFrame extends javax.swing.JFrame implements Context, PlatformMenuAction {

  private static final long serialVersionUID = 3798040833406256900L;

  private static final Logger LOGGER = LoggerFactory.getLogger(MainFrame.class);

  private static final boolean DELETE_MOVING_FILE_TO_TRASH = true;

  private final EditorTabPane tabPane;
  private final ExplorerTree explorerTree;

  private final boolean stateless;

  private final AtomicReference<Runnable> taskToEndFullScreen = new AtomicReference<>();

  private final JPanel stackPanel;
  private final JPanel mainPanel;

  private final AtomicReference<FindTextPanel> currentFindTextPanel = new AtomicReference<>();

  public MainFrame(@Nonnull @MustNotContainNull final String... args) {
    super();
    initComponents();

    if (PlatformProvider.getPlatform().registerPlatformMenuEvent(com.igormaznitsa.sciareto.ui.platform.PlatformMenuEvent.ABOUT, this)) {
      this.menuHelp.setVisible(false);
    }

    if (PlatformProvider.getPlatform().registerPlatformMenuEvent(com.igormaznitsa.sciareto.ui.platform.PlatformMenuEvent.PREFERENCES, this)) {
      this.menuPreferences.setVisible(false);
    }

    if (PlatformProvider.getPlatform().registerPlatformMenuEvent(com.igormaznitsa.sciareto.ui.platform.PlatformMenuEvent.QUIT, this)) {
      this.separatorExitSection.setVisible(false);
      this.menuExit.setVisible(false);
    }

    PlatformProvider.getPlatform().registerPlatformMenuEvent(PlatformMenuEvent.REOPEN_APPLICATION, this);

    this.stackPanel = new JPanel();
    this.stackPanel.setFocusable(false);
    this.stackPanel.setOpaque(false);
    this.stackPanel.setBorder(BorderFactory.createEmptyBorder(32, 32, 16, 32));
    this.stackPanel.setLayout(new BoxLayout(this.stackPanel, BoxLayout.Y_AXIS));

    final JPanel glassPanel = (JPanel) this.getGlassPane();
    glassPanel.setOpaque(false);

    this.setGlassPane(glassPanel);

    glassPanel.setLayout(new BorderLayout(8, 8));
    glassPanel.add(Box.createGlue(), BorderLayout.CENTER);

    final JPanel ppanel = new JPanel(new BorderLayout(0, 0));
    ppanel.setFocusable(false);
    ppanel.setOpaque(false);
    ppanel.setCursor(null);
    ppanel.add(this.stackPanel, BorderLayout.SOUTH);

    glassPanel.add(ppanel, BorderLayout.EAST);

    this.stackPanel.add(Box.createGlue());

    glassPanel.setVisible(false);

    this.setTitle("Scia Reto");

    setIconImage(UiUtils.loadImage("logo256x256.png"));

    this.stateless = args.length > 0;

    final MainFrame theInstance = this;

    this.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(@Nonnull final WindowEvent e) {
        if (doClosing()) {
          dispose();
        }
      }
    });

    this.tabPane = new EditorTabPane(this);

    this.explorerTree = new ExplorerTree(this);

    final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    splitPane.setOneTouchExpandable(true);
    splitPane.setDividerLocation(250);
    splitPane.setResizeWeight(0.0d);
    splitPane.setLeftComponent(this.explorerTree);

    this.mainPanel = new JPanel(new BorderLayout(0, 0));
    this.mainPanel.add(this.tabPane, BorderLayout.CENTER);

    splitPane.setRightComponent(this.mainPanel);

    add(splitPane, BorderLayout.CENTER);

    this.menuOpenRecentProject.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        final File[] lastOpenedProjects = FileHistoryManager.getInstance().getLastOpenedProjects();
        if (lastOpenedProjects.length > 0) {
          for (final File folder : lastOpenedProjects) {
            final JMenuItem item = new JMenuItem(folder.getName());
            item.setToolTipText(folder.getAbsolutePath());
            item.addActionListener(new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                openProject(folder, false);
              }
            });
            menuOpenRecentProject.add(item);
          }
        }
      }

      @Override
      public void menuDeselected(MenuEvent e) {
        menuOpenRecentProject.removeAll();
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    this.menuOpenRecentFile.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        final File[] lastOpenedFiles = FileHistoryManager.getInstance().getLastOpenedFiles();
        if (lastOpenedFiles.length > 0) {
          for (final File file : lastOpenedFiles) {
            final JMenuItem item = new JMenuItem(file.getName());
            item.setToolTipText(file.getAbsolutePath());
            item.addActionListener(new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                openFileAsTab(file);
              }
            });
            menuOpenRecentFile.add(item);
          }
        }
      }

      @Override
      public void menuDeselected(MenuEvent e) {
        menuOpenRecentFile.removeAll();
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    if (!this.stateless) {
      restoreState();
    }
    else {
      boolean openedProject = false;
      for (final String filePath : args) {
        final File file = new File(filePath);
        if (file.isDirectory()) {
          openedProject = true;
          openProject(file, true);
        }
        else if (file.isFile()) {
          openFileAsTab(file);
        }
      }
      if (!openedProject) {
        //TODO try to hide project panel!
      }
    }

    final LookAndFeel current = UIManager.getLookAndFeel();
    final ButtonGroup lfGroup = new ButtonGroup();
    final String currentLFClassName = current.getClass().getName();
    for (final UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
      final JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(info.getName());
      lfGroup.add(menuItem);
      if (currentLFClassName.equals(info.getClassName())) {
        menuItem.setSelected(true);
      }
      menuItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(@Nonnull final ActionEvent e) {
          try {
            UIManager.setLookAndFeel(info.getClassName());
            SwingUtilities.updateComponentTreeUI(theInstance);
            PreferencesManager.getInstance().getPreferences().put(Main.PROPERTY_LOOKANDFEEL, info.getClassName());
            PreferencesManager.getInstance().flush();
          }
          catch (Exception ex) {
            LOGGER.error("Can't change LF", ex);
          }
        }
      });
      this.menuLookAndFeel.add(menuItem);
    }

    this.menuGoToFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | KeyEvent.SHIFT_MASK));
    this.menuSaveAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

    this.menuRedo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | KeyEvent.SHIFT_MASK));
    this.menuUndo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    this.menuFindText.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (tabPane.getTabCount() > 0) {
          tabPane.setSelectedIndex(0);
          processTabChanged(tabPane.getCurrentTitle());
        }
        else {
          processTabChanged(null);
        }
        tabPane.setNotifyForTabChanged(true);
      }
    });

  }

  public void processTabChanged(@Nullable final TabTitle title) {
    this.menuSaveAll.setEnabled(this.tabPane.hasEditableAndChangedDocument());

    if (title != null && !this.tabPane.isEmpty() && title.getProvider().doesSupportPatternSearch()) {
      this.menuFindText.setEnabled(true);
      final FindTextPanel findTextPanel = this.currentFindTextPanel.get();
      if (findTextPanel != null) {
        findTextPanel.updateUI(title);
      }
    }
    else {
      this.menuFindText.setEnabled(false);
      hideFindTextPane();
    }

    if (title == null) {
      this.menuRedo.setEnabled(false);
      this.menuUndo.setEnabled(false);
      this.menuSave.setEnabled(false);
      this.menuSaveAs.setEnabled(false);
    }
    else {
      this.menuRedo.setEnabled(title.getProvider().isRedo());
      this.menuUndo.setEnabled(title.getProvider().isUndo());
      if (title.getProvider().isSaveable()) {
        this.menuSave.setEnabled(title != null && title.isChanged());
        this.menuSaveAs.setEnabled(true);
      }
      else {
        this.menuSave.setEnabled(false);
        this.menuSaveAs.setEnabled(false);
      }
      title.visited();
    }
  }

  @Override
  public void notifyUpdateRedoUndo() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        processTabChanged(tabPane.getCurrentTitle());
      }
    });
  }

  public JPanel getStackPanel() {
    return this.stackPanel;
  }

  @Override
  public boolean doPlatformMenuAction(@Nonnull final PlatformMenuEvent event, @Nullable @MayContainNull final Object... args) {
    boolean handled = false;
    switch (event) {
      case ABOUT: {
        this.menuAboutActionPerformed(new ActionEvent(this, 0, "about"));
        handled = true;
      }
      break;
      case QUIT: {
        handled = doClosing();
        if (handled) {
          dispose();
        }
      }
      break;
      case REOPEN_APPLICATION: {
        this.setVisible(true);
        handled = true;
      }
      break;
      case PREFERENCES: {
        editPreferences();
        handled = true;
      }
      break;
    }
    return handled;
  }

  private boolean doClosing() {
    endFullScreenIfActive();

    boolean hasUnsaved = false;
    for (final TabTitle t : tabPane) {
      hasUnsaved |= t.isChanged();
    }

    if (hasUnsaved) {
      if (!DialogProviderManager.getInstance().getDialogProvider().msgConfirmOkCancel("Detected non-saved documents", "Detected unsaved documents! Close application?")) {
        return false;
      }
    }

    if (!this.stateless) {
      saveState();
    }
    return true;
  }

  @Override
  public void notifyFileRenamed(@Nullable @MustNotContainNull final List<File> affectedFiles, @Nonnull final File oldFile, @Nonnull final File newFile) {
    this.tabPane.replaceFileLink(oldFile, newFile);
    if (affectedFiles != null) {
      for (final TabTitle t : this.tabPane) {
        final File tabFile = t.getAssociatedFile();
        if (tabFile != null && affectedFiles.contains(tabFile)) {
          t.reload(true);
        }
      }
    }
  }

  @Override
  public boolean safeCloseEditorsForFile(@Nonnull final File file) {
    boolean changed = false;

    final List<TabTitle> list = new ArrayList<>();
    for (final TabTitle t : this.tabPane) {
      if (t.belongFolderOrSame(file)) {
        list.add(t);
        changed |= t.isChanged();
      }
    }

    if (changed && !DialogProviderManager.getInstance().getDialogProvider().msgConfirmOkCancel("Confirmation", "Some changed file will be affected! To close them?")) {
      return false;
    }

    closeTab(list.toArray(new TabTitle[list.size()]));

    return true;
  }

  private void restoreState() {
    try {
      final File[] activeProjects = FileHistoryManager.getInstance().getActiveProjects();
      for (final File f : activeProjects) {
        if (f.isDirectory()) {
          openProject(f, true);
        }
      }
      final File[] activeFiles = FileHistoryManager.getInstance().getActiveFiles();
      for (final File f : activeFiles) {
        if (f.isFile()) {
          openFileAsTab(f);
        }
      }
    }
    catch (IOException ex) {
      LOGGER.error("Can't restore state", ex);
    }
  }

  private void saveState() {
    try {
      final List<File> files = new ArrayList<>();
      for (final NodeFileOrFolder p : this.explorerTree.getCurrentGroup()) {
        final File f = ((NodeProject) p).getFolder();
        if (f.isDirectory()) {
          files.add(f);
        }
      }
      FileHistoryManager.getInstance().saveActiveProjects(files.toArray(new File[files.size()]));
      files.clear();

      for (final TabTitle p : this.tabPane) {
        final File f = p.getAssociatedFile();
        if (f != null && f.isFile()) {
          files.add(f);
        }
      }
      FileHistoryManager.getInstance().saveActiveFiles(files.toArray(new File[files.size()]));
    }
    catch (IOException ex) {
      LOGGER.error("Can't save state", ex);
    }
  }

  @Override
  public boolean openFileAsTab(@Nonnull final File file) {
    boolean result = false;
    if (file.isFile()) {
      if (this.tabPane.focusToFile(file)) {
        result = true;
      }
      else {
        final String ext = FilenameUtils.getExtension(file.getName()).toLowerCase(Locale.ENGLISH);
        if (ext.equals("mmd")) {
          try {
            final MMDEditor panel = new MMDEditor(this, file);
            this.tabPane.createTab(panel);
            result = true;
          }
          catch (IOException ex) {
            LOGGER.error("Can't load mind map", ex);
          }
        }
        else if (PictureViewer.SUPPORTED_FORMATS.contains(ext)) {
          try {
            final PictureViewer panel = new PictureViewer(this, file);
            this.tabPane.createTab(panel);
            result = true;
          }
          catch (IOException ex) {
            LOGGER.error("Can't load file as image", ex);
          }
        }
        else {
          if (file.length() >= (2L * 1024L * 1024L) && !DialogProviderManager.getInstance().getDialogProvider().msgConfirmYesNo("Very big file", "It is a very big file! Are you sure to open it?")) {
            return true;
          }

          try {
            final TextEditor panel = new TextEditor(this, file);
            this.tabPane.createTab(panel);
            result = true;
          }
          catch (IOException ex) {
            LOGGER.error("Can't load file as text", ex);
          }
          finally {
            processTabChange();
          }
        }
      }
    }
    if (result) {
      try {
        FileHistoryManager.getInstance().registerOpenedFile(file);
      }
      catch (IOException x) {
        LOGGER.error("Can't register last opened file", x);
      }
      finally {
        this.tabPane.focusToFile(file);
      }
    }
    return result;
  }

  @Override
  public void focusInTree(@Nonnull final TabTitle title) {
    final File file = title.getAssociatedFile();
    if (file != null) {
      this.explorerTree.focusToFileItem(file);
    }
  }

  @Override
  public void focusInTree(@Nonnull final File file) {
    this.explorerTree.focusToFileItem(file);
  }

  @Override
  public void closeTab(@Nonnull final TabTitle... titles) {
    for (final TabTitle t : titles) {
      this.tabPane.removeTab(t);
    }
    processTabChange();
  }

  private void processTabChange() {
    this.menuFindText.setEnabled(!this.tabPane.isEmpty() && this.tabPane.getCurrentTitle().getProvider().doesSupportPatternSearch());

    if (this.tabPane.isEmpty() || !this.tabPane.getCurrentTitle().getProvider().doesSupportPatternSearch()) {
      hideFindTextPane();
    }
  }

  @Override
  public void showFindTextPane(@Nullable final String text) {
    final TabTitle current = getFocusedTab();
    if (current != null && this.tabPane.getCurrentTitle().getProvider().doesSupportPatternSearch()) {

      FindTextPanel panel = this.currentFindTextPanel.get();

      if (panel == null) {
        panel = new FindTextPanel(this, text);
        panel.updateUI(current);
        this.mainPanel.add(panel, BorderLayout.SOUTH);
      }

      this.currentFindTextPanel.set(panel);

      this.mainPanel.revalidate();
      this.mainPanel.repaint();

      panel.requestFocus();
    }
  }

  @Override
  public void hideFindTextPane() {
    final FindTextPanel panel = this.currentFindTextPanel.get();
    if (panel != null) {
      this.currentFindTextPanel.set(null);
      this.mainPanel.remove(panel);
      this.mainPanel.revalidate();
      this.mainPanel.repaint();
    }
  }

  @Override
  @Nullable
  public NodeProject findProjectForFile(@Nonnull final File file) {
    return this.explorerTree.getCurrentGroup().findProjectForFile(file);
  }

  @Override
  public void notifyReloadConfig() {
    for (final TabTitle t : this.tabPane) {
      final JComponent editor = t.getProvider().getMainComponent();
      if (editor instanceof MMDEditor) {
        ((MMDEditor) editor).refreshConfig();
      }
    }
  }

  @Override
  public void onCloseProject(@Nonnull final NodeProject project) {
    final File projectFolder = project.getFolder();
    for (final TabTitle t : this.tabPane) {
      if (t.belongFolderOrSame(projectFolder)) {
        t.doSafeClose();
      }
    }
  }

  @Override
  public boolean deleteTreeNode(@Nonnull final NodeFileOrFolder node) {
    final File file = node.makeFileForNode();

    if (file != null && file.exists()) {
      final List<TabTitle> tabsToClose = this.tabPane.findListOfRelatedTabs(file);
      boolean hasUnsaved = false;

      for (final TabTitle t : tabsToClose) {
        hasUnsaved |= t.isChanged();
      }

      if (hasUnsaved && !DialogProviderManager.getInstance().getDialogProvider().msgConfirmOkCancel("Confirmation", "Are you sure to delete changed unsaved file?")) {
        return false;
      }

      closeTab(tabsToClose.toArray(new TabTitle[tabsToClose.size()]));

      final NodeProject project = findProjectForFile(file);

      List<File> affectedFiles = project == null ? Collections.EMPTY_LIST : project.findAffectedFiles(file);

      final Iterator<File> iterator = affectedFiles.iterator();
      final Path removingFile = file.toPath();
      while (iterator.hasNext()) {
        if (iterator.next().toPath().startsWith(removingFile)) {
          iterator.remove();
        }
      }

      if (!affectedFiles.isEmpty()) {
        affectedFiles = UiUtils.showSelectAffectedFiles(affectedFiles);
        if (affectedFiles == null) {
          return false;
        }
      }

      boolean ok = false;
      if (file.isDirectory()) {
        if (SystemUtils.deleteFile(file, DELETE_MOVING_FILE_TO_TRASH)) {
          ok = true;
        }
        else {
          DialogProviderManager.getInstance().getDialogProvider().msgError("Can't delete directory, see the log!");
        }
      }
      else {
        ok = SystemUtils.deleteFile(file, DELETE_MOVING_FILE_TO_TRASH);
        if (!ok) {
          DialogProviderManager.getInstance().getDialogProvider().msgError("Can't delete file!");
        }
      }

      if (ok) {
        explorerTree.deleteNode(node);
      }

      if (!affectedFiles.isEmpty() && project != null) {
        final List<File> changedFiles = project.deleteAllLinksToFile(affectedFiles, file);
        if (!changedFiles.isEmpty()) {
          for (final TabTitle t : tabPane) {
            final File associated = t.getAssociatedFile();
            if (associated != null && changedFiles.contains(associated)) {
              t.reload(true);
            }
          }
        }
      }

      return ok;
    }
    return false;
  }

  /**
   * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form
   * Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    jMenuBar1 = new javax.swing.JMenuBar();
    menuFile = new javax.swing.JMenu();
    menuNewProject = new javax.swing.JMenuItem();
    jSeparator2 = new javax.swing.JPopupMenu.Separator();
    menuOpenProject = new javax.swing.JMenuItem();
    menuOpenRecentProject = new javax.swing.JMenu();
    menuOpenFile = new javax.swing.JMenuItem();
    menuOpenRecentFile = new javax.swing.JMenu();
    jSeparator3 = new javax.swing.JPopupMenu.Separator();
    menuSave = new javax.swing.JMenuItem();
    menuSaveAs = new javax.swing.JMenuItem();
    menuSaveAll = new javax.swing.JMenuItem();
    separatorExitSection = new javax.swing.JPopupMenu.Separator();
    menuExit = new javax.swing.JMenuItem();
    menuEdit = new javax.swing.JMenu();
    menuUndo = new javax.swing.JMenuItem();
    menuRedo = new javax.swing.JMenuItem();
    jSeparator1 = new javax.swing.JPopupMenu.Separator();
    menuFindText = new javax.swing.JMenuItem();
    jSeparator5 = new javax.swing.JPopupMenu.Separator();
    menuPreferences = new javax.swing.JMenuItem();
    menuView = new javax.swing.JMenu();
    menuFullScreen = new javax.swing.JMenuItem();
    menuLookAndFeel = new javax.swing.JMenu();
    menuNavigate = new javax.swing.JMenu();
    menuGoToFile = new javax.swing.JMenuItem();
    menuHelp = new javax.swing.JMenu();
    menuAbout = new javax.swing.JMenuItem();
    jSeparator4 = new javax.swing.JPopupMenu.Separator();
    menuMakeDonation = new javax.swing.JMenuItem();

    setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
    setLocationByPlatform(true);

    menuFile.setText("File");

    menuNewProject.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/box_closed.png"))); // NOI18N
    menuNewProject.setText("New Project");
    menuNewProject.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuNewProjectActionPerformed(evt);
      }
    });
    menuFile.add(menuNewProject);
    menuFile.add(jSeparator2);

    menuOpenProject.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    menuOpenProject.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/open_folder.png"))); // NOI18N
    menuOpenProject.setText("Open Project");
    menuOpenProject.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuOpenProjectActionPerformed(evt);
      }
    });
    menuFile.add(menuOpenProject);

    menuOpenRecentProject.setText("Open Recent Project");
    menuFile.add(menuOpenRecentProject);

    menuOpenFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    menuOpenFile.setText("Open File");
    menuOpenFile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuOpenFileActionPerformed(evt);
      }
    });
    menuFile.add(menuOpenFile);

    menuOpenRecentFile.setText("Open Recent File");
    menuFile.add(menuOpenRecentFile);
    menuFile.add(jSeparator3);

    menuSave.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/diskette.png"))); // NOI18N
    menuSave.setText("Save");
    menuSave.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuSaveActionPerformed(evt);
      }
    });
    menuFile.add(menuSave);

    menuSaveAs.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/file_save_as.png"))); // NOI18N
    menuSaveAs.setText("Save As");
    menuSaveAs.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuSaveAsActionPerformed(evt);
      }
    });
    menuFile.add(menuSaveAs);

    menuSaveAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/disk_multiple.png"))); // NOI18N
    menuSaveAll.setText("Save All");
    menuSaveAll.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuSaveAllActionPerformed(evt);
      }
    });
    menuFile.add(menuSaveAll);
    menuFile.add(separatorExitSection);

    menuExit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_MASK));
    menuExit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/door_in.png"))); // NOI18N
    menuExit.setText("Exit");
    menuExit.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuExitActionPerformed(evt);
      }
    });
    menuFile.add(menuExit);

    jMenuBar1.add(menuFile);

    menuEdit.setText("Edit");

    menuUndo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/undo.png"))); // NOI18N
    menuUndo.setText("Undo");
    menuUndo.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuUndoActionPerformed(evt);
      }
    });
    menuEdit.add(menuUndo);

    menuRedo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/redo.png"))); // NOI18N
    menuRedo.setText("Redo");
    menuRedo.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuRedoActionPerformed(evt);
      }
    });
    menuEdit.add(menuRedo);
    menuEdit.add(jSeparator1);

    menuFindText.setText("Find text");
    menuFindText.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuFindTextActionPerformed(evt);
      }
    });
    menuEdit.add(menuFindText);
    menuEdit.add(jSeparator5);

    menuPreferences.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/setting_tools.png"))); // NOI18N
    menuPreferences.setText("Preferences");
    menuPreferences.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuPreferencesActionPerformed(evt);
      }
    });
    menuEdit.add(menuPreferences);

    jMenuBar1.add(menuEdit);

    menuView.setText("View");

    menuFullScreen.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0));
    menuFullScreen.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/monitor.png"))); // NOI18N
    menuFullScreen.setText("Full screen");
    menuFullScreen.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuFullScreenActionPerformed(evt);
      }
    });
    menuView.add(menuFullScreen);

    menuLookAndFeel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/application.png"))); // NOI18N
    menuLookAndFeel.setText("Look and Feel");
    menuView.add(menuLookAndFeel);

    jMenuBar1.add(menuView);

    menuNavigate.setText("Navigate");

    menuGoToFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/rocket.png"))); // NOI18N
    menuGoToFile.setText("Go to File");
    menuGoToFile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuGoToFileActionPerformed(evt);
      }
    });
    menuNavigate.add(menuGoToFile);

    jMenuBar1.add(menuNavigate);

    menuHelp.setText("Help");

    menuAbout.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu_icons/information.png"))); // NOI18N
    menuAbout.setText("About");
    menuAbout.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuAboutActionPerformed(evt);
      }
    });
    menuHelp.add(menuAbout);
    menuHelp.add(jSeparator4);

    menuMakeDonation.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/coins_in_hand16.png"))); // NOI18N
    menuMakeDonation.setText("Make donation");
    menuMakeDonation.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuMakeDonationActionPerformed(evt);
      }
    });
    menuHelp.add(menuMakeDonation);

    jMenuBar1.add(menuHelp);

    setJMenuBar(jMenuBar1);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void menuAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuAboutActionPerformed
    JOptionPane.showMessageDialog(Main.getApplicationFrame(), new AboutPanel(), "About", JOptionPane.PLAIN_MESSAGE);
  }//GEN-LAST:event_menuAboutActionPerformed

  private void menuOpenProjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuOpenProjectActionPerformed
    final JFileChooser fileChooser = new JFileChooser();
    fileChooser.setFileView(new FileView() {
      private Icon KNOWLEDGE_FOLDER_ICO = null;

      @Override
      public Icon getIcon(final File f) {
        if (f.isDirectory()) {
          final File knowledge = new File(f, ".projectKnowledge");
          if (knowledge.isDirectory()) {
            if (KNOWLEDGE_FOLDER_ICO == null) {
              final Icon icon = UIManager.getIcon("FileView.directoryIcon");
              if (icon != null) {
                KNOWLEDGE_FOLDER_ICO = new ImageIcon(UiUtils.makeBadgedRightBottom(UiUtils.iconToImage(fileChooser, icon), Icons.MMDBADGE.getIcon().getImage()));
              }
            }
            return KNOWLEDGE_FOLDER_ICO;
          }
          else {
            return super.getIcon(f);
          }
        }
        else if (f.isFile() && f.getName().toLowerCase(Locale.ENGLISH).endsWith(".mmd")) {
          return Icons.DOCUMENT.getIcon();
        }
        else {
          return super.getIcon(f);
        }
      }
    });
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fileChooser.setMultiSelectionEnabled(false);
    fileChooser.setDialogTitle("Open project folder");

    if (fileChooser.showOpenDialog(Main.getApplicationFrame()) == JFileChooser.APPROVE_OPTION) {
      openProject(fileChooser.getSelectedFile(), false);
    }
  }//GEN-LAST:event_menuOpenProjectActionPerformed

  @Override
  public boolean openProject(@Nonnull final File folder, final boolean enforceSeparatedProject) {
    boolean result = false;
    if (folder.isDirectory()) {
      final NodeProject alreadyOpened = findProjectForFile(folder);
      if (alreadyOpened == null || enforceSeparatedProject) {
        final boolean firstProject = this.explorerTree.getCurrentGroup().getChildCount() == 0;
        final NodeProject node = this.explorerTree.getCurrentGroup().addProjectFolder(folder);
        if (firstProject) {
          this.explorerTree.unfoldProject(node);
        }
        try {
          FileHistoryManager.getInstance().registerOpenedProject(folder);
        }
        catch (IOException ex) {
          LOGGER.error("Can't register last opened project", ex);
        }
      }
      else {
        this.focusInTree(folder);
      }
      result = true;
    }
    else {
      LOGGER.error("Can't find folder : " + folder);
      DialogProviderManager.getInstance().getDialogProvider().msgError("Can't find project folder!");
    }
    return result;
  }

  @Override
  public void editPreferences() {
    final PreferencesPanel configPanel = new PreferencesPanel(this);
    configPanel.load();
    if (DialogProviderManager.getInstance().getDialogProvider().msgOkCancel("Preferences", configPanel)) {
      configPanel.save();
      for (final TabTitle t : this.tabPane) {
        t.getProvider().updateConfiguration();
      }
    }
  }

  private void menuSaveAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuSaveAllActionPerformed
    for (final TabTitle t : this.tabPane) {
      try {
        t.save();
      }
      catch (IOException ex) {
        LOGGER.error("Can't save file", ex);
        DialogProviderManager.getInstance().getDialogProvider().msgError("Can't save document, may be it is read-only! See log!");
      }
    }
  }//GEN-LAST:event_menuSaveAllActionPerformed

  private void menuPreferencesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuPreferencesActionPerformed
    editPreferences();
  }//GEN-LAST:event_menuPreferencesActionPerformed

  private void menuOpenFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuOpenFileActionPerformed
    final File file = DialogProviderManager.getInstance().getDialogProvider().msgOpenFileDialog("open-file", "Open file", null, true, MMDEditor.MMD_FILE_FILTER, "Open");
    if (file != null) {
      if (openFileAsTab(file)) {
        try {
          FileHistoryManager.getInstance().registerOpenedProject(file);
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              centerRootTopicIfFocusedMMD();
            }
          });
        }
        catch (IOException ex) {
          LOGGER.error("Can't register last opened file", ex);
        }
      }
    }
  }//GEN-LAST:event_menuOpenFileActionPerformed

  @Override
  public boolean centerRootTopicIfFocusedMMD() {
    boolean result = false;
    final TabTitle title = this.getFocusedTab();
    if (title != null && title.getProvider().getContentType() == EditorType.MINDMAP) {
      ((MMDEditor) title.getProvider().getMainComponent()).rootToCentre();
      result = true;
    }
    return result;
  }

  private void menuSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuSaveActionPerformed
    final int index = this.tabPane.getSelectedIndex();
    if (index >= 0) {
      try {
        ((TabTitle) this.tabPane.getTabComponentAt(index)).save();
      }
      catch (IOException ex) {
        LOGGER.error("Can't save file", ex);
        DialogProviderManager.getInstance().getDialogProvider().msgError("Can't save document, may be it is read-only! See log!");
      }
    }
  }//GEN-LAST:event_menuSaveActionPerformed

  private void menuSaveAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuSaveAsActionPerformed
    final int index = this.tabPane.getSelectedIndex();
    if (index >= 0) {
      try {
        ((TabTitle) this.tabPane.getTabComponentAt(index)).saveAs();
      }
      catch (IOException ex) {
        LOGGER.error("Can't save file", ex);
        DialogProviderManager.getInstance().getDialogProvider().msgError("Can't save document, may be it is read-only! See log!");
      }
    }
  }//GEN-LAST:event_menuSaveAsActionPerformed

  private void menuExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuExitActionPerformed
    if (doClosing()) {
      dispose();
    }
  }//GEN-LAST:event_menuExitActionPerformed

  private void menuNewProjectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuNewProjectActionPerformed
    final JFileChooser folder = new JFileChooser();
    folder.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    folder.setDialogTitle("Create project folder");
    folder.setApproveButtonText("Create");
    folder.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (folder.showOpenDialog(Main.getApplicationFrame()) == JFileChooser.APPROVE_OPTION) {
      final File file = folder.getSelectedFile();
      if (file.exists()) {
        DialogProviderManager.getInstance().getDialogProvider().msgError("File '" + file.getName() + "' already exists!");
      }
      else if (file.mkdirs()) {
        if (PreferencesManager.getInstance().getPreferences().getBoolean(PreferencesPanel.PREFERENCE_KEY_KNOWLEDGEFOLDER_ALLOWED, true)) {
          final File knowledgeFolder = new File(file, ".projectKnowledge");
          knowledgeFolder.mkdirs();
        }
        if (openProject(file, true)) {
          this.focusInTree(file);
        }
      }
      else {
        LOGGER.error("Can't create folder : " + file);
        DialogProviderManager.getInstance().getDialogProvider().msgError("Can't create folder: " + file);
      }
    }
  }//GEN-LAST:event_menuNewProjectActionPerformed

  private void menuFullScreenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFullScreenActionPerformed
    final Component currentComponent = this.tabPane.getSelectedComponent();
    if (!(currentComponent instanceof Container)) {
      LOGGER.warn("Detected attempt to full screen not a container : " + currentComponent);
      return;
    }

    final GraphicsConfiguration gconfig = this.getGraphicsConfiguration();
    if (gconfig != null) {
      final GraphicsDevice device = gconfig.getDevice();
      if (device.isFullScreenSupported()) {
        if (device.getFullScreenWindow() == null) {
          final JLabel label = new JLabel("Opened in full screen");
          final int tabIndex = this.tabPane.getSelectedIndex();
          this.tabPane.setComponentAt(tabIndex, label);
          final JWindow window = new JWindow(Main.getApplicationFrame());
          window.setAlwaysOnTop(true);
          window.setContentPane((Container) currentComponent);

          endFullScreenIfActive();

          final KeyEventDispatcher fullScreenEscCatcher = new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(@Nonnull final KeyEvent e) {
              if (e.getID() == KeyEvent.KEY_PRESSED && (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_F11)) {
                endFullScreenIfActive();
                return true;
              }
              return false;
            }
          };

          if (this.taskToEndFullScreen.compareAndSet(null, new Runnable() {
            @Override
            public void run() {
              try {
                window.dispose();
              }
              finally {
                tabPane.setComponentAt(tabIndex, currentComponent);
                device.setFullScreenWindow(null);
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(fullScreenEscCatcher);
              }
            }
          })) {
            try {
              KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(fullScreenEscCatcher);
              device.setFullScreenWindow(window);
            }
            catch (Exception ex) {
              LOGGER.error("Can't turn on full screen", ex);
              endFullScreenIfActive();
              KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(fullScreenEscCatcher);
            }
          }
          else {
            LOGGER.error("Unexpected state, processor is not null!");
          }
        }
        else {
          LOGGER.warn("Attempt to full screen device which already in full screen!");
        }
      }
      else {
        LOGGER.warn("Device doesn's support full screen");
        DialogProviderManager.getInstance().getDialogProvider().msgWarn("The Device doesn't support full-screen mode!");
      }
    }
    else {
      LOGGER.warn("Can't find graphics config for the frame");
    }
  }//GEN-LAST:event_menuFullScreenActionPerformed

  private void menuMakeDonationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuMakeDonationActionPerformed
    new DonateButton().doClick();
  }//GEN-LAST:event_menuMakeDonationActionPerformed

  private void menuGoToFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuGoToFileActionPerformed
    final GoToFilePanel panel = new GoToFilePanel(this.explorerTree);
    if (DialogProviderManager.getInstance().getDialogProvider().msgOkCancel("Go To File", panel)) {
      final NodeFileOrFolder selected = panel.getSelected();
      if (selected != null) {
        final File file = selected.makeFileForNode();
        if (file != null) {
          this.focusInTree(file);
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              explorerTree.requestFocus();
            }
          });
        }
      }
    }
  }//GEN-LAST:event_menuGoToFileActionPerformed

  private void menuUndoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuUndoActionPerformed
    final TabTitle title = this.tabPane.getCurrentTitle();
    if (title == null) {
      this.menuUndo.setEnabled(false);
      this.menuRedo.setEnabled(false);
    }
    else {
      this.menuUndo.setEnabled(title.getProvider().undo());
      this.menuRedo.setEnabled(title.getProvider().isRedo());
    }
  }//GEN-LAST:event_menuUndoActionPerformed

  private void menuRedoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuRedoActionPerformed
    final TabTitle title = this.tabPane.getCurrentTitle();
    if (title == null) {
      this.menuUndo.setEnabled(false);
      this.menuRedo.setEnabled(false);
    }
    else {
      this.menuRedo.setEnabled(title.getProvider().redo());
      this.menuUndo.setEnabled(title.getProvider().isUndo());
    }
  }//GEN-LAST:event_menuRedoActionPerformed

  private void menuFindTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFindTextActionPerformed
    showFindTextPane("");
  }//GEN-LAST:event_menuFindTextActionPerformed

  public void endFullScreenIfActive() {
    final Runnable runnable = this.taskToEndFullScreen.getAndSet(null);
    if (runnable != null) {
      Utils.safeSwingCall(runnable);
    }
  }

  @Override
  public boolean hasUnsavedDocument() {
    return this.tabPane.hasEditableAndChangedDocument();
  }

  @Override
  @Nullable
  public TabTitle getFocusedTab() {
    return this.tabPane.getCurrentTitle();
  }

  @Override
  @Nonnull
  public NodeProjectGroup getCurrentGroup() {
    return this.explorerTree.getCurrentGroup();
  }

  @Nullable
  @Override
  public File createMindMapFile(@Nullable final File folder) {
    final JFileChooser chooser = new JFileChooser(folder);
    chooser.setDialogTitle("Create new Mind Map");
    chooser.setFileFilter(MMDEditor.MMD_FILE_FILTER);
    chooser.setMultiSelectionEnabled(false);
    chooser.setApproveButtonText("Create");

    File result = null;

    if (chooser.showSaveDialog(Main.getApplicationFrame()) == JFileChooser.APPROVE_OPTION) {
      File file = chooser.getSelectedFile();
      if (!file.getName().endsWith(".mmd")) {
        file = new File(file.getAbsolutePath() + ".mmd");
      }

      if (file.exists()) {
        DialogProviderManager.getInstance().getDialogProvider().msgError("File '" + file + "' already exists!");
      }
      else {
        try {
          final MindMap mindMap = new MindMap(null, true);
          final String text = mindMap.write(new StringWriter()).toString();
          SystemUtils.saveUTFText(file, text);
          result = file;
        }
        catch (IOException ex) {
          DialogProviderManager.getInstance().getDialogProvider().msgError("Can't save mind map into file '" + file.getName() + "'");
        }
      }
    }
    return result;
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JMenuBar jMenuBar1;
  private javax.swing.JPopupMenu.Separator jSeparator1;
  private javax.swing.JPopupMenu.Separator jSeparator2;
  private javax.swing.JPopupMenu.Separator jSeparator3;
  private javax.swing.JPopupMenu.Separator jSeparator4;
  private javax.swing.JPopupMenu.Separator jSeparator5;
  private javax.swing.JMenuItem menuAbout;
  private javax.swing.JMenu menuEdit;
  private javax.swing.JMenuItem menuExit;
  private javax.swing.JMenu menuFile;
  private javax.swing.JMenuItem menuFindText;
  private javax.swing.JMenuItem menuFullScreen;
  private javax.swing.JMenuItem menuGoToFile;
  private javax.swing.JMenu menuHelp;
  private javax.swing.JMenu menuLookAndFeel;
  private javax.swing.JMenuItem menuMakeDonation;
  private javax.swing.JMenu menuNavigate;
  private javax.swing.JMenuItem menuNewProject;
  private javax.swing.JMenuItem menuOpenFile;
  private javax.swing.JMenuItem menuOpenProject;
  private javax.swing.JMenu menuOpenRecentFile;
  private javax.swing.JMenu menuOpenRecentProject;
  private javax.swing.JMenuItem menuPreferences;
  private javax.swing.JMenuItem menuRedo;
  private javax.swing.JMenuItem menuSave;
  private javax.swing.JMenuItem menuSaveAll;
  private javax.swing.JMenuItem menuSaveAs;
  private javax.swing.JMenuItem menuUndo;
  private javax.swing.JMenu menuView;
  private javax.swing.JPopupMenu.Separator separatorExitSection;
  // End of variables declaration//GEN-END:variables
}
