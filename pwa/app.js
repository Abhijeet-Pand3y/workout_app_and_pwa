'use strict';
/*
 * Workout PWA. Same data format as the Android app, so backups and shared
 * plan files work in both directions.
 */

const KEY = 'workout-data-v1';
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];

// ================================================================ data

const uid = () =>
  (self.crypto && crypto.randomUUID) ? crypto.randomUUID()
    : 'id-' + Date.now().toString(36) + Math.random().toString(36).slice(2);

const emptyData = () => ({ version: 1, plans: [], activePlanId: null, sessions: [], current: null, weightUnit: 'kg', timedExercises: [] });

// Exercise kinds: 'strength' = weight × reps, 'timed' = duration (+ optional distance), e.g. plank or cardio.
const TIMED = 'timed';

function normSet(x) {
  const s = {
    id: x.id || uid(),
    w: x.w != null ? +x.w : null,
    r: x.r | 0,
    p: x.p | 0,
    drops: (x.drops || []).map((d) => ({ w: d.w != null ? +d.w : null, r: d.r | 0 })),
  };
  if (x.t) s.t = x.t | 0;
  if (x.d != null) s.d = +x.d;
  return s;
}
function normSession(s) {
  return {
    id: s.id || uid(),
    planId: s.planId ?? null,
    planName: s.planName || '',
    dayId: s.dayId ?? null,
    dayName: s.dayName || 'Workout',
    startedAt: +s.startedAt || Date.now(),
    finishedAt: s.finishedAt != null ? +s.finishedAt : null,
    exercises: (s.exercises || []).map((e) => {
      const ex = { id: e.id || uid(), name: String(e.name || ''), sets: (e.sets || []).map(normSet) };
      if (e.kind === TIMED) ex.kind = TIMED;
      return ex;
    }),
  };
}
function normalize(d) {
  const o = Object.assign(emptyData(), d || {});
  o.plans = (o.plans || []).map((p) => ({
    id: p.id || uid(),
    name: p.name || 'Plan',
    nextDayIndex: p.nextDayIndex | 0,
    days: (p.days || []).map((x) => ({ id: x.id || uid(), name: x.name || 'Day', exercises: (x.exercises || []).map(String) })),
  }));
  o.sessions = (o.sessions || []).map(normSession);
  o.current = o.current ? normSession(o.current) : null;
  o.weightUnit = o.weightUnit === 'lb' ? 'lb' : 'kg';
  o.timedExercises = [...new Set((o.timedExercises || []).map((n) => String(n).trim().toLowerCase()))];
  o.version = 1;
  return o;
}

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? normalize(JSON.parse(raw)) : emptyData();
  } catch (e) {
    return emptyData();
  }
}

let data = load();

function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(data));
  } catch (e) {
    toast('Could not save. Storage may be full.');
  }
}
function commit() { save(); render(); }

const activePlan = () => data.plans.find((p) => p.id === data.activePlanId) || data.plans[0] || null;
const findPlan = (id) => data.plans.find((p) => p.id === id);
const findDay = (plan, id) => plan && plan.days.find((d) => d.id === id);
const getSession = (key) => key === 'current' ? data.current : data.sessions.find((s) => s.id === key);
const setCount = (s) => s.exercises.reduce((a, e) => a + e.sets.length, 0);
const isTimedName = (name) => data.timedExercises.includes(String(name).trim().toLowerCase());
const isTimedEx = (ex) => ex.kind === TIMED;
const distUnit = () => (data.weightUnit === 'lb' ? 'mi' : 'km');

/** Remembers whether an exercise is timed (plank, cardio) or strength. */
function setTimed(name, timed) {
  const k = String(name).trim().toLowerCase();
  const has = data.timedExercises.includes(k);
  if (timed && !has) data.timedExercises.push(k);
  if (!timed && has) data.timedExercises = data.timedExercises.filter((x) => x !== k);
}
const newExercise = (name, timed) => (timed ? { id: uid(), name, sets: [], kind: TIMED } : { id: uid(), name, sets: [] });

function moved(arr, i, delta) {
  const to = i + delta;
  if (i < 0 || to < 0 || i >= arr.length || to >= arr.length) return arr;
  const a = arr.slice();
  const [x] = a.splice(i, 1);
  a.splice(to, 0, x);
  return a;
}

/** Replace a plan's days while keeping "next up" on the same day if it still exists. */
function setDays(p, newDays) {
  const nextId = p.days[p.nextDayIndex] && p.days[p.nextDayIndex].id;
  const f = newDays.findIndex((d) => d.id === nextId);
  p.nextDayIndex = newDays.length === 0 ? 0 : f >= 0 ? f : p.nextDayIndex % newDays.length;
  p.days = newDays;
}

function createPlan(name, dayNames = []) {
  const p = { id: uid(), name, nextDayIndex: 0, days: dayNames.map((n) => ({ id: uid(), name: n, exercises: [] })) };
  data.plans.push(p);
  if (!data.activePlanId) data.activePlanId = p.id;
  save();
  return p.id;
}
const createPpl = () => createPlan('Push Pull Legs', ['Push 1', 'Pull 1', 'Legs 1', 'Push 2', 'Pull 2', 'Legs 2']);

function importPlan({ plan, timed }) {
  (timed || []).forEach((n) => setTimed(n, true));
  const taken = new Set(data.plans.map((p) => p.name.toLowerCase()));
  let name = plan.name, n = 2;
  while (taken.has(name.toLowerCase())) name = `${plan.name} (${n++})`;
  plan.name = name;
  data.plans.push(plan);
  if (!data.activePlanId) data.activePlanId = plan.id;
  save();
  return plan.id;
}

function addTemplateExercise(plan, day, name) {
  if (!day.exercises.some((n) => n.toLowerCase() === name.toLowerCase())) day.exercises.push(name);
}

function startWorkout(planId, dayId) {
  if (data.current) return;
  const plan = findPlan(planId);
  const day = findDay(plan, dayId);
  data.current = {
    id: uid(),
    planId: plan ? plan.id : null,
    planName: plan ? plan.name : '',
    dayId: day ? day.id : null,
    dayName: day ? day.name : 'Freestyle',
    startedAt: Date.now(),
    finishedAt: null,
    exercises: day ? day.exercises.map((n) => newExercise(n, isTimedName(n))) : [],
  };
  commit();
}

/** Saves the workout and moves the rotation to the day after the one just done. */
function finishWorkout() {
  const cur = data.current;
  if (!cur) return;
  cur.finishedAt = Date.now();
  cur.exercises = cur.exercises.filter((e) => e.sets.length > 0);
  const plan = findPlan(cur.planId);
  if (plan) {
    const i = plan.days.findIndex((d) => d.id === cur.dayId);
    if (i >= 0) plan.nextDayIndex = (i + 1) % plan.days.length;
  }
  data.sessions.push(cur);
  data.current = null;
  commit();
}

function library() {
  const seen = new Map();
  data.plans.forEach((p) => p.days.forEach((d) => d.exercises.forEach((n) => add(n))));
  data.sessions.forEach((s) => s.exercises.forEach((e) => add(e.name)));
  function add(n) { n = String(n).trim(); if (n && !seen.has(n.toLowerCase())) seen.set(n.toLowerCase(), n); }
  return [...seen.values()].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
}

