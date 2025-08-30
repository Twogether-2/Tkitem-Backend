package tkitem.backend.domain.auth.service;

import java.time.Instant;
import java.util.Random;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.util.RedisUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
	private final JavaMailSender emailSender;
	private final RedisUtil redisUtil;

	// 인증번호 8자리 무작위 생성
	public String createCode() {
		Random random = new Random();
		StringBuilder key = new StringBuilder();

		for (int i = 0; i < 8; i++) {
			int idx = random.nextInt(3);
			switch (idx) {
				case 0 -> key.append((char) (random.nextInt(26) + 97)); // a-z
				case 1 -> key.append((char) (random.nextInt(26) + 65)); // A-Z
				case 2 -> key.append(random.nextInt(10)); // 0-9
			}
		}
		return key.toString();
	}

	// 메일 본문
	private String buildEmailContent(String authCode) {
		return """
               <h1>Tkitem(트킷템)</h1>
               <h2>이메일 인증 코드</h2>
               <p>아래 코드를 복사해서 입력해주세요.</p>
               <div style="font-size:18px; font-weight:bold; color:#2c7be5;">
                 %s
               </div>
               """.formatted(authCode);
	}

	// 메일 양식 작성
	public MimeMessage createEmailForm(String email, String authCode) throws MessagingException{
		String setFrom = "qlrqod3356@gmail.com"; // 발신자
		String title = "[Tkitem] email verification code"; // 제목

		MimeMessage message = emailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

		helper.setFrom(setFrom);
		helper.setTo(email);
		helper.setSubject(title);
		helper.setText(buildEmailContent(authCode), true); // true → HTML 지원

		return message;
	}

	// 메일 전송
	public String sendEmail(String email){
		log.info("EmailService : sendEmail = {}", email);
		long sec = 60 * 5 * 1000L;
		try{
			String authCode = createCode();
			MimeMessage emailForm = createEmailForm(email, authCode);
			emailSender.send(emailForm);

			// Redis 저장 (5분)
			redisUtil.set(email, authCode, sec);
		}catch(Exception e){
			throw new RuntimeException(e);
		}

		return Instant.now().plusSeconds(sec).toString();
	}

	// 코드 검증
	public boolean verifyAuthCode(String email, String code) {
		String result = redisUtil.get(email);
		return result != null && result.equals(code);
	}
}
