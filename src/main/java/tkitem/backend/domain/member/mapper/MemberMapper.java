package tkitem.backend.domain.member.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import tkitem.backend.domain.member.vo.SignUp;
import tkitem.backend.domain.member.vo.Member;

@Mapper
public interface MemberMapper {
    void insertMember(SignUp signUp);
    Member selectMemberById(@Param("id") Long id);
    boolean existsByEmail(@Param("email") String email);
    boolean existsByEmailAndPassword(@Param("email") String email, @Param("password") String password);
    boolean existsByEmailAndSocialType(@Param("email") String email, @Param("type") String type);
    void updateIsDeleted(@Param("id") Long id);
    void updateIsDeletedFalse(@Param("email") String email);
}
