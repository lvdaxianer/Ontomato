package io.ontomato.dataengine.service;

public interface PythonFormatService {

	/**
	 * Reformats python source with black. Black only rewrites layout, never semantics, and answers
	 * syntax problems with a non-zero exit, so a failed call never returns altered code.
	 */
	public String formatPythonCode(String code, String lang) throws Exception;

}
