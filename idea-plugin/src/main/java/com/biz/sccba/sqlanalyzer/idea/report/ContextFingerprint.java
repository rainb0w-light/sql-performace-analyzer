package com.biz.sccba.sqlanalyzer.idea.report;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;

/** Full report freshness fingerprint required by the P1 design. */
public record ContextFingerprint(
        String contentHash,
        String statementId,
        String datasourceProfileId,
        String knowledgeVersion,
        String profileSnapshotId
) {
    public ContextFingerprint {
        contentHash = text(contentHash);
        statementId = text(statementId);
        datasourceProfileId = text(datasourceProfileId);
        knowledgeVersion = text(knowledgeVersion);
        profileSnapshotId = text(profileSnapshotId);
    }

    public boolean staleComparedWith(ContextFingerprint current) {
        return current != null && !equals(current);
    }

    public static ContextFingerprint fromReport(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject subject = object(root, "subject");
            JsonObject audit = object(root, "audit");
            JsonObject context = object(root, "contextFingerprint");
            return new ContextFingerprint(
                    value(context, "contentHash", value(subject, "contentHash", "")),
                    value(context, "statementId", value(subject, "statementId", "")),
                    value(context, "datasourceProfileId", value(audit, "datasourceProfileId", "")),
                    value(context, "knowledgeVersion", value(audit, "knowledgeVersion", "")),
                    value(context, "profileSnapshotId", value(audit, "profileSnapshotId", "")));
        } catch (RuntimeException ignored) {
            return new ContextFingerprint("", "", "", "", "");
        }
    }

    private static JsonObject object(JsonObject root, String field) {
        return root.has(field) && root.get(field).isJsonObject()
                ? root.getAsJsonObject(field) : new JsonObject();
    }

    private static String value(JsonObject object, String field, String fallback) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : fallback;
    }

    private static String text(String value) { return Objects.requireNonNullElse(value, ""); }
}

