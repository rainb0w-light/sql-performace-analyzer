interface StatusBarProps {
  status?: 'idle' | 'busy' | 'error';
  sessionId?: string;
  datasource?: string;
  llm?: string;
}

const STATUS_TEXT: Record<string, string> = {
  idle: '就绪',
  busy: '处理中',
  error: '错误',
};

const STATUS_COLOR: Record<string, string> = {
  idle: 'green',
  busy: 'yellow',
  error: 'red',
};

export function StatusBar({
  status = 'idle',
  sessionId = '-',
  datasource = '-',
  llm = '-',
}: StatusBarProps) {
  return (
    <box
      style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        padding: [0, 2],
        borderTop: 'single',
        borderColor: '#333',
        backgroundColor: '#1a1a2e',
      }}
    >
      <box style={{ flexDirection: 'row', gap: 2 }}>
        <text style={{ fg: STATUS_COLOR[status] }}>{STATUS_TEXT[status]}</text>
        <text style={{ fg: '#666' }}>|</text>
        <text style={{ fg: '#888' }}>Session:</text>
        <text>{sessionId}</text>
      </box>
      <box style={{ flexDirection: 'row', gap: 2 }}>
        <text style={{ fg: '#888' }}>DS:</text>
        <text>{datasource}</text>
        <text style={{ fg: '#666' }}>|</text>
        <text style={{ fg: '#888' }}>LLM:</text>
        <text>{llm}</text>
      </box>
    </box>
  );
}
