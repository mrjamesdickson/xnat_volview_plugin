# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an XNAT plugin that embeds Kitware's VolView medical image viewer into XNAT, allowing users to visualize DICOM imaging studies directly within the XNAT interface. The plugin integrates with the companion `xnat_dicomweb_proxy` plugin to access DICOM data via DICOMweb (QIDO-RS/WADO-RS) endpoints.

**Key dependencies:**
- XNAT 1.9.5+ (built against parent BOM `org.nrg:parent:1.9.0`)
- Java 8
- Node.js 18+ (for building VolView assets)
- Git submodule: VolView (https://github.com/mrjamesdickson/VolView)
- Companion plugin: `xnat_dicomweb_proxy` (must be deployed to XNAT instance)

## Build Commands

### Initial Setup
```bash
# Initialize VolView submodule (first time only)
git submodule update --init --recursive

# Build VolView assets and copy to plugin resources
./build_volview_assets.sh
```

### Building the Plugin
```bash
# Build the plugin JAR
./gradlew jar

# Clean and build
./gradlew clean jar

# Run tests
./gradlew test

# Run all checks
./gradlew check
```

**Output:** `build/libs/xnat-volview-plugin-0.1.0.jar`

### Environment Requirements
If running in a restricted environment, set `GRADLE_USER_HOME`:
```bash
export GRADLE_USER_HOME=$(pwd)/.gradle-home
```

For Java 8:
```bash
export JAVA_HOME=/usr/local/opt/openjdk@8/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

### Gradle Credentials
XNAT dependencies require NRG Artifactory credentials in `~/.gradle/gradle.properties`:
```
nrgRepoUser=YOUR_USERNAME
nrgRepoKey=YOUR_API_KEY
```

## Architecture

### Backend (Java/Spring)

**Plugin Entry Point:**
- `VolViewPlugin.java` - Spring configuration annotated with `@XnatPlugin`, scans `org.nrg.xnat.volview` package

**REST Controllers:**
- `VolViewConfigController` (`/xapi/volview/*`) - Provides JSON configuration to the frontend shell:
  - `/config/projects/{projectId}` - Returns DICOMweb endpoints and viewer URLs for a project
  - `/config/projects/{projectId}/sessions/{sessionId}` - Returns session-specific config with Study Instance UID
- `VolViewPageController` (`/xapi/volview/app/*`) - Serves the shell HTML page for `/xapi/volview/app/projects/{projectId}`

**Settings:**
- `VolViewSettings.java` - Component that reads site configuration properties and provides defaults:
  - `volview.dicomweb.base-path` (default: `/xapi/dicomweb/projects`)
  - `volview.viewer.entry-point` (default: `/volview/app/index.html`)
  - `volview.shell.path` (default: `/plugin-resources/xnat-volview/index.html`)
  - `volview.server-name` (default: `XNAT DICOMweb`)

**URL Construction Pattern:**
All controllers build relative URLs dynamically using `HttpServletRequest` context (scheme, host, port, context path) so the plugin works regardless of XNAT's deployment URL.

### Frontend

**Shell UI** (`src/main/resources/META-INF/resources/plugin-resources/xnat-volview/`):
- `index.html` + `app.js` - Vanilla JS single-page application
- No build step required (ES modules)
- Queries `/xapi/volview/config` endpoints for DICOMweb URLs
- Issues QIDO-RS requests to list studies and series
- Launches VolView in iframe with `?dicomweb={encodedURL}` parameter

**VolView Application** (`src/main/resources/META-INF/resources/volview/app/`):
- Populated by `build_volview_assets.sh` from `frontend/VolView` submodule
- Vue/Vite production build with VTK.js and itk-wasm dependencies
- Served statically via Spring at `/volview/app/*`

**Session Action** (`src/main/resources/META-INF/resources/scripts/xnat/plugin/volview/viewerAction.js`):
- Adds "Open in VolView" action to imaging session pages
- Template: `src/main/resources/META-INF/resources/templates/screens/xnat_imageSessionData/actionsBox/ViewInVolView.vm`

### Configuration Integration

**Site Settings Panel:**
- Defined in `src/main/resources/META-INF/xnat/spawner/xnat-volview-plugin/site-settings.yaml`
- Creates admin UI form at **Administer → Plugin Settings → VolView Configuration**
- All properties stored in XNAT's site configuration and read via `SiteConfigPreferences`

## VolView Submodule Workflow

The `frontend/VolView` directory is a Git submodule pointing to a fork of Kitware's VolView.

**Update VolView to latest:**
```bash
cd frontend/VolView
git checkout main
git pull origin main
cd ../..
git add frontend/VolView
git commit -m "Update VolView submodule"
```

**Rebuild assets after update:**
```bash
./build_volview_assets.sh
```

**Switch to different fork/branch:**
Edit `.gitmodules`, then:
```bash
git submodule sync
git submodule update --init --recursive
```

## Deployment

Deploy to XNAT server (e.g., demo02):
```bash
scp build/libs/xnat-volview-plugin-0.1.0.jar user@server:/data/xnat/home/plugins/
ssh user@server sudo service tomcat8 restart
```

Verify logs for:
```
INFO  [org.nrg.xnat.volview.VolViewPlugin] - XNAT VolView Plugin configuration initialized
```

## Key Integration Points

**DICOMweb Proxy Dependency:**
This plugin does NOT handle DICOM data directly. It requires the `xnat_dicomweb_proxy` plugin to expose DICOMweb QIDO-RS and WADO-RS endpoints at `/xapi/dicomweb/projects/{projectId}/`.

**Authentication:**
All requests from the shell UI use `credentials: 'include'` to pass XNAT session cookies. The config controller validates user permissions via `XDAT.getUserDetails()`.

**Study/Series Metadata:**
The shell parses DICOM JSON responses following the DICOMweb standard (tag objects with `vr` and `Value` arrays).

**VolView Launch Mechanism:**
VolView supports a `dicomweb=` URL parameter. The shell constructs URLs like:
```
/volview/app/index.html?dicomweb=https://xnat.example.com/xapi/dicomweb/projects/DEMO/studies/1.2.840...
```

## Common Development Patterns

**Adding new configuration properties:**
1. Add `@Value` parameter to `VolViewSettings` constructor
2. Add getter method that reads from `SiteConfigPreferences`
3. Update `site-settings.yaml` to expose in admin UI
4. Update documentation in README and this file

**Extending REST endpoints:**
Controllers follow XNAT conventions:
- Use `@RestController` for JSON APIs (`/xapi/*`)
- Use `@Controller` for HTML pages (`/app/*`)
- Return `ResponseEntity` for explicit status codes
- Use `@PathVariable` for URL parameters
- Validate permissions using XNAT's `UserI` and data models

**Frontend modifications:**
The shell UI in `xnat-volview/app.js` is intentionally simple:
- No framework dependencies (easier to debug in XNAT context)
- All state in JavaScript closures
- DICOM tag parsing uses helper functions (`extractTagValue`, `extractTagString`)

## Testing

Tests use JUnit 5 (`jupiter`). The framework is configured but no test files exist yet in the repository.

```bash
./gradlew test
```

## Troubleshooting

**VolView shell loads but shows no studies:**
- Verify `xnat_dicomweb_proxy` is deployed
- Check `/xapi/dicomweb/projects/{projectId}/studies` responds
- Review `volview.dicomweb.base-path` configuration

**VolView iframe stays blank:**
- Ensure `build_volview_assets.sh` was run successfully
- Verify `/volview/app/index.html` is accessible
- Check browser console for CORS or loading errors

**Gradle build fails:**
- Verify Java 8 is active: `java -version`
- Check Artifactory credentials in `~/.gradle/gradle.properties`
- Set `GRADLE_USER_HOME` if running in restricted environment

**Submodule issues:**
- Run `git submodule sync` then `git submodule update --init --recursive`
- Verify network access to `https://github.com/mrjamesdickson/VolView.git`
