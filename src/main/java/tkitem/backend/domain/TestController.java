package tkitem.backend.domain;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

	@GetMapping("/test-check")
	public String testCheck(){
		return "success";
	}
}
