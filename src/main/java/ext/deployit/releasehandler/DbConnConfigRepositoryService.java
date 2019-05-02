/* REL-6235 version */
/* For GE-1.4.0:  expanded argument list line 62 */

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

import java.util.ArrayList;
import java.util.List;

import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.xlrelease.api.v1.ConfigurationApi;
import ext.deployit.releasehandler.exception.DbConnNotConfiguredException;

/**
 * Created by jdewinne on 2/5/15.
 */
public class DbConnConfigRepositoryService {

    private List<DbConnConfiguration> dbConnConfigurations = new ArrayList<>();

    private ConfigurationApi configurationApi;

    DbConnConfigRepositoryService(ConfigurationApi configurationApi) {
        this.configurationApi = configurationApi;
    }

    public List<DbConnConfiguration> getDbConnConfigurations() throws DbConnNotConfiguredException {
        if (dbConnConfigurations.isEmpty()) {
            setDbConnConfigurations();
        }

        return dbConnConfigurations;
    }

    public List<DbConnConfiguration> getEligibleDbConnConfigurations(String templateName) throws DbConnNotConfiguredException {
        if (dbConnConfigurations.isEmpty()) {
            setDbConnConfigurations();
        }

        List<DbConnConfiguration> eligibleDbConnConfigurations = new ArrayList<>();

        for (DbConnConfiguration dbConnConfiguration : dbConnConfigurations) {
            if (dbConnConfiguration.isEnabled()) {
                eligibleDbConnConfigurations.add(dbConnConfiguration);
            }
        }

        return eligibleDbConnConfigurations;
    }

    public Boolean isDbConnEnabled() throws DbConnNotConfiguredException {
        if (dbConnConfigurations.isEmpty()) {
            setDbConnConfigurations();
        }

        for (DbConnConfiguration dbConnConfiguration : dbConnConfigurations) {
            if (dbConnConfiguration.isEnabled()) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    private void setDbConnConfigurations() throws DbConnNotConfiguredException {
        // Get flowdock properties
        final List<? extends ConfigurationItem> configurations = configurationApi.searchByTypeAndTitle("expressSripts.ReportingDatabase", null);
        if (configurations.size() > 0) {
            configurations.forEach(conf -> dbConnConfigurations.add(
//              new FlowdockConfiguration(conf.getProperty("apiUrl"), conf.getProperty("flowToken"), conf.getProperty("enabled")));
                new DbConnConfiguration(conf.getProperty("apiUrl"), conf.getProperty("apiKey"), conf.getProperty("enabled"), conf.getProperty("proxyHost"), conf.getProperty("proxyPort"), conf.getProperty("templateName"), conf.getProperty("flowToken"), conf.getProperty("orgParamName")))
            );
        } else {
            throw new DbConnNotConfiguredException();
        }
    }


}
