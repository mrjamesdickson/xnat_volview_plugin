(function () {
    function openVolView(event) {
        const newTab = event.button === 1 || event.metaKey || event.ctrlKey;
        const context = XNAT && XNAT.data && XNAT.data.context ? XNAT.data.context : {};
        const projectId = context.projectID || context.projectId;
        const sessionId = context.ID || context.id;

        if (!projectId || !sessionId) {
            XNAT.ui.banner.top(1, 'Unable to determine project or session id for VolView.');
            return;
        }

        const url = XNAT.url.rootUrl(`/xapi/volview/app/projects/${encodeURIComponent(projectId)}?session=${encodeURIComponent(sessionId)}`);
        if (newTab) {
            window.open(url, '_blank', 'noopener');
        } else {
            window.location.href = url;
        }
    }

    function handleMouseDown(event) {
        if (event.button === 2) {
            return;
        }
        event.preventDefault();
        openVolView(event);
    }

    function handleContextMenu(event) {
        event.preventDefault();
        openVolView({ button: 1, metaKey: true, ctrlKey: true });
    }

    $(document).on('mousedown', '#volviewViewer', handleMouseDown);
    $(document).on('contextmenu', '#volviewViewer', handleContextMenu);
})();
