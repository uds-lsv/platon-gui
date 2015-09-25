package de.uds.lsv.platon.gui.asr;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.google.inject.Inject;

import de.martingropp.util.ProcessWatcher;
import de.martingropp.util.ProcessWatcher.OutputListener;

public class StdoutASR implements ASR, OutputListener {
	private ASRListener listener = null;
	private String[] command;
	private ProcessWatcher processWatcher = null;
	
	public StdoutASR(String... command) {
		this.command = command;
	}
	
	@Inject
	public StdoutASR(StdoutASRConfig config) {
		 this(config.getASRCommand());
	}
	
	@Override
	public void startRecognizer(ASRListener listener) throws IOException {
		if (this.listener != null) {
			throw new IllegalStateException("ASR is Already running!");
		}
		if (listener == null) {
			throw new IllegalArgumentException("ASR Listener cannot be null!");
		}
		
		this.listener = listener;
		
		processWatcher = new ProcessWatcher(command);
		processWatcher.addStdoutListener(this);
		processWatcher.addStderrListener(
			new OutputListener() {
				@Override
				public void processOutputLine(String line, boolean stderr) {
					System.err.println(line);
				}
			}
		);
		processWatcher.start();
	}
	
	@Override
	public void stopRecognizer() throws InterruptedException {
		if (processWatcher == null) {
			return;
		}
		
		processWatcher.closeStdinAndTerminate(5, TimeUnit.SECONDS);
	}
	
	@Override
	public void processOutputLine(String line, boolean stderr) {
		listener.speechRecognized(line);
	}
}
