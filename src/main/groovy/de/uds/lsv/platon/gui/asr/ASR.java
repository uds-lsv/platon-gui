package de.uds.lsv.platon.gui.asr;

public interface ASR {
	void startRecognizer(ASRListener listener) throws Exception;
	void stopRecognizer() throws InterruptedException;
}
