package org.edena.ada.web.controllers.dataset

import org.edena.ada.server.dataaccess.TransientLocalFieldStore
import org.edena.ada.server.models.Field
import org.edena.core.field.FieldTypeId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

// Text fields can't be sorted, so an order by one (looked up in the dictionary) is ignored
class TextSortableOrderTest extends AnyFlatSpec with Matchers {

  private val fieldStore = TransientLocalFieldStore(Seq(
    Field("note", fieldType = FieldTypeId.Text),
    Field("code", fieldType = FieldTypeId.String)
  ))

  private val helper = new DataSetViewHelper {
    override protected def dataSpaceService = throw new UnsupportedOperationException

    def sortable(orderBy: String) = Await.result(sortableOrderBy(fieldStore)(orderBy), 10.seconds)
  }

  "An order by a Text field" should "be ignored, also a descending one" in {
    helper.sortable("note") shouldBe ""
    helper.sortable("-note") shouldBe ""
  }

  "Another order" should "be kept" in {
    helper.sortable("code") shouldBe "code"
    helper.sortable("-code") shouldBe "-code"
    helper.sortable("unknown") shouldBe "unknown"
    helper.sortable("") shouldBe ""
  }
}
