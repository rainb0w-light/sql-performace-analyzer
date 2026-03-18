import { useEffect, useState } from 'react';

interface SpinnerProps {
  label?: string;
}

const FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'];

export function Spinner({ label = 'Loading' }: SpinnerProps) {
  const [frame, setFrame] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setFrame((f) => (f + 1) % FRAMES.length);
    }, 80);
    return () => clearInterval(interval);
  }, []);

  return (
    <box style={{ flexDirection: 'row', alignItems: 'center', gap: 1 }}>
      <text style={{ fg: 'cyan' }}>{FRAMES[frame]}</text>
      <text style={{ fg: '#666' }}>{label}...</text>
    </box>
  );
}
