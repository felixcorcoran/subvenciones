package es.felixcorcoran.subvenciones.encaje.adaptadores;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Lo común a las pruebas con el servicio entero: un PostgreSQL y un Kafka de verdad, en
 * contenedores. Se arrancan una sola vez para todas las clases (si cada clase los reiniciara, los
 * contextos de Spring en caché apuntarían a puertos que ya no existen). Sin Docker, las pruebas se
 * saltan; en CI, el workflow falla si alguna se salta.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class PruebaConContenedores {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@ServiceConnection
	static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.0");

	static {
		if (DockerClientFactory.instance().isDockerAvailable()) {
			postgres.start();
			kafka.start();
		}
	}
}
