package de.uds.lsv.platon.gui.config;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import de.uds.lsv.platon.gui.asr.ASR;
import de.uds.lsv.platon.gui.asr.DummyASR;
import de.uds.lsv.platon.gui.tts.MaryTTS;
import de.uds.lsv.platon.gui.tts.TTS;

public class DefaultInjectionModule extends AbstractModule {
	private static Injector injector;
	
	public static Injector getInjector() {
		if (injector == null) {
			injector = Guice.createInjector(new DefaultInjectionModule());
		}
		
		return injector;
	}
	
	@Override
	protected void configure() {
		// ASR
		bind(ASR.class).to(DummyASR.class);
		
		// TTS
		bind(TTS.class).to(MaryTTS.class);
	}
}
