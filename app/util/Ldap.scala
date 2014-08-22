package util

import com.typesafe.config.ConfigFactory
import com.unboundid.ldap.sdk._
import play.Logger
import collection.JavaConversions._

case class LDAPServer(domain: String, servers: List[String], port: Int, baseDN: DN,
                      user: String, pass: String, ignoreList: List[DN],
                      terminatedDN: String) {
  val ldapServerSet = new RoundRobinServerSet(servers.toArray[java.lang.String], servers.map(_ => port).toArray[Int])
  val bindRequest: BindRequest = new SimpleBindRequest(user, pass)
  val pool = new LDAPConnectionPool(ldapServerSet, bindRequest, servers.size * 2)

  def getByDistinguishedName(dn: String): Option[SearchResultEntry] = {
    if (dn == null || dn.contains(terminatedDN)) {
      None
    } else {
      val connection = pool.getConnection

      try {
        val result = connection.getEntry(dn)
        pool.releaseConnection(connection)
        Some(result)
      } catch {
        case lse: LDAPSearchException =>
          Logger.error("LDAP:getByDistinguishedName Exception", lse)
          pool.releaseConnection(connection)
          None
        case ex: LDAPException =>
          Logger.error("LDAP:getByDistinguishedName Exception", ex)
          pool.releaseDefunctConnection(connection)
          None
      }
    }
  }

  def search(searchFilter: Filter): List[SearchResultEntry] = {
    val searchRequest: SearchRequest = new SearchRequest(this.baseDN.toString, SearchScope.SUB, searchFilter)
    Logger.debug("search:"+domain+"\t"+searchFilter)
    val connection = pool.getConnection
    try {
      val searchResult: SearchResult = connection.search(searchRequest)
      pool.releaseConnection(connection)
      searchResult.getSearchEntries.toList
        .filter({ a =>
        val dnName = new DN(a.getAttributeValue("distinguishedName"))
        this.ignoreList.filter({ ou => ou.isAncestorOf(dnName, true)}).isEmpty
      }
      ).filter(r => !r.getAttributeValue("distinguishedName").contains(terminatedDN))
    } catch {
      case lse: LDAPSearchException =>
        Logger.error("Search:Search Exception", lse)
        pool.releaseConnection(connection)
        new Array[SearchResultEntry](0).toList
      case ex: LDAPException =>
        Logger.error("Search:LDAP Exception", ex)
        pool.releaseDefunctConnection(connection)
        new Array[SearchResultEntry](0).toList
    }
  }
}


class LDAP {
  val mainDN = ConfigFactory.load().getString("ldap.mainDomain")
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
        p.getStringList("OUIgnore").map(x => new DN(x)).toList,
        p.getString("terminatedGroup")
      )

    }
    servers.map(a => (a.domain, a)).toMap
  }

  private def server(dn: DN): LDAPServer = {

    val availableServers = serverMap.filter(_._2.baseDN.isAncestorOf(dn, true))
    if (availableServers.nonEmpty) {
      val server: LDAPServer = availableServers.head._2
      server
    } else {
      System.err.println("LDAP:Couldn't find a match for :" + dn)
      val server = serverMap(mainDN)
      server
    }
  }
  def domain(dn: String): String = {
    server( new DN(dn)).domain
  }

  def getPersonByAccount(accountName: String, domain: Option[String] = None): List[SearchResultEntry] = {
    val searchFilter = Filter.createANDFilter(
      Filter.createEqualityFilter("samAccountType", LDAP.accountTypePerson),
      Filter.createEqualityFilter("sAMAccountName", accountName)
    )
    domain match {
      case None => search(searchFilter)
      case Some(p) => serverMap.getOrElse(p, serverMap(mainDN)).search(searchFilter)
    }
  }

  def getGroupsByAccount(accountName: String, domain: Option[String] = None): List[SearchResultEntry] = {

    val searchFilter = Filter.createORFilter(
      Filter.createANDFilter(
        Filter.createEqualityFilter("samAccountType", LDAP.accountTypeMailGroup),
      //  Filter.createORFilter(
          Filter.createEqualityFilter("sAMAccountName", accountName)
  //        ,Filter.createEqualityFilter("mailNickname", accountName)
  //      )
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
    domain match {
      case None => search(searchFilter)
      case Some(p) => serverMap.getOrElse(p, serverMap(mainDN)).search(searchFilter)
    }
  }


  def searchByCN(cn: String): List[SearchResultEntry] = {
    val filter: Filter = Filter.createANDFilter(
      Filter.createEqualityFilter("samAccountType", LDAP.accountTypePerson),
      Filter.createSubstringFilter("cn", null, Array(cn), null)
    )
    search(filter)
  }

  def groupSearchCompact(searchText: String): List[SearchResultEntry] = {
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

  def search(searchFilter: Filter): List[SearchResultEntry] = {
    serverMap.map(a => a._2.search(searchFilter)).flatten.toList
  }

  def getByDistinguishedName(dn: String): Option[SearchResultEntry] = {
    if (dn == null) {
      None
    } else {
      server(new DN(dn)).getByDistinguishedName(dn)
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

  val people = ldap.personSearchDetailed(Some("iholsman"), Some("ihol"), None, None, None, None, Some("hols"))
  System.out.println("Results")
  System.out.println(people)
}
