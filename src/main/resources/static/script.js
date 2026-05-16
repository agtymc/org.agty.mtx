const state = {
    mode: 'login',
    view: 'chat',
    user: null,
    displayName: null,
    email: '',
    theme: 'dark',
    fontSize: '14',
    menuFontSize: '13',
    sidebarWidth: '320',
    answerNavSide: 'right',
    selectedModel: '',
    models: [],
    defaultModel: '',
    groups: [],
    chats: [],
    currentChat: null,
    lastChatId: Number(localStorage.getItem('lastChatId') || '') || null,
    collapsedGroups: {},
    collapsedGroupsPersistTimer: null,
    dragGroupId: null,
    dragChatId: null,
    dragChatSourceGroupId: null,
    isStreaming: false,
    messagesAutoScroll: true,
    activeStreamController: null,
    activeStreamChatId: null,
    activeStreamPrivate: false,
    privateChat: null,
    treeMutationInFlight: false,
    modelUsageStats: null
};

let authRedirectInProgress = false;

marked.setOptions({
    breaks: true,
    gfm: true,
    highlight(code, lang) {
        if (lang && hljs.getLanguage(lang)) {
            return hljs.highlight(code, {language: lang, ignoreIllegals: true}).value;
        }
        return hljs.highlightAuto(code).value;
    }
});

function escapeHtml(text) {
    return (text || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function stripThinking(text) {
    const noClosed = text.replace(/<think>[\s\S]*?<\/think>\s*/g, '');
    const openIdx = noClosed.indexOf('<think>');
    return openIdx >= 0 ? noClosed.substring(0, openIdx) : noClosed;
}

function renderAssistantMarkdown(text) {
    return marked.parse(stripThinking(text || ''));
}

async function api(url, options = {}) {
    const config = {
        headers: {'Content-Type': 'application/json'},
        ...options
    };

    const response = await fetch(url, config);
    const isJson = response.headers.get('content-type')?.includes('application/json');
    const payload = isJson ? await response.json() : null;

    const isAuthAttempt = url === '/api/auth/login' || url === '/api/auth/register';
    if ((response.status === 401 || response.status === 403) && !isAuthAttempt) {
        redirectToLoginOnExpiredSession(payload?.message || payload?.error || 'Сессия завершена. Войдите снова.');
        const error = new Error('AUTH_EXPIRED');
        error.authExpired = true;
        throw error;
    }

    if (!response.ok) {
        const message = payload?.message || payload?.error || `HTTP ${response.status}`;
        throw new Error(message);
    }

    return payload;
}

function setMode(mode) {
    state.mode = mode;
    document.getElementById('loginTab').classList.toggle('active', mode === 'login');
    document.getElementById('registerTab').classList.toggle('active', mode === 'register');
    document.getElementById('authSubmit').textContent = mode === 'login' ? 'Войти' : 'Создать аккаунт';
    document.getElementById('authError').textContent = '';
}

function applyTheme(theme) {
    const normalized = theme === 'dark' ? 'dark' : 'light';
    state.theme = normalized;
    document.body.setAttribute('data-theme', normalized);
    const toggle = document.getElementById('themeToggle');
    if (toggle) {
        toggle.checked = normalized === 'dark';
    }
}

function applyFontSize(size) {
    const parsed = Number(size);
    const normalized = Number.isFinite(parsed) ? Math.max(11, Math.min(28, Math.round(parsed))) : 14;
    state.fontSize = String(normalized);
    document.documentElement.style.setProperty('--font-size', `${state.fontSize}px`);
    const input = document.getElementById('fontSizeInput');
    if (input) {
        input.value = state.fontSize;
    }
}

function applyMenuFontSize(size) {
    const parsed = Number(size);
    const normalized = Number.isFinite(parsed) ? Math.max(10, Math.min(24, Math.round(parsed))) : 13;
    state.menuFontSize = String(normalized);
    document.documentElement.style.setProperty('--menu-font-size', `${state.menuFontSize}px`);
    const input = document.getElementById('menuFontSizeInput');
    if (input) {
        input.value = state.menuFontSize;
    }
}

function applySidebarWidth(size) {
    const parsed = Number(size);
    const normalized = Number.isFinite(parsed) ? Math.max(260, Math.min(520, Math.round(parsed))) : 320;
    state.sidebarWidth = String(normalized);
    document.documentElement.style.setProperty('--sidebar-width', `${state.sidebarWidth}px`);
    const input = document.getElementById('sidebarWidthInput');
    if (input) {
        input.value = state.sidebarWidth;
    }
}

function applyAnswerNavSide(side) {
    const normalized = side === 'left' ? 'left' : 'right';
    state.answerNavSide = normalized;

    const wrap = document.getElementById('answerNav');
    if (wrap) {
        wrap.classList.toggle('left', normalized === 'left');
    }

    const input = document.getElementById('answerNavSideInput');
    if (input) {
        input.value = normalized;
    }
    updateAnswerNavPosition();
}

function setAccountIdentity(username) {
    const accountName = document.getElementById('accountName');
    const accountAvatar = document.getElementById('accountAvatar');
    const normalizedName = (username || '').trim();
    if (accountName) {
        accountName.textContent = normalizedName;
    }
    if (accountAvatar) {
        accountAvatar.textContent = (normalizedName || '?').charAt(0).toUpperCase();
    }
}

function setWorkspaceView(view) {
    state.view = view;
    const isChat = view === 'chat';
    const chatMessages = document.getElementById('chatMessages');
    const sendForm = document.getElementById('sendForm');
    const printMeta = document.getElementById('printMeta');
    const profilePage = document.getElementById('profilePage');
    const settingsPage = document.getElementById('settingsPage');
    const statsPage = document.getElementById('statsPage');
    const privateBtn = document.getElementById('privateChatBtn');
    const newChatBtn = document.getElementById('newChatBtn');

    if (chatMessages) chatMessages.classList.toggle('hidden', !isChat);
    if (sendForm) sendForm.classList.toggle('hidden', !isChat);
    if (printMeta) printMeta.classList.toggle('hidden', !isChat);
    if (privateBtn) privateBtn.classList.toggle('hidden', !isChat);
    if (newChatBtn) newChatBtn.classList.toggle('hidden', !isChat);
    if (profilePage) profilePage.classList.toggle('hidden', view !== 'profile');
    if (settingsPage) settingsPage.classList.toggle('hidden', view !== 'settings');
    if (statsPage) statsPage.classList.toggle('hidden', view !== 'stats');

    if (isChat) {
        setCurrentTitle(state.currentChat?.chat || null);
    } else if (view === 'profile') {
        document.getElementById('chatTitle').textContent = 'Профиль';
        updateChatTokenUsage();
    } else if (view === 'settings') {
        document.getElementById('chatTitle').textContent = 'Настройки';
        updateChatTokenUsage();
    } else if (view === 'stats') {
        document.getElementById('chatTitle').textContent = 'Статистика';
        updateChatTokenUsage();
    }
    updateAnswerNavButtonsState();
}

function closeAccountMenu() {
    const menu = document.getElementById('accountMenu');
    if (menu) {
        menu.classList.add('hidden');
    }
}

function toggleAccountMenu() {
    const menu = document.getElementById('accountMenu');
    if (menu) {
        menu.classList.toggle('hidden');
    }
}

function placePopover(modal, anchorEl) {
    const card = modal?.querySelector('.modal-card');
    if (!modal || !card) return;

    card.style.left = '8px';
    card.style.top = '8px';
    card.style.visibility = 'hidden';
    modal.classList.remove('hidden');

    const viewportW = window.innerWidth;
    const viewportH = window.innerHeight;
    const cardRect = card.getBoundingClientRect();
    const anchorRect = anchorEl?.getBoundingClientRect?.();

    let left = 8;
    let top = 8;

    if (anchorRect) {
        left = anchorRect.right + 8;
        top = anchorRect.top;
        if (left + cardRect.width > viewportW - 8) {
            left = anchorRect.left - cardRect.width - 8;
        }
        if (left < 8) {
            left = 8;
        }
        if (top + cardRect.height > viewportH - 8) {
            top = viewportH - cardRect.height - 8;
        }
        if (top < 8) {
            top = 8;
        }
    } else {
        left = Math.max(8, Math.round((viewportW - cardRect.width) / 2));
        top = Math.max(8, Math.round((viewportH - cardRect.height) / 2));
    }

    card.style.left = `${Math.round(left)}px`;
    card.style.top = `${Math.round(top)}px`;
    card.style.visibility = 'visible';
}

function askConfirm({title, text, confirmLabel = 'Удалить', anchorEl = null}) {
    return new Promise((resolve) => {
        const modal = document.getElementById('confirmModal');
        const titleEl = document.getElementById('confirmTitle');
        const textEl = document.getElementById('confirmText');
        const okBtn = document.getElementById('confirmOkBtn');
        const cancelBtn = document.getElementById('confirmCancelBtn');

        if (!modal || !titleEl || !textEl || !okBtn || !cancelBtn) {
            resolve(false);
            return;
        }

        titleEl.textContent = title;
        textEl.textContent = text;
        okBtn.textContent = confirmLabel;

        const cleanup = () => {
            okBtn.onclick = null;
            cancelBtn.onclick = null;
            modal.onclick = null;
            document.removeEventListener('keydown', onEscape);
            document.removeEventListener('mousedown', onOutsideClick, true);
            modal.classList.add('hidden');
        };

        const onEscape = (event) => {
            if (event.key === 'Escape') {
                cleanup();
                resolve(false);
            }
        };

        cancelBtn.onclick = () => {
            cleanup();
            resolve(false);
        };
        okBtn.onclick = () => {
            cleanup();
            resolve(true);
        };
        const onOutsideClick = (event) => {
            const card = modal.querySelector('.modal-card');
            if (card && !card.contains(event.target)) {
                cleanup();
                resolve(false);
            }
        };
        modal.onclick = (event) => {
            if (event.target === modal) {
                cleanup();
                resolve(false);
            }
        };

        placePopover(modal, anchorEl);
        document.addEventListener('keydown', onEscape);
        document.addEventListener('mousedown', onOutsideClick, true);
    });
}

function askTextInput({title, label, initialValue = '', confirmLabel = 'Сохранить', maxLength = 160, anchorEl = null}) {
    return new Promise((resolve) => {
        const modal = document.getElementById('inputModal');
        const titleEl = document.getElementById('inputTitle');
        const labelEl = document.getElementById('inputLabel');
        const inputEl = document.getElementById('inputValue');
        const okBtn = document.getElementById('inputOkBtn');
        const cancelBtn = document.getElementById('inputCancelBtn');

        if (!modal || !titleEl || !labelEl || !inputEl || !okBtn || !cancelBtn) {
            resolve(null);
            return;
        }

        titleEl.textContent = title;
        labelEl.textContent = label;
        okBtn.textContent = confirmLabel;
        inputEl.value = initialValue || '';
        inputEl.maxLength = maxLength;

        const cleanup = () => {
            okBtn.onclick = null;
            cancelBtn.onclick = null;
            modal.onclick = null;
            inputEl.onkeydown = null;
            document.removeEventListener('keydown', onEscape);
            document.removeEventListener('mousedown', onOutsideClick, true);
            modal.classList.add('hidden');
        };

        const submit = () => {
            const value = (inputEl.value || '').trim();
            cleanup();
            resolve(value);
        };

        const onEscape = (event) => {
            if (event.key === 'Escape') {
                cleanup();
                resolve(null);
            }
        };

        cancelBtn.onclick = () => {
            cleanup();
            resolve(null);
        };
        okBtn.onclick = submit;
        const onOutsideClick = (event) => {
            const card = modal.querySelector('.modal-card');
            if (card && !card.contains(event.target)) {
                cleanup();
                resolve(null);
            }
        };
        inputEl.onkeydown = (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                submit();
            }
        };
        modal.onclick = (event) => {
            if (event.target === modal) {
                cleanup();
                resolve(null);
            }
        };

        placePopover(modal, anchorEl);
        inputEl.focus();
        inputEl.select();
        document.addEventListener('keydown', onEscape);
        document.addEventListener('mousedown', onOutsideClick, true);
    });
}

function askComposeInput(initialValue = '') {
    return new Promise((resolve) => {
        const modal = document.getElementById('composeModal');
        const inputEl = document.getElementById('composeInput');
        const modelSelect = document.getElementById('composeModelSelect');
        const applyBtn = document.getElementById('composeApplyBtn');
        const cancelBtn = document.getElementById('composeCancelBtn');

        if (!modal || !inputEl || !modelSelect || !applyBtn || !cancelBtn) {
            resolve(null);
            return;
        }

        inputEl.value = initialValue || '';
        fillModels();
        modelSelect.value = state.selectedModel || modelSelect.value;

        const cleanup = () => {
            applyBtn.onclick = null;
            cancelBtn.onclick = null;
            modal.onclick = null;
            inputEl.onkeydown = null;
            document.removeEventListener('keydown', onEscape);
            document.removeEventListener('mousedown', onOutsideClick, true);
            modal.classList.add('hidden');
        };

        const submit = () => {
            const value = inputEl.value || '';
            const selectedModel = (modelSelect.value || '').trim();
            cleanup();
            resolve({value, selectedModel, submit: true});
        };

        const onEscape = (event) => {
            if (event.key === 'Escape') {
                cleanup();
                resolve(null);
            }
        };

        cancelBtn.onclick = () => {
            cleanup();
            resolve(null);
        };
        applyBtn.onclick = submit;

        inputEl.onkeydown = (event) => {
            if (event.key === 'Enter' && event.ctrlKey) {
                event.preventDefault();
                submit();
            }
        };

        const onOutsideClick = (event) => {
            const card = modal.querySelector('.modal-card');
            if (card && !card.contains(event.target)) {
                cleanup();
                resolve(null);
            }
        };

        modal.onclick = (event) => {
            if (event.target === modal) {
                cleanup();
                resolve(null);
            }
        };

        placePopover(modal, null);
        inputEl.focus();
        inputEl.selectionStart = inputEl.value.length;
        inputEl.selectionEnd = inputEl.value.length;
        document.addEventListener('keydown', onEscape);
        document.addEventListener('mousedown', onOutsideClick, true);
    });
}

function showAuth() {
    document.getElementById('authView').classList.remove('hidden');
    document.getElementById('appView').classList.add('hidden');
    const authError = document.getElementById('authError');
    if (authError) {
        authError.textContent = '';
    }
}

function showApp() {
    document.getElementById('authView').classList.add('hidden');
    document.getElementById('appView').classList.remove('hidden');
}

function resetAppStateOnSessionExpired() {
    if (state.collapsedGroupsPersistTimer) {
        clearTimeout(state.collapsedGroupsPersistTimer);
        state.collapsedGroupsPersistTimer = null;
    }
    state.user = null;
    state.displayName = null;
    state.email = '';
    state.groups = [];
    state.chats = [];
    state.collapsedGroups = {};
    state.currentChat = null;
    state.privateChat = null;
    state.modelUsageStats = null;
    state.view = 'chat';
    state.isStreaming = false;
    state.activeStreamController = null;
    state.activeStreamChatId = null;
    state.activeStreamPrivate = false;
    setTyping(false);
    setSendButtonStreaming(false);
    closeAccountMenu();
}

function redirectToLoginOnExpiredSession(message = 'Сессия завершена. Войдите снова.') {
    if (authRedirectInProgress) {
        return;
    }
    authRedirectInProgress = true;
    localStorage.removeItem('authHint');
    resetAppStateOnSessionExpired();
    showAuth();
    const authError = document.getElementById('authError');
    if (authError) {
        authError.textContent = message;
    }
}

function setInlineStatus(id, text, isError = false) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text || '';
    el.classList.toggle('error', Boolean(isError));
}

