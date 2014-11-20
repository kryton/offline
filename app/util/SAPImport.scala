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
    val file = new URI("file:///Users/iholsman/Documents/SAPWork/Final%2011-07-2014.txt")
    val employees = importFile(file)
    employees.foreach(x => println(s"${x.login},${x.managerID.getOrElse("-")},${x.reports},${x.name}"))
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
          val mgr = bits(14).trim match {
              case "" => None
              case x: String => Some(x)
            }
          val mgrID = if (bits.length == 19) None
          else bits(20).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val empID = bits(18).trim match {
            case "" => None
            case x: String => Some(x)
          }
          val exec = bits(15).trim match {
            case "" => None
            case x: String => Some(x)
          }

          val record = QuickBookImport(toLong(bits(0)).get, bits(1).trim, bits(2).trim, bits(3),
            toInt(bits(4)).getOrElse(0) /*CompanyCode */ ,
            bits(5), toLong(bits(6)).getOrElse(0) /* costCenter */ , bits(7), bits(8), bits(9),
            bits(10), /*skip EmpSubgroup */ bits(12), bits(13).trim, mgr, exec,
            toDate(bits(16)), None /*TerminationDate*/ ,
            empID,
            toLong(bits(19)),
            mgrID,
            None /*officeLocation*/)
          Some(record)
        } else {
          None
        }
    }.flatten.toList
    val byId = employees.map(x => x.personNumber -> x).toMap

    val v = employees.map(x => (x.managerID, x.login.get, x.personNumber, 0,0,0, x.personalSubArea))

    val v2 = genTree(v)

    val empRels = v2.map {
      empWithCounts =>
        val empRecordO = byId.get(empWithCounts._3)
        val mgrRecord = byId.get(empWithCounts._1.getOrElse(0L))

        val mgrLogin = mgrRecord match {
          case Some(x) => x.login
          case None => None
        }

        //  val empRel: EmpRelation()
        empRecordO match {
          case Some(empRecord) =>
            val empRel: EmpRelation = EmpRelation(empRecord.personNumber, empRecord.login.get,
              empRecord.firstName, empRecord.lastName, mgrLogin,
              empWithCounts._4 /* directs */, empWithCounts._5 /* reports */, empWithCounts._6 /* reportsContractors*/,
              empRecord.companyCode, empRecord.companyCodeName, empRecord.costCenter, empRecord.costCenterText,
              empRecord.personalArea, empRecord.personalSubArea, empRecord.employeeGroup, empRecord.position,
              empRecord.job,
              empRecord.executiveName, empRecord.officeLocation)
            Some(empRel)
          case None => None
        }
    }.flatten
    empRels

  }


  def genTree(employees: List[(Option[Long], String, Long, Int,Int,Int,String)], i: Int = 1): List[(Option[Long], String, Long, Int,Int,Int,String)] = {
    val mgrs2 = employees.groupBy(_._1)
    val mgrs = mgrs2.map(x => x._1 -> (x._2.length + x._2.map(_._4).sum)).toMap
    //mgrs2.filter(x => x._1 == Some("Jina Fritz")).foreach(x => println(s"$i MGR Jina - "+ x._2.map( y => y._2 + " "+ y._4 )))
   // val foo = employees.count( x=> x._1 == Some(4603))
   // println(foo)
    // Add Direct reports in here
    val noReports = employees.filter(emp => mgrs.get(Some(emp._3)).isEmpty).map(
       x => (x._1, x._2, x._3, employees.count( y=> y._1 == Some( x._3)), x._5, x._6, x._7)
    )
    val reports = employees.filter(emp => mgrs.get(Some(emp._3)).isDefined).map(
      emp2 => (emp2._1, emp2._2, emp2._3,
        noReports.filter(x => x._1 == Some(emp2._3)).map(y => y._5).sum + noReports.count(x => x._1 == Some(emp2._3)), /* directs */
        emp2._5 + noReports.filter(x => x._1 == Some(emp2._3)).map(y => y._5).sum + noReports.count(x => x._1 == Some(emp2._3)), /*all*/
        emp2._6 + noReports.filter(x => x._1 == Some(emp2._3) && x._7.equalsIgnoreCase("Contract")).map(y => y._6).sum ,
        emp2._7
        )
    )


    reports.count(x => x._1.isDefined) match {
      case 0 => reports ::: noReports
      case _ => genTree(reports, i + 1) ::: noReports
    }
  }
}
