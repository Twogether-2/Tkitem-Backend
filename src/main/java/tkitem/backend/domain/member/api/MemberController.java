package tkitem.backend.domain.member.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.dto.request.SignUpRequest;
import tkitem.backend.domain.member.dto.request.SocialSignUpRequest;
import tkitem.backend.domain.member.dto.response.MemberInfoResponse;
import tkitem.backend.domain.member.dto.response.TokenResponse;
import tkitem.backend.domain.member.service.MemberService;
import tkitem.backend.domain.member.vo.Member;

@Slf4j
@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {
	private final MemberService memberService;

	@PostMapping("/sign-up")
	@Operation(summary = "회원가입", description = "일반 회원가입을 진행")
	public ResponseEntity<TokenResponse> signUp(@RequestBody SignUpRequest signUpRequest) {
		TokenResponse response = memberService.signUp(signUpRequest);
		return ResponseEntity.ok().body(response);
	}

	@PostMapping("/sign-up/{oauthType}")
	@Operation(summary = "회원가입", description = "소셜 로그인 후 회원가입을 진행")
	public ResponseEntity<TokenResponse> socialSignUp(
		@RequestBody SocialSignUpRequest signUpRequest,
		@PathVariable("oauthType") String oauthType) {
		TokenResponse response = memberService.socialSignUp(signUpRequest, oauthType);
		return ResponseEntity.ok().body(response);
	}

	@PatchMapping("/resign")
	@Operation(summary = "회원 탈퇴", description = "회원 탈퇴 처리")
	public ResponseEntity<String> resign(@AuthenticationPrincipal Member member){
		memberService.resign(member);
		return ResponseEntity.ok().body("success");
	}

	@GetMapping()
	@Operation(summary = "회원 정보 조회", description = "현재 로그인된 회원의 정보를 조회")
	public ResponseEntity<MemberInfoResponse> getMemberInfo(@AuthenticationPrincipal Member member){
		return ResponseEntity.ok().body(member.toDto());
	}

}
