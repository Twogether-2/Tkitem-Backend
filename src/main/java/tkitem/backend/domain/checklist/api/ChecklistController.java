package tkitem.backend.domain.checklist.api;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.checklist.dto.request.ChecklistCreateRequestDto;
import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;
import tkitem.backend.domain.checklist.dto.response.ChecklistListResponseDto;
import tkitem.backend.domain.checklist.service.ChecklistService;
import tkitem.backend.domain.member.vo.Member;

@RequiredArgsConstructor
@RequestMapping("/checklist")
@RestController
public class ChecklistController {

    private final ChecklistService checklistService;

    @PostMapping("/ai/{tripId}")
    @Operation(summary = "체크리스트 자동 세팅", description = "trip_id에 따른 체크리스트 자동 세팅")
    public ResponseEntity<ChecklistAiResponseDto> generateAiChecklist(
            @PathVariable Long tripId,
            @AuthenticationPrincipal Member member
    ) {
        return ResponseEntity.ok(checklistService.generateAiChecklist(tripId, member.getMemberId()));
    }

    @GetMapping("/{tripId}")
    @Operation(summary="체크리스트 조회", description = """
        trip_id별 is_deleted='F' 항목 조회
        - day 미지정: 전체
        - day=0: 공통만 (schedule_date IS NULL)
        - day>=1: 해당 일자만 (schedule_date = day)
        - checked: false(체크비활성), true(체크활성), null(전체)
        """
    )
    public ResponseEntity<ChecklistListResponseDto> getChecklist(
            @PathVariable Long tripId,
            @RequestParam(required = false) Integer day,
            @RequestParam(required = false) Boolean checked
    ) {
        return ResponseEntity.ok(checklistService.getChecklistByTrip(tripId, day, checked));
    }

    @PostMapping("/{tripId}")
    @Operation(summary = "체크리스트 수기 등록", description = """
            - scheduleDate가 null이거나 0이면 공통
            - scheduleDate가 1이상이면 해당 여행 일정 DAY
            """)
    public ResponseEntity<String> createChecklist(
            @PathVariable Long tripId,
            @Valid @RequestBody ChecklistCreateRequestDto requestDto,
            @AuthenticationPrincipal Member member
    ){
        checklistService.createChecklist(
                tripId,
                member.getMemberId(),
                requestDto.getProductCategorySubIds(),
                requestDto.getScheduleDate()
        );
        return ResponseEntity.ok("추가 성공");
    }

}