package controllers

import javax.crypto.IllegalBlockSizeException

import com.typesafe.config.ConfigFactory
import controllers.Application._
import filters.LdapAuthFilter
import models._
import org.apache.commons.codec.DecoderException
import play.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{DBAction, _}
import play.api.libs.Crypto
import play.api.mvc._
import play.filters.csrf.CSRFCheck
import util.{Json, LDAP}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.slick.jdbc.StaticQuery
import scala.util.Random

/**
 * Created by iholsman on 9/01/2015.
 */

class UserRequest[A](val username: Option[String], request: Request[A]) extends WrappedRequest[A](request)

case class LDAPAuthAction[A](action: Action[A]) extends Action[A] {

  import play.api.libs.concurrent.Execution.Implicits._

  lazy val unauthResult = Results.Unauthorized.withHeaders(("WWW-Authenticate", "Basic realm=\"use your Digital River Login please\""))


  def apply(request: Request[A]): Future[Result] = {
    request.headers.get("authorization").map { basicAuth =>
      LdapAuthFilter.decodeBasicAuth(basicAuth) match {
        case Some((user, pass)) =>
          val enableAuth = ConfigFactory.load().getBoolean("auth.enable")
          if (enableAuth) {
            val res: Boolean = new LDAP().authenticateAccount(user, pass)
            if (res) {
              return action(new UserRequest(Some(user), request))
            }
          } else {
            Logger.error("XXXXX AUTH IS CURRENTLY DISABLED!")
            return action(new UserRequest(Some(user), request))
          }

        case _ => ;
      }
    }

    Future(unauthResult)

  }

  lazy val parser = action.parser
}

case class FlagData(login: String, complaint: String)

case class authData(from: String, to: String, message: String)

object Kudos extends Controller {
  implicit val timeout = 10.seconds
  val employees = TableQuery[EmpRelations]

  //  val ldap: LDAP = new LDAP
  def getUser(request: Request[AnyContent]): Option[String] = {
    request.headers.get("authorization").map { basicAuth =>
      LdapAuthFilter.decodeBasicAuth(basicAuth).getOrElse(("None", ""))._1
    }.headOption
  }

  val pageSize = 10

  val theForm = Form(
    mapping(
      "id" -> optional(longNumber),
      "fromPerson" -> text,
      "toPerson" -> text,
      "dateAdded" -> sqlDate,
      "feedback" -> text,
      "rejected" -> boolean,
      "rejectedBy" -> optional(text),
      "rejectedOn" -> optional(sqlDate),
      "rejectedReason" -> optional(text)

    )(KudosToPerson.apply)(KudosToPerson.unapply)
  )

  def kudosList(size: Int, formatO: Option[String]) =
    DBAction { implicit rs =>
      val list = KudosToPeople.top(size)
      formatO match {
        case Some(format) => if (format.toLowerCase.equals("html")) {
          Ok(views.html.Kudos.list("Most recent Kudos", list))
        } else {
          Ok(Json.toJson(list)).as("application/json;  charset=utf-8").withHeaders(("Access-Control-Allow-Origin","*"))
        }
        case None => Ok(Json.toJson(list)).as("application/json;  charset=utf-8") .withHeaders(("Access-Control-Allow-Origin","*"))

      }
    }

  def kudosFrom(login: String, page: Int) = DBAction { implicit rs =>
    Ok("ok")
  }

  def kudosTo(login: String, page: Int) = DBAction { implicit rs =>
    Ok("ok")
  }

  def id(login: String, id: Long) = DBAction { implicit rs =>
    EmpRelations.findByLogin(login) match {
      case None => NotFound("User not found")
      case Some(empRecord) =>
        KudosToPeople.findById(login, id) match {
          case Some(thing) => Ok(views.html.Kudos.CRUD.id(empRecord, thing))
          case None => NotFound("Not Found")
        }
    }
  }

