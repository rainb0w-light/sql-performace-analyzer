const api = {
  token() { return localStorage.getItem("spaKnowledgeToken") || ""; },
  setToken(value) { localStorage.setItem("spaKnowledgeToken", value.trim()); },
  async call(path, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Authorization", `Bearer ${api.token()}`);
    headers.set("X-Request-Id", `web_${crypto.randomUUID()}`);
    const response = await fetch(path, {...options, headers});
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new Error(problem.detail || `HTTP ${response.status}`);
    }
    return response.headers.get("content-type")?.includes("json") ? response.json() : response;
  }
};
function bindToken() {
  const input = document.querySelector("#token");
  if (!input) return;
  input.value = api.token();
  input.addEventListener("change", () => api.setToken(input.value));
}
function notice(message) {
  const node = document.querySelector("#notice");
  node.textContent = message;
  node.style.display = "block";
  setTimeout(() => node.style.display = "none", 4500);
}
function html(value) {
  return String(value ?? "").replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
  })[character]);
}
window.addEventListener("DOMContentLoaded", bindToken);
