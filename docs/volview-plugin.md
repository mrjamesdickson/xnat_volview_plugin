# XNAT VolView Plugin – Developer & Operator Guide

## 1. Overview

The XNAT VolView Plugin embeds Kitware’s VolView web application inside XNAT, allowing users to interrogate imaging data through the DICOMweb proxy (`xnat_dicomweb_proxy`). It provides:

- A project-scoped SPA at `/xapi/volview/app/projects/{projectId}` where users can browse studies and series exposed via DICOMweb.
- A session-level action (“Open in VolView”) that launches the SPA pre-populated for the selected imaging session.
- Configurable viewer endpoints so the plugin can be paired with either a locally hosted VolView build or an external deployment.

This document expands on the repository README, covering build flows, deployment, configuration, and troubleshooting in detail.

---

## 2. Repository Layout

```
xnat_volview_plugin/
├── build.gradle                       # Gradle build script (mirrors xnat_dicomweb_proxy conventions)
├── gradle/                            # Gradle wrapper files (Gradle 5.6.2)
├── gradle.properties                  # JVM + daemon settings
├── gradlew / gradlew.bat              # Wrapper entry points
├── frontend/
│   └── VolView/                       # Git submodule pointing to https://github.com/mrjamesdickson/VolView.git
├── src/
│   └── main/
│       ├── java/org/nrg/xnatx/volview # Spring components, REST endpoints
│       └── resources/META-INF/
│           ├── resources/plugin-resources/
│           │   ├── volview/app/       # VolView build output (populated via build_volview_assets.sh)
│           │   └── xnat-volview/      # VolView shell UI (index.html + app.js)
│           └── resources/scripts/
│               └── xnat/plugin/volview/viewerAction.js
├── build_volview_assets.sh            # Helper to build and copy VolView static assets
├── deploy_to_demo02.sh                # (Optional) deployment helper script
└── docs/
    └── volview-plugin.md              # This guide
```

---

## 3. Prerequisites

| Component | Requirement | Notes |
|-----------|-------------|-------|
| XNAT      | 1.9.5+      | Plugin built against parent BOM `org.nrg:parent:1.9.0` with dependency overrides for 1.9.5. |
| Java      | 8           | Match XNAT server JRE. Build uses Gradle 5.6.2. |
| Node.js   | 18+         | Needed to build VolView (Vue/Vite) bundle via `npm`. |
| Git       | Submodules  | Ensure submodule support is enabled; remote uses HTTPS. |
| VolView   | GitHub fork | Submodule targets `https://github.com/mrjamesdickson/VolView.git`. |
| DICOMweb  | `xnat_dicomweb_proxy` | Must be installed on the target XNAT instance; the VolView plugin only consumes its REST endpoints. |

**Credentials**: Gradle resolves XNAT dependencies from NRG’s Artifactory. Provide credentials via `~/.gradle/gradle.properties`:

```
nrgRepoUser=YOUR_USERNAME
nrgRepoKey=YOUR_API_KEY
```

or environment variables `NRG_REPO_USER` / `NRG_REPO_KEY`.

---

## 4. Submodule Management

1. Clone the plugin repository (or pull latest changes).
2. Initialise the VolView submodule:

   ```bash
   git submodule sync
   git submodule update --init --recursive
   ```

3. To check out the latest VolView upstream changes:

   ```bash
   (cd frontend/VolView && git checkout main && git pull origin main)
   ```

4. If you need a different fork/branch, update `.gitmodules`, run `git submodule sync`, and repeat step 2.

---

## 5. Building VolView Assets

VolView is a Vue application built with Vite. Build it once per plugin update (or whenever you want to refresh the assets).

```bash
./build_volview_assets.sh
```

The script performs:

1. `npm ci` inside `frontend/VolView`
2. `npm run build` to produce a production-ready bundle under `frontend/VolView/dist/`
3. Copies the `dist/` contents into `src/main/resources/META-INF/resources/volview/app/`

If you prefer to run commands manually:

```bash
cd frontend/VolView
npm install          # or npm ci for repeatable builds
npm run build
rm -rf ../..../volview/app/*
cp -R dist/* ../..../volview/app/
```

