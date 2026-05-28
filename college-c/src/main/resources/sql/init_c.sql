CREATE DATABASE IF NOT EXISTS collegeC CHARACTER SET utf8mb4;
USE collegeC;

DROP TABLE IF EXISTS 选课;
DROP TABLE IF EXISTS 学生;
DROP TABLE IF EXISTS 课程;
DROP TABLE IF EXISTS 账户;

CREATE TABLE 账户 (
  acc        varchar(12)  NOT NULL PRIMARY KEY,
  passwd     varchar(12)  NOT NULL,
  CreateDate timestamp    NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE 学生 (
  Sno char(12)    NOT NULL PRIMARY KEY,
  Snm varchar(10) NOT NULL,
  Sex char(2)     NOT NULL,
  Sde varchar(10) NOT NULL,
  Pwd varchar(12) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE 课程 (
  Cno   char(4)       NOT NULL PRIMARY KEY,
  Cnm   varchar(20)   NOT NULL,
  Ctm   int           NOT NULL DEFAULT 32,
  Cpt   decimal(3,1)  NOT NULL,
  Tec   varchar(10)   NOT NULL,
  Pla   varchar(20)   NOT NULL,
  Share char(1)       NOT NULL
) ENGINE=InnoDB;

CREATE TABLE 选课 (
  Cno char(4)    NOT NULL,
  Sno char(12)   NOT NULL,
  Grd varchar(3) NULL,
  Org char(1)    NOT NULL DEFAULT 'C',
  FOREIGN KEY (Cno) REFERENCES 课程(Cno),
  UNIQUE KEY UK_SC (Sno, Cno)
) ENGINE=InnoDB;
