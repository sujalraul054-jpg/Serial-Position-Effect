"use strict";

// In local development this page comes from VS Code Live Server (ports 55xx), so the
// backend is called directly. Anywhere else the backend itself serves this page, so the
// API is on the same origin.
const isLiveServer = ["localhost", "127.0.0.1"].includes(window.location.hostname) && /^55\d\d$/.test(window.location.port);
const API_BASE = isLiveServer ? "http://localhost:8080/api/experiment" : "/api/experiment";
const URLS = {
  plan: `${API_BASE}/plan`,
  sessions: `${API_BASE}/sessions`,
  aggregate: `${API_BASE}/aggregate`,
  export: `${API_BASE}/export`,
};

const THEME_KEY = "spe-theme";
const INTERVAL_KEY = "spe-interval-seconds";
const LIST_SIZE = 10;
const MAX_RECALLED_WORDS = 200;
const MAX_WORD_LENGTH = 40;
const EXPORT_FILENAME = "memory-experiment-results.xlsx";
const TYPE_LABELS = { NORMAL: "Normal", SEMANTIC: "Semantic" };

const $ = (id) => document.getElementById(id);

const screens = Object.fromEntries(
  ["landing", "level", "test", "recall", "results"].map((name) => [name, $(`screen-${name}`)])
);

// State of the session in progress.
const session = { plan: null, levelIndex: 0, recalls: [] };
let presentationTimer = null;
let chart = null;
let graphRequestId = 0;
// Participant whose results are currently on the results page.
let shownParticipantId = null;

/* ---------- Generic helpers ---------- */

function showScreen(name) {
  Object.values(screens).forEach((screen) => screen.classList.remove("is-active"));
  screens[name].classList.add("is-active");
  // No controls are shown while words are being presented.
  $("app-bar").classList.toggle("is-hidden", name === "test");
}

