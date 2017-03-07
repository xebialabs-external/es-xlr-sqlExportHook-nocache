from sql.ExportClient import ExportClient

releases = releaseApi.releases

exportClient = ExportClient('release_details','tasks_details','user_stories')

sqlCmds = []
for release in releases:
    if release.status!='TEMPLATE' :
       sqlCmds.append(exportClient.get_release_details_sql(release,'JiraProject','ApplicationVersion'))
       sqlCmds.extend(exportClient.get_tasks_details_sql(release))
       sqlCmds.extend(exportClient.get_user_stories_sql(release,'JiraUserStories'))

response.entity= sqlCmds
