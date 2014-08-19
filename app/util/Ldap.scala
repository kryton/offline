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

case class LDAPServer(domain: String, servers: List[String], port: Int, baseDN: DN, user: String, pass: String, ignoreList: List[DN]) {
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
        p.getString("pass"),
        p.getStringList("OUIgnore").map(x => new DN(x)).toList
      )

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
 //   val searchFilter: String = "(&(samAccountType=" + LDAP.accountTypePerson + ")(sAMAccountName=" + accountName + "))"
    val searchFilter = Filter.createANDFilter(
      Filter.createEqualityFilter("samAccountType", LDAP.accountTypePerson),
      Filter.createEqualityFilter("sAMAccountName",accountName)
    )
    val searchResult = search(searchFilter)
    if (searchResult.length == 1) {
      Some(searchResult.head)
    } else {
      Logger.info("LDAP:getPersonByAccount person NOT Found:" + accountName)
      None
    }
  }

  def getGroupByAccount(accountName: String): Option[SearchResultEntry] = {
    /*
    val searchxFilter: String = "(|" +
      "(&(samAccountType=" + LDAP.accountTypeMailGroup + ")(|(mailNickname=" + accountName + ")(sAMAccountName=" + accountName + ")))" +
      "(&(samAccountType=" + LDAP.accountTypeOtherGroup + ")(sAMAccountName=" + accountName + "))" +
      "(&(samAccountType=" + LDAP.accountTypeAliasObject + ")(sAMAccountName=" + accountName + "))" +
      ")"
      */
    val searchFilter = Filter.createORFilter(
      Filter.createANDFilter(
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypeMailGroup),
        Filter.createORFilter(
          Filter.createEqualityFilter("sAMAccountName", accountName),
          Filter.createEqualityFilter("mailNickname", accountName)
        )
      ),
      Filter.createANDFilter(
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypeOtherGroup),
        Filter.createEqualityFilter("sAMAccountName", accountName)
      ),
      Filter.createANDFilter(
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypeAliasObject),
        Filter.createEqualityFilter("sAMAccountName", accountName)
      )
    )
    val searchResult = search(searchFilter)
    if (searchResult.length == 1) {
      Some(searchResult.head)
    } else {
      Logger.info("LDAP:getGroupByAccount group NOT Found:" + accountName)
      None
    }
  }


  def searchByCN(cn: String): List[SearchResultEntry] = {
    //  val searchFilter: String = "(&(samAccountType=" + LDAP.accountTypePerson + ")(cn=*" + cn + "*))"
    val filter: Filter = Filter.createANDFilter(
      Filter.createEqualityFilter("samAccountType", LDAP.accountTypePerson),
      Filter.createSubstringFilter("cn", null, Array(cn), null)
    )
    search(filter)
  }

  def groupSearchCompact(searchText: String): List[SearchResultEntry] = {
    /*
    val searchFilter =
      "(&(|(samAccountType=" + LDAP.accountTypeMailGroup + ")(samAccountType=" + LDAP.accountTypeOtherGroup + ")(samAccountType=" + LDAP.accountTypeAliasObject + "))" +
        "(|(sAMAccountName=" + searchText + "*)(mailNickname=" + searchText + "*)(cn=" + searchText + "*)(displayName=" + searchText + "*)))"
*/
    val searchFilter = Filter.createANDFilter(
      Filter.createORFilter(
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypeMailGroup),
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypeOtherGroup),
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypeAliasObject)
      ),
      Filter.createORFilter(
        Filter.createSubstringFilter("sAMAccountName", searchText, null, null),
        Filter.createSubstringFilter("mailNickname", searchText, null, null),
        Filter.createSubstringFilter("cn", searchText, null, null),
        Filter.createSubstringFilter("displayName", searchText, null, null)
      )
    )
    search(searchFilter)
      .sortBy(r => if (r.getAttributeValue("cn") == null) {
      "Z"
    } else {
      r.getAttributeValue("cn")
    })
  }

  def personSearchCompact(searchText: String): List[SearchResultEntry] = {
    /*
    val searchFilter = "(&(samAccountType=" + LDAP.accountTypePerson + ")" +
        "(!(msExchHideFromAddressLists=TRUE))" +
        "(|(sAMAccountName=" + searchText + "*)(cn=" + searchText + "*)(sn=" + searchText + "*)(title=" + searchText + "*)))"
      */
    val searchFilter: Filter = Filter.createANDFilter(
      Filter.createEqualityFilter("samAccountType", LDAP.accountTypePerson),
      Filter.createNOTFilter(Filter.createEqualityFilter("msExchHideFromAddressLists", "TRUE")),
      Filter.createORFilter(
        Filter.createSubstringFilter("sAMAccountName", searchText, null, null),
        Filter.createSubstringFilter("cn", searchText, null, null),
        Filter.createSubstringFilter("sn", searchText, null, null),
        Filter.createSubstringFilter("title", searchText, null, null)
      ))

    search(searchFilter)
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
    //  val searchFilter = "(&(samAccountType=" + LDAP.accountTypePerson + ")" +   "(!(msExchHideFromAddressLists=TRUE))"

    //  "(|(sAMAccountName=" + searchText + "*)(cn=" + searchText + "*)(sn=" + searchText + "*)(title=" + searchText + "*))"
    val searchOpts: List[Filter] = List[Option[Filter]](
      if (alias.isDefined && alias.get.trim != "") {
        Some(Filter.createSubstringFilter("sAMAccountName", alias.get, null, null))
      } else {
        None
      },
      if (email.isDefined && email.get.trim != "") {
        Some(Filter.createSubstringFilter("mail", email.get, null, null))
      } else None,
      if (name.isDefined && name.get.trim != "") {
        Some(Filter.createSubstringFilter("sn", name.get, null, null))
      } else None,
      if (title.isDefined && title.get.trim != "") {
        Some(Filter.createSubstringFilter("title", title.get, null, null))
      } else None,
      if (reportsTo.isDefined && reportsTo.get.trim != "") {
        Some(Filter.createSubstringFilter("reports", reportsTo.get, null, null))
      } else None,
      if (phone.isDefined && phone.get.trim != "") {
        Some(
          Filter.createORFilter(
            Filter.createSubstringFilter("otherTelephone", phone.get, null, null),
            Filter.createSubstringFilter("telephoneNumber", phone.get, null, null),
            Filter.createSubstringFilter("mobile", phone.get, null, null)
          )
        )
      } else None,
      if (office.isDefined && office.get.trim != "") {
        Some(Filter.createSubstringFilter("department", office.get, null, null))
      } else None
    ).flatten

    if (searchOpts.isEmpty) {
      search(Filter.createEqualityFilter("cn", "ZZZZZZ"))
    } else {
      search(Filter.createANDFilter(
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypePerson),
        Filter.createNOTFilter(Filter.createEqualityFilter("msExchHideFromAddressLists", "TRUE")),
        Filter.createORFilter(searchOpts)
       )
      ).sortBy(r => (if (r.getAttributeValue("sn") == null) {
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

  private def xxsearch(searchFilter: String): List[SearchResultEntry] = {
    Logger.error("Potentially Unsafe Query being used:" + searchFilter)
    search(Filter.create(searchFilter))
  }

  def search(searchFilter: Filter): List[SearchResultEntry] = {
    // connect
 //   Logger.debug("Search: filter=" + searchFilter)
    val mainServer = serverMap(mainDN)
    val searchRequest: SearchRequest = new SearchRequest(mainServer.baseDN.toString, SearchScope.SUB, searchFilter)

    val connection = mainServer.pool.getConnection
    try {
      val searchResult: SearchResult = connection.search(searchRequest)
      mainServer.pool.releaseConnection(connection)
      searchResult.getSearchEntries.toList
        .filter({ a =>
          val dnName = new DN(a.getAttributeValue("distinguishedName"))
          mainServer.ignoreList.filter({ ou => ou.isAncestorOf(dnName, true)}).isEmpty
          }
        ).filter(r => !r.getAttributeValue("distinguishedName").contains(terminatedDN))
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
  val listTypes = Map(LDAP.accountTypeMailGroup -> "Mailing Lists",
    LDAP.accountTypeOtherGroup -> "Security Groups",
    LDAP.accountTypeAliasObject -> "Aliases"
  )

}

object testLDAP extends App {
  val ldap: LDAP = new LDAP

  //  val person = ldap.getByDistinguishedName("CN=Joel Rangsmo,OU=Users,OU=Stockholm,OU=EMEA,DC=mpls,DC=digitalriver,DC=com")
  val people = ldap.personSearchDetailed(Some("iholsman"), Some("ihol"), None, None, None, None, Some("Cahall"))
  System.out.println("Results")
  System.out.println(people)
}