function fillProfileForm(profile) {
    document.getElementById('profileUsername').value = profile?.username || state.user || '';
    document.getElementById('profileDisplayName').value = profile?.displayName || '';
    document.getElementById('profileEmail').value = profile?.email || '';
}

function fillSettingsForm(settings) {
    applyTheme(settings?.theme || state.theme || 'light');
    applyFontSize(settings?.fontSize || state.fontSize || '14');
    applyMenuFontSize(settings?.menuFontSize || state.menuFontSize || '13');
    applySidebarWidth(settings?.sidebarWidth || state.sidebarWidth || '320');
    applyAnswerNavSide(settings?.answerNavSide || state.answerNavSide || 'right');
}

function setTyping(visible) {
    const indicator = document.getElementById('typingIndicator');
    if (!indicator) {
        return;
    }
    indicator.classList.toggle('hidden', !visible);
}

function nowTime() {
    return new Date().toLocaleTimeString('ru-RU', {hour: '2-digit', minute: '2-digit'});
}

function getChatTitle(chat) {
    if (!chat) return 'Выберите чат';
    if (chat.private) return chat.title || 'Приватный чат';
    if (chat.groupName) return `${chat.groupName} / ${chat.title}`;
    return chat.title;
}

function formatTokenCount(tokens) {
    const value = Number(tokens);
    if (!Number.isFinite(value) || value < 0) {
        return '0';
    }
    return Math.round(value).toLocaleString('ru-RU');
}

function formatFloat(value, digits = 2) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return '0';
    }
    return number.toLocaleString('ru-RU', {
        minimumFractionDigits: 0,
        maximumFractionDigits: digits
    });
}

function formatMsFromNs(nanoseconds) {
    const value = Number(nanoseconds);
    if (!Number.isFinite(value) || value <= 0) {
        return '0 мс';
    }
    const ms = value / 1_000_000;
    if (ms >= 1000) {
        return `${formatFloat(ms / 1000, 3)} с`;
    }
    return `${formatFloat(ms, 2)} мс`;
}

function formatDateTime(value) {
    if (!value) {
        return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '—';
    }
    return date.toLocaleString('ru-RU');
}

function updateChatTokenUsage() {
    const tokenEl = document.getElementById('chatTokenUsage');
    if (!tokenEl) {
        return;
    }
    if (state.view !== 'chat') {
        tokenEl.classList.add('hidden');
        return;
    }
    const current = state.currentChat;
    if (!current || !current.chat || current.chat.private) {
        tokenEl.classList.add('hidden');
        return;
    }
    const inputTokens = Number(current.inputTokens || 0);
    const outputTokens = Number(current.outputTokens || 0);
    const totalTokens = Number(current.totalTokens || 0);
    tokenEl.textContent =
        `Токены: вход ${formatTokenCount(inputTokens)} · выход ${formatTokenCount(outputTokens)} · всего ${formatTokenCount(totalTokens)}`;
    tokenEl.classList.remove('hidden');
}

function statsMetric(label, value) {
    const item = document.createElement('div');
    item.className = 'stats-metric';
    const name = document.createElement('div');
    name.className = 'stats-metric-label';
    name.textContent = label;
    const data = document.createElement('div');
    data.className = 'stats-metric-value';
    data.textContent = value;
    item.append(name, data);
    return item;
}

function renderDoneReasonsMap(doneReasons) {
    if (!doneReasons || Object.keys(doneReasons).length === 0) {
        return '—';
    }
    return Object.entries(doneReasons)
        .map(([reason, count]) => `${reason}: ${formatTokenCount(count)}`)
        .join(' · ');
}

function buildModelStatsCard(modelStats) {
    const card = document.createElement('article');
    card.className = 'stats-card';

    const title = document.createElement('h3');
    title.className = 'stats-model-title';
    title.textContent = modelStats.model || 'unknown';

    const subtitle = document.createElement('div');
    subtitle.className = 'stats-model-subtitle';
    subtitle.textContent = `Ответов: ${formatTokenCount(modelStats.replies || 0)}`;

    const grid = document.createElement('div');
    grid.className = 'stats-metrics-grid';
    grid.append(
        statsMetric('Input токены', formatTokenCount(modelStats.promptTokens || 0)),
        statsMetric('Output токены', formatTokenCount(modelStats.completionTokens || 0)),
        statsMetric('Всего токенов', formatTokenCount(modelStats.totalTokens || 0)),
        statsMetric('Средний input/ответ', formatFloat(modelStats.avgPromptTokensPerReply || 0, 2)),
        statsMetric('Средний output/ответ', formatFloat(modelStats.avgCompletionTokensPerReply || 0, 2)),
        statsMetric('Средний total/ответ', formatFloat(modelStats.avgTotalTokensPerReply || 0, 2)),
        statsMetric('Скорость output', `${formatFloat(modelStats.outputTokensPerSecond || 0, 2)} ток/с`),
        statsMetric('Total duration (sum)', formatMsFromNs(modelStats.totalDurationNs || 0)),
        statsMetric('Load duration (sum)', formatMsFromNs(modelStats.loadDurationNs || 0)),
        statsMetric('Prompt eval duration (sum)', formatMsFromNs(modelStats.promptEvalDurationNs || 0)),
        statsMetric('Eval duration (sum)', formatMsFromNs(modelStats.evalDurationNs || 0)),
        statsMetric('Avg total duration/ответ', `${formatFloat(modelStats.avgTotalDurationMsPerReply || 0, 2)} мс`),
        statsMetric('Avg load duration/ответ', `${formatFloat(modelStats.avgLoadDurationMsPerReply || 0, 2)} мс`),
        statsMetric('Avg prompt eval/ответ', `${formatFloat(modelStats.avgPromptEvalDurationMsPerReply || 0, 2)} мс`),
        statsMetric('Avg eval/ответ', `${formatFloat(modelStats.avgEvalDurationMsPerReply || 0, 2)} мс`),
        statsMetric('Done reasons', renderDoneReasonsMap(modelStats.doneReasons || {})),
        statsMetric('Первое использование', formatDateTime(modelStats.firstUsedAt)),
        statsMetric('Последнее использование', formatDateTime(modelStats.lastUsedAt))
    );

    card.append(title, subtitle, grid);
    return card;
}

