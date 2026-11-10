/*
 * Site de consulta da NFL API.
 * - Na primeira visita pede uma chave READ_ONLY em POST /api/keys/register e a guarda no navegador.
 * - Se a chave deixar de valer (ex.: a API reiniciou), pede outra sozinho e repete a busca.
 * - Todo texto vindo da API é inserido com textContent/createTextNode: nada de innerHTML com dados.
 */
(() => {
  'use strict';

  const PAGE_SIZE = 9;
  const STORAGE_KEY = 'nflSiteApiKey';

  // ---------- Rótulos em português ----------
  const DIVISIONS = { EAST: 'Leste', NORTH: 'Norte', SOUTH: 'Sul', WEST: 'Oeste' };
  const ROOFS = { OPEN: 'Aberto', DOME: 'Fechado (domo)', RETRACTABLE: 'Retrátil' };
  const GAME_STATUS = {
    SCHEDULED: 'Agendado', IN_PROGRESS: 'Em andamento', FINAL: 'Finalizado',
    POSTPONED: 'Adiado', CANCELED: 'Cancelado',
  };
  const POSITIONS = {
    QB: 'Quarterback', RB: 'Running back', WR: 'Wide receiver', TE: 'Tight end', OL: 'Linha ofensiva',
    DL: 'Linha defensiva', LB: 'Linebacker', CB: 'Cornerback', S: 'Safety', K: 'Kicker', P: 'Punter',
  };

  // ---------- Utilidades ----------
  function h(tag, props, ...children) {
    const node = document.createElement(tag);
    for (const [name, value] of Object.entries(props || {})) {
      if (value == null || value === false) continue;
      if (name === 'class') node.className = value;
      else if (name.startsWith('on')) node.addEventListener(name.slice(2), value);
      else node.setAttribute(name, value === true ? '' : value);
    }
    for (const child of children.flat(Infinity)) {
      if (child == null || child === false) continue;
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
  }

  const $ = (selector) => document.querySelector(selector);

  const store = {
    get() { try { return localStorage.getItem(STORAGE_KEY); } catch { return null; } },
    set(value) { try { localStorage.setItem(STORAGE_KEY, value); } catch { /* sem armazenamento */ } },
    clear() { try { localStorage.removeItem(STORAGE_KEY); } catch { /* sem armazenamento */ } },
  };

  const number = new Intl.NumberFormat('pt-BR');
  const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

  function formatDate(isoDate) {
    if (!isoDate) return '—';
    const [y, m, d] = isoDate.split('-');
    return `${d}/${m}/${y}`;
  }

  function formatDateTime(isoDateTime) {
    if (!isoDateTime) return '—';
    const date = new Date(isoDateTime);
    return Number.isNaN(date.getTime())
      ? isoDateTime
      : date.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'site-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
  }

  /** Converte um link HATEOAS absoluto em caminho local (funciona em qualquer host/proxy). */
  function localPath(href) {
    const url = new URL(href, window.location.href);
    return url.pathname + url.search;
  }

  // ---------- Cliente da API ----------
  class ApiProblem extends Error {
    constructor(message, retryAfter) {
      super(message);
      this.retryAfter = retryAfter;
    }
  }

  let apiKey = store.get();
  let pendingKey = null;

  function rateLimited(response) {
    const seconds = Number(response.headers.get('Retry-After')) || null;
    return new ApiProblem(
      seconds
        ? `Muitas buscas em pouco tempo. Tente de novo em ${seconds} s.`
        : 'Muitas buscas em pouco tempo. Aguarde um instante e tente de novo.',
      seconds);
  }

  function registerKey() {
    if (!pendingKey) {
      pendingKey = (async () => {
        const response = await fetch('/api/keys/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': uuid() },
          body: JSON.stringify({ owner: 'Site de consulta NFL' }),
        });
        if (response.status === 429) throw rateLimited(response);
        if (!response.ok) throw new ApiProblem('Não foi possível iniciar a consulta. Tente novamente.');
        const data = await response.json();
        apiKey = data.apiKey;
        store.set(apiKey);
      })().finally(() => { pendingKey = null; });
    }
    return pendingKey;
  }

  async function apiGet(path, attempt = 0) {
    if (!apiKey) await registerKey();
    const response = await fetch(path, { headers: { 'X-API-Key': apiKey, Accept: 'application/hal+json' } });
    if (response.status === 401 && attempt === 0) {
      apiKey = null;
      store.clear();
      await registerKey();
      return apiGet(path, 1);
    }
    if (response.status === 429) throw rateLimited(response);
    if (!response.ok) {
      let message = 'Não foi possível carregar os dados.';
      try { message = (await response.json()).message || message; } catch { /* corpo vazio */ }
      throw new ApiProblem(message);
    }
    return response.json();
  }

  // ---------- Definição das abas ----------
  const text = (name, label, placeholder) => ({ type: 'text', name, label, placeholder });
  const num = (name, label, placeholder, min = 0) => ({ type: 'number', name, label, placeholder, min });
  const select = (name, label, options) => ({ type: 'select', name, label, options });
  const all = [['', 'Todos']];
  const entries = (map) => Object.entries(map);

  const TABS = {
    teams: {
      label: 'Times', rel: 'teams',
      fields: [
        text('name', 'Nome do time', 'Ex.: Eagles'),
        select('conference', 'Conferência', [...all, ['AFC', 'AFC'], ['NFC', 'NFC']]),
        select('division', 'Divisão', [...all, ...entries(DIVISIONS)]),
      ],
      request: (v) => ({ path: '/api/teams/search', params: v }),
      card: teamCard, detail: teamDetail,
    },
    players: {
      label: 'Jogadores', rel: 'players',
      fields: [
        text('name', 'Nome do jogador', 'Ex.: Mahomes'),
        select('position', 'Posição', [...all, ...entries(POSITIONS).map(([k, v]) => [k, `${k} · ${v}`])]),
        { type: 'select', name: 'teamId', label: 'Time', options: () => teamOptions },
      ],
      request: (v) => ({ path: '/api/players/search', params: v }),
      card: playerCard, detail: playerDetail,
    },
    stadiums: {
      label: 'Estádios', rel: 'stadiums',
      fields: [
        num('minCapacity', 'Capacidade mínima', 'Ex.: 70000'),
        select('roofType', 'Cobertura', [...all, ...entries(ROOFS)]),
      ],
      request: (v) => ({ path: '/api/stadiums/search', params: v }),
      card: stadiumCard, detail: stadiumDetail,
    },
    sponsors: {
      label: 'Patrocinadores', rel: 'sponsors',
      fields: [
        text('industry', 'Setor', 'Ex.: Bebidas'),
        num('minValue', 'Valor anual mínimo (US$)', 'Ex.: 100000000'),
      ],
      request: (v) => ({ path: '/api/sponsors/search', params: v }),
      card: sponsorCard, detail: sponsorDetail,
    },
    games: {
      label: 'Partidas', rel: 'games',
      fields: [
        num('season', 'Temporada', 'Ex.: 2026', 1920),
        num('week', 'Semana', '1 a 22', 1),
        select('status', 'Situação', [...all, ...entries(GAME_STATUS)]),
      ],
      defaults: { season: '2026' },
      hint: 'Informe a temporada para filtrar por semana e situação.',
      request: (v) => (v.season
        ? { path: '/api/games/search', params: v }
        : { path: '/api/games', params: {} }),
      card: gameCard, detail: gameDetail,
    },
  };

  // ---------- Estado ----------
  const state = { tab: 'teams', page: 0, values: {}, loadToken: 0, teamsLoaded: false };
  let teamOptions = [...all];
  const filtersEl = $('#filters');
  const resultsEl = $('#results');
  const statusEl = $('#status');
  const pagerEl = $('#pager');
  const tabsEl = $('#tabs');
  const dialog = $('#detail');

  function valuesFor(tab) {
    if (!state.values[tab]) state.values[tab] = { ...(TABS[tab].defaults || {}) };
    return state.values[tab];
  }

  // ---------- Abas ----------
  function renderTabs() {
    tabsEl.replaceChildren(...Object.entries(TABS).map(([key, tab]) =>
      h('button', {
        type: 'button', role: 'tab', id: `tab-${key}`, 'aria-controls': 'panel',
        'aria-selected': String(key === state.tab), tabindex: key === state.tab ? '0' : '-1',
        class: 'tab', 'data-tab': key,
      }, tab.label)));
  }

  function selectTab(key, { focus = false } = {}) {
    if (!TABS[key]) key = 'teams';
    state.tab = key;
    state.page = 0;
    if (window.location.hash !== `#${key}`) history.replaceState(null, '', `#${key}`);
    renderTabs();
    renderFilters();
    $('#panel').setAttribute('aria-labelledby', `tab-${key}`);
    if (focus) $(`#tab-${key}`).focus();
    if (key === 'players') loadTeamOptions();
    load();
  }

  tabsEl.addEventListener('click', (event) => {
    const button = event.target.closest('[data-tab]');
    if (button) selectTab(button.dataset.tab);
  });

  tabsEl.addEventListener('keydown', (event) => {
    const keys = Object.keys(TABS);
    const index = keys.indexOf(state.tab);
    let next = null;
    if (event.key === 'ArrowRight') next = keys[(index + 1) % keys.length];
    if (event.key === 'ArrowLeft') next = keys[(index - 1 + keys.length) % keys.length];
    if (event.key === 'Home') next = keys[0];
    if (event.key === 'End') next = keys[keys.length - 1];
    if (next) {
      event.preventDefault();
      selectTab(next, { focus: true });
    }
  });

  // ---------- Filtros ----------
  let debounceTimer = null;

  function renderFilters() {
    const tab = TABS[state.tab];
    const values = valuesFor(state.tab);

    const controls = tab.fields.map((field) => {
      const id = `f-${field.name}`;
      let control;
      if (field.type === 'select') {
        const options = typeof field.options === 'function' ? field.options() : field.options;
        control = h('select', { id, name: field.name },
          options.map(([value, label]) => h('option', { value }, label)));
        control.value = values[field.name] ?? '';
        control.addEventListener('change', () => onFilterChange(field.name, control.value, true));
      } else {
        control = h('input', {
          id, name: field.name, type: field.type, placeholder: field.placeholder,
          min: field.min, autocomplete: 'off', inputmode: field.type === 'number' ? 'numeric' : null,
        });
        control.value = values[field.name] ?? '';
        control.addEventListener('input', () => onFilterChange(field.name, control.value, false));
      }
      return h('div', { class: 'field' }, h('label', { for: id }, field.label), control);
    });

    const clear = h('button', { type: 'button', class: 'btn btn-ghost', onclick: clearFilters }, 'Limpar filtros');
    const form = h('form', { class: 'filters-form', role: 'search', 'aria-label': `Filtros de ${tab.label}` },
      controls, h('div', { class: 'field field-action' }, clear));
    form.addEventListener('submit', (event) => { event.preventDefault(); load(); });
    filtersEl.replaceChildren(...[form, tab.hint ? h('p', { class: 'hint' }, tab.hint) : null].filter(Boolean));
  }

  function onFilterChange(name, value, immediate) {
    valuesFor(state.tab)[name] = value;
    state.page = 0;
    clearTimeout(debounceTimer);
    if (immediate) load();
    else debounceTimer = setTimeout(load, 400);
  }

  function clearFilters() {
    state.values[state.tab] = { ...(TABS[state.tab].defaults || {}) };
    state.page = 0;
    renderFilters();
    load();
  }

  async function loadTeamOptions() {
    if (state.teamsLoaded) return;
    try {
      const data = await apiGet('/api/teams?size=100&sort=name');
      const teams = data._embedded?.teams ?? [];
      teamOptions = [['', 'Todos'], ...teams.map((t) => [String(t.id), t.name])];
      state.teamsLoaded = true;
      if (state.tab === 'players') renderFilters();
    } catch { /* o filtro por time simplesmente fica só com "Todos" */ }
  }

  // ---------- Carregamento e resultados ----------
  function setStatus(message, kind = 'info') {
    statusEl.className = `status status-${kind}`;
    statusEl.replaceChildren(...(typeof message === 'string' ? [message] : message));
  }

  async function load() {
    const token = ++state.loadToken;
    const tab = TABS[state.tab];
    const { path, params } = tab.request(valuesFor(state.tab));

    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(params)) {
      if (String(value).trim() !== '') query.set(name, String(value).trim());
    }
    query.set('page', state.page);
    query.set('size', PAGE_SIZE);

    resultsEl.setAttribute('aria-busy', 'true');
    setStatus('Carregando…');
    try {
      const data = await apiGet(`${path}?${query}`);
      if (token !== state.loadToken) return; // chegou uma busca mais nova
      const items = data._embedded?.[tab.rel] ?? [];
      const page = data.page || { totalElements: items.length, totalPages: 1, number: 0 };
      renderResults(tab, items, page);
    } catch (error) {
      if (token !== state.loadToken) return;
      resultsEl.replaceChildren();
      pagerEl.replaceChildren();
      setStatus([
        error.message || 'Algo deu errado.', ' ',
        h('button', { type: 'button', class: 'link-btn', onclick: load }, 'Tentar novamente'),
      ], 'error');
    } finally {
      if (token === state.loadToken) resultsEl.setAttribute('aria-busy', 'false');
    }
  }

  function renderResults(tab, items, page) {
    if (items.length === 0) {
      resultsEl.replaceChildren(h('div', { class: 'empty' },
        h('p', { class: 'empty-title' }, 'Nada encontrado'),
        h('p', null, 'Nenhum resultado para esses filtros.'),
        h('button', { type: 'button', class: 'btn', onclick: clearFilters }, 'Limpar filtros')));
      pagerEl.replaceChildren();
      setStatus('0 resultados');
      return;
    }
    resultsEl.replaceChildren(...items.map((item) => {
      const card = tab.card(item);
      card.classList.add('card');
      card.tabIndex = 0;
      card.setAttribute('role', 'button');
      card.addEventListener('click', () => openDetail(tab, item));
      card.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openDetail(tab, item);
        }
      });
      return card;
    }));

    const total = page.totalElements;
    setStatus(`${number.format(total)} ${total === 1 ? 'resultado' : 'resultados'}`);
    renderPager(page);
  }

  function renderPager(page) {
    if (page.totalPages <= 1) {
      pagerEl.replaceChildren();
      return;
    }
    const go = (delta) => () => {
      state.page = Math.max(0, Math.min(page.totalPages - 1, state.page + delta));
      load();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    pagerEl.replaceChildren(
      h('button', { type: 'button', class: 'btn', disabled: page.number <= 0, onclick: go(-1) }, '← Anterior'),
      h('span', { class: 'pager-info' }, `Página ${page.number + 1} de ${page.totalPages}`),
      h('button', { type: 'button', class: 'btn', disabled: page.number >= page.totalPages - 1, onclick: go(1) }, 'Próxima →'));
  }

  // ---------- Cartões ----------
  const chip = (label, kind) => h('span', { class: `chip${kind ? ' chip-' + kind : ''}` }, label);
  const meta = (label, value) => (value == null || value === '') ? null
    : h('li', null, h('span', { class: 'meta-label' }, label), h('span', null, value));

  function teamCard(t) {
    return h('article', { class: `team-${t.conference.toLowerCase()}`, 'aria-label': t.name },
      h('div', { class: 'card-head' },
        h('span', { class: 'badge' }, t.abbreviation),
        h('div', null, h('h3', null, t.name), h('p', { class: 'sub' }, t.city))),
      h('div', { class: 'chips' }, chip(t.conference, t.conference.toLowerCase()), chip(DIVISIONS[t.division] || t.division)),
      h('ul', { class: 'meta' },
        meta('Fundado em', t.foundedYear),
        meta('Super Bowls', t.superBowlTitles),
        meta('Estádio', t.stadiumName),
        meta('Jogadores', t.playerCount)),
      h('span', { class: 'more' }, 'Ver detalhes'));
  }

  function playerCard(p) {
    return h('article', { 'aria-label': `${p.firstName} ${p.lastName}` },
      h('div', { class: 'card-head' },
        h('span', { class: 'badge badge-number' }, `#${p.jerseyNumber}`),
        h('div', null, h('h3', null, `${p.firstName} ${p.lastName}`), h('p', { class: 'sub' }, p.teamName || 'Sem time'))),
      h('div', { class: 'chips' }, chip(p.position, 'pos'), chip(POSITIONS[p.position] || '')),
      h('ul', { class: 'meta' },
        meta('Universidade', p.college),
        meta('Altura', p.heightCm ? `${p.heightCm} cm` : null),
        meta('Peso', p.weightKg ? `${p.weightKg} kg` : null)),
      h('span', { class: 'more' }, 'Ver detalhes'));
  }

  function stadiumCard(s) {
    return h('article', { 'aria-label': s.name },
      h('div', { class: 'card-head' },
        h('span', { class: 'badge badge-field' }, 'EST'),
        h('div', null, h('h3', null, s.name), h('p', { class: 'sub' }, s.city))),
      h('div', { class: 'chips' }, chip(ROOFS[s.roofType] || s.roofType, 'roof')),
      h('ul', { class: 'meta' },
        meta('Capacidade', number.format(s.capacity)),
        meta('Inaugurado em', s.openedYear),
        meta('Time', s.teamName)),
      h('span', { class: 'more' }, 'Ver detalhes'));
  }

  function sponsorCard(s) {
    return h('article', { 'aria-label': s.name },
      h('div', { class: 'card-head' },
        h('span', { class: 'badge badge-gold' }, '$'),
        h('div', null, h('h3', null, s.name), h('p', { class: 'sub' }, s.industry))),
      h('ul', { class: 'meta' },
        meta('Valor anual', money.format(s.annualValueUsd)),
        meta('Times', s.teams.length ? s.teams.join(', ') : 'Nenhum')),
      h('span', { class: 'more' }, 'Ver detalhes'));
  }

  function gameCard(g) {
    const played = g.homeScore != null && g.awayScore != null;
    const winnerHome = g.winner && g.winner === g.homeTeamName;
    const winnerAway = g.winner && g.winner === g.awayTeamName;
    const line = (name, score, winner) => h('div', { class: `score-line${winner ? ' winner' : ''}` },
      h('span', null, name), h('strong', null, played ? score : '–'));
    return h('article', { 'aria-label': `${g.homeTeamName} contra ${g.awayTeamName}` },
      h('div', { class: 'chips' },
        chip(`Temporada ${g.season} · Semana ${g.week}`),
        chip(GAME_STATUS[g.status] || g.status, g.status === 'FINAL' ? 'final' : 'status')),
      h('div', { class: 'scoreboard' }, line(g.homeTeamName, g.homeScore, winnerHome), line(g.awayTeamName, g.awayScore, winnerAway)),
      h('ul', { class: 'meta' },
        meta('Kickoff', formatDateTime(g.kickoff)),
        meta('Estádio', g.stadiumName),
        meta('Vencedor', g.winner)),
      h('span', { class: 'more' }, 'Ver detalhes'));
  }

  // ---------- Detalhes (janela) ----------
  function definitionList(rows) {
    return h('dl', { class: 'details' }, rows.filter(([, value]) => value != null && value !== '')
      .map(([label, value]) => [h('dt', null, label), h('dd', null, value)]));
  }

  function openDetail(tab, item) {
    const { title, subtitle, body, extra } = tab.detail(item);
    $('#detail-title').textContent = title;
    $('#detail-subtitle').textContent = subtitle || '';
    const content = $('#detail-body');
    content.replaceChildren(body);
    if (extra) extra(content);
    dialog.showModal();
  }

  async function loadList(container, heading, href, renderItem, emptyText) {
    const section = h('section', { class: 'related' }, h('h4', null, heading), h('p', { class: 'sub' }, 'Carregando…'));
    container.append(section);
    try {
      const sep = href.includes('?') ? '&' : '?';
      const data = await apiGet(`${localPath(href)}${sep}size=50`);
      const key = Object.keys(data._embedded || {})[0];
      const items = key ? data._embedded[key] : [];
      section.replaceChildren(h('h4', null, `${heading} (${items.length})`),
        items.length ? h('ul', { class: 'related-list' }, items.map((i) => h('li', null, renderItem(i))))
          : h('p', { class: 'sub' }, emptyText));
    } catch (error) {
      section.replaceChildren(h('h4', null, heading), h('p', { class: 'sub' }, error.message));
    }
  }

  function teamDetail(t) {
    return {
      title: t.name, subtitle: `${t.city} · ${t.conference} ${DIVISIONS[t.division] || t.division}`,
      body: definitionList([
        ['Sigla', t.abbreviation], ['Fundado em', t.foundedYear], ['Títulos de Super Bowl', t.superBowlTitles],
        ['Estádio', t.stadiumName], ['Patrocinadores', t.sponsors.join(', ')],
      ]),
      extra: (container) => {
        if (t._links?.players) {
          loadList(container, 'Elenco', t._links.players.href,
            (p) => `#${p.jerseyNumber} ${p.firstName} ${p.lastName} — ${p.position}`, 'Nenhum jogador cadastrado.');
        }
      },
    };
  }

  function playerDetail(p) {
    return {
      title: `${p.firstName} ${p.lastName}`, subtitle: `#${p.jerseyNumber} · ${POSITIONS[p.position] || p.position}`,
      body: definitionList([
        ['Time', p.teamName || 'Sem time (free agent)'], ['Nascimento', formatDate(p.birthDate)],
        ['Altura', p.heightCm ? `${p.heightCm} cm` : null], ['Peso', p.weightKg ? `${p.weightKg} kg` : null],
        ['Universidade', p.college],
      ]),
    };
  }

  function stadiumDetail(s) {
    return {
      title: s.name, subtitle: s.city,
      body: definitionList([
        ['Capacidade', number.format(s.capacity)], ['Cobertura', ROOFS[s.roofType] || s.roofType],
        ['Inaugurado em', s.openedYear], ['Time mandante', s.teamName],
      ]),
      extra: (container) => {
        if (s._links?.games) {
          loadList(container, 'Jogos no estádio', s._links.games.href,
            (g) => `${g.homeTeamName} x ${g.awayTeamName} — ${formatDateTime(g.kickoff)}`, 'Nenhum jogo cadastrado.');
        }
      },
    };
  }

  function sponsorDetail(s) {
    const site = /^https?:\/\//i.test(s.website || '')
      ? h('a', { href: s.website, target: '_blank', rel: 'noopener noreferrer' }, s.website) : null;
    return {
      title: s.name, subtitle: s.industry,
      body: definitionList([
        ['Valor anual', money.format(s.annualValueUsd)], ['Site', site],
        ['Times patrocinados', s.teams.join(', ') || 'Nenhum'],
      ]),
    };
  }

  function gameDetail(g) {
    return {
      title: `${g.homeTeamName} x ${g.awayTeamName}`,
      subtitle: `Temporada ${g.season} · Semana ${g.week}`,
      body: definitionList([
        ['Situação', GAME_STATUS[g.status] || g.status],
        ['Placar', g.homeScore != null && g.awayScore != null
          ? `${g.homeTeamName} ${g.homeScore} – ${g.awayScore} ${g.awayTeamName}` : null],
        ['Vencedor', g.winner], ['Kickoff', formatDateTime(g.kickoff)], ['Estádio', g.stadiumName],
      ]),
    };
  }

  // ---------- Janela de detalhes: fechar ----------
  $('#detail-close').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });

  // ---------- Início ----------
  window.addEventListener('hashchange', () => {
    const key = window.location.hash.slice(1);
    if (TABS[key] && key !== state.tab) selectTab(key);
  });

  const initial = window.location.hash.slice(1);
  selectTab(TABS[initial] ? initial : 'teams');
})();
