import play.api.mvc.WithFilters
import play.filters.csrf.CSRFFilter
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter
import com.kenshoo.play.metrics.MetricsFilter
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import util.LDAP
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
/**
 * Created by iholsman on 11/08/2014.
 */
object Global extends  WithFilters(CSRFFilter(),SecurityHeadersFilter(), new GzipFilter(), MetricsFilter,AccessLog) {

  override def onError(request: RequestHeader, ex: Throwable) = {
    Future.successful(InternalServerError(
      views.html.errorPage(ex)
    ))
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound(
      views.html.notFoundPage(request.path)
    ))
  }
  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest("Bad Request: " + error))
  }


}

object AccessLog extends Filter {
  val accessLogger = Logger("access")
  def apply(nextFilter: (RequestHeader) => Future[Result])  (request: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis
    nextFilter(request).map { result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime
      val msg = s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress} " +
        s"domain=${request.domain} query-string=${request.rawQueryString} " +
        s"referer=${request.headers.get("referer").getOrElse("N/A")} " +
        s"timing=${requestTime} " +
        s"user-agent=[${request.headers.get("user-agent").getOrElse("N/A")}]"
      accessLogger.info(msg)
      result.withHeaders("X-Request-Time" -> requestTime.toString)
    }
  }
}