function renderModelUsageStats(payload) {
    const statsContent = document.getElementById('statsContent');
    if (!statsContent) {
        return;
    }
    statsContent.innerHTML = '';

    const overall = payload?.overall || {};
    const models = Array.isArray(payload?.models) ? payload.models : [];

    const summary = document.createElement('article');
    summary.className = 'stats-card stats-summary-card';
    const summaryTitle = document.createElement('h3');
    summaryTitle.className = 'stats-model-title';
    summaryTitle.textContent = 'Общая статистика';
    const summaryGrid = document.createElement('div');
    summaryGrid.className = 'stats-metrics-grid';
    summaryGrid.append(
        statsMetric('Ответов всего', formatTokenCount(overall.replies || 0)),
        statsMetric('Input токены', formatTokenCount(overall.promptTokens || 0)),
        statsMetric('Output токены', formatTokenCount(overall.completionTokens || 0)),
        statsMetric('Всего токенов', formatTokenCount(overall.totalTokens || 0)),
        statsMetric('Средний total/ответ', formatFloat(overall.avgTotalTokensPerReply || 0, 2)),
        statsMetric('Скорость output', `${formatFloat(overall.outputTokensPerSecond || 0, 2)} ток/с`),
        statsMetric('Total duration (sum)', formatMsFromNs(overall.totalDurationNs || 0)),
        statsMetric('Load duration (sum)', formatMsFromNs(overall.loadDurationNs || 0)),
        statsMetric('Prompt eval duration (sum)', formatMsFromNs(overall.promptEvalDurationNs || 0)),
        statsMetric('Eval duration (sum)', formatMsFromNs(overall.evalDurationNs || 0)),
        statsMetric('Avg total duration/ответ', `${formatFloat(overall.avgTotalDurationMsPerReply || 0, 2)} мс`),
        statsMetric('Done reasons', renderDoneReasonsMap(overall.doneReasons || {}))
    );
    summary.append(summaryTitle, summaryGrid);
    statsContent.appendChild(summary);

    if (models.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'stats-empty';
        empty.textContent = 'Пока нет данных usage по моделям.';
        statsContent.appendChild(empty);
        return;
    }

    for (const modelStats of models) {
        statsContent.appendChild(buildModelStatsCard(modelStats));
    }
}

function setCurrentTitle(chat) {
    if (state.view !== 'chat') {
        return;
    }
    document.getElementById('chatTitle').textContent = getChatTitle(chat);
    updateChatTokenUsage();
}

function updatePrintDomainLink() {
    const link = document.getElementById('printDomainLink');
    if (!link) {
        return;
    }
    link.href = window.location.origin;
    link.textContent = window.location.origin;
}

function fallbackCopyText(text) {
    const input = document.createElement('textarea');
    input.value = text || '';
    input.setAttribute('readonly', '');
    input.style.position = 'fixed';
    input.style.opacity = '0';
    input.style.pointerEvents = 'none';
    input.style.top = '-1000px';
    document.body.appendChild(input);
    input.focus();
    input.select();
    input.setSelectionRange(0, input.value.length);

    let copied = false;
    try {
        copied = document.execCommand('copy');
    } finally {
        input.remove();
    }
    return copied;
}

async function copyTextToClipboard(text) {
    const value = text || '';
    if (navigator.clipboard && window.isSecureContext) {
        try {
            await navigator.clipboard.writeText(value);
            return true;
        } catch (_) {
            // Browser denied async clipboard API; fallback below.
        }
    }
    return fallbackCopyText(value);
}