### Notes
- VolView pulls heavyweight dependencies (VTK.js, itk-wasm). Expect a large `node_modules/` directory and initial build time.
- Keep `volview/app/` checked into Git empty (only `.gitkeep`) – the build output is ephemeral and bundled inside the plugin JAR.

---

## 6. Building the Plugin JAR

Ensure the VolView assets have been copied as above, then run:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@8/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
./gradlew clean jar
```

Gradle outputs:

```
build/libs/xnat-volview-plugin-0.1.0.jar
```

### Troubleshooting Gradle Builds

- `java.lang.RuntimeException: Could not create parent directory for lock file …`  
  – If running inside a restricted environment, set `GRADLE_USER_HOME` to a writable directory (e.g., the repo’s `.gradle-home`).

- `Unable to locate a Java Runtime`  
  – Ensure Java 8 is installed; on macOS with Homebrew: `brew install openjdk@8`.

- `Could not find org.nrg.xnat:web:1.9.0`  
  – Verify Artifactory credentials and network access.

---

## 7. Deploying to XNAT (e.g., demo02)

1. **Copy the JAR**

   ```bash
   scp build/libs/xnat-volview-plugin-0.1.0.jar user@demo02:/data/xnat/home/plugins/
   ```

2. **Restart Tomcat**

   ```bash
   sudo service tomcat8 restart        # adjust for your environment
   ```

3. **Verify startup logs**

   Look for log entries similar to:

   ```
   INFO  [org.nrg.xnat.volview.VolViewPlugin] - XNAT VolView Plugin registered
   ```

4. **Confirm static assets**

   - Visit `https://demo02/volview/app/index.html`
   - Ensure it renders the VolView entry point (may require authentication).

5. **Session action check**

   - Navigate to an imaging session report page, open the action menu, and confirm “Open in VolView” is present.

---

## 8. Runtime Usage

### 8.1 Session-level launch

1. Open an XNAT imaging session report.
2. Use the actions menu (`More actions` > `Open in VolView`).
3. The link goes to `/xapi/volview/app/projects/{project}?session={sessionId}`.
4. The SPA fetches session metadata, resolves the Study Instance UID, and auto-launches VolView pointed at `/xapi/dicomweb/projects/{project}/studies/{uid}`.

If the session lacks a StudyInstanceUID, the shell notifies the user and leaves the study selector enabled.

### 8.2 Project-level browsing

Direct URL: `/xapi/volview/app/projects/{projectId}`.  
Features:

- Lists studies via DICOMweb QIDO (`/studies`).
- On study selection, fetches series tree (`/studies/{uid}/series`).
- Buttons:
  - **Open Study in VolView** – launches entire study.
  - **Open Series Only** – when a series is selected.

### 8.3 Manual query parameters

| Query parameter | Purpose |
|-----------------|---------|
| `session` / `sessionId` | Preload session context and auto-launch its study. |
| `study` / `studyInstanceUID` | Preselect a study; combined with `session` ensures fallback if UID differs. |

Examples:

```
/xapi/volview/app/projects/DEMO?session=XNAT_E00123
/xapi/volview/app/projects/DEMO?study=1.2.840.113619...
```

---

## 9. Configuration Reference

The plugin reads site configuration properties (via `VolViewSettings`):

| Property | Default | Description |
|----------|---------|-------------|
| `volview.dicomweb.base-path` | `/xapi/dicomweb/projects` | Base path for the DICOMweb proxy. |
| `volview.viewer.entry-point` | `/volview/app/index.html` | Relative or absolute URL to VolView’s `index.html`. Supports `{dicomweb}` placeholders. |
| `volview.shell.path` | `/plugin-resources/xnat-volview/index.html` | Shell page served by `/xapi/volview/app/projects/**`. |
| `volview.server-name` | `XNAT DICOMweb` | Friendly label displayed in the shell UI. |

Configure these values through **Administer → Plugin Settings → VolView Plugin Settings** or by editing the site configuration directly (e.g. Admin UI → Configuration → Site Config).

---

## 10. Integration Details

### 10.1 Backend REST Endpoints

