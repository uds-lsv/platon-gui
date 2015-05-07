package de.uds.lsv.platon.gui.config;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;

import de.uds.lsv.platon.gui.asr.ASR;
import de.uds.lsv.platon.gui.asr.SphinxASR;
import de.uds.lsv.platon.gui.tts.MaryTTS;
import de.uds.lsv.platon.gui.tts.TTS;
import edu.cmu.sphinx.api.Configuration;

public class InjectionModule extends AbstractModule {
	private static Injector injector;
	
	public static Injector getInjector() {
		if (injector == null) {
			injector = Guice.createInjector(new InjectionModule());
		}
		
		return injector;
	}
	
	@Override 
	protected void configure() {
		// ASR
		bind(ASR.class).to(SphinxASR.class);
		bind(Configuration.class).toProvider(new Provider<Configuration>() {
			@Override
			public Configuration get() {
				return SphinxASR.getDefaultConfig();
			}
		});
		
		// TTS
		bind(TTS.class).to(MaryTTS.class);
	}
}
