package tkitem.backend.domain.auth.service;

import jakarta.mail.MessagingException;

public interface EmailService {
	// 이메일 전송
	String sendEmail(String email);
	// 이메일 인증번호 확인
	boolean verifyAuthCode(String email, String code);
}
