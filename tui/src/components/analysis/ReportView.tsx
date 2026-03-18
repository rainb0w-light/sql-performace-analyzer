interface ReportViewProps {
  title?: string;
  content: string;
}

export function ReportView({ title = '分析报告', content }: ReportViewProps) {
  const parseContent = (text: string) => {
    const lines = text.split('\n');
    const elements: Array<{ type: string; content: string; level?: number }> = [];

    for (const line of lines) {
      if (line.startsWith('### ')) {
        elements.push({ type: 'h3', content: line.replace(/^###\s*/, ''), level: 3 });
      } else if (line.startsWith('## ')) {
        elements.push({ type: 'h2', content: line.replace(/^##\s*/, ''), level: 2 });
      } else if (line.startsWith('# ')) {
        elements.push({ type: 'h1', content: line.replace(/^#\s*/, ''), level: 1 });
      } else if (line.startsWith('- ') || line.startsWith('* ')) {
        elements.push({ type: 'li', content: line.replace(/^[-*]\s*/, '') });
      } else if (line.match(/^\d+\.\s/)) {
        elements.push({ type: 'ol', content: line.replace(/^\d+\.\s*/, '') });
      } else if (line.startsWith('**') && line.endsWith('**')) {
        elements.push({ type: 'strong', content: line.replace(/^\*\*|\*\*$/g, '') });
      } else if (line.trim()) {
        elements.push({ type: 'p', content: line });
      }
    }

    return elements;
  };

  const parsedElements = parseContent(content);

  return (
    <box style={{ flexDirection: 'column', padding: [1, 1] }}>
      <box style={{ marginBottom: 1 }}>
        <text style={{ fg: 'cyan' }}>{title}</text>
      </box>

      <box style={{ flexDirection: 'column' }}>
        {parsedElements.map((el, i) => {
          switch (el.type) {
            case 'h1':
              return (
                <box key={i} style={{ marginBottom: 1 }}>
                  <text style={{ fg: 'cyan' }}>{el.content}</text>
                </box>
              );
            case 'h2':
              return (
                <box key={i} style={{ marginBottom: 1 }}>
                  <text style={{ fg: 'cyan' }}>{el.content}</text>
                </box>
              );
            case 'h3':
              return (
                <box key={i} style={{ marginBottom: 1 }}>
                  <text style={{ fg: 'cyan' }}>{el.content}</text>
                </box>
              );
            case 'strong':
              return (
                <box key={i} style={{ marginBottom: 1 }}>
                  <text>{el.content}</text>
                </box>
              );
            case 'li':
            case 'ol':
              return (
                <box key={i} style={{ flexDirection: 'row', gap: 1, marginBottom: 1 }}>
                  <text style={{ fg: '#666' }}>•</text>
                  <text>{el.content}</text>
                </box>
              );
            default:
              return (
                <box key={i} style={{ marginBottom: 1 }}>
                  <text>{el.content}</text>
                </box>
              );
          }
        })}
      </box>
    </box>
  );
}
