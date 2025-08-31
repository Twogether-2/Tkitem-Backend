package tkitem.backend.domain.cart.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.cart.dto.CartInsertDto;
import tkitem.backend.domain.cart.dto.CartTripDto;

import java.util.List;

@Mapper
public interface CartMapper {

    Long findCartIdByMemberId(@Param("memberId") Long memberId);
    int insertCart(CartInsertDto dto);

    List<CartTripDto> findTripsByMemberId(Long memberId);
}
