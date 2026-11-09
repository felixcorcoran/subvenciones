package es.felixcorcoran.subvenciones.recogida;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Las mismas reglas hexagonales que el servicio de encaje (docs/decisiones/0001) */
@AnalyzeClasses(packages = "es.felixcorcoran.subvenciones.recogida", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

	private static final String BASE = "es.felixcorcoran.subvenciones.recogida";

	@ArchTest
	static final ArchRule capas = layeredArchitecture().consideringOnlyDependenciesInLayers()
			.layer("Dominio").definedBy(BASE + ".dominio..")
			.layer("Aplicacion").definedBy(BASE + ".aplicacion..")
			.layer("Adaptadores").definedBy(BASE + ".adaptadores..")
			.layer("Arranque").definedBy(BASE)
			.whereLayer("Adaptadores").mayOnlyBeAccessedByLayers("Arranque")
			.whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Adaptadores", "Arranque")
			.whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacion", "Adaptadores", "Arranque");

	@ArchTest
	static final ArchRule dominioYAplicacionSinFrameworks = noClasses()
			.that().resideInAnyPackage(BASE + ".dominio..", BASE + ".aplicacion..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "tools.jackson..",
					"com.fasterxml..", "jakarta..", "java.sql..", "org.apache.kafka..");
}