```
GET /xapi/volview/config/projects/{projectId}
GET /xapi/volview/config/projects/{projectId}/sessions/{sessionId}
```

Both endpoints:

- Validate user authentication + project visibility.
- Build absolute DICOMweb URLs using `volview.dicomweb.base-path`.
- Return viewer entry point / launch templates.

The session-specific endpoint reads `StudyInstanceUID` to facilitate auto-launch.

### 10.2 Frontend Shell (`xnat-volview/index.html + app.js`)

- Modern ES module script with no build step (vanilla JS + fetch).
- Handles DICOM metadata parsing (`Tag.Value` decoding).
- Uses XNAT credentials via `fetch` with `credentials: 'include'`.
- Launches VolView by appending `?dicomweb={encodedUrl}` to the entry point.

### 10.3 Session Action (`ViewInVolView.vm` & `viewerAction.js`)

- Injected into session page via Velocity template.
- JS listens for click/context events on `#volviewViewer`.
- Uses `XNAT.data.context` to retrieve project/session IDs.
- Opens `/xapi/volview/app/projects/{project}?session={session}` in current tab or new tab (middle click / modifiers).

---

## 11. Updating VolView

1. Pull changes in submodule:

   ```bash
   git submodule update --remote frontend/VolView
   ```

2. Re-run `./build_volview_assets.sh`.
3. Rebuild plugin JAR.
4. Redeploy to XNAT.

If you check in the submodule reference, other developers can sync with `git submodule update --init --recursive`.

---

## 12. Troubleshooting

| Symptom | Possible Causes | Remedies |
|---------|-----------------|----------|
| **VolView shell loads but lists no studies** | DICOMweb proxy missing or misconfigured; wrong `volview.dicomweb.base-path` | Confirm `xnat_dicomweb_proxy` is installed; `curl /xapi/dicomweb/projects/{project}/studies`; update configuration. |
| **“Session metadata does not include a StudyInstanceUID”** | Session lacks UIDs in XNAT metadata | Ensure session has `StudyInstanceUID`; run DICOM import or manual correction. |
| **VolView stays blank** | Assets missing; entry point points to stale build | Re-run `./build_volview_assets.sh`; verify `volview.viewer.entry-point`. |
| **Gradle cannot resolve dependencies** | Artifactory credentials absent | Set `nrgRepoUser`/`nrgRepoKey`; ensure network access. |
| **VolView submodule fails to clone** | GitHub auth required or offline | Verify access to `https://github.com/mrjamesdickson/VolView.git`. |
| **Action menu missing** | XNAT caches templates; plugin not redeployed | Restart Tomcat after deploying; clear browser cache. |

---

## 13. Deployment Checklist

- [ ] VolView submodule initialised and updated.
- [ ] `./build_volview_assets.sh` executed successfully.
- [ ] `./gradlew clean jar` completes without errors.
- [ ] `build/libs/xnat-volview-plugin-0.1.0.jar` copied to XNAT plugins directory.
- [ ] Tomcat restarted; logs confirm plugin registration.
- [ ] `/volview/app/index.html` accessible.
- [ ] Session action menu shows “Open in VolView” and launches viewer correctly.
- [ ] `/xapi/volview/app/projects/{project}` lists studies and integrates with VolView.

---

## 14. Future Enhancements (Ideas)

- Subject-level or project dashboard buttons linking to the viewer.
- Persisting user layout preferences in XNAT.
- Injecting additional metadata (e.g., segmentation overlays) through the existing ROI infrastructure.
- Support for project-specific VolView configuration (e.g., pointing to alternate DICOMweb endpoints per project).

---

## 15. Support & Maintenance

- **Repository**: XNAT VolView Plugin  
- **VolView Upstream**: https://github.com/mrjamesdickson/VolView (fork)  
- **DICOMweb Proxy**: `/Users/james/projects/xnat_dicomweb_proxy`

For issues related to XNAT integration (authentication, permissions), consult the XNAT logs under `$XNAT_HOME/logs/`. VolView application-level logs can be captured via browser DevTools or the terminal while running `npm run dev` against the submodule.

--- 

_Document version: October 31, 2025_
