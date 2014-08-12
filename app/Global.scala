import play.api.mvc.WithFilters
import play.filters.csrf.CSRFFilter
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter

/**
 * Created by iholsman on 11/08/2014.
 */
object Global extends  WithFilters(CSRFFilter(),SecurityHeadersFilter(), new GzipFilter()) {

}

