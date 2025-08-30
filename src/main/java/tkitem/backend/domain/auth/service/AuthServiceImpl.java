package tkitem.backend.domain.auth.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.global.util.RedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{
	private final RedisUtil redisUtil;

	@Override
	public void logout(Member member) {
		// 토큰 만료 처리
		redisUtil.delete(member.getMemberId().toString());
	}
}
