package io.ontomato.dataengine.service.ontology;

import io.ontomato.dataengine.core.exception.CoreException;

/** Provides stable, user-actionable error codes for ontology revision operations.
 *
 * @author lvdaxianer@yeah.net
 */
public class OntologyRevisionException extends CoreException {

    private static final long serialVersionUID = 1L;
    public static final String INVALID_REQUEST = "ONTOLOGY_REVISION_INVALID_REQUEST";
    public static final String NOT_FOUND = "ONTOLOGY_REVISION_NOT_FOUND";
    public static final String STALE_REVISION = "ONTOLOGY_REVISION_STALE";
    public static final String IMPORT_CONFLICT = "ONTOLOGY_IMPORT_CONFLICT";

    private final String code;

    /** Creates a stable business exception without exposing database internals. */
    public OntologyRevisionException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Returns the stable code consumed by the Manager conflict UI. */
    public String getCode() {
        return code;
    }
}
