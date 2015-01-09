package filters

import play.Logger
import play.api.mvc.{Filter, _}
import sun.misc.BASE64Decoder
import util.LDAP

import scala.concurrent.Future

/**
 * Created by iholsman on 7/01/2015.
 */
object LdapAuthFilter extends Filter {
  private lazy val unauthResult = Results.Unauthorized.withHeaders(("WWW-Authenticate", "Basic realm=\"use your Digital River Login please\""))
  private lazy val username = "someUsername"
  private lazy val password = "somePassword"
  private lazy val outsidePages = Seq("ping.html")
  //need the space at the end
  private lazy val basicSt = "basic "

  //This is needed if you are behind a load balancer or a proxy
  private def getUserIPAddress(request: RequestHeader): String = {
    request.headers.get("x-forwarded-for").getOrElse(request.remoteAddress.toString)
  }

  private def logFailedAttempt(requestHeader: RequestHeader) = {
    Logger.warn(s"IP address ${getUserIPAddress(requestHeader)} failed to log in, " +
      s"requested uri: ${requestHeader.uri}")
  }

   def decodeBasicAuth(auth: String): Option[(String, String)] = {
    if (auth.length() < basicSt.length()) {
      return None
    }
    val basicReqSt = auth.substring(0, basicSt.length())
    if (basicReqSt.toLowerCase != basicSt) {
      return None
    }
    val basicAuthSt = auth.replaceFirst(basicReqSt, "")
    //BESE64Decoder is not thread safe, don't make it a field of this object
    val decoder = new BASE64Decoder()
    val decodedAuthSt = new String(decoder.decodeBuffer(basicAuthSt), "UTF-8")
    val usernamePassword = decodedAuthSt.split(":")
    if (usernamePassword.length >= 2) {
      //account for ":" in passwords
      return Some(usernamePassword(0), usernamePassword.splitAt(1)._2.mkString)
    }
    None
  }

  private def isInsideSecurityRealm(requestHeader: RequestHeader): Boolean = {
    val reqURI = requestHeader.uri
    if (reqURI.length() > 0) {
      //remove the first "/" in the uri
      return reqURI.startsWith("/kudos/")
    }
    false
  }

  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader):
  Future[Result] = {
    Logger.error("In Apply")
    if (!isInsideSecurityRealm(requestHeader)) {
      return nextFilter(requestHeader)
    }

    requestHeader.headers.get("authorization").map { basicAuth =>
      decodeBasicAuth(basicAuth) match {
        case Some((user, pass)) =>
          Logger.info("Doing Auth")
          val res: Boolean = new LDAP().authenticateAccount(user, pass)
          if (res) {
            return nextFilter(requestHeader)
          }

        case _ => ;
      }
      logFailedAttempt(requestHeader)
      return Future.successful(unauthResult)
    }.getOrElse({
      logFailedAttempt(requestHeader)
      Future.successful(unauthResult)
    })

  }
}
