from sql.ExportClient import ExportClient


releaseDetailsTable =  request.query['r'] if 'r' in request.query else 'release_details'
taskDetailsTable    =  request.query['t'] if 't' in request.query else 'task_details'
userStoriesTable    =  request.query['u'] if 'u' in request.query else 'user_stories'

releases = releaseApi.releases

exportClient = ExportClient(releaseDetailsTable, taskDetailsTable, userStoriesTable)

resultJson=[]
sqlCmds = []
for release in releases:
    if release.status!='TEMPLATE' :
       resultJson.append({"release":exportClient.get_release_details_sql(release,'JiraProject','ApplicationVersion')})
       for task in exportClient.get_tasks_details_sql(release):
           resultJson.append({"task":task})
       for userstory in exportClient.get_user_stories_sql(release,'JiraUserStories') :
           resultJson.append({"userstory":userstory})

response.entity=resultJson
