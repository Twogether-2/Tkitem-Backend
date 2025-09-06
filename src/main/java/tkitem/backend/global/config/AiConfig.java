package tkitem.backend.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {
	// JSON 응답 고정
	private static final String SYSTEM = "You are a professional fashion stylist. Return JSON only.";
	private static final String TOUR_PROMPT = "You are a travel schedule-type classifier. Return JSON only.";

	// OpenAI 기본 ChatClient
	@Bean
	@Primary
	public ChatClient openAiChatClient(@Qualifier("openAiChatModel") ChatModel openAi) {
		return ChatClient.builder(openAi).defaultSystem(SYSTEM).build();
	}

	// Gemini(Developer API, OpenAI 호환) 전용 ChatClient
	@Bean(name = "geminiChatClient")
	ChatClient geminiChatClient(@Value("${gemini.api.key}") String geminiApiKey) {

		OpenAiApi geminiApi = OpenAiApi.builder()
				.baseUrl("https://generativelanguage.googleapis.com/v1beta/openai")
				.apiKey(geminiApiKey)
				.completionsPath("/chat/completions") // v1 붙지 않게 명시
				.build();

		OpenAiChatOptions geminiOptions = OpenAiChatOptions.builder()
				.model("gemini-2.0-flash")
				.build();

		OpenAiChatModel geminiModel = OpenAiChatModel.builder()
				.openAiApi(geminiApi)
				.defaultOptions(geminiOptions)
				.build();

		return ChatClient.builder(geminiModel)
				.defaultSystem(TOUR_PROMPT)
				.build();
	}
}
