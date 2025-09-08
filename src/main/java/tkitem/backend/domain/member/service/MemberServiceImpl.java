package tkitem.backend.domain.member.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.dto.request.InfoInputRequest;
import tkitem.backend.domain.member.dto.request.SignUpRequest;
import tkitem.backend.domain.member.dto.request.SocialSignUpRequest;
import tkitem.backend.domain.member.dto.response.TokenResponse;
import tkitem.backend.domain.member.mapper.MemberMapper;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.member.vo.SignUp;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.security.jwt.JwtProvider;
import tkitem.backend.global.security.login.OidcService;
import tkitem.backend.global.util.HashUtil;
import tkitem.backend.global.util.RedisUtil;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
	private final MemberMapper memberMapper;
	private final OidcService oidcService;
	private final JwtProvider jwtProvider;
	private final PasswordEncoder passwordEncoder;
	private final RedisUtil redisUtil;

	@Override
	public boolean isDuplicatedEmail(String email) {
		log.info("[MEMBER] 이메일 중복 검증 email: {}", email);
		return memberMapper.existsByEmailAndType(email, "NONE");
	}

	@Override
	public TokenResponse signUp(SignUpRequest signUpRequest) {
		log.info("[MEMBER] 일반 회원 가입 email : {}", signUpRequest.getEmail());
		if(memberMapper.existsByEmailAndType(signUpRequest.getEmail(), "NONE")) {
			throw new BusinessException(ErrorCode.DUPLICATED_MEMBER);
		}

		SignUp signUpVo = new SignUp();
		signUpVo.setEmail(signUpRequest.getEmail());
		signUpVo.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
		signUpVo.setNickname(signUpRequest.getNickname());
		signUpVo.setType("NONE");

		memberMapper.insertMember(signUpVo);
		Long memberId = signUpVo.getMemberId();
		return jwtProvider.provideAccessTokenAndRefreshToken(memberId);
	}

	@Override
	public TokenResponse socialSignUp(SocialSignUpRequest signUpRequest, String provider) {
		log.info("[MEMBER] 소셜 회원 가입 email : {}", signUpRequest.getEmail());
		if(memberMapper.existsByEmailAndType(signUpRequest.getEmail(), provider.toUpperCase())) {
			throw new BusinessException(ErrorCode.DUPLICATED_MEMBER);
		}

		String oauthId = oidcService.verify(signUpRequest.getIdToken()).get("sub").toString();
		SignUp signUpVo = new SignUp();
		signUpVo.setEmail(signUpRequest.getEmail());
		signUpVo.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
		signUpVo.setNickname(signUpRequest.getNickname());
		signUpVo.setType(provider.toUpperCase());
		signUpVo.setOauthId(HashUtil.hash(oauthId));

		memberMapper.insertMember(signUpVo);
		Long memberId = signUpVo.getMemberId();
		return jwtProvider.provideAccessTokenAndRefreshToken(memberId);
	}

	@Override
	public void updateAdditionalInfo(InfoInputRequest inputRequest, Member member) {
		log.info("[MEMBER] 추가 회원 가입 memberId : {}", member.getMemberId());
		if(memberMapper.selectMemberByMemberId(member.getMemberId()).isEmpty()){
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}
		memberMapper.updateBirthdayAndGender(member.getMemberId(), inputRequest.getBirthday(), inputRequest.getGender());
	}

	@Override
	public void resign(Member member) {
		// 토큰 만료 처리
		redisUtil.delete(member.getMemberId().toString());
		// 논리적 삭제
		memberMapper.updateIsDeleted(member.getMemberId());
	}

	@Override
	public void updateImgUrlAndNickname(Member member, String nickname) {
		// 기존 정보랑 일치하면 null 대입
		if(nickname != null && nickname.isEmpty()){
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		String newNickname = nickname == null || nickname.equals(member.getNickname()) ? null : nickname;

		memberMapper.updateNickname(member.getMemberId(), newNickname);
	}

}
