import { createCliRenderer } from '@opentui/core';
import { createRoot } from '@opentui/react';
import { App } from './App';

async function main() {
  const renderer = await createCliRenderer({
    exitOnCtrlC: false,
  });
  createRoot(renderer).render(<App />);
}

main().catch(console.error);