function lastPerformance(name, before) {
  const lower = name.toLowerCase();
  const list = data.sessions.filter((s) => s.startedAt < before).sort((a, b) => b.startedAt - a.startedAt);
  for (const s of list) {
    const e = s.exercises.find((x) => x.name.toLowerCase() === lower && x.sets.length);
    if (e) return e;
  }
  return null;
}

function lastDone(dayId) {
  let m = null;
  data.sessions.forEach((s) => { if (s.dayId === dayId && (m == null || s.startedAt > m)) m = s.startedAt; });
  return m;
}

// ---------------------------------------------------------------- shared plan format

function planShareJson(plan) {
  const o = { type: 'workout-plan', version: 1, name: plan.name, days: plan.days.map((d) => ({ name: d.name, exercises: d.exercises.slice() })) };
  const seen = new Set();
  const timed = [];
  plan.days.forEach((d) => d.exercises.forEach((n) => {
    if (isTimedName(n) && !seen.has(n.toLowerCase())) { seen.add(n.toLowerCase()); timed.push(n); }
  }));
  if (timed.length) o.timed = timed;
  return o;
}
/** Returns { plan, timed } for a shared plan file, or null if it isn't one. */
function parseSharedPlan(text) {
  try {
    const o = JSON.parse(String(text).trim());
    if (!o || o.type !== 'workout-plan') return null;
    return {
      plan: {
        id: uid(),
        name: String(o.name || '').trim() || 'Shared plan',
        nextDayIndex: 0,
        days: (o.days || []).map((d) => ({ id: uid(), name: String(d.name || '').trim() || 'Day', exercises: (d.exercises || []).map(String) })),
      },
      timed: (o.timed || []).map((n) => String(n).trim().toLowerCase()),
    };
  } catch (e) {
    return null;
  }
}

// ================================================================ stats

const setVolume = (x) => (x.w || 0) * x.r + x.drops.reduce((a, d) => a + (d.w || 0) * d.r, 0);

function computeStats(all, from, to) {
  const ss = all.filter((s) => s.startedAt >= from && s.startedAt < to);
  const st = { workouts: ss.length, sets: 0, reps: 0, partials: 0, dropSets: 0, volume: 0, duration: 0, byDay: [], byExercise: [], timedSeconds: 0, timedRounds: 0, distance: 0 };
  const ex = new Map();
  const days = new Map();
  for (const s of ss) {
    if (s.finishedAt) st.duration += Math.max(0, s.finishedAt - s.startedAt);
    days.set(s.dayName, (days.get(s.dayName) || 0) + 1);
    for (const e of s.exercises) {
      if (!e.sets.length) continue;
      const timed = isTimedEx(e);
      const k = (timed ? 't:' : 's:') + e.name.trim().toLowerCase();
      if (!ex.has(k)) ex.set(k, { name: e.name.trim(), timed, sets: 0, reps: 0, partials: 0, dropSets: 0, volume: 0, best: null, sessions: 0, seconds: 0, bestSeconds: 0, distance: 0 });
      const t = ex.get(k);
      t.sessions++;
      if (timed) {
        for (const x of e.sets) {
          st.timedRounds++; t.sets++;
          st.timedSeconds += x.t || 0; t.seconds += x.t || 0;
          if ((x.t || 0) > t.bestSeconds) t.bestSeconds = x.t || 0;
          if (x.d != null) { st.distance += x.d; t.distance += x.d; }
        }
        continue;
      }
      for (const x of e.sets) {
        st.sets++; t.sets++;
        st.reps += x.r; t.reps += x.r;
        st.partials += x.p; t.partials += x.p;
        if (x.drops.length) { st.dropSets++; t.dropSets++; }
        const v = setVolume(x);
        st.volume += v; t.volume += v;
        if (x.w != null && (t.best == null || x.w > t.best)) t.best = x.w;
      }
    }
  }
  st.byDay = [...days.entries()].sort((a, b) => b[1] - a[1]);
  st.byExercise = [...ex.values()].sort((a, b) => (a.timed - b.timed) || (b.sets - a.sets));
  return st;
}

/** Week = Monday..Sunday. offset 0 = current period. */
function period(monthly, offset) {
  const t = new Date(); t.setHours(0, 0, 0, 0);
  let s, e;
  if (monthly) {
    s = new Date(t.getFullYear(), t.getMonth() + offset, 1);
    e = new Date(s.getFullYear(), s.getMonth() + 1, 1);
  } else {
    const dow = (t.getDay() + 6) % 7;
    s = new Date(t.getFullYear(), t.getMonth(), t.getDate() - dow + 7 * offset);
    e = new Date(s.getFullYear(), s.getMonth(), s.getDate() + 7);
  }
  return { s, e, from: s.getTime(), to: e.getTime(), monthly };
}
function periodLabel(p) {
  if (p.monthly) return p.s.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  const last = new Date(p.e.getFullYear(), p.e.getMonth(), p.e.getDate() - 1);
  const o = { month: 'short', day: 'numeric' };
  const o2 = last.getFullYear() !== new Date().getFullYear() ? { ...o, year: 'numeric' } : o;
  return `${p.s.toLocaleDateString(undefined, o)} – ${last.toLocaleDateString(undefined, o2)}`;
}
const periodShort = (p) => p.s.toLocaleDateString(undefined, p.monthly ? { month: 'short', year: '2-digit' } : { month: 'short', day: 'numeric' });

// ================================================================ formatting

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtNum = (n) => String(Math.round(n * 100) / 100);
const fmtVol = (n) => n >= 10000 ? (Math.round(n / 100) / 10) + 'k' : String(Math.round(n));
const unit = () => data.weightUnit;
const setMain = (x) => x.w != null ? `${fmtNum(x.w)} ${unit()} × ${x.r}` : `${x.r} reps`;
const dropText = (d) => d.w != null ? `${fmtNum(d.w)} ${unit()} × ${d.r}` : `${d.r} reps`;
function setSummary(x) {
  let s = x.w != null ? `${fmtNum(x.w)}×${x.r}` : `${x.r}`;
  if (x.p > 0) s += ` +${x.p}p`;
  x.drops.forEach((d) => { s += ' ↓' + (d.w != null ? `${fmtNum(d.w)}×${d.r}` : d.r); });
  return s;
}
/** 90 -> "1:30", 3725 -> "1:02:05" */
function fmtClock(sec) {
  sec = Math.max(0, Math.floor(sec || 0));
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  const pad = (n) => String(n).padStart(2, '0');
  return h ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}
const timedText = (x) => fmtClock(x.t) + (x.d != null ? ` · ${fmtNum(x.d)} ${distUnit()}` : '');
const summaryFor = (ex, x) => isTimedEx(ex) ? fmtClock(x.t) + (x.d != null ? `/${fmtNum(x.d)}${distUnit()}` : '') : setSummary(x);

function fmtDur(ms) {
  const m = Math.max(0, Math.floor(ms / 60000));
  return m < 60 ? `${m}m` : `${Math.floor(m / 60)}h ${String(m % 60).padStart(2, '0')}m`;
}
function relDay(ms) {
  const a = new Date(ms); a.setHours(0, 0, 0, 0);
  const b = new Date(); b.setHours(0, 0, 0, 0);
  const n = Math.round((b - a) / 86400000);
  return n === 0 ? 'today' : n === 1 ? 'yesterday' : `${n} days ago`;
}
const fmtDate = (ms, o) => new Date(ms).toLocaleDateString(undefined, o);
const fmtTime = (ms) => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
function parseNum(v) {
  v = String(v ?? '').replace(',', '.').trim();
  if (!v) return null;
  const n = parseFloat(v);
  return isFinite(n) && n >= 0 ? n : null;
}
const roundTo = (v, step) => Math.round(v / step) * step;

