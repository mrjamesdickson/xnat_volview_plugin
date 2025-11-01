package org.nrg.xnat.volview.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.volview.config.VolViewSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@XapiRestController
@Api("VolView Configuration API")
public class VolViewConfigController extends AbstractXapiRestController {
    private static final Logger log = LoggerFactory.getLogger(VolViewConfigController.class);
    private final VolViewSettings settings;

    @Autowired
    public VolViewConfigController(final VolViewSettings settings,
                                   final UserManagementServiceI userManagementService,
                                   final RoleHolder roleHolder) {
        super(userManagementService, roleHolder);
        this.settings = settings;
    }

    @XapiRequestMapping(value = "/volview/config/projects/{projectId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get VolView configuration for a project", response = Map.class)
    public Map<String, Object> getProjectConfig(@PathVariable final String projectId,
                                                final HttpServletRequest request) {
        log.debug("VolView project config requested for projectId={} from {}", projectId, request.getRemoteAddr());
        final String baseUrl = buildBaseUrl(request);
        final String pathPrefix = buildPathPrefix(request);
        final String dicomwebRoot = joinPaths(pathPrefix, settings.getProjectDicomwebPath(projectId));
        final String viewerEntryPoint = resolveUrl(settings.getViewerEntryPoint(), baseUrl);
        final String shellUrl = resolveUrl(settings.getShellPath(), baseUrl);

        log.debug("Computed VolView config for projectId={} baseUrl={} dicomwebRoot={} viewerEntryPoint={} shellUrl={}",
                projectId, baseUrl, dicomwebRoot, viewerEntryPoint, shellUrl);

        final Map<String, String> dicomweb = new LinkedHashMap<>();
        dicomweb.put("root", dicomwebRoot);
        dicomweb.put("studies", joinPaths(dicomwebRoot, "studies"));
        dicomweb.put("series", joinPaths(dicomwebRoot, "studies/{studyInstanceUID}/series"));
        dicomweb.put("instances", joinPaths(dicomwebRoot, "studies/{studyInstanceUID}/series/{seriesInstanceUID}/instances"));

        final Map<String, Object> viewer = new LinkedHashMap<>();
        viewer.put("shellUrl", shellUrl);
        viewer.put("entryPoint", viewerEntryPoint);
        viewer.put("launchUrlTemplate", viewerEntryPoint + "?dicomweb=%s");

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("projectId", projectId);
        response.put("serverName", settings.getServerName());
        response.put("dicomweb", dicomweb);
        response.put("viewer", viewer);

        return response;
    }

