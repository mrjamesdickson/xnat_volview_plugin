const state = {
    projectId: null,
    config: null,
    studies: [],
    seriesByStudy: new Map(),
    sessionId: null,
    preselectStudyUid: null,
    autoLaunch: false,
    sessionContext: null,
    queryParams: new URLSearchParams(window.location.search)
};

const elements = {
    projectName: document.getElementById('project-name'),
    dicomwebRoot: document.getElementById('dicomweb-root'),
    viewerEntryPoint: document.getElementById('viewer-entry-point'),
    studySelect: document.getElementById('study-select'),
    seriesSelect: document.getElementById('series-select'),
    openStudy: document.getElementById('open-study'),
    openSeries: document.getElementById('open-series'),
    status: document.getElementById('status'),
    viewerFrame: document.getElementById('viewer-frame')
};

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        init().catch((error) => {
            console.error(error);
            setStatus(`Unexpected error: ${error.message}`, true);
        });
    });
} else {
    init().catch((error) => {
        console.error(error);
        setStatus(`Unexpected error: ${error.message}`, true);
    });
}

async function init() {
    state.projectId = resolveProjectId();
    if (!state.projectId) {
        setStatus('Unable to determine project identifier from URL. Expected /xapi/volview/app/projects/{projectId}.', true);
        disableControls();
        return;
    }
    elements.projectName.textContent = state.projectId;

    state.sessionId = resolveSessionId();
    const preselectParam = state.queryParams.get('study') || state.queryParams.get('studyInstanceUID');
    if (preselectParam) {
        state.preselectStudyUid = preselectParam;
        state.autoLaunch = true;
    }

    try {
        state.config = await fetchConfig(state.projectId);
        elements.dicomwebRoot.textContent = state.config.dicomweb.root;
        elements.viewerEntryPoint.textContent = state.config.viewer.entryPoint;
    } catch (error) {
        console.error(error);
        setStatus(`Failed to load VolView configuration: ${error.message}`, true);
        disableControls();
        return;
    }

    if (state.sessionId) {
        try {
            await loadSessionContext(state.projectId, state.sessionId);
        } catch (error) {
            console.error(error);
            setStatus(`Failed to resolve session metadata: ${error.message}`);
        }
    }

    elements.studySelect.addEventListener('change', async (event) => {
        const studyUid = event.target.value;
        if (!studyUid) {
            elements.seriesSelect.innerHTML = `<option value="">Select a study first</option>`;
            elements.seriesSelect.disabled = true;
            elements.openStudy.disabled = true;
            elements.openSeries.disabled = true;
            return;
        }
        elements.openStudy.disabled = false;
        await ensureSeriesLoaded(studyUid);
    });

    elements.seriesSelect.addEventListener('change', () => {
        const seriesUid = elements.seriesSelect.value;
        elements.openSeries.disabled = !seriesUid;
    });

    elements.openStudy.addEventListener('click', () => {
        const studyUid = elements.studySelect.value;
        if (!studyUid) {
            return;
        }
        const url = buildViewerUrl(studyUid, null);
        setViewerSource(url);
    });

    elements.openSeries.addEventListener('click', () => {
        const studyUid = elements.studySelect.value;
        const seriesUid = elements.seriesSelect.value;
        if (!studyUid || !seriesUid) {
            return;
        }
        const url = buildViewerUrl(studyUid, seriesUid);
        setViewerSource(url);
    });

    await loadStudies();
}

function resolveProjectId() {
    const pathSegments = window.location.pathname.split('/').filter(Boolean);
    const volviewIndex = pathSegments.lastIndexOf('volview');
    if (volviewIndex !== -1) {
        const nextSegment = pathSegments[volviewIndex + 1];
        if (nextSegment === 'projects' && pathSegments.length > volviewIndex + 2) {
            return decodeURIComponent(pathSegments[volviewIndex + 2]);
        }
        if (nextSegment === 'app' && pathSegments[volviewIndex + 2] === 'projects' && pathSegments.length > volviewIndex + 3) {
            return decodeURIComponent(pathSegments[volviewIndex + 3]);
        }
    }
    const params = new URLSearchParams(window.location.search);
    return params.get('project');
}

function resolveSessionId() {
    return state.queryParams.get('session') || state.queryParams.get('sessionId');
}

