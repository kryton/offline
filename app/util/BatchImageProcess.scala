package util

import java.awt.image.BufferedImage
import java.io.File
import java.lang.Exception
import javax.imageio.ImageIO

import com.sksamuel.scrimage.{Image, Format}
import com.typesafe.config.ConfigFactory

import scala.reflect.io.{Path, Directory}

/**
 * Created by iholsman on 19/08/2014.
 */
object BatchImageProcess extends App {
  val srcDir = ConfigFactory.load().getString("image.directory")
  val srcContractorsDir = ConfigFactory.load().getString("image.directory") + "/Contractors"
  val destDir = ConfigFactory.load().getString("image.cache")

  System.out.println("Processing Contractors")
  for (fileName: Path <- Directory(srcContractorsDir).list.filter(
    p => {
    (p.extension.toLowerCase == "jpg" || p.extension.toLowerCase == "png") && !p.name.startsWith("._")
    }
  )
  ) {
      System.out.println(fileName.name)
      val inImage: BufferedImage = ImageIO.read(new java.io.File(fileName.toString()))
      val newName = fileName.name.split('.').init :+ "jpg" mkString "."
      try {
        val outImage = FaceDetect.findFaces(inImage, 1, 40)
        Image(outImage).fitToWidth(300).writer(Format.JPEG).write(new File(destDir + "/" + newName))
      } catch {
        case e: Exception => {
          System.err.println("Skipping " + fileName.name)
          e.printStackTrace()
          Image(inImage).fitToWidth(300).writer(Format.JPEG).write(new File(destDir + "/" + newName))
        }
    }
   // ImageIO.write(outImage, "jpg", new java.io.File(destDir + "/" + newName))
  }
  System.out.println("Processing FTEs")
  for (fileName: Path <- Directory(srcDir).list.filter( p => {
    (p.extension.toLowerCase == "jpg" || p.extension.toLowerCase == "png") && !p.name.startsWith("._")
  })) {
    System.out.println(fileName.name)
    val inImage: BufferedImage = ImageIO.read(new java.io.File(fileName.toString()))
    val newName = fileName.name.split('.').init :+ "jpg" mkString "."
    try {
      val outImage = FaceDetect.findFaces(inImage, 1, 40)
      Image(outImage).fitToWidth(300).writer(Format.JPEG).write(new File(destDir + "/" + newName))
    } catch {
      case e:Exception=> {
        System.err.println("Skipping "+ fileName.name)
        e.printStackTrace()
        Image(inImage).fitToWidth(300).writer(Format.JPEG).write(new File(destDir + "/" + newName))
      }
    }
   // ImageIO.write(outImage, "jpg", new java.io.File(destDir + "/" + newName))
  }
}
