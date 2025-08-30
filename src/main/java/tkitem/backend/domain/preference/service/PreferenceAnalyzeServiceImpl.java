package tkitem.backend.domain.preference.service;

import org.springframework.ai.content.Media;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.preference.dto.response.ScoreResponse;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import java.net.URI;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PreferenceAnalyzeServiceImpl implements PreferenceAnalyzeService {
	private final ChatClient chatClient;

	@Override
	public ScoreResponse getWeightByOnlyOpenAI(String imageUrl) {
		log.info("[PreferenceAnalyzeService] getWeightByOnlyOpenAI imageUrl = {}", imageUrl);
		return callOnce(imageUrl);
	}

	private ScoreResponse callOnce(String imageUrl) {
		PromptTemplate promptTemplate = new PromptTemplate(new ClassPathResource("prompts/fashion-score.md"));
		String promptText = promptTemplate.render().replace("{", "\\{").replace("}", "\\}");

        return chatClient.prompt()
			.user(userSpec -> userSpec
				.text(promptText)
				.media(new Media(mappingImageType(imageUrl), URI.create(imageUrl)))
			)
			.call()
			.entity(ScoreResponse.class);
	}

	// 이미지 타입 수동 매핑
	private MimeType mappingImageType(String imgUrl){
		if (imgUrl.endsWith(".png")) return MimeTypeUtils.IMAGE_PNG;
		else if (imgUrl.endsWith(".jpg") || imgUrl.endsWith(".jpeg")) return MimeTypeUtils.IMAGE_JPEG;
		else if (imgUrl.endsWith(".webp")) return new MimeType("image", "webp");
		else throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
	}
}
