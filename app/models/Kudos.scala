package models

/**
 * Created by iholsman on 9/01/2015.
 */

import scala.slick.lifted.Tag
import scala.slick.util.Logging
import play.api.db.slick.Config.driver.simple._
import java.sql.Date

case class KudosTo( from:String, to:String, dateAdded:Date, feedback:String,
                    rejected:Boolean, rejectedBy:String,rejectedOn:Date, rejectedReason:String )