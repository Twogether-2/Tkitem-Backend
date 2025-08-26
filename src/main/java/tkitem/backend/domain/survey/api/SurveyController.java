package tkitem.backend.domain.survey.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.survey.service.SurveyService;
import tkitem.backend.domain.survey.vo.Survey;

@Slf4j
@RestController
@RequestMapping("/survey")
@RequiredArgsConstructor
public class SurveyController {
	private final SurveyService surveyService;

	@GetMapping("")
	@Operation(summary = "취향 설문 조사 문항 가져오기", description = "회원 성별, 생일에 따라 랜덤으로 가져오기")
	public ResponseEntity<List<Survey>> getSurveys(@AuthenticationPrincipal Member member) {
		List<Survey> result = surveyService.getSurveyQuestions(member);
		return ResponseEntity.ok(result);
	}
}
