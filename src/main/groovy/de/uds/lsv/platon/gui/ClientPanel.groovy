package de.uds.lsv.platon.gui;

import groovy.transform.TypeChecked

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.prefs.Preferences

import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.plaf.basic.BasicSplitPaneUI
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

import de.martingropp.util.IdleManager
import de.uds.lsv.platon.gui.asr.ASR
import de.uds.lsv.platon.gui.asr.ASRListener
import de.uds.lsv.platon.session.User


@TypeChecked
public class ClientPanel extends JPanel implements ASRListener {
	private static final long serialVersionUID = -4879445778545380477L;
	
	public static interface TextInputListener {
		void textEntered(User user, String text);
	}
	
	public static SimpleAttributeSet defaultAttributes;
	public static SimpleAttributeSet inputAttributes;
	public static SimpleAttributeSet outputAttributes;
	static {
		defaultAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(defaultAttributes, Color.DARK_GRAY);
		
		inputAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(inputAttributes, new Color(0x004462));
		StyleConstants.setBold(inputAttributes, true);
		
		outputAttributes = new SimpleAttributeSet();
		StyleConstants.setForeground(outputAttributes, Color.BLACK);
		StyleConstants.setBold(outputAttributes, true);
	}
	
	private final ASR asr;
	private final ExecutorService asrExecutor;
	
	private final User user;
	private final String bulkInputPreferencesKey;
	private String title = "";
	
	private Preferences preferences = Preferences.userNodeForPackage(ClientPanel.class);
	private final IdleManager idleManager;
	
	private final JTextPane outputPane;
	private final JTextField inputField;
	private final JButton asrButton;
	private final Action asrAction;
	private final Action abortAsrAction;
	private Future asrFuture = null;
	
	private final List<TextInputListener> textInputListeners = new ArrayList<>();
	
	public ClientPanel(User user, ASR asr) {
		this.user = user;
		
		// ASR
		this.asr = asr;
		this.asrExecutor = Executors.newSingleThreadExecutor();
		asr.startRecognizer(this);
		
		// GUI
		bulkInputPreferencesKey = String.format(
			"bulkinputfile-%s-%s-%s",
			user.id,
			user.language,
			user.region
		);
	
		setTitle(String.format("%s (%s)", user.name, user.language));	
	
		setLayout(new BorderLayout());
		
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		panel.setLayout(new BorderLayout());
		add(panel);
		
		outputPane = new JTextPane();
		outputPane.setEditable(false);
		outputPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		
		// Bulk Input
		idleManager = new IdleManager(Executors.newSingleThreadScheduledExecutor());
		
		JPanel bulkInputPanel = new JPanel();
		bulkInputPanel.setLayout(new BorderLayout());
		bulkInputPanel.add(new JLabel("Bulk Input:"), BorderLayout.NORTH);
		
		final JTextPane bulkInputText = new JTextPane();
		bulkInputText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		bulkInputPanel.add(new JScrollPane(bulkInputText), BorderLayout.CENTER);
		
		String filename = preferences.get(
			bulkInputPreferencesKey,
			null
		);
		if (filename != null) {
			File file = new File(filename);
			if (file.exists() && file.isFile()) {
				System.err.println("Reading bulk input from " + file);
				BufferedReader reader = new BufferedReader(new FileReader(file));
				List<String> lines;
				try {
					lines = reader.readLines();
				}
				finally {
					reader.close();
				}
				
				bulkInputText.setText(lines.join("\n"));
			}
		} 
		
				
		JPanel toolbar = new JPanel();
		toolbar.add(new JButton(new AbstractAction("Open") {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
				
				FileFilter txtFilter = new FileNameExtensionFilter("Text Files", "txt");
				chooser.addChoosableFileFilter(txtFilter);
				chooser.setFileFilter(txtFilter);
				
				if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
					File file = chooser.getSelectedFile();
					preferences.put(
						bulkInputPreferencesKey,
						file.getAbsolutePath()
					);
					
					System.err.println("Reading bulk input from " + file);
					BufferedReader reader = new BufferedReader(new FileReader(file));
					List<String> lines;
					try {
						lines = reader.readLines();
					}
					finally {
						reader.close();
					}
					
					bulkInputText.setText(lines.join("\n"));
				}
			}
		}));
		toolbar.add(new JButton(new AbstractAction("Clear") {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void actionPerformed(ActionEvent e) {
				bulkInputText.setText("");
			}
		}));
		toolbar.add(new JButton(new AbstractAction("Send") {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void actionPerformed(ActionEvent e) {
				final Action action = this;
				action.setEnabled(false);
								
				List<String> lines = Arrays.asList(bulkInputText.getText().split('\n'));
				final Iterator<String> lineIterator = lines.iterator();
				
				idleManager.add(
					1500l,
					{
						if (lineIterator.hasNext()) {
							String line = lineIterator.next();
							SwingUtilities.invokeLater({
								ClientPanel.this.addText(line, inputAttributes);
							});
							for (TextInputListener listener : textInputListeners) {
								listener.textEntered(user, line);
							}
						} else {
							SwingUtilities.invokeLater({
								action.setEnabled(true);
							});
							
							idleManager.clear();
						}
					}
				);
			
				if (!idleManager.isActive()) {
					idleManager.start();
				}
			}
		}));
		bulkInputPanel.add(toolbar, BorderLayout.SOUTH);
		
		// Input
		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new BorderLayout());
		inputField = new JTextField();
		
		inputPanel.add(new JLabel("Input:"), BorderLayout.NORTH);
		inputPanel.add(inputField, BorderLayout.CENTER);
		
		Action sendAction = new AbstractAction("Send") {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void actionPerformed(ActionEvent e) {
				String text = inputField.getText().trim();
				
				ClientPanel.this.addText(text, inputAttributes);
				
				for (TextInputListener listener : textInputListeners) {
					listener.textEntered(user, text);
				}
				
				inputField.setText("");
			}
		};
		
		inputField.setAction(sendAction);
		inputPanel.add(new JButton(sendAction), BorderLayout.EAST);
		
		panel.add(inputPanel, BorderLayout.SOUTH);
		
		JSplitPane splitPane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			new JScrollPane(outputPane),
			bulkInputPanel
		);
		splitPane.setDividerLocation(300);
		splitPane.setUI(new BasicSplitPaneUI() {
			@Override
			public void paint(Graphics g, JComponent jc) { }
		});
		
		panel.add(splitPane, BorderLayout.CENTER);
		panel.add(inputPanel, BorderLayout.SOUTH);
	}
	
	@Override
	public void speechRecognized(String text) {
		addText(text, inputAttributes);
		
		for (TextInputListener listener : textInputListeners) {
			listener.textEntered(user, text);
		}
	}
	
	private void appendLine(JTextPane textPane, String text, SimpleAttributeSet attributes) {
		StyledDocument document = textPane.getStyledDocument();
		try {
			document.insertString(document.getLength(), text + '\n', attributes);
		}
		catch (BadLocationException e) {
			throw new RuntimeException(e);
		}
		
		// Stupid Windows... :/
		String s = textPane.getText();
		if (s.endsWith("\r\n")) {
			s = s.substring(0, text.length()-1);
		}
		textPane.setCaretPosition(s.length());
	}
	
	public void addText(String text, SimpleAttributeSet attributes) {
		appendLine(outputPane, text, attributes);
	}
	
	public void addText(String text) {
		appendLine(outputPane, text, defaultAttributes);
	}
	
	public void addTextInputListener(TextInputListener listener) {
		this.textInputListeners.add(listener);
	}
	
	public void say(String text) {
		idleManager.ping();
		addText(text, outputAttributes);
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
}
