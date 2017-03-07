#
# THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
# FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
#
import os
import re
import sys
from java.lang import Class
from java.sql  import DriverManager, SQLException
import time

from sql.ExportClient import ExportClient

exportSQLs = []

exportClient = ExportClient(exportHook.releaseDetailsTable, exportHook.taskDetailsTable, exportHook.userStoriesTable)
exportSQLs.append(exportClient.get_release_details_sql(release, exportHook.jiraProjectVariableName, exportHook.jiraProjectReleaseVersion))
exportSQLs.extend(exportClient.get_tasks_details_sql(release))
exportSQLs.extend(exportClient.get_user_stories_sql(release, exportHook.userStoriesVariableName))


# Inserting records into databse -----------------------------------------------------
con = None
try:
    con = DriverManager.getConnection( exportHook.JDBCUrl, exportHook.username, exportHook.password )
    stmt = con.createStatement() 
    for sqlCmd in exportSQLs:
        rs = stmt.execute( sqlCmd )
finally:
    if con not None:
       con.close()

logger.info("Completed SQL export of the release %s" % release.id)
