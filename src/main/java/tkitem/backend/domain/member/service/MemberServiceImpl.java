package tkitem.backend.domain.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.dto.request.SignUpRequest;
import tkitem.backend.domain.member.dto.request.SocialSignUpRequest;
import tkitem.backend.domain.member.dto.response.TokenResponse;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.domain.member.vo.Member;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
	private final MemberMapper memberMapper;

	@Override
	public boolean isDuplicatedEmail(String email) {
		log.info("[MEMBER] 이메일 중복 검증");
		return memberMapper.existsByEmail(email);
	}

	@Override
	public TokenResponse signUp(SignUpRequest signUpRequest) {
		return null;
	}

	@Override
	public TokenResponse socialSignUp(SocialSignUpRequest signUpRequest, String memberType) {

		return null;
	}

	@Override
	public void logout(Member member) {

	}

	@Override
	public void resign(Member member) {

	}
}
