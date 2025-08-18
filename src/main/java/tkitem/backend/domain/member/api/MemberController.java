package tkitem.backend.domain.member.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.image.ImageService;
import tkitem.backend.domain.member.dto.request.InfoInputRequest;
import tkitem.backend.domain.member.dto.request.InfoUpdateRequest;
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
	private final ImageService imageService;

	@GetMapping("/duplicate")
	@Operation(summary = "이메일 중복 체크", description = "이메일이 중복된 회원인지 검증")
	public ResponseEntity<Boolean> duplicate(@RequestParam("email") String email) {
		boolean result = memberService.isDuplicatedEmail(email);
		return ResponseEntity.ok(result);
	}


	@PostMapping("/sign-up")
	@Operation(summary = "회원가입", description = "일반 회원가입을 진행")
	public ResponseEntity<TokenResponse> signUp(@RequestBody SignUpRequest signUpRequest) {
		TokenResponse response = memberService.signUp(signUpRequest);
		return ResponseEntity.ok().body(response);
	}

	@PostMapping("/sign-up/{provider}")
	@Operation(summary = "소셜 회원가입", description = "소셜 회원가입을 진행")
	public ResponseEntity<TokenResponse> socialSignUp(
		@RequestBody SocialSignUpRequest signUpRequest,
		@PathVariable("provider") String provider) {
		TokenResponse response = memberService.socialSignUp(signUpRequest, provider);
		return ResponseEntity.ok().body(response);
	}

	@PostMapping("/info")
	@Operation(summary = "추가 정보 입력", description = "추가 정보는 필수로 입력해야합니다.")
	public ResponseEntity<String> updateAdditionalInfo(
		@RequestBody InfoInputRequest inputRequest,
		@AuthenticationPrincipal Member member){
		memberService.updateAdditionalInfo(inputRequest, member);
		return ResponseEntity.ok().body("success");
	}


	@PatchMapping("/resign")
	@Operation(summary = "회원 탈퇴", description = "회원 탈퇴 처리")
	public ResponseEntity<String> resign(@AuthenticationPrincipal Member member){
		memberService.resign(member);
		return ResponseEntity.ok().body("success");
	}

	@PatchMapping(value = "/me", consumes = "multipart/form-data")
	@Operation(summary = "회원 정보 수정", description = "Multipart로 프로필 이미지 업로드, 닉네임 수정 가능")
	public ResponseEntity<String> uploadOne(
		@RequestPart(value = "file", required = false) MultipartFile file,
		@RequestPart(value = "nickname", required = false) InfoUpdateRequest request,
		@AuthenticationPrincipal Member member) throws Exception {
		String imgUrl = null;
		if(file != null && !file.isEmpty()){
			imgUrl = imageService.upload(file);
		}
		memberService.updateImgUrlAndNickname(member, imgUrl, request.getNickname());
		return ResponseEntity.ok("success");
	}

	@GetMapping()
	@Operation(summary = "회원 정보 조회", description = "현재 로그인된 회원의 정보를 조회")
	public ResponseEntity<MemberInfoResponse> getMemberInfo(@AuthenticationPrincipal Member member){
		return ResponseEntity.ok().body(member.toDto());
	}

}
