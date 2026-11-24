"use strict";

// Relative URLs keep the page on the same host, port, and application context.
const API = "api/v1/candidates";
const STATUSES = ["NEW", "SCREENING", "INTERVIEWING", "OFFERED", "HIRED", "REJECTED"];
const filters = document.getElementById("filters");
const editor = document.getElementById("candidate-form");
const dialog = document.getElementById("candidate-dialog");
const rows = document.getElementById("candidate-rows");
const state = {
  page: 0, totalPages: 0, loading: false, deleting: false,
  editorId: null, version: null, editorReady: false, saving: false, stale: false
};
let searchTimer;
let searchController;
let searchSequence = 0;
let editorController;
let editorSequence = 0;

function field(form, name) {
  return form.elements.namedItem(name);
}

function setText(id, text) {
  document.getElementById(id).textContent = text;
}

function addOption(select, value, label) {
  const option = document.createElement("option");
  option.value = value;
  option.textContent = label;
  select.append(option);
}

for (const status of STATUSES) {
  const label = document.createElement("label");
  label.className = "status-option";
  const checkbox = document.createElement("input");
  checkbox.type = "checkbox";
  checkbox.name = "status";
  checkbox.value = status;
  checkbox.setAttribute("aria-describedby", "filter-status-help filter-status-error");
  label.append(checkbox, document.createTextNode(status[0] + status.slice(1).toLowerCase()));
  document.getElementById("filter-status").append(label);
  addOption(field(editor, "status"), status, status);
}
for (const [name, label] of [
  ["createdAt", "Created"], ["updatedAt", "Updated"], ["firstName", "First name"],
  ["lastName", "Last name"], ["experienceYears", "Experience"], ["status", "Status"]
]) {
  addOption(field(filters, "sort"), `${name},desc`, `${label}: descending`);
  addOption(field(filters, "sort"), `${name},asc`, `${label}: ascending`);
}

function clearErrors(form) {
  for (const error of form.querySelectorAll("[data-error-for]")) error.textContent = "";
  for (const input of form.querySelectorAll("[aria-invalid]")) input.removeAttribute("aria-invalid");
}

function fieldError(form, name, message) {
  // Set-element errors arrive as "skills[]"; never use API text as an HTML selector.
  const baseName = name.split("[")[0];
  const error = Array.from(form.querySelectorAll("[data-error-for]"))
    .find(element => element.dataset.errorFor === baseName);
  if (error) {
    error.textContent = error.textContent ? `${error.textContent}; ${message}` : message;
    for (const input of form.querySelectorAll("input, select, textarea")) {
      if (input.name === baseName) input.setAttribute("aria-invalid", "true");
    }
  }
}

function selectedStatuses() {
  return filters.querySelectorAll('input[name="status"]:checked');
}

function focusFirstError(form) {
  const invalid = form?.querySelector('[aria-invalid="true"]');
  if (!invalid) return;
  // An error in a collapsed filter must be visible before it can receive focus.
  const details = invalid.closest("details");
  if (details) details.open = true;
  invalid.focus();
}

function updateFilterCount() {
  const names = ["location", "skills", "minExperience", "maxExperience", "maxNoticePeriod"];
  const count = names.filter(name => field(filters, name).value.trim() !== "").length +
    (selectedStatuses().length ? 1 : 0);
  setText("filter-count", count ? `${count} active` : "No filters active");
}

function splitSkills(value) {
  return value.trim() ? [...new Set(value.split(",").map(name => name.trim()))] : [];
}

