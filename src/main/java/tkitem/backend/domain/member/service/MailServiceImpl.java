package tkitem.backend.domain.member.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService{

	@Override
	public void sendEmail(String email) {

	}

	@Override
	public boolean checkEmailVerificationCode(String email, String code) {

		return false;
	}
}
