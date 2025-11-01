# XNAT VolView Plugin Roadmap

This document provides a strategic plan for enhancing the XNAT VolView plugin based on the identified improvement areas.

## Version Planning

### v1.1.0 - Foundation & Stability (Priority: High)

Focus: Establish testing infrastructure and improve reliability.

#### Issues
- **#1: Add unit and integration tests**
- **#2: Improve error handling and validation**

#### Rationale
Before adding new features, we need a solid foundation:
- Tests ensure existing functionality doesn't break
- Better error handling improves debugging and user experience
- These improvements make all future development safer

#### Implementation Order
1. **Error handling first** (#2) - Easier to test code with clear error boundaries
2. **Tests second** (#1) - Can validate error handling implementation

#### Estimated Effort
- #2: 2-3 days
- #1: 5-7 days
- **Total: ~2 weeks**

#### Success Metrics
- 70%+ code coverage
- All REST endpoints return appropriate HTTP status codes
- Clear error messages for common failure scenarios

---

### v1.2.0 - Performance & Observability (Priority: Medium)

Focus: Optimize performance and add visibility into plugin usage.

#### Issues
- **#3: Add configuration caching**
- **#6: Add monitoring and metrics**

#### Rationale
- Configuration caching provides immediate performance benefits
- Monitoring helps understand usage patterns and identify issues in production
- Both are relatively isolated changes with low risk

#### Implementation Order
1. **Caching first** (#3) - Simpler, provides immediate value
2. **Monitoring second** (#6) - Can leverage existing test infrastructure

#### Estimated Effort
- #3: 2-3 days
- #6: 3-4 days
- **Total: ~1 week**

#### Success Metrics
- Configuration endpoint response time reduced by 50%+
- Usage metrics available in admin dashboard
- Error rate tracking operational

---

### v1.3.0 - Enhanced User Experience (Priority: Medium)

Focus: Improve the frontend experience for end users.

#### Issues
- **#5: Enhance shell UI with better study/series browsing**
- **#4: Add user preferences for viewer settings**

#### Rationale
- Shell UI improvements make the viewer more useful for power users
- User preferences improve workflow efficiency
- Both features benefit from the stability foundation (v1.1.0) and monitoring (v1.2.0)

#### Implementation Order
1. **Shell UI enhancements first** (#5) - More visible impact
2. **User preferences second** (#4) - Can build on UI improvements

#### Estimated Effort
- #5: 5-7 days
- #4: 4-5 days
- **Total: ~2 weeks**

#### Success Metrics
- Users can browse/filter studies efficiently
- Thumbnail previews load within 2 seconds
- User preferences persist across sessions

---

### v2.0.0 - Advanced Configuration (Priority: Low)

Focus: Enterprise features for complex deployments.

#### Issues
- **#7: Add support for project-specific viewer configurations**
- **#8: Improve documentation and deployment guide**

#### Rationale
- Project-specific configs enable advanced use cases
- Better documentation reduces support burden
- Documentation should wait until features stabilize

#### Implementation Order
1. **Project configs first** (#7) - Core feature
2. **Documentation last** (#8) - Covers all features from v1.x and v2.0

#### Estimated Effort
- #7: 7-10 days
- #8: 3-4 days
- **Total: ~2-3 weeks**

#### Success Metrics
- Projects can have independent DICOMweb configurations
- Deployment guide tested by 3+ new users
- API documentation complete for all endpoints

---

## Dependency Graph

```
v1.1.0 (Foundation)
  ├─ #2 Error handling
  └─ #1 Tests
       │
       ├─── v1.2.0 (Performance)
       │      ├─ #3 Caching
       │      └─ #6 Monitoring
       │           │
       │           └─── v1.3.0 (UX)
       │                  ├─ #5 Shell UI
       │                  └─ #4 User prefs
       │                       │
       │                       └─── v2.0.0 (Enterprise)
       │                              ├─ #7 Project configs
       │                              └─ #8 Documentation
```

## Technical Considerations

### Issue #1: Testing Strategy

**Framework choices:**
- JUnit 5 (already configured)
- Mockito for mocking XNAT components
- Spring Test for integration tests
- WireMock for DICOMweb endpoint mocking

**Coverage targets:**
- `VolViewSettings`: 100% (simple bean)
- `VolViewConfigController`: 90%+ (core logic)
- `VolViewPageController`: 80%+ (mostly URL construction)

**Integration test scenarios:**
- Config endpoint with valid project/session
- Config endpoint with invalid/unauthorized access
- Page controller URL construction with various XNAT deployment configs

---

### Issue #2: Error Handling Patterns

**Standardize responses:**
```java
// Success
{ "config": {...} }

// Error
{
  "error": "PROJECT_NOT_FOUND",
  "message": "Project 'INVALID' does not exist or you lack permission",
  "timestamp": "2025-11-01T12:34:56Z"
}
```

**HTTP status code mapping:**
- 200: Success
- 400: Invalid request (bad Study UID format, etc.)
- 403: Permission denied
- 404: Project/session not found
- 500: Internal server error
- 503: DICOMweb proxy unavailable

**Add custom exceptions:**
```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProjectNotFoundException extends RuntimeException {}

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class DicomWebProxyUnavailableException extends RuntimeException {}
```

---

### Issue #3: Caching Implementation

**Use Spring Cache abstraction:**
```java
@Cacheable(value = "volview-config", key = "#projectId")
public VolViewConfig getProjectConfig(String projectId) {
    // ...
}

@CacheEvict(value = "volview-config", allEntries = true)
public void onSiteConfigUpdated() {
    // Invalidate on admin settings change
}
```

**Configuration:**
```java
@EnableCaching
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(
            new ConcurrentMapCache("volview-config")
        ));
        return manager;
    }
}
```

**TTL:** 5-10 minutes (balances performance vs. config update latency)

---

### Issue #4: User Preferences Schema

**Storage location:** XNAT `userOptions` table

**Preference structure:**
```json
{
  "volview": {
    "defaultLayout": "3x3",
    "windowLevelPresets": [
      {"name": "Brain", "window": 80, "level": 40},
      {"name": "Lung", "window": 1500, "level": -600}
    ],
    "autoLoadSeries": false,
    "preferredOrientation": "axial"
  }
}
```

**REST endpoints:**
```
GET  /xapi/volview/preferences
POST /xapi/volview/preferences
PUT  /xapi/volview/preferences/{key}
```

**Pass to VolView:**
Encode preferences in launch URL:
```
/volview/app/index.html?dicomweb=...&prefs=base64(JSON)
```

---

### Issue #5: Shell UI Framework Choice

**Options:**
1. **Alpine.js** (14KB) - Declarative, minimal
2. **Petite-vue** (6KB) - Vue subset, progressive enhancement
3. **Vanilla JS + Web Components** - No dependencies

**Recommendation:** Alpine.js
- Small footprint
- Easy integration with existing vanilla JS
- No build step required
- Good for progressive enhancement

**New features:**
- Virtual scrolling for large study lists (use Intersection Observer)
- Thumbnail loading via WADO-RS frame retrieval
- Local storage for UI state (sort order, filters)

---

### Issue #6: Metrics to Track

**Usage metrics:**
- Viewer launches (per project, per user, per day)
- Studies viewed (unique Study Instance UIDs)
- Average session duration
- Series loaded per session

**Performance metrics:**
- Config endpoint response time (p50, p95, p99)
- Study list load time
- VolView iframe load time

**Error metrics:**
- 4xx/5xx error rates by endpoint
- DICOMweb proxy errors
- Failed viewer launches

**Implementation:**
- Use XNAT's existing metrics if available (check `org.nrg.framework.utilities.Metric`)
- Otherwise, implement simple counters with scheduled log output
- Consider Micrometer for future Prometheus integration

---

### Issue #7: Project-Specific Configurations

**Storage:** Project resources or separate config table

**Configuration hierarchy:**
```
Site-wide defaults (site-settings.yaml)
  └─ Project overrides (project config)
      └─ User preferences (Issue #4)
```

**Override capabilities:**
- DICOMweb endpoint URL (for projects with external PACS)
- VolView version/fork (for testing custom builds)
- Viewer feature flags
- Default window/level presets

**Admin UI:**
- Add to Project Settings page
- Form similar to site settings
- Preview/test button to validate config

**Backward compatibility:**
Projects without overrides use site defaults (no breaking changes)

---

### Issue #8: Documentation Improvements

**New documentation:**

1. **Architecture diagram** (`docs/architecture.md`)
   - Component diagram (plugin, proxy, VolView, XNAT)
   - Sequence diagram (user click → study loads)
   - Data flow (QIDO/WADO requests)

2. **Deployment guide** (`docs/deployment.md`)
   - Prerequisites checklist
   - Step-by-step with screenshots
   - Verification steps
   - Common deployment patterns (Docker, Tomcat, reverse proxy)

3. **Troubleshooting guide** (`docs/troubleshooting.md`)
   - Common issues (no studies visible, blank iframe, etc.)
   - How to check logs
   - How to verify DICOMweb proxy
   - Browser console debugging

4. **API documentation** (`docs/api.md`)
   - OpenAPI/Swagger spec for REST endpoints
   - Request/response examples
   - Authentication requirements

5. **Developer guide** (`docs/development.md`)
   - Local development setup
   - How to extend the plugin
   - How to customize VolView fork
   - Testing guidelines

---

## Release Strategy

### Version Numbering
- **Major** (X.0.0): Breaking changes (e.g., v2.0.0)
- **Minor** (1.X.0): New features, backward compatible
- **Patch** (1.0.X): Bug fixes only

### Release Process
1. Create feature branch from `main`
2. Implement issue(s)
3. Update CHANGELOG.md
4. Create PR with test results
5. Merge to `main`
6. Tag release (e.g., `1.1.0`)
7. Build JAR: `./gradlew clean jar`
8. Create GitHub release with JAR artifact
9. Update documentation site

### Branch Strategy
- `main`: Stable, production-ready
- `develop`: Integration branch (if needed for v2.0+)
- `feature/issue-N`: Feature branches

---

## Risk Assessment

### High Risk
- **#7 (Project configs)**: Database schema changes, migration complexity
  - Mitigation: Thorough testing, backward compatibility

### Medium Risk
- **#5 (Shell UI)**: Introducing new framework could cause regressions
  - Mitigation: Progressive enhancement, fallback to vanilla JS
- **#4 (User prefs)**: Integration with XNAT user storage
  - Mitigation: Research XNAT APIs early, prototype first

### Low Risk
- **#1 (Tests)**: No production impact
- **#2 (Error handling)**: Improves stability
- **#3 (Caching)**: Can disable if issues arise
- **#6 (Monitoring)**: Observability only
- **#8 (Documentation)**: No code changes

---

## Resource Requirements

### Development Time
- **Total estimated effort**: ~8-10 weeks
- **Parallel work possible**: Yes (frontend vs. backend)

### Skills Needed
- Java/Spring (backend issues)
- JavaScript (frontend issues)
- XNAT plugin development (all issues)
- DICOMweb/DICOM knowledge (helpful for #5)

### Infrastructure
- Test XNAT instance with sample data
- CI/CD pipeline for automated testing (future)

---

## Community Engagement

### When to seek feedback
- Before implementing #7 (project configs) - validate use cases
- After implementing #5 (Shell UI) - user testing
- During #8 (documentation) - have new users try deployment guide

### Potential contributors
- Shell UI (#5) - frontend developers
- Documentation (#8) - technical writers
- Testing (#1) - QA engineers

---

## Success Criteria

The roadmap is successful when:

1. ✅ Plugin has >70% test coverage
2. ✅ Error messages are clear and actionable
3. ✅ Configuration reads are cached and performant
4. ✅ Users can efficiently browse studies
5. ✅ User preferences persist across sessions
6. ✅ Usage metrics inform future development
7. ✅ Projects can have custom configurations
8. ✅ New users can deploy without assistance

---

## Appendix: Quick Wins (Could be v1.0.1)

Small improvements that could be done immediately:

- Add version number to plugin manifest
- Add GitHub Actions CI for automated builds
- Add `.editorconfig` for consistent code style
- Add `CONTRIBUTING.md` with development setup
- Add Swagger/OpenAPI annotations to controllers
- Add request logging to controllers
- Add session ID to Study config endpoint (issue #2)

These could be batched into a quick v1.0.1 patch release.

---

*Last updated: 2025-11-01*
*Version: 1.0*
