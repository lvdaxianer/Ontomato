package io.ontomato.dataengine.service.ontology;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter.Feature;

/** Creates stable content snapshots and hashes for ontology revisions.
 *
 * @author lvdaxianer@yeah.net
 */
public final class OntologyRevisionCanonicalizer {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String ROOT_PATH = "$";
    private static final int HEX_DIGITS_PER_BYTE = 2;

    private OntologyRevisionCanonicalizer() {
    }

    /** Serializes an ontology rule with sorted object keys and semantic definition arrays. */
    public static String canonicalize(Map<String, Object> ontologyRule) {
        if (ontologyRule == null) {
            throw new IllegalArgumentException("ontologyRule is required");
        }
        return JSON.toJSONString(normalizeValue(ontologyRule, ROOT_PATH), Feature.WriteMapNullValue);
    }

    /** Returns the SHA-256 hash of a canonical ontology rule snapshot. */
    public static String sha256(Map<String, Object> ontologyRule) {
        byte[] content = canonicalize(ontologyRule).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(content);
            StringBuilder hash = new StringBuilder(digest.length * HEX_DIGITS_PER_BYTE);
            for (byte value : digest) {
                hash.append(String.format("%02x", value));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Recursively sorts object keys and the ontology arrays whose order is not semantic. */
    private static Object normalizeValue(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = Objects.toString(entry.getKey(), "null");
                normalized.put(key, normalizeValue(entry.getValue(), path + "." + key));
            }
            return new LinkedHashMap<>(normalized);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                normalized.add(normalizeValue(item, path));
            }
            if (isSemanticArray(path)) {
                normalized.sort(Comparator.comparing(item -> semanticKey(item, path)));
            }
            return normalized;
        }
        return value;
    }

    /** Identifies ontology arrays whose members have stable business keys. */
    private static boolean isSemanticArray(String path) {
        return path.endsWith(".classlist")
                || path.endsWith(".classDef")
                || path.endsWith(".attrs")
                || path.endsWith(".indicators");
    }

    /** Extracts a deterministic key from a semantic ontology definition. */
    private static String semanticKey(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            String key = path.endsWith(".classDef") ? "className"
                    : path.endsWith(".attrs") || path.endsWith(".indicators") ? "name" : "";
            return Objects.toString(map.get(key), "");
        }
        return Objects.toString(value, "");
    }
}
