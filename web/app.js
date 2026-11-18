'use strict';

/* Fitness Center Manager: menu-driven front end for the Java + SQLite API.
   Plain JavaScript, no libraries. All text reaches the page through textContent
   (via h()), never innerHTML, so data from the database can't inject markup. */

// ------------------------------------------------------------------ helpers

/** Creates a DOM element: h('button', {class: 'primary', onclick: fn}, 'Save') */
function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value == null || value === false) continue;
    if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (key === 'class') el.className = value;
    else if (key in el) el[key] = value;
    else el.setAttribute(key, value);
  }
  for (const child of children.flat(Infinity)) {
    if (child != null && child !== false) el.append(child instanceof Node ? child : String(child));
  }
  return el;
}

const $ = (selector) => document.querySelector(selector);

async function request(method, url, body) {
  const response = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error((data && data.error) || `Request failed (${response.status})`);
  return data;
}

const api = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body || {}),
  put: (url, body) => request('PUT', url, body),
  del: (url) => request('DELETE', url),
};

const pad = (n) => String(n).padStart(2, '0');
const isoDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const isoDateTime = (d) => `${isoDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
const today = () => isoDate(new Date());

const fmtDate = (s) => (s ? new Date(`${s}T00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—');
const fmtTime = (s) => new Date(s).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
const fmtDay = (s) => new Date(s).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
const money = (n) => (n == null ? '—' : `$${Number(n).toFixed(2)}`);
const dash = (v) => (v == null || v === '' ? '—' : v);
const fullName = (p) => `${p.first_name} ${p.last_name}`;
const titleCase = (s) => s.charAt(0).toUpperCase() + s.slice(1).replace(/_/g, ' ');

/** Two-line "when" cell: the day on top, the time range underneath. */
const when = (row) => [fmtDay(row.start_time), h('span', { class: 'sub' }, `${fmtTime(row.start_time)} – ${fmtTime(row.end_time)}`)];

function debounce(fn, ms = 250) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

// ------------------------------------------------------------ UI components

const TONES = {
  active: 'good', frozen: 'warn', cancelled: 'bad',
  scheduled: 'info', completed: 'good',
  operational: 'good', out_of_order: 'bad', under_maintenance: 'warn',
  open: 'bad', in_progress: 'warn',
  class: 'info', training: 'good', booking: 'warn',
};

const badge = (value, text) => h('span', { class: `badge ${TONES[value] || ''}` }, text || titleCase(value));

const button = (text, onclick, cls) => h('button', { type: 'button', class: cls, onclick }, text);

/** Row-level action button: small, and reports failures as a toast instead of failing silently. */
const action = (text, fn, cls = '') =>
  button(text, async () => {
    try {
      await fn();
    } catch (error) {
      toast(error.message, true);
    }
  }, `small ${cls}`);

const rowActions = (...buttons) => h('div', { class: 'row-actions' }, buttons);

function card(title, extras, body) {
  return h('section', { class: 'card' },
    h('div', { class: 'card-head' }, h('h2', null, title), extras),
    body);
}

function table(columns, rows, emptyText) {
  if (!rows.length) return h('div', { class: 'empty' }, emptyText || 'Nothing here yet.');
  return h('div', { class: 'table-wrap' },
    h('table', null,
      h('thead', null, h('tr', null, columns.map((c) => h('th', { class: c.class }, c.label)))),
      h('tbody', null, rows.map((row) => h('tr', null, columns.map((c) => h('td', { class: c.class }, c.cell(row))))))));
}

function toast(message, isError) {
  const el = h('div', { class: `toast${isError ? ' error' : ''}`, role: 'status' }, message);
  $('#toasts').append(el);
  setTimeout(() => el.remove(), isError ? 6000 : 3000);
}

function modal(title, size) {
  const body = h('div', { class: 'dialog-body' });
  const dialog = h('dialog', { class: size },
    h('div', { class: 'dialog-head' },
      h('h2', null, title),
      h('button', { type: 'button', 'aria-label': 'Close', onclick: () => dialog.close() }, '×')),
    body);
  document.body.append(dialog);
  const closed = new Promise((resolve) => {
    dialog.addEventListener('close', () => {
      dialog.remove();
      resolve();
    });
  });
  dialog.showModal();
  return { body, closed, close: () => dialog.close() };
}

/** Asks before a destructive action. Resolves true only if the user confirms. */
function confirmAction(message, confirmLabel = 'Delete') {
  return new Promise((resolve) => {
    const m = modal('Please confirm', 'narrow');
    let confirmed = false;
    m.body.append(
      h('p', { style: 'margin: 0' }, message),
      h('div', { class: 'dialog-foot' },
        button('Keep it', m.close),
        button(confirmLabel, () => { confirmed = true; m.close(); }, 'danger solid')));
    m.closed.then(() => resolve(confirmed));
  });
}

function control(field, value) {
  let el;
  if (field.type === 'select') {
    el = h('select', { required: field.required });
    if (!field.required || value == null || value === '') {
      el.append(h('option', { value: '' }, field.required ? 'Select…' : field.none || '— None —'));
    }
    for (const option of field.options) el.append(h('option', { value: String(option.value) }, option.label));
  } else if (field.type === 'textarea') {
    el = h('textarea', { required: field.required, rows: 3 });
  } else {
    el = h('input', {
      type: field.type || 'text', required: field.required,
      min: field.min, max: field.max, step: field.step, placeholder: field.placeholder,
    });
  }
  el.name = field.name;
  el.value = value == null ? '' : String(value);
  return el;
}