function buildApiUrl(path) {
    const pathname = window.location.pathname;
    const xapiIndex = pathname.indexOf('/xapi/volview/app/');
    if (xapiIndex >= 0) {
        const contextPath = pathname.substring(0, xapiIndex);
        return contextPath + path;
    }
    const newPathIndex = pathname.indexOf('/volview/app/');
    if (newPathIndex >= 0) {
        const contextPath = pathname.substring(0, newPathIndex);
        return contextPath + path;
    }
    const legacyIndex = pathname.indexOf('/app/volview/');
    if (legacyIndex >= 0) {
        const contextPath = pathname.substring(0, legacyIndex);
        return contextPath + path;
    }
    return path;
}

async function fetchConfig(projectId) {
    const configUrl = buildApiUrl(`/xapi/volview/config/projects/${encodeURIComponent(projectId)}`);
    const response = await fetch(configUrl, {
        credentials: 'include',
        headers: {
            'Accept': 'application/json'
        }
    });
    if (!response.ok) {
        throw new Error(`Config request failed with status ${response.status}`);
    }
    return response.json();
}

async function fetchSessionConfig(projectId, sessionId) {
    const configUrl = buildApiUrl(`/xapi/volview/config/projects/${encodeURIComponent(projectId)}/sessions/${encodeURIComponent(sessionId)}`);
    const response = await fetch(configUrl, {
        credentials: 'include',
        headers: {
            'Accept': 'application/json'
        }
    });
    if (!response.ok) {
        throw new Error(`Session config request failed with status ${response.status}`);
    }
    return response.json();
}

async function loadSessionContext(projectId, sessionId) {
    const info = await fetchSessionConfig(projectId, sessionId);
    state.sessionContext = info;
    if (info.studyInstanceUID) {
        if (!state.preselectStudyUid) {
            state.preselectStudyUid = info.studyInstanceUID;
        }
        state.autoLaunch = state.autoLaunch || Boolean(info.studyInstanceUID);
    } else {
        setStatus('Session metadata does not include a StudyInstanceUID. Please choose a study manually.');
    }
}

async function loadStudies() {
    const listUrl = new URL(state.config.dicomweb.studies, window.location.origin);
    listUrl.searchParams.set('limit', '200');
    listUrl.searchParams.append('includefield', '00080020'); // StudyDate
    listUrl.searchParams.append('includefield', '00081030'); // StudyDescription
    listUrl.searchParams.append('includefield', '00100010'); // PatientName
    listUrl.searchParams.append('includefield', '0020000D'); // StudyInstanceUID

    setStatus('Loading studies…');

    const response = await fetch(listUrl.toString(), {
        credentials: 'include',
        headers: {
            'Accept': 'application/dicom+json'
        }
    });

    if (!response.ok) {
        throw new Error(`Study query failed with status ${response.status}`);
    }

    const data = await response.json();
    state.studies = Array.isArray(data) ? data : [];
    renderStudyOptions();

    if (state.studies.length) {
        setStatus(`Loaded ${state.studies.length} studies.`);
        elements.studySelect.disabled = false;
        elements.openStudy.disabled = false;
    } else {
        setStatus('No DICOM studies were returned for this project.', true);
        elements.studySelect.disabled = true;
        elements.openStudy.disabled = true;
    }

    if (state.preselectStudyUid) {
        const selected = setStudySelection(state.preselectStudyUid, state.autoLaunch);
        if (!selected) {
            setStatus(`Study ${state.preselectStudyUid} is not available via the DICOMweb proxy.`, true);
        }
        state.preselectStudyUid = null;
        state.autoLaunch = false;
    }
}

async function ensureSeriesLoaded(studyUid) {
    if (state.seriesByStudy.has(studyUid)) {
        renderSeriesOptions(state.seriesByStudy.get(studyUid));
        return;
    }

    const seriesTemplate = state.config.dicomweb.series.replace('{studyInstanceUID}', encodeURIComponent(studyUid));
    const listUrl = new URL(seriesTemplate, window.location.origin);
    listUrl.searchParams.set('limit', '400');
    listUrl.searchParams.append('includefield', '0008103E'); // SeriesDescription
    listUrl.searchParams.append('includefield', '0020000E'); // SeriesInstanceUID

    setStatus('Loading series…');

    const response = await fetch(listUrl.toString(), {
        credentials: 'include',
        headers: {
            'Accept': 'application/dicom+json'
        }
    });

    if (!response.ok) {
        setStatus(`Series query failed: HTTP ${response.status}`, true);
        elements.seriesSelect.disabled = true;
        elements.openSeries.disabled = true;
        return;
    }

    const data = await response.json();
    const series = Array.isArray(data) ? data : [];
    state.seriesByStudy.set(studyUid, series);
    renderSeriesOptions(series);
    setStatus(`Loaded ${series.length} series.`);
}

