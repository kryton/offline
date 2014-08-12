package controllers

import java.net.URLDecoder

import com.typesafe.config.ConfigFactory
import com.unboundid.ldap.sdk.{DN, SearchResultEntry}
import play.api._
import play.api.data.Form
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._
import play.core.parsers.FormUrlEncodedParser
import util.{Page, LDAP}
import views.html.defaultpages.notFound

import scala.text

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
    Ok(views.html.index("Your new application is ready."))
  }

  val pageSize = 10
  def personSearchCompact(page: Int, search: Option[String]) = Action { implicit request =>


    personSearchCompactForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.peopleSearchC(personSearchCompactForm, Page(null, 1, 0, 0, pageSize)))
      },
      personSearchCompactData => {
        /* binding success, you get the actual value. */
        val searchText = personSearchCompactData.search
        val results = ldap.personSearchCompact(searchText)
        System.out.println("SearchText=" + searchText + ": Results =" + results.size)
        val offset = pageSize * page
        val pageList = Page(results.drop(offset).take(pageSize), page, offset, results.size, pageSize)
        Ok(views.html.peopleSearchC(personSearchCompactForm.bindFromRequest(), pageList))
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
        BadRequest(views.html.peopleSearchD(personSearchDetailForm, Page(null, 1, 0, 0, pageSize)))
      },
      personSearchDetailData => {
        /* binding success, you get the actual value. */

        val results = ldap.personSearchDetailed(alias,email,name, title,reportsTo,phone, office)
        val offset = pageSize * page
        val pageList = Page(results.drop(offset).take(pageSize), page, offset, results.size, pageSize)
        Ok(views.html.peopleSearchD(personSearchDetailForm.bindFromRequest(), pageList))
      }
    )
  }
  def groupSearchCompact(page: Int, search: Option[String]) = Action { implicit request =>


    personSearchCompactForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.groupSearchC(ldap,groupSearchCompactForm, Page(null, 1, 0, 0, pageSize)))
      },
      groupSearchCompactData => {
        /* binding success, you get the actual value. */
        val searchText = groupSearchCompactData.search
        val results = ldap.groupSearchCompact(searchText)
        System.out.println("G SearchText=" + searchText + ": Results =" + results.size)
        val offset = pageSize * page
        val pageList = Page(results.drop(offset).take(pageSize), page, offset, results.size, pageSize)
        Ok(views.html.groupSearchC(ldap, groupSearchCompactForm.bindFromRequest(), pageList))
      }
    )
  }

  def person(name: String) = Action {

    ldap.getPersonByAccount( URLDecoder.decode( name,"UTF-8")) match {
      case Some(p)=> Ok(views.html.person(ldap, p))
      case None => NotFound
    }


  }

  def group(name: String) = Action { implicit request =>
    // val ldap = new LDAP
    ldap.getGroupByAccount(  URLDecoder.decode( name,"UTF-8")) match {
      case Some(p) => Ok(views.html.group(ldap,p))
      case None=>NotFound
    }
  }
}

