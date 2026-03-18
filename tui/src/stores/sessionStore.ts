import { create } from 'zustand';

interface Session {
  id: string;
  name: string;
  status: 'idle' | 'active' | 'error';
  createdAt: string;
}

interface SessionState {
  sessions: Session[];
  activeSession: Session | null;
  setSessions: (sessions: Session[]) => void;
  setActiveSession: (session: Session | null) => void;
  addSession: (session: Session) => void;
  removeSession: (sessionId: string) => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  sessions: [],
  activeSession: null,
  setSessions: (sessions) => set({ sessions }),
  setActiveSession: (session) => set({ activeSession: session }),
  addSession: (session) =>
    set((state) => ({ sessions: [...state.sessions, session] })),
  removeSession: (sessionId) =>
    set((state) => ({
      sessions: state.sessions.filter((s) => s.id !== sessionId),
    })),
}));
