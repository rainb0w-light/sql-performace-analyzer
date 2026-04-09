# OpenTUI Testing Capabilities - Complete Guide

## 1. Key Characteristics and Features

OpenTUI provides **Bun-exclusive** testing utilities designed for terminal UI applications with the following key features:

### Core Testing Philosophy
- **Frame-based testing**: Capture and assert against rendered terminal output as character grids
- **Mock input injection**: Simulate keyboard and mouse events programmatically
- **Real renderer execution**: Uses the actual Zig-native renderer in test mode, not a mock
- **No terminal side effects**: Tests run without affecting the actual terminal state

**Evidence** - Test Renderer Setup:
```typescript
export async function createTestRenderer(options: TestRendererOptions): Promise<{
  renderer: TestRenderer
  mockInput: MockInput
  mockMouse: MockMouse
  renderOnce: () => Promise<void>
  captureCharFrame: () => string
  captureSpans: () => CapturedFrame
  resize: (width: number, height: number) => void
}> {
  const renderer = await setupTestRenderer({
    ...options,
    screenMode: options.screenMode ?? "main-screen",
    footerHeight: options.footerHeight ?? 12,
  })
  // Uses real Zig-native renderer with testing mode enabled
  const ziglib = resolveRenderLib()
  const rendererPtr = ziglib.createRenderer(width, renderHeight, {
    testing: true,  // ← Testing mode flag
    remote: config.remote ?? false,
  })
}
```

---

## 2. Frame Capture and Access

### Frame Capture Methods

OpenTUI provides **three primary mechanisms** for accessing rendered output:

| Method | Returns | Use Case |
|--------|---------|----------|
| `captureCharFrame()` | `string` | Simple text assertions |
| `captureSpans()` | `CapturedFrame` | Rich text with attributes |
| `TestRecorder` | `RecordedFrame[]` | Animation/render sequence testing |

### Method 1: Direct Frame Capture

**Evidence** - Character Frame Capture:
```typescript
captureCharFrame: () => {
  const currentBuffer = renderer.currentRenderBuffer
  const frameBytes = currentBuffer.getRealCharBytes(true)
  return decoder.decode(frameBytes)
}

captureSpans: () => {
  const currentBuffer = renderer.currentRenderBuffer
  const lines = currentBuffer.getSpanLines()
  const cursorState = renderer.getCursorState()
  return {
    cols: currentBuffer.width,
    rows: currentBuffer.height,
    cursor: [cursorState.x, cursorState.y] as [number, number],
    lines,  // Contains styled text spans with attributes
  }
}
```

**Usage Example**:
```typescript
import { test, expect } from "bun:test"
import { createTestRenderer } from "@opentui/core/testing"

test("renders markdown table", async () => {
  const { renderer, renderOnce, captureCharFrame } = await createTestRenderer({
    width: 60,
    height: 40,
  })

  const md = new MarkdownRenderable(renderer, {
    content: "| Name | Age |\n|---|---|\n| Alice | 30 |",
  })

  renderer.root.add(md)
  await renderOnce()

  const frame = captureCharFrame()
  expect(frame).toContain("┌─────┬───┐")
  expect(frame).toContain("│Alice│30 │")
})
```

### Method 2: TestRecorder for Render Sequences

Records **multiple frames over time** for testing animations, streaming content, or render state transitions.

**Evidence** - TestRecorder Implementation:
```typescript
export class TestRecorder {
  private renderer: TestRenderer
  private frames: RecordedFrame[] = []
  private recording: boolean = false
  private frameNumber: number = 0
  private startTime: number = 0

  public rec(): void {
    this.recording = true
    this.frames = []
    this.frameNumber = 0
    this.startTime = this.now()

    // Hook into renderNative to capture frames after each render
    this.originalRenderNative = this.renderer["renderNative"].bind(this.renderer)
    this.renderer["renderNative"] = () => {
      this.originalRenderNative!()
      this.captureFrame()  // Automatically captures after every render
    }
  }

  public get recordedFrames(): RecordedFrame[] {
    return [...this.frames]
  }
}
```

**RecordedFrame Structure**:
```typescript
export interface RecordedFrame {
  frame: string              // The captured frame content
  timestamp: number          // Time in milliseconds since recording started
  frameNumber: number        // Sequential frame number (0-indexed)
  buffers?: RecordedBuffers  // Optional: raw buffer data (fg, bg, attributes)
}
```

**Usage Example** - Testing for Visual Flicker:
```typescript
import { TestRecorder } from "@opentui/core/testing"

test("streaming code blocks with concealCode=true do not flash unconcealed markdown", async () => {
  const mockTreeSitterClient = new MockTreeSitterClient()
  const recorder = new TestRecorder(renderer)
  
  recorder.rec()  // Start recording

  const md = new MarkdownRenderable(renderer, {
    content: "```markdown\n# Hidden heading\n```",
    concealCode: true,
    streaming: true,
  })

  renderer.root.add(md)
  await renderer.idle()
  recorder.stop()

  // Assert no frame in the sequence contained the unconcealed version
  const frames = recorder.recordedFrames.map((frame) => frame.frame)
  const unconcealedFrames = frames.filter((frame) => 
    frame.includes("# Hidden heading")
  )
  expect(unconcealedFrames.length).toBe(0)  // No flickering occurred
})
```

**Advanced: Recording Buffer Data**:
```typescript
// Record background color buffers for selection visual testing
const recorder = new TestRecorder(currentRenderer, { 
  recordBuffers: { bg: true }  // Also capture raw RGBA background data
})

