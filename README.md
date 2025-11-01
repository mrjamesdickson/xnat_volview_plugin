# XNAT VolView Plugin

This plugin adds a project-level VolView experience to XNAT by embedding Kitware's VolView viewer and wiring it to the DICOMweb endpoints that are exposed by the companion [`xnat_dicomweb_proxy`](../xnat_dicomweb_proxy) plugin. Users can browse the DICOM studies that belong to an XNAT project and launch either whole studies or individual series directly inside VolView without leaving the XNAT UI.

The viewer shell is implemented as a lightweight single page app (`/xapi/volview/app/projects/{projectId}`) that discovers DICOM studies through the proxy's QIDO-RS endpoints and launches VolView using its `dicomweb=` URL parameter support, which allows VolView to attach to an existing DICOMweb server and optionally open a specific study or series.

> **XNAT prerequisites:** XNAT 1.9.5 (or newer), Java 8+, the `xnat_dicomweb_proxy` plugin deployed and configured, and CORS enabled for the XNAT host if you expect to view the project from a different origin.

For full developer/operator documentation (build flows, deployment checklist, troubleshooting), see [`docs/volview-plugin.md`](docs/volview-plugin.md).

---

## Repository layout

```
.
├── build.gradle
├── settings.gradle
├── frontend
│   └── VolView/                # VolView submodule (https://github.com/mrjamesdickson/VolView)
├── src
│   └── main
│       ├── java
│       │   └── org/nrg/xnatx/volview
│       │       ├── VolViewPlugin.java
│       │       ├── config/VolViewSettings.java
│       │       └── web/{VolViewConfigController, VolViewPageController}.java
│       └── resources
│           └── META-INF/resources
│               ├── xnat-volview/{index.html, app.js}
│               └── volview/app/                # populated by build_volview_assets.sh
└── README.md
```

---

## Preparing VolView assets

1. **Initialise the VolView submodule (once)**
   ```bash
   git submodule update --init --recursive
   ```
   The submodule targets `https://github.com/mrjamesdickson/VolView.git`. You can switch forks by editing `.gitmodules`, then re-running `git submodule sync`.

2. **Build and copy the VolView bundle**
   ```bash
   ./build_volview_assets.sh
   ```
   This script runs `npm ci && npm run build` inside `frontend/VolView` and then copies the generated `dist/` contents into `src/main/resources/META-INF/resources/volview/app/`.

   > VolView's build pulls a large dependency graph (itk-wasm, vtk.js, etc.). Make sure the machine running the script has Node.js 18+, `npm`, and network access to fetch those packages (or a populated npm cache).

3. **Optional** – If you prefer hosting VolView elsewhere, skip the script and point `volview.viewer.entry-point` to your external URL (see configuration below).

---

## Building the plugin

After the assets are in place:

1. **Compile**
   ```bash
   ./gradlew jar
   ```

2. **Deploy**
   Copy `build/libs/xnat-volview-plugin-0.1.0.jar` into your XNAT `plugins/` directory and restart XNAT.

---

## Runtime configuration

The plugin exposes a handful of properties (e.g. through the XNAT site configuration, `siteConfig` entries, or environment variables):

| Property | Default | Description |
|----------|---------|-------------|
| `volview.dicomweb.base-path` | `/xapi/dicomweb/projects` | Root path served by the DICOMweb proxy plugin. |
| `volview.viewer.entry-point` | `/volview/app/index.html` | URL (relative or absolute) that loads the VolView application. Include a `{dicomweb}` token if your build requires it; otherwise the plugin appends a `?dicomweb=` query parameter automatically. |
| `volview.shell.path` | `/plugin-resources/xnat-volview/index.html` | Path to the control-shell page served under `/xapi/volview/app/projects/{projectId}`. |
| `volview.server-name` | `XNAT DICOMweb` | Friendly name shown in the UI. |

Manage these values through **Administer → Plugin Settings → VolView Plugin Settings**, which ships with the plugin, or by editing the properties directly in the site configuration.

---

## Using the viewer

1. Navigate to `/xapi/volview/app/projects/{projectId}` (e.g. `/xnat/xapi/volview/app/projects/DEMO`) while authenticated in XNAT.
2. The shell page queries `/xapi/volview/config/projects/{projectId}` to discover the appropriate DICOMweb endpoints and VolView entry point.
3. The study selector issues a QIDO-RS `studies` request via the DICOMweb proxy. Selecting a study optionally loads its series list on demand.
4. Choosing **Open Study** launches the locally hosted VolView build inside the iframe using a URL of the form  
   `<volview-entry-point>?dicomweb=https://{xnat}/xapi/dicomweb/projects/{projectId}/studies/{studyUID}`.  
   Series navigation uses the more specific `/series/{seriesUID}` target.

If the viewer iframe remains blank, verify:

- The VolView static build has been copied to the `volview/app/` folder.
- You can load the entry point URL directly in a browser tab.
- The DICOMweb proxy responds at `/xapi/dicomweb/projects/{projectId}` and you are authenticated.
- CORS settings allow the VolView origin if you are hosting VolView elsewhere.

---

## Extending

- Extend the shell UI (in `xnat-volview/app.js`) to display additional metadata pulled from QIDO responses or to pre-filter studies based on custom rules.
- Override the `volview.viewer.entry-point` property to point to an upgraded VolView build without rebuilding this plugin.
- Integrate with project dashboards by adding Project Actions within XNAT that deep-link to `/xapi/volview/app/projects/{projectId}`.

---

## Development notes

- The Java controllers only ever return relative URLs so the plugin respects whatever host/port/context-path XNAT is deployed under.
- No DICOM data is copied or transformed: all browsing happens through the DICOMweb proxy supplied by `xnat_dicomweb_proxy`.
- The viewer shell uses the `dicomweb` launch parameter supported by VolView to attach to the preconfigured DICOMweb root.
