package nb.yoda.orm

import java.sql.{Connection, Timestamp}

import nb.yoda.reflect.Accessor
import org.joda.time.DateTime

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
  * Created by Peerapat A on Mar 31, 2017
  */
object PManager {

  def apply[A: TypeTag : ClassTag](obj: A)(implicit conn: Connection): Int = try {
    insert(obj)
  } catch {
    case _:Throwable => update(obj)
  }

  final def insert[A: TypeTag : ClassTag](obj: A)(implicit conn: Connection): Int = {
    val kv = Accessor.toMap[A](obj)

    val keys = colNames[A]

    val table = obj.getClass.getSimpleName.toLowerCase

    val stmt = insertStatement(table, keys)

    val p = PStatement(stmt)

    keys.foreach(k => set(p, kv(k)))

    p.update
  }

  final def update[A: TypeTag : ClassTag](obj: A)(implicit conn: Connection): Int = {
    val kv = Accessor.toMap[A](obj)

    val meta = findMeta(kv)

    val pk = meta.pk

    val columns = colNames[A]
      .filter(k => k != pk)
      .filter(k => !meta.readonly.contains(k))

    val table = obj.getClass.getSimpleName.toLowerCase

    val stmt = updateStatement(table, pk, columns)

    val p = PStatement(stmt)

    columns.foreach(k => set(p, kv(k)))

    set(p, kv(pk))

    p.update
  }

  final def delete[A](obj: A)(implicit conn: Connection): Int = {
    val kv = Accessor.toMap[A](obj)

    val meta = findMeta(kv)

    val pk = meta.pk

    val table = obj.getClass.getSimpleName.toLowerCase

    PStatement(
      s"""
         | DELETE $table WHERE $pk = ${kv(pk)}
       """.stripMargin)
      .update
  }

  private[orm] def set(p: PStatement, v: Any) = v match {
    case _: Boolean => p.setBoolean(v.asInstanceOf[Boolean])
    case _: Int => p.setInt(v.asInstanceOf[Int])
    case _: Long => p.setLong(v.asInstanceOf[Long])
    case _: Float => p.setDouble(v.asInstanceOf[Float])
    case _: Double => p.setDouble(v.asInstanceOf[Double])
    case _: String => p.setString(v.asInstanceOf[String])
    case _: Timestamp => p.setTimestamp(v.asInstanceOf[Timestamp])
    case _: DateTime => p.setDateTime(v.asInstanceOf[DateTime])
    case _ => ;
  }

  private[orm] def findMeta(kv: Map[String, Any]): Meta = kv
    .find(kv => kv._2.isInstanceOf[Meta])
    .map(kv => kv._2.asInstanceOf[Meta])
    .getOrElse(Meta())

  private[orm] def insertStatement(table: String, keys: List[String]) =
    s"""
       | INSERT INTO $table (${keys.mkString(", ")}) VALUES (${params(keys.size)})
     """.stripMargin

  private[orm] def updateStatement(table: String, pk: String, columns: List[String]) =
    s"""
       | UPDATE $table SET ${updateValue(columns)} = ? WHERE $pk = ?
     """.stripMargin

  private[orm] def updateValue(columns: List[String]): String = columns
    .mkString(" = ?, ")

  private[orm] def colNames[A: TypeTag]: List[String] = Accessor.methods[A]
    .map(sym => sym.name.toString)
    .map(name => name.toLowerCase)

  private[orm] def params(count: Int): String = List.fill(count)("?")
    .mkString(", ")

}
