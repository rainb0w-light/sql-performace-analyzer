interface StreamEvent {
  type: 'thinking' | 'tool_call' | 'tool_result' | 'token';
  content: string;
  timestamp: number;
}

interface StreamingOutputProps {
  events: StreamEvent[];
  isComplete?: boolean;
}

export function StreamingOutput({ events, isComplete = false }: StreamingOutputProps) {
  return (
    <box style={{ flexDirection: 'column', border: true, borderStyle: 'single', borderColor: '#333', paddingTop: 1, paddingBottom: 1, paddingLeft: 1, paddingRight: 1 }}>
      {events.map((event, i) => {
        switch (event.type) {
          case 'thinking':
            return (
              <box key={i} style={{ marginBottom: 1 }}>
                <text style={{ fg: 'yellow' }}>{`[思考] ${event.content}`}</text>
              </box>
            );
          case 'tool_call':
            return (
              <box key={i} style={{ marginBottom: 1 }}>
                <text style={{ fg: 'cyan' }}>{`[工具] ${event.content}`}</text>
              </box>
            );
          case 'tool_result':
            return (
              <box
                key={i}
                style={{
                  paddingTop: 1,
                  paddingBottom: 1,
                  paddingLeft: 1,
                  paddingRight: 1,
                  backgroundColor: '#1a1a1a',
                  border: true,
                  borderStyle: 'single',
                  borderColor: '#333',
                  marginBottom: 1,
                }}
              >
                <text style={{ fg: 'green' }}>{event.content}</text>
              </box>
            );
          case 'token':
            return (
              <box key={i} style={{ marginBottom: 1 }}>
                <text>{event.content}</text>
              </box>
            );
          default:
            return null;
        }
      })}
      {isComplete && (
        <box style={{ marginTop: 1, border: true, borderStyle: 'single', borderColor: '#333', paddingTop: 1 }}>
          <text style={{ fg: 'green' }}>✓ 完成</text>
        </box>
      )}
    </box>
  );
}
