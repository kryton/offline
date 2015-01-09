package controllers


import java.awt.image.BufferedImage
import java.io.{FileReader, FileInputStream, File}
import java.net.URLDecoder
import javax.imageio.{IIOImage, ImageWriteParam, ImageIO}

import com.sksamuel.scrimage.io.JpegWriter
import com.sksamuel.scrimage.{Format, Image}
import com.typesafe.config.ConfigFactory
import controllers.Application._
import org.imgscalr.Scalr
import play.api.Logger
import play.api.http.Writeable
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{ResponseHeader, Result, Action, Controller}
import util.{Page, FaceDetect, LDAP}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future


/**
 * Created by iholsman on 18/08/2014.
 */
object ImageC extends Controller {

  val imageDir = ConfigFactory.load().getString("image.directory")
  val cacheDir = ConfigFactory.load().getString("image.cache")
  val resourcesFile: String = "conf/haarcascade_frontalface_default.xml"
  val ldap: LDAP = Application.ldap

  def findPicInDir(directoryName: String, firstName: String, surname: String): Option[String] = {
    val theList = getMatchingPics(directoryName,firstName, surname)
    if (theList.nonEmpty) {
      Some(theList.last)
    } else {
      None
    }
  }
  def getNextPicNamePrefix(directoryName: String, firstName: String, surname: String):String = {
    val theList = getMatchingPics(directoryName,firstName, surname)
    val count = theList.length
   // String.format("%s %s %d", firstName, surname, count+1)
    s"$firstName $surname ${count+1}"

  }
  def getMatchingPics(directoryName: String, firstName: String, surname: String): List[String] = {
    def FNFilter(filename: String) = filename.toLowerCase.startsWith(firstName) && filename.toLowerCase.contains(surname)

    new File(directoryName).list.filter(FNFilter).sortWith(_>_).toList
  }


  def genImage(dir: String, file: String): Some[JpegWriter] = {
    val inImage: BufferedImage = ImageIO.read(new File(dir + "/" + file))
    val theFace = FaceDetect.findFaces(inImage, 1, 40)
    Some(Image(theFace).fitToWidth(300).writer(Format.JPEG))
  }
  def headShot(alias: String, domain:Option[String] ) = Action {
    val results =ldap.getPersonByAccount(URLDecoder.decode(alias, "UTF-8"),domain)
     results.size match {
      case 0 => Redirect(routes.Assets.at("images/noFace.jpg"))
      case 1 => {
        val fn = results.head.getAttributeValue("givenName").toLowerCase
        val sn = results.head.getAttributeValue("sn").toLowerCase
        val usedCached = cacheDir != null && !cacheDir.equalsIgnoreCase("None")
        val dirName = {
          if (usedCached) {
            Some(cacheDir)
          } else {
            if (imageDir != null && !imageDir.equalsIgnoreCase("None")) {
              Some(imageDir)
            }
            else {
              None
            }
          }
        }

        val fileName: Option[String] = dirName match {
          case Some(dir) => findPicInDir(dir, fn, sn)
          case _ => None
        }
        if (usedCached && fileName.isEmpty || (!usedCached && fileName.isDefined)) {
          if (imageDir != null || imageDir.equalsIgnoreCase("None")) {
            findPicInDir(imageDir, fn, sn) match {
              case Some(file) => {
                Logger.info("Using UnCached version of " + file)
                val processedImage = genImage(imageDir, file).get
                val newName = file.split('.').init :+ "jpg" mkString "."
                if (cacheDir != null && cacheDir != "None") {
                  Future {
                    processedImage.write(new File(cacheDir + "/" + file))
                    Logger.debug("Generated Cached image of " + file)
                  }
                }
                val aFile: Array[Byte] = processedImage.write()
                Ok(aFile).as("image/jpeg")
              }
              case _ => Redirect(routes.Assets.at("images/noFace.jpg"))
            }
          } else {
            Redirect(routes.Assets.at("images/noFace.jpg"))
          }
        } else {
          fileName match {
            case Some(file: String) => {
       //       Logger.info("Using Cached version of " + file)
              val cacheFile = new java.io.File(cacheDir + "/" + file)
              Ok.sendFile(content = cacheFile, inline = true).as("image/jpeg")
            }
            case None => Redirect(routes.Assets.at("images/noFace.jpg"))
          }
        }
      }
      case _ => Redirect(routes.Assets.at("images/noFace.jpg"))
    }
  }
 // def editImage(alias: String, domain:Option[String]) = Action { implicit request =>
  def editImage(page: Int, search: Option[String]) = Action { implicit request =>
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
}

