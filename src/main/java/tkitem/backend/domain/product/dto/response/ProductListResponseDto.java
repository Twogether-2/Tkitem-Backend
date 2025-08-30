package tkitem.backend.domain.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductListResponseDto<T> {
    private List<T> items;
    private Long nextCursor;
    private boolean hasMore;
}