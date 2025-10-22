package org.edena.store.elastic.json.company

import net.codingwell.scalaguice.ScalaModule
import java.{util => ju}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.edena.store.elastic.ElasticBaseTest
import org.edena.store.elastic.json.company.Company.CompanyIdentity
import org.edena.store.elastic.json.company.StoreTypes.CompanyStore
import org.edena.core.store.Criterion._
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ElasticCompanyTest extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ElasticBaseTest {

  override protected val modules = super.modules ++ Seq(new CompanyStoreModule())

  private val companyStore = instance[CompanyStore]
  private implicit val actorSystem = instance[ActorSystem]
  private implicit val materializer = instance[Materializer]

  private implicit val ec = materializer.executionContext

  private val fixedId = CompanyIdentity.next

  // Sample addresses for double nested testing
  private val address1 = Address("123 Tech Street", "San Francisco", "CA", "94105", "USA")
  private val address2 = Address("456 Finance Ave", "New York", "NY", "10004", "USA")
  private val address3 = Address("789 Retail Blvd", "Chicago", "IL", "60601", "USA")
  private val address4 = Address("321 Industrial Rd", "Detroit", "MI", "48201", "USA")
  private val address5 = Address("555 Startup Way", "Palo Alto", "CA", "94301", "USA")
  private val address6 = Address("777 Wall Street", "New York", "NY", "10005", "USA")
  private val address7 = Address("999 Main Street", "Chicago", "IL", "60602", "USA")
  private val address8 = Address("111 Factory Lane", "Detroit", "MI", "48202", "USA")

  // Sample employees for testing (directors - objectField)
  private val ceo1 = Employee("John", "Smith", "john.smith@techcorp.com", "CEO", 250000.0, new ju.Date(120, 0, 15), address1)
  private val ceo2 = Employee("Jane", "Doe", "jane.doe@finserv.com", "CEO", 300000.0, new ju.Date(118, 5, 1), address2)
  private val ceo3 = Employee("Bob", "Johnson", "bob.johnson@retail.com", "CEO", 200000.0, new ju.Date(115, 8, 20), address3)
  private val ceo4 = Employee("Alice", "Williams", "alice.williams@manufacturing.com", "CEO", 180000.0, new ju.Date(110, 3, 10), address4)

  // Sample owners (nestedField - will require NestedQuery wrapping)
  private val owner1 = Employee("Steve", "Miller", "steve.miller@techcorp.com", "Founder", 500000.0, new ju.Date(100, 0, 1), address5)
  private val owner2 = Employee("Linda", "Garcia", "linda.garcia@finserv.com", "Founder", 600000.0, new ju.Date(105, 6, 15), address6)
  private val owner3 = Employee("Tom", "Davis", "tom.davis@retail.com", "Founder", 400000.0, new ju.Date(95, 3, 20), address7)
  private val owner4 = Employee("Sarah", "Martinez", "sarah.martinez@manufacturing.com", "Founder", 350000.0, new ju.Date(90, 9, 5), address8)

  "ElasticCompanyTest" should "save items" in {
    println("1. Test - save items")

    for {
      ids <- companyStore.save(Seq(
        Company(Some(fixedId), "TechCorp Inc", "Technology", ceo1, owner1, 500, 50000000.0, new ju.Date(100, 0, 1)),
        Company(None, "FinServ Ltd", "Financial Services", ceo2, owner2, 250, 30000000.0, new ju.Date(105, 6, 15)),
        Company(None, "Retail Global", "Retail", ceo3, owner3, 1000, 100000000.0, new ju.Date(95, 3, 20)),
        Company(None, "Manufacturing Co", "Manufacturing", ceo4, owner4, 750, 75000000.0, new ju.Date(90, 9, 5))
      ))

      _ <- companyStore.refresh

      count <- companyStore.count()
    } yield {
      ids should not be empty
      ids should have size (4)
      ids.head should be (fixedId)
      count should be (4)
    }
  }

  "ElasticCompanyTest" should "get an item" in {
    Thread.sleep(1000) // sleep 1 sec
    println("2. Test - get an item")

    companyStore.get(fixedId).map { company =>
      println(company)

      company should not be None
      company.get.id should be (Some(fixedId))
      company.get.name should be ("TechCorp Inc")
      company.get.industry should be ("Technology")
      company.get.director.firstName should be ("John")
      company.get.director.lastName should be ("Smith")
      company.get.director.email should be ("john.smith@techcorp.com")
      company.get.director.position should be ("CEO")
      // Verify double nested director address
      company.get.director.address.street should be ("123 Tech Street")
      company.get.director.address.city should be ("San Francisco")
      company.get.director.address.state should be ("CA")
      company.get.director.address.zipCode should be ("94105")
      company.get.director.address.country should be ("USA")
      // Verify double nested owner address
      company.get.owner.address.street should be ("555 Startup Way")
      company.get.owner.address.city should be ("Palo Alto")
      company.get.employeeCount should be (500)
      company.get.revenue should be (50000000.0)
    }
  }

  "ElasticCompanyTest" should "count" in {
    println("3. Test - count")

    companyStore.count().map { count =>
      count should be (4)
    }
  }

  "ElasticCompanyTest" should "find items by industry" in {
    println("4. Test - find items by industry")

    companyStore.find(
      "industry" #== "Technology"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("TechCorp Inc")
      companies.head.industry should be ("Technology")
    }
  }

  "ElasticCompanyTest" should "find items by employee count range" in {
    println("5. Test - find items by employee count range")

    companyStore.find(
      ("employeeCount" #>= 250) AND ("employeeCount" #<= 750)
    ).map { companies =>
      companies should have size 3
      companies.map(_.name).toSet should be (Set("TechCorp Inc", "FinServ Ltd", "Manufacturing Co"))
    }
  }

  "ElasticCompanyTest" should "find items by nested director field" in {
    println("6. Test - find items by nested director field")

    companyStore.find(
      "director.firstName" #== "Jane"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("FinServ Ltd")
      companies.head.director.lastName should be ("Doe")
    }
  }

  "ElasticCompanyTest" should "find items by nested director field with firstName John" in {
    println("6b. Test - find items by nested director.firstName == John")

    companyStore.find(
      "director.firstName" #== "John"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("TechCorp Inc")
      companies.head.director.firstName should be ("John")
      companies.head.director.lastName should be ("Smith")
      companies.head.director.email should be ("john.smith@techcorp.com")
    }
  }

  "ElasticCompanyTest" should "find items by multiple nested director fields" in {
    println("6c. Test - find items by multiple nested director fields")

    companyStore.find(
      ("director.firstName" #== "Bob") AND ("director.lastName" #== "Johnson")
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("Retail Global")
      companies.head.director.position should be ("CEO")
    }
  }

  "ElasticCompanyTest" should "find items by nested director salary range" in {
    println("6d. Test - find items by nested director.salary range")

    companyStore.find(
      ("director.salary" #>= 200000.0) AND ("director.salary" #<= 300000.0)
    ).map { companies =>
      companies should have size 3
      companies.map(_.name).toSet should be (Set("TechCorp Inc", "FinServ Ltd", "Retail Global"))
    }
  }

  "ElasticCompanyTest" should "find items by nested owner field (nestedField)" in {
    println("6e. Test - find items by nested owner.firstName == Linda")

    companyStore.find(
      "owner.firstName" #== "Linda"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("FinServ Ltd")
      companies.head.owner.lastName should be ("Garcia")
    }
  }

  "ElasticCompanyTest" should "find items by multiple nested owner fields (nestedField)" in {
    println("6f. Test - find items by multiple nested owner fields")

    companyStore.find(
      ("owner.firstName" #== "Steve") AND ("owner.position" #== "Founder")
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("TechCorp Inc")
      companies.head.owner.email should be ("steve.miller@techcorp.com")
    }
  }

  "ElasticCompanyTest" should "find items by nested owner salary range (nestedField)" in {
    println("6g. Test - find items by nested owner.salary >= 400000")

    companyStore.find(
      "owner.salary" #>= 400000.0
    ).map { companies =>
      companies should have size 3
      companies.map(_.name).toSet should be (Set("TechCorp Inc", "FinServ Ltd", "Retail Global"))
    }
  }

  // Double nested field tests (objectField -> objectField: director.address)
  "ElasticCompanyTest" should "find items by double nested director address city" in {
    println("7a. Test - find items by double nested director.address.city")

    companyStore.find(
      "director.address.city" #== "San Francisco"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("TechCorp Inc")
      companies.head.director.address.city should be ("San Francisco")
      companies.head.director.address.state should be ("CA")
    }
  }

  "ElasticCompanyTest" should "find items by double nested director address state" in {
    println("7b. Test - find items by double nested director.address.state")

    companyStore.find(
      "director.address.state" #== "NY"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("FinServ Ltd")
      companies.head.director.address.city should be ("New York")
    }
  }

  "ElasticCompanyTest" should "find items by double nested director address country" in {
    println("7c. Test - find items by double nested director.address.country")

    companyStore.find(
      "director.address.country" #== "USA"
    ).map { companies =>
      companies should have size 4
      companies.map(_.name).toSet should be (Set("TechCorp Inc", "FinServ Ltd", "Retail Global", "Manufacturing Co"))
    }
  }

  "ElasticCompanyTest" should "find items by multiple double nested director address fields" in {
    println("7d. Test - find items by multiple double nested director.address fields")

    companyStore.find(
      ("director.address.city" #== "Chicago") AND ("director.address.zipCode" #== "60601")
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("Retail Global")
      companies.head.director.address.street should be ("789 Retail Blvd")
    }
  }

  // Double nested field tests (nestedField -> objectField: owner.address)
  "ElasticCompanyTest" should "find items by double nested owner address city (nestedField parent)" in {
    println("7e. Test - find items by double nested owner.address.city")

    companyStore.find(
      "owner.address.city" #== "Palo Alto"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("TechCorp Inc")
      companies.head.owner.address.city should be ("Palo Alto")
      companies.head.owner.address.state should be ("CA")
    }
  }

  "ElasticCompanyTest" should "find items by double nested owner address state (nestedField parent)" in {
    println("7f. Test - find items by double nested owner.address.state == NY")

    companyStore.find(
      "owner.address.state" #== "NY"
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("FinServ Ltd")
      companies.head.owner.address.street should be ("777 Wall Street")
    }
  }

  "ElasticCompanyTest" should "find items by multiple double nested owner address fields (nestedField parent)" in {
    println("7g. Test - find items by multiple double nested owner.address fields")

    companyStore.find(
      ("owner.address.city" #== "Chicago") AND ("owner.address.street" #== "999 Main Street")
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("Retail Global")
      companies.head.owner.firstName should be ("Tom")
    }
  }

  "ElasticCompanyTest" should "find items by combining director and owner double nested address fields" in {
    println("7h. Test - combine director.address and owner.address fields")

    companyStore.find(
      ("director.address.state" #== "CA") AND ("owner.address.state" #== "CA")
    ).map { companies =>
      companies should have size 1
      companies.head.name should be ("TechCorp Inc")
      companies.head.director.address.city should be ("San Francisco")
      companies.head.owner.address.city should be ("Palo Alto")
    }
  }

  private val allFieldsSorted = Seq(
    "id",
    "name",
    "industry",
    "director",
    "owner",
    "employeeCount",
    "revenue",
    "founded"
  ).sorted

  "ElasticCompanyTest" should "find items as value maps (w/o projection)" in {
    println("7. Test - find items as value maps (w/o projection)")

    companyStore.findAsValueMap("industry" #== "Technology", projection = Nil).map { companyMaps =>
      companyMaps should have size 1

      println(companyMaps.mkString("\n"))

      companyMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (allFieldsSorted)
    }
  }

  "ElasticCompanyTest" should "find items as value maps with projection" in {
    println("8. Test - find items as value maps with projection")

    val projection = Seq("name", "industry", "revenue")

    companyStore.findAsValueMap("revenue" #> 40000000.0, projection = projection).map { companyMaps =>
      companyMaps should have size 3

      println(companyMaps.mkString("\n"))

      companyMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection.sorted)
    }
  }

  "ElasticCompanyTest" should "find items with objectField sub-fields in projection" in {
    println("8b. Test - find items with objectField (director) sub-fields in projection")

    val projection = Seq("name", "director.firstName", "director.lastName", "revenue")

    companyStore.findAsValueMap("industry" #== "Technology", projection = projection).map { companyMaps =>
      println(companyMaps.mkString("\n"))

      companyMaps should have size 1
      companyMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection.sorted)

      // Verify director sub-fields are present
      companyMaps.head.get("director.firstName") should be (Some(Some("John")))
      companyMaps.head.get("director.lastName") should be (Some(Some("Smith")))
    }
  }

  "ElasticCompanyTest" should "NOT project nestedField sub-fields (Elasticsearch limitation)" in {
    println("8c. Test - nestedField (owner) sub-fields NOT supported in projection")

    // Note: Elasticsearch storedFields does not support nested field projections
    // Nested fields require access via _source or inner_hits
    val projection = Seq("name", "owner.firstName", "owner.lastName", "revenue")

    companyStore.findAsValueMap("industry" #== "Technology", projection = projection).map { companyMaps =>
      println(companyMaps.mkString("\n"))

      companyMaps should have size 1

      // Only non-nested fields are returned
      val returnedFields = companyMaps.flatMap(_.keySet).toSet.toSeq.sorted
      println(s"Returned fields: $returnedFields")

      // Nested field sub-fields are NOT returned (Elasticsearch limitation)
      companyMaps.head.contains("owner.firstName") should be (false)
      companyMaps.head.contains("owner.lastName") should be (false)

      // But objectField and primitive fields are returned
      companyMaps.head.contains("name") should be (true)
      companyMaps.head.contains("revenue") should be (true)
    }
  }

  "ElasticCompanyTest" should "support objectField but not nestedField in mixed projection" in {
    println("8d. Test - mixed projection: objectField works, nestedField does not")

    val projection = Seq("name", "director.firstName", "owner.lastName", "employeeCount")

    companyStore.findAsValueMap("employeeCount" #>= 500, projection = projection).map { companyMaps =>
      println(companyMaps.mkString("\n"))

      companyMaps should have size 3

      // Only objectField and primitive fields are returned
      companyMaps.forall(_.contains("director.firstName")) should be (true)
      companyMaps.forall(_.contains("employeeCount")) should be (true)
      companyMaps.forall(_.contains("name")) should be (true)

      // nestedField sub-fields are NOT returned (Elasticsearch limitation)
      companyMaps.forall(!_.contains("owner.lastName")) should be (true)
    }
  }

  "ElasticCompanyTest" should "project double nested objectField sub-fields (director.address.*)" in {
    println("8e. Test - projection with double nested objectField (director.address)")

    val projection = Seq("name", "director.firstName", "director.address.city", "director.address.state")

    companyStore.findAsValueMap("industry" #== "Technology", projection = projection).map { companyMaps =>
      println(companyMaps.mkString("\n"))

      companyMaps should have size 1
      companyMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection.sorted)

      // Verify double nested objectField sub-fields are present
      companyMaps.head.get("director.firstName") should be (Some(Some("John")))
      companyMaps.head.get("director.address.city") should be (Some(Some("San Francisco")))
      companyMaps.head.get("director.address.state") should be (Some(Some("CA")))
    }
  }

  "ElasticCompanyTest" should "NOT project double nested nestedField sub-fields (owner.address.*)" in {
    println("8f. Test - projection with double nested nestedField (owner.address) - NOT supported")

    // Note: Elasticsearch storedFields does not support nested field projections, even for double nesting
    val projection = Seq("name", "owner.firstName", "owner.address.city", "owner.address.state")

    companyStore.findAsValueMap("industry" #== "Technology", projection = projection).map { companyMaps =>
      println(companyMaps.mkString("\n"))

      companyMaps should have size 1

      // Only non-nested fields are returned
      companyMaps.head.contains("name") should be (true)

      // Nested field sub-fields (including double nested) are NOT returned
      companyMaps.head.contains("owner.firstName") should be (false)
      companyMaps.head.contains("owner.address.city") should be (false)
      companyMaps.head.contains("owner.address.state") should be (false)
    }
  }

  "ElasticCompanyTest" should "find items as streamed value maps" in {
    println("9. Test - find items as streamed value maps")

    for {
      companySource <- companyStore.findAsValueMapStream("employeeCount" #> 200, projection = Nil)

      companyMaps <- companySource.runWith(Sink.seq)
    } yield {
      companyMaps should have size 4

      println(companyMaps.mkString("\n"))

      companyMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (allFieldsSorted)
    }
  }

  "ElasticCompanyTest" should "find items with OR condition" in {
    println("10. Test - find items with OR")

    companyStore.find(
      criterion = ("industry" #== "Technology") OR (("employeeCount" #> 700) AND ("revenue" #> 70000000.0))
    ).map { companies =>
      println(companies.mkString("\n"))

      companies should have size 3
      companies.map(_.name).toSet should contain ("TechCorp Inc")
      companies.map(_.name).toSet should contain ("Retail Global")
      companies.map(_.name).toSet should contain ("Manufacturing Co")
    }
  }

  "ElasticCompanyTest" should "update a company's director" in {
    println("11. Test - update company's director")

    val newDirectorAddress = Address("888 Executive Plaza", "San Francisco", "CA", "94106", "USA")
    val newDirector = Employee("Michael", "Brown", "michael.brown@techcorp.com", "CEO", 280000.0, new ju.Date(125, 0, 1), newDirectorAddress)

    for {
      company <- companyStore.get(fixedId)
      _ <- companyStore.update(company.get.copy(director = newDirector))
      updatedCompany <- companyStore.get(fixedId)
    } yield {
      updatedCompany should not be None
      updatedCompany.get.director.firstName should be ("Michael")
      updatedCompany.get.director.lastName should be ("Brown")
      updatedCompany.get.director.email should be ("michael.brown@techcorp.com")
    }
  }

  override protected def beforeAll = {
    println("Before All - Cleanup")
    Await.result(companyStore.deleteAll, 30.seconds)
  }

  override protected def afterAll = {
    println("After All - Cleanup")
    Await.result(companyStore.deleteAll, 30.seconds)
  }
}

class CompanyStoreModule extends ScalaModule {
  override def configure = {
    bind[CompanyStore].to(classOf[ElasticCompanyStore]).asEagerSingleton
  }
}
