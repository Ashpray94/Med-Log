// Meeting Timer widget renderer.
(() => {
  const $ = (id) => document.getElementById(id);
  const body = document.body;
  const stage = $('stage');
  const card = $('card');
  const capsule = $('capsule');
  const host = window.host || stubHost();

  const EASE = 'cubic-bezier(0.22, 1, 0.36, 1)';
  let cfg = { lead: 300e3, warn: 30e3, sound: true };
  let M = null;
  let list = [];            // every meeting currently shown, soonest first; M is list[0]
  let state = null;
  let mini = false;
  let lastSecond = null;

  // ------------------------------------------------------------ rolling digits
  // Each character sits in its own slot; when it changes, the old glyph slides out and the new
  // one slides in (transform/opacity only, via the Web Animations API).
  class Roller {
    constructor(el) { this.el = el; this.text = null; this.slots = []; }

    set(text, direction) {
      if (text === this.text) return;
      const prev = this.text;
      this.text = text;
      if (prev === null || prev.length !== text.length || prev[0] === '+' !== (text[0] === '+')) {
        this.build(text, prev !== null);
        return;
      }
      [...text].forEach((ch, i) => { if (ch !== prev[i]) this.roll(this.slots[i], ch, direction); });
    }

    build(text, animate) {
      this.el.textContent = '';
      this.slots = [...text].map((ch) => {
        const slot = document.createElement('span');
        slot.className = /\d/.test(ch) ? 'slot' : ch === '+' ? 'slot sign' : 'slot sep';
        slot.appendChild(glyph(ch));
        this.el.appendChild(slot);
        return slot;
      });
      if (animate) {
        this.el.animate([{ opacity: 0, transform: 'translateY(0.12em)' }, { opacity: 1, transform: 'none' }],
          { duration: 420, easing: EASE });
      }
    }

    roll(slot, ch, direction) {
      const old = slot.querySelector('.glyph:not(.out)');
      const next = glyph(ch);
      slot.appendChild(next);
      const d = 0.42 * direction;
      const opts = { duration: 460, easing: EASE, fill: 'both' };
      next.animate([
        { transform: `translateY(${-d}em)`, opacity: 0, filter: 'blur(2px)' },
        { transform: 'translateY(0)', opacity: 1, filter: 'blur(0)' },
      ], opts);
      if (!old) return;
      old.classList.add('out');
      old.animate([
        { transform: 'translateY(0)', opacity: 1, filter: 'blur(0)' },
        { transform: `translateY(${d}em)`, opacity: 0, filter: 'blur(2px)' },
      ], opts).onfinish = () => old.remove();
    }
  }

  function glyph(ch) {
    const g = document.createElement('span');
    g.className = 'glyph';
    g.textContent = ch;
    return g;
  }

  // ------------------------------------------------------------ split-flap tile
  // Static top shows the new value, static bottom the old one; the old top flap folds down
  // (ease-in, gaining shadow), then the new bottom flap lands (ease-out). Transform/opacity only.
  class FlipTile {
    constructor(el) {
      const q = (s) => el.querySelector(s);
      this.top = q('.half.top span');
      this.bottom = q('.half.bottom span');
      this.flapTop = q('.flap.top');
      this.flapBottom = q('.flap.bottom');
      this.value = null;
      this.anims = [];
    }

    set(value) {
      if (value === this.value) return;
      const old = this.value;
      this.value = value;
      this.anims.forEach((a) => a.cancel());
      this.anims = [];
      if (old === null || REDUCED_MOTION.matches) {
        this.top.textContent = this.bottom.textContent = value;
        return;
      }
      this.top.textContent = value;
      this.bottom.textContent = old;
      this.flapTop.firstChild.textContent = old;
      this.flapBottom.firstChild.textContent = value;
      this.flapTop.style.visibility = this.flapBottom.style.visibility = 'visible';

      const fold = { duration: 260, easing: 'cubic-bezier(0.55, 0, 0.9, 0.4)', fill: 'forwards' };
      const land = { duration: 340, delay: 250, easing: 'cubic-bezier(0.15, 0.7, 0.3, 1.12)', fill: 'backwards' };
      const shade = (flap, from, to, opts) => {
        const s = flap.querySelector('.shade') || flap.appendChild(Object.assign(document.createElement('i'), { className: 'shade' }));
        return s.animate([{ opacity: from }, { opacity: to }], opts);
      };
      this.anims = [
        this.flapTop.animate([{ transform: 'perspective(420px) rotateX(0deg)' }, { transform: 'perspective(420px) rotateX(-90deg)' }], fold),
        shade(this.flapTop, 0, 0.45, fold),
        this.flapBottom.animate([{ transform: 'perspective(420px) rotateX(90deg)' }, { transform: 'perspective(420px) rotateX(0deg)' }], land),
        shade(this.flapBottom, 0.4, 0, land),
      ];
      this.anims[2].onfinish = () => {
        this.bottom.textContent = value;
        this.flapTop.style.visibility = this.flapBottom.style.visibility = 'hidden';
        this.anims = [];
      };
    }
  }

  // Optical centering: fonts reserve room for descenders that digits never use, so a centered
  // line box leaves the numbers visibly high. Measure the real digit ink with the tile's own font
  // and shift it to the tile's true center (works for whichever font the platform ends up using).
  function centerDigits() {
    const tile = document.querySelector('.flip-card');
    if (!tile) return;
    const cs = getComputedStyle(tile);
    const size = parseFloat(cs.fontSize);
    const ctx = document.createElement('canvas').getContext('2d');
    ctx.font = `${cs.fontWeight} ${cs.fontStretch === '75%' ? 'condensed ' : ''}${size}px ${cs.fontFamily}`;
    const m = ctx.measureText('0123456789');
    if (!m.fontBoundingBoxAscent) return;
    const lineTop = (size - (m.fontBoundingBoxAscent + m.fontBoundingBoxDescent)) / 2; // line-height: 1
    const baseline = lineTop + m.fontBoundingBoxAscent;
    const inkCenter = baseline - (m.actualBoundingBoxAscent - m.actualBoundingBoxDescent) / 2;
    document.querySelector('.clock').style.setProperty('--digit-shift', `${(size / 2 - inkCenter).toFixed(2)}px`);
  }
  document.fonts.ready.then(centerDigits);

  const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)');
  const flipMin = new FlipTile($('flipMin'));
  const flipSec = new FlipTile($('flipSec'));
  const miniRoller = new Roller($('mini'));

  // ------------------------------------------------------------ countdown

  const fmt = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

  function update() {
    if (!M) return;
    const ms = M.start - Date.now();
    let text, next, second, shown;
    if (ms > 0) {
      second = Math.ceil(ms / 1000);
      shown = second;
      text = fmt(second);
      next = ms <= cfg.warn ? 'warn' : 'normal';
    } else {
      shown = Math.floor(-ms / 1000);
      second = -shown - 1;
      text = `+${fmt(shown)}`;
      next = 'late';
    }
    setState(next);
    const dir = next === 'late' ? -1 : 1; // counting down: new digits drop in from above
    miniRoller.set(text, dir);
    flipMin.set(String(Math.min(99, Math.floor(shown / 60))).padStart(2, '0'));
    flipSec.set(String(shown % 60).padStart(2, '0'));
    if (second !== lastSecond && shown % 15 === 0) {
      $('clock').setAttribute('aria-label', `${Math.floor(shown / 60)} minutes ${shown % 60} seconds ${next === 'late' ? 'late' : 'left'}`);
    }
    body.classList.toggle('tock', ((ms % 1000) + 1000) % 1000 < 500);

    if (second !== lastSecond) {
      const first = lastSecond === null;
      lastSecond = second;
      // alarm cues: 1 beep at 0:10, 2 at 0:05, 3 at 0:03, continuous from 0:00 until muted/joined
      if (!first && ALARM_CUES[second]) beeps(...ALARM_CUES[second]);
      if (next === 'late') startAlarm();
    }
  }

  const STATUS = { normal: 'Starts in', warn: 'Starting soon', late: 'Started · Join now' };

  function setState(next) {
    if (next === state) return;
    const prev = state;
    state = next;
    body.classList.remove('state-normal', 'state-warn', 'state-late');
    body.classList.add(`state-${next}`);
    // MedLog: the words come from the app (translated); the defaults are Meeting Timer's
    const W = window.__words || {};
    $('status').textContent = (W.status || STATUS)[next];
    $('miniLabel').textContent = next === 'late' ? 'Late by' : 'Starts in';
    $('labelMin').textContent = next === 'late' ? (W.minLate || 'Minutes late') : (W.min || 'Minutes');
    $('labelSec').textContent = next === 'late' ? (W.secLate || 'Seconds late') : (W.sec || 'Seconds');
  }

  // ------------------------------------------------------------ details

  const timeFmt = new Intl.DateTimeFormat([], { hour: 'numeric', minute: '2-digit' });

  function timeRange(a, b) {
    const pa = timeFmt.formatToParts(a);
    const pb = timeFmt.formatToParts(b);
    const period = (p) => p.find((x) => x.type === 'dayPeriod')?.value;
    const strip = (p) => p.filter((x) => x.type !== 'dayPeriod').map((x) => x.value).join('').trim();
    const first = period(pa) && period(pa) === period(pb) ? strip(pa) : timeFmt.format(a);
    return `${first} – ${timeFmt.format(b)}`;
  }

  const AVATAR_TONES = [
    ['#5e5ce6', '#8e8cff'], ['#0a84ff', '#64b5ff'], ['#30b0c7', '#6fd6e8'], ['#34c759', '#7ee29a'],
    ['#ff9f0a', '#ffc766'], ['#ff375f', '#ff7a94'], ['#bf5af2', '#d98cff'], ['#a2845e', '#cdb08a'],
  ];

  function avatar(person) {
    const el = document.createElement('span');
    el.className = 'avatar';
    const base = (person.name || person.email || '?').replace(/@.*/, '');
    const words = base.split(/[\s._-]+/).filter(Boolean);
    el.textContent = ((words[0]?.[0] || '?') + (words[1]?.[0] || '')).toUpperCase();
    let h = 0;
    for (const c of person.email || person.name) h = (h * 31 + c.charCodeAt(0)) >>> 0;
    const [a, b] = AVATAR_TONES[h % AVATAR_TONES.length];
    el.style.background = `linear-gradient(145deg, ${b}, ${a})`;
    return el;
  }

  const RSVP_LABEL = { accepted: 'Going', tentative: 'Maybe', declined: 'Declined', needsAction: 'Awaiting' };
  const RSVP_ORDER = { accepted: 0, tentative: 1, needsAction: 2, declined: 3 };

  function renderPeople(m) {
    const people = $('people');
    const list = [...m.attendees].sort((a, b) =>
      (b.organizer - a.organizer) || (b.self - a.self) || (RSVP_ORDER[a.status] - RSVP_ORDER[b.status]));
    people.hidden = list.length === 0;
    if (!list.length) return;

    $('guestCount').textContent = `${list.length} ${list.length === 1 ? 'guest' : 'guests'}`;
    const org = m.organizer;
    $('organizer').textContent = !org ? '' : org.self ? 'You’re the organizer' : `Organized by ${org.name}`;

    const avatars = $('avatars');
    avatars.textContent = '';
    list.slice(0, 3).forEach((p) => avatars.appendChild(avatar(p)));
    if (list.length > 3) {
      const more = document.createElement('span');
      more.className = 'avatar more';
      more.textContent = `+${list.length - 3}`;
      avatars.appendChild(more);
    }

    const counts = {};
    list.forEach((p) => { counts[p.status] = (counts[p.status] || 0) + 1; });
    $('summary').textContent = ['accepted', 'tentative', 'needsAction', 'declined']
      .filter((s) => counts[s]).map((s) => `${counts[s]} ${RSVP_LABEL[s].toLowerCase()}`).join('  ·  ');

    const ul = $('peopleList');
    ul.textContent = '';
    for (const p of list) {
      const li = document.createElement('li');
      li.className = `person ${p.status}`;
      li.appendChild(avatar(p));
      const text = document.createElement('div');
      text.style.minWidth = '0';
      const name = document.createElement('div');
      name.className = 'name';
      name.textContent = p.self && p.name === 'You' ? 'You' : p.name;
      if (p.self && p.name !== 'You') {
        const you = document.createElement('span');
        you.className = 'you';
        you.textContent = '(you)';
        name.appendChild(you);
      }
      text.appendChild(name);
      if (p.organizer) {
        const role = document.createElement('span');
        role.className = 'role';
        role.textContent = 'Organizer';
        text.appendChild(role);
      }
      li.appendChild(text);
      const rsvp = document.createElement('span');
      rsvp.className = `rsvp ${p.status}`;
      rsvp.textContent = RSVP_LABEL[p.status] || 'Awaiting';
      li.appendChild(rsvp);
      ul.appendChild(li);
    }
  }

  function render(m) {
    M = m;
    $('title').textContent = m.title;
    $('title').title = m.title;
    $('miniTitle').textContent = m.title;
    $('when').textContent = timeRange(m.start, m.end);
    $('service').textContent = m.link ? (m.service || 'Online meeting') : 'No video link';
    const join = $('join');
    join.classList.toggle('secondary', !m.link);
    $('joinLabel').textContent = m.link ? `Join ${m.service || 'meeting'}` : 'I’m joining';
    join.querySelector('.join-icon').style.display = m.link ? '' : 'none';
    $('account').textContent = m.account;
    // your own meetings can be edited; others' open for details
    const cal = $('calBtn');
    cal.hidden = !m.calendarUrl;
    const mine = !!m.organizer?.self;
    const calLabel = mine ? 'Edit in Google Calendar' : 'Open in Google Calendar';
    cal.title = calLabel;
    cal.setAttribute('aria-label', calLabel);
    cal.querySelector('use').setAttribute('href', mine ? '#i-edit' : '#i-open');
    if (window.__alertApply) window.__alertApply(m); // MedLog: an alert instead of a meeting
    renderPeople(m);
    update();
  }

  // ------------------------------------------------------------ interactions

  function setMini(value) {
    mini = value;
    body.classList.toggle('mini', value);
    reportHeight();
    positionGlass();
  }

  $('minimize').addEventListener('click', () => setMini(true));
  $('expand').addEventListener('click', () => setMini(false));
  capsule.addEventListener('dblclick', (e) => { if (!e.target.closest('button')) setMini(false); });

  $('peopleToggle').addEventListener('click', () => {
    const p = $('people');
    const open = !p.classList.contains('open');
    p.classList.toggle('open', open);
    $('peopleToggle').setAttribute('aria-expanded', String(open));
  });

  function act(type, minutes, key = M?.key) {
    stopAlarm();
    if (list.length > 1) {
      // one of several: that row folds away, the rest stay (the alarm resumes within a second if still due)
      stackRows.get(key)?.classList.add('gone');
      host.action(type, minutes, key);
      setTimeout(() => setList(list.filter((m) => m.key !== key)), 220);
      return;
    }
    clearTimeout(realarmTimer);
    body.classList.add('leaving');
    host.action(type, minutes, key);
  }

  // ------------------------------------------------------------ several meetings at once
  const stackRows = new Map();
  function setList(ms) {
    list = [...ms].sort((a, b) => a.start - b.start);
    if (!list.length) return;
    const stacked = list.length > 1;
    body.classList.toggle('stacked', stacked);
    render(list[0]);
    renderStack();
    $('miniMore').hidden = !stacked;
    $('miniMore').textContent = stacked ? `+${list.length - 1}` : '';
    lastState = null;
    reportHeight();
  }

  function renderStack() {
    const box = $('stack');
    const keys = new Set(list.length > 1 ? list.map((m) => m.key) : []);
    for (const [k, row] of stackRows) if (!keys.has(k)) { row.remove(); stackRows.delete(k); }
    if (list.length < 2) return;
    list.forEach((m) => {
      let row = stackRows.get(m.key);
      if (!row) {
        row = document.createElement('div');
        row.className = 'srow';
        row.setAttribute('role', 'listitem');
        row.innerHTML = `
          <div class="srow-main"><div class="srow-title"></div><div class="srow-meta"></div></div>
          <div class="srow-count" role="timer"></div>
          <div class="srow-acts">
            <button class="srow-join"><svg><use href="#i-video"/></svg><span></span></button>
            <button class="srow-icon" data-a="snooze" title="Snooze"><svg><use href="#i-snooze"/></svg></button>
            <button class="srow-icon" data-a="dismiss" title="Dismiss"><svg><use href="#i-x"/></svg></button>
          </div>`;
        row.querySelector('.srow-join').addEventListener('click', () => act('join', 0, m.key));
        row.querySelector('[data-a=snooze]').addEventListener('click', () => act('snooze', cfg.snoozeMinutes, m.key));
        row.querySelector('[data-a=dismiss]').addEventListener('click', () => act('dismiss', 0, m.key));
        stackRows.set(m.key, row);
      }
      row.querySelector('.srow-title').textContent = m.title;
      row.querySelector('.srow-meta').textContent = `${timeRange(m.start, m.end)} · ${m.link ? (m.service || 'Online meeting') : 'No video link'}`;
      row.querySelector('.srow-join span').textContent = m.link ? 'Join' : 'I’m joining';
      row.querySelector('.srow-join svg').style.display = m.link ? '' : 'none';
      row.querySelector('[data-a=snooze]').setAttribute('aria-label', `Snooze ${m.title}`);
      row.querySelector('[data-a=dismiss]').setAttribute('aria-label', `Dismiss ${m.title}`);
      box.append(row); // keeps rows in start order
    });
  }

  let lastState = null;
  function updateStack() {
    if (list.length < 2) return;
    const now = Date.now();
    for (const m of list) {
      const row = stackRows.get(m.key);
      if (!row) continue;
      const ms = m.start - now;
      const s = ms > 0 ? Math.ceil(ms / 1000) : Math.floor(-ms / 1000);
      const text = ms > 0 ? fmt(s) : `+${fmt(s)}`;
      const cell = row.querySelector('.srow-count');
      if (cell.textContent !== text) cell.textContent = text;
      row.classList.toggle('warn', ms > 0 && ms <= cfg.warn);
      row.classList.toggle('late', ms <= 0);
    }
    const box = $('stack');
    box.classList.toggle('more', box.scrollTop + box.clientHeight < box.scrollHeight - 2); // fade hints at more below
    const n = `${list.length} meetings`;
    if ($('status').textContent !== n) $('status').textContent = n;
  }

  $('join').addEventListener('click', () => act('join'));
  $('calBtn').addEventListener('click', () => { if (M?.calendarUrl) host.openUrl(M.calendarUrl); });
  $('snoozeBtn').addEventListener('click', () => act('snooze', cfg.snoozeMinutes));
  $('dismissBtn').addEventListener('click', () => act('dismiss'));
  $('lateBtn').addEventListener('click', () => body.classList.add('picking'));
  $('lateCancel').addEventListener('click', () => body.classList.remove('picking'));
  document.querySelectorAll('.late-options button').forEach((b) =>
    b.addEventListener('click', () => act('late', Number(b.dataset.min))));

  // dragging: the main process moves the window with the cursor once the pointer travels 3px
  let press = null;
  let dragging = false;
  for (const surface of [card, capsule]) {
    surface.addEventListener('pointerdown', (e) => {
      if (e.button !== 0 || e.target.closest('button, .people-list')) return;
      press = { x: e.screenX, y: e.screenY, id: e.pointerId };
      surface.setPointerCapture(e.pointerId);
    });
    surface.addEventListener('pointermove', (e) => {
      if (!press || dragging) return;
      if (Math.hypot(e.screenX - press.x, e.screenY - press.y) > 3) {
        dragging = true;
        body.classList.add('dragging');
        host.dragStart({ x: press.x, y: press.y });
      }
    });
    const end = () => {
      if (dragging) host.dragEnd();
      dragging = false;
      press = null;
      body.classList.remove('dragging');
    };
    surface.addEventListener('pointerup', end);
    surface.addEventListener('pointercancel', end);
    surface.addEventListener('lostpointercapture', end);
  }

  // tells the main process where the visible surface is, for click-through and stacking
  function reportHeight() {
    const s = mini ? capsule : card;
    host.contentHeight(Math.ceil(s.offsetHeight));
    host.hitRect({
      x: stage.offsetLeft + s.offsetLeft,
      y: stage.offsetTop + s.offsetTop,
      w: s.offsetWidth,
      h: s.offsetHeight,
      r: mini ? s.offsetHeight / 2 : 28,
    });
    // Android drags the window natively; keep the scrollable guest list scrollable
    if (host.noDrag) {
      const list = $('peopleList');
      const r = list.getBoundingClientRect();
      const rects = !mini && $('people').classList.contains('open') && r.height > 0 ? [{ x: r.left, y: r.top, w: r.width, h: r.height }] : [];
      const sb = $('stack').getBoundingClientRect();
      if (!mini && list.length > 1 && sb.height > 0) rects.push({ x: sb.left, y: sb.top, w: sb.width, h: sb.height });
      host.noDrag(rects);
    }
  }
  new ResizeObserver(reportHeight).observe(stage);

  // ------------------------------------------------------------ frosted glass
  // The main process sends a snapshot of the display behind us; we blur it once into a canvas
  // and slide that canvas so each surface shows exactly the region behind it.

  let geo = { x: 0, y: 0 };
  let backdrop = null; // { canvas, bounds }

  async function setBackdrop({ url, bounds }) {
    const img = new Image();
    img.src = url;
    try { await img.decode(); } catch { return; }
    const src = document.createElement('canvas');
    src.width = img.width;
    src.height = img.height;
    const ctx = src.getContext('2d');
    const k = img.width / bounds.width;            // snapshot px per DIP
    const blur = Math.max(4, Math.round(18 * k));
    ctx.filter = `blur(${blur}px) saturate(1.8) brightness(0.55)`;
    ctx.drawImage(img, -blur * 2, -blur * 2, img.width + blur * 4, img.height + blur * 4);
    backdrop = { canvas: src, bounds };
    const sharp = document.createElement('canvas');
    sharp.width = img.width;
    sharp.height = img.height;
    const sctx = sharp.getContext('2d');
    sctx.filter = `blur(${Math.max(1, Math.round(1.5 * k))}px) saturate(1.6) brightness(0.72)`;
    sctx.drawImage(img, 0, 0);
    for (const surface of [card, capsule]) {
      const s = surface.querySelector('.glass-sharp');
      s.width = sharp.width;
      s.height = sharp.height;
      s.getContext('2d').drawImage(sharp, 0, 0);
      s.style.width = `${bounds.width}px`;
      s.style.height = `${bounds.height}px`;
    }

    for (const surface of [card, capsule]) {
      const [a, b] = surface.querySelectorAll('.glass');
      const target = a.classList.contains('on') ? b : a;
      const other = target === a ? b : a;
      target.width = src.width;
      target.height = src.height;
      target.getContext('2d').drawImage(src, 0, 0);
      target.style.width = `${bounds.width}px`;
      target.style.height = `${bounds.height}px`;
      target.classList.add('on');
      other.classList.remove('on');
    }
    body.classList.remove('no-glass');
    body.classList.add('lens-on');
    refreshLenses();
    positionGlass();
  }

  function positionGlass() {
    if (!backdrop) return;
    const b = backdrop.bounds;
    for (const surface of [card, capsule]) {
      const sx = geo.x + stage.offsetLeft + surface.offsetLeft;
      const sy = geo.y + stage.offsetTop + surface.offsetTop;
      const t = `translate3d(${b.x - sx + LENS_MARGIN}px, ${b.y - sy + LENS_MARGIN}px, 0)`;
      surface.querySelectorAll('.glass, .glass-sharp').forEach((c) => { c.style.transform = t; });
    }
  }

  // ------------------------------------------------------------ liquid glass lens
  // A displacement map per surface: neutral inside, and within `rim` px of the rounded edge it points
  // outward, so the rim shows what lies just beyond the card, squeezed into the curve (refraction and
  // splay). Red, green and blue are displaced by slightly different amounts (dispersion).
  const LENS_MARGIN = 48;
  const SVGNS = 'http://www.w3.org/2000/svg';
  const lensDefs = document.createElementNS(SVGNS, 'svg');
  lensDefs.setAttribute('width', '0');
  lensDefs.setAttribute('height', '0');
  lensDefs.style.position = 'absolute';
  document.body.append(lensDefs);

  function lensFilter(id) {
    const f = document.createElementNS(SVGNS, 'filter');
    f.id = id;
    for (const [k, v] of Object.entries({ filterUnits: 'userSpaceOnUse', primitiveUnits: 'userSpaceOnUse', 'color-interpolation-filters': 'sRGB', x: 0, y: 0 })) f.setAttribute(k, v);
    f.innerHTML = `
      <feImage result="map" x="0" y="0" preserveAspectRatio="none"/>
      <feDisplacementMap in="SourceGraphic" in2="map" xChannelSelector="R" yChannelSelector="G" data-k="1.1" result="dR"/>
      <feColorMatrix in="dR" type="matrix" values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0" result="r"/>
      <feDisplacementMap in="SourceGraphic" in2="map" xChannelSelector="R" yChannelSelector="G" data-k="1" result="dG"/>
      <feColorMatrix in="dG" type="matrix" values="0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0" result="g"/>
      <feDisplacementMap in="SourceGraphic" in2="map" xChannelSelector="R" yChannelSelector="G" data-k="0.9" result="dB"/>
      <feColorMatrix in="dB" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0" result="b"/>
      <feComposite in="r" in2="g" operator="arithmetic" k2="1" k3="1" result="rg"/>
      <feComposite in="rg" in2="b" operator="arithmetic" k2="1" k3="1"/>`;
    lensDefs.append(f);
    return f;
  }

  function lensMap(w, h, r, rim) {
    const M = LENS_MARGIN;
    const W = Math.round(w + 2 * M);
    const H = Math.round(h + 2 * M);
    const c = document.createElement('canvas');
    c.width = W;
    c.height = H;
    const g = c.getContext('2d');
    const img = g.createImageData(W, H);
    const d = img.data;
    const mask = g.createImageData(W, H);
    const mk = mask.data;
    const hx = w / 2 - r;
    const hy = h / 2 - r;
    for (let y = 0; y < H; y++) {
      const py = y + 0.5 - M - h / 2;
      for (let x = 0; x < W; x++) {
        const px = x + 0.5 - M - w / 2;
        const qx = Math.abs(px) - hx;
        const qy = Math.abs(py) - hy;
        let sd; let nx; let ny;
        if (qx > 0 && qy > 0) { const l = Math.hypot(qx, qy); sd = l - r; nx = qx / l; ny = qy / l; } else if (qx > qy) { sd = qx - r; nx = 1; ny = 0; } else { sd = qy - r; nx = 0; ny = 1; }
        if (px < 0) nx = -nx;
        if (py < 0) ny = -ny;
        const depth = -sd; // px inside the edge
        const t = depth <= 0 ? 1 : depth >= rim ? 0 : 1 - depth / rim;
        const m = t * t * (1.6 - 0.6 * t); // steep at the very edge, fading smoothly inward
        const i = (y * W + x) * 4;
        d[i] = 128 + nx * m * 127;
        d[i + 1] = 128 + ny * m * 127;
        d[i + 2] = 128;
        d[i + 3] = 255;
        // rim mask: opaque at the edge, gone by the inner end of the rim
        mk[i] = mk[i + 1] = mk[i + 2] = 255;
        mk[i + 3] = Math.min(255, t * 330);
      }
    }
    g.putImageData(img, 0, 0);
    const url = c.toDataURL();
    g.putImageData(mask, 0, 0);
    return { url, mask: c.toDataURL(), W, H };
  }

  const lenses = new Map(); // surface -> { filter, size }
  function updateLens(surface, id, radius, rim, strength) {
    const w = surface.offsetWidth;
    const h = surface.offsetHeight;
    if (!w || !h) return;
    let L = lenses.get(surface);
    if (!L) { L = { filter: lensFilter(id), size: '' }; lenses.set(surface, L); }
    const size = `${w}x${h}`;
    if (L.size === size) return;
    L.size = size;
    const { url, mask, W, H } = lensMap(w, h, Math.min(radius(h), w / 2, h / 2), rim);
    L.filter.setAttribute('width', W);
    L.filter.setAttribute('height', H);
    const im = L.filter.querySelector('feImage');
    im.setAttribute('href', url);
    const band = surface.querySelector('.rim');
    band.style.webkitMaskImage = band.style.maskImage = `url(${mask})`;
    im.setAttribute('width', W);
    im.setAttribute('height', H);
    L.filter.querySelectorAll('feDisplacementMap').forEach((dm) => dm.setAttribute('scale', String(strength * Number(dm.dataset.k))));
  }
  const refreshLenses = () => {
    if (!backdrop) return;
    updateLens(card, 'lens-card', () => 28, 26, 64);
    updateLens(capsule, 'lens-capsule', (h) => h / 2, 16, 40);
  };
  new ResizeObserver(refreshLenses).observe(card);
  new ResizeObserver(refreshLenses).observe(capsule);

  // ------------------------------------------------------------ sound

  // One identical beep for every cue; urgency is expressed only by how closely beeps repeat.
  //   appear: 1   0:10: 1   0:05: 2 (0.30s apart)   0:03: 3 (0.22s apart)   0:00+: every 0.18s, unbroken
  const ALARM_CUES = { 10: [1, 0], 5: [2, 0.30], 3: [3, 0.22] };
  const CONTINUOUS_GAP = 0.18;
  // ~2.4 kHz sits where hearing is most sensitive; a few odd harmonics give it a piercing,
  // alarm-clock edge that carries over room noise and small laptop speakers
  const BEEP = { freq: 2400, length: 0.11, peak: 0.95 };
  let wave = null;
  let limiter = null;
  let audio = null;
  let alarmTimer = null;
  let alarmNext = 0;
  let muted = false;
  let realarmAt = 0;
  let realarmTimer = null;
  const live = new Set(); // scheduled oscillators, so muting silences beeps already queued
  // Android plays the same beeps natively on the alarm stream (works in silent mode, vibrates)
  const nativeAlarm = typeof host.alarm === 'function';

  function ctx() {
    audio = audio || new AudioContext();
    if (audio.state === 'suspended') audio.resume();
    return audio;
  }

  function beepAt(at) {
    const a = ctx();
    if (!wave) {
      // fundamental + softened odd harmonics (a rounded square wave)
      wave = a.createPeriodicWave(new Float32Array([0, 0, 0, 0, 0, 0]), new Float32Array([0, 1, 0, 0.33, 0, 0.14]));
      // brick-wall-ish limiter: lets the beep sit right at full scale without clipping
      limiter = a.createDynamicsCompressor();
      limiter.threshold.value = -2;
      limiter.knee.value = 0;
      limiter.ratio.value = 20;
      limiter.attack.value = 0.001;
      limiter.release.value = 0.05;
      limiter.connect(a.destination);
    }
    const peak = BEEP.peak * (cfg.volume ?? 1);
    const osc = a.createOscillator();
    const gain = a.createGain();
    osc.setPeriodicWave(wave);
    osc.frequency.value = BEEP.freq;
    gain.gain.setValueAtTime(0, at);
    gain.gain.linearRampToValueAtTime(peak, at + 0.005);
    gain.gain.setValueAtTime(peak, at + BEEP.length - 0.02);
    gain.gain.linearRampToValueAtTime(0, at + BEEP.length);
    osc.connect(gain).connect(limiter);
    osc.start(at);
    osc.stop(at + BEEP.length + 0.01);
    live.add(osc);
    osc.onended = () => live.delete(osc);
  }

  function beeps(n, gap = 0) {
    if (!cfg.sound || (muted && state === 'late')) return;
    if (nativeAlarm) { host.alarm('beeps', n, gap); return; }
    try {
      const t0 = ctx().currentTime + 0.02;
      for (let i = 0; i < n; i++) beepAt(t0 + i * gap);
    } catch { /* no audio device */ }
  }

  // continuous alarm from 0:00: beeps are queued ~0.5s ahead on the audio clock so the rhythm
  // stays perfectly even regardless of timer jitter
  function startAlarm() {
    if (alarmTimer || muted || !cfg.sound) return;
    if (nativeAlarm) { alarmTimer = true; host.alarm('start', 0, CONTINUOUS_GAP); return; }
    try {
      alarmNext = ctx().currentTime + 0.02;
      const pump = () => {
        const horizon = audio.currentTime + 0.5;
        while (alarmNext < horizon) {
          beepAt(alarmNext);
          alarmNext += CONTINUOUS_GAP;
        }
      };
      pump();
      alarmTimer = setInterval(pump, 150);
    } catch { /* no audio device */ }
  }

  function stopAlarm() {
    if (nativeAlarm && alarmTimer) host.alarm('stop', 0, 0);
    if (alarmTimer !== true) clearInterval(alarmTimer);
    alarmTimer = null;
    live.forEach((osc) => { try { osc.stop(); } catch { /* already stopped */ } });
    live.clear();
  }

  // Muting only lasts `realarmSeconds`: if you still haven't joined, the alarm comes back and
  // "Running late" is surfaced next to Snooze and Dismiss.
  function setMuted(value) {
    muted = value;
    body.classList.toggle('muted', value);
    clearTimeout(realarmTimer);
    if (value) {
      stopAlarm();
      realarmAt = Date.now() + cfg.realarmSeconds * 1000;
      realarmTimer = setTimeout(() => {
        body.classList.add('second-alarm');
        setMuted(false);
      }, cfg.realarmSeconds * 1000);
    } else {
      realarmAt = 0;
      if (state === 'late') startAlarm();
    }
    updateMuteLabel();
  }

  function updateMuteLabel() {
    const left = Math.max(0, Math.ceil((realarmAt - Date.now()) / 1000));
    const text = muted ? `Muted · ${fmt(left)}` : 'Mute';
    document.querySelectorAll('.mute-label').forEach((el) => { if (el.textContent !== text) el.textContent = text; });
  }
  document.querySelectorAll('.mute').forEach((b) => b.addEventListener('click', () => setMuted(!muted)));
  window.__alarm = () => ({ running: !!alarmTimer, queued: live.size, muted, audio: audio?.state }); // diagnostics

  // ------------------------------------------------------------ host wiring

  host.onInit((init) => {
    cfg = {
      lead: init.lead,
      warn: init.warn,
      sound: init.sound,
      snoozeMinutes: init.snoozeMinutes || 5,
      realarmSeconds: init.realarmSeconds || 60,
      // the setting is perceptual: each step is a clearly audible drop (100 → 0 dB, 70 → -12, 40 → -24, 15 → -34)
      volume: (init.alarmVolume ?? 100) > 0 ? 10 ** ((-(100 - Math.min(100, init.alarmVolume ?? 100)) * 0.4) / 20) : 0,
    };
    $('snoozeLabel').textContent = `Snooze ${cfg.snoozeMinutes} min`;
    body.classList.add(`platform-${init.platform || 'desktop'}`);
    body.classList.toggle('system-glass', !!init.systemGlass);
    const root = document.documentElement.style;
    root.setProperty('--pad', `${init.pad}px`);
    root.setProperty('--card-w', `${init.cardWidth}px`);
    root.setProperty('--mini-size', `${init.miniSize}px`);
    list = [init.meeting];
    render(init.meeting);
  });
  host.onMeeting((m) => { if (list.length < 2) { list = [m]; render(m); } });
  host.onMeetings?.(setList);
  host.onGeometry((g) => { geo = g; positionGlass(); });
  host.onBackdrop(setBackdrop);
  host.onEnter(() => {
    requestAnimationFrame(() => requestAnimationFrame(() => body.classList.remove('pre')));
    if (state !== 'late') beeps(1); // late widgets go straight into the continuous alarm
  });
  host.onLeave(() => { stopAlarm(); body.classList.add('leaving'); });

  setInterval(() => { update(); updateStack(); if (muted) updateMuteLabel(); }, 100);

  // lets widget.html be opened in a plain browser for design work
  function stubHost() {
    const noop = () => {};
    const fire = (cb) => setTimeout(cb, 0);
    const start = Date.now() + 64e3;
    return {
      onInit: (cb) => fire(() => cb({
        pad: 32, cardWidth: 360, miniSize: 34, lead: 300e3, warn: 30e3, sound: false,
        meeting: {
          key: 'x', title: 'Q4 roadmap review with Design & Engineering', start, end: start + 45 * 60e3,
          link: 'https://meet.google.com/', service: 'Google Meet', account: 'you@company.com',
          organizer: { name: 'Priya Raman', self: false },
          attendees: [
            { name: 'Priya Raman', email: 'p', status: 'accepted', organizer: true, self: false },
            { name: 'You', email: 'y', status: 'accepted', self: true, organizer: false },
            { name: 'Alex Chen', email: 'a', status: 'tentative', self: false, organizer: false },
            { name: 'Sam Patel', email: 's', status: 'needsAction', self: false, organizer: false },
          ],
        },
      })),
      onMeeting: noop, onGeometry: noop, onBackdrop: noop, onLeave: noop,
      onEnter: (cb) => setTimeout(cb, 50),
      hitRect: noop, dragStart: noop, dragEnd: noop, contentHeight: noop, action: noop, openUrl: noop,
    };
  }
})();
