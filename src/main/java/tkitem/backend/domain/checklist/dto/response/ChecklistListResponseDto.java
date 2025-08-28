package tkitem.backend.domain.checklist.dto.response;

import lombok.*;
import tkitem.backend.domain.checklist.vo.ChecklistItemVo;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistListResponseDto {
    private Long tripId;
    private int totalCount;
    private List<ChecklistItemResponseDto> items;

    public static ChecklistListResponseDto of(Long tripId, List<ChecklistItemVo> vos) {
        List<ChecklistItemResponseDto> items = vos.stream()
                .map(ChecklistItemResponseDto::from)
                .toList();
        return ChecklistListResponseDto.builder()
                .tripId(tripId)
                .totalCount(items.size())
                .items(items)
                .build();
    }
}