package tkitem.backend.domain.member.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import tkitem.backend.domain.member.vo.SignUp;
import tkitem.backend.domain.member.vo.Member;

@Mapper
public interface MemberMapper {
    void insertMember(SignUp signUp);
    Optional<Member> selectMemberByMemberId(@Param("memberId") Long memberId);
    Optional<Member> selectMemberByEmail(@Param("email") String email);
    Optional<Member> selectMemberByEmailAndTypeAndOauthId(
        @Param("email") String email,
        @Param("type") String type,
        @Param("oauthId") String oauthId
    );
    boolean existsByEmail(@Param("email") String email);
    boolean existsByEmailAndPassword(@Param("email") String email, @Param("password") String password);
    boolean existsByEmailAndType(
        @Param("email") String email,
        @Param("type") String type
    );
    void updateBirthdayAndGender(
        @Param("memberId") Long memberId,
        @Param("birthday") String birthday,
        @Param("gender") char gender
    );
    void updateIsDeleted(@Param("memberId") Long memberId);
    void updateIsDeletedFalse(@Param("email") String email);
}
