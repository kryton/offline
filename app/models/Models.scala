package models

import java.sql.Date

import scala.slick.lifted.Tag
import scala.slick.util.Logging
import play.api.db.slick.Config.driver.simple._
//import scala.slick.driver.JdbcDriver.backend.Database


/**
 * Created by iholsman on 16/09/2014.
 */
case class QuickBookImport(
                            personNumber: Long,
                            firstName: String,
                            nickName: Option[String],
                            lastName: String,
                            employeeStatus: String,
                            companyCode: Int,
                            companyCodeName: String,
                            costCenter: Long,
                            costCenterText: String,
                            personalArea: String,
                            personalSubArea: String,
                            employeeGroup: String,
                            position: String,
                            job: String,
                            managerName: Option[String],
                            executiveName: Option[String],
                            hireRehireDate: Option[Date],
                            terminationDate: Option[Date],
                            login: Option[String],
                            managerID: Option[Long],
                            managerLogin: Option[String],
                            officeLocation: Option[String]
                            )

case class EmpRelation(
                         personNumber: Long,
                         login: String,
                         firstName: String,
                         nickName: Option[String],
                         lastName: String,
                         managerID: Option[String],
                         directs: Long,
                         reports: Long,
                         reportsContractor: Long,
                         companyCode: Int,
                         companyCodeName: String,
                         costCenter: Long,
                         costCenterText: String,
                         personalArea: String,
                         personalSubArea: String,
                         employeeGroup: String,
                         position: String,
                         job: String,
                         executiveName: Option[String],
                         officeLocation: Option[String]
                         ) {
  def name: String = {
    firstName + " " + lastName
  }
}


class EmpRelations(tag: Tag) extends Table[EmpRelation](tag, "EmpRelations") with Logging {
  def personNumber = column[Long]("PersonNumber", O.NotNull)
  def login = column[String]("Login", O.Nullable)
  def firstName = column[String]("Firstname", O.NotNull)
  def nickName = column[String]("NickName", O.Nullable)
  def lastName = column[String]("LastName", O.NotNull)
  def managerID = column[String]("ManagerID", O.Nullable)
  def directs = column[Long]("Directs", O.NotNull)
  def reports = column[Long]("Reports", O.NotNull)
  def reportsContractor = column[Long]("ReportsContractor", O.NotNull)
  def companyCode = column[Int]("CompanyCode", O.NotNull)
  def companyCodeName = column[String]("CompanyCodeName", O.NotNull)
  def costCenter = column[Long]("CostCenter", O.NotNull)
  def costCenterText = column[String]("CostCenterText", O.NotNull)
  def personalArea = column[String]("PersonalArea", O.NotNull)
  def personalSubArea = column[String]("PersonalSubArea", O.NotNull)
  def employeeGroup = column[String]("EmployeeGroup", O.NotNull)
  def position = column[String]("Position", O.NotNull)
  def job = column[String]("Job", O.NotNull)
  def executiveName = column[String]("ExecutiveName", O.Nullable)
  def officeLocation = column[String]("OfficeLocation", O.Nullable)

  def * = (personNumber, login, firstName, nickName.?, lastName, managerID.?, directs, reports, reportsContractor,
    companyCode, companyCodeName,
    costCenter, costCenterText, personalArea, personalSubArea, employeeGroup, position, job,
    executiveName.?,  officeLocation.?) <>((EmpRelation.apply _).tupled, EmpRelation.unapply)
}

object EmpRelations {
  val tableQuery = TableQuery[EmpRelations]

  def truncate(implicit session: Session) = {
    tableQuery.delete
  }
  def findByLogin(login:String)(implicit session: Session): Option[EmpRelation] = {
      tableQuery.filter(_.login === login).firstOption
  }

  def create(obj: EmpRelation)(implicit session: Session): Unit = {
    tableQuery += obj
  }

  def all(implicit s: Session) = {
    tableQuery.sortBy(_.personNumber)
  }

  def repopulate(list:List[EmpRelation])( implicit session:Session): Unit = {
      tableQuery.delete(session)
      println(s"List = ${list.length} ")
      list foreach {
         item =>  tableQuery.insert(item)(session)
      }
  }
}
