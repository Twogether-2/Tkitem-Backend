package tkitem.backend.domain.auth.service;

import tkitem.backend.domain.member.vo.Member;

public interface AuthService {
	// 로그아웃
	void logout(Member member);
}
