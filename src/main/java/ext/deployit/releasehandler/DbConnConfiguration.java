/* GE-1.2.2 version */

/**
 * Copyright 2018 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ext.deployit.releasehandler;

import com.google.common.base.Strings;

/**
 * Created by jdewinne on 2/5/15.
 */
public class DbConnConfiguration {

    private String apiUrl = "";
    private String apiKey = "";
    private String proxyHost = "";
    private Integer proxyPort = 0;
    private String templateName = "";
    private String flowToken = "";
    private String orgParamName="";
    private Boolean enabled = Boolean.FALSE;

    // Add the API token as the global config

    //organization/flowparameterized name for the flow

//    public FlowdockConfiguration(String apiUrl, String apiKey, Boolean enabled, String templateName, String flowToken, String orgParamName) {
//        this(apiUrl, apiKey, enabled, null, null, templateName, flowToken, orgParamName);
//    }

    public DbConnConfiguration(String apiUrl, String apiKey, Boolean enabled, String proxyHost, String proxyPort, String templateName, String flowToken, String orgParamName) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.proxyHost = proxyHost;
        this.proxyPort = Strings.isNullOrEmpty(proxyPort)?0:Integer.parseInt(proxyPort);
        this.enabled = enabled;
        this.templateName = templateName;
        this.flowToken = flowToken;
        this.orgParamName = orgParamName;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getFlowToken() {
        return flowToken;
    }

    public String getOrgParamName() {
        return orgParamName;
    }

}
