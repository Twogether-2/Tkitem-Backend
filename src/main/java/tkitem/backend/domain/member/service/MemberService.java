package tkitem.backend.domain.member.service;

import tkitem.backend.domain.member.dto.request.InfoInputRequest;
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
	TokenResponse socialSignUp(SocialSignUpRequest signUpRequest, String provider);
	// 추가 회원 정보 입력
	void updateAdditionalInfo(InfoInputRequest inputRequest, Member member);
	// 탈퇴
	void resign(Member member);
	// 프로필 및 닉네임 수정
	void updateImgUrlAndNickname(Member member, String imgUrl, String nickname);
}
