package controllers

import java.text.SimpleDateFormat

import play.api.db.slick.DBAction
import models._
import play.api.Play.current
import play.api.data._
import play.api.mvc._
import play.filters.csrf.CSRFCheck
import util.SAPImport
import scala.concurrent.duration.DurationInt
import scala.io.Source
import play.api.db.slick.Config.driver.simple._

/**
 * Created by iholsman on 16/09/2014.
 */
object ImportController extends Controller {
  def toInt(s: String): Option[Int] = {
    try {
      Some(s.trim.toInt)
    } catch {
      case e: Exception => println(s"TOint:$s"); None
    }
  }

  def toLong(s: String): Option[Long] = {
    try {
      Some(s.trim.toLong)
    } catch {
      case e: Exception => println(s"TOLONG:$s - ${e.getMessage}"); None
    }
  }

  def toDate(s: String): Option[java.sql.Date] = {
    val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("mm.dd.yyyy")
    try {
      Some(new java.sql.Date(simpleDateFormat.parse(s).getTime))

    } catch {
      case e: Exception => println(s"Date:$s - ${e.getMessage}"); None
    }
  }

  implicit val timeout = 10.seconds

  def index = DBAction { implicit rs =>
    Ok(views.html.QuickbooksImport("Offline"))
  }

  def doImport = CSRFCheck {
    DBAction(parse.multipartFormData) { implicit rs =>
    //  println("InHere")
      rs.request.body.file("importFile").map { file =>
        //println("about to import")
        val employees = SAPImport.importFile(file.ref.file)
      //  println("About to adjust DB")
        play.api.db.slick.DB.withTransaction { implicit session =>
          EmpRelations.repopulate(employees)
        }
        //employees.foreach(x => println(s"${x.login} - ${x.mangerID.getOrElse("_")} - ${x.reports} - ${x.name}"))
      }

      Ok("done")
    }

  }

}
