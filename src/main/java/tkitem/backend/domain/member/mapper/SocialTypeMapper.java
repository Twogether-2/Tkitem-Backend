package tkitem.backend.domain.member.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SocialTypeMapper {
	void insertSocialType(@Param("memberId") Long memberId, @Param("type") String type);
}
