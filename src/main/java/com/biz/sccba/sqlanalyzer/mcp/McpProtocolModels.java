package com.biz.sccba.sqlanalyzer.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * MCP协议模型类
 * 基于JSON-RPC 2.0和MCP规范
 */
public class McpProtocolModels {

    /**
     * JSON-RPC 2.0 请求
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcRequest {
        @JsonProperty("jsonrpc")
        private String jsonrpc = "2.0";
        
        private String method;
        
        @JsonProperty("params")
        private Map<String, Object> params;
        
        private String id;
    }

    /**
     * JSON-RPC 2.0 响应
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse {
        @JsonProperty("jsonrpc")
        private String jsonrpc = "2.0";
        
        private Object result;
        
        private JsonRpcError error;
        
        private String id;
    }

    /**
     * JSON-RPC 错误
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcError {
        private Integer code;
        private String message;
        private Object data;
    }

    /**
     * MCP工具定义
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpTool {
        private String name;
        private String description;
        private ToolInputSchema inputSchema;
    }

    /**
     * 工具输入模式
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolInputSchema {
        private String type = "object";
        private Map<String, ToolProperty> properties;
        private List<String> required;
    }

    /**
     * 工具属性定义
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolProperty {
        private String type;
        private String description;
        private Object items; // 用于数组类型
    }

    /**
     * MCP资源定义
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpResource {
        private String uri;
        private String name;
        private String description;
        private String mimeType;
    }

    /**
     * MCP初始化参数
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InitializeParams {
        @JsonProperty("protocolVersion")
        private String protocolVersion = "2024-11-05";
        
        private String capabilities;
        
        @JsonProperty("clientInfo")
        private ClientInfo clientInfo;
    }

    /**
     * 客户端信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientInfo {
        private String name;
        private String version;
    }

    /**
     * MCP初始化结果
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InitializeResult {
        @JsonProperty("protocolVersion")
        private String protocolVersion = "2024-11-05";
        
        private ServerCapabilities capabilities;
        
        @JsonProperty("serverInfo")
        private ServerInfo serverInfo;
    }

    /**
     * 服务器能力
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServerCapabilities {
        private ToolsCapability tools;
        private ResourcesCapability resources;
    }

    /**
     * 工具能力
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolsCapability {
        @JsonProperty("listChanged")
        private Boolean listChanged = true;
    }

    /**
     * 资源能力
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourcesCapability {
        @JsonProperty("subscribe")
        private Boolean subscribe = false;
        
        @JsonProperty("listChanged")
        private Boolean listChanged = true;
    }

    /**
     * 服务器信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServerInfo {
        private String name = "SQL Performance Analyzer MCP Server";
        private String version = "1.0.0";
    }

    /**
     * 工具调用结果
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallResult {
        @JsonProperty("content")
        private List<ContentItem> content;
        
        @JsonProperty("isError")
        private Boolean isError = false;
    }

    /**
     * 内容项
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentItem {
        private String type; // "text" or "resource"
        private String text;
        private String uri;
    }
}