// ================================================================ icons

const ICONS = {
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  del: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  more: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  up: 'M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z',
  down: 'M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z',
  left: 'M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z',
  right: 'M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z',
  close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  play: 'M8 5v14l11-7z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  share: 'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z',
};
const icon = (n) => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[n]}"/></svg>`;
const pill = (t, cls = '') => `<span class="pill ${cls}">${esc(t)}</span>`;
const section = (t) => `<div class="section">${esc(t)}</div>`;
const attrs = (o) => Object.entries(o).map(([k, v]) => v == null ? '' : ` data-${k}="${esc(v)}"`).join('');

function topbar(title, { back = false, sub = '', actions = '' } = {}) {
  return `<header class="topbar ${back ? 'has-back' : ''}">
    ${back ? `<button class="icon-btn" data-a="back" aria-label="Back">${icon('back')}</button>` : ''}
    <div class="t"><h1>${esc(title)}</h1>${sub ? `<div class="sub">${sub}</div>` : ''}</div>
    ${actions}
  </header>`;
}

// ================================================================ UI state + router

const ui = { tab: 'workout', sub: null, statsMonthly: false, statsOffset: 0 };

function openSub(sub) {
  ui.sub = sub;
  history.pushState({ sub }, '');
  render();
  window.scrollTo(0, 0);
}
window.addEventListener('popstate', (e) => {
  // Back while a dialog is open just closes the dialog.
  if (closeModal()) { history.pushState(ui.sub ? { sub: ui.sub } : null, ''); return; }
  ui.sub = (history.state && history.state.sub) || null;
  render();
});

function render() {
  let h;
  if (ui.sub && ui.sub.type === 'plan') h = renderPlanEditor(ui.sub.id);
  else if (ui.sub && ui.sub.type === 'session') h = renderSessionDetail(ui.sub.id);
  else if (ui.tab === 'history') h = renderHistory();
  else if (ui.tab === 'stats') h = renderStats();
  else if (ui.tab === 'plans') h = renderPlans();
  else h = data.current ? renderActive() : renderStart();
  if (h == null) { ui.sub = null; return render(); }
  $('#app').innerHTML = h;
  $$('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.t === ui.tab));
}

// ================================================================ screens

const isStandalone = () => matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
const isIOS = () => /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
function installHint() {
  let hidden = false;
  try { hidden = localStorage.getItem('hideInstallHint') === '1'; } catch (e) {}
  if (isStandalone() || hidden) return '';
  const how = isIOS()
    ? 'In Safari tap <b>Share</b> (square with arrow) → <b>Add to Home Screen</b>. Then always open it from the home-screen icon: Safari and the installed app keep separate data.'
    : 'Open the browser menu <b>⋮</b> → <b>Install app</b> / <b>Add to Home screen</b>.';
  return `<section class="card hint col"><div class="row"><b class="grow">Install this app</b><button class="icon-btn sm" data-a="hideHint" aria-label="Hide">${icon('close')}</button></div><p class="small">${how}</p></section>`;
}

function renderStart() {
  const plan = activePlan();
  let h = topbar('Workout') + '<main class="content">' + installHint();
  if (!plan) {
    h += `<section class="card col">
      <h2>Set up your split</h2>
      <p class="muted">Create a plan with as many days as you like and name them however you want. Workouts rotate through the days in order, whenever you go. No calendar involved.</p>
      <button class="btn primary block" data-a="newPlan">Create my own plan</button>
      <button class="btn outline block" data-a="createPpl">Start from Push / Pull / Legs ×2</button>
      <button class="btn outline block" data-a="importPlan">Import a plan a friend sent you</button>
    </section>`;
  } else {
    if (data.plans.length > 1) {
      h += `<div class="chips">${data.plans.map((p) => `<button class="chip ${p.id === plan.id ? 'on' : ''}" data-a="setActive"${attrs({ id: p.id })}>${esc(p.name)}</button>`).join('')}</div>`;
    }
    if (!plan.days.length) {
      h += `<section class="card col"><p>"${esc(plan.name)}" has no days yet.</p><button class="btn primary" data-a="openPlan"${attrs({ id: plan.id })}>Add days</button></section>`;
    } else {
      const ni = Math.min(Math.max(plan.nextDayIndex, 0), plan.days.length - 1);
      const next = plan.days[ni];
      const ld = lastDone(next.id);
      h += `<section class="card hero">
        <div class="label">NEXT UP</div>
        <div class="hero-title">${esc(next.name)}</div>
        <div class="small">Day ${ni + 1} of ${plan.days.length} · ${esc(plan.name)}${ld ? ' · last done ' + relDay(ld) : ''}</div>
        ${next.exercises.length ? `<div class="small">${esc(next.exercises.join(' · '))}</div>` : ''}
        <button class="btn primary block" style="margin-top:6px" data-a="start"${attrs({ plan: plan.id, day: next.id })}>${icon('play')} Start ${esc(next.name)}</button>
      </section>`;
      h += section('Or do a different day') + '<p class="small muted">The rotation carries on from whichever day you finish.</p>';
      plan.days.forEach((d, i) => {
        const l = lastDone(d.id);
        h += `<section class="card row">
          <div class="grow"><div class="title">${esc(d.name)} ${i === ni ? pill('Next') : ''}</div>
          <div class="small muted">${d.exercises.length} exercises${l ? ' · ' + relDay(l) : ''}</div></div>
          <button class="btn text" data-a="start"${attrs({ plan: plan.id, day: d.id })}>Start</button>
        </section>`;
      });
    }
  }
  const w = period(false, 0);
  const st = computeStats(data.sessions, w.from, w.to);
  h += `<button class="btn outline block" data-a="start">Start empty workout (not part of split)</button>
    <p class="small muted">This week: ${st.workouts} workouts · ${st.sets} sets · ${st.reps} reps</p></main>`;
  return h;
}

function elapsedText(s) {
  return [s.planName ? esc(s.planName) : '', fmtDur(Date.now() - s.startedAt), `${setCount(s)} sets`].filter(Boolean).join(' · ');
}

function renderActive() {
  const s = data.current;
  return topbar(s.dayName, {
    sub: `<span id="elapsed">${elapsedText(s)}</span>`,
    actions: `<button class="icon-btn" data-a="discard" aria-label="Discard workout">${icon('del')}</button>
              <button class="btn primary" data-a="finish">Finish</button>`,
  }) + sessionEditor(s, 'current');
}

function renderSessionDetail(id) {
  const s = getSession(id);
  if (!s) return null;
  const sub = fmtDate(s.startedAt, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }) + ' · ' + fmtTime(s.startedAt) +
    (s.finishedAt ? ' · ' + fmtDur(s.finishedAt - s.startedAt) : '');
  return topbar(s.dayName, {
    back: true, sub,
    actions: `<button class="icon-btn" data-a="deleteSession"${attrs({ id: s.id })} aria-label="Delete workout">${icon('del')}</button>`,
  }) + sessionEditor(s, s.id);
}

