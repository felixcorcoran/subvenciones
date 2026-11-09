package es.felixcorcoran.subvenciones.lectura.adaptadores.salida.gemini;

/**
 * Las instrucciones y el esquema de respuesta, los mismos que scripts/analizar.ts de la web: si
 * cambian allí, se cambian aquí.
 */
final class Instrucciones {

	private Instrucciones() {
	}

	static final String SISTEMA = """
			Lees convocatorias de ayudas públicas españolas y las explicas al dueño de un pequeño negocio o a un autónomo, que no sabe de jerga administrativa.

			Reglas:
			- Usa SOLO lo que dice el documento. Si un dato no aparece, pon "no_consta" o null. No supongas ni completes con lo habitual.
			- Español claro y directo, frases cortas, sin jerga («beneficiario», «ex post», «acreditar» → dilo con palabras normales).
			- categoria: "ayuda" si es dinero (o una ventaja) que un negocio puede pedir para su actividad; "premio" si es un concurso o premio; "otro" si es otra cosa (seleccionar una entidad colaboradora, becas para personas, etc.).
			- requisitos: los que de verdad deciden si alguien puede pedirla (dónde tiene que estar el negocio, qué tipo de negocio, antigüedad, estar al corriente de pagos…). Como mucho 8, cada uno en una frase corta.
			- gastos: en qué se puede gastar la ayuda. Lista vacía si no lo dice o no aplica.
			- importe: lo que puede recibir cada negocio («Hasta 3.000 € por negocio», «El 50 % de la inversión, con un máximo de 10.000 €»). Si solo indica el presupuesto total, dilo así. importeMaximo: el máximo por negocio en euros, o null.
			- plazo: cuándo se puede pedir, dicho claro. plazoFin: la fecha de cierre AAAA-MM-DD solo si se puede saber con certeza a partir del documento y de la fecha de publicación que te doy; si depende de una publicación cuya fecha no sabes, null.
			- condiciones.autonomos / sociedades: "si" si pueden pedirla, "no" si quedan fuera, "no_consta" si no lo dice.
			- condiciones.emprendedores: "si" si pueden pedirla personas que aún no han empezado su negocio (por ejemplo, ayudas para darse de alta), "no" si hay que tener ya la actividad en marcha, "no_consta" si no lo dice.
			- condiciones.maxEmpleados: el máximo de trabajadores permitido, o null. antiguedadMinMeses: meses mínimos de actividad, o null. antiguedadMaxMeses: si solo vale para negocios creados hace menos de X meses, esos meses; si no, null.
			- condiciones.municipio: si el negocio tiene que estar en un municipio concreto, su nombre; si no, null.
			- dudas: una frase corta con lo importante que no queda claro, o null.""";

	static final String ESQUEMA = """
			{"type":"OBJECT","properties":{
			  "categoria":{"type":"STRING","enum":["ayuda","premio","otro"]},
			  "resumen":{"type":"STRING"},
			  "paraQuien":{"type":"STRING"},
			  "importe":{"type":"STRING"},
			  "importeMaximo":{"type":"NUMBER","nullable":true},
			  "plazo":{"type":"STRING"},
			  "plazoFin":{"type":"STRING","nullable":true},
			  "requisitos":{"type":"ARRAY","items":{"type":"STRING"}},
			  "gastos":{"type":"ARRAY","items":{"type":"STRING"}},
			  "condiciones":{"type":"OBJECT","properties":{
			    "autonomos":{"type":"STRING","enum":["si","no","no_consta"]},
			    "sociedades":{"type":"STRING","enum":["si","no","no_consta"]},
			    "emprendedores":{"type":"STRING","enum":["si","no","no_consta"]},
			    "maxEmpleados":{"type":"INTEGER","nullable":true},
			    "antiguedadMinMeses":{"type":"INTEGER","nullable":true},
			    "antiguedadMaxMeses":{"type":"INTEGER","nullable":true},
			    "municipio":{"type":"STRING","nullable":true}},
			   "required":["autonomos","sociedades","emprendedores","maxEmpleados","antiguedadMinMeses","antiguedadMaxMeses","municipio"]},
			  "dudas":{"type":"STRING","nullable":true}},
			 "required":["categoria","resumen","paraQuien","importe","importeMaximo","plazo","plazoFin","requisitos","gastos","condiciones","dudas"]}""";
}
