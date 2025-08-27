package tkitem.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class TkitemBackendApplication {

	@Value("${cloud.aws.region.static}")
	private static String db;

	public static void main(String[] args) {

		SpringApplication.run(TkitemBackendApplication.class, args);
		log.info("db : {}", db);
	}

}