recorder.rec()
// ... interactions ...
recorder.stop()

// Access raw buffer data for pixel-perfect assertions
const frameWithBuffers = recorder.recordedFrames[0]
const backgroundColors = frameWithBuffers.buffers?.bg  // Float32Array of RGBA
```

---

## 3. Testing Utilities Available

### 3.1 Test Renderer (`createTestRenderer`)

**Evidence** - Full API:

| Export | Description |
|--------|-------------|
| `renderer` | Full `CliRenderer` instance for mounting components |
| `mockInput` | Keyboard input simulator |
| `mockMouse` | Mouse input simulator |
| `renderOnce()` | Triggers single render cycle |
| `captureCharFrame()` | Get plain text output |
| `captureSpans()` | Get styled output with attributes |
| `resize(w, h)` | Simulate terminal resize |

### 3.2 Keyboard Input Mocking (`createMockKeys`)

**Evidence** - Keyboard Mock API:

```typescript
const mockInput = createMockKeys(renderer, {
  kittyKeyboard: true,  // Enable Kitty keyboard protocol
  otherModifiersMode: false,
})

// Type text
mockInput.typeText("hello world")
await mockInput.typeText("hello", 10)  // 10ms delay between keys

// Press single keys
mockInput.pressKey("a")
mockInput.pressKey(KeyCodes.ENTER)

// Press keys with modifiers
mockInput.pressKey("a", { ctrl: true })
mockInput.pressKey("f", { meta: true })
mockInput.pressKey("z", { ctrl: true, shift: true })

// Convenience methods
mockInput.pressEnter()
mockInput.pressEscape()
mockInput.pressTab()
mockInput.pressBackspace()
mockInput.pressArrow("up" | "down" | "left" | "right")
mockInput.pressCtrlC()
mockInput.pasteBracketedText("paste content")
```

**Available KeyCodes**:
```typescript
export const KeyCodes = {
  RETURN: "\r",
  LINEFEED: "\n",
  TAB: "\t",
  BACKSPACE: "\b",
  DELETE: "\x1b[3~",
  HOME: "\x1b[H",
  END: "\x1b[F",
  ESCAPE: "\x1b",
  ARROW_UP: "\x1b[A",
  ARROW_DOWN: "\x1b[B",
  ARROW_RIGHT: "\x1b[C",
  ARROW_LEFT: "\x1b[D",
  F1: "\x1bOP",
  // F2-F12 available
}
```

### 3.3 Mouse Input Mocking (`createMockMouse`)

**Evidence** - Mouse Mock API:

```typescript
const mockMouse = createMockMouse(renderer)

// Click
await mockMouse.click(x, y)
await mockMouse.click(x, y, MouseButtons.RIGHT)
await mockMouse.click(x, y, MouseButtons.LEFT, {
  modifiers: { ctrl: true, shift: true, alt: true },
  delayMs: 10,
})

// Double click
await mockMouse.doubleClick(x, y)

// Press and release
await mockMouse.pressDown(x, y, MouseButtons.MIDDLE)
await mockMouse.release(x, y, MouseButtons.MIDDLE)

// Move
await mockMouse.moveTo(x, y)
await mockMouse.moveTo(x, y, { modifiers: { shift: true } })

// Drag
await mockMouse.drag(startX, startY, endX, endY)
await mockMouse.drag(startX, startY, endX, endY, MouseButtons.RIGHT, {
  modifiers: { alt: true },
})

// Scroll
await mockMouse.scroll(x, y, "up" | "down" | "left" | "right")

// State
const pos = mockMouse.getCurrentPosition()  // { x, y }
const buttons = mockMouse.getPressedButtons()  // MouseButton[]
```

### 3.4 Spy Utility (`createSpy`)

**Evidence** - Spy API:
```typescript
import { createSpy } from "@opentui/core/testing"

const spy = createSpy()

// Use as callback
button.onClick = spy

// Assertions
spy.callCount()           // number
spy.calledWith(arg1, arg2) // boolean
spy.calls                 // any[][]
spy.reset()
```

**Usage Example** - Button Click Test:
```typescript
test("button click", async () => {
  const { renderer, mockMouse, renderOnce } = await createTestRenderer({ 
    width: 80, 
    height: 24 
  })

  const clicked = createSpy()
  const button = new Button("btn", { text: "Click me", onClick: clicked })

  renderer.add(button)
  await renderOnce()

  await mockMouse.click(10, 5)
  expect(clicked.callCount()).toBe(1)
})
```

### 3.5 Mock Utilities

**MockTreeSitterClient** - For testing syntax highlighting without real tree-sitter:
```typescript
const mockTreeSitterClient = new MockTreeSitterClient()
mockTreeSitterClient.setMockResult({
  highlights: [[0, 1, "conceal", { conceal: "" }]],
})
```

**ManualClock** - For testing time-dependent animations:
```typescript
import { ManualClock } from "@opentui/core/testing"