function validate(form) {
  clearErrors(form);
  for (const input of form.querySelectorAll("input, select, textarea")) {
    if (!input.checkValidity()) fieldError(form, input.name, input.validationMessage);
    // maxlength does not flag values assigned programmatically, so also check length.
    if (input.maxLength >= 0 && input.value.length > input.maxLength) {
      fieldError(form, input.name, `Use at most ${input.maxLength} characters.`);
    }
    if (input.type === "number" && input.value && !Number.isFinite(Number(input.value))) {
      fieldError(form, input.name, "Enter a finite number.");
    }
  }
  const skills = splitSkills(field(form, "skills").value);
  const maxSkills = form === filters ? 20 : 50;
  const maxName = form === filters ? 100 : 50;
  if (skills.length > maxSkills || skills.some(name => !name || name.length > maxName)) {
    fieldError(form, "skills", `Use at most ${maxSkills} non-blank names, ${maxName} characters each.`);
  }
  if (form === filters) {
    const q = field(form, "q").value.trim();
    if (q && q.split(/\s+/).length > 5) fieldError(form, "q", "Use at most 5 search words.");
    const min = field(form, "minExperience").value;
    const max = field(form, "maxExperience").value;
    if (min !== "" && max !== "" && Number(min) > Number(max)) {
      fieldError(form, "minExperience", "Minimum experience must not exceed maximum experience.");
      fieldError(form, "maxExperience", "Maximum experience must not be below minimum experience.");
    }
  } else {
    for (const name of ["firstName", "lastName", "email"]) {
      if (!field(form, name).value.trim()) fieldError(form, name, "This field is required.");
    }
  }
  return !form.querySelector('[aria-invalid="true"]');
}

async function request(path = "", options = {}) {
  let response;
  try {
    response = await fetch(API + path, options);
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error("Cannot reach the server. Check your connection and try again.", { cause: error });
  }
  if (response.status === 204) return null;
  if (!(response.headers.get("Content-Type") || "").includes("json")) {
    throw new Error(`Unexpected server response (${response.status}). Please try again.`);
  }
  const body = await response.json();
  if (!response.ok) {
    const error = new Error(body.detail || `Request failed (${response.status}).`);
    error.status = response.status;
    error.problem = body;
    throw error;
  }
  return body;
}

function showError(area, error, form) {
  document.getElementById(`${area}-error`).hidden = false;
  setText(`${area}-error-text`, error.message);
  const problem = error.problem;
  if (form && problem) {
    for (const item of problem.errors || []) {
      if (item.field === "experienceRangeValid") {
        fieldError(form, "minExperience", item.message);
        fieldError(form, "maxExperience", item.message);
      } else {
        fieldError(form, item.field, item.message);
      }
    }
    if (error.status === 409 && problem.title === "Duplicate email") {
      fieldError(form, "email", problem.detail);
    }
  }
  if (area === "editor") {
    state.stale = error.status === 409 && problem?.title === "Concurrent modification";
    document.getElementById("editor-reload").hidden = state.editorId === null ||
      (!state.stale && state.editorReady);
    if (state.stale) {
      setText("editor-error-text", `${error.message} Reload replaces your unsaved changes.`);
    }
  }
  focusFirstError(form);
}

function updatePagination() {
  document.getElementById("previous-page").disabled =
    state.loading || state.deleting || state.page === 0;
  document.getElementById("next-page").disabled =
    state.loading || state.deleting || state.page + 1 >= state.totalPages;
}

function setLoading(loading) {
  state.loading = loading;
  document.getElementById("table-region").setAttribute("aria-busy", String(loading));
  updatePagination();
}

function searchParams() {
  const params = new URLSearchParams();
  for (const name of ["q", "location", "minExperience", "maxExperience", "maxNoticePeriod", "skillMatch", "sort", "size"]) {
    const value = field(filters, name).value;
    if (value !== "") params.set(name, value);
  }
  for (const checkbox of selectedStatuses()) params.append("status", checkbox.value);
  const skills = splitSkills(field(filters, "skills").value);
  if (skills.length) params.set("skills", skills.join(","));
  params.set("page", state.page);
  return params;
}

function cell(row, value) {
  const element = document.createElement("td");
  // API values are text only, including names containing HTML-looking characters.
  element.textContent = value === null || value === undefined || value === "" ? "-" : String(value);
  row.append(element);
  return element;
}

