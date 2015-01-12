package controllers

import java.net.URLDecoder

import com.unboundid.ldap.sdk.SearchResultEntry

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import util.{Page, LDAP}
import play.api.db.slick.DBAction
import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import play.api.mvc.Security.Authenticated
import scala.concurrent.duration.DurationInt

import models.{KudosToPeople, EmpRelations}

case class personSearchDetailData(alias: Option[String],
                                  email: Option[String],
                                  name: Option[String],
                                  title: Option[String],
                                  reportsTo: Option[String],
                                  phone: Option[String],
                                  office: Option[String])

case class personSearchCompactData(search: String)

case class groupSearchCompactData(search: String)

object Application extends Controller {
  implicit val timeout = 10.seconds
  val employees = TableQuery[EmpRelations]

  val ldap: LDAP = new LDAP
  val personSearchCompactForm = Form(
    mapping(
      "search" -> nonEmptyText
    )(personSearchCompactData.apply)(personSearchCompactData.unapply)
  )
  val personSearchDetailForm = Form(
    mapping(
      "alias" -> optional(nonEmptyText),
      "email" -> optional(nonEmptyText),
      "name" -> optional(nonEmptyText),
      "title" -> optional(nonEmptyText),
      "reportsTo" -> optional(nonEmptyText),
      "phone" -> optional(nonEmptyText),
      "office" -> optional(nonEmptyText)
    )(personSearchDetailData.apply)(personSearchDetailData.unapply)
  )
  val groupSearchCompactForm = Form(
    mapping(
      "search" -> nonEmptyText
    )(groupSearchCompactData.apply)(groupSearchCompactData.unapply)
  )

  def index = Action {
    Ok(views.html.index("Offline"))
  }

  val pageSize = 10

  def personSearchCompact(page: Int, search: Option[String]) = Action { implicit request =>

    personSearchCompactForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.peopleSearchC(ldap,personSearchCompactForm, Page(null, 1, 0, 0, pageSize)))
      },
      personSearchCompactData => {
        /* binding success, you get the actual value. */
        val searchText = personSearchCompactData.search
        val results: List[SearchResultEntry] = ldap.personSearchCompact(searchText)
        if (results.size == 1) {
          val result = results.head
          val alias = result.getAttributeValue("sAMAccountName")

          Redirect(routes.Application.person(alias))
        } else {
          val offset = pageSize * page
          val pageList = Page(results.drop(offset).take(pageSize), page, offset, results.size, pageSize)
          Ok(views.html.peopleSearchC(ldap,personSearchCompactForm.bindFromRequest(), pageList))
        }
      }
    )
  }

  def personSearchDetailed(page: Int,
                           alias: Option[String],
                           email: Option[String],
                           name: Option[String],
                           title: Option[String],
                           reportsTo: Option[String],
                           phone: Option[String],
                           office: Option[String]
                            ) = Action { implicit request =>

    personSearchDetailForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.peopleSearchD(ldap, personSearchDetailForm, Page(null, 1, 0, 0, pageSize)))
      },
      personSearchDetailData => {
        /* binding success, you get the actual value. */

        val results = ldap.personSearchDetailed(alias, email, name, title, reportsTo, phone, office)
        val offset = pageSize * page
        val pageList = Page(results.drop(offset).take(pageSize), page, offset, results.size, pageSize)
        Ok(views.html.peopleSearchD(ldap, personSearchDetailForm.bindFromRequest(), pageList))
      }
    )
  }

  def groupSearchCompact(page: Int, search: Option[String]) = Action { implicit request =>
    personSearchCompactForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.groupSearchC(ldap, groupSearchCompactForm, Page(null, 1, 0, 0, pageSize)))
      },
      groupSearchCompactData => {
        /* binding success, you get the actual value. */
        val searchText = groupSearchCompactData.search
        val results = ldap.groupSearchCompact(searchText)
        val offset = pageSize * page
        val pageList = Page(results.drop(offset).take(pageSize), page, offset, results.size, pageSize)
        Ok(views.html.groupSearchC(ldap, groupSearchCompactForm.bindFromRequest(), pageList))
      }
    )
  }

  def person(name: String, domain: Option[String]) = DBAction { implicit rs =>
    val results = ldap.getPersonByAccount(URLDecoder.decode(name, "UTF-8"), domain)

    results.size match {
      case 1 =>
        val employee = employees.filter(_.login === name).firstOption
        val directsDB = employees.filter(_.managerID === name).list
        if ( directsDB.isEmpty ) {

          Ok(views.html.person(ldap, results.head, employee, List.empty,  KudosToPeople.findByFrom(employee.get.login), KudosToPeople.findByTo(employee.get.login)))
        } else {
          //  val directsLDAP = results.head.getAttributeValues("directReports")
           // if (directsLDAP != null) {
              val directsLDAPMap = directsDB.map {
                x =>  ldap.getPersonByAccount(x.login.toLowerCase,None).headOption
              }.flatten.map(f => (f.getAttributeValue("sAMAccountName") -> f)).toMap
              val directsToShow = directsDB.map {
                emp => directsLDAPMap.get(emp.login.toLowerCase) match {
                  case None => (emp, None)
                  case Some(res) => (emp, Some(res))
                }
              }
              Ok(views.html.person(ldap, results.head, employee, directsToShow, KudosToPeople.findByFrom(employee.get.login), KudosToPeople.findByTo(employee.get.login)  ))
        }


      case 0 => NotFound(views.html.notFoundPerson(name))
      case _ => Redirect(routes.Application.personSearchCompact(0, Some(name)))
    }
  }

  def group(name: String, domain: Option[String]) = Action { implicit request =>
    // val ldap = new LDAP
    val results = ldap.getGroupsByAccount(URLDecoder.decode(name, "UTF-8"), domain)
    results.size match {
      case 0 => NotFound(views.html.notFoundGroup(name))
      case 1 => Ok(views.html.group(ldap, results.head))
      case _ => Redirect(routes.Application.groupSearchCompact(0,Some(name)))
    }
  }
}

