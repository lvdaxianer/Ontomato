package io.ontomato.dataengine.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import io.ontomato.dataengine.core.bean.User;
import io.ontomato.dataengine.service.ontology.OntologyRevisionException;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService.ImportPreview;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService.ImportRequest;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService.RestoreRequest;
import io.ontomato.dataengine.service.ontology.OntologyRevisionService.RevisionSaveRequest;
import io.ontomato.dataengine.service.ontology.OntologyRevisionSnapshot;
import io.ontomato.dataengine.util.SystemUtils;
import lombok.extern.slf4j.Slf4j;

/** Exposes domain-scoped revision history, export, import preview, and restore APIs.
 *
 * @author lvdaxianer@yeah.net
 */
@Slf4j
@RestController
public class OntologyRevisionController {

    private static final String INTERNAL_ERROR = "ONTOLOGY_REVISION_INTERNAL";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_OFFSET = 0;

    @Autowired
    private OntologyRevisionService revisionService;

    /** Lists revision metadata and the active revision for the authenticated domain. */
    @GetMapping("/admin/ontology/revisions")
    @ResponseBody
    public JSONObject list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            User user = SystemUtils.getCurUser();
            List<OntologyRevisionSnapshot> revisions = revisionService.list(user.getDomainId(), limit, offset);
            JSONObject data = new JSONObject();
            data.put("items", snapshots(revisions));
            data.put("activeRevision", revisionService.current(user.getDomainId()).revision());
            return success(data);
        } catch (Exception exception) {
            return failure("list", exception);
        }
    }

    /** Exports one revision as a portable, data-free ontology definition document. */
    @GetMapping("/admin/ontology/revisions/{revision}")
    @ResponseBody
    public JSONObject export(@PathVariable long revision) {
        try {
            User user = SystemUtils.getCurUser();
            OntologyRevisionSnapshot snapshot = revisionService.find(user.getDomainId(), revision);
            JSONObject data = snapshotData(snapshot);
            data.put("formatVersion", 1);
            return success(data);
        } catch (Exception exception) {
            return failure("export", exception);
        }
    }

    /** Previews an import and returns semantic conflicts without changing stored ontology state. */
    @PostMapping("/admin/ontology/revisions/preview")
    @ResponseBody
    public JSONObject previewImport(@RequestBody JSONObject payload) {
        try {
            User user = SystemUtils.getCurUser();
            ImportPreview preview = revisionService.preview(importRequest(payload, user), user.getDomainId());
            JSONObject data = new JSONObject();
            data.put("currentRevision", preview.current().revision());
            data.put("currentHash", preview.current().contentHash());
            data.put("baseRevision", preview.base().revision());
            data.put("baseHash", preview.base().contentHash());
            data.put("incomingHash", preview.incomingHash());
            data.put("merged", preview.mergedRule());
            data.put("conflicts", preview.conflicts());
            data.put("canApply", preview.canApply());
            return success(data);
        } catch (Exception exception) {
            return failure("preview", exception);
        }
    }

    /** Applies a conflict-free or explicitly resolved import as a new revision. */
    @PostMapping("/admin/ontology/revisions/apply")
    @ResponseBody
    public JSONObject applyImport(@RequestBody JSONObject payload) {
        try {
            User user = SystemUtils.getCurUser();
            OntologyRevisionSnapshot snapshot = revisionService.applyImport(
                    importRequest(payload, user), user.getDomainId());
            return success(snapshotData(snapshot));
        } catch (Exception exception) {
            return failure("apply", exception);
        }
    }

    /** Restores a historical revision by creating a new active revision. */
    @PostMapping("/admin/ontology/revisions/restore")
    @ResponseBody
    public JSONObject restore(@RequestBody JSONObject payload) {
        try {
            User user = SystemUtils.getCurUser();
            Long expectedRevision = payload.getLong("expectedCurrentRevision");
            String expectedHash = payload.getString("expectedCurrentHash");
            RestoreRequest request = new RestoreRequest(
                    requiredLong(payload, "targetRevision"),
                    expectedRevision,
                    expectedHash,
                    user.getId());
            return success(snapshotData(revisionService.restore(request, user.getDomainId())));
        } catch (Exception exception) {
            return failure("restore", exception);
        }
    }

    /** Converts an import payload into a server-owned domain request. */
    @SuppressWarnings("unchecked")
    private static ImportRequest importRequest(JSONObject payload, User user) {
        if (payload == null || !(payload.get("content") instanceof Map<?, ?>)) {
            throw new OntologyRevisionException(
                    OntologyRevisionException.INVALID_REQUEST, "Import content must be a JSON object");
        }
        Map<String, Object> rule = JSON.parseObject(JSON.toJSONString(payload.get("content")));
        Map<String, Object> resolutions = payload.get("resolutions") instanceof Map<?, ?>
                ? JSON.parseObject(JSON.toJSONString(payload.get("resolutions")))
                : Map.of();
        Long baseRevision = payload.getLong("baseRevision");
        if (baseRevision == null) {
            baseRevision = payload.getLong("revision");
        }
        String baseHash = payload.getString("baseHash");
        if (baseHash == null) {
            baseHash = payload.getString("contentHash");
        }
        if (baseHash == null) {
            baseHash = payload.getString("hash");
        }
        if (baseRevision == null) {
            throw new OntologyRevisionException(
                    OntologyRevisionException.INVALID_REQUEST, "Base revision is required");
        }
        return new ImportRequest(rule, baseRevision, baseHash, resolutions, payload.getString("source"), user.getId());
    }

    /** Reads a required positive long from a request body. */
    private static long requiredLong(JSONObject payload, String field) {
        Long value = payload == null ? null : payload.getLong(field);
        if (value == null || value < 1) {
            throw new OntologyRevisionException(
                    OntologyRevisionException.INVALID_REQUEST, field + " is required");
        }
        return value;
    }

    /** Converts snapshots to a bounded JSON response shape. */
    private static List<JSONObject> snapshots(List<OntologyRevisionSnapshot> snapshots) {
        List<JSONObject> data = new ArrayList<>(snapshots.size());
        for (OntologyRevisionSnapshot snapshot : snapshots) {
            data.add(snapshotMetadata(snapshot));
        }
        return data;
    }

    /** Converts one snapshot into the compact history-list shape without repeating ontology content. */
    private static JSONObject snapshotMetadata(OntologyRevisionSnapshot snapshot) {
        JSONObject data = new JSONObject();
        data.put("revision", snapshot.revision());
        data.put("contentHash", snapshot.contentHash());
        data.put("source", snapshot.source());
        data.put("actorId", snapshot.actorId());
        data.put("createdAt", snapshot.createdAt());
        return data;
    }

    /** Converts one snapshot into the export and API response shape. */
    private static JSONObject snapshotData(OntologyRevisionSnapshot snapshot) {
        JSONObject data = new JSONObject();
        data.put("revision", snapshot.revision());
        data.put("contentHash", snapshot.contentHash());
        data.put("content", snapshot.rule());
        data.put("source", snapshot.source());
        data.put("actorId", snapshot.actorId());
        data.put("createdAt", snapshot.createdAt());
        return data;
    }

    /** Creates a successful response using the existing Manager response convention. */
    private static JSONObject success(Object data) {
        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("data", data);
        return response;
    }

    /** Creates a stable failure response and logs only endpoint context, never ontology content. */
    private static JSONObject failure(String endpoint, Exception exception) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        if (exception instanceof OntologyRevisionException revisionException) {
            response.put("code", revisionException.getCode());
            response.put("message", revisionException.getMessage());
        } else {
            response.put("code", INTERNAL_ERROR);
            response.put("message", "Ontology revision operation failed");
            log.error("Ontology revision operation failed: endpoint={}", endpoint, exception);
        }
        return response;
    }
}
