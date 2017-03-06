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

def stripApplication(releaseId):
    return releaseId.replace('Applications/', '')
# End def

def escape(value):
    return value.replace("'","''") if value is not None else None
# End def

def getPhase( releaseId ):
   phaseId = re.sub(r'Applications/Release\d*/', '', releaseId)
   return re.sub(r'/Task.*', '', phaseId)
# End def

def getTask( releaseId ):
   phaseId = re.sub(r'Applications/Release.*/', '', releaseId)
   return re.sub(r'/Task.*', '', phaseId)
# End def

def noneToEmpty(value):
    if value is None:
        return ''
    else:
        return value
# End def

def noneToZero(value):
    if value is None:
        return 0 
    else:
        return value
# End def

def oneIfAutomated(taskType):
    if taskType == 'xlrelease.Task':
        return 0
    else:
        return 1
# End def

def oneIfOverrun(plannedDuration, duration):
    if plannedDuration and duration > plannedDuration:
        return 1
    else:
        return 0
# End def

def isEmpty(path):
    return os.path.getsize(path) == 0
# End def

def divOrEmpty(dividend, divisor):
    if divisor != 0:
        return float(dividend) / divisor
    else:
        return ''
# End def

def expandGroup(taskOrGroup):
    if taskOrGroup.type != 'xlrelease.ParallelGroup':
        return [taskOrGroup]
    else:
        tasks = []
        for task in taskOrGroup.tasks:
            tasks.extend(expandGroup(task))
        return tasks
# End def

def formatDateTime(datetimevalue):
    return time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(datetimevalue))

def getVariableByName(variables, variableName):
    for variable in variables:
        if variable.key == variableName  :
            return variable.value
    return None
# End def

          
logger.info("Starting SQL export of the release %s" % release.id)
sqlScratchFile="/tmp/releaseDetails.sql"

startReleaseMillis = release.startDate.getTime()
endReleaseMillis = release.endDate.getTime()
releaseDuration = (endReleaseMillis - startReleaseMillis) / 1000

logger.debug("Writing release details for %s" % release.id)

sql_command_file = open(sqlScratchFile, 'a')
con = DriverManager.getConnection( exportHook.JDBCUrl, exportHook.username, exportHook.password )
try:
    stmt = con.createStatement()
    numTasks = 0
    numAutomatedTasks = 0
    numRetriedTasks = 0
    numCompletedTasks = 0
    numSkippedTasks = 0
    for phase in release.phases:
        for taskOrGroup in phase.tasks:
            for task in expandGroup(taskOrGroup):
                numTasks += 1
                if oneIfAutomated(task.type):
                    numAutomatedTasks += 1
                if task.status == 'COMPLETED':
                    numCompletedTasks += 1
                elif task.status == 'SKIPPED':
                    numSkippedTasks += 1
                if task.failuresCount:
                    numRetriedTasks += 1
    
    sqlCmd = "INSERT INTO %s (Release_ID, Template_ID, Release_Title, Release_Owner, Application_Name, Application_Version, Release_Start_Time, Release_End_Time, Release_Duration, Planned_Release_Duration \
                , Is_Release_Delayed , Release_Status, Release_Flag_Status, Release_Tags, No_Tasks, No_Retried_Tasks, No_Completed_Tasks, No_Skipped_Tasks , Automation ) \
              VALUES ('%s','%s','%s','%s','%s','%s','%s','%s',%d,%s,%d,'%s','%s','%s',%d,%s,%s,%s,%s);" % \
              (exportHook.releaseDetailsTable, stripApplication(release.id), stripApplication(noneToEmpty(release.originTemplateId)), escape(release.title), escape(release.owner) \
                 , getVariableByName(release.variables,exportHook.jiraProjectVariableName), getVariableByName(release.variables,exportHook.jiraProjectReleaseVersion)
                 , formatDateTime(startReleaseMillis/1000), formatDateTime(endReleaseMillis/1000), noneToZero(releaseDuration), noneToZero(release.plannedDuration) \
                 , oneIfOverrun(release.plannedDuration, releaseDuration), release.status \
                 , release.flagStatus, noneToEmpty(' '.join(release.tags)), numTasks, numRetriedTasks, numCompletedTasks, numSkippedTasks \
                 , divOrEmpty(numAutomatedTasks, numTasks) )
    logger.debug( sqlCmd )
    sql_command_file.write( sqlCmd )
    rs = stmt.execute( sqlCmd )
finally:
    sql_command_file.close()
    con.close()
    
logger.debug("Writing task details for %s" % release.id )

sql_command_file = open(sqlScratchFile, 'a')
con = DriverManager.getConnection( exportHook.JDBCUrl, exportHook.username, exportHook.password )
try:
    stmt = con.createStatement()
    for phase in release.phases:
        for taskOrGroup in phase.tasks:
            for task in expandGroup(taskOrGroup):
                startTaskMillis = task.startDate.getTime()
                endTaskMillis = task.endDate.getTime()
                sqlCmd="INSERT INTO %s VALUES ('%s','%s','%s','%s','%s',%d,'%s','%s','%s','%s','%s',%d,'%s',%d,'%s');\n" % \
                (exportHook.taskDetailsTable, stripApplication(release.id), stripApplication(phase.id), stripApplication(task.id), \
                 escape(phase.title), task.type, oneIfAutomated(task.type), escape(task.title), noneToEmpty(task.owner), noneToEmpty(task.team), \
                 formatDateTime(startTaskMillis/1000), formatDateTime(endTaskMillis/1000), (endTaskMillis - startTaskMillis) / 1000, task.status, \
                 task.failuresCount, task.flagStatus)
                logger.debug( sqlCmd )
                sql_command_file.write( sqlCmd )
                rs = stmt.execute( sqlCmd )
finally:
    sql_command_file.close()
    con.close()

logger.debug("Writing User Stories detail for %s" % release.id )

sql_command_file = open(sqlScratchFile, 'a')
con = DriverManager.getConnection( exportHook.JDBCUrl, exportHook.username, exportHook.password )
try:
    stmt = con.createStatement()
    userStories = getVariableByName(release.variables,exportHook.userStoriesVariableName)
    if userStories is not None:
        for userStoryId, userStoryDescr in  userStories.items():
            sqlCmd="INSERT INTO %s VALUES ('%s','%s','%s');\n" % \
            (exportHook.userStoriesTable, stripApplication(release.id) , userStoryId, escape(userStoryDescr))
            logger.debug( sqlCmd )
            sql_command_file.write( sqlCmd )
            rs = stmt.execute( sqlCmd )
finally:
    sql_command_file.close()
    con.close()

logger.info("Completed SQL export of the release %s" % release.id)
