package tkitem.backend.domain.checklist.api;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.checklist.dto.request.ChecklistCreateRequestDto;
import tkitem.backend.domain.checklist.dto.response.AiReasonEnvelope;
import tkitem.backend.domain.checklist.dto.response.AiReasonResponse;
import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;
import tkitem.backend.domain.checklist.dto.response.ChecklistListResponseDto;
import tkitem.backend.domain.checklist.service.AiReasonService;
import tkitem.backend.domain.checklist.service.ChecklistService;
import tkitem.backend.domain.member.vo.Member;

@RequiredArgsConstructor
@RequestMapping("/checklist")
@RestController
public class ChecklistController {

    private final ChecklistService checklistService;
    private final AiReasonService aiReasonService;

    @PostMapping("/ai/{tripId}")
    @Operation(summary = "체크리스트 자동 세팅", description = "trip_id에 따른 체크리스트 자동 세팅 + AI 이유 재생성 트리거")
    public ResponseEntity<ChecklistAiResponseDto> generateAiChecklist(
            @PathVariable Long tripId,
            @AuthenticationPrincipal Member member
    ) {
        ChecklistAiResponseDto resp = checklistService.generateAiChecklist(tripId, member.getMemberId());
        aiReasonService.regenerate(tripId);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{tripId}")
    @Operation(summary="체크리스트 조회", description = """
        trip_id별 is_deleted='F' 항목 조회
        - day 미지정: 전체
        - day=0: 공통만 (schedule_date IS NULL)
        - day>=1: 해당 일자만 (schedule_date = day)
        - checked: false(체크비활성), true(체크활성), null(전체)
        - isProduct : null(전체), true(상품형만), false(비상품형만)
        """
    )
    public ResponseEntity<ChecklistListResponseDto> getChecklist(
            @PathVariable Long tripId,
            @RequestParam(required = false) Integer day,
            @RequestParam(required = false) Boolean checked,
            @RequestParam(required = false) Boolean isProduct
    ) {
        return ResponseEntity.ok(checklistService.getChecklistByTrip(tripId, day, checked, isProduct));
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

    @DeleteMapping("/item/{checklistItemId}")
    @Operation(summary = "체크리스트 단건 삭제", description = "checklist_item_id 기준 soft delete (is_deleted='T')")
    public ResponseEntity<String> deleteChecklistItem(
            @PathVariable Long checklistItemId,
            @AuthenticationPrincipal Member member
    ) {
        checklistService.deleteChecklistItem(checklistItemId, member.getMemberId());
        return ResponseEntity.ok("삭제되었습니다.");
    }

    @DeleteMapping("/{tripId}")
    @Operation(summary = "체크리스트 초기화(일괄 삭제)",
            description = "해당 trip의 is_deleted='F' 항목을 모두 'T'로 변경")
    public ResponseEntity<String> deleteAllActiveByTrip(
            @PathVariable Long tripId,
            @AuthenticationPrincipal Member member
    ) {
        int count = checklistService.deleteAllActiveByTrip(tripId, member.getMemberId());
        return ResponseEntity.ok(count + "건 삭제되었습니다.");
    }

    @PatchMapping("/item/{checklistItemId}/checked")
    @Operation(summary="체크/체크해제(단건)",
            description="value=true면 체크, false면 체크해제")
    public ResponseEntity<String> setChecked(
            @PathVariable Long checklistItemId,
            @RequestParam boolean value,
            @AuthenticationPrincipal Member member
    ) {
        checklistService.setChecked(checklistItemId, value, member.getMemberId());
        return ResponseEntity.ok(value ? "체크되었습니다." : "체크 해제되었습니다.");
    }

    @GetMapping("/{tripId}/days")
    @Operation(summary = "여행 총 일수 조회", description = "출발~도착일 차이(+1)를 반환")
    public ResponseEntity<Integer> getTripTotalDays(@PathVariable Long tripId) {
        return ResponseEntity.ok(checklistService.getTripTotalDays(tripId));
    }

    @GetMapping("/{tripId}/ai/reason")
    @Operation(
            summary = "AI 체크리스트 이유 조회",
            description = "is_deleted='F' 최신본 상태를 반환(READY/PROCESSING/ERROR). 기본적으로 없으면 생성 트리거 후 PROCESSING 반환."
    )
    public ResponseEntity<AiReasonEnvelope> getAiReason(
            @PathVariable long tripId,
            @RequestParam(defaultValue = "true") boolean triggerIfMissing
    ) {
        AiReasonEnvelope env = aiReasonService.getOrTrigger(tripId, triggerIfMissing);

        if ("READY".equals(env.getStatus())) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(env);
        }
        // PROCESSING 또는 ERROR → 202로 내려주면 프런트에서 폴링/재시도 UI 가능
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Retry-After", "2")
                .cacheControl(CacheControl.noStore())
                .body(env);
    }



}