  def create(login: String) = LDAPAuthAction {
    CSRFCheck {
      DBAction { implicit rs =>

        getUser(rs.request) match {
          case Some(user) =>
            EmpRelations.findByLogin(login) match {
              case None => NotFound("login not found")
              case Some(empRecord) =>
                if (empRecord.login.toLowerCase.equals(user.toLowerCase)) {
                  NotFound("<html><body>You're Awesome, but we need someone else to tell us! :-)<br /> <iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/9cQgQIMlwWw?autoplay=1\" frameborder=\"0\" allowfullscreen></iframe></body></html>").as("text/html")
                } else {
                  Ok(views.html.Kudos.CRUD.createForm(empRecord, user, theForm))
                }
            }
          case _ => NotFound("userid not found")
        }
      }
    }
  }

  def save(login: String) = LDAPAuthAction {
    CSRFCheck {
      DBAction { implicit rs =>
        val now: java.sql.Date = new java.sql.Date(System.currentTimeMillis())
        getUser(rs.request) match {
          case Some(user) =>
            EmpRelations.findByLogin(login) match {
              case None => NotFound("login not found")
              case Some(empRecord) =>
                if (user.toLowerCase.equals(empRecord.login.toLowerCase)) {
                  NotFound("<html><body>You're Awesome, but we need someone else to tell us! :-)<br /> <iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/9cQgQIMlwWw?autoplay=1\" frameborder=\"0\" allowfullscreen></iframe></body></html>").as("text/html")
                } else {
                  theForm.bindFromRequest.fold(
                    formWithErrors => BadRequest(views.html.Kudos.CRUD.createForm(empRecord, user, theForm.bindFromRequest)),
                    obj => {
                      val result:KudosToPerson = KudosToPeople.create(login, obj.copy(toPerson = empRecord.login, fromPerson = user, rejected = false, dateAdded = now))
                      KudosToPeople.genNewEmail(result)
                     // val id = (StaticQuery[Long] + "SELECT LAST_INSERT_ID()").first
                      Redirect(routes.Kudos.id(login, result.id.get))
                    }
                  )
                }
            }
          case _ => NotFound("userid not found")
        }
      }
    }
  }

  def edit(login: String, id: Long) = LDAPAuthAction {
    DBAction { implicit rs =>
      EmpRelations.findByLogin(login) match {
        case None => NotFound("emp not found")
        case Some(emp) =>
          KudosToPeople.findById(login, id) match {
            case None => NotFound("Not Found")
            case Some(thing) =>
              getUser(rs.request) match {
                case Some(user) =>
                  if (user.toLowerCase == thing.fromPerson.toLowerCase) {
                    Ok(views.html.Kudos.CRUD.editForm(emp, id, theForm.fill(thing)))
                  } else {
                    NotFound("Sorry, you can only edit your own Kudos")
                  }
                case None => NotFound("user Not Found")
              }
          }
      }
    }
  }

  def update(login: String, id: Long) = LDAPAuthAction {
    CSRFCheck {
      DBAction { implicit rs =>
        KudosToPeople.findById(login, id) match {
          case None => NotFound("Employee not found")
          case Some(kudo) =>
            getUser(rs.request) match {
              case Some(user) =>
                if (kudo.fromPerson.toLowerCase == user.toLowerCase) {
                  val emp = EmpRelations.findByLogin(login).get
                  theForm.bindFromRequest.fold(
                    formWithErrors => BadRequest(views.html.Kudos.CRUD.editForm(emp, id, theForm.bindFromRequest)),
                    obj => {
                      val result = KudosToPeople.update(emp.login, id, obj.copy(id = Some(id),
                        dateAdded = kudo.dateAdded,
                        rejected = kudo.rejected,
                        rejectedOn = kudo.rejectedOn,
                        rejectedBy = kudo.rejectedBy,
                        toPerson = kudo.toPerson,
                        fromPerson = kudo.fromPerson
                      ))
                      Redirect(routes.Kudos.id(login, id))
                    }
                  )
                } else {
                  NotFound("you can only edit your own Kudos!")
                }
              case None => NotFound("user Not Found")
            }
        }
      }
    }
  }