function sessionEditor(s, key) {
  const plan = findPlan(s.planId);
  const day = findDay(plan, s.dayId);
  let h = '<main class="content">';
  if (!s.exercises.length) {
    h += `<p class="muted">${day ? `No exercises saved for ${esc(day.name)} yet. Add what you do and tick "save to ${esc(day.name)}" so it's here next time.` : 'Add your first exercise.'}</p>`;
  }
  s.exercises.forEach((ex) => { h += exerciseCard(s, key, ex, day); });
  h += `<button class="btn outline block" data-a="addExercise"${attrs({ s: key })}>${icon('add')} Add exercise</button><div style="height:24px"></div></main>`;
  return h;
}

function exerciseCard(s, key, ex, day) {
  const last = lastPerformance(ex.name, s.startedAt);
  const inTpl = !day || day.exercises.some((n) => n.toLowerCase() === ex.name.toLowerCase());
  const a = { s: key, ex: ex.id };
  const timed = isTimedEx(ex);
  return `<section class="card">
    <div class="ex-head"><div class="title grow">${esc(ex.name)}</div>${timed ? pill('⏱ timed', 'sec') : ''}${inTpl ? '' : pill('today only', 'tert')}
      <button class="icon-btn" data-a="exMenu"${attrs(a)} aria-label="More">${icon('more')}</button></div>
    ${last ? `<div class="small muted" style="margin-bottom:4px">Last time: ${esc(last.sets.map((x) => summaryFor(ex, x)).join(',  '))}</div>` : ''}
    ${timed
    ? ex.sets.map((x, i) => `<button class="set-row" data-a="editSet"${attrs({ ...a, i })}>
        <div class="line"><span class="n">${i + 1}</span><span class="main">${esc(timedText(x))}</span></div></button>`).join('')
    : ex.sets.map((x, i) => `<button class="set-row" data-a="editSet"${attrs({ ...a, i })}>
        <div class="line"><span class="n">${i + 1}</span><span class="main">${esc(setMain(x))}</span>
        ${x.p > 0 ? pill(`+${x.p} partial`, 'tert') : ''}${x.drops.length ? pill(x.drops.length === 1 ? 'drop set' : `${x.drops.length}× drop`, 'sec') : ''}</div>
        ${x.drops.length ? `<div class="drops">${esc(x.drops.map((d) => '↓ ' + dropText(d)).join('   '))}</div>` : ''}
      </button>`).join('')}
    <div class="ex-actions">
      <button class="btn tonal" data-a="addSet"${attrs(a)}>${icon('add')} ${timed ? 'Add round' : 'Add set'}</button>
      ${ex.sets.length ? `<button class="btn text" data-a="repeatSet"${attrs(a)}>Repeat last</button>` : ''}
    </div>
  </section>`;
}

function renderHistory() {
  let h = topbar('History') + '<main class="content">';
  if (!data.sessions.length) return h + '<p class="muted" style="text-align:center;margin-top:40px">No workouts logged yet.</p></main>';
  const groups = new Map();
  data.sessions.slice().sort((a, b) => b.startedAt - a.startedAt).forEach((s) => {
    const k = fmtDate(s.startedAt, { month: 'long', year: 'numeric' });
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k).push(s);
  });
  groups.forEach((list, month) => {
    h += section(`${month} · ${list.length} workouts`);
    list.forEach((s) => {
      const meta = [s.planName, `${s.exercises.length} exercises`, `${setCount(s)} sets`, s.finishedAt ? fmtDur(s.finishedAt - s.startedAt) : ''].filter(Boolean).join(' · ');
      const names = s.exercises.map((e) => {
        if (isTimedEx(e)) return `${e.name} ${fmtClock(e.sets.reduce((a, x) => a + (x.t || 0), 0))}`;
        const best = e.sets.reduce((m, x) => (x.w != null && (m == null || x.w > m) ? x.w : m), null);
        return e.name + (best != null ? ` ${fmtNum(best)}${unit()}` : '');
      }).join(' · ');
      h += `<section class="card clickable col" style="gap:2px" data-a="openSession"${attrs({ id: s.id })}>
        <div class="row"><div class="title grow">${esc(s.dayName)}</div><span class="small">${fmtDate(s.startedAt, { weekday: 'short', month: 'short', day: 'numeric' })}</span></div>
        <div class="small muted">${esc(meta)}</div>
        <div class="small">${esc(names)}</div>
      </section>`;
    });
  });
  return h + '</main>';
}

function renderStats() {
  const m = ui.statsMonthly, off = ui.statsOffset;
  const p = period(m, off);
  const st = computeStats(data.sessions, p.from, p.to);
  const word = m ? 'month' : 'week';
  const u = unit();
  let h = topbar('Progress') + '<main class="content">';
  h += `<div class="chips"><button class="chip ${m ? '' : 'on'}" data-a="statsMode" data-m="week">Week</button><button class="chip ${m ? 'on' : ''}" data-a="statsMode" data-m="month">Month</button></div>`;
  h += `<div class="period"><button class="icon-btn" data-a="statsPrev" aria-label="Previous">${icon('left')}</button>
    <div class="grow">${esc(periodLabel(p))}${off === 0 ? ` <span class="muted small">(this ${word})</span>` : ''}</div>
    <button class="icon-btn" data-a="statsNext" ${off < 0 ? '' : 'disabled'} aria-label="Next">${icon('right')}</button></div>`;
  const tiles = [
    ['Workouts', st.workouts], ['Time trained', fmtDur(st.duration)],
    ['Sets', st.sets], ['Reps', st.reps],
    ['Partial reps', st.partials], ['Drop sets', st.dropSets],
    [`Volume (${u})`, fmtVol(st.volume)], ['Cardio / timed', fmtClock(st.timedSeconds)],
  ];
  if (st.distance > 0) tiles.push([`Distance (${distUnit()})`, fmtNum(st.distance)]);
  h += `<div class="tiles">${tiles.map(([l, v]) => `<div class="card tile"><div class="v">${esc(v)}</div><div class="l">${esc(l)}</div></div>`).join('')}</div>`;
  if (!st.workouts) h += `<p class="muted">No workouts this ${word}.</p>`;
  if (st.byDay.length) {
    h += section('Split days done') + `<section class="card col" style="gap:6px">${st.byDay.map(([n, c]) => `<div class="row"><span class="grow">${esc(n)}</span><b>${c}×</b></div>`).join('')}</section>`;
  }
  if (st.byExercise.length) {
    h += section('Exercises');
    st.byExercise.forEach((e) => {
      if (e.timed) {
        const tp = [`${e.sets} rounds`, `${fmtClock(e.seconds)} total`, e.distance > 0 ? `${fmtNum(e.distance)} ${distUnit()}` : '', e.sessions > 1 ? `${e.sessions} workouts` : ''].filter(Boolean);
        h += `<section class="card"><div class="row"><b class="grow">⏱ ${esc(e.name)}</b><span class="small"><b>best ${fmtClock(e.bestSeconds)}</b></span></div>
          <div class="small muted">${esc(tp.join(' · '))}</div></section>`;
        return;
      }
      const parts = [`${e.sets} sets`, `${e.reps} reps`,
        e.partials ? `${e.partials} partials` : '', e.dropSets ? `${e.dropSets} drop sets` : '',
        e.volume > 0 ? `${fmtVol(e.volume)} ${u} volume` : '', e.sessions > 1 ? `${e.sessions} workouts` : ''].filter(Boolean);
      h += `<section class="card"><div class="row"><b class="grow">${esc(e.name)}</b>${e.best != null ? `<span class="small"><b>best ${fmtNum(e.best)} ${u}</b></span>` : ''}</div>
        <div class="small muted">${esc(parts.join(' · '))}</div></section>`;
    });
  }
  const trend = [7, 6, 5, 4, 3, 2, 1, 0].map((k) => { const pp = period(m, off - k); return [pp, computeStats(data.sessions, pp.from, pp.to)]; });
  const max = Math.max(1, ...trend.map(([, s]) => s.sets));
  h += section(m ? 'Last 8 months · sets' : 'Last 8 weeks · sets');
  h += `<section class="card col">${trend.map(([pp, s]) => `<div class="bar-row"><span class="lbl">${esc(periodShort(pp))}</span>
      <div class="track"><div class="bar" style="width:${(s.sets / max) * 100}%"></div></div>
      <span class="val">${s.workouts}× · ${s.sets}</span></div>`).join('')}</section>`;
  return h + '</main>';
}

