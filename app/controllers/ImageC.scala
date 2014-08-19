package controllers


import java.awt.image.BufferedImage
import java.io.{FileInputStream, ByteArrayInputStream, ByteArrayOutputStream, File}
import java.net.URLDecoder
import javax.imageio.{IIOImage, ImageWriteParam, ImageIO}

import com.sksamuel.scrimage.{Format, Image}
import com.typesafe.config.ConfigFactory
import org.imgscalr.Scalr
import play.api.http.Writeable
import play.api.mvc.{Action, Controller}
import util.{FaceDetect, LDAP}


/**
 * Created by iholsman on 18/08/2014.
 */
object ImageC extends Controller {
  val imageDir = ConfigFactory.load().getString("image.directory")
  val resourcesFile: String = "conf/haarcascade_frontalface_default.xml"
  val ldap: LDAP = Application.ldap

  def headShot(alias: String) = Action {
    if (imageDir != null && !imageDir.equalsIgnoreCase("None")) {
      ldap.getPersonByAccount(URLDecoder.decode(alias, "UTF-8")) match {
        case Some(p) => {
          val fn = p.getAttributeValue("givenName").toLowerCase
          val sn = p.getAttributeValue("sn").toLowerCase
          def FNFilter(filename: String) = filename.toLowerCase.startsWith(fn) && filename.toLowerCase.contains(sn)
          val rawFileList = new File(imageDir).list.filter(FNFilter)
          if (rawFileList.nonEmpty) {
            val imageName = rawFileList.last
            val inImage: BufferedImage = ImageIO.read(new File(imageDir + "/" + imageName))

            val theFace = FaceDetect.findFaces(inImage, 1, 40)
            val tmpFile: File = File.createTempFile("face", ".jpg")
            val aFile: Array[Byte] = Image(theFace).fitToWidth(300).writer(Format.JPEG).write()

            Ok(aFile).as("image/jpg")
            /*  */
          } else {
            Redirect(routes.Assets.at("images/noFace.jpg"))
          }


        }
        case None => Redirect(routes.Assets.at("images/noFace.jpg"))
      }
    } else {
      Redirect(routes.Assets.at("images/noFace.jpg"))
    }


  }

}

