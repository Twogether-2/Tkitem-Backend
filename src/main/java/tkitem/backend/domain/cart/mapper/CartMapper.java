package tkitem.backend.domain.cart.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.cart.dto.CartInsertDto;

@Mapper
public interface CartMapper {

    Long findCartIdByMemberId(@Param("memberId") Long memberId);
    int insertCart(CartInsertDto dto);
}
