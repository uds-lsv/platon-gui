package de.uds.lsv.platon.gui.asr;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.LiveSpeechRecognizer;
import edu.cmu.sphinx.api.SpeechResult;

@Singleton
public class SphinxASR implements ASR {
	private static final Log logger = LogFactory.getLog(SphinxASR.class.getName());
	
	public static Configuration getDefaultConfig() {
		Configuration config = new Configuration();
		config.setAcousticModelPath("resource:/edu/cmu/sphinx/models/en-us/en-us");
		config.setDictionaryPath("resource:/edu/cmu/sphinx/models/en-us/cmudict-en-us.dict");
		config.setLanguageModelPath("resource:/edu/cmu/sphinx/models/en-us/en-us.lm.dmp");
		config.setUseGrammar(false);
		return config;
		
	}
	
	private LiveSpeechRecognizer recognizer;
	
	@Inject
	public SphinxASR(Configuration config) throws IOException {
		this.recognizer = new LiveSpeechRecognizer(config);
	}
	
	@Override
	public synchronized String recognizeOnce() {
		recognizer.startRecognition(true);
		logger.debug("ASR started...");
		try {
			SpeechResult result = recognizer.getResult();
			logger.debug("ASR speech result: " + result);
			String text = result.getHypothesis();
			logger.debug("ASR text: " + text);
			return text;
		}
		finally {
			recognizer.stopRecognition();
		}
	}
	
	public static void main(String[] args) throws IOException {
		ASR asr = new SphinxASR(getDefaultConfig());
		System.err.flush();
		System.out.println("?");
		System.out.println("RECOGNIZED: " + asr.recognizeOnce());
	}
}
