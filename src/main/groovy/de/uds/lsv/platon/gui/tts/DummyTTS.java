package de.uds.lsv.platon.gui.tts;

public class DummyTTS implements TTS {
	@Override
	public void say(String text) {
		System.out.println("TTS: " + text);
	}
}
