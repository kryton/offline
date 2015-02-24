package util

import java.io.StringWriter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * Created by iholsman on 2/01/2015.
 */
object Json {
  def toJson(o: AnyRef): String = {

    val out = new StringWriter()
    new ObjectMapper().registerModule(DefaultScalaModule).writeValue(out, o)
    out.toString

  }
}
