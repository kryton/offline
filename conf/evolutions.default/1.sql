# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

CREATE TABLE EmpRelations (
    `PersonNumber`    INT(11)      NOT NULL DEFAULT '0',
    Login             VARCHAR(20),
    `FirstName`       VARCHAR(255) NOT NULL,
    NickName          VARCHAR(255) NULL,
    `LastName`        VARCHAR(255) NOT NULL,
    ManagerID         VARCHAR(20),
    directs           INT(11)      NOT NULL,
    reports           INT(11)      NOT NULL,
    reportsContractor INT(11)      NOT NULL,
    `CompanyCode`     INT(11)      NOT NULL,
    `CompanyCodeName` VARCHAR(255) NOT NULL,
    `CostCenter`      INT(11)      NOT NULL,
    `CostCenterText`  VARCHAR(45)  NOT NULL,
    `PersonalArea`    VARCHAR(45)  NOT NULL,
    `PersonalSubArea` VARCHAR(45)  NOT NULL,
    `EmployeeGroup`   VARCHAR(45)  NOT NULL,
    `Position`        VARCHAR(255) NOT NULL,
    `Job`             VARCHAR(255) NOT NULL,
    `ExecutiveName`   VARCHAR(255) NULL,
    `OfficeLocation`  VARCHAR(255) NULL,

    PRIMARY KEY (`PersonNumber`),
    UNIQUE KEY `Login_UNIQUE` (`Login`),
    FOREIGN KEY (ManagerID) REFERENCES EmpRelations (Login)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
)
    ENGINE =InnoDB
    DEFAULT CHARSET =utf8;

# --- !Downs

DROP TABLE EmpRelations;
