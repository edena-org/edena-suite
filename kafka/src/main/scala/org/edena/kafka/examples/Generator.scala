package org.edena.kafka.examples

import java.time.LocalDate
import java.util.UUID

object Generator {
  def articles: List[Article] = {
    List(
      Article(
        UUID.randomUUID.toString,
        "Introduction to Scala Programming",
        "Scala is a powerful programming language...",
        LocalDate.now(),
        Author(1, "John Doe")
      ),
      Article(
        UUID.randomUUID.toString,
        "Introduction to Scala Spire",
        "Spire  is a powerful numerical library...",
        LocalDate.now(),
        Author(2, "Jane Doe")
      ),
      Article(
        UUID.randomUUID.toString,
        "Introduction to Kafka",
        "In this article, we'll have an overview of kafka in scala...",
        LocalDate.now(),
        Author(3, "Foo Bar")
      )
    )
  }

  def users: List[User] = {
    List(
      User(
        UUID.randomUUID(),
        "Alice Johnson",
        "alice.johnson@example.com",
        28,
//        LocalDate.of(2023, 3, 15),
        true,
        Some("Photography")
      ),
      User(
        UUID.randomUUID(),
        "Bob Smith",
        "bob.smith@techcorp.io",
        34,
//        LocalDate.of(2023, 1, 8),
        true,
        None
      ),
      User(
        UUID.randomUUID(),
        "Carol Davis",
        "carol.davis@university.edu",
        22,
//        LocalDate.of(2023, 9, 20),
        false,
        Some("Gaming")
      )
    )
  }
}
