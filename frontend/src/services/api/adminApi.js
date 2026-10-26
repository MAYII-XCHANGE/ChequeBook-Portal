import { apiRequest } from "./client";

export const adminApi = {
  users: () => apiRequest("/admin/users"),
  roles: () => apiRequest("/admin/roles"),
  branches: () => apiRequest("/admin/branches"),
  createUser: (body) =>
    apiRequest("/admin/users", { method: "POST", body: JSON.stringify(body) }),
  updateUser: (id, body) =>
    apiRequest(`/admin/users/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  setUserActive: (id, active) =>
    apiRequest(`/admin/users/${id}/${active ? "activate" : "deactivate"}`, {
      method: "POST",
    }),
  createRole: (body) =>
    apiRequest("/admin/roles", { method: "POST", body: JSON.stringify(body) }),
  updateRole: (id, body) =>
    apiRequest(`/admin/roles/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  createBranch: (body) =>
    apiRequest("/admin/branches", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateBranch: (id, body) =>
    apiRequest(`/admin/branches/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  setBranchActive: (id, active) =>
    apiRequest(`/admin/branches/${id}/${active ? "activate" : "deactivate"}`, {
      method: "POST",
    }),
};
