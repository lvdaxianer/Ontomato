package io.ontomato.dataengine.dataAdapter;

/**
 * One connection field as declared by a {@link DataAdapterProvider}: the name
 * used on the wire and in the saved map, plus the presentation metadata the
 * settings form needs.
 *
 * <p>{@code label} is optional: when it is null the form falls back to a known
 * translation or a humanized name. {@code example} is the placeholder.</p>
 */
public record DataAdapterConnectionField(
		String name,
		String label,
		boolean required,
		boolean secret,
		String example,
		boolean fullWidth) {

	/** Names that must never be echoed by a read API or written to a log. */
	public static boolean isSecretName(String name) {
		if (name == null) {
			return false;
		}
		return name.matches("(?i).*(password|secret|token|private.?key|access.?key|api.?key).*");
	}

	/** The default descriptor used by providers that only implement connectionFields()/connectionExamples(). */
	public static DataAdapterConnectionField of(String name, String example) {
		return new DataAdapterConnectionField(name, null, false, isSecretName(name), example, false);
	}
}
