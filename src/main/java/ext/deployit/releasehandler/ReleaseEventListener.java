/**
 * Copyright 2017 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
/*
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND,
 * EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A PARTICULAR PURPOSE. THIS
 * CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package ext.deployit.releasehandler;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.xebialabs.deployit.engine.spi.event.CisCreatedEvent;
import com.xebialabs.deployit.engine.spi.event.CisUpdatedEvent;

import com.xebialabs.xlrelease.domain.events.ActivityLogEvent;
import com.xebialabs.xlrelease.domain.events.ReleaseEvent;
import com.xebialabs.xlrelease.domain.events.ReleaseStartedEvent;
import com.xebialabs.xlrelease.domain.events.ReleaseUpdatedEvent;
import com.xebialabs.xlrelease.domain.events.ReleaseCompletedEvent;
import com.xebialabs.xlrelease.domain.events.ReleaseAbortedEvent;
import com.xebialabs.xlrelease.security.authentication.AuthenticationService;
import com.xebialabs.xlrelease.events.AsyncSubscribe;
import com.xebialabs.xlrelease.events.XLReleaseEventListener;
import com.xebialabs.xlrelease.domain.status.PhaseStatus;
import com.xebialabs.xlrelease.domain.status.ReleaseStatus;
import com.xebialabs.xlrelease.domain.status.TaskStatus;

import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.repository.RepositoryService;
import com.xebialabs.deployit.repository.SearchParameters;
// import com.xebialabs.xlrelease.api.XLReleaseServiceHolder;
import com.xebialabs.xlrelease.api.v1.ConfigurationApi;
import com.xebialabs.xlrelease.domain.Configuration;
import com.xebialabs.xlrelease.domain.Phase;
import com.xebialabs.xlrelease.domain.Release;
import com.xebialabs.xlrelease.domain.Task;
import com.xebialabs.xlrelease.domain.status.ReleaseStatus;
import com.xebialabs.xlrelease.domain.status.TaskStatus;
import com.xebialabs.xlrelease.domain.variables.Variable;

// import nl.javadude.t2bus.Subscribe;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.xebialabs.xlrelease.service.SharedConfigurationService;
import static com.xebialabs.deployit.plugin.api.reflect.Type.valueOf;
import com.xebialabs.xlrelease.script.EncryptionHelper;

// Previously a @DeployitEventListener
public class ReleaseEventListener implements XLReleaseEventListener {
  private static final Logger logger = LoggerFactory.getLogger(ReleaseEventListener.class);

  private static final Type RELEASE_TYPE = Type.valueOf("xlrelease.Release");
  private static final String TASK_SEQUENTIAL_GROUP = "xlrelease.SequentialGroup";
  private static final String TASK_PARALLEL_GROUP = "xlrelease.ParallelGroup";
  private static final String DB_CONN_NAME = "ReportingDatabase";
  private static final String DB_CONN_PREFIX = "expressScripts";
  private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

  @Resource
  private AuthenticationService authenticationService;

  @Resource
  private ConfigurationApi configurationApi;
  
  @Resource
  private SharedConfigurationService sharedConfigurationService;

  private static final Cache<String, Boolean> RELEASES_SEEN = CacheBuilder.newBuilder().maximumSize(1000)
      .expireAfterWrite(10, SECONDS).<String, Boolean>build();

  private ConfigurationItem getDBConnectionConfig(Release release) {

    // Test - Direct access to configuration items
    List<Configuration> items = sharedConfigurationService.searchByTypeAndTitle(valueOf(DB_CONN_PREFIX + "." + DB_CONN_NAME), null, null, false);
    ConfigurationItem dbConnectionConfiguration = items.get(0);
    if (dbConnectionConfiguration != null) {
      EncryptionHelper.decrypt(dbConnectionConfiguration);
      return dbConnectionConfiguration;
    }
    

    logger.debug("Executing getDBConnectionConfig()");
    authenticationService.loginScriptUser(release);
    //    List<? extends ConfigurationItem> configs = XLReleaseServiceHolder.getConfigurationApi().searchByTypeAndTitle(DB_CONN_PREFIX + "." + DB_CONN_NAME, null);
    List<? extends ConfigurationItem> configs = configurationApi.searchByTypeAndTitle(DB_CONN_PREFIX + "." + DB_CONN_NAME, null);
    authenticationService.logoutScriptUser();
    logger.debug(String.format("Got %d config(s)", configs.size()));
    ConfigurationItem dbConnectionConfig = configs.get(0); // Assume it's the first and it exists
    logger.debug("Returning first config");
    return dbConnectionConfig;

  }

  @AsyncSubscribe
  public synchronized void receiveReleaseEvent(ReleaseEvent event) {

    logger.debug("Executing receiveReleaseEvent()");
    logger.debug("event class is " + event.getClass().getName());
    // logger.debug("event.releaseId=” + event.releaseId()");
    // logger.debug("activityType: " + event.activityType());

    Release release = null;
    String[] splitClassname = event.getClass().getName().split("\\.");
    String shortClassname = splitClassname[splitClassname.length - 1];
    switch (shortClassname)
    {

      // Event published when a release has started, completed or aborted.
      case "ReleaseStartedEvent":
          release = ((ReleaseStartedEvent) event).release();
          break;
      case "ReleaseCompletedEvent":
          release = ((ReleaseCompletedEvent) event).release();
          break;
      case "ReleaseAbortedEvent":
          release = ((ReleaseAbortedEvent) event).release();
          break;
      // Event published when properties of a release or a template (such as its title) has been updated.
      // This event does not include changes in task/phase content or task/phase execution status.
      case "ReleaseUpdatedEvent":
          release = ((ReleaseUpdatedEvent) event).updated();
          break;
      default:
          break;
    }
    if (release != null) {
//        authenticationService.loginScriptUser(release);
        logger.debug(release.getTitle());
        if (release.getStatus() != ReleaseStatus.TEMPLATE) {
          if (RELEASES_SEEN.getIfPresent(release.getId()) != null) {
            logger.debug("Release '{}' already seen. Doing nothing", release.getId());
          } else {
            RELEASES_SEEN.put(release.getId(), true);
            exportRelease(release);
          }
        }
//        authenticationService.logoutScriptUser();
    }
  }

  private void exportRelease(final Release release) {
    logger.debug("Submitting runnable to invoke release exporter for release '{}'", release.getId());
    /*
     * Needs to be called from a different thread to allow this source event
     * handler to complete. Otherwise, the release modification will run
     * slow.
     */
    EXECUTOR_SERVICE.submit(new Runnable() {
      public void run() {
        try {
          invokeReleaseExporter(release);
        } catch (IOException exception) {
          logger.error("Exception trying to invoke ReleaseExporter callback: {}", exception);
        } catch (SQLException exception) {
          logger.error("Exception trying to export release: {}", exception);
        } catch (Exception exception) {
          logger.error("Generic Exception trying to export release: {}", exception);
        }
      }
    });
  }

  private void invokeReleaseExporter(Release release) throws IOException, SQLException, PropertyVetoException {

    final ConfigurationItem connConfig = getDBConnectionConfig(release);
    if (connConfig == null) {
      logger.error("Could not find config, ignoring export to reporting database");
      return;
    }

    final String releaseTableName = connConfig.getProperty("releaseDetailsTable").toString();
    final String taskDetailsTableName = connConfig.getProperty("taskDetailsTable").toString();
    final String userStoriesTableName = connConfig.getProperty("userStoriesTable").toString();
    final String appNameVarName = connConfig.getProperty("jiraProjectVariableName").toString();
    final String appVerVarName = connConfig.getProperty("jiraProjectReleaseVersion").toString();
    final String userStoriesVariableName = connConfig.getProperty("userStoriesVariableName").toString();

    final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    final Variable appNameVar = ReleaseExportHelper.getVariableByName(release.getVariables(), appNameVarName);
    final Variable appVersionVar = ReleaseExportHelper.getVariableByName(release.getVariables(), appVerVarName);
    final Variable userStoriesVar = ReleaseExportHelper.getVariableByName(release.getVariables(),
        userStoriesVariableName);

    int numTasks = 0;
    int numAutomatedTasks = 0;
    int numSkippedTasks = 0;
    int numRetriedTasks = 0;
    int numCompletedTasks = 0;

    StringBuffer releaseSql = new StringBuffer();
    releaseSql.append("INSERT INTO " + releaseTableName);
    releaseSql
        .append(" (Release_ID, Template_ID, Release_Title, Release_Owner, Application_Name, Application_Version, Release_Start_Time, Release_End_Time "
            + ", Release_Duration, Planned_Release_Duration, Is_Release_Delayed , Release_Status, Release_Flag_Status, Release_Tags, No_Tasks"
            + ", No_Retried_Tasks, No_Completed_Tasks, No_Skipped_Tasks , Automation )");

    releaseSql.append(" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

    StringBuffer taskSql = new StringBuffer();
    taskSql.append("INSERT INTO " + taskDetailsTableName);
    taskSql.append(" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

    StringBuffer userStorySQL = new StringBuffer();
    userStorySQL.append("INSERT INTO " + userStoriesTableName);
    userStorySQL.append(" VALUES (?,?,?)");

    logger.info("Exporting release :{} - {}", release.getId(), release.getTitle());

    Connection dbConnection = getDBConnection(connConfig);

    PreparedStatement releaseStmt = dbConnection.prepareStatement(releaseSql.toString());
    PreparedStatement taskStmt = dbConnection.prepareStatement(taskSql.toString());
    PreparedStatement userStoryStmt = dbConnection.prepareStatement(userStorySQL.toString());

    final long releaseDuration = (release.getEndOrDueDate().getTime() - release.getStartOrScheduledDate().getTime())
        / 1000;
    final int releasePlannedDuration = release.getPlannedDuration() == null ? 0
        : release.getPlannedDuration().intValue();

    releaseStmt.setString(1, release.getId());
    releaseStmt.setString(2, release.getOriginTemplateId());
    releaseStmt.setString(3, release.getTitle());
    releaseStmt.setString(4, release.getOwner());
    releaseStmt.setString(5, appNameVar != null ? appNameVar.getValueAsString() : null);
    releaseStmt.setString(6, appVersionVar != null ? appVersionVar.getValueAsString() : null);
    releaseStmt.setString(7, sdf.format(release.getStartOrScheduledDate()));
    releaseStmt.setString(8, sdf.format(release.getEndOrDueDate().getTime()));
    releaseStmt.setLong(9, releaseDuration);
    releaseStmt.setInt(10, releasePlannedDuration);
    releaseStmt.setInt(11, releasePlannedDuration > releaseDuration ? 1 : 0);
    releaseStmt.setString(12, release.getStatus().toString());
    releaseStmt.setString(13, release.getFlagStatus().toString());
    releaseStmt.setString(14, String.join(" ", release.getTags()));

    for (Phase phase : release.getPhases()) {
      for (Task taskOrGroup : phase.getAllTasks()) {
        if (!taskOrGroup.getTaskType().toString().equals(TASK_SEQUENTIAL_GROUP)
            && !taskOrGroup.getTaskType().toString().equals(TASK_PARALLEL_GROUP)) {
          numTasks++;
          if (taskOrGroup.isAutomated())
            numAutomatedTasks += 1;

          if (taskOrGroup.getStatus() == TaskStatus.COMPLETED)
            numCompletedTasks += 1;
          else if (taskOrGroup.getStatus() == TaskStatus.SKIPPED)
            numSkippedTasks += 1;
          if (taskOrGroup.getFailuresCount() > 0)
            numRetriedTasks += 1;

          final long startTime = taskOrGroup.getStartOrScheduledDate() == null ? 0
              : taskOrGroup.getStartOrScheduledDate().getTime();
          final long endTime = taskOrGroup.getEndOrDueDate() == null ? 0
              : taskOrGroup.getEndOrDueDate().getTime();

          taskStmt.setString(1, release.getId());
          taskStmt.setString(2, phase.getId());
          taskStmt.setString(3, taskOrGroup.getId());
          taskStmt.setString(4, phase.getTitle());
          taskStmt.setString(5, taskOrGroup.getTaskType().toString());
          taskStmt.setInt(6, taskOrGroup.isAutomated() ? 1 : 0);
          taskStmt.setString(7, taskOrGroup.getTitle());
          taskStmt.setString(8, taskOrGroup.getOwner());
          taskStmt.setString(9, taskOrGroup.getTeam());
          taskStmt.setString(10, startTime > 0 ? sdf.format(taskOrGroup.getStartOrScheduledDate()) : null);
          taskStmt.setString(11, endTime > 0 ? sdf.format(taskOrGroup.getEndOrDueDate()) : null);
          taskStmt.setLong(12, (endTime - startTime) / 1000);
          taskStmt.setString(13, taskOrGroup.getStatus().toString());
          taskStmt.setInt(14, taskOrGroup.getFailuresCount());
          taskStmt.setString(15, taskOrGroup.getFlagStatus().toString());

          taskStmt.addBatch();
        }
      }
    }

    releaseStmt.setInt(15, numTasks);
    releaseStmt.setInt(16, numRetriedTasks);
    releaseStmt.setInt(17, numCompletedTasks);
    releaseStmt.setInt(18, numSkippedTasks);
    releaseStmt.setInt(19, numTasks > 0 ? numAutomatedTasks / numTasks : 0);

    int userStoryCount = 0;
    if (userStoriesVar != null) {
      Map<String, String> userStories = (Map<String, String>) userStoriesVar.getValue();
      for (Map.Entry<String, String> userStory : userStories.entrySet()) {
        userStoryCount++;
        userStoryStmt.setString(1, release.getId());
        userStoryStmt.setString(2, userStory.getKey());
        userStoryStmt.setString(3, userStory.getValue());
        userStoryStmt.addBatch();
      }
    }

    try {
      
      logger.debug("   About to try SQL... "); // DebugInfo
      
      // Delete data if release already exists
      if (release.getStatus() != ReleaseStatus.PLANNED) {
        deleteReleaseData(connConfig, dbConnection, release);
      }
      // Insert release and associated data
      releaseStmt.execute();
      if (numTasks > 0)
        taskStmt.executeBatch();
      if (userStoryCount > 0)
        userStoryStmt.executeBatch();

      dbConnection.commit();
      logger.info("Done exporting release {} - {}", release.getId(), release.getTitle());
    } catch (SQLException exception) {
      dbConnection.rollback();
      throw exception;
    } finally {
      dbConnection.close();
    }
  }

  private void deleteReleaseData(final ConfigurationItem dbConnectionConfig, final Connection dbConnection,
      final Release release) throws SQLException {

    final String releaseTableName = dbConnectionConfig.getProperty("releaseDetailsTable").toString();
    final String taskDetailsTableName = dbConnectionConfig.getProperty("taskDetailsTable").toString();
    final String userStoriesTableName = dbConnectionConfig.getProperty("userStoriesTable").toString();

    PreparedStatement releaseDelStmt = dbConnection
        .prepareStatement("delete from " + releaseTableName + " where release_id=?");
    PreparedStatement taskDelStmt = dbConnection
        .prepareStatement("delete from " + taskDetailsTableName + " where release_id=?");
    PreparedStatement userStoryDelStmt = dbConnection
        .prepareStatement("delete from " + userStoriesTableName + " where release_id=?");

    releaseDelStmt.setString(1, release.getId());
    taskDelStmt.setString(1, release.getId());
    userStoryDelStmt.setString(1, release.getId());

    releaseDelStmt.execute();
    taskDelStmt.execute();
    userStoryDelStmt.execute();

  }

  private Connection getDBConnection(final ConfigurationItem connConfig)
      throws IOException, SQLException, PropertyVetoException {
    final String databaseDriver = connConfig.getProperty("dbDriver").toString();
    logger.debug("Database driver is " + databaseDriver);
    final String databaseURL = connConfig.getProperty("JDBCUrl").toString();
    logger.debug("Database URL is " + databaseURL);
    final String username = connConfig.getProperty("username").toString();
    logger.debug("Database username is " + username);
    if (connConfig.getProperty("password") == null) {
        logger.debug("Password is null");
    }
    // logger.debug((String)connConfig.getProperty("password"));
    final String password = connConfig.getProperty("password").toString();
    logger.debug("Database password is " + password);

    // Class.forName(databaseDriver);
    // Connection dbConnection = DriverManager.getConnection(databaseURL,
    // username, password);
    // dbConnection.setAutoCommit(false);
    logger.debug("Trying to initialize SQL (DataSource)..."); 
    DataSource ds = DataSource.getInstance(databaseDriver, databaseURL, username, password);
    logger.debug("Got SQL (DataSource)."); 
    return ds.getConnection();
    
  }
}
