"use strict";

// Run with: node --test src\test\browser\candidate-ui.test.js
// Set CHROME_PATH if Chrome or Edge is installed in a nonstandard location.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { execFile } = require("node:child_process");
const { mkdtemp, readFile, rm } = require("node:fs/promises");
const { existsSync } = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const { promisify } = require("node:util");

const staticDir = path.resolve(__dirname, "../../main/resources/static");
const browser = [
  process.env.CHROME_PATH,
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium"
].find(candidate => candidate && existsSync(candidate));

// Replace only network access in the browser; the actual page and script are unchanged.
const browserChecks = `
<script>
window.requests = [];
window.fetch = async (url) => {
  window.requests.push(String(url));
  if (String(url).includes("q=statuserror")) {
    return new Response(JSON.stringify({
      detail: "Invalid filters",
      errors: [{ field: "status", message: "Invalid status" }]
    }), { status: 400, headers: { "Content-Type": "application/problem+json" } });
  }
  if (String(url).includes("q=server")) {
    return new Response(JSON.stringify({
      detail: "Invalid filters",
      errors: [{ field: "location", message: "Invalid location" }]
    }), { status: 400, headers: { "Content-Type": "application/problem+json" } });
  }
  return new Response(JSON.stringify({
    content: [{ id: 1, firstName: "<img>", lastName: "Example",
      email: "sample@example.com", currentTitle: "Developer",
      currentCompany: "Example", location: "Pune", experienceYears: 2,
      status: "NEW", skills: ["java"] }],
    page: 0, size: 20, totalElements: 1, totalPages: 1
  }), { headers: { "Content-Type": "application/json" } });
};
window.addEventListener("load", async () => {
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  const el = id => document.getElementById(id);
  const check = (condition, message) => { if (!condition) throw new Error(message); };
  try {
    await wait(100);
    check(!el("advanced-filters").open, "Filters should initially be collapsed");
    check(el("filter-count").textContent === "No filters active", "Initial filter count");
    check(el("candidate-rows").textContent.includes("<img> Example"), "Candidate is rendered");
    check(!el("candidate-rows").querySelector("img"), "Candidate data must remain text");
    check(el("candidate-rows").querySelector(".status-badge").textContent === "NEW", "Status badge");
    check(document.documentElement.scrollWidth <= window.innerWidth, "Page fits the viewport");
    const searchTop = el("q").getBoundingClientRect().top;
    const sortTop = el("sort").getBoundingClientRect().top;
    check(window.innerWidth <= 600 ? sortTop > searchTop : sortTop === searchTop,
      "Search controls adapt to the viewport");

    el("advanced-filters").querySelector("summary").click();
    check(el("advanced-filters").open, "Filters open on click");
    el("filter-location").value = "Pune";
    el("filter-location").dispatchEvent(new Event("input", { bubbles: true }));
    el("filter-location").dispatchEvent(new Event("change", { bubbles: true }));
    await wait(100);
    check(el("filter-count").textContent === "1 active", "Location counted");
    check(window.requests.at(-1).includes("location=Pune"), "Location reaches the API");
    const statuses = [...el("filter-status").querySelectorAll('input[name="status"]')];
    check(statuses.length === 6 && statuses.every(input => input.type === "checkbox"), "All statuses use checkboxes");
    statuses[0].focus();
    check(document.activeElement === statuses[0], "Status checkbox is keyboard-focusable");
    statuses[0].closest("label").click();
    statuses[1].closest("label").click();
    await wait(100);
    check(statuses[0].checked && statuses[1].checked, "Multiple statuses selected");
    check(el("filter-count").textContent === "2 active", "Statuses count as one filter");
    const statusParams = () => new URL(window.requests.at(-1), location.href).searchParams.getAll("status");
    check(statusParams().join(",") === "NEW,SCREENING", "Selected statuses reach the API");
    statuses[0].click();
    await wait(100);
    check(statusParams().join(",") === "SCREENING", "Deselecting a status updates the API request");

    el("minExperience").value = "-1";
    el("advanced-filters").open = false;
    el("filters").dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    check(el("advanced-filters").open, "Invalid filter opens collapsed section");
    check(document.activeElement === el("minExperience"), "Invalid filter receives focus");

    el("filters").querySelector('[type="reset"]').click();
    await wait(100);
    check(el("filter-count").textContent === "No filters active", "Reset clears count");
    check(!el("filter-location").value && statuses.every(input => !input.checked), "Reset clears filters");
    check(statusParams().length === 0, "Reset removes status parameters");

    el("advanced-filters").open = false;
    el("q").value = "server";
    el("q").dispatchEvent(new Event("input", { bubbles: true }));
    await wait(400);
    check(el("advanced-filters").open, "Server field error opens collapsed section");
    check(document.activeElement === el("filter-location"), "Server error receives focus");
    check(el("filter-location-error").textContent === "Invalid location", "Server error shown");
    el("advanced-filters").open = false;
    el("q").value = "statuserror";
    el("q").dispatchEvent(new Event("input", { bubbles: true }));
    await wait(400);
    check(el("advanced-filters").open, "Status error opens collapsed section");
    check(document.activeElement === statuses[0], "Status error focuses the first checkbox");
    check(statuses.every(input => input.getAttribute("aria-invalid") === "true"), "Status checkboxes marked invalid");
    check(el("filter-status-error").textContent === "Invalid status", "Status error shown");

    el("create-candidate").click();
    check(el("candidate-dialog").open && el("status").options.length === 6, "Editor status remains a select");
    el("candidate-dialog").close();
    document.body.dataset.testResult = "passed";
  } catch (error) {
    document.body.dataset.testResult = "failed: " + error.message;
  }
});
</script>`;

test("candidate filters remain usable in the redesigned UI", async () => {
  assert.ok(browser, "Chrome or Edge is required; set CHROME_PATH to its executable");
  const profile = await mkdtemp(path.join(os.tmpdir(), "candidate-ui-test-"));
  const server = http.createServer(async (req, res) => {
    try {
      const file = req.url === "/" ? "index.html" : req.url.slice(1);
      if (!["index.html", "app.js", "styles.css"].includes(file)) {
        res.writeHead(404).end();
        return;
      }
      let contents = await readFile(path.join(staticDir, file), "utf8");
      if (file === "index.html") contents = contents.replace("</head>", `${browserChecks}</head>`);
      res.setHeader("Content-Type", file === "app.js" ? "text/javascript" :
        file === "styles.css" ? "text/css" : "text/html");
      res.end(contents);
    } catch (error) {
      res.writeHead(500).end(error.message);
    }
  });
  try {
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    const url = `http://127.0.0.1:${server.address().port}/`;
    for (const width of [1440, 500]) {
      const { stdout } = await promisify(execFile)(browser, [
        "--headless=new", "--disable-gpu", "--no-first-run",
        `--user-data-dir=${profile}`, `--window-size=${width},900`,
        "--virtual-time-budget=5000", "--dump-dom", url
      ], { timeout: 30000, maxBuffer: 1024 * 1024 });
      const result = stdout.match(/data-test-result="([^"]+)"/);
      assert.ok(result, `Browser checks did not finish at ${width}px`);
      assert.equal(result[1], "passed", `Browser checks at ${width}px`);
    }
  } finally {
    await new Promise(resolve => server.close(resolve));
    await rm(profile, { recursive: true, force: true });
  }
});