function downloadTextFile(filename, text) {
    const blob = new Blob([text || ''], {type: 'text/plain;charset=utf-8'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

function makeActionButton(label, className, onClick) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = className;
    btn.textContent = label;
    btn.onclick = onClick;
    return btn;
}

function detectCodeLanguage(codeEl) {
    if (!codeEl) return 'text';
    const classes = Array.from(codeEl.classList || []);
    const langClass = classes.find(cls => cls.startsWith('language-') || cls.startsWith('lang-'));
    if (!langClass) return 'text';
    const raw = langClass.includes('language-')
        ? langClass.substring('language-'.length)
        : langClass.substring('lang-'.length);
    const normalized = (raw || '').trim().toLowerCase();
    if (!normalized) return 'text';

    const pretty = {
        js: 'JavaScript',
        ts: 'TypeScript',
        py: 'Python',
        sh: 'Shell',
        bash: 'Bash',
        yml: 'YAML',
        md: 'Markdown',
        cs: 'C#',
        cpp: 'C++',
        c: 'C'
    };
    return pretty[normalized] || normalized;
}

function attachCodeCopyControls(contentEl) {
    const preBlocks = contentEl.querySelectorAll('pre');
    preBlocks.forEach((pre) => {
        if (pre.parentElement && pre.parentElement.classList.contains('code-block-wrap')) {
            return;
        }

        const wrapper = document.createElement('div');
        wrapper.className = 'code-block-wrap';

        const top = document.createElement('div');
        top.className = 'code-copy-top';
        const langLabel = document.createElement('div');
        langLabel.className = 'code-lang-label';
        langLabel.textContent = detectCodeLanguage(pre.querySelector('code'));

        const bottom = document.createElement('div');
        bottom.className = 'code-copy-bottom';

        const buildCopyBtn = () => makeActionButton('Скопировать', 'code-copy-btn', async () => {
            const code = pre.querySelector('code');
            const text = code ? code.innerText : pre.innerText;
            const copied = await copyTextToClipboard(text);
            if (!copied) {
                alert('Не удалось скопировать код в буфер обмена.');
            }
        });

        top.append(langLabel, buildCopyBtn());
        bottom.appendChild(buildCopyBtn());

        pre.parentNode.insertBefore(wrapper, pre);
        wrapper.append(top, pre, bottom);
    });
}

function findPreviousUserPrompt(assistantMessageEl) {
    let cursor = assistantMessageEl.previousElementSibling;
    while (cursor) {
        if (cursor.classList.contains('message') && cursor.classList.contains('user')) {
            return cursor.dataset.rawContent || '';
        }
        cursor = cursor.previousElementSibling;
    }
    return '';
}

function groupKey(groupId) {
    return groupId == null ? '__ungrouped__' : String(groupId);
}

function normalizeGroupId(value) {
    if (value === null || value === undefined || value === '' || value === '__ungrouped__') return null;
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
}

function setLastChatId(chatId) {
    state.lastChatId = chatId == null ? null : Number(chatId);
    if (state.lastChatId == null || Number.isNaN(state.lastChatId)) {
        localStorage.removeItem('lastChatId');
    } else {
        localStorage.setItem('lastChatId', String(state.lastChatId));
    }
}

function getCollapsedGroupIdsForPersist() {
    return Object.keys(state.collapsedGroups)
        .map((key) => Number(key))
        .filter((id) => Number.isFinite(id));
}

async function persistCollapsedGroupsToServer() {
    await api('/api/groups/collapsed', {
        method: 'PUT',
        body: JSON.stringify({groupIds: getCollapsedGroupIdsForPersist()})
    });
}

function saveCollapsedGroups() {
    if (state.collapsedGroupsPersistTimer) {
        clearTimeout(state.collapsedGroupsPersistTimer);
    }
    state.collapsedGroupsPersistTimer = setTimeout(() => {
        persistCollapsedGroupsToServer().catch(() => {
        });
    }, 160);
}

function isGroupCollapsed(groupId) {
    return Boolean(state.collapsedGroups[groupKey(groupId)]);
}

function setGroupCollapsed(groupId, collapsed) {
    const key = groupKey(groupId);
    if (collapsed) {
        state.collapsedGroups[key] = true;
    } else {
        delete state.collapsedGroups[key];
    }
    saveCollapsedGroups();
}

function getMessagesScrollContainer() {
    return document.getElementById('chatMessages');
}

function getMessagesListContainer() {
    return document.getElementById('chatMessagesInner');
}

function getAssistantMessageElements() {
    const list = getMessagesListContainer();
    if (!list) {
        return [];
    }
    return Array.from(list.querySelectorAll('.message.assistant'))
        .filter((item) => item.dataset.streaming !== '1');
}

function getElementTopInScroll(el, scroll) {
    if (!el || !scroll) {
        return 0;
    }
    const scrollRect = scroll.getBoundingClientRect();
    const elRect = el.getBoundingClientRect();
    return scroll.scrollTop + (elRect.top - scrollRect.top);
}

function scrollToAssistantAnswer(direction) {
    const scroll = getMessagesScrollContainer();
    const assistantMessages = getAssistantMessageElements();
    if (!scroll || assistantMessages.length === 0) {
        return;
    }

    const currentTop = scroll.scrollTop + 8;
    const indexed = assistantMessages
        .map((el) => ({el, top: getElementTopInScroll(el, scroll)}))
        .sort((left, right) => left.top - right.top);

    let target = null;
    if (direction < 0) {
        for (let i = indexed.length - 1; i >= 0; i--) {
            if (indexed[i].top < currentTop - 4) {
                target = indexed[i];
                break;
            }
        }
    } else {
        for (let i = 0; i < indexed.length; i++) {
            if (indexed[i].top > currentTop + 4) {
                target = indexed[i];
                break;
            }
        }
    }
    if (!target) {
        return;
    }
    scroll.scrollTo({top: Math.max(0, target.top - 6), behavior: 'smooth'});
}

function updateAnswerNavPosition() {
    const wrap = document.getElementById('answerNav');
    const panel = document.querySelector('.chat-panel');
    const sendForm = document.getElementById('sendForm');
    const sendFormInner = sendForm ? sendForm.querySelector('.send-form-inner') : null;
    const textarea = document.getElementById('messageInput');
    if (!wrap || !panel || !sendForm || !sendFormInner || !textarea) {
        return;
    }

    const panelRect = panel.getBoundingClientRect();
    const sendFormRect = sendForm.getBoundingClientRect();
    const sendFormInnerRect = sendFormInner.getBoundingClientRect();
    const textareaRect = textarea.getBoundingClientRect();
    const sendFormInnerStyle = window.getComputedStyle(sendFormInner);

    const innerTopInset = Math.max(0, textareaRect.top - sendFormRect.top);
    const horizontalGap = 10;
    const wrapWidth = wrap.offsetWidth || 42;
    const innerPadLeft = Number.parseFloat(sendFormInnerStyle.paddingLeft) || 0;
    const innerPadRight = Number.parseFloat(sendFormInnerStyle.paddingRight) || 0;
    const sendContentLeft = sendFormInnerRect.left + innerPadLeft;
    const sendContentRight = sendFormInnerRect.right - innerPadRight;

    let navTop = sendFormRect.top - panelRect.top + innerTopInset;
    let navBottom = sendFormRect.bottom - panelRect.top - innerTopInset;

    const panelHeight = panel.clientHeight;
    navTop = Math.max(0, Math.min(panelHeight, navTop));
    navBottom = Math.max(navTop, Math.min(panelHeight, navBottom));
    const navHeight = Math.max(2, navBottom - navTop);

    const desiredLeft = state.answerNavSide === 'left'
        ? (sendContentLeft - panelRect.left - wrapWidth - horizontalGap)
        : (sendContentRight - panelRect.left + horizontalGap);
    const maxLeft = Math.max(0, panel.clientWidth - wrapWidth);
    const navLeft = Math.max(0, Math.min(maxLeft, desiredLeft));

    wrap.style.setProperty('--answer-nav-left', `${Math.round(navLeft)}px`);
    wrap.style.setProperty('--answer-nav-top', `${Math.round(navTop)}px`);
    wrap.style.setProperty('--answer-nav-height', `${Math.round(navHeight)}px`);
}

function updateAnswerNavButtonsState() {
    const wrap = document.getElementById('answerNav');
    const prevBtn = document.getElementById('prevAnswerBtn');
    const nextBtn = document.getElementById('nextAnswerBtn');
    if (!wrap || !prevBtn || !nextBtn) {
        return;
    }

    const isChatView = state.view === 'chat';
    if (!isChatView) {
        wrap.classList.add('hidden');
        updateAdaptiveScrollNavState();
        return;
    }

    wrap.classList.remove('hidden');
    updateAnswerNavPosition();
    const scroll = getMessagesScrollContainer();
    const assistantMessages = getAssistantMessageElements();
    if (!scroll || assistantMessages.length === 0) {
        prevBtn.disabled = true;
        nextBtn.disabled = true;
        updateAdaptiveScrollNavState();
        return;
    }

    const currentTop = scroll.scrollTop + 8;
    const tops = assistantMessages
        .map((el) => getElementTopInScroll(el, scroll))
        .sort((a, b) => a - b);

    prevBtn.disabled = !tops.some((top) => top < currentTop - 4);
    nextBtn.disabled = !tops.some((top) => top > currentTop + 4);
    updateAdaptiveScrollNavState();
}

function updateAdaptiveScrollNavState() {
    const wrap = document.getElementById('adaptiveScrollNav');
    const btn = document.getElementById('adaptiveScrollBtn');
    const scroll = getMessagesScrollContainer();
    if (!wrap || !btn) {
        return;
    }
    if (state.view !== 'chat' || !scroll) {
        wrap.classList.add('hidden');
        return;
    }

    const visibleHeight = Math.max(1, scroll.clientHeight);
    const contentHeight = Math.max(0, scroll.scrollHeight);
    const ratio = contentHeight / visibleHeight;
    const maxTop = Math.max(0, contentHeight - visibleHeight);
    if (maxTop <= 4 || ratio <= 1.2) {
        wrap.classList.add('hidden');
        btn.disabled = true;
        btn.textContent = '↓';
        return;
    }

    wrap.classList.remove('hidden');
    updateAdaptiveScrollNavPosition();
    btn.disabled = false;
    const isUpperHalf = scroll.scrollTop <= (maxTop / 2);
    if (isUpperHalf) {
        btn.textContent = '↓';
    } else {
        btn.textContent = '↑';
    }
}

function updateAdaptiveScrollNavPosition() {
    const wrap = document.getElementById('adaptiveScrollNav');
    const panel = document.querySelector('.chat-panel');
    const messages = getMessagesScrollContainer();
    if (!wrap || !panel || !messages) {
        return;
    }

    const panelRect = panel.getBoundingClientRect();
    const messagesRect = messages.getBoundingClientRect();
    const panelHeight = panel.clientHeight;

    let navTop = messagesRect.top - panelRect.top;
    let navBottom = messagesRect.bottom - panelRect.top;
    navTop = Math.max(0, Math.min(panelHeight, navTop));
    navBottom = Math.max(navTop, Math.min(panelHeight, navBottom));
    const navHeight = Math.max(0, navBottom - navTop);

    wrap.style.setProperty('--adaptive-nav-top', `${Math.round(navTop)}px`);
    wrap.style.setProperty('--adaptive-nav-height', `${Math.round(navHeight)}px`);
}

function scrollChatAdaptive() {
    const scroll = getMessagesScrollContainer();
    if (!scroll) {
        return;
    }
    const maxTop = Math.max(0, scroll.scrollHeight - scroll.clientHeight);
    if (maxTop <= 0) {
        return;
    }
    const isUpperHalf = scroll.scrollTop <= (maxTop / 2);
    scroll.scrollTo({top: isUpperHalf ? maxTop : 0, behavior: 'smooth'});
}

function setEmptyChatLayout(enabled) {
    const chatInner = document.querySelector('.chat-inner');
    if (!chatInner) {
        return;
    }
    chatInner.classList.toggle('empty-chat-mode', Boolean(enabled));
}

function isNearMessagesBottom() {
    const scroll = getMessagesScrollContainer();
    if (!scroll) {
        return true;
    }
    const bottomOffset = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight;
    return bottomOffset <= 24;
}

function updateMessagesAutoScrollState() {
    state.messagesAutoScroll = isNearMessagesBottom();
    updateAnswerNavButtonsState();
    updateAdaptiveScrollNavPosition();
    updateAdaptiveScrollNavState();
}

function scrollMessagesToBottom(force = false) {
    const scroll = getMessagesScrollContainer();
    if (!scroll) {
        return;
    }
    if (force || state.messagesAutoScroll) {
        scroll.scrollTop = scroll.scrollHeight;
    }
}

function createMessageElement(role, content, createdAt, isStreaming = false) {
    const wrap = document.createElement('div');
    wrap.className = `message ${role}`;
    wrap.dataset.role = role;
    wrap.dataset.rawContent = content || '';

    const body = document.createElement('div');
    if (role === 'assistant') {
        body.className = 'assistant-content';
        body.innerHTML = renderAssistantMarkdown(content || '');
        body.querySelectorAll('pre code').forEach(block => hljs.highlightElement(block));
        if (!isStreaming) {
            attachCodeCopyControls(body);
        }
    } else {
        body.innerHTML = escapeHtml(content || '').replace(/\n/g, '<br>');
    }

    const time = document.createElement('div');
    time.className = 'message-time';
    time.textContent = createdAt ? new Date(createdAt).toLocaleString('ru-RU') : nowTime();

    let actions = null;

    if (role === 'user' && !isStreaming) {
        actions = document.createElement('div');
        actions.className = 'assistant-actions';

        const resendBtn = makeActionButton('Обновить', 'assistant-action-btn', async () => {
            const prompt = wrap.dataset.rawContent || '';
            if (!prompt.trim()) {
                return;
            }
            await sendPromptToCurrentChat(prompt);
        });

        const editBtn = makeActionButton('Редактировать', 'assistant-action-btn', async () => {
            const initialValue = wrap.dataset.rawContent || '';
            const edited = await askTextInput({
                title: 'Редактирование сообщения',
                label: 'Текст запроса',
                initialValue,
                confirmLabel: 'Отправить',
                maxLength: 8000,
                anchorEl: wrap
            });
            if (edited == null || !edited.trim()) {
                return;
            }
            await sendPromptToCurrentChat(edited);
        });

        actions.append(resendBtn, editBtn);
    }

    if (role === 'assistant' && !isStreaming) {
        actions = document.createElement('div');
        actions.className = 'assistant-actions';

        const copyBtn = makeActionButton('Скопировать', 'assistant-action-btn', async () => {
            const copied = await copyTextToClipboard(wrap.dataset.rawContent || '');
            if (!copied) {
                alert('Не удалось скопировать сообщение в буфер обмена.');
            }
        });

        const saveBtn = makeActionButton('Сохранить', 'assistant-action-btn', () => {
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            downloadTextFile(`agty-response-${timestamp}.md`, wrap.dataset.rawContent || '');
        });

        const regenBtn = makeActionButton('Обновить', 'assistant-action-btn', async () => {
            const prompt = findPreviousUserPrompt(wrap);
            if (!prompt || !state.currentChat) {
                return;
            }
            await sendPromptToCurrentChat(prompt);
        });

        actions.append(copyBtn, saveBtn, regenBtn);
    }

    const footer = document.createElement('div');
    footer.className = 'message-footer';
    if (actions) {
        footer.appendChild(actions);
    }
    footer.appendChild(time);

    wrap.append(body, footer);

    if (isStreaming) {
        wrap.dataset.streaming = '1';
    }
    return wrap;
}

function renderMessages(messages, options = {}) {
    const forceBottom = options.forceBottom !== undefined ? Boolean(options.forceBottom) : true;
    const preserveScroll = Boolean(options.preserveScroll);
    const list = getMessagesListContainer();
    const scroll = getMessagesScrollContainer();
    const previousScrollTop = scroll ? scroll.scrollTop : 0;
    list.innerHTML = '';

    if (!messages || messages.length === 0) {
        setEmptyChatLayout(true);
        list.innerHTML = '<div class="empty-state"></div>';
        state.messagesAutoScroll = true;
        scrollMessagesToBottom(true);
        updateAnswerNavButtonsState();
        return;
    }

    setEmptyChatLayout(false);
    for (const msg of messages) {
        list.appendChild(createMessageElement(msg.role, msg.content, msg.createdAt));
    }

    if (forceBottom) {
        state.messagesAutoScroll = true;
        scrollMessagesToBottom(true);
        updateAnswerNavButtonsState();
        return;
    }

    if (preserveScroll && scroll) {
        scroll.scrollTop = previousScrollTop;
    }
    updateAnswerNavButtonsState();
}

function appendMessage(role, content) {
    const list = getMessagesListContainer();
    const empty = list.querySelector('.empty-state');
    if (empty) empty.remove();

    const messageEl = createMessageElement(role, content, null);
    list.appendChild(messageEl);
    scrollMessagesToBottom();
    updateAnswerNavButtonsState();
    return messageEl;
}

function createStreamingAssistantMessage() {
    const list = getMessagesListContainer();
    const empty = list.querySelector('.empty-state');
    if (empty) empty.remove();

    const messageEl = createMessageElement('assistant', 'Идет размышление...', null, true);
    const actions = messageEl.querySelector('.assistant-actions');
    if (actions) {
        actions.remove();
    }
    list.appendChild(messageEl);
    scrollMessagesToBottom();
    updateAnswerNavButtonsState();
    return messageEl;
}

function updateStreamingAssistantMessage(el, text) {
    el.dataset.rawContent = text || '';
    const actions = el.querySelector('.assistant-actions');
    if (actions) {
        actions.remove();
    }
    const contentEl = el.querySelector('.assistant-content');
    if (contentEl) {
        contentEl.innerHTML = renderAssistantMarkdown(text || '');
        contentEl.querySelectorAll('pre code').forEach(block => hljs.highlightElement(block));
    }
    scrollMessagesToBottom();
    updateAnswerNavButtonsState();
}

function getSelectedModel() {
    const composeSelect = document.getElementById('composeModelSelect');
    if (composeSelect && !composeSelect.disabled && composeSelect.value) {
        return composeSelect.value;
    }
    const select = document.getElementById('modelSelect');
    return select ? select.value : '';
}

function persistSelectedModel(model) {
    const normalized = (model || '').trim();
    state.selectedModel = normalized;
}

function getCurrentChatModelPreference() {
    const chatModel = state.currentChat?.chat?.model;
    if (chatModel && state.models.includes(chatModel)) {
        return chatModel;
    }
    if (state.selectedModel && state.models.includes(state.selectedModel)) {
        return state.selectedModel;
    }
    if (state.defaultModel && state.models.includes(state.defaultModel)) {
        return state.defaultModel;
    }
    return state.models[0] || '';
}

function getModelSelectElements() {
    return ['modelSelect', 'composeModelSelect']
        .map((id) => document.getElementById(id))
        .filter(Boolean);
}

function syncModelSelectValues(value) {
    for (const select of getModelSelectElements()) {
        if (Array.from(select.options).some((option) => option.value === value)) {
            select.value = value;
        }
    }
}

function setSendButtonStreaming(streaming) {
    const sendBtn = document.getElementById('sendBtn');
    if (!sendBtn) {
        return;
    }
    sendBtn.classList.toggle('stop-mode', streaming);
    sendBtn.textContent = streaming ? '■' : 'Отправить';
    sendBtn.title = streaming ? 'Остановить генерацию' : '';
}

function fillModels() {
    const selects = getModelSelectElements();
    const firstValue = selects[0]?.value || '';
    const currentValue = getCurrentChatModelPreference() || firstValue || state.defaultModel;
    for (const select of selects) {
        select.innerHTML = '';
    }

    if (!state.models || state.models.length === 0) {
        for (const select of selects) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'Нет моделей';
            select.appendChild(option);
            select.disabled = true;
        }
        persistSelectedModel('');
        return;
    }

    for (const select of selects) {
        for (const model of state.models) {
            const option = document.createElement('option');
            option.value = model;
            option.textContent = model;
            select.appendChild(option);
        }
    }

    let resolvedValue = '';
    if (state.models.includes(currentValue)) {
        resolvedValue = currentValue;
    } else if (state.models.includes(state.defaultModel)) {
        resolvedValue = state.defaultModel;
    } else if (state.models.length > 0) {
        resolvedValue = state.models[0];
    }

    for (const select of selects) {
        select.value = resolvedValue;
        select.disabled = false;
    }
    persistSelectedModel(resolvedValue);
    if (state.currentChat?.chat && !state.currentChat.chat.private) {
        state.currentChat.chat.model = resolvedValue;
    }
}

function showModelsLoadingState() {
    for (const select of getModelSelectElements()) {
        select.innerHTML = '';
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'loading...';
        select.appendChild(option);
        select.disabled = true;
    }
}

async function persistGroupOrder(groupIds) {
    await api('/api/groups/reorder', {
        method: 'PATCH',
        body: JSON.stringify({ids: groupIds})
    });
}

async function persistChatOrder(groupId, chatIds) {
    await api('/api/chats/reorder', {
        method: 'PATCH',
        body: JSON.stringify({groupId, chatIds})
    });
}

function chatsByGroup(groupId) {
    return state.chats.filter(chat => (chat.groupId ?? null) === (groupId ?? null));
}

async function runTreeMutation(operation) {
    if (state.treeMutationInFlight) {
        return;
    }
    state.treeMutationInFlight = true;
    try {
        await operation();
    } finally {
        state.treeMutationInFlight = false;
    }
}

function clearGroupDropIndicators() {
    document.querySelectorAll('.group-drop-before, .group-drop-after').forEach((node) => {
        node.classList.remove('group-drop-before', 'group-drop-after');
    });
}

function clearChatDropIndicators() {
    document.querySelectorAll('.chat-drop-before, .chat-drop-after, .chat-drop-end').forEach((node) => {
        node.classList.remove('chat-drop-before', 'chat-drop-after', 'chat-drop-end');
    });
}

function clearAllDropIndicators() {
    clearGroupDropIndicators();
    clearChatDropIndicators();
}

function isDropAfterCursor(event, element) {
    const rect = element.getBoundingClientRect();
    return (event.clientY - rect.top) >= (rect.height / 2);
}

function renderTree() {
    const tree = document.getElementById('chatTree');
    clearAllDropIndicators();
    tree.innerHTML = '';
    tree.ondragleave = (event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) {
            clearAllDropIndicators();
        }
    };

    const allSections = state.groups.map(group => ({
        id: group.id,
        name: group.name,
        chats: state.chats.filter(chat => chat.groupId === group.id)
    }));

    for (const section of allSections) {
        const groupNode = document.createElement('div');
        groupNode.className = 'group-node';

        const collapsed = isGroupCollapsed(section.id);

        const head = document.createElement('div');
        head.className = 'group-head';

        const main = document.createElement('div');
        main.className = 'group-main';
        main.onclick = () => {
            setGroupCollapsed(section.id, !collapsed);
            renderTree();
        };

        const chevron = document.createElement('div');
        chevron.className = 'group-chevron';
        chevron.textContent = collapsed ? '▸' : '▾';

        const title = document.createElement('div');
        title.className = 'group-title';
        title.textContent = `${section.name}`;

        main.append(chevron, title);
        main.addEventListener('dragover', (e) => {
            if (state.dragChatId == null) return;
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
        });
        main.addEventListener('drop', async (e) => {
            if (state.dragChatId == null) return;
            e.preventDefault();
            e.stopPropagation();
            await runTreeMutation(async () => {
                const targetGroupId = section.id;
                const sourceGroupId = state.dragChatSourceGroupId;
                const movedChatId = state.dragChatId;

                if (sourceGroupId !== targetGroupId) {
                    await api(`/api/chats/${movedChatId}/move`, {
                        method: 'PATCH',
                        body: JSON.stringify({targetGroupId})
                    });
                }

                const targetIds = chatsByGroup(targetGroupId).map(c => c.id).filter(id => id !== movedChatId);
                targetIds.push(movedChatId);
                await persistChatOrder(targetGroupId, targetIds);

                if (sourceGroupId !== targetGroupId) {
                    const sourceIds = chatsByGroup(sourceGroupId).map(c => c.id).filter(id => id !== movedChatId);
                    await persistChatOrder(sourceGroupId, sourceIds);
                }

                await refreshData(state.currentChat?.chat?.id ?? movedChatId);
            });
        });

        const actions = document.createElement('div');
        actions.className = 'group-actions';

        const createBtn = document.createElement('button');
        createBtn.type = 'button';
        createBtn.className = 'mini-btn';
        createBtn.textContent = '+ чат';
        createBtn.onclick = (e) => {
            e.stopPropagation();
            createChat(section.id);
        };

        const renameBtn = document.createElement('button');
        renameBtn.type = 'button';
        renameBtn.className = 'mini-btn';
        renameBtn.textContent = '✎';
        renameBtn.onclick = (e) => {
            e.stopPropagation();
            renameGroup(section.id, section.name, e.currentTarget);
        };

        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'mini-btn';
        deleteBtn.textContent = '🗑';
        deleteBtn.onclick = (e) => {
            e.stopPropagation();
            deleteGroup(section.id, section.name, e.currentTarget);
        };

        actions.append(createBtn, renameBtn, deleteBtn);

        head.append(main, actions);

        const chatsWrap = document.createElement('div');
        chatsWrap.className = 'group-chats';
        chatsWrap.dataset.groupId = groupKey(section.id);
        if (collapsed) {
            chatsWrap.classList.add('hidden');
        }

        groupNode.draggable = true;
        groupNode.dataset.groupId = String(section.id);
        groupNode.addEventListener('dragstart', (e) => {
            if (e.target && e.target.closest && e.target.closest('.chat-item')) {
                e.preventDefault();
                return;
            }
            state.dragGroupId = section.id;
            clearGroupDropIndicators();
            e.dataTransfer.setData('text/plain', `group:${section.id}`);
            e.dataTransfer.effectAllowed = 'move';
        });
        groupNode.addEventListener('dragover', (e) => {
            if (state.dragGroupId == null) return;
            e.preventDefault();
            e.stopPropagation();
            e.dataTransfer.dropEffect = 'move';
            const placeAfter = isDropAfterCursor(e, groupNode);
            clearGroupDropIndicators();
            groupNode.classList.add(placeAfter ? 'group-drop-after' : 'group-drop-before');
        });
        groupNode.addEventListener('drop', async (e) => {
            if (state.dragGroupId == null) return;
            e.preventDefault();
            e.stopPropagation();
            const placeAfter = isDropAfterCursor(e, groupNode);
            clearGroupDropIndicators();
            await runTreeMutation(async () => {
                const targetGroupId = section.id;
                const sourceGroupId = state.dragGroupId;
                if (sourceGroupId === targetGroupId) return;

                const ordered = state.groups.map(g => g.id);
                const fromIndex = ordered.indexOf(sourceGroupId);
                const toIndex = ordered.indexOf(targetGroupId);
                if (fromIndex < 0 || toIndex < 0) return;
                const [moved] = ordered.splice(fromIndex, 1);
                let insertIndex = toIndex + (placeAfter ? 1 : 0);
                if (fromIndex < toIndex) {
                    insertIndex -= 1;
                }
                if (insertIndex < 0) insertIndex = 0;
                if (insertIndex > ordered.length) insertIndex = ordered.length;
                ordered.splice(insertIndex, 0, moved);
                await persistGroupOrder(ordered);
                await refreshData(state.currentChat?.chat?.id ?? state.lastChatId);
            });
        });
        groupNode.addEventListener('dragend', () => {
            state.dragGroupId = null;
            clearGroupDropIndicators();
        });

        const sortedChats = [...section.chats];

        chatsWrap.addEventListener('dragover', (e) => {
            if (state.dragChatId == null) return;
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            clearChatDropIndicators();
            chatsWrap.classList.add('chat-drop-end');
        });

        chatsWrap.addEventListener('drop', async (e) => {
            if (state.dragChatId == null) return;
            e.preventDefault();
            e.stopPropagation();
            clearChatDropIndicators();
            await runTreeMutation(async () => {
                const targetGroupId = normalizeGroupId(chatsWrap.dataset.groupId);
                const sourceGroupId = state.dragChatSourceGroupId;
                const movedChatId = state.dragChatId;

                if (sourceGroupId !== targetGroupId) {
                    await api(`/api/chats/${movedChatId}/move`, {
                        method: 'PATCH',
                        body: JSON.stringify({targetGroupId})
                    });
                }

                const targetIds = chatsByGroup(targetGroupId).map(c => c.id).filter(id => id !== movedChatId);
                targetIds.push(movedChatId);
                await persistChatOrder(targetGroupId, targetIds);

                if (sourceGroupId !== targetGroupId) {
                    const sourceIds = chatsByGroup(sourceGroupId).map(c => c.id).filter(id => id !== movedChatId);
                    await persistChatOrder(sourceGroupId, sourceIds);
                }

                await refreshData(state.currentChat?.chat?.id ?? movedChatId);
            });
        });

        for (const chat of sortedChats) {
            const chatItem = document.createElement('div');
            chatItem.className = 'chat-item';
            chatItem.draggable = true;
            chatItem.dataset.chatId = String(chat.id);
            chatItem.dataset.groupId = groupKey(chat.groupId);
            if (state.currentChat?.chat?.id === chat.id) {
                chatItem.classList.add('active');
            }

            chatItem.addEventListener('dragstart', (e) => {
                e.stopPropagation();
                state.dragChatId = chat.id;
                state.dragChatSourceGroupId = chat.groupId ?? null;
                clearChatDropIndicators();
                e.dataTransfer.setData('text/plain', `chat:${chat.id}`);
                e.dataTransfer.effectAllowed = 'move';
            });
            chatItem.addEventListener('dragend', () => {
                state.dragChatId = null;
                state.dragChatSourceGroupId = null;
                clearChatDropIndicators();
            });
            chatItem.addEventListener('dragover', (e) => {
                if (state.dragChatId == null) return;
                e.preventDefault();
                e.stopPropagation();
                e.dataTransfer.dropEffect = 'move';
                const placeAfter = isDropAfterCursor(e, chatItem);
                clearChatDropIndicators();
                chatItem.classList.add(placeAfter ? 'chat-drop-after' : 'chat-drop-before');
            });
            chatItem.addEventListener('drop', async (e) => {
                if (state.dragChatId == null) return;
                e.preventDefault();
                e.stopPropagation();
                const placeAfter = isDropAfterCursor(e, chatItem);
                clearChatDropIndicators();
                await runTreeMutation(async () => {
                    const movedChatId = state.dragChatId;
                    const sourceGroupId = state.dragChatSourceGroupId;
                    const targetGroupId = chat.groupId ?? null;
                    const targetChatId = chat.id;

                    if (movedChatId === targetChatId) return;

                    if (sourceGroupId !== targetGroupId) {
                        await api(`/api/chats/${movedChatId}/move`, {
                            method: 'PATCH',
                            body: JSON.stringify({targetGroupId})
                        });
                    }

                    const targetIds = chatsByGroup(targetGroupId).map(c => c.id).filter(id => id !== movedChatId);
                    const insertAt = targetIds.indexOf(targetChatId);
                    if (insertAt < 0) {
                        targetIds.push(movedChatId);
                    } else {
                        targetIds.splice(insertAt + (placeAfter ? 1 : 0), 0, movedChatId);
                    }
                    await persistChatOrder(targetGroupId, targetIds);

                    if (sourceGroupId !== targetGroupId) {
                        const sourceIds = chatsByGroup(sourceGroupId).map(c => c.id).filter(id => id !== movedChatId);
                        await persistChatOrder(sourceGroupId, sourceIds);
                    }

                    await refreshData(state.currentChat?.chat?.id ?? movedChatId);
                });
            });

            const name = document.createElement('div');
            name.className = 'chat-name';
            name.textContent = chat.title;
            name.onclick = () => selectChat(chat.id);

            const actionsWrap = document.createElement('div');
            actionsWrap.className = 'chat-actions';

            const renameChatBtn = document.createElement('button');
            renameChatBtn.type = 'button';
            renameChatBtn.className = 'mini-btn';
            renameChatBtn.textContent = '✎';
            renameChatBtn.onclick = (e) => {
                e.stopPropagation();
                renameChat(chat.id, chat.title, e.currentTarget);
            };

            const deleteChatBtn = document.createElement('button');
            deleteChatBtn.type = 'button';
            deleteChatBtn.className = 'mini-btn';
            deleteChatBtn.textContent = '🗑';
            deleteChatBtn.onclick = (e) => {
                e.stopPropagation();
                deleteChat(chat.id, chat.title, e.currentTarget);
            };

            actionsWrap.append(renameChatBtn, deleteChatBtn);
            chatItem.append(name, actionsWrap);
            chatsWrap.appendChild(chatItem);
        }

        if (section.chats.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'label';
            empty.style.padding = '4px 6px';
            empty.textContent = 'Пока пусто';
            chatsWrap.appendChild(empty);
        }

        groupNode.append(head, chatsWrap);
        tree.appendChild(groupNode);
    }
}

