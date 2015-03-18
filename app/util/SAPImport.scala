package util

import java.io.File
import java.net.URI
import java.text.SimpleDateFormat

import models.{EmpRelation, QuickBookImport}
import java.nio.charset.CodingErrorAction
import scala.io.{BufferedSource, Codec}


/**
 * Created by iholsman on 26/09/2014.
 */
object SAPImport {
  def main(args: Array[String]) {
    val file = new URI("file:///Users/iholsman/Documents/SAPWork/Final%2011-21-2014.txt")
    val employees = importFile(file)
    employees.foreach(x => println(s"${x.login},${x.managerID.getOrElse("-")},${x.directs},${x.reports},${x.reportsContractor},(${x.name})"))
  }

  private def toInt(s: String): Option[Int] = {
    try {
      Some(s.trim.toInt)
    } catch {
      case e: Exception => println(s"TOint:$s"); None
    }
  }

  private def toLong(s: String): Option[Long] = {
    try {
      Some(s.trim.toLong)
    } catch {
      case e: Exception => println(s"TOLONG:$s - ${e.getMessage}"); None
    }
  }

  private def toDate(s: String): Option[java.sql.Date] = {
    val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("mm/dd/yyyy")
    s match {
      case "" => None
      case _ => try {
        Some(new java.sql.Date(simpleDateFormat.parse(s).getTime))

      } catch {
        case e: Exception => println(s"Date:$s - ${e.getMessage}"); None
      }
    }
  }

  def importFile(file: File): List[EmpRelation] = {
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    importFile(scala.io.Source.fromFile(file))
  }

  def importFile(file: URI): List[EmpRelation] = {
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    importFile(scala.io.Source.fromFile(file))
  }

  private def importFile(buffer: BufferedSource): List[EmpRelation] = {

    val employees = buffer.getLines().map {
      line =>
        if (!line.startsWith("Pers No|")) {
          val bits: Array[String] = line.split("\\|", -1) //.drop(1)
          val nickName = bits(2).trim match {
              case "" => None
              case x: String => Some(x)
            }

          val mgrNumber = bits(15).trim match {
              case "" => None
              case x: String => toLong(x)
          }
          val mgr = bits(16).trim match {
              case "" => None
              case x: String => Some(x)
          }

          val mgrID = bits(17).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val empID = bits(21).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val exec = bits(18).trim match {
            case "" => None
            case x: String => Some(x)
          }

          val termDate = bits(20).trim match {
            case "" => None
            case x: String => if ( x.equals("00/00/0000")) None else { toDate(x)}
          }
          val workstation = bits(22).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val workstation2 = bits(23).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val employeeType = bits(24).trim match {
            case "" => None
            case x: String => Some(x)
          }

          val officeCity = bits(25).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val officeStreet = bits(26).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val officePOBox = bits(27).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val officeRegion = bits(28).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val officeZipCode= bits(29).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val officeCountry = bits(30).trim match {
            case "" => None
            case x: String => Some(x)
          }

          val record = QuickBookImport(toLong(bits(0)).get, bits(1).trim, nickName, bits(3).trim, bits(4),
            toInt(bits(5)).getOrElse(0) /*CompanyCode */ ,
            bits(6), toLong(bits(7)).getOrElse(0) /* costCenter */ , bits(8), bits(9), bits(10),
            bits(11), /*skip EmpSubgroup */ bits(13) /* position */, bits(14).trim /* agency */,
            mgrNumber, mgrID, mgr, exec,
            toDate(bits(19)), termDate /*TerminationDate*/,
            empID,
            workstation,workstation2,
            employeeType, officeCity,officeStreet,officePOBox, officeRegion, officeZipCode, officeCountry)
          Some(record)
        } else {
          None
        }
    }.flatten.toList
    val byId = employees.map(x => x.personNumber -> x).toMap

    val v = employees.map(x =>MgrHeirarchy (x.managerID, x.login.get, x.personNumber, 0,0,0, x.isFTE))

    val v2 = genTree(v)

    val empRels = v2.map {
      empWithCounts =>
        val empRecordO = byId.get(empWithCounts.personNumber)
        val mgrRecord = byId.get(empWithCounts.managerID.getOrElse(0L))

        val mgrLogin = mgrRecord match {
          case Some(x) => x.login
          case None => None
        }

        //  val empRel: EmpRelation()
        empRecordO match {
          case Some(empRecord) =>
            val empRel: EmpRelation = EmpRelation(empRecord.personNumber, empRecord.login.get,
              empRecord.firstName, empRecord.nickName, empRecord.lastName, mgrLogin,
              empWithCounts.directs /* directs */, empWithCounts.reports /* reports */, empWithCounts.reportsContractors /* reportsContractors*/,
              empRecord.companyCode, empRecord.companyCodeName, empRecord.costCenter, empRecord.costCenterText,
              empRecord.personalArea, empRecord.personalSubArea, empRecord.employeeGroup, empRecord.position,
              empRecord.agency,
              empRecord.executiveName, empRecord.officeLocation, empRecord.officeLocation2, empRecord.employeeType)
            Some(empRel)
          case None => None
        }
    }.flatten
    empRels

  }

case class MgrHeirarchy(managerID:Option[Long],login:String,personNumber:Long,directs:Int,reports:Int,reportsContractors:Int,isFTE:Boolean)

  def genTree(employees: List[MgrHeirarchy], i: Int = 1): List[MgrHeirarchy] = {
    val mgrs2 = employees.groupBy(_.managerID)
    val mgrs = mgrs2.map(x => x._1 -> (x._2.length + x._2.map(_.directs).sum)).toMap
    //mgrs2.filter(x => x._1 == Some("Jina Fritz")).foreach(x => println(s"$i MGR Jina - "+ x._2.map( y => y._2 + " "+ y._4 )))
   // val foo = employees.count( x=> x._1 == Some(4603))
   // println(foo)
    // Add Direct reports in here
    val noReports = employees.filter(emp => mgrs.get(Some(emp.personNumber)).isEmpty).map(
       x => MgrHeirarchy(x.managerID, x.login, x.personNumber,
         x.directs + employees.count( y=> y.managerID == Some( x.personNumber)) +0,
         x.reports,
         x.reportsContractors,
         x.isFTE)
    )
    val reports = employees.filter(emp => mgrs.get(Some(emp.personNumber)).isDefined).map(
      emp2 => MgrHeirarchy(emp2.managerID, emp2.login, emp2.personNumber,
        emp2.directs + noReports.count(x => x.managerID == Some(emp2.personNumber)), /* directs */
        emp2.reports + noReports.filter(x => x.managerID == Some(emp2.personNumber)).map(y => y.reports).sum +
                       noReports.count(x => x.managerID == Some(emp2.personNumber)), /*all*/
        emp2.reportsContractors + noReports.filter(x => x.managerID == Some(emp2.personNumber)).map(y => y.reportsContractors).sum +
                       noReports.count(x => x.managerID == Some(emp2.personNumber)&& !x.isFTE),
        emp2.isFTE
        )
    )

    reports.count(x => x.managerID.isDefined) match {
      case 0 => reports ::: noReports
      case _ => genTree(reports, i + 1) ::: noReports
    }
  }
}
