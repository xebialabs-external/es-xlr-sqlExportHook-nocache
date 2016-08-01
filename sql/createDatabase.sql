CREATE DATABASE IF NOT EXISTS xlrelease;

USE xlrelease;

CREATE TABLE IF NOT EXISTS release_details (
   Release_ID               VARCHAR(20) NOT NULL PRIMARY KEY,
   Template_ID              VARCHAR(20),
   Release_Title            VARCHAR(255),
   Release_Owner            VARCHAR(32),
   Release_Start_Time       DATETIME,
   Release_End_Time         DATETIME,
   Release_Duration         FLOAT,
   Planned_Release_Duration FLOAT,
   Is_Release_Delayed       INT,
   Release_Status           VARCHAR(15),
   Release_Flag_Status      VARCHAR(15),
   Release_Tags             VARCHAR(15),
   No_Tasks                 INT,
   No_Retried_Tasks         INT,
   No_Completed_Tasks       INT,
   No_Skipped_Tasks         INT,
   Automation               INT
);

CREATE TABLE IF NOT EXISTS task_details (
   Release_ID               VARCHAR(20) NOT NULL,
   Phase_ID                 VARCHAR(20) NOT NULL,
   Task_ID                  VARCHAR(20) NOT NULL,
   Phase_Title              VARCHAR(255),
   Task_Type                VARCHAR(32),
   Is_Automated_Task        INT,
   Task_Title               VARCHAR(32),
   Task_Owner               VARCHAR(32),
   Task_Team                VARCHAR(32),
   Task_Start_Time          DATETIME,
   Task_End_Time            DATETIME,
   Task_Duration            FLOAT,
   Task_Status              VARCHAR(15),
   Task_Failure_Count       INT,
   Task_Flag_Status         VARCHAR(15),
   PRIMARY KEY( Release_ID, Phase_ID, Task_ID )
);