async function refreshData(keepChatId = null, options = {}) {
    const forceBottom = options.forceBottom !== undefined ? Boolean(options.forceBottom) : true;
    const preserveScroll = Boolean(options.preserveScroll);
    const [groups, chats] = await Promise.all([
        api('/api/groups'),
        api('/api/chats')
    ]);

    state.groups = groups;
    state.chats = chats;
    renderTree();

    const target = keepChatId || state.currentChat?.chat?.id || state.lastChatId;
    if (target && state.chats.some(c => c.id === target)) {
        await selectChat(target, {forceBottom, preserveScroll});
    } else if (state.currentChat?.chat?.private) {
        setCurrentTitle(state.currentChat.chat);
        renderMessages(state.currentChat.messages || [], {forceBottom, preserveScroll});
    } else {
        state.currentChat = null;
        setLastChatId(null);
        setCurrentTitle(null);
        renderMessages([], {forceBottom: true});
    }
}

async function selectChat(chatId, options = {}) {
    const forceBottom = options.forceBottom !== undefined ? Boolean(options.forceBottom) : true;
    const preserveScroll = Boolean(options.preserveScroll);
    const detail = await api(`/api/chats/${chatId}`);
    state.currentChat = detail;
    state.privateChat = null;
    setWorkspaceView('chat');
    setLastChatId(chatId);
    setCurrentTitle(detail.chat);
    if (detail.chat?.model) {
        persistSelectedModel(detail.chat.model);
        syncModelSelectValues(detail.chat.model);
    }
    renderMessages(detail.messages, {forceBottom, preserveScroll});
    renderTree();
}

