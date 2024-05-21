package reactivemongo.api.commands

import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.collection.BSONSerializationPack

import scala.util.Try

case class FSyncCommand(
  async: Boolean = true,
  lock: Boolean = false
) extends Command with CommandWithResult[Unit] {
  override protected[reactivemongo] def commandKind = new CommandKind("fsync")
}

object FSyncCommand {

  implicit object FSyncCommandWriter extends BSONSerializationPack.Writer[FSyncCommand] { // JSONSerializationPack.Writer[FSyncCommand] {

    override def writeTry(t: FSyncCommand) =
      Try(BSONDocument("fsync" -> 1, "async" -> t.async, "lock" -> t.lock))
  }

  implicit object UnitBoxReader extends BSONSerializationPack.Reader[Unit] {

    override def readDocument(doc: BSONDocument): Try[Unit] = Try(())
  }
}
