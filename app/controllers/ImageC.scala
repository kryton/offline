package controllers


import java.awt.image.BufferedImage
import java.io.{FileReader, FileInputStream, File}
import java.net.URLDecoder
import javax.imageio.{IIOImage, ImageWriteParam, ImageIO}

import com.sksamuel.scrimage.io.JpegWriter
import com.sksamuel.scrimage.{Format, Image}
import com.typesafe.config.ConfigFactory
import org.imgscalr.Scalr
import play.api.Logger
import play.api.http.Writeable
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{ResponseHeader, Result, Action, Controller}
import util.{FaceDetect, LDAP}
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
    def FNFilter(filename: String) = filename.toLowerCase.startsWith(firstName) && filename.toLowerCase.contains(surname)

    val rawFileList = new File(directoryName).list.filter(FNFilter)
    if (rawFileList.nonEmpty) {
      Some(rawFileList.last)
    } else {
      None
    }
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
                Ok(aFile).as("image/jpg")
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
              Ok.sendFile(content = cacheFile, inline = true).as("image/jpg")
            }
            case None => Redirect(routes.Assets.at("images/noFace.jpg"))
          }
        }
      }
      case _ => Redirect(routes.Assets.at("images/noFace.jpg"))
    }


  }

}