  //TODO add ADMIN-ONLY CHECK
  def moderate(login: String, id: Long) = LDAPAuthAction {
    val admins: Set[String] = ConfigFactory.load().getStringList("kudos.admins").toList.map(x => x.toLowerCase).toSet

    DBAction { implicit rs =>
      getUser(rs.request) match {
        case None => NotFound("user Not Found")
        case Some(user) =>
          if (admins.contains(user.toLowerCase)) {
            KudosToPeople.findById(login, id) match {
              case None => NotFound("Not Found")
              case Some(kudos) => Ok(views.html.Kudos.CRUD.moderateForm(kudos, id, theForm.fill(kudos)))
            }
          } else {
            NotFound("Only Admins can use this screen")
          }
      }
    }
  }


  //TODO add ADMIN-ONLY CHECK
  def moderateUpdate(login: String, id: Long) = LDAPAuthAction {
    val admins: Set[String] = ConfigFactory.load().getStringList("kudos.admins").toList.map(x => x.toLowerCase).toSet

    CSRFCheck {
      DBAction { implicit rs =>
        getUser(rs.request) match {
          case None => NotFound("User Not Found")
          case Some(user) =>
            if (admins.contains(user.toLowerCase)) {
              KudosToPeople.findById(login, id) match {
                case None => NotFound("Employee not found")
                case Some(kudos) =>
                  theForm.bindFromRequest.fold(
                    formWithErrors => BadRequest(views.html.Kudos.CRUD.moderateForm(kudos, id, theForm.bindFromRequest)),
                    obj => {
                      val now: java.sql.Date = new java.sql.Date(System.currentTimeMillis())

                      val result = KudosToPeople.update(login, id,
                        obj.copy(id = Some(id),
                          toPerson = kudos.toPerson,
                          dateAdded = kudos.dateAdded,
                          fromPerson = kudos.fromPerson,
                          feedback = kudos.feedback,
                          rejectedBy = Some(user.toLowerCase),
                          rejectedOn = Some(now)))
                      Redirect(routes.Kudos.id(login, id))
                    }
                  )
              }
            } else {
              NotFound("Only Admins can use this screen")
            }
        }
      }
    }
  }


  def delete(login: String, id: Long) = LDAPAuthAction {
    CSRFCheck {
      DBAction { implicit rs =>
        getUser(rs.request) match {
          case Some(user) =>
            KudosToPeople.findById(login, id) match {
              case None => NotFound("Not Found")
              case Some(thing) =>
                if (user.toLowerCase == thing.fromPerson.toLowerCase) {
                  val role = KudosToPeople.delete(login, id)
                  Redirect(routes.Application.person(login))
                } else {
                  NotFound("You can only delete your own Kudos")
                }
            }
          case None => NotFound("User not found")
        }
      }
    }
  }

  def logout() = Action {
    Results.Unauthorized.withHeaders(("WWW-Authenticate", "Basic realm=\"use your Digital River Login please\""))
  }


  val flagForm = Form(
    mapping("from" -> text,
      "compliant" -> text
    )(FlagData.apply)(FlagData.unapply)
  )


  def flag(login: String, id: Long) = CSRFCheck {
    DBAction { implicit rs =>

      KudosToPeople.findById(login, id) match {
        case None => NotFound("Not Found")
        case Some(thing) =>
          Ok(views.html.Kudos.flagForm(thing, flagForm))

      }
    }
  }

