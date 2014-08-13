package util

import java.security.GeneralSecurityException
import javax.net.SocketFactory

import akka.event.Logging
import com.typesafe.config.ConfigFactory
import com.unboundid.ldap.sdk._
import com.unboundid.ldap.sdk.controls.{SortKey, ServerSideSortRequestControl, PasswordExpiringControl, PasswordExpiredControl}
import com.unboundid.util.ssl.{SSLUtil, TrustAllTrustManager}
import play.{Logger, Play}
import collection.JavaConversions._

case class LDAPServer(domain: String, servers: List[String], port: Int, baseDN: DN, user: String, pass: String) {
  // var connection: LDAPConnection = connect()
  val ldapServerSet = new RoundRobinServerSet(servers.toArray[java.lang.String], servers.map(_ => port).toArray[Int])
  val bindRequest: BindRequest = new SimpleBindRequest(user, pass)
  val pool = new LDAPConnectionPool(ldapServerSet, bindRequest, servers.size * 2)
}

class LDAP {
  val mainDN = ConfigFactory.load().getString("ldap.mainDomain")
  val terminatedDN = ConfigFactory.load().getString("ldap.terminatedGroup")
  val serverMap: Map[String, LDAPServer] = {
    System.err.println("Rebuilding LDAP connections")
    val port = ConfigFactory.load().getInt("ldap.port")
    val servers = ConfigFactory.load().getConfigList("ldap.servers") map {
      p => LDAPServer(p.getString("domain"),
        p.getStringList("servers").toList,
        port,
        new DN(p.getString("dn")),
        p.getString("user"),
        p.getString("pass"))
    }
    servers.map(a => (a.domain, a)).toMap
  }

  // val ldapConnection = serverMap(mainDN).connection

  private def server(dn: DN): LDAPServer = {

    val availableServers = serverMap.filter(_._2.baseDN.isAncestorOf(dn, true))
    if (availableServers.nonEmpty) {
      val server: LDAPServer = availableServers.head._2
      server
    } else {
      System.err.println("Couldn't find a match for " + dn)
      val server = serverMap(mainDN)
      server
    }
  }

  def getPersonByAccount(accountName: String): Option[SearchResultEntry] = {
    val searchFilter: String = "(&(samAccountType=" + LDAP.accountTypePerson + ")(sAMAccountName=" + accountName + "))"
    val searchResult = search(searchFilter)
    if (searchResult.length == 1) {
      Some(searchResult.get(0))
    } else {
      Logger.info("LDAP:getPersonByAccount person NOT Found" + accountName)
      None
    }
  }

  def getGroupByAccount(accountName: String): Option[SearchResultEntry] = {
    val searchFilter: String = "(|" +
      "(&(samAccountType=" + LDAP.accountTypeMailGroup + ")(mailNickname=" + accountName + "))" +
      "(&(samAccountType=" + LDAP.accountTypeOtherGroup + ")(sAMAccountName=" + accountName + "))" +
      "(&(samAccountType=" + LDAP.accountTypeAliasObject + ")(sAMAccountName=" + accountName + "))" +
      ")"
    val searchResult = search(searchFilter)
    if (searchResult.length == 1) {
      Some(searchResult.get(0))
    } else {
      Logger.info("LDAP:getGroupByAccount group NOT Found" + accountName)
      None
    }
  }


  def searchByCN(cn: String): List[SearchResultEntry] = {
    val searchFilter: String = "(&(samAccountType=" + LDAP.accountTypePerson + ")(cn=*" + cn + "*))"
    search(searchFilter)
  }

  def groupSearchCompact(searchText: String): List[SearchResultEntry] = {
    val searchFilter =
      "(&(|(samAccountType=" + LDAP.accountTypeMailGroup + ")(samAccountType=" + LDAP.accountTypeOtherGroup + ")(samAccountType=" + LDAP.accountTypeAliasObject + "))" +
        "(|(sAMAccountName=" + searchText + "*)(mailNickname=" + searchText + "*)(cn=" + searchText + "*)(displayName=" + searchText + "*)))"

    // TODO: the msExchHideFromAddressLists != OU=Terminated Employees
    search(searchFilter).filter(r => !r.getAttributeValue("distinguishedName").contains(terminatedDN))
      .sortBy(r => if (r.getAttributeValue("cn") == null) {
      "Z"
    } else {
      r.getAttributeValue("cn")
    })
  }

  def personSearchCompact(searchText: String): List[SearchResultEntry] = {
    val searchFilter = "(&(samAccountType=" + LDAP.accountTypePerson + ")" +
      "(!(msExchHideFromAddressLists=TRUE))" +
      "(|(sAMAccountName=" + searchText + "*)(cn=" + searchText + "*)(sn=" + searchText + "*)(title=" + searchText + "*)))"

    // TODO: the msExchHideFromAddressLists != OU=Terminated Employees
    search(searchFilter).filter(r => !r.getAttributeValue("distinguishedName").contains(terminatedDN))
      .sortBy(r => (if (r.getAttributeValue("sn") == null) {
      "Z"
    } else {
      r.getAttributeValue("sn")
    }, if (r.getAttributeValue("cn") == null) {
      "Z"
    } else {
      r.getAttributeValue("cn")
    }))
  }

