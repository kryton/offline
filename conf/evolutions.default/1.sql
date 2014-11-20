# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

CREATE TABLE EmpRelations (
  `PersonNumber` int(11) NOT NULL DEFAULT '0',
   Login varchar(20) ,
  `FirstName` varchar(255) NOT NULL,
  `LastName` varchar(255) NOT NULL,
   ManagerID varchar(20),
    directs int(11) not null,
    reports int(11) not null,
    reportsContractor int(11) not NULL,
  `CompanyCode` int(11) NOT NULL,
  `CompanyCodeName` varchar(255) NOT NULL,
  `CostCenter` int(11) NOT NULL,
  `CostCenterText` varchar(45) NOT NULL,
  `PersonalArea` varchar(45) NOT NULL,
  `PersonalSubArea` varchar(45) NOT NULL,
  `EmployeeGroup` varchar(45) NOT NULL,
  `Position` varchar(255) NOT NULL,
  `Job` varchar(255) NOT NULL,
    `ExecutiveName` varchar(255)  NULL,
    `OfficeLocation` varchar(255)  NULL,

  PRIMARY KEY (`PersonNumber`),
  UNIQUE KEY `Login_UNIQUE` (`Login`),
  foreign key (ManagerID) references EmpRelations(Login) on DELETE restrict on update RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

# --- !Downs

drop table EmpRelations;
