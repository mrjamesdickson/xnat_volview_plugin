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
        final String dicomwebRoot = baseUrl + settings.getProjectDicomwebPath(projectId);
        final String viewerEntryPoint = resolveUrl(settings.getViewerEntryPoint(), baseUrl);
        final String shellUrl = resolveUrl(settings.getShellPath(), baseUrl);

        log.debug("Computed VolView config for projectId={} baseUrl={} dicomwebRoot={} viewerEntryPoint={} shellUrl={}",
                projectId, baseUrl, dicomwebRoot, viewerEntryPoint, shellUrl);

        final Map<String, String> dicomweb = new LinkedHashMap<>();
        dicomweb.put("root", dicomwebRoot);
        dicomweb.put("studies", dicomwebRoot + "/studies");
        dicomweb.put("series", dicomwebRoot + "/studies/{studyInstanceUID}/series");
        dicomweb.put("instances", dicomwebRoot + "/studies/{studyInstanceUID}/series/{seriesInstanceUID}/instances");

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
        final String dicomwebRoot = baseUrl + settings.getProjectDicomwebPath(projectId);
        final String viewerEntryPoint = resolveUrl(settings.getViewerEntryPoint(), baseUrl);

        final String studyInstanceUid = session.getUid();
        log.debug("VolView session config computed for projectId={} sessionId={} studyUID={} viewerEntryPoint={} dicomwebStudyUrl={}",
                projectId, sessionId, studyInstanceUid, viewerEntryPoint,
                studyInstanceUid == null ? null : dicomwebRoot + "/studies/" + studyInstanceUid);
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("sessionId", sessionId);
        payload.put("label", session.getLabel());
        payload.put("studyInstanceUID", studyInstanceUid);
        payload.put("dicomwebStudyUrl", studyInstanceUid == null ? null : dicomwebRoot + "/studies/" + studyInstanceUid);
        payload.put("viewerEntryPoint", viewerEntryPoint);

        return ResponseEntity.ok(payload);
    }

    private static String buildBaseUrl(final HttpServletRequest request) {
        final String scheme = request.getScheme();
        final String server = request.getServerName();
        final int port = request.getServerPort();
        final String contextPath = request.getContextPath() == null ? "" : request.getContextPath();

        final boolean isDefaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        final String portSection = isDefaultPort ? "" : ":" + port;

        return scheme + "://" + server + portSection + contextPath;
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