async function loadInitialData() {
    const bootstrap = await api('/api/bootstrap');
    state.user = bootstrap.username;
    state.displayName = bootstrap.displayName || bootstrap.username || '';
    state.email = bootstrap.email || '';
    state.models = bootstrap.models || [];
    state.defaultModel = bootstrap.defaultModel;
    state.theme = bootstrap.theme || 'light';
    state.fontSize = String(bootstrap.fontSize || 14);
    state.menuFontSize = String(bootstrap.menuFontSize || 13);
    state.sidebarWidth = String(bootstrap.sidebarWidth || 320);
    state.answerNavSide = bootstrap.answerNavSide === 'left' ? 'left' : 'right';
    state.selectedModel = (bootstrap.selectedModel || '').trim();
    state.collapsedGroups = {};
    if (Array.isArray(bootstrap.collapsedGroupIds)) {
        for (const groupId of bootstrap.collapsedGroupIds) {
            const normalized = Number(groupId);
            if (Number.isFinite(normalized)) {
                state.collapsedGroups[String(normalized)] = true;
            }
        }
    }

    setAccountIdentity(state.displayName);
    fillSettingsForm({
        theme: state.theme,
        fontSize: state.fontSize,
        menuFontSize: state.menuFontSize,
        sidebarWidth: state.sidebarWidth,
        answerNavSide: state.answerNavSide
    });
    fillModels();
    setWorkspaceView('chat');

    await refreshData(state.lastChatId);

    if (state.chats.length > 0 && !state.currentChat) {
        await selectChat(state.lastChatId && state.chats.some(c => c.id === state.lastChatId)
            ? state.lastChatId
            : state.chats[0].id);
    }
}

function openPrivateChat() {
    state.privateChat = {
        chat: {
            id: null,
            title: 'Приватный чат',
            groupId: null,
            groupName: null,
            private: true
        },
        messages: []
    };
    state.currentChat = state.privateChat;
    setWorkspaceView('chat');
    syncModelSelectValues(getCurrentChatModelPreference());
    setCurrentTitle(state.currentChat.chat);
    renderMessages(state.currentChat.messages, {forceBottom: true});
    renderTree();
}