function renderCandidates(candidates) {
  rows.replaceChildren();
  for (const candidate of candidates) {
    const row = document.createElement("tr");
    const name = `${candidate.firstName} ${candidate.lastName}`;
    cell(row, name);
    cell(row, candidate.email);
    cell(row, [candidate.currentTitle, candidate.currentCompany].filter(Boolean).join(" / "));
    cell(row, candidate.location);
    cell(row, candidate.experienceYears);
    const status = document.createElement("span");
    status.className = "status-badge";
    status.textContent = candidate.status;
    cell(row, "").replaceChildren(status);
    cell(row, candidate.skills.join(", "));
    const actions = document.createElement("div");
    actions.className = "row-actions";
    for (const [label, action] of [
      ["Edit", () => openEditor(candidate.id)], ["Delete", () => deleteCandidate(candidate)]
    ]) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = label === "Delete" ? "danger" : "secondary";
      button.textContent = label;
      button.setAttribute("aria-label", `${label} ${name}`);
      button.disabled = state.deleting;
      button.addEventListener("click", action);
      actions.append(button);
    }
    const actionCell = document.createElement("td");
    actionCell.append(actions);
    row.append(actionCell);
    rows.append(row);
  }
}

async function loadCandidates(resetPage = false) {
  clearTimeout(searchTimer);
  updateFilterCount();
  // A slow response for an older search must not replace newer results.
  searchController?.abort();
  const sequence = ++searchSequence;
  if (resetPage) state.page = 0;
  document.getElementById("list-error").hidden = true;
  rows.replaceChildren();
  state.totalPages = 0;
  setText("page-info", "");
  if (!validate(filters)) {
    setText("list-message", "Correct the highlighted filters to search.");
    setLoading(false);
    focusFirstError(filters);
    return;
  }
  searchController = new AbortController();
  setLoading(true);
  setText("list-message", "Loading candidates...");
  try {
    const page = await request(`?${searchParams()}`, { signal: searchController.signal });
    if (sequence !== searchSequence) return;
    // Removing the final row on the final page can make that page disappear.
    if (page.totalPages > 0 && state.page >= page.totalPages) {
      state.page = page.totalPages - 1;
      await loadCandidates();
      return;
    }
    state.page = page.totalPages === 0 ? 0 : page.page;
    state.totalPages = page.totalPages;
    renderCandidates(page.content);
    setText("list-message", page.content.length ? "" : "No candidates match these filters.");
    setText("page-info", `Page ${page.totalPages ? page.page + 1 : 0} of ${page.totalPages}, ${page.totalElements} results`);
  } catch (error) {
    if (error.name !== "AbortError" && sequence === searchSequence) {
      setText("list-message", "Candidates could not be loaded.");
      showError("list", error, filters);
    }
  } finally {
    if (sequence === searchSequence) setLoading(false);
  }
}

function editorBusy(busy) {
  document.getElementById("editor-fields").disabled = busy || !state.editorReady;
  document.getElementById("save-candidate").disabled = busy || !state.editorReady || state.stale;
  document.getElementById("cancel-edit").disabled = state.saving;
  document.getElementById("editor-reload").disabled = busy;
}

async function openEditor(id = null) {
  editorController?.abort();
  const sequence = ++editorSequence;
  editor.reset();
  clearErrors(editor);
  state.editorId = id;
  state.version = null;
  state.stale = false;
  state.editorReady = id === null;
  document.getElementById("editor-error").hidden = true;
  document.getElementById("editor-reload").hidden = true;
  setText("dialog-title", id === null ? "New candidate" : "Edit candidate");
  setText("editor-message", id === null ? "" : "Loading candidate...");
  editorBusy(id !== null);
  if (!dialog.open) dialog.showModal();
  if (id === null) {
    field(editor, "firstName").focus();
    return;
  }
  editorController = new AbortController();
  try {
    const candidate = await request(`/${id}`, { signal: editorController.signal });
    if (sequence !== editorSequence || !dialog.open) return;
    for (const input of editor.querySelectorAll("input, select, textarea")) {
      input.value = input.name === "skills" ? candidate.skills.join(", ") : candidate[input.name] ?? "";
    }
    // Keep the exact version from GET; PUT sends it back for optimistic locking.
    state.version = candidate.version;
    state.editorReady = true;
    setText("editor-message", "");
  } catch (error) {
    if (error.name !== "AbortError" && sequence === editorSequence) {
      setText("editor-message", "");
      showError("editor", error, editor);
    }
  } finally {
    if (sequence === editorSequence) {
      editorBusy(false);
      if (state.editorReady) field(editor, "firstName").focus();
    }
  }
}

