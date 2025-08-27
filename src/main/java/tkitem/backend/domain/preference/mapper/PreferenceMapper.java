package tkitem.backend.domain.preference.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import tkitem.backend.domain.preference.vo.FashionType;
import tkitem.backend.domain.preference.vo.Preference;

@Mapper
public interface PreferenceMapper {
	// 취향 정보 삽입
	void insertPreference(Preference preference);

	// memberId로 가장 최근 취향 조회
	Preference selectPreferenceByMemberId(@Param("memberId") Long memberId);

	// fashionType 으로 패션 유형 정보 조회
	FashionType selectFashionTypeById(@Param("fashionTypeId") String fashionTypeId);
}