function renderPlans() {
  const act = activePlan();
  let h = topbar('Split plans') + '<main class="content">' + installHint();
  if (!data.plans.length) h += '<p class="muted">No plans yet. Create one, import one a friend sent you, or start from the Push/Pull/Legs template below.</p>';
  data.plans.forEach((p) => {
    const next = p.days[p.nextDayIndex];
    h += `<section class="card clickable col" style="gap:4px" data-a="openPlan"${attrs({ id: p.id })}>
      <div class="row"><div class="title grow">${esc(p.name)}</div>
        ${act && act.id === p.id ? pill('Active') : `<button class="btn text" data-a="setActive"${attrs({ id: p.id })}>Use this plan</button>`}</div>
      <div class="small muted">${p.days.length ? `${p.days.length} days: ${esc(p.days.map((d) => d.name).join(' → '))}` : 'No days yet'}</div>
      ${next ? `<div class="small">Next up: ${esc(next.name)}</div>` : ''}
    </section>`;
  });
  h += `<button class="btn outline block" data-a="importPlan">Import a plan a friend sent you</button>
    <button class="btn outline block" data-a="createPpl">Add Push / Pull / Legs ×2 template</button>`;
  h += section('Settings') + `<section class="card col">
    <div class="row"><span class="grow">Weight unit</span>
      ${['kg', 'lb'].map((u) => `<button class="chip ${data.weightUnit === u ? 'on' : ''}" data-a="unit" data-u="${u}">${u}</button>`).join('')}</div>
    <div class="divider"></div>
    <b>Backup</b>
    <p class="small muted">Your data is stored only on this device. Export a backup now and then (to Files / Drive) so you don't lose it. Backups also work with the Android app.</p>
    <div class="row"><button class="btn outline" data-a="exportBackup">Export backup</button><button class="btn outline" data-a="importBackup">Import backup</button></div>
  </section><div style="height:70px"></div></main>`;
  h += `<button class="btn primary fab" data-a="newPlan">${icon('add')} New plan</button>`;
  return h;
}

function renderPlanEditor(id) {
  const plan = findPlan(id);
  if (!plan) return null;
  const isActive = activePlan() && activePlan().id === plan.id;
  let h = topbar(plan.name, {
    back: true,
    actions: `<button class="icon-btn" data-a="planShare"${attrs({ id })} aria-label="Share plan">${icon('share')}</button>
      <button class="icon-btn" data-a="planRename"${attrs({ id })} aria-label="Rename plan">${icon('edit')}</button>
      <button class="icon-btn" data-a="planDelete"${attrs({ id })} aria-label="Delete plan">${icon('del')}</button>`,
  }) + '<main class="content">';
  h += '<p class="muted small">Days run in this order and loop back to the start. Each workout picks up where the last one left off, however many days a week you train.</p>';
  if (!isActive) h += `<button class="btn primary" data-a="setActive"${attrs({ id })}>Use this plan</button>`;
  if (!plan.days.length) h += '<p>No days yet. Tap "Add day" (e.g. Push 1, Pull 1, Legs 1…).</p>';
  plan.days.forEach((d, i) => {
    const a = { plan: plan.id, day: d.id, i };
    h += `<section class="card" style="padding-right:6px">
      <div class="row" style="gap:4px">
        <span style="color:var(--primary);font-weight:700">${i + 1}.</span>
        <div class="title grow">${esc(d.name)} ${i === plan.nextDayIndex ? pill('Next') : ''}</div>
        <button class="icon-btn" data-a="dayMove" data-d="-1"${attrs(a)} ${i > 0 ? '' : 'disabled'} aria-label="Move up">${icon('up')}</button>
        <button class="icon-btn" data-a="dayMove" data-d="1"${attrs(a)} ${i < plan.days.length - 1 ? '' : 'disabled'} aria-label="Move down">${icon('down')}</button>
        <button class="icon-btn" data-a="dayMenu"${attrs(a)} aria-label="More">${icon('more')}</button>
      </div>
      ${d.exercises.length ? '' : '<p class="small muted">No saved exercises. Add some here, or add them during a workout.</p>'}
      ${d.exercises.map((n, j) => `<div class="tpl-row"><span class="grow">${isTimedName(n) ? '⏱ ' : ''}${esc(n)}</span>
        <button class="icon-btn sm" data-a="tplMove" data-d="-1"${attrs({ ...a, j })} ${j > 0 ? '' : 'disabled'} aria-label="Move up">${icon('up')}</button>
        <button class="icon-btn sm" data-a="tplMove" data-d="1"${attrs({ ...a, j })} ${j < d.exercises.length - 1 ? '' : 'disabled'} aria-label="Move down">${icon('down')}</button>
        <button class="icon-btn sm" data-a="tplRemove"${attrs({ ...a, j })} aria-label="Remove">${icon('close')}</button></div>`).join('')}
      <button class="btn text" data-a="tplAdd"${attrs(a)}>${icon('add')} Add exercise</button>
    </section>`;
  });
  h += '<div style="height:70px"></div></main>';
  h += `<button class="btn primary fab" data-a="addDay"${attrs({ id })}>${icon('add')} Add day</button>`;
  return h;
}

// ================================================================ modals

let modalHandlers = {};
function openModal(html, handlers = {}, onMount) {
  modalHandlers = handlers;
  $('#modal-root').innerHTML = `<div class="overlay" data-a="m:backdrop"><div class="dialog" role="dialog" aria-modal="true">${html}</div></div>`;
  if (onMount) onMount();
}
function closeModal() {
  const root = $('#modal-root');
  const was = !!root.firstChild;
  const onClose = modalHandlers.__onClose;
  root.innerHTML = '';
  modalHandlers = {};
  if (onClose) onClose();
  return was;
}

function confirmDialog(title, msg, okText, onOk, danger = false) {
  openModal(`<h3>${esc(title)}</h3><p class="muted" style="white-space:pre-line">${esc(msg)}</p>
    <div class="dialog-actions"><span class="grow"></span><button class="btn text" data-a="m:close">Cancel</button>
    <button class="btn text ${danger ? 'danger' : ''}" data-a="m:ok">${esc(okText)}</button></div>`,
  { ok() { closeModal(); onOk(); } });
}

