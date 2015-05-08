package de.uds.lsv.platon.gui.tts;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

import marytts.config.VoiceConfig;
import marytts.exceptions.MaryConfigurationException;
import marytts.modules.phonemiser.AllophoneSet;
import marytts.util.MaryRuntimeUtils;
import marytts.util.MaryUtils;

public class Config extends VoiceConfig {
	private Set<Locale> locales = new HashSet<>();
	
	private static InputStream openProperties() {
		System.err.println("Opening properties stream: /tts/marytts.config");
		InputStream stream = Config.class.getResourceAsStream("/tts/marytts.config");
		if (stream == null) {
			throw new RuntimeException("Could not open properties stream!");
		}
		return stream;
	}
	
	public Config() throws MaryConfigurationException {
		super(openProperties());
		
		String localeProp = getProperties().getProperty("locale");
		if (localeProp == null) {
			throw new MaryConfigurationException("property stream does not contain a locale property");
		}
		for (StringTokenizer st = new StringTokenizer(localeProp); st.hasMoreTokens();) {
			String localeString = st.nextToken();
			locales.add(MaryUtils.string2locale(localeString));
		}
		if (locales.isEmpty()) {
			throw new MaryConfigurationException("property stream does not define any locale");
		}
	}
	
	@Override
	public boolean isMainConfig() {
		return true;
	}
	
	@Override
	public boolean isLanguageConfig() {
		return true;
	}
	
	@Override
	public boolean isVoiceConfig() {
		return true;
	}

	public Set<Locale> getLocales() {
		return locales;
	}

	protected AllophoneSet getAllophoneSetFor(Locale locale) throws MaryConfigurationException {
		return MaryRuntimeUtils.needAllophoneSet(locale.toString() + ".allophoneset");
	}
}
