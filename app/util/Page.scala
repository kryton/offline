package util

/**
 * Created by iholsman on 11/08/2014.
 */

case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long, pageSize:Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}
