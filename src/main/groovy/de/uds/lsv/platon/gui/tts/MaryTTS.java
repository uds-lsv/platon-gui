package de.uds.lsv.platon.gui.tts;

import java.util.Set;

import javax.inject.Singleton;
import javax.sound.sampled.AudioInputStream;

import marytts.LocalMaryInterface;
import marytts.MaryInterface;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;
import marytts.modules.synthesis.Voice;
import marytts.server.MaryProperties;
import marytts.util.data.audio.AudioPlayer;

@Singleton
public class MaryTTS implements TTS {
	private final MaryInterface marytts;
	
	public MaryTTS() throws MaryConfigurationException {
		marytts = new LocalMaryInterface();
		Set<String> voices = marytts.getAvailableVoices();
		if (voices.isEmpty()) {
			// Try marytts.htsengine.HMMVoice
			//marytts.htsengine.HMMVoice x;
			throw new RuntimeException("No TTS voices found!");
		}
		
		String voiceName = voices.iterator().next();
		System.out.println(
			Voice.getVoice(voiceName)
		);
		System.out.println(
			Voice.getVoice(voiceName).getClass()
		);
		
		System.out.println(MaryProperties.synthesizerClasses());
		
		MaryProperties.getList("hmm.voices.list");
		
		marytts.setVoice(voiceName);
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
	
	public static void main(String[] args) throws MaryConfigurationException {
		MaryTTS tts = new MaryTTS();
		tts.say("Test");
	}
}
