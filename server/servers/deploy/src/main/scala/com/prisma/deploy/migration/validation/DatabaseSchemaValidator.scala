package com.prisma.deploy.migration.validation
import com.prisma.deploy.connector.{Column, DatabaseSchema, Table}
import com.prisma.shared.models.{Field, Model, Schema}
import com.prisma.utils.boolean.BooleanUtils

trait DatabaseSchemaValidator {
  def check(schema: Schema, databaseSchema: DatabaseSchema): Vector[DeployError]
}

object DatabaseSchemaValidatorImpl extends DatabaseSchemaValidator {
  override def check(schema: Schema, databaseSchema: DatabaseSchema): Vector[DeployError] = {
    DatabaseSchemaValidatorImpl(schema, databaseSchema).check.toVector
  }
}

case class DatabaseSchemaValidatorImpl(schema: Schema, databaseSchema: DatabaseSchema) extends BooleanUtils {
  def check = modelErrors ++ fieldErrors

  val modelErrors = schema.models.flatMap { model =>
    val missingTableError = table(model).isEmpty.toOption {
      DeployError(model.name, s"Could not find the table for the model ${model.name} in the database.")
    }
    missingTableError
  }

  val fieldErrors = {
    val tmp = for {
      model <- schema.models
      field <- model.fields
      _     <- table(model).toVector // only run the validation if the table exists
    } yield {
      column(field) match {
        case Some(column) if field.typeIdentifier != column.typeIdentifier =>
          Some(
            DeployError(
              model.name,
              field.name,
              s"The underlying column for the field ${field.name} has an incompatible type. The field has type `${field.typeIdentifier.userFriendlyTypeName}` and the column has type `${column.typeIdentifier.userFriendlyTypeName}`."
            ))
        case None =>
          Some(DeployError(model.name, field.name, s"Could not find the column for the field ${field.name} in the database."))
        case _ =>
          None
      }
    }

    tmp.flatten
  }

  private def table(model: Model): Option[Table]   = databaseSchema.table(model.dbName)
  private def column(field: Field): Option[Column] = table(field.model).flatMap(_.column(field.dbName))
}
