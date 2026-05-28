-- college-a/src/main/resources/sql/init_a.sql
IF DB_ID('collegeA') IS NULL CREATE DATABASE collegeA COLLATE Chinese_PRC_CI_AS;
GO
USE collegeA;
GO

IF OBJECT_ID('选课', 'U') IS NOT NULL DROP TABLE 选课;
IF OBJECT_ID('学生', 'U') IS NOT NULL DROP TABLE 学生;
IF OBJECT_ID('课程', 'U') IS NOT NULL DROP TABLE 课程;
IF OBJECT_ID('账户', 'U') IS NOT NULL DROP TABLE 账户;
GO

CREATE TABLE 账户 (
  账户名 varchar(10)  NOT NULL PRIMARY KEY,
  密码   varchar(6)   NOT NULL,
  权限   char(4)      NOT NULL
);

CREATE TABLE 学生 (
  学号     varchar(12)  NOT NULL PRIMARY KEY,
  姓名     nvarchar(10) NOT NULL,
  性别     nvarchar(2)  NOT NULL,
  院系     nvarchar(10) NOT NULL,
  关联账户 varchar(10)  NULL FOREIGN KEY REFERENCES 账户(账户名)
);

CREATE TABLE 课程 (
  课程编号 varchar(8)   NOT NULL PRIMARY KEY,
  课程名称 nvarchar(20) NOT NULL,
  学分     varchar(2)   NOT NULL,
  授课老师 nvarchar(10) NOT NULL,
  授课地点 nvarchar(20) NOT NULL,
  共享     char(1)      NOT NULL,
  课时     int          NOT NULL DEFAULT 32
);

CREATE TABLE 选课 (
  课程编号  varchar(8)  NOT NULL FOREIGN KEY REFERENCES 课程(课程编号),
  学生编号  varchar(12) NOT NULL,
  成绩      varchar(3)  NULL,
  来源      char(1)     NOT NULL DEFAULT 'A',
  CONSTRAINT UK_选课 UNIQUE (课程编号, 学生编号)
);
GO
