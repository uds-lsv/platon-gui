package de.uds.lsv.platon.gui.tts;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioInputStream;

import marytts.LocalMaryInterface;
import marytts.MaryInterface;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;
import marytts.util.data.audio.AudioPlayer;

@Singleton
public class MaryTTS implements TTS {
	private final MaryInterface marytts;
	
	public MaryTTS() throws MaryConfigurationException {
		marytts = new LocalMaryInterface();
		Set<String> voices = marytts.getAvailableVoices();
		if (voices.isEmpty()) {
			throw new RuntimeException("No TTS voices found!");
		}
		marytts.setVoice(voices.iterator().next());
	}
	
	@Override
	public void say(String text) {
		try {
			AudioInputStream audio = marytts.generateAudio(text);
			AudioPlayer player = new AudioPlayer(audio);
			player.start();
			player.join();
		}
		catch (InterruptedException e) {
		}
		catch (SynthesisException e) {
			throw new RuntimeException(e);
		}
	}
}
