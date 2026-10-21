// End-to-end tests of the generated respondent page against in-memory fakes of the public API.
// Run: npm i jsdom && node survey-adapter-presentation/src/test/js/respondent-page.e2e.js samples
const { JSDOM } = require('jsdom');
const fs = require('fs');
const dir = process.argv[2] || 'samples';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const json = (status, body) => Promise.resolve({ ok: status < 400, status, text: () => Promise.resolve(JSON.stringify(body)) });
let failures = 0;
const ok = (c, m) => { if (c) console.log('  ok -', m); else { failures++; console.error('  FAIL -', m); } };

function open(file, url, handler, languages) {
  const html = fs.readFileSync(dir + '/' + file, 'utf8');
  const calls = [];
  const dom = new JSDOM(html, { url, runScripts: 'dangerously', pretendToBeVisual: true, beforeParse(w) {
    w.fetch = (u, o) => {
      const m = (o && o.method) || 'GET', body = o && o.body ? JSON.parse(o.body) : null;
      const p = u.replace(/.*\/api\/v1\/public\/[^/?]+/, '');
      calls.push({ m, p, body });
      return handler(m, p, body);
    };
    if (languages) { Object.defineProperty(w.navigator, 'languages', { value: languages, configurable: true }); Object.defineProperty(w.navigator, 'language', { value: languages[0], configurable: true }); }
    w.scrollTo = () => {}; w.HTMLElement.prototype.scrollIntoView = () => {};
  } });
  const d = dom.window.document;
  return { dom, d, calls, text: () => d.body.textContent,
    btn: l => [...d.querySelectorAll('button')].find(b => b.textContent === l),
    fire: (el, t) => el.dispatchEvent(new dom.window.Event(t, { bubbles: true })) };
}

async function scenarioNps() {
  console.log('Scenario 1: NPS, per-question layout, server-driven branching');
  const Q = {
    customer_type: { id: 'customer_type', title: 'How long have you been a customer?', required: false, type: { kind: 'single_choice', allowOther: false, options: [{ id: 'new', label: 'Less than a year' }, { id: 'returning', label: 'More than a year' }] } },
    nps: { id: 'nps', title: 'How likely are you to recommend us?', required: true, type: { kind: 'scale', min: 0, max: 10, minLabel: 'No', maxLabel: 'Yes' } },
    reason_low: { id: 'reason_low', title: 'What went wrong?', required: true, type: { kind: 'long_text', maxLength: 2000 } },
  };
  let answers = {}, submitted = false;
  const visible = () => ['customer_type', 'nps'].concat(answers.nps && answers.nps.number <= 6 ? ['reason_low'] : []).map(k => Q[k]);
  const session = () => ({ id: 'r1', surveyId: 's1', status: submitted ? 'SUBMITTED' : 'IN_PROGRESS', attempt: 1, questions: visible(), answers });
  const t = open('respondent-nps.html', 'http://localhost/p/TOKEN123', (m, p, body) => {
    if (p === '' && m === 'GET') return json(200, { title: 'T', open: true, access: 'PUBLIC_LINK', anonymous: true, publicResults: false });
    if (p === '/responses' && m === 'POST') return json(201, session());
    let mm;
    if ((mm = p.match(/^\/responses\/r1\/answers\/(\w+)$/)) && m === 'PUT') { if (!Object.keys(body).length) delete answers[mm[1]]; else answers[mm[1]] = body; return json(200, session()); }
    if (p === '/responses/r1/submit') {
      const bad = visible().filter(q => q.required && !answers[q.id]).map(q => q.id + ':REQUIRED');
      if (bad.length) return json(422, { message: 'Response cannot be submitted', details: bad });
      submitted = true; return json(200, { id: 'r1', thankYouMessage: 'Thank you for your feedback!' });
    }
    return json(404, { message: 'nope ' + m + ' ' + p });
  });
  await sleep(50);
  ok(t.text().includes('Two minutes, we promise.') && t.btn('Start'), 'themed welcome screen');
  t.btn('Start').click(); await sleep(50);
  ok(t.text().includes('How long have you been a customer?') && !t.text().includes('recommend us'), 'one question per page');
  const radio = t.d.querySelector('input[type=radio]'); radio.checked = true; t.fire(radio, 'change'); await sleep(50);
  ok(answers.customer_type && answers.customer_type.choice[0] === 'new', 'single choice saved immediately as {choice:[id]}');
  t.btn('Next').click(); await sleep(20);
  ok(t.d.querySelectorAll('button.pill').length === 11, 'scale 0..10 renders 11 pills');
  [...t.d.querySelectorAll('button.pill')].find(b => b.textContent === '3').click(); await sleep(50);
  ok(answers.nps.number === 3 && t.btn('Next') && !t.btn('Submit'), 'low score reveals the follow-up (Next, not Submit)');
  t.btn('Next').click(); await sleep(20);
  t.btn('Submit').click(); await sleep(60);
  ok(/required/i.test(t.text()), 'server REQUIRED error shown');
  const ta = t.d.querySelector('textarea'); ta.value = 'Too slow'; t.fire(ta, 'input'); await sleep(700);
  ok(answers.reason_low && answers.reason_low.text === 'Too slow', 'debounced text saved');
  t.btn('Submit').click(); await sleep(60);
  ok(t.text().includes('Thank you for your feedback!'), 'thank-you screen');
  ok(!t.dom.window.localStorage.getItem('survey:TOKEN123'), 'resume pointer cleared');
}

