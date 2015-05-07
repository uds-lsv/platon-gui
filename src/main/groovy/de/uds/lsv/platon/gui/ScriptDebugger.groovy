package de.uds.lsv.platon.gui

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.awt.AWTKeyStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.prefs.Preferences

import javax.inject.Inject
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.InputMap
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileFilter
import javax.swing.table.DefaultTableModel
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.DialogWorld
import de.uds.lsv.platon.Platon
import de.uds.lsv.platon.action.IOType
import de.uds.lsv.platon.gui.ClientPanel.TextInputListener
import de.uds.lsv.platon.gui.asr.ASR
import de.uds.lsv.platon.gui.config.InjectionModule
import de.uds.lsv.platon.gui.tts.TTS
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User
import de.uds.lsv.sonarsilence.config.Config
import de.uds.lsv.util.PrimitiveUtil

@TypeChecked
public class ScriptDebugger extends JFrame implements DialogClient, TextInputListener {
	static SimpleAttributeSet stdoutAttributes;
	static SimpleAttributeSet stderrAttributes;
	static SimpleAttributeSet outputAttributes;
	static SimpleAttributeSet inputAttributes;
	static SimpleAttributeSet separatorAttributes;
	static {
		stdoutAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(stdoutAttributes, Color.DARK_GRAY);
		
		stderrAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(stderrAttributes, Color.RED);
		
		outputAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(outputAttributes, Color.BLACK);
		StyleConstants.setBold(outputAttributes, true);
		
		inputAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(inputAttributes, new Color(0x21A1B8));
		StyleConstants.setBold(inputAttributes, true);
		
		separatorAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(separatorAttributes, new Color(0xDB800E));
		StyleConstants.setBold(separatorAttributes, true);
	}
	
