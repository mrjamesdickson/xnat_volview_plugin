package org.nrg.xnat.volview;

import org.nrg.framework.annotations.XnatPlugin;
import org.springframework.context.annotation.ComponentScan;

@XnatPlugin(
        value = "xnat-volview-plugin",
        name = "XNAT VolView Plugin",
        description = "Embeds Kitware VolView using the XNAT DICOMweb proxy.",
        entityPackages = "org.nrg.xnat.volview",
        openUrls = {
                "/xapi/volview/test",
                "/xapi/volview/test/**",
                "/xapi/volview/app/**",
                "/volview/app/**",
                "/app/volview/**"
        }
)
@ComponentScan({"org.nrg.xnat.volview"})
public class VolViewPlugin {
}
