package scala.org.edena.ada.server.models

import org.edena.ada.server.models.User
import org.scalatest._
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID


class UserSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  behavior of "User"

  it should "be serializable" in {
    val id = BSONObjectID.parse("5dc029c50c00000f059e1ccd") getOrElse fail
    val user = User(Some(id), "userName", None, "User Name", "userName@me.com", List("ROLE_A", "ROLE_B"), List("PERM_A", "PERM_B"), true)
    val serial = Json.toJson(user).toString()
    assert(serial == "{\"_id\":{\"$oid\":\"5dc029c50c00000f059e1ccd\"},\"userId\":\"userName\",\"name\":\"User Name\",\"email\":\"userName@me.com\",\"roles\":[\"ROLE_A\",\"ROLE_B\"],\"permissions\":[\"PERM_A\",\"PERM_B\"],\"locked\":true}")
  }

  it should "be de-serializable" in {
    val serial = "{\"_id\":{\"$oid\":\"5dc029c50c00000f059e1ccd\"},\"userId\":\"userName\",\"name\":\"User Name\",\"email\":\"userName@me.com\",\"roles\":[\"ROLE_A\",\"ROLE_B\"],\"permissions\":[\"PERM_A\",\"PERM_B\"],\"locked\":true}"
    val json = Json.parse(serial)
    val user = Json.fromJson[User](json) getOrElse fail
    assert(user._id.getOrElse(fail).stringify == "5dc029c50c00000f059e1ccd")
    assert(user.userId == "userName")
    assert(user.email == "userName@me.com")
    assert(user.roles == List("ROLE_A", "ROLE_B"))
    assert(user.permissions == List("PERM_A", "PERM_B"))
    assert(user.locked)
  }
}