async function createGroup(anchorEl = null) {
    const name = await askTextInput({
        title: 'Новая группа',
        label: 'Название группы',
        initialValue: '',
        confirmLabel: 'Создать',
        maxLength: 120,
        anchorEl
    });
    if (name == null) return;

    await api('/api/groups', {
        method: 'POST',
        body: JSON.stringify({name})
    });
    await refreshData();
}

async function renameGroup(groupId, currentName, anchorEl = null) {
    const name = await askTextInput({
        title: 'Редактирование группы',
        label: 'Название группы',
        initialValue: currentName,
        confirmLabel: 'Сохранить',
        maxLength: 120,
        anchorEl
    });
    if (name == null) return;
    await api(`/api/groups/${groupId}`, {
        method: 'PUT',
        body: JSON.stringify({name})
    });
    await refreshData();
}

async function deleteGroup(groupId, groupName, anchorEl = null) {
    const ok = await askConfirm({
        title: 'Удаление группы',
        text: `Удалить группу "${groupName}"? Чаты будут перемещены в "Основная группа".`,
        confirmLabel: 'Удалить группу',
        anchorEl
    });
    if (!ok) return;
    await api(`/api/groups/${groupId}`, {method: 'DELETE'});
    await refreshData();
}

async function createChat(groupId) {
    const created = await api('/api/chats', {
        method: 'POST',
        body: JSON.stringify({title: '', groupId})
    });
    await refreshData(created.id);
}

function resolveCurrentGroupIdForNewChat() {
    if (state.currentChat?.chat && !state.currentChat.chat.private) {
        return state.currentChat.chat.groupId ?? null;
    }
    if (state.groups.length > 0) {
        return state.groups[0].id;
    }
    return null;
}

async function createChatFromHeader() {
    await createChat(resolveCurrentGroupIdForNewChat());
}

async function ensureActiveChat() {
    if (state.currentChat) {
        return true;
    }

    if (state.chats.length > 0) {
        await selectChat(state.chats[0].id);
        return true;
    }

    if (state.groups.length > 0) {
        const created = await api('/api/chats', {
            method: 'POST',
            body: JSON.stringify({title: '', groupId: state.groups[0].id})
        });
        await refreshData(created.id);
        return Boolean(state.currentChat);
    }

    await refreshData();
    if (state.currentChat) {
        return true;
    }

    if (state.groups.length > 0) {
        const created = await api('/api/chats', {
            method: 'POST',
            body: JSON.stringify({title: '', groupId: state.groups[0].id})
        });
        await refreshData(created.id);
    }

    return Boolean(state.currentChat);
}

async function renameChat(chatId, currentName, anchorEl = null) {
    const title = await askTextInput({
        title: 'Редактирование чата',
        label: 'Название чата',
        initialValue: currentName,
        confirmLabel: 'Сохранить',
        maxLength: 160,
        anchorEl
    });
    if (title == null) return;

    const chat = state.chats.find(c => c.id === chatId);
    await api(`/api/chats/${chatId}`, {
        method: 'PUT',
        body: JSON.stringify({
            title,
            groupId: chat?.groupId ?? null
        })
    });

    await refreshData(chatId);
}

async function deleteChat(chatId, chatTitle, anchorEl = null) {
    const ok = await askConfirm({
        title: 'Удаление чата',
        text: `Удалить чат "${chatTitle}"? Это действие нельзя отменить.`,
        confirmLabel: 'Удалить чат',
        anchorEl
    });
    if (!ok) return;
    const wasActiveChat = !state.currentChat?.chat?.private && state.currentChat?.chat?.id === chatId;
    if (state.isStreaming && !state.activeStreamPrivate && state.activeStreamChatId === chatId) {
        await stopCurrentStreaming();
    }
    await api(`/api/chats/${chatId}`, {method: 'DELETE'});
    await refreshData();
    if (wasActiveChat) {
        await ensureActiveChat();
    }
}

async function submitAuth(event) {
    event.preventDefault();

    const username = document.getElementById('authUsername').value.trim();
    const password = document.getElementById('authPassword').value.trim();
    const endpoint = state.mode === 'login' ? '/api/auth/login' : '/api/auth/register';

    try {
        await api(endpoint, {
            method: 'POST',
            body: JSON.stringify({username, password})
        });
        authRedirectInProgress = false;
        document.getElementById('authError').textContent = '';
        localStorage.setItem('authHint', '1');
        showApp();
        await loadInitialData();
    } catch (error) {
        document.getElementById('authError').textContent = error.message;
    }
}

async function logout() {
    await stopCurrentStreaming();
    await api('/api/auth/logout', {method: 'POST'});
    localStorage.removeItem('authHint');
    resetAppStateOnSessionExpired();
    showAuth();
}

async function stopCurrentStreaming() {
    if (!state.isStreaming) {
        return;
    }

    const streamController = state.activeStreamController;
    const streamChatId = state.activeStreamChatId;
    const streamPrivate = state.activeStreamPrivate;

    if (!streamPrivate && streamChatId != null) {
        try {
            await api(`/api/chats/${streamChatId}/messages/stop`, {method: 'POST'});
        } catch {
        }
    }

    if (streamController) {
        streamController.abort();
    }
}

async function refreshModels() {
    const refreshButtons = ['refreshModelsBtn', 'composeRefreshModelsBtn']
        .map((id) => document.getElementById(id))
        .filter(Boolean);
    const previousModels = [...state.models];
    const previousDefaultModel = state.defaultModel;
    for (const refreshBtn of refreshButtons) {
        refreshBtn.disabled = true;
    }
    showModelsLoadingState();
    try {
        const payload = await api('/api/models/refresh', {method: 'POST'});
        state.models = payload.models || [];
        state.defaultModel = payload.defaultModel || state.defaultModel;
        fillModels();
    } catch (error) {
        state.models = previousModels;
        state.defaultModel = previousDefaultModel;
        fillModels();
        alert(`Не удалось обновить модели: ${error.message}`);
    } finally {
        for (const refreshBtn of refreshButtons) {
            refreshBtn.disabled = false;
        }
    }
}

async function loadProfile() {
    const profile = await api('/api/auth/profile');
    state.displayName = profile.displayName || state.displayName || '';
    state.email = profile.email || '';
    setAccountIdentity(state.displayName || profile.username || state.user || '');
    fillProfileForm(profile);
}

async function openProfilePage() {
    setWorkspaceView('profile');
    setInlineStatus('profileStatus', '');
    setInlineStatus('passwordStatus', '');
    await loadProfile();
}

async function openSettingsPage() {
    setWorkspaceView('settings');
    setInlineStatus('settingsStatus', '');
    const settings = await api('/api/settings');
    fillSettingsForm(settings);
}

async function openStatsPage() {
    setWorkspaceView('stats');
    setInlineStatus('statsStatus', 'Загрузка статистики...');
    try {
        const payload = await api('/api/stats/models');
        state.modelUsageStats = payload;
        renderModelUsageStats(payload);
        setInlineStatus('statsStatus', '');
    } catch (error) {
        setInlineStatus('statsStatus', error.message || 'Не удалось загрузить статистику', true);
    }
}

async function saveProfile(event) {
    event.preventDefault();
    const displayName = document.getElementById('profileDisplayName').value.trim();
    const email = document.getElementById('profileEmail').value.trim();
    try {
        const saved = await api('/api/auth/profile', {
            method: 'PUT',
            body: JSON.stringify({displayName, email})
        });
        state.displayName = saved.displayName || '';
        state.email = saved.email || '';
        setAccountIdentity(state.displayName || saved.username || state.user || '');
        fillProfileForm(saved);
        setInlineStatus('profileStatus', 'Профиль сохранен');
    } catch (error) {
        setInlineStatus('profileStatus', error.message, true);
    }
}

async function savePassword(event) {
    event.preventDefault();
    const currentPassword = document.getElementById('currentPasswordInput').value;
    const newPassword = document.getElementById('newPasswordInput').value;
    const confirmPassword = document.getElementById('confirmPasswordInput').value;
    if (newPassword !== confirmPassword) {
        setInlineStatus('passwordStatus', 'Новые пароли не совпадают', true);
        return;
    }
    try {
        await api('/api/auth/change-password', {
            method: 'POST',
            body: JSON.stringify({currentPassword, newPassword})
        });
        document.getElementById('currentPasswordInput').value = '';
        document.getElementById('newPasswordInput').value = '';
        document.getElementById('confirmPasswordInput').value = '';
        setInlineStatus('passwordStatus', 'Пароль обновлен');
    } catch (error) {
        setInlineStatus('passwordStatus', error.message, true);
    }
}

