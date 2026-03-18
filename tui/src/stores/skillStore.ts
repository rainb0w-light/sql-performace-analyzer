import { create } from 'zustand';

interface SkillState {
  isExecuting: boolean;
  output: string[];
  error: string | null;
  setExecuting: (executing: boolean) => void;
  addOutput: (line: string) => void;
  clearOutput: () => void;
  setError: (error: string | null) => void;
}

export const useSkillStore = create<SkillState>((set) => ({
  isExecuting: false,
  output: [],
  error: null,
  setExecuting: (executing) => set({ isExecuting: executing }),
  addOutput: (line) => set((state) => ({ output: [...state.output, line] })),
  clearOutput: () => set({ output: [] }),
  setError: (error) => set({ error }),
}));
