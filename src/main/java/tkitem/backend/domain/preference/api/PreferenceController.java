package tkitem.backend.domain.preference.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.preference.dto.request.CalculateWeightRequest;
import tkitem.backend.domain.preference.dto.response.PreferenceResponse;
import tkitem.backend.domain.preference.service.PreferenceService;

@Slf4j
@RequestMapping("/preference")
@RestController
@RequiredArgsConstructor
public class PreferenceController {
	private final PreferenceService preferenceService;

	@PostMapping("")
	@Operation(summary = "사용자 취향 저장", description = "로그인한 사용자의 패션 취향을 계산하여 저장합니다.")
	public ResponseEntity<String> insertPreference(
		@AuthenticationPrincipal Member member,
		@RequestBody CalculateWeightRequest request
	){
		preferenceService.insertPreference(member, request);
		return ResponseEntity.ok().body("success");
	}

	@GetMapping("")
	@Operation(summary = "사용자 취향 조회", description = "로그인한 사용자의 최신 패션 취향 데이터를 조회합니다.")
	public ResponseEntity<PreferenceResponse> getPreference(@AuthenticationPrincipal Member member){
		PreferenceResponse preference = preferenceService.getPreference(member);
		return ResponseEntity.ok().body(preference);
	}
}