const clock = new ManualClock()
const { renderer } = await createTestRenderer({ clock })

// Manually advance time
clock.advance(100)  // Advance 100ms
```

---

## 4. Comparison: OpenTUI vs Ink Testing

### Ink Testing Library

```jsx
import { render } from 'ink-testing-library'

const Counter = ({ count }) => <Text>Count: {count}</Text>

const { lastFrame, rerender, stdout, stdin } = render(<Counter count={0} />)

lastFrame() === 'Count: 0'  // Compare output
stdout.frames              // Array of all frames
stdin.write('hello')       // Simulate input
rerender(<Counter count={1} />)
```

### Feature Comparison Table

| Feature | OpenTUI | Ink Testing Library |
|---------|---------|---------------------|
| **Test Runner** | Bun native | Jest/Vitest compatible |
| **Frame Capture** | `captureCharFrame()`, `captureSpans()` | `lastFrame()`, `stdout.frames` |
| **Frame Recording** | `TestRecorder` class | `stdout.frames` array |
| **Keyboard Mock** | `mockInput` with Kitty/modifier support | `stdin.write()` |
| **Mouse Mock** | Full mouse API (click, drag, scroll) | ❌ Not available |
| **Resize Testing** | `resize(width, height)` | ❌ Limited |
| **Buffer Access** | Raw RGBA buffer capture | ❌ Text only |
| **Real Renderer** | Yes (Zig-native in test mode) | No (pure JS render) |
| **React/Solid Support** | Yes via bindings | React only |

### Key Differentiators

1. **Mouse Testing**: OpenTUI has comprehensive mouse mocking; Ink has none (terminal mouse support is limited in React-based TUIs)

2. **Buffer-Level Testing**: OpenTUI can capture raw color/attribute buffers for pixel-perfect visual testing

3. **Protocol Support**: OpenTUI supports Kitty keyboard protocol and modifyOtherKeys for advanced key combinations

4. **Native Rendering**: OpenTUI tests use the actual Zig native renderer in test mode, providing more accurate results than pure JS mocking

5. **Frame Recording**: OpenTUI's `TestRecorder` hooks into the render pipeline to capture every frame automatically, while Ink requires manual frame collection

---

## Best Practices

### 1. Always Clean Up Renderers

```typescript
afterEach(async () => {
  if (renderer) {
    renderer.destroy()  // Prevent memory leaks
  }
})
```

### 2. Use Inline Snapshots for Frame Output

```typescript
test("renders table", async () => {
  const frame = await renderMarkdown("| A | B |\n|---|---|\n| 1 | 2 |")
  expect(frame).toMatchInlineSnapshot(`
    "
    ┌─┬─┐
    │A│B│
    ├─┼─┤
    │1│2│
    └─┴─┘"
  `)
})
```

### 3. Use ManualClock for Time-Dependent Tests

```typescript
test("animation completes", async () => {
  const clock = new ManualClock()
  const { renderer } = await createTestRenderer({ clock })
  
  // Start animation
  const component = new AnimatedComponent(renderer)
  
  // Advance time manually (no real waiting)
  clock.advance(500)
  await renderOnce()
  
  // Assert animation state
  expect(component.progress).toBe(0.5)
})
```

### 4. Test Render Sequences with TestRecorder

```typescript
test("no visual flicker during streaming", async () => {
  const recorder = new TestRecorder(renderer)
  recorder.rec()
  
  // Stream content
  component.content = generateLargeContent()
  await renderer.idle()
  
  recorder.stop()
  
  // Assert all frames meet quality criteria
  for (const frame of recorder.recordedFrames) {
    expect(frame.frame).not.toContain('unwanted-artifact')
  }
})
```

### 5. Test Mouse and Keyboard Together

```typescript
test("keyboard shortcuts with mouse selection", async () => {
  const { renderer, mockInput, mockMouse, renderOnce } = await createTestRenderer()
  
  // Select text with mouse
  await mockMouse.drag(10, 5, 50, 10)
  await renderOnce()
  
  // Copy with keyboard
  mockInput.pressKey("c", { ctrl: true })
  await renderOnce()
  
  // Assert clipboard content
  expect(renderer.clipboard).toContain("selected text")
})
```

---

## Summary

OpenTUI provides a **comprehensive, Bun-native testing framework** with:

- ✅ Frame capture (text + rich spans + raw buffers)
- ✅ `TestRecorder` for animation/render sequence testing
- ✅ Full keyboard mocking with Kitty protocol support
- ✅ Complete mouse mocking (click, drag, scroll)
- ✅ Real native renderer in test mode
- ✅ Manual clock for deterministic time-based tests
- ✅ Tree-sitter mocking for syntax highlighting tests

Compared to Ink, OpenTUI offers **significantly more testing capabilities**, especially for mouse interactions, buffer-level assertions, and render sequence validation.