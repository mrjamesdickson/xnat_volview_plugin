package org.nrg.xnat.volview.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@XapiRestController
@Api("VolView Test Page")
public class VolViewTestPageApi extends AbstractXapiRestController {

    @Autowired
    public VolViewTestPageApi(final UserManagementServiceI userManagementService,
                              final RoleHolder roleHolder) {
        super(userManagementService, roleHolder);
    }

    @XapiRequestMapping(
            value = "/vol/test",
            method = RequestMethod.GET,
            produces = MediaType.TEXT_HTML_VALUE
    )
    @ApiOperation(value = "VolView Test Page", response = String.class)
    public String getTestPage() {
        try {
            InputStream is = getClass().getResourceAsStream("/META-INF/resources/volview-test.html");
            if (is != null) {
                return new String(readAllBytes(is), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return "<html><body><h1>Error loading test page</h1><p>" + e.getMessage() + "</p></body></html>";
        }
        return "<html><body><h1>Test page not found</h1></body></html>";
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((bytesRead = is.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }
}