    @XapiRequestMapping(value = "/volview/config/projects/{projectId}/sessions/{sessionId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get VolView configuration for a session", response = Map.class)
    public ResponseEntity<Map<String, Object>> getSessionConfig(@PathVariable final String projectId,
                                                                @PathVariable final String sessionId,
                                                                final HttpServletRequest request) {
        log.debug("VolView session config requested for projectId={} sessionId={} from {}", projectId, sessionId, request.getRemoteAddr());
        final UserI user = XDAT.getUserDetails();
        if (user == null) {
            log.warn("Rejected VolView session config request for projectId={} sessionId={} due to missing user session", projectId, sessionId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        final XnatImagesessiondata session = XnatImagesessiondata.getXnatImagesessiondatasById(sessionId, user, false);
        if (session == null) {
            log.warn("VolView session config request for projectId={} sessionId={} not found or not visible to user {}", projectId, sessionId, user.getUsername());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        final String sessionProject = session.getProject();
        final boolean matchesProject = (sessionProject != null && sessionProject.equalsIgnoreCase(projectId))
                                       || session.hasProject(projectId);
        if (!matchesProject) {
            log.warn("VolView session config request for projectId={} sessionId={} denied; session belongs to project {}", projectId, sessionId, sessionProject);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        final String baseUrl = buildBaseUrl(request);
        final String pathPrefix = buildPathPrefix(request);
        final String dicomwebRoot = joinPaths(pathPrefix, settings.getProjectDicomwebPath(projectId));
        final String viewerEntryPoint = resolveUrl(settings.getViewerEntryPoint(), baseUrl);

        final String studyInstanceUid = session.getUid();
        log.debug("VolView session config computed for projectId={} sessionId={} studyUID={} viewerEntryPoint={} dicomwebStudyUrl={}",
                projectId, sessionId, studyInstanceUid, viewerEntryPoint,
                studyInstanceUid == null ? null : joinPaths(dicomwebRoot, "studies/" + studyInstanceUid));
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("sessionId", sessionId);
        payload.put("label", session.getLabel());
        payload.put("studyInstanceUID", studyInstanceUid);
        payload.put("dicomwebStudyUrl", studyInstanceUid == null ? null : joinPaths(dicomwebRoot, "studies/" + studyInstanceUid));
        payload.put("viewerEntryPoint", viewerEntryPoint);

        return ResponseEntity.ok(payload);
    }

    private static String buildBaseUrl(final HttpServletRequest request) {
        String scheme = firstForwardedValue(request, "X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) {
            scheme = request.getScheme();
        }

        String host = request.getServerName();
        int port = request.getServerPort();

        final String forwardedHost = firstForwardedValue(request, "X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            final HostPort hostPort = parseHostPort(forwardedHost);
            if (hostPort.host != null && !hostPort.host.isEmpty()) {
                host = hostPort.host;
            }
            if (hostPort.port != null) {
                port = hostPort.port;
            }
        }

        final String forwardedPort = firstForwardedValue(request, "X-Forwarded-Port");
        if (forwardedPort != null && !forwardedPort.isEmpty()) {
            port = safeParseInt(forwardedPort, port);
        }

        final String contextPath = buildPathPrefix(request);

        final boolean isDefaultPort = (scheme.equalsIgnoreCase("http") && port == 80)
                                      || (scheme.equalsIgnoreCase("https") && port == 443);
        final String portSection = (isDefaultPort || port <= 0) ? "" : ":" + port;

        return scheme + "://" + host + portSection + contextPath;
    }

    private static String buildPathPrefix(final HttpServletRequest request) {
        final String forwardedPrefix = firstForwardedValue(request, "X-Forwarded-Prefix");
        final String contextPathRaw = request.getContextPath() == null ? "" : request.getContextPath();
        return normalizePrefix(forwardedPrefix, contextPathRaw);
    }

    private static String firstForwardedValue(final HttpServletRequest request, final String headerName) {
        final String raw = request.getHeader(headerName);
        if (raw == null) {
            return null;
        }
        final int commaIndex = raw.indexOf(',');
        return (commaIndex >= 0 ? raw.substring(0, commaIndex) : raw).trim();
    }

    private static int safeParseInt(final String value, final int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Integer safeParseIntNullable(final String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizePrefix(final String forwardedPrefix, final String contextPath) {
        final StringBuilder builder = new StringBuilder();

        if (forwardedPrefix != null && !forwardedPrefix.isEmpty()) {
            String prefix = forwardedPrefix.trim();
            if (!prefix.startsWith("/")) {
                prefix = "/" + prefix;
            }
            if (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            builder.append(prefix);
        }

        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
            if (builder.length() == 0) {
                builder.append(contextPath);
            } else {
                builder.append(contextPath.startsWith("/") ? contextPath : "/" + contextPath);
            }
        }

        return builder.length() == 0 ? "" : builder.toString();
    }

    private static String joinPaths(final String base, final String path) {
        final String safeBase = base == null ? "" : base.trim();
        final String safePath = path == null ? "" : path.trim();

        if (safeBase.isEmpty()) {
            if (safePath.startsWith("/")) {
                return safePath;
            }
            return safePath.isEmpty() ? "" : "/" + safePath;
        }

        if (safePath.isEmpty()) {
            return safeBase;
        }

        final boolean baseEndsWithSlash = safeBase.endsWith("/");
        final boolean pathStartsWithSlash = safePath.startsWith("/");

        if (baseEndsWithSlash && pathStartsWithSlash) {
            return safeBase + safePath.substring(1);
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return safeBase + "/" + safePath;
        }
        return safeBase + safePath;
    }

    private static HostPort parseHostPort(final String value) {
        String host = value;
        Integer port = null;

        if (value.startsWith("[")) {
            final int closing = value.indexOf(']');
            if (closing > 0) {
                host = value.substring(0, closing + 1);
                if (closing + 1 < value.length() && value.charAt(closing + 1) == ':') {
                    final String portCandidate = value.substring(closing + 2);
                    port = safeParseIntNullable(portCandidate);
                }
            }
        } else {
            final int colonIndex = value.lastIndexOf(':');
            if (colonIndex > 0 && colonIndex == value.indexOf(':')) {
                host = value.substring(0, colonIndex);
                port = safeParseIntNullable(value.substring(colonIndex + 1));
            }
        }

        if (port != null && port <= 0) {
            port = null;
        }

        return new HostPort(host, port);
    }

    private static final class HostPort {
        private final String host;
        private final Integer port;

        private HostPort(final String host, final Integer port) {
            this.host = host;
            this.port = port;
        }
    }

    private static String resolveUrl(final String path, final String baseUrl) {
        if (path == null || path.trim().isEmpty()) {
            return baseUrl;
        }
        final String trimmed = path.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return baseUrl + trimmed;
        }
        return baseUrl + "/" + trimmed;
    }
}
