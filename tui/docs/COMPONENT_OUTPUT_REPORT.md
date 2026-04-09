# OpenTUI 组件渲染输出报告

**生成时间**: 2026-04-09  
**项目**: GoldenDB SQL Performance Analyzer - TUI  
**测试框架**: OpenTUI React Test Utils

---

## 目录

1. [主界面 (App)](#主界面-app)
2. [侧边栏 (Sidebar)](#侧边栏-sidebar)
3. [聊天气泡 (ChatBubble)](#聊天气泡-chatbubble)
4. [命令输入框 (CommandInput)](#命令输入框-commandinput)

---

## 主界面 (App)

**文件**: [`app-main-interface.txt`](./test-output/app-main-interface.txt)

**尺寸**: 120 x 40

**Props**: `{}`

**渲染输出**:
```
                                                                                                                        
  GDB   |   性能分析工具                                                                                      ○ 未连接  
                                                                                                 ┌────────────────────┐ 
  会话记录                                                                                       │                    │ 
                                                                                                 │  状态              │ 
                                                                                              █  │                    │ 
                                                                                              █  └────────────────────┘ 
                                                                                              █                         
                                                                                              █    WebSocket:           
                                                                                              █                         
                                                                                              █    ○ 未连接             
                                                                                              █                         
                                                                                              █    状态:                
                                                                                              █                         
                                                                                              █    就绪                 
                                                                                              █                         
                                                                                              █    Session:             
                                                                                              █                         
                                                                                              █    -                    
                                                                                              █                         
                                                                                              ▀                         
  ❯                                                                                                                     
     快捷键:    Enter 发送    |    Esc 清空                                                                             
```

**分析**:
- 头部显示品牌标识 "GDB | 性能分析工具"
- 右上角显示连接状态 "○ 未连接"
- 侧边栏显示 WebSocket 状态、处理状态、Session ID
- 底部输入框显示快捷键提示

---

## 侧边栏 (Sidebar)

**文件**: [`sidebar-connected.txt`](./test-output/sidebar-connected.txt)

**尺寸**: 28 x 24

**Props**: 
```json
{
  "isConnected": true,
  "status": "idle",
  "sessionId": "test-session-123"
}
```

**渲染输出**:
```
 ┌────────────────────┐     
 │                    │     
 │  状态              │     
 │                    │     
 └────────────────────┘     
                            
   WebSocket:               
                            
   ● 已连接                 
                            
   状态:                    
                            
   就绪                     
                            
   Session:                 
                            
   test-sessi...            
```

**分析**:
- 边框分组清晰
- 连接状态用 ● (绿色) 表示已连接
- Session ID 过长时自动截断

---

## 聊天气泡 (ChatBubble)

**文件**: [`chatbubble-response.txt`](./test-output/chatbubble-response.txt)

**尺寸**: 80 x 10

**Props**: 
```json
{
  "type": "response",
  "content": "测试消息",
  "timestamp": "10:30:00",
  "title": "响应"
}
```

**渲染输出**:
```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  ● 响应  10:30:00                                                            │
│                                                                              │
│  这是一个测试响应消息，用于验证聊天气泡的渲染效果。                          │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

**分析**:
- 边框样式统一
- 消息类型图标 ● 显示正确
- 时间戳显示在标题行
- 内容区域有足够的内边距

---

## 命令输入框 (CommandInput)

**文件**: [`commandinput-empty.txt`](./test-output/commandinput-empty.txt)

**尺寸**: 60 x 5

**Props**: 
```json
{
  "onSubmit": "() => {}",
  "disabled": false
}
```

**渲染输出**:
```
                                                            
                                                            
快捷键：    Enter 发送    |    Esc 清空                      
                                                            
                                                            
```

**分析**:
- 空输入框时显示快捷键提示
- 不显示占位符文本
- 提示清晰说明 Enter 和 Esc 的功能

---

## 附录：运行测试生成报告

```bash
# 运行 UX 分析测试并生成输出文件
bun test tests/ux-analysis.test.tsx

# 输出文件保存在 docs/test-output/ 目录
```

---

**报告生成工具**: `tests/ux-analysis.test.tsx`  
**输出目录**: `docs/test-output/`