/**
 * Opens a form in a dialog. onSubmit receives {fieldName: value}; if it throws
 * (for example a scheduling conflict from the server) the message is shown in
 * the form and the dialog stays open. Resolves with onSubmit's result, or null if cancelled.
 */
function openForm({ title, fields, values = {}, submitLabel = 'Save', setup, onSubmit }) {
  return new Promise((resolve) => {
    const m = modal(title);
    const inputs = {};
    let result = null;
    const error = h('p', { class: 'form-error', role: 'alert', hidden: true });
    const submit = h('button', { type: 'submit', class: 'primary' }, submitLabel);

    const form = h('form', {
      onsubmit: async (event) => {
        event.preventDefault();
        const data = {};
        for (const field of fields) {
          const raw = inputs[field.name].value.trim();
          data[field.name] = raw === '' ? null : field.type === 'number' ? Number(raw) : raw;
        }
        submit.disabled = true;
        try {
          result = (await onSubmit(data)) || true;
          m.close();
        } catch (err) {
          error.textContent = err.message;
          error.hidden = false;
          submit.disabled = false;
        }
      },
    },
      h('div', { class: 'form-grid' }, fields.map((field) => {
        inputs[field.name] = control(field, values[field.name] ?? field.value);
        return h('label', { class: `field${field.wide ? ' wide' : ''}` },
          h('span', null, field.label + (field.required ? ' *' : '')), inputs[field.name]);
      })),
      error,
      h('div', { class: 'dialog-foot' }, button('Cancel', m.close), submit));

    m.body.append(form);
    if (setup) setup(inputs);
    inputs[fields[0].name].focus();
    m.closed.then(() => resolve(result));
  });
}

/** When a start time is picked and no end time is set yet, default the end to start + N minutes. */
const autoEnd = (minutes) => (inputs) => {
  inputs.start_time.addEventListener('change', () => {
    if (inputs.start_time.value && !inputs.end_time.value) {
      inputs.end_time.value = isoDateTime(new Date(new Date(inputs.start_time.value).getTime() + minutes * 60000));
    }
  });
};

const opts = {
  members: (members, keepId) => members
    .filter((m) => m.status === 'active' || m.member_id === keepId)
    .map((m) => ({ value: m.member_id, label: fullName(m) + (m.status === 'active' ? '' : ` (${m.status})`) })),
  trainers: (trainers) => trainers.map((t) => ({ value: t.trainer_id, label: fullName(t) + (t.specialty ? ` · ${t.specialty}` : '') })),
  rooms: (rooms) => rooms.map((r) => ({ value: r.room_id, label: `${r.name} (holds ${r.capacity})` })),
  list: (...values) => values.map((v) => ({ value: v, label: titleCase(v) })),
};

// Filter choices survive moving between menu items.
const filters = {
  memberQuery: '', memberStatus: '', classesAll: false, trainingAll: false,
  scheduleDate: today(), scheduleRoom: '', ticketsAll: false,
};

// -------------------------------------------------------------------- views