function renderStudyOptions(studies = state.studies) {
    const options = [];

    if (!studies.length) {
        options.push(`<option value="">No studies available</option>`);
    } else {
        options.push(`<option value="">Choose a study…</option>`);
    }

    for (const study of studies) {
        const studyUid = getDicomValue(study, '0020000D');
        if (!studyUid) {
            continue;
        }
        const studyDescription = getDicomValue(study, '00081030') || 'Unnamed study';
        const studyDate = getDicomValue(study, '00080020');
        const patientName = getDicomValue(study, '00100010');
        const labelParts = [];
        labelParts.push(studyDescription);
        if (studyDate) {
            labelParts.push(formatDicomDate(studyDate));
        }
        if (patientName) {
            labelParts.push(patientName);
        }
        options.push(`<option value="${studyUid}">${escapeHtml(labelParts.join(' · '))}</option>`);
    }

    elements.studySelect.innerHTML = options.join('');
}

function renderSeriesOptions(series) {
    if (!series || !series.length) {
        elements.seriesSelect.innerHTML = `<option value="">No series available</option>`;
        elements.seriesSelect.disabled = true;
        elements.openSeries.disabled = true;
        return;
    }

    const options = [`<option value="">Open entire study…</option>`];
    for (const entry of series) {
        const seriesUid = getDicomValue(entry, '0020000E');
        if (!seriesUid) {
            continue;
        }
        const description = getDicomValue(entry, '0008103E') || 'Unnamed series';
        options.push(`<option value="${seriesUid}">${escapeHtml(description)}</option>`);
    }
    elements.seriesSelect.innerHTML = options.join('');
    elements.seriesSelect.disabled = false;
    elements.openSeries.disabled = true;
}

function setStudySelection(studyUid, autoLaunch = false) {
    const option = Array.from(elements.studySelect.options).find((opt) => opt.value === studyUid);
    if (!option) {
        return false;
    }
    elements.studySelect.value = studyUid;
    elements.openStudy.disabled = false;
    elements.studySelect.dispatchEvent(new Event('change'));
    if (autoLaunch) {
        const url = buildViewerUrl(studyUid, null);
        setViewerSource(url);
    }
    return true;
}

function buildViewerUrl(studyUid, seriesUid) {
    const dicomwebRoot = state.config.dicomweb.root;
    let target = `${dicomwebRoot}/studies/${encodeURIComponent(studyUid)}`;
    if (seriesUid) {
        target += `/series/${encodeURIComponent(seriesUid)}`;
    }

    const entryPoint = state.config.viewer.entryPoint;
    const encodedTarget = encodeURIComponent(target);

    if (entryPoint.includes('{dicomweb}')) {
        return entryPoint.replace('{dicomweb}', encodedTarget);
    }
    const separator = entryPoint.includes('?') ? '&' : '?';
    return `${entryPoint}${separator}dicomweb=${encodedTarget}`;
}

function setViewerSource(url) {
    elements.viewerFrame.src = url;
    setStatus(`Launching VolView…`);
}

function disableControls() {
    elements.studySelect.disabled = true;
    elements.seriesSelect.disabled = true;
    elements.openStudy.disabled = true;
    elements.openSeries.disabled = true;
}

function setStatus(message, isError = false) {
    elements.status.textContent = message ?? '';
    elements.status.style.color = isError ? '#ff8b8b' : '#ffb347';
}

function getDicomValue(item, tag) {
    if (!item || !item[tag]) {
        return null;
    }
    const value = item[tag].Value;
    if (!Array.isArray(value) || value.length === 0) {
        return null;
    }
    const first = value[0];
    if (first == null) {
        return null;
    }
    if (typeof first === 'object') {
        if (first.Alphabetic) {
            return first.Alphabetic;
        }
        if (first.Text) {
            return first.Text;
        }
        return Object.values(first)
            .filter((entry) => typeof entry === 'string')
            .join(' ');
    }
    return String(first);
}

function formatDicomDate(raw) {
    if (!raw || raw.length !== 8) {
        return raw;
    }
    return `${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6)}`;
}

function escapeHtml(value) {
    return value.replace(/[&<>"']/g, (ch) => {
        switch (ch) {
            case '&':
                return '&amp;';
            case '<':
                return '&lt;';
            case '>':
                return '&gt;';
            case '"':
                return '&quot;';
            case '\'':
                return '&#39;';
            default:
                return ch;
        }
    });
}