async function scenarioSections() {
  console.log('Scenario 2: sections as pages, campaign tags, screen-out, stable random order');
  const sec = { eligibility: { id: 'sec1', title: 'Eligibility' }, views: { id: 'sec2', title: 'Your views' } };
  const parties = ['pa', 'pb', 'pc', 'pd', 'pe', 'pf'].map(id => ({ id, label: 'Party ' + id.toUpperCase() }));
  const Q = {
    registered: { id: 'registered', title: 'Registered?', required: true, type: { kind: 'yes_no' }, section: sec.eligibility },
    age: { id: 'age', title: 'Age group', required: true, type: { kind: 'single_choice', options: [{ id: 'a18', label: '18-29' }, { id: 'a30', label: '30+' }] }, section: sec.eligibility },
    intent: { id: 'intent', title: 'Vote intention', required: true, randomizeOptions: true, type: { kind: 'single_choice', options: parties }, section: sec.views },
  };
  let answers = {}, status = 'IN_PROGRESS';
  const session = () => ({ id: 'r9', surveyId: 's2', status, attempt: 1, questions: Object.values(Q), answers,
    message: status === 'DISQUALIFIED' ? 'This poll is for registered voters only.' : undefined });
  const handler = (m, p, body) => {
    if (p === '' && m === 'GET') return json(200, { title: 'Poll', open: true, access: 'PUBLIC_LINK', anonymous: true, publicResults: false });
    if (p === '/responses' && m === 'POST') return json(201, session());
    let mm;
    if ((mm = p.match(/^\/responses\/r9\/answers\/(\w+)$/)) && m === 'PUT') {
      answers[mm[1]] = body;
      if (mm[1] === 'registered' && body.bool === false) status = 'DISQUALIFIED';
      return json(200, session());
    }
    return json(404, { message: 'nope' });
  };

  let t = open('respondent-voter-poll.html', 'http://localhost/p/TOK?utm_source=poster&utm_medium=print&evil=1&utm_campaign=' + 'x'.repeat(300), handler);
  await sleep(50); t.btn('Start').click(); await sleep(50);
  const start = t.calls.find(c => c.m === 'POST' && c.p === '/responses');
  ok(start.body.metadata && start.body.metadata.utm_source === 'poster' && start.body.metadata.utm_medium === 'print', 'utm tags from the link are sent on start');
  ok(!('evil' in start.body.metadata) && start.body.metadata.utm_campaign.length === 200, 'unknown params dropped, long values capped');
  ok(t.text().includes('Eligibility') && t.text().includes('Registered?') && !t.text().includes('Vote intention'), 'page 1 = first section with its heading');
  const yes = [...t.d.querySelectorAll('input[type=radio]')][0]; yes.checked = true; t.fire(yes, 'change'); await sleep(50);
  ok(t.btn('Next') && !t.btn('Submit'), 'sections give a Next button');
  t.btn('Next').click(); await sleep(20);
  ok(t.text().includes('Vote intention') && !t.text().includes('Registered?'), 'page 2 = second section');
  const order1 = [...t.d.querySelectorAll('label.opt')].map(l => l.textContent);
  ok(order1.length === 6 && new Set(order1).size === 6, 'all options rendered once');

  // same respondent, reload: random order must be identical (seeded by response id + question)
  const t2 = open('respondent-voter-poll.html', 'http://localhost/p/TOK', handler);
  await sleep(50);
  ok(!!t2.btn('Start'), 'second load works');
  t2.btn('Start').click(); await sleep(50); t2.btn('Next') && (t2.btn('Next').click(), await sleep(20));
  const order2 = [...t2.d.querySelectorAll('label.opt')].map(l => l.textContent);
  ok(JSON.stringify(order1) === JSON.stringify(order2), 'random option order is stable for the same respondent: ' + order1.join(','));
  ok(JSON.stringify(order1) !== JSON.stringify(parties.map(p => p.label)), 'random option order differs from the authored order');

  // screen-out
  answers = {}; status = 'IN_PROGRESS';
  t = open('respondent-voter-poll.html', 'http://localhost/p/TOK', handler);
  await sleep(50); t.btn('Start').click(); await sleep(50);
  const no = [...t.d.querySelectorAll('input[type=radio]')][1]; no.checked = true; t.fire(no, 'change'); await sleep(60);
  ok(t.text().includes('registered voters only') && !t.btn('Submit') && !t.btn('Next'), 'screened-out respondents see the message and no form');
}


