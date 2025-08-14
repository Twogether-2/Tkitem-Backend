package tkitem.backend.domain.member.service;

import tkitem.backend.domain.member.dto.request.SignUpRequest;
import tkitem.backend.domain.member.dto.request.SocialSignUpRequest;
import tkitem.backend.domain.member.dto.response.TokenResponse;
import tkitem.backend.domain.member.vo.Member;

public interface MemberService {
	// 이메일 중복 검증
	boolean isDuplicatedEmail(String email);
	// 일반 회원 가입
	TokenResponse signUp(SignUpRequest signUpRequest);
	// 소셜 회원 가입
	TokenResponse socialSignUp(SocialSignUpRequest signUpRequest, String memberType);
	// 탈퇴
	void resign(Member member);
}