function promptDialog(title, initial, label, okText, onOk) {
  openModal(`<h3>${esc(title)}</h3><div class="field"><div class="field-label">${esc(label)}</div>
    <input id="p-in" type="text" autocomplete="off" autocapitalize="words" value="${esc(initial)}"></div>
    <div class="dialog-actions"><span class="grow"></span><button class="btn text" data-a="m:close">Cancel</button>
    <button class="btn text" data-a="m:ok">${esc(okText)}</button></div>`,
  { ok() { const v = $('#p-in').value.trim(); if (!v) return; closeModal(); onOk(v); } },
  () => {
    const inp = $('#p-in');
    setTimeout(() => { inp.focus(); inp.select(); }, 50);
    inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); modalHandlers.ok(); } });
  });
}

function actionSheet(title, items) {
  openModal(`<h3>${esc(title)}</h3>${items.map((it, i) => `<button class="sheet-item ${it.danger ? 'danger' : ''}" data-a="m:pick" data-i="${i}">${esc(it.label)}</button>`).join('')}
    <div class="dialog-actions"><span class="grow"></span><button class="btn text" data-a="m:close">Cancel</button></div>`,
  { pick(el) { const it = items[+el.dataset.i]; closeModal(); it.fn(); } });
}

/** onAdd(name, saveToPlan, timed). Strength defaults to saving to the day; timed/cardio defaults to "today only". */
function addExerciseDialog(suggestions, saveLabel, onAdd) {
  let timed = false;
  openModal(`<h3>Add exercise</h3>
    <div class="field"><input id="ex-name" type="text" autocomplete="off" autocapitalize="words" placeholder="Exercise name"></div>
    <div class="chips" style="margin-bottom:6px">
      <button class="chip on" id="k-s" data-a="m:kind" data-k="s">Weights × reps</button>
      <button class="chip" id="k-t" data-a="m:kind" data-k="t">Time / cardio</button>
    </div>
    ${saveLabel ? `<label class="check"><input id="ex-save" type="checkbox" checked> ${esc(saveLabel)}</label>` : ''}
    <div id="sugg"></div>
    <div class="dialog-actions"><span class="grow"></span><button class="btn text" data-a="m:close">Cancel</button><button class="btn text" data-a="m:add">Add</button></div>`,
  {
    kind(el) { choose(el.dataset.k === 't'); },
    pick(el) {
      const n = el.dataset.n;
      const t = isTimedName(n);
      const save = saveLabel ? (t === timed ? $('#ex-save').checked : !t) : false;
      closeModal();
      onAdd(n, save, t);
    },
    add() {
      const v = $('#ex-name').value.trim();
      if (!v) return;
      const save = saveLabel ? $('#ex-save').checked : false;
      closeModal();
      onAdd(v, save, timed);
    },
  },
  () => {
    const inp = $('#ex-name');
    const draw = () => {
      const q = inp.value.trim().toLowerCase();
      const known = suggestions.find((n) => n.toLowerCase() === q);
      if (known) choose(isTimedName(known));
      const list = (q ? suggestions.filter((n) => n.toLowerCase().includes(q)) : suggestions).slice(0, 10);
      $('#sugg').innerHTML = list.length
        ? `<div class="field-label" style="margin-top:6px">${q ? 'Matches' : 'Your exercises'}</div>` +
          list.map((n) => `<button class="sugg" data-a="m:pick" data-n="${esc(n)}">${isTimedName(n) ? '⏱ ' : ''}${esc(n)}</button>`).join('')
        : '';
    };
    inp.addEventListener('input', draw);
    inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); modalHandlers.add(); } });
    draw();
    setTimeout(() => inp.focus(), 50);
  });
  function choose(t) {
    if (t === timed) return;
    timed = t;
    $('#k-s').classList.toggle('on', !t);
    $('#k-t').classList.toggle('on', t);
    const cb = $('#ex-save');
    if (cb) cb.checked = !t;
  }
}

/** One round of a timed exercise: duration (with a stopwatch) and optional distance. */
function timedDialog(s, ex, idx) {
  const isEdit = idx != null && idx >= 0 && idx < ex.sets.length;
  const init = isEdit ? ex.sets[idx] : { t: 0, d: null };
  let timer = null;
  const total = () => (parseInt($('#f-min').value, 10) || 0) * 60 + (parseInt($('#f-sec').value, 10) || 0);
  const showClock = () => { $('#clock').textContent = fmtClock(total()); };
  const stop = () => {
    if (timer) clearInterval(timer);
    timer = null;
    const b = $('#f-timer');
    if (b) { b.textContent = total() > 0 ? 'Continue timer' : 'Start timer'; b.classList.remove('danger-bg'); }
  };
  openModal(`<h3>${esc(ex.name)} · round ${isEdit ? idx + 1 : ex.sets.length + 1}</h3>
    <div id="clock" style="font-size:44px;font-weight:700;text-align:center;font-variant-numeric:tabular-nums;margin:4px 0 10px">${fmtClock(init.t)}</div>
    <button class="btn primary block" id="f-timer" data-a="m:timer" style="margin-bottom:14px">${init.t ? 'Continue timer' : 'Start timer'}</button>
    ${stepper('Minutes', 'f-min', init.t ? Math.floor(init.t / 60) : '', 1, false)}
    ${stepper('Seconds', 'f-sec', init.t ? init.t % 60 : '', 15, false)}
    ${stepper('Distance (optional)', 'f-dist', init.d != null ? fmtNum(init.d) : '', 0.5, true, distUnit())}
    <div class="dialog-actions">
      ${isEdit ? '<button class="btn text danger" data-a="m:delete">Delete</button>' : ''}
      <span class="grow"></span>
      <button class="btn text" data-a="m:cancel">Cancel</button>
      <button class="btn primary" data-a="m:save">Save</button>
    </div>`,
  {
    timer() {
      if (timer) { stop(); return; }
      const base = total();
      const start = Date.now();
      const b = $('#f-timer');
      b.textContent = 'Stop timer';
      b.classList.add('danger-bg');
      timer = setInterval(() => {
        if (!$('#f-min')) { stop(); return; }
        const t = base + Math.floor((Date.now() - start) / 1000);
        $('#f-min').value = Math.floor(t / 60);
        $('#f-sec').value = t % 60;
        showClock();
      }, 200);
    },
    cancel() { stop(); closeModal(); },
    save() {
      stop();
      const t = total();
      if (t <= 0) { toast('Enter a time or use the timer'); return; }
      const set = { id: isEdit ? ex.sets[idx].id : uid(), w: null, r: 0, p: 0, drops: [], t };
      const d = parseNum($('#f-dist').value);
      if (d != null) set.d = d;
      if (isEdit) ex.sets[idx] = set; else ex.sets.push(set);
      closeModal();
      commit();
    },
    delete() { stop(); ex.sets.splice(idx, 1); closeModal(); commit(); },
  },
  () => {
    ['#f-min', '#f-sec'].forEach((id) => $(id).addEventListener('input', showClock));
    $$('[data-t="f-min"],[data-t="f-sec"]').forEach((b) => b.addEventListener('click', () => setTimeout(showClock)));
  });
  // Closing the dialog any other way must stop the stopwatch too.
  const prevClose = modalHandlers;
  prevClose.__onClose = stop;
}