async function scenarioLanguages() {
  console.log('Scenario 3: language picker, translated interface, right-to-left');
  const base = { description: '', welcome: '', thankYou: 'x', open: true, access: 'PUBLIC_LINK', anonymous: true, publicResults: false, locales: ['en', 'id', 'ar'] };
  const metas = {
    en: { ...base, title: 'Visit feedback', locale: 'en' },
    id: { ...base, title: 'Masukan kunjungan', locale: 'id' },
    ar: { ...base, title: 'آراء الزيارة', locale: 'ar' },
  };
  const requests = [];
  const handler = (m, p, body) => {
    requests.push(m + ' ' + p);
    if (m === 'GET' && (p === '' || p.startsWith('?lang='))) return json(200, metas[p.startsWith('?lang=') ? p.slice(6) : 'en']);
    if (m === 'POST' && p === '/responses') return json(201, { id: 'r7', status: 'IN_PROGRESS', attempt: 1, locale: body.locale, questions: [], answers: {} });
    return json(404, { message: 'nope' });
  };

  // browser prefers Indonesian -> picked automatically after the first meta response lists the languages
  let t = open('respondent-nps.html', 'http://localhost/p/TOK', handler, ['id-ID', 'en']);
  await sleep(80);
  ok(t.text().includes('Masukan kunjungan') && t.btn('Mulai'), 'browser language (id-ID) selects Indonesian: translated title and "Mulai" button');
  ok(t.d.documentElement.lang === 'id' && t.d.documentElement.dir === 'ltr', 'html lang/dir set');
  const sel = t.d.querySelector('select');
  ok(sel && sel.options.length === 3 && sel.value === 'id', 'language picker offers the survey languages');

  sel.value = 'ar'; t.fire(sel, 'change'); await sleep(60);
  ok(t.d.documentElement.dir === 'rtl' && t.text().includes('آراء الزيارة'), 'Arabic switches the page to right-to-left with the translated title');
  ok(t.btn('Start'), 'languages without a built-in interface dictionary fall back to English labels');
  ok(Object.keys(t.dom.window.localStorage).some(k => k.endsWith(':lang')) && Object.keys(t.dom.window.localStorage).map(k => t.dom.window.localStorage.getItem(k)).includes('ar'), 'choice is remembered');
  t.btn('Start').click(); await sleep(60);
  const start = JSON.parse(JSON.stringify(requests));
  ok(start.includes('POST /responses'), 'response started');

  // explicit ?lang= wins over the browser language
  t = open('respondent-nps.html', 'http://localhost/p/TOK2?lang=en', handler, ['id-ID']);
  await sleep(80);
  ok(t.btn('Start') && t.text().includes('Visit feedback'), 'explicit ?lang=en beats the browser language');
  const sent = [];
  const t3 = open('respondent-nps.html', 'http://localhost/p/TOK3?lang=id', (m, p, b) => { if (m === 'POST') sent.push(b); return handler(m, p, b); });
  await sleep(80); t3.btn('Mulai').click(); await sleep(60);
  ok(sent.length === 1 && sent[0].locale === 'id', 'start request carries the chosen locale');
}

(async () => {
  await scenarioNps();
  await scenarioSections();
  await scenarioLanguages();
  console.log(failures ? failures + ' FAILED' : 'All page tests passed.');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
