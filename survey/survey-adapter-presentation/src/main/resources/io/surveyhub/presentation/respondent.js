(function () {
  'use strict';
  var C = window.SURVEY_CONFIG;
  var API = location.pathname.replace(/\/p\/[^\/]*$/, '') + '/api/v1/public/' + encodeURIComponent(C.token);
  var app = document.getElementById('app');
  var KEY = 'survey:' + C.token;
  var state = { meta: null, session: null, errors: {}, step: 0, visibleKey: '', timer: null, locale: null };
  var timers = {};

  // ---------- interface language (survey content is translated on the server) ----------
  var UI = {
    en: { yes: 'Yes', no: 'No', start: 'Start', resume: 'Resume where I left off', email: 'Your e-mail', closed: 'This survey is not accepting responses right now.',
      inviteOnly: 'Open this survey from your personal invitation link.', back: 'Back', next: 'Next', submit: 'Submit', fix: 'Please fix the highlighted questions.',
      timeLeft: 'Time left', other: 'Other:', keepOrder: 'Keep this order', topN: 'Only your top {n} are counted.', remaining: 'Remaining: {left} of {total}',
      thanks: 'Thank you!', passed: 'Passed 🎉', notPassed: 'Not passed', points: '{earned} of {max} points', responses: '{n} responses', language: 'Language',
      notEligible: 'Thank you - you are not eligible for this survey.', unsupported: 'Unsupported question type: {kind}', invalid: 'Invalid answer ({code}).',
      REQUIRED: 'This question is required.', TOO_LONG: 'Too long.', PATTERN_MISMATCH: 'Invalid format.', TOO_FEW_SELECTIONS: 'Select more options.',
      TOO_MANY_SELECTIONS: 'Select fewer options.', OUT_OF_RANGE: 'Out of range.', NOT_AN_INTEGER: 'Whole numbers only.', SUM_MISMATCH: 'The points must add up to the total.',
      MUST_ACCEPT: 'You must accept to continue.', INVALID_EMAIL: 'Enter a valid e-mail address.', INVALID_PHONE: 'Enter a valid phone number.',
      INVALID_URL: 'Enter a valid URL (https://…).', WRONG_RANK_COUNT: 'Rank the required number of items.', MISSING_ROW: 'Answer every row.',
      BELOW_MIN: 'Too small.', ABOVE_MAX: 'Too large.', NOT_ON_STEP: 'Pick a value on the slider steps.' },
    id: { yes: 'Ya', no: 'Tidak', start: 'Mulai', resume: 'Lanjutkan dari terakhir', email: 'Email Anda', closed: 'Survei ini sedang tidak menerima jawaban.',
      inviteOnly: 'Buka survei ini dari tautan undangan pribadi Anda.', back: 'Kembali', next: 'Lanjut', submit: 'Kirim', fix: 'Mohon perbaiki pertanyaan yang ditandai.',
      timeLeft: 'Sisa waktu', other: 'Lainnya:', keepOrder: 'Gunakan urutan ini', topN: 'Hanya {n} teratas yang dihitung.', remaining: 'Sisa: {left} dari {total}',
      thanks: 'Terima kasih!', passed: 'Lulus 🎉', notPassed: 'Tidak lulus', points: '{earned} dari {max} poin', responses: '{n} jawaban', language: 'Bahasa',
      notEligible: 'Terima kasih - Anda tidak memenuhi syarat untuk survei ini.', unsupported: 'Jenis pertanyaan tidak didukung: {kind}', invalid: 'Jawaban tidak valid ({code}).',
      REQUIRED: 'Pertanyaan ini wajib diisi.', TOO_LONG: 'Terlalu panjang.', PATTERN_MISMATCH: 'Format tidak valid.', TOO_FEW_SELECTIONS: 'Pilih lebih banyak opsi.',
      TOO_MANY_SELECTIONS: 'Pilih lebih sedikit opsi.', OUT_OF_RANGE: 'Di luar rentang.', NOT_AN_INTEGER: 'Hanya bilangan bulat.', SUM_MISMATCH: 'Jumlah poin harus sama dengan total.',
      MUST_ACCEPT: 'Anda harus menyetujui untuk melanjutkan.', INVALID_EMAIL: 'Masukkan alamat email yang valid.', INVALID_PHONE: 'Masukkan nomor telepon yang valid.',
      INVALID_URL: 'Masukkan URL yang valid (https://…).', WRONG_RANK_COUNT: 'Urutkan jumlah item yang diminta.', MISSING_ROW: 'Isi setiap baris.',
      BELOW_MIN: 'Terlalu kecil.', ABOVE_MAX: 'Terlalu besar.', NOT_ON_STEP: 'Pilih nilai sesuai langkah penggeser.' },
    es: { yes: 'Sí', no: 'No', start: 'Comenzar', resume: 'Continuar donde lo dejé', email: 'Su correo electrónico', closed: 'Esta encuesta no acepta respuestas en este momento.',
      inviteOnly: 'Abra esta encuesta desde su enlace de invitación personal.', back: 'Atrás', next: 'Siguiente', submit: 'Enviar', fix: 'Corrija las preguntas resaltadas.',
      timeLeft: 'Tiempo restante', other: 'Otro:', keepOrder: 'Mantener este orden', topN: 'Solo se cuentan sus {n} primeras.', remaining: 'Restante: {left} de {total}',
      thanks: '¡Gracias!', passed: 'Aprobado 🎉', notPassed: 'No aprobado', points: '{earned} de {max} puntos', responses: '{n} respuestas', language: 'Idioma',
      notEligible: 'Gracias: no cumple los requisitos para esta encuesta.', unsupported: 'Tipo de pregunta no compatible: {kind}', invalid: 'Respuesta no válida ({code}).',
      REQUIRED: 'Esta pregunta es obligatoria.', TOO_LONG: 'Demasiado largo.', PATTERN_MISMATCH: 'Formato no válido.', TOO_FEW_SELECTIONS: 'Seleccione más opciones.',
      TOO_MANY_SELECTIONS: 'Seleccione menos opciones.', OUT_OF_RANGE: 'Fuera de rango.', NOT_AN_INTEGER: 'Solo números enteros.', SUM_MISMATCH: 'Los puntos deben sumar el total.',
      MUST_ACCEPT: 'Debe aceptar para continuar.', INVALID_EMAIL: 'Introduzca un correo electrónico válido.', INVALID_PHONE: 'Introduzca un teléfono válido.',
      INVALID_URL: 'Introduzca una URL válida (https://…).', WRONG_RANK_COUNT: 'Ordene el número de elementos requerido.', MISSING_ROW: 'Responda cada fila.',
      BELOW_MIN: 'Demasiado pequeño.', ABOVE_MAX: 'Demasiado grande.', NOT_ON_STEP: 'Elija un valor en los pasos del control.' },
    fr: { yes: 'Oui', no: 'Non', start: 'Commencer', resume: 'Reprendre où je me suis arrêté', email: 'Votre e-mail', closed: "Ce questionnaire n'accepte pas de réponses pour le moment.",
      inviteOnly: "Ouvrez ce questionnaire depuis votre lien d'invitation personnel.", back: 'Retour', next: 'Suivant', submit: 'Envoyer', fix: 'Veuillez corriger les questions signalées.',
      timeLeft: 'Temps restant', other: 'Autre :', keepOrder: 'Garder cet ordre', topN: 'Seuls vos {n} premiers sont comptés.', remaining: 'Restant : {left} sur {total}',
      thanks: 'Merci !', passed: 'Réussi 🎉', notPassed: 'Non réussi', points: '{earned} sur {max} points', responses: '{n} réponses', language: 'Langue',
      notEligible: "Merci - vous n'êtes pas éligible pour ce questionnaire.", unsupported: 'Type de question non pris en charge : {kind}', invalid: 'Réponse invalide ({code}).',
      REQUIRED: 'Cette question est obligatoire.', TOO_LONG: 'Trop long.', PATTERN_MISMATCH: 'Format invalide.', TOO_FEW_SELECTIONS: "Sélectionnez plus d'options.",
      TOO_MANY_SELECTIONS: "Sélectionnez moins d'options.", OUT_OF_RANGE: 'Hors limites.', NOT_AN_INTEGER: 'Nombres entiers uniquement.', SUM_MISMATCH: 'Les points doivent totaliser le montant indiqué.',
      MUST_ACCEPT: 'Vous devez accepter pour continuer.', INVALID_EMAIL: 'Saisissez une adresse e-mail valide.', INVALID_PHONE: 'Saisissez un numéro de téléphone valide.',
      INVALID_URL: 'Saisissez une URL valide (https://…).', WRONG_RANK_COUNT: "Classez le nombre d'éléments demandé.", MISSING_ROW: 'Répondez à chaque ligne.',
      BELOW_MIN: 'Trop petit.', ABOVE_MAX: 'Trop grand.', NOT_ON_STEP: 'Choisissez une valeur sur les pas du curseur.' },
    de: { yes: 'Ja', no: 'Nein', start: 'Starten', resume: 'Dort weitermachen, wo ich aufgehört habe', email: 'Ihre E-Mail', closed: 'Diese Umfrage nimmt derzeit keine Antworten an.',
      inviteOnly: 'Öffnen Sie diese Umfrage über Ihren persönlichen Einladungslink.', back: 'Zurück', next: 'Weiter', submit: 'Absenden', fix: 'Bitte korrigieren Sie die markierten Fragen.',
      timeLeft: 'Verbleibende Zeit', other: 'Sonstiges:', keepOrder: 'Diese Reihenfolge beibehalten', topN: 'Nur Ihre ersten {n} zählen.', remaining: 'Übrig: {left} von {total}',
      thanks: 'Vielen Dank!', passed: 'Bestanden 🎉', notPassed: 'Nicht bestanden', points: '{earned} von {max} Punkten', responses: '{n} Antworten', language: 'Sprache',
      notEligible: 'Vielen Dank - Sie sind für diese Umfrage nicht teilnahmeberechtigt.', unsupported: 'Nicht unterstützter Fragetyp: {kind}', invalid: 'Ungültige Antwort ({code}).',
      REQUIRED: 'Diese Frage ist erforderlich.', TOO_LONG: 'Zu lang.', PATTERN_MISMATCH: 'Ungültiges Format.', TOO_FEW_SELECTIONS: 'Wählen Sie mehr Optionen.',
      TOO_MANY_SELECTIONS: 'Wählen Sie weniger Optionen.', OUT_OF_RANGE: 'Außerhalb des Bereichs.', NOT_AN_INTEGER: 'Nur ganze Zahlen.', SUM_MISMATCH: 'Die Punkte müssen die Gesamtsumme ergeben.',
      MUST_ACCEPT: 'Sie müssen zustimmen, um fortzufahren.', INVALID_EMAIL: 'Geben Sie eine gültige E-Mail-Adresse ein.', INVALID_PHONE: 'Geben Sie eine gültige Telefonnummer ein.',
      INVALID_URL: 'Geben Sie eine gültige URL ein (https://…).', WRONG_RANK_COUNT: 'Ordnen Sie die geforderte Anzahl an Elementen.', MISSING_ROW: 'Beantworten Sie jede Zeile.',
      BELOW_MIN: 'Zu klein.', ABOVE_MAX: 'Zu groß.', NOT_ON_STEP: 'Wählen Sie einen Wert auf den Schiebereglerschritten.' }
  };
  var RTL = { ar: 1, he: 1, fa: 1, ur: 1 };
  function lang() { return String(state.locale || 'en').split('-')[0].toLowerCase(); }
  function t(key, vars) {
    var table = UI[lang()] || UI.en, text = table[key] != null ? table[key] : (UI.en[key] != null ? UI.en[key] : key);
    for (var k in (vars || {})) text = text.split('{' + k + '}').join(String(vars[k]));
    return text;
  }
  function applyLocale() {
    document.documentElement.lang = state.locale || 'en';
    document.documentElement.dir = RTL[lang()] ? 'rtl' : 'ltr';
    document.title = C.title;
  }

  // ---------- tiny DOM helpers (textContent only: no HTML injection from survey data) ----------
  function add(e, c) {
    if (c == null || c === false) return;
    if (Array.isArray(c)) { c.forEach(function (x) { add(e, x); }); return; }
    e.appendChild(typeof c === 'object' ? c : document.createTextNode(String(c)));
  }
  function h(tag, attrs) {
    var e = document.createElement(tag), k, v;
    for (k in (attrs || {})) {
      v = attrs[k];
      if (v == null || v === false) continue;
      if (k === 'class') e.className = v;
      else if (k.slice(0, 2) === 'on') e.addEventListener(k.slice(2), v);
      else if (k === 'value') e.value = v;
      else if (k === 'checked') e.checked = true;
      else if (k === 'selected') e.selected = true;
      else e.setAttribute(k, v === true ? '' : v);
    }
    for (var i = 2; i < arguments.length; i++) add(e, arguments[i]);
    return e;
  }
  function clear(e) { while (e.firstChild) e.removeChild(e.firstChild); }
  function store(op, val) {
    try {
      if (op === 'lang') { if (val) localStorage.setItem(KEY + ':lang', val); return localStorage.getItem(KEY + ':lang'); }
      if (op === 'get') return localStorage.getItem(KEY);
      if (op === 'set') localStorage.setItem(KEY, val); else localStorage.removeItem(KEY);
    } catch (e) { return null; }
  }

  var META_KEYS = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'ref', 'source', 'channel'];
  function collectMeta() {
    var q = new URLSearchParams(location.search), m = {}, any = false;
    META_KEYS.forEach(function (k) { var v = q.get(k); if (v) { m[k] = v.slice(0, 200); any = true; } });
    return any ? m : undefined;
  }

  function api(method, path, body) {
    return fetch(API + path, { method: method, headers: { 'Content-Type': 'application/json' }, body: body == null ? undefined : JSON.stringify(body) })
      .then(function (r) {
        return r.text().then(function (t) {
          var j = t ? JSON.parse(t) : {};
          if (!r.ok) { var e = new Error(j.message || ('HTTP ' + r.status)); e.status = r.status; e.details = j.details || []; throw e; }
          return j;
        });
      });
  }

  function msg(code) { var k = String(code).split(':')[0]; var table = UI[lang()] || UI.en; return table[k] || UI.en[k] || t('invalid', { code: code }); }

  // ---------- answer widgets: (question, current answer, set(payload, debounced)) -> element ----------
  function hashSeed(str) { var h = 1779033703 ^ str.length; for (var i = 0; i < str.length; i++) { h = Math.imul(h ^ str.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); } return h >>> 0; }
  function rng(seed) { return function () { seed = (seed + 0x6D2B79F5) | 0; var t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
  function opts(q) {
    var list = (q.type.options || []).slice();
    if (q.randomizeOptions && state.session) {
      var r = rng(hashSeed(state.session.id + '|' + q.id));
      for (var i = list.length - 1; i > 0; i--) { var j = Math.floor(r() * (i + 1)), t = list[i]; list[i] = list[j]; list[j] = t; }
    }
    return list;
  }
  function pills(q, a, set, from, to) {
    var row = h('div', { class: 'row' });
    for (var i = from; i <= to; i++) (function (n) {
      row.appendChild(h('button', { type: 'button', class: 'pill' + (a && a.number === n ? ' sel' : ''), onclick: function () { set({ number: n }); } }, n));
    })(i);
    return row;
  }
  function choiceInput(q, a, set, multi) {
    var cur = { choice: (a && a.choice || []).slice(), other: a && a.other || '' };
    var wrap = h('div');
    function push(now) { set(cur.choice.length || cur.other ? { choice: cur.choice, other: cur.other || undefined } : {}, !now); }
    opts(q).forEach(function (o) {
      var checked = cur.choice.indexOf(o.id) >= 0;
      wrap.appendChild(h('label', { class: 'opt' },
        h('input', { type: multi ? 'checkbox' : 'radio', name: 'q_' + q.id, checked: checked, onchange: function (e) {
          if (multi) { var i = cur.choice.indexOf(o.id); if (e.target.checked && i < 0) cur.choice.push(o.id); else if (!e.target.checked && i >= 0) cur.choice.splice(i, 1); }
          else { cur.choice = [o.id]; cur.other = ''; }
          push(!multi);
        } }), o.label));
    });
    if (q.type.allowOther) wrap.appendChild(h('label', { class: 'opt' }, t('other'),
      h('input', { type: 'text', class: 'in', value: cur.other, oninput: function (e) {
        cur.other = e.target.value; if (!multi && cur.other) cur.choice = []; push(); } })));
    return wrap;
  }

  var INPUTS = {
    short_text: function (q, a, set) { return h('input', { class: 'in', type: 'text', maxlength: q.type.maxLength, value: a && a.text || '', oninput: function (e) { set({ text: e.target.value }, true); } }); },
    long_text: function (q, a, set) { return h('textarea', { maxlength: q.type.maxLength, oninput: function (e) { set({ text: e.target.value }, true); } }, a && a.text || ''); },
    formatted_text: function (q, a, set) {
      var t = { EMAIL: 'email', PHONE: 'tel', URL: 'url' }[q.type.format] || 'text';
      return h('input', { class: 'in', type: t, value: a && a.text || '', oninput: function (e) { set({ text: e.target.value }, true); } });
    },
    number: function (q, a, set) {
      return h('input', { class: 'in', type: 'number', step: q.type.integerOnly ? 1 : 'any', min: q.type.min, max: q.type.max, value: a && a.number != null ? a.number : '',
        oninput: function (e) { set(e.target.value === '' ? {} : { number: Number(e.target.value) }, true); } });
    },
    date: function (q, a, set) { return h('input', { class: 'in', type: 'date', min: q.type.min, max: q.type.max, value: a && a.date || '', onchange: function (e) { set(e.target.value ? { date: e.target.value } : {}); } }); },
    yes_no: function (q, a, set) {
      return h('div', { class: 'row' }, [[true, t('yes')], [false, t('no')]].map(function (p) {
        return h('label', { class: 'opt' }, h('input', { type: 'radio', name: 'q_' + q.id, checked: a && a.bool === p[0], onchange: function () { set({ bool: p[0] }); } }), p[1]);
      }));
    },
    consent: function (q, a, set) {
      return h('label', { class: 'opt' }, h('input', { type: 'checkbox', checked: a && a.bool === true, onchange: function (e) { set(e.target.checked ? { bool: true } : {}); } }), q.type.statement);
    },
    single_choice: function (q, a, set) { return choiceInput(q, a, set, false); },
    multi_choice: function (q, a, set) { return choiceInput(q, a, set, true); },
    rating: function (q, a, set) { return pills(q, a, set, 1, q.type.max); },
    scale: function (q, a, set) {
      return h('div', null, pills(q, a, set, q.type.min, q.type.max), h('div', { class: 'scale-labels' }, h('span', null, q.type.minLabel), h('span', null, q.type.maxLabel)));
    },
    slider: function (q, a, set) {
      var out = h('strong', null, a && a.number != null ? a.number + ' ' + q.type.unit : '–');
      var input = h('input', { type: 'range', min: q.type.min, max: q.type.max, step: q.type.step, value: a && a.number != null ? a.number : q.type.min,
        oninput: function (e) { out.textContent = e.target.value + ' ' + q.type.unit; set({ number: Number(e.target.value) }, true); } });
      return h('div', { class: 'row' }, input, out);
    },
    ranking: function (q, a, set) {
      var labels = {}; opts(q).forEach(function (o) { labels[o.id] = o.label; });
      var order = (a && a.ranking && a.ranking.length >= opts(q).length) ? a.ranking.slice() : opts(q).map(function (o) { return o.id; });
      var top = q.type.topN, box = h('div');
      function payload() { return { ranking: top ? order.slice(0, top) : order.slice() }; }
      function draw() {
        clear(box);
        var ol = h('ol');
        order.forEach(function (id, i) {
          ol.appendChild(h('li', null, h('span', null, labels[id] || id),
            h('button', { type: 'button', disabled: i === 0, onclick: function () { order.splice(i - 1, 0, order.splice(i, 1)[0]); set(payload()); draw(); } }, '▲'),
            h('button', { type: 'button', disabled: i === order.length - 1, onclick: function () { order.splice(i + 1, 0, order.splice(i, 1)[0]); set(payload()); draw(); } }, '▼')));
        });
        box.appendChild(h('div', { class: 'rank' }, ol));
        if (top) box.appendChild(h('div', { class: 'q-desc' }, t('topN', { n: top })));
        if (!(a && a.ranking)) box.appendChild(h('button', { type: 'button', onclick: function () { set(payload()); } }, t('keepOrder')));
      }
      draw();
      return box;
    },
    matrix: function (q, a, set) {
      var cur = {}; Object.keys(a && a.matrix || {}).forEach(function (k) { cur[k] = a.matrix[k].slice(); });
      var multi = q.type.multiplePerRow;
      var head = h('tr', null, h('th'), q.type.columns.map(function (c) { return h('th', null, c.label); }));
      var body = q.type.rows.map(function (r) {
        return h('tr', null, h('td', null, r.label), q.type.columns.map(function (c) {
          return h('td', null, h('input', { type: multi ? 'checkbox' : 'radio', name: 'q_' + q.id + '_' + r.id, checked: (cur[r.id] || []).indexOf(c.id) >= 0, onchange: function (e) {
            var row = cur[r.id] || [];
            if (multi) { var i = row.indexOf(c.id); if (e.target.checked && i < 0) row.push(c.id); else if (i >= 0 && !e.target.checked) row.splice(i, 1); } else row = [c.id];
            cur[r.id] = row; set({ matrix: cur });
          } }));
        }));
      });
      return h('div', { style: 'overflow-x:auto' }, h('table', { class: 'mx' }, head, body));
    },
    constant_sum: function (q, a, set) {
      var vals = {}; opts(q).forEach(function (o) { vals[o.id] = a && a.allocation && a.allocation[o.id] || 0; });
      var left = h('div', { class: 'q-desc' });
      function total() { return Object.keys(vals).reduce(function (s, k) { return s + vals[k]; }, 0); }
      function refresh() { left.textContent = t('remaining', { left: q.type.total - total(), total: q.type.total }); }
      var rows = opts(q).map(function (o) {
        return h('label', { class: 'opt' }, h('span', { style: 'flex:1' }, o.label), h('input', { class: 'in', style: 'width:100px', type: 'number', min: 0, value: vals[o.id], oninput: function (e) {
          vals[o.id] = Number(e.target.value) || 0; refresh();
          if (total() === q.type.total) set({ allocation: vals }, true); else if (total() === 0) set({}, true);
        } }));
      });
      refresh();
      return h('div', null, rows, left);
    },
    statement: function (q) { return h('p', null, q.type.text); }
  };

  // ---------- saving ----------
  function set(q, payload, debounce) {
    clearTimeout(timers[q.id]);
    var run = function () {
      delete state.errors[q.id];
      api('PUT', '/responses/' + state.session.id + '/answers/' + encodeURIComponent(q.id), payload).then(function (s) {
        state.session = s; refreshAfterSave();
      }).catch(function (e) { state.errors[q.id] = (e.details && e.details.length ? e.details : [e.message]).map(msg); markError(q.id); });
    };
    if (debounce) timers[q.id] = setTimeout(run, 450); else run();
  }
  function visibleKey() { return state.session.questions.map(function (q) { return q.id + '|' + q.title; }).join(','); }
  function refreshAfterSave() {
    if (state.session.status === 'DISQUALIFIED') { showDisqualified(); return; }
    var k = visibleKey();
    if (k !== state.visibleKey) { state.visibleKey = k; renderSurvey(); } else updateProgress();
  }
  function markError(id) {
    var el = document.getElementById('err_' + id), card = document.getElementById('card_' + id);
    if (!el || !card) return;
    el.textContent = (state.errors[id] || []).join(' ');
    card.className = 'card' + (state.errors[id] && state.errors[id].length ? ' err' : '');
  }

  // ---------- screens ----------
  function setMeta(m) {
    state.meta = m;
    state.locale = m.locale || state.locale || 'en';
    if (m.title) C.title = m.title;
    if (m.description != null) C.description = m.description;
    if (m.welcome != null) C.welcome = m.welcome;
    if (m.thankYou != null) C.thankYou = m.thankYou;
    applyLocale();
  }

  function chooseLanguage(code) {
    store('lang', code);
    return api('GET', '?lang=' + encodeURIComponent(code)).then(function (m) { setMeta(m); welcome(); });
  }

  function header() { return [C.logo ? h('img', { class: 'logo', src: C.logo, alt: '' }) : null, h('h1', null, C.title)]; }

  function langName(code) {
    try { return new Intl.DisplayNames([code], { type: 'language' }).of(code) || code; } catch (e) { return code; }
  }

  function welcome() {
    clear(app);
    var m = state.meta, inv = new URLSearchParams(location.search).get('invite');
    var email = h('input', { class: 'in', type: 'email', placeholder: t('email') });
    var needsId = !m.anonymous && m.access === 'PUBLIC_LINK';
    var card = h('div', { class: 'card' }, C.description && h('p', null, C.description), C.welcome && h('p', null, C.welcome));
    if (m.locales && m.locales.length > 1) {
      var pick = h('select', { class: 'in', 'aria-label': t('language'), style: 'max-width:220px;margin-bottom:8px', onchange: function (e) { chooseLanguage(e.target.value); } },
        m.locales.map(function (l) { return h('option', { value: l, selected: l === state.locale }, langName(l)); }));
      card.insertBefore(pick, card.firstChild);
    }
    app.appendChild(h('div', null, header(), card));
    if (!m.open) { card.appendChild(h('p', { class: 'msg' }, t('closed'))); return; }
    if (m.access === 'INVITE_ONLY' && !inv) { card.appendChild(h('p', { class: 'msg' }, t('inviteOnly'))); return; }
    if (needsId) card.appendChild(email);
    var err = h('div', { class: 'msg' });
    var btn = h('button', { class: 'primary', type: 'button', onclick: function () {
      err.textContent = '';
      api('POST', '/responses', { respondentId: needsId ? email.value : undefined, invitation: inv || undefined, metadata: collectMeta(), locale: state.locale || undefined }).then(function (s) {
        store('set', s.id); begin(s);
      }).catch(function (e) { err.textContent = e.message; });
    } }, t('start'));
    card.appendChild(h('div', { class: 'row', style: 'margin-top:12px' }, btn));
    card.appendChild(err);
    var rid = store('get');
    if (rid) api('GET', '/responses/' + rid).then(function (s) {
      if (s.status === 'IN_PROGRESS') card.appendChild(h('div', { class: 'row', style: 'margin-top:8px' }, h('button', { type: 'button', onclick: function () { begin(s); } }, t('resume'))));
    }).catch(function () { store('del'); });
  }

  function showDisqualified() {
    clearInterval(state.timer); store('del'); clear(app);
    app.appendChild(h('div', null, header(), h('div', { class: 'card' }, h('p', null, state.session.message || t('notEligible')))));
  }

  function begin(s) { state.session = s; if (s.locale && s.locale !== state.locale) { state.locale = s.locale; applyLocale(); }
    if (s.status === 'DISQUALIFIED') { showDisqualified(); return; }
    state.session = s; state.step = 0; state.errors = {}; state.visibleKey = visibleKey(); renderSurvey(); startTimer(); }

  function startTimer() {
    clearInterval(state.timer);
    if (!state.session.deadline) return;
    var end = Date.parse(state.session.deadline);
    state.timer = setInterval(function () {
      var el = document.getElementById('timer'); if (!el) return;
      var left = Math.max(0, Math.round((end - Date.now()) / 1000));
      el.textContent = t('timeLeft') + ' ' + Math.floor(left / 60) + ':' + ('0' + (left % 60)).slice(-2);
    }, 1000);
  }

  function questionCard(q) {
    var a = state.session.answers[q.id];
    var build = INPUTS[q.type.kind] || function () { return h('p', { class: 'msg' }, t('unsupported', { kind: q.type.kind })); };
    var card = h('div', { class: 'card', id: 'card_' + q.id },
      h('div', { class: 'q-title' }, q.title, q.required && h('span', { class: 'req' }, '*')),
      q.description && h('div', { class: 'q-desc' }, q.description),
      build(q, a, function (payload, d) { set(q, payload, d); }),
      h('div', { class: 'msg', id: 'err_' + q.id }, (state.errors[q.id] || []).join(' ')));
    if (state.errors[q.id] && state.errors[q.id].length) card.className = 'card err';
    return card;
  }

  function answerable() { return state.session.questions.filter(function (q) { return q.type.kind !== 'statement'; }); }
  function updateProgress() {
    var bar = document.getElementById('bar'); if (!bar) return;
    var qs = answerable(), done = qs.filter(function (q) { return state.session.answers[q.id]; }).length;
    bar.style.width = (qs.length ? Math.round(100 * done / qs.length) : 100) + '%';
  }

  function pages() {
    var qs = state.session.questions;
    if (C.layout === 'PER_QUESTION') return qs.map(function (q) { return [q]; });
    if (C.layout === 'PER_SECTION') {
      var out = [], last = null;
      qs.forEach(function (q) { var sid = q.section ? q.section.id : ''; if (!out.length || sid !== last) { out.push([]); last = sid; } out[out.length - 1].push(q); });
      return out;
    }
    return [qs];
  }

  function renderSurvey() {
    clear(app);
    var pgs = pages();
    if (state.step >= pgs.length) state.step = Math.max(0, pgs.length - 1);
    app.appendChild(h('div', null, header()));
    if (state.session.deadline) app.appendChild(h('div', { class: 'timer', id: 'timer' }));
    if (C.showProgress) app.appendChild(h('div', { class: 'progress' }, h('div', { id: 'bar' })));
    var lastSection = null, multiSection = false;
    state.session.questions.forEach(function (q) { if (q.section && lastSection && q.section.id !== lastSection) multiSection = true; if (q.section) lastSection = q.section.id; });
    lastSection = null;
    (pgs[state.step] || []).forEach(function (q) {
      if (multiSection && q.section && q.section.id !== lastSection && q.section.title) app.appendChild(h('h2', { class: 'sec' }, q.section.title));
      lastSection = q.section ? q.section.id : null;
      app.appendChild(questionCard(q));
    });
    var nav = h('div', { class: 'row spread' });
    var last = state.step >= pgs.length - 1;
    nav.appendChild(pgs.length > 1 ? h('button', { type: 'button', disabled: state.step === 0, onclick: function () { state.step--; renderSurvey(); } }, t('back')) : h('span'));
    nav.appendChild(last
      ? h('button', { class: 'primary', type: 'button', id: 'submit', onclick: submit }, t('submit'))
      : h('button', { class: 'primary', type: 'button', onclick: function () { state.step++; renderSurvey(); } }, t('next')));
    app.appendChild(nav);
    app.appendChild(h('div', { class: 'msg', id: 'submit_err' }));
    updateProgress();
  }

  function submit() {
    var btn = document.getElementById('submit'); btn.disabled = true;
    api('POST', '/responses/' + state.session.id + '/submit').then(done).catch(function (e) {
      btn.disabled = false;
      state.errors = {}; var first = null;
      (e.details || []).forEach(function (d) {
        var i = d.indexOf(':'); if (i < 0) return;
        var id = d.slice(0, i); (state.errors[id] = state.errors[id] || []).push(msg(d.slice(i + 1)));
        if (!first) first = id;
      });
      if (first && C.layout !== 'ONE_PAGE') {
        var pgs = pages();
        for (var k = 0; k < pgs.length; k++) pgs[k].forEach(function (q) { if (q.id === first) state.step = k; });
      }
      renderSurvey();
      document.getElementById('submit_err').textContent = first ? t('fix') : e.message;
      var el = first && document.getElementById('card_' + first); if (el && el.scrollIntoView) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }

  function done(res) {
    clearInterval(state.timer); store('del'); clear(app);
    var card = h('div', { class: 'card' }, h('h2', null, res.thankYouMessage || C.thankYou || t('thanks')));
    if (res.score) {
      card.appendChild(h('div', { class: 'big' }, res.score.percent + '%'));
      if (res.score.band) {
        card.appendChild(h('h3', null, res.score.band));
        if (res.score.bandMessage) card.appendChild(h('p', null, res.score.bandMessage));
      } else {
        card.appendChild(h('p', null, t('points', { earned: res.score.earned, max: res.score.max }) + ' – ' + (res.score.passed ? t('passed') : t('notPassed'))));
      }
    }
    app.appendChild(h('div', null, header(), card));
    if (state.meta.publicResults) api('GET', '/results').then(showResults).catch(function () {});
  }

  function showResults(r) {
    var labels = {};
    state.session.questions.forEach(function (q) { (q.type.options || []).forEach(function (o) { labels[q.id + '|' + o.id] = o.label; }); });
    r.questions.forEach(function (q) {
      var counts = q.stats && q.stats.counts; if (!counts) return;
      var max = Math.max.apply(null, Object.keys(counts).map(function (k) { return counts[k]; }).concat([1]));
      var card = h('div', { class: 'card' }, h('div', { class: 'q-title' }, q.title), h('div', { class: 'q-desc' }, t('responses', { n: r.totalSubmitted })));
      Object.keys(counts).forEach(function (k) {
        card.appendChild(h('div', { class: 'bar' }, h('span', { style: 'width:' + Math.round(100 * counts[k] / max) + '%' }), h('em', null, (labels[q.id + '|' + k] || k) + ' – ' + counts[k])));
      });
      app.appendChild(card);
    });
  }

  // ---------- boot ----------
  var explicit = new URLSearchParams(location.search).get('lang') || store('lang');
  api('GET', explicit ? '?lang=' + encodeURIComponent(explicit) : '').then(function (m) {
    setMeta(m);
    if (!explicit && m.locales && m.locales.length > 1) {
      var prefs = (navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || 'en']);
      for (var i = 0; i < prefs.length; i++) {
        var want = String(prefs[i]).toLowerCase(), hit = null;
        m.locales.forEach(function (l) { if (!hit && (l.toLowerCase() === want || l.toLowerCase().split('-')[0] === want.split('-')[0])) hit = l; });
        if (hit) { if (hit !== m.locale) return api('GET', '?lang=' + encodeURIComponent(hit)).then(function (m2) { setMeta(m2); welcome(); }); break; }
      }
    }
    welcome();
  })
    .catch(function (e) { app.appendChild(h('div', { class: 'card' }, h('p', { class: 'msg' }, e.message))); });
})();
