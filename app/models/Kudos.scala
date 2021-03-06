package models

/**
 * Created by iholsman on 9/01/2015.
 */

import java.sql.Date

import com.typesafe.config.ConfigFactory
import controllers.routes
import org.apache.commons.lang3.StringEscapeUtils
import play.Logger
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

  def create(to: String, obj: KudosToPerson)(implicit session: Session): KudosToPerson = {
    val newObj = obj.copy(toPerson = to.toLowerCase, fromPerson = obj.fromPerson.toLowerCase)
    tableQuery += newObj
    val id =    (StaticQuery[Long] + "SELECT LAST_INSERT_ID()").first
    newObj.copy(id=Some(id))
  //  genNewEmail(newObj.copy(id=Some(id)))
  }

  def genNewEmail(obj: KudosToPerson): Unit = {
    import play.libs.mailer.{Email, MailerPlugin}

    import scala.collection.JavaConversions._

    val admins = ConfigFactory.load().getStringList("kudos.admins").toList
    val mailReceipient  = ConfigFactory.load().getBoolean("kudos.emailRecipient")

    val emailDomain = ConfigFactory.load().getString("kudos.emailDomain")
    val offlineHostname = ConfigFactory.load().getString("kudos.hostname")
    val url = routes.Kudos.moderate(obj.toPerson, obj.id.get)
    val email: Email = new Email()
    email.setSubject(s"New Kudos received for ${obj.toPerson}")
    email.setFrom(s"Kudos Admin <kudos-noreply@$emailDomain>")
    email.setTo(admins.map(x => s"$x@$emailDomain"))
    if (mailReceipient) {
      email.addCc(s"${obj.toPerson}@$emailDomain")
      email.addCc(s"${obj.fromPerson}@$emailDomain")
    }
    email.setBodyText(s"A new Kudos has been generated by ${obj.fromPerson} to ${obj.toPerson} on ${obj.dateAdded}\n"+
      s"The feedback was: ${obj.feedback}\n" +
      s"To moderate please go to ${offlineHostname}${url} to moderate, or do nothing to let it be.")
    email.setBodyHtml(views.html.Kudos.email.apply(obj,offlineHostname,url.toString()).toString())
 //   Logger.error("XXXXX AUTH IS CURRENTLY DISABLED!")
    MailerPlugin.send(email)
  }

  def genAuthEmail(obj: KudosToPerson, crypt:String): Unit = {
    import play.libs.mailer.{Email, MailerPlugin}

    import scala.collection.JavaConversions._

    val admins = ConfigFactory.load().getStringList("kudos.admins").toList
    val mailReceipient  = ConfigFactory.load().getBoolean("kudos.emailRecipient")

    val emailDomain = ConfigFactory.load().getString("kudos.emailDomain")
    val offlineHostname = ConfigFactory.load().getString("kudos.hostname")
    val url = routes.Kudos.authKudos(crypt)
    val email: Email = new Email()
    email.setSubject(s"Authorization required: Kudos for ${obj.toPerson}")
    email.setFrom(s"Kudos Admin <kudos-noreply@$emailDomain>")
    email.addTo(s"${obj.fromPerson}@$emailDomain")

    email.setBodyText(s"A new Kudos has been generated by you for ${obj.toPerson} on ${obj.dateAdded}\n"+
      s"The feedback was: ${obj.feedback}\n" +
      s"To authorize this please go to $offlineHostname$url to authorize, or do nothing to let it be.")
    email.setBodyHtml(views.html.Kudos.authEmail.apply(obj,offlineHostname,url.toString(),admins.map(x => s"$x@$emailDomain").mkString(",")).toString())
    MailerPlugin.send(email)
  }

  def genFlaggedEmail(obj: KudosToPerson, managerLogin:Option[String], compliainerLogin:String, reason:String): Unit = {
    import play.libs.mailer.{Email, MailerPlugin}

    import scala.collection.JavaConversions._

    val admins = ConfigFactory.load().getStringList("kudos.admins").toList
    val emailDomain = ConfigFactory.load().getString("kudos.emailDomain")
    val offlineHostname = ConfigFactory.load().getString("kudos.hostname")
    val url = routes.Kudos.moderate(obj.toPerson, obj.id.get)
    val email: Email = new Email()
    email.setSubject(s"A Kudos was Flagged as Inappropriate, which was written by ${obj.fromPerson}")
    email.setFrom(s"Kudos Admin <kudos-noreply@d$emailDomain>")
    if (managerLogin.isDefined) {
      email.addTo(s"${managerLogin.get}@$emailDomain" )
      email.setCc(admins.map(x => s"$x@$emailDomain"))
    } else {
      email.setTo(admins.map(x => s"$x@$emailDomain"))
    }
    email.setBodyText(s"A new Kudos has been generated by ${obj.fromPerson} to ${obj.toPerson} on ${obj.dateAdded}\n"+
      s"The feedback was: ${obj.feedback}\n" +
      s"To moderate please go to ${offlineHostname}${url} to moderate, or do nothing to let it be.")
    email.setBodyHtml(views.html.Kudos.FlagEmail.apply(obj,compliainerLogin, reason,offlineHostname,url.toString()).toString())
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
