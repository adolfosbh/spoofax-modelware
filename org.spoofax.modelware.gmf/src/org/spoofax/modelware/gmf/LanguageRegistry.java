package org.spoofax.modelware.gmf;

/**
 * Registry containing all languages for which a textual and graphical editor is provided.
 * 
 * @author oskarvanrest
 */
import java.util.ArrayList;
import java.util.Collection;

public class LanguageRegistry {

	private static LanguageRegistry instance = new LanguageRegistry();

	private Collection<Language> languages = new ArrayList<Language>();
	
	private LanguageRegistry() {
	}
	
	public static LanguageRegistry getInstance() {
		return instance;
	}
	
	public void add(Language language) {
		languages.add(language);
	}
	
	public Language get(String fileExtension) {
		for (Language language : languages) {
			if (language.getTextFileExtension().equals(fileExtension) || language.getDiagramFileExtension().equals(fileExtension)) {
				return language;
			}
		}
		return null;
	}
}