function escapeHtml(value) {
  const entities = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  return String(value).replace(/[&<>"']/g, (char) => entities[char]);
}

function formatPercent(value) {
  return `${Number(value).toFixed(1)}%`;
}

function formatSeconds(value) {
  return `${Number(value).toFixed(1)} s`;
}

function showMessage(element, text) {
  element.textContent = text;
  element.hidden = false;
}

function hideMessage(element) {
  element.hidden = true;
}

function setBusy(button, busy, busyLabel) {
  if (busy) {
    button.dataset.label = button.textContent;
    button.textContent = busyLabel;
  } else if (button.dataset.label !== undefined) {
    button.textContent = button.dataset.label;
    delete button.dataset.label;
  }
  button.disabled = busy;
}

async function request(url, options) {
  let response;
  try {
    response = await fetch(url, options);
  } catch {
    throw new Error("Cannot reach the server. Check that the backend is running and that you are online.");
  }
  if (!response.ok) {
    let message = `The server responded with status ${response.status}.`;
    try {
      const body = await response.json();
      if (body && body.message) {
        message = body.message;
      }
    } catch {
      // Response had no JSON body; keep the generic message.
    }
    throw new Error(message);
  }
  return response;
}

async function requestJson(url, options) {
  return (await request(url, options)).json();
}

/* ---------- Settings: theme and word interval ---------- */

function getThemePreference() {
  return localStorage.getItem(THEME_KEY) || "system";
}

function applyTheme() {
  const preference = getThemePreference();
  const resolved = preference === "system"
    ? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")
    : preference;
  document.documentElement.setAttribute("data-theme", resolved);
  document.querySelectorAll("#theme-picker .segmented__option").forEach((option) => {
    option.classList.toggle("is-active", option.dataset.themeChoice === preference);
  });
}

function getIntervalSeconds() {
  const stored = parseFloat(localStorage.getItem(INTERVAL_KEY));
  return stored >= 1 && stored <= 3 ? stored : 1;
}

function renderIntervalSetting() {
  const seconds = getIntervalSeconds();
  $("interval-slider").value = String(seconds);
  $("interval-value").textContent = formatSeconds(seconds);
}

function openSettings() {
  applyTheme();
  renderIntervalSetting();
  hideMessage($("settings-status"));
  $("settings-overlay").hidden = false;
}

function closeSettings() {
  $("settings-overlay").hidden = true;
}

/* ---------- Session flow ---------- */

async function startSession() {
  const button = $("btn-start");
  const errorElement = $("landing-error");
  hideMessage(errorElement);
  setBusy(button, true, "Preparing...");
  try {
    session.plan = await requestJson(URLS.plan);
    session.levelIndex = 0;
    session.recalls = [];
    showLevelIntro();
  } catch (error) {
    showMessage(errorElement, error.message);
  } finally {
    setBusy(button, false);
  }
}

function showLevelIntro() {
  const level = session.levelIndex + 1;
  const total = session.plan.tests.length;
  $("level-title").textContent = `Level ${level} of ${total}`;

  const note = $("level-note");
  note.textContent = level > 1 ? `Level ${level - 1} is complete.` : "";
  note.hidden = level === 1;

  $("level-instructions").textContent =
    `${LIST_SIZE} words will appear one at a time, each for ${formatSeconds(getIntervalSeconds())}. ` +
    "Read each word and try to remember as many as you can. You will be asked to type them afterwards.";
  showScreen("level");
}

function beginLevel() {
  const words = session.plan.tests[session.levelIndex].words;
  const intervalMs = getIntervalSeconds() * 1000;
  let index = 0;

  showScreen("test");
  renderWord(words, index);

  presentationTimer = setInterval(() => {
    index += 1;
    if (index >= words.length) {
      clearInterval(presentationTimer);
      presentationTimer = null;
      openRecall();
      return;
    }
    renderWord(words, index);
  }, intervalMs);
}

function renderWord(words, index) {
  const display = $("word-display");
  display.textContent = words[index];
  // Restart the entrance animation for every word.
  display.classList.remove("is-animating");
  void display.offsetWidth;
  display.classList.add("is-animating");

  const percent = ((index + 1) / words.length) * 100;
  $("progress-display").innerHTML =
    `<span class="stimulus-progress-label">Word ${index + 1} of ${words.length}</span>` +
    `<span class="stimulus-progress-track"><span class="stimulus-progress-fill" style="width: ${percent}%"></span></span>`;
}

function openRecall() {
  const level = session.levelIndex + 1;
  const isLast = level === session.plan.tests.length;
  $("recall-title").textContent = `Level ${level}: recall`;
  $("btn-submit-recall").textContent = isLast ? "Submit" : `Continue to level ${level + 1}`;
  $("recall-input").value = "";
  hideMessage($("recall-error"));
  showScreen("recall");
  $("recall-input").focus();
}

function parseWords(raw) {
  return raw.split(/[\s,]+/).filter((word) => word.length > 0);
}

function validateRecall(words) {
  if (words.length === 0) {
    return "Enter at least one word before continuing.";
  }
  if (words.length > MAX_RECALLED_WORDS) {
    return `Enter at most ${MAX_RECALLED_WORDS} words.`;
  }
  if (words.some((word) => word.length > MAX_WORD_LENGTH)) {
    return `Each word must be at most ${MAX_WORD_LENGTH} characters. Check that words are separated by spaces or commas.`;
  }
  return null;
}

async function submitRecall() {
  const words = parseWords($("recall-input").value);
  const problem = validateRecall(words);
  if (problem) {
    showMessage($("recall-error"), problem);
    return;
  }
  hideMessage($("recall-error"));
  session.recalls[session.levelIndex] = words;

  if (session.levelIndex < session.plan.tests.length - 1) {
    session.levelIndex += 1;
    showLevelIntro();
    return;
  }

  const button = $("btn-submit-recall");
  setBusy(button, true, "Saving...");
  try {
    const payload = {
      tests: session.plan.tests.map((test, i) => ({
        testType: test.testType,
        words: test.words,
        recalledWords: session.recalls[i],
      })),
    };
    const result = await requestJson(URLS.sessions, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    showResults(result);
  } catch (error) {
    showMessage($("recall-error"), error.message);
  } finally {
    setBusy(button, false);
  }
}

/* ---------- Results ---------- */

function markCell(recalled) {
  return `<td class="${recalled ? "hit" : "miss"}">${recalled ? "Yes" : "No"}</td>`;
}

function duplicateNote(label, test) {
  if (test.duplicateWords.length === 0) {
    return "";
  }
  const words = test.duplicateWords.map(escapeHtml).join(", ");
  return `<li>${label} list: entered more than once and counted once: ${words}.</li>`;
}

function improvementHtml(result, normal, semantic) {
  const formula = "((Semantic Total Recall \u2212 Normal Total Recall) / Normal Total Recall) \u00d7 100";
  const improvement = result.recallImprovement;

  if (!Number.isFinite(improvement)) {
    return `<div class="improvement">
      <h2>Total Recall Improvement</h2>
      <p class="improvement__value">N/A</p>
      <p class="improvement__formula">${formula}. Normal recall is 0, so it cannot be calculated.</p>
    </div>`;
  }

  const value = `${improvement > 0 ? "+" : ""}${improvement.toFixed(1)}%`;
  const working = `((${semantic.totalRecalled} \u2212 ${normal.totalRecalled}) / ${normal.totalRecalled}) \u00d7 100`;
  return `<div class="improvement">
    <h2>Total Recall Improvement</h2>
      <p class="improvement__value">${value}</p>
      <p class="improvement__formula">${formula}<br>= ${working} = ${value}</p>
    </div>`;
}

function middleImprovementHtml(result, normal, semantic) {
  const formula = "((Semantic Middle Recall \u2212 Normal Middle Recall) / Normal Middle Recall) \u00d7 100";
  const improvement = result.middleRecallImprovement;
  const normalPercent = formatPercent(normal.middleScore);
  const semanticPercent = formatPercent(semantic.middleScore);
  const normalRecall = `${normal.middleRecalled}/4 (${normalPercent})`;
  const semanticRecall = `${semantic.middleRecalled}/4 (${semanticPercent})`;

  if (!Number.isFinite(improvement)) {
    return `<div class="improvement">
      <h2>Middle Position Recall Improvement</h2>
      <p class="improvement__value">N/A</p>
      <p class="improvement__formula">Normal middle recall: ${normalRecall}</p>
      <p class="improvement__formula">Semantic middle recall: ${semanticRecall}</p>
      <p class="improvement__formula">Formula: ${formula}. Normal middle recall is 0, so it cannot be calculated.</p>
    </div>`;
  }

  const value = `${improvement > 0 ? "+" : ""}${improvement.toFixed(1)}%`;
  const working = `((${semanticPercent} \u2212 ${normalPercent}) / ${normalPercent}) \u00d7 100`;
  return `<div class="improvement">
      <h2>Middle Position Recall Improvement</h2>
      <p class="improvement__value">${value}</p>
      <p class="improvement__formula">Normal middle recall: ${normalRecall}</p>
      <p class="improvement__formula">Semantic middle recall: ${semanticRecall}</p>
      <p class="improvement__formula">Formula: ${formula}<br>= ${working} = ${value}</p>
    </div>`;
}

function resultsHtml(result) {
  const normal = result.tests.find((test) => test.testType === "NORMAL");
  const semantic = result.tests.find((test) => test.testType === "SEMANTIC");

  const measures = [
    ["Words recalled", (test) => `${test.totalRecalled} / ${test.totalWords}`],
    ["Overall recall", (test) => formatPercent(test.recallPercentage)],
    ["Primacy (positions 1\u20133)", (test) => formatPercent(test.primacyScore)],
    ["Middle recall", (test) => `${test.middleRecalled}/4 (${formatPercent(test.middleScore)})`],
    ["Recency (positions 8\u201310)", (test) => formatPercent(test.recencyScore)],
  ];
  const summaryRows = measures
    .map(([label, value]) => `<tr><th scope="row">${label}</th><td>${value(normal)}</td><td>${value(semantic)}</td></tr>`)
    .join("");

  const positionRows = normal.positionResults
    .map((normalPosition, i) => {
      const semanticPosition = semantic.positionResults[i];
      const related = i >= 3 && i < 7;
      return `<tr${related ? ' class="row-related"' : ""}>` +
        `<td>${normalPosition.position}</td>` +
        `<td>${escapeHtml(normalPosition.word)}</td>${markCell(normalPosition.recalled)}` +
        `<td>${escapeHtml(semanticPosition.word)}</td>${markCell(semanticPosition.recalled)}` +
        "</tr>";
    })
    .join("");

  const notes = duplicateNote("Normal", normal) + duplicateNote("Semantic", semantic);

  return `
    <h2 class="results-heading">Comparison</h2>
    <div class="table-scroll">
      <table class="data-table">
        <thead><tr><th scope="col">Measure</th><th scope="col">Normal list</th><th scope="col">Semantic list</th></tr></thead>
        <tbody>${summaryRows}</tbody>
      </table>
    </div>
    ${improvementHtml(result, normal, semantic)}
    ${middleImprovementHtml(result, normal, semantic)}
    <h2 class="results-heading">Recall by position</h2>
    <p class="table-caption">Normal list: ten unrelated words. Semantic list: unrelated words at the start and end, and related words in the middle (shaded rows, positions 4\u20137).</p>
    <div class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th scope="col">Position</th>
            <th scope="col">Normal word</th><th scope="col">Recalled</th>
            <th scope="col">Semantic word</th><th scope="col">Recalled</th>
          </tr>
        </thead>
        <tbody>${positionRows}</tbody>
      </table>
    </div>
    ${notes ? `<ul class="notes">${notes}</ul>` : ""}`;
}

function showResults(result) {
  shownParticipantId = result.participantId;
  const order = `${TYPE_LABELS[result.firstTestType]} list first`;
  $("results-title").textContent = `Results for ${result.participantId}`;
  $("results-subtitle").textContent = `Presentation order: ${order}. This session has been recorded.`;
  $("results-body").innerHTML = resultsHtml(result);
  hideMessage($("results-error"));
  showScreen("results");
  loadGraph();
}

/* ---------- Spreadsheet download ---------- */

async function downloadSpreadsheet(button, messageElement) {
  hideMessage(messageElement);
  setBusy(button, true, "Preparing...");
  try {
    const blob = await (await request(URLS.export)).blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = EXPORT_FILENAME;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch (error) {
    showMessage(messageElement, error.message);
  } finally {
    setBusy(button, false);
  }
}

/* ---------- Recall curves (results page) ---------- */

// Draws the serial position curves for all recorded participants. A failure here
// only affects the graph; the results above it stay as they are.
async function loadGraph() {
  // Only the most recent request may update the graph, so a slow earlier answer
  // cannot overwrite a newer one.
  const requestId = ++graphRequestId;
  try {
    const aggregate = await requestJson(URLS.aggregate);
    if (requestId === graphRequestId) {
      renderGraph(aggregate);
    }
  } catch (error) {
    if (requestId === graphRequestId) {
      clearChart();
      $("spe-chart").parentElement.hidden = true;
      $("graph-note").textContent = `The graph could not be loaded. ${error.message}`;
    }
  }
}

function clearChart() {
  if (chart) {
    chart.destroy();
    chart = null;
  }
}

function renderGraph(aggregate) {
  const hasData = aggregate.participants > 0;
  $("spe-chart").parentElement.hidden = !hasData;
  $("graph-note").textContent = hasData
    ? `Percentage of participants who recalled the word at each position. Based on ${aggregate.participants} ` +
      `participant${aggregate.participants === 1 ? "" : "s"}.`
    : "No sessions have been recorded yet.";

  clearChart();
  if (!hasData) {
    return;
  }

  const styles = getComputedStyle(document.documentElement);
  const color = (name) => styles.getPropertyValue(name).trim();
  Chart.defaults.color = color("--ink-soft");
  Chart.defaults.borderColor = color("--line");

  const positions = Array.from({ length: LIST_SIZE }, (_, i) => i + 1);
  const series = (curve) => positions.map((position) => {
    const point = curve.find((entry) => entry.position === position);
    return point ? point.recallPercentage : null;
  });
  const dataset = (label, curve, lineColor) => ({
    label,
    data: series(curve),
    borderColor: lineColor,
    backgroundColor: lineColor,
    borderWidth: 2,
    pointRadius: 4,
    pointHoverRadius: 6,
    tension: 0,
  });

  chart = new Chart($("spe-chart").getContext("2d"), {
    type: "line",
    data: {
      labels: positions,
      datasets: [
        dataset("Normal list", aggregate.normal, color("--teal")),
        dataset("Semantic list", aggregate.semantic, color("--amber")),
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: "bottom" },
        tooltip: { callbacks: { label: (item) => `${item.dataset.label}: ${item.parsed.y}% recalled` } },
      },
      scales: {
        x: { title: { display: true, text: "Position in word list" } },
        y: { title: { display: true, text: "Recall (%)" }, min: 0, max: 100 },
      },
    },
  });
}

/* ---------- Delete data ---------- */

// Runs a deletion from Settings, reports the outcome there, then redraws the graph
// if the results page is open. The spreadsheet is built from the database on every
// download, so it needs no refresh.
async function runDeletion(deleteAction) {
  const status = $("settings-status");
  let message;
  try {
    message = await deleteAction();
  } catch (error) {
    showMessage(status, error.message);
    return;
  }
  showMessage(status, message);
  if (screens.results.classList.contains("is-active")) {
    await loadGraph();
  }
}

// participantId is null when every session was deleted.
function markResultsDeleted(participantId) {
  if (participantId === null || participantId === shownParticipantId) {
    $("results-subtitle").textContent = "This session was deleted and is no longer recorded.";
    shownParticipantId = null;
  }
}

async function deleteLatestSession() {
  const question = "Delete the most recent session? It is removed from the spreadsheet and the graph. This cannot be undone.";
  if (!window.confirm(question)) {
    return;
  }
  await runDeletion(async () => {
    const deleted = await requestJson(`${URLS.sessions}/latest`, { method: "DELETE" });
    markResultsDeleted(deleted.participantId);
    return `${deleted.participantId} was deleted. It no longer appears in the spreadsheet or the graph.`;
  });
}

async function deleteAllSessions() {
  if (!window.confirm("Delete every recorded session? This cannot be undone.")) {
    return;
  }
  await runDeletion(async () => {
    await request(URLS.sessions, { method: "DELETE" });
    markResultsDeleted(null);
    return "All recorded sessions were deleted.";
  });
}

/* ---------- Event wiring ---------- */

$("btn-start").addEventListener("click", startSession);
$("btn-begin-level").addEventListener("click", beginLevel);
$("btn-submit-recall").addEventListener("click", submitRecall);

$("btn-results-download").addEventListener("click", (event) => downloadSpreadsheet(event.currentTarget, $("results-error")));

$("btn-settings").addEventListener("click", openSettings);
$("btn-settings-close").addEventListener("click", closeSettings);
$("settings-overlay").addEventListener("click", (event) => {
  if (event.target === $("settings-overlay")) {
    closeSettings();
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !$("settings-overlay").hidden) {
    closeSettings();
  }
});
$("theme-picker").addEventListener("click", (event) => {
  const option = event.target.closest(".segmented__option");
  if (option) {
    localStorage.setItem(THEME_KEY, option.dataset.themeChoice);
    applyTheme();
  }
});
$("interval-slider").addEventListener("input", (event) => {
  localStorage.setItem(INTERVAL_KEY, event.target.value);
  renderIntervalSetting();
});
$("btn-settings-download").addEventListener("click", (event) => downloadSpreadsheet(event.currentTarget, $("settings-status")));
$("btn-settings-delete-latest").addEventListener("click", deleteLatestSession);
$("btn-settings-clear").addEventListener("click", deleteAllSessions);

window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
  if (getThemePreference() === "system") {
    applyTheme();
  }
});

applyTheme();
renderIntervalSetting();