function stepper(label, id, value, step, dec, u = '') {
  return `<div class="field"><div class="field-label">${esc(label)}</div><div class="stepper">
    <button type="button" class="step" data-a="step"${attrs({ t: id, d: -step, dec: dec ? 1 : 0 })}>−</button>
    <input id="${id}" inputmode="${dec ? 'decimal' : 'numeric'}" autocomplete="off" value="${esc(value)}">
    <span class="unit">${esc(u)}</span>
    <button type="button" class="step" data-a="step"${attrs({ t: id, d: step, dec: dec ? 1 : 0 })}>+</button>
  </div></div>`;
}
const dropRow = (w, r) => `<div class="drop-row"><span class="tag">↓</span>
  <input class="drop-w" inputmode="decimal" placeholder="${esc(unit())}" value="${w != null && w !== '' ? esc(fmtNum(+w)) : ''}">
  <span class="muted">×</span>
  <input class="drop-r" inputmode="numeric" placeholder="reps" value="${r ? esc(r) : ''}">
  <button class="icon-btn sm" data-a="m:removeDrop" aria-label="Remove drop">${icon('close')}</button></div>`;

function setDialog(key, exId, idx) {
  const s = getSession(key);
  const ex = s && s.exercises.find((e) => e.id === exId);
  if (!ex) return;
  if (isTimedEx(ex)) { timedDialog(s, ex, idx); return; }
  const isEdit = idx != null && idx >= 0 && idx < ex.sets.length;
  let init;
  if (isEdit) init = ex.sets[idx];
  else {
    const lp = lastPerformance(ex.name, s.startedAt);
    const base = ex.sets[ex.sets.length - 1] || (lp && lp.sets[0]);
    init = base ? { w: base.w, r: base.r, p: 0, drops: [] } : { w: null, r: 0, p: 0, drops: [] };
  }
  openModal(`<h3>${esc(ex.name)} · set ${isEdit ? idx + 1 : ex.sets.length + 1}</h3>
    ${stepper('Weight', 'f-w', init.w != null ? fmtNum(init.w) : '', 2.5, true, unit())}
    ${stepper('Reps', 'f-r', init.r > 0 ? init.r : '', 1, false)}
    ${stepper('Partial reps', 'f-p', init.p > 0 ? init.p : '', 1, false)}
    <div class="field-label">Drop sets</div>
    <p class="small muted" style="margin-bottom:8px">Dropped the weight and kept going after this set? Add each drop here.</p>
    <div id="drops">${init.drops.map((d) => dropRow(d.w, d.r)).join('')}</div>
    <button class="btn text" data-a="m:addDrop">${icon('add')} Add drop set</button>
    <div class="dialog-actions">
      ${isEdit ? '<button class="btn text danger" data-a="m:delete">Delete</button>' : ''}
      <span class="grow"></span>
      <button class="btn text" data-a="m:close">Cancel</button>
      <button class="btn primary" data-a="m:save">Save</button>
    </div>`,
  {
    addDrop() {
      const rows = $$('#drops .drop-row');
      const base = parseNum(rows.length ? rows[rows.length - 1].querySelector('.drop-w').value : $('#f-w').value);
      $('#drops').insertAdjacentHTML('beforeend', dropRow(base != null ? roundTo(base * 0.8, 2.5) : null, ''));
    },
    removeDrop(el) { el.closest('.drop-row').remove(); },
    save() {
      const r = parseInt($('#f-r').value, 10) || 0;
      if (r <= 0) { toast('Enter the reps'); $('#f-r').focus(); return; }
      const set = {
        id: isEdit ? ex.sets[idx].id : uid(),
        w: parseNum($('#f-w').value),
        r,
        p: parseInt($('#f-p').value, 10) || 0,
        drops: $$('#drops .drop-row')
          .map((row) => ({ w: parseNum(row.querySelector('.drop-w').value), r: parseInt(row.querySelector('.drop-r').value, 10) || 0 }))
          .filter((d) => d.r > 0),
      };
      if (isEdit) ex.sets[idx] = set; else ex.sets.push(set);
      closeModal();
      commit();
    },
    delete() { ex.sets.splice(idx, 1); closeModal(); commit(); },
  });
}

function importPlanDialog(text) {
  const shared = parseSharedPlan(text);
  const plan = shared && shared.plan;
  if (!plan) {
    openModal(`<h3>Not a plan file</h3><p class="muted">This file isn't a shared Workout plan. (Full backups are restored from Plans → Settings → Import backup.)</p>
      <div class="dialog-actions"><span class="grow"></span><button class="btn text" data-a="m:close">OK</button></div>`);
    return;
  }
  openModal(`<h3>Add plan "${esc(plan.name)}"?</h3>
    <p class="muted" style="margin-bottom:8px">It's added as a new plan. Your existing plans and workouts aren't changed.</p>
    ${plan.days.map((d, i) => `<div>${i + 1}. ${esc(d.name)}${d.exercises.length ? ` <span class="muted small">· ${d.exercises.length} exercises</span>` : ''}</div>`).join('')}
    <div class="dialog-actions"><span class="grow"></span><button class="btn text" data-a="m:close">Cancel</button><button class="btn primary" data-a="m:ok">Add plan</button></div>`,
  {
    ok() {
      closeModal();
      const id = importPlan(shared);
      ui.tab = 'plans';
      openSub({ type: 'plan', id });
    },
  });
}

// ================================================================ files

function pickFile(cb) {
  const inp = document.createElement('input');
  inp.type = 'file';
  inp.accept = '.json,application/json,text/plain';
  inp.style.display = 'none';
  document.body.appendChild(inp);
  inp.addEventListener('change', () => {
    const f = inp.files && inp.files[0];
    inp.remove();
    if (!f) return;
    if (f.size > 5e6) { toast('File is too big'); return; }
    f.text().then(cb).catch(() => toast("Couldn't read file"));
  });
  inp.click();
}

async function shareFile(filename, text, title) {
  const file = new File([text], filename, { type: 'application/json' });
  try {
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      await navigator.share({ files: [file], title });
      return;
    }
  } catch (e) {
    if (e && e.name === 'AbortError') return;
  }
  const url = URL.createObjectURL(file);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
  toast('Saved ' + filename);
}

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

let toastTimer;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 2500);
}

// ================================================================ actions

function exerciseOf(d) {
  const s = getSession(d.s);
  return { s, ex: s && s.exercises.find((e) => e.id === d.ex) };
}

