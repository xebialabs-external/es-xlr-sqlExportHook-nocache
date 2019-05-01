import sys
import urllib
import com.xhaus.jyson.JysonCodec as json
import time

class ExportClient(object):

   def __init__(self, releaseDetailsTable=None, tasksDetailsTable=None, userStoriesTable=None):
       self.releaseDetailsTable = releaseDetailsTable
       self.taskDetailsTable = tasksDetailsTable
       self.userStoriesTable = userStoriesTable

   def get_release_details_sql(self, release=None, projectVarName=None,appVerVarName=None ):
       numTasks = 0
       numAutomatedTasks = 0
       numRetriedTasks = 0
       numCompletedTasks = 0
       numSkippedTasks = 0

       startReleaseMillis = release.startDate.getTime() if release.startDate is not None else 0
       endReleaseMillis = release.endDate.getTime() if release.endDate is not None else 0
       if release.endDate is not None:
          releaseDuration = (endReleaseMillis - startReleaseMillis) / 1000
       else:
            releaseDuration = 0

       for phase in release.phases:
           for taskOrGroup in phase.tasks:
               for task in self.expandGroup(taskOrGroup):
                   numTasks += 1
                   if self.oneIfAutomated(task.type):
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
                (self.releaseDetailsTable, self.stripApplication(release.id), self.stripApplication(self.noneToEmpty(release.originTemplateId)), self.escape(release.title),self.escape(release.owner) \
                 , self.getVariableByName(release.variables,projectVarName), self.getVariableByName(release.variables, appVerVarName)
                 , self.formatDateTime(startReleaseMillis/1000), self.formatDateTime(endReleaseMillis/1000), self.noneToZero(releaseDuration), self.noneToZero(release.plannedDuration) \
                 , self.oneIfOverrun(release.plannedDuration, releaseDuration), release.status \
                 , release.flagStatus, self.noneToEmpty(' '.join(release.tags)), numTasks, numRetriedTasks, numCompletedTasks, numSkippedTasks \
                 , self.divOrEmpty(numAutomatedTasks, numTasks) )
       return sqlCmd

   def get_tasks_details_sql(self,release=None):
       exportSQLs = []
       for phase in release.phases:
          for taskOrGroup in phase.tasks:
             for task in self.expandGroup(taskOrGroup):
                 startTaskMillis = task.startDate.getTime() if task.startDate is not None else 0
                 endTaskMillis = task.endDate.getTime() if task.endDate is not None else 0
                 sqlCmd="INSERT INTO %s VALUES ('%s','%s','%s','%s','%s',%d,'%s','%s','%s','%s','%s',%d,'%s',%d,'%s');" % \
                         (self.taskDetailsTable, self.stripApplication(release.id), self.stripApplication(phase.id), self.stripApplication(task.id), \
                         self.escape(phase.title), task.type, self.oneIfAutomated(task.type), self.escape(task.title), self.noneToEmpty(task.owner), self.noneToEmpty(task.team), \
                         self.formatDateTime(startTaskMillis/1000), self.formatDateTime(endTaskMillis/1000), (endTaskMillis - startTaskMillis) / 1000, task.status, \
                         task.failuresCount, task.flagStatus)
                 exportSQLs.append(sqlCmd)
             # end for 
          # end for
       # end for
       return exportSQLs
   # end def

   def get_user_stories_sql(self, release=None, userStoriesVarName=None):
       exportSQLs = []
       userStories = self.getVariableByName(release.variables, userStoriesVarName)
       if userStories is not None:
          for userStoryId, userStoryDescr in  userStories.items():
              sqlCmd="INSERT INTO %s VALUES ('%s','%s','%s');" % \
                      (self.userStoriesTable, self.stripApplication(release.id) , userStoryId, self.escape(userStoryDescr))
              exportSQLs.append(sqlCmd)
          # end for
       # end if
       return exportSQLs
   #end def

   def stripApplication(self,releaseId):
       return releaseId.replace('Applications/', '')
   # End def

   def escape(self,value):
       return value.replace("'","''") if value is not None else None
   # End def

   def getPhase(self, releaseId ):
      phaseId = re.sub(r'Applications/Release\d*/', '', releaseId)
      return re.sub(r'/Task.*', '', phaseId)
   # End def

   def getTask( self,releaseId ):
      phaseId = re.sub(r'Applications/Release.*/', '', releaseId)
      return re.sub(r'/Task.*', '', phaseId)
   # End def

   def noneToEmpty(self, value):
       if value is None:
          return ''
       else:
           return value
   # End def

   def noneToZero(self, value):
       if value is None:
          return 0 
       else:
           return value
   # End def

   def oneIfAutomated(self, taskType):
       if taskType == 'xlrelease.Task':
          return 0
       else:
           return 1
   # End def

   def oneIfOverrun(self, plannedDuration, duration):
       if plannedDuration and duration > plannedDuration:
          return 1
       else:
           return 0
   # End def

   def isEmpty(self, path):
       return os.path.getsize(path) == 0
   # End def

   def divOrEmpty(self, dividend, divisor):
       if divisor != 0:
           return float(dividend) / divisor
       else:
           return ''
   # End def

   def expandGroup(self,taskOrGroup):
       if taskOrGroup.type != 'xlrelease.ParallelGroup':
          return [taskOrGroup]
       else:
           tasks = []
           for task in taskOrGroup.tasks:
               tasks.extend(self.expandGroup(task))
           return tasks
   # End def

   def formatDateTime(self, datetimevalue):
       return time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(datetimevalue))

   def getVariableByName(self, variables, variableName):
       for variable in variables:
           if variable.key == variableName  :
               return variable.value
       return None
   # End def 


