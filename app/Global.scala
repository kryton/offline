import play.api.mvc.WithFilters
import play.filters.csrf.CSRFFilter
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.Future

/**
 * Created by iholsman on 11/08/2014.
 */
object Global extends  WithFilters(CSRFFilter(),SecurityHeadersFilter(), new GzipFilter()) {
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

