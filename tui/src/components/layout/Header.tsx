export function Header() {
  return (
    <box
      style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingLeft: 2,
        paddingRight: 2,
        paddingTop: 0,
        paddingBottom: 0,
        border: true,
        borderStyle: 'single',
        borderColor: '#333',
        backgroundColor: '#1a1a2e',
      }}
    >
      <box style={{ flexDirection: 'row', alignItems: 'center', gap: 2 }}>
        <text style={{ fg: 'cyan' }}>SQL Performance Analyzer</text>
        <text style={{ fg: '#666' }}>|</text>
        <text style={{ fg: 'yellow' }}>AgentScope Edition</text>
      </box>
      <box style={{ flexDirection: 'row', gap: 2 }}>
        <text style={{ fg: '#666' }}>[F1]</text>
        <text>帮助</text>
        <text style={{ fg: '#666' }}>[Ctrl+Q]</text>
        <text>退出</text>
      </box>
    </box>
  );
}