function candidatePayload() {
  const payload = {};
  for (const name of ["firstName", "lastName", "email", "phone", "currentTitle", "currentCompany", "location", "notes", "status"]) {
    const value = field(editor, name).value;
    payload[name] = value === "" ? null : value;
  }
  for (const name of ["experienceYears", "noticePeriodDays"]) {
    const value = field(editor, name).value;
    payload[name] = value === "" ? null : Number(value);
  }
  payload.skills = splitSkills(field(editor, "skills").value);
  if (state.editorId !== null) payload.version = state.version;
  return payload;
}

async function saveCandidate(event) {
  event.preventDefault();
  if (state.saving || !state.editorReady || state.stale) return;
  // The request record strips surrounding email whitespace before validation too.
  field(editor, "email").value = field(editor, "email").value.trim();
  document.getElementById("editor-error").hidden = true;
  if (!validate(editor)) {
    editor.querySelector('[aria-invalid="true"]').focus();
    return;
  }
  const id = state.editorId;
  state.saving = true;
  editorBusy(true);
  setText("editor-message", "Saving candidate...");
  try {
    await request(id === null ? "" : `/${id}`, {
      method: id === null ? "POST" : "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(candidatePayload())
    });
    dialog.close();
    await loadCandidates();
  } catch (error) {
    setText("editor-message", "");
    showError("editor", error, editor);
  } finally {
    state.saving = false;
    editorBusy(false);
    if (dialog.open) editor.querySelector('[aria-invalid="true"]')?.focus();
  }
}

async function deleteCandidate(candidate) {
  if (state.deleting || !window.confirm(`Delete ${candidate.firstName} ${candidate.lastName}? This soft-deletes the candidate.`)) return;
  state.deleting = true;
  updatePagination();
  for (const button of rows.querySelectorAll("button")) button.disabled = true;
  document.getElementById("list-error").hidden = true;
  setText("list-message", "Deleting candidate...");
  try {
    await request(`/${candidate.id}`, { method: "DELETE" });
    await loadCandidates();
  } catch (error) {
    setText("list-message", "The candidate could not be deleted.");
    showError("list", error);
  } finally {
    state.deleting = false;
    for (const button of rows.querySelectorAll("button")) button.disabled = false;
    updatePagination();
  }
}

field(filters, "q").addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => loadCandidates(true), 300);
});
filters.addEventListener("change", event => {
  if (event.target.name !== "q") loadCandidates(true);
});
filters.addEventListener("input", updateFilterCount);
filters.addEventListener("submit", event => {
  event.preventDefault();
  loadCandidates(true);
});
filters.addEventListener("reset", () => {
  // The reset event fires before the browser restores the form's default values.
  setTimeout(() => loadCandidates(true), 0);
});
document.getElementById("previous-page").addEventListener("click", () => {
  state.page--;
  loadCandidates();
});
document.getElementById("next-page").addEventListener("click", () => {
  state.page++;
  loadCandidates();
});
document.getElementById("list-reload").addEventListener("click", () => loadCandidates());
document.getElementById("create-candidate").addEventListener("click", () => openEditor());
document.getElementById("cancel-edit").addEventListener("click", () => dialog.close());
document.getElementById("editor-reload").addEventListener("click", () => {
  if (!state.stale || window.confirm("Reload the latest candidate and discard your unsaved changes?")) {
    openEditor(state.editorId);
  }
});
editor.addEventListener("submit", saveCandidate);
dialog.addEventListener("cancel", event => {
  if (state.saving) event.preventDefault();
});
dialog.addEventListener("close", () => {
  editorController?.abort();
  editorSequence++;
});

loadCandidates();
