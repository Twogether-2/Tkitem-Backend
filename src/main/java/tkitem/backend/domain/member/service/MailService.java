package tkitem.backend.domain.member.service;

public interface MailService {
	// 이메일 전송
	void sendEmail(String email);
	// 이메일 인증번호 확인
	boolean checkEmailVerificationCode(String email, String code);
}
