package com.biz.sccba.sqlanalyzer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * MCP服务器控制器
 * 处理MCP协议请求（基于JSON-RPC 2.0）
 */
@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

    private static final Logger logger = LoggerFactory.getLogger(McpServerController.class);

    @Autowired
    private McpToolHandler toolHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * MCP协议端点
     * 处理所有MCP请求
     */
    @PostMapping(value = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
    public McpProtocolModels.JsonRpcResponse handleMcpRequest(
            @RequestBody McpProtocolModels.JsonRpcRequest request) {
        
        logger.info("收到MCP请求: method={}, id={}", request.getMethod(), request.getId());

        try {
            McpProtocolModels.JsonRpcResponse response = new McpProtocolModels.JsonRpcResponse();
            response.setJsonrpc("2.0");
            response.setId(request.getId());

            String method = request.getMethod();
            Map<String, Object> params = request.getParams() != null ? request.getParams() : new HashMap<>();

            switch (method) {
                case "initialize":
                    response.setResult(handleInitialize(params));
                    break;
                case "tools/list":
                    response.setResult(handleListTools());
                    break;
                case "tools/call":
                    response.setResult(handleCallTool(params));
                    break;
                case "resources/list":
                    response.setResult(handleListResources());
                    break;
                default:
                    McpProtocolModels.JsonRpcError error = new McpProtocolModels.JsonRpcError();
                    error.setCode(-32601);
                    error.setMessage("Method not found: " + method);
                    response.setError(error);
            }

            return response;
        } catch (Exception e) {
            logger.error("处理MCP请求失败", e);
            McpProtocolModels.JsonRpcResponse response = new McpProtocolModels.JsonRpcResponse();
            response.setJsonrpc("2.0");
            response.setId(request.getId());
            McpProtocolModels.JsonRpcError error = new McpProtocolModels.JsonRpcError();
            error.setCode(-32603);
            error.setMessage("Internal error: " + e.getMessage());
            response.setError(error);
            return response;
        }
    }

    /**
     * 处理initialize请求
     */
    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        logger.info("处理initialize请求");

        McpProtocolModels.InitializeResult result = new McpProtocolModels.InitializeResult();
        result.setProtocolVersion("2024-11-05");

        // 设置服务器能力
        McpProtocolModels.ServerCapabilities capabilities = new McpProtocolModels.ServerCapabilities();
        
        McpProtocolModels.ToolsCapability toolsCapability = new McpProtocolModels.ToolsCapability();
        toolsCapability.setListChanged(true);
        capabilities.setTools(toolsCapability);

        McpProtocolModels.ResourcesCapability resourcesCapability = new McpProtocolModels.ResourcesCapability();
        resourcesCapability.setSubscribe(false);
        resourcesCapability.setListChanged(true);
        capabilities.setResources(resourcesCapability);

        result.setCapabilities(capabilities);

        // 设置服务器信息
        McpProtocolModels.ServerInfo serverInfo = new McpProtocolModels.ServerInfo();
        serverInfo.setName("SQL Performance Analyzer MCP Server");
        serverInfo.setVersion("1.0.0");
        result.setServerInfo(serverInfo);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) objectMapper.convertValue(result, Map.class);
        return resultMap;
    }

    /**
     * 处理tools/list请求
     */
    private Map<String, Object> handleListTools() {
        logger.info("处理tools/list请求");

        List<McpProtocolModels.McpTool> tools = toolHandler.getAvailableTools();
        
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return result;
    }

    /**
     * 处理tools/call请求
     */
    private Map<String, Object> handleCallTool(Map<String, Object> params) {
        logger.info("处理tools/call请求: {}", params);

        String name = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        if (name == null) {
            throw new IllegalArgumentException("工具名称不能为空");
        }

        if (arguments == null) {
            arguments = new HashMap<>();
        }

        McpProtocolModels.ToolCallResult result = toolHandler.handleToolCall(name, arguments);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) objectMapper.convertValue(result, Map.class);
        return resultMap;
    }

    /**
     * 处理resources/list请求
     */
    private Map<String, Object> handleListResources() {
        logger.info("处理resources/list请求");

        // 目前不提供资源，返回空列表
        Map<String, Object> result = new HashMap<>();
        result.put("resources", Collections.emptyList());
        return result;
    }
}

