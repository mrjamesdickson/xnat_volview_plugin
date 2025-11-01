package org.nrg.xnat.volview.rest;

import org.nrg.xnat.volview.config.VolViewSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping({"/xapi/volview/app/projects", "/volview/app/projects", "/app/volview/projects"})
public class VolViewPageController {
    private static final Logger log = LoggerFactory.getLogger(VolViewPageController.class);
    private final VolViewSettings settings;

    public VolViewPageController(final VolViewSettings settings) {
        this.settings = settings;
        log.info("VolViewPageController initialized - Shell page available at /xapi/volview/app/projects/{projectId}");
    }

    @GetMapping(value = {"/{projectId}", "/{projectId}/**"})
    public ResponseEntity<Resource> serveShell(@PathVariable final String projectId) {
        return buildShellResponse(projectId);
    }

    private ResponseEntity<Resource> buildShellResponse(final String projectId) {
        final ClassPathResource shell = resolveShellResource();
        if (!shell.exists()) {
            log.error("VolView shell resource {} not found on classpath", shell.getPath());
            return ResponseEntity.internalServerError().build();
        }
        log.debug("Serving VolView shell for project {}", projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(shell);
    }

    private ClassPathResource resolveShellResource() {
        final String shellPath = settings.getShellPath();
        final String resourcePath;
        if (shellPath.startsWith("/")) {
            resourcePath = "META-INF/resources" + shellPath;
        } else {
            resourcePath = "META-INF/resources/" + shellPath;
        }
        return new ClassPathResource(resourcePath);
    }
}
