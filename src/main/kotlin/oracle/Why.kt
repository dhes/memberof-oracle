package oracle

import java.io.File
import org.hl7.fhir.r5.model.CodeableConcept
import org.hl7.fhir.r5.model.Coding
import org.hl7.fhir.r5.model.ValueSet
import org.hl7.fhir.utilities.validation.ValidationOptions

/** Diagnostic: asks HAPI's worker context directly, so the reason behind a memberOf answer is visible. */
fun why(packageDir: String, valueSetUrl: String, system: String, code: String, txServer: String?) {
  val engine = HapiR5(txServer)
  File(packageDir).listFiles { f -> f.name.startsWith("ValueSet-") || f.name.startsWith("CodeSystem-") }!!.forEach { engine.load(it.readText()) }
  val context = engine.workerContext
  val valueSet = context.fetchResource(ValueSet::class.java, valueSetUrl)
  val r = context.validateCode(ValidationOptions(), Coding(system, code, null), valueSet)
  println("Coding          ok=${r.isOk} severity=${r.severity} class=${r.errorClass}\n   message=${r.message}")
  val rc = context.validateCode(ValidationOptions(), CodeableConcept().addCoding(Coding(system, code, null)), valueSet)
  println("CodeableConcept ok=${rc.isOk} severity=${rc.severity} class=${rc.errorClass}\n   message=${rc.message}")
}
