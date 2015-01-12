package models

/**
 * Created by iholsman on 9/01/2015.
 */

import java.sql.Date

import com.typesafe.config.ConfigFactory
import controllers.routes
import play.api.db.slick.Config.driver.simple._

import scala.slick.jdbc.StaticQuery
import scala.slick.lifted.Tag
import scala.slick.util.Logging

case class KudosToPerson(id: Option[Long],
                         fromPerson: String, toPerson: String, dateAdded: Date,
                         feedback: String,
                         rejected: Boolean,
                         rejectedBy: Option[String],
                         rejectedOn: Option[Date],
                         rejectedReason: Option[String])


class KudosToPeople(tag: Tag) extends Table[KudosToPerson](tag, "KudosTo") with Logging {
  def id: Column[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def fromPerson = column[String]("FromPerson", O.NotNull)

  def toPerson = column[String]("ToPerson", O.NotNull)

  def dateAdded = column[Date]("DateAdded", O.NotNull)

  def feedback = column[String]("Feedback", O.NotNull)

  def rejected = column[Boolean]("Rejected", O.NotNull)

  def rejectedBy = column[String]("RejectedBy", O.Nullable)

  def rejectedOn = column[Date]("RejectedOn", O.Nullable)

  def rejectedReason = column[String]("RejectedReason", O.Nullable)

  def * = (id.?, fromPerson, toPerson, dateAdded, feedback, rejected, rejectedBy.?, rejectedOn.?, rejectedReason.?) <>
    ((KudosToPerson.apply _).tupled, KudosToPerson.unapply)
}

object KudosToPeople {
  val tableQuery = TableQuery[KudosToPeople]

  def truncate(implicit session: Session) = {
    tableQuery.delete
  }

  def findByFrom(login: String)(implicit session: Session): List[KudosToPerson] = {
    tableQuery.filter(_.rejected === false).filter(_.fromPerson.toLowerCase === login.toLowerCase).sortBy(_.dateAdded.desc).list
  }

  def findByTo(login: String)(implicit session: Session): List[KudosToPerson] = {
    tableQuery.filter(_.rejected === false).filter(_.toPerson.toLowerCase === login.toLowerCase).sortBy(_.dateAdded.desc).list
  }

  def create(to: String, obj: KudosToPerson)(implicit session: Session): Unit = {
    val newObj = obj.copy(toPerson = to.toLowerCase, fromPerson = obj.fromPerson.toLowerCase)
    tableQuery += newObj
    val id = (StaticQuery[Long] + "SELECT LAST_INSERT_ID()").first
    genEmail(newObj.copy(id=Some(id)))

  }

  def genEmail(obj: KudosToPerson): Unit = {
    import play.libs.mailer.{Email, MailerPlugin}

    import scala.collection.JavaConversions._;

    val admins = ConfigFactory.load().getStringList("kudos.admins").toList
    val emailDomain = ConfigFactory.load().getString("kudos.emailDomain")
    val offlineHostname = ConfigFactory.load().getString("kudos.hostname")
    val url = routes.Kudos.moderate(obj.toPerson, obj.id.get)
    val email: Email = new Email()
    email.setSubject(s"New Kudos received for ${obj.toPerson}")
    email.setFrom(s"Kudos Admin <kudos-noreply@d$emailDomain>")
    email.setTo(admins.map(x => s"$x@$emailDomain"))
    email.setBodyText(s"A new Kudos has been generated by ${obj.fromPerson} to ${obj.toPerson} on ${obj.dateAdded}\n"+
      s"The feedback was: ${obj.feedback}\n" +
      s"To moderate please go to ${offlineHostname}${url} to moderate, or do nothing to let it be.")
    email.setBodyHtml(views.html.Kudos.email.apply(obj,offlineHostname,url.toString()).toString())
    MailerPlugin.send(email)
  }

  def all(implicit s: Session) = {
    tableQuery.filter(_.rejected === false).sortBy(_.dateAdded.desc)
  }

  def top(num: Int)(implicit s: Session): List[KudosToPerson] = {
    tableQuery.filter(_.rejected === false).sortBy(_.dateAdded.desc).take(num).list
  }

  def findById(to: String, id: Long)(implicit session: Session): Option[KudosToPerson] = {
    tableQuery.filter(_.id === id).filter(_.toPerson.toLowerCase === to.toLowerCase).firstOption
  }

  def findById(id: Long)(implicit session: Session): Option[KudosToPerson] = {
    tableQuery.filter(_.id === id).firstOption
  }

  /**
   * Delete a specific entity by id. If successfully completed return true, else false
   */
  def delete(to: String, id: Long)(implicit session: Session): Boolean =
    findById(to.toLowerCase, id) match {
      case Some(entity) =>
        tableQuery.filter(_.id === id).delete
        true
      case None => false
    }

  def update(to: String, id: Long, entity: KudosToPerson)(implicit session: Session): Boolean = {
    findById(to, id) match {
      case Some(e) => {
        tableQuery.filter(_.id === id).update(entity.copy(toPerson = to.toLowerCase, fromPerson = entity.fromPerson.toLowerCase))
        true
      }
      case None => false
    }
  }
}