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
    <box style={{ flexDirection: 'column', border: 'single', borderColor: '#333', padding: [1, 1] }}>
      {events.map((event, i) => {
        switch (event.type) {
          case 'thinking':
            return (
              <box key={i} style={{ marginBottom: 1 }}>
                <text style={{ fg: 'yellow' }}>{`🤔 ${event.content}`}</text>
              </box>
            );
          case 'tool_call':
            return (
              <box key={i} style={{ marginBottom: 1 }}>
                <text style={{ fg: 'cyan' }}>{`🔧 ${event.content}`}</text>
              </box>
            );
          case 'tool_result':
            return (
              <box
                key={i}
                style={{
                  padding: [1, 1],
                  backgroundColor: '#1a1a1a',
                  border: 'single',
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
        <box style={{ marginTop: 1, borderTop: 'single', borderColor: '#333', paddingTop: 1 }}>
          <text style={{ fg: 'green' }}>✓ 完成</text>
        </box>
      )}
    </box>
  );
}
