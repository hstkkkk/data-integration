-- college-b/src/main/resources/sql/init_b.sql

ALTER SESSION SET CONTAINER=freepdb1;
CREATE USER collegeb IDENTIFIED BY collegeb;
GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO collegeb;

CONNECT collegeb/collegeb@freepdb1;

DROP TABLE 选课 CASCADE CONSTRAINTS;
DROP TABLE 学生 CASCADE CONSTRAINTS;
DROP TABLE 课程 CASCADE CONSTRAINTS;
DROP TABLE 账户 CASCADE CONSTRAINTS;

CREATE TABLE 账户 (
  账户名 varchar2(12) PRIMARY KEY,
  密码   varchar2(12) NOT NULL,
  级别   number(2)    NOT NULL,
  客体   varchar2(9)
);

CREATE TABLE 学生 (
  学号 varchar2(12) PRIMARY KEY,
  姓名 varchar2(10) NOT NULL,
  性别 varchar2(2)  NOT NULL,
  专业 varchar2(10) NOT NULL,
  密码 varchar2(12) NOT NULL
);

ALTER TABLE 账户 ADD CONSTRAINT FK_账户_客体 FOREIGN KEY (客体) REFERENCES 学生(学号);

CREATE TABLE 课程 (
  编号 varchar2(5)   PRIMARY KEY,
  名称 varchar2(20)  NOT NULL,
  课时 number         NOT NULL,
  学分 number(3,1)    NOT NULL,
  老师 varchar2(10)  NOT NULL,
  地点 varchar2(20)  NOT NULL,
  共享 char(1)       NOT NULL
);

CREATE TABLE 选课 (
  课程编号 varchar2(5)  NOT NULL REFERENCES 课程(编号),
  学号     varchar2(12) NOT NULL REFERENCES 学生(学号),
  得分     varchar2(3)
);
