package controllers

import java.net.URLDecoder

import filters.LdapAuthFilter
import models.EmpRelations
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.{DBAction, _}
import play.api.mvc._
import util.LDAP

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Created by iholsman on 9/01/2015.
 */

class UserRequest[A](val username: Option[String], request: Request[A]) extends WrappedRequest[A](request)

case class LDAPAuthAction[A](action: Action[A]) extends Action[A] {

  import play.api.libs.concurrent.Execution.Implicits._

  private lazy val unauthResult = Results.Unauthorized.withHeaders(("WWW-Authenticate", "Basic realm=\"use your Digital River Login please\""))

  def apply(request: Request[A]): Future[Result] = {
    request.headers.get("authorization").map { basicAuth =>
      LdapAuthFilter.decodeBasicAuth(basicAuth) match {
        case Some((user, pass)) =>
          val res: Boolean = new LDAP().authenticateAccount(user, pass)
          if (res) {
            return action(new UserRequest(Some(user), request))
          }

        case _ => ;
      }
    }

    Future(unauthResult)

  }

  lazy val parser = action.parser
}


object Kudos extends Controller {
  implicit val timeout = 10.seconds
  val employees = TableQuery[EmpRelations]

  val ldap: LDAP = new LDAP

  def foobar = {
    DBAction { implicit rs =>
      Ok("hi")
    }
  }

  def personAuth(name: String, domain: Option[String]) =
    LDAPAuthAction {
      DBAction { implicit rs =>
        val UP = LdapAuthFilter.decodeBasicAuth(rs.request.headers.get("authorization").get)
        val userName = UP.get._1
        val results = ldap.getPersonByAccount(URLDecoder.decode(name, "UTF-8"), domain)

        results.size match {
          case 1 =>
            val employee = employees.filter(_.login === name).firstOption
            val directsDB = employees.filter(_.managerID === name).list
            if (directsDB.isEmpty) {
              Ok(views.html.person2(ldap, userName, results.head, employee, List.empty))
            } else {
              //  val directsLDAP = results.head.getAttributeValues("directReports")
              // if (directsLDAP != null) {
              val directsLDAPMap = directsDB.map {
                x => ldap.getPersonByAccount(x.login.toLowerCase, None).headOption
              }.flatten.map(f => (f.getAttributeValue("sAMAccountName") -> f)).toMap
              val directsToShow = directsDB.map {
                emp => directsLDAPMap.get(emp.login.toLowerCase) match {
                  case None => (emp, None)
                  case Some(res) => (emp, Some(res))
                }
              }
              Ok(views.html.person2(ldap, userName, results.head, employee, directsToShow))
            }


          case 0 => NotFound(views.html.notFoundPerson(name))
          case _ => Redirect(routes.Application.personSearchCompact(0, Some(name)))
        }
      }
    }

}
