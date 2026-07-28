package com.biz.sccba.sqlanalyzer.idea.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Project-level settings (development-guide §8.3): backend endpoint and analysis session belong
 * to the project; the user token is an application-level secret stored in {@link TokenStore}.
 */
@State(name = "SqlAnalyzerProjectSettings", storages = @Storage("sql-analyzer-project.xml"))
public final class ProjectAnalyzerSettings implements PersistentStateComponent<ProjectAnalyzerSettings.State> {

    public static final class State {
        public String endpoint = "http://localhost:18881";
        public String sessionId = "";
        /** Explicit project → datasource binding; inferred only when the tenant has one profile. */
        public String datasourceProfileId = "";
        /** Module-specific binding wins over the project default. */
        public Map<String, String> moduleDatasourceProfiles = new HashMap<>();
        public Map<String, String> mapperArtifactsByHash = new java.util.LinkedHashMap<>();
        public String executionMode = "AUTO";
        public int maxScenarios = 20;
        public String costThreshold = "MEDIUM";
        public int localCacheMegabytes = 50;
        /** Client-side content-hash cache: identical mapper content is not re-uploaded (§8.1). */
        public String lastMapperHash = "";
        public String lastMapperArtifactId = "";
    }

    private State state = new State();

    public static ProjectAnalyzerSettings getInstance(@NotNull Project project) {
        return project.getService(ProjectAnalyzerSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String endpoint() { return state.endpoint; }
    public void endpoint(String value) { state.endpoint = value == null ? "" : value.trim(); }
    public String sessionId() { return state.sessionId; }
    public void sessionId(String value) { state.sessionId = value == null ? "" : value.trim(); }
    public String datasourceProfileId() { return state.datasourceProfileId; }
    public void datasourceProfileId(String value) {
        state.datasourceProfileId = value == null ? "" : value.trim();
    }
    public String datasourceProfileIdForModule(String moduleName) {
        if (moduleName != null && !moduleName.isBlank()) {
            String moduleValue = state.moduleDatasourceProfiles.get(moduleName);
            if (moduleValue != null && !moduleValue.isBlank()) return moduleValue;
        }
        return datasourceProfileId();
    }
    public String moduleDatasourceProfile(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) return "";
        return state.moduleDatasourceProfiles.getOrDefault(moduleName, "");
    }
    public void bindDatasourceProfile(String moduleName, String value) {
        String normalized = value == null ? "" : value.trim();
        if (moduleName == null || moduleName.isBlank()) {
            datasourceProfileId(normalized);
        } else if (normalized.isBlank()) {
            state.moduleDatasourceProfiles.remove(moduleName);
        } else {
            state.moduleDatasourceProfiles.put(moduleName, normalized);
        }
    }
    public Map<String, String> moduleDatasourceProfiles() {
        return Map.copyOf(state.moduleDatasourceProfiles);
    }
    public String lastMapperHash() { return state.lastMapperHash; }
    public String lastMapperArtifactId() { return state.lastMapperArtifactId; }
    public void mapperCache(String hash, String artifactId) {
        state.lastMapperHash = hash == null ? "" : hash;
        state.lastMapperArtifactId = artifactId == null ? "" : artifactId;
        cacheArtifact(hash, artifactId);
    }
    public String artifactForHash(String hash) {
        String value = state.mapperArtifactsByHash.get(hash);
        if (value != null && !value.isBlank()) return value;
        return hash != null && hash.equals(state.lastMapperHash) ? state.lastMapperArtifactId : "";
    }
    public void cacheArtifact(String hash, String artifactId) {
        if (hash == null || hash.isBlank() || artifactId == null || artifactId.isBlank()) return;
        state.mapperArtifactsByHash.put(hash, artifactId);
        while (state.mapperArtifactsByHash.size() > 32) {
            String eldest = state.mapperArtifactsByHash.keySet().iterator().next();
            state.mapperArtifactsByHash.remove(eldest);
        }
        state.lastMapperHash = hash;
        state.lastMapperArtifactId = artifactId;
    }
    public String executionMode() { return state.executionMode; }
    public void executionMode(String value) {
        state.executionMode = "REVIEW".equals(value) ? "REVIEW" : "AUTO";
    }
    public int maxScenarios() { return Math.max(1, Math.min(100, state.maxScenarios)); }
    public void maxScenarios(int value) { state.maxScenarios = Math.max(1, Math.min(100, value)); }
    public String costThreshold() { return state.costThreshold; }
    public void costThreshold(String value) {
        state.costThreshold = java.util.Set.of("UNKNOWN", "LOW", "MEDIUM", "HIGH", "EXTREME").contains(value)
                ? value : "MEDIUM";
    }
    public int localCacheMegabytes() { return Math.max(10, Math.min(500, state.localCacheMegabytes)); }
    public void localCacheMegabytes(int value) {
        state.localCacheMegabytes = Math.max(10, Math.min(500, value));
    }
}
