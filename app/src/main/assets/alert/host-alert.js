// The helper alert: Meeting Timer's widget (widget.html / widget.css / widget.js, unchanged in look) showing a MedLog
// alert instead of a meeting. Same `window.host` contract as Meeting Timer's Android bridge; the app answers through
// window.AndroidHost.action('join' | 'snooze' | 'dismiss'), which are the alert's reply 1, 2 and 3.
(() => {
  const listeners = {};
  const on = (name) => (cb) => { (listeners[name] = listeners[name] || []).push(cb); };
  window.__hostEmit = (name, payload) => (listeners[name] || []).forEach((cb) => cb(payload));
  const A = window.AndroidHost || { action() {} };
  const noop = () => {};
  window.host = {
    onInit: on('init'), onMeeting: on('meeting'), onMeetings: on('meetings'), onGeometry: on('geometry'),
    onBackdrop: on('backdrop'), onEnter: on('enter'), onLeave: on('leave'),
    hitRect: noop, noDrag: noop, dragStart: noop, dragEnd: noop, contentHeight: noop, openUrl: noop,
    // the sound is the app's own alarm (AlertSound), so the page never plays or starts one
    alarm: noop,
    action: (type) => A.action(type, 0),
  };

  // two small pictures Meeting Timer does not have, drawn the same way as its own
  const defs = document.querySelector('svg[aria-hidden]');
  if (defs) {
    defs.insertAdjacentHTML('beforeend',
      '<symbol id="i-check" viewBox="0 0 24 24"><path d="M4.5 12.8l5 5L19.5 6.8"/></symbol>' +
      '<symbol id="i-people" viewBox="0 0 24 24"><path d="M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM3.5 19c0-3 2.5-5 5.5-5s5.5 2 5.5 5M16.5 11.2a2.6 2.6 0 0 0 0-5M18 14.3c1.7.6 2.8 2.1 2.8 4.7"/></symbol>');
  }
  const ICON = { coming: 'i-check', give: 'i-check', handle: 'i-check', '5min': 'i-snooze', skip: 'i-x', skipdose: 'i-x', ask: 'i-people' };
  const $ = (id) => document.getElementById(id);
  const icon = (btn, code) => { const u = btn.querySelector('use'); if (u) u.setAttribute('href', '#' + (ICON[code] || 'i-check')); };

  // Puts the alert's words where the meeting's went: title = alert title; "who · when" on the meta line; the message
  // under it; the three answers on Join / Snooze / Dismiss.
  window.__alertApply = (m) => {
    if (!m.replies) return;
    window.__words = m.words;
    document.body.classList.toggle('no-clock', !m.clock);
    $('when').textContent = m.who;
    $('service').textContent = m.at;
    $('account').textContent = m.text || '';
    document.querySelector('.info').append($('account')); // the message goes right under the title
    const [r1, r2, r3] = m.replies;
    const join = $('join');
    join.classList.remove('secondary');
    $('joinLabel').textContent = r1.words;
    const ji = join.querySelector('.join-icon');
    ji.style.display = '';
    icon(join, r1.code);
    $('snoozeLabel').textContent = r2.words;
    icon($('snoozeBtn'), r2.code);
    $('dismissBtn').querySelector('span').textContent = r3.words;
    icon($('dismissBtn'), r3.code);
  };
})();