const views = {

  async dashboard(page) {
    const { stats, today: todayRows, tickets } = await api.get('/api/dashboard');
    page.set(new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' }));

    const stat = (label, value, note, alert) => h('div', { class: `card stat${alert ? ' alert' : ''}` },
      h('div', { class: 'label' }, label), h('div', { class: 'value' }, value), h('div', { class: 'note' }, note || ' '));

    page.root.replaceChildren(
      h('div', { class: 'stats' },
        stat('Active members', stats.active_members, `${stats.total_members} on file`),
        stat('Classes, next 7 days', stats.classes_this_week),
        stat('Training sessions, next 7 days', stats.sessions_this_week),
        stat('Membership revenue / month', money(stats.monthly_revenue), 'from active members'),
        stat('Open maintenance tickets', stats.open_tickets, `${stats.equipment_down} item(s) out of service`, stats.equipment_down > 0)),
      h('div', { class: 'columns' },
        card("Today's schedule", h('a', { href: '#rooms', class: 'hint' }, 'Room calendar →'),
          table([
            { label: 'Time', class: 'nowrap', cell: (r) => `${fmtTime(r.start_time)} – ${fmtTime(r.end_time)}` },
            { label: 'What', cell: (r) => [h('strong', null, r.title), h('span', { class: 'sub' }, dash(r.trainer_name))] },
            { label: 'Room', cell: (r) => dash(r.room_name) },
            { label: 'Type', cell: (r) => badge(r.kind) },
          ], todayRows, 'Nothing scheduled today.')),
        card('Open maintenance', h('a', { href: '#maintenance', class: 'hint' }, 'All tickets →'),
          table([
            { label: 'Equipment', cell: (t) => [h('strong', null, t.equipment_name), h('span', { class: 'sub' }, t.description)] },
            { label: 'Status', cell: (t) => badge(t.status) },
          ], tickets, 'No open tickets. Everything is in working order.'))));
  },

  // ---------------------------------------------------------------- members

  async members(page) {
    const plans = await api.get('/api/plans');
    const host = h('div');

    const fields = () => [
      { name: 'first_name', label: 'First name', required: true },
      { name: 'last_name', label: 'Last name', required: true },
      { name: 'email', label: 'Email', type: 'email', required: true },
      { name: 'phone', label: 'Phone', type: 'tel' },
      { name: 'date_of_birth', label: 'Date of birth', type: 'date', max: today() },
      { name: 'emergency_contact', label: 'Emergency contact', placeholder: 'Name and phone' },
      { name: 'plan_id', label: 'Membership plan', type: 'select', required: true,
        options: plans.map((p) => ({ value: p.plan_id, label: `${p.name} · ${money(p.monthly_fee)}/month` })) },
      { name: 'status', label: 'Status', type: 'select', required: true, value: 'active', options: opts.list('active', 'frozen', 'cancelled') },
      { name: 'join_date', label: 'Join date', type: 'date', value: today() },
    ];

    const register = async () => {
      if (await openForm({ title: 'Register member', fields: fields(), submitLabel: 'Register', onSubmit: (d) => api.post('/api/members', d) })) {
        toast('Member registered');
        load();
      }
    };
    const edit = async (member) => {
      if (await openForm({ title: `Edit ${fullName(member)}`, fields: fields(), values: member, onSubmit: (d) => api.put(`/api/members/${member.member_id}`, d) })) {
        toast('Member updated');
        load();
      }
    };
    const remove = async (member) => {
      if (!(await confirmAction(`Delete ${fullName(member)}? Their class enrollments, training sessions and room bookings will be removed too. To keep the history, set the membership to "cancelled" instead.`))) return;
      await api.del(`/api/members/${member.member_id}`);
      toast('Member deleted');
      load();
    };
    const view = async (member) => {
      const d = await api.get(`/api/members/${member.member_id}`);
      const m = modal(fullName(d), 'wide');
      const item = (label, value) => h('div', null, h('dt', null, label), h('dd', null, value));
      m.body.append(
        h('dl', { class: 'detail' },
          item('Status', badge(d.status)), item('Plan', `${d.plan_name} · ${money(d.monthly_fee)}/month`),
          item('Member since', fmtDate(d.join_date)), item('Email', d.email), item('Phone', dash(d.phone)),
          item('Date of birth', fmtDate(d.date_of_birth)), item('Emergency contact', dash(d.emergency_contact))),
        h('h3', null, `Classes (${d.classes.length})`),
        h('div', { class: 'card' }, table([
          { label: 'Class', cell: (c) => h('strong', null, c.name) },
          { label: 'When', cell: when },
          { label: 'Room', cell: (c) => c.room_name },
          { label: 'Status', cell: (c) => badge(c.status) },
        ], d.classes, 'Not enrolled in any classes.')),
        h('h3', null, `Personal training (${d.sessions.length})`),
        h('div', { class: 'card' }, table([
          { label: 'Trainer', cell: (s) => h('strong', null, s.trainer_name) },
          { label: 'When', cell: when },
          { label: 'Status', cell: (s) => badge(s.status) },
        ], d.sessions, 'No training sessions.')));
    };

    async function load() {
      const query = new URLSearchParams({ q: filters.memberQuery, status: filters.memberStatus });
      const rows = await api.get(`/api/members?${query}`);
      page.set(`${rows.length} ${rows.length === 1 ? 'member' : 'members'} shown`, button('Register member', register, 'primary'));
      host.replaceChildren(table([
        { label: 'Member', cell: (m) => [h('strong', null, fullName(m)), h('span', { class: 'sub' }, m.email)] },
        { label: 'Phone', class: 'nowrap', cell: (m) => dash(m.phone) },
        { label: 'Plan', cell: (m) => m.plan_name },
        { label: 'Status', cell: (m) => badge(m.status) },
        { label: 'Joined', class: 'nowrap', cell: (m) => fmtDate(m.join_date) },
        { label: '', cell: (m) => rowActions(action('View', () => view(m)), action('Edit', () => edit(m)), action('Delete', () => remove(m), 'danger')) },
      ], rows, 'No members match.'));
    }

    const search = h('input', { type: 'search', placeholder: 'Search name, email or phone', value: filters.memberQuery, 'aria-label': 'Search members' });
    search.addEventListener('input', debounce(() => { filters.memberQuery = search.value; load(); }));
    const status = control({ type: 'select', name: 'status', none: 'All statuses', options: opts.list('active', 'frozen', 'cancelled') }, filters.memberStatus);
    status.setAttribute('aria-label', 'Filter by status');
    status.addEventListener('change', () => { filters.memberStatus = status.value; load(); });

    page.root.replaceChildren(card('Member directory', h('div', { class: 'toolbar' }, search, status), host));
    await load();
  },

  // --------------------------------------------------------------- trainers

  async trainers(page) {
    const host = h('div');
    const fields = [
      { name: 'first_name', label: 'First name', required: true },
      { name: 'last_name', label: 'Last name', required: true },
      { name: 'email', label: 'Email', type: 'email', required: true },
      { name: 'phone', label: 'Phone', type: 'tel' },
      { name: 'specialty', label: 'Specialty', wide: true, placeholder: 'e.g. Yoga & Pilates' },
    ];
    const add = async () => {
      if (await openForm({ title: 'Add trainer', fields, onSubmit: (d) => api.post('/api/trainers', d) })) { toast('Trainer added'); load(); }
    };
    const edit = async (t) => {
      if (await openForm({ title: `Edit ${fullName(t)}`, fields, values: t, onSubmit: (d) => api.put(`/api/trainers/${t.trainer_id}`, d) })) { toast('Trainer updated'); load(); }
    };
    const remove = async (t) => {
      if (!(await confirmAction(`Delete trainer ${fullName(t)}?`))) return;
      await api.del(`/api/trainers/${t.trainer_id}`);
      toast('Trainer deleted');
      load();
    };

    async function load() {
      const rows = await api.get('/api/trainers');
      page.set(`${rows.length} on staff`, button('Add trainer', add, 'primary'));
      host.replaceChildren(table([
        { label: 'Trainer', cell: (t) => [h('strong', null, fullName(t)), h('span', { class: 'sub' }, t.email)] },
        { label: 'Phone', class: 'nowrap', cell: (t) => dash(t.phone) },
        { label: 'Specialty', cell: (t) => dash(t.specialty) },
        { label: 'Upcoming', cell: (t) => `${t.upcoming} ${t.upcoming === 1 ? 'booking' : 'bookings'}` },
        { label: '', cell: (t) => rowActions(action('Edit', () => edit(t)), action('Delete', () => remove(t), 'danger')) },
      ], rows, 'No trainers yet.'));
    }

    page.root.replaceChildren(card('Trainers', null, host));
    await load();
  },

  // ---------------------------------------------------------------- classes

  async classes(page) {
    const [trainers, rooms] = await Promise.all([api.get('/api/trainers'), api.get('/api/rooms')]);
    const host = h('div');

    const fields = (editing) => [
      { name: 'name', label: 'Class name', required: true, placeholder: 'e.g. Morning Yoga' },
      { name: 'capacity', label: 'Capacity', type: 'number', min: 1, step: 1, required: true },
      { name: 'trainer_id', label: 'Trainer', type: 'select', required: true, options: opts.trainers(trainers) },
      { name: 'room_id', label: 'Room', type: 'select', required: true, options: opts.rooms(rooms) },
      { name: 'start_time', label: 'Starts', type: 'datetime-local', required: true },
      { name: 'end_time', label: 'Ends', type: 'datetime-local', required: true },
      { name: 'description', label: 'Description', wide: true },
      ...(editing ? [{ name: 'status', label: 'Status', type: 'select', required: true, options: opts.list('scheduled', 'cancelled') }] : []),
    ];

    const add = async () => {
      if (await openForm({ title: 'Schedule a class', fields: fields(false), submitLabel: 'Schedule', setup: autoEnd(60), onSubmit: (d) => api.post('/api/classes', d) })) {
        toast('Class scheduled');
        load();
      }
    };
    const edit = async (c) => {
      if (await openForm({ title: `Edit ${c.name}`, fields: fields(true), values: c, onSubmit: (d) => api.put(`/api/classes/${c.class_id}`, d) })) {
        toast('Class updated');
        load();
      }
    };
    const remove = async (c) => {
      const note = c.enrolled ? ` ${c.enrolled} enrolled member(s) will be removed from it.` : '';
      if (!(await confirmAction(`Delete "${c.name}" on ${fmtDay(c.start_time)}?${note}`))) return;
      await api.del(`/api/classes/${c.class_id}`);
      toast('Class deleted');
      load();
    };

    // Roster dialog: who is enrolled, plus enroll / remove.
    const roster = async (c) => {
      const members = await api.get('/api/members?status=active');
      const m = modal(`${c.name} · ${fmtDay(c.start_time)}, ${fmtTime(c.start_time)}`, 'wide');
      const list = h('div', { class: 'card' });
      const picker = h('select', { 'aria-label': 'Member to enroll' });
      const error = h('p', { class: 'form-error', role: 'alert', hidden: true });
      const summary = h('p', { style: 'margin: 0 0 12px', class: 'hint' });

      const refresh = async () => {
        error.hidden = true;
        const enrolled = await api.get(`/api/classes/${c.class_id}/enrollments`);
        const ids = new Set(enrolled.map((e) => e.member_id));
        summary.textContent = `${enrolled.length} of ${c.capacity} spots taken · ${c.room_name} · ${c.trainer_name}`;
        list.replaceChildren(table([
          { label: 'Member', cell: (e) => [h('strong', null, fullName(e)), h('span', { class: 'sub' }, e.email)] },
          { label: 'Enrolled', class: 'nowrap', cell: (e) => fmtDay(e.enrolled_at) },
          { label: '', cell: (e) => rowActions(action('Remove', async () => {
            await api.del(`/api/classes/${c.class_id}/enrollments/${e.member_id}`);
            await refresh();
          }, 'danger')) },
        ], enrolled, 'Nobody is enrolled yet.'));
        picker.replaceChildren(h('option', { value: '' }, 'Choose a member…'),
          ...members.filter((x) => !ids.has(x.member_id)).map((x) => h('option', { value: String(x.member_id) }, fullName(x))));
      };

      const enroll = async (event) => {
        event.preventDefault();
        error.hidden = true;
        if (!picker.value) return;
        try {
          await api.post(`/api/classes/${c.class_id}/enrollments`, { member_id: Number(picker.value) });
          await refresh();
        } catch (err) {
          error.textContent = err.message;
          error.hidden = false;
        }
      };

      m.body.append(summary, list,
        h('form', { class: 'inline-form', onsubmit: enroll }, picker, h('button', { type: 'submit', class: 'primary' }, 'Enroll')),
        error);
      await refresh();
      m.closed.then(load);
    };

    async function load() {
      const rows = await api.get(`/api/classes?scope=${filters.classesAll ? 'all' : 'upcoming'}`);
      page.set(`${rows.length} ${filters.classesAll ? 'total' : 'upcoming'}`, button('Schedule class', add, 'primary'));
      host.replaceChildren(table([
        { label: 'Class', cell: (c) => [h('strong', null, c.name), h('span', { class: 'sub' }, dash(c.description))] },
        { label: 'When', class: 'nowrap', cell: when },
        { label: 'Trainer', cell: (c) => c.trainer_name },
        { label: 'Room', cell: (c) => c.room_name },
        { label: 'Spots', class: 'nowrap', cell: (c) => [`${c.enrolled} / ${c.capacity}`,
          h('span', { class: `meter${c.enrolled >= c.capacity ? ' full' : ''}` }, h('i', { style: `width: ${Math.min(100, (c.enrolled / c.capacity) * 100)}%` }))] },
        { label: 'Status', cell: (c) => badge(c.status) },
        { label: '', cell: (c) => rowActions(action('Roster', () => roster(c)), action('Edit', () => edit(c)), action('Delete', () => remove(c), 'danger')) },
      ], rows, 'No classes scheduled.'));
    }

    const past = h('input', { type: 'checkbox', checked: filters.classesAll, style: 'width: auto' });
    past.addEventListener('change', () => { filters.classesAll = past.checked; load(); });
    page.root.replaceChildren(card('Class schedule', h('label', { class: 'toolbar hint' }, past, 'Include past classes'), host));
    await load();
  },

  // ------------------------------------------------------ personal training

  async training(page) {
    const [members, trainers, rooms] = await Promise.all([api.get('/api/members'), api.get('/api/trainers'), api.get('/api/rooms')]);
    const host = h('div');

    const fields = (session) => [
      { name: 'member_id', label: 'Member', type: 'select', required: true, options: opts.members(members, session && session.member_id) },
      { name: 'trainer_id', label: 'Trainer', type: 'select', required: true, options: opts.trainers(trainers) },
      { name: 'start_time', label: 'Starts', type: 'datetime-local', required: true },
      { name: 'end_time', label: 'Ends', type: 'datetime-local', required: true },
      { name: 'room_id', label: 'Room', type: 'select', none: '— No room needed —', options: opts.rooms(rooms) },
      ...(session ? [{ name: 'status', label: 'Status', type: 'select', required: true, options: opts.list('scheduled', 'completed', 'cancelled') }] : []),
      { name: 'notes', label: 'Notes', type: 'textarea', wide: true },
    ];

    const add = async () => {
      if (await openForm({ title: 'Book a training session', fields: fields(null), submitLabel: 'Book', setup: autoEnd(60), onSubmit: (d) => api.post('/api/training', d) })) {
        toast('Session booked');
        load();
      }
    };
    const edit = async (s) => {
      if (await openForm({ title: `Edit session for ${s.member_name}`, fields: fields(s), values: s, onSubmit: (d) => api.put(`/api/training/${s.session_id}`, d) })) {
        toast('Session updated');
        load();
      }
    };
    const setStatus = async (s, status, message) => {
      await api.post(`/api/training/${s.session_id}/status`, { status });
      toast(message);
      load();
    };
    const remove = async (s) => {
      if (!(await confirmAction(`Delete ${s.member_name}'s session on ${fmtDay(s.start_time)}?`))) return;
      await api.del(`/api/training/${s.session_id}`);
      toast('Session deleted');
      load();
    };

    async function load() {
      const rows = await api.get(`/api/training?scope=${filters.trainingAll ? 'all' : 'upcoming'}`);
      page.set(`${rows.length} ${filters.trainingAll ? 'total' : 'upcoming'}`, button('Book session', add, 'primary'));
      host.replaceChildren(table([
        { label: 'Member', cell: (s) => [h('strong', null, s.member_name), h('span', { class: 'sub' }, dash(s.notes))] },
        { label: 'Trainer', cell: (s) => s.trainer_name },
        { label: 'When', class: 'nowrap', cell: when },
        { label: 'Room', cell: (s) => dash(s.room_name) },
        { label: 'Status', cell: (s) => badge(s.status) },
        { label: '', cell: (s) => rowActions(
          s.status === 'scheduled' && action('Complete', () => setStatus(s, 'completed', 'Session marked completed')),
          s.status === 'scheduled' && action('Cancel', () => setStatus(s, 'cancelled', 'Session cancelled')),
          action('Edit', () => edit(s)), action('Delete', () => remove(s), 'danger')) },
      ], rows, 'No training sessions booked.'));
    }

    const past = h('input', { type: 'checkbox', checked: filters.trainingAll, style: 'width: auto' });
    past.addEventListener('change', () => { filters.trainingAll = past.checked; load(); });
    page.root.replaceChildren(card('Training sessions', h('label', { class: 'toolbar hint' }, past, 'Include past sessions'), host));
    await load();
  },

  // ----------------------------------------------------------- room booking

  async rooms(page) {
    const members = await api.get('/api/members');
    let rooms = [];
    const calendarHost = h('div');
    const bookingsHost = h('div');
    const roomsHost = h('div');

    const bookingFields = (booking) => [
      { name: 'room_id', label: 'Room', type: 'select', required: true, options: opts.rooms(rooms) },
      { name: 'member_id', label: 'Booked for', type: 'select', none: '— Staff / gym event —', options: opts.members(members, booking && booking.member_id) },
      { name: 'start_time', label: 'From', type: 'datetime-local', required: true },
      { name: 'end_time', label: 'Until', type: 'datetime-local', required: true },
      { name: 'purpose', label: 'Purpose', required: true, wide: true, placeholder: 'e.g. Private stretching group' },
    ];
    const roomFields = [
      { name: 'name', label: 'Room name', required: true },
      { name: 'room_type', label: 'Type', required: true, placeholder: 'e.g. Group studio' },
      { name: 'capacity', label: 'Capacity (people)', type: 'number', min: 1, step: 1, required: true },
    ];

    const book = async () => {
      const values = { room_id: filters.scheduleRoom, start_time: `${filters.scheduleDate}T09:00`, end_time: `${filters.scheduleDate}T10:00` };
      if (await openForm({ title: 'Book a room', fields: bookingFields(null), values, submitLabel: 'Book', onSubmit: (d) => api.post('/api/bookings', d) })) {
        toast('Room booked');
        loadBookings();
      }
    };
    const editBooking = async (b) => {
      if (await openForm({ title: 'Edit booking', fields: bookingFields(b), values: b, onSubmit: (d) => api.put(`/api/bookings/${b.booking_id}`, d) })) {
        toast('Booking updated');
        loadBookings();
      }
    };
    const cancelBooking = async (b) => {
      if (!(await confirmAction(`Cancel the booking "${b.purpose}" in ${b.room_name}?`, 'Cancel booking'))) return;
      await api.del(`/api/bookings/${b.booking_id}`);
      toast('Booking cancelled');
      loadBookings();
    };
    const addRoom = async () => {
      if (await openForm({ title: 'Add room', fields: roomFields, onSubmit: (d) => api.post('/api/rooms', d) })) { toast('Room added'); loadRooms(); }
    };
    const editRoom = async (r) => {
      if (await openForm({ title: `Edit ${r.name}`, fields: roomFields, values: r, onSubmit: (d) => api.put(`/api/rooms/${r.room_id}`, d) })) { toast('Room updated'); loadRooms(); }
    };
    const removeRoom = async (r) => {
      if (!(await confirmAction(`Delete ${r.name}?`))) return;
      await api.del(`/api/rooms/${r.room_id}`);
      toast('Room deleted');
      loadRooms();
    };

    const date = h('input', { type: 'date', value: filters.scheduleDate, 'aria-label': 'Calendar date' });
    const roomFilter = h('select', { 'aria-label': 'Filter by room' });
    date.addEventListener('change', () => { filters.scheduleDate = date.value || today(); loadCalendar(); });
    roomFilter.addEventListener('change', () => { filters.scheduleRoom = roomFilter.value; loadCalendar(); });

    async function loadCalendar() {
      const query = new URLSearchParams({ date: filters.scheduleDate, room_id: filters.scheduleRoom });
      const rows = await api.get(`/api/schedule?${query}`);
      calendarHost.replaceChildren(table([
        { label: 'Time', class: 'nowrap', cell: (r) => `${fmtTime(r.start_time)} – ${fmtTime(r.end_time)}` },
        { label: 'Room', cell: (r) => (r.room_name ? h('strong', null, r.room_name) : 'No room') },
        { label: 'In use for', cell: (r) => [r.title, r.trainer_name && h('span', { class: 'sub' }, r.trainer_name)] },
        { label: 'Type', cell: (r) => badge(r.kind) },
      ], rows, 'Nothing booked for this day. Every room is free.'));
    }

    async function loadBookings() {
      const rows = await api.get('/api/bookings');
      bookingsHost.replaceChildren(table([
        { label: 'Room', cell: (b) => h('strong', null, b.room_name) },
        { label: 'When', class: 'nowrap', cell: when },
        { label: 'Purpose', cell: (b) => b.purpose },
        { label: 'Booked for', cell: (b) => b.member_name || 'Staff / gym event' },
        { label: '', cell: (b) => rowActions(action('Edit', () => editBooking(b)), action('Cancel', () => cancelBooking(b), 'danger')) },
      ], rows, 'No upcoming room bookings.'));
      await loadCalendar();
    }

    async function loadRooms() {
      rooms = await api.get('/api/rooms');
      page.set(`${rooms.length} rooms`, button('Book a room', book, 'primary'));
      roomFilter.replaceChildren(h('option', { value: '' }, 'All rooms'), ...rooms.map((r) => h('option', { value: String(r.room_id) }, r.name)));
      roomFilter.value = filters.scheduleRoom;
      roomsHost.replaceChildren(table([
        { label: 'Room', cell: (r) => h('strong', null, r.name) },
        { label: 'Type', cell: (r) => r.room_type },
        { label: 'Capacity', cell: (r) => `${r.capacity} people` },
        { label: 'Equipment', cell: (r) => `${r.equipment_count} ${r.equipment_count === 1 ? 'item' : 'items'}` },
        { label: '', cell: (r) => rowActions(action('Edit', () => editRoom(r)), action('Delete', () => removeRoom(r), 'danger')) },
      ], rooms, 'No rooms yet.'));
    }

    page.root.replaceChildren(
      card('Room calendar', h('div', { class: 'toolbar' }, date, roomFilter), calendarHost),
      card('Upcoming room bookings', h('span', { class: 'hint' }, 'Classes and training sessions reserve their rooms automatically'), bookingsHost),
      card('Rooms', button('Add room', addRoom), roomsHost));
    await loadRooms();
    await loadBookings();
  },

  // -------------------------------------------------------------- equipment

  async equipment(page) {
    const rooms = await api.get('/api/rooms');
    const host = h('div');

    const fields = (editing) => [
      { name: 'name', label: 'Name', required: true, placeholder: 'e.g. Treadmill #3' },
      { name: 'category', label: 'Category', required: true, placeholder: 'e.g. Cardio' },
      { name: 'serial_number', label: 'Serial number' },
      { name: 'room_id', label: 'Location', type: 'select', none: '— Unassigned / storage —', options: rooms.map((r) => ({ value: r.room_id, label: r.name })) },
      { name: 'purchase_date', label: 'Purchase date', type: 'date', max: today() },
      { name: 'last_service_date', label: 'Last serviced', type: 'date', max: today() },
      ...(editing ? [{ name: 'status', label: 'Status', type: 'select', required: true, options: opts.list('operational', 'out_of_order', 'under_maintenance', 'retired') }] : []),
    ];

    const add = async () => {
      if (await openForm({ title: 'Add equipment', fields: fields(false), onSubmit: (d) => api.post('/api/equipment', d) })) { toast('Equipment added'); load(); }
    };
    const edit = async (e) => {
      if (await openForm({ title: `Edit ${e.name}`, fields: fields(true), values: e, onSubmit: (d) => api.put(`/api/equipment/${e.equipment_id}`, d) })) { toast('Equipment updated'); load(); }
    };
    const remove = async (e) => {
      if (!(await confirmAction(`Delete ${e.name}? Its maintenance history will be deleted too. To keep the history, set its status to "retired" instead.`))) return;
      await api.del(`/api/equipment/${e.equipment_id}`);
      toast('Equipment deleted');
      load();
    };
    const report = async (e) => {
      const ticketFields = [
        { name: 'type', label: 'Type', type: 'select', required: true, value: 'repair',
          options: [{ value: 'repair', label: 'Repair (takes it out of service)' }, { value: 'routine', label: 'Routine service' }] },
        { name: 'technician', label: 'Assigned to' },
        { name: 'description', label: 'What needs doing?', type: 'textarea', required: true, wide: true },
      ];
      if (await openForm({ title: `Maintenance for ${e.name}`, fields: ticketFields, submitLabel: 'Open ticket',
        onSubmit: (d) => api.post('/api/maintenance', { ...d, equipment_id: e.equipment_id }) })) {
        toast('Maintenance ticket opened');
        load();
      }
    };

    async function load() {
      const rows = await api.get('/api/equipment');
      const down = rows.filter((e) => e.status === 'out_of_order' || e.status === 'under_maintenance').length;
      page.set(`${rows.length} items · ${down} out of service`, button('Add equipment', add, 'primary'));
      host.replaceChildren(table([
        { label: 'Equipment', cell: (e) => [h('strong', null, e.name), h('span', { class: 'sub' }, e.serial_number ? `S/N ${e.serial_number}` : 'No serial number')] },
        { label: 'Category', cell: (e) => e.category },
        { label: 'Location', cell: (e) => e.room_name || 'Unassigned' },
        { label: 'Status', cell: (e) => [badge(e.status), e.open_tickets > 0 && h('span', { class: 'sub' }, `${e.open_tickets} open ${e.open_tickets === 1 ? 'ticket' : 'tickets'}`)] },
        { label: 'Last serviced', class: 'nowrap', cell: (e) => fmtDate(e.last_service_date) },
        { label: '', cell: (e) => rowActions(
          e.status !== 'retired' && action('Report issue', () => report(e)),
          action('Edit', () => edit(e)), action('Delete', () => remove(e), 'danger')) },
      ], rows, 'No equipment on file.'));
    }

    page.root.replaceChildren(card('Equipment inventory', null, host));
    await load();
  },

  // ------------------------------------------------------------ maintenance

  async maintenance(page) {
    const equipment = await api.get('/api/equipment');
    const host = h('div');
    const equipmentOptions = equipment.map((e) => ({ value: e.equipment_id, label: e.room_name ? `${e.name} (${e.room_name})` : e.name }));

    const fields = (editing) => [
      { name: 'equipment_id', label: 'Equipment', type: 'select', required: true, options: equipmentOptions },
      { name: 'type', label: 'Type', type: 'select', required: true, value: 'repair', options: opts.list('repair', 'routine') },
      { name: 'description', label: 'What needs doing?', type: 'textarea', required: true, wide: true },
      { name: 'technician', label: 'Assigned to' },
      { name: 'reported_date', label: 'Reported', type: 'date', value: today(), max: today() },
      ...(editing ? [
        { name: 'status', label: 'Status', type: 'select', required: true, options: opts.list('open', 'in_progress', 'completed') },
        { name: 'completed_date', label: 'Completed', type: 'date', max: today() },
        { name: 'cost', label: 'Cost ($)', type: 'number', min: 0, step: 0.01 },
      ] : []),
      { name: 'notes', label: 'Notes', type: 'textarea', wide: true },
    ];

    const add = async () => {
      if (await openForm({ title: 'New maintenance ticket', fields: fields(false), submitLabel: 'Open ticket', onSubmit: (d) => api.post('/api/maintenance', d) })) {
        toast('Maintenance ticket opened');
        load();
      }
    };
    const edit = async (t) => {
      if (await openForm({ title: `Edit ticket: ${t.equipment_name}`, fields: fields(true), values: t, onSubmit: (d) => api.put(`/api/maintenance/${t.log_id}`, d) })) {
        toast('Ticket updated');
        load();
      }
    };
    const start = async (t) => {
      await api.put(`/api/maintenance/${t.log_id}`, { ...t, status: 'in_progress' });
      toast(`${t.equipment_name} is now under maintenance`);
      load();
    };
    const complete = async (t) => {
      const closeFields = [
        { name: 'completed_date', label: 'Completed on', type: 'date', required: true, value: today(), max: today() },
        { name: 'cost', label: 'Cost ($)', type: 'number', min: 0, step: 0.01 },
        { name: 'technician', label: 'Done by' },
        { name: 'notes', label: 'Work done', type: 'textarea', wide: true },
      ];
      if (await openForm({ title: `Complete: ${t.equipment_name}`, fields: closeFields, values: { technician: t.technician, notes: t.notes, cost: t.cost },
        submitLabel: 'Mark completed', onSubmit: (d) => api.put(`/api/maintenance/${t.log_id}`, { ...t, ...d, status: 'completed' }) })) {
        toast(`${t.equipment_name} is back in service`);
        load();
      }
    };
    const remove = async (t) => {
      if (!(await confirmAction(`Delete this maintenance ticket for ${t.equipment_name}?`))) return;
      await api.del(`/api/maintenance/${t.log_id}`);
      toast('Ticket deleted');
      load();
    };

    async function load() {
      const rows = await api.get(`/api/maintenance?scope=${filters.ticketsAll ? 'all' : 'open'}`);
      const open = rows.filter((t) => t.status !== 'completed').length;
      page.set(`${open} open ${open === 1 ? 'ticket' : 'tickets'}`, button('New ticket', add, 'primary'));
      host.replaceChildren(table([
        { label: 'Equipment', cell: (t) => [h('strong', null, t.equipment_name), h('span', { class: 'sub' }, t.room_name || 'Unassigned')] },
        { label: 'Work', class: 'wide', cell: (t) => [t.description, h('span', { class: 'sub' }, `${titleCase(t.type)}${t.notes ? ` · ${t.notes}` : ''}`)] },
        { label: 'Status', cell: (t) => badge(t.status) },
        { label: 'Reported', class: 'nowrap', cell: (t) => fmtDate(t.reported_date) },
        { label: 'Assigned to', cell: (t) => dash(t.technician) },
        ...(filters.ticketsAll ? [
          { label: 'Completed', class: 'nowrap', cell: (t) => fmtDate(t.completed_date) },
          { label: 'Cost', class: 'right', cell: (t) => money(t.cost) },
        ] : []),
        { label: '', cell: (t) => rowActions(
          t.status === 'open' && action('Start work', () => start(t)),
          t.status !== 'completed' && action('Complete', () => complete(t)),
          action('Edit', () => edit(t)), action('Delete', () => remove(t), 'danger')) },
      ], rows, 'No open maintenance tickets.'));
    }

    const all = h('input', { type: 'checkbox', checked: filters.ticketsAll, style: 'width: auto' });
    all.addEventListener('change', () => { filters.ticketsAll = all.checked; load(); });
    page.root.replaceChildren(card('Maintenance tickets', h('label', { class: 'toolbar hint' }, all, 'Include completed work'), host));
    await load();
  },
};

// --------------------------------------------------------------- menu, routing

const MENU = [
  { group: 'Overview' },
  { id: 'dashboard', label: 'Dashboard' },
  { group: 'People' },
  { id: 'members', label: 'Members' },
  { id: 'trainers', label: 'Trainers' },
  { group: 'Scheduling' },
  { id: 'classes', label: 'Classes' },
  { id: 'training', label: 'Personal Training' },
  { id: 'rooms', label: 'Room Booking' },
  { group: 'Facilities' },
  { id: 'equipment', label: 'Equipment' },
  { id: 'maintenance', label: 'Maintenance' },
];

let renderToken = 0;

async function route() {
  const requested = location.hash.slice(1);
  const item = MENU.find((entry) => entry.id === requested) || MENU[1];
  const token = ++renderToken;

  for (const link of document.querySelectorAll('#menu a')) {
    const current = link.getAttribute('href') === `#${item.id}`;
    link.classList.toggle('active', current);
    if (current) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
  }
  document.title = `${item.label} · Fitness Center Manager`;
  $('#page-title').textContent = item.label;
  $('#page-subtitle').textContent = '';
  $('#page-actions').replaceChildren();

  const root = h('div', null, h('div', { class: 'empty' }, 'Loading…'));
  $('#content').replaceChildren(root);

  // A view only updates the header if it is still the one on screen.
  const page = {
    root,
    set(subtitle, ...actions) {
      if (token !== renderToken) return;
      $('#page-subtitle').textContent = subtitle;
      $('#page-actions').replaceChildren(...actions);
    },
  };

  try {
    await views[item.id](page);
  } catch (error) {
    root.replaceChildren(h('div', { class: 'card empty' }, `Couldn't load this page: ${error.message}`));
  }
}

$('#menu').append(...MENU.map((entry) => (entry.group
  ? h('div', { class: 'group' }, entry.group)
  : h('a', { href: `#${entry.id}` }, entry.label))));

window.addEventListener('hashchange', route);
route();

api.get('/api/info')
  .then((info) => { $('#demo-banner').hidden = !info.demo; })
  .catch(() => {});
