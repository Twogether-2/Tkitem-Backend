package tkitem.backend.domain.auth.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.auth.service.AuthService;
import tkitem.backend.domain.member.dto.request.LoginRequest;
import tkitem.backend.domain.member.dto.request.SocialLoginRequest;
import tkitem.backend.domain.member.service.MailService;
import tkitem.backend.domain.member.vo.Member;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
	private final MailService mailService;
	private final AuthService authService;

	@PostMapping("/login")
	@Operation(summary = "일반 로그인", description = "로그인 시도 후 회원 여부 판단")
	public String login(@RequestBody LoginRequest loginRequest) {
		return "success";
	}

	@PostMapping("/login/kakao")
	@Operation(summary = "카카오 로그인", description = "로그인 시도 후 회원 여부 판단")
	public String login(@RequestBody SocialLoginRequest socialLoginRequest) {
		return "success";
	}

	@PostMapping("/logout")
	@Operation(summary = "로그아웃", description = "로그아웃 처리")
	public ResponseEntity<String> logout(@AuthenticationPrincipal Member member){
		authService.logout(member);
		return ResponseEntity.ok().body("success");
	}
}
