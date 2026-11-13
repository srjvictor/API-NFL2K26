/*
 * Site da NFL API: consulta pública + criação, edição e exclusão para quem entra com uma chave de API.
 *
 * - Visitante: na primeira visita o site pede sozinho uma chave READ_ONLY (POST /api/keys/register).
 * - Editor (READ_WRITE): vê "Novo" e "Editar". Administrador (ADMIN): também "Excluir".
 *   A chave de edição é informada pelo usuário; o nível é descoberto em GET /api/keys/me e o servidor
 *   continua sendo quem decide: esconder botões aqui é só conveniência.
 * - Todo texto vindo da API entra com textContent/createTextNode: nada de innerHTML com dados.
 */
(() => {
  'use strict';

  const PAGE_SIZE = 9;
  const READ_KEY_STORAGE = 'nflSiteApiKey';
  const EDIT_KEY_STORAGE = 'nflSiteEditKey';

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
  const ROLE_LABELS = { READ_ONLY: 'Somente leitura', READ_WRITE: 'Editor', ADMIN: 'Administrador' };

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

  /** Acesso seguro a localStorage/sessionStorage (podem lançar erro em navegação privada). */
  const storage = (area) => ({
    get(key) { try { return window[area].getItem(key); } catch { return null; } },
    set(key, value) { try { window[area].setItem(key, value); } catch { /* sem armazenamento */ } },
    del(key) { try { window[area].removeItem(key); } catch { /* sem armazenamento */ } },
  });
  const local = storage('localStorage');
  const session = storage('sessionStorage');

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

  let toastTimer = null;
  function toast(message, kind = 'ok') {
    const el = $('#toast');
    el.textContent = message;
    el.className = `toast toast-${kind}`;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 5000);
  }

  // ---------- Cliente da API ----------
  class ApiProblem extends Error {
    constructor(message, { retryAfter = null, fields = {}, status = 0 } = {}) {
      super(message);
      this.retryAfter = retryAfter;
      this.fields = fields;
      this.status = status;
    }
  }

  let readKey = local.get(READ_KEY_STORAGE);
  const auth = { key: null, role: 'READ_ONLY' };
  let pendingKey = null;

  const canWrite = () => auth.key && (auth.role === 'READ_WRITE' || auth.role === 'ADMIN');
  const canDelete = () => auth.key && auth.role === 'ADMIN';

  function rateLimited(response) {
    const seconds = Number(response.headers.get('Retry-After')) || null;
    return new ApiProblem(
      seconds
        ? `Muitas requisições em pouco tempo. Tente de novo em ${seconds} s.`
        : 'Muitas requisições em pouco tempo. Aguarde um instante e tente de novo.',
      { retryAfter: seconds, status: 429 });
  }

  function problemFrom(response, body) {
    const fields = {};
    for (const item of body?.fieldErrors ?? []) {
      if (item.field && !fields[item.field]) fields[item.field] = item.message;
    }
    return new ApiProblem(body?.message || 'Não foi possível concluir a operação.', { fields, status: response.status });
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
        readKey = (await response.json()).apiKey;
        local.set(READ_KEY_STORAGE, readKey);
      })().finally(() => { pendingKey = null; });
    }
    return pendingKey;
  }

  /** Chamada à API. 204 devolve null. Erros viram ApiProblem (com `fields` nos erros de validação). */
  async function request(path, { method = 'GET', body, idempotencyKey, retried = false } = {}) {
    if (!auth.key && !readKey) await registerKey();
    const headers = { 'X-API-Key': auth.key || readKey, Accept: 'application/hal+json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (idempotencyKey) headers['X-Idempotency-Key'] = idempotencyKey;

    const response = await fetch(path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (response.status === 401) {
      if (auth.key) {
        signOut({ silent: true });
        const message = 'Sua chave de edição foi recusada ou revogada. Voltando ao modo somente leitura.';
        toast(message, 'error');
        if (method !== 'GET') throw new ApiProblem(message, { status: 401 });
        return request(path, { method, body, idempotencyKey, retried });
      }
      if (!retried) {
        readKey = null;
        local.del(READ_KEY_STORAGE);
        await registerKey();
        return request(path, { method, body, idempotencyKey, retried: true });
      }
    }
    if (response.status === 429) throw rateLimited(response);
    if (response.status === 204) return null;
    if (!response.ok) {
      let data = null;
      try { data = await response.json(); } catch { /* corpo vazio */ }
      throw problemFrom(response, data);
    }
    return response.json();
  }

  // ---------- Listas de apoio (times, estádios, patrocinadores) ----------
  const lookups = {};
  const LOOKUP_PATHS = { teams: '/api/teams', stadiums: '/api/stadiums', sponsors: '/api/sponsors' };

  function lookup(name) {
    if (!lookups[name]) {
      lookups[name] = request(`${LOOKUP_PATHS[name]}?size=100&sort=name`)
        .then((data) => data._embedded?.[name] ?? [])
        .catch((error) => { delete lookups[name]; throw error; });
    }
    return lookups[name];
  }

  function invalidateLookups() {
    for (const key of Object.keys(lookups)) delete lookups[key];
    state.teamsLoaded = false;
    if (state.tab === 'players') loadTeamOptions();
    loadStrip(); // a faixa de placares reflete partidas criadas, editadas ou excluídas
  }

  // ---------- Definição das abas ----------
  const text = (name, label, placeholder) => ({ type: 'text', name, label, placeholder });
  const num = (name, label, placeholder, min = 0) => ({ type: 'number', name, label, placeholder, min });
  const select = (name, label, options) => ({ type: 'select', name, label, options });
  const all = [['', 'Todos']];
  const entries = (map) => Object.entries(map);
  const options = (map) => entries(map).map(([value, label]) => ({ value, label }));

  // Campos dos formulários de criar/editar (os nomes são os do corpo JSON da API)
  const TEAM_FORM = [
    { name: 'name', label: 'Nome', type: 'text', required: true, maxlength: 60, placeholder: 'Ex.: Detroit Lions' },
    { name: 'city', label: 'Cidade', type: 'text', required: true, maxlength: 60 },
    { name: 'abbreviation', label: 'Sigla', type: 'text', required: true, maxlength: 3, pattern: '[A-Z]{2,3}',
      title: '2 ou 3 letras maiúsculas', upper: true, placeholder: 'DET' },
    { name: 'conference', label: 'Conferência', type: 'select', required: true,
      options: [{ value: 'AFC', label: 'AFC' }, { value: 'NFC', label: 'NFC' }] },
    { name: 'division', label: 'Divisão', type: 'select', required: true, options: options(DIVISIONS) },
    { name: 'foundedYear', label: 'Ano de fundação', type: 'number', required: true, min: 1920, max: 2100 },
    { name: 'superBowlTitles', label: 'Títulos de Super Bowl', type: 'number', required: true, min: 0, initial: '0' },
    { name: 'stadiumId', label: 'Estádio', type: 'select', lookup: 'stadiums', blank: 'Sem estádio' },
    { name: 'sponsors', label: 'Patrocinadores', type: 'sponsors' },
  ];
  const PLAYER_FORM = [
    { name: 'firstName', label: 'Nome', type: 'text', required: true, maxlength: 40 },
    { name: 'lastName', label: 'Sobrenome', type: 'text', required: true, maxlength: 40 },
    { name: 'jerseyNumber', label: 'Número da camisa', type: 'number', required: true, min: 0, max: 99 },
    { name: 'position', label: 'Posição', type: 'select', required: true,
      options: entries(POSITIONS).map(([value, label]) => ({ value, label: `${value} (${label})` })) },
    { name: 'birthDate', label: 'Data de nascimento', type: 'date', required: true,
      max: new Date(Date.now() - 86400000).toISOString().slice(0, 10) },
    { name: 'teamId', label: 'Time', type: 'select', lookup: 'teams', blank: 'Sem time (free agent)' },
    { name: 'heightCm', label: 'Altura (cm)', type: 'number', min: 150, max: 230 },
    { name: 'weightKg', label: 'Peso (kg)', type: 'number', min: 60, max: 200 },
    { name: 'college', label: 'Universidade', type: 'text', maxlength: 80 },
  ];
  const STADIUM_FORM = [
    { name: 'name', label: 'Nome', type: 'text', required: true, maxlength: 80 },
    { name: 'city', label: 'Cidade', type: 'text', required: true, maxlength: 60 },
    { name: 'capacity', label: 'Capacidade', type: 'number', required: true, min: 1000, max: 200000 },
    { name: 'roofType', label: 'Cobertura', type: 'select', required: true, options: options(ROOFS) },
    { name: 'openedYear', label: 'Ano de inauguração', type: 'number', min: 1850, max: 2100 },
  ];
  const SPONSOR_FORM = [
    { name: 'name', label: 'Nome', type: 'text', required: true, maxlength: 80 },
    { name: 'industry', label: 'Setor de atuação', type: 'text', required: true, maxlength: 60 },
    { name: 'annualValueUsd', label: 'Valor anual (US$)', type: 'number', required: true, min: 0.01, step: '0.01' },
    { name: 'website', label: 'Site', type: 'url', maxlength: 120, pattern: 'https?://.+',
      title: 'Comece com http:// ou https://', placeholder: 'https://' },
  ];
  const GAME_FORM = [
    { name: 'season', label: 'Temporada', type: 'number', required: true, min: 1920, max: 2100, initial: '2026' },
    { name: 'week', label: 'Semana (1 a 22)', type: 'number', required: true, min: 1, max: 22 },
    { name: 'kickoff', label: 'Data e hora do jogo', type: 'datetime-local', required: true },
    { name: 'status', label: 'Situação', type: 'select', required: true, options: options(GAME_STATUS), initial: 'SCHEDULED' },
    { name: 'homeTeamId', label: 'Time mandante', type: 'select', lookup: 'teams', required: true, blank: 'Selecione…' },
    { name: 'awayTeamId', label: 'Time visitante', type: 'select', lookup: 'teams', required: true, blank: 'Selecione…' },
    { name: 'stadiumId', label: 'Estádio', type: 'select', lookup: 'stadiums', blank: 'Padrão: estádio do mandante' },
    { name: 'homeScore', label: 'Placar do mandante', type: 'number', min: 0 },
    { name: 'awayScore', label: 'Placar do visitante', type: 'number', min: 0 },
  ];

  const TABS = {
    teams: {
      label: 'Times', rel: 'teams', singular: 'time', endpoint: '/api/teams', form: TEAM_FORM,
      itemLabel: (t) => t.name,
      fields: [
        text('name', 'Nome do time', 'Ex.: Eagles'),
        select('conference', 'Conferência', [...all, ['AFC', 'AFC'], ['NFC', 'NFC']]),
        select('division', 'Divisão', [...all, ...entries(DIVISIONS)]),
      ],
      request: (v) => ({ path: '/api/teams/search', params: v }),
      card: teamCard, detail: teamDetail, afterSave: syncTeamSponsors,
      detailClass: (t) => `team-${t.conference.toLowerCase()}`,
    },
    players: {
      label: 'Jogadores', rel: 'players', singular: 'jogador', endpoint: '/api/players', form: PLAYER_FORM,
      itemLabel: (p) => `${p.firstName} ${p.lastName}`,
      fields: [
        text('name', 'Nome do jogador', 'Ex.: Mahomes'),
        select('position', 'Posição', [...all, ...entries(POSITIONS).map(([k, v]) => [k, `${k} (${v})`])]),
        { type: 'select', name: 'teamId', label: 'Time', options: () => teamOptions },
      ],
      request: (v) => ({ path: '/api/players/search', params: v }),
      card: playerCard, detail: playerDetail,
      detailClass: (p) => `pos-${p.position}`,
    },
    stadiums: {
      label: 'Estádios', rel: 'stadiums', singular: 'estádio', endpoint: '/api/stadiums', form: STADIUM_FORM,
      itemLabel: (s) => s.name,
      fields: [
        num('minCapacity', 'Capacidade mínima', 'Ex.: 70000'),
        select('roofType', 'Cobertura', [...all, ...entries(ROOFS)]),
      ],
      request: (v) => ({ path: '/api/stadiums/search', params: v }),
      card: stadiumCard, detail: stadiumDetail,
    },
    sponsors: {
      label: 'Patrocinadores', rel: 'sponsors', singular: 'patrocinador', endpoint: '/api/sponsors', form: SPONSOR_FORM,
      itemLabel: (s) => s.name,
      fields: [
        text('industry', 'Setor', 'Ex.: Bebidas'),
        num('minValue', 'Valor anual mínimo (US$)', 'Ex.: 100000000'),
      ],
      request: (v) => ({ path: '/api/sponsors/search', params: v }),
      card: sponsorCard, detail: sponsorDetail,
    },
    games: {
      label: 'Partidas', rel: 'games', singular: 'partida', endpoint: '/api/games', form: GAME_FORM,
      itemLabel: (g) => `${g.homeTeamName} x ${g.awayTeamName}`,
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
  const state = { tab: 'teams', page: 0, values: {}, loadToken: 0, teamsLoaded: false, count: 0 };
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
    state.fresh = true;
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
        const opts = typeof field.options === 'function' ? field.options() : field.options;
        control = h('select', { id, name: field.name },
          opts.map(([value, label]) => h('option', { value }, label)));
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

    const toolbar = canWrite()
      ? h('div', { class: 'toolbar' },
        h('button', { type: 'button', class: 'btn btn-new', onclick: () => openForm(tab, null) }, `+ Novo ${tab.singular}`))
      : null;

    filtersEl.replaceChildren(...[toolbar, form, tab.hint ? h('p', { class: 'hint' }, tab.hint) : null].filter(Boolean));
  }

  function onFilterChange(name, value, immediate) {
    valuesFor(state.tab)[name] = value;
    state.page = 0;
    clearTimeout(debounceTimer);
    if (immediate) { state.fresh = true; load(); }
    else debounceTimer = setTimeout(load, 400);
  }

  function clearFilters() {
    state.values[state.tab] = { ...(TABS[state.tab].defaults || {}) };
    state.page = 0;
    renderFilters();
    state.fresh = true;
    load();
  }

  async function loadTeamOptions() {
    if (state.teamsLoaded) return;
    try {
      const teams = await lookup('teams');
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

    // Troca de aba, de página ou de filtro de seleção: esqueletos enquanto carrega e entrada animada dos cartões.
    // Digitar na busca só troca os cartões (sem piscar).
    const fresh = state.fresh;
    state.fresh = false;
    resultsEl.setAttribute('aria-busy', 'true');
    setStatus('Carregando…');
    if (fresh) {
      resultsEl.replaceChildren(...skeletonCards(state.tab));
      pagerEl.replaceChildren();
    }
    try {
      const data = await request(`${path}?${query}`);
      if (token !== state.loadToken) return; // chegou uma busca mais nova
      const items = data._embedded?.[tab.rel] ?? [];
      const page = data.page || { totalElements: items.length, totalPages: 1, number: 0 };
      renderResults(tab, items, page, fresh);
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

  function renderResults(tab, items, page, animate = false) {
    state.count = items.length;
    if (items.length === 0) {
      // Página vazia depois de excluir o último item de uma página: volta uma página
      if (state.page > 0 && page.totalPages > 0) {
        state.page = Math.max(0, page.totalPages - 1);
        load();
        return;
      }
      resultsEl.replaceChildren(h('div', { class: 'empty' },
        h('p', { class: 'empty-title' }, 'Nada encontrado'),
        h('p', null, 'Nenhum resultado para esses filtros.'),
        h('button', { type: 'button', class: 'btn', onclick: clearFilters }, 'Limpar filtros')));
      pagerEl.replaceChildren();
      setStatus('0 resultados');
      return;
    }
    resultsEl.replaceChildren(...items.map((item, index) => {
      const card = tab.card(item);
      card.classList.add('card');
      if (animate) {
        card.classList.add('enter');
        card.style.setProperty('--i', Math.min(index, 11));
      }
      card.append(h('div', { class: 'card-actions' },
        h('button', { type: 'button', class: 'act act-link', onclick: () => openDetail(tab, item) }, 'Ver detalhes'),
        actionButtons(tab, item)));
      // Clicar no cartão (fora dos botões) também abre os detalhes
      card.addEventListener('click', (event) => {
        if (!event.target.closest('button, a')) openDetail(tab, item);
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
      state.fresh = true;
      load();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    pagerEl.replaceChildren(
      h('button', { type: 'button', class: 'btn', disabled: page.number <= 0, onclick: go(-1) }, 'Anterior'),
      h('span', { class: 'pager-info' }, `Página ${page.number + 1} de ${page.totalPages}`),
      h('button', { type: 'button', class: 'btn', disabled: page.number >= page.totalPages - 1, onclick: go(1) }, 'Próxima'));
  }

  /** Botões "Editar" e "Excluir" conforme o nível da chave (o servidor confere de novo). */
  function actionButtons(tab, item, { beforeClick } = {}) {
    const buttons = [];
    if (canWrite()) {
      buttons.push(h('button', {
        type: 'button', class: 'act', 'aria-label': `Editar ${tab.itemLabel(item)}`,
        onclick: () => { beforeClick?.(); openForm(tab, item); },
      }, 'Editar'));
    }
    if (canDelete()) {
      buttons.push(h('button', {
        type: 'button', class: 'act act-danger', 'aria-label': `Excluir ${tab.itemLabel(item)}`,
        onclick: () => { beforeClick?.(); confirmDelete(tab, item); },
      }, 'Excluir'));
    }
    return buttons;
  }

  // ---------- Cartões ----------
  // Cada tipo de dado tem o seu desenho; os numerais grandes (fonte de display) são a identidade visual:
  // número da camisa, sigla do time, capacidade do estádio, valor do patrocínio e placar.
  const meta = (label, value) => (value == null || value === '') ? null
    : h('li', null, h('span', { class: 'meta-label' }, label), h('span', null, value));
  const stat = (value, label) => h('li', { class: 'stat' },
    h('span', { class: 'stat-num' }, value), h('span', { class: 'stat-label' }, label));
  const moneyCompact = new Intl.NumberFormat('pt-BR', {
    style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1,
  });
  const BAR_CAPACITY = 100000; // a barra do estádio é proporcional a 100 mil lugares

  function teamCard(t) {
    return h('article', { class: `team team-${t.conference.toLowerCase()}`, 'aria-label': t.name },
      h('div', { class: 'card-main' },
        h('div', { class: 'team-head' },
          h('span', { class: 'abbr', 'aria-hidden': 'true' }, t.abbreviation),
          h('div', null,
            h('h3', null, t.name),
            h('p', { class: 'sub' }, `${t.city}, ${t.conference} ${DIVISIONS[t.division] || t.division}`))),
        h('ul', { class: 'stats' },
          stat(t.superBowlTitles, 'Super Bowls'), stat(t.playerCount, 'Jogadores'), stat(t.foundedYear, 'Fundação')),
        t.stadiumName ? h('p', { class: 'facts' }, `Estádio: ${t.stadiumName}`) : null));
  }

  function playerCard(p) {
    // A classe pos-XX (QB, WR...) define a cor da camisa; posição desconhecida cai no azul-marinho padrão
    const details = [p.college, p.heightCm ? `${p.heightCm} cm` : null, p.weightKg ? `${p.weightKg} kg` : null]
      .filter(Boolean);
    return h('article', { class: `player pos-${p.position}`, 'aria-label': `${p.firstName} ${p.lastName}` },
      h('div', { class: 'jersey' },
        h('span', { class: 'jersey-num' }, h('span', { class: 'sr-only' }, 'Camisa '), p.jerseyNumber),
        h('span', { class: 'jersey-pos' }, p.position)),
      h('div', { class: 'card-main' },
        h('div', null,
          h('h3', null, `${p.firstName} ${p.lastName}`),
          h('p', { class: 'sub' }, POSITIONS[p.position] || p.position),
          h('p', { class: 'sub' }, p.teamName || 'Sem time')),
        details.length ? h('p', { class: 'facts' }, details.join(', ')) : null));
  }

  function stadiumCard(s) {
    const share = Math.max(2, Math.min(100, Math.round((s.capacity / BAR_CAPACITY) * 100)));
    return h('article', { class: 'stadium', 'aria-label': s.name },
      h('div', { class: 'card-main' },
        h('div', null,
          h('h3', null, s.name),
          h('p', { class: 'sub' }, s.teamName ? `${s.city}, sede do ${s.teamName}` : s.city)),
        h('div', null,
          h('p', { class: 'stat-line' },
            h('span', { class: 'stat-num' }, number.format(s.capacity)), h('span', { class: 'stat-label' }, 'lugares')),
          h('div', { class: 'bar', role: 'img', 'aria-label': `${share}% de 100 mil lugares` },
            h('span', { style: `width:${share}%` }))),
        h('ul', { class: 'meta' },
          meta('Cobertura', ROOFS[s.roofType] || s.roofType),
          meta('Inaugurado em', s.openedYear))));
  }

  function sponsorCard(s) {
    return h('article', { class: 'sponsor', 'aria-label': s.name },
      h('div', { class: 'card-main' },
        h('div', null, h('h3', null, s.name), h('p', { class: 'sub' }, s.industry)),
        h('p', { class: 'stat-line' },
          h('span', { class: 'stat-num' }, moneyCompact.format(s.annualValueUsd)), h('span', { class: 'stat-label' }, 'por ano')),
        h('p', { class: 'facts' }, s.teams.length ? `Patrocina: ${s.teams.join(', ')}` : 'Sem times vinculados')));
  }

  function gameCard(g) {
    const played = g.homeScore != null && g.awayScore != null;
    const line = (name, score) => h('div', { class: `score-line${g.winner === name ? ' winner' : ''}` },
      h('span', { class: 'team-name' }, name), h('strong', { class: 'score' }, played ? score : '–'));
    return h('article', { class: 'game', 'aria-label': `${g.homeTeamName} contra ${g.awayTeamName}` },
      h('div', { class: 'card-main' },
        h('div', { class: 'board-top' },
          h('span', null, `Semana ${g.week}, temporada ${g.season}`),
          h('span', { class: `chip ${g.status === 'FINAL' ? 'chip-final' : 'chip-status'}` }, GAME_STATUS[g.status] || g.status)),
        h('div', { class: 'scoreboard' }, line(g.homeTeamName, g.homeScore), line(g.awayTeamName, g.awayScore)),
        h('p', { class: 'board-foot' }, [formatDateTime(g.kickoff), g.stadiumName].filter(Boolean).join(', '))));
  }

  // ---------- Detalhes (janela) ----------
  function definitionList(rows) {
    return h('dl', { class: 'details' }, rows.filter(([, value]) => value != null && value !== '')
      .map(([label, value]) => [h('dt', null, label), h('dd', null, value)]));
  }

  function openDetail(tab, item) {
    const { title, subtitle, body, extra } = tab.detail(item);
    dialog.className = `closable ${tab.detailClass ? tab.detailClass(item) : ''}`.trim();
    $('#detail-title').textContent = title;
    $('#detail-subtitle').textContent = subtitle || '';
    const content = $('#detail-body');
    content.replaceChildren(body);
    if (extra) extra(content);
    const actions = actionButtons(tab, item, { beforeClick: () => dialog.close() });
    if (actions.length) content.append(h('div', { class: 'detail-actions' }, actions));
    dialog.showModal();
  }

  async function loadList(container, heading, href, renderItem, emptyText) {
    const section = h('section', { class: 'related' }, h('h4', null, heading), h('p', { class: 'sub' }, 'Carregando…'));
    container.append(section);
    try {
      const sep = href.includes('?') ? '&' : '?';
      const data = await request(`${localPath(href)}${sep}size=50`);
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
      title: t.name, subtitle: `${t.city}, ${t.conference} ${DIVISIONS[t.division] || t.division}`,
      body: definitionList([
        ['Sigla', t.abbreviation], ['Fundado em', t.foundedYear], ['Títulos de Super Bowl', t.superBowlTitles],
        ['Estádio', t.stadiumName], ['Patrocinadores', t.sponsors.join(', ')],
      ]),
      extra: (container) => {
        if (t._links?.players) loadRoster(container, t);
      },
    };
  }

  function playerDetail(p) {
    return {
      title: `${p.firstName} ${p.lastName}`, subtitle: `Camisa ${p.jerseyNumber}, ${POSITIONS[p.position] || p.position}`,
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
            (g) => `${g.homeTeamName} x ${g.awayTeamName}, ${formatDateTime(g.kickoff)}`, 'Nenhum jogo cadastrado.');
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
      subtitle: `Temporada ${g.season}, semana ${g.week}`,
      body: definitionList([
        ['Situação', GAME_STATUS[g.status] || g.status],
        ['Placar', g.homeScore != null && g.awayScore != null
          ? `${g.homeTeamName} ${g.homeScore} – ${g.awayScore} ${g.awayTeamName}` : null],
        ['Vencedor', g.winner], ['Kickoff', formatDateTime(g.kickoff)], ['Estádio', g.stadiumName],
      ]),
    };
  }

  // ---------- Criar e editar ----------
  const formDialog = $('#form-dialog');
  const entityForm = $('#entity-form');

  function initialValue(field, item) {
    if (!item) return field.initial ?? '';
    const raw = item[field.name];
    if (raw == null) return '';
    if (field.type === 'datetime-local') return String(raw).slice(0, 16);
    return String(raw);
  }

  function buildControl(field, value, items) {
    const id = `ff-${field.name}`;
    const common = { id, name: field.name, required: field.required, 'aria-describedby': `err-${field.name}` };
    let control;
    if (field.type === 'select') {
      const opts = field.lookup
        ? items.map((i) => ({ value: String(i.id), label: i.name }))
        : field.options;
      control = h('select', common,
        (field.lookup || !field.required) ? h('option', { value: '' }, field.blank || 'Selecione…') : null,
        opts.map((o) => h('option', { value: o.value }, o.label)));
      control.value = value;
    } else {
      control = h('input', {
        ...common, type: field.type, min: field.min, max: field.max, step: field.step,
        maxlength: field.maxlength, pattern: field.pattern, title: field.title, placeholder: field.placeholder,
        autocomplete: 'off',
      });
      control.value = value;
      if (field.upper) control.addEventListener('input', () => { control.value = control.value.toUpperCase(); });
    }
    return control;
  }

  /** Lê os valores do formulário e monta o corpo JSON (vazio vira null; números viram number). */
  function collectBody(fields, form) {
    const body = {};
    for (const field of fields) {
      if (field.type === 'sponsors') continue;
      const raw = form.elements.namedItem(field.name).value.trim();
      if (raw === '') body[field.name] = null;
      else if (field.type === 'number' || (field.type === 'select' && field.lookup)) body[field.name] = Number(raw);
      else body[field.name] = raw;
    }
    return body;
  }

  function showFormErrors(error, form) {
    const summary = form.querySelector('#form-error');
    const unplaced = [];
    for (const [name, message] of Object.entries(error.fields || {})) {
      const slot = form.querySelector(`#err-${name}`);
      if (slot) {
        slot.textContent = message;
        form.elements.namedItem(name)?.setAttribute('aria-invalid', 'true');
      } else {
        unplaced.push(message);
      }
    }
    const hasFieldMessages = Object.keys(error.fields || {}).length > unplaced.length;
    const message = hasFieldMessages && unplaced.length === 0 ? 'Corrija os campos destacados.' : error.message;
    summary.textContent = unplaced.length ? `${message} ${unplaced.join(' ')}` : message;
    summary.hidden = false;
    summary.scrollIntoView({ block: 'nearest' });
  }

  async function openForm(tab, item) {
    const isEdit = Boolean(item);
    const idempotencyKey = uuid(); // vale para esta abertura: reenviar o mesmo formulário não duplica
    $('#form-title').textContent = `${isEdit ? 'Editar' : 'Novo'} ${tab.singular}`;
    $('#form-subtitle').textContent = isEdit ? tab.itemLabel(item) : 'Preencha os dados e salve.';
    entityForm.replaceChildren(h('p', { class: 'sub' }, 'Carregando…'));
    if (!formDialog.open) formDialog.showModal();

    let lookupData;
    try {
      const needed = [...new Set(tab.form.filter((f) => f.lookup).map((f) => f.lookup)
        .concat(tab.form.some((f) => f.type === 'sponsors') ? ['sponsors'] : []))];
      lookupData = Object.fromEntries(await Promise.all(needed.map(async (n) => [n, await lookup(n)])));
    } catch (error) {
      entityForm.replaceChildren(h('p', { class: 'form-error' }, error.message),
        h('div', { class: 'dialog-actions' }, h('button', { type: 'button', class: 'btn', 'data-close': true }, 'Fechar')));
      return;
    }

    const initialSponsorIds = new Set();
    const rows = tab.form.map((field) => {
      if (field.type === 'sponsors') {
        const linked = new Set(item?.sponsors ?? []);
        const boxes = lookupData.sponsors.map((s) => {
          const checked = linked.has(s.name);
          if (checked) initialSponsorIds.add(s.id);
          const lockedOn = checked && !canDelete(); // só administrador remove vínculos
          return h('label', { class: 'check' },
            h('input', { type: 'checkbox', name: 'sponsor', value: s.id, checked, disabled: lockedOn }),
            ` ${s.name}`);
        });
        return h('fieldset', { class: 'field field-wide sponsors' },
          h('legend', null, field.label),
          boxes.length ? h('div', { class: 'checks' }, boxes) : h('p', { class: 'sub' }, 'Nenhum patrocinador cadastrado.'),
          !canDelete() && initialSponsorIds.size ? h('p', { class: 'hint' }, 'Só administradores removem patrocinadores de um time.') : null);
      }
      const control = buildControl(field, initialValue(field, item), lookupData[field.lookup]);
      return h('div', { class: `field${field.wide ? ' field-wide' : ''}` },
        h('label', { for: `ff-${field.name}` }, field.label, field.required ? h('span', { class: 'req', 'aria-hidden': 'true' }, ' *') : null),
        control,
        h('p', { class: 'field-error', id: `err-${field.name}` }));
    });

    const submit = h('button', { type: 'submit', class: 'btn' }, isEdit ? 'Salvar alterações' : 'Criar');
    entityForm.replaceChildren(
      h('div', { class: 'form-grid' }, rows),
      h('p', { id: 'form-error', class: 'form-error', role: 'alert', hidden: true }),
      h('div', { class: 'dialog-actions' },
        h('button', { type: 'button', class: 'btn btn-ghost', 'data-close': true }, 'Cancelar'), submit));

    entityForm.onsubmit = async (event) => {
      event.preventDefault();
      entityForm.querySelectorAll('.field-error').forEach((el) => { el.textContent = ''; });
      entityForm.querySelectorAll('[aria-invalid]').forEach((el) => el.removeAttribute('aria-invalid'));
      entityForm.querySelector('#form-error').hidden = true;
      submit.disabled = true;
      submit.textContent = 'Salvando…';
      try {
        const body = collectBody(tab.form, entityForm);
        const saved = isEdit
          ? await request(`${tab.endpoint}/${item.id}`, { method: 'PUT', body })
          : await request(tab.endpoint, { method: 'POST', body, idempotencyKey });
        let warning = null;
        if (tab.afterSave) {
          const wanted = new Set([...entityForm.querySelectorAll('input[name=sponsor]:checked')].map((i) => Number(i.value)));
          warning = await tab.afterSave(saved, initialSponsorIds, wanted);
        }
        formDialog.close();
        invalidateLookups();
        state.page = isEdit ? state.page : 0;
        load();
        if (warning) toast(`${tab.singular[0].toUpperCase()}${tab.singular.slice(1)} salvo, mas: ${warning}`, 'error');
        else toast(isEdit ? `${tab.itemLabel(saved)} atualizado com sucesso.` : `${tab.itemLabel(saved)} criado com sucesso.`);
      } catch (error) {
        submit.disabled = false;
        submit.textContent = isEdit ? 'Salvar alterações' : 'Criar';
        showFormErrors(error, entityForm);
      }
    };
  }

  /** Após salvar um time: cria/remove os vínculos com patrocinadores (relação muitos-para-muitos). */
  async function syncTeamSponsors(team, initial, wanted) {
    const failures = [];
    for (const id of wanted) {
      if (!initial.has(id)) {
        try { await request(`/api/teams/${team.id}/sponsors/${id}`, { method: 'PUT' }); }
        catch (error) { failures.push(error.message); }
      }
    }
    if (canDelete()) {
      for (const id of initial) {
        if (!wanted.has(id)) {
          try { await request(`/api/teams/${team.id}/sponsors/${id}`, { method: 'DELETE' }); }
          catch (error) { failures.push(error.message); }
        }
      }
    }
    return failures.length ? `não foi possível atualizar todos os patrocinadores (${failures[0]})` : null;
  }

  // ---------- Excluir ----------
  const confirmDialog = $('#confirm-dialog');

  function confirmDelete(tab, item) {
    const label = tab.itemLabel(item);
    $('#confirm-title').textContent = `Excluir ${tab.singular}`;
    $('#confirm-message').textContent = `Tem certeza que deseja excluir "${label}"? Essa ação não pode ser desfeita.`;
    const error = $('#confirm-error');
    error.hidden = true;
    const ok = $('#confirm-ok');
    ok.disabled = false;
    ok.textContent = 'Excluir';
    ok.onclick = async () => {
      ok.disabled = true;
      ok.textContent = 'Excluindo…';
      try {
        await request(`${tab.endpoint}/${item.id}`, { method: 'DELETE' });
        confirmDialog.close();
        invalidateLookups();
        toast(`"${label}" excluído com sucesso.`);
        load(); // se a página ficar vazia, renderResults volta uma página
      } catch (e) {
        error.textContent = e.message;
        error.hidden = false;
        ok.disabled = false;
        ok.textContent = 'Excluir';
      }
    };
    confirmDialog.showModal();
  }

  // ---------- Acesso (chave de editor/administrador) ----------
  const accessDialog = $('#access-dialog');
  const accessBtn = $('#access-btn');

  function updateAccessUI() {
    if (auth.key) {
      accessBtn.textContent = ROLE_LABELS[auth.role] || auth.role;
      accessBtn.classList.toggle('access-on', Boolean(canWrite()));
      $('#access-sub').textContent = auth.role === 'READ_ONLY'
        ? 'Chave conectada, mas ela só permite leitura.'
        : `Conectado como ${ROLE_LABELS[auth.role].toLowerCase()}.`;
    } else {
      accessBtn.textContent = 'Entrar para editar';
      accessBtn.classList.remove('access-on');
      $('#access-sub').textContent = 'Você está no modo somente leitura.';
    }
    $('#access-signout').hidden = !auth.key;
    $('#access-submit').textContent = auth.key ? 'Trocar chave' : 'Entrar';
  }

  async function signIn(rawKey, remember) {
    const response = await fetch('/api/keys/me', { headers: { 'X-API-Key': rawKey, Accept: 'application/hal+json' } });
    if (response.status === 401) throw new ApiProblem('Chave inválida ou revogada.', { status: 401 });
    if (response.status === 429) throw rateLimited(response);
    if (!response.ok) throw new ApiProblem('Não foi possível verificar a chave. Tente novamente.');
    const data = await response.json();
    auth.key = rawKey;
    auth.role = data.role;
    if (remember) { local.set(EDIT_KEY_STORAGE, rawKey); session.del(EDIT_KEY_STORAGE); }
    else { session.set(EDIT_KEY_STORAGE, rawKey); local.del(EDIT_KEY_STORAGE); }
    updateAccessUI();
  }

  function signOut({ silent = false } = {}) {
    auth.key = null;
    auth.role = 'READ_ONLY';
    local.del(EDIT_KEY_STORAGE);
    session.del(EDIT_KEY_STORAGE);
    updateAccessUI();
    renderFilters(); // some o botão "Novo"
    if (!silent) {
      load();
      toast('Você saiu do modo edição.');
    }
  }

  accessBtn.addEventListener('click', () => {
    $('#access-key').value = '';
    $('#access-error').hidden = true;
    $('#access-remember').checked = Boolean(local.get(EDIT_KEY_STORAGE));
    updateAccessUI();
    accessDialog.showModal();
    $('#access-key').focus();
  });

  $('#access-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const rawKey = $('#access-key').value.trim();
    const error = $('#access-error');
    error.hidden = true;
    if (!rawKey) {
      error.textContent = 'Informe a chave de API.';
      error.hidden = false;
      return;
    }
    const submit = $('#access-submit');
    submit.disabled = true;
    try {
      await signIn(rawKey, $('#access-remember').checked);
      accessDialog.close();
      renderFilters();
      load();
      toast(auth.role === 'READ_ONLY'
        ? 'Essa chave só permite leitura. Para editar, use uma chave de editor ou administrador.'
        : `Modo edição ativado (${ROLE_LABELS[auth.role].toLowerCase()}).`, auth.role === 'READ_ONLY' ? 'error' : 'ok');
    } catch (problem) {
      error.textContent = problem.message;
      error.hidden = false;
    } finally {
      submit.disabled = false;
    }
  });

  $('#access-signout').addEventListener('click', () => {
    accessDialog.close();
    signOut();
  });

  async function restoreAuth() {
    const stored = session.get(EDIT_KEY_STORAGE) || local.get(EDIT_KEY_STORAGE);
    if (!stored) return;
    try {
      await signIn(stored, Boolean(local.get(EDIT_KEY_STORAGE)));
    } catch (problem) {
      // Chave guardada não vale mais (revogada, ou a API reiniciou): volta ao modo somente leitura
      if (problem.status === 401) { local.del(EDIT_KEY_STORAGE); session.del(EDIT_KEY_STORAGE); }
    }
  }

  // ---------- Janelas: fechar ----------
  document.addEventListener('click', (event) => {
    const closer = event.target.closest('[data-close]');
    if (closer) closer.closest('dialog')?.close();
  });
  for (const closable of document.querySelectorAll('dialog.closable')) {
    // Clicar fora (no fundo escurecido) fecha; o formulário de edição não, para não perder o que foi digitado
    closable.addEventListener('click', (event) => { if (event.target === closable) closable.close(); });
  }

  // ---------- Esqueletos de carregamento ----------
  const motionOk = () => !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function skeletonCards(tabKey, count = PAGE_SIZE) {
    return Array.from({ length: count }, () => h('article', {
      class: `card skeleton${tabKey === 'players' ? ' player' : ''}`, 'aria-hidden': 'true',
    },
    tabKey === 'players' ? h('div', { class: 'jersey sk-block' }) : null,
    h('div', { class: 'card-main' },
      h('span', { class: 'sk sk-title' }), h('span', { class: 'sk sk-line' }),
      h('span', { class: 'sk sk-line sk-short' }), h('span', { class: 'sk sk-big' }))));
  }

  // ---------- Faixa de placares ----------
  const stripEl = $('#strip');
  const stripTrack = $('#strip-track');
  const stripPrev = $('#strip-prev');
  const stripNext = $('#strip-next');

  function shortKickoff(iso) {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? ''
      : date.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  function stripStatus(g) {
    if (g.status === 'FINAL') return 'Final';
    if (g.status === 'SCHEDULED') return shortKickoff(g.kickoff);
    return GAME_STATUS[g.status] || g.status;
  }

  function stripItem(g, abbreviations) {
    const played = g.homeScore != null && g.awayScore != null;
    const row = (id, name, score) => h('div', { class: `strip-row${g.winner === name ? ' winner' : ''}` },
      h('span', { class: 'strip-team' }, abbreviations[id] || name.slice(0, 3).toUpperCase()),
      h('strong', { class: 'strip-score' }, played ? score : '–'));
    return h('li', null, h('button', {
      type: 'button', class: 'strip-game', onclick: () => openDetail(TABS.games, g),
      'aria-label': `${g.homeTeamName} contra ${g.awayTeamName}, ${stripStatus(g)}`,
    },
    h('span', { class: 'strip-status' }, stripStatus(g)),
    row(g.homeTeamId, g.homeTeamName, g.homeScore),
    row(g.awayTeamId, g.awayTeamName, g.awayScore)));
  }

  function updateStripNav() {
    const max = stripTrack.scrollWidth - stripTrack.clientWidth;
    stripPrev.hidden = stripNext.hidden = max <= 4;
    stripPrev.disabled = stripTrack.scrollLeft <= 2;
    stripNext.disabled = stripTrack.scrollLeft >= max - 2;
  }

  function stripSkeleton() {
    stripEl.hidden = false;
    stripPrev.hidden = stripNext.hidden = true;
    stripTrack.replaceChildren(...Array.from({ length: 6 }, () => h('li', null,
      h('div', { class: 'strip-game sk-strip', 'aria-hidden': 'true' },
        h('span', { class: 'sk sk-line sk-short' }), h('span', { class: 'sk sk-line' }), h('span', { class: 'sk sk-line' })))));
  }

  /** Jogos mais recentes primeiro. Quem não carregar (erro ou sem jogos) simplesmente não vê a faixa. */
  async function loadStrip() {
    try {
      const [data, teams] = await Promise.all([request('/api/games?size=12&sort=kickoff,desc'), lookup('teams')]);
      const games = data._embedded?.games ?? [];
      if (!games.length) { stripEl.hidden = true; return; }
      const abbreviations = Object.fromEntries(teams.map((t) => [t.id, t.abbreviation]));
      stripEl.hidden = false;
      stripTrack.replaceChildren(...games.map((g) => stripItem(g, abbreviations)));
      stripTrack.scrollLeft = 0;
      updateStripNav();
    } catch { stripEl.hidden = true; }
  }

  const scrollStrip = (direction) => () => stripTrack.scrollBy({
    left: direction * stripTrack.clientWidth * 0.8, behavior: motionOk() ? 'smooth' : 'auto',
  });
  stripPrev.addEventListener('click', scrollStrip(-1));
  stripNext.addEventListener('click', scrollStrip(1));
  stripTrack.addEventListener('scroll', updateStripNav, { passive: true });
  window.addEventListener('resize', updateStripNav);

  // ---------- Campo com o elenco (detalhe do time) ----------
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const FIELD_W = 600;
  const FIELD_H = 340;

  function svgEl(tag, attrs, ...children) {
    const node = document.createElementNS(SVG_NS, tag);
    for (const [name, value] of Object.entries(attrs || {})) if (value != null) node.setAttribute(name, value);
    for (const child of children.flat(Infinity)) {
      if (child == null) continue;
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
  }

  // Posições [x, y] por tipo: ataque à esquerda da linha de scrimmage (x = 300), defesa à direita.
  const FORMATION = {
    QB: [[212, 170]],
    RB: [[160, 170], [160, 120], [160, 220]],
    WR: [[268, 34], [268, 306], [238, 92], [238, 248]],
    TE: [[270, 258], [270, 82]],
    OL: [[268, 114], [268, 142], [268, 170], [268, 198], [268, 226]],
    DL: [[334, 128], [334, 156], [334, 184], [334, 212]],
    LB: [[382, 108], [382, 170], [382, 232]],
    CB: [[432, 36], [432, 304], [410, 96]],
    S: [[484, 118], [484, 222], [510, 170]],
    K: [[84, 150]],
    P: [[84, 196]],
  };

  function fieldDot(player, [x, y], onHover) {
    const label = `#${player.jerseyNumber} ${player.firstName} ${player.lastName}, ${POSITIONS[player.position] || player.position}`;
    const dot = svgEl('g', {
      class: `dot pos-${player.position}`, transform: `translate(${x} ${y})`, tabindex: '0', role: 'img', 'aria-label': label,
    }, svgEl('title', null, label), svgEl('circle', { r: 13 }), svgEl('text', { y: 4.5 }, player.jerseyNumber));
    dot.addEventListener('mouseenter', () => onHover(label));
    dot.addEventListener('focus', () => onHover(label));
    return dot;
  }

  function buildField(players, onHover) {
    const bands = Array.from({ length: 12 }, (_, i) =>
      svgEl('rect', { x: i * 50, y: 0, width: 50, height: FIELD_H, fill: i % 2 ? '#123a24' : '#0f3220' }));
    const yardLines = [];
    for (let x = 50; x < FIELD_W; x += 50) {
      yardLines.push(svgEl('line', { x1: x, y1: 0, x2: x, y2: FIELD_H, stroke: 'rgba(255,255,255,.16)', 'stroke-width': 2 }));
    }
    const byPosition = {};
    for (const p of players) (byPosition[p.position] ||= []).push(p);
    const dots = [];
    for (const [position, list] of Object.entries(byPosition)) {
      const slots = FORMATION[position] || [];
      list.sort((a, b) => a.jerseyNumber - b.jerseyNumber);
      list.slice(0, slots.length).forEach((p, i) => dots.push(fieldDot(p, slots[i], onHover)));
      const extra = list.length - slots.length;
      if (slots.length && extra > 0) { // há mais jogadores na posição do que lugares no campo
        const [x, y] = slots[slots.length - 1];
        dots.push(svgEl('text', { class: 'dot-extra', x: x + 20, y: y + 5 }, `+${extra}`));
      }
    }
    return svgEl('svg', {
      class: 'pitch', viewBox: `0 0 ${FIELD_W} ${FIELD_H}`, role: 'group',
      'aria-label': 'Campo com a posição de cada jogador do elenco',
    },
    bands, yardLines,
    svgEl('line', { x1: 300, y1: 0, x2: 300, y2: FIELD_H, stroke: '#ffffff', 'stroke-opacity': 0.75, 'stroke-width': 3 }),
    svgEl('text', { class: 'field-side', x: 14, y: 24 }, 'Ataque'),
    svgEl('text', { class: 'field-side', x: FIELD_W - 14, y: 24, 'text-anchor': 'end' }, 'Defesa'),
    dots);
  }

  async function loadRoster(container, team) {
    const section = h('section', { class: 'related roster' }, h('h4', null, 'Elenco'), h('p', { class: 'sub' }, 'Carregando…'));
    container.append(section);
    try {
      const href = team._links.players.href;
      const data = await request(`${localPath(href)}${href.includes('?') ? '&' : '?'}size=100`);
      const players = data._embedded?.players ?? [];
      if (!players.length) {
        section.replaceChildren(h('h4', null, 'Elenco (0)'), h('p', { class: 'sub' }, 'Nenhum jogador cadastrado.'));
        return;
      }
      const caption = h('p', { class: 'field-caption', 'aria-live': 'polite' },
        'Passe o mouse ou use o teclado sobre um jogador para ver quem é.');
      const field = buildField(players, (label) => { caption.textContent = label; });
      const present = [...new Set(players.map((p) => p.position))].filter((p) => FORMATION[p]);
      const legend = h('ul', { class: 'legend' },
        present.map((pos) => h('li', { class: `pos-${pos}` }, h('span', { class: 'swatch' }), pos)));
      const sorted = [...players].sort((a, b) => a.jerseyNumber - b.jerseyNumber);
      section.replaceChildren(
        h('h4', null, `Elenco (${players.length})`),
        h('div', { class: 'field-wrap' }, field), caption, legend,
        h('details', { class: 'roster-list' }, h('summary', null, 'Ver lista completa'),
          h('ul', { class: 'related-list' },
            sorted.map((p) => h('li', null, `#${p.jerseyNumber} ${p.firstName} ${p.lastName}, ${p.position}`)))));
    } catch (error) {
      section.replaceChildren(h('h4', null, 'Elenco'), h('p', { class: 'sub' }, error.message));
    }
  }

  // ---------- Início ----------
  window.addEventListener('hashchange', () => {
    const key = window.location.hash.slice(1);
    if (TABS[key] && key !== state.tab) selectTab(key);
  });

  (async () => {
    updateAccessUI();
    await restoreAuth();
    stripSkeleton();
    loadStrip();
    const initial = window.location.hash.slice(1);
    selectTab(TABS[initial] ? initial : 'teams');
  })();
})();