const actions = {
  tab(d) {
    ui.tab = d.t;
    if (ui.sub) { ui.sub = null; history.replaceState(null, ''); }
    render();
    window.scrollTo(0, 0);
  },
  back() { history.back(); },
  hideHint() { try { localStorage.setItem('hideInstallHint', '1'); } catch (e) {} render(); },

  // plans
  newPlan() { promptDialog('New split plan', '', 'Plan name', 'Create', (n) => openSub({ type: 'plan', id: createPlan(n) })); },
  createPpl() { openSub({ type: 'plan', id: createPpl() }); },
  importPlan() { pickFile(importPlanDialog); },
  openPlan(d) { openSub({ type: 'plan', id: d.id }); },
  setActive(d) { data.activePlanId = d.id; commit(); },
  planShare(d) {
    const p = findPlan(d.id);
    const safe = p.name.replace(/[^A-Za-z0-9 _-]/g, '').trim() || 'plan';
    shareFile(`${safe} - workout plan.json`, JSON.stringify(planShareJson(p), null, 2), `Workout plan: ${p.name}`);
  },
  planRename(d) { const p = findPlan(d.id); promptDialog('Rename plan', p.name, 'Plan name', 'Save', (n) => { p.name = n; commit(); }); },
  planDelete(d) {
    const p = findPlan(d.id);
    confirmDialog(`Delete "${p.name}"?`, 'The plan is deleted. Workouts you already logged stay in history and stats.', 'Delete', () => {
      data.plans = data.plans.filter((x) => x.id !== p.id);
      if (data.activePlanId === p.id) data.activePlanId = data.plans[0] ? data.plans[0].id : null;
      save();
      history.back();
    }, true);
  },
  addDay(d) {
    const p = findPlan(d.id);
    promptDialog('Add day', '', 'Day name (e.g. Push 1)', 'Add', (n) => { setDays(p, [...p.days, { id: uid(), name: n, exercises: [] }]); commit(); });
  },
  dayMove(d) { const p = findPlan(d.plan); setDays(p, moved(p.days, +d.i, +d.d)); commit(); },
  dayMenu(d) {
    const p = findPlan(d.plan);
    const day = findDay(p, d.day);
    const i = +d.i;
    const items = [{ label: 'Rename', fn: () => promptDialog('Rename day', day.name, 'Day name', 'Save', (n) => { day.name = n; commit(); }) }];
    if (i !== p.nextDayIndex) items.push({ label: 'Make this the next workout', fn: () => { p.nextDayIndex = i; commit(); } });
    items.push({
      label: 'Delete day', danger: true,
      fn: () => confirmDialog(`Delete ${day.name}?`, 'Removes the day and its saved exercise list from the plan. Past workouts are kept.', 'Delete', () => {
        setDays(p, p.days.filter((x) => x.id !== day.id)); commit();
      }, true),
    });
    actionSheet(day.name, items);
  },
  tplMove(d) { const day = findDay(findPlan(d.plan), d.day); day.exercises = moved(day.exercises, +d.j, +d.d); commit(); },
  tplRemove(d) { const day = findDay(findPlan(d.plan), d.day); day.exercises.splice(+d.j, 1); commit(); },
  tplAdd(d) {
    const p = findPlan(d.plan);
    const day = findDay(p, d.day);
    const sugg = library().filter((n) => !day.exercises.some((x) => x.toLowerCase() === n.toLowerCase()));
    addExerciseDialog(sugg, null, (name, _save, timed) => { setTimed(name, timed); addTemplateExercise(p, day, name); commit(); });
  },

  // workouts
  start(d) { startWorkout(d.plan || null, d.day || null); window.scrollTo(0, 0); },
  finish() {
    const s = data.current;
    const p = findPlan(s.planId);
    const i = p ? p.days.findIndex((x) => x.id === s.dayId) : -1;
    const next = i >= 0 ? p.days[(i + 1) % p.days.length].name : null;
    let msg = `${setCount(s)} sets logged in ${fmtDur(Date.now() - s.startedAt)}.`;
    if (next) msg += `\nNext time: ${next}`;
    if (s.exercises.some((e) => !e.sets.length)) msg += "\nExercises with no sets won't be saved to this workout.";
    confirmDialog('Finish workout?', msg, 'Finish', () => { finishWorkout(); window.scrollTo(0, 0); });
  },
  discard() {
    confirmDialog('Discard workout?', "Everything logged in this workout will be deleted and the rotation won't move.", 'Discard', () => {
      data.current = null; commit();
    }, true);
  },
  addExercise(d) {
    const s = getSession(d.s);
    const p = findPlan(s.planId);
    const day = findDay(p, s.dayId);
    const sugg = library().filter((n) => !s.exercises.some((e) => e.name.toLowerCase() === n.toLowerCase()));
    addExerciseDialog(sugg, day ? `Also add to ${day.name} plan for next time` : null, (name, saveTo, timed) => {
      s.exercises.push(newExercise(name, timed));
      setTimed(name, timed);
      if (saveTo && day) addTemplateExercise(p, day, name);
      commit();
    });
  },
  exMenu(d) {
    const { s, ex } = exerciseOf(d);
    const p = findPlan(s.planId);
    const day = findDay(p, s.dayId);
    const items = [];
    if (day && !day.exercises.some((n) => n.toLowerCase() === ex.name.toLowerCase())) {
      items.push({ label: `Add to ${day.name} plan`, fn: () => { addTemplateExercise(p, day, ex.name); commit(); toast(`Added to ${day.name}`); } });
    }
    const remove = () => { s.exercises = s.exercises.filter((e) => e.id !== ex.id); commit(); };
    items.push({
      label: 'Remove from this workout', danger: true,
      fn: () => ex.sets.length
        ? confirmDialog(`Remove ${ex.name}?`, `Its ${ex.sets.length} logged sets will be removed from this workout.`, 'Remove', remove, true)
        : remove(),
    });
    actionSheet(ex.name, items);
  },
  addSet(d) { setDialog(d.s, d.ex, -1); },
  editSet(d) { setDialog(d.s, d.ex, +d.i); },
  repeatSet(d) {
    const { ex } = exerciseOf(d);
    const last = ex.sets[ex.sets.length - 1];
    ex.sets.push({ ...last, id: uid(), drops: (last.drops || []).map((x) => ({ ...x })) });
    commit();
  },
  step(d) {
    const inp = document.getElementById(d.t);
    const n = Math.max(0, (parseNum(inp.value) || 0) + +d.d);
    inp.value = d.dec === '1' ? fmtNum(n) : String(Math.round(n));
  },

  // history
  openSession(d) { openSub({ type: 'session', id: d.id }); },
  deleteSession(d) {
    confirmDialog('Delete this workout?', 'It will be removed from history and stats.', 'Delete', () => {
      data.sessions = data.sessions.filter((s) => s.id !== d.id);
      save();
      history.back();
    }, true);
  },

  // stats
  statsMode(d) { ui.statsMonthly = d.m === 'month'; ui.statsOffset = 0; render(); },
  statsPrev() { ui.statsOffset--; render(); },
  statsNext() { if (ui.statsOffset < 0) ui.statsOffset++; render(); },

  // settings
  unit(d) { data.weightUnit = d.u; commit(); },
  exportBackup() { shareFile(`workout-backup-${today()}.json`, JSON.stringify(data, null, 2), 'Workout backup'); },
  importBackup() {
    pickFile((text) => {
      let parsed;
      try {
        parsed = JSON.parse(text);
        if (!parsed || !Array.isArray(parsed.plans) || !Array.isArray(parsed.sessions)) throw new Error('bad');
      } catch (e) {
        if (parseSharedPlan(text)) { importPlanDialog(text); return; }
        toast("That file isn't a valid backup");
        return;
      }
      confirmDialog('Replace all data?', 'Importing this backup replaces every plan and workout currently in the app.', 'Import', () => {
        data = normalize(parsed);
        commit();
        toast('Backup imported');
      }, true);
    });
  },
};

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-a]');
  if (!el || el.disabled) return;
  const a = el.dataset.a;
  if (a.startsWith('m:')) {
    const n = a.slice(2);
    if (n === 'backdrop') { if (e.target === el) closeModal(); return; }
    if (n === 'close') { closeModal(); return; }
    if (modalHandlers[n]) modalHandlers[n](el, e);
    return;
  }
  if (actions[a]) actions[a](el.dataset, el, e);
});

// Keep the workout timer in the header fresh.
setInterval(() => {
  const el = document.getElementById('elapsed');
  if (el && data.current) el.innerHTML = elapsedText(data.current);
}, 15000);

// Other tabs/windows changing data.
window.addEventListener('storage', (e) => { if (e.key === KEY) { data = load(); render(); } });

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('sw.js').catch(() => {}));
}
if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});

history.replaceState(null, '');
render();
