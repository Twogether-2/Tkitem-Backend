package tkitem.backend.domain.checklist.api;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;
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


}