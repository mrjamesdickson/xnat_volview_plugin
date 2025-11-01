package org.nrg.xnat.volview.config;

import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VolViewSettings {
    private static final String PROP_VIEWER_ENTRY_POINT = "volview.viewer.entry-point";
    private static final String PROP_DICOMWEB_BASE = "volview.dicomweb.base-path";
    private static final String PROP_SHELL_PATH = "volview.shell.path";
    private static final String PROP_SERVER_NAME = "volview.server-name";

    private final SiteConfigPreferences siteConfigPreferences;
    private final String defaultDicomwebBasePath;
    private final String defaultViewerEntryPoint;
    private final String defaultShellPath;
    private final String defaultServerName;

    public VolViewSettings(
            final SiteConfigPreferences siteConfigPreferences,
            @Value("${volview.dicomweb.base-path:/xapi/dicomweb/projects}") final String dicomwebBasePath,
            @Value("${volview.viewer.entry-point:/volview/app/index.html}") final String viewerEntryPoint,
            @Value("${volview.shell.path:/plugin-resources/xnat-volview/index.html}") final String shellPath,
            @Value("${volview.server-name:XNAT DICOMweb}") final String serverName
    ) {
        this.siteConfigPreferences = siteConfigPreferences;
        this.defaultDicomwebBasePath = normalizePath(dicomwebBasePath);
        this.defaultViewerEntryPoint = viewerEntryPoint;
        this.defaultShellPath = shellPath;
        this.defaultServerName = serverName;
    }

    public String getDicomwebBasePath() {
        final String value = getStringProperty(PROP_DICOMWEB_BASE, defaultDicomwebBasePath);
        return normalizePath(value);
    }

    public String getViewerEntryPoint() {
        return getStringProperty(PROP_VIEWER_ENTRY_POINT, defaultViewerEntryPoint);
    }

    public String getShellPath() {
        return getStringProperty(PROP_SHELL_PATH, defaultShellPath);
    }

    public String getServerName() {
        return getStringProperty(PROP_SERVER_NAME, defaultServerName);
    }

    public String getProjectDicomwebPath(final String projectId) {
        return getDicomwebBasePath() + "/" + projectId;
    }

    private static String normalizePath(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getStringProperty(final String key, final String defaultValue) {
        if (siteConfigPreferences != null) {
            final Object stored = siteConfigPreferences.getProperty(key);
            if (stored instanceof String) {
                final String trimmed = ((String) stored).trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return defaultValue;
    }
}