	private static FileFilter dialogScriptFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			return file.isDirectory() || file.getName().endsWith(".groovy");
		}
		
		@Override
		public String getDescription() {
			return "Dialog Script (*.groovy)";
		}
	};
	
	private static FileFilter dialogWorldFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			return file.canExecute();
		}
		
		@Override
		public String getDescription() {
			return "Executable Files";
		}
	};
	
	private static int CONFIG_TAB = 0;
	private static int WORLD_SERVER_TAB = 1;
	private static int FIRST_CLIENT_TAB = 2;
	
	private static GridBagConstraints centerConstraints = new GridBagConstraints();
	static {
		centerConstraints.gridx = 0;
		centerConstraints.fill = GridBagConstraints.HORIZONTAL;
		centerConstraints.weightx = 1.0;
		centerConstraints.weighty = 0.0;
		centerConstraints.gridwidth = 3;
	}
	
	private static GridBagConstraints labelConstraints = new GridBagConstraints();
	static {
		labelConstraints.gridx = 0;
		labelConstraints.fill = GridBagConstraints.HORIZONTAL;
		labelConstraints.weightx = 0.2;
		labelConstraints.weighty = 0.0;
		labelConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
	}
	
	private static GridBagConstraints inputConstraints = new GridBagConstraints();
	static {
		inputConstraints.gridx = 1;
		inputConstraints.fill = GridBagConstraints.HORIZONTAL;
		inputConstraints.weightx = 1.0;
		inputConstraints.weighty = 0.0;
		inputConstraints.anchor = GridBagConstraints.PAGE_START;
	}
	
	private static GridBagConstraints bigInputConstraints = new GridBagConstraints();
	static {
		bigInputConstraints.gridx = 1;
		bigInputConstraints.fill = GridBagConstraints.BOTH;
		bigInputConstraints.weightx = 1.0;
		bigInputConstraints.weighty = 0.3;
		bigInputConstraints.anchor = GridBagConstraints.CENTER;
	}
	
	private static GridBagConstraints buttonConstraints = new GridBagConstraints();
	static {
		buttonConstraints.gridx = 2;
		buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
		buttonConstraints.weightx = 0.0;
		buttonConstraints.weighty = 0.0;
		buttonConstraints.anchor = GridBagConstraints.PAGE_START;
	}
	
	private static GridBagConstraints verticalFillerConstraints = new GridBagConstraints();
	static {
		verticalFillerConstraints.gridx = 0;
		verticalFillerConstraints.fill = GridBagConstraints.BOTH;
		verticalFillerConstraints.weightx = 1.0;
		verticalFillerConstraints.weighty = 0.7;
		verticalFillerConstraints.gridwidth = 3;
		verticalFillerConstraints.anchor = GridBagConstraints.CENTER;
	}
	
	private final ASR asr;
	private final TTS tts;
	
	private JTabbedPane tabbedPane;
	private JTextPane dialogWorldText;
	private JLabel restartInfoLabel;
	
	private JTextField dialogScriptInput;
	private JTextField dialogWorldInput;
	private JTable languageTable;
	private List<String> languageList = new ArrayList<>();
	
	private Map<Integer,ClientPanel> clientPanels = new HashMap<>();
	
	private DialogSession dialogSession = null;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private AtomicInteger nextOutputId = new AtomicInteger(1);
	private Map<Integer,Future> outputFutures = new HashMap<>();
	
	private DefaultTableModel languageTableModel = new DefaultTableModel() {
		@Override
		public int getRowCount() {
			return languageList.size();
		}

		@Override
		public int getColumnCount() {
			return 1;
		}
		
		@Override
		public String getColumnName(int column) {
			return "Language";
		}	
		
		@Override
		public Object getValueAt(int row, int column) {
			return languageList.get(row);
		}
		
		@Override
		public void setValueAt(Object value, int row, int column) {
			languageList.set(row, value.toString());
			preferences.put("languages", languageList.join(" "));
			if (dialogEngineServerThread != null) {
				restartInfoLabel.setText("Restart to apply the new settings!");
			}
		}
		
		@Override
		public boolean isCellEditable(int row, int column) {
			return true;
		}
	};

	private JButton startButton;
	
	private Config dialogEngineConfig;
	
	private Thread dialogEngineServerThread;
	private DialogWorld dialogWorld;
	
	private Preferences preferences = Preferences.userNodeForPackage(ScriptDebugger.class);
	
	private Action startAction = new AbstractAction("Start") {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			start(); 
		}
	};

	private Action addLanguageAction = new AbstractAction("+") {
		@Override
		public void actionPerformed(ActionEvent e) {
			String language = JOptionPane.showInputDialog("Language (en-us, de-de, fr-ch, …):");
			if (language == null) {
				return;
			}
			if (language.indexOf(' ') >= 0) {
				throw new RuntimeException("Language cannot contain spaces.");
			}
			languageList.add(language);
			languageTableModel.fireTableDataChanged();
			
			preferences.put("languages", languageList.join(" "));
			
			if (dialogEngineServerThread != null) {
				restartInfoLabel.setText("Restart to apply the new settings!");
			}
		}
	};

	private Action removeLanguageAction = new AbstractAction("‒") {
		@Override
		public void actionPerformed(ActionEvent e) {
			languageList.remove(languageTable.getSelectedRow());
			languageTableModel.fireTableDataChanged();
			preferences.put("languages", languageList.join(" "));
			if (dialogEngineServerThread != null) {
				restartInfoLabel.setText("Restart to apply the new settings!");
			}
		}
	}; 

	private JTextField createConfigTextField(String name, JComponent parent, String labelText) {
		JLabel label = new JLabel(labelText);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
		parent.add(label, labelConstraints);
		
		JTextField textField = new JTextField();
		textField.setText(preferences.get(name, ""));
		
		textField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent event) {
				preferences.put(name, textField.getText());
				if (dialogEngineServerThread != null) {
					restartInfoLabel.setText("Restart to apply the new settings!");
				}
			}

			@Override
			public void insertUpdate(DocumentEvent event) {
				preferences.put(name, textField.getText());
				if (dialogEngineServerThread != null) {
					restartInfoLabel.setText("Restart to apply the new settings!");
				}
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				preferences.put(name, textField.getText());
				if (dialogEngineServerThread != null) {
					restartInfoLabel.setText("Restart to apply the new settings!");
				}
			}
		});
	
		parent.add(textField, inputConstraints);
	
		return textField;
	}

	private JTextField addFileConfigItem(String name, JComponent parent, String labelText, FileFilter fileFilter, Closure changedClosure=null) {
		JTextField textField = createConfigTextField(name, parent, labelText);
		
		Action browseAction = new AbstractAction("…") {
			public void actionPerformed(ActionEvent event) {
				String filename = textField.getText().trim();
				JFileChooser chooser = null;
				if (filename.length() > 0) {
					File file = new File(filename);
					if (file.exists()) {
						chooser = new JFileChooser(file.getParentFile());
					}
				}
				if (chooser == null) {
					chooser = new JFileChooser();
				}
				
				chooser.setFileFilter(fileFilter);
				
				if (chooser.showOpenDialog(ScriptDebugger.this) == JFileChooser.APPROVE_OPTION) {
					String f = chooser.getSelectedFile().getAbsolutePath();
					
					textField.setText(f);
					
					if (changedClosure != null) {
						changedClosure(f);
					}
				}
			}
		};
		JButton browseButton = new JButton(browseAction);
		parent.add(browseButton, buttonConstraints);
		
		return textField;
	}
	
	private void addUserConfig(JComponent parent) {
		JLabel label = new JLabel("Users:");
		parent.add(label, labelConstraints);
		
		languageList.clear();
		
		for (String language : preferences.get("languages", "").split(" ")) {
			if (!language.isEmpty()) {
				languageList.add(language);
			}
		}
		
		languageTable = new JTable(languageTableModel);
		languageTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		languageTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		
		JPopupMenu popupMenu = new JPopupMenu();
		final int[] rowToEdit = [ 0 ];
		popupMenu.add(new AbstractAction("Edit") {
			@Override
			public void actionPerformed(ActionEvent e) {
				languageTable.editCellAt(rowToEdit[0], 0);
				languageTable.transferFocus();
			}
		});
		popupMenu.add(addLanguageAction);
		popupMenu.add(removeLanguageAction);
		
		languageTable.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					rowToEdit[0] = languageTable.rowAtPoint(e.getPoint());
					languageTable.setRowSelectionInterval(rowToEdit[0], rowToEdit[0]);
					popupMenu.show(languageTable, e.getX(), e.getY());
				}
			}
			
			@Override
			public void mouseEntered(MouseEvent e) { }
			
			@Override
			public void mouseReleased(MouseEvent e) { }
			
			@Override
			public void mousePressed(MouseEvent e) { }
			
			@Override
			public void mouseExited(MouseEvent e) { }
		});
		
		JScrollPane scrollPane = new JScrollPane(languageTable);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		parent.add(scrollPane, bigInputConstraints);
		
		JPanel toolbar = new JPanel();
		toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
		JButton button = new JButton(addLanguageAction);
		button.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		toolbar.add(button);
		
		button = new JButton(removeLanguageAction);
		button.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		toolbar.add(button);
		
		parent.add(toolbar, buttonConstraints);
	}
	
	@Inject
	public ScriptDebugger(ASR asr, TTS tts) {
		setTitle("Dialog Script Debugger");
		setSize(1024, 600);
		
		this.asr = asr;
		this.tts = tts;
		
		// Tabbed Pane
		tabbedPane = new JTabbedPane();
		
		KeyStroke ctrlTab = KeyStroke.getKeyStroke("ctrl TAB");
		KeyStroke ctrlShiftTab = KeyStroke.getKeyStroke("ctrl shift TAB");

		Set<AWTKeyStroke> forwardKeys = new HashSet<AWTKeyStroke>(tabbedPane.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
		forwardKeys.remove(ctrlTab);
		tabbedPane.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forwardKeys);

		Set<AWTKeyStroke> backwardKeys = new HashSet<AWTKeyStroke>(tabbedPane.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
		backwardKeys.remove(ctrlShiftTab);
		tabbedPane.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backwardKeys);

		InputMap inputMap = tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		inputMap.put(ctrlTab, "navigateNext");
		inputMap.put(ctrlShiftTab, "navigatePrevious");
		
		add(tabbedPane);
		
		dialogEngineConfig = new Config();
		dialogEngineConfig.wrapExceptions = false;
		dialogEngineConfig.serverExceptionHandler = {
			Exception e ->
			e.printStackTrace();
			System.err.println();
			System.err.println("=== EXCEPTION SUMMARY ===");
			System.err.println(e);
			JOptionPane.showMessageDialog(
				null,
				e.getMessage(),
				e.getClass().getSimpleName(),
				JOptionPane.ERROR_MESSAGE
			);
			System.exit(3);
		};
		String dialogScript = preferences.get("dialogscript", null);
		if (dialogScript != null) {
			dialogEngineConfig.dialogScript = new File(dialogScript).toURI().toURL();
		}
		
		// Configuration
		JPanel configPanel = new JPanel();
		configPanel.setLayout(new GridBagLayout());
		configPanel.setBorder(BorderFactory.createEmptyBorder(0, 32, 0, 32));
		
		configPanel.add(new JPanel(), verticalFillerConstraints);
		
		JLabel titleLabel = new JLabel("Dialog Script Debugger");
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setFont(titleLabel.getFont().deriveFont(20.0f));
		titleLabel.setBorder(BorderFactory.createEmptyBorder(16, 0, 32, 0));
		configPanel.add(titleLabel, centerConstraints);
		
		dialogScriptInput = addFileConfigItem(
			"dialogscript",
			configPanel,
			"Dialog script:",
			dialogScriptFilter,
			{
				String filename ->
				dialogEngineConfig.dialogScript = new File(filename).toURI().toURL();
			}
		);
		dialogWorldInput = addFileConfigItem("worldserver", configPanel, "<html>World server class:<br><small>(leave empty to use no server)</small></html>", dialogWorldFilter);
		
		addUserConfig(configPanel);
		
		restartInfoLabel = new JLabel();
		restartInfoLabel.setForeground(Color.RED);
		restartInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		restartInfoLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		configPanel.add(restartInfoLabel, centerConstraints);
		
		JPanel startPanel = new JPanel();
		startPanel.add(new JButton(startAction));
		configPanel.add(startPanel, centerConstraints);
		
		configPanel.add(new JPanel(), verticalFillerConstraints);
		
		tabbedPane.addTab("Configuration", configPanel);
		
		// World Server
		JPanel dialogWorldPanel = new JPanel();
		dialogWorldPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		dialogWorldPanel.setLayout(new BorderLayout());
		
		JPanel toolbar = new JPanel();
		toolbar.add(new JButton(new AbstractAction("Terminate") {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if (dialogWorld == null) {
					JOptionPane.showMessageDialog(
						null,
						"Process is not active.",
						"Not active!",
						JOptionPane.ERROR_MESSAGE
					);
					return;
				}
				//dialogWorld.terminate();
				JOptionPane.showMessageDialog(null, "Process terminated.");
			}
		}));
		
		dialogWorldPanel.add(toolbar, BorderLayout.NORTH);
		
		dialogWorldText = new JTextPane();
		dialogWorldText.setEditable(false);
		dialogWorldText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		
		dialogWorldPanel.add(new JScrollPane(dialogWorldText), BorderLayout.CENTER);
		tabbedPane.addTab("World Server", dialogWorldPanel);
	}
	
	public void addClientPanel(int userId, ClientPanel panel) {
		clientPanels.put(userId, panel);
		
		tabbedPane.addTab(
			panel.getTitle(),
			panel
		);
	
		if (tabbedPane.getTabCount() == FIRST_CLIENT_TAB + 1) {
			tabbedPane.setSelectedIndex(FIRST_CLIENT_TAB);
		}
	}
	
	private URL getDialogScriptUrl() {
		String s = dialogScriptInput.getText().trim();
		
		if (s.isEmpty()) {
			return null;
		}
		
		try {
			return new URL(s);
		}
		catch (java.net.MalformedURLException e) {
			return new File(s).toURI().toURL();
		}
	}
	
	private def getDialogWorldClassAndArguments() {
		String s = dialogWorldInput.getText().trim();
		
		Class<? extends DialogWorld> worldClass;
		List<?> arguments;
		
		if (s.contains("(")) {
			String[] tokens = s.split("[()]+");
			if (tokens.length != 2) {
				throw new IllegalArgumentException("Could not parse world class: " + s);
			}
			
			worldClass = (Class)Class.forName(tokens[0].trim());
			arguments = PrimitiveUtil.parseLiterals(tokens[1]);
			
		} else {
			worldClass = (Class)Class.forName(s);
			arguments = null;
		}
		
		return [ worldClass, arguments ];
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public void start() {
		startAction.setEnabled(false);
		
		if (SwingUtilities.isEventDispatchThread()) {
			restartInfoLabel.setText("");
		} else {
			SwingUtilities.invokeLater({
				restartInfoLabel.setText("");
				File dialogScriptFile = new File(dialogScriptInput.getText().trim());
				setTitle("Dialog Script Debugger: " + dialogScriptFile.getName());
			} as Runnable);
		}
		
		try {
			URL dialogScriptUrl = getDialogScriptUrl();
			Class<? extends DialogWorld> dialogWorldClass;
			List<?> dialogWorldArguments;
			( dialogWorldClass, dialogWorldArguments ) = getDialogWorldClassAndArguments();
			
			if (dialogScriptUrl == null) {
				tabbedPane.setSelectedIndex(CONFIG_TAB)
				return;
			}
			
			List<User> users = (0..(languageList.size()-1)).collect {
				int i ->
				String l = languageList.get(i);
				String language;
				String region;
				if (l.contains("-")) {
					language = l.substring(0, l.indexOf('-'));
					region = l.substring(l.indexOf("-") + 1);
				} else {
					language = l;
					region = null;
				}
				return new User(i, "User ${i}", language, region);
			};
		
			/*** World Server ***/
			if (dialogWorldClass != null) {
				dialogWorld = dialogWorldClass.newInstance();
			}
		
			/*** Dialog Engine ***/
			dialogSession = Platon.createSession(
				dialogScriptUrl,
				this,
				dialogWorld,
				users
			);
			
			/*** Client Tabs ***/
			for (int i = tabbedPane.getTabCount()-1; i >= FIRST_CLIENT_TAB; i--) {
				tabbedPane.remove(i);
			}
			
			for (User user : users) {
				ClientPanel clientPanel = new ClientPanel(user, asr);
				clientPanel.addTextInputListener(this);
				addClientPanel(user.id, clientPanel);
			}
			
			/*** ACTUALLY START ***/		
			/*if (!dialogEngineServerThread.isAlive()) {
				dialogEngineServerThread.start();
			}*/
			
			int tab = WORLD_SERVER_TAB;
			if (dialogWorldClass == null) {
				tab = FIRST_CLIENT_TAB;
			}
			if (SwingUtilities.isEventDispatchThread()) {
				tabbedPane.setSelectedIndex(tab);
			} else {
				SwingUtilities.invokeAndWait({
					tabbedPane.setSelectedIndex(tab)
				} as Runnable);
			}
			
			//dialogWorld.start();
			
			startAction.putValue(Action.NAME, "Restart");
			
			dialogSession.setActive(true);
		}
		finally {
			startAction.setEnabled(true);
		}
	}
	
	private void appendLine(JTextPane textPane, String text, SimpleAttributeSet attributes) {
		StyledDocument document = textPane.getStyledDocument();
		document.insertString(document.getLength(), text + '\n', attributes);
		
		textPane.setCaretPosition(textPane.getText().length());
	}
	
	public void addDialogWorldText(String text, SimpleAttributeSet attributes) {
		appendLine(dialogWorldText, text, attributes);
	}
	
	private void loadBulkInputText(File file, JTextPane textPane) {
		preferences.put("bulkinputfile", file.getAbsolutePath());
		
		System.err.println("Reading bulk input from " + file);
		BufferedReader reader = new BufferedReader(new FileReader(file));
		List<String> lines;
		try {
			lines = reader.readLines();
		}
		finally {
			reader.close();
		}
		
		textPane.setText(lines.join("\n"));
	}
	
	@Override
	public void close() throws IOException {
		executor.shutdownNow();
	}

	@Override
	public int outputStart(User user, IOType ioType, String text, Map<String, Object> details) {
		final int outputId = nextOutputId.getAndIncrement();

		if (user == null) {
			for (ClientPanel clientPanel : clientPanels.values()) {
				clientPanel.say(text);
			}
		} else {
			ClientPanel clientPanel = clientPanels.get(user.id);
			if (clientPanel == null) {
				throw new IllegalArgumentException("Unknown user: " + user);
			}
			clientPanel.say(text);
		}
		
		Future future = executor.submit({
			tts.say(text);
			synchronized (outputFutures) {
				if (outputFutures.containsKey(outputId)) {
					dialogSession.outputEnded(outputId, user, 1.0);
					outputFutures.remove(outputId);
				}
			}
		});
	
		synchronized (outputFutures) {
			outputFutures.put(outputId, future);
		}
		
		return outputId;
	}

	@Override
	public void outputAbort(int outputId, User user) {
		boolean aborted = false;
		
		synchronized (outputFutures) {
			if (outputFutures.containsKey(outputId)) {
				Future future = outputFutures.remove(outputId);
				future.cancel(true);
				aborted = true;
			}
		}
		
		if (aborted) {
			//clientPanel.say("(Output aborted.)");
			dialogSession.outputEnded(outputId, user, 0.0);
		}
	}
	
	public void setDialogWorldClass(String dialogWorldClass) {
		dialogWorldInput.setText(dialogWorldClass);
	}
	
	public void setUsers(List<String> users) {
		users.clear();
		users.addAll(users);
	}
	
	public void setScript(String script) {
		/*if (script.contains(":/")) {
			script = 
		}*/
		
		dialogScriptInput.setText(script);
	}
	
	public static void run(String script, List<String> users, String dialogWorldClass, boolean startNow) {
		ScriptDebugger scriptDebugger = InjectionModule.getInjector().getInstance(ScriptDebugger.class);
		scriptDebugger.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		scriptDebugger.setLocationRelativeTo(null);
		
		if (dialogWorldClass != null && !script.isAllWhitespace()) {
			scriptDebugger.setDialogWorldClass(dialogWorldClass);
		}
		if (users != null && !users.isEmpty()) {
			scriptDebugger.setUsers(users);
		}
		if (script != null && !script.isAllWhitespace()) {
			scriptDebugger.setScript(script);
		}
		
		scriptDebugger.setVisible(true);
		
		if (startNow) {
			scriptDebugger.start();
		}
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public static void main(String[] args) {
		try {
			for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		}
		catch (Exception e) {
		}
		
		CliBuilder cli = new CliBuilder(usage: 'ScriptDebugger [wl] <script>')
		cli.h(longOpt: 'help', 'display usage information');
		cli.w(longOpt: 'world', args: 1, 'set world server class');
		cli.u(longOpt: 'user', args: 1, 'add a user with this language-region (e.g. en-us)');
		cli._(longOpt: 'script', 'set dialog script');
		
		def options = cli.parse(args);
		if (!options) {
			return;
		}
		if (options.help) {
			cli.usage();
		}
		
		try {
			run(
				options.script ?: null,
				options.users ?: null,
				options.world ?: null,
				!!options.script
			);
		}
		catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(
				null,
				e.getMessage(),
				e.getClass().getSimpleName(),
				JOptionPane.ERROR_MESSAGE
			);
			System.err.println();
			System.err.println("=== EXCEPTION SUMMARY ===");
			System.err.println(e);
			System.exit(1);
		}
	}

	@Override
	public void textEntered(User user, String text) {
		dialogSession.inputStarted(user, IOType.TEXT);
		dialogSession.inputComplete(user, IOType.TEXT, text, null);
	}
}
