# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table `EmpRelations` (`PersonNumber` BIGINT NOT NULL,`Login` VARCHAR(254),`Firstname` VARCHAR(254) NOT NULL,`NickName` VARCHAR(254),`LastName` VARCHAR(254) NOT NULL,`ManagerID` VARCHAR(254),`Directs` BIGINT NOT NULL,`Reports` BIGINT NOT NULL,`ReportsContractor` BIGINT NOT NULL,`CompanyCode` INTEGER NOT NULL,`CompanyCodeName` VARCHAR(254) NOT NULL,`CostCenter` BIGINT NOT NULL,`CostCenterText` VARCHAR(254) NOT NULL,`PersonalArea` VARCHAR(254) NOT NULL,`PersonalSubArea` VARCHAR(254) NOT NULL,`EmployeeGroup` VARCHAR(254) NOT NULL,`Position` VARCHAR(254) NOT NULL,`Job` VARCHAR(254) NOT NULL,`ExecutiveName` VARCHAR(254),`OfficeLocation` VARCHAR(254));

# --- !Downs

drop table `EmpRelations`;