async function saveSettings(event) {
    event.preventDefault();
    const payload = {
        theme: state.theme,
        fontSize: Number(document.getElementById('fontSizeInput').value),
        menuFontSize: Number(document.getElementById('menuFontSizeInput').value),
        sidebarWidth: Number(document.getElementById('sidebarWidthInput').value),
        answerNavSide: document.getElementById('answerNavSideInput').value,
        selectedModel: state.selectedModel
    };

    applyFontSize(payload.fontSize);
    applyMenuFontSize(payload.menuFontSize);
    applySidebarWidth(payload.sidebarWidth);
    applyAnswerNavSide(payload.answerNavSide);

    try {
        const saved = await api('/api/settings', {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        fillSettingsForm(saved);
        state.selectedModel = saved.selectedModel || state.selectedModel;
        setInlineStatus('settingsStatus', 'Настройки сохранены');
    } catch (error) {
        setInlineStatus('settingsStatus', error.message, true);
    }
}

async function persistSelectedModelToServer(model) {
    const normalized = (model || '').trim();
    persistSelectedModel(normalized);
    syncModelSelectValues(normalized);
    try {
        const saved = await api('/api/settings', {
            method: 'PUT',
            body: JSON.stringify({selectedModel: normalized})
        });
        if (saved?.selectedModel) {
            persistSelectedModel(saved.selectedModel);
            syncModelSelectValues(saved.selectedModel);
        }
    } catch {
    }
}

async function persistModelForCurrentContext(model) {
    const normalized = (model || '').trim();
    if (!normalized) {
        return;
    }

    persistSelectedModel(normalized);
    syncModelSelectValues(normalized);

    const current = state.currentChat?.chat;
    if (!current || current.private || current.id == null) {
        await persistSelectedModelToServer(normalized);
        return;
    }

    try {
        const updated = await api(`/api/chats/${current.id}`, {
            method: 'PUT',
            body: JSON.stringify({model: normalized})
        });
        if (updated?.model) {
            current.model = updated.model;
            persistSelectedModel(updated.model);
            syncModelSelectValues(updated.model);
            const chatInList = state.chats.find((item) => item.id === current.id);
            if (chatInList) {
                chatInList.model = updated.model;
            }
        }
    } catch {
    }
}

async function sendPromptToCurrentChat(text) {
    if (state.isStreaming || !state.currentChat) return;
    if (!text || !text.trim()) return;

    const prompt = text.trim();
    setEmptyChatLayout(false);
    const selectedModel = getSelectedModel();
    await persistModelForCurrentContext(selectedModel);
    state.messagesAutoScroll = true;
    const isPrivate = Boolean(state.currentChat.chat?.private);
    const chatId = state.currentChat.chat.id;
    const privateSession = isPrivate ? state.currentChat : null;

    const streamController = new AbortController();
    state.isStreaming = true;
    state.activeStreamController = streamController;
    state.activeStreamChatId = chatId;
    state.activeStreamPrivate = isPrivate;
    setSendButtonStreaming(true);
    appendMessage('user', prompt);
    setTyping(true);

    if (isPrivate) {
        privateSession.messages.push({role: 'user', content: prompt, createdAt: new Date().toISOString()});
    }

    const assistantEl = createStreamingAssistantMessage();
    let assistantText = '';

    try {
        const endpoint = isPrivate
            ? '/api/private/messages/stream'
            : `/api/chats/${chatId}/messages/stream`;

        const payload = {
            message: prompt,
            model: selectedModel,
            temperature: 0.7
        };

        if (isPrivate) {
            payload.history = privateSession.messages
                .filter(msg => msg.role === 'user' || msg.role === 'assistant')
                .map(msg => ({role: msg.role, content: msg.content}));
            payload.history.pop();
        }

        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload),
            signal: streamController.signal
        });

        if (response.status === 401 || response.status === 403) {
            redirectToLoginOnExpiredSession('Сессия завершена. Войдите снова.');
            const error = new Error('AUTH_EXPIRED');
            error.authExpired = true;
            throw error;
        }

        if (!response.ok || !response.body) {
            throw new Error(`HTTP ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let receivedDone = false;

        while (true) {
            const {done, value} = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, {stream: true});
            const events = buffer.split('\n\n');
            buffer = events.pop() || '';

            for (const rawEvent of events) {
                const lines = rawEvent.split('\n');
                for (const line of lines) {
                    const trimmed = line.trim();
                    if (!trimmed.startsWith('data:')) continue;
                    const payload = trimmed.substring(5).trim();
                    if (!payload) continue;

                    let data;
                    try {
                        data = JSON.parse(payload);
                    } catch {
                        continue;
                    }

                    if (data.type === 'chunk') {
                        assistantText += data.content || '';
                        updateStreamingAssistantMessage(assistantEl, assistantText);
                    } else if (data.type === 'error') {
                        assistantText += `\n\n❌ ${data.error || 'Ошибка генерации'}`;
                        updateStreamingAssistantMessage(assistantEl, assistantText);
                    } else if (data.type === 'done') {
                        receivedDone = true;
                    }
                }
            }
        }

        if (!receivedDone) {
            updateStreamingAssistantMessage(assistantEl, assistantText || '');
        }

        if (isPrivate) {
            privateSession.messages.push({role: 'assistant', content: assistantText, createdAt: new Date().toISOString()});
            if (state.currentChat === privateSession) {
                renderMessages(privateSession.messages, {
                    forceBottom: state.messagesAutoScroll,
                    preserveScroll: !state.messagesAutoScroll
                });
            }
        } else {
            const keepChatId = state.currentChat?.chat?.private ? null : state.currentChat?.chat?.id;
            await refreshData(keepChatId, {
                forceBottom: state.messagesAutoScroll,
                preserveScroll: !state.messagesAutoScroll
            });
        }
    } catch (error) {
        if (error.name === 'AbortError') {
            updateStreamingAssistantMessage(assistantEl, assistantText || '⏹ Остановлено');
        } else if (error.authExpired) {
            updateStreamingAssistantMessage(assistantEl, assistantText || '');
        } else {
            updateStreamingAssistantMessage(assistantEl, `❌ Ошибка: ${error.message}`);
        }
    } finally {
        setTyping(false);
        state.isStreaming = false;
        state.activeStreamController = null;
        state.activeStreamChatId = null;
        state.activeStreamPrivate = false;
        setSendButtonStreaming(false);
    }
}

async function sendMessage(event) {
    event.preventDefault();
    if (state.isStreaming) {
        await stopCurrentStreaming();
        return;
    }

    try {
        const hasActiveChat = await ensureActiveChat();
        if (!hasActiveChat) return;
    } catch {
        return;
    }

    const input = document.getElementById('messageInput');
    const text = input.value.trim();
    if (!text) return;
    input.value = '';

    try {
        await sendPromptToCurrentChat(text);
    } catch (error) {
        input.value = text;
    }
}

async function openExpandedInputDialog() {
    const input = document.getElementById('messageInput');
    if (!input) return;
    const composeResult = await askComposeInput(input.value || '');
    if (composeResult == null) {
        return;
    }
    input.value = composeResult.value || '';
    if (composeResult.selectedModel) {
        await persistModelForCurrentContext(composeResult.selectedModel);
    }
    input.focus();
    input.selectionStart = input.value.length;
    input.selectionEnd = input.value.length;
    if (!state.isStreaming && input.value.trim()) {
        document.getElementById('sendForm').requestSubmit();
    }
}

async function init() {
    authRedirectInProgress = false;
    applyTheme(state.theme);
    applyFontSize(state.fontSize);
    applyMenuFontSize(state.menuFontSize);
    applySidebarWidth(state.sidebarWidth);
    applyAnswerNavSide(state.answerNavSide);
    updatePrintDomainLink();

    document.getElementById('themeToggle').addEventListener('change', (e) => {
        applyTheme(e.target.checked ? 'dark' : 'light');
    });
    document.getElementById('fontSizeInput').addEventListener('input', (e) => applyFontSize(e.target.value));
    document.getElementById('menuFontSizeInput').addEventListener('input', (e) => applyMenuFontSize(e.target.value));
    document.getElementById('sidebarWidthInput').addEventListener('input', (e) => applySidebarWidth(e.target.value));
    document.getElementById('answerNavSideInput').addEventListener('change', (e) => applyAnswerNavSide(e.target.value));
    document.getElementById('modelSelect').addEventListener('change', (e) => persistModelForCurrentContext(e.target.value));
    document.getElementById('modelSelect').addEventListener('input', (e) => persistModelForCurrentContext(e.target.value));
    document.getElementById('composeModelSelect').addEventListener('change', (e) => persistModelForCurrentContext(e.target.value));
    document.getElementById('composeModelSelect').addEventListener('input', (e) => persistModelForCurrentContext(e.target.value));
    document.getElementById('refreshModelsBtn').addEventListener('click', refreshModels);
    document.getElementById('composeRefreshModelsBtn').addEventListener('click', refreshModels);
    document.getElementById('loginTab').addEventListener('click', () => setMode('login'));
    document.getElementById('registerTab').addEventListener('click', () => setMode('register'));
    document.getElementById('authForm').addEventListener('submit', submitAuth);
    document.getElementById('logoutBtn').addEventListener('click', logout);
    document.getElementById('accountButton').addEventListener('click', toggleAccountMenu);
    document.getElementById('profileBtn').addEventListener('click', async () => {
        closeAccountMenu();
        try {
            await openProfilePage();
        } catch (error) {
            setWorkspaceView('profile');
            setInlineStatus('profileStatus', error.message, true);
        }
    });
    document.getElementById('settingsBtn').addEventListener('click', async () => {
        closeAccountMenu();
        try {
            await openSettingsPage();
        } catch (error) {
            setWorkspaceView('settings');
            setInlineStatus('settingsStatus', error.message, true);
        }
    });
    document.getElementById('statsBtn').addEventListener('click', async () => {
        closeAccountMenu();
        try {
            await openStatsPage();
        } catch (error) {
            setWorkspaceView('stats');
            setInlineStatus('statsStatus', error.message, true);
        }
    });
    document.getElementById('profileForm').addEventListener('submit', saveProfile);
    document.getElementById('passwordForm').addEventListener('submit', savePassword);
    document.getElementById('settingsForm').addEventListener('submit', saveSettings);
    document.addEventListener('click', (event) => {
        const menu = document.getElementById('accountMenu');
        const button = document.getElementById('accountButton');
        if (!menu || !button) return;
        if (menu.classList.contains('hidden')) return;
        if (!menu.contains(event.target) && !button.contains(event.target)) {
            closeAccountMenu();
        }
    });

    document.getElementById('addGroupBtn').addEventListener('click', (event) => createGroup(event.currentTarget));
    document.getElementById('newChatBtn').addEventListener('click', createChatFromHeader);
    document.getElementById('privateChatBtn').addEventListener('click', openPrivateChat);
    document.getElementById('sendForm').addEventListener('submit', sendMessage);
    document.getElementById('expandInputBtn').addEventListener('click', openExpandedInputDialog);
    document.getElementById('chatMessages').addEventListener('scroll', updateMessagesAutoScrollState);
    document.getElementById('prevAnswerBtn').addEventListener('click', () => scrollToAssistantAnswer(-1));
    document.getElementById('nextAnswerBtn').addEventListener('click', () => scrollToAssistantAnswer(1));
    document.getElementById('adaptiveScrollBtn').addEventListener('click', scrollChatAdaptive);
    window.addEventListener('resize', () => {
        updateAnswerNavPosition();
        updateAdaptiveScrollNavPosition();
        updateAdaptiveScrollNavState();
    });
    window.addEventListener('beforeprint', updatePrintDomainLink);
    document.getElementById('messageInput').addEventListener('keydown', (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            document.getElementById('sendForm').requestSubmit();
        }
    });

    setMode('login');
    setWorkspaceView('chat');
    setSendButtonStreaming(false);
    const authHint = localStorage.getItem('authHint') === '1';
    if (authHint) {
        showApp();
    }

    try {
        const me = await api('/api/auth/me');
        if (me.success) {
            localStorage.setItem('authHint', '1');
            showApp();
            await loadInitialData();
        } else {
            localStorage.removeItem('authHint');
            showAuth();
        }
    } catch {
        localStorage.removeItem('authHint');
        showAuth();
    }
}

document.addEventListener('DOMContentLoaded', init);