  def flagSend(login: String, id: Long) = CSRFCheck {
    DBAction { implicit rs =>
      KudosToPeople.findById(login, id) match {
        case None => NotFound("Not Found")
        case Some(thing) =>
          flagForm.bindFromRequest.fold(
            formWithErrors => BadRequest(views.html.Kudos.flagForm(thing, flagForm.bindFromRequest)),
            obj => {
              EmpRelations.findByLogin(thing.fromPerson) match {
                case None => KudosToPeople.genFlaggedEmail(thing, None, obj.login, obj.complaint)
                case Some(emp) => KudosToPeople.genFlaggedEmail(thing, emp.managerID, obj.login, obj.complaint)
              }
              Redirect(routes.Application.person(login))
            })
      }
      //      Ok("")
    }
  }

  def authKudos(cryptString: String) = DBAction {
    implicit rs =>
      try {
        val decrypted: String = Crypto.decryptAES(cryptString)
        val parts = decrypted.split("\\|")
        if (parts.length != 3 ) {
          BadRequest("Invalid message. I was expecting 4 parts")
        } else {
          if (parts(0) != "42") {
            BadRequest("Invalid message. Bad cookie")
          }else {
            val id:Long = parts(1).toLong
           KudosToPeople.findById(id) match {
             case None => NotFound("I can't find that")
             case Some(obj:KudosToPerson) =>
               if (obj.rejected ) {
               obj.rejectedBy match {
                 case None =>
                   val newObj = obj.copy(rejected = false, rejectedBy = None, rejectedReason = None)
                   KudosToPeople.update(obj.toPerson, id, newObj)
                   KudosToPeople.genNewEmail(newObj)
                   Ok("Thank you. The feedback should be now visible")
                 case Some(rj) =>
                   if (rj.equals("SYSTEM")) {
                     val newObj = obj.copy(rejected = false, rejectedBy = None, rejectedReason = None, rejectedOn=None)
                     KudosToPeople.update(obj.toPerson, id, newObj)
                     KudosToPeople.genNewEmail(newObj)
                     Ok("Thank you. The feedback should be now visible")
                   } else {
                     BadRequest("I'm sorry. That message was flagged for moderation. please contact the admins for more information")
                   }
               }
             } else {
               Ok("Thanks! it should be visible")
             }
           }
          }
        }
      } catch {
        case e: DecoderException => BadRequest(e.getLocalizedMessage)
        case e: IllegalBlockSizeException => BadRequest(e.getLocalizedMessage)
      }
  }

  val authForm = Form(
    mapping(
      "from" -> text,
      "to" -> text,
      "message" -> text
    )(authData.apply)(authData.unapply)
  )

  def genKudosEmail() = DBAction { implicit rs =>
    authForm.bindFromRequest.fold(
      formWithErrors => BadRequest("I don't understand that"),
      obj => {
        if ( obj.from.toLowerCase.equals(obj.to.toLowerCase)) {
          BadRequest("Can't give feedback to yourself")
        } else {
          val fromPers: Option[EmpRelation] = EmpRelations.findByLogin(obj.from)
          val toPers: Option[EmpRelation] = EmpRelations.findByLogin(obj.to)
          if (fromPers.isEmpty) {
            BadRequest("I can't find the person who is giving the feedback")
          } else {
            if (toPers.isEmpty) {
              BadRequest("I can't find the person who the feedback is for")
            } else {
              val now: java.sql.Date = new java.sql.Date(System.currentTimeMillis())
              //  val twentyFourHours: Long = 86400000L
              val kudosToPerson: KudosToPerson = new KudosToPerson(None, obj.from, obj.to, now, obj.message, true, Some("SYSTEM"), Some(now), Some("AwaitingAuth"))
              val result: KudosToPerson = KudosToPeople.create(obj.to, kudosToPerson)

              val encrypted: String = Crypto.encryptAES("42|" + result.id.get + "|" + Random.nextString(3))
              KudosToPeople.genAuthEmail(kudosToPerson, encrypted)
              Ok(s"An Email was sent to you to authenticate this.")
            }
          }
        }
      }
    )
  }
}
