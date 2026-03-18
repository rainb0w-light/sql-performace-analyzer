import axios from 'axios';

const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export interface SessionCreateRequest {
  datasourceId?: string;
}

export interface SkillExecuteRequest {
  skillName: string;
  sessionId: string;
  params: Record<string, unknown>;
}

export const sessionApi = {
  create: (req: SessionCreateRequest) =>
    apiClient.post('/sessions', req).then((res) => res.data),
  list: () => apiClient.get('/sessions').then((res) => res.data),
  get: (id: string) => apiClient.get(`/sessions/${id}`).then((res) => res.data),
  close: (id: string) => apiClient.delete(`/sessions/${id}`).then((res) => res.data),
};

export const skillApi = {
  execute: (req: SkillExecuteRequest) =>
    apiClient.post('/skills/execute', req).then((res) => res.data),
};

export const ddlApi = {
  confirm: (sessionId: string, approved: boolean) =>
    apiClient.post(`/sessions/${sessionId}/ddl/confirm`, { approved }).then((res) => res.data),
};
