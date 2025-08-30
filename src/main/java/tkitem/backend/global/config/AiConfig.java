package tkitem.backend.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
	// JSON 응답 고정
	private static final String SYSTEM = "You are a professional fashion stylist. Return JSON only.";

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {

		return builder
			.defaultSystem(SYSTEM)
			.build();
	}
}