  def personSearchDetailed(alias: Option[String],
                           email: Option[String],
                           name: Option[String],
                           title: Option[String],
                           reportsTo: Option[String],
                           phone: Option[String],
                           office: Option[String]): List[SearchResultEntry] = {
    val searchFilter = "(&(samAccountType=" + LDAP.accountTypePerson + ")" +
      "(!(msExchHideFromAddressLists=TRUE))"

    //  "(|(sAMAccountName=" + searchText + "*)(cn=" + searchText + "*)(sn=" + searchText + "*)(title=" + searchText + "*))"
    val searchOpts: List[String] = List[String](
      if (alias.isDefined && alias.get.trim != "") {
        "(sAMAccountName=" + alias.get + "*)"
      } else "",
      if (email.isDefined && email.get.trim != "") {
        "(mail=" + email.get + "*)"
      } else "",
      if (name.isDefined && name.get.trim != "") {
        "(sn=" + name.get + "*)"
      } else "",
      if (title.isDefined && title.get.trim != "") {
        "(title=" + title.get + "*)"
      } else "",
      if (reportsTo.isDefined && reportsTo.get.trim != "") {
        "(reports=" + reportsTo.get + "*)"
      } else "",
      if (phone.isDefined && phone.get.trim != "") {
        "(otherTelephone=" + phone.get + "*)(telephoneNumber=" + phone.get + "*)(mobile=" + phone.get + "*)"
      } else "",
      if (office.isDefined && office.get.trim != "") {
        "(department=" + office.get + "*)"
      } else ""
    ).filter(_ != "")

    if (searchOpts.isEmpty) {
      search(searchFilter + "(cn=ZZZZZ))")
    } else {
      // TODO: the msExchHideFromAddressLists != OU=Terminated Employees
      search(searchFilter + "(|" + searchOpts.mkString("") + "))").filter(r => !r.getAttributeValue("distinguishedName").contains(terminatedDN))
        .sortBy(r => (if (r.getAttributeValue("sn") == null) {
        "Z"
      } else {
        r.getAttributeValue("sn")
      }, if (r.getAttributeValue("cn") == null) {
        "Z"
      } else {
        r.getAttributeValue("cn")
      }))
    }
  }

  def search(searchFilter: String): List[SearchResultEntry] = {
    // connect
    Logger.debug("Search: filter=" + searchFilter)
    val mainServer = serverMap(mainDN)
    val searchRequest: SearchRequest = new SearchRequest(mainServer.baseDN.toString(), SearchScope.SUB, searchFilter)

    val connection = mainServer.pool.getConnection
    try {
      val searchResult: SearchResult = connection.search(searchRequest)
      mainServer.pool.releaseConnection(connection)
      searchResult.getSearchEntries.toList
    } catch {
      case lse: LDAPSearchException =>
        Logger.error("Search:Search Exception", lse)
        mainServer.pool.releaseConnection(connection)
        new Array[SearchResultEntry](0).toList
      case ex: LDAPException =>
        Logger.error("Search:LDAP Exception", ex)
        mainServer.pool.releaseDefunctConnection(connection)
        new Array[SearchResultEntry](0).toList
    }
  }

  def getByDistinguishedName(dn: String): Option[SearchResultEntry] = {
    if (dn == null) {
      None
    } else {
      val connectedServer = server(new DN(dn))
      val connection = connectedServer.pool.getConnection

      try {
        val result = connection.getEntry(dn)
        connectedServer.pool.releaseConnection(connection)
        Some(result)
      } catch {
        case lse: LDAPSearchException =>
          Logger.error("LDAP:getByDistinguishedName Exception", lse)
          connectedServer.pool.releaseConnection(connection)
          None
        case ex: LDAPException =>
          Logger.error("LDAP:getByDistinguishedName Exception", ex)
          connectedServer.pool.releaseDefunctConnection(connection)
          None
      }
    }
  }
}

object LDAP {
  val accountTypePerson = "805306368"
  val accountTypeMailGroup = "268435457"
  val accountTypeOtherGroup = "268435456"
  val accountTypeAliasObject = "536870912"

}

object testLDAP extends App {
  val ldap: LDAP = new LDAP

  //  val person = ldap.getByDistinguishedName("CN=Joel Rangsmo,OU=Users,OU=Stockholm,OU=EMEA,DC=mpls,DC=digitalriver,DC=com")
  val people = ldap.personSearchDetailed(Some("iholsman"), Some("ihol"), None, None, None, None, Some("Cahall"))
  System.out.println("Results")
  System.out.println(people)
}
