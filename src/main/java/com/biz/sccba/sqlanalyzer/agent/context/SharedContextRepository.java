package com.biz.sccba.sqlanalyzer.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared context repository for multi-expert coordination.
 * Provides real-time access to expert findings and enables true collaboration between experts.
 */
@Component
public class SharedContextRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Session-specific contexts
    private final ConcurrentMap<String, ExpertExecutionContext> sessionContexts = new ConcurrentHashMap<>();
    
    /**
     * Creates or retrieves an execution context for a session.
     */
    public ExpertExecutionContext createContext(String sessionId) {
        return sessionContexts.computeIfAbsent(sessionId, ExpertExecutionContext::new);
    }
    
    /**
     * Retrieves an existing execution context for a session.
     */
    public ExpertExecutionContext getContext(String sessionId) {
        return sessionContexts.get(sessionId);
    }
    
    /**
     * Removes the execution context for a session (cleanup).
     */
    public void removeContext(String sessionId) {
        sessionContexts.remove(sessionId);
    }
    
    /**
     * Expert execution context containing shared state and results.
     */
    public static class ExpertExecutionContext {
        private final String sessionId;
        private final ConcurrentMap<String, ExpertResult> expertResults = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Object> sharedMetadata = new ConcurrentHashMap<>();
        
        public ExpertExecutionContext(String sessionId) {
            this.sessionId = sessionId;
        }
        
        /**
         * Stores an expert result in the shared context.
         */
        public void storeExpertResult(String toolName, String resultJson) {
            try {
                JsonNode jsonNode = new ObjectMapper().readTree(resultJson);
                boolean success = jsonNode.has("success") && jsonNode.get("success").asBoolean();
                int priority = jsonNode.has("priority") ? jsonNode.get("priority").asInt() : 3;
                double confidence = jsonNode.has("confidence") ? jsonNode.get("confidence").asDouble() : 0.5;
                
                ExpertResult expertResult = new ExpertResult(
                    toolName,
                    resultJson,
                    success,
                    priority,
                    confidence,
                    System.currentTimeMillis()
                );
                expertResults.put(toolName, expertResult);
            } catch (Exception e) {
                // Log error but don't fail - store raw result as fallback
                ExpertResult expertResult = new ExpertResult(
                    toolName,
                    resultJson,
                    false,
                    3,
                    0.0,
                    System.currentTimeMillis()
                );
                expertResults.put(toolName, expertResult);
            }
        }
        
        /**
         * Retrieves an expert result from the shared context.
         */
        public ExpertResult getExpertResult(String toolName) {
            return expertResults.get(toolName);
        }
        
        /**
         * Retrieves all expert results.
         */
        public Map<String, ExpertResult> getAllExpertResults() {
            return expertResults;
        }
        
        /**
         * Stores shared metadata that can be accessed by all experts.
         */
        public void storeMetadata(String key, Object value) {
            sharedMetadata.put(key, value);
        }
        
        /**
         * Retrieves shared metadata.
         */
        public <T> T getMetadata(String key, Class<T> type) {
            Object value = sharedMetadata.get(key);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            // Try to convert if it's a JSON string
            if (value instanceof String stringValue) {
                try {
                    return new ObjectMapper().readValue(stringValue, type);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
        
        /**
         * Gets the session ID.
         */
        public String getSessionId() {
            return sessionId;
        }
        
        /**
         * Clears all results and metadata (for session cleanup).
         */
        public void clear() {
            expertResults.clear();
            sharedMetadata.clear();
        }
    }
    
    /**
     * Represents a single expert's analysis result.
     */
    public static class ExpertResult {
        private final String toolName;
        private final String resultJson;
        private final boolean success;
        private final int priority;
        private final double confidence;
        private final long timestamp;
        
        public ExpertResult(String toolName, String resultJson, boolean success, 
                           int priority, double confidence, long timestamp) {
            this.toolName = toolName;
            this.resultJson = resultJson;
            this.success = success;
            this.priority = priority;
            this.confidence = confidence;
            this.timestamp = timestamp;
        }

        public String getToolName() { return toolName; }
        public String getResultJson() { return resultJson; }
        public boolean isSuccess() { return success; }
        public int getPriority() { return priority; }
        public double getConfidence() { return confidence; }
        public long getTimestamp() { return timestamp; }
    }
